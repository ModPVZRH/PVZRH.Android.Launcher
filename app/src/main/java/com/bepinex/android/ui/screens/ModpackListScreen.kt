package com.bepinex.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bepinex.android.R
import com.bepinex.android.modpack.ModpackMeta

/**
 * Lists all modpacks for a selected game.
 *
 * The list keeps the vanilla entry separate from user-created modpacks so that
 * the currently selected runtime is easy to identify at a glance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModpackListScreen(
    packageName: String,
    targetGameLabel: String,
    modpacks: List<ModpackMeta>,
    activeModpackName: String?,
    onCreateModpack: (String) -> Unit,
    onDeleteModpack: (String) -> Unit,
    onRenameModpack: (String, String) -> Boolean,
    onSelectModpack: (String?) -> Unit,
    onOpenModpack: (String) -> Unit,
    onExportModpack: (String) -> Unit,
    onImportModpack: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    var showRenameDialog by remember { mutableStateOf<String?>(null) }
    var showFabCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(

                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.modpack_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = targetGameLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onImportModpack) {
                        Icon(
                            imageVector = Icons.Filled.FileOpen,
                            contentDescription = stringResource(R.string.modpack_import)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showFabCreateDialog = true },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null
                    )
                },
                text = { Text(stringResource(R.string.modpack_create)) },
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            // Leave room for the FAB and navigation bar so the last card is
            // never hidden behind the action button.
            contentPadding = PaddingValues(top = 12.dp, bottom = 104.dp)
        ) {
            item(key = "vanilla") {
                val isActive = activeModpackName == null
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelectModpack(null) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Block,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = if (isActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.modpack_vanilla),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (isActive) FontWeight.SemiBold else null
                            )
                            Text(
                                text = stringResource(R.string.no_mods_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RadioButton(
                            selected = isActive,
                            onClick = { onSelectModpack(null) }
                        )
                    }
                }
            }

            if (modpacks.isEmpty()) {
                item(key = "empty") {
                    EmptyModpackState(onCreate = { showFabCreateDialog = true })
                }
            } else {
                items(
                    items = modpacks,
                    key = { it.name },
                    contentType = { "modpack" }
                ) { modpack ->
                    val isActive = modpack.name == activeModpackName
                    ModpackCard(
                        modpack = modpack,
                        isActive = isActive,
                        onOpen = { onOpenModpack(modpack.name) },
                        onSelect = { onSelectModpack(modpack.name) },
                        onRename = { showRenameDialog = modpack.name },
                        onDelete = { showDeleteDialog = modpack.name }
                    )
                }
            }
        }
    }

    if (showFabCreateDialog) {
        CreateModpackDialog(
            targetGame = targetGameLabel,
            onDismiss = { showFabCreateDialog = false },
            onCreate = { name ->
                onCreateModpack(name)
                showFabCreateDialog = false
            }
        )
    }

    showRenameDialog?.let { oldName ->
        RenameModpackDialog(
            oldName = oldName,
            onDismiss = { showRenameDialog = null },
            onRename = { newName ->
                if (onRenameModpack(oldName, newName)) {
                    showRenameDialog = null
                    true
                } else {
                    false
                }
            }
        )
    }

    showDeleteDialog?.let { name ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.modpack_confirm_delete_title)) },
            text = { Text(stringResource(R.string.modpack_confirm_delete_msg, name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteModpack(name)
                        showDeleteDialog = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.confirm_cancel))
                }
            }
        )
    }
}

@Composable
private fun EmptyModpackState(onCreate: () -> Unit) {
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
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderZip,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.no_mods_installed),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(16.dp))
            Button(onClick = onCreate) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.modpack_create))
            }
        }
    }
}

@Composable
private fun ModpackCard(
    modpack: ModpackMeta,
    isActive: Boolean,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        onClick = onOpen,
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.FolderZip,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = modpack.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isActive) FontWeight.SemiBold else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isActive) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = stringResource(R.string.modpack_active),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.modpack_mod_count, modpack.modCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(selected = isActive, onClick = onSelect)
            IconButton(onClick = onRename) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.modpack_rename),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun RenameModpackDialog(
    oldName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Boolean
) {
    var newName by remember(oldName) { mutableStateOf(oldName) }
    var renameFailed by remember(oldName) { mutableStateOf(false) }
    val trimmedName = newName.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.modpack_rename)) },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = {
                    newName = it
                    renameFailed = false
                },
                label = { Text(stringResource(R.string.modpack_name_hint)) },
                singleLine = true,
                isError = renameFailed,
                supportingText = if (renameFailed) {
                    { Text(stringResource(R.string.modpack_rename_failed)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!onRename(trimmedName)) {
                        renameFailed = true
                    }
                },
                enabled = trimmedName.isNotEmpty() && trimmedName != oldName
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
fun CreateModpackDialog(
    targetGame: String,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val trimmedName = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.modpack_create)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.modpack_target_game, targetGame),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.modpack_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(trimmedName) },
                enabled = trimmedName.isNotEmpty()
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
