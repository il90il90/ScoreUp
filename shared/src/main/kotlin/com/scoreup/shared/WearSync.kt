package com.scoreup.shared

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.scoreup.core.ScoreEngine
import com.scoreup.core.SyncCodec
import com.scoreup.core.SyncMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.Channel

/**
 * Wear OS Data Layer transport for [ScoreEngine]. Messages are fire-and-forget; reliability
 * comes from the protocol (idempotent events + periodic anti-entropy summaries), so a lost or
 * duplicated message never corrupts the score.
 */
class WearSync(
    context: Context,
    private val engine: ScoreEngine,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    data class Peer(val id: String, val name: String, val nearby: Boolean, val appVersionCode: Long? = null)

    private val app = context.applicationContext
    private val messageClient = Wearable.getMessageClient(app)
    private val capabilityClient = Wearable.getCapabilityClient(app)
    private val nodeClient = Wearable.getNodeClient(app)
    private val outbox = Channel<SyncMessage>(Channel.UNLIMITED)

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    /** Nodes running ScoreUp (have our capability) and currently reachable. */
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

    private val _lastSyncMs = MutableStateFlow(0L)
    val lastSyncMs: StateFlow<Long> = _lastSyncMs.asStateFlow()

    private val _connectedDevices = MutableStateFlow<List<Peer>>(emptyList())
    /** All connected Wear OS nodes, whether or not ScoreUp is installed on them. */
    val connectedDevices: StateFlow<List<Peer>> = _connectedDevices.asStateFlow()

    private var jobs = emptyList<Job>()
    private val peerVersions = HashMap<String, Long>()

    private val messageListener = MessageClient.OnMessageReceivedListener { onMessage(it) }
    private val capabilityListener = CapabilityClient.OnCapabilityChangedListener { info -> updatePeers(info); handshake() }

    fun start() {
        if (jobs.isNotEmpty()) return
        engine.outbound = { outbox.trySend(it) }
        messageClient.addListener(messageListener)
        capabilityClient.addListener(capabilityListener, CAPABILITY)
        jobs = listOf(
            scope.launch { for (m in outbox) deliver(m) },
            scope.launch {
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
        capabilityClient.removeListener(capabilityListener, CAPABILITY)
        jobs.forEach { it.cancel() }; jobs = emptyList()
    }

    /** Immediately exchanges summaries (e.g. on resume / reconnection). */
    fun handshake() { outbox.trySend(engine.summary(reply = true)) }

    /** Entry point for messages delivered to a WearableListenerService while the UI is closed. */
    fun onMessage(event: MessageEvent) {
        if (event.path != PATH) return
        val msg = SyncCodec.decode(event.data) ?: return
        if (msg is SyncMessage.Summary) {
            peerVersions[event.sourceNodeId] = msg.appVersionCode
            _peers.value = _peers.value.map { if (it.id == event.sourceNodeId) it.copy(appVersionCode = msg.appVersionCode) else it }
        }
        _lastSyncMs.value = System.currentTimeMillis()
        engine.receive(msg)
    }

    private suspend fun deliver(m: SyncMessage) {
        val targets = _peers.value.ifEmpty { refreshPeers(); _peers.value }
        if (targets.isEmpty()) return // offline: the summary exchange on reconnect will catch up
        val bytes = SyncCodec.encode(m)
        for (node in targets) {
            runCatching { messageClient.sendMessage(node.id, PATH, bytes).await() }
                .onFailure { Log.w(TAG, "send to ${node.name} failed: ${it.message}") }
        }
    }

    suspend fun refreshPeers() {
        runCatching {
            val info = capabilityClient.getCapability(CAPABILITY, CapabilityClient.FILTER_REACHABLE).await()
            updatePeers(info)
            _connectedDevices.value = nodeClient.connectedNodes.await().map { it.toPeer() }
        }.onFailure {
            // Play services / Wear OS app missing on the phone: no peers, app keeps working standalone.
            _peers.value = emptyList()
        }
    }

    private fun updatePeers(info: CapabilityInfo) {
        _peers.value = info.nodes.map { it.toPeer() }
    }

    private fun Node.toPeer() = Peer(id, displayName, isNearby, peerVersions[id])

    companion object {
        const val PATH = "/scoreup/sync/v1"
        const val CAPABILITY = "scoreup_sync"
        private const val TAG = "ScoreUpSync"
    }
}

