package com.scoreup.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scoreup.app.R
import com.scoreup.app.ui.common.ScreenScaffold
import com.scoreup.app.ui.common.formatClock
import com.scoreup.app.ui.common.sportEmoji
import com.scoreup.app.ui.common.teamName
import com.scoreup.app.ui.theme.ScoreDigits
import com.scoreup.app.ui.theme.toColor
import com.scoreup.core.MatchSummary
import com.scoreup.core.Side
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(items: List<MatchSummary>, onBack: () -> Unit, onRematch: (MatchSummary) -> Unit, onDelete: (String) -> Unit, onClear: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    ScreenScaffold(stringResource(R.string.history), onBack, actions = {
        if (items.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, stringResource(R.string.clear_history)) }
    }) { pad ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.history_empty), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(pad).testTag("history_list"),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = { it.matchId }) { m -> HistoryCard(m, onRematch, onDelete) }
                item { com.scoreup.app.ui.common.MadeWithLove() }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.clear_history)) },
        confirmButton = { TextButton(onClick = { onClear(); confirmClear = false }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun HistoryCard(m: MatchSummary, onRematch: (MatchSummary) -> Unit, onDelete: (String) -> Unit) {
    val winner = m.winner
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.testTag("history_item")) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sportEmoji(m.sport), fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(m.startedAtMs)),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.duration, formatClock((m.endedAtMs - m.startedAtMs).coerceAtLeast(0))),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamCell(m, Side.HOME, winner == Side.HOME, Modifier.weight(1f))
                Text("${m.homeScore}", style = ScoreDigits, fontSize = 40.sp)
                Text("  :  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${m.awayScore}", style = ScoreDigits, fontSize = 40.sp)
                TeamCell(m, Side.AWAY, winner == Side.AWAY, Modifier.weight(1f), end = true)
            }
            if (m.sets.isNotEmpty()) {
                Text(m.sets.joinToString("  ·  ") { "${it.home}-${it.away}" }, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { onDelete(m.matchId) }) { Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.delete)) }
                TextButton(onClick = { onRematch(m) }) { Icon(Icons.Rounded.Replay, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.play_again)) }
            }
        }
    }
}

@Composable
private fun TeamCell(m: MatchSummary, side: Side, won: Boolean, modifier: Modifier, end: Boolean = false) {
    val team = if (side == Side.HOME) m.home else m.away
    Row(modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (end) Arrangement.End else Arrangement.Start) {
        if (!end) Box(Modifier.size(12.dp).clip(CircleShape).background(team.color.toColor()))
        Text((if (won) "🏆 " else "") + teamName(team, side), fontWeight = if (won) FontWeight.Black else FontWeight.Medium,
            maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp))
        if (end) Box(Modifier.size(12.dp).clip(CircleShape).background(team.color.toColor()))
    }
}
