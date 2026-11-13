package com.stephenmorris.drums.audio

import com.stephenmorris.drums.core.Level
import com.stephenmorris.drums.core.PatternData
import com.stephenmorris.drums.core.Sound
import com.stephenmorris.drums.core.StepGrid
import com.stephenmorris.drums.core.TakePhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 120 bpm, one bar of 4/4, at 48 kHz: one step is 6,000 frames. Playback starts 960 frames (0.02 s) after the clock.
class TransportTest {
    private val output = FakeAudioOutput()
    private var now = 1_000L
    private val recorded = mutableListOf<Pair<String, StepGrid>>()
    private val transport = Transport(output, { now }) { uuid, steps -> recorded += uuid to steps }
    private val pattern = PatternData(uuid = "p", createdAt = 0, bpm = 120, steps = StepGrid.EMPTY.with(Sound.KICK, 0, Level.ACCENT))

    /** Moves the clock to [frame] in 3,000-frame ticks, as the 10 ms loop would. */
    private fun runTo(frame: Long) {
        while (output.renderedFrames < frame) {
            output.renderedFrames = minOf(frame, output.renderedFrames + 3_000)
            transport.tick()
        }
    }

    private fun stepFrame(step: Int) = 960L + 6_000L * step

    @Test
    fun playSchedulesTheLookahead() {
        transport.play(pattern, 0)
        assertEquals(listOf(VoiceEvent.Trigger(0, 960, 1f)), output.triggers)
        assertEquals("p", transport.playingUuid.value)
    }

    @Test
    fun tickSchedulesAsTheClockMoves() {
        transport.play(pattern.copy(steps = StepGrid.EMPTY.with(Sound.KICK, 1, Level.HIT)), 0)
        output.renderedFrames = 6_000
        transport.tick()
        assertEquals(listOf(VoiceEvent.Trigger(0, 6_960, 0.6f)), output.triggers)
    }

    @Test
    fun stopSendsStopAllAndClears() {
        transport.play(pattern, 0)
        runTo(stepFrame(1))
        transport.stop()
        assertEquals(VoiceEvent.StopAll, output.events.last())
        assertNull(transport.playingUuid.value)
        assertNull(transport.currentStep.value)
        assertNull(transport.takePhase.value)
    }

    @Test
    fun updateAppliesFromTheNextStep() {
        transport.play(pattern, 0)
        transport.update(pattern.copy(steps = pattern.steps.with(Sound.SNARE, 2, Level.HIT)))
        runTo(stepFrame(2))
        assertTrue(VoiceEvent.Trigger(1, stepFrame(2), 0.6f) in output.triggers)
    }

    @Test
    fun deletingThePlayingPatternStops() {
        transport.play(pattern, 0)
        transport.patternDeleted("p")
        assertEquals(VoiceEvent.StopAll, output.events.last())
        assertNull(transport.playingUuid.value)
    }

    @Test
    fun padHitPlaysAtOnceAtPadGain() {
        output.renderedFrames = 500
        transport.hit(Sound.KICK, Level.HIT, null)
        transport.hit(Sound.SNARE, Level.ACCENT, null)
        assertEquals(listOf(VoiceEvent.Trigger(0, 500, 0.85f), VoiceEvent.Trigger(1, 500, 1f)), output.triggers)
    }

    @Test
    fun recordFromStoppedCountsInThenRecords() {
        transport.record(pattern, 0)
        val clicks = output.triggers.filter { it.voice >= 8 }
        assertEquals(listOf(8, 9, 9, 9), clicks.map { it.voice })
        assertEquals(listOf(960L, 24_960L, 48_960L, 72_960L), clicks.map { it.frame })
        assertEquals(TakePhase.COUNTING_IN, transport.takePhase.value)
        val counts = mutableListOf<Int?>()
        for (frame in clicks.map { it.frame }) {
            runTo(frame)
            counts += transport.countInNumber.value
        }
        assertEquals(listOf<Int?>(1, 2, 3, 4), counts)
        runTo(960L + 96_000)
        assertEquals(TakePhase.RECORDING, transport.takePhase.value)
        assertNull(transport.countInNumber.value)
    }

    @Test
    fun recordedHitLandsOnTheHeardStepOnce() {
        transport.play(pattern, 0)
        transport.record(pattern, 0)
        assertEquals(TakePhase.RECORDING, transport.takePhase.value)
        output.latencyFrames = 3_000
        runTo(stepFrame(4) + 3_000 + 100)
        transport.hit(Sound.SNARE, Level.ACCENT, pattern)
        assertEquals(1, recorded.size)
        assertEquals("p", recorded[0].first)
        assertEquals(Level.ACCENT, recorded[0].second[Sound.SNARE, 4])
        transport.hit(Sound.SNARE, Level.ACCENT, pattern)
        assertEquals(1, recorded.size)
    }

    @Test
    fun clicksOnlyOnPadsInRec() {
        transport.play(pattern, 0)
        runTo(stepFrame(8))
        assertFalse(output.triggers.any { it.voice >= 8 })
        transport.isOnPads = true
        runTo(stepFrame(16))
        assertTrue(VoiceEvent.Trigger(8, stepFrame(16), 1f) in output.triggers)
    }

    @Test
    fun aStalledClockSchedulesNothingNew() {
        transport.play(pattern, 0)
        val sent = output.events.size
        repeat(100) { transport.tick() }
        assertEquals(sent, output.events.size)
    }

    @Test
    fun aStopOvertakesAPendingStart() {
        transport.play(pattern, 0)
        transport.stop()
        output.renderedFrames = 10_000
        transport.play(pattern, 0)
        assertEquals(1, output.events.count { it == VoiceEvent.StopAll })
        val afterStop = output.events.drop(output.events.indexOf(VoiceEvent.StopAll) + 1)
        assertEquals(listOf(VoiceEvent.Trigger(0, 10_960, 1f)), afterStop)
    }

    @Test
    fun leavingDuringACountInStops() {
        transport.record(pattern, 0)
        transport.suspend()
        assertNull(transport.takePhase.value)
        assertNull(transport.countInNumber.value)
        assertEquals(VoiceEvent.StopAll, output.events.last())
        assertEquals(1, output.suspendCount)
    }

    @Test
    fun releasesTheOutputAfterThirtySecondsIdle() {
        transport.play(pattern, 0)
        transport.stop()
        now += 29_999
        transport.tick()
        assertTrue(output.isStarted)
        now += 1
        transport.tick()
        assertFalse(output.isStarted)
        assertEquals(1, output.suspendCount)
    }

    @Test
    fun failedStartPlaysNothing() {
        output.startResult = false
        transport.play(pattern, 0)
        assertTrue(output.events.isEmpty())
        assertNull(transport.playingUuid.value)
    }

    // Review finding 2: an output that stopped by itself (its writer died) is started again on the next sound,
    // rather than the transport trusting that it's still going.
    @Test
    fun startsTheOutputAgainOnTheNextSound() {
        transport.play(pattern, 0)
        val starts = output.startCount
        output.isStarted = false
        transport.stop()
        transport.play(pattern, 0)
        assertEquals(starts + 1, output.startCount)
        transport.hit(Sound.KICK, Level.HIT, null)
        assertEquals(starts + 2, output.startCount)
    }
}
