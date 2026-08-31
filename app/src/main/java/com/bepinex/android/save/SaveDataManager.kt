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

    fun needsSafAccess(): Boolean =
        Build.VERSION.SDK_INT in Build.VERSION_CODES.R..34

    fun needsShizuku(): Boolean = Build.VERSION.SDK_INT >= 35

    fun getGameExternalSavesDir(packageName: String): File =
        File(Environment.getExternalStorageDirectory(), "Android/data/$packageName/files/Saves")

    fun getGameExternalFilesDir(packageName: String): File =
        File(Environment.getExternalStorageDirectory(), "Android/data/$packageName/files")

    fun hasGameLaunched(context: Context): Boolean {
        val savesDir = File(context.getExternalFilesDir(null), "Saves")
        return savesDir.exists()
    }

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
                    val savesPath = "/sdcard/Android/data/$packageName/files/Saves"
                    val (success, output) = ShizukuShell.exec("ls \"$savesPath\"")
                    if (!success) return@withContext SaveStatus.NOT_FOUND
                    val names = output.trim().split("\n").filter { it.isNotEmpty() }
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
        return SaveStatus.FOUND(files.size, files.map { it.name })
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
                    if (mime != DocumentsContract.Document.MIME_TYPE_DIR) names.add(name)
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
        val destPath = getG2LDir(packageName).absolutePath
        ShizukuShell.execOrThrow("rm -rf \"$destPath\" && mkdir -p \"$destPath\"")

        val savesSrc = "/sdcard/Android/data/$packageName/files/Saves"
        ShizukuShell.exec("cp -r \"$savesSrc\" \"$destPath/Saves\"")

        val pdSrc = "/sdcard/Android/data/$packageName/files/playerData.json"
        ShizukuShell.exec("cp \"$pdSrc\" \"$destPath/playerData.json\"")

        countFiles(getG2LDir(packageName))
    }

    private fun g2lBackupViaSaf(context: Context, packageName: String): Result<Int> = runCatching {
        val destDir = getG2LDir(packageName)
        destDir.deleteRecursively()
        destDir.mkdirs()

        val treeUri = getPersistedSafTreeUri(context, packageName)
            ?: throw IllegalStateException("SAF permission not granted")

        var count = 0

        val savesTreeUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val filesTreeUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri).replace("/Saves", "")
        )

        count += copySafDirToDir(context, savesTreeUri, File(destDir, "Saves"))

        try {
            val pdUri = DocumentsContract.buildDocumentUriUsingTree(
                filesTreeUri,
                DocumentsContract.getTreeDocumentId(filesTreeUri) + "/playerData.json"
            )
            val pfd = context.contentResolver.openFileDescriptor(pdUri, "r")
            if (pfd != null) {
                val dest = File(destDir, "playerData.json")
                FileInputStream(pfd.fileDescriptor).use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
                count++
            }
        } catch (_: Exception) {}

        count
    }

    private fun copySafDirToDir(context: Context, treeDocUri: Uri, destDir: File): Int {
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

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    val childTreeUri = DocumentsContract.buildDocumentUriUsingTree(treeDocUri, docId)
                    count += copySafDirToDir(context, childTreeUri, File(destDir, name))
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

        val savesSrc = getGameExternalSavesDir(packageName)
        if (savesSrc.exists()) {
            copyFileRecursive(savesSrc, File(destDir, "Saves"))
        }

        val pdSrc = File(getGameExternalFilesDir(packageName), "playerData.json")
        if (pdSrc.exists()) {
            pdSrc.copyTo(File(destDir, "playerData.json"), overwrite = true)
        }

        countFiles(destDir)
    }

    suspend fun l2gRestore(context: Context, packageName: String): Result<Int> = withContext(Dispatchers.IO) {
        when {
            needsShizuku() -> l2gRestoreViaShizuku(packageName)
            needsSafAccess() -> l2gRestoreViaSaf(context, packageName)
            else -> l2gRestoreDirect(packageName)
        }
    }

    private fun l2gRestoreViaShizuku(packageName: String): Result<Int> = runCatching {
        val srcPath = getL2GDir(packageName).absolutePath
        val savesDest = "/sdcard/Android/data/$packageName/files/Saves"

        ShizukuShell.execOrThrow("mkdir -p \"$savesDest\"")
        ShizukuShell.execOrThrow("cp -r \"$srcPath/Saves\"/. \"$savesDest\"/")

        val pdSrc = "$srcPath/playerData.json"
        val pdDest = "/sdcard/Android/data/$packageName/files/playerData.json"
        ShizukuShell.exec("cp \"$pdSrc\" \"$pdDest\"")

        countFiles(getL2GDir(packageName))
    }

    private fun l2gRestoreViaSaf(context: Context, packageName: String): Result<Int> = runCatching {
        val srcDir = getL2GDir(packageName)

        val treeUri = getPersistedSafTreeUri(context, packageName)
            ?: throw IllegalStateException("SAF permission not granted")

        val savesDocId = DocumentsContract.getTreeDocumentId(treeUri)
        var count = 0

        val savesSrc = File(srcDir, "Saves")
        if (savesSrc.exists()) {
            count += copyDirToSafDir(context, treeUri, savesDocId, savesSrc)
        }

        try {
            val pdSrc = File(srcDir, "playerData.json")
            if (pdSrc.exists()) {
                val filesDocId = savesDocId.replace("/Saves", "")
                val filesTreeUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, filesDocId)
                val pdDocId = "$filesDocId/playerData.json"
                ensureSafFileExists(context, treeUri, pdDocId)
                val pdUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, pdDocId)
                val pfd = context.contentResolver.openFileDescriptor(pdUri, "w")
                if (pfd != null) {
                    FileOutputStream(pfd.fileDescriptor).use { output ->
                        FileInputStream(pdSrc).use { input -> input.copyTo(output) }
                    }
                    count++
                }
            }
        } catch (_: Exception) {}

        count
    }

    private fun copyDirToSafDir(context: Context, treeUri: Uri, parentDocId: String, srcDir: File): Int {
        var count = 0
        srcDir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                val dirDocId = "$parentDocId/${child.name}"
                ensureSafDirExists(context, treeUri, dirDocId)
                count += copyDirToSafDir(context, treeUri, dirDocId, child)
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

    private fun l2gRestoreDirect(packageName: String): Result<Int> = runCatching {
        val srcDir = getL2GDir(packageName)
        val savesDest = getGameExternalSavesDir(packageName)

        val savesSrc = File(srcDir, "Saves")
        if (savesSrc.exists()) {
            copyFileRecursive(savesSrc, savesDest)
        }

        val pdSrc = File(srcDir, "playerData.json")
        if (pdSrc.exists()) {
            val pdDest = File(getGameExternalFilesDir(packageName), "playerData.json")
            pdSrc.copyTo(pdDest, overwrite = true)
        }

        countFiles(getGameExternalSavesDir(packageName))
    }

    fun getLauncherSavesDir(context: Context): File = File(context.getExternalFilesDir(null), "Saves")

    fun getLauncherPlayerDataFile(context: Context): File = File(context.getExternalFilesDir(null), "playerData.json")

    fun g2lImportToLauncher(context: Context, packageName: String): Result<Int> = runCatching {
        val g2lDir = getG2LDir(packageName)
        var count = 0

        val savesSrc = File(g2lDir, "Saves")
        if (savesSrc.exists()) {
            val savesDest = getLauncherSavesDir(context)
            count += copyFileRecursive(savesSrc, savesDest)
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
        l2gDir.mkdirs()
        var count = 0

        val savesSrc = getLauncherSavesDir(context)
        if (savesSrc.exists()) {
            val savesDest = File(l2gDir, "Saves")
            count += copyFileRecursive(savesSrc, savesDest)
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
        val expectedDocId = "primary:Android/data/$packageName/files/Saves"
        return context.contentResolver.persistedUriPermissions
            .firstOrNull { it.isReadPermission && it.isWritePermission }
            ?.uri
            ?.takeIf { uri ->
                val docId = try { DocumentsContract.getTreeDocumentId(uri) } catch (_: Exception) { null }
                docId == expectedDocId || uri.toString().contains("Android/data/$packageName/files/Saves")
            }
    }

    fun buildSafInitialUri(packageName: String): Uri {
        val docId = "primary:Android/data/$packageName/files/Saves"
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

    private fun copyFileRecursive(source: File, dest: File): Int {
        var count = 0
        if (source.isDirectory) {
            dest.mkdirs()
            source.listFiles()?.forEach { child ->
                count += copyFileRecursive(child, File(dest, child.name))
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
