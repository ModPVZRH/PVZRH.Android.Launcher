package com.bepinex.android

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Extracts BepInEx and .NET runtime from APK assets to per-game directories.
 */
class FileExtractor(private val context: Context) {

    /**
     * Extract BepInEx.Android.zip for a specific game.
     * Match the managed runtime to the APK's native bridge, preserving user files.
     */
    fun extractBepInExIfNeeded(packageName: String, onProgress: (String) -> Unit = {}) {
        val bepInExDir = BepInExPaths.getBepInExDir(packageName)
        val core = File(bepInExDir, "core")
        val marker = File(core, ".launcher-asset-sha256")
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open("BepInEx.Android.zip").use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val assetHash = digest.digest().joinToString("") { "%02x".format(it) }
        val required = listOf("BepInEx.Core.dll", "BepInEx.Unity.IL2CPP.dll",
            "Il2CppInterop.Runtime.dll", "Il2CppInterop.HarmonySupport.dll")
        if (marker.isFile && marker.readText() == assetHash &&
            required.all { File(core, it).isFile }) {
            BepInExLog.i("BepInEx already extracted for $packageName")
            return
        }

        onProgress("Extracting BepInEx for $packageName...")
        bepInExDir.mkdirs()
        val staging = File(bepInExDir, ".framework-${UUID.randomUUID()}")
        val backup = File(bepInExDir, ".core-backup-${UUID.randomUUID()}")
        try {
            extractZip("BepInEx.Android.zip", staging)
            val newCore = File(staging, "core")
            check(required.all { File(newCore, it).isFile }) { "Incomplete BepInEx package" }
            File(newCore, marker.name).writeText(assetHash)
            val hadCore = core.exists()
            check(!hadCore || core.renameTo(backup)) { "Cannot back up BepInEx core" }
            if (!newCore.renameTo(core)) {
                check(!hadCore || backup.renameTo(core)) {
                    "Core update failed; backup retained at ${backup.absolutePath}"
                }
                error("Cannot install BepInEx core")
            }
            val config = File(bepInExDir, "config/BepInEx.cfg")
            if (!config.exists()) {
                config.parentFile?.mkdirs()
                File(staging, "config/BepInEx.cfg").copyTo(config)
            }
            File(bepInExDir, "plugins").mkdirs()
            backup.deleteRecursively()
        } finally {
            staging.deleteRecursively()
        }
        BepInExLog.i("BepInEx extracted: ${bepInExDir.absolutePath}")
    }

    /**
     * Copies dependency files from a modpack `core/` into the active BepInEx core.
     * Files shipped by this APK, including BepInEx itself, are left unchanged.
     * Extras from the previously applied modpack are removed.
     */
    fun mergeModpackCoreExtras(packageName: String, modpackCoreDir: File?) {
        val runtimeCore = File(BepInExPaths.getBepInExDir(packageName), "core")
        if (!runtimeCore.isDirectory) return

        val launcherFiles = launcherCoreRelativePaths()
        val protected = launcherFiles + CORE_ASSET_MARKER
        val extras = linkedMapOf<String, File>()
        if (modpackCoreDir != null && modpackCoreDir.isDirectory) {
            val root = modpackCoreDir.canonicalFile
            modpackCoreDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = coreRelativePath(file.canonicalFile.relativeTo(root).path)
                if (relative.isEmpty() || relative in protected) return@forEach
                extras[relative] = file
            }
        }

        val runtimeRoot = runtimeCore.canonicalFile
        runtimeCore.walkBottomUp().filter { it.isFile }.forEach { file ->
            val relative = coreRelativePath(file.relativeTo(runtimeCore).path)
            if (relative in protected || relative in extras) return@forEach
            if (file.delete()) {
                BepInExLog.i("Removed stale core extra: $relative")
            }
        }
        runtimeCore.walkBottomUp()
            .filter { it.isDirectory && it.canonicalFile != runtimeRoot && it.list().isNullOrEmpty() }
            .forEach { it.delete() }

