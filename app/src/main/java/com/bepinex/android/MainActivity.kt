package com.bepinex.android

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.LocaleList
import android.provider.Settings
import android.widget.Toast
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.bepinex.android.log.BepInExLogReader
import com.bepinex.android.settings.AppSettings
import com.bepinex.android.ui.navigation.BepInExNavHost
import com.bepinex.android.ui.theme.BepInExTheme
import com.bepinex.android.update.UpdateChecker
import com.bepinex.android.update.AnnouncementDialog
import com.bepinex.android.update.UpdateDialog
import com.bepinex.android.update.BlockedDialog
import com.bepinex.android.update.CrashDialog
import com.bepinex.android.update.openUpdateUrl
import kotlinx.coroutines.*
import java.io.File

/**
 * Main launcher Activity with Compose UI for BepInEx mod management.
 */
class MainActivity : ComponentActivity() {

    private lateinit var fileExtractor: FileExtractor
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Per-game state
    private var detectedGames by mutableStateOf(listOf<GameDetector.DetectedGame>())
    private var selectedGame by mutableStateOf<GameDetector.DetectedGame?>(null)
    private var isScanning by mutableStateOf(true)
    private var isExtracting by mutableStateOf(false)
    private var extractionStatus by mutableStateOf("")
    private var storagePermissionGranted by mutableStateOf(false)
    private var hasPaused = false

    // Settings state
    private var themeMode by mutableStateOf(AppSettings.ThemeMode.SYSTEM)
    private var language by mutableStateOf(AppSettings.Language.SYSTEM)

    // Update check state
    private var updateInfo by mutableStateOf<UpdateChecker.UpdateInfo?>(null)
    private var showAnnouncement by mutableStateOf(false)
    private var showUpdate by mutableStateOf(false)
    private var showBlocked by mutableStateOf(false)
    private var isCheckingUpdate by mutableStateOf(true)

    // Crash detection state
    private var crashMonitorJob: Job? = null
    private var gameProcessAlive by mutableStateOf(false)
    private var showCrashDialog by mutableStateOf(false)
    private var crashInfo by mutableStateOf<CrashInfo?>(null)

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { checkStoragePermission(requestIfMissing = false) }

    companion object {
        /** Saved across activity recreations (e.g. language switch) */
        private var savedPackageName: String? = null
    }

    override fun attachBaseContext(newBase: Context?) {
        val ctx = newBase ?: return super.attachBaseContext(newBase)
        val lang = AppSettings.getLanguage(ctx)
        val locale = when (lang) {
            AppSettings.Language.ENGLISH -> Locale.forLanguageTag("en")
            AppSettings.Language.CHINESE -> Locale.forLanguageTag("zh-CN")
            AppSettings.Language.SYSTEM -> return super.attachBaseContext(newBase)
        }
        val config = Configuration(ctx.resources.configuration)
        config.setLocales(LocaleList(locale))
        super.attachBaseContext(ctx.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // A native crash cannot execute a finally block; archive the previous
        // run before initializing and appending to the current launcher log.
        DebugCrashCollector.collect(this)

        // Initialize settings first (before any Compose rendering)
        AppSettings.initialize(this)
        themeMode = AppSettings.getThemeMode(this)
        language = AppSettings.getLanguage(this)

        BepInExLog.init(this)
        BepInExLog.i("=== PVZRH Launcher ===")
        BepInExLog.i("Device: ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.SDK_INT}")

        fileExtractor = FileExtractor(this)

        // Compose is installed once; subsequent updates are driven by observable state.
        setupContent()
        checkStoragePermission(requestIfMissing = true)
        handleSharedText(intent)
        checkForUpdates()
    }

    // Storage permission

    private fun checkForUpdates() {
        scope.launch(Dispatchers.IO) {
            val info = UpdateChecker.fetchInfo(this@MainActivity)
            withContext(Dispatchers.Main) {
                updateInfo = info
                isCheckingUpdate = false
                if (info != null) {
                    val currentVersion = try {
                        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
                    } catch (_: Exception) { "" }

                    when {
                        !info.allowStart -> showBlocked = true
                        UpdateChecker.hasUpdate(currentVersion, info.version) -> {
                            showUpdate = true
                        }
                        info.announcementDate.isNotEmpty()
                            && info.announcementDate != AppSettings.getLastSeenAnnouncementDate(this@MainActivity) -> {
                            showAnnouncement = true
                        }
                    }
                }
            }
        }
    }

    private fun onDismissAnnouncement() {
        showAnnouncement = false
        updateInfo?.let { AppSettings.setLastSeenAnnouncementDate(this, it.announcementDate) }
    }

    private fun onDismissUpdate() {
        showUpdate = false
        // Show announcement if available and not yet seen. Compose observes both
        // state changes, so dismissing the update always removes it immediately.
        updateInfo?.let {
            if (it.announcementDate.isNotEmpty()
                && it.announcementDate != AppSettings.getLastSeenAnnouncementDate(this)) {
                showAnnouncement = true
            }
        }
    }

    private fun onUpdateNow() {
        updateInfo?.let { openUpdateUrl(this, it.urlApk) }
    }

    private fun checkStoragePermission(requestIfMissing: Boolean) {
        storagePermissionGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

        if (!storagePermissionGranted) {
            BepInExLog.w("MANAGE_EXTERNAL_STORAGE not granted  -- showing permission dialog")
            Toast.makeText(this, "Need storage permission to extract BepInEx files", Toast.LENGTH_LONG).show()
            if (requestIfMissing) requestStoragePermission()
        } else {
            BepInExLog.i("Storage permission granted")
            startGameDetection()
            // Initial render
        }
    }

    private fun requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            storagePermissionLauncher.launch(intent)
        } catch (e: Exception) {
            BepInExLog.e("Failed to open storage permission settings", e)
            Toast.makeText(this, "Please grant 'All files access' in app settings", Toast.LENGTH_LONG).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedText(intent)
    }

