package com.bepinex.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bepinex.android.R
import com.bepinex.android.modpack.ModpackMod
import java.io.File

/**
 * Manage mods within a specific modpack and edit config files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModpackDetailScreen(
    modpackName: String,
    mods: List<ModpackMod>,
    configFiles: List<File>,
    onNavigateBack: () -> Unit,
    onAddMod: () -> Unit,
    onDeleteMod: (ModpackMod) -> Unit,
    onRenameMod: (ModpackMod, String) -> Unit,
    onToggleMod: (ModpackMod, Boolean) -> Unit,
    onOpenConfig: (File) -> Unit,
    onViewLog: () -> Unit,
    onExportModpack: () -> Unit,
    onBrowseModFiles: () -> Unit = {}
) {
    var modPendingDelete by remember { mutableStateOf<ModpackMod?>(null) }
    var modPendingRename by remember { mutableStateOf<ModpackMod?>(null) }
    var sortMode by remember { mutableStateOf(ModSortMode.DISPLAY_NAME) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    val sortedMods = remember(mods, sortMode) { mods.sortedWith(sortMode.comparator) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = modpackName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onViewLog) {
                        Icon(
                            imageVector = Icons.Filled.Terminal,
                            contentDescription = stringResource(R.string.log_title)
                        )
                    }
                    IconButton(onClick = onExportModpack) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(R.string.modpack_export)
                        )
                    }
                    IconButton(onClick = onAddMod) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.modpack_add_mod)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            item {
                BrowseModFilesCard(onClick = onBrowseModFiles)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.installed_mods),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() }
                    )
                    if (mods.isNotEmpty()) {
                        StatusBadge(
                            text = stringResource(
                                R.string.modpack_mod_count_ratio,
                                mods.count { it.enabled },
                                mods.size
                            )
                        )
                        Box {
                            IconButton(onClick = { sortMenuOpen = true }) {
                                Icon(
                                    imageVector = Icons.Filled.Sort,
                                    contentDescription = stringResource(R.string.modpack_mod_sort)
                                )
                            }
                            DropdownMenu(
                                expanded = sortMenuOpen,
                                onDismissRequest = { sortMenuOpen = false }
                            ) {
                                ModSortMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(mode.labelRes)) },
                                        onClick = {
                                            sortMode = mode
                                            sortMenuOpen = false
                                        },
                                        trailingIcon = if (sortMode == mode) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = null
                                                )
                                            }
                                        } else {
                                            null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (mods.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Extension,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.modpack_no_mods),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = onAddMod) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.modpack_add_mod))
                            }
                        }
                    }
                }
            } else {
                items(
                    items = sortedMods,
                    key = { it.file.absolutePath },
                    contentType = { "mod" }
                ) { mod ->
                    ModItemCard(
                        mod = mod,
                        onToggle = { enabled -> onToggleMod(mod, enabled) },
                        onRename = { modPendingRename = mod },
                        onDelete = { modPendingDelete = mod }
                    )
                }
            }

            if (configFiles.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.settings_section_config),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .semantics { heading() }
                    )
                }
                items(
                    items = configFiles,
                    key = { it.absolutePath },
                    contentType = { "config" }
                ) { cfg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        onClick = { onOpenConfig(cfg) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = cfg.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    modPendingDelete?.let { mod ->
        AlertDialog(
            onDismissRequest = { modPendingDelete = null },
            title = { Text(stringResource(R.string.mod_file_browser_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.mod_file_browser_delete_message,
                        mod.displayName
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteMod(mod)
                        modPendingDelete = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.confirm_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { modPendingDelete = null }) {
                    Text(stringResource(R.string.confirm_cancel))
                }
            }
        )
    }

    modPendingRename?.let { mod ->
        RenameDllDialog(
            mod = mod,
            onDismiss = { modPendingRename = null },
            onConfirm = { newName ->
                onRenameMod(mod, newName)
                modPendingRename = null
            }
        )
    }
}

private enum class ModSortMode(val labelRes: Int) {
    DISPLAY_NAME(R.string.modpack_mod_sort_name),
    FILE_NAME(R.string.modpack_mod_sort_file),
    ENABLED_FIRST(R.string.modpack_mod_sort_enabled_first),
    DISABLED_FIRST(R.string.modpack_mod_sort_disabled_first);

    val comparator: Comparator<ModpackMod>
        get() = when (this) {
            DISPLAY_NAME -> compareBy(String.CASE_INSENSITIVE_ORDER, ModpackMod::displayName)
                .thenBy(String.CASE_INSENSITIVE_ORDER, ModpackMod::relativePath)
            FILE_NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { mod: ModpackMod -> mod.file.name }
                .thenBy(String.CASE_INSENSITIVE_ORDER, ModpackMod::relativePath)
            ENABLED_FIRST -> compareByDescending(ModpackMod::enabled)
                .thenBy(String.CASE_INSENSITIVE_ORDER, ModpackMod::displayName)
            DISABLED_FIRST -> compareBy(ModpackMod::enabled)
                .thenBy(String.CASE_INSENSITIVE_ORDER, ModpackMod::displayName)
        }
}

@Composable
private fun RenameDllDialog(
    mod: ModpackMod,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(mod.relativePath) { mutableStateOf(mod.displayName) }
    val trimmed = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.modpack_mod_rename)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.modpack_mod_file_name, mod.relativePath),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.modpack_mod_display_name)) },
                    supportingText = {
                        Text(
                            if (trimmed.isEmpty()) {
                                stringResource(R.string.modpack_mod_rename_failed)
                            } else {
                                stringResource(R.string.modpack_mod_display_name_hint)
                            }
                        )
                    },
                    singleLine = true,
                    isError = trimmed.isEmpty(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty()
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirm_cancel))
            }
        }
    )
}

@Composable
private fun ModItemCard(
    mod: ModpackMod,
    onToggle: (Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val showsMappedName = mod.displayName != mod.file.name
    val enableLabel = stringResource(R.string.modpack_mod_enable)
    val subtitle = buildString {
        if (showsMappedName || mod.relativePath.contains('/')) {
            append(mod.relativePath)
            append(" · ")
        }
        append(formatFileSize(mod.file.length()))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (mod.enabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Extension,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = if (mod.enabled) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mod.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (mod.enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = mod.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics {
                        contentDescription = enableLabel + ": " + mod.displayName
                    }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onRename) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.modpack_rename))
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.delete))
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BrowseModFilesCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.modpack_browse_mod_files),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.modpack_browse_mod_files_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
