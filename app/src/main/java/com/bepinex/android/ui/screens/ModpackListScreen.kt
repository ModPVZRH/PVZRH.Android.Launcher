package com.bepinex.android.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bepinex.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.bepinex.android.modpack.ModpackManager
import com.bepinex.android.modpack.ModpackMeta
import com.bepinex.android.shortcut.ModpackShortcutHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModpackListScreen(
    packageName: String,
    targetGameLabel: String,
    modpacks: List<ModpackMeta>,
    activeModpackName: String?,
    iconRefreshKey: Int = 0,
    onCreateModpack: (String, Boolean, android.graphics.Bitmap?) -> Unit,
    onDeleteModpack: (String) -> Unit,
    onEditModpack: (String, String, Boolean, android.graphics.Bitmap?) -> Unit,
    onSelectModpack: (String?) -> Unit,
    onOpenModpack: (String) -> Unit,
    onExportModpack: (String) -> Unit,
    onImportModpack: () -> Unit
) {
    val context = LocalContext.current
    val manager = remember { ModpackManager() }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    var showEditDialog by remember { mutableStateOf<ModpackMeta?>(null) }
    var showFabCreateDialog by remember { mutableStateOf(false) }
    var editingIconForModpack by remember { mutableStateOf<String?>(null) }

    // Refresh icons when modpacks change
    var internalIconRefreshKey by remember { mutableIntStateOf(0) }
    val combinedIconRefreshKey = iconRefreshKey + internalIconRefreshKey

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val targetName = editingIconForModpack ?: return@rememberLauncherForActivityResult
        uri?.let {
            val ext = when {
                it.toString().endsWith(".jpg", true) -> "jpg"
                it.toString().endsWith(".jpeg", true) -> "jpg"
                it.toString().endsWith(".webp", true) -> "webp"
                else -> "png"
            }
            context.contentResolver.openInputStream(it)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                if (bitmap != null) {
                    manager.saveModpackIcon(packageName, targetName, bitmap, ext)
                    internalIconRefreshKey++
                }
            }
        }
        editingIconForModpack = null
    }

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
            contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
        ) {
            item(key = "vanilla") {
                VanillaCard(
                    isActive = activeModpackName == null,
                    onSelect = { onSelectModpack(null) }
                )
            }

            if (modpacks.isEmpty()) {
                item {
                    EmptyModpacksCard(onCreate = { showFabCreateDialog = true })
                }
            } else {
                items(modpacks, key = { it.name }) { modpack ->
                    // Force recomposition when internalIconRefreshKey changes
                    key(combinedIconRefreshKey) {
                        ModpackCard(
                            modpack = modpack,
                            packageName = packageName,
                            iconRefreshKey = combinedIconRefreshKey,
                            isActive = activeModpackName == modpack.name,
                            onOpen = { onOpenModpack(modpack.name) },
                            onSelect = { onSelectModpack(modpack.name) },
                            onIconClick = {
                                editingIconForModpack = modpack.name
                                imagePicker.launch("image/*")
                            },
                            onEdit = { showEditDialog = modpack },
                            onDelete = { showDeleteDialog = modpack.name }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // Delete dialog
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
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.confirm_cancel))
                }
            }
        )
    }

    // Edit dialog (name + shortcut only)
    showEditDialog?.let { modpack ->
        EditModpackDialog(
            modpack = modpack,
            packageName = packageName,
            onDismiss = { showEditDialog = null },
            onSave = { newName, createShortcut, bitmap ->
                onEditModpack(modpack.name, newName, createShortcut, bitmap)
                showEditDialog = null
            }
        )
    }

    // Create dialog
    if (showFabCreateDialog) {
        CreateModpackDialog(
            targetGame = targetGameLabel,
            onDismiss = { showFabCreateDialog = false },
            onCreate = { name, createShortcut, bitmap ->
                onCreateModpack(name, createShortcut, bitmap)
                showFabCreateDialog = false
            }
        )
    }
}

@Composable
private fun VanillaCard(
    isActive: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        onClick = onSelect,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Block,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.modpack_vanilla),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isActive) FontWeight.SemiBold else null
                )
                Text(
                    text = stringResource(R.string.modpack_vanilla_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(selected = isActive, onClick = onSelect)
        }
    }
}

