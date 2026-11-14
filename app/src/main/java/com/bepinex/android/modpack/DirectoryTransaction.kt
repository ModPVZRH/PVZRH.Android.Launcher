package com.bepinex.android.modpack

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Properties

/** Replaces selected directory entries, retaining originals until the commit is durable. */
internal class DirectoryTransaction(
    private val root: File,
    private val afterInstall: (String) -> Unit = {}
) {
    private val transaction = File(root, ".launcher-transaction")
    private val journal = File(transaction, "journal")
    private val staged = File(transaction, "staged")
    private val backup = File(transaction, "backup")

    fun recover() {
        if (!transaction.exists()) return
        if (!journal.exists()) {
            remove(transaction)
            return
        }
        val record = readProperties(journal)
        if (record.getProperty("committed") != "true") {
            val count = record.getProperty("count").toInt()
            repeat(count) { index ->
                val name = record.getProperty("name.$index")
                requireEntry(name)
                val saved = File(backup, name)
                val live = File(root, name)
                if (saved.exists()) {
                    remove(live)
                    move(saved, live)
                } else if (record.getProperty("existed.$index") != "true") {
                    remove(live)
                }
            }
        }
        remove(transaction)
    }

    fun replace(names: Set<String>, prepare: (File) -> Unit) {
        recover()
        names.forEach(::requireEntry)
        directory(staged)
        directory(backup)
        try {
            prepare(staged)
            val record = Properties().apply {
                setProperty("count", names.size.toString())
                names.forEachIndexed { index, name ->
                    setProperty("name.$index", name)
                    setProperty("existed.$index", File(root, name).exists().toString())
                }
            }
            writeProperties(journal, record)
            names.forEach { name ->
                val live = File(root, name)
                if (live.exists()) move(live, File(backup, name))
                val replacement = File(staged, name)
                if (replacement.exists()) move(replacement, live)
                afterInstall(name)
            }
            record.setProperty("committed", "true")
            writeProperties(journal, record)
        } catch (error: Exception) {
            try { recover() } catch (rollback: Exception) { error.addSuppressed(rollback) }
            throw error
        }
        // A committed journal is safe to clean on the next operation if cleanup fails.
        runCatching { remove(transaction) }
    }

    companion object {
        fun directory(file: File) {
            if (!file.isDirectory && !file.mkdirs()) throw IOException("Cannot create $file")
        }

        fun remove(file: File) {
            if (file.exists() && !file.deleteRecursively()) throw IOException("Cannot remove $file")
        }

        fun move(from: File, to: File) {
            if (!from.renameTo(to)) throw IOException("Cannot move $from to $to")
        }

        fun copy(from: File, to: File) {
            if (!from.exists()) return
            directory(to.parentFile!!)
            if (!from.copyRecursively(to, overwrite = true)) throw IOException("Cannot copy $from")
        }

        fun readProperties(file: File): Properties = Properties().apply {
            file.inputStream().use { load(it) }
        }

        fun writeProperties(file: File, properties: Properties) {
            directory(file.parentFile!!)
            val temp = File(file.parentFile, "${file.name}.tmp")
            FileOutputStream(temp).use { output ->
                properties.store(output, null)
                output.fd.sync()
            }
            java.nio.file.Files.move(temp.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        }

        private fun requireEntry(name: String) {
            require(name.isNotEmpty() && name != "." && name != ".." &&
                '/' !in name && '\\' !in name && name != ".launcher-transaction")
        }
    }
}
