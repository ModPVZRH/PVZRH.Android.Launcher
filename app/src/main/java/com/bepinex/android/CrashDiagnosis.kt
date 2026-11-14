package com.bepinex.android

/** Classifies fatal evidence; ordinary plugin errors never establish a crash cause. */
object CrashDiagnosis {
    enum class Kind { UNKNOWN, JAVA, MANAGED, NATIVE, SIGNAL, LOW_MEMORY, ANR, RESOURCE, INITIALIZATION }

    data class Result(
        val kind: Kind,
        val exception: String? = null,
        val signal: String? = null,
        val abortMessage: String? = null,
        val frame: String? = null
    ) {
        fun evidence(): String = buildString {
            exception?.let { appendLine("Exception: $it") }
            signal?.let { appendLine("Signal: $it") }
            abortMessage?.let { appendLine("Abort message: $it") }
            frame?.let { appendLine("Crash frame (not proof of the responsible mod): $it") }
        }.trimEnd()
    }

    private val epochLine = Regex("^\\s*(\\d{9,}\\.\\d+)\\s+(\\d+)\\s+\\d+\\s+[VDIWEF]\\s+([^:]+): ?(.*)$")
    private val exceptionLine = Regex("^(?:Caused by: |---> |Unhandled exception[.:]?\\s*)?([\\w.$`]+(?:Exception|Error)(?::.*)?)$")
    private val nativeSignal = Regex("(?:Fatal )?signal\\s+\\d+\\s+\\(SIG\\w+\\)[^\\n]*", RegexOption.IGNORE_CASE)
    private val nativeFrame = Regex("#\\d+\\s+pc\\s+[0-9a-fA-F]+\\s+.*")

    fun analyze(javaCrash: String, logcat: String, exitReason: String? = null, status: Int = 0): Result {
        val lines = logcat.lineSequence().map { epochLine.matchEntire(it)?.groupValues?.get(4) ?: it }.toList()
        val fatalJava = lines.indexOfLast { it.contains("FATAL EXCEPTION:") }
        val managed = lines.indexOfLast { it.trimStart().startsWith("Unhandled exception", ignoreCase = true) }
        val exceptionLines = when {
            javaCrash.isNotBlank() -> javaCrash.lines()
            fatalJava >= 0 -> lines.drop(fatalJava + 1)
            managed >= 0 -> lines.drop(managed)
            else -> emptyList()
        }
        val exceptions = exceptionLines.map { it.trim() }
            .mapNotNull { exceptionLine.matchEntire(it)?.groupValues?.get(1) }
        val cause = exceptionLines.lastOrNull { it.trimStart().startsWith("Caused by: ") }
            ?.trim()?.removePrefix("Caused by: ") ?: exceptions.firstOrNull()
        val signal = lines.asReversed().firstNotNullOfOrNull { nativeSignal.find(it)?.value }
        val kind = when (exitReason) {
            "LOW_MEMORY" -> Kind.LOW_MEMORY
            "ANR" -> Kind.ANR
            "EXCESSIVE_RESOURCE_USAGE" -> Kind.RESOURCE
            "INITIALIZATION_FAILURE" -> Kind.INITIALIZATION
            "CRASH_NATIVE" -> Kind.NATIVE
            "CRASH" -> Kind.JAVA
            "SIGNALED" -> if (signal != null) Kind.NATIVE else Kind.SIGNAL
            else -> when {
                javaCrash.isNotBlank() || fatalJava >= 0 -> Kind.JAVA
                managed >= 0 -> Kind.MANAGED
                signal != null -> Kind.NATIVE
                else -> Kind.UNKNOWN
            }
        }
        return Result(
            kind = kind,
            exception = if (kind in setOf(Kind.JAVA, Kind.MANAGED, Kind.NATIVE)) cause else null,
            signal = if (kind in setOf(Kind.NATIVE, Kind.SIGNAL)) signal ?: signalName(status) else null,
            abortMessage = if (kind == Kind.NATIVE) lines.lastOrNull { it.contains("Abort message:") }
                ?.substringAfter("Abort message:")?.trim() else null,
            frame = when (kind) {
                Kind.NATIVE -> lines.firstNotNullOfOrNull { nativeFrame.find(it)?.value }
                Kind.JAVA, Kind.MANAGED -> exceptionLines.drop(
                    exceptionLines.indexOfLast { it.trimStart().startsWith("Caused by: ") }.coerceAtLeast(0)
                ).firstOrNull { it.trimStart().startsWith("at ") }?.trim()
                else -> null
            }
        )
    }

    fun signalName(status: Int): String? = when (status) {
        4 -> "SIGILL (4)"
        6 -> "SIGABRT (6)"
        7 -> "SIGBUS (7)"
        8 -> "SIGFPE (8)"
        9 -> "SIGKILL (9)"
        11 -> "SIGSEGV (11)"
        15 -> "SIGTERM (15)"
        31 -> "SIGSYS (31)"
        0 -> null
        else -> "signal $status"
    }

    /** Include debuggerd output only when its block identifies this exact process and PID. */
    fun selectSessionLogcat(text: String, pid: Int, processName: String, sinceMillis: Long): String {
        if (pid <= 0) return ""
        val lines = text.lines().mapNotNull { raw ->
            val match = epochLine.matchEntire(raw) ?: return@mapNotNull null
            val timestamp = (match.groupValues[1].toDoubleOrNull()?.times(1000))?.toLong() ?: return@mapNotNull null
            if (timestamp < sinceMillis) return@mapNotNull null
            LogLine(raw, match.groupValues[2].toInt(), match.groupValues[3].trim(), match.groupValues[4])
        }
        val selected = BooleanArray(lines.size)
        lines.forEachIndexed { index, line -> if (line.pid == pid) selected[index] = true }
        lines.withIndex().filter { it.value.tag == "DEBUG" }.groupBy { it.value.pid }.values.forEach { reporter ->
            var block = mutableListOf<IndexedValue<LogLine>>()
            fun commit() {
                if (block.any { Regex("\\bpid: $pid,").containsMatchIn(it.value.message) &&
                        it.value.message.contains(">>> $processName <<<") }) {
                    block.forEach { selected[it.index] = true }
                }
                block = mutableListOf()
            }
            reporter.forEach { line ->
                if (line.value.message.startsWith("*** ***")) commit()
                block.add(line)
            }
            commit()
        }
        return lines.indices.filter { selected[it] }.joinToString("\n") { lines[it].raw }
    }

    private data class LogLine(val raw: String, val pid: Int, val tag: String, val message: String)
}
