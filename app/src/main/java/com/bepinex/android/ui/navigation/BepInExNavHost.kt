package com.bepinex.android.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.bepinex.android.shortcut.ModpackShortcutHelper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bepinex.android.BepInExPaths
import com.bepinex.android.GameDetector
import com.bepinex.android.R
import com.bepinex.android.log.BepInExLogReader
import com.bepinex.android.modpack.ModpackManager
import com.bepinex.android.modpack.ModpackMeta
import com.bepinex.android.settings.AppSettings
import com.bepinex.android.ui.components.ConfigEditorDialog
import com.bepinex.android.ui.screens.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun resolveModpackFile(modpackDirectory: File, target: File): File? = runCatching {
    val canonicalRoot = modpackDirectory.canonicalFile
    val canonicalTarget = target.canonicalFile
    val isInsideModpack = canonicalTarget != canonicalRoot &&
        canonicalTarget.toPath().startsWith(canonicalRoot.toPath())

    canonicalTarget.takeIf { isInsideModpack && it.isFile }
}.getOrNull()

private fun isProtectedModpackFile(modpackDirectory: File, target: File): Boolean {
    val canonicalRoot = runCatching { modpackDirectory.canonicalFile }.getOrNull() ?: return false
    val canonicalTarget = resolveModpackFile(canonicalRoot, target) ?: return false
    return canonicalTarget.name.equals("modpack.json", ignoreCase = true) &&
        canonicalTarget.parentFile?.canonicalFile == canonicalRoot
}

private val editableTextExtensions = setOf(
    "cfg", "conf", "config", "ini", "json", "json5", "log", "lua", "txt",
    "xml", "yaml", "yml", "toml", "properties", "md", "csv", "cs", "js",
    "ts", "sh", "bat", "ps1"
)

private fun isEditableTextFile(file: File): Boolean =
    file.isFile && file.extension.lowercase() in editableTextExtensions

private fun isPluginsDll(modpackDirectory: File, target: File): Boolean {
    val canonicalRoot = runCatching { modpackDirectory.canonicalFile }.getOrNull() ?: return false
    val canonicalTarget = resolveModpackFile(canonicalRoot, target) ?: return false
    val relativeSegments = canonicalTarget.relativeTo(canonicalRoot)
        .invariantSeparatorsPath
        .split('/')

    return relativeSegments.firstOrNull()?.equals("plugins", ignoreCase = true) == true &&
        canonicalTarget.extension.equals("dll", ignoreCase = true)
}

private fun isSaveImportRoute(route: String?): Boolean = route == NavRoutes.SAVE_IMPORT

private fun deleteModpackFile(modpackDirectory: File, target: File): Boolean {
    val safeTarget = resolveModpackFile(modpackDirectory, target) ?: return false
    if (isProtectedModpackFile(modpackDirectory, safeTarget)) return false
    return runCatching { safeTarget.delete() }.getOrDefault(false)
}

/**
 * Root navigation host with bottom navigation bar.
 */
