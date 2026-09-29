package com.bepinex.android.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.bepinex.android.R
import com.bepinex.android.ui.theme.GlassDialog
import com.bepinex.android.ui.theme.glassContainerColor
import com.bepinex.android.ui.theme.glassSurface

private const val LONG_TOKEN_BREAK = 24

@Composable
fun MarkdownText(
    rawText: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    lineHeight: androidx.compose.ui.unit.TextUnit = 24.sp
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val linkStyle = TextLinkStyles(
        style = SpanStyle(
            color = linkColor,
            textDecoration = TextDecoration.Underline
        )
    )
    val regex = Regex("""\[([^\]]+)]\s*\(([^)]+)\)""")

    val annotated = remember(rawText, linkColor, uriHandler) {
        buildAnnotatedString {
            var lastEnd = 0
            for (match in regex.findAll(rawText)) {
                val start = match.range.first
                if (start > lastEnd) {
                    appendWrapping(rawText.substring(lastEnd, start))
                }
                val linkText = match.groupValues[1]
                val url = match.groupValues[2].trim()
                withLink(
                    LinkAnnotation.Url(
                        url = url,
                        styles = linkStyle,
                        linkInteractionListener = { uriHandler.openUri(url) }
                    )
                ) {
                    appendWrapping(linkText)
                }
                lastEnd = match.range.last + 1
            }
            if (lastEnd < rawText.length) {
                appendWrapping(rawText.substring(lastEnd))
            }
        }
    }

    Text(
        text = annotated,
        style = style,
        lineHeight = lineHeight,
        softWrap = true,
        modifier = modifier.fillMaxWidth()
    )
}

private fun AnnotatedString.Builder.appendWrapping(text: String) {
    val buffer = StringBuilder(text.length + 8)
    var run = 0
    for (ch in text) {
        buffer.append(ch)
        if (ch.isWhitespace()) {
            run = 0
        } else {
            run++
            if (run >= LONG_TOKEN_BREAK) {
                buffer.append('\u200B')
                run = 0
            }
        }
    }
    append(buffer.toString())
}

@Composable
private fun ScrollableNoticeDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    title: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit = {},
    body: @Composable ColumnScope.() -> Unit
) {
    val configuration = LocalConfiguration.current
    val maxHeight = (configuration.screenHeightDp * 0.85f).dp
    val maxWidth = (configuration.screenWidthDp - 48).dp
    GlassDialog(onDismissRequest = onDismissRequest, properties = properties) {
        Card(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .glassSurface(RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = glassContainerColor(MaterialTheme.colorScheme.surface)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                title()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    body()
                }
                actions()
            }
        }
    }
}

@Composable
fun AnnouncementDialog(
    date: String,
    message: String,
    onDismiss: () -> Unit
) {
    ScrollableNoticeDialog(
        onDismissRequest = onDismiss,
        title = {
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
        },
        actions = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    ) {
        MarkdownText(
            rawText = message,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 24.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

@Composable
fun IncompleteTranslationDialog(onDismiss: () -> Unit) {
    ScrollableNoticeDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.lang_incomplete_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        },
        actions = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.ok))
                }
            }
        }
    ) {
        MarkdownText(
            rawText = stringResource(R.string.lang_incomplete_message),
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 24.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateDialog(
    currentVersion: String,
    remoteVersion: String,
    updateMessage: String,
    onUpdate: () -> Unit,
    onSkip: () -> Unit
) {
    ScrollableNoticeDialog(
        onDismissRequest = onSkip,
        title = {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
        },
        actions = {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.update_skip))
                }
                Button(onClick = onUpdate) {
                    Text(stringResource(R.string.update_now))
                }
            }
        }
    ) {
        if (updateMessage.isNotEmpty()) {
            MarkdownText(
                rawText = updateMessage,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
fun BlockedDialog(message: String) {
    ScrollableNoticeDialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = {
            Text(
                text = stringResource(R.string.update_blocked_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )
        }
    ) {
        if (message.isNotEmpty()) {
            MarkdownText(rawText = message)
        } else {
            Text(
                text = stringResource(R.string.update_blocked_message),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

fun openUpdateUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: Exception) { }
}

@Composable
fun CrashDialog(
    gameName: String,
    signal: String?,
    crashLog: String,
    onDismiss: () -> Unit,
    onExportLogs: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    val maxLogHeight = (LocalConfiguration.current.screenHeightDp * 0.42f).dp
    val logScroll = rememberScrollState()

    GlassDialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxDialogHeight)
                .glassSurface(RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = glassContainerColor(MaterialTheme.colorScheme.surface)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight)
                    .padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.crash_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = stringResource(R.string.crash_game_info, gameName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                if (signal != null) {
                    Text(
                        text = stringResource(R.string.crash_signal, signal),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (crashLog.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.crash_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = maxLogHeight)
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = crashLog,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            ),
                            modifier = Modifier
                                .verticalScroll(logScroll)
                                .padding(12.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = {
                        clipboardManager.setText(AnnotatedString(buildCrashSummary(gameName, signal, crashLog)))
                        copied = true
                    }) {
                        Text(if (copied) stringResource(R.string.done) else stringResource(R.string.crash_copy))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onExportLogs) {
                        Text(stringResource(R.string.crash_export_logs))
                    }
                }
            }
        }
    }
}

private fun buildCrashSummary(gameName: String, signal: String?, crashLog: String): String = buildString {
    appendLine("=== Crash Report ===")
    appendLine("Game: $gameName")
    if (signal != null) appendLine("Signal: $signal")
    appendLine()
    appendLine(crashLog)
}
