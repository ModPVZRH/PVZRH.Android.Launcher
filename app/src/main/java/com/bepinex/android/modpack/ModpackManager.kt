package com.bepinex.android.modpack

import android.content.Context
import android.net.Uri
import com.bepinex.android.BepInExLog
import com.bepinex.android.BepInExPaths
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Metadata for a modpack.
 */
data class ModpackMeta(
    val name: String,
    val packageName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val modCount: Int = 0
)

/**
 * Manages modpack CRUD operations on the file system.
 *
 * Directory layout:
 * ```
 * /storage/emulated/0/PVZRH_Launcher/{pkg}/
 *   modpacks/
 *     {name}/
 *       modpack.json       (metadata)
 *       plugins/           (mod DLL files)
 *       config/            (mod .cfg config files)
 * ```
 */
class ModpackManager {

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

        // The directory rename is the actual operation. Metadata is repaired on
        // a best-effort basis so a write error cannot report a successful rename
        // as failed and leave the active-pack setting pointing at the old name.
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

    /** Re-sync modpack.json modCount with actual file system count */
    private fun syncModpackMeta(packageName: String, modpackName: String) {
        val current = readMeta(packageName, modpackName) ?: return
        val real = getModCount(packageName, modpackName)
        if (current.modCount != real) {
            writeMeta(current.copy(modCount = real))
        }
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

    fun listConfigs(packageName: String, modpackName: String): List<File> {
        val configDir = getModpackConfigDir(packageName, modpackName)
        return configDir.listFiles()?.filter { it.isFile && it.extension == "cfg" } ?: emptyList()
    }

    fun addMod(packageName: String, modpackName: String, sourceFile: File): File? {
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
        val fileName = resolveFileName(context, uri) ?: "mod_${System.currentTimeMillis()}.dll"
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
     *
     * A modpack may contain any BepInEx-root entry, not only plugins/config/logs
     * (for example patchers, monomod, or other runtime folders). Runtime writes
     * stay in BepInEx/; persistRuntimeState() copies them back later.
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
            // Synchronize instead of deleting everything on every launch. Files
            // that are already identical are kept, while changed/new files are
            // copied and stale files from the previous state are removed.
            syncDirContents(getModpackPluginsDir(packageName, modpackName), pluginsDir)
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
     * Mirror [source] into [dest] without rewriting files that already have the
     * same content. Unlike a simple "target exists" check, this still applies an
     * updated mod/config that keeps the same file name.
     */
    private fun syncDirContents(source: File, dest: File) {
        if (!source.isDirectory) {
            replaceDir(dest)
            return
        }

        if (dest.exists() && !dest.isDirectory) dest.delete()
        dest.mkdirs()

        val sourceChildren = source.listFiles()?.associateBy { it.name }.orEmpty()
        dest.listFiles()
            ?.filter { it.name !in sourceChildren }
            ?.forEach { it.deleteRecursively() }

        sourceChildren.values.forEach { child ->
            syncEntry(child, File(dest, child.name))
        }
    }

    private fun syncEntry(source: File, target: File) {
        if (source.isDirectory) {
            syncDirContents(source, target)
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
     * Copy a modpack's BepInEx-root contents while excluding launcher metadata
     * and runtime-owned directories. Existing identical files are retained;
     * changed/new files are copied and stale children are removed.
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

    fun exportModpack(packageName: String, modpackName: String, outputFile: File): Boolean {
        val modpackDir = getModpackDir(packageName, modpackName)
        if (!modpackDir.exists()) return false

        return try {
            ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                modpackDir.walkTopDown().filter { file ->
                    if (!file.isFile) return@filter false
                    val relativePath = file.relativeTo(modpackDir).path.replace('\\', '/')
                    !relativePath.substringBefore('/').equals("logs", ignoreCase = true)
                }.forEach { file ->
                    val entryName = "$modpackName/${file.relativeTo(modpackDir).path.replace('\\', '/')}"
                    zos.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
            BepInExLog.i("Exported modpack: $modpackName  -> ${outputFile.absolutePath}")
            true
        } catch (e: Exception) {
            BepInExLog.e("Failed to export modpack", e)
            false
        }
    }

    fun importModpack(packageName: String, uri: Uri, context: Context, zipName: String? = null): ModpackMeta? {
        try {
            val tempDir = File(context.cacheDir, "modpack_import_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name.trim('/')
                        val outFile = File(tempDir, name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
            }

            val resolvedName = zipName ?: "imported_${System.currentTimeMillis()}"
            val modpackDir = getModpackDir(packageName, resolvedName)
            if (modpackDir.exists()) modpackDir.deleteRecursively()

            // Find the actual content directory inside temp (skip top-level dir if present)
            val topDirs = tempDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
            val sourceDir = if (topDirs.size == 1) topDirs[0] else tempDir
            sourceDir.copyRecursively(modpackDir, overwrite = true)
            tempDir.deleteRecursively()

            getModpackPluginsDir(packageName, resolvedName).mkdirs()
            getModpackConfigDir(packageName, resolvedName).mkdirs()
            getModpackLogsDir(packageName, resolvedName).mkdirs()

            val meta = ModpackMeta(name = resolvedName, packageName = packageName,
                modCount = getModCount(packageName, resolvedName))
            writeMeta(meta)
            BepInExLog.i("Imported modpack: $resolvedName")
            return meta
        } catch (e: Exception) {
            BepInExLog.e("Failed to import modpack", e)
            return null
        }
    }

    // Metadata persistence

    private fun readMeta(packageName: String, name: String): ModpackMeta? {
        val file = getMetaFile(packageName, name)
        // modCount in modpack.json is only a cache. Files can also be copied into
        // the modpack directory outside the launcher, so always derive the count
        // from the current plugins directory when displaying the pack.
        val actualModCount = getModCount(packageName, name)
        if (!file.exists()) {
            // Infer from directory
            return ModpackMeta(
                name = name,
                packageName = packageName,
                modCount = actualModCount
            )
        }
        return try {
            val json = JSONObject(file.readText())
            // The directory being read is the source of truth for identity.
            // This also keeps metadata writes inside the renamed directory when
            // an older modpack.json still contains the previous name.
            val meta = ModpackMeta(
                name = name,
                packageName = packageName,
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                modCount = actualModCount
            )
            // Repair stale metadata so exports and later reads also contain the
            // current value rather than the old cached count.
            if (json.optInt("modCount", -1) != actualModCount) {
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
        }
        getMetaFile(meta.packageName, meta.name).writeText(json.toString(2))
    }
}
