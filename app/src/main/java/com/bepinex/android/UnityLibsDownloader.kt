package com.bepinex.android

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * Downloads Unity base libraries from unity.bepinex.dev using Android's HTTP stack.
 */
object UnityLibsDownloader {

    private const val TAG = "UnityLibsDownloader"
    private const val BASE_URL = "https://unity.bepinex.dev/libraries/"

    // Same pattern as FusionCore LibUnityDownloader.java line 25
    // Extracts "2022.3.62" from "2022.3.62f3"
    private val UNITY_BASE_VERSION_PATTERN = Pattern.compile("^(\\d+\\.\\d+\\.\\d+)")

    /**
     * Ensure the unity base libraries ZIP is cached in [outputDir].
     * BepInEx skips its broken .NET download path when the file already exists.
     *
     * @return true if the ZIP is ready (cached or freshly downloaded)
     */
    fun ensureLibraries(outputDir: File, version: String, onProgress: (String) -> Unit = {}): Boolean {
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            BepInExLog.e("$TAG: Failed to create unity-libs directory: ${outputDir.absolutePath}")
            return false
        }

        // Normalize version same way FusionCore does (2022.3.62f3  -> 2022.3.62)
        val downloadVersion = normalizeVersionForDownload(version)
        if (version != downloadVersion) {
            BepInExLog.i("$TAG: Normalized Unity version for download: $version  -> $downloadVersion")
        }

        val zipFile = File(outputDir, "$downloadVersion.zip")
        if (zipFile.exists() && zipFile.length() > 0) {
            BepInExLog.i("$TAG: Unity base libraries already cached (${formatSize(zipFile.length())})")
            onProgress("Unity libraries cached")
            return true
        }

        // Remove any partial download before starting
        zipFile.delete()

        return downloadZip(zipFile, downloadVersion, onProgress)
    }

    /**
     * Extracts the base version (Major.Minor.Build) from a full Unity version string.
     */
    private fun normalizeVersionForDownload(version: String): String {
        val matcher = UNITY_BASE_VERSION_PATTERN.matcher(version)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return version
    }

    // Download

    private fun downloadZip(destFile: File, version: String, onProgress: (String) -> Unit = {}): Boolean {
        val url = BASE_URL + "$version.zip"
        BepInExLog.i("$TAG: Downloading $url")
        onProgress("Connecting to server...")

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 300_000  // 5 min  -- ZIP can be 100+ MB
                instanceFollowRedirects = true
            }

            val status = connection.responseCode
            if (status !in 200..299) {
                BepInExLog.e("$TAG: HTTP $status  -- cannot download unity base libraries")
                onProgress("Download failed (HTTP $status)")
                return false
            }

            val totalBytes = connection.contentLengthLong
            val totalStr = formatSize(totalBytes)
            BepInExLog.i("$TAG: Downloading $totalStr...")
            onProgress("0 / $totalStr")

            // Download to temp file, then rename atomically
            val tempFile = File(destFile.parentFile, "${destFile.name}.download")
            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0L
                    var lastProgress = 0L
                    var bytes: Int
                    while (input.read(buffer).also { bytes = it } != -1) {
                        output.write(buffer, 0, bytes)
                        downloaded += bytes
                        val now = System.currentTimeMillis()
                        if (now - lastProgress > 500) {
                            val pct = if (totalBytes > 0) (downloaded * 100 / totalBytes).toInt() else 0
                            onProgress("$pct%  ${formatSize(downloaded)} / $totalStr")
                            lastProgress = now
                        }
                    }
                }
            }

            if (!tempFile.renameTo(destFile)) {
                BepInExLog.e("$TAG: Failed to rename temp file to ${destFile.name}")
                tempFile.delete()
                return false
            }

            BepInExLog.i("$TAG: Download complete (${formatSize(destFile.length())})")
            return true

        } catch (e: Exception) {
            BepInExLog.e("$TAG: Download failed", e)
            destFile.delete()
            return false
        } finally {
            connection?.disconnect()
            val tempFile = File(destFile.parentFile, "${destFile.name}.download")
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 0 -> "? B"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
    }
}
