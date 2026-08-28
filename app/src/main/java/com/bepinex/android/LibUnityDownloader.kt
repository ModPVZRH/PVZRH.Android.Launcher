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
    private const val BASE_URL_RAW =
        "https://raw.githubusercontent.com/Modded-PVZRH/PVZRH.Launcher-release/refs/heads/main/$VERSION"
    private const val BASE_URL_GH_PROXY =
        "https://gh-proxy.org/https://raw.githubusercontent.com/Modded-PVZRH/PVZRH.Launcher-release/refs/heads/main/$VERSION"

    // Try gh-proxy mirror first (China-reachable), then fall back to raw.githubusercontent.com
    private fun getFileUrls(fileName: String): List<String> =
        listOf("$BASE_URL_GH_PROXY/$fileName", "$BASE_URL_RAW/$fileName")

    fun ensureLibUnity(
        targetDir: File,
        onProgress: (String) -> Unit = {}
    ): File? {
        targetDir.mkdirs()

        // Download libunity.so (try gh-proxy first, fallback to github)
        val destLib = File(targetDir, "libunity.so")
        if (!(destLib.exists() && destLib.length() > 1024 * 1024)) {
            var result: File? = null
            for (url in getFileUrls("libunity.so")) {
                onProgress("Downloading libunity.so...")
                BepInExLog.i("$TAG: Downloading libunity.so from $url")
                result = downloadAndVerify(url, destLib, onProgress)
                if (result != null) break
                BepInExLog.w("$TAG: Failed from $url, trying next mirror...")
            }
            if (result == null) {
                BepInExLog.e("$TAG: Failed to download libunity.so from all mirrors")
                return null
            }
        } else {
            BepInExLog.i("$TAG: Using cached libunity.so (${destLib.length()} bytes)")
        }

        // Download libunity.sym.so (optional)
        val destSym = File(targetDir, "libunity.sym.so")
        if (!(destSym.exists() && destSym.length() > 1024)) {
            for (url in getFileUrls("libunity.sym.so")) {
                onProgress("Downloading libunity.sym.so...")
                BepInExLog.i("$TAG: Downloading libunity.sym.so from $url")
                val result = downloadAndVerify(url, destSym, onProgress, minSize = 1024)
                if (result != null) break
                BepInExLog.w("$TAG: Failed sym from $url, trying next...")
            }
            // sym.so is optional — continue without it, hook will fail gracefully
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
