package com.scoreup.core

import kotlinx.serialization.Serializable

@Serializable
data class SetResult(val home: Int, val away: Int) {
    val winner: Side get() = if (home > away) Side.HOME else Side.AWAY
}

/** Game clock. Elapsed time is derived on demand so it never drifts between devices. */
@Serializable
data class ClockState(val running: Boolean = false, val accumulatedMs: Long = 0, val runningSinceMs: Long = 0) {
    fun elapsedMs(nowMs: Long): Long = accumulatedMs + if (running) (nowMs - runningSinceMs).coerceAtLeast(0) else 0
}

/** One line in the scoring feed. */
@Serializable
data class ScoringEntry(val id: EventId, val side: Side, val points: Int, val homeAfter: Int, val awayAfter: Int, val period: Int, val wallMs: Long)

/** Fully derived, read-only view of a match. Produced by [MatchReducer.reduce]. */
@Serializable
data class MatchState(
    val matchId: String,
    val createdAtMs: Long,
    val config: MatchConfig,
    val home: Int = 0,
    val away: Int = 0,
    /** 1-based current period (quarter/half) or current set number for set-based sports. */
    val period: Int = 1,
    val sets: List<SetResult> = emptyList(),
    val clock: ClockState = ClockState(),
    val finished: Boolean = false,
    val feed: List<ScoringEntry> = emptyList(),
    val undoableIds: List<EventId> = emptyList(),
    val eventCount: Int = 0,
) {
    fun score(side: Side) = if (side == Side.HOME) home else away
    fun setsWon(side: Side) = sets.count { it.winner == side }
    val canUndo: Boolean get() = undoableIds.isNotEmpty()
    val hasActivity: Boolean get() = feed.isNotEmpty() || home != 0 || away != 0 || sets.isNotEmpty()

    val leader: Side?
        get() = if (config.rules.setBased && sets.isNotEmpty() && setsWon(Side.HOME) != setsWon(Side.AWAY)) {
            if (setsWon(Side.HOME) > setsWon(Side.AWAY)) Side.HOME else Side.AWAY
        } else when {
            home > away -> Side.HOME
            away > home -> Side.AWAY
            else -> null
        }

    /** Remaining (count-down) or elapsed (count-up) clock text value in ms. */
    fun displayClockMs(nowMs: Long): Long {
        val elapsed = clock.elapsedMs(nowMs)
        val limit = config.rules.periodMinutes * 60_000L
        return if (config.rules.clockCountsDown && limit > 0) (limit - elapsed).coerceAtLeast(0) else elapsed
    }

    fun periodExpired(nowMs: Long): Boolean {
        val limit = config.rules.periodMinutes * 60_000L
        return limit > 0 && clock.elapsedMs(nowMs) >= limit
    }
}

/** Deterministic fold from an event set to [MatchState]. Same events => same state on every device. */
object MatchReducer {
    val ORDER: Comparator<MatchEvent> = compareBy<MatchEvent>({ it.lamport }, { it.id.device }, { it.id.seq })

