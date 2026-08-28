package com.bepinex.android.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bepinex.android.R

/**
 * Render text with [text](url) markdown links as clickable annotated text.
 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val parts = text.split(Regex("""(\[[^\]]+\]\([^)]+\))"""))

    val annotated = buildAnnotatedString {
        var i = 0
        while (i < parts.size) {
            val part = parts[i]
            if (part.isNotEmpty()) {
                append(part)
            }
            i++
            if (i < parts.size) {
                val linkMatch = Regex("""\[([^\]]+)\]\(([^)]+)\)""").find(parts[i - 1])
                    ?: Regex("""\[([^\]]+)\]\(([^)]+)\)""").find(parts[i])
                // Actually check current part
                i--
                val currentPart = parts[i]
                val m = Regex("""\[([^\]]+)\]\(([^)]+)\)""").find(currentPart)
                if (m != null) {
                    val linkText = m.groupValues[1]
                    val url = m.groupValues[2]
                    val start = length
                    append(linkText)
                    addStyle(
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        ),
                        start,
                        length
                    )
                    addStringAnnotation("URL", url, start, length)
                }
                i++
            }
        }
    }

    ClickableText(
        text = annotated,
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.item)))
                } catch (_: Exception) { }
            }
        }
    )
}

@Composable
fun AnnouncementDialog(
    date: String,
    message: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.update_announcement),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (date.isNotEmpty()) {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateDialog(
    currentVersion: String,
    remoteVersion: String,
    updateMessage: String,
    onUpdate: () -> Unit,
    onSkip: () -> Unit
) {
    Dialog(onDismissRequest = onSkip) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.update_available),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = stringResource(R.string.update_version_info, currentVersion, remoteVersion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (updateMessage.isNotEmpty()) {
                    MarkdownText(
                        text = updateMessage,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.update_skip))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onUpdate) {
                        Text(stringResource(R.string.update_now))
                    }
                }
            }
        }
    }
}

@Composable
fun BlockedDialog(message: String) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.update_blocked_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (message.isNotEmpty()) {
                    MarkdownText(
                        text = message,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                } else {
                    Text(
                        text = stringResource(R.string.update_blocked_message),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
}

fun openUpdateUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: Exception) { }
}
