package com.bepinex.android.save

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object SaveDataManager {

    private const val TAG = "SaveDataManager"
    private const val DOC_AUTHORITY = "com.android.externalstorage.documents"
    private const val EXCLUDED_SAVE_DIRECTORY = "il2cpp"

    fun needsSafAccess(): Boolean =
        Build.VERSION.SDK_INT in Build.VERSION_CODES.R..34

    fun needsShizuku(): Boolean = Build.VERSION.SDK_INT >= 35

    fun getGameExternalSavesDir(packageName: String): File =
        File(Environment.getExternalStorageDirectory(), "Android/data/$packageName/files")

    fun getGameExternalFilesDir(packageName: String): File =
        File(Environment.getExternalStorageDirectory(), "Android/data/$packageName/files")

    fun getG2LDir(packageName: String): File =
        File(Environment.getExternalStorageDirectory(), "PVZRH_Launcher/$packageName/saves_backup/G2L")

    fun getL2GDir(packageName: String): File =
        File(Environment.getExternalStorageDirectory(), "PVZRH_Launcher/$packageName/saves_backup/L2G")

    fun hasBackup(packageName: String): Boolean {
        val g2l = getG2LDir(packageName)
        return g2l.exists() && (g2l.listFiles()?.isNotEmpty() == true)
    }

    fun hasL2G(packageName: String): Boolean {
        val l2g = getL2GDir(packageName)
        return l2g.exists() && (l2g.listFiles()?.isNotEmpty() == true)
    }

    fun getG2LContents(packageName: String): List<String> {
        val g2l = getG2LDir(packageName)
        if (!g2l.exists()) return emptyList()
        return g2l.listFiles()?.map { it.name } ?: emptyList()
    }

    fun getL2GContents(packageName: String): List<String> {
        val l2g = getL2GDir(packageName)
        if (!l2g.exists()) return emptyList()
        return l2g.listFiles()?.map { it.name } ?: emptyList()
    }

    fun clearG2L(packageName: String) {
        getG2LDir(packageName).deleteRecursively()
    }

    fun clearL2G(packageName: String) {
        getL2GDir(packageName).deleteRecursively()
    }

    fun isShizukuAvailable(): Boolean {
        return try { Shizuku.pingBinder() } catch (e: Exception) { false }
    }

    fun hasShizukuPermission(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) { false }
    }

    suspend fun getGameSavesStatus(context: Context, packageName: String): SaveStatus {
        if (needsShizuku()) {
            if (!isShizukuAvailable()) return SaveStatus.SHIZUKU_NOT_AVAILABLE
            if (!hasShizukuPermission()) return SaveStatus.NEED_PERMISSION
            return withContext(Dispatchers.IO) {
                try {
                    val filesPath = "/sdcard/Android/data/$packageName/files"
                    val (success, output) = ShizukuShell.exec("ls -A \"$filesPath\"")
                    if (!success) return@withContext SaveStatus.NOT_FOUND
                    val names = output.trim().split("\n")
                        .filter { it.isNotEmpty() && it != EXCLUDED_SAVE_DIRECTORY }
                    if (names.isEmpty()) SaveStatus.EMPTY else SaveStatus.FOUND(names.size, names)
                } catch (e: Exception) {
                    Log.e(TAG, "Shizuku query failed", e)
                    SaveStatus.NOT_FOUND
                }
            }
        } else if (needsSafAccess()) {
            return getSavesStatusViaSaf(context, packageName)
        } else {
            return getSavesStatusDirect(packageName)
        }
    }

    private fun getSavesStatusDirect(packageName: String): SaveStatus {
        val dir = getGameExternalSavesDir(packageName)
        if (!dir.exists()) return SaveStatus.NOT_FOUND
        val files = dir.listFiles() ?: return SaveStatus.NOT_FOUND
        if (files.isEmpty()) return SaveStatus.EMPTY
        val saveFiles = files.filter { it.name != EXCLUDED_SAVE_DIRECTORY }
        if (saveFiles.isEmpty()) return SaveStatus.EMPTY
        return SaveStatus.FOUND(saveFiles.size, saveFiles.map { it.name })
    }

    fun getSavesStatusViaSaf(context: Context, packageName: String): SaveStatus {
        if (!needsSafAccess()) return getSavesStatusDirect(packageName)
        if (!hasPersistedSafPermission(context, packageName)) return SaveStatus.NEED_PERMISSION
        return try {
            val treeUri = getPersistedSafTreeUri(context, packageName) ?: return SaveStatus.NEED_PERMISSION
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri)
            )
            val cursor = context.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
                null, null, null
            ) ?: return SaveStatus.NOT_FOUND
            val names = mutableListOf<String>()
            cursor.use {
                while (it.moveToNext()) {
                    val name = it.getString(0)
                    val mime = it.getString(1)
                    if (name != EXCLUDED_SAVE_DIRECTORY) names.add(name)
                }
            }
            if (names.isEmpty()) SaveStatus.EMPTY else SaveStatus.FOUND(names.size, names)
        } catch (e: Exception) {
            Log.e(TAG, "SAF query failed", e)
            SaveStatus.NOT_FOUND
        }
    }

    suspend fun g2lBackup(context: Context, packageName: String): Result<Int> = withContext(Dispatchers.IO) {
        when {
            needsShizuku() -> g2lBackupViaShizuku(packageName)
            needsSafAccess() -> g2lBackupViaSaf(context, packageName)
            else -> g2lBackupDirect(packageName)
        }
    }

    private fun g2lBackupViaShizuku(packageName: String): Result<Int> = runCatching {
        val destPath = toShizukuPath(getG2LDir(packageName))
        ShizukuShell.execOrThrow("rm -rf \"$destPath\" && mkdir -p \"$destPath\"")

        val filesSrc = "/sdcard/Android/data/$packageName/files"
        copyTopLevelViaShizuku(filesSrc, destPath)

        countFiles(getG2LDir(packageName))
    }

    private fun g2lBackupViaSaf(context: Context, packageName: String): Result<Int> = runCatching {
        val destDir = getG2LDir(packageName)
        destDir.deleteRecursively()
        destDir.mkdirs()

        val treeUri = getPersistedSafTreeUri(context, packageName)
            ?: throw IllegalStateException("SAF permission not granted")

        var count = 0

        count += copySafDirToDir(context, treeUri, destDir, setOf(EXCLUDED_SAVE_DIRECTORY))

        count
    }

    private fun copySafDirToDir(
        context: Context,
        treeDocUri: Uri,
        destDir: File,
        excludedNames: Set<String> = emptySet()
    ): Int {
        var count = 0
        destDir.mkdirs()

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeDocUri, DocumentsContract.getTreeDocumentId(treeDocUri))
        val cursor = context.contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null, null, null
        ) ?: return 0

        cursor.use {
            while (it.moveToNext()) {
                val name = it.getString(0)
                val mime = it.getString(1)
                val docId = it.getString(2)

                if (name in excludedNames) continue

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    val childTreeUri = DocumentsContract.buildDocumentUriUsingTree(treeDocUri, docId)
                    count += copySafDirToDir(context, childTreeUri, File(destDir, name), excludedNames)
                } else {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeDocUri, docId)
                    val pfd = context.contentResolver.openFileDescriptor(fileUri, "r") ?: continue
                    val dest = File(destDir, name)
                    FileInputStream(pfd.fileDescriptor).use { input ->
                        FileOutputStream(dest).use { output -> input.copyTo(output) }
                    }
                    count++
                }
            }
        }
        return count
    }

    private fun g2lBackupDirect(packageName: String): Result<Int> = runCatching {
        val destDir = getG2LDir(packageName)
        destDir.deleteRecursively()
        destDir.mkdirs()

        val filesSrc = getGameExternalFilesDir(packageName)
        if (filesSrc.exists()) {
            copyFileRecursive(filesSrc, destDir, setOf(EXCLUDED_SAVE_DIRECTORY))
        }

        countFiles(destDir)
    }

    suspend fun l2gRestore(context: Context, packageName: String): Result<Int> = withContext(Dispatchers.IO) {
        restoreFromDirectory(context, packageName, getL2GDir(packageName), nestedSaves = false)
    }

    suspend fun restoreBackup(context: Context, packageName: String): Result<Int> = withContext(Dispatchers.IO) {
        restoreFromDirectory(context, packageName, getG2LDir(packageName), nestedSaves = false)
    }

    private fun restoreFromDirectory(
        context: Context,
        packageName: String,
        sourceDir: File,
        nestedSaves: Boolean
    ): Result<Int> {
        if (!sourceDir.exists() || sourceDir.listFiles()?.isNotEmpty() != true) {
            return Result.failure(IllegalStateException("No save data to restore"))
        }
        val payloadDir = resolveRestorePayloadDir(sourceDir, nestedSaves)
        if (!payloadDir.exists()) {
            return Result.failure(IllegalStateException("No save data to restore"))
        }
        return when {
            needsShizuku() -> restoreViaShizuku(packageName, sourceDir, payloadDir)
            needsSafAccess() -> restoreViaSaf(context, packageName, sourceDir, payloadDir)
            else -> restoreDirect(packageName, sourceDir, payloadDir)
        }
    }

    private fun resolveRestorePayloadDir(sourceDir: File, nestedSaves: Boolean): File {
        val savesDir = File(sourceDir, "Saves")
        if (nestedSaves) return savesDir

        val rootEntries = sourceDir.listFiles()
            ?.filter { it.name != "playerData.json" }
            ?: emptyList()
        return if (savesDir.isDirectory && rootEntries.size == 1 && rootEntries.first() == savesDir) {
            savesDir
        } else {
            sourceDir
        }
    }

    private fun restoreViaShizuku(
        packageName: String,
        sourceDir: File,
        payloadDir: File
    ): Result<Int> = runCatching {
        val srcPath = toShizukuPath(payloadDir)
        val filesDest = "/sdcard/Android/data/$packageName/files"

        ShizukuShell.execOrThrow("mkdir -p \"$filesDest\"")
        copyTopLevelViaShizuku(srcPath, filesDest)

        copyLegacyPlayerDataViaShizuku(sourceDir, payloadDir, filesDest)

        countFiles(payloadDir) + if (sourceDir != payloadDir && File(sourceDir, "playerData.json").exists()) 1 else 0
    }

    private fun copyTopLevelViaShizuku(sourcePath: String, destinationPath: String) {
        val command =
            "for item in \"$sourcePath\"/*; do " +
                "case \"\$item\" in \"$sourcePath/$EXCLUDED_SAVE_DIRECTORY\") continue ;; esac; " +
                "[ -e \"\$item\" ] || continue; " +
                "cp -rf \"\$item\" \"$destinationPath/\" || exit 1; " +
                "done"
        ShizukuShell.execOrThrow(command)
    }

    private fun copyLegacyPlayerDataViaShizuku(sourceDir: File, payloadDir: File, filesDest: String) {
        if (sourceDir == payloadDir) return
        val playerData = File(sourceDir, "playerData.json")
        if (playerData.exists()) {
            ShizukuShell.execOrThrow("cp \"${toShizukuPath(playerData)}\" \"$filesDest/playerData.json\"")
        }
    }

    private fun restoreViaSaf(
        context: Context,
        packageName: String,
        sourceDir: File,
        payloadDir: File
    ): Result<Int> = runCatching {
        val treeUri = getPersistedSafTreeUri(context, packageName)
            ?: throw IllegalStateException("SAF permission not granted")

        val filesDocId = DocumentsContract.getTreeDocumentId(treeUri)
        var count = copyDirToSafDir(
            context,
            treeUri,
            filesDocId,
            payloadDir,
            setOf(EXCLUDED_SAVE_DIRECTORY)
        )

        if (sourceDir != payloadDir) {
            count += copyLegacyPlayerDataViaSaf(context, treeUri, filesDocId, sourceDir)
        }

        count
    }

    private fun copyLegacyPlayerDataViaSaf(
        context: Context,
        treeUri: Uri,
        filesDocId: String,
        sourceDir: File
    ): Int {
        val playerData = File(sourceDir, "playerData.json")
        if (!playerData.exists()) return 0
        return try {
            val playerDataDocId = "$filesDocId/playerData.json"
            ensureSafFileExists(context, treeUri, playerDataDocId)
            val playerDataUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, playerDataDocId)
            val pfd = context.contentResolver.openFileDescriptor(playerDataUri, "w") ?: return 0
            FileOutputStream(pfd.fileDescriptor).use { output ->
                FileInputStream(playerData).use { input -> input.copyTo(output) }
            }
            1
        } catch (_: Exception) {
            0
        }
    }

    private fun copyDirToSafDir(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        srcDir: File,
        excludedNames: Set<String> = emptySet()
    ): Int {
        var count = 0
        srcDir.listFiles()?.forEach { child ->
            if (child.name in excludedNames) return@forEach
            if (child.isDirectory) {
                val dirDocId = "$parentDocId/${child.name}"
                ensureSafDirExists(context, treeUri, dirDocId)
                count += copyDirToSafDir(context, treeUri, dirDocId, child, excludedNames)
            } else {
                val fileDocId = "$parentDocId/${child.name}"
                ensureSafFileExists(context, treeUri, fileDocId)
                val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, fileDocId)
                val pfd = context.contentResolver.openFileDescriptor(fileUri, "w") ?: return@forEach
                FileOutputStream(pfd.fileDescriptor).use { output ->
                    FileInputStream(child).use { input -> input.copyTo(output) }
                }
                count++
            }
        }
        return count
    }

    private fun ensureSafDirExists(context: Context, treeUri: Uri, docId: String) {
        try {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            context.contentResolver.query(
                DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null, null, null
            )?.use { if (it.moveToFirst()) return }
        } catch (_: Exception) {}
        try {
            val parentDocId = docId.substringBeforeLast("/")
            val dirName = docId.substringAfterLast("/")
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
            DocumentsContract.createDocument(context.contentResolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, dirName)
        } catch (_: Exception) {}
    }

    private fun ensureSafFileExists(context: Context, treeUri: Uri, docId: String) {
        try {
            context.contentResolver.query(
                DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null, null, null
            )?.use { if (it.moveToFirst()) return }
        } catch (_: Exception) {}
        try {
            val parentDocId = docId.substringBeforeLast("/")
            val fileName = docId.substringAfterLast("/")
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
            DocumentsContract.createDocument(context.contentResolver, parentUri, "application/octet-stream", fileName)
        } catch (_: Exception) {}
    }

    private fun restoreDirect(
        packageName: String,
        sourceDir: File,
        payloadDir: File
    ): Result<Int> = runCatching {
        val filesDest = getGameExternalFilesDir(packageName)
        copyFileRecursive(payloadDir, filesDest, setOf(EXCLUDED_SAVE_DIRECTORY))

        if (sourceDir != payloadDir) {
            val pdSrc = File(sourceDir, "playerData.json")
            if (pdSrc.exists()) {
                pdSrc.copyTo(File(filesDest, "playerData.json"), overwrite = true)
            }
        }

        countFiles(filesDest)
    }

    private fun toShizukuPath(file: File): String {
        val externalRoot = Environment.getExternalStorageDirectory().absolutePath
        val absolutePath = file.absolutePath
        return if (absolutePath == externalRoot) {
            "/sdcard"
        } else if (absolutePath.startsWith("$externalRoot/")) {
            "/sdcard" + absolutePath.removePrefix(externalRoot)
        } else {
            absolutePath
        }
    }

    fun getLauncherSavesDir(context: Context): File = File(context.getExternalFilesDir(null), "Saves")

    fun getLauncherPlayerDataFile(context: Context): File = File(context.getExternalFilesDir(null), "playerData.json")

    fun g2lImportToLauncher(context: Context, packageName: String): Result<Int> = runCatching {
        val g2lDir = getG2LDir(packageName)
        var count = 0

        val payloadDir = resolveRestorePayloadDir(g2lDir, nestedSaves = false)
        if (payloadDir.exists()) {
            val savesDest = getLauncherSavesDir(context)
            count += copyFileRecursive(
                payloadDir,
                savesDest,
                setOf(EXCLUDED_SAVE_DIRECTORY, "playerData.json")
            )
        }

        val pdSrc = File(g2lDir, "playerData.json")
        if (pdSrc.exists()) {
            pdSrc.copyTo(getLauncherPlayerDataFile(context), overwrite = true)
            count++
        }

        count
    }

    fun launcherExportToL2g(context: Context, packageName: String): Result<Int> = runCatching {
        val l2gDir = getL2GDir(packageName)
        l2gDir.deleteRecursively()
        l2gDir.mkdirs()
        var count = 0

        val savesSrc = getLauncherSavesDir(context)
        if (savesSrc.exists()) {
            count += copyFileRecursive(savesSrc, l2gDir, setOf(EXCLUDED_SAVE_DIRECTORY))
        }

        val pdSrc = getLauncherPlayerDataFile(context)
        if (pdSrc.exists()) {
            pdSrc.copyTo(File(l2gDir, "playerData.json"), overwrite = true)
            count++
        }

        count
    }

    fun hasPersistedSafPermission(context: Context, packageName: String): Boolean {
        if (!needsSafAccess()) return true
        return getPersistedSafTreeUri(context, packageName) != null
    }

    fun getPersistedSafTreeUri(context: Context, packageName: String): Uri? {
        val expectedDocId = "primary:Android/data/$packageName/files"
        return context.contentResolver.persistedUriPermissions
            .firstOrNull { it.isReadPermission && it.isWritePermission }
            ?.uri
            ?.takeIf { uri ->
                val docId = try { DocumentsContract.getTreeDocumentId(uri) } catch (_: Exception) { null }
                docId == expectedDocId
            }
    }

    fun buildSafInitialUri(packageName: String): Uri {
        val docId = "primary:Android/data/$packageName/files"
        return DocumentsContract.buildDocumentUri(DOC_AUTHORITY, docId)
    }

    fun createSafPickerIntent(packageName: String): Intent {
        val initialUri = buildSafInitialUri(packageName)
        return Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
            putExtra("android.provider.extra.SHOW_ADVANCED", true)
            putExtra("android.content.extra.SHOW_ADVANCED", true)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        }
    }

    fun handleSafPickerResult(context: Context, resultCode: Int, data: Intent?): Boolean {
        if (resultCode != android.app.Activity.RESULT_OK || data?.data == null) return false
        val treeUri = data.data ?: return false
        return try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to take persistable URI permission", e)
            false
        }
    }

    private fun copyFileRecursive(
        source: File,
        dest: File,
        excludedNames: Set<String> = emptySet()
    ): Int {
        var count = 0
        if (source.isDirectory) {
            dest.mkdirs()
            source.listFiles()?.forEach { child ->
                if (child.name !in excludedNames) {
                    count += copyFileRecursive(child, File(dest, child.name), excludedNames)
                }
            }
        } else {
            FileInputStream(source).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            count++
        }
        return count
    }

    private fun countFiles(dir: File): Int {
        var count = 0
        dir.listFiles()?.forEach { child ->
            if (child.name == EXCLUDED_SAVE_DIRECTORY) return@forEach
            if (child.isDirectory) count += countFiles(child) else count++
        }
        return count
    }

    sealed class SaveStatus {
        data object NEED_PERMISSION : SaveStatus()
        data object NOT_FOUND : SaveStatus()
        data object EMPTY : SaveStatus()
        data object SHIZUKU_NOT_AVAILABLE : SaveStatus()
        data class FOUND(val fileCount: Int, val names: List<String> = emptyList()) : SaveStatus()
    }
}
