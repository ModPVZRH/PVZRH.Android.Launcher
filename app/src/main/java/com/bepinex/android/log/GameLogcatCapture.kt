package com.bepinex.android.log

import com.bepinex.android.BepInExPaths
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference

/**
 * Streams this process's logcat into a file under BepInEx/.
 *
 * MainActivity cannot reliably read the :game process logcat on modern Android,
 * so capture has to run inside :game and flush on every error line.
 */
object GameLogcatCapture {

    private val processRef = AtomicReference<Process?>(null)
    private val threadRef = AtomicReference<Thread?>(null)

    fun start(packageName: String) {
        stop()
        val outFile = BepInExPaths.getLogcatCaptureFile(packageName)
        outFile.parentFile?.mkdirs()
        outFile.writeText("")

        val thread = Thread({
            val proc = try {
                Runtime.getRuntime().exec(
                    arrayOf(
                        "logcat",
                        "-v", "threadtime",
                        "-T", "1",
                        "Unity:V",
                        "BepInEx:V",
                        "AndroidRuntime:E",
                        "DEBUG:I",
                        "*:S"
                    )
                )
            } catch (_: Exception) {
                return@Thread
            }
            processRef.set(proc)
            try {
                FileOutputStream(outFile, true).use { fos ->
                    proc.inputStream.bufferedReader().forEachLine { line ->
                        if (!isRelevant(line)) return@forEachLine
                        fos.write((line + "\n").toByteArray(Charsets.UTF_8))
                        fos.flush()
                        try {
                            fos.fd.sync()
                        } catch (_: Exception) { }
                    }
                }
            } catch (_: Exception) {
            } finally {
                proc.destroy()
                processRef.compareAndSet(proc, null)
            }
        }, "GameLogcatCapture")
        thread.isDaemon = true
        threadRef.set(thread)
        thread.start()
    }

    fun stop() {
        processRef.getAndSet(null)?.destroy()
        threadRef.getAndSet(null)?.interrupt()
    }

    fun isRelevant(line: String): Boolean {
        val lower = line.lowercase()
        if (lower.contains("fatal exception")) return true
        if (lower.contains("[error") || lower.contains("[fatal")) return true
        if (lower.contains("notsupportedexception")) return true
        if (lower.contains("il2cppinterop") &&
            (lower.contains("error") || lower.contains("exception"))
        ) {
            return true
        }
        if (lower.contains("signal") &&
            (lower.contains("sigsegv") || lower.contains("sigabrt") ||
                lower.contains("sigbus") || lower.contains("sigfpe"))
        ) {
            return true
        }
        return Regex("\\sE\\s+Unity\\b").containsMatchIn(line)
    }
}
