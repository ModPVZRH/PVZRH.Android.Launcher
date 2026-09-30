package com.bepinex.android.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bepinex.android.R
import com.bepinex.android.market.MarketApi
import com.bepinex.android.market.MarketInstaller
import com.bepinex.android.market.MarketMod
import com.bepinex.android.modpack.ModpackManager
import com.bepinex.android.modpack.ModpackMeta
import com.bepinex.android.modpack.PeekedModpackInfo
import com.bepinex.android.settings.AppSettings
import com.bepinex.android.shortcut.ModpackShortcutHelper
import com.bepinex.android.ui.components.MarkdownContent
import com.bepinex.android.ui.theme.GlassAlertDialog
import com.bepinex.android.ui.theme.glassContainerColor
import com.bepinex.android.ui.theme.glassSurface
import com.bepinex.android.ui.theme.glassTopBarColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class MarketDetailTab { Info, Download, Source }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketModDetailScreen(
    modId: String,
    onBack: () -> Unit,
    gameVersion: String = "",
    packageName: String = "",
    gameLabel: String = "",
    onModpacksChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferChinese = remember { AppSettings.isChineseUi(context) }
    val modpackManager = remember { ModpackManager() }
    var mod by remember { mutableStateOf(MarketApi.findCachedMod(modId)) }
    var isLoading by remember { mutableStateOf(mod == null) }
    var tab by remember { mutableStateOf(MarketDetailTab.Info) }
    var installJob by remember { mutableStateOf<Job?>(null) }
    var installProgress by remember { mutableStateOf<MarketInstaller.Progress?>(null) }
    var importing by remember { mutableStateOf(false) }
    var workDir by remember { mutableStateOf<File?>(null) }
    var pendingPlugins by remember { mutableStateOf<MarketInstaller.PreparedKind.Plugins?>(null) }
    var pendingModpack by remember { mutableStateOf<MarketInstaller.PreparedKind.Modpack?>(null) }
    var versionMismatch by remember { mutableStateOf<PeekedModpackInfo?>(null) }
    var showCreatePack by remember { mutableStateOf(false) }
    var modpacks by remember { mutableStateOf<List<ModpackMeta>>(emptyList()) }

    LaunchedEffect(modId) {
        val cached = MarketApi.findCachedMod(modId)
        if (cached != null) {
            mod = cached
            isLoading = false
        } else {
            isLoading = true
            mod = withContext(Dispatchers.IO) { MarketApi.loadModDetail(context, modId) }
            isLoading = false
        }
        if (mod != null) {
            withContext(Dispatchers.IO) { MarketApi.recordView(context, modId) }
            MarketApi.findCachedMod(modId)?.let { mod = it }
        }
    }

    LaunchedEffect(packageName, pendingPlugins, showCreatePack) {
        if (packageName.isNotBlank() && pendingPlugins != null) {
            modpacks = withContext(Dispatchers.IO) { modpackManager.listModpacks(packageName) }
        }
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun cleanupInstall() {
        MarketInstaller.cleanup(workDir)
        workDir = null
        pendingPlugins = null
        pendingModpack = null
        versionMismatch = null
        showCreatePack = false
        installProgress = null
        importing = false
    }

    fun cancelInstall() {
        val job = installJob
        if (job?.isActive != true && !importing && installProgress == null) return
        toast(context.getString(R.string.market_direct_cancelled))
        installProgress = null
        importing = false
        job?.cancel()
    }

    DisposableEffect(Unit) {
        onDispose {
            installJob?.cancel()
            MarketInstaller.cleanup(workDir)
        }
    }

    fun openUrl(url: String): Boolean {
        return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        }.getOrElse {
            Toast.makeText(context, context.getString(R.string.market_open_failed), Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun recordDownload() {
        scope.launch {
            withContext(Dispatchers.IO) { MarketApi.recordDownload(context, modId) }
            MarketApi.findCachedMod(modId)?.let { mod = it }
        }
    }

    fun openDownload(url: String) {
        if (!openUrl(url)) return
        recordDownload()
    }

    fun importPreparedModpack(scanRoot: File, fallbackName: String) {
        if (importing || packageName.isBlank()) return
        installProgress = null
        importing = true
        installJob = scope.launch {
            try {
                val imported = withContext(Dispatchers.IO) {
                    modpackManager.importModpackFromDirectory(packageName, scanRoot, fallbackName)
                }
                if (imported != null) {
                    onModpacksChanged()
                    toast(context.getString(R.string.market_direct_imported_modpack, imported.name))
                    cleanupInstall()
                } else {
                    toast(context.getString(R.string.modpack_invalid_archive))
                    importing = false
                }
            } catch (_: CancellationException) {
                cleanupInstall()
            } catch (_: Exception) {
                toast(context.getString(R.string.import_failed))
                importing = false
            }
        }
    }

    fun importPluginsInto(modpackName: String, plugins: MarketInstaller.PreparedKind.Plugins) {
        if (importing || packageName.isBlank()) return
        installProgress = null
        importing = true
        installJob = scope.launch {
            try {
                val count = withContext(Dispatchers.IO) {
                    val current = mod
                    modpackManager.addScannedPlugins(
                        packageName,
                        modpackName,
                        plugins.dlls,
                        plugins.scanRoot,
                        displayName = current?.modName?.ifBlank { current.englishName }
                    )
                }
                if (count > 0) {
                    onModpacksChanged()
                    val label = plugins.dlls.joinToString { it.name }
                    toast(context.getString(R.string.market_direct_imported_mod, label, modpackName))
                    cleanupInstall()
                } else {
                    toast(context.getString(R.string.import_failed))
                    importing = false
                }
            } catch (_: CancellationException) {
                cleanupInstall()
            } catch (_: Exception) {
                toast(context.getString(R.string.import_failed))
                importing = false
            }
        }
    }

    fun startDirectInstall(url: String) {
        if (installJob?.isActive == true || importing) return
        if (packageName.isBlank()) {
            toast(context.getString(R.string.market_direct_need_game))
            return
        }
        val item = mod ?: return
        cleanupInstall()
        installJob = scope.launch {
            try {
                val prepared = withContext(Dispatchers.IO) {
                    MarketInstaller.downloadAndPrepare(
                        context = context,
                        mod = item,
                        url = url,
                        expectedSize = item.fileSize
                    ) { progress ->
                        withContext(Dispatchers.Main.immediate) {
                            if (isActive) installProgress = progress
                        }
                    }
                }
                workDir = prepared.workDir
                recordDownload()
                when (val kind = prepared.kind) {
                    is MarketInstaller.PreparedKind.Modpack -> {
                        val peeked = withContext(Dispatchers.IO) {
                            modpackManager.peekModpackInfoFromDirectory(kind.scanRoot)
                        }
                        if (peeked != null &&
                            !ModpackManager.isGameVersionCompatible(peeked.gameVersion, gameVersion)
                        ) {
                            pendingModpack = kind
                            versionMismatch = peeked
                            installProgress = null
                        } else {
                            val fallback = peeked?.name
                                ?: item.englishName.ifBlank { item.modName }.ifBlank { "imported" }
                            importPreparedModpack(kind.scanRoot, fallback)
                        }
                    }
                    is MarketInstaller.PreparedKind.Plugins -> {
                        pendingPlugins = kind
                        installProgress = null
                    }
                    MarketInstaller.PreparedKind.NoMatch -> {
                        toast(context.getString(R.string.market_direct_no_match))
                        cleanupInstall()
                    }
                }
            } catch (_: CancellationException) {
                cleanupInstall()
            } catch (_: MarketInstaller.UnsupportedArchiveException) {
                toast(context.getString(R.string.market_direct_unsupported_archive))
                cleanupInstall()
            } catch (_: Exception) {
                toast(context.getString(R.string.market_direct_download_failed))
                cleanupInstall()
            }
        }
    }

    Scaffold(
        containerColor = glassContainerColor(MaterialTheme.colorScheme.background),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.market_detail)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                ),
                modifier = Modifier.glassSurface(
                    RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
                ),
                colors = glassTopBarColors()
            )
        }
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            mod == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.market_error),
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = onBack) {
                            Text(stringResource(R.string.back))
                        }
                    }
                }
            }
            else -> {
                val item = mod!!
                val installBusy = installProgress != null ||
                    importing ||
                    pendingPlugins != null ||
                    pendingModpack != null ||
                    showCreatePack
                val primaryUrl = if (item.canInstallDirect) {
                    item.downloadDirectUrl
                } else {
                    item.downloadCloudUrl.ifBlank { item.downloadDirectUrl }
                }
                val hasSource = item.videoUrl.isNotBlank()
                val hasCloudAlt = item.downloadCloudUrl.isNotBlank() &&
                    item.downloadCloudUrl != primaryUrl
                val tabs = remember(hasSource, hasCloudAlt) {
                    buildList {
                        add(MarketDetailTab.Info)
                        if (hasCloudAlt) add(MarketDetailTab.Download)
                        if (hasSource) add(MarketDetailTab.Source)
                    }
                }
                val visibleTab = if (tab in tabs) tab else MarketDetailTab.Info

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    MarketDetailHeroCard(
                        item = item,
                        preferChinese = preferChinese,
                        gameVersion = gameVersion,
                        primaryUrl = primaryUrl,
                        installEnabled = !installBusy,
                        onInstall = { url ->
                            if (item.canInstallDirect && url == item.downloadDirectUrl) {
                                startDirectInstall(url)
                            } else {
                                openDownload(url)
                            }
                        }
                    )

                    Spacer(Modifier.height(12.dp))

                    MarketDetailTabBar(
                        tabs = tabs,
                        selected = visibleTab,
                        onSelect = { tab = it }
                    )

                    Spacer(Modifier.height(12.dp))

                    when (visibleTab) {
                        MarketDetailTab.Info -> MarketInfoTab(
                            item = item,
                            onOpenVideo = { openUrl(item.videoUrl) }
                        )
                        MarketDetailTab.Download -> MarketDownloadTab(
                            item = item,
                            primaryUrl = primaryUrl,
                            installEnabled = !installBusy,
                            onCloudDownload = { openDownload(it) }
                        )
                        MarketDetailTab.Source -> MarketSourceTab(item, onOpenUrl = { openUrl(it) })
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    val progress = installProgress
    if (progress != null && pendingPlugins == null && versionMismatch == null) {
        MarketDirectProgressDialog(
            progress = progress,
            fileSizeHint = mod?.fileSize ?: 0L,
            onCancel = { cancelInstall() }
        )
    } else if (importing) {
        GlassAlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.modpack_import)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.market_direct_importing))
                }
            },
            confirmButton = {
                TextButton(onClick = { cancelInstall() }) {
                    Text(stringResource(R.string.modpack_import_cancel))
                }
            }
        )
    }

    versionMismatch?.let { peeked ->
        val scanRoot = pendingModpack?.scanRoot ?: return@let
        GlassAlertDialog(
            onDismissRequest = { cleanupInstall() },
            title = { Text(stringResource(R.string.modpack_game_version_mismatch_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.modpack_game_version_mismatch_message,
                        peeked.gameVersion,
                        gameVersion
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        versionMismatch = null
                        importPreparedModpack(
                            scanRoot,
                            peeked.name.ifBlank { mod?.englishName.orEmpty() }
                        )
                    }
                ) {
                    Text(stringResource(R.string.modpack_game_version_mismatch_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { cleanupInstall() }) {
                    Text(stringResource(R.string.modpack_import_cancel))
                }
            }
        )
    }

    pendingPlugins?.let { plugins ->
        if (!showCreatePack && !importing) {
            MarketDirectImportDialog(
                dllNames = plugins.dlls.map { it.name },
                modpacks = modpacks,
                onSelectModpack = { pack -> importPluginsInto(pack.name, plugins) },
                onCreateNew = { showCreatePack = true },
                onDismiss = { cleanupInstall() }
            )
        }
    }

    if (showCreatePack && pendingPlugins != null) {
        val item = mod
        CreateModpackDialog(
            targetGame = gameLabel.ifBlank { packageName },
            currentGameVersion = gameVersion,
            initialName = item?.let { marketMod ->
                marketMod.localizedName(preferChinese)
                    .ifBlank { marketMod.englishName }
                    .ifBlank { marketMod.modName }
            }.orEmpty(),
            onDismiss = { showCreatePack = false },
            onCreate = { name, createShortcut, bitmap, packGameVersion ->
                val plugins = pendingPlugins ?: return@CreateModpackDialog
                showCreatePack = false
                importing = true
                installJob = scope.launch {
                    try {
                        val createdName = withContext(Dispatchers.IO) {
                            val created = modpackManager.createModpack(
                                packageName,
                                name,
                                packGameVersion
                            ) ?: return@withContext null
                            modpackManager.updateMeta(packageName, created.name, createShortcut)
                            if (bitmap != null) {
                                modpackManager.saveModpackIcon(
                                    packageName,
                                    created.name,
                                    bitmap,
                                    "png"
                                )
                            }
                            val count = modpackManager.addScannedPlugins(
                                packageName,
                                created.name,
                                plugins.dlls,
                                plugins.scanRoot,
                                displayName = item?.let { it.modName.ifBlank { it.englishName } }
                            )
                            if (count <= 0) null else created.name
                        }
                        if (createdName == null) {
                            toast(context.getString(R.string.modpack_rename_failed))
                            importing = false
                            showCreatePack = true
                        } else {
                            if (createShortcut) {
                                ModpackShortcutHelper.createShortcut(
                                    context,
                                    packageName,
                                    createdName,
                                    createdName
                                )
                            }
                            onModpacksChanged()
                            val label = plugins.dlls.joinToString { it.name }
                            toast(
                                context.getString(
                                    R.string.market_direct_imported_mod,
                                    label,
                                    createdName
                                )
                            )
                            cleanupInstall()
                        }
                    } catch (_: CancellationException) {
                        cleanupInstall()
                    } catch (_: Exception) {
                        toast(context.getString(R.string.import_failed))
                        importing = false
                        showCreatePack = true
                    }
                }
            }
        )
    }
}

@Composable
private fun MarketDetailHeroCard(
    item: MarketMod,
    preferChinese: Boolean,
    gameVersion: String,
    primaryUrl: String,
    installEnabled: Boolean = true,
    onInstall: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(MarketCardShape),
        shape = MarketCardShape,
        colors = marketCardColors(),
        elevation = marketCardElevation()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModIcon(
                    url = item.iconUrl,
                    name = item.localizedName(preferChinese),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.localizedName(preferChinese),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        MarketTagChip(item)
                    }
                    Text(
                        text = item.displayAuthor.ifBlank {
                            stringResource(R.string.market_unknown_author)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.supportedVersions.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        MarketCompatibleLabel(
                            versions = item.supportedVersions,
                            gameVersion = gameVersion,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    if (item.categoryName.isNotBlank() || item.tags.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.categoryName.isNotBlank()) {
                                Text(
                                    text = item.categoryName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (item.tags.isNotEmpty()) {
                                MarketApiTagRow(tags = item.tags)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(
                                R.string.market_downloads_count,
                                formatDownloadCount(item.downloadCount)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            imageVector = Icons.Filled.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = relativeTimeLabel(item.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            com.bepinex.android.ui.liquid.LiquidButton(
                onClick = { if (primaryUrl.isNotBlank()) onInstall(primaryUrl) },
                enabled = primaryUrl.isNotBlank() && installEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                val canInstall = item.canInstallDirect
                Icon(
                    imageVector = if (canInstall) Icons.Filled.Add else Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = when {
                        canInstall && item.version.isNotBlank() ->
                            stringResource(R.string.market_install, item.version)
                        canInstall ->
                            stringResource(R.string.market_install_no_version)
                        item.version.isNotBlank() ->
                            stringResource(R.string.market_download_with_version, item.version)
                        else ->
                            stringResource(R.string.market_download)
                    }
                )
            }
        }
    }
}

@Composable
private fun MarketDetailTabBar(
    tabs: List<MarketDetailTab>,
    selected: MarketDetailTab,
    onSelect: (MarketDetailTab) -> Unit
) {
    val selectedIndex = tabs.indexOf(selected).coerceAtLeast(0)
    val tabShape = RoundedCornerShape(12.dp)
    TabRow(
        selectedTabIndex = selectedIndex,
        modifier = Modifier
            .clip(tabShape)
            .glassSurface(tabShape),
        containerColor = glassContainerColor(MaterialTheme.colorScheme.surfaceVariant),
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        tabs.forEach { item ->
            Tab(
                selected = selected == item,
                onClick = { onSelect(item) },
                text = {
                    Text(
                        text = stringResource(
                            when (item) {
                                MarketDetailTab.Info -> R.string.market_tab_info
                                MarketDetailTab.Download -> R.string.market_tab_download
                                MarketDetailTab.Source -> R.string.market_tab_source
                            }
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun MarketInfoTab(
    item: MarketMod,
    onOpenVideo: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(MarketCardShape),
        shape = MarketCardShape,
        colors = marketCardColors(),
        elevation = marketCardElevation()
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            if (item.modDescription.isBlank()) {
                Text(
                    text = stringResource(R.string.market_no_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                MarkdownContent(markdown = item.modDescription)
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(MarketCardShape),
        shape = MarketCardShape,
        colors = marketCardColors(),
        elevation = marketCardElevation()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DetailRow(
                stringResource(R.string.market_latest_version),
                item.version.ifBlank { "—" }
            )
            DetailRow(
                stringResource(R.string.market_detail_size),
                if (item.fileSize > 0L) formatFileSize(item.fileSize) else "—"
            )
            DetailRow(
                stringResource(R.string.market_mod_id),
                item.englishName.ifBlank { item.id }
            )
            DetailRow(
                stringResource(R.string.market_created),
                relativeTimeLabel(item.createdAt.ifBlank { item.timestamp })
            )
            if (item.categoryName.isNotBlank()) {
                DetailRow(stringResource(R.string.market_detail_category), item.categoryName)
            }
            if (item.tags.isNotEmpty()) {
                DetailRow(
                    stringResource(R.string.market_detail_tags),
                    item.tags.joinToString(" · ") { it.name }
                )
            }
            if (item.gameName.isNotBlank()) {
                DetailRow(stringResource(R.string.market_detail_game), item.gameName)
            }
            if (item.supportedVersions.isNotBlank()) {
                DetailRow(stringResource(R.string.market_detail_compatible), item.supportedVersions)
            }
            if (item.videoUrl.isNotBlank()) {
                OutlinedButton(
                    onClick = onOpenVideo,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.market_open_video))
                }
            }
        }
    }
}

@Composable
private fun MarketDownloadTab(
    item: MarketMod,
    primaryUrl: String,
    installEnabled: Boolean = true,
    onCloudDownload: (String) -> Unit
) {
    val cloudUrl = item.downloadCloudUrl
    val showCloud = cloudUrl.isNotBlank() && cloudUrl != primaryUrl
    val hasAnyLink = item.downloadDirectUrl.isNotBlank() || cloudUrl.isNotBlank()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(MarketCardShape),
        shape = MarketCardShape,
        colors = marketCardColors(),
        elevation = marketCardElevation()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (!hasAnyLink) {
                Text(
                    text = stringResource(R.string.market_no_download),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                if (showCloud) {
                    OutlinedButton(
                        onClick = { onCloudDownload(cloudUrl) },
                        enabled = installEnabled,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.market_cloud_download))
                    }
                    Spacer(Modifier.height(16.dp))
                }
                DetailRow(
                    stringResource(R.string.market_detail_version),
                    item.version.ifBlank { "—" }
                )
                Spacer(Modifier.height(8.dp))
                DetailRow(
                    stringResource(R.string.market_detail_size),
                    if (item.fileSize > 0L) formatFileSize(item.fileSize) else "—"
                )
            }
        }
    }
}

@Composable
private fun MarketSourceTab(
    item: MarketMod,
    onOpenUrl: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(MarketCardShape),
        shape = MarketCardShape,
        colors = marketCardColors(),
        elevation = marketCardElevation()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (item.videoUrl.isBlank()) {
                Text(
                    text = stringResource(R.string.market_no_source),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                if (isEmbeddableHttpUrl(item.videoUrl)) {
                    MarketVideoBrowser(
                        url = item.videoUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedButton(
                    onClick = { onOpenUrl(item.videoUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.market_open_video))
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MarketVideoBrowser(
    url: String,
    modifier: Modifier = Modifier
) {
    val loading = remember { mutableStateOf(true) }
    val failed = remember { mutableStateOf(false) }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                            runCatching { loading.value = false }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                runCatching {
                                    loading.value = false
                                    failed.value = true
                                }
                            }
                        }
                    }
                    setOnTouchListener { view, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                                view.parent?.requestDisallowInterceptTouchEvent(true)
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                                view.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        false
                    }
                    tag = url
                    loadUrl(url, mapOf("Referer" to "https://www.bilibili.com/"))
                }
            },
            update = { webView ->
                if (webView.tag != url) {
                    webView.tag = url
                    loading.value = true
                    failed.value = false
                    webView.loadUrl(url, mapOf("Referer" to "https://www.bilibili.com/"))
                }
            },
            onRelease = { webView ->
                runCatching {
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.webChromeClient = null
                    webView.webViewClient = WebViewClient()
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    webView.destroy()
                }
            }
        )
        if (loading.value && !failed.value) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun isEmbeddableHttpUrl(url: String): Boolean {
    val scheme = runCatching { Uri.parse(url).scheme }.getOrNull()?.lowercase()
    return scheme == "http" || scheme == "https"
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 16.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MarketDirectProgressDialog(
    progress: MarketInstaller.Progress,
    fileSizeHint: Long,
    onCancel: () -> Unit
) {
    val total = progress.totalBytes.takeIf { it > 0L } ?: fileSizeHint
    val fraction = if (total > 0L) {
        (progress.bytesRead.toDouble() / total).coerceIn(0.0, 1.0).toFloat()
    } else {
        0f
    }
    val phaseText = stringResource(
        when (progress.phase) {
            MarketInstaller.Progress.Phase.Downloading -> R.string.market_direct_downloading
            MarketInstaller.Progress.Phase.Extracting -> R.string.market_direct_extracting
            MarketInstaller.Progress.Phase.Scanning -> R.string.market_direct_scanning
        }
    )
    GlassAlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.market_download)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(phaseText)
                if (progress.phase == MarketInstaller.Progress.Phase.Downloading && total > 0L) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(
                            R.string.bootstrap_download_progress,
                            (fraction * 100).toInt(),
                            formatFileSize(progress.bytesRead),
                            formatFileSize(total)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.modpack_import_cancel))
            }
        }
    )
}

@Composable
private fun MarketDirectImportDialog(
    dllNames: List<String>,
    modpacks: List<ModpackMeta>,
    onSelectModpack: (ModpackMeta) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit
) {
    val foundLabel = dllNames.joinToString()
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.market_direct_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.market_direct_import_message, foundLabel))
                if (modpacks.isEmpty()) {
                    Text(
                        text = stringResource(R.string.market_direct_no_modpacks),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringResource(R.string.market_direct_choose_modpack),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(modpacks, key = { it.name }) { pack ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectModpack(pack) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pack.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.modpack_mod_count_ratio,
                                            pack.enabledModCount,
                                            pack.modCount
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = stringResource(R.string.market_direct_import_existing),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreateNew) {
                Text(stringResource(R.string.market_direct_import_new))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirm_cancel))
            }
        }
    )
}
