package com.bepinex.android.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bepinex.android.R
import java.io.File

/**
 * Browses all files and directories below a modpack's root directory.
 *
 * Directory navigation is handled locally so this screen can be used as a
 * single navigation destination. The callbacks expose file tree events to the
 * navigation layer without requiring it to manage directory listings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModFileBrowserScreen(
    rootDirectory: File,
    onNavigateBack: () -> Unit,
    onDirectoryChanged: (File) -> Unit = {},
    onFileClick: (File) -> Unit = {}
) {
    val browserRootDirectory = remember(rootDirectory.absolutePath) {
        rootDirectory.absoluteFile
    }
    var currentDirectory by remember(browserRootDirectory.absolutePath) {
        mutableStateOf(browserRootDirectory)
    }
    val entries = remember(currentDirectory.absolutePath) {
        currentDirectory.listFiles()
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?: emptyList()
    }
    val canNavigateUp = currentDirectory.absolutePath != browserRootDirectory.absolutePath

    fun navigateUp() {
        val parent = currentDirectory.parentFile
        if (canNavigateUp && parent != null) {
            currentDirectory = parent
            onDirectoryChanged(parent)
        } else {
            onNavigateBack()
        }
    }

    BackHandler(onBack = ::navigateUp)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (canNavigateUp) currentDirectory.name else stringResource(R.string.mod_file_browser_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (canNavigateUp) {
                            Text(
                                currentDirectory.relativeTo(browserRootDirectory).path,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = ::navigateUp) {
                        Icon(Icons.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (!currentDirectory.isDirectory) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.mod_file_browser_missing))
            }
        } else if (entries.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.mod_file_browser_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(entries, key = { it.absolutePath }) { entry ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        onClick = {
                            if (entry.isDirectory) {
                                currentDirectory = entry
                                onDirectoryChanged(entry)
                            } else {
                                onFileClick(entry)
                            }
                        }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                                null,
                                Modifier.size(24.dp),
                                tint = if (entry.isDirectory) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    if (entry.isDirectory) stringResource(R.string.mod_file_browser_directory) else formatFileSize(entry.length()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (entry.isDirectory) {
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
