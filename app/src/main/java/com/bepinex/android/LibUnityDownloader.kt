package com.bepinex.android

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads unstripped libunity.so + libunity.sym.so from GitHub
 * and caches them in the target directory.
 *
 * The native C++ layer (fusion.cpp) reads libunity.so from:
 *   appDataDir/libunity/unstripped/libunity.so (when useOriginalLibUnity=false)
 */
object LibUnityDownloader {

    private const val TAG = "LibUnityDownloader"
    private const val VERSION = "2022.3.62f1c1"
    private const val BASE_URL =
        "https://raw.githubusercontent.com/Modded-PVZRH/PVZRH.Launcher-release/refs/heads/main/$VERSION"

    fun ensureLibUnity(
        targetDir: File,
        onProgress: (String) -> Unit = {}
    ): File? {
        targetDir.mkdirs()

        // Download libunity.so
        val destLib = File(targetDir, "libunity.so")
        if (!(destLib.exists() && destLib.length() > 1024 * 1024)) {
            onProgress("Downloading libunity.so...")
            val url = "$BASE_URL/libunity.so"
            BepInExLog.i("$TAG: Downloading libunity.so from $url")
            val result = downloadAndVerify(url, destLib, onProgress)
            if (result == null) {
                BepInExLog.e("$TAG: Failed to download libunity.so")
                return null
            }
        } else {
            BepInExLog.i("$TAG: Using cached libunity.so (${destLib.length()} bytes)")
        }

        // Download libunity.sym.so
        val destSym = File(targetDir, "libunity.sym.so")
        if (!(destSym.exists() && destSym.length() > 1024)) {
            onProgress("Downloading libunity.sym.so...")
            val url = "$BASE_URL/libunity.sym.so"
            BepInExLog.i("$TAG: Downloading libunity.sym.so from $url")
            val result = downloadAndVerify(url, destSym, onProgress, minSize = 1024)
            if (result == null) {
                BepInExLog.e("$TAG: Failed to download libunity.sym.so")
                // sym.so is optional — continue without it, hook will fail gracefully
            }
        } else {
            BepInExLog.i("$TAG: Using cached libunity.sym.so (${destSym.length()} bytes)")
        }

        return destLib
    }

    private fun downloadAndVerify(
        urlStr: String,
        destFile: File,
        onProgress: (String) -> Unit,
        minSize: Long = 1024 * 1024
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
