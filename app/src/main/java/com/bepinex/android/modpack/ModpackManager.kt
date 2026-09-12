package com.bepinex.android.modpack

import android.content.Context
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import com.bepinex.android.BepInExLog
import com.bepinex.android.BepInExPaths
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Metadata for a modpack.
 *
 * [dllNames] maps a plugin-relative DLL path to a display name. Older
 * modpack.json files omit this field; missing entries fall back to the file name.
 *
 * [disabledDlls] lists plugin-relative DLL paths that should not be copied into
 * the runtime plugins directory. Older files omit this field; missing means all enabled.
 */
data class ModpackMeta(
    val name: String,
    val packageName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val modCount: Int = 0,
    val createShortcut: Boolean = false,
    val dllNames: Map<String, String> = emptyMap(),
    val disabledDlls: Set<String> = emptySet()
) {
    val enabledModCount: Int
        get() = (modCount - disabledDlls.size).coerceAtLeast(0)
}

/** A plugin DLL inside a modpack, with its saved display name and load switch. */
data class ModpackMod(
    val file: File,
    val relativePath: String,
    val displayName: String,
    val enabled: Boolean = true
)

data class ModpackExportProgress(
    val phase: String,
    val currentFile: String? = null,
    val completedFiles: Long = 0,
    val totalFiles: Long = 0,
    val completedBytes: Long = 0,
    val totalBytes: Long = 0
) {
    val fraction: Float
        get() = when {
            totalBytes > 0 -> (completedBytes.toDouble() / totalBytes).coerceIn(0.0, 1.0).toFloat()
            totalFiles > 0 -> (completedFiles.toDouble() / totalFiles).coerceIn(0.0, 1.0).toFloat()
            else -> 0f
        }
}

/**
 * Manages modpack CRUD operations on the file system.
 */
class ModpackManager {

    companion object {
        const val MODPACK_EXTENSION = "rhp"
        const val MODPACK_MIME_TYPE = "application/octet-stream"

        private val SUPPORTED_MODPACK_EXTENSIONS = setOf("rhp", "zip")
        private const val DLL_NAMES_KEY = "dllNames"
        private const val DISABLED_DLLS_KEY = "disabledDlls"

        fun isModpackFileName(fileName: String?): Boolean =
            fileName?.substringAfterLast('.', "")?.lowercase() in SUPPORTED_MODPACK_EXTENSIONS

        fun isModFileName(fileName: String?): Boolean =
            fileName?.substringAfterLast('.', "")?.equals("dll", ignoreCase = true) == true

        private val runtimeMutex = Mutex()

        @Volatile
        var runtimeSwitchInProgress: Boolean = false
            internal set
    }

    fun normalizeModpackName(name: String): String =
        name.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()

    private fun getModpacksDir(packageName: String): File =
        File(BepInExPaths.getGameRootDir(packageName), "modpacks")

    private fun getModpackDir(packageName: String, name: String): File =
        BepInExPaths.getModpackDir(packageName, name)

    private fun getModpackPluginsDir(packageName: String, name: String): File =
        File(getModpackDir(packageName, name), "plugins")

    private fun getModpackConfigDir(packageName: String, name: String): File =
        BepInExPaths.getModpackConfigDir(packageName, name)

    private fun getModpackLogsDir(packageName: String, name: String): File =
        BepInExPaths.getModpackLogsDir(packageName, name)

    private fun getMetaFile(packageName: String, name: String): File =
        File(getModpackDir(packageName, name), "modpack.json")

    // CRUD

