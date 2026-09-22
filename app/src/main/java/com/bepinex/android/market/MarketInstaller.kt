package com.bepinex.android.market

import android.content.Context
import com.bepinex.android.BepInExLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.zip.ZipFile

/**
 * Downloads a marketplace direct link, unpacks archives, and recursively
 * looks for `modpack.json` or a DLL whose name matches the mod English name.
 */
object MarketInstaller {

    private const val TAG = "MarketInstaller"
    private const val WORK_DIR = "market-download"
    private const val MAX_REDIRECTS = 8
    private const val MAX_NESTED_ARCHIVES = 3

    data class Progress(
        val phase: Phase,
        val bytesRead: Long = 0L,
        val totalBytes: Long = 0L
    ) {
        enum class Phase { Downloading, Extracting, Scanning }
    }

    class UnsupportedArchiveException(message: String) : IOException(message)

    sealed class PreparedKind {
        data class Modpack(val scanRoot: File) : PreparedKind()
        data class Plugins(val dlls: List<File>, val scanRoot: File) : PreparedKind()
        data object NoMatch : PreparedKind()
    }

    data class PreparedInstall(
        val workDir: File,
        val kind: PreparedKind
    )

    suspend fun downloadAndPrepare(
        context: Context,
        mod: MarketMod,
        url: String,
        expectedSize: Long = 0L,
        onProgress: suspend (Progress) -> Unit
    ): PreparedInstall {
        val workDir = File(File(context.cacheDir, WORK_DIR), sanitizeSegment(mod.id.ifBlank { mod.englishName }))
        if (workDir.exists()) workDir.deleteRecursively()
        workDir.mkdirs()
        val downloadDir = File(workDir, "download").apply { mkdirs() }

        try {
            onProgress(Progress(Progress.Phase.Downloading, 0L, expectedSize.coerceAtLeast(0L)))
            val downloaded = downloadToDir(url, downloadDir, mod, expectedSize, onProgress)
            currentCoroutineContext().ensureActive()

            onProgress(Progress(Progress.Phase.Extracting, downloaded.length(), downloaded.length()))
            val scanRoot = prepareScanRoot(downloaded, workDir, onProgress)
            currentCoroutineContext().ensureActive()

            onProgress(Progress(Progress.Phase.Scanning, downloaded.length(), downloaded.length()))
            val kind = scanPreparedFiles(scanRoot, mod)
            BepInExLog.i("$TAG: Prepared ${mod.englishName.ifBlank { mod.modName }} as $kind")
            return PreparedInstall(workDir, kind)
        } catch (error: CancellationException) {
            workDir.deleteRecursively()
            throw error
        } catch (error: Throwable) {
            workDir.deleteRecursively()
            currentCoroutineContext().ensureActive()
            throw error
        }
    }

    fun cleanup(workDir: File?) {
        if (workDir?.exists() == true) {
            runCatching { workDir.deleteRecursively() }
        }
    }

    private suspend fun prepareScanRoot(
        downloaded: File,
        workDir: File,
        onProgress: suspend (Progress) -> Unit
    ): File {
        val kind = detectFileKind(downloaded)
        if (kind == FileKind.SevenZip || kind == FileKind.Rar) {
            throw UnsupportedArchiveException(kind.name)
        }
        if (kind != FileKind.Zip && !isArchiveFileName(downloaded.name)) {
            return downloaded.parentFile ?: workDir
        }

        val extractedDir = File(workDir, "extracted")
        extractedDir.deleteRecursively()
        extractedDir.mkdirs()
        extractZip(downloaded, extractedDir)
        extractNestedArchives(extractedDir, onProgress)
        return extractedDir
    }

    private fun scanPreparedFiles(scanRoot: File, mod: MarketMod): PreparedKind {
        if (!scanRoot.exists()) return PreparedKind.NoMatch
        val files = scanRoot.walkTopDown().filter { it.isFile }.toList()
        if (files.any { it.name.equals("modpack.json", ignoreCase = true) }) {
            return PreparedKind.Modpack(scanRoot)
        }
        val expected = normalizeEnglishName(mod.englishName)
        val matching = files.filter { file ->
            file.extension.equals("dll", ignoreCase = true) &&
                dllMatchesEnglishName(file, expected)
        }
        if (matching.isNotEmpty()) {
            return PreparedKind.Plugins(matching, scanRoot)
        }
        return PreparedKind.NoMatch
    }

    private fun dllMatchesEnglishName(file: File, expected: String): Boolean {
        if (expected.isEmpty()) return false
        return file.nameWithoutExtension.equals(expected, ignoreCase = true)
    }

    private fun normalizeEnglishName(raw: String): String {
        val trimmed = raw.trim().substringAfterLast('/').substringAfterLast('\\')
        return if (trimmed.endsWith(".dll", ignoreCase = true)) trimmed.dropLast(4) else trimmed
    }

