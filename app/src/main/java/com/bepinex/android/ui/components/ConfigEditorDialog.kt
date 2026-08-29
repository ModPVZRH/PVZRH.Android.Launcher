package com.bepinex.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bepinex.android.R
import java.io.File

/**
 * Full-screen editor for supported text files in a modpack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditorDialog(
    configFile: File,
    onDismiss: () -> Unit,
    onSave: (File, String) -> Boolean
) {
    val initialContent = remember(configFile) {
        runCatching { configFile.readText() }.getOrDefault("")
    }
    var content by remember { mutableStateOf(initialContent) }
    var hasChanges by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }

    val highlightColors = SyntaxHighlightColors(
        property = MaterialTheme.colorScheme.primary,
        string = MaterialTheme.colorScheme.tertiary,
        number = MaterialTheme.colorScheme.secondary,
        boolean = MaterialTheme.colorScheme.error,
        nullLiteral = MaterialTheme.colorScheme.onSurfaceVariant,
        keyword = MaterialTheme.colorScheme.primary,
        function = lerp(
            MaterialTheme.colorScheme.tertiary,
            MaterialTheme.colorScheme.primary,
            0.35f
        ),
        builtin = lerp(
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.error,
            0.2f
        ),
        comment = MaterialTheme.colorScheme.outline.copy(alpha = 0.9f)
    )
    val syntaxHighlighting = remember(configFile.extension, highlightColors) {
        SyntaxHighlightVisualTransformation(configFile.extension, highlightColors)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        configFile.name,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasChanges) showDiscardDialog = true
                        else onDismiss()
                    }) {
                        Icon(Icons.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (onSave(configFile, content)) {
                                hasChanges = false
                            } else {
                                saveFailed = true
                            }
                        },
                        enabled = hasChanges
                    ) {
                        Icon(Icons.Filled.Save, stringResource(R.string.config_editor_save),
                            tint = if (hasChanges) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(8.dp)) {
            if (hasChanges) {
                Text(
                    stringResource(R.string.config_editor_unsaved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            if (saveFailed) {
                Text(
                    stringResource(R.string.config_editor_save_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            OutlinedTextField(
                value = content,
                onValueChange = {
                    content = it
                    hasChanges = it != initialContent
                    saveFailed = false
                },
                modifier = Modifier.fillMaxSize(),
                visualTransformation = syntaxHighlighting,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )
        }
    }

    // Discard changes dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.config_editor_discard_title)) },
            text = { Text(stringResource(R.string.config_editor_discard_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onDismiss()
                }) {
                    Text(stringResource(R.string.confirm_yes), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.confirm_no))
                }
            }
        )
    }
}