    fun listModpacks(packageName: String): List<ModpackMeta> {
        val dir = getModpacksDir(packageName)
        if (!dir.exists()) return emptyList()

        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { readMeta(packageName, it.name) }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    fun createModpack(packageName: String, name: String): ModpackMeta? {
        val safeName = normalizeModpackName(name)
        if (safeName.isEmpty()) return null

        val modpackDir = getModpackDir(packageName, safeName)
        if (modpackDir.exists()) return null // already exists

        return try {
            modpackDir.mkdirs()
            getModpackPluginsDir(packageName, safeName).mkdirs()
            getModpackConfigDir(packageName, safeName).mkdirs()
            getModpackLogsDir(packageName, safeName).mkdirs()

            val meta = ModpackMeta(name = safeName, packageName = packageName)
            writeMeta(meta)
            BepInExLog.i("Created modpack: $safeName")
            meta
        } catch (e: Exception) {
            BepInExLog.e("Failed to create modpack: $safeName", e)
            null
        }
    }

    fun deleteModpack(packageName: String, name: String): Boolean {
        val dir = getModpackDir(packageName, name)
        return if (dir.exists()) {
            dir.deleteRecursively().also {
                BepInExLog.i("Deleted modpack: $name")
            }
        } else false
    }

    fun renameModpack(packageName: String, oldName: String, newName: String): Boolean {
        val safeNewName = normalizeModpackName(newName)
        if (safeNewName.isEmpty()) return false
        if (safeNewName == oldName) return true

        val oldDir = getModpackDir(packageName, oldName)
        val newDir = getModpackDir(packageName, safeNewName)
        if (!oldDir.exists() || newDir.exists()) return false

        if (!oldDir.renameTo(newDir)) return false

        // Metadata is repaired on a best-effort basis after the directory rename.
        try {
            val meta = readMeta(packageName, safeNewName)
                ?: ModpackMeta(name = safeNewName, packageName = packageName)
            writeMeta(meta.copy(name = safeNewName, packageName = packageName))
        } catch (e: Exception) {
            BepInExLog.e("Renamed modpack but failed to update metadata: $safeNewName", e)
        }

        BepInExLog.i("Renamed modpack: $oldName -> $safeNewName")
        return true
    }

    // Mod management

    /** Resolve the display name from a content URI */
    private fun resolveFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
        return name
    }

    /** Re-sync modpack.json modCount and DLL display-name mappings. */
    private fun syncModpackMeta(packageName: String, modpackName: String) {
        readMeta(packageName, modpackName)
    }

    fun listMods(packageName: String, modpackName: String): List<File> {
        val pluginsDir = getModpackPluginsDir(packageName, modpackName)
        return if (pluginsDir.isDirectory) {
            pluginsDir.walkTopDown()
                // Android paths are case-sensitive and File.extension preserves
                // the original case. Treat .dll/.DLL/.Dll as the same mod type.
                .filter { it.isFile && it.extension.equals("dll", ignoreCase = true) }
                .toList()
        } else {
            emptyList()
        }
    }

    fun listModEntries(packageName: String, modpackName: String): List<ModpackMod> {
        val pluginsDir = getModpackPluginsDir(packageName, modpackName)
        val meta = readMeta(packageName, modpackName)
        val mappings = meta?.dllNames.orEmpty()
        val disabled = meta?.disabledDlls.orEmpty()
        return listMods(packageName, modpackName).map { file ->
            val relativePath = dllRelativePath(pluginsDir, file)
            ModpackMod(
                file = file,
                relativePath = relativePath,
                displayName = resolveDllDisplayName(mappings, relativePath, file.name),
                enabled = isDllEnabled(disabled, relativePath, file.name)
            )
        }
    }

    fun setDllDisplayName(
        packageName: String,
        modpackName: String,
        relativePath: String,
        displayName: String
    ): Boolean {
        val name = displayName.trim()
        if (name.isEmpty()) return false
        val current = readMeta(packageName, modpackName) ?: return false
        val dllNames = syncedDllNames(packageName, modpackName, current.dllNames).toMutableMap()
        dllNames[normalizeDllKey(relativePath)] = name
        writeMeta(
            current.copy(
                modCount = getModCount(packageName, modpackName),
                dllNames = dllNames,
                disabledDlls = syncedDisabledDlls(packageName, modpackName, current.disabledDlls)
            )
        )
        return true
    }

    fun setDllEnabled(
        packageName: String,
        modpackName: String,
        relativePath: String,
        enabled: Boolean
    ): Boolean {
        val current = readMeta(packageName, modpackName) ?: return false
        val key = normalizeDllKey(relativePath)
        if (key.isEmpty()) return false
        val disabled = syncedDisabledDlls(packageName, modpackName, current.disabledDlls).toMutableSet()
        if (enabled) {
            disabled.remove(key)
            disabled.remove(key.substringAfterLast('/'))
        } else {
            disabled.add(key)
        }
        writeMeta(
            current.copy(
                modCount = getModCount(packageName, modpackName),
                disabledDlls = disabled
            )
        )
        return true
    }

