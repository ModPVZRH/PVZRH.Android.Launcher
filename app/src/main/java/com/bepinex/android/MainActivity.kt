package com.bepinex.android

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
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
import androidx.compose.material3.TextButton
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
import com.bepinex.android.ui.onboarding.OnboardingHost
import com.bepinex.android.ui.theme.BepInExTheme
import com.bepinex.android.update.UpdateChecker
import com.bepinex.android.update.AnnouncementDialog
import com.bepinex.android.update.IncompleteTranslationDialog
import com.bepinex.android.update.UpdateDialog
import com.bepinex.android.update.BlockedDialog
import com.bepinex.android.update.CrashDialog
import com.bepinex.android.update.openUpdateUrl
import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile

private fun isCompleteTranslationLocale(locale: Locale?): Boolean {
    val language = locale?.language?.lowercase(Locale.ROOT) ?: return false
    if (language == "en") return true
    if (language != "zh") return false

    val script = locale.script.lowercase(Locale.ROOT)
    val country = locale.country.uppercase(Locale.ROOT)
    return script != "hant" && country !in setOf("TW", "HK", "MO")
}

private fun usesChineseAnnouncement(language: AppSettings.Language, locale: Locale?): Boolean =
    when (language) {
        AppSettings.Language.CHINESE, AppSettings.Language.CHINESE_TW -> true
        AppSettings.Language.SYSTEM -> locale?.language?.equals("zh", ignoreCase = true) == true
        else -> false
    }

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
    private var dynamicColor by mutableStateOf(false)
    private var animationDisabled by mutableStateOf(false)

    // Update check state
    private var updateInfo by mutableStateOf<UpdateChecker.UpdateInfo?>(null)
    private var showAnnouncement by mutableStateOf(false)
    private var showUpdate by mutableStateOf(false)
    private var showBlocked by mutableStateOf(false)
    private var isCheckingUpdate by mutableStateOf(true)
    private var pendingAnnouncementToShow by mutableStateOf(false)
    private var showIncompleteTranslation by mutableStateOf(false)
    private var showOnboarding by mutableStateOf(false)

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
        private var savedPagerPage = 0
    }

    override fun attachBaseContext(newBase: Context?) {
        val ctx = newBase ?: return super.attachBaseContext(newBase)
        val lang = AppSettings.getLanguage(ctx)
        if (lang == AppSettings.Language.SYSTEM) return super.attachBaseContext(newBase)
        val locale = Locale.forLanguageTag(lang.key)
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
        dynamicColor = AppSettings.isDynamicColorEnabled(this)
        animationDisabled = AppSettings.isAnimationDisabled(this)
        skipOnboardingForExistingUser()
        showOnboarding = !AppSettings.isOnboardingCompleted(this)

        BepInExLog.init(this)
        BepInExLog.i("=== PVZRH Launcher ===")
        BepInExLog.i("Device: ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.SDK_INT}")

        fileExtractor = FileExtractor(this)
        
        val selectedLanguage = AppSettings.getLanguage(this)
        val systemLocale = resources.configuration.locales[0]
        val isCompleteLocale = when (selectedLanguage) {
            AppSettings.Language.SYSTEM -> isCompleteTranslationLocale(systemLocale)
            else -> AppSettings.Language.isCompleteTranslation(selectedLanguage)
        }
        val pendingIncompleteDialog = AppSettings.isPendingIncompleteDialog(this)
        val needsIncompleteDialog = !isCompleteLocale
                && !AppSettings.isLanguageIncompleteShown(this)
        if (isCompleteLocale) {
            if (pendingIncompleteDialog) AppSettings.setPendingIncompleteDialog(this, false)
        } else if (needsIncompleteDialog || pendingIncompleteDialog) {
            AppSettings.setPendingIncompleteDialog(this, false)
            AppSettings.setLanguageIncompleteShown(this, true)
            showIncompleteTranslation = true
        }

        // Compose is installed once; subsequent updates are driven by observable state.
        setupContent()
        checkStoragePermission(requestIfMissing = !showOnboarding)
        if (showOnboarding) startGameDetection()
        handleSharedText(intent)
        checkForUpdates()
    }

    private fun skipOnboardingForExistingUser() {
        if (AppSettings.isOnboardingCompleted(this)) return
        val hasLauncherData =
            AppSettings.getLastSeenAnnouncementDate(this).isNotEmpty() ||
                AppSettings.isLanguageIncompleteShown(this) ||
                BepInExPaths.getGameRootDir("com.LanPiaoPiao.PlantsVsZombiesRH").exists() ||
                BepInExPaths.getGameRootDir("com.LanPiaoPiao.PlantsVsZombiesRHMod").exists()
        if (hasLauncherData) {
            AppSettings.setOnboardingCompleted(this, true)
            AppSettings.setCoachMarksShown(this, true)
        }
    }

    private fun completeOnboarding() {
        AppSettings.setOnboardingCompleted(this, true)
        showOnboarding = false
        if (!storagePermissionGranted) {
            checkStoragePermission(requestIfMissing = true)
        } else if (detectedGames.isEmpty() && !isScanning) {
            startGameDetection()
        }
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
                        pendingAnnouncementToShow
                            && info.announcementDate.isNotEmpty()
                            && info.announcementDate != AppSettings.getLastSeenAnnouncementDate(this@MainActivity)
                            && !showOnboarding -> {
                            pendingAnnouncementToShow = false
                            showAnnouncement = true
                        }
                        info.announcementDate.isNotEmpty()
                            && info.announcementDate != AppSettings.getLastSeenAnnouncementDate(this@MainActivity)
                            && !showOnboarding -> {
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

    private fun onIncompleteDialogDismissed() {
        // Show announcement next if available, otherwise flag it for when update info loads
        val info = updateInfo
        if (info != null && info.announcementDate.isNotEmpty()
            && info.announcementDate != AppSettings.getLastSeenAnnouncementDate(this)) {
            showAnnouncement = true
        } else {
            pendingAnnouncementToShow = true
        }
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
        val manufacturer = Build.MANUFACTURER.lowercase()
        val isHuawei = manufacturer.contains("huawei") || manufacturer.contains("honor")
        if (isHuawei) {
            try {
                val intent = Intent().apply {
                    setClassName("com.huawei.permissionmanager",
                        "com.huawei.permissionmanager.filepermission.FilePermissionActivity")
                }
                storagePermissionLauncher.launch(intent)
                return
            } catch (_: Exception) { }
        }
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            storagePermissionLauncher.launch(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                storagePermissionLauncher.launch(intent)
            } catch (e2: Exception) {
                BepInExLog.e("Failed to open storage permission settings", e2)
                Toast.makeText(this, "Please grant 'All files access' in app settings", Toast.LENGTH_LONG).show()
            }
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
                maybeReportGameCrash(game.packageName)
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
            var sawGameProcess = false
            while (isActive && gameProcessAlive) {
                val running = isLauncherGameProcessRunning()
                if (running) sawGameProcess = true
                if (sawGameProcess && !running) {
                    reportGameCrash(packageName)
                    break
                }
                delay(2000L)
            }
        }
    }

    private fun maybeReportGameCrash(packageName: String) {
        if (!gameProcessAlive) return
        if (isLauncherGameProcessRunning()) return
        scope.launch(Dispatchers.IO) {
            reportGameCrash(packageName)
        }
    }

    private suspend fun reportGameCrash(packageName: String) {
        if (!gameProcessAlive) return
        gameProcessAlive = false
        crashMonitorJob?.cancel()
        delay(400L)
        val info = captureCrashInfo(packageName) ?: return
        withContext(Dispatchers.Main) {
            crashInfo = info
            showCrashDialog = true
        }
    }

    private fun isLauncherGameProcessRunning(): Boolean {
        val expected = "$packageName:game"
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            if (am.runningAppProcesses?.any { it.processName == expected } == true) return true
        } catch (_: Exception) { }

        return try {
            val dirs = File("/proc").listFiles { file ->
                file.isDirectory && file.name.all { it.isDigit() }
            } ?: return true
            dirs.any { dir ->
                val cmdline = File(dir, "cmdline")
                if (!cmdline.canRead()) return@any false
                val text = cmdline.readBytes().toString(Charsets.UTF_8)
                    .replace('\u0000', ' ')
                    .trim()
                text == expected || text.startsWith("$expected ")
            }
        } catch (_: Exception) {
            true
        }
    }

    private fun captureCrashInfo(packageName: String): CrashInfo? {
        return try {
            val bepinexLog = extractLatestErrorsFromText(readLogTail(BepInExPaths.getLogFile(packageName)))
            val capturedLogcat = extractLatestErrorsFromText(
                readLogTail(BepInExPaths.getLogcatCaptureFile(packageName))
            )
            val liveLogcat = dumpGameLogcat()
            val liveErrors = extractLatestErrorsFromText(liveLogcat)
            val logcatInfo = captureLogcatCrash(liveLogcat)

            val logcatText = listOfNotNull(capturedLogcat, liveErrors, logcatInfo?.log)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString("\n\n")

            if (bepinexLog.isNullOrBlank() && logcatText.isEmpty()) return null

            val log = buildString {
                if (!bepinexLog.isNullOrBlank()) {
                    appendLine("--- BepInEx ---")
                    append(bepinexLog.trim())
                }
                if (logcatText.isNotEmpty()) {
                    if (isNotEmpty()) appendLine().appendLine()
                    appendLine("--- logcat ---")
                    append(logcatText)
                }
            }.trim()
            if (log.isEmpty()) return null

            val signal = logcatInfo?.signal
                ?: if (!bepinexLog.isNullOrBlank() || capturedLogcat != null || liveErrors != null) {
                    getString(R.string.crash_bepinex)
                } else {
                    null
                }
            CrashInfo(signal = signal, log = log)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractLatestErrorsFromText(text: String): String? {
        if (text.isBlank()) return null
        val lines = text.lines()
        var lastErrorIdx = -1
        for (i in lines.indices) {
            if (isCrashLogLine(lines[i])) lastErrorIdx = i
        }
        if (lastErrorIdx < 0) return null

        var start = lastErrorIdx
        while (start > 0) {
            val previous = lines[start - 1]
            if (isCrashLogLine(previous) || !looksLikeNewLogEntry(previous)) {
                start--
            } else {
                break
            }
        }

        var end = lastErrorIdx
        while (end + 1 < lines.size) {
            val next = lines[end + 1]
            if (looksLikeNewLogEntry(next) && !isCrashLogLine(next)) break
            end++
        }

        return lines.subList(start, end + 1)
            .takeLast(80)
            .joinToString("\n")
            .take(8000)
            .ifBlank { null }
    }

    private fun isCrashLogLine(line: String): Boolean {
        val lower = line.lowercase()
        if (lower.contains("fatal exception")) return true
        if (lower.contains("[error") || lower.contains("[fatal")) return true
        if (lower.contains("notsupportedexception")) return true
        if (lower.contains("il2cppinterop") &&
            (lower.contains("error") || lower.contains("exception"))
        ) {
            return true
        }
        if (lower.contains("signal") &&
            (lower.contains("sigsegv") || lower.contains("sigabrt") ||
                lower.contains("sigbus") || lower.contains("sigfpe"))
        ) {
            return true
        }
        return Regex("\\sE\\s+Unity\\b").containsMatchIn(line)
    }

    private fun looksLikeNewLogEntry(line: String): Boolean {
        if (line.startsWith("[") && line.contains(":")) return true
        return line.length >= 18 && line[2] == '-' && line[5] == ' '
    }

    private fun readLogTail(file: File, maxBytes: Int = 256 * 1024): String {
        if (!file.isFile) return ""
        val length = file.length()
        if (length <= 0L) return ""
        return RandomAccessFile(file, "r").use { raf ->
            val start = (length - maxBytes).coerceAtLeast(0L)
            raf.seek(start)
            val bytes = ByteArray((length - start).toInt())
            raf.readFully(bytes)
            var text = String(bytes, Charsets.UTF_8)
            if (start > 0L) {
                val newline = text.indexOf('\n')
                if (newline >= 0) text = text.substring(newline + 1)
            }
            text
        }
    }

    private fun dumpGameLogcat(): String {
        val pid = findLauncherGamePid()
        val command = if (pid != null) {
            arrayOf("logcat", "-d", "-v", "threadtime", "--pid", pid, "-t", "200")
        } else {
            arrayOf("logcat", "-d", "-v", "threadtime", "-t", "200")
        }
        return try {
            Runtime.getRuntime().exec(command)
                .inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            ""
        }
    }

    private fun findLauncherGamePid(): String? {
        val expected = "$packageName:game"
        return try {
            File("/proc").listFiles { file ->
                file.isDirectory && file.name.all { it.isDigit() }
            }?.firstOrNull { dir ->
                val cmdline = File(dir, "cmdline")
                if (!cmdline.canRead()) return@firstOrNull false
                val text = cmdline.readBytes().toString(Charsets.UTF_8)
                    .replace('\u0000', ' ')
                    .trim()
                text == expected || text.startsWith("$expected ")
            }?.name
        } catch (_: Exception) {
            null
        }
    }

    private fun captureLogcatCrash(logcat: String = dumpGameLogcat()): CrashInfo? {
        if (logcat.isBlank()) return null
        return try {
            val fatalLine = logcat.lines().firstOrNull { it.contains("FATAL EXCEPTION") }
            val signalLine = logcat.lines().firstOrNull { line ->
                line.contains("signal") && (line.contains("SIGSEGV") || line.contains("SIGABRT")
                    || line.contains("SIGBUS") || line.contains("SIGFPE"))
            }
            val unityError = logcat.lines().any { isCrashLogLine(it) }
            if (fatalLine == null && signalLine == null && !unityError) return null

            val signal = signalLine?.let { sig ->
                Regex("signal\\s+(\\d+)\\s+\\((\\w+)\\)").find(sig)?.let {
                    "${it.groupValues[2]} (${it.groupValues[1]})"
                }
            }
            val crashLog = logcat.lines().filter { line ->
                isCrashLogLine(line) ||
                    line.contains("FATAL EXCEPTION") || line.contains("AndroidRuntime")
                    || line.contains("backtrace")
                    || line.contains("#00") || line.contains("#0 ") || line.contains("#1 ")
                    || line.contains("#2 ")
            }.takeLast(40).joinToString("\n")
            CrashInfo(signal = signal, log = crashLog)
        } catch (_: Exception) {
            null
        }
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

                // Capture launcher process logcat
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

                // Capture game process logcat (Unity, BepInEx, crash)
                val gameLogcatFile = File(extDir, "game_logcat.txt")
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "threadtime"))
                    val output = process.inputStream.bufferedReader().readText()
                    process.waitFor()
                    val filtered = output.lines().filter { line ->
                        line.contains("Unity") || line.contains("BepInEx") ||
                        line.contains("FATAL") || line.contains("DEBUG") ||
                        line.contains("ActivityManager") || line.contains("CRASH") ||
                        line.contains("libunity") || line.contains("il2cpp") ||
                        line.contains("signal") || line.contains("backtrace")
                    }.joinToString("\n")
                    gameLogcatFile.writeText(filtered.ifEmpty { "No game-related logs found" })
                } catch (e: Exception) {
                    gameLogcatFile.writeText("Failed to capture game logcat: ${e.message}")
                }

                // Capture crash logcat
                val crashLogcatFile = File(extDir, "crash_logcat.txt")
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("logcat", "-b", "crash", "-d", "-v", "threadtime"))
                    val output = process.inputStream.bufferedReader().readText()
                    process.waitFor()
                    crashLogcatFile.writeText(output)
                } catch (e: Exception) {
                    crashLogcatFile.writeText("Failed to capture crash logcat: ${e.message}")
                }

                // Capture Unity log files from game directory
                val unityLogFile = File(extDir, "unity_logs.txt")
                try {
                    val gamePackages = listOf(
                        "com.LanPiaoPiao.PlantsVsZombiesRH",
                        "com.LanPiaoPiao.PlantsVsZombiesRHMod"
                    )
                    val logNames = setOf(
                        "main.log", "il2cpp.log", "LogOutput.log", "bepinexlogoutput.log",
                        "BepInExLogOutput.log", "output_log.txt", "player.log"
                    )
                    val sb = StringBuilder()
                    for (pkg in gamePackages) {
                        val gameRoot = java.io.File("/storage/emulated/0/PVZRH_Launcher/$pkg")
                        if (gameRoot.exists()) {
                            gameRoot.walkTopDown()
                                .filter { it.isFile && it.length() <= 1024 * 1024 && logNames.contains(it.name) }
                                .forEach { file ->
                                    sb.appendLine("=== ${file.absolutePath} ===")
                                    try { sb.appendLine(file.readText()) } catch (_: Exception) {}
                                    sb.appendLine()
                                }
                        }
                    }
                    unityLogFile.writeText(sb.toString().ifEmpty { "No Unity log files found" })
                } catch (e: Exception) {
                    unityLogFile.writeText("Failed to capture Unity logs: ${e.message}")
                }

                // Capture tombstones
                val tombstoneFile = File(extDir, "tombstones.txt")
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("ls", "-lt", "/data/tombstones/"))
                    val output = process.inputStream.bufferedReader().readText()
                    process.waitFor()
                    val sb = StringBuilder()
                    sb.appendLine(output)
                    val lines = output.lines().filter { it.contains("tombstone") }
                    for (line in lines.take(3)) {
                        val parts = line.trim().split("\\s+".toRegex())
                        if (parts.isNotEmpty()) {
                            val name = parts.last()
                            try {
                                val pull = Runtime.getRuntime().exec(arrayOf("cat", "/data/tombstones/$name"))
                                val content = pull.inputStream.bufferedReader().readText()
                                pull.waitFor()
                                sb.appendLine("\n=== $name ===")
                                sb.appendLine(content)
                            } catch (_: Exception) {}
                        }
                    }
                    tombstoneFile.writeText(sb.toString())
                } catch (e: Exception) {
                    tombstoneFile.writeText("Failed to capture tombstones: ${e.message}")
                }

                // Capture BepInEx plugin logs
                val bepinexLogFile = File(extDir, "bepinex_plugin_logs.txt")
                try {
                    val gamePackages = listOf(
                        "com.LanPiaoPiao.PlantsVsZombiesRH",
                        "com.LanPiaoPiao.PlantsVsZombiesRHMod"
                    )
                    val sb = StringBuilder()
                    for (pkg in gamePackages) {
                        val logFile = java.io.File("/storage/emulated/0/PVZRH_Launcher/$pkg/BepInEx/LogOutput.log")
                        if (logFile.exists() && logFile.length() <= 2 * 1024 * 1024) {
                            sb.appendLine("=== ${logFile.absolutePath} ===")
                            try { sb.appendLine(logFile.readText()) } catch (_: Exception) {}
                            sb.appendLine()
                        }
                    }
                    bepinexLogFile.writeText(sb.toString().ifEmpty { "No BepInEx LogOutput.log found" })
                } catch (e: Exception) {
                    bepinexLogFile.writeText("Failed to capture BepInEx logs: ${e.message}")
                }

                // Zip all logs
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
                    if (gameLogcatFile.exists()) {
                        zos.putNextEntry(java.util.zip.ZipEntry("game_logcat.txt"))
                        gameLogcatFile.inputStream().copyTo(zos)
                        zos.closeEntry()
                    }
                    if (crashLogcatFile.exists()) {
                        zos.putNextEntry(java.util.zip.ZipEntry("crash_logcat.txt"))
                        crashLogcatFile.inputStream().copyTo(zos)
                        zos.closeEntry()
                    }
                    if (unityLogFile.exists()) {
                        zos.putNextEntry(java.util.zip.ZipEntry("unity_logs.txt"))
                        unityLogFile.inputStream().copyTo(zos)
                        zos.closeEntry()
                    }
                    if (tombstoneFile.exists()) {
                        zos.putNextEntry(java.util.zip.ZipEntry("tombstones.txt"))
                        tombstoneFile.inputStream().copyTo(zos)
                        zos.closeEntry()
                    }
                    if (bepinexLogFile.exists()) {
                        zos.putNextEntry(java.util.zip.ZipEntry("bepinex_plugin_logs.txt"))
                        bepinexLogFile.inputStream().copyTo(zos)
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
            BepInExTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                if (showOnboarding) {
                    OnboardingHost(
                        detectedGames = detectedGames,
                        isScanning = isScanning,
                        permissionGranted = storagePermissionGranted,
                        onRescan = {
                            GameDetector.invalidateCache()
                            startGameDetection()
                        },
                        onRequestPermission = { requestStoragePermission() },
                        onFinished = { completeOnboarding() }
                    )
                } else if (storagePermissionGranted) {
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
                    dynamicColor = dynamicColor,
                    animationDisabled = animationDisabled,
                    onSelectGame = { selectGame(it) },
                    onRescan = {
                        GameDetector.invalidateCache()
                        startGameDetection()
                    },
                    onLaunch = { modpackName -> launchGame(modpackName) },
                    onThemeChanged = { onThemeChanged(it) },
                    onLanguageChanged = { onLanguageChanged(it) },
                    onDynamicColorChanged = { enabled ->
                        dynamicColor = enabled
                    },
                    onAnimationDisabledChanged = { disabled ->
                        animationDisabled = disabled
                    },
                    onClearBepInEx = { onClearBepInEx(it) },
                    onClearDotnet = { onClearDotnet(it) },
                    onClearLibUnity = { onClearLibUnity(it) },
                    onCopyGameResources = { onCopyGameResources(it) },
                    onExportLogs = { onExportLogs() },
                    onShowAnnouncement = {
                        if (updateInfo != null) showAnnouncement = true
                    },
                    showIncompleteBanner = run {
                        val isComplete = when (language) {
                            AppSettings.Language.SYSTEM -> isCompleteTranslationLocale(resources.configuration.locales[0])
                            else -> AppSettings.Language.isCompleteTranslation(language)
                        }
                        !isComplete
                    },
                    onReplayOnboarding = { showOnboarding = true }
                    )
                } else {
                    StoragePermissionContent(onGrant = { requestStoragePermission() })
                }

                // Update / Announcement dialogs
                if (showBlocked) {
                    val isZh = usesChineseAnnouncement(language, resources.configuration.locales[0])
                    val blockedMsg = updateInfo?.let {
                        if (isZh) it.announcementZh else it.announcementEn
                    } ?: ""
                    BlockedDialog(message = blockedMsg)
                }

                if (showUpdate && !showOnboarding) {
                    updateInfo?.let { info ->
                        val isZh = usesChineseAnnouncement(language, resources.configuration.locales[0])
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

                if (showAnnouncement && !showOnboarding) {
                    updateInfo?.let { info ->
                        val isZh = usesChineseAnnouncement(language, resources.configuration.locales[0])
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

                if (showIncompleteTranslation && !showOnboarding) {
                    IncompleteTranslationDialog(
                        onDismiss = {
                            showIncompleteTranslation = false
                            onIncompleteDialogDismissed()
                        }
                    )
                }
            }
        }
    }
}
