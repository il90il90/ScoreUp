package com.scoreup.app.ui.scoreboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scoreup.app.R
import com.scoreup.app.ui.common.HeartBadge
import com.scoreup.app.ui.common.formatClock
import com.scoreup.app.ui.common.periodLabel
import com.scoreup.app.ui.common.teamName
import com.scoreup.app.ui.theme.ClockDigits
import com.scoreup.app.ui.theme.toColor
import com.scoreup.core.MatchState
import com.scoreup.core.Rules
import com.scoreup.core.ScoringEntry
import com.scoreup.core.Side
import kotlinx.coroutines.delay

enum class Nav { SETUP, HISTORY, WATCH, TV, SETTINGS, LANGUAGE }

data class WatchStatus(val connected: Boolean, val name: String?)

@Composable
fun ScoreboardScreen(
    state: MatchState,
    controller: ScoreController,
    watch: WatchStatus,
    tvActive: Boolean,
    watchUpdateAvailable: Boolean = false,
    heartRate: Int? = null,
    onSaveScore: () -> Unit = {},
    onNavigate: (Nav) -> Unit,
) {
    // A ticking clock only while the game clock runs (no wasted recompositions otherwise).
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.clock.running) {
        now = System.currentTimeMillis()
        while (state.clock.running) { delay(200); now = System.currentTimeMillis() }
    }
    // Period buzzer: once per expiry, then stop the clock.
    LaunchedEffect(state.clock.running, state.period, now >= 0 && state.periodExpired(now)) {
        if (state.clock.running && state.periodExpired(now)) { controller.buzzer(); controller.engine.pauseClock() }
    }

    var editing by remember { mutableStateOf<Side?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().testTag("scoreboard")
    ) {
        val landscape = maxWidth > maxHeight
        val wide = maxWidth >= 840.dp
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            TopBar(state, now, watch, tvActive, heartRate, controller, onSaveScore, onNavigate)
            if (watchUpdateAvailable) {
                Surface(
                    onClick = { onNavigate(Nav.WATCH) }, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag("watch_update_banner"),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Watch, null, tint = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.installer_update_available), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (landscape) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TeamPanel(state, Side.HOME, { controller.add(Side.HOME) }, { editing = Side.HOME }, { controller.undoSide(Side.HOME) }, Modifier.weight(1f).fillMaxHeight())
                    CenterColumn(state, now, controller, onReset = { confirmReset = true }, Modifier.width(if (wide) 220.dp else 150.dp).fillMaxHeight())
                    TeamPanel(state, Side.AWAY, { controller.add(Side.AWAY) }, { editing = Side.AWAY }, { controller.undoSide(Side.AWAY) }, Modifier.weight(1f).fillMaxHeight())
                    if (wide) RecentPlays(state, Modifier.width(240.dp).fillMaxHeight())
                }
            } else {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TeamPanel(state, Side.HOME, { controller.add(Side.HOME) }, { editing = Side.HOME }, { controller.undoSide(Side.HOME) }, Modifier.weight(1f).fillMaxWidth())
                    TapPointsBar(state, controller)
                    TeamPanel(state, Side.AWAY, { controller.add(Side.AWAY) }, { editing = Side.AWAY }, { controller.undoSide(Side.AWAY) }, Modifier.weight(1f).fillMaxWidth())
                }
                Spacer(Modifier.height(10.dp))
                ControlBar(state, controller, onReset = { confirmReset = true })
            }
        }
        WinnerBanner(state, controller, Modifier.align(Alignment.Center))
    }

    editing?.let { side ->
        TeamOptionsDialog(state, side, onDismiss = { editing = null }) { name, score ->
            controller.rename(side, name)
            score?.let { controller.setScore(side, it) }
            editing = null
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.reset_confirm_title)) },
            text = { Text(stringResource(R.string.reset_confirm_body)) },
            confirmButton = { TextButton(onClick = { controller.reset(); confirmReset = false }, modifier = Modifier.testTag("confirm_reset")) { Text(stringResource(R.string.reset_scores)) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun TopBar(
    state: MatchState, now: Long, watch: WatchStatus, tvActive: Boolean, heartRate: Int?,
    controller: ScoreController, onSaveScore: () -> Unit, onNavigate: (Nav) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("SCOREUP", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, letterSpacing = 3.sp, maxLines = 1)
            val p = periodLabel(state)
            if (p.isNotEmpty() || heartRate != null) Row(verticalAlignment = Alignment.CenterVertically) {
                if (p.isNotEmpty()) Text(p, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, maxLines = 1,
                    modifier = Modifier.testTag("period"))
                if (p.isNotEmpty() && heartRate != null) Spacer(Modifier.width(10.dp))
                // Live heart rate of whoever wears the watch.
                if (heartRate != null) HeartBadge(heartRate, 16.sp, Modifier.testTag("heart_rate"), color = MaterialTheme.colorScheme.onBackground)
            }
        }
        var editMinutes by remember { mutableStateOf(false) }
        ClockChip(state, now, onClick = { controller.toggleClock() }, onLongClick = { editMinutes = true })
        if (editMinutes) NumberDialog(stringResource(R.string.game_time_minutes), state.config.rules.periodMinutes, 0..240, onDismiss = { editMinutes = false }) {
            controller.setGameMinutes(it); editMinutes = false
        }
        Spacer(Modifier.width(4.dp))
        Box(contentAlignment = Alignment.TopEnd) {
            IconButton(onClick = { onNavigate(Nav.WATCH) }, modifier = Modifier.testTag("nav_watch")) {
                Icon(Icons.Rounded.Watch, stringResource(R.string.watch), tint = if (watch.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.padding(10.dp).size(9.dp).clip(CircleShape).background(if (watch.connected) Color(0xFF00E58F) else Color(0xFF59607A)))
        }
        IconButton(onClick = { onNavigate(Nav.TV) }, modifier = Modifier.testTag("nav_tv")) {
            Icon(Icons.Rounded.Tv, stringResource(R.string.tv), tint = if (tvActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        var menu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.testTag("menu")) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.menu)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                MenuItem(Icons.Rounded.Tune, stringResource(R.string.setup_title), "menu_setup") { menu = false; onNavigate(Nav.SETUP) }
                MenuItem(Icons.Rounded.Save, stringResource(R.string.save_score), "menu_save") { menu = false; onSaveScore() }
                MenuItem(Icons.Rounded.History, stringResource(R.string.history), "menu_history") { menu = false; onNavigate(Nav.HISTORY) }
                MenuItem(Icons.Rounded.Watch, stringResource(R.string.installer_title), "menu_watch") { menu = false; onNavigate(Nav.WATCH) }
                MenuItem(Icons.Rounded.Tv, stringResource(R.string.cast_title), "menu_tv") { menu = false; onNavigate(Nav.TV) }
                if (state.finished) MenuItem(Icons.Rounded.Refresh, stringResource(R.string.reopen_match), "menu_reopen") { menu = false; controller.reopen() }
                else MenuItem(Icons.Rounded.Flag, stringResource(R.string.finish_match), "menu_finish") { menu = false; controller.finish() }
                MenuItem(Icons.Rounded.Translate, stringResource(R.string.language_menu), "menu_language") { menu = false; onNavigate(Nav.LANGUAGE) }
                MenuItem(Icons.Rounded.Settings, stringResource(R.string.settings), "menu_settings") { menu = false; onNavigate(Nav.SETTINGS) }
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, tag: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = onClick, modifier = Modifier.testTag(tag))
}

@Composable
fun ClockChip(state: MatchState, now: Long, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    val r = state.config.rules
    val running = state.clock.running
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(1f, 0.55f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (running) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick).testTag("clock"),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text(
                formatClock(state.displayClockMs(now), roundUp = r.clockCountsDown), style = ClockDigits, fontSize = 20.sp,
                color = if (running) MaterialTheme.colorScheme.primary.copy(alpha = if (state.periodExpired(now)) pulse.value else 1f) else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** One chip showing the current points per tap (default +1); tap it to change the value. */
@Composable
private fun TapPointsChip(state: MatchState, controller: ScoreController) {
    var editing by remember { mutableStateOf(false) }
    Surface(
        onClick = { editing = true }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.testTag("tap_points_edit"),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.points_per_tap), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text("+${state.config.tapPoints}", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("tap_points_value"))
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.Edit, stringResource(R.string.edit_points), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (editing) NumberDialog(stringResource(R.string.points_per_tap), state.config.tapPoints, 1..Rules.MAX_POINTS, onDismiss = { editing = false }) {
        controller.setTapPoints(it); editing = false
    }
}

@Composable
private fun TapPointsBar(state: MatchState, controller: ScoreController) {
    Row(Modifier.fillMaxWidth().testTag("tap_points"), horizontalArrangement = Arrangement.Center) { TapPointsChip(state, controller) }
}

@Composable
private fun CenterColumn(state: MatchState, now: Long, controller: ScoreController, onReset: () -> Unit, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        if (state.config.rules.setBased) {
            Text(stringResource(R.string.sets_score, state.setsWon(Side.HOME), state.setsWon(Side.AWAY)), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
        }
        TapPointsChip(state, controller)
        ControlButton(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.undo), enabled = state.canUndo, tag = "undo") { controller.undo() }
        ControlButton(if (state.clock.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            stringResource(if (state.clock.running) R.string.pause else R.string.start), primary = true, tag = "play") { controller.toggleClock() }
        if (state.config.rules.periods > 0 || state.config.rules.setBased) {
            ControlButton(Icons.Rounded.SkipNext, stringResource(if (state.config.rules.setBased) R.string.next_set else R.string.next_period), enabled = !state.finished, tag = "next") { controller.nextPeriod() }
        }
        ControlButton(Icons.Rounded.Refresh, stringResource(R.string.reset_scores), tag = "reset", onClick = onReset)
    }
}

@Composable
private fun ControlBar(state: MatchState, controller: ScoreController, onReset: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            ControlButton(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.undo), enabled = state.canUndo, tag = "undo") { controller.undo() }
            ControlButton(if (state.clock.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                stringResource(if (state.clock.running) R.string.pause else R.string.start), primary = true, tag = "play") { controller.toggleClock() }
            if (state.config.rules.periods > 0 || state.config.rules.setBased) {
                ControlButton(Icons.Rounded.SkipNext, stringResource(if (state.config.rules.setBased) R.string.next_set else R.string.next_period), enabled = !state.finished, tag = "next") { controller.nextPeriod() }
            }
            ControlButton(Icons.Rounded.Refresh, stringResource(R.string.reset_scores), tag = "reset", onClick = onReset)
        }
    }
}

@Composable
private fun ControlButton(icon: ImageVector, label: String, enabled: Boolean = true, primary: Boolean = false, tag: String, onClick: () -> Unit) {
    val bg = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.testTag(tag)) {
        Surface(onClick = onClick, enabled = enabled, shape = CircleShape, color = bg.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(if (primary) 60.dp else 50.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, label, tint = fg.copy(alpha = if (enabled) 1f else 0.5f), modifier = Modifier.size(if (primary) 32.dp else 26.dp)) }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun RecentPlays(state: MatchState, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp)) {
            Text(stringResource(R.string.recent_plays), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.feed.takeLast(40).reversed(), key = { it.id.toString() }) { e -> PlayRow(state, e) }
            }
        }
    }
}

