package com.stephenmorris.drums.session

import com.stephenmorris.drums.audio.Transport
import com.stephenmorris.drums.core.BarWindow
import com.stephenmorris.drums.core.Follow
import com.stephenmorris.drums.core.Level
import com.stephenmorris.drums.core.PatternData
import com.stephenmorris.drums.core.Sound
import com.stephenmorris.drums.core.StepGrid
import com.stephenmorris.drums.core.StepPaintStroke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * One visit to a pattern (spec §8): the pattern as edited, the bars in view, follow,
 * and step edits. The pattern and sound screens share it. Every edit is shown at once, saved through [save], and
 * handed to the transport, so it plays from the next unscheduled step.
 */
class PatternSession(
    initial: PatternData,
    private val transport: Transport,
    scope: CoroutineScope,
    private val save: (PatternData) -> Unit,
) {
    private val _pattern = MutableStateFlow(initial)
    val pattern: StateFlow<PatternData> = _pattern.asStateFlow()

    private var firstBar = 0
    private val _window = MutableStateFlow(windowFor(initial))

    /** The bars in view. Opening a pattern shows bar 1. */
    val window: StateFlow<BarWindow> = _window.asStateFlow()

    /** Whether this pattern is the one playing. */
    val isPlaying: StateFlow<Boolean> = transport.playingUuid
        .map { it == initial.uuid }
        .stateIn(scope, SharingStarted.Eagerly, false)

    /** The step reaching the speaker while this pattern plays. */
    val playhead: StateFlow<Int?> = combine(transport.playingUuid, transport.currentStep) { uuid, step ->
        if (uuid == initial.uuid) step else null
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private var follow = Follow()
    private var stroke: StepPaintStroke? = null

    /** The last step the playhead reached while playing: what a bar dot compares against. */
    private var lastPlayhead: Int? = null

    /** The store's latest copy. Ignored mid-stroke, so a save arriving during a drag can't undo the drag. */
    fun refresh(stored: PatternData) {
        if (stroke != null) {
            return
        }
        _pattern.value = stored
        updateWindow()
    }

    /** Play starts from the first bar in view and turns follow on; Stop leaves the view where it is (spec §8). */
    fun togglePlay() {
        if (isPlaying.value) {
            transport.stop()
            lastPlayhead = null
        } else {
            follow = follow.played()
            transport.play(_pattern.value, _window.value.firstStep)
        }
    }

    /** A bar dot: shows that bar first; follow stays on only for the playing bar's dot (spec §8). */
    fun showBar(bar: Int) {
        val playingBar = if (isPlaying.value) lastPlayhead?.let { _window.value.bar(it) } else null
        follow = follow.tappedBar(bar, playingBar)
        firstBar = _window.value.startingAt(bar).firstBar
        updateWindow()
    }

    /** While following, the window moves to the playing bar when the playhead leaves it. */
    fun playheadMoved(step: Int) {
        lastPlayhead = step
        firstBar = follow.window(_window.value, step).firstBar
        updateWindow()
    }

    /** A tap toggles rest and hit; tapping an accent clears it. */
    fun tap(sound: Sound, step: Int) = edit(_pattern.value.steps.toggled(sound, step))

    /** A hold sets an accent. */
    fun accent(sound: Sound, step: Int) = edit(_pattern.value.steps.with(sound, step, Level.ACCENT))

    fun beginPaint(sound: Sound, step: Int) {
        follow = follow.edited()
        stroke = StepPaintStroke(_pattern.value.steps[sound, step])
    }

    fun paint(sound: Sound, step: Int) {
        val stroke = stroke ?: return
        val steps = _pattern.value.steps
        val painted = stroke.apply(sound, step, steps)
        if (painted !== steps) {
            commit(painted)
        }
    }

    fun endPaint() {
        stroke = null
    }

    private fun edit(steps: StepGrid) {
        follow = follow.edited()
        if (steps != _pattern.value.steps) {
            commit(steps)
        }
    }

    private fun commit(steps: StepGrid) {
        val edited = _pattern.value.copy(steps = steps)
        _pattern.value = edited
        save(edited)
        transport.update(edited)
    }

    private fun updateWindow() {
        _window.value = windowFor(_pattern.value)
    }

    private fun windowFor(pattern: PatternData) = BarWindow.of(pattern.meter, pattern.bars, firstBar)
}
