package com.scoreup.wear

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.HorizontalPageIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.LocalTextStyle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PageIndicatorState
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Confirmation
import androidx.wear.compose.material.dialog.Dialog
import com.scoreup.core.MatchState
import com.scoreup.core.MatchSummary
import com.scoreup.core.ScoreEngine
import com.scoreup.core.Side
import com.scoreup.core.TeamInfo
import com.scoreup.shared.WearSync
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val WearColors = Colors(
    primary = Color(0xFFFFD60A), onPrimary = Color.Black,
    secondary = Color(0xFF00C2FF), background = Color.Black,
    surface = Color(0xFF151A2E), onSurface = Color.White, onSurfaceVariant = Color(0xFFB4BCD6),
)
private val Neon = Color(0xFFFFD60A)
private val Muted = Color(0xFFB4BCD6)
private val Dim = Color(0xFF8A93B2)
private val HeartRed = Color(0xFFFF4D6D)
private val Online = Color(0xFF00E58F)

/** Score, controls, saved scores. */
private const val PAGES = 3

/**
 * Lays digits out by their glyph height (about 0.72 em) rather than the font's line height, which
 * adds ~45% empty space around digits; that space is what the centre strip needs on a round screen.
 */
private fun Modifier.digitBox(fontPx: Float) = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val baseline = p[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: p.height
    val cap = fontPx * 0.72f
    val h = (fontPx * 0.86f).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(p.width.coerceIn(constraints.minWidth, constraints.maxWidth), h) { p.place(0, ((h + cap) / 2f - baseline).roundToInt()) }
}

/** A reading older than this is hidden (the sensor lost contact or the screen slept). */
private const val HEART_FRESH_MS = 15_000L

/** Heart-rate state for the UI: the latest reading, and whether measuring is possible, on and allowed. */
data class HeartUi(
    val reading: WearSync.HeartReading? = null,
    val available: Boolean = false,
    val enabled: Boolean = false,
    val permitted: Boolean = false,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WearScoreApp(
    state: MatchState, engine: ScoreEngine, haptics: Haptics, phoneConnected: Boolean, onInteraction: () -> Unit,
    updateAvailable: Boolean = false,
    sync: WearSync.Diagnostics = WearSync.Diagnostics(), onSyncNow: () -> Unit = {},
    heart: HeartUi = HeartUi(), onHeartToggle: (Boolean) -> Unit = {},
    saved: List<MatchSummary> = emptyList(), onSaveScore: () -> Unit = {}, onClearSaved: () -> Unit = {},
    initialPage: Int = 0,
) {
    MaterialTheme(colors = WearColors) {
        val pager = rememberPagerState(initialPage) { PAGES }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> ScorePage(state, engine, haptics, phoneConnected, heart.reading, onInteraction)
                    1 -> ControlsPage(state, engine, haptics, phoneConnected, onInteraction, updateAvailable, sync, onSyncNow, heart, onHeartToggle, onSaveScore)
                    else -> SavedPage(saved, onClearSaved, onInteraction)
                }
            }
            val indicator = remember(pager) {
                object : PageIndicatorState {
                    override val pageOffset: Float get() = pager.currentPageOffsetFraction
                    override val selectedPage: Int get() = pager.currentPage
                    override val pageCount: Int get() = PAGES
                }
            }
            HorizontalPageIndicator(indicator, Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp))
        }
    }
}

@Composable
private fun ScorePage(state: MatchState, engine: ScoreEngine, haptics: Haptics, phoneConnected: Boolean, heart: WearSync.HeartReading?, onInteraction: () -> Unit) {
    val width = LocalConfiguration.current.screenWidthDp
    val unit = width / 192f
    var confirmRestart by remember { mutableStateOf(false) }

    fun score(side: Side) {
        onInteraction()
        if (state.finished) { haptics.undo(); return }
        engine.addPoints(side)
        haptics.score(state.config.tapPoints)
    }

    Column(Modifier.fillMaxSize()) {
        TeamHalf(state, Side.HOME, unit, top = true, onTap = { score(Side.HOME) },
            onLong = { onInteraction(); if (engine.undoLast(Side.HOME)) haptics.undo() }, modifier = Modifier.weight(1f))
        CenterStrip(state, engine, haptics, phoneConnected, heart, unit, onInteraction,
            onRestart = { onInteraction(); haptics.tick(); confirmRestart = true })
        TeamHalf(state, Side.AWAY, unit, top = false, onTap = { score(Side.AWAY) },
            onLong = { onInteraction(); if (engine.undoLast(Side.AWAY)) haptics.undo() }, modifier = Modifier.weight(1f))
    }
    RestartDialog(confirmRestart, onDismiss = { confirmRestart = false }) {
        onInteraction(); engine.resetMatch(); haptics.tick(); confirmRestart = false
    }
}