@Composable
private fun PlayRow(state: MatchState, e: ScoringEntry) {
    val team = state.config.team(e.side)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(team.color.toColor()))
        Spacer(Modifier.width(8.dp))
        Text("${teamName(team, e.side)} +${e.points}", modifier = Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.Bold)
        Text("${e.homeAfter}–${e.awayAfter}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WinnerBanner(state: MatchState, controller: ScoreController, modifier: Modifier) {
    AnimatedVisibility(state.finished, modifier, enter = fadeIn() + scaleIn(initialScale = 0.6f), exit = fadeOut()) {
        val leader = state.leader
        val pulse = rememberInfiniteTransition(label = "win").animateFloat(1f, 1.06f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "s")
        Surface(
            shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), tonalElevation = 8.dp, shadowElevation = 16.dp,
            modifier = Modifier.padding(24.dp).testTag("winner_banner"),
        ) {
            Column(Modifier.padding(horizontal = 28.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.SportsScore, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                Text(
                    if (leader == null) stringResource(R.string.draw) else stringResource(R.string.winner_is, teamName(state.config.team(leader), leader)),
                    style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
                    color = leader?.let { state.config.team(it).color.toColor() } ?: MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.scale(pulse.value),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { controller.reopen() }) { Text(stringResource(R.string.reopen_match)) }
                    TextButton(onClick = { controller.reset() }, modifier = Modifier.testTag("rematch")) { Text(stringResource(R.string.play_again)) }
                }
            }
        }
    }
}

