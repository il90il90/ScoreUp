package com.scoreup.app.ui.common

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.scoreup.app.R
import com.scoreup.shared.WearSync
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

val HeartRed = Color(0xFFFF4D6D)

/** The watch sends a keep-alive every 10 s while it measures; silence longer than this means it stopped. */
private const val HEART_STALE_MS = 25_000L

/** Heart rate from the watch while it keeps arriving, else null. */
@Composable
fun rememberWatchHeartRate(readings: StateFlow<WearSync.HeartReading?>): Int? {
    val reading by readings.collectAsState()
    val fresh by produceState<Int?>(null, reading) {
        value = reading?.bpm
        if (reading != null) { delay(HEART_STALE_MS); value = null }
    }
    return fresh
}

/** "♥ 128": the heart beats at the measured rate. */
@Composable
fun HeartBadge(bpm: Int, fontSize: TextUnit, modifier: Modifier = Modifier, color: Color = Color.White) {
    val iconSize = with(LocalDensity.current) { fontSize.toDp() }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        key(bpm / 5) {
            val beat = rememberInfiniteTransition(label = "beat").animateFloat(
                1f, 1.22f, infiniteRepeatable(tween(30_000 / bpm.coerceIn(30, 240), easing = FastOutLinearInEasing), RepeatMode.Reverse), label = "b",
            )
            Icon(Icons.Rounded.Favorite, stringResource(R.string.heart_rate), Modifier.size(iconSize).scale(beat.value), tint = HeartRed)
        }
        Spacer(Modifier.width(iconSize * 0.3f))
        Text("$bpm", color = color, fontSize = fontSize, fontWeight = FontWeight.Black, maxLines = 1)
    }
}