@Composable
private fun TeamHalf(state: MatchState, side: Side, unit: Float, top: Boolean, onTap: () -> Unit, onLong: () -> Unit, modifier: Modifier) {
    val team = state.config.team(side)
    val color = Color(team.color.toInt())
    val fg = if (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue > 0.62) Color(0xFF0B1020) else Color.White
    val name = teamLabel(team, side)
    val score = state.score(side)
    val bump = remember { Animatable(1f) }
    LaunchedEffect(score) { bump.snapTo(1.18f); bump.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) }

    Box(
        modifier.fillMaxWidth().background(color)
            .combinedClickable(onClick = onTap, onLongClick = onLong)
            .semantics { contentDescription = "$name $score" }
            .testTag("watch_${side.name.lowercase()}"),
        contentAlignment = if (top) Alignment.BottomCenter else Alignment.TopCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = (4 * unit).dp)) {
            if (!top) ScoreText(score, fg, unit, bump.value, side)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.config.rules.setBased) {
                    Text("${state.setsWon(side)} ", color = fg.copy(alpha = 0.8f), fontSize = (12 * unit).sp, fontWeight = FontWeight.Bold)
                }
                Text(name.uppercase(), color = fg, fontSize = (13 * unit).sp, fontWeight = FontWeight.Black, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = (34 * unit).dp), letterSpacing = 1.sp)
            }
            if (top) ScoreText(score, fg, unit, bump.value, side)
        }
    }
}

@Composable
private fun ScoreText(score: Int, color: Color, unit: Float, scale: Float, side: Side) {
    AnimatedContent(score, transitionSpec = {
        val up = targetState > initialState
        (slideInVertically(tween(160)) { if (up) it / 2 else -it / 2 } + fadeIn(tween(160))) togetherWith (slideOutVertically(tween(120)) { if (up) -it / 2 else it / 2 } + fadeOut(tween(120)))
    }, label = "s") { v ->
        val size = ((if (v >= 100) 40 else 50) * unit).sp
        val px = with(LocalDensity.current) { size.toPx() }
        Text(v.toString(), color = color, fontSize = size, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
            modifier = Modifier.scale(scale).digitBox(px).testTag("watch_score_${side.name.lowercase()}"))
    }
}

@Composable
private fun CenterStrip(
    state: MatchState, engine: ScoreEngine, haptics: Haptics, phoneConnected: Boolean, heart: WearSync.HeartReading?,
    unit: Float, onInteraction: () -> Unit, onRestart: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.clock.running) { now = System.currentTimeMillis(); while (state.clock.running) { delay(250); now = System.currentTimeMillis() } }
    var wallNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { wallNow = System.currentTimeMillis(); delay(1000 - wallNow % 1000) } }
    var buzzed by remember(state.period) { mutableStateOf(false) }
    LaunchedEffect(now) { if (state.clock.running && state.periodExpired(now) && !buzzed) { buzzed = true; haptics.buzzer() } }

    var showGameTime by remember { mutableStateOf(false) }
    val period = periodShort(state)
    val clockUsed = state.config.rules.periodMinutes > 0 || state.clock.running || state.clock.accumulatedMs > 0
    val bpm = heart?.takeIf { wallNow - it.atMs < HEART_FRESH_MS }?.bpm

    Row(
        Modifier.fillMaxWidth().height((46 * unit).dp).background(Color.Black).padding(horizontal = (14 * unit).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Undo: tap = undo one point; long-press = restart (with confirmation).
        StripButton(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.undo), unit, "watch_undo",
            onClick = { onInteraction(); if (engine.undo()) haptics.undo() }, onLongClick = onRestart)

        // Game clock (tap = start/pause; long-press = game time) above the time of day and heart rate.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f).fillMaxHeight().combinedClickable(
                onClick = { onInteraction(); engine.toggleClock(); haptics.tick() },
                onLongClick = { onInteraction(); haptics.tick(); showGameTime = true },
            ).testTag("watch_clock"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (period.isNotEmpty()) {
                    Text(period, fontSize = (9 * unit).sp, fontWeight = FontWeight.Bold, color = Neon, maxLines = 1)
                    Spacer(Modifier.width((3 * unit).dp))
                }
                Text(formatClock(state.displayClockMs(now), state.config.rules.clockCountsDown), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    fontSize = (15 * unit).sp, maxLines = 1, color = if (state.clock.running) Neon else if (clockUsed) Color.White else Dim,
                    modifier = Modifier.testTag("watch_game_clock"))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size((5 * unit).dp).clip(CircleShape).background(if (phoneConnected) Online else Color(0xFF59607A)))
                Spacer(Modifier.width((3 * unit).dp))
                Text(wallClock(wallNow), fontSize = (10 * unit).sp, color = Muted, fontWeight = FontWeight.Bold, maxLines = 1,
                    style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"), modifier = Modifier.testTag("watch_time"))
                if (bpm != null) {
                    Spacer(Modifier.width((5 * unit).dp))
                    Icon(Icons.Rounded.Favorite, stringResource(R.string.heart_rate), Modifier.size((9 * unit).dp), tint = HeartRed)
                    Spacer(Modifier.width((2 * unit).dp))
                    Text("$bpm", fontSize = (10 * unit).sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.testTag("watch_heart"))
                }
            }
        }

        StripButton(Icons.Rounded.RestartAlt, stringResource(R.string.restart), unit, "watch_restart", onClick = onRestart)
    }

    if (showGameTime) GameTimeDialog(state.config.rules.periodMinutes, onDismiss = { showGameTime = false }) { m ->
        onInteraction(); engine.setGameMinutes(m); haptics.tick(); showGameTime = false
    }
}

