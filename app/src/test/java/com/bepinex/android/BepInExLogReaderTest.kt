package com.bepinex.android

import com.bepinex.android.log.BepInExLogReader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.RandomAccessFile

class BepInExLogReaderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After fun stop() {
        BepInExLogReader.stopWatching()
        scope.cancel()
    }

    private suspend fun awaitLast(message: String) = withTimeout(5000) {
        BepInExLogReader.lines.first { it.lastOrNull()?.message == message }
    }

    @Test fun largeSparseFileReadsOnlyTheTailAndKeepsNewestThousandLines(): Unit = runBlocking {
        val file = temporary.newFile()
        RandomAccessFile(file, "rw").use {
            it.setLength(512L * 1024 * 1024)
            it.seek(it.length())
            it.write("\n".toByteArray())
            repeat(20_000) { line -> it.write("[Info :Test] line-$line\n".toByteArray()) }
        }
        BepInExLogReader.startWatchingFile(file, scope)
        val entries = awaitLast("line-19999")
        assertEquals(1000, entries.size)
        assertEquals("line-19000", entries.first().message)
        assertTrue(entries.all { it.source == "Test" })
    }

    @Test fun oversizedLineIsBoundedAndLaterAppendsRecoverUtf8AndPartialLines(): Unit = runBlocking {
        val file = temporary.newFile()
        file.writeText("x".repeat(2 * 1024 * 1024))
        BepInExLogReader.startWatchingFile(file, scope)
        val longEntry = withTimeout(5000) { BepInExLogReader.lines.first { it.isNotEmpty() } }.single()
        assertTrue(longEntry.message.length <= LogMemoryLimits.MAX_ENTRY_CHARACTERS)
        file.appendText("\n[Info :Test] par")
        awaitLast("par")
        val chinese = "汉".toByteArray(Charsets.UTF_8)
        file.appendBytes("tial-".toByteArray() + chinese.copyOf(2))
        delay(650)
        file.appendBytes(chinese.copyOfRange(2, 3) + "🙂\n".toByteArray(Charsets.UTF_8))
        val entries = awaitLast("partial-汉🙂")
        assertEquals(1, entries.size)
    }

    @Test fun textBudgetIsEnforcedBeforePublishing(): Unit = runBlocking {
        val file = temporary.newFile()
        file.bufferedWriter().use { writer ->
            repeat(1000) { writer.appendLine("[Info :Test] $it:" + "汉".repeat(9000)) }
        }
        BepInExLogReader.startWatchingFile(file, scope)
        val entries = withTimeout(5000) { BepInExLogReader.lines.first { it.isNotEmpty() } }
        assertTrue(entries.all { it.message.length <= LogMemoryLimits.MAX_ENTRY_CHARACTERS })
        assertTrue(entries.sumOf { it.source.length + it.message.length } <= LogMemoryLimits.MAX_CHARACTERS)
        assertTrue(entries.last().message.startsWith("999:"))
    }

    @Test fun truncationDeletionAndRecreationReplaceOldEntries(): Unit = runBlocking {
        val file = temporary.newFile()
        file.writeText("[Info :Test] old-long-message\n")
        BepInExLogReader.startWatchingFile(file, scope)
        awaitLast("old-long-message")
        file.writeText("[Info :Test] new\n")
        assertEquals(1, awaitLast("new").size)
        assertTrue(file.delete())
        withTimeout(5000) { BepInExLogReader.lines.first { it.isEmpty() } }
        file.writeText("[Info :Test] recreated\n")
        assertEquals(1, awaitLast("recreated").size)
    }

    @Test fun clearReloadsAndSwitchingFilesCannotPublishTheOldSession(): Unit = runBlocking {
        val first = temporary.newFile()
        first.writeText("[Info :Test] first\n")
        val second = temporary.newFile()
        second.writeText("[Info :Test] second\n")
        BepInExLogReader.startWatchingFile(first, scope)
        awaitLast("first")
        BepInExLogReader.clear()
        awaitLast("first")
        repeat(100) {
            BepInExLogReader.startWatchingFile(first, scope)
            BepInExLogReader.startWatchingFile(second, scope)
        }
        awaitLast("second")
        delay(650)
        assertEquals(listOf("second"), BepInExLogReader.lines.value.map { it.message })
    }

    @Test fun sameFileCanRestartAfterItsScopeWasCancelled(): Unit = runBlocking {
        val file = temporary.newFile()
        file.writeText("[Info :Test] initial\n")
        val previousScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            BepInExLogReader.startWatchingFile(file, previousScope)
            awaitLast("initial")
        } finally {
            previousScope.cancel()
        }
        file.appendText("[Info :Test] restarted\n")
        BepInExLogReader.startWatchingFile(file, scope)
        awaitLast("restarted")
    }
}