    fun listConfigs(packageName: String, modpackName: String): List<File> {
        val configDir = getModpackConfigDir(packageName, modpackName)
        return configDir.listFiles()?.filter { it.isFile && it.extension == "cfg" } ?: emptyList()
    }

    fun addMod(packageName: String, modpackName: String, sourceFile: File): File? {
        if (!isModFileName(sourceFile.name)) {
            BepInExLog.w("Rejected mod with unsupported extension: ${sourceFile.name}")
            return null
        }
        val pluginsDir = getModpackPluginsDir(packageName, modpackName)
        pluginsDir.mkdirs()
        val dest = File(pluginsDir, sourceFile.name)
        return try {
            sourceFile.copyTo(dest, overwrite = true).also {
                syncModpackMeta(packageName, modpackName)
                BepInExLog.i("Added mod: ${sourceFile.name}  -> $modpackName")
            }
        } catch (e: Exception) {
            BepInExLog.e("Failed to add mod", e)
            null
        }
    }

    fun addModFromUri(context: Context, packageName: String, modpackName: String, uri: Uri): File? {
        // Resolve real file name from content URI (lastPathSegment is just a numeric ID)
        val fileName = resolveFileName(context, uri)
        if (!isModFileName(fileName)) {
            BepInExLog.w("Rejected mod URI with unsupported extension: $fileName")
            return null
        }
        val pluginsDir = getModpackPluginsDir(packageName, modpackName)
        pluginsDir.mkdirs()
        val dest = File(pluginsDir, fileName)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            syncModpackMeta(packageName, modpackName)
            BepInExLog.i("Added mod from URI: $fileName  -> $modpackName")
            dest
        } catch (e: Exception) {
            BepInExLog.e("Failed to add mod from URI", e)
            null
        }
    }

    fun removeMod(file: File): Boolean {
        return file.delete().also {
            if (it) {
                // Infer packageName and modpackName from path at any plugin depth.
                // Path: .../modpacks/{modpackName}/plugins/{file}
                val pluginsDir = generateSequence(file.parentFile) { it.parentFile }
                    .firstOrNull { it.name == "plugins" }
                val modpackDir = pluginsDir?.parentFile
                val modpacksDir = modpackDir?.parentFile
                val gameRootDir = modpacksDir?.parentFile
                val pkg = gameRootDir?.name ?: ""
                val modpackName = modpackDir?.name ?: ""
                if (pkg.isNotEmpty() && modpackName.isNotEmpty()) {
                    syncModpackMeta(pkg, modpackName)
                }
                BepInExLog.i("Removed mod: ${file.name}")
            }
        }
    }

    fun getModCount(packageName: String, modpackName: String): Int =
        listMods(packageName, modpackName).size

    // Activate / Apply

    /**
     * Apply the modpack contents to the active BepInEx directory.
     * Runtime writes stay in BepInEx/; persistRuntimeState() copies them back later.
     */
    fun applyModpack(packageName: String, modpackName: String): Boolean {
        return try {
            restoreRuntimeState(packageName, modpackName)
            BepInExLog.i("Applied modpack: $modpackName  -> active")
            true
        } catch (e: Exception) {
            BepInExLog.e("Failed to apply modpack", e)
            false
        }
    }

    /** Clear active mods (vanilla mode) */
    fun clearActiveMods(packageName: String) {
        restoreRuntimeState(packageName, null)
        BepInExLog.i("Cleared active mods (vanilla mode)")
    }

    /**
     * Persist [from] then restore [to] under a lock so UI switches and
     * shortcut launches cannot interleave copies.
     */
    suspend fun switchRuntime(packageName: String, from: String?, to: String?): Boolean {
        return runtimeMutex.withLock {
            runtimeSwitchInProgress = true
            try {
                persistRuntimeState(packageName, from)
                if (to.isNullOrEmpty()) {
                    clearActiveMods(packageName)
                    true
                } else {
                    applyModpack(packageName, to)
                }
            } finally {
                runtimeSwitchInProgress = false
            }
        }
    }

    fun persistRuntimeState(packageName: String, modpackName: String?) {
        val destRoot = stateRoot(packageName, modpackName)
        destRoot.mkdirs()
        copyDirContents(BepInExPaths.getConfigDir(packageName), File(destRoot, "config"))
        copyRuntimeLogs(packageName, File(destRoot, "logs"))
        BepInExLog.i("Persisted runtime cfg/logs -> ${destRoot.absolutePath}")
    }

    fun restoreRuntimeState(packageName: String, modpackName: String?) {
        val srcRoot = stateRoot(packageName, modpackName)
        val bepInExDir = BepInExPaths.getBepInExDir(packageName)
        val pluginsDir = BepInExPaths.getPluginsDir(packageName)
        val configDir = BepInExPaths.getConfigDir(packageName)
        val logsDir = BepInExPaths.getLogsDir(packageName)
        val logFile = BepInExPaths.getLogFile(packageName)

        replaceDir(logsDir)
        if (logFile.exists()) logFile.delete()

        if (!modpackName.isNullOrEmpty()) {
            // plugins/config/logs use their dedicated runtime-state handling.
            val pluginsSource = getModpackPluginsDir(packageName, modpackName)
            val disabledDlls = readMeta(packageName, modpackName)?.disabledDlls.orEmpty()
            if (disabledDlls.isNotEmpty()) {
                BepInExLog.i("Skipping ${disabledDlls.size} disabled plugin(s) for $modpackName")
            }
            syncDirContents(
                source = pluginsSource,
                dest = pluginsDir,
                excludedRelativePaths = disabledDlls,
                relativeRoot = pluginsSource
            )
            copyModpackRootContents(srcRoot, bepInExDir)
        } else {
            // Vanilla state never owns plugins, so make sure no active mod is left.
            replaceDir(pluginsDir)
        }
        syncDirContents(File(srcRoot, "config"), configDir)
        // Do NOT restore LogOutput.log from modpack — let each session start fresh.
        // persistRuntimeState() will save the latest logs when the session ends.
        BepInExLog.i("Restored runtime cfg from ${srcRoot.absolutePath}")
    }

    private fun stateRoot(packageName: String, modpackName: String?): File =
        if (modpackName.isNullOrEmpty()) {
            BepInExPaths.getVanillaStateDir(packageName)
        } else {
            getModpackDir(packageName, modpackName)
        }

    private fun copyRuntimeLogs(packageName: String, destLogs: File) {
        destLogs.mkdirs()
        BepInExPaths.getLogFile(packageName).takeIf { it.isFile }?.copyTo(
            File(destLogs, "LogOutput.log"), overwrite = true
        )
        copyDirContents(BepInExPaths.getLogsDir(packageName), destLogs)
    }

    private fun replaceDir(dir: File) {
        dir.deleteRecursively()
        dir.mkdirs()
    }

    private fun copyDirContents(source: File, dest: File) {
        dest.mkdirs()
        if (!source.isDirectory) return
        source.listFiles()?.forEach { child ->
            val target = File(dest, child.name)
            if (child.isDirectory) {
                child.copyRecursively(target, overwrite = true)
            } else {
                child.copyTo(target, overwrite = true)
            }
        }
    }

    /**
     * Mirror [source] into [dest], keeping identical files and updating changed ones.
     * Files whose path relative to [relativeRoot] is in [excludedRelativePaths] are
     * treated as absent, so leftover copies in [dest] are removed.
     */
    private fun syncDirContents(
        source: File,
        dest: File,
        excludedRelativePaths: Set<String> = emptySet(),
        relativeRoot: File = source
    ) {
        if (!source.isDirectory) {
            replaceDir(dest)
            return
        }

        if (dest.exists() && !dest.isDirectory) dest.delete()
        dest.mkdirs()

        val sourceChildren = source.listFiles()
            ?.filterNot { child ->
                child.isFile &&
                    excludedRelativePaths.isNotEmpty() &&
                    normalizeDllKey(child.relativeTo(relativeRoot).invariantSeparatorsPath) in
                        excludedRelativePaths
            }
            ?.associateBy { it.name }
            .orEmpty()
        dest.listFiles()
            ?.filter { it.name !in sourceChildren }
            ?.forEach { it.deleteRecursively() }

        sourceChildren.values.forEach { child ->
            syncEntry(child, File(dest, child.name), excludedRelativePaths, relativeRoot)
        }
    }

    private fun syncEntry(
        source: File,
        target: File,
        excludedRelativePaths: Set<String> = emptySet(),
        relativeRoot: File = source
    ) {
        if (source.isDirectory) {
            syncDirContents(source, target, excludedRelativePaths, relativeRoot)
            return
        }

        if (target.isFile && filesHaveSameContent(source, target)) return
        if (target.exists()) target.deleteRecursively()
        target.parentFile?.mkdirs()
        source.copyTo(target, overwrite = true)
    }

    private fun filesHaveSameContent(first: File, second: File): Boolean {
        if (first.length() != second.length()) return false

        first.inputStream().buffered().use { firstInput ->
            second.inputStream().buffered().use { secondInput ->
                val firstBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
                val secondBuffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val firstRead = firstInput.read(firstBuffer)
                    val secondRead = secondInput.read(secondBuffer)
                    if (firstRead != secondRead) return false
                    if (firstRead == -1) return true
                    for (index in 0 until firstRead) {
                        if (firstBuffer[index] != secondBuffer[index]) return false
                    }
                }
            }
        }
    }

    /**
     * Copy a modpack's BepInEx-root contents, excluding launcher metadata.
     */
    private fun copyModpackRootContents(source: File, bepInExDir: File) {
        if (!source.isDirectory) return

        val runtimeOwned = setOf("modpack.json", "plugins", "config", "logs")
        source.listFiles()
            ?.filter { it.name !in runtimeOwned }
            ?.forEach { child ->
                val target = File(bepInExDir, child.name)
                syncEntry(child, target)
                BepInExLog.i("Restored modpack entry: ${child.name} -> ${target.absolutePath}")
            }
    }

    // Export / Import

    suspend fun exportModpack(
        packageName: String,
        modpackName: String,
        outputFile: File,
        onProgress: suspend (ModpackExportProgress) -> Unit = {}
    ): Boolean {
        val modpackDir = getModpackDir(packageName, modpackName)
        if (!modpackDir.exists()) return false

        val tempFile = File(outputFile.parentFile, ".${outputFile.name}.part")
        return try {
            tempFile.delete()
            var totalFiles = 0L
            var totalBytes = 0L
            for (file in modpackDir.walkTopDown()) {
                currentCoroutineContext().ensureActive()
                if (isExportableFile(modpackDir, file)) {
                    totalFiles++
                    totalBytes += file.length()
                }
            }
            onProgress(ModpackExportProgress("preparing", totalFiles = totalFiles, totalBytes = totalBytes))

            var completedFiles = 0L
            var completedBytes = 0L
            var lastProgressNanos = 0L
            suspend fun reportProgress(force: Boolean = false, currentFile: String? = null) {
                val now = System.nanoTime()
                if (force || now - lastProgressNanos >= 100_000_000L) {
                    lastProgressNanos = now
                    onProgress(
                        ModpackExportProgress(
                            phase = "exporting",
                            currentFile = currentFile,
                            completedFiles = completedFiles,
                            totalFiles = totalFiles,
                            completedBytes = completedBytes,
                            totalBytes = totalBytes
                        )
                    )
                }
            }

            val zos = ZipOutputStream(BufferedOutputStream(FileOutputStream(tempFile)))
            try {
                for (file in modpackDir.walkTopDown()) {
                    currentCoroutineContext().ensureActive()
                    if (!isExportableFile(modpackDir, file)) continue
                    val entryName = "$modpackName/${file.relativeTo(modpackDir).path.replace('\\', '/')}"
                    zos.putNextEntry(ZipEntry(entryName))
                    try {
                        val input = BufferedInputStream(file.inputStream())
                        try {
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                            var read: Int
                            do {
                                currentCoroutineContext().ensureActive()
                                read = input.read(buffer)
                                if (read > 0) {
                                    zos.write(buffer, 0, read)
                                    completedBytes += read
                                    reportProgress(currentFile = file.name)
                                }
                            } while (read >= 0)
                        } finally {
                            input.close()
                        }
                    } finally {
                        zos.closeEntry()
                    }
                    completedFiles++
                    reportProgress(force = true, currentFile = file.name)
                }
            } finally {
                zos.close()
            }

            currentCoroutineContext().ensureActive()
            if (outputFile.exists() && !outputFile.delete()) {
                throw java.io.IOException("Unable to replace existing export: ${outputFile.absolutePath}")
            }
            if (!tempFile.renameTo(outputFile)) {
                tempFile.copyTo(outputFile, overwrite = false)
                tempFile.delete()
            }
            onProgress(
                ModpackExportProgress(
                    phase = "complete",
                    completedFiles = totalFiles,
                    totalFiles = totalFiles,
                    completedBytes = totalBytes,
                    totalBytes = totalBytes
                )
            )
            BepInExLog.i("Exported modpack: $modpackName  -> ${outputFile.absolutePath}")
            true
        } catch (e: CancellationException) {
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            tempFile.delete()
            BepInExLog.e("Failed to export modpack", e)
            false
        }
    }

    private fun isExportableFile(modpackDir: File, file: File): Boolean {
        if (!file.isFile) return false
        val relativePath = file.relativeTo(modpackDir).invariantSeparatorsPath
        return !relativePath.substringBefore('/').equals("logs", ignoreCase = true)
    }

    fun scanDownloadModpacks(): List<File> {
        return downloadDirectories()
            .flatMap { dir ->
                dir.walkTopDown().maxDepth(3)
                    .filter { file ->
                        file.isFile && file.extension.equals(MODPACK_EXTENSION, ignoreCase = true)
                    }
                    .toList()
            }
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
            .filter { containsModpackMetadata(it) }
            .sortedByDescending { it.lastModified() }
    }

    suspend fun importModpack(packageName: String, file: File): ModpackMeta? {
        if (!file.isFile) return null
        return try {
            file.inputStream().use { input ->
                importModpackFromStream(packageName, input, file.name)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BepInExLog.e("Failed to import modpack from ${file.absolutePath}", e)
            null
        }
    }

    suspend fun importModpack(
        packageName: String,
        uri: Uri,
        context: Context,
        archiveName: String? = null
    ): ModpackMeta? {
        val input = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            BepInExLog.e("Failed to open modpack archive", e)
            null
        } ?: return null.also {
            BepInExLog.e("Unable to open modpack archive")
        }
        return try {
            input.use { stream ->
                importModpackFromStream(packageName, stream, archiveName)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BepInExLog.e("Failed to import modpack", e)
            null
        }
    }

    private fun downloadDirectories(): List<File> {
        val dirs = linkedSetOf<File>()
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { dirs.add(it) }
        Environment.getExternalStorageDirectory()?.let { root ->
            dirs.add(File(root, "Download"))
            dirs.add(File(root, "Downloads"))
        }
        return dirs.filter { it.isDirectory }.distinctBy {
            runCatching { it.canonicalPath }.getOrDefault(it.absolutePath)
        }
    }

    private fun containsModpackMetadata(file: File): Boolean {
        return try {
            ZipFile(file).use { zip ->
                zip.entries().asSequence().any { entry ->
                    entry.name.replace('\\', '/')
                        .substringAfterLast('/')
                        .equals("modpack.json", ignoreCase = true)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun importModpackFromStream(
        packageName: String,
        input: InputStream,
        archiveName: String?
    ): ModpackMeta? {
        if (!isModpackFileName(archiveName)) {
            BepInExLog.w("Rejected modpack import with unsupported extension: $archiveName")
            return null
        }
        val requestedName = normalizeModpackName(
            archiveName!!.substringBeforeLast('.')
        )
        val resolvedName = requestedName.ifEmpty { "imported_${System.currentTimeMillis()}" }
        val modpackDir = getModpackDir(packageName, resolvedName)
        val stagingDir = File(
            modpackDir.parentFile,
            ".${resolvedName}.import-${System.currentTimeMillis()}"
        )

        return try {
            stagingDir.deleteRecursively()
            stagingDir.mkdirs()
            val stagingRoot = stagingDir.canonicalFile.toPath()
            val zis = ZipInputStream(BufferedInputStream(input))
            try {
                var entry = zis.nextEntry
                while (entry != null) {
                    currentCoroutineContext().ensureActive()
                    val normalizedEntryName = entry.name.replace('\\', '/').trimStart('/')
                    if (normalizedEntryName.isNotEmpty()) {
                        val entryFile = File(stagingDir, normalizedEntryName).canonicalFile
                        if (!entryFile.toPath().startsWith(stagingRoot)) {
                            throw java.io.IOException("Unsafe archive entry: ${entry.name}")
                        }
                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                        } else {
                            entryFile.parentFile?.mkdirs()
                            val output = BufferedOutputStream(FileOutputStream(entryFile))
                            try {
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                                var read: Int
                                do {
                                    currentCoroutineContext().ensureActive()
                                    read = zis.read(buffer)
                                    if (read > 0) output.write(buffer, 0, read)
                                } while (read >= 0)
                            } finally {
                                output.close()
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            } finally {
                zis.close()
            }

            currentCoroutineContext().ensureActive()
            modpackDir.parentFile?.mkdirs()
            val children = stagingDir.listFiles().orEmpty()
            val sourceDir = if (children.size == 1 && children[0].isDirectory) children[0] else stagingDir
            val metadataFile = sourceDir.walkTopDown().firstOrNull { file ->
                file.isFile && file.name.equals("modpack.json", ignoreCase = true)
            } ?: throw java.io.IOException("Modpack archive is missing modpack.json")
            val importedJson = JSONObject(metadataFile.readText())
            val importedDllNames = parseDllNames(importedJson)
            val importedDisabledDlls = parseDisabledDlls(importedJson)
            val contentRoot = metadataFile.parentFile
                ?: throw java.io.IOException("Invalid modpack metadata location")

            if (modpackDir.exists()) modpackDir.deleteRecursively()
            if (!contentRoot.renameTo(modpackDir)) {
                contentRoot.copyRecursively(modpackDir, overwrite = true)
            }
            stagingDir.deleteRecursively()

            getModpackPluginsDir(packageName, resolvedName).mkdirs()
            getModpackConfigDir(packageName, resolvedName).mkdirs()
            getModpackLogsDir(packageName, resolvedName).mkdirs()

            val meta = ModpackMeta(
                name = resolvedName,
                packageName = packageName,
                createdAt = importedJson.optLong("createdAt", System.currentTimeMillis()),
                modCount = getModCount(packageName, resolvedName),
                createShortcut = importedJson.optBoolean("createShortcut", false),
                dllNames = syncedDllNames(packageName, resolvedName, importedDllNames),
                disabledDlls = syncedDisabledDlls(packageName, resolvedName, importedDisabledDlls)
            )
            writeMeta(meta)
            BepInExLog.i("Imported modpack: $resolvedName")
            meta
        } catch (e: CancellationException) {
            stagingDir.deleteRecursively()
            throw e
        } catch (e: Exception) {
            stagingDir.deleteRecursively()
            BepInExLog.e("Failed to import modpack", e)
            null
        }
    }
    // Metadata persistence

    private fun readMeta(packageName: String, name: String): ModpackMeta? {
        val file = getMetaFile(packageName, name)
        val actualModCount = getModCount(packageName, name)
        if (!file.exists()) {
            return ModpackMeta(
                name = name,
                packageName = packageName,
                modCount = actualModCount
            )
        }
        return try {
            val json = JSONObject(file.readText())
            val dllNames = syncedDllNames(packageName, name, parseDllNames(json))
            val disabledDlls = syncedDisabledDlls(packageName, name, parseDisabledDlls(json))
            val meta = ModpackMeta(
                name = name,
                packageName = packageName,
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                modCount = actualModCount,
                createShortcut = json.optBoolean("createShortcut", false),
                dllNames = dllNames,
                disabledDlls = disabledDlls
            )
            val storedModCount = json.optInt("modCount", -1)
            val storedDllNames = parseDllNames(json)
            val storedDisabledDlls = parseDisabledDlls(json)
            val shouldRewrite =
                storedModCount != actualModCount ||
                    storedDllNames != dllNames ||
                    json.has(DISABLED_DLLS_KEY) && storedDisabledDlls != disabledDlls
            if (shouldRewrite) {
                writeMeta(meta)
            }
            meta
        } catch (e: Exception) {
            ModpackMeta(name = name, packageName = packageName,
                modCount = actualModCount)
        }
    }

    private fun writeMeta(meta: ModpackMeta) {
        val json = JSONObject().apply {
            put("name", meta.name)
            put("packageName", meta.packageName)
            put("createdAt", meta.createdAt)
            put("modCount", meta.modCount)
            put("createShortcut", meta.createShortcut)
            put(DLL_NAMES_KEY, JSONObject().apply {
                meta.dllNames.toSortedMap().forEach { (path, displayName) ->
                    put(path, displayName)
                }
            })
            put(DISABLED_DLLS_KEY, JSONArray().apply {
                meta.disabledDlls.sorted().forEach { put(it) }
            })
        }
        getMetaFile(meta.packageName, meta.name).writeText(json.toString(2))
    }

    private fun parseDllNames(json: JSONObject): Map<String, String> {
        val names = json.optJSONObject(DLL_NAMES_KEY) ?: return emptyMap()
        val result = linkedMapOf<String, String>()
        names.keys().forEach { key ->
            val displayName = names.optString(key).trim()
            if (key.isNotBlank() && displayName.isNotEmpty()) {
                result[normalizeDllKey(key)] = displayName
            }
        }
        return result
    }

    private fun syncedDllNames(
        packageName: String,
        modpackName: String,
        existing: Map<String, String>
    ): Map<String, String> {
        val pluginsDir = getModpackPluginsDir(packageName, modpackName)
        val knownFiles = listMods(packageName, modpackName).associate { file ->
            val relativePath = dllRelativePath(pluginsDir, file)
            relativePath to file.name
        }
        if (knownFiles.isEmpty()) return emptyMap()

        val result = linkedMapOf<String, String>()
        knownFiles.forEach { (relativePath, fileName) ->
            result[relativePath] = resolveDllDisplayName(existing, relativePath, fileName)
        }
        return result
    }

    private fun dllRelativePath(pluginsDir: File, file: File): String =
        normalizeDllKey(file.relativeTo(pluginsDir).invariantSeparatorsPath)

    private fun normalizeDllKey(path: String): String =
        path.replace('\\', '/').trimStart('/')

    private fun resolveDllDisplayName(
        mappings: Map<String, String>,
        relativePath: String,
        fileName: String
    ): String {
        mappings[relativePath]?.takeIf { it.isNotBlank() }?.let { return it }
        mappings[fileName]?.takeIf { it.isNotBlank() }?.let { return it }
        return fileName
    }

    private fun parseDisabledDlls(json: JSONObject): Set<String> {
        val array = json.optJSONArray(DISABLED_DLLS_KEY) ?: return emptySet()
        val result = linkedSetOf<String>()
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isNotEmpty()) result.add(normalizeDllKey(value))
        }
        return result
    }

    private fun syncedDisabledDlls(
        packageName: String,
        modpackName: String,
        existing: Set<String>
    ): Set<String> {
        if (existing.isEmpty()) return emptySet()
        val pluginsDir = getModpackPluginsDir(packageName, modpackName)
        val knownPaths = listMods(packageName, modpackName)
            .map { dllRelativePath(pluginsDir, it) }
            .toSet()
        if (knownPaths.isEmpty()) return emptySet()

        return existing.map(::normalizeDllKey).mapNotNull { path ->
            when {
                path in knownPaths -> path
                else -> knownPaths.firstOrNull { known ->
                    known.substringAfterLast('/') == path
                }
            }
        }.toSet()
    }

    private fun isDllEnabled(
        disabled: Set<String>,
        relativePath: String,
        fileName: String
    ): Boolean {
        if (disabled.isEmpty()) return true
        val key = normalizeDllKey(relativePath)
        return key !in disabled && fileName !in disabled
    }

    fun updateMeta(packageName: String, modpackName: String, createShortcut: Boolean): ModpackMeta? {
        val current = readMeta(packageName, modpackName) ?: return null
        val updated = current.copy(createShortcut = createShortcut)
        writeMeta(updated)
        return updated
    }

    // Icon

    fun getModpackIconFile(packageName: String, modpackName: String): File? {
        val dir = getModpackDir(packageName, modpackName)
        return dir.listFiles()?.firstOrNull {
            it.isFile && it.name.startsWith("icon.") &&
                it.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp")
        }
    }

    fun saveModpackIcon(packageName: String, modpackName: String, bitmap: Bitmap, extension: String = "png") {
        // Delete old icon first
        getModpackIconFile(packageName, modpackName)?.delete()
        val file = File(getModpackDir(packageName, modpackName), "icon.$extension")
        file.outputStream().use { out ->
            val format = when (extension.lowercase()) {
                "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
                "webp" -> Bitmap.CompressFormat.WEBP_LOSSY
                else -> Bitmap.CompressFormat.PNG
            }
            bitmap.compress(format, 90, out)
        }
    }

    fun deleteModpackIcon(packageName: String, modpackName: String) {
        getModpackIconFile(packageName, modpackName)?.delete()
    }

    fun hasModpackIcon(packageName: String, modpackName: String): Boolean =
        getModpackIconFile(packageName, modpackName) != null
}
