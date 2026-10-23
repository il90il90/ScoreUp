package com.scoreup.core

import kotlinx.serialization.Serializable

/** Compact, immutable record of a played match for the history screen. */
@Serializable
data class MatchSummary(
    val matchId: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val sport: Sport,
    val home: TeamInfo,
    val away: TeamInfo,
    val homeScore: Int,
    val awayScore: Int,
    val sets: List<SetResult> = emptyList(),
    val periods: Int = 1,
    val finished: Boolean = false,
    val pointsLog: List<ScoringEntry> = emptyList(),
    /** Wearer's heart rate during the match, when the watch measured it. */
    val heart: HeartStats? = null,
) {
    val winner: Side?
        get() = if (sets.isNotEmpty()) {
            val h = sets.count { it.winner == Side.HOME }; val a = sets.size - h
            if (h > a) Side.HOME else if (a > h) Side.AWAY else null
        } else if (homeScore > awayScore) Side.HOME else if (awayScore > homeScore) Side.AWAY else null

    companion object {
        fun of(s: MatchState, endedAtMs: Long, heart: HeartStats? = null) = MatchSummary(
            s.matchId, s.createdAtMs.takeIf { it > 0 } ?: s.feed.firstOrNull()?.wallMs ?: endedAtMs, endedAtMs, s.config.sport, s.config.home, s.config.away,
            s.home, s.away, s.sets, s.period, s.finished, s.feed.takeLast(500), heart,
        )
    }
}

/**
 * Merges match summaries into a bounded history list. Re-archiving the same match id updates
 * it in place (e.g. late events arriving after a match was replaced), never duplicates it.
 */
object HistoryMerge {
    const val MAX = 200
    fun upsert(history: List<MatchSummary>, item: MatchSummary): List<MatchSummary> =
        (listOf(item) + history.filter { it.matchId != item.matchId }).sortedByDescending { it.startedAtMs }.take(MAX)
}
