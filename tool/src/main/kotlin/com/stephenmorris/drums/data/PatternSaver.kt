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
    private sealed interface Job {
        data class Save(val pattern: PatternData) : Job

        data class Delete(val uuid: String) : Job
    }

    private val queue = Channel<Job>(Channel.UNLIMITED)
    private val latest = ConcurrentHashMap<String, PatternData>()

    /** Deleted patterns: saves arriving after the delete are dropped, so nothing can bring one back. */
    private val deleted = ConcurrentHashMap.newKeySet<String>()

    init {
        scope.launch {
            for (job in queue) {
                when (job) {
                    is Job.Save -> store.save(job.pattern)
                    is Job.Delete -> store.delete(job.uuid)
                }
            }
        }
    }

    /** Queues [pattern] to save. */
    fun save(pattern: PatternData) {
        if (pattern.uuid in deleted) {
            return
        }
        latest[pattern.uuid] = pattern
        queue.trySend(Job.Save(pattern))
    }

    /** Queues a delete behind any saves already queued for the pattern, and drops any that come after it. */
    fun delete(uuid: String) {
        deleted += uuid
        latest.remove(uuid)
        queue.trySend(Job.Delete(uuid))
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
