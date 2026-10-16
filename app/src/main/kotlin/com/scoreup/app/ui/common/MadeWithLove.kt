package com.scoreup.app.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.scoreup.app.R

const val CREATOR_TELEGRAM = "https://t.me/israelcohen"

/** "Made with ❤️ from Israel" — the word Israel opens the creator's Telegram. */
@Composable
fun MadeWithLove(modifier: Modifier = Modifier) {
    val prefix = stringResource(R.string.made_with_love_prefix)
    val word = stringResource(R.string.made_with_love_israel)
    val linkColor = MaterialTheme.colorScheme.primary
    val text = buildAnnotatedString {
        append(prefix)
        withLink(LinkAnnotation.Url(CREATOR_TELEGRAM, TextLinkStyles(SpanStyle(color = linkColor, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)))) {
            append(word)
        }
    }
    Text(
        text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(vertical = 16.dp).testTag("made_with_love"),
    )
}
