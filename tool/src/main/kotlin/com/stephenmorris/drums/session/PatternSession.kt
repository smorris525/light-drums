package com.stephenmorris.drums.session

import com.stephenmorris.drums.audio.Transport
import com.stephenmorris.drums.core.BarWindow
import com.stephenmorris.drums.core.Follow
import com.stephenmorris.drums.core.Level
import com.stephenmorris.drums.core.MeterId
import com.stephenmorris.drums.core.MeterLayout
import com.stephenmorris.drums.core.PatternText
import com.stephenmorris.drums.core.SettingsSnapshot
import com.stephenmorris.drums.core.Swing
import com.stephenmorris.drums.core.Tempo
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
    /** The newest copy saved, from any source (a recorded hit, say): every edit builds on it, if there is one. */
    private val latest: () -> PatternData? = { null },
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

    /** Taken when Settings opens; `null` while it's closed. Meter and bar changes come from it (spec §5). */
    private var settings: SettingsSnapshot? = null

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
    fun tap(sound: Sound, step: Int) = edit(base().steps.toggled(sound, step))

    /** A hold sets an accent. */
    fun accent(sound: Sound, step: Int) = edit(base().steps.with(sound, step, Level.ACCENT))

    fun beginPaint(sound: Sound, step: Int) {
        follow = follow.edited()
        stroke = StepPaintStroke(base().steps[sound, step])
    }

    fun paint(sound: Sound, step: Int) {
        val stroke = stroke ?: return
        val steps = base().steps
        val painted = stroke.apply(sound, step, steps)
        if (painted !== steps) {
            commit(base().copy(steps = painted))
        }
    }

    fun endPaint() {
        stroke = null
    }

    /** Opens the settings session: meter and bar changes are computed from this moment until [closeSettings]. */
    fun openSettings() {
        val pattern = base()
        settings = SettingsSnapshot(pattern.meter, pattern.bars, pattern.steps)
    }

    /** Commits the settings session: hits beyond the pattern's length are trimmed (spec §5). */
    fun closeSettings() {
        val pattern = base()
        val length = pattern.meter.layout.stepsPerBar * MeterLayout.clampedBars(pattern.bars)
        val trimmed = pattern.steps.trimmed(length)
        if (trimmed != pattern.steps) {
            commit(pattern.copy(steps = trimmed))
        }
        settings = null
    }

    fun setBpm(bpm: Int) {
        val pattern = base()
        val clamped = Tempo.clamped(bpm)
        if (clamped != pattern.bpm) commit(pattern.copy(bpm = clamped))
    }

    fun setSwing(swing: Swing) {
        val pattern = base()
        if (swing != pattern.swing) commit(pattern.copy(swing = swing))
    }

    fun setMeter(meter: MeterId) {
        val pattern = base()
        if (meter != pattern.meter) reshape(pattern, meter, pattern.bars)
    }

    fun setBars(bars: Int) {
        val pattern = base()
        val clamped = MeterLayout.clampedBars(bars)
        if (clamped != pattern.bars) reshape(pattern, pattern.meter, clamped)
    }

    /** A rename (spec §11): trimmed; an empty name keeps the old one. */
    fun rename(typed: String) {
        val pattern = base()
        val name = PatternText.renamed(typed, pattern.name)
        if (name != pattern.name) commit(pattern.copy(name = name))
    }

    /** The steps for the meter and bars now chosen, from the settings snapshot, opening one if none is open. */
    private fun reshape(pattern: PatternData, meter: MeterId, bars: Int) {
        val snapshot = settings ?: SettingsSnapshot(pattern.meter, pattern.bars, pattern.steps).also { settings = it }
        commit(pattern.copy(meter = meter, bars = bars, steps = snapshot.steps(meter, bars)))
    }

    /** The newest copy to edit: the newest saved, if the saver has one, otherwise this session's. */
    private fun base(): PatternData = latest() ?: _pattern.value

    private fun edit(steps: StepGrid) {
        follow = follow.edited()
        val pattern = base()
        if (steps != pattern.steps) {
            commit(pattern.copy(steps = steps))
        }
    }

    private fun commit(edited: PatternData) {
        _pattern.value = edited
        save(edited)
        transport.update(edited)
        updateWindow()
    }

    private fun updateWindow() {
        _window.value = windowFor(_pattern.value)
        firstBar = _window.value.firstBar
    }

    private fun windowFor(pattern: PatternData) = BarWindow.of(pattern.meter, pattern.bars, firstBar)
}
