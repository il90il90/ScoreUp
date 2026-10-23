package com.scoreup.app.ui.tv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.scoreup.app.ui.common.HeartBadge
import com.scoreup.app.ui.common.formatClock
import com.scoreup.app.ui.common.periodLabel
import com.scoreup.app.ui.common.teamName
import com.scoreup.app.ui.theme.Arcade
import com.scoreup.app.ui.theme.ClockDigits
import com.scoreup.app.ui.theme.ScoreDigits
import com.scoreup.app.ui.theme.onColor
import com.scoreup.app.ui.theme.toColor
import com.scoreup.core.MatchState
import com.scoreup.core.Side
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val Night = Color(0xFF04060D)
private val NightHi = Color(0xFF0B1124)

private val TvDigits = ScoreDigits.copy(letterSpacing = (-0.02).em)

/** Digits are about cap-height tall (Roboto: 0.71 em); the font's line adds ~45% empty space around them. */
private const val DIGIT_HEIGHT_EM = 0.72f
private const val DIGIT_BOX_EM = 0.86f

/**
 * Lays digits out by their glyph height rather than the font's line height: reports a box
 * [DIGIT_BOX_EM] x the font size tall with the digits centred in it, so a score can fill its panel.
 * (Android keeps every line at least as tall as the font, even when a smaller lineHeight is set.)
 */
private fun Modifier.digitBox(fontPx: Float) = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val baseline = p[FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: p.height
    val cap = fontPx * DIGIT_HEIGHT_EM
    val h = (fontPx * DIGIT_BOX_EM).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(p.width.coerceIn(constraints.minWidth, constraints.maxWidth), h) { p.place(0, ((h + cap) / 2f - baseline).roundToInt()) }
}

/** Everything is sized from one unit (1/16 of a 16:9 frame), independent of the font-size setting. */
private class TvScale(val unit: Float, private val fontScale: Float) {
    fun dp(f: Float) = (unit * f).dp
    fun sp(f: Float) = (unit * f / fontScale).sp
}

/**
 * Arena-style, display-only scoreboard for TVs / external displays (always dark, landscape).
 * The scores fill their team panels; the header carries the period and game clock, the time of
 * day and the watch wearer's heart rate.
 */
@Composable
fun TvScoreboard(state: MatchState, modifier: Modifier = Modifier, heartRate: Int? = null) {
    BoxWithConstraints(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(NightHi, Night))).testTag("tv_scoreboard")) {
        val s = TvScale(minOf(maxWidth.value / 16f, maxHeight.value / 9f), LocalDensity.current.fontScale)
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val home = state.config.home.color.toColor()
        val away = state.config.away.color.toColor()
        // Soft team-colored stage lights behind each panel.
        Box(Modifier.fillMaxSize().drawBehind {
            val r = size.maxDimension * 0.5f
            fun glow(c: Color, x: Float) = drawRect(Brush.radialGradient(listOf(c.copy(alpha = 0.22f), Color.Transparent), Offset(size.width * x, size.height * 0.7f), r))
            glow(home, if (rtl) 0.76f else 0.24f)
            glow(away, if (rtl) 0.24f else 0.76f)
        })
        Column(Modifier.fillMaxSize().padding(horizontal = s.dp(0.35f), vertical = s.dp(0.25f))) {
            TvHeader(state, heartRate, s)
            Spacer(Modifier.height(s.dp(0.22f)))
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(s.dp(0.25f))) {
                TvTeam(state, Side.HOME, s, Modifier.weight(1f).fillMaxHeight())
                TvTeam(state, Side.AWAY, s, Modifier.weight(1f).fillMaxHeight())
            }
            if (state.config.rules.setBased && state.sets.isNotEmpty()) {
                Text(
                    state.sets.joinToString("     ") { "${it.home}–${it.away}" }, color = Arcade.Muted, fontWeight = FontWeight.Bold,
                    fontSize = s.sp(0.4f), maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = s.dp(0.15f)),
                )
            }
        }
    }
}

@Composable
private fun TvHeader(state: MatchState, heartRate: Int?, s: TvScale) {
    Row(Modifier.fillMaxWidth().height(s.dp(0.95f)), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Text("SCOREUP", color = Arcade.Neon, fontWeight = FontWeight.Black, fontSize = s.sp(0.42f), letterSpacing = s.sp(0.07f), maxLines = 1)
        }
        MatchClock(state, s)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(s.dp(0.3f), Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            if (heartRate != null) HeartBadge(heartRate, s.sp(0.44f), Modifier.testTag("tv_heart"))
            TimeOfDay(s)
            LiveBadge(s)
        }
    }
}

