package com.scoreup.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.scoreup.app.R
import com.scoreup.core.MatchState
import com.scoreup.core.Side
import com.scoreup.core.Sport
import com.scoreup.core.TeamInfo

/** Default team names are stored as neutral "HOME"/"AWAY" and shown localized. */
@Composable
fun teamName(team: TeamInfo, side: Side): String = when (team.name) {
    "HOME", "" -> if (side == Side.HOME) stringResource(R.string.home) else stringResource(R.string.away)
    "AWAY" -> if (side == Side.AWAY) stringResource(R.string.away) else stringResource(R.string.home)
    else -> team.name
}

@Composable
fun sportName(sport: Sport): String = stringResource(
    when (sport) {
        Sport.BASKETBALL -> R.string.sport_basketball
        Sport.SOCCER -> R.string.sport_soccer
        Sport.VOLLEYBALL -> R.string.sport_volleyball
        Sport.GENERIC -> R.string.sport_generic
    }
)

fun sportEmoji(sport: Sport) = when (sport) {
    Sport.BASKETBALL -> "🏀"; Sport.SOCCER -> "⚽"; Sport.VOLLEYBALL -> "🏐"; Sport.GENERIC -> "🏆"
}

@Composable
fun periodLabel(s: MatchState): String {
    if (s.finished) return stringResource(R.string.final_label)
    val r = s.config.rules
    return when {
        r.setBased -> stringResource(R.string.period_set, s.period)
        r.periods == 4 -> stringResource(R.string.period_quarter, s.period)
        r.periods == 2 -> stringResource(R.string.period_half, s.period)
        r.periods > 0 -> stringResource(R.string.period_generic, s.period)
        else -> ""
    }
}

/** Count-down clocks round up (never show 00:00 early), count-up clocks round down. */
fun formatClock(ms: Long, roundUp: Boolean = false): String {
    val totalSec = if (roundUp) (ms + 999) / 1000 else ms / 1000
    val h = totalSec / 3600; val m = (totalSec % 3600) / 60; val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
