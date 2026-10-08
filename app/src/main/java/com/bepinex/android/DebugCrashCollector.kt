package com.bepinex.android

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Saves the previous process crash state on the next launcher start.
 */
object DebugCrashCollector {
    private val TARGET_PACKAGES = listOf(
        "com.LanPiaoPiao.PlantsVsZombiesRH",
        "com.LanPiaoPiao.PlantsVsZombiesRHMod"
    )
    private const val MAX_FILE_BYTES = 32L * 1024L * 1024L
    private const val MAX_LOG_FILES = 64

    fun collect(context: Context) {
        try {
            val debugRoot = File(context.filesDir, "debug").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val snapshot = File(debugRoot, stamp).apply { mkdirs() }

            writeCommand(snapshot, "logcat.txt", arrayOf("logcat", "-d", "-v", "threadtime"))
            writeCommand(snapshot, "crash_logcat.txt", arrayOf("logcat", "-b", "crash", "-d", "-v", "threadtime"))
            writeCommand(snapshot, "exit_info.txt", arrayOf("dumpsys", "activity", "exit-info", context.packageName))
            writeCommand(snapshot, "activity.txt", arrayOf("dumpsys", "activity", "activities"))
            writeCommand(snapshot, "package.txt", arrayOf("dumpsys", "package", context.packageName))
            writeCommand(snapshot, "meminfo.txt", arrayOf("dumpsys", "meminfo", context.packageName))
            writeCommand(snapshot, "properties.txt", arrayOf("getprop"))
            val exitRecords = ProcessExitDiagnostics.read(context)
            writeText(
                snapshot,
                "application_exit_info.txt",
                ProcessExitDiagnostics.format(exitRecords).ifBlank {
                    "No process exit information available (requires Android 11 or newer)."
                }
            )

            writeText(snapshot, "device.txt", buildString {
                appendLine("package=${context.packageName}")
                appendLine("sdk=${Build.VERSION.SDK_INT}")
                appendLine("model=${Build.MODEL}")
                appendLine("manufacturer=${Build.MANUFACTURER}")
                appendLine("time=${Date()}")
            })

            copyIfPresent(File(context.getExternalFilesDir(null), "bepinex_launcher.log"), File(snapshot, "bepinex_launcher.log"))
            copyIfPresent(File(context.getExternalFilesDir(null), "logcat.txt"), File(snapshot, "launcher_logcat_previous.txt"))
            copyIfPresent(File(context.getExternalFilesDir(null), "logcat.1.txt"), File(snapshot, "launcher_logcat_previous.1.txt"))
            copyIfPresent(File(context.getExternalFilesDir(null), "launcher_java_crash.txt"), File(snapshot, "launcher_java_crash.txt"))
            copyIfPresent(File(context.getExternalFilesDir(null), "launcher_java_crash_previous.txt"), File(snapshot, "launcher_java_crash_previous.txt"))

            val names = setOf(
                "main.log", "il2cpp.log", "LogOutput.log", "bepinexlogoutput.log",
                "BepInExLogOutput.log", "output_log.txt", "player.log"
            )
            TARGET_PACKAGES.forEach { packageName ->
                val gameRoot = BepInExPaths.getGameRootDir(packageName)
                gameRoot.walkTopDown().maxDepth(8)
                    .filter { it.isFile && it.length() <= MAX_FILE_BYTES && names.contains(it.name) }
                    .take(MAX_LOG_FILES)
                    .forEach { source ->
                        val relative = source.relativeTo(gameRoot).path.replace(File.separatorChar, '_')
                        copyIfPresent(source, File(snapshot, "${packageName}_$relative"))
                    }

                val sidecars = listOf(
                    BepInExPaths.getLogcatCaptureFile(packageName),
                    BepInExPaths.getLogcatCaptureArchiveFile(packageName),
                    BepInExPaths.getPreviousLogcatCaptureFile(packageName),
                    BepInExPaths.getJavaCrashFile(packageName),
                    BepInExPaths.getPreviousJavaCrashFile(packageName)
                )
                sidecars.forEach { source ->
                    copyIfPresent(source, File(snapshot, "${packageName}_${source.name}"))
                }
            }

            // Keep the directory bounded while retaining recent snapshots.
            debugRoot.listFiles { file -> file.isDirectory }
                ?.sortedByDescending { it.name }
                ?.drop(8)
                ?.forEach { it.deleteRecursively() }
        } catch (_: Throwable) {
            // Diagnostics must never prevent the launcher from starting.
        }
    }

    private fun writeCommand(directory: File, name: String, command: Array<String>) {
        try {
            val process = Runtime.getRuntime().exec(command)
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.errorStream.bufferedReader().use { errors ->
                val errorText = errors.readText()
                writeText(directory, name, output + if (errorText.isEmpty()) "" else "\n[stderr]\n$errorText")
            }
            process.waitFor(10, TimeUnit.SECONDS)
        } catch (error: Throwable) {
            writeText(directory, name, "collector failed: ${error.stackTraceToString()}")
        }
    }

    private fun copyIfPresent(source: File, target: File) {
        if (!source.isFile || source.length() > MAX_FILE_BYTES) return
        target.parentFile?.mkdirs()
        source.copyTo(target, overwrite = true)
    }

    private fun writeText(directory: File, name: String, text: String) {
        File(directory, name).writeText(text)
    }
}
