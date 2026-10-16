package com.scoreup.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scoreup.app.R

/** Language options shown in their own language so anyone can find theirs. */
val LANGUAGES = listOf("en" to "English", "iw" to "עברית", "" to null)

@Composable
fun LanguageOptions(current: String, onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LANGUAGES.forEach { (tag, label) ->
            Surface(
                onClick = { onPick(tag) }, shape = RoundedCornerShape(14.dp),
                color = if (current == tag) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().testTag("lang_${tag.ifEmpty { "system" }}"),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = current == tag, onClick = { onPick(tag) })
                    Text(label ?: stringResource(R.string.language_system), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun LanguageDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_menu)) },
        text = { LanguageOptions(current) { onPick(it); onDismiss() } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