@Composable
private fun ModpackCard(
    modpack: ModpackMeta,
    packageName: String,
    iconRefreshKey: Int,
    isActive: Boolean,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onIconClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val manager = remember { ModpackManager() }
    var iconBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // Reload icon whenever iconRefreshKey changes
    LaunchedEffect(iconRefreshKey, modpack.name) {
        iconBitmap = withContext(Dispatchers.IO) {
            val file = manager.getModpackIconFile(packageName, modpack.name)
            if (file != null && file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }
    }

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
            // Clickable icon
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onIconClick() },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val bmp = iconBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = stringResource(R.string.modpack_icon),
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.FolderZip,
                            contentDescription = stringResource(R.string.modpack_icon),
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
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
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.modpack_edit),
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
private fun EditModpackDialog(
    modpack: ModpackMeta,
    packageName: String,
    onDismiss: () -> Unit,
    onSave: (newName: String, createShortcut: Boolean, bitmap: android.graphics.Bitmap?) -> Unit
) {
    val context = LocalContext.current
    val manager = remember { ModpackManager() }
    var name by remember(modpack.name) { mutableStateOf(modpack.name) }
    var renameFailed by remember(modpack.name) { mutableStateOf(false) }
    var iconBitmap by remember(modpack.name) {
        mutableStateOf(
            manager.getModpackIconFile(packageName, modpack.name)?.let {
                BitmapFactory.decodeFile(it.absolutePath)
            }
        )
    }
    var hasNewIcon by remember(modpack.name) { mutableStateOf(false) }
    var showPermissionDialog by remember(modpack.name) { mutableStateOf(false) }
    val trimmedName = name.trim()

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                iconBitmap = BitmapFactory.decodeStream(stream)
                hasNewIcon = true
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.modpack_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .clickable { imagePicker.launch("image/*") },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (iconBitmap != null) {
                                Image(
                                    bitmap = iconBitmap!!.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.AddAPhoto,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.modpack_icon),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.modpack_icon_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        renameFailed = false
                    },
                    label = { Text(stringResource(R.string.modpack_name_hint)) },
                    singleLine = true,
                    isError = renameFailed,
                    supportingText = if (renameFailed) {
                        { Text(stringResource(R.string.modpack_rename_failed)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                ShortcutActionButton(
                    enabled = trimmedName.isNotEmpty(),
                    onClick = {
                        if (!ModpackShortcutHelper.hasShortcutPermission(context)) {
                            showPermissionDialog = true
                        } else {
                            onSave(trimmedName, true, if (hasNewIcon) iconBitmap else null)
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (trimmedName.isEmpty()) {
                        renameFailed = true
                    } else {
                        onSave(trimmedName, false, if (hasNewIcon) iconBitmap else null)
                    }
                },
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

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(stringResource(R.string.shortcut_permission_title)) },
            text = { Text(stringResource(R.string.shortcut_permission_guide)) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    ModpackShortcutHelper.openShortcutPermissionSettings(context)
                }) {
                    Text(stringResource(R.string.shortcut_permission_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(stringResource(R.string.confirm_cancel))
                }
            }
        )
    }
}

@Composable
private fun EmptyModpacksCard(onCreate: () -> Unit) {
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.FolderOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = stringResource(R.string.modpack_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
fun CreateModpackDialog(
    targetGame: String,
    onDismiss: () -> Unit,
    onCreate: (String, Boolean, android.graphics.Bitmap?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var createShortcut by remember { mutableStateOf(false) }
    var iconBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    val manager = remember { ModpackManager() }
    val trimmedName = name.trim()
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                iconBitmap = BitmapFactory.decodeStream(stream)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.modpack_create)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .clickable { imagePicker.launch("image/*") },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (iconBitmap != null) {
                                Image(
                                    bitmap = iconBitmap!!.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.AddAPhoto,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.modpack_icon),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(R.string.modpack_icon_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Game target
                Text(
                    text = stringResource(R.string.modpack_target_game, targetGame),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.modpack_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ShortcutActionButton(
                    enabled = trimmedName.isNotEmpty(),
                    onClick = {
                        if (!ModpackShortcutHelper.hasShortcutPermission(context)) {
                            showPermissionDialog = true
                        } else {
                            onCreate(trimmedName, true, iconBitmap)
                        }
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (trimmedName.isNotEmpty()) {
                        onCreate(trimmedName, createShortcut, iconBitmap)
                    }
                },
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

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(stringResource(R.string.shortcut_permission_title)) },
            text = { Text(stringResource(R.string.shortcut_permission_guide)) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    ModpackShortcutHelper.openShortcutPermissionSettings(context)
                }) {
                    Text(stringResource(R.string.shortcut_permission_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text(stringResource(R.string.confirm_cancel))
                }
            }
        )
    }
}

@Composable
private fun ShortcutActionButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            imageVector = Icons.Filled.AddToHomeScreen,
            contentDescription = null
        )
        Spacer(Modifier.width(8.dp))
        Text(text = stringResource(R.string.modpack_shortcut_create))
    }
}
