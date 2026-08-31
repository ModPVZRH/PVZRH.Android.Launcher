package com.bepinex.android.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bepinex.android.R
import com.bepinex.android.save.SaveDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnRequestPermissionResultListener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveImportScreen(
    packageName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    var gameSavesStatus by remember { mutableStateOf<SaveDataManager.SaveStatus>(SaveDataManager.SaveStatus.NOT_FOUND) }
    var gameLaunched by remember { mutableStateOf(false) }
    var g2lContents by remember { mutableStateOf<List<String>>(emptyList()) }
    var l2gContents by remember { mutableStateOf<List<String>>(emptyList()) }
    var launcherSavesExists by remember { mutableStateOf(false) }
    var isOperating by remember { mutableStateOf(false) }
    val useSaf = SaveDataManager.needsSafAccess()
    val useShizuku = SaveDataManager.needsShizuku()
    var hasSafPermission by remember { mutableStateOf(false) }
    var shizukuAvailable by remember { mutableStateOf(false) }
    var shizukuPermission by remember { mutableStateOf(false) }

    fun refreshStatus() {
        scope.launch {
            withContext(Dispatchers.IO) {
                gameLaunched = SaveDataManager.hasGameLaunched(context)
                g2lContents = SaveDataManager.getG2LContents(packageName)
                l2gContents = SaveDataManager.getL2GContents(packageName)
                launcherSavesExists = SaveDataManager.getLauncherSavesDir(context).exists()

                if (useShizuku) {
                    shizukuAvailable = SaveDataManager.isShizukuAvailable()
                    shizukuPermission = SaveDataManager.hasShizukuPermission()
                    gameSavesStatus = if (shizukuAvailable && shizukuPermission) {
                        SaveDataManager.getGameSavesStatus(context, packageName)
                    } else if (!shizukuAvailable) {
                        SaveDataManager.SaveStatus.SHIZUKU_NOT_AVAILABLE
                    } else {
                        SaveDataManager.SaveStatus.NEED_PERMISSION
                    }
                } else if (useSaf) {
                    hasSafPermission = SaveDataManager.hasPersistedSafPermission(context, packageName)
                    gameSavesStatus = if (hasSafPermission) {
                        SaveDataManager.getSavesStatusViaSaf(context, packageName)
                    } else {
                        SaveDataManager.SaveStatus.NEED_PERMISSION
                    }
                } else {
                    gameSavesStatus = SaveDataManager.getGameSavesStatus(context, packageName)
                }
            }
        }
    }

    val permissionListener = remember {
        OnRequestPermissionResultListener { _, _ -> refreshStatus() }
    }
    LaunchedEffect(Unit) {
        try { Shizuku.addRequestPermissionResultListener(permissionListener) } catch (_: Exception) {}
    }
    DisposableEffect(Unit) {
        onDispose {
            try { Shizuku.removeRequestPermissionResultListener(permissionListener) } catch (_: Exception) {}
        }
    }
    LaunchedEffect(packageName) { refreshStatus() }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (SaveDataManager.handleSafPickerResult(context, packageName, result.resultCode, result.data)) {
            Toast.makeText(context, R.string.save_permission_granted, Toast.LENGTH_SHORT).show()
            refreshStatus()
        } else {
            Toast.makeText(context, R.string.save_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_import_saves)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Tab selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                        .background(if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .clickable { selectedTab = 0 },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "G2L",
                        color = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                        .background(if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .clickable { selectedTab = 1 },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "L2G",
                        color = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Content
            when (selectedTab) {
                0 -> G2LTab(
                    packageName, gameSavesStatus, gameLaunched, g2lContents,
                    useSaf, useShizuku, hasSafPermission, shizukuAvailable, shizukuPermission,
                    isOperating, safLauncher, context, scope, { refreshStatus() },
                    { isOperating = it }
                )
                1 -> L2GTab(
                    packageName, l2gContents, launcherSavesExists,
                    useShizuku, isOperating,
                    context, scope, { refreshStatus() },
                    { isOperating = it }
                )
            }
        }
    }
}

@Composable
private fun G2LTab(
    packageName: String,
    gameSavesStatus: SaveDataManager.SaveStatus,
    gameLaunched: Boolean,
    g2lContents: List<String>,
    useSaf: Boolean,
    useShizuku: Boolean,
    hasSafPermission: Boolean,
    shizukuAvailable: Boolean,
    shizukuPermission: Boolean,
    isOperating: Boolean,
    safLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    refreshStatus: () -> Unit,
    setOperating: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Description
        item {
            Text(stringResource(R.string.save_g2l_desc_detail), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Game status card
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (gameLaunched) Icons.Outlined.CheckCircle else Icons.Outlined.Error,
                            contentDescription = null, modifier = Modifier.size(16.dp),
                            tint = if (gameLaunched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            stringResource(if (gameLaunched) R.string.save_game_launched else R.string.save_game_not_launched),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (gameLaunched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    if (!gameLaunched) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.save_launch_game_prompt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    when (val status = gameSavesStatus) {
                        is SaveDataManager.SaveStatus.NEED_PERMISSION -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Error, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.save_need_permission), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        is SaveDataManager.SaveStatus.NOT_FOUND -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Error, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.save_not_found), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        is SaveDataManager.SaveStatus.EMPTY -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.save_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        is SaveDataManager.SaveStatus.SHIZUKU_NOT_AVAILABLE -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Error, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.save_shizuku_not_installed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        is SaveDataManager.SaveStatus.FOUND -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.save_found, status.fileCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            status.names.forEach { name ->
                                Row(modifier = Modifier.padding(start = 22.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(name, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Shizuku setup
        if (useShizuku && !shizukuAvailable) {
            item {
                Card(modifier = Modifier.fillMaxWidth().clickable {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/"))) }
                    catch (_: Exception) { Toast.makeText(context, "Cannot open browser", Toast.LENGTH_SHORT).show() }
                }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.save_shizuku_install), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.save_shizuku_not_installed_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else if (useShizuku && !shizukuPermission) {
            item {
                Card(modifier = Modifier.fillMaxWidth().clickable {
                    try { Shizuku.requestPermission(100) }
                    catch (_: Exception) { Toast.makeText(context, "Failed to request Shizuku permission", Toast.LENGTH_SHORT).show() }
                }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.save_shizuku_grant_permission), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.save_shizuku_not_running_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // SAF permission
        if (useSaf && !hasSafPermission) {
            item {
                Card(modifier = Modifier.fillMaxWidth().clickable {
                    safLauncher.launch(SaveDataManager.createSafPickerIntent(packageName))
                }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.save_grant_permission), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.save_grant_permission_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // G2L contents
        if (g2lContents.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.save_g2l_contents), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        g2lContents.forEach { name ->
                            Row(modifier = Modifier.padding(start = 8.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(name, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Backup button
        if (gameSavesStatus is SaveDataManager.SaveStatus.FOUND) {
            item {
                val canBackup = !isOperating
                Card(modifier = Modifier.fillMaxWidth().clickable(enabled = canBackup) {
                    scope.launch {
                        setOperating(true)
                        val result = when {
                            useShizuku -> SaveDataManager.g2lBackupViaShizuku(packageName)
                            else -> SaveDataManager.g2lBackupDirect(packageName)
                        }
                        setOperating(false)
                        result.fold(
                            onSuccess = { count ->
                                refreshStatus()
                                Toast.makeText(context, context.getString(R.string.save_g2l_done, count), Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { e ->
                                Toast.makeText(context, context.getString(R.string.save_g2l_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }, colors = CardDefaults.cardColors(
                    containerColor = if (canBackup) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Backup, contentDescription = null, modifier = Modifier.size(24.dp), tint = if (canBackup) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.save_g2l_button), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (g2lContents.isNotEmpty()) stringResource(R.string.save_g2l_exists) else stringResource(R.string.save_g2l_desc),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Import from G2L button
        if (g2lContents.isNotEmpty()) {
            item {
                val canImport = !isOperating
                Card(modifier = Modifier.fillMaxWidth().clickable(enabled = canImport) {
                    scope.launch {
                        setOperating(true)
                        val result = SaveDataManager.g2lImportToLauncher(context, packageName)
                        setOperating(false)
                        result.fold(
                            onSuccess = { count ->
                                refreshStatus()
                                Toast.makeText(context, context.getString(R.string.save_g2l_import_done, count), Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { e ->
                                Toast.makeText(context, context.getString(R.string.save_g2l_import_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }, colors = CardDefaults.cardColors(
                    containerColor = if (canImport) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(24.dp), tint = if (canImport) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.save_g2l_import_button), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.save_g2l_import_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Clear G2L
        if (g2lContents.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth().clickable(enabled = !isOperating) {
                    SaveDataManager.clearG2L(packageName)
                    refreshStatus()
                    Toast.makeText(context, R.string.save_backup_cleared, Toast.LENGTH_SHORT).show()
                }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.save_clear_g2l), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Manual guide
        item { HorizontalDivider() }
        item {
            Text(stringResource(R.string.save_manual_guide), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (useShizuku) {
                        Text(stringResource(R.string.save_manual_shizuku_title), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.save_manual_shizuku_step1), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.save_manual_shizuku_step2), style = MaterialTheme.typography.bodyMedium)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(stringResource(R.string.save_adb_step1), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.save_adb_backup_cmd, packageName), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    } else if (useSaf) {
                        Text(stringResource(R.string.save_manual_saf_title), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.save_manual_saf_step1), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.save_manual_saf_step2), style = MaterialTheme.typography.bodyMedium)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(stringResource(R.string.save_manual_adb_alt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.save_adb_backup_cmd, packageName), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(stringResource(R.string.save_manual_direct_title), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.save_manual_direct_step1, packageName), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.save_manual_direct_step2, packageName), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun L2GTab(
    packageName: String,
    l2gContents: List<String>,
    launcherSavesExists: Boolean,
    useShizuku: Boolean,
    isOperating: Boolean,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    refreshStatus: () -> Unit,
    setOperating: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Description
        item {
            Text(stringResource(R.string.save_l2g_desc_detail), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Launcher saves status
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (launcherSavesExists) Icons.Outlined.CheckCircle else Icons.Outlined.Error,
                        contentDescription = null, modifier = Modifier.size(16.dp),
                        tint = if (launcherSavesExists) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (launcherSavesExists) stringResource(R.string.save_game_launched) else stringResource(R.string.save_game_not_launched),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (launcherSavesExists) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // L2G contents
        if (l2gContents.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.save_l2g_contents), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        l2gContents.forEach { name ->
                            Row(modifier = Modifier.padding(start = 8.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(name, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.save_l2g_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Export to L2G button
        item {
            val canExport = !isOperating
            Card(modifier = Modifier.fillMaxWidth().clickable(enabled = canExport) {
                scope.launch {
                    setOperating(true)
                    val result = SaveDataManager.launcherExportToL2g(context, packageName)
                    setOperating(false)
                    result.fold(
                        onSuccess = { count ->
                            refreshStatus()
                            Toast.makeText(context, context.getString(R.string.save_l2g_export_done, count), Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { e ->
                            Toast.makeText(context, context.getString(R.string.save_l2g_export_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }, colors = CardDefaults.cardColors(
                containerColor = if (canExport) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Backup, contentDescription = null, modifier = Modifier.size(24.dp), tint = if (canExport) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.save_l2g_export_button), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.save_l2g_export_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Restore button
        item {
            val canRestore = l2gContents.isNotEmpty() && !isOperating
            Card(modifier = Modifier.fillMaxWidth().clickable(enabled = canRestore) {
                scope.launch {
                    setOperating(true)
                    val result = when {
                        useShizuku -> SaveDataManager.l2gRestoreViaShizuku(packageName)
                        else -> SaveDataManager.l2gRestoreDirect(packageName)
                    }
                    setOperating(false)
                    result.fold(
                        onSuccess = { count ->
                            Toast.makeText(context, context.getString(R.string.save_l2g_done, count), Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { e ->
                            Toast.makeText(context, context.getString(R.string.save_l2g_error, e.message ?: ""), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }, colors = CardDefaults.cardColors(
                containerColor = if (canRestore) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(24.dp), tint = if (canRestore) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.save_l2g_button), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.save_l2g_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Clear L2G
        if (l2gContents.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth().clickable(enabled = !isOperating) {
                    SaveDataManager.clearL2G(packageName)
                    refreshStatus()
                    Toast.makeText(context, R.string.save_backup_cleared, Toast.LENGTH_SHORT).show()
                }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.save_clear_l2g), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Manual guide
        item { HorizontalDivider() }
        item {
            Text(stringResource(R.string.save_manual_guide), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (useShizuku) {
                        Text(stringResource(R.string.save_manual_shizuku_title), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(R.string.save_manual_saf_step3), style = MaterialTheme.typography.bodyMedium)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(stringResource(R.string.save_adb_step1), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.save_adb_restore_cmd, packageName), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(stringResource(R.string.save_manual_direct_step3), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
