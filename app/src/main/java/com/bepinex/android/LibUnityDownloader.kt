package com.bepinex.android

import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads unstripped libunity.so from GitHub and caches it in appDataDir.
 *
 * The native C++ layer (fusion.cpp) reads libunity.so from appDataDir/libunity.so
 * when useOriginalLibUnity=false.
 */
object LibUnityDownloader {

    private const val TAG = "LibUnityDownloader"
    private const val VERSION = "2022.3.62f1c1"

    fun ensureLibUnity(
        appDataDir: File,
        onProgress: (String) -> Unit = {}
    ): File? {
        appDataDir.mkdirs()
        val destFile = File(appDataDir, "libunity.so")

        if (destFile.exists() && destFile.length() > 1024 * 1024) {
            BepInExLog.i("$TAG: Using cached libunity.so (${destFile.length()} bytes)")
            return destFile
        }

        val url = "https://raw.githubusercontent.com/Modded-PVZRH/PVZRH.Launcher-release/refs/heads/main/$VERSION/libunity.so"
        BepInExLog.i("$TAG: Downloading libunity.so from $url")

        return try {
            val tempFile = File(appDataDir, "libunity.so.tmp")
            downloadFile(url, tempFile, onProgress)

            if (tempFile.length() < 1024 * 1024) {
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
