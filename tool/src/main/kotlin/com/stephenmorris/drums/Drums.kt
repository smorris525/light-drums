package com.stephenmorris.drums

import com.stephenmorris.drums.audio.TickLoop
import com.stephenmorris.drums.audio.TrackAudioOutput
import com.stephenmorris.drums.audio.Transport
import com.stephenmorris.drums.data.ClickPreference
import com.stephenmorris.drums.data.PatternSaver
import com.stephenmorris.drums.data.PatternStore
import com.thelightphone.sdk.SealedLightContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
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
    data class Ready(
        val store: PatternStore,
        val transport: Transport,
        val saver: PatternSaver,
        val click: ClickPreference,
    )

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
        val saver = PatternSaver(store, scope)
        val transport = withContext(Dispatchers.Main) {
            Transport(output) { uuid, steps ->
                // A recorded hit: save the transport's grid onto the latest copy of the pattern.
                scope.launch {
                    val current = saver.latest(uuid) ?: store.pattern(uuid).first() ?: return@launch
                    saver.save(current.copy(steps = steps))
                }
            }
        }
        val click = ClickPreference.open(context)
        transport.clickMode = click.load()
        scope.launch { TickLoop.run(transport) }
        return Ready(store, transport, saver, click)
    }

}
