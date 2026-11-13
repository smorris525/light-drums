package com.stephenmorris.drums.audio

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Ticks the transport: every [BUSY_TICK_MS] while playing or counting in, every [IDLE_TICK_MS] otherwise. A change
 * between the two wakes it at once, so Play never waits out an idle tick.
 */
object TickLoop {
    /** How often the transport schedules ahead while playing or counting in. */
    const val BUSY_TICK_MS = 10L

    /** How often it checks for releasing audio while stopped. */
    const val IDLE_TICK_MS = 1_000L

    /** Runs until cancelled, on the transport's (main) thread. */
    suspend fun run(transport: Transport) {
        combine(transport.playingUuid, transport.takePhase) { uuid, phase -> uuid != null || phase != null }
            .distinctUntilChanged()
            .collectLatest { busy ->
                while (true) {
                    transport.tick()
                    delay(if (busy) BUSY_TICK_MS else IDLE_TICK_MS)
                }
            }
    }
}
