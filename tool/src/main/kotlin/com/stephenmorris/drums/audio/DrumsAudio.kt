package com.stephenmorris.drums.audio

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The process-wide audio: one output and one transport, built once, off the main thread (rendering the kit is slow). */
object DrumsAudio {
    private val ready = CompletableDeferred<Transport>()
    private val preparing = AtomicBoolean(false)

    /** The transport, building it on first call. Every caller gets the same one; later callers wait for the first. */
    suspend fun prepare(): Transport {
        if (preparing.compareAndSet(false, true)) {
            val output = withContext(Dispatchers.Default) { TrackAudioOutput.create() }
            // Recorded hits reach the store in plan 3; until then the transport keeps them for this session.
            ready.complete(Transport(output) { _, _ -> })
        }
        return ready.await()
    }
}
