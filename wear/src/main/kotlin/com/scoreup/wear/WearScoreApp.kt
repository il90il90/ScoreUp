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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.HorizontalPageIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PageIndicatorState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import androidx.wear.compose.material.Colors
import com.scoreup.core.MatchState
import com.scoreup.core.ScoreEngine
import com.scoreup.core.Side
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val WearColors = Colors(
    primary = Color(0xFFFFD60A), onPrimary = Color.Black,
    secondary = Color(0xFF00C2FF), background = Color.Black,
    surface = Color(0xFF151A2E), onSurface = Color.White, onSurfaceVariant = Color(0xFFB4BCD6),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WearScoreApp(state: MatchState, engine: ScoreEngine, haptics: Haptics, phoneConnected: Boolean, onInteraction: () -> Unit, updateAvailable: Boolean = false) {
    MaterialTheme(colors = WearColors) {
        val pager = rememberPagerState { 2 }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                if (page == 0) ScorePage(state, engine, haptics, phoneConnected, onInteraction)
                else ControlsPage(state, engine, haptics, phoneConnected, onInteraction, updateAvailable)
            }
            val indicator = remember(pager) {
                object : PageIndicatorState {
                    override val pageOffset: Float get() = pager.currentPageOffsetFraction
                    override val selectedPage: Int get() = pager.currentPage
                    override val pageCount: Int get() = 2
                }
            }
            HorizontalPageIndicator(indicator, Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp))
        }
    }
}

@Composable
private fun ScorePage(state: MatchState, engine: ScoreEngine, haptics: Haptics, phoneConnected: Boolean, onInteraction: () -> Unit) {
    val width = LocalConfiguration.current.screenWidthDp
    val unit = width / 192f
    val focus = remember { FocusRequester() }
    var rotaryAcc by remember { mutableStateOf(0f) }
    val options = remember(state.config.rules) { (state.config.rules.pointOptions + listOf(1, 2, 3)).distinct().sorted() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    fun score(side: Side) {
        onInteraction()
        if (state.finished) { haptics.undo(); return }
        engine.addPoints(side)
        haptics.score(state.config.tapPoints)
    }

    Column(
        Modifier.fillMaxSize()
            // Rotating the crown/bezel changes points-per-tap.
            .onRotaryScrollEvent { e ->
                rotaryAcc += e.verticalScrollPixels
                if (kotlin.math.abs(rotaryAcc) > 60f) {
                    val idx = options.indexOf(state.config.tapPoints).coerceAtLeast(0)
                    val next = options[(idx + if (rotaryAcc > 0) 1 else options.size - 1) % options.size]
                    engine.setTapPoints(next); haptics.tick(); onInteraction(); rotaryAcc = 0f
                }
                true
            }
            .focusRequester(focus).focusable(),
    ) {
        TeamHalf(state, Side.HOME, unit, top = true, onTap = { score(Side.HOME) },
            onLong = { onInteraction(); if (engine.undoLast(Side.HOME)) haptics.undo() }, modifier = Modifier.weight(1f))
        CenterStrip(state, engine, haptics, phoneConnected, unit, options, onInteraction)
        TeamHalf(state, Side.AWAY, unit, top = false, onTap = { score(Side.AWAY) },
            onLong = { onInteraction(); if (engine.undoLast(Side.AWAY)) haptics.undo() }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TeamHalf(state: MatchState, side: Side, unit: Float, top: Boolean, onTap: () -> Unit, onLong: () -> Unit, modifier: Modifier) {
    val team = state.config.team(side)
    val color = Color(team.color.toInt())
    val fg = if (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue > 0.62) Color(0xFF0B1020) else Color.White
    val name = when (team.name) { "HOME", "" -> stringResource(if (side == Side.HOME) R.string.home else R.string.away); "AWAY" -> stringResource(R.string.away); else -> team.name }
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
        Text(v.toString(), color = color, fontSize = ((if (v >= 100) 40 else 50) * unit).sp, fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center, modifier = Modifier.scale(scale).testTag("watch_score_${side.name.lowercase()}"))
    }
}

@Composable
private fun CenterStrip(state: MatchState, engine: ScoreEngine, haptics: Haptics, phoneConnected: Boolean, unit: Float, options: List<Int>, onInteraction: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.clock.running) { now = System.currentTimeMillis(); while (state.clock.running) { delay(250); now = System.currentTimeMillis() } }
    var buzzed by remember(state.period) { mutableStateOf(false) }
    LaunchedEffect(now) { if (state.clock.running && state.periodExpired(now) && !buzzed) { buzzed = true; haptics.buzzer() } }

    Row(
        Modifier.fillMaxWidth().height((38 * unit).dp).background(Color.Black).padding(horizontal = (22 * unit).dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier.size((30 * unit).dp).clip(CircleShape).background(Color(0xFF1A2240))
                .combinedClickable(onClick = { onInteraction(); if (engine.undo()) haptics.undo() }).testTag("watch_undo"),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.undo), Modifier.size((18 * unit).dp), tint = Color.White) }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.combinedClickable(onClick = { onInteraction(); engine.toggleClock(); haptics.tick() }).testTag("watch_clock"),
        ) {
            val showClock = state.config.rules.periodMinutes > 0 || state.clock.running || state.clock.accumulatedMs > 0
            if (showClock) {
                Text(formatClock(state.displayClockMs(now), state.config.rules.clockCountsDown), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    fontSize = (14 * unit).sp, color = if (state.clock.running) Color(0xFFFFD60A) else Color.White)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size((6 * unit).dp).clip(CircleShape).background(if (phoneConnected) Color(0xFF00E58F) else Color(0xFF59607A)))
                Spacer(Modifier.width(3.dp))
                Text(periodShort(state), fontSize = (10 * unit).sp, color = Color(0xFFB4BCD6), fontWeight = FontWeight.Bold)
            }
        }

        Box(
            Modifier.clip(CircleShape).background(Color(0xFFFFD60A))
                .combinedClickable(onClick = {
                    onInteraction()
                    val idx = options.indexOf(state.config.tapPoints)
                    engine.setTapPoints(options[(idx + 1).mod(options.size)]); haptics.tick()
                })
                .padding(horizontal = (8 * unit).dp, vertical = (3 * unit).dp).testTag("watch_tap_points"),
            contentAlignment = Alignment.Center,
        ) { Text("+${state.config.tapPoints}", color = Color.Black, fontWeight = FontWeight.Black, fontSize = (14 * unit).sp) }
    }
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
        else -> "SCOREUP"
    }
}