    private suspend fun downloadToDir(
        url: String,
        downloadDir: File,
        mod: MarketMod,
        expectedSize: Long,
        onProgress: suspend (Progress) -> Unit
    ): File {
        val conn = openDownloadConnection(url)
        return runCancellable({ conn.disconnect() }) {
            val headerLength = conn.contentLengthLong.takeIf { it > 0L } ?: expectedSize
            val fallbackName = fallbackFileName(mod)
            val fileName = sanitizeFileName(
                parseContentDisposition(conn.getHeaderField("Content-Disposition"))
                    ?: fileNameFromUrl(conn.url?.toString() ?: url)
                    ?: fallbackName,
                fallbackName
            )
            val dest = File(downloadDir, fileName)
            dest.parentFile?.mkdirs()
            val temp = File(dest.absolutePath + ".tmp")
            if (temp.exists()) temp.delete()

            try {
                conn.inputStream.use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                        var totalRead = 0L
                        var lastPercent = -1
                        var lastBytes = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            totalRead += read
                            val percent = if (headerLength > 0L) {
                                ((totalRead * 100L) / headerLength).toInt()
                            } else {
                                -1
                            }
                            val shouldPublish = if (percent >= 0) {
                                percent != lastPercent
                            } else {
                                totalRead - lastBytes >= 256 * 1024
                            }
                            if (shouldPublish) {
                                lastPercent = percent
                                lastBytes = totalRead
                                onProgress(Progress(Progress.Phase.Downloading, totalRead, headerLength))
                            }
                        }
                        onProgress(
                            Progress(
                                Progress.Phase.Downloading,
                                totalRead,
                                headerLength.coerceAtLeast(totalRead)
                            )
                        )
                    }
                }
            } catch (error: CancellationException) {
                temp.delete()
                throw error
            } catch (error: IOException) {
                temp.delete()
                currentCoroutineContext().ensureActive()
                throw error
            }

            currentCoroutineContext().ensureActive()
            if (temp.length() <= 0L) {
                temp.delete()
                throw IOException("Downloaded file is empty")
            }
            if (dest.exists()) dest.delete()
            if (!temp.renameTo(dest)) {
                temp.copyTo(dest, overwrite = true)
                temp.delete()
            }
            maybeRenameByMagic(dest)
        }.also {
            runCatching { conn.disconnect() }
        }
    }

    private suspend fun openDownloadConnection(urlStr: String): HttpURLConnection {
        var current = urlStr
        repeat(MAX_REDIRECTS) {
            currentCoroutineContext().ensureActive()
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 30_000
                readTimeout = 120_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "PVZRH-Launcher/1.0")
                setRequestProperty("Accept", "*/*")
            }
            val code = try {
                runCancellable({ conn.disconnect() }) { conn.responseCode }
            } catch (error: CancellationException) {
                runCatching { conn.disconnect() }
                throw error
            } catch (error: Exception) {
                runCatching { conn.disconnect() }
                currentCoroutineContext().ensureActive()
                throw error
            }
            if (code in 301..308) {
                val location = conn.getHeaderField("Location")
                runCatching { conn.disconnect() }
                if (location.isNullOrBlank()) {
                    throw IOException("Redirect without Location")
                }
                current = URL(URL(current), location).toString()
                BepInExLog.i("$TAG: Redirect $code -> $current")
                return@repeat
            }
            if (code !in 200..299) {
                runCatching { conn.disconnect() }
                throw IOException("HTTP $code")
            }
            return conn
        }
        throw IOException("Too many redirects")
    }

    private suspend fun <T> runCancellable(onCancel: () -> Unit, block: suspend () -> T): T {
        val parentJob = currentCoroutineContext().job
        return coroutineScope {
            val watchdog = launch {
                try {
                    awaitCancellation()
                } finally {
                    if (parentJob.isCancelled) {
                        runCatching { onCancel() }
                    }
                }
            }
            try {
                block()
            } finally {
                watchdog.cancel()
            }
        }
    }

    private fun maybeRenameByMagic(file: File): File {
        val kind = detectFileKind(file)
        val extension = when (kind) {
            FileKind.Zip -> "zip"
            FileKind.Dll -> "dll"
            FileKind.SevenZip -> "7z"
            FileKind.Rar -> "rar"
            FileKind.Unknown -> return file
        }
        if (file.extension.equals(extension, ignoreCase = true)) return file
        val renamed = File(file.parentFile, "${file.nameWithoutExtension}.$extension")
        if (renamed.exists() && renamed != file) renamed.delete()
        return if (file.renameTo(renamed)) renamed else file
    }

    private suspend fun extractZip(archive: File, destDir: File) {
        destDir.mkdirs()
        val destRoot = destDir.canonicalFile.toPath()
        ZipFile(archive).use { zip ->
            runCancellable({ zip.close() }) {
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    currentCoroutineContext().ensureActive()
                    val entry = entries.nextElement()
                    val normalized = entry.name.replace('\\', '/').trimStart('/')
                    if (normalized.isEmpty()) continue
                    val outFile = File(destDir, normalized).canonicalFile
                    if (!outFile.toPath().startsWith(destRoot)) {
                        throw IOException("Unsafe archive entry: ${entry.name}")
                    }
                    if (entry.isDirectory || normalized.endsWith('/')) {
                        outFile.mkdirs()
                        continue
                    }
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun extractNestedArchives(
        root: File,
        onProgress: suspend (Progress) -> Unit
    ) {
        repeat(MAX_NESTED_ARCHIVES) { depth ->
            currentCoroutineContext().ensureActive()
            val archives = root.walkTopDown()
                .filter { it.isFile && (isArchiveFileName(it.name) || detectFileKind(it) == FileKind.Zip) }
                .toList()
            if (archives.isEmpty()) return
            var extractedAny = false
            archives.forEach { archive ->
                currentCoroutineContext().ensureActive()
                val dest = uniqueExtractDir(archive)
                if (dest.exists()) return@forEach
                try {
                    dest.mkdirs()
                    extractZip(archive, dest)
                    extractedAny = true
                    onProgress(Progress(Progress.Phase.Extracting))
                    BepInExLog.i("$TAG: Extracted nested archive ${archive.name} (depth $depth)")
                } catch (error: CancellationException) {
                    dest.deleteRecursively()
                    throw error
                } catch (error: Exception) {
                    dest.deleteRecursively()
                    BepInExLog.w("$TAG: Nested extract failed for ${archive.name}: ${error.message}")
                }
            }
            if (!extractedAny) return
        }
    }

    private fun uniqueExtractDir(archive: File): File {
        val parent = archive.parentFile ?: return File(archive.absolutePath + "_extracted")
        val base = archive.nameWithoutExtension.ifBlank { archive.name }
        var dest = File(parent, base)
        if (dest.exists() && dest.isFile) dest = File(parent, "${base}_extracted")
        var index = 2
        while (dest.exists() && dest.isFile) {
            dest = File(parent, "${base}_extracted_$index")
            index++
        }
        return dest
    }

    private enum class FileKind { Zip, Dll, SevenZip, Rar, Unknown }

    private fun detectFileKind(file: File): FileKind {
        if (!file.isFile) return FileKind.Unknown
        val header = ByteArray(8)
        val read = runCatching {
            file.inputStream().use { it.read(header) }
        }.getOrDefault(-1)
        if (read < 2) return FileKind.Unknown
        if (header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()) return FileKind.Zip
        if (header[0] == 0x4D.toByte() && header[1] == 0x5A.toByte()) return FileKind.Dll
        if (read >= 6 &&
            header[0] == 0x37.toByte() &&
            header[1] == 0x7A.toByte() &&
            header[2] == 0xBC.toByte() &&
            header[3] == 0xAF.toByte() &&
            header[4] == 0x27.toByte() &&
            header[5] == 0x1C.toByte()
        ) {
            return FileKind.SevenZip
        }
        if (header[0] == 0x52.toByte() && header[1] == 0x61.toByte()) return FileKind.Rar
        return FileKind.Unknown
    }

    private fun isArchiveFileName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext == "zip" || ext == "rhp"
    }

    private fun fallbackFileName(mod: MarketMod): String {
        val base = sanitizeFileName(
            mod.englishName.ifBlank { mod.modName }.ifBlank { "mod" },
            "mod"
        )
        return "$base.bin"
    }

    private fun fileNameFromUrl(url: String): String? {
        val last = url.substringBefore('#').substringBefore('?').substringAfterLast('/')
        if (last.isBlank() || !last.contains('.')) return null
        return last
    }

    private fun parseContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val utf8 = Regex("filename\\*=(?:UTF-8''|utf-8'')([^;]+)", RegexOption.IGNORE_CASE)
            .find(header)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { encoded ->
                runCatching { URLDecoder.decode(encoded.trim('"', '\''), Charsets.UTF_8.name()) }
                    .getOrNull()
            }
        if (!utf8.isNullOrBlank()) {
            return utf8.substringAfterLast('/').substringAfterLast('\\')
        }
        val simple = Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
            .find(header)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        return simple?.substringAfterLast('/')?.substringAfterLast('\\')
    }

    private fun sanitizeFileName(name: String, fallback: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().trim('.')
        return cleaned.ifBlank { fallback }
    }

    private fun sanitizeSegment(value: String): String {
        val cleaned = value.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_')
        return cleaned.ifBlank { "mod" }
    }
}
