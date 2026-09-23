package com.bepinex.android

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicReference

object BepInExLog {

    private const val MAX_LOGCAT_CHUNK_BYTES = 4L * 1024L * 1024L

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries

    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var crashHandlerInstalled = false

    data class LogEntry(
        val timestamp: String,
        val level: Level,
        val message: String
    )

    enum class Level { DEBUG, INFO, WARN, ERROR }

    fun init(context: Context) {
        // Write log to external storage so we can adb pull it
        logFile = File(context.getExternalFilesDir(null), "bepinex_launcher.log")
        installCrashHandler(context)
        // Also capture native logcat for crash diagnosis
        startLogcatCapture(context)
    }

    // Logcat capture

    private var logcatThread: Thread? = null
    private val logcatProcess = AtomicReference<Process?>(null)

    @Synchronized
    private fun startLogcatCapture(context: Context) {
        if (logcatThread?.isAlive == true) return
        val logcatFile = File(context.getExternalFilesDir(null), "logcat.txt")
        val archiveFile = File(context.getExternalFilesDir(null), "logcat.1.txt")
        logcatFile.writeText("=== launcher logcat session ${Date()} pid=${android.os.Process.myPid()} ===\n")
        archiveFile.delete()
        logcatThread = Thread({
            try {
                val pid = android.os.Process.myPid()
                val process = Runtime.getRuntime().exec(
                    arrayOf("logcat", "-v", "threadtime", "-T", "1", "--pid=$pid")
                )
                logcatProcess.set(process)
                var output = FileOutputStream(logcatFile, true)
                try {
                    process.inputStream.bufferedReader().forEachLine { line ->
                        val bytes = (line + "\n").toByteArray(Charsets.UTF_8)
                        if (logcatFile.length() + bytes.size > MAX_LOGCAT_CHUNK_BYTES) {
                            output.close()
                            archiveFile.delete()
                            if (!logcatFile.renameTo(archiveFile)) {
                                logcatFile.copyTo(archiveFile, overwrite = true)
                                logcatFile.delete()
                            }
                            output = FileOutputStream(logcatFile, true)
                            output.write("=== continued launcher logcat ${Date()} ===\n".toByteArray())
                        }
                        output.write(bytes)
                    }
                } finally {
                    output.close()
                    process.destroy()
                    logcatProcess.compareAndSet(process, null)
                }
            } catch (error: Exception) {
                try { logcatFile.appendText("\nlogcat capture failed: ${error.stackTraceToString()}\n") } catch (_: Exception) { }
            }
        }, "logcat-capture").apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    private fun installCrashHandler(context: Context) {
        if (crashHandlerInstalled) return
        crashHandlerInstalled = true
        val directory = context.getExternalFilesDir(null) ?: context.filesDir
        val crashFile = File(directory, "launcher_java_crash.txt")
        val previousCrashFile = File(directory, "launcher_java_crash_previous.txt")
        previousCrashFile.delete()
        if (crashFile.isFile && crashFile.length() > 0L) {
            if (!crashFile.renameTo(previousCrashFile)) {
                crashFile.copyTo(previousCrashFile, overwrite = true)
                crashFile.delete()
            }
        }
        val delegate = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                FileOutputStream(crashFile, true).use { output ->
                    output.write(buildString {
                        appendLine("time=${Date()}")
                        appendLine("thread=${thread.name} (${thread.id})")
                        appendLine()
                        append(error.stackTraceToString())
                        appendLine()
                    }.toByteArray(Charsets.UTF_8))
                    output.flush()
                    try { output.fd.sync() } catch (_: Exception) { }
                }
            } catch (_: Throwable) { }
            delegate?.uncaughtException(thread, error)
        }
    }

    /** Set per-game log file path (for BepInEx LogOutput.log viewer) */
    fun getBepInExLogFile(packageName: String): File =
        BepInExPaths.getLogFile(packageName)

    fun getEntries(): List<LogEntry> = _entries.value

    fun i(message: String) = log(Level.INFO, message)
    fun w(message: String) = log(Level.WARN, message)
    fun e(message: String, throwable: Throwable? = null) {
        log(Level.ERROR, message)
        throwable?.let { log(Level.ERROR, it.stackTraceToString()) }
    }
    fun d(message: String) = log(Level.DEBUG, message)

    /** Clear in-memory log entries */
    fun clear() { _entries.value = emptyList() }

    private fun log(level: Level, message: String) {
        val timestamp = dateFormat.format(Date())
        val entry = LogEntry(timestamp, level, message)
        _entries.value = _entries.value + entry

        // Write to file
        logFile?.appendText("[$timestamp] [${level.name}] $message\n")

        // Output to logcat
        val prio = when (level) {
            Level.ERROR -> android.util.Log.ERROR
            Level.WARN -> android.util.Log.WARN
            Level.INFO -> android.util.Log.INFO
            Level.DEBUG -> android.util.Log.DEBUG
        }
        android.util.Log.println(prio, "BepInEx", message)
    }
}
