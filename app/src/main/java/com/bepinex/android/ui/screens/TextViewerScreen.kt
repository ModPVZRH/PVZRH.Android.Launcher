package com.bepinex.android.ui.screens

import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bepinex.android.R
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextViewerScreen(
    file: File,
    onNavigateBack: () -> Unit,
    onSave: ((File, String) -> Boolean)? = null,
    onSettingsClick: () -> Unit = {},
    wordWrap: Boolean = true,
    showLineNumbers: Boolean = true
) {
    val initialContent = remember(file) {
        runCatching { file.readText() }.getOrDefault("")
    }
    var textFieldValue by remember(file) {
        mutableStateOf(TextFieldValue(initialContent))
    }
    val hasChanges = textFieldValue.text != initialContent
    var showDiscardDialog by remember(file) { mutableStateOf(false) }
    var saveFailed by remember(file) { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun requestDismiss() {
        if (hasChanges && onSave != null) showDiscardDialog = true else onNavigateBack()
    }

    BackHandler(enabled = !showDiscardDialog, onBack = ::requestDismiss)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            file.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (hasChanges) {
                            Text(
                                stringResource(R.string.config_editor_unsaved),
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = ::requestDismiss) {
                        Icon(Icons.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (onSave != null) {
                        IconButton(
                            onClick = {
                                val saved = runCatching { onSave(file, textFieldValue.text) }.getOrDefault(false)
                                if (saved) {
                                    saveFailed = false
                                    keyboardController?.hide()
                                } else {
                                    saveFailed = true
                                }
                            },
                            enabled = hasChanges
                        ) {
                            Icon(
                                Icons.Filled.Save,
                                stringResource(R.string.config_editor_save),
                                tint = if (hasChanges) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.viewer_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (saveFailed) {
                Text(
                    stringResource(R.string.config_editor_save_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                val lines = remember(textFieldValue.text) { textFieldValue.text.split("\n") }
                var visualLineCount by remember { mutableIntStateOf(lines.size) }
                val lineNumberWidth = remember(visualLineCount) { "${visualLineCount}".length * 8 + 4 }

                if (onSave != null) {
                    if (wordWrap) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (showLineNumbers) {
                                Column(
                                    modifier = Modifier
                                        .width(lineNumberWidth.dp)
                                        .padding(top = 12.dp, bottom = 12.dp)
                                ) {
                                    for (i in 1..visualLineCount) {
                                        Text(
                                            text = "$i",
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(end = 2.dp)
                                        )
                                    }
                                }
                            }

                            BasicTextField(
                                value = textFieldValue,
                                onValueChange = {
                                    textFieldValue = it
                                    saveFailed = false
                                },
                                onTextLayout = { visualLineCount = it.lineCount },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 0.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
                                textStyle = TextStyle(
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (textFieldValue.text.isEmpty()) {
                                            Text(
                                                stringResource(R.string.viewer_empty),
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (showLineNumbers) {
                                Column(
                                    modifier = Modifier
                                        .width(lineNumberWidth.dp)
                                        .padding(top = 12.dp, bottom = 12.dp)
                                ) {
                                    for (i in 1..visualLineCount) {
                                        Text(
                                            text = "$i",
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(end = 2.dp)
                                        )
                                    }
                                }
                            }

                            val editorTextColor = MaterialTheme.colorScheme.onSurface
                            AndroidView(
                                factory = { ctx ->
                                    EditText(ctx).apply {
                                        setHorizontallyScrolling(true)
                                        setMaxLines(Int.MAX_VALUE)
                                        setBackgroundColor(0x00000000)
                                        typeface = Typeface.MONOSPACE
                                        textSize = 11f
                                        setPadding(4, 36, 36, 36)
                                        setTextColor(editorTextColor.toArgb())
                                        addTextChangedListener(object : TextWatcher {
                                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                                            override fun afterTextChanged(s: Editable?) {
                                                val newText = s?.toString() ?: ""
                                                if (newText != textFieldValue.text) {
                                                    textFieldValue = textFieldValue.copy(text = newText)
                                                    saveFailed = false
                                                }
                                                post { visualLineCount = layout.lineCount }
                                            }
                                        })
                                        setText(textFieldValue.text)
                                        post { visualLineCount = layout.lineCount }
                                    }
                                },
                                update = { et ->
                                    if (et.text.toString() != textFieldValue.text) {
                                        et.setText(textFieldValue.text)
                                        et.setSelection(et.text.length)
                                    }
                                    et.setTextColor(editorTextColor.toArgb())
                                    et.post { visualLineCount = et.layout.lineCount }
                                },
                                modifier = Modifier
                                    .weight(1f)
                            )
                        }
                    }
                } else {
                    SelectionContainer {
                        Text(
                            text = textFieldValue.text.ifEmpty { stringResource(R.string.viewer_empty) },
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(rememberScrollState())
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (textFieldValue.text.isEmpty()) MaterialTheme.colorScheme.outline
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.config_editor_discard_title)) },
            text = { Text(stringResource(R.string.config_editor_discard_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    keyboardController?.hide()
                    onNavigateBack()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerSettingsScreen(
    onNavigateBack: () -> Unit,
    wordWrap: Boolean,
    showLineNumbers: Boolean,
    onWordWrapChange: (Boolean) -> Unit,
    onLineNumbersChange: (Boolean) -> Unit
) {
    BackHandler { onNavigateBack() }
    val previewText = stringResource(R.string.viewer_preview_text)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.viewer_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.viewer_word_wrap))
                            Text(
                                stringResource(R.string.viewer_word_wrap_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = wordWrap, onCheckedChange = onWordWrapChange)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.viewer_line_numbers))
                            Text(
                                stringResource(R.string.viewer_line_numbers_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = showLineNumbers, onCheckedChange = onLineNumbersChange)
                    }
                }
            }

            Text(
                stringResource(R.string.viewer_preview),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                val previewLines = remember(previewText) { previewText.split("\n") }
                val previewLineNumberWidth = remember(previewLines.size) { "${previewLines.size}".length * 8 + 24 }

                if (wordWrap) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (showLineNumbers) {
                            previewLines.forEachIndexed { index, line ->
                                Row {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.width(previewLineNumberWidth.dp)
                                    )
                                    Text(
                                        text = line,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = previewText,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (showLineNumbers) {
                                previewLines.forEachIndexed { index, line ->
                                    Row {
                                        Text(
                                            text = "${index + 1}",
                                            fontSize = 10.sp,
                                            lineHeight = 14.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.width(previewLineNumberWidth.dp)
                                        )
                                        Text(
                                            text = line,
                                            fontSize = 10.sp,
                                            lineHeight = 14.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = previewText,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