    override fun onPause() {
        hasPaused = true
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (hasPaused) {
            hasPaused = false
            // Persist runtime logs/config to active modpack when returning from game
            selectedGame?.let { game ->
                val active = AppSettings.getActiveModpack(this, game.packageName)
                if (!active.isNullOrEmpty()) {
                    try {
                        com.bepinex.android.modpack.ModpackManager()
                            .persistRuntimeState(game.packageName, active)
                    } catch (_: Exception) { }
                }
            }
        }
    }

    override fun onDestroy() {
        BepInExLogReader.stopWatching()
        scope.cancel()
        super.onDestroy()
    }

    // Share message handling

    /**
     * Handle incoming shared text (e.g. browser sharing the OAuth callback URL).
      * Stores to /PVZRH_Launcher/share_messages.txt for AuthFix to read.
     */
    private fun handleSharedText(intent: Intent) {
        BepInExLog.i("handleSharedText: action=${intent.action}, type=${intent.type}")

        if (intent.action != Intent.ACTION_SEND) {
            BepInExLog.w("Not ACTION_SEND, ignoring")
            return
        }

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getStringExtra(Intent.EXTRA_HTML_TEXT)
        if (sharedText.isNullOrEmpty()) {
            BepInExLog.w("No EXTRA_TEXT or EXTRA_HTML_TEXT in share")
            return
        }

        BepInExLog.i("Shared text: ${sharedText.take(200)}")

        if (!sharedText.contains("accounts.innersloth.com")
            && !sharedText.contains("token=")) {
            BepInExLog.w("Not an Innersloth/token URL, ignoring")
            return
        }

        try {
            val shareFile = File(
                Environment.getExternalStorageDirectory(),
                "PVZRH_Launcher/share_messages.txt"
            )
            shareFile.parentFile?.mkdirs()
            shareFile.writeText(sharedText)
            BepInExLog.i("Share message saved (${sharedText.length} chars)")
            Toast.makeText(this, getString(R.string.share_saved), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            BepInExLog.e("Failed to save share message", e)
        }
    }

    // Game detection

    private fun startGameDetection() {
        scope.launch {
            isScanning = true

            try {
                detectedGames = GameDetector.detectGames(this@MainActivity)
                BepInExLog.i("Detected ${detectedGames.size} Unity IL2CPP game(s)")

                // Keep the selection by package name across rescans. If the
                // previously selected game disappeared, clear it and select the
                // first currently detected game when one is available.
                val preferredPackage = savedPackageName ?: selectedGame?.packageName
                val refreshedSelection = preferredPackage?.let { packageName ->
                    detectedGames.firstOrNull { it.packageName == packageName }
                }

                savedPackageName = null
                when {
                    refreshedSelection != null -> selectedGame = refreshedSelection
                    detectedGames.isNotEmpty() -> selectGame(detectedGames.first())
                    else -> selectedGame = null
                }
            } catch (e: Exception) {
                BepInExLog.e("Game detection failed", e)
            }

            isScanning = false
        }
    }

    private fun selectGame(game: GameDetector.DetectedGame) {
        selectedGame = game
        BepInExLog.i("Selected: ${game.label} (${game.packageName})")

        if (!fileExtractor.isFrameworkReady(game.packageName)) {
            startExtraction(game.packageName)
        }
    }

    // Framework extraction

    private fun startExtraction(packageName: String) {
        isExtracting = true
        extractionStatus = getString(R.string.extracting)

        scope.launch(Dispatchers.IO) {
            try {
                fileExtractor.extractBepInExIfNeeded(packageName) { status ->
                    scope.launch(Dispatchers.Main.immediate) { extractionStatus = status }
                }
                fileExtractor.extractDotnetIfNeeded(packageName) { status ->
                    scope.launch(Dispatchers.Main.immediate) { extractionStatus = status }
                }
                BepInExLog.i("Framework extraction complete for $packageName")
            } catch (e: Exception) {
                BepInExLog.e("Extraction failed", e)
            }
            withContext(Dispatchers.Main) {
                isExtracting = false
                extractionStatus = ""
            }
        }
    }

    // Launch

    private fun launchGame(modpackName: String? = null) {
        val game = selectedGame ?: return

        if (!fileExtractor.isFrameworkReady(game.packageName)) {
            Toast.makeText(this, getString(R.string.launch_wait_extraction), Toast.LENGTH_SHORT).show()
            if (!isExtracting) startExtraction(game.packageName)
            return
        }

        BepInExLog.i("=== Launching ${game.label} (modpack: ${modpackName ?: "vanilla"}) via BootstrapActivity ===")

        try {
            val intent = Intent(this, BootstrapActivity::class.java).apply {
                putExtra(BootstrapActivity.EXTRA_TARGET_PACKAGE, game.packageName)
                modpackName?.let { putExtra(BootstrapActivity.EXTRA_ACTIVE_MODPACK, it) }
            }
            startActivity(intent)
            startCrashMonitor(game.packageName)
        } catch (e: Exception) {
            BepInExLog.e("Launch failed", e)
            Toast.makeText(this, getString(R.string.launch_failed), Toast.LENGTH_LONG).show()
        }
    }

    private fun startCrashMonitor(packageName: String) {
        crashMonitorJob?.cancel()
        gameProcessAlive = true
        crashMonitorJob = scope.launch(Dispatchers.IO) {
            delay(3000L)
            while (isActive && gameProcessAlive) {
                if (!isGameProcessRunning(packageName)) {
                    gameProcessAlive = false
                    val info = captureCrashInfo()
                    if (info != null) {
                        withContext(Dispatchers.Main) {
                            crashInfo = info
                            showCrashDialog = true
                        }
                    }
                    break
                }
                delay(2000L)
            }
        }
    }

    private fun isGameProcessRunning(packageName: String): Boolean {
        return try {
            val processName = "$packageName:game"
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val procs = am.runningAppProcesses ?: return true
            procs.any { it.processName == processName }
        } catch (_: Exception) { true }
    }

    private fun captureCrashInfo(): CrashInfo? {
        return try {
            val timestamp = android.os.SystemClock.elapsedRealtime()
            val logcat = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime", "-t", "100"))
                .inputStream.bufferedReader().use { it.readText() }

            val fatalLine = logcat.lines().firstOrNull { it.contains("FATAL EXCEPTION") }
            val signalLine = logcat.lines().firstOrNull { line ->
                line.contains("signal") && (line.contains("SIGSEGV") || line.contains("SIGABRT")
                    || line.contains("SIGBUS") || line.contains("SIGFPE"))
            }

            if (fatalLine == null && signalLine == null) return null

            val signal = signalLine?.let { sig ->
                Regex("signal\\s+(\\d+)\\s+\\((\\w+)\\)").find(sig)?.let {
                    "${it.groupValues[2]} (${it.groupValues[1]})"
                }
            }

            val crashLog = logcat.lines().filter { line ->
                line.contains("FATAL EXCEPTION") || line.contains("AndroidRuntime")
                    || line.contains("signal") || line.contains("backtrace")
                    || line.contains("#0") || line.contains("#1") || line.contains("#2")
            }.take(25).joinToString("\n")

            CrashInfo(signal = signal, log = crashLog)
        } catch (_: Exception) { null }
    }

    // Settings actions

    private fun onThemeChanged(mode: AppSettings.ThemeMode) {
        themeMode = mode
        AppSettings.setThemeMode(this, mode)
        // Compose observes themeMode and updates the theme without recreating navigation.
    }

    private fun onLanguageChanged(lang: AppSettings.Language) {
        language = lang
        AppSettings.setLanguage(this, lang)
        savedPackageName = selectedGame?.packageName
        recreate()
    }

    private fun onClearBepInEx(packageName: String) {
        val dir = BepInExPaths.getBepInExDir(packageName)
        if (dir.exists()) {
            dir.deleteRecursively()
            BepInExLog.i("Cleared BepInEx: ${dir.absolutePath}")
            Toast.makeText(this, getString(R.string.done), Toast.LENGTH_SHORT).show()
        }
    }

    private fun onClearDotnet(packageName: String) {
        val dotnetDir = BepInExPaths.getDotnetDir(filesDir, packageName)
        val dataDir = BepInExPaths.getCopiedDataDir(filesDir, packageName)
        if (dotnetDir.exists()) dotnetDir.deleteRecursively()
        if (dataDir.exists()) dataDir.deleteRecursively()
        BepInExLog.i("Cleared .NET data for $packageName")
        Toast.makeText(this, getString(R.string.done), Toast.LENGTH_SHORT).show()
    }

    private fun onClearLibUnity(packageName: String) {
        val appDataDir = BepInExPaths.getAppDataDir(filesDir, packageName)
        val libunityDir = java.io.File(appDataDir, "libunity")
        val resId = if (libunityDir.exists()) {
            libunityDir.deleteRecursively()
            BepInExLog.i("Cleared libunity cache: ${libunityDir.absolutePath}")
            R.string.clear_libunity_done
        } else {
            BepInExLog.i("Clear libunity: nothing to clear at ${libunityDir.absolutePath}")
            R.string.clear_libunity_none
        }
        Toast.makeText(this, getString(resId), Toast.LENGTH_SHORT).show()
    }

    private fun onCopyGameResources(packageName: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val gameContext = createPackageContext(packageName,
                    android.content.Context.CONTEXT_IGNORE_SECURITY or android.content.Context.CONTEXT_INCLUDE_CODE)
                // This delegate to BootstrapActivity's logic via reflection or direct copy
                // For now, just trigger re-extraction by deleting Data_copy
                val dataDir = BepInExPaths.getCopiedDataDir(filesDir, packageName)
                if (dataDir.exists()) dataDir.deleteRecursively()
                dataDir.mkdirs()
                // Copy will happen on next launch via BootstrapActivity
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity,
                        "Resources will be copied on next launch", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                BepInExLog.e("Failed to copy resources", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Log export

    private fun onExportLogs() {
        scope.launch(Dispatchers.IO) {
            try {
                val extDir = getExternalFilesDir(null) ?: filesDir
                val launcherLog = File(extDir, "bepinex_launcher.log")

                // Capture current process logcat
                val logcatFile = File(extDir, "logcat.txt")
                try {
                    val pid = android.os.Process.myPid()
                    val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime", "--pid=$pid"))
                    val output = process.inputStream.bufferedReader().readText()
                    process.waitFor()
                    logcatFile.writeText(output)
                } catch (e: Exception) {
                    logcatFile.writeText("Failed to capture logcat: ${e.message}")
                }

                // Zip both logs
                val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                    .format(java.util.Date())
                val zipFile = File(cacheDir, "pvzrh_logs_$timestamp.zip")
                java.util.zip.ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
                    if (launcherLog.exists()) {
                        zos.putNextEntry(java.util.zip.ZipEntry("bepinex_launcher.log"))
                        launcherLog.inputStream().copyTo(zos)
                        zos.closeEntry()
                    }
                    if (logcatFile.exists()) {
                        zos.putNextEntry(java.util.zip.ZipEntry("logcat.txt"))
                        logcatFile.inputStream().copyTo(zos)
                        zos.closeEntry()
                    }
                }

                // Share via system intent
                withContext(Dispatchers.Main) {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        this@MainActivity,
                        "${packageName}.provider",
                        zipFile
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "PVZRH Launcher Logs")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Logs"))
                }
            } catch (e: Exception) {
                BepInExLog.e("Failed to export logs", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // UI render

    private data class CrashInfo(val signal: String? = null, val log: String = "")

    private fun dismissCrashDialog() {
        showCrashDialog = false
        crashInfo = null
    }

    @Composable
    private fun StoragePermissionContent(onGrant: () -> Unit) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = getString(R.string.storage_permission_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = getString(R.string.storage_permission_message),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Button(onClick = onGrant) {
                        Text(text = getString(R.string.storage_permission_grant))
                    }
                }
            }
        }
    }

    private fun setupContent() {
        setContent {
            BepInExTheme(themeMode = themeMode) {
                if (storagePermissionGranted) {
                    BepInExNavHost(
                    scope = scope,
                    detectedGames = detectedGames,
                    selectedGame = selectedGame,
                    isScanning = isScanning,
                    isFrameworkReady = selectedGame?.let {
                        fileExtractor.isFrameworkReady(it.packageName)
                    } ?: false,
                    isExtracting = isExtracting,
                    extractionStatus = extractionStatus,
                    themeMode = themeMode,
                    language = language,
                    onSelectGame = { selectGame(it) },
                    onRescan = {
                        GameDetector.invalidateCache()
                        startGameDetection()
                    },
                    onLaunch = { modpackName -> launchGame(modpackName) },
                    onThemeChanged = { onThemeChanged(it) },
                    onLanguageChanged = { onLanguageChanged(it) },
                    onClearBepInEx = { onClearBepInEx(it) },
                    onClearDotnet = { onClearDotnet(it) },
                    onClearLibUnity = { onClearLibUnity(it) },
                    onCopyGameResources = { onCopyGameResources(it) },
                    onExportLogs = { onExportLogs() }
                    )
                } else {
                    StoragePermissionContent(onGrant = { requestStoragePermission() })
                }

                // Update / Announcement dialogs
                if (showBlocked) {
                    val isZh = resources.configuration.locales[0]?.language == "zh"
                    val blockedMsg = updateInfo?.let {
                        if (isZh) it.announcementZh else it.announcementEn
                    } ?: ""
                    BlockedDialog(message = blockedMsg)
                }

                if (showUpdate) {
                    updateInfo?.let { info ->
                        val isZh = resources.configuration.locales[0]?.language == "zh"
                        val announcement = if (isZh) info.announcementZh else info.announcementEn
                        val currentVersion = try {
                            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
                        } catch (_: Exception) { "" }

                        UpdateDialog(
                            currentVersion = currentVersion,
                            remoteVersion = info.version,
                            updateMessage = announcement,
                            onUpdate = { onUpdateNow() },
                            onSkip = { onDismissUpdate() }
                        )
                    }
                }

                if (showAnnouncement) {
                    updateInfo?.let { info ->
                        val isZh = resources.configuration.locales[0]?.language == "zh"
                        val announcement = if (isZh) info.announcementZh else info.announcementEn
                        if (announcement.isNotEmpty()) {
                            AnnouncementDialog(
                                date = info.announcementDate,
                                message = announcement,
                                onDismiss = { onDismissAnnouncement() }
                            )
                        }
                    }
                }

                if (showCrashDialog) {
                    crashInfo?.let { info ->
                        val gameLabel = selectedGame?.label ?: getString(R.string.app_name)
                        CrashDialog(
                            gameName = gameLabel,
                            signal = info.signal,
                            crashLog = info.log,
                            onDismiss = { dismissCrashDialog() },
                            onExportLogs = {
                                dismissCrashDialog()
                                onExportLogs()
                            }
                        )
                    }
                }
            }
        }
    }
}
