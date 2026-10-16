package com.scoreup.app.ui.tv

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.scoreup.app.R
import com.scoreup.app.ui.common.ScreenScaffold

@Composable
fun CastScreen(
    presentationDisplay: String?,
    castAvailable: Boolean,
    castConnectedTo: String?,
    onBack: () -> Unit,
    onOpenTvMode: () -> Unit,
) {
    val context = LocalContext.current
    ScreenScaffold(stringResource(R.string.cast_title), onBack) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.cast_body), color = MaterialTheme.colorScheme.onSurfaceVariant)

            OptionCard(Icons.Rounded.Tv, stringResource(R.string.cast_option_presentation), stringResource(R.string.cast_option_presentation_body)) {
                Text(
                    if (presentationDisplay != null) stringResource(R.string.presentation_active, presentationDisplay) else stringResource(R.string.presentation_none),
                    color = if (presentationDisplay != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("presentation_status"),
                )
            }

            OptionCard(Icons.Rounded.Fullscreen, stringResource(R.string.cast_option_mirror), stringResource(R.string.cast_option_mirror_body)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        // Not every OEM exposes the cast settings screen; fall back to display settings.
                        val intents = listOf(Intent(Settings.ACTION_CAST_SETTINGS), Intent(Settings.ACTION_DISPLAY_SETTINGS))
                        intents.firstOrNull { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }
                    }) { Icon(Icons.Rounded.Cast, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.open_cast_settings)) }
                    Button(onClick = onOpenTvMode, modifier = Modifier.testTag("open_tv_mode")) { Text(stringResource(R.string.open_tv_mode)) }
                }
            }

            if (castAvailable) {
                OptionCard(Icons.Rounded.CastConnected, stringResource(R.string.cast_option_cast), stringResource(R.string.cast_option_cast_body)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AndroidView(factory = { ctx -> MediaRouteButton(ctx).also { CastButtonFactory.setUpMediaRouteButton(ctx, it) } }, modifier = Modifier.size(48.dp))
                        castConnectedTo?.let { Text(stringResource(R.string.cast_connected, it), color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionCard(icon: ImageVector, title: String, body: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