@Composable
private fun StripButton(icon: ImageVector, label: String, unit: Float, tag: String, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Box(
        Modifier.size((30 * unit).dp).clip(CircleShape).background(Color(0xFF1A2240))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick).testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, Modifier.size((18 * unit).dp), tint = Color.White) }
}

/** Restarting archives the match, so the score lands in Saved scores before the board resets. */
@Composable
private fun RestartDialog(show: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(showDialog = show, onDismissRequest = onDismiss) {
        Alert(
            title = { Text(stringResource(R.string.restart_confirm), textAlign = TextAlign.Center) },
            negativeButton = { Button(onClick = onDismiss, colors = ButtonDefaults.secondaryButtonColors()) { Text("✕") } },
            positiveButton = { Button(onClick = onConfirm, modifier = Modifier.testTag("restart_confirm")) { Text("✓") } },
        )
    }
}

/** Game-time picker on the watch: minute stepper, 0 = count up (no limit). */
@Composable
private fun GameTimeDialog(current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var minutes by remember { mutableStateOf(current.coerceIn(0, 180)) }
    Dialog(showDialog = true, onDismissRequest = onDismiss) {
        Alert(
            title = { Text(stringResource(R.string.game_time), textAlign = TextAlign.Center) },
            negativeButton = { Button(onClick = onDismiss, colors = ButtonDefaults.secondaryButtonColors()) { Text("✕") } },
            positiveButton = { Button(onClick = { onPick(minutes) }) { Text("✓") } },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                CompactChip(onClick = { minutes = (minutes - 1).coerceAtLeast(0) }, label = { Text("−", fontSize = 18.sp) }, colors = ChipDefaults.secondaryChipColors())
                Text(if (minutes == 0) stringResource(R.string.count_up) else stringResource(R.string.minutes_fmt, minutes),
                    modifier = Modifier.padding(horizontal = 10.dp), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                CompactChip(onClick = { minutes = (minutes + 1).coerceAtMost(180) }, label = { Text("+", fontSize = 18.sp) }, colors = ChipDefaults.secondaryChipColors())
            }
        }
    }
}

private fun wallClock(ms: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return "%02d:%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE), c.get(java.util.Calendar.SECOND))
}

@Composable
private fun periodShort(s: MatchState): String {
    if (s.finished) return stringResource(R.string.final_label)
    val r = s.config.rules
    return when {
        r.setBased -> stringResource(R.string.set_short, s.period)
        r.periods == 4 -> "Q${s.period}"
        r.periods == 2 -> stringResource(R.string.half_short, s.period)
        r.periods > 0 -> "P${s.period}"
        else -> ""
    }
}

