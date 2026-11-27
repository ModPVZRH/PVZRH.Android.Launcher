package com.bepinex.android.log

import com.bepinex.android.BepInExPaths
import com.bepinex.android.BoundedLogBuffer
import com.bepinex.android.BoundedTextFile
import com.bepinex.android.LogMemoryLimits
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Reads and tails BepInEx runtime log files, exposing new lines as a StateFlow.
 */
object BepInExLogReader {

    data class LogLine(
        val timestamp: String,
        val level: LogLevel,
        val source: String,
        val message: String
    )

    enum class LogLevel(val label: String) {
        FATAL("Fatal"),
        ERROR("Error"),
        WARNING("Warning"),
        MESSAGE("Message"),
        INFO("Info"),
        DEBUG("Debug"),
        UNKNOWN("");

        companion object {
            fun parse(levelStr: String): LogLevel = when (levelStr.lowercase()) {
                "fatal" -> FATAL
                "error" -> ERROR
                "warning", "warn" -> WARNING
                "message", "msg" -> MESSAGE
                "info" -> INFO
                "debug" -> DEBUG
                else -> UNKNOWN
            }
        }
    }

    /** BepInEx log format: [LogLevel :Source] message */
    private val LOG_PATTERN = Regex("^\\[([A-Za-z]+)\\s*:?(\\S+)?\\]\\s?(.*)")

    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    val lines: StateFlow<List<LogLine>> = _lines

    private val lock = Any()
    private var pollingJob: Job? = null
    private var currentSession: WatchSession? = null

    private class WatchSession(val file: File) {
        val follower = BoundedTextFile.Follower()
        var revision = 0L
    }

    fun startWatching(packageName: String, scope: CoroutineScope, modpackName: String? = null) {
        startWatchingFile(BepInExPaths.getModpackLogFile(packageName, modpackName), scope)
    }

    fun startWatchingFile(logFile: File, scope: CoroutineScope) = synchronized(lock) {
        if (currentSession?.file?.absolutePath == logFile.absolutePath && pollingJob?.isActive == true) return@synchronized
        pollingJob?.cancel()
        val session = WatchSession(logFile)
        currentSession = session
        _lines.value = emptyList()

        pollingJob = scope.launch(Dispatchers.IO) {
            var previous: BoundedTextFile.Snapshot? = null
            var previousRevision = -1L
            while (isActive) {
                val revision = synchronized(lock) { session.revision }
                try {
                    val snapshot = session.follower.read(session.file)
                    if (snapshot != previous || revision != previousRevision) {
                        val buffer = BoundedLogBuffer<LogLine> {
                            it.timestamp.length + it.source.length + it.message.length
                        }
                        for (raw in snapshot.text.lineSequence()) {
                            ensureActive()
                            if (raw.isNotBlank()) buffer.add(parseLine(LogMemoryLimits.preview(raw)))
                        }
                        val parsed = buffer.snapshot()
                        synchronized(lock) {
                            if (isActive && currentSession === session && session.revision == revision) {
                                _lines.value = parsed
                            }
                        }
                        previous = snapshot
                        previousRevision = revision
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A writer can replace or truncate the file between the size check and read.
                }
                delay(500)
            }
        }
    }

    fun stopWatching() = synchronized(lock) {
        currentSession = null
        pollingJob?.cancel()
        pollingJob = null
    }

    fun clear() = synchronized(lock) {
        _lines.value = emptyList()
        currentSession?.let { it.revision++ }
    }

    private fun parseLine(raw: String): LogLine {
        val match = LOG_PATTERN.find(raw) ?: return LogLine(
            timestamp = "",
            level = LogLevel.UNKNOWN,
            source = "",
            message = raw
        )
        val level = LogLevel.parse(match.groupValues[1])
        val source = match.groupValues[2].ifEmpty { "" }
        val message = match.groupValues[3]
        return LogLine(timestamp = "", level = level, source = source, message = message)
    }
}