    fun reduce(header: MatchHeader, events: Collection<MatchEvent>): MatchState {
        val sorted = events.sortedWith(ORDER)
        val revoked = HashSet<EventId>()
        // Undo is only honoured for revocable events, and an undo itself cannot be undone.
        val revocable = sorted.filter { isRevocable(it.payload) }.associateBy { it.id }
        for (e in sorted) {
            val p = e.payload
            if (p is Payload.Undo && p.target in revocable) revoked += p.target
        }

        var s = MatchState(header.matchId, header.createdAtMs, header.initialConfig, eventCount = sorted.size)
        val feed = ArrayList<ScoringEntry>()
        val undoable = ArrayList<EventId>()
        for (e in sorted) {
            if (e.id in revoked) continue
            val p = e.payload
            s = when (p) {
                is Payload.ScoreAdded -> {
                    if (s.finished) s else {
                        val next = applyPoints(s, p.side, p.points)
                        feed += ScoringEntry(e.id, p.side, p.points, next.homeAfterAdd, next.awayAfterAdd, s.period, e.wallMs)
                        undoable += e.id
                        next.state
                    }
                }
                is Payload.ScoreSet -> {
                    undoable += e.id
                    val v = p.value.coerceIn(0, 9999)
                    if (p.side == Side.HOME) s.copy(home = v) else s.copy(away = v)
                }
                is Payload.TeamUpdated -> {
                    val t = s.config.team(p.side)
                    val nt = t.copy(name = p.name?.take(TeamInfo.MAX_NAME) ?: t.name, color = p.color ?: t.color)
                    s.copy(config = if (p.side == Side.HOME) s.config.copy(home = nt) else s.config.copy(away = nt))
                }
                is Payload.ConfigUpdated -> s.copy(config = p.config)
                is Payload.TapPointsUpdated -> s.copy(config = s.config.copy(tapPoints = p.tapPoints.coerceIn(1, Rules.MAX_POINTS)))
                Payload.ClockStarted -> if (s.clock.running || s.finished) s else s.copy(clock = s.clock.copy(running = true, runningSinceMs = e.wallMs))
                Payload.ClockPaused -> if (!s.clock.running) s else s.copy(clock = ClockState(false, s.clock.elapsedMs(e.wallMs), 0))
                Payload.ClockReset -> s.copy(clock = ClockState())
                Payload.PeriodAdvanced -> {
                    undoable += e.id
                    val r = s.config.rules
                    when {
                        s.finished -> s
                        r.setBased -> {
                            // Manually close the current set (e.g. custom rules) if someone leads.
                            if (s.home == s.away) s else closeSet(s)
                        }
                        r.periods > 0 && s.period >= r.periods -> s.copy(clock = ClockState(), finished = true)
                        else -> s.copy(period = s.period + 1, clock = ClockState())
                    }
                }
                Payload.Finished -> s.copy(finished = true, clock = ClockState(false, s.clock.elapsedMs(e.wallMs), 0))
                Payload.Reopened -> s.copy(finished = false)
                is Payload.Undo -> s
            }
        }
        return s.copy(feed = feed, undoableIds = undoable)
    }

    fun isRevocable(p: Payload) = p is Payload.ScoreAdded || p is Payload.ScoreSet || p is Payload.PeriodAdvanced

    private class Applied(val state: MatchState, val homeAfterAdd: Int, val awayAfterAdd: Int)

    private fun applyPoints(s: MatchState, side: Side, points: Int): Applied {
        val pts = points.coerceIn(-Rules.MAX_POINTS, Rules.MAX_POINTS)
        val home = if (side == Side.HOME) (s.home + pts).coerceAtLeast(0) else s.home
        val away = if (side == Side.AWAY) (s.away + pts).coerceAtLeast(0) else s.away
        var next = s.copy(home = home, away = away)
        val r = s.config.rules
        if (r.setBased) {
            val decidingSet = s.sets.size + 1 == r.setsToWin * 2 - 1
            val target = if (decidingSet) r.finalSetTarget else r.setTarget
            val hi = maxOf(home, away); val lo = minOf(home, away)
            if (hi >= target && hi - lo >= r.winBy) next = closeSet(next)
        }
        return Applied(next, home, away)
    }

    private fun closeSet(s: MatchState): MatchState {
        val sets = s.sets + SetResult(s.home, s.away)
        val r = s.config.rules
        val won = sets.count { it.winner == Side.HOME } >= r.setsToWin || sets.count { it.winner == Side.AWAY } >= r.setsToWin
        return s.copy(home = 0, away = 0, sets = sets, period = if (won) s.period else s.period + 1, finished = won,
            clock = if (won) ClockState(false, s.clock.accumulatedMs, 0) else s.clock)
    }
}