@Composable
fun BepInExNavHost(
    scope: CoroutineScope,
    // Game state
    detectedGames: List<GameDetector.DetectedGame>,
    selectedGame: GameDetector.DetectedGame?,
    isScanning: Boolean,
    isFrameworkReady: Boolean,
    isExtracting: Boolean,
    extractionStatus: String,
    // Settings state
    themeMode: AppSettings.ThemeMode,
    language: AppSettings.Language,
    dynamicColor: Boolean,
    animationDisabled: Boolean,
    // Callbacks
    onSelectGame: (GameDetector.DetectedGame) -> Unit,
    onRescan: () -> Unit,
    onLaunch: (modpackName: String?) -> Unit,
    onThemeChanged: (AppSettings.ThemeMode) -> Unit,
    onLanguageChanged: (AppSettings.Language) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onAnimationDisabledChanged: (Boolean) -> Unit,
    onClearBepInEx: (String) -> Unit,
    onClearDotnet: (String) -> Unit,
    onClearLibUnity: (String) -> Unit,
    onCopyGameResources: (String) -> Unit,
    onExportLogs: () -> Unit,
    onShowAnnouncement: () -> Unit = {},
    showIncompleteBanner: Boolean = false
) {
    val navController = rememberNavController()
    val modpackManager = remember { ModpackManager() }
    val context = LocalContext.current
    val composeScope = rememberCoroutineScope()

    // State for modpack list
    var modpacks by remember { mutableStateOf<List<ModpackMeta>>(emptyList()) }
    var activeModpackName by remember { mutableStateOf<String?>(null) }
    var modpackRefreshKey by remember { mutableStateOf(0) }
    var modpackIconRefreshKey by remember { mutableStateOf(0) }

    // Load active modpack on game selection
    LaunchedEffect(selectedGame?.packageName) {
        selectedGame?.let { game ->
            activeModpackName = AppSettings.getActiveModpack(context, game.packageName)
        }
    }

    // Refresh modpack list when game changes or refresh key bumps
    LaunchedEffect(selectedGame?.packageName, modpackRefreshKey) {
        selectedGame?.let { game ->
            modpacks = modpackManager.listModpacks(game.packageName)
        }
    }

    // File picker triggers (launcher must be at composable top level)
    var importModpackTrigger by remember { mutableStateOf(false) }
    var addModTrigger by remember { mutableStateOf<String?>(null) }

    // Import modpack file picker — inline import to avoid navigation reset
    val importModpackLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val game = selectedGame
            if (game != null) {
                kotlinx.coroutines.MainScope().launch(kotlinx.coroutines.Dispatchers.IO) {
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    val displayName = cursor?.use {
                        if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)) else null
                    }
                    val zipName = displayName?.removeSuffix(".zip")?.removeSuffix(".ZIP")
                    modpackManager.importModpack(game.packageName, uri, context, zipName)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        modpackRefreshKey++
                    }
                }
            }
        }
    }

    // Add mod to modpack file picker — inline import to avoid navigation reset
    val addModLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val targetModpack = addModTrigger
        if (uri != null && targetModpack != null) {
            val game = selectedGame
            if (game != null) {
                kotlinx.coroutines.MainScope().launch(kotlinx.coroutines.Dispatchers.IO) {
                    // Must use Activity context — URI permission is on the Activity
                    modpackManager.addModFromUri(context, game.packageName, targetModpack, uri)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        modpackRefreshKey++
                    }
                }
            }
        }
        addModTrigger = null
    }

    LaunchedEffect(importModpackTrigger) {
        if (importModpackTrigger) {
            importModpackLauncher.launch(arrayOf("application/zip", "*/*"))
            importModpackTrigger = false
        }
    }

    LaunchedEffect(addModTrigger) {
        if (addModTrigger != null) {
            addModLauncher.launch(arrayOf("*/*"))
        }
    }

    // Start watching BepInEx log when game or active modpack changes
    LaunchedEffect(selectedGame?.packageName, activeModpackName) {
        selectedGame?.let { game ->
            val active = AppSettings.getActiveModpack(context, game.packageName)
            val logFile = if (active.isNullOrEmpty()) {
                BepInExPaths.getLogFile(game.packageName)
            } else {
                BepInExPaths.getModpackLogFile(game.packageName, active)
            }
            // If active modpack log doesn't exist yet, fall back to active runtime log
            val targetLog = if (logFile.exists()) logFile else BepInExPaths.getLogFile(game.packageName)
            BepInExLogReader.startWatchingFile(targetLog, scope)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute == NavRoutes.MAIN

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 3 })

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = if (animationDisabled) EnterTransition.None
                    else slideInVertically(spring()) { height -> height } + fadeIn(spring()),
                exit = ExitTransition.None
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    NavigationBarItem(
                        selected = pagerState.currentPage == 0,
                        onClick = {
                            if (pagerState.currentPage != 0) {
                                composeScope.launch {
                                    if (animationDisabled) pagerState.scrollToPage(0)
                                    else pagerState.animateScrollToPage(0)
                                }
                            }
                        },
                        icon = { Icon(Icons.Filled.SportsEsports, stringResource(R.string.nav_games)) },
                        label = { Text(stringResource(R.string.nav_games)) }
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == 1,
                        onClick = {
                            if (selectedGame != null && pagerState.currentPage != 1) {
                                composeScope.launch {
                                    if (animationDisabled) pagerState.scrollToPage(1)
                                    else pagerState.animateScrollToPage(1)
                                }
                            }
                        },
                        enabled = selectedGame != null,
                        icon = { Icon(Icons.Filled.FolderZip, stringResource(R.string.nav_modpacks)) },
                        label = { Text(stringResource(R.string.nav_modpacks)) }
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == 2,
                        onClick = {
                            if (selectedGame != null && pagerState.currentPage != 2) {
                                composeScope.launch {
                                    if (animationDisabled) pagerState.scrollToPage(2)
                                    else pagerState.animateScrollToPage(2)
                                }
                            }
                        },
                        enabled = selectedGame != null,
                        icon = { Icon(Icons.Filled.Settings, stringResource(R.string.nav_settings)) },
                        label = { Text(stringResource(R.string.nav_settings)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavRoutes.MAIN,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            enterTransition = {
                if (animationDisabled) EnterTransition.None
                else if (isSaveImportRoute(targetState.destination.route)) {
                    fadeIn(animationSpec = tween(durationMillis = 110))
                } else {
                    slideInHorizontally(
                        animationSpec = tween(
                            durationMillis = 190,
                            easing = FastOutSlowInEasing
                        ),
                        initialOffsetX = { it / 4 }
                    ) + fadeIn(animationSpec = tween(durationMillis = 150))
                }
            },
            exitTransition = {
                if (animationDisabled) ExitTransition.None
                else if (isSaveImportRoute(targetState.destination.route)) {
                    fadeOut(animationSpec = tween(durationMillis = 80))
                } else {
                    slideOutHorizontally(
                        animationSpec = tween(
                            durationMillis = 160,
                            easing = LinearOutSlowInEasing
                        ),
                        targetOffsetX = { -it / 6 }
                    ) + fadeOut(animationSpec = tween(durationMillis = 120))
                }
            },
            popEnterTransition = {
                if (animationDisabled) EnterTransition.None
                else if (isSaveImportRoute(initialState.destination.route)) {
                    fadeIn(animationSpec = tween(durationMillis = 110))
                } else {
                    slideInHorizontally(
                        animationSpec = tween(
                            durationMillis = 190,
                            easing = FastOutSlowInEasing
                        ),
                        initialOffsetX = { -it / 6 }
                    ) + fadeIn(animationSpec = tween(durationMillis = 150))
                }
            },
            popExitTransition = {
                if (animationDisabled) ExitTransition.None
                else if (isSaveImportRoute(initialState.destination.route)) {
                    fadeOut(animationSpec = tween(durationMillis = 80))
                } else {
                    slideOutHorizontally(
                        animationSpec = tween(
                            durationMillis = 160,
                            easing = LinearOutSlowInEasing
                        ),
                        targetOffsetX = { it / 4 }
                    ) + fadeOut(animationSpec = tween(durationMillis = 120))
                }
            }
        ) {
                // Main pager — 3 pages: Games, Modpacks, Settings
                composable(route = NavRoutes.MAIN) {
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 2
                    ) { page ->
                        when (page) {
                            0 -> GameScreen(
                                detectedGames = detectedGames,
                                selectedGame = selectedGame,
                                isScanning = isScanning,
                                isFrameworkReady = isFrameworkReady,
                                isExtracting = isExtracting,
                                extractionStatus = extractionStatus,
                                activeModpackName = activeModpackName,
                                activeModpackModCount = if (activeModpackName != null)
                                    modpacks.find { it.name == activeModpackName }?.modCount ?: 0 else 0,
                                onSelectGame = onSelectGame,
                                onRescan = onRescan,
                                onLaunch = { onLaunch(activeModpackName) },
                                onManageSaves = {
                                    selectedGame?.let { game ->
                                        navController.navigate(NavRoutes.saveImport(game.packageName))
                                    }
                                },
                                onExportLogs = onExportLogs,
                                onShowAnnouncement = onShowAnnouncement,
                                showIncompleteBanner = showIncompleteBanner
                            )
                            1 -> {
                                val packageName = selectedGame?.packageName ?: ""
                                LaunchedEffect(packageName, modpackRefreshKey) {
                                    if (packageName.isNotEmpty()) {
                                        modpacks = withContext(Dispatchers.IO) { modpackManager.listModpacks(packageName) }
                                        activeModpackName = AppSettings.getActiveModpack(context, packageName)
                                    }
                                }
                                ModpackListScreen(
                                    packageName = packageName,
                                    targetGameLabel = selectedGame?.label ?: packageName,
                                    modpacks = modpacks,
                                    activeModpackName = activeModpackName,
                                    iconRefreshKey = modpackIconRefreshKey,
                                    onCreateModpack = { name, createShortcut, iconBitmap ->
                                        val created = modpackManager.createModpack(packageName, name)
                                        if (created != null) {
                                            modpackManager.updateMeta(packageName, created.name, createShortcut)
                                            if (iconBitmap != null) {
                                                modpackManager.saveModpackIcon(packageName, created.name, iconBitmap, "png")
                                            }
                                            if (createShortcut) {
                                                ModpackShortcutHelper.createShortcut(context, packageName, created.name, created.name)
                                            }
                                        }
                                        modpacks = modpackManager.listModpacks(packageName)
                                    },
                                    onDeleteModpack = { name ->
                                        modpackManager.deleteModpack(packageName, name)
                                        if (activeModpackName == name) activeModpackName = null
                                        modpacks = modpackManager.listModpacks(packageName)
                                    },
                                    onEditModpack = { oldName, newName, createShortcut, iconBitmap ->
                                        val normalizedName = modpackManager.normalizeModpackName(newName)
                                        val success = modpackManager.renameModpack(packageName, oldName, newName)
                                        if (success) {
                                            if (activeModpackName == oldName) {
                                                activeModpackName = normalizedName
                                                AppSettings.setActiveModpack(context, packageName, normalizedName)
                                            }
                                            modpackManager.updateMeta(packageName, normalizedName, createShortcut)
                                            if (createShortcut) {
                                                if (oldName != normalizedName) {
                                                    ModpackShortcutHelper.removeShortcut(context, packageName, oldName)
                                                }
                                                ModpackShortcutHelper.createShortcut(context, packageName, normalizedName, newName)
                                            } else {
                                                ModpackShortcutHelper.removeShortcut(context, packageName, normalizedName)
                                            }
                                            if (iconBitmap != null) {
                                                val ext = when {
                                                    iconBitmap.config == android.graphics.Bitmap.Config.RGB_565 -> "jpg"
                                                    else -> "png"
                                                }
                                                modpackManager.saveModpackIcon(packageName, normalizedName, iconBitmap, ext)
                                            }
                                            modpacks = modpackManager.listModpacks(packageName)
                                            modpackRefreshKey++
                                            modpackIconRefreshKey++
                                        }
                                        success
                                    },
                                    onSelectModpack = { name ->
                                        val previous = activeModpackName
                                        if (previous != name) {
                                            modpackManager.persistRuntimeState(packageName, previous)
                                            if (name == null) {
                                                modpackManager.clearActiveMods(packageName)
                                            } else {
                                                modpackManager.applyModpack(packageName, name)
                                            }
                                            AppSettings.setActiveModpack(context, packageName, name)
                                            activeModpackName = name
                                            modpackRefreshKey++
                                        }
                                    },
                                    onOpenModpack = { name ->
                                        navController.navigate(NavRoutes.modpackDetail(packageName, name))
                                    },
                                    onExportModpack = { name ->
                                        val outputFile = java.io.File(
                                            android.os.Environment.getExternalStorageDirectory(),
                                            "PVZRH_Launcher/export/${name}.zip"
                                        )
                                        outputFile.parentFile?.mkdirs()
                                        modpackManager.exportModpack(packageName, name, outputFile)
                                    },
                                    onImportModpack = { importModpackTrigger = true }
                                )
                            }
                            2 -> {
                                val packageName = selectedGame?.packageName ?: ""
                                val settingsContext = LocalContext.current
                                var floatingLogInGame by remember {
                                    mutableStateOf(AppSettings.isFloatingLogInGameEnabled(settingsContext))
                                }
                                var useUnstrippedLibUnity by remember {
                                    mutableStateOf(AppSettings.isUseUnstrippedLibUnity(settingsContext))
                                }
                                var dynamicColor by remember {
                                    mutableStateOf(AppSettings.isDynamicColorEnabled(settingsContext))
                                }
                                var animationDisabledSetting by remember {
                                    mutableStateOf(AppSettings.isAnimationDisabled(settingsContext))
                                }
                                SettingsScreen(
                                    themeMode = themeMode,
                                    language = language,
                                    dynamicColor = dynamicColor,
                                    animationDisabled = animationDisabledSetting,
                                    floatingLogInGame = floatingLogInGame,
                                    useUnstrippedLibUnity = useUnstrippedLibUnity,
                                    onNavigateToAbout = { navController.navigate(NavRoutes.ABOUT) },
                                    onThemeChanged = onThemeChanged,
                                    onLanguageChanged = onLanguageChanged,
                                    onDynamicColorChanged = { enabled ->
                                        AppSettings.setDynamicColorEnabled(settingsContext, enabled)
                                        dynamicColor = enabled
                                        onDynamicColorChanged(enabled)
                                    },
                                    onAnimationDisabledChanged = { disabled ->
                                        AppSettings.setAnimationDisabled(settingsContext, disabled)
                                        animationDisabledSetting = disabled
                                    },
                                    onFloatingLogInGameChanged = { enabled ->
                                        AppSettings.setFloatingLogInGameEnabled(settingsContext, enabled)
                                        floatingLogInGame = enabled
                                    },
                                    onUseUnstrippedLibUnityChanged = { enabled ->
                                        AppSettings.setUseUnstrippedLibUnity(settingsContext, enabled)
                                        useUnstrippedLibUnity = enabled
                                    },
                                    onClearBepInEx = { onClearBepInEx(packageName) },
                                    onClearDotnet = { onClearDotnet(packageName) },
                                    onClearLibUnity = { onClearLibUnity(packageName) },
                                    onCopyGameResources = { onCopyGameResources(packageName) },
                                    isLanguageIncompleteShown = AppSettings.isLanguageIncompleteShown(settingsContext),
                                    onLanguageIncompleteShown = { AppSettings.setLanguageIncompleteShown(settingsContext, true) }
                                )
                            }
                        }
                    }
                }

                // Modpack detail
                composable(
                    route = NavRoutes.MODPACK_DETAIL,
                    arguments = listOf(
                        navArgument("packageName") { type = NavType.StringType },
                        navArgument("modpackName") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val packageName = backStackEntry.arguments?.getString("packageName") ?: return@composable
                    val modpackName = backStackEntry.arguments?.getString("modpackName") ?: return@composable

                    var mods by remember(packageName, modpackName, modpackRefreshKey) {
                        mutableStateOf(modpackManager.listMods(packageName, modpackName))
                    }
                    val configFiles = remember(packageName, modpackName, modpackRefreshKey) {
                        modpackManager.listConfigs(packageName, modpackName)
                    }

                    ModpackDetailScreen(
                        modpackName = modpackName,
                        mods = mods,
                        configFiles = configFiles,
                        onNavigateBack = { navController.popBackStack() },
                        onAddMod = { addModTrigger = modpackName },
                        onDeleteMod = { file ->
                            modpackManager.removeMod(file)
                            mods = modpackManager.listMods(packageName, modpackName)
                        },
                        onOpenConfig = { configFile ->
                            navController.navigate(NavRoutes.configEditor(configFile.absolutePath))
                        },
                        onViewLog = {
                            navController.navigate(NavRoutes.logViewer(packageName, modpackName))
                        },
                        onBrowseModFiles = {
                            navController.navigate(NavRoutes.modFileBrowser(packageName, modpackName))
                        },
                        onExportModpack = {
                            val outputFile = java.io.File(
                                context.cacheDir,
                                "${modpackName}.zip"
                            )
                            outputFile.parentFile?.mkdirs()
                            val success = modpackManager.exportModpack(packageName, modpackName, outputFile)
                            if (success) {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    outputFile
                                )
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, modpackName)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Modpack"))
                            } else {
                                android.widget.Toast.makeText(context, "Export failed", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                }

                // Modpack file browser
                composable(
                    route = NavRoutes.MOD_FILE_BROWSER,
                    arguments = listOf(
                        navArgument("packageName") { type = NavType.StringType },
                        navArgument("modpackName") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val packageName = backStackEntry.arguments?.getString("packageName")
                        ?: return@composable
                    val modpackName = backStackEntry.arguments?.getString("modpackName")
                        ?: return@composable
                    val modpackDirectory = BepInExPaths.getModpackDir(packageName, modpackName)

                    ModFileBrowserScreen(
                        rootDirectory = modpackDirectory,
                        onNavigateBack = { navController.popBackStack() },
                        onFileClick = { file ->
                            val safeFile = resolveModpackFile(modpackDirectory, file)
                            if (safeFile != null &&
                                !isProtectedModpackFile(modpackDirectory, safeFile) &&
                                isEditableTextFile(safeFile)
                            ) {
                                navController.navigate(NavRoutes.configEditor(safeFile.absolutePath)) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onDeleteFile = { file ->
                            val deletedPluginDll = isPluginsDll(modpackDirectory, file)
                            deleteModpackFile(modpackDirectory, file).also { deleted ->
                                if (deleted && deletedPluginDll) {
                                    modpackRefreshKey++
                                }
                            }
                        }
                    )
                }

                // Log Viewer
                composable(
                    route = NavRoutes.LOG_VIEWER,
                    arguments = listOf(
                        navArgument("packageName") { type = NavType.StringType },
                        navArgument("modpackName") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val pkg = backStackEntry.arguments?.getString("packageName") ?: return@composable
                    val mpName = backStackEntry.arguments?.getString("modpackName") ?: return@composable
                    val logFile = com.bepinex.android.BepInExPaths.getModpackLogFile(pkg, mpName)
                    LogViewerScreen(
                        logFilePath = logFile.absolutePath,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                // Config Editor
                composable(
                    route = NavRoutes.CONFIG_EDITOR,
                    arguments = listOf(navArgument("filePath") { type = NavType.StringType })
                ) { backStackEntry ->
                    val encodedPath = backStackEntry.arguments?.getString("filePath") ?: return@composable
                    val filePath = java.net.URLDecoder.decode(encodedPath, "UTF-8")
                    val file = java.io.File(filePath)
                    ConfigEditorDialog(
                        configFile = file,
                        onDismiss = { navController.popBackStack() },
                        onSave = { f, content ->
                            val success = runCatching {
                                f.writeText(content)
                            }.isSuccess
                            success.also {
                                if (success) navController.popBackStack()
                            }
                        }
                    )
                }

                // Save Import
                composable(
                    route = NavRoutes.SAVE_IMPORT,
                    arguments = listOf(
                        navArgument("packageName") { type = NavType.StringType }
                    )
                ) { backStackEntry ->
                    val packageName = backStackEntry.arguments?.getString("packageName") ?: return@composable
                    SaveImportScreen(
                        packageName = packageName,
                        onBack = { navController.popBackStack() }
                    )
                }

                // About
                composable(
                    route = NavRoutes.ABOUT
                ) {
                    val context = LocalContext.current
                    val versionName = runCatching {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.170"
                    }.getOrDefault("0.170")
                    AboutScreen(
                        versionName = versionName,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToCredits = { navController.navigate(NavRoutes.CREDITS) }
                    )
                }

                // Credits
                composable(
                    route = NavRoutes.CREDITS
                ) {
                    CreditsScreen(onNavigateBack = { navController.popBackStack() })
                }
            }
    }
}
