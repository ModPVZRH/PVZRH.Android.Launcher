package com.bepinex.android.ui.screens

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bepinex.android.R
import com.bepinex.android.save.SaveDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnRequestPermissionResultListener

private enum class SaveConfirmation { BACKUP, RESTORE_BACKUP, RESTORE_STAGED, CLEAR_BACKUP, CLEAR_RESTORE_DATA }
private enum class SaveOperation { BACKUP, RESTORE_BACKUP, RESTORE_STAGED, IMPORT, EXPORT }

private data class SaveUiSnapshot(
    val status: SaveDataManager.SaveStatus,
    val backupContents: List<String>,
    val restoreContents: List<String>,
    val launcherSavesExist: Boolean,
    val safPermission: Boolean,
    val shizukuAvailable: Boolean,
    val shizukuPermission: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveImportScreen(packageName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val useSaf = SaveDataManager.needsSafAccess()
    val useShizuku = SaveDataManager.needsShizuku()

    var uiSnapshot by remember {
        mutableStateOf(
            SaveUiSnapshot(
                status = SaveDataManager.SaveStatus.NOT_FOUND,
                backupContents = emptyList(),
                restoreContents = emptyList(),
                launcherSavesExist = false,
                safPermission = false,
                shizukuAvailable = false,
                shizukuPermission = false
            )
        )
    }
    var activeOperation by remember { mutableStateOf<SaveOperation?>(null) }
    var showMaintenance by remember { mutableStateOf(true) }
    var showGuide by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<SaveConfirmation?>(null) }
    var refreshJob by remember { mutableStateOf<Job?>(null) }
    var refreshGeneration by remember { mutableStateOf(0) }

    fun refreshStatus() {
        refreshJob?.cancel()
        val generation = refreshGeneration + 1
        refreshGeneration = generation
        refreshJob = scope.launch {
            val snapshot = runCatching {
                withContext(Dispatchers.IO) {
                    val available = !useShizuku || SaveDataManager.isShizukuAvailable()
                    SaveUiSnapshot(
                        status = SaveDataManager.getGameSavesStatus(context, packageName),
                        backupContents = SaveDataManager.getG2LContents(packageName),
                        restoreContents = SaveDataManager.getL2GContents(packageName),
                        launcherSavesExist = SaveDataManager.getLauncherSavesDir(context).exists() ||
                            SaveDataManager.getLauncherPlayerDataFile(context).exists(),
                        safPermission = !useSaf || SaveDataManager.hasPersistedSafPermission(context, packageName),
                        shizukuAvailable = available,
                        shizukuPermission = !useShizuku || (available && SaveDataManager.hasShizukuPermission())
                    )
                }
            }.getOrNull() ?: return@launch

            if (generation != refreshGeneration) return@launch
            uiSnapshot = snapshot
        }
    }

    fun runOperation(
        operationType: SaveOperation,
        operation: suspend () -> Result<Int>,
        successMessage: (Int) -> String,
        errorMessage: (String) -> String
    ) {
        if (activeOperation != null) return
        scope.launch {
            refreshJob?.cancel()
            activeOperation = operationType
            val result = try {
                withContext(Dispatchers.IO) { operation() }
            } catch (error: Exception) {
                Result.failure(error)
            }
            activeOperation = null
            result.fold(
                onSuccess = { count ->
                    refreshStatus()
                    Toast.makeText(context, successMessage(count), Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    Toast.makeText(context, errorMessage(error.message.orEmpty()), Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    fun backup() = runOperation(
        SaveOperation.BACKUP,
        { SaveDataManager.g2lBackup(context, packageName) },
        { context.getString(R.string.save_g2l_done, it) },
        { context.getString(R.string.save_g2l_error, it) }
    )

    fun restoreBackup() = runOperation(
        SaveOperation.RESTORE_BACKUP,
        { SaveDataManager.restoreBackup(context, packageName) },
        { context.getString(R.string.save_l2g_done, it) },
        { context.getString(R.string.save_l2g_error, it) }
    )

    fun restoreStagedData() = runOperation(
        SaveOperation.RESTORE_STAGED,
        { SaveDataManager.l2gRestore(context, packageName) },
        { context.getString(R.string.save_l2g_done, it) },
        { context.getString(R.string.save_l2g_error, it) }
    )

    val permissionListener = remember(packageName) {
        OnRequestPermissionResultListener { _, _ -> refreshStatus() }
    }
    DisposableEffect(permissionListener) {
        try { Shizuku.addRequestPermissionResultListener(permissionListener) } catch (_: Exception) {}
        onDispose {
            try { Shizuku.removeRequestPermissionResultListener(permissionListener) } catch (_: Exception) {}
        }
    }
    LaunchedEffect(packageName) { refreshStatus() }

    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (SaveDataManager.handleSafPickerResult(context, result.resultCode, result.data)) {
            Toast.makeText(context, R.string.save_permission_granted, Toast.LENGTH_SHORT).show()
            refreshStatus()
        } else {
            Toast.makeText(context, R.string.save_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    val accessReady = when {
        useShizuku -> uiSnapshot.shizukuAvailable && uiSnapshot.shizukuPermission
        useSaf -> uiSnapshot.safPermission
        else -> true
    }
    val busy = activeOperation != null
    val gameDataFound = uiSnapshot.status is SaveDataManager.SaveStatus.FOUND
    val canBackup = accessReady && gameDataFound && !busy
    val canRestoreBackup = accessReady && uiSnapshot.backupContents.isNotEmpty() && !busy

    BackHandler(enabled = busy) {}

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_section_saves)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { GameSaveStatusCard(packageName, uiSnapshot.status, useSaf, useShizuku) }

            item {
                AnimatedVisibility(
                    visible = !accessReady,
                    enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(120))
                ) {
                    SavePrerequisiteCard(
                        useSaf, useShizuku, uiSnapshot.shizukuAvailable, safLauncher, packageName, context
                    )
                }
            }

            item {
                PrimarySaveActionCard(
                    icon = Icons.Outlined.Backup,
                    title = stringResource(R.string.save_g2l_title),
                    description = backupDescription(uiSnapshot.status, accessReady, uiSnapshot.backupContents.isNotEmpty()),
                    buttonLabel = stringResource(R.string.save_g2l_button),
                    enabled = canBackup,
                    operating = activeOperation == SaveOperation.BACKUP,
                    onClick = {
                        if (uiSnapshot.backupContents.isEmpty()) backup() else confirmation = SaveConfirmation.BACKUP
                    }
                )
            }

            item {
                PrimarySaveActionCard(
                    icon = Icons.Outlined.Restore,
                    title = stringResource(R.string.save_restore_backup_title),
                    description = restoreDescription(accessReady, uiSnapshot.backupContents.size),
                    buttonLabel = stringResource(R.string.save_restore_backup_title),
                    enabled = canRestoreBackup,
                    operating = activeOperation == SaveOperation.RESTORE_BACKUP,
                    onClick = { confirmation = SaveConfirmation.RESTORE_BACKUP }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp)) }
            item {
                ExpandableSectionHeader(
                    stringResource(R.string.settings_section_maintenance),
                    showMaintenance,
                    !busy
                ) { showMaintenance = !showMaintenance }
            }

            if (showMaintenance) {
                item { SaveFileSummaryCard(uiSnapshot.backupContents, uiSnapshot.restoreContents) }
                item {
                    SecondarySaveAction(
                        Icons.Outlined.Restore,
                        stringResource(R.string.save_g2l_import_button),
                        stringResource(R.string.save_g2l_import_desc),
                        uiSnapshot.backupContents.isNotEmpty() && !busy
                    ) {
                        runOperation(
                            SaveOperation.IMPORT,
                            { SaveDataManager.g2lImportToLauncher(context, packageName) },
                            { context.getString(R.string.save_g2l_import_done, it) },
                            { context.getString(R.string.save_g2l_import_error, it) }
                        )
                    }
                }
                item {
                    SecondarySaveAction(
                        Icons.Outlined.Backup,
                        stringResource(R.string.save_l2g_export_button),
                        stringResource(R.string.save_l2g_export_desc),
                        uiSnapshot.launcherSavesExist && !busy
                    ) {
                        runOperation(
                            SaveOperation.EXPORT,
                            { SaveDataManager.launcherExportToL2g(context, packageName) },
                            { context.getString(R.string.save_l2g_export_done, it) },
                            { context.getString(R.string.save_l2g_export_error, it) }
                        )
                    }
                }
                item {
                    AnimatedVisibility(
                        visible = uiSnapshot.restoreContents.isNotEmpty(),
                        enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                        exit = fadeOut(tween(120)) + shrinkVertically(tween(120))
                    ) {
                        SecondarySaveAction(
                            Icons.Outlined.Restore,
                            stringResource(R.string.save_l2g_button),
                            stringResource(R.string.save_l2g_desc),
                            accessReady && !busy
                        ) { confirmation = SaveConfirmation.RESTORE_STAGED }
                    }
                }
                item {
                    AnimatedVisibility(
                        visible = uiSnapshot.backupContents.isNotEmpty(),
                        enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                        exit = fadeOut(tween(120)) + shrinkVertically(tween(120))
                    ) {
                        SecondarySaveAction(
                            Icons.Outlined.Delete,
                            stringResource(R.string.save_clear_g2l),
                            "",
                            !busy,
                            destructive = true
                        ) { confirmation = SaveConfirmation.CLEAR_BACKUP }
                    }
                }
                item {
                    AnimatedVisibility(
                        visible = uiSnapshot.restoreContents.isNotEmpty(),
                        enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                        exit = fadeOut(tween(120)) + shrinkVertically(tween(120))
                    ) {
                        SecondarySaveAction(
                            Icons.Outlined.Delete,
                            stringResource(R.string.save_clear_l2g),
                            "",
                            !busy,
                            destructive = true
                        ) { confirmation = SaveConfirmation.CLEAR_RESTORE_DATA }
                    }
                }
                item { SavePathCard(packageName) }
                item {
                    ExpandableSectionHeader(stringResource(R.string.save_manual_guide), showGuide, !busy) {
                        showGuide = !showGuide
                    }
                }
                if (showGuide) item { ManualGuideCard(packageName, useSaf, useShizuku) }
            }
        }
    }

    confirmation?.let { pending ->
        val title = when (pending) {
            SaveConfirmation.BACKUP -> stringResource(R.string.save_g2l_title)
            SaveConfirmation.RESTORE_BACKUP -> stringResource(R.string.save_restore_backup_title)
            SaveConfirmation.RESTORE_STAGED -> stringResource(R.string.save_l2g_title)
            SaveConfirmation.CLEAR_BACKUP -> stringResource(R.string.save_clear_g2l)
            SaveConfirmation.CLEAR_RESTORE_DATA -> stringResource(R.string.save_clear_l2g)
        }
        val message = when (pending) {
            SaveConfirmation.BACKUP -> stringResource(R.string.save_g2l_exists)
            SaveConfirmation.RESTORE_BACKUP,
            SaveConfirmation.RESTORE_STAGED -> stringResource(R.string.save_restore_backup_confirm)
            else -> stringResource(R.string.mod_file_browser_delete_message, title)
        }
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    when (pending) {
                        SaveConfirmation.BACKUP -> backup()
                        SaveConfirmation.RESTORE_BACKUP -> restoreBackup()
                        SaveConfirmation.RESTORE_STAGED -> restoreStagedData()
                        SaveConfirmation.CLEAR_BACKUP -> {
                            SaveDataManager.clearG2L(packageName)
                            refreshStatus()
                            Toast.makeText(context, R.string.save_backup_cleared, Toast.LENGTH_SHORT).show()
                        }
                        SaveConfirmation.CLEAR_RESTORE_DATA -> {
                            SaveDataManager.clearL2G(packageName)
                            refreshStatus()
                            Toast.makeText(context, R.string.save_backup_cleared, Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Text(stringResource(if (pending.name.startsWith("CLEAR")) R.string.confirm_delete else R.string.confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }) {
                    Text(stringResource(R.string.confirm_cancel))
                }
            }
        )
    }
}

@Composable
private fun backupDescription(
    status: SaveDataManager.SaveStatus,
    accessReady: Boolean,
    backupExists: Boolean
): String = when {
    !accessReady -> stringResource(R.string.save_need_permission)
    status is SaveDataManager.SaveStatus.FOUND && backupExists -> stringResource(R.string.save_g2l_exists)
    status is SaveDataManager.SaveStatus.FOUND -> stringResource(R.string.save_g2l_desc)
    status is SaveDataManager.SaveStatus.EMPTY -> stringResource(R.string.save_empty)
    else -> stringResource(R.string.save_not_found)
}

@Composable
private fun restoreDescription(accessReady: Boolean, backupCount: Int): String = when {
    !accessReady -> stringResource(R.string.save_need_permission)
    backupCount > 0 -> stringResource(R.string.save_restore_backup_desc)
    else -> stringResource(R.string.save_import_nothing)
}

@Composable
private fun GameSaveStatusCard(
    packageName: String,
    status: SaveDataManager.SaveStatus,
    useSaf: Boolean,
    useShizuku: Boolean
) {
    val title = when {
        useShizuku -> stringResource(R.string.save_shizuku_title)
        useSaf -> stringResource(R.string.save_saf_title)
        else -> stringResource(R.string.save_direct_title)
    }
    val hint = when {
        useShizuku -> stringResource(R.string.save_shizuku_hint)
        useSaf -> stringResource(R.string.save_saf_hint)
        else -> packageName
    }
    val statusText = when (status) {
        SaveDataManager.SaveStatus.NEED_PERMISSION -> stringResource(R.string.save_need_permission)
        SaveDataManager.SaveStatus.NOT_FOUND -> stringResource(R.string.save_not_found)
        SaveDataManager.SaveStatus.EMPTY -> stringResource(R.string.save_empty)
        SaveDataManager.SaveStatus.SHIZUKU_NOT_AVAILABLE -> stringResource(R.string.save_shizuku_not_running)
        is SaveDataManager.SaveStatus.FOUND -> stringResource(R.string.save_found, status.fileCount)
    }
    val ready = status is SaveDataManager.SaveStatus.FOUND

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            StatusLine(if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.Info, statusText, ready)
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, text: String, positive: Boolean) {
    val tint = if (positive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

@Composable
private fun SavePrerequisiteCard(
    useSaf: Boolean,
    useShizuku: Boolean,
    shizukuAvailable: Boolean,
    safLauncher: ActivityResultLauncher<Intent>,
    packageName: String,
    context: Context
) {
    val title = when {
        useSaf -> stringResource(R.string.save_grant_permission)
        useShizuku && !shizukuAvailable -> stringResource(R.string.save_shizuku_install)
        else -> stringResource(R.string.save_shizuku_grant_permission)
    }
    val description = when {
        useSaf -> stringResource(R.string.save_grant_permission_desc)
        useShizuku && !shizukuAvailable -> stringResource(R.string.save_shizuku_not_installed_desc)
        else -> stringResource(R.string.save_need_permission)
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable {
            when {
                useSaf -> safLauncher.launch(SaveDataManager.createSafPickerIntent(packageName))
                useShizuku && !shizukuAvailable -> try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.save_shizuku_not_installed, Toast.LENGTH_SHORT).show()
                }
                else -> try {
                    Shizuku.requestPermission(100)
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.save_permission_denied, Toast.LENGTH_SHORT).show()
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Folder, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PrimarySaveActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    buttonLabel: String,
    enabled: Boolean,
    operating: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    icon,
                    null,
                    Modifier.size(28.dp),
                    tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick, Modifier.fillMaxWidth(), enabled = enabled) {
                if (operating) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(buttonLabel)
            }
        }
    }
}

@Composable
private fun ExpandableSectionHeader(title: String, expanded: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
        }
    }
}

@Composable
private fun SecondarySaveAction(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    OutlinedButton(
        onClick,
        Modifier.fillMaxWidth(),
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge, color = color)
            if (description.isNotBlank()) {
                Text(
                    description,
                    Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SaveFileSummaryCard(backupContents: List<String>, restoreContents: List<String>) {
    Card(
        Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(180)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FileSummary(stringResource(R.string.save_g2l_contents), backupContents)
            HorizontalDivider()
            FileSummary(stringResource(R.string.save_l2g_contents), restoreContents)
        }
    }
}

@Composable
private fun FileSummary(title: String, contents: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (contents.isEmpty()) {
            Text(
                stringResource(R.string.save_import_nothing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(stringResource(R.string.save_found, contents.size), color = MaterialTheme.colorScheme.primary)
            contents.take(5).forEach { name ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Folder, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(name, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun SavePathCard(packageName: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.save_g2l_desc_detail),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.save_l2g_desc_detail),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                packageName,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ManualGuideCard(packageName: String, useSaf: Boolean, useShizuku: Boolean) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                useShizuku -> {
                    Text(stringResource(R.string.save_manual_shizuku_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.save_manual_shizuku_step1))
                    Text(stringResource(R.string.save_manual_shizuku_step2))
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    CommandText(stringResource(R.string.save_adb_backup_cmd, packageName))
                    CommandText(stringResource(R.string.save_adb_restore_cmd, packageName))
                }
                useSaf -> {
                    Text(stringResource(R.string.save_manual_saf_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.save_manual_saf_step1))
                    Text(stringResource(R.string.save_manual_saf_step2))
                    Text(stringResource(R.string.save_manual_saf_step3))
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    CommandText(stringResource(R.string.save_adb_backup_cmd, packageName))
                    CommandText(stringResource(R.string.save_adb_restore_cmd, packageName))
                }
                else -> {
                    Text(stringResource(R.string.save_manual_direct_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.save_manual_direct_step1, packageName))
                    Text(stringResource(R.string.save_manual_direct_step2, packageName))
                    Text(stringResource(R.string.save_manual_direct_step3))
                }
            }
        }
    }
}

@Composable
private fun CommandText(text: String) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("ADB command", text))
                    Toast.makeText(context, R.string.save_command_copied, Toast.LENGTH_SHORT).show()
                }
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = stringResource(R.string.save_copy_command),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
