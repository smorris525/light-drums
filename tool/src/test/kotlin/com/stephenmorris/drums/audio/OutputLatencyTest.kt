package com.stephenmorris.drums.audio

import kotlin.test.Test
import kotlin.test.assertEquals

class OutputLatencyTest {
    // 40,000 frames played at the timestamp, 4,800 more in the 0.1 s since, against 48,000 written.
    @Test
    fun latencyIsWrittenMinusPlayed() = assertEquals(3_200L, OutputLatency.frames(48_000, 40_000, 0, 100_000_000, 48_000))

    @Test
    fun neverNegative() = assertEquals(0L, OutputLatency.frames(100, 1_000, 0, 0, 48_000))
}