        var applied = 0
        extras.forEach { (relative, source) ->
            val dest = File(runtimeCore, relative)
            dest.parentFile?.mkdirs()
            if (!dest.canonicalFile.toPath().startsWith(runtimeRoot.toPath())) {
                BepInExLog.w("Skipped core extra outside core: $relative")
                return@forEach
            }
            source.copyTo(dest, overwrite = true)
            applied++
            BepInExLog.i("Applied modpack core extra: $relative")
        }
        if (applied > 0) {
            BepInExLog.i("Applied $applied modpack core extra(s)")
        }
    }

    private fun launcherCoreRelativePaths(): Set<String> {
        val paths = mutableSetOf<String>()
        context.assets.open("BepInEx.Android.zip").use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = coreRelativePath(entry.name)
                    if (!entry.isDirectory && name.startsWith("core/")) {
                        val relative = name.removePrefix("core/")
                        if (relative.isNotEmpty()) paths += relative
                    }
                    entry = zip.nextEntry
                }
            }
        }
        return paths
    }

    private fun coreRelativePath(path: String): String =
        path.replace('\\', '/').trim('/').trim()

    /**
     * Extract dotnet.zip for a specific game.
     * Skips if System.Private.CoreLib.dll marker already exists.
     */
    fun extractDotnetIfNeeded(packageName: String, onProgress: (String) -> Unit = {}) {
        val dotnetDir = BepInExPaths.getDotnetDir(context.filesDir, packageName)
        val marker = File(dotnetDir, "System.Private.CoreLib.dll")

        if (marker.exists()) {
            BepInExLog.i("dotnet already extracted for $packageName")
            return
        }

        if (dotnetDir.exists()) {
            BepInExLog.i("dotnet dir exists but incomplete — re-extracting for $packageName")
            dotnetDir.deleteRecursively()
        }

        onProgress("Extracting .NET runtime for $packageName...")
        BepInExLog.i("Extracting dotnet.zip → ${dotnetDir.absolutePath}")
        extractZip("dotnet.zip", dotnetDir)
        BepInExLog.i("dotnet extracted: ${dotnetDir.absolutePath}")
    }

    /**
     * Check if BepInEx and dotnet are both ready for a game.
     */
    fun isFrameworkReady(packageName: String): Boolean =
        BepInExPaths.isBepInExExtracted(packageName) &&
        BepInExPaths.isDotnetExtracted(context.filesDir, packageName)

    /**
     * Stage [ASSET_LAUNCHER_UI] from APK assets and copy it into BepInEx/plugins
     * so BepInEx loads it. Safe to call on every bootstrap so the plugin can update.
     */
    fun installLauncherUiPlugin(packageName: String) {
        val staged = stagedLauncherUiPlugin(packageName)
        staged.parentFile?.mkdirs()
        try {
            context.assets.open(ASSET_LAUNCHER_UI).use { input ->
                staged.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            BepInExLog.e("Failed to stage $ASSET_LAUNCHER_UI", e)
            return
        }
        copyStagedLauncherUiPlugin(packageName)
    }

    companion object {
        const val ASSET_LAUNCHER_UI = "PVZRH.LauncherUi.dll"
        private const val CORE_ASSET_MARKER = ".launcher-asset-sha256"

        fun stagedLauncherUiPlugin(packageName: String): File =
            File(BepInExPaths.getGameRootDir(packageName), "launcher/$ASSET_LAUNCHER_UI")

        /**
         * Copies the staged Launcher UI plugin into BepInEx/plugins.
         * Call after any plugins-dir replace (modpack apply / vanilla clear).
         */
        fun copyStagedLauncherUiPlugin(packageName: String) {
            val staged = stagedLauncherUiPlugin(packageName)
            if (!staged.isFile) {
                BepInExLog.w("LauncherUi plugin not staged at ${staged.absolutePath}")
                return
            }
            val dest = File(BepInExPaths.getPluginsDir(packageName), ASSET_LAUNCHER_UI)
            dest.parentFile?.mkdirs()
            staged.copyTo(dest, overwrite = true)
            BepInExLog.i("Installed LauncherUi BepInEx plugin: ${dest.absolutePath}")
        }
    }

    /**
     * Extract a ZIP asset to a destination directory.
     * Handles Windows path separators (\ → /) for compatibility.
     */
    private fun extractZip(assetName: String, destDir: File) {
        if (!destDir.mkdirs() && !destDir.exists()) {
            val msg = "Cannot create dest dir: ${destDir.absolutePath} — check MANAGE_EXTERNAL_STORAGE permission"
            BepInExLog.e(msg)
            throw RuntimeException(msg)
        }
        var extractCount = 0
        context.assets.open(assetName).use { input ->
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    // Normalize path separators: Windows-created zips may use \ instead of /
                    val normalizedName = entry.name.replace('\\', '/')
                    val outFile = File(destDir, normalizedName)
                    if (entry.isDirectory || normalizedName.endsWith("/")) {
                        if (!outFile.mkdirs() && !outFile.exists()) {
                            BepInExLog.w("Cannot create dir: ${outFile.absolutePath}")
                        }
                    } else {
                        val parent = outFile.parentFile
                        if (parent != null && !parent.mkdirs() && !parent.exists()) {
                            val msg = "Cannot create parent dir: ${parent.absolutePath} — storage permission issue?"
                            BepInExLog.e(msg)
                            throw RuntimeException(msg)
                        }
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        extractCount++
                    }
                    entry = zis.nextEntry
                }
            }
        }
        BepInExLog.i("Extracted $extractCount files to ${destDir.absolutePath}")
    }
}
