package com.bepinex.android

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Date

/** Reads the OS-owned exit record, which survives native crashes and process death. */
object ProcessExitDiagnostics {
    private const val MAX_TRACE_BYTES = 512 * 1024

    data class ExitRecord(
        val processName: String,
        val timestamp: Long,
        val reason: Int,
        val status: Int,
        val description: String,
        val importance: Int,
        val pss: Long,
        val rss: Long,
        val trace: String,
        val pid: Int = 0,
        val nativeTrace: ByteArray? = null
    ) {
        val reasonLabel: String get() = reasonName(reason)
        val isAbnormal: Boolean
            get() = reason == ApplicationExitInfo.REASON_CRASH ||
                reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                reason == ApplicationExitInfo.REASON_ANR ||
                reason == ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE ||
                reason == ApplicationExitInfo.REASON_LOW_MEMORY ||
                reason == ApplicationExitInfo.REASON_INITIALIZATION_FAILURE ||
                reason == ApplicationExitInfo.REASON_SIGNALED

        fun format(): String = buildString {
            appendLine("process=$processName")
            appendLine("pid=$pid")
            appendLine("time=${Date(timestamp)} ($timestamp)")
            appendLine("reason=${reasonName(reason)} ($reason)")
            appendLine("status=$status")
            appendLine("description=$description")
            appendLine("importance=$importance")
            appendLine("pss_kb=$pss")
            appendLine("rss_kb=$rss")
            if (trace.isNotBlank()) {
                appendLine()
                appendLine("--- system trace ---")
                append(trace.trimEnd())
            }
        }.trimEnd()
    }

    fun read(
        context: Context,
        sinceMillis: Long = 0L,
        processName: String? = null,
        maxRecords: Int = 16
    ): List<ExitRecord> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.getHistoricalProcessExitReasons(context.packageName, 0, maxRecords)
                .asSequence()
                .filter { it.timestamp >= sinceMillis }
                .filter { processName == null || it.processName == processName }
                .map { info ->
                    val trace = readTrace(info)
                    ExitRecord(
                        processName = info.processName,
                        timestamp = info.timestamp,
                        reason = info.reason,
                        status = info.status,
                        description = info.description.orEmpty(),
                        importance = info.importance,
                        pss = info.pss,
                        rss = info.rss,
                        trace = trace.first,
                        pid = info.pid,
                        nativeTrace = trace.second
                    )
                }
                .sortedByDescending { it.timestamp }
                .toList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun format(records: List<ExitRecord>): String = records
        .joinToString("\n\n====================\n\n") { it.format() }

    fun writeNativeTraces(directory: File, records: List<ExitRecord>) {
        records.forEach { record ->
            record.nativeTrace?.let { bytes ->
                directory.mkdirs()
                File(directory, "native-${record.timestamp}-${record.pid}.pb").writeBytes(bytes)
            }
        }
    }

    private fun readTrace(info: ApplicationExitInfo): Pair<String, ByteArray?> {
        return try {
            info.traceInputStream?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var remaining = MAX_TRACE_BYTES
                while (remaining > 0) {
                    val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    remaining -= count
                }
                val truncated = remaining == 0 && input.read() != -1
                if (info.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
                    if (truncated) {
                        "Native tombstone exceeded $MAX_TRACE_BYTES bytes; incomplete protobuf omitted." to null
                    } else {
                        "Native tombstone is binary protobuf; see native-${info.timestamp}-${info.pid}.pb in the exported logs." to output.toByteArray()
                    }
                } else {
                    (output.toString(Charsets.UTF_8.name()) +
                        if (truncated) "\n[trace truncated at $MAX_TRACE_BYTES bytes]" else "") to null
                }
            } ?: ("System trace not provided by Android." to null)
        } catch (error: Exception) {
            "System trace unavailable: ${error.javaClass.simpleName}: ${error.message}" to null
        }
    }

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_UNKNOWN -> "UNKNOWN"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "PACKAGE_STATE_CHANGE"
        ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
        else -> "REASON_$reason"
    }
}