/** Period and game clock in one pill, centred over the two panels. */
@Composable
private fun MatchClock(state: MatchState, s: TvScale) {
    val r = state.config.rules
    val period = periodLabel(state)
    val showClock = r.periodMinutes > 0 || state.clock.running || state.clock.accumulatedMs > 0
    if (period.isEmpty() && !showClock) return
    val now by rememberTicker(250, active = state.clock.running)
    val shape = RoundedCornerShape(s.dp(0.24f))
    Row(
        Modifier.clip(shape).background(Arcade.Panel).border(1.dp, Color(0xFF2E3A63), shape)
            .padding(horizontal = s.dp(0.32f), vertical = s.dp(0.06f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (period.isNotEmpty()) {
            Text(period, color = if (state.finished) Arcade.Neon else Color.White, fontWeight = FontWeight.Black, fontSize = s.sp(0.42f),
                maxLines = 1, modifier = Modifier.testTag("tv_period"))
        }
        if (period.isNotEmpty() && showClock) Spacer(Modifier.width(s.dp(0.28f)))
        if (showClock) {
            Text(formatClock(state.displayClockMs(now), r.clockCountsDown), style = ClockDigits, fontSize = s.sp(0.62f), maxLines = 1,
                color = if (state.clock.running) Arcade.Neon else Color.White, modifier = Modifier.testTag("tv_clock"))
        }
    }
}

@Composable
private fun TimeOfDay(s: TvScale) {
    val context = LocalContext.current
    val now by rememberTicker(60_000)
    val text = remember(now) { android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(now)) }
    Text(text, style = ClockDigits, color = Color(0xFFC9D0E8), fontSize = s.sp(0.42f), maxLines = 1, modifier = Modifier.testTag("tv_time"))
}

@Composable
private fun LiveBadge(s: TvScale) {
    val blink = rememberInfiniteTransition(label = "live").animateFloat(1f, 0.25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Row(
        Modifier.clip(CircleShape).background(Arcade.Hot).padding(horizontal = s.dp(0.22f), vertical = s.dp(0.06f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(s.dp(0.16f)).graphicsLayer { alpha = blink.value }.clip(CircleShape).background(Color.White))
        Spacer(Modifier.width(s.dp(0.1f)))
        Text("LIVE", color = Color.White, fontWeight = FontWeight.Black, fontSize = s.sp(0.3f), maxLines = 1)
    }
}

@Composable
private fun TvTeam(state: MatchState, side: Side, s: TvScale, modifier: Modifier) {
    val team = state.config.team(side)
    val c = team.color.toColor()
    val on = c.onColor()
    val leader = state.leader == side
    Column(
        modifier.clip(RoundedCornerShape(s.dp(0.4f)))
            .background(Brush.verticalGradient(0f to c, 0.2f to c, 0.62f to lerp(c, Night, 0.5f), 1f to lerp(c, Night, 0.82f))),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = s.dp(0.3f), vertical = s.dp(0.12f)), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            if (leader) Text(if (state.finished) "🏆 " else "▲ ", color = on.copy(alpha = 0.9f), fontSize = s.sp(0.36f))
            Text(teamName(team, side).uppercase(), color = on, fontWeight = FontWeight.Black, fontSize = s.sp(0.6f), letterSpacing = s.sp(0.04f),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("tv_name_${side.name.lowercase()}"))
        }
        if (state.config.rules.setBased) {
            Row(horizontalArrangement = Arrangement.spacedBy(s.dp(0.12f))) {
                repeat(state.config.rules.setsToWin) { i ->
                    Box(Modifier.size(s.dp(0.22f)).clip(CircleShape).background(if (i < state.setsWon(side)) Color.White else Color.White.copy(alpha = 0.25f)))
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = s.dp(0.2f)), contentAlignment = Alignment.Center) {
            val score = state.score(side)
            val size = fitDigits(score, constraints.maxWidth, constraints.maxHeight)
            val px = with(LocalDensity.current) { size.toPx() }
            val style = TvDigits.copy(shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, px * 0.025f), px * 0.06f))
            AnimatedContent(score, transitionSpec = { (scaleIn(tween(260), initialScale = 1.15f) + fadeIn(tween(200))) togetherWith fadeOut(tween(140)) }, label = "tv") {
                Text(it.toString(), style = style, fontSize = size, color = Color.White, maxLines = 1, softWrap = false,
                    modifier = Modifier.digitBox(px).testTag("tv_score_${side.name.lowercase()}"))
            }
        }
    }
}

/**
 * The largest font size at which the score fits [maxW] x [maxH] pixels. Sized for at least two
 * digits so the number doesn't jump when it passes 9; three digits shrink to fit.
 */
@Composable
private fun fitDigits(score: Int, maxW: Int, maxH: Int): TextUnit {
    val measurer = rememberTextMeasurer()
    val digits = maxOf(2, score.toString().length)
    val refWidth = remember(digits) { measurer.measure("8".repeat(digits), TvDigits.copy(fontSize = 100.sp), maxLines = 1, softWrap = false).size.width }
    val refPx = with(LocalDensity.current) { 100.sp.toPx() }
    if (refWidth <= 0 || maxW <= 0 || maxH <= 0) return 100.sp
    val fit = minOf(maxW.toFloat() / refWidth, maxH / (refPx * DIGIT_BOX_EM)) * 0.96f
    return (100f * fit).coerceIn(12f, 2000f).sp
}

/** The current time, refreshed on every [periodMs] boundary while [active]. */
@Composable
private fun rememberTicker(periodMs: Long, active: Boolean = true): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs, active) {
        now.longValue = System.currentTimeMillis()
        while (active) {
            delay(periodMs - System.currentTimeMillis() % periodMs)
            now.longValue = System.currentTimeMillis()
        }
    }
    return now
}
