package com.stephenmorris.drums.session

import com.stephenmorris.drums.audio.FakeAudioOutput
import com.stephenmorris.drums.audio.Transport
import com.stephenmorris.drums.audio.VoiceEvent
import com.stephenmorris.drums.core.Level
import com.stephenmorris.drums.core.PatternData
import com.stephenmorris.drums.core.Sound
import com.stephenmorris.drums.core.StepGrid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PatternSessionTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val output = FakeAudioOutput()
    private val transport = Transport(output) { _, _ -> }
    private val saved = mutableListOf<PatternData>()
    private val twoBars = PatternData(uuid = "p", createdAt = 0, bpm = 120, bars = 2)

    private fun session(pattern: PatternData = twoBars) = PatternSession(pattern, transport, scope) { saved += it }

    @AfterTest
    fun tearDown() = scope.cancel()

    @Test
    fun tapTogglesAndSaves() {
        val session = session()
        session.tap(Sound.KICK, 2)
        assertEquals(Level.HIT, session.pattern.value.steps[Sound.KICK, 2])
        assertEquals(listOf(session.pattern.value), saved)
    }

    @Test
    fun tappingAnAccentClearsIt() {
        val session = session(twoBars.copy(steps = StepGrid.EMPTY.with(Sound.KICK, 2, Level.ACCENT)))
        session.tap(Sound.KICK, 2)
        assertNull(session.pattern.value.steps[Sound.KICK, 2])
    }

    @Test
    fun accentSetsAnAccent() {
        val session = session()
        session.accent(Sound.SNARE, 4)
        assertEquals(Level.ACCENT, session.pattern.value.steps[Sound.SNARE, 4])
    }

    @Test
    fun paintStrokeEnablesThenErases() {
        val session = session()
        session.beginPaint(Sound.HAT, 0)
        (0..3).forEach { session.paint(Sound.HAT, it) }
        session.endPaint()
        assertEquals((0..3).associateWith { Level.HIT }, session.pattern.value.steps.hits(Sound.HAT))
        session.beginPaint(Sound.HAT, 1)
        (1..2).forEach { session.paint(Sound.HAT, it) }
        session.endPaint()
        assertEquals(setOf(0, 3), session.pattern.value.steps.hits(Sound.HAT).keys)
    }

    @Test
    fun unchangedEditsDontSave() {
        val session = session(twoBars.copy(steps = StepGrid.EMPTY.with(Sound.HAT, 0, Level.HIT)))
        session.beginPaint(Sound.TOM, 5)
        session.endPaint()
        session.beginPaint(Sound.HAT, 1)
        session.paint(Sound.HAT, 0)
        session.endPaint()
        assertEquals(0, saved.size)
    }

    // An edit turns follow off, and the transport hears it.
    @Test
    fun editingTurnsFollowOffAndUpdatesTheTransport() {
        val session = session(twoBars.copy(steps = StepGrid.EMPTY.with(Sound.KICK, 0, Level.ACCENT)))
        session.togglePlay()
        session.tap(Sound.SNARE, 4)
        session.playheadMoved(16)
        assertEquals(0, session.window.value.firstBar)
        // The transport plays the edit: step 4's snare is scheduled once the clock gets there.
        while (output.renderedFrames < 960 + 6_000 * 4L) {
            output.renderedFrames += 3_000
            transport.tick()
        }
        assertTrue(VoiceEvent.Trigger(1, 960 + 6_000 * 4L, 0.6f) in output.triggers)
    }

    @Test
    fun followingMovesTheWindow() {
        val session = session()
        session.togglePlay()
        session.playheadMoved(16)
        assertEquals(1, session.window.value.firstBar)
    }

    @Test
    fun showBarClampsAndFollowsOnlyThePlayingBar() {
        val session = session()
        session.togglePlay()
        session.playheadMoved(2)
        // Another bar: follow turns off, so the window stays put as the playhead moves on.
        session.showBar(1)
        session.playheadMoved(5)
        assertEquals(1, session.window.value.firstBar)
        // The playing bar's dot turns follow back on.
        session.showBar(0)
        session.playheadMoved(17)
        assertEquals(1, session.window.value.firstBar)
        session.showBar(9)
        assertEquals(1, session.window.value.firstBar)
    }

    @Test
    fun refreshIsIgnoredMidStroke() {
        val session = session()
        val other = twoBars.copy(name = "Elsewhere")
        session.beginPaint(Sound.HAT, 0)
        session.refresh(other)
        assertEquals(twoBars.name, session.pattern.value.name)
        session.endPaint()
        session.refresh(other)
        assertEquals("Elsewhere", session.pattern.value.name)
    }

    @Test
    fun playStartsFromTheFirstStepInView() {
        val session = session(twoBars.copy(steps = StepGrid.EMPTY.with(Sound.KICK, 16, Level.ACCENT)))
        session.showBar(1)
        session.togglePlay()
        assertTrue(session.isPlaying.value)
        assertEquals(VoiceEvent.Trigger(0, 960, 1f), output.triggers.first())
        session.togglePlay()
        assertFalse(session.isPlaying.value)
    }
}