@Composable
private fun TeamOptionsDialog(state: MatchState, side: Side, onDismiss: () -> Unit, onSave: (name: String, score: Int?) -> Unit) {
    val team = state.config.team(side)
    val isDefault = team.name == "HOME" || team.name == "AWAY"
    var name by remember { mutableStateOf(if (isDefault) "" else team.name) }
    var scoreText by remember { mutableStateOf(state.score(side).toString()) }
    val shown = teamName(team, side)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(shown) },
        text = {
            Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(24) }, singleLine = true,
                    label = { Text(stringResource(R.string.team_name)) }, placeholder = { Text(shown) },
                    modifier = Modifier.testTag("team_name_field"),
                )
                OutlinedTextField(
                    value = scoreText, onValueChange = { v -> scoreText = v.filter { it.isDigit() }.take(4) }, singleLine = true,
                    label = { Text(stringResource(R.string.edit_score)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.testTag("number_input"),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), scoreText.toIntOrNull()) }, modifier = Modifier.testTag("number_save")) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun NumberDialog(title: String, initial: Int, range: IntRange, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.toString()) }
    val value = text.toIntOrNull()
    val valid = value != null && value in range
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { text = ((value ?: initial) - 1).coerceIn(range).toString() }) { Text("−", fontSize = 28.sp) }
                    OutlinedTextField(
                        value = text, onValueChange = { v -> text = v.filter { it.isDigit() }.take(4) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = !valid,
                        textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center),
                        modifier = Modifier.width(120.dp).testTag("number_input"),
                    )
                    TextButton(onClick = { text = ((value ?: initial) + 1).coerceIn(range).toString() }) { Text("+", fontSize = 28.sp) }
                }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(value!!) }, modifier = Modifier.testTag("number_save")) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        modifier = Modifier.clickable(enabled = false) {},
    )
}
