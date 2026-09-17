package com.bepinex.android

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Extracts BepInEx and .NET runtime from APK assets to per-game directories.
 */
class FileExtractor(private val context: Context) {

    /**
     * Extract BepInEx.Android.zip for a specific game.
     * Skips if BepInEx.Core.dll marker already exists.
     */
    fun extractBepInExIfNeeded(packageName: String, onProgress: (String) -> Unit = {}) {
        val bepInExDir = BepInExPaths.getBepInExDir(packageName)
        val marker = File(bepInExDir, "core/BepInEx.Core.dll")

        if (marker.exists()) {
            BepInExLog.i("BepInEx already extracted for $packageName")
            return
        }

        // Incomplete extraction — delete and retry
        if (bepInExDir.exists()) {
            BepInExLog.i("BepInEx dir exists but incomplete — re-extracting for $packageName")
            bepInExDir.deleteRecursively()
        }

        onProgress("Extracting BepInEx for $packageName...")
        BepInExLog.i("Extracting BepInEx.Android.zip → ${bepInExDir.absolutePath}")
        extractZip("BepInEx.Android.zip", bepInExDir)
        BepInExLog.i("BepInEx extracted: ${bepInExDir.absolutePath}")
    }

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
