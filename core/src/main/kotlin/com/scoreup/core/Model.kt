package com.scoreup.core

import kotlinx.serialization.Serializable

@Serializable
enum class Side { HOME, AWAY;
    val other: Side get() = if (this == HOME) AWAY else HOME
}

@Serializable
enum class Sport { BASKETBALL, SOCCER, VOLLEYBALL, GENERIC }

/** How a sport is scored and timed. All values are user-overridable in match setup. */
@Serializable
data class Rules(
    /** Quick-add increments offered in the UI (e.g. +1/+2/+3 for basketball). */
    val pointOptions: List<Int>,
    /** Number of periods (quarters / halves). 0 = no periods. Ignored for set-based sports. */
    val periods: Int,
    /** Period length in minutes; 0 = no game clock limit (count up). */
    val periodMinutes: Int,
    /** Clock counts down from [periodMinutes] instead of up. */
    val clockCountsDown: Boolean,
    /** Set-based sports (volleyball): points reset per set; sets decide the match. */
    val setBased: Boolean = false,
    val setTarget: Int = 25,
    val finalSetTarget: Int = 15,
    val winBy: Int = 2,
    val setsToWin: Int = 3,
) {
    init {
        require(pointOptions.isNotEmpty() && pointOptions.all { it in 1..MAX_POINTS }) { "bad pointOptions" }
        require(periods in 0..20 && periodMinutes in 0..240) { "bad periods" }
        require(!setBased || (setTarget in 1..999 && finalSetTarget in 1..999 && winBy in 1..10 && setsToWin in 1..10)) { "bad set rules" }
    }

    companion object {
        const val MAX_POINTS = 100

        fun defaultsFor(sport: Sport): Rules = when (sport) {
            Sport.BASKETBALL -> Rules(listOf(1, 2, 3), periods = 4, periodMinutes = 10, clockCountsDown = true)
            Sport.SOCCER -> Rules(listOf(1), periods = 2, periodMinutes = 45, clockCountsDown = false)
            Sport.VOLLEYBALL -> Rules(listOf(1), periods = 0, periodMinutes = 0, clockCountsDown = false, setBased = true)
            Sport.GENERIC -> Rules(listOf(1, 2, 3), periods = 0, periodMinutes = 0, clockCountsDown = false)
        }
    }
}

@Serializable
data class TeamInfo(val name: String, val color: Long) {
    init { require(name.length <= MAX_NAME) { "name too long" } }
    companion object { const val MAX_NAME = 24 }
}

@Serializable
data class MatchConfig(
    val sport: Sport,
    val rules: Rules,
    val home: TeamInfo,
    val away: TeamInfo,
    /** Points added by a single tap of a team's big button (watch + phone). */
    val tapPoints: Int,
) {
    init { require(tapPoints in 1..Rules.MAX_POINTS) { "bad tapPoints" } }

    fun team(side: Side) = if (side == Side.HOME) home else away

    companion object {
        val HOME_COLORS = listOf(0xFFFF3D57, 0xFFFF8A00, 0xFFFFD60A, 0xFF8E5CFF, 0xFFFF4FD8, 0xFFFFFFFF)
        val AWAY_COLORS = listOf(0xFF00C2FF, 0xFF00E58F, 0xFF3D7BFF, 0xFF00F0E0, 0xFFB4FF39, 0xFF9AA4B2)
        val PALETTE = (HOME_COLORS + AWAY_COLORS)

        fun default(sport: Sport = Sport.BASKETBALL, homeName: String = "HOME", awayName: String = "AWAY"): MatchConfig {
            val rules = Rules.defaultsFor(sport)
            return MatchConfig(sport, rules, TeamInfo(homeName, HOME_COLORS[0]), TeamInfo(awayName, AWAY_COLORS[0]),
                tapPoints = rules.pointOptions.first().let { if (sport == Sport.BASKETBALL) 2 else it })
        }
    }
}
