package com.scoreup.app.ui.scoreboard

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
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scoreup.app.R
import com.scoreup.app.ui.common.teamName
import com.scoreup.app.ui.theme.ScoreDigits
import com.scoreup.app.ui.theme.onColor
import com.scoreup.app.ui.theme.toColor
import com.scoreup.core.MatchState
import com.scoreup.core.Side

/** One team's half of the scoreboard: tap anywhere to score, long-press to edit. */
@Composable
fun TeamPanel(
    state: MatchState,
    side: Side,
    onScore: () -> Unit,
    onEdit: () -> Unit,
    onMinus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val team = state.config.team(side)
    val color = team.color.toColor()
    val on = color.onColor()
    val score = state.score(side)
    val leading = state.leader == side
    val name = teamName(team, side)

    // Punchy "bump" whenever this team's score changes.
    val bump = remember { Animatable(1f) }
    LaunchedEffect(score) {
        bump.snapTo(1.12f)
        bump.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }

    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.72f).compositeOver(Color.Black))))
            .combinedClickable(onClick = onScore, onLongClick = onEdit)
            .semantics { contentDescription = "$name $score" }
            .testTag("team_${side.name.lowercase()}"),
    ) {
        val compact = maxHeight < 220.dp
        val digitSize = (minOf(maxWidth.value * 0.42f, maxHeight.value * 0.55f)).coerceIn(56f, 260f).sp
        Column(
            Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = if (compact) 10.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading) Text("▲ ", color = on.copy(alpha = 0.9f), fontSize = 14.sp)
                Text(
                    name.uppercase(), color = on, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                    fontSize = if (compact) 18.sp else 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("name_${side.name.lowercase()}"),
                )
            }
            if (state.config.rules.setBased) {
                Spacer(Modifier.height(6.dp))
                SetDots(won = state.setsWon(side), total = state.config.rules.setsToWin, color = on)
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = score,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically(tween(220)) { h -> if (up) h / 2 else -h / 2 } + fadeIn(tween(220))) togetherWith
                            (slideOutVertically(tween(180)) { h -> if (up) -h / 2 else h / 2 } + fadeOut(tween(180)))
                    },
                    label = "score",
                ) { value ->
                    Text(
                        value.toString(), style = ScoreDigits, color = on, fontSize = digitSize, textAlign = TextAlign.Center,
                        maxLines = 1, modifier = Modifier.scale(bump.value).testTag("score_${side.name.lowercase()}"),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = onMinus, modifier = Modifier.size(if (compact) 36.dp else 44.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = on.copy(alpha = 0.16f), contentColor = on),
                ) { Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.undo)) }
                Text(
                    "+${state.config.tapPoints}", color = on.copy(alpha = 0.85f), fontWeight = FontWeight.Black, fontSize = 18.sp,
                    modifier = Modifier.clip(CircleShape).background(on.copy(alpha = 0.16f)).padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SetDots(won: Int, total: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            Box(Modifier.size(12.dp).clip(CircleShape).background(if (i < won) color else color.copy(alpha = 0.25f)))
        }
    }
}

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}