/** Default team names are stored as neutral "HOME"/"AWAY" and shown localized. */
@Composable
private fun teamLabel(team: TeamInfo, side: Side): String = when (team.name) {
    "HOME", "" -> stringResource(if (side == Side.HOME) R.string.home else R.string.away)
    "AWAY" -> stringResource(R.string.away)
    else -> team.name
}

fun formatClock(ms: Long, roundUp: Boolean): String {
    val t = if (roundUp) (ms + 999) / 1000 else ms / 1000
    return "%02d:%02d".format(t / 60, t % 60)
}

@Composable
private fun ControlsPage(
    state: MatchState, engine: ScoreEngine, haptics: Haptics, phoneConnected: Boolean, onInteraction: () -> Unit, updateAvailable: Boolean,
    sync: WearSync.Diagnostics, onSyncNow: () -> Unit, heart: HeartUi, onHeartToggle: (Boolean) -> Unit, onSaveScore: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    var confirmRestart by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    ScalingLazyColumn(Modifier.fillMaxSize().testTag("watch_controls"), state = listState) {
        if (updateAvailable) item {
            Text(stringResource(R.string.update_on_phone), color = Neon, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag("watch_update_notice"))
        }
        item { ListHeader { Text(stringResource(R.string.controls)) } }
        item {
            Chip(
                label = { Text(stringResource(R.string.save_score)) }, icon = { Icon(Icons.Rounded.Save, null) },
                enabled = state.hasActivity, onClick = { onInteraction(); onSaveScore(); haptics.tick(); showSaved = true },
                modifier = Modifier.fillMaxWidth().testTag("watch_save"), colors = ChipDefaults.primaryChipColors(),
            )
        }
        item {
            Chip(
                label = { Text(stringResource(if (state.clock.running) R.string.pause else R.string.start)) },
                icon = { Icon(if (state.clock.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null) },
                onClick = { onInteraction(); engine.toggleClock(); haptics.tick() }, modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        item {
            Chip(
                label = { Text(stringResource(R.string.reset_clock)) }, icon = { Icon(Icons.Rounded.TimerOff, null) },
                enabled = state.clock.running || state.clock.accumulatedMs > 0,
                onClick = { onInteraction(); engine.resetClock(); haptics.tick() }, modifier = Modifier.fillMaxWidth().testTag("watch_reset_clock"),
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        if (state.config.rules.periods > 0 || state.config.rules.setBased) item {
            Chip(
                label = { Text(stringResource(if (state.config.rules.setBased) R.string.end_set else R.string.next_period)) },
                icon = { Icon(Icons.Rounded.SkipNext, null) }, enabled = !state.finished,
                onClick = { onInteraction(); engine.nextPeriod(); haptics.tick() }, modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        item {
            Chip(
                label = { Text(stringResource(R.string.undo)) }, icon = { Icon(Icons.AutoMirrored.Rounded.Undo, null) },
                enabled = state.canUndo, onClick = { onInteraction(); if (engine.undo()) haptics.undo() }, modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        item {
            Chip(
                label = { Text(stringResource(R.string.restart)) }, icon = { Icon(Icons.Rounded.RestartAlt, null) },
                onClick = { confirmRestart = true }, modifier = Modifier.fillMaxWidth().testTag("watch_reset"),
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        if (heart.available) item {
            val on = heart.enabled && heart.permitted
            ToggleChip(
                checked = on, onCheckedChange = { onInteraction(); onHeartToggle(it) },
                label = { Text(stringResource(R.string.heart_rate)) },
                secondaryLabel = {
                    Text(when {
                        !heart.enabled -> stringResource(R.string.off)
                        !heart.permitted -> stringResource(R.string.heart_allow)
                        else -> heart.reading?.let { stringResource(R.string.heart_bpm, it.bpm) } ?: stringResource(R.string.heart_measuring)
                    }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                appIcon = { Icon(Icons.Rounded.Favorite, null, tint = HeartRed) },
                toggleControl = { Switch(checked = on) },
                modifier = Modifier.fillMaxWidth().testTag("watch_heart_toggle"),
            )
        }
        item {
            Text(
                stringResource(if (phoneConnected) R.string.phone_connected else R.string.phone_offline),
                color = if (phoneConnected) Online else Muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
        item {
            Chip(
                label = { Text(stringResource(R.string.sync_now)) }, onClick = { onInteraction(); onSyncNow(); haptics.tick() },
                modifier = Modifier.fillMaxWidth().testTag("watch_sync_now"), colors = ChipDefaults.secondaryChipColors(),
            )
        }
        item {
            Text("📡 ${sync.connectedNodes}/${sync.scoreUpPeers} · ↑${sync.messagesSent + sync.dataItemsPublished} ↓${sync.messagesReceived + sync.dataItemsReceived}",
                color = Dim, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        sync.lastError?.let { err -> item { Text(err, color = Color(0xFFFF8A8A), fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) } }
        item { Text("v${BuildConfig.VERSION_NAME}", color = Color(0xFF59607A), fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        item { Text(stringResource(R.string.made_with_love), color = Dim, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    }
    RestartDialog(confirmRestart, onDismiss = { confirmRestart = false }) {
        onInteraction(); engine.resetMatch(); haptics.tick(); confirmRestart = false
    }
    Dialog(showDialog = showSaved, onDismissRequest = { showSaved = false }) {
        Confirmation(
            onTimeout = { showSaved = false }, durationMillis = 1500,
            icon = { Icon(Icons.Rounded.CheckCircle, null, Modifier.size(40.dp), tint = Online) },
        ) { Text(stringResource(R.string.saved), textAlign = TextAlign.Center) }
    }
}

@Composable
private fun SavedPage(saved: List<MatchSummary>, onClear: () -> Unit, onInteraction: () -> Unit) {
    val listState = rememberScalingLazyListState()
    var confirmClear by remember { mutableStateOf(false) }
    ScalingLazyColumn(Modifier.fillMaxSize().testTag("watch_saved"), state = listState) {
        item { ListHeader { Text(stringResource(R.string.saved_scores)) } }
        if (saved.isEmpty()) item {
            Text(stringResource(R.string.no_saved_scores), color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
        }
        items(saved, key = { it.matchId }) { m -> SavedScore(m) }
        if (saved.isNotEmpty()) item {
            Chip(
                label = { Text(stringResource(R.string.clear_saved)) }, icon = { Icon(Icons.Rounded.DeleteSweep, null) },
                onClick = { onInteraction(); confirmClear = true }, modifier = Modifier.fillMaxWidth().testTag("watch_clear_saved"),
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
    }
    Dialog(showDialog = confirmClear, onDismissRequest = { confirmClear = false }) {
        Alert(
            title = { Text(stringResource(R.string.clear_saved_confirm), textAlign = TextAlign.Center) },
            negativeButton = { Button(onClick = { confirmClear = false }, colors = ButtonDefaults.secondaryButtonColors()) { Text("✕") } },
            positiveButton = { Button(onClick = { onClear(); confirmClear = false }) { Text("✓") } },
        )
    }
}

/** One saved score: result in team colors (winner bright), teams, when and how long, heart rate. */
@Composable
private fun SavedScore(m: MatchSummary) {
    val winner = m.winner
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF151A2E))
            .padding(horizontal = 10.dp, vertical = 6.dp).testTag("watch_saved_item"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamDot(m.home.color)
            Text("${m.homeScore}", fontSize = 24.sp, fontWeight = FontWeight.Black, color = if (winner == Side.AWAY) Dim else Color.White,
                modifier = Modifier.padding(horizontal = 6.dp))
            Text(":", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Dim)
            Text("${m.awayScore}", fontSize = 24.sp, fontWeight = FontWeight.Black, color = if (winner == Side.HOME) Dim else Color.White,
                modifier = Modifier.padding(horizontal = 6.dp))
            TeamDot(m.away.color)
        }
        Text("${teamLabel(m.home, Side.HOME)} – ${teamLabel(m.away, Side.AWAY)}", fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (m.sets.isNotEmpty()) Text(m.sets.joinToString("  ") { "${it.home}-${it.away}" }, fontSize = 10.sp, color = Dim, maxLines = 1)
        Text(savedWhen(m), fontSize = 10.sp, color = Dim, maxLines = 1)
        m.heart?.let { h ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Favorite, null, Modifier.size(10.dp), tint = HeartRed)
                Spacer(Modifier.width(3.dp))
                Text(stringResource(R.string.heart_avg_max, h.avg, h.max), fontSize = 10.sp, color = Muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TeamDot(color: Long) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(color.toInt())))
}

private fun savedWhen(m: MatchSummary): String {
    val at = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(m.endedAtMs))
    return "$at · ${formatClock((m.endedAtMs - m.startedAtMs).coerceAtLeast(0), roundUp = false)}"
}
