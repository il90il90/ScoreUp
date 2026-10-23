package com.scoreup.shared

import android.content.Context
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.scoreup.core.HeartTracker
import com.scoreup.core.ScoreEngine
import com.scoreup.core.SyncCodec
import com.scoreup.core.SyncMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Wear OS Data Layer transport for [ScoreEngine], over two independent channels:
 *
 *  1. MessageClient: low-latency delivery of each event + periodic anti-entropy summaries.
 *  2. DataClient: every device also publishes its whole active match log as a DataItem at
 *     `/scoreup/state/<deviceId>`. Google Play services syncs DataItems by itself whenever the
 *     devices see each other (even if our process wasn't running), so state converges even when
 *     messages are dropped or no reachable peer was known at send time.
 *
 * Both channels carry the same idempotent [SyncMessage]s, so duplicates are harmless.
 */
class WearSync(
    context: Context,
    private val engine: ScoreEngine,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    data class Peer(val id: String, val name: String, val nearby: Boolean, val appVersionCode: Long? = null)

    /** One heart-rate reading in beats per minute, stamped when it was measured or received. */
    data class HeartReading(val bpm: Int, val atMs: Long)

    /** Live sync health, shown on the phone's ⌚ screen and the watch's controls page. */
    data class Diagnostics(
        val connectedNodes: Int = 0,
        val scoreUpPeers: Int = 0,
        val messagesSent: Int = 0,
        val messagesReceived: Int = 0,
        val dataItemsPublished: Int = 0,
        val dataItemsReceived: Int = 0,
        val lastSentMs: Long = 0,
        val lastReceivedMs: Long = 0,
        val lastError: String? = null,
    )

    private val app = context.applicationContext
    private val messageClient = Wearable.getMessageClient(app)
    private val capabilityClient = Wearable.getCapabilityClient(app)
    private val nodeClient = Wearable.getNodeClient(app)
    private val dataClient = Wearable.getDataClient(app)
    private val outbox = Channel<SyncMessage>(Channel.UNLIMITED)
    private val publishRequests = Channel<Unit>(Channel.CONFLATED)

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    /** Nodes running ScoreUp (have our capability) and currently reachable. */
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

    private val _lastSyncMs = MutableStateFlow(0L)
    val lastSyncMs: StateFlow<Long> = _lastSyncMs.asStateFlow()

    private val _connectedDevices = MutableStateFlow<List<Peer>>(emptyList())
    /** All connected Wear OS nodes, whether or not ScoreUp is installed on them. */
    val connectedDevices: StateFlow<List<Peer>> = _connectedDevices.asStateFlow()

    private val _diagnostics = MutableStateFlow(Diagnostics())
    val diagnostics: StateFlow<Diagnostics> = _diagnostics.asStateFlow()

    private val _heartRate = MutableStateFlow<HeartReading?>(null)
    /** Latest heart rate measured by the peer watch (the watch stops sending when its app closes). */
    val heartRate: StateFlow<HeartReading?> = _heartRate.asStateFlow()
    private var lastHeartSent: HeartReading? = null

    private var jobs = emptyList<Job>()
    private val peerVersions = HashMap<String, Long>()

    private val messageListener = MessageClient.OnMessageReceivedListener { onMessage(it) }
    private val dataListener = DataClient.OnDataChangedListener { onDataChanged(it) }
    private val capabilityListener = CapabilityClient.OnCapabilityChangedListener { info -> updatePeers(info); handshake() }

    fun start() {
        if (jobs.isNotEmpty()) return
        engine.outbound = { m ->
            outbox.trySend(m)
            if (m is SyncMessage.Events) publishRequests.trySend(Unit) // local change → republish state
        }
        messageClient.addListener(messageListener)
        dataClient.addListener(dataListener)
        capabilityClient.addListener(capabilityListener, CAPABILITY)
        jobs = listOf(
            scope.launch { for (m in outbox) deliver(m) },
            scope.launch {
                for (r in publishRequests) { publishState(); delay(250) } // coalesce rapid taps
            },
            scope.launch {
                importExistingDataItems()
                publishRequests.trySend(Unit)
                // Anti-entropy: cheap periodic summary so missed messages heal within seconds.
                while (isActive) {
                    refreshPeers()
                    if (_peers.value.isNotEmpty()) outbox.trySend(engine.summary(reply = true))
                    delay(if (_peers.value.isEmpty()) 15_000 else 5_000)
                }
            },
        )
    }

    fun stop() {
        messageClient.removeListener(messageListener)
        dataClient.removeListener(dataListener)
        capabilityClient.removeListener(capabilityListener, CAPABILITY)
        jobs.forEach { it.cancel() }; jobs = emptyList()
    }

    /** Immediately exchanges summaries and republishes state (on resume / "Sync now"). */
    fun handshake() {
        outbox.trySend(engine.summary(reply = true))
        publishRequests.trySend(Unit)
        scope.launch { refreshPeers() }
    }

    // ------------------------------------------------------------------ inbound

    /** Entry point for messages (also from the WearableListenerService while the UI is closed). */
    fun onMessage(event: MessageEvent) {
        if (event.path == HEART_PATH) {
            event.data.decodeToString().toIntOrNull()?.takeIf { it in HeartTracker.MIN_BPM..HeartTracker.MAX_BPM }
                ?.let { _heartRate.value = HeartReading(it, System.currentTimeMillis()) }
            return
        }
        if (event.path != PATH) return
        val msg = SyncCodec.decode(event.data) ?: return note("Unreadable message from ${event.sourceNodeId}")
        if (msg is SyncMessage.Summary) {
            peerVersions[event.sourceNodeId] = msg.appVersionCode
            _peers.update { list ->
                // A node that talks our protocol IS a peer, even if the capability lookup missed it.
                val known = list.any { it.id == event.sourceNodeId }
                val base = if (known) list else list + (_connectedDevices.value.firstOrNull { it.id == event.sourceNodeId }
                    ?: Peer(event.sourceNodeId, "ScoreUp", true))
                base.map { if (it.id == event.sourceNodeId) it.copy(appVersionCode = msg.appVersionCode) else it }
            }
        }
        _lastSyncMs.value = System.currentTimeMillis()
        _diagnostics.update { it.copy(messagesReceived = it.messagesReceived + 1, lastReceivedMs = System.currentTimeMillis()) }
        engine.receive(msg)
    }

    /** DataItem changes (also from the WearableListenerService). */
    fun onDataChanged(events: DataEventBuffer) {
        try {
            for (e in events) if (e.type == DataEvent.TYPE_CHANGED) importItem(e.dataItem)
        } finally {
            events.release()
        }
    }

    private fun importItem(item: DataItem) {
        val path = item.uri.path ?: return
        if (!path.startsWith(STATE_PREFIX)) return
        val msg = item.data?.let { SyncCodec.decode(it) } ?: return
        if (msg.from == engine.deviceId) return
        _lastSyncMs.value = System.currentTimeMillis()
        _diagnostics.update { it.copy(dataItemsReceived = it.dataItemsReceived + 1, lastReceivedMs = System.currentTimeMillis()) }
        engine.receive(msg)
        // The peer may lack some of OUR events: answer with a summary so it asks for them.
        outbox.trySend(engine.summary(reply = false))
    }

    private suspend fun importExistingDataItems() {
        runCatching {
            val buffer = dataClient.dataItems.await()
            try { buffer.forEach { importItem(it) } } finally { buffer.release() }
        }.onFailure { note("Read data items: ${describe(it)}") }
    }

    // ------------------------------------------------------------------ outbound

    private suspend fun deliver(m: SyncMessage) {
        // Peers advertising our capability, plus every connected node: if the capability lookup
        // is stale or fails, the message still reaches the watch (others simply ignore it).
        if (_peers.value.isEmpty() && _connectedDevices.value.isEmpty()) refreshPeers()
        val targets = (_peers.value + _connectedDevices.value).distinctBy { it.id }
        if (targets.isEmpty()) return // the DataItem channel and reconnect summaries will catch up
        val bytes = SyncCodec.encode(m)
        for (node in targets) {
            runCatching { messageClient.sendMessage(node.id, PATH, bytes).await() }
                .onSuccess { _diagnostics.update { it.copy(messagesSent = it.messagesSent + 1, lastSentMs = System.currentTimeMillis()) } }
                .onFailure { note("Send to ${node.name}: ${describe(it)}") }
        }
    }

    /**
     * Shares the wearer's heart rate with the phone: on a change (at most every 2 s) and as a
     * keep-alive every 10 s. Ephemeral, so it bypasses the event log and the DataItem channel.
     */
    fun sendHeartRate(bpm: Int) {
        val now = System.currentTimeMillis()
        val last = lastHeartSent
        if (last != null && now - last.atMs < if (bpm == last.bpm) 10_000 else 2_000) return
        lastHeartSent = HeartReading(bpm, now)
        val targets = (_peers.value + _connectedDevices.value).distinctBy { it.id }
        if (targets.isEmpty()) return
        val bytes = bpm.toString().encodeToByteArray()
        scope.launch { for (node in targets) runCatching { messageClient.sendMessage(node.id, HEART_PATH, bytes).await() } }
    }

    /** Publishes this device's full active match log (bounded) as an urgent DataItem. */
    private suspend fun publishState() {
        val log = engine.activeLog
        var events = log.events.values.sortedBy { it.lamport }
        var bytes = SyncCodec.encode(SyncMessage.Events(engine.deviceId, log.header, events))
        // DataItems are limited to 100 KB: keep the newest events; summaries fill in the rest.
        while (bytes.size > MAX_ITEM_BYTES && events.size > 10) {
            events = events.takeLast(events.size * 2 / 3)
            bytes = SyncCodec.encode(SyncMessage.Events(engine.deviceId, log.header, events))
        }
        val request = PutDataRequest.create("$STATE_PREFIX${engine.deviceId}").setData(bytes).setUrgent()
        runCatching { dataClient.putDataItem(request).await() }
            .onSuccess { _diagnostics.update { it.copy(dataItemsPublished = it.dataItemsPublished + 1, lastSentMs = System.currentTimeMillis()) } }
            .onFailure { note("Publish state: ${describe(it)}") }
    }

    suspend fun refreshPeers() {
        runCatching { _connectedDevices.value = nodeClient.connectedNodes.await().map { it.toPeer() } }
            .onFailure { note("Connected devices: ${describe(it)}") }
        runCatching {
            val info = capabilityClient.getCapability(CAPABILITY, CapabilityClient.FILTER_REACHABLE).await()
            updatePeers(info)
            _diagnostics.update { it.copy(connectedNodes = _connectedDevices.value.size, scoreUpPeers = _peers.value.size) }
        }.onFailure {
            // Play services / Wear OS companion missing: no peers, app keeps working standalone.
            _peers.value = emptyList()
            note("Find ScoreUp on watch/phone: ${describe(it)}")
        }
    }

    private fun updatePeers(info: CapabilityInfo) {
        _peers.value = info.nodes.map { it.toPeer() }
        _diagnostics.update { it.copy(scoreUpPeers = _peers.value.size) }
    }

    private fun Node.toPeer() = Peer(id, displayName, isNearby, peerVersions[id])

    private fun note(error: String) {
        Log.w(TAG, error)
        _diagnostics.update { it.copy(lastError = error) }
    }

    private fun describe(t: Throwable): String =
        if (t is ApiException) "Play services error ${t.statusCode}: ${t.message}" else (t.message ?: t.javaClass.simpleName)

    companion object {
        const val PATH = "/scoreup/sync/v1"
        const val HEART_PATH = "/scoreup/heart/v1"
        const val STATE_PREFIX = "/scoreup/state/"
        const val CAPABILITY = "scoreup_sync"
        private const val MAX_ITEM_BYTES = 90_000
        private const val TAG = "ScoreUpSync"
    }
}
