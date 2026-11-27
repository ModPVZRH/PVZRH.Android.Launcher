package com.bepinex.android

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class LogMemoryTest {
    @Test fun burstKeepsNewestEntriesWithoutChangingAnEarlierSnapshot() {
        val buffer = BoundedLogBuffer<String>(maxEntries = 1000) { it.length }
        buffer.add("original")
        val original = buffer.snapshot()
        repeat(100_000) { buffer.add("entry-$it") }
        assertEquals(listOf("original"), original)
        assertEquals(1000, buffer.snapshot().size)
        assertEquals("entry-99000", buffer.snapshot().first())
        assertEquals("entry-99999", buffer.snapshot().last())
    }

    @Test fun textBudgetAppliesEvenWithFewLargeEntries() {
        val buffer = BoundedLogBuffer<String> { it.length }
        repeat(1000) { buffer.add(LogMemoryLimits.preview("$it:" + "x".repeat(16 * 1024))) }
        val entries = buffer.snapshot()
        assertEquals(32, entries.size)
        assertTrue(entries.sumOf { it.length } <= LogMemoryLimits.MAX_CHARACTERS)
        assertTrue(entries.last().startsWith("999:"))
    }

    @Test fun truncationDoesNotSplitASurrogatePair() {
        val boundary = LogMemoryLimits.MAX_ENTRY_CHARACTERS - " … [truncated]".length
        val preview = LogMemoryLimits.preview("x".repeat(boundary - 1) + "🙂" + "y".repeat(100))
        assertTrue(preview.length <= LogMemoryLimits.MAX_ENTRY_CHARACTERS)
        assertFalse(preview.any { it.isSurrogate() })
        assertTrue(preview.endsWith("[truncated]"))
    }

    @Test fun concurrentWritersAreBatchedAndClearCannotRestoreAnOldSnapshot() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val cache = BatchedLogBuffer<String>(scope, publishIntervalMillis = 100) { it.length }
            coroutineScope {
                repeat(4) { writer ->
                    launch(Dispatchers.Default) { repeat(5000) { cache.add("$writer:$it") } }
                }
            }
            cache.add("final")
            val snapshot = withTimeout(5000) { cache.entries.first { it.lastOrNull() == "final" } }
            assertEquals(1000, snapshot.size)
            assertEquals(1000, snapshot.distinct().size)
            cache.add("pending-before-clear")
            cache.clear()
            delay(250)
            assertTrue(cache.entries.value.isEmpty())
            assertTrue(cache.snapshot().isEmpty())
            cache.add("after-clear")
            assertEquals(listOf("after-clear"), withTimeout(5000) { cache.entries.first { it.isNotEmpty() } })
        } finally {
            scope.cancel()
        }
    }

    @Test fun snapshotsAreDelayedUntilTheBatchInterval() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val cache = BatchedLogBuffer<String>(scope, publishIntervalMillis = 250) { it.length }
            repeat(1000) { cache.add("entry-$it") }
            assertTrue(cache.entries.value.isEmpty())
            assertEquals(1000, cache.snapshot().size)
            assertEquals(1000, withTimeout(5000) { cache.entries.first { it.isNotEmpty() } }.size)
        } finally {
            scope.cancel()
        }
    }
}