fun formatClock(ms: Long, roundUp: Boolean): String {
    val t = if (roundUp) (ms + 999) / 1000 else ms / 1000
    return "%02d:%02d".format(t / 60, t % 60)
}

@Composable
private fun ControlsPage(state: MatchState, engine: ScoreEngine, haptics: Haptics, phoneConnected: Boolean, onInteraction: () -> Unit, updateAvailable: Boolean) {
    val listState = rememberScalingLazyListState()
    var confirmReset by remember { mutableStateOf(false) }
    ScalingLazyColumn(Modifier.fillMaxSize().testTag("watch_controls"), state = listState) {
        if (updateAvailable) item {
            Text(stringResource(R.string.update_on_phone), color = Color(0xFFFFD60A), fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag("watch_update_notice"))
        }
        item { ListHeader { Text(stringResource(R.string.controls)) } }
        item {
            Chip(
                label = { Text(stringResource(if (state.clock.running) R.string.pause else R.string.start)) },
                icon = { Icon(if (state.clock.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null) },
                onClick = { onInteraction(); engine.toggleClock(); haptics.tick() }, modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors(),
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
        item { ListHeader { Text(stringResource(R.string.points_per_tap)) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (state.config.rules.pointOptions + listOf(1, 2, 3)).distinct().sorted().take(4).forEach { p ->
                    CompactChip(
                        label = { Text("+$p", fontWeight = FontWeight.Black) },
                        onClick = { onInteraction(); engine.setTapPoints(p); haptics.tick() },
                        colors = if (state.config.tapPoints == p) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                    )
                }
            }
        }
        item {
            Chip(
                label = { Text(stringResource(R.string.new_match)) }, icon = { Icon(Icons.Rounded.Refresh, null) },
                onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth().testTag("watch_reset"),
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        item {
            Text(
                stringResource(if (phoneConnected) R.string.phone_connected else R.string.phone_offline),
                color = if (phoneConnected) Color(0xFF00E58F) else Color(0xFFB4BCD6), fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
        item { Text("v${BuildConfig.VERSION_NAME}", color = Color(0xFF59607A), fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        item { Text(stringResource(R.string.made_with_love), color = Color(0xFF8A93B2), fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    }
    Dialog(showDialog = confirmReset, onDismissRequest = { confirmReset = false }) {
        Alert(
            title = { Text(stringResource(R.string.new_match_confirm), textAlign = TextAlign.Center) },
            negativeButton = { androidx.wear.compose.material.Button(onClick = { confirmReset = false }, colors = androidx.wear.compose.material.ButtonDefaults.secondaryButtonColors()) { Text("✕") } },
            positiveButton = { androidx.wear.compose.material.Button(onClick = { onInteraction(); engine.resetMatch(); haptics.tick(); confirmReset = false }) { Text("✓") } },
        )
    }
}
