package com.bepinex.android

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal object LogMemoryLimits {
    const val MAX_ENTRIES = 1000
    const val MAX_CHARACTERS = 256 * 1024
    const val MAX_ENTRY_CHARACTERS = 8 * 1024

    fun preview(text: String): String {
        if (text.length <= MAX_ENTRY_CHARACTERS) return text
        val suffix = " … [truncated]"
        var end = MAX_ENTRY_CHARACTERS - suffix.length
        if (text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
        return text.substring(0, end) + suffix
    }
}

/** Callers serialize access; both entry count and retained text have fixed budgets. */
internal class BoundedLogBuffer<T>(
    private val maxEntries: Int = LogMemoryLimits.MAX_ENTRIES,
    private val maxCharacters: Int = LogMemoryLimits.MAX_CHARACTERS,
    private val characters: (T) -> Int
) {
    private val entries = ArrayDeque<T>()
    private var retainedCharacters = 0

    init {
        require(maxEntries > 0 && maxCharacters > 0)
    }

    fun add(entry: T) {
        val size = characters(entry)
        require(size >= 0)
        if (size > maxCharacters) {
            clear()
            return
        }
        while (entries.isNotEmpty() &&
            (entries.size >= maxEntries || retainedCharacters > maxCharacters - size)) {
            retainedCharacters -= characters(entries.removeFirst())
        }
        entries.addLast(entry)
        retainedCharacters += size
    }

    fun snapshot(): List<T> = entries.toList()

    fun clear() {
        entries.clear()
        retainedCharacters = 0
    }
}

/** A conflated notification batches snapshots without queueing individual log messages. */
internal class BatchedLogBuffer<T>(
    scope: CoroutineScope,
    private val publishIntervalMillis: Long = 250,
    maxEntries: Int = LogMemoryLimits.MAX_ENTRIES,
    maxCharacters: Int = LogMemoryLimits.MAX_CHARACTERS,
    characters: (T) -> Int
) {
    private val lock = Any()
    private val buffer = BoundedLogBuffer(maxEntries, maxCharacters, characters)
    private val changed = Channel<Unit>(Channel.CONFLATED)
    private val state = MutableStateFlow<List<T>>(emptyList())
    val entries = state.asStateFlow()
    private var dirty = false

    init {
        scope.launch {
            for (notification in changed) {
                delay(publishIntervalMillis)
                synchronized(lock) {
                    if (dirty) {
                        state.value = buffer.snapshot()
                        dirty = false
                    }
                }
            }
        }
    }

    fun add(entry: T) {
        synchronized(lock) {
            buffer.add(entry)
            dirty = true
        }
        changed.trySend(Unit)
    }

    fun snapshot(): List<T> = synchronized(lock) { buffer.snapshot() }

    fun clear() = synchronized(lock) {
        buffer.clear()
        dirty = false
        state.value = emptyList()
    }
}
