package com.bepinex.android

import android.content.Context
import com.bepinex.android.update.UpdateChecker
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Provides unstripped libunity.so / libunity.sym.so.
 * [UpdateChecker.LibSource.offlineMode] extracts from assets/unstrip.lib.zip;
 * otherwise downloads from github / gh-proxy / cos, then falls back to assets.
 */
object LibUnityDownloader {

    private const val TAG = "LibUnityDownloader"
    private const val ASSET_ZIP = "unstrip.lib.zip"
    private const val LIB_NAME = "libunity.so"
    private const val SYM_NAME = "libunity.sym.so"
    private const val LIB_MIN_SIZE = 1024L * 1024L
    private const val SYM_MIN_SIZE = 1024L

    fun ensureLibUnity(
        context: Context,
        targetDir: File,
        onProgress: (String) -> Unit = {}
    ): File? {
        targetDir.mkdirs()

        val destLib = File(targetDir, LIB_NAME)
        val destSym = File(targetDir, SYM_NAME)
        val libReady = isReady(destLib, LIB_MIN_SIZE)
        val symReady = isReady(destSym, SYM_MIN_SIZE)
        if (libReady) {
            BepInExLog.i("$TAG: Using cached $LIB_NAME (${destLib.length()} bytes)")
        }
        if (symReady) {
            BepInExLog.i("$TAG: Using cached $SYM_NAME (${destSym.length()} bytes)")
        }
        if (libReady && symReady) return destLib

        val (libSource, symSource) = UpdateChecker.resolveLibSources(context)
        val preferProxy = UpdateChecker.preferProxyMirrors(context)

        if (!libReady) {
            obtainFile(
                context = context,
                destFile = destLib,
                fileName = LIB_NAME,
                source = libSource,
                preferProxy = preferProxy,
                minSize = LIB_MIN_SIZE,
                required = true,
                onProgress = onProgress
            )
        }
        if (!isReady(destSym, SYM_MIN_SIZE)) {
            obtainFile(
                context = context,
                destFile = destSym,
                fileName = SYM_NAME,
                source = symSource,
                preferProxy = preferProxy,
                minSize = SYM_MIN_SIZE,
                required = false,
                onProgress = onProgress
            )
        }

        return destLib.takeIf { isReady(it, LIB_MIN_SIZE) }
    }

    private fun obtainFile(
        context: Context,
        destFile: File,
        fileName: String,
        source: UpdateChecker.LibSource,
        preferProxy: Boolean,
        minSize: Long,
        required: Boolean,
        onProgress: (String) -> Unit
    ) {
        if (!source.offlineMode) {
            val urls = source.downloadUrls(preferProxy)
            for (url in urls) {
                onProgress("Downloading $fileName...")
                BepInExLog.i("$TAG: Downloading $fileName from $url")
                if (downloadAndVerify(url, destFile, onProgress, minSize) != null) return
                BepInExLog.w("$TAG: Failed $fileName from $url, trying next...")
            }
            BepInExLog.w("$TAG: Network $fileName failed; falling back to assets")
        } else {
            BepInExLog.i("$TAG: offline-mode for $fileName, extracting from assets")
        }

        if (extractFromAssets(context, destFile.parentFile ?: return, fileName, onProgress) &&
            isReady(destFile, minSize)
        ) {
            return
        }
        if (required) {
            BepInExLog.e("$TAG: Failed to obtain $fileName")
        } else {
            BepInExLog.w("$TAG: Optional $fileName unavailable")
        }
    }

    private fun extractFromAssets(
        context: Context,
        targetDir: File,
        fileName: String,
        onProgress: (String) -> Unit
    ): Boolean {
        onProgress("Extracting $fileName...")
        return try {
            var extracted = false
            context.assets.open(ASSET_ZIP).use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = File(entry.name.replace('\\', '/')).name
                        if (!entry.isDirectory && name == fileName) {
                            val destFile = File(targetDir, name)
                            val tempFile = File(targetDir, "$name.tmp")
                            FileOutputStream(tempFile).use { output -> zis.copyTo(output) }
                            if (destFile.exists()) destFile.delete()
                            if (!tempFile.renameTo(destFile)) {
                                tempFile.copyTo(destFile, overwrite = true)
                                tempFile.delete()
                            }
                            BepInExLog.i("$TAG: Extracted $name (${destFile.length()} bytes)")
                            extracted = true
                            break
                        }
                        entry = zis.nextEntry
                    }
                }
            }
            if (!extracted) BepInExLog.e("$TAG: $fileName not found in $ASSET_ZIP")
            extracted
        } catch (e: Exception) {
            BepInExLog.e("$TAG: Failed to extract $fileName from $ASSET_ZIP", e)
            false
        }
    }

    private fun isReady(file: File, minSize: Long): Boolean =
        file.exists() && file.length() > minSize

    private fun downloadAndVerify(
        urlStr: String,
        destFile: File,
        onProgress: (String) -> Unit,
        minSize: Long
    ): File? {
        return try {
            val tempFile = File(destFile.absolutePath + ".tmp")
            downloadFile(urlStr, tempFile, onProgress)

            if (tempFile.length() < minSize) {
                BepInExLog.e("$TAG: Downloaded file too small (${tempFile.length()} bytes)")
                tempFile.delete()
                return null
            }

            if (destFile.exists()) destFile.delete()
            tempFile.renameTo(destFile)
            BepInExLog.i("$TAG: Download complete (${destFile.length()} bytes)")
            destFile
        } catch (e: Exception) {
            BepInExLog.e("$TAG: Download failed", e)
            null
        }
    }

    private fun downloadFile(
        urlStr: String,
        destFile: File,
        onProgress: (String) -> Unit
    ) {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        conn.connect()

        val totalSize = conn.contentLength.toLong()
        BepInExLog.i("$TAG: Content-Length: $totalSize")

        conn.inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalSize > 0) {
                        val progress = (totalRead * 100 / totalSize).toInt()
                        onProgress("$totalRead / $totalSize bytes ($progress%)")
                    }
                }
            }
        }
    }
}
