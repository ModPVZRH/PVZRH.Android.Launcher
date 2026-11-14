package com.bepinex.android.modpack

import java.io.File
import java.io.RandomAccessFile
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import com.bepinex.android.modpack.DirectoryTransaction.Companion.copy
import com.bepinex.android.modpack.DirectoryTransaction.Companion.directory
import com.bepinex.android.modpack.DirectoryTransaction.Companion.readProperties
import com.bepinex.android.modpack.DirectoryTransaction.Companion.remove
import com.bepinex.android.modpack.DirectoryTransaction.Companion.writeProperties

/** Disk state is shared by the launcher and :game; callers must use [locked]. */
internal class ModpackRuntimeStore(private val gameRoot: File) {
    private val runtime = File(gameRoot, "BepInEx")
    private val marker get() = File(runtime, STATE_FILE)

    fun <T> locked(block: ModpackRuntimeStore.() -> T): T {
        return locks.getOrPut(gameRoot.canonicalPath) { ReentrantLock() }.withLock {
            directory(gameRoot)
            RandomAccessFile(File(gameRoot, ".runtime.lock"), "rw").use { file ->
                file.channel.lock().use {
                    DirectoryTransaction(runtime).recover()
                    block()
                }
            }
        }
    }

    fun owner(fallback: String?): String? =
        if (marker.isFile) readProperties(marker).getProperty("owner").orEmpty().ifEmpty { null }
        else fallback

    fun save(expectedOwner: String?) {
        val actual = owner(expectedOwner)
        if (actual != expectedOwner) return
        if (!marker.isFile && !File(runtime, "config").isDirectory) return
        val destination = stateRoot(actual)
        DirectoryTransaction(destination).replace(setOf("config", "logs")) { staged ->
            copy(File(runtime, "config"), File(staged, "config"))
            directory(File(staged, "config"))
            copy(File(destination, "logs"), File(staged, "logs"))
            copy(File(runtime, "logs"), File(staged, "logs"))
            copy(File(runtime, "LogOutput.log"), File(staged, "logs/LogOutput.log"))
        }
    }

    fun apply(target: String?, fallbackOwner: String?, disabled: Set<String>, plugin: File) {
        val previous = owner(fallbackOwner)
        val source = stateRoot(target)
        require(target == null || File(source, "modpack.json").isFile) { "Modpack is missing: $target" }
        DirectoryTransaction(source).recover()
        save(previous)
        val oldExtras = if (marker.isFile) readExtras(readProperties(marker))
            else if (previous != null) extras(stateRoot(previous)) else emptySet()
        val newExtras = if (target == null) emptySet() else extras(source)
        val extraRoots = (oldExtras + newExtras).map { it.substringBefore('/') }.toSet()
        val entries = linkedSetOf("plugins", "config", "logs", "LogOutput.log", STATE_FILE).apply {
            addAll(extraRoots)
        }
        DirectoryTransaction(runtime).replace(entries) { staged ->
            copy(File(source, "config"), File(staged, "config"))
            directory(File(staged, "config"))
            directory(File(staged, "logs"))
            if (target != null) copy(File(source, "plugins"), File(staged, "plugins"))
            directory(File(staged, "plugins"))
            disabled.forEach { remove(safeChild(File(staged, "plugins"), it)) }
            copy(plugin, File(staged, "plugins/${plugin.name}"))
            extraRoots.forEach { copy(File(runtime, it), File(staged, it)) }
            oldExtras.forEach { remove(safeChild(staged, it)) }
            newExtras.forEach { relative ->
                val destination = safeChild(staged, relative)
                if (destination.isDirectory) remove(destination)
                copy(safeChild(source, relative), destination)
            }
            extraRoots.forEach { root ->
                File(staged, root).walkBottomUp().filter { it.isDirectory && it.list()?.isEmpty() == true }
                    .forEach { remove(it) }
            }
            writeProperties(File(staged, STATE_FILE), Properties().apply {
                setProperty("owner", target.orEmpty())
                setProperty("extra.count", newExtras.size.toString())
                newExtras.forEachIndexed { index, path -> setProperty("extra.$index", path) }
            })
        }
    }

    fun renameOwner(old: String, new: String) {
        if (!marker.isFile || owner(null) != old) return
        val state = readProperties(marker).apply { setProperty("owner", new) }
        writeProperties(marker, state)
    }

    private fun stateRoot(name: String?): File =
        if (name == null) File(gameRoot, "vanilla") else {
            require(name.isNotBlank() && name != "." && name != ".." && '/' !in name && '\\' !in name)
            safeChild(File(gameRoot, "modpacks"), name)
        }

    private fun extras(source: File): Set<String> {
        if (!source.isDirectory) return emptySet()
        return source.listFiles().orEmpty().filter { !protected(it.name) }
            .flatMap { child -> child.walkTopDown().filter { it.isFile }.toList() }
            .map { it.relativeTo(source).invariantSeparatorsPath }.toSet()
    }

    private fun readExtras(state: Properties): Set<String> =
        (0 until state.getProperty("extra.count", "0").toInt()).map { state.getProperty("extra.$it") }
            .onEach { require(!protected(it.substringBefore('/'))) }.toSet()

    private fun protected(name: String): Boolean = name.startsWith(".") || name in setOf(
        "modpack.json", "plugins", "config", "logs", "core", "interop", "unity-libs",
        "LogOutput.log", "launcher-bridge.json", "logcat-capture.txt", "logcat-capture.1.txt",
        "logcat-capture-previous.txt", "java-crash.txt", "java-crash-previous.txt"
    )

    private fun safeChild(root: File, relative: String): File {
        val path = File(root, relative).canonicalFile
        require(path != root.canonicalFile && path.toPath().startsWith(root.canonicalFile.toPath())) {
            "Path outside runtime directory: $relative"
        }
        return path
    }

    companion object {
        private const val STATE_FILE = ".launcher-runtime.properties"
        private val locks = ConcurrentHashMap<String, ReentrantLock>()
    }
}
