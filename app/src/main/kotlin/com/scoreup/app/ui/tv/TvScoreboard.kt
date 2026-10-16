package com.scoreup.app.ui.tv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.scoreup.app.ui.common.formatClock
import com.scoreup.app.ui.common.periodLabel
import com.scoreup.app.ui.common.sportEmoji
import com.scoreup.app.ui.common.teamName
import com.scoreup.app.ui.theme.ClockDigits
import com.scoreup.app.ui.theme.ScoreDigits
import com.scoreup.app.ui.theme.toColor
import com.scoreup.core.MatchState
import com.scoreup.core.Side
import kotlinx.coroutines.delay

/** Arena-style, display-only scoreboard for TVs / external displays (always dark, landscape). */
@Composable
fun TvScoreboard(state: MatchState, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.clock.running) { while (true) { now = System.currentTimeMillis(); if (!state.clock.running) break; delay(250) } }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF04060D)).testTag("tv_scoreboard")) {
        val unit = minOf(maxWidth.value / 16f, maxHeight.value / 9f)
        Column(Modifier.fillMaxSize().padding((unit * 0.4f).dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${sportEmoji(state.config.sport)}  SCOREUP", color = Color(0xFFFFD60A), fontWeight = FontWeight.Black, fontSize = (unit * 0.5f).sp, letterSpacing = (unit * 0.08f).sp)
                Spacer(Modifier.weight(1f))
                Box(Modifier.clip(CircleShape).background(Color(0xFFFF3D57)).padding(horizontal = (unit * 0.25f).dp, vertical = (unit * 0.06f).dp)) {
                    Text("● LIVE", color = Color.White, fontWeight = FontWeight.Black, fontSize = (unit * 0.32f).sp)
                }
            }
            Row(Modifier.weight(1f).padding(top = (unit * 0.3f).dp), horizontalArrangement = Arrangement.spacedBy((unit * 0.3f).dp)) {
                TvTeam(state, Side.HOME, unit, Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    val p = periodLabel(state)
                    if (p.isNotEmpty()) Text(p, color = Color.White, fontWeight = FontWeight.Black, fontSize = (unit * 0.55f).sp)
                    Spacer(Modifier.height((unit * 0.2f).dp))
                    Box(Modifier.clip(RoundedCornerShape((unit * 0.2f).dp)).background(Color(0xFF111729)).padding(horizontal = (unit * 0.3f).dp, vertical = (unit * 0.1f).dp)) {
                        Text(formatClock(state.displayClockMs(now), state.config.rules.clockCountsDown), style = ClockDigits,
                            color = if (state.clock.running) Color(0xFFFFD60A) else Color.White, fontSize = (unit * 0.8f).sp)
                    }
                    if (state.config.rules.setBased && state.sets.isNotEmpty()) {
                        Spacer(Modifier.height((unit * 0.2f).dp))
                        state.sets.forEach { Text("${it.home} – ${it.away}", color = Color(0xFF8A93B2), fontSize = (unit * 0.35f).sp, fontWeight = FontWeight.Bold) }
                    }
                }
                TvTeam(state, Side.AWAY, unit, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun TvTeam(state: MatchState, side: Side, unit: Float, modifier: Modifier) {
    val team = state.config.team(side)
    val c = team.color.toColor()
    Column(
        modifier.clip(RoundedCornerShape((unit * 0.4f).dp))
            .background(Brush.verticalGradient(listOf(c.copy(alpha = 0.95f), c.copy(alpha = 0.35f), Color(0xFF0B1020))))
            .padding((unit * 0.3f).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(teamName(team, side).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = (unit * 0.6f).sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, letterSpacing = (unit * 0.05f).sp)
        if (state.config.rules.setBased) {
            Row(horizontalArrangement = Arrangement.spacedBy((unit * 0.1f).dp), modifier = Modifier.padding(top = (unit * 0.1f).dp)) {
                repeat(state.config.rules.setsToWin) { i ->
                    Box(Modifier.size((unit * 0.22f).dp).clip(CircleShape).background(if (i < state.setsWon(side)) Color.White else Color.White.copy(alpha = 0.25f)))
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            AnimatedContent(state.score(side), transitionSpec = { (scaleIn(tween(250), 1.3f) + fadeIn()) togetherWith fadeOut(tween(150)) }, label = "tv") {
                Text(it.toString(), style = ScoreDigits, color = Color.White, fontSize = (unit * 3.2f).sp, modifier = Modifier.testTag("tv_score_${side.name.lowercase()}"))
            }
        }
    }
}
