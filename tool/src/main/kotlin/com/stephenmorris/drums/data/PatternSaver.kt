package com.stephenmorris.drums.data

import com.stephenmorris.drums.core.PatternData
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Saves edited patterns one at a time, in the order they were made, in a scope that outlives any screen, so leaving a
 * screen mid-save can't lose an edit. It remembers the latest copy saved of each pattern, so a screen can tell a copy
 * the store emits for an earlier save from the current one.
 */
class PatternSaver(private val store: PatternStore, scope: CoroutineScope) {
    private val queue = Channel<PatternData>(Channel.UNLIMITED)
    private val latest = ConcurrentHashMap<String, PatternData>()

    init {
        scope.launch {
            for (pattern in queue) {
                store.save(pattern)
            }
        }
    }

    /** Queues [pattern] to save. */
    fun save(pattern: PatternData) {
        latest[pattern.uuid] = pattern
        queue.trySend(pattern)
    }

    /** The latest copy saved here, or `null` if this pattern hasn't been saved through the saver. */
    fun latest(uuid: String): PatternData? = latest[uuid]

    /**
     * Whether [stored] is the latest copy saved, ignoring `editedAt`, which the store sets. A pattern never saved here
     * is always current.
     */
    fun isCurrent(stored: PatternData): Boolean {
        val saved = latest[stored.uuid] ?: return true
        return saved == stored.copy(editedAt = saved.editedAt)
    }
}
