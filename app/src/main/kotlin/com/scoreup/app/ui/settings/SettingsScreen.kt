package com.scoreup.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scoreup.app.BuildConfig
import com.scoreup.app.R
import com.scoreup.app.data.SettingsState
import com.scoreup.app.data.ThemeMode
import com.scoreup.app.ui.common.ScreenScaffold

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(state: SettingsState, onBack: () -> Unit, onChange: ((SettingsState) -> SettingsState) -> Unit) {
    ScreenScaffold(stringResource(R.string.settings), onBack) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ToggleRow(stringResource(R.string.sounds), state.sounds, "toggle_sounds") { v -> onChange { it.copy(sounds = v) } }
            ToggleRow(stringResource(R.string.haptics), state.haptics, "toggle_haptics") { v -> onChange { it.copy(haptics = v) } }
            ToggleRow(stringResource(R.string.keep_screen_on), state.keepScreenOn, "toggle_screen") { v -> onChange { it.copy(keepScreenOn = v) } }

            Card(stringResource(R.string.theme)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ThemeMode.DARK to R.string.theme_dark, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.SYSTEM to R.string.theme_system).forEach { (m, label) ->
                        FilterChip(selected = state.theme == m, onClick = { onChange { it.copy(theme = m) } }, label = { Text(stringResource(label)) },
                            modifier = Modifier.testTag("theme_${m.name.lowercase()}"))
                    }
                }
            }
            Card(stringResource(R.string.language)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("" to stringResource(R.string.language_system), "en" to "English", "iw" to "עברית").forEach { (tag, label) ->
                        FilterChip(selected = state.language == tag, onClick = { onChange { it.copy(language = tag) } }, label = { Text(label) },
                            modifier = Modifier.testTag("lang_${tag.ifEmpty { "system" }}"))
                    }
                }
            }
            Card(stringResource(R.string.cast_app_id)) {
                var id by remember { mutableStateOf(state.castAppId) }
                OutlinedTextField(
                    value = id, onValueChange = { v -> id = v.filter { it.isLetterOrDigit() }.take(16).uppercase(); onChange { it.copy(castAppId = id) } },
                    placeholder = { Text(stringResource(R.string.cast_app_id_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(stringResource(R.string.restart_to_apply)) },
                )
            }
            Card(stringResource(R.string.about)) {
                Text(stringResource(R.string.version_fmt, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), modifier = Modifier.testTag("version"))
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
