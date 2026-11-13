package com.stephenmorris.drums

import com.stephenmorris.drums.audio.TrackAudioOutput
import com.stephenmorris.drums.audio.Transport
import com.stephenmorris.drums.data.PatternStore
import com.thelightphone.sdk.SealedLightContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The tool's process-wide parts: the pattern store, the audio transport, and the loop that ticks the transport. Built
 * once, off the main thread (opening the database and rendering the kit are slow).
 */
object Drums {
    /** Everything a screen needs, once it's built. */
    data class Ready(val store: PatternStore, val transport: Transport)

    private val scope = MainScope()
    private val mutex = Mutex()

    @Volatile
    private var ready: Ready? = null

    /**
     * The store and transport, building them on first call. Every caller gets the same ones; later callers wait for
     * the first. A cancelled caller doesn't cancel the build, so nobody is left waiting for one that never finishes.
     */
    suspend fun prepare(context: SealedLightContext): Ready {
        ready?.let { return it }
        return withContext(NonCancellable) {
            mutex.withLock { ready ?: build(context).also { ready = it } }
        }
    }

    private suspend fun build(context: SealedLightContext): Ready {
        val (store, output) = withContext(Dispatchers.Default) {
            val store = PatternStore.open(context).apply { seedIfNeeded() }
            store to TrackAudioOutput.create()
        }
        val transport = withContext(Dispatchers.Main) {
            Transport(output) { uuid, steps ->
                // A recorded hit: save the transport's grid onto the stored pattern.
                scope.launch {
                    store.pattern(uuid).first()?.let { store.save(it.copy(steps = steps)) }
                }
            }
        }
        scope.launch {
            while (isActive) {
                transport.tick()
                val busy = transport.playingUuid.value != null || transport.takePhase.value != null
                delay(if (busy) BUSY_TICK_MS else IDLE_TICK_MS)
            }
        }
        return Ready(store, transport)
    }

    /** How often the transport schedules ahead while playing or counting in. */
    private const val BUSY_TICK_MS = 10L

    /** How often it checks for releasing audio while stopped. */
    private const val IDLE_TICK_MS = 1_000L
}
