package com.stephenmorris.drums.audio

import com.stephenmorris.drums.core.Level
import com.stephenmorris.drums.core.PatternData
import com.stephenmorris.drums.core.Sound
import com.stephenmorris.drums.core.StepGrid
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class TickLoopTest {
    // Review finding: Play from stopped waited out the idle tick (up to a second) before scheduling past its first
    // 0.12 s, so the groove dropped out and restarted. Ticks must start at once.
    @Test
    fun playWakesTheLoopAtOnce() = runBlocking {
        val output = FakeAudioOutput()
        val transport = Transport(output) {}
        val pattern = PatternData(
            uuid = "p",
            createdAt = 0,
            bpm = 120,
            steps = (0 until 16).fold(StepGrid.EMPTY) { grid, step -> grid.with(Sound.HAT, step, Level.HIT) },
        )
        val loop = launch { TickLoop.run(transport) }
        delay(50)
        transport.play(pattern, 0)
        val scheduled = output.triggers.size
        // A quarter second of audio passes; a running loop schedules the steps it reaches.
        output.renderedFrames += 12_000
        delay(100)
        loop.cancelAndJoin()
        assertTrue(output.triggers.size > scheduled, "no ticks after play: ${output.triggers.size} triggers")
    }
}
