package com.scoreup.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Single source of truth on a device. All mutations are synchronous and serialized; every
 * local action becomes one persisted [MatchEvent] before it is broadcast, so a crash or
 * disconnect can never lose or duplicate a point.
 *
 * Thread-safe: all public methods synchronize on the engine.
 */
class ScoreEngine(
    val deviceId: String,
    private val store: SessionStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val appVersionCode: Long = 0,
    defaultConfig: MatchConfig = MatchConfig.default(),
    private val keepPrevious: Int = 3,
) {
    /** Called with every outgoing message; the transport delivers it to connected peers. */
    @Volatile var outbound: (SyncMessage) -> Unit = {}

    /** Called once for a match that is being replaced by a newer one and had activity. */
    @Volatile var onArchive: (MatchState) -> Unit = {}

    private var snapshot: SessionSnapshot
    private val _state: MutableStateFlow<MatchState>
    val state: StateFlow<MatchState>

    init {
        snapshot = runCatching { store.load() }.getOrNull() ?: freshSnapshot(defaultConfig, 0)
        _state = MutableStateFlow(snapshot.active.state())
        state = _state.asStateFlow()
    }

    val activeLog: MatchLog @Synchronized get() = snapshot.active

    // ---------------------------------------------------------------- local actions

    fun addPoints(side: Side, points: Int = state.value.config.tapPoints) =
        record(Payload.ScoreAdded(side, points.coerceIn(-Rules.MAX_POINTS, Rules.MAX_POINTS)))

    fun setScore(side: Side, value: Int) = record(Payload.ScoreSet(side, value.coerceIn(0, 9999)))

    /** Undoes the most recent revocable event (from any device). Returns false when nothing to undo. */
    @Synchronized
    fun undo(): Boolean {
        val target = state.value.undoableIds.lastOrNull() ?: return false
        record(Payload.Undo(target)); return true
    }

    /** Undoes the most recent point scored by [side]. */
    @Synchronized
    fun undoLast(side: Side): Boolean {
        val target = state.value.feed.lastOrNull { it.side == side }?.id ?: return false
        record(Payload.Undo(target)); return true
    }

    fun renameTeam(side: Side, name: String) = record(Payload.TeamUpdated(side, name = name.trim().take(TeamInfo.MAX_NAME)))
    fun setTeamColor(side: Side, color: Long) = record(Payload.TeamUpdated(side, color = color))
    fun setTapPoints(points: Int) = record(Payload.TapPointsUpdated(points.coerceIn(1, Rules.MAX_POINTS)))
    fun updateConfig(config: MatchConfig) = record(Payload.ConfigUpdated(config))
    fun startClock() = record(Payload.ClockStarted)
    fun pauseClock() = record(Payload.ClockPaused)
    fun resetClock() = record(Payload.ClockReset)
    fun toggleClock() = if (state.value.clock.running) pauseClock() else startClock()
    fun nextPeriod() = record(Payload.PeriodAdvanced)
    fun finish() = record(Payload.Finished)
    fun reopen() = record(Payload.Reopened)

    /** Starts a brand new match (archiving the current one). Defaults to the current teams/config. */
    @Synchronized
    fun newMatch(config: MatchConfig = state.value.config) {
        val lamport = snapshot.lamport + 1
        val header = MatchHeader(UUID.randomUUID().toString(), clock(), lamport, deviceId, config)
        switchTo(MatchLog(header), lamport)
        broadcastSummary(reply = false)
    }

    /** Resets scores but keeps teams and settings: equivalent to a new match with the same config. */
    fun resetMatch() = newMatch(state.value.config)

    @Synchronized
    private fun record(payload: Payload): MatchEvent {
        val log = snapshot.active
        val lamport = maxOf(snapshot.lamport, log.maxLamport()) + 1
        val event = MatchEvent(EventId(deviceId, log.nextSeqFor(deviceId)), lamport, clock(), payload)
        commit(snapshot.copy(active = log.merge(listOf(event)), lamport = lamport))
        outbound(SyncMessage.Events(deviceId, log.header, listOf(event)))
        return event
    }

    // ---------------------------------------------------------------- sync

    fun summary(reply: Boolean = false): SyncMessage.Summary = synchronized(this) { summaryOf(snapshot.active, reply) }

    private fun summaryOf(log: MatchLog, reply: Boolean = false) =
        SyncMessage.Summary(deviceId, log.header, log.vector(), appVersionCode, reply)

    fun broadcastSummary(reply: Boolean = true) = outbound(summary(reply))

    /** Applies a message from a peer and sends whatever reply is needed to converge. */
    @Synchronized
    fun receive(message: SyncMessage) {
        if (message.from == deviceId) return
        when (message) {
            is SyncMessage.Events -> {
                val log = adoptHeader(message.header)
                if (log != null) {
                    val merged = log.merge(message.events.filter { it.id.device != deviceId || !log.contains(it.id) })
                    if (merged !== log) {
                        val lamport = maxOf(snapshot.lamport, merged.maxLamport())
                        commit(updateLog(merged, lamport))
                        // If events arrived past a gap, ask the peer to fill it in.
                        val hasGap = message.events.any { (merged.vector()[it.id.device] ?: 0) < it.id.seq }
                        if (hasGap && merged.matchId == snapshot.active.matchId) broadcastSummary(reply = true)
                    }
                }
            }
            is SyncMessage.Summary -> {
                val log = adoptHeader(message.header) ?: return
                val active = snapshot.active
                sendMissing(log, message.vector)
                val mine = log.vector()
                val peerHasMore = message.vector.any { (dev, seq) -> seq > (mine[dev] ?: 0) }
                // Ask for whatever the peer has that we lack (also for a previous match, so
                // points scored offline on an old match are recovered into history).
                if (peerHasMore || (message.reply && log.matchId == active.matchId)) outbound(summaryOf(log))
                // Peer is on an older match: move it to ours.
                if (log.matchId != active.matchId) outbound(summary(reply = false))
            }
        }
    }

    private fun sendMissing(log: MatchLog, peerVector: Map<String, Long>) {
        val missing = log.missingFor(peerVector)
        if (missing.isNotEmpty()) missing.chunked(200).forEach { outbound(SyncMessage.Events(deviceId, log.header, it)) }
    }

    /** Returns the log for [header] (switching the active match if the header is newer), or null if unknown+older. */
    private fun adoptHeader(header: MatchHeader): MatchLog? {
        val active = snapshot.active
        if (header.matchId == active.matchId) return active
        snapshot.previous.firstOrNull { it.matchId == header.matchId }?.let { return it }
        if (header.isNewerThan(active.header)) {
            switchTo(MatchLog(header), maxOf(snapshot.lamport, header.lamport))
            return snapshot.active
        }
        // Older match we no longer track: keep a log so late events are still accepted and archived.
        val log = MatchLog(header)
        commit(snapshot.copy(previous = (listOf(log) + snapshot.previous).take(keepPrevious)))
        return log
    }

    private fun updateLog(log: MatchLog, lamport: Long): SessionSnapshot =
        if (log.matchId == snapshot.active.matchId) snapshot.copy(active = log, lamport = lamport)
        else snapshot.copy(previous = snapshot.previous.map { if (it.matchId == log.matchId) log else it }, lamport = lamport)
            .also { s -> s.previous.firstOrNull { it.matchId == log.matchId }?.let { prev -> archiveIfNeeded(prev) } }

    private fun switchTo(log: MatchLog, lamport: Long) {
        val old = snapshot.active
        val prev = (listOf(old) + snapshot.previous.filter { it.matchId != old.matchId && it.matchId != log.matchId }).take(keepPrevious)
        commit(SessionSnapshot(log, prev, lamport))
        archiveIfNeeded(old)
    }

    private fun archiveIfNeeded(log: MatchLog) {
        val s = log.state()
        if (s.hasActivity) runCatching { onArchive(s) }
    }

    private fun commit(next: SessionSnapshot) {
        snapshot = next
        store.save(next)
        _state.value = next.active.state()
    }

    /**
     * A fresh install starts on the shared, deterministic [INITIAL_MATCH_ID] (lamport 0), so two new
     * devices are on the same match from the start, and a newly installed watch never displaces a
     * match already in progress on the phone (any real match has a higher lamport).
     */
    private fun freshSnapshot(config: MatchConfig, lamport: Long) =
        SessionSnapshot(MatchLog(MatchHeader(INITIAL_MATCH_ID, 0, lamport, "", config)), lamport = lamport)

    companion object {
        const val INITIAL_MATCH_ID = "initial"
    }
}
