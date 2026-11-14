package com.bepinex.android.modpack

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DirectoryTransactionTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun failedPreparationDoesNotTouchLiveFiles() {
        val root = temporary.newFolder()
        File(root, "config").writeText("original")
        assertThrows(IOException::class.java) {
            DirectoryTransaction(root).replace(setOf("config")) { stage ->
                File(stage, "config").writeText("incomplete")
                throw IOException("disk full")
            }
        }
        assertEquals("original", File(root, "config").readText())
        assertFalse(File(root, ".launcher-transaction").exists())
    }

    @Test fun failedCommitRestoresOverwrittenDeletedAndNewEntries() {
        val root = temporary.newFolder()
        File(root, "plugins").writeText("old plugins")
        File(root, "config").writeText("old config")
        val transaction = DirectoryTransaction(root) { entry ->
            if (entry == "new") throw IOException("rename failed")
        }
        assertThrows(IOException::class.java) {
            transaction.replace(linkedSetOf("plugins", "config", "new")) { stage ->
                File(stage, "plugins").writeText("new plugins")
                File(stage, "new").writeText("new file")
            }
        }
        assertEquals("old plugins", File(root, "plugins").readText())
        assertEquals("old config", File(root, "config").readText())
        assertFalse(File(root, "new").exists())
    }

    @Test fun nextInstanceRecoversInterruptedCommit() {
        val root = temporary.newFolder()
        File(root, "config").writeText("old config")
        File(root, "plugins").writeText("old plugins")
        val transaction = DirectoryTransaction(root) { throw SimulatedProcessDeath() }
        assertThrows(SimulatedProcessDeath::class.java) {
            transaction.replace(linkedSetOf("config", "plugins")) { stage ->
                File(stage, "config").writeText("new config")
                File(stage, "plugins").writeText("new plugins")
            }
        }
        DirectoryTransaction(root).recover()
        DirectoryTransaction(root).recover()
        assertEquals("old config", File(root, "config").readText())
        assertEquals("old plugins", File(root, "plugins").readText())
        assertFalse(File(root, ".launcher-transaction").exists())
    }

    @Test fun committedReplacementCanDeleteEntriesAndKeepsUnrelatedFiles() {
        val root = temporary.newFolder()
        File(root, "old").writeText("old")
        File(root, "core").writeText("framework")
        DirectoryTransaction(root).replace(setOf("old", "new")) { stage ->
            File(stage, "new").writeText("new")
        }
        DirectoryTransaction(root).recover()
        assertFalse(File(root, "old").exists())
        assertEquals("new", File(root, "new").readText())
        assertEquals("framework", File(root, "core").readText())
    }

    private class SimulatedProcessDeath : Error()
}
