package com.scoreup.app.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scoreup.app.R
import com.scoreup.app.ui.common.ScreenScaffold
import com.scoreup.app.ui.common.sportEmoji
import com.scoreup.app.ui.common.sportName
import com.scoreup.app.ui.common.teamName
import com.scoreup.app.ui.scoreboard.NumberDialog
import com.scoreup.app.ui.theme.toColor
import com.scoreup.core.MatchConfig
import com.scoreup.core.Rules
import com.scoreup.core.Side
import com.scoreup.core.Sport
import com.scoreup.core.TeamInfo

/**
 * Configure sport, teams, colors and rules. "Start match" begins a new match (current one is
 * archived); "Apply" changes the running match in place (scores kept).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MatchSetupScreen(current: MatchConfig, hasActivity: Boolean, onBack: () -> Unit, onStart: (MatchConfig) -> Unit, onApply: (MatchConfig) -> Unit) {
    var config by remember { mutableStateOf(current) }
    var editNumber by remember { mutableStateOf<NumberField?>(null) }

    ScreenScaffold(stringResource(R.string.setup_title), onBack) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Section(stringResource(R.string.sport)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sport.entries.forEach { sport ->
                        val selected = config.sport == sport
                        Surface(
                            onClick = {
                                val rules = Rules.defaultsFor(sport)
                                val tap = 1
                                config = config.copy(sport = sport, rules = rules, tapPoints = tap)
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.testTag("sport_${sport.name.lowercase()}"),
                        ) {
                            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(sportEmoji(sport), fontSize = 28.sp)
                                Text(sportName(sport), fontWeight = FontWeight.Bold,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            Section(stringResource(R.string.teams)) {
                TeamEditor(Side.HOME, config.home, MatchConfig.PALETTE) { config = config.copy(home = it) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { config = config.copy(home = config.away, away = config.home) }, modifier = Modifier.testTag("swap")) {
                        Icon(Icons.Rounded.SwapVert, null); Text(stringResource(R.string.swap_teams))
                    }
                }
                TeamEditor(Side.AWAY, config.away, MatchConfig.PALETTE) { config = config.copy(away = it) }
            }

            Section(stringResource(R.string.points_per_tap)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val opts = (config.rules.pointOptions + listOf(1, 2, 3)).distinct().sorted()
                    opts.forEach { p ->
                        FilterChip(selected = config.tapPoints == p, onClick = { config = config.copy(tapPoints = p) },
                            label = { Text("+$p", fontWeight = FontWeight.Black) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
                    }
                    FilterChip(selected = config.tapPoints !in opts, onClick = { editNumber = NumberField.TAP },
                        label = { Text(if (config.tapPoints !in opts) "+${config.tapPoints}" else stringResource(R.string.custom)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
                }
            }

            Section(stringResource(R.string.rules)) {
                val r = config.rules
                if (r.setBased) {
                    NumberRow(stringResource(R.string.set_target), r.setTarget) { editNumber = NumberField.SET_TARGET }
                    NumberRow(stringResource(R.string.final_set_target), r.finalSetTarget) { editNumber = NumberField.FINAL_SET }
                    NumberRow(stringResource(R.string.sets_to_win), r.setsToWin) { editNumber = NumberField.SETS_TO_WIN }
                    NumberRow(stringResource(R.string.win_by), r.winBy) { editNumber = NumberField.WIN_BY }
                } else {
                    NumberRow(stringResource(R.string.periods), r.periods) { editNumber = NumberField.PERIODS }
                    NumberRow(stringResource(R.string.period_minutes), r.periodMinutes) { editNumber = NumberField.MINUTES }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.countdown_clock), Modifier.weight(1f))
                        Switch(checked = r.clockCountsDown, onCheckedChange = { config = config.copy(rules = r.copy(clockCountsDown = it)) })
                    }
                }
            }

            Button(onClick = { onStart(config) }, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("start_match"), shape = RoundedCornerShape(18.dp)) {
                Text(stringResource(R.string.start_match), fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
            if (hasActivity) {
                OutlinedButton(onClick = { onApply(config) }, modifier = Modifier.fillMaxWidth().testTag("apply_changes"), shape = RoundedCornerShape(18.dp)) {
                    Text(stringResource(R.string.apply_changes))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    editNumber?.let { field ->
        val r = config.rules
        val (title, value, range) = when (field) {
            NumberField.TAP -> Triple(stringResource(R.string.custom_points), config.tapPoints, 1..Rules.MAX_POINTS)
            NumberField.PERIODS -> Triple(stringResource(R.string.periods), r.periods, 0..20)
            NumberField.MINUTES -> Triple(stringResource(R.string.period_minutes), r.periodMinutes, 0..240)
            NumberField.SET_TARGET -> Triple(stringResource(R.string.set_target), r.setTarget, 1..999)
            NumberField.FINAL_SET -> Triple(stringResource(R.string.final_set_target), r.finalSetTarget, 1..999)
            NumberField.SETS_TO_WIN -> Triple(stringResource(R.string.sets_to_win), r.setsToWin, 1..10)
            NumberField.WIN_BY -> Triple(stringResource(R.string.win_by), r.winBy, 1..10)
        }
        NumberDialog(title, value, range, onDismiss = { editNumber = null }) { v ->
            config = when (field) {
                NumberField.TAP -> config.copy(tapPoints = v)
                NumberField.PERIODS -> config.copy(rules = r.copy(periods = v))
                NumberField.MINUTES -> config.copy(rules = r.copy(periodMinutes = v))
                NumberField.SET_TARGET -> config.copy(rules = r.copy(setTarget = v))
                NumberField.FINAL_SET -> config.copy(rules = r.copy(finalSetTarget = v))
                NumberField.SETS_TO_WIN -> config.copy(rules = r.copy(setsToWin = v))
                NumberField.WIN_BY -> config.copy(rules = r.copy(winBy = v))
            }
            editNumber = null
        }
    }
}

private enum class NumberField { TAP, PERIODS, MINUTES, SET_TARGET, FINAL_SET, SETS_TO_WIN, WIN_BY }

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun NumberRow(label: String, value: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            Text(value.toString(), fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamEditor(side: Side, team: TeamInfo, palette: List<Long>, onChange: (TeamInfo) -> Unit) {
    val shown = teamName(team, side)
    val isDefault = team.name == "HOME" || team.name == "AWAY"
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(team.color.toColor()))
                Spacer(Modifier.size(10.dp))
                OutlinedTextField(
                    value = if (isDefault) "" else team.name,
                    onValueChange = { onChange(team.copy(name = it.take(TeamInfo.MAX_NAME))) },
                    placeholder = { Text(shown) }, label = { Text(stringResource(R.string.team_name)) }, singleLine = true,
                    modifier = Modifier.weight(1f).testTag("team_name_${side.name.lowercase()}"),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                palette.forEach { c ->
                    val sel = c == team.color
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(c.toColor())
                            .border(if (sel) 3.dp else 0.dp, if (sel) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                            .clickable { onChange(team.copy(color = c)) }
                            .widthIn(min = 36.dp),
                    )
                }
            }
        }
    }
}
