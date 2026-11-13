package com.stephenmorris.drums.audio

import com.stephenmorris.drums.core.CountInSchedule
import com.stephenmorris.drums.core.ClickMode
import com.stephenmorris.drums.core.HitPlacement
import com.stephenmorris.drums.core.Level
import com.stephenmorris.drums.core.MeterLayout
import com.stephenmorris.drums.core.PatternData
import com.stephenmorris.drums.core.PatternPlayback
import com.stephenmorris.drums.core.PlaybackScheduler
import com.stephenmorris.drums.core.PlayheadHistory
import com.stephenmorris.drums.core.RecordControl
import com.stephenmorris.drums.core.Sound
import com.stephenmorris.drums.core.StepGrid
import com.stephenmorris.drums.core.SuppressedHits
import com.stephenmorris.drums.core.Take
import com.stephenmorris.drums.core.TakePhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The tool's single playback transport (spec §4, §10). At most one pattern plays at a time. While playing, [tick]
 * schedules steps [LOOKAHEAD] seconds ahead of the audio clock from the latest copy of the pattern, so edits sent
 * through [update] apply from the next unscheduled step. [currentStep] is the step reaching the speaker.
 *
 * It also runs takes: a one-bar count-in, the click per [clickMode], and pad hits recorded on the nearest step, each
 * reported through [onRecorded]. Main thread only.
 */
class Transport(
    private val output: AudioOutput,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val onRecorded: (uuid: String, steps: StepGrid) -> Unit,
) {
    private val _playingUuid = MutableStateFlow<String?>(null)
    private val _currentStep = MutableStateFlow<Int?>(null)
    private val _takePhase = MutableStateFlow<TakePhase?>(null)
    private val _countInNumber = MutableStateFlow<Int?>(null)

    /** The pattern playing, or `null` when stopped. */
    val playingUuid: StateFlow<String?> = _playingUuid.asStateFlow()

    /** The step sounding now, or `null` when stopped or before the first step reaches the speaker. */
    val currentStep: StateFlow<Int?> = _currentStep.asStateFlow()

    /** Where a take is, or `null` without one. */
    val takePhase: StateFlow<TakePhase?> = _takePhase.asStateFlow()

    /** The count reaching the speaker during a count-in. */
    val countInNumber: StateFlow<Int?> = _countInNumber.asStateFlow()

    /** When the pattern's beats click (spec §10). Set from the stored preference. */
    var clickMode = ClickMode.REC

    /** Whether the Pads screen is showing, the only place the beats click. */
    var isOnPads = false

    private val sampleRate = output.sampleRate.toDouble()
    private var pattern: PatternData? = null
    private var playback: PatternPlayback? = null
    private var scheduler: PlaybackScheduler? = null
    private val history = PlayheadHistory()
    private val suppressed = SuppressedHits()
    private var countIn: CountInSchedule? = null

    /** Where playback starts, on the render clock: after the count-in, or at once. */
    private var playbackStart = HitPlacement.Upcoming(0, 0)
    private var outputStarted = false
    private var lastSoundMillis = 0L

    /** Bumped by every start and stop, so a start overtaken by a stop or another start does nothing. */
    private var startGeneration = 0

    /** Plays [pattern] from [fromStep], the first step in view (spec §8). Nothing happens if audio can't start. */
    fun play(pattern: PatternData, fromStep: Int) = start(pattern, fromStep, countsIn = false, recording = false)

    /**
     * The Record button (spec §10): counts in and records from stopped, records at once while playing, cancels a
     * count-in, or stops recording and keeps playing.
     */
    fun record(pattern: PatternData, fromStep: Int) {
        when (RecordControl.tapped(_takePhase.value, _playingUuid.value == pattern.uuid)) {
            RecordControl.Action.COUNT_IN -> start(pattern, fromStep, countsIn = true, recording = true)
            RecordControl.Action.RECORD_NOW -> _takePhase.value = TakePhase.RECORDING
            RecordControl.Action.CANCEL_COUNT_IN -> stop()
            RecordControl.Action.STOP_RECORDING -> endTake()
        }
    }

    /**
     * Plays a pad at once and, during a take of [pattern], records [level] on the nearest step. [pattern] is `null`
     * where nothing can be recorded.
     */
    fun hit(sound: Sound, level: Level, pattern: PatternData?) {
        if (!ensureStarted()) {
            return
        }
        val now = output.renderedFrames
        output.send(VoiceEvent.Trigger(DrumKit.voiceIndex(sound), now, if (level == Level.ACCENT) level.gain else PAD_GAIN))
        lastSoundMillis = nowMillis()
        val current = this.pattern
        val phase = _takePhase.value
        if (pattern == null || current == null || phase == null || current.uuid != pattern.uuid) {
            return
        }
        val layout = current.meter.layout
        // Quantize to what the person heard: the playhead at the speaker, not at the render clock. During the count-in
        // the first step may not be scheduled yet (slow tempos); an early downbeat still lands on it.
        val placement = history.placement(
            now - output.latencyFrames,
            layout.stepDuration(current.bpm) * sampleRate,
            layout.stepsPerBar * MeterLayout.clampedBars(current.bars),
            if (phase == TakePhase.COUNTING_IN) playbackStart else null,
        ) ?: return
        val (steps, recorded) = Take.record(sound, level, placement, current.steps)
        // The tap already sounded it: the step's own playback of this sound is skipped once (no doubles), even when
        // the step already had the sound.
        if (recorded.suppressesStep) {
            suppressed.insert(placement.step, sound)
        }
        if (!recorded.changed) {
            return
        }
        replacePattern(current.copy(steps = steps))
        onRecorded(current.uuid, steps)
    }

    /** The latest copy of [pattern] after an edit. Applies from the next unscheduled step if it's playing. */
    fun update(pattern: PatternData) {
        if (pattern.uuid == _playingUuid.value) {
            replacePattern(pattern)
        }
    }

    /** Stops playback if the deleted pattern is playing. */
    fun patternDeleted(uuid: String) {
        if (uuid == _playingUuid.value) {
            stop()
        }
    }

    /** Ends a take and keeps playing. */
    fun endTake() {
        _takePhase.value = null
        _countInNumber.value = null
    }

    /** Stops playback, ending any take (its hits stay). */
    fun stop() {
        startGeneration += 1
        endTake()
        countIn = null
        if (_playingUuid.value == null) {
            return
        }
        output.send(VoiceEvent.StopAll)
        scheduler = null
        history.reset()
        suppressed.removeAll()
        pattern = null
        playback = null
        _playingUuid.value = null
        _currentStep.value = null
        lastSoundMillis = nowMillis()
    }

    /** Stops playback and releases audio: for when the tool is paused or left. */
    fun suspend() {
        stop()
        releaseOutput()
    }

    /**
     * Schedules ahead while playing, and releases audio after [RELEASE_DELAY_MS] of silence while stopped. Call every
     * 10 ms while playing or counting in, and about once a second otherwise while audio is started.
     */
    fun tick() {
        val playback = playback
        val scheduler = scheduler
        if (playback == null || scheduler == null) {
            if (outputStarted && nowMillis() - lastSoundMillis >= RELEASE_DELAY_MS) {
                releaseOutput()
            }
            return
        }
        lastSoundMillis = nowMillis()
        val now = output.renderedFrames
        val horizon = now + (LOOKAHEAD * sampleRate).toLong()
        val steps = scheduler.advance(horizon, now, playback)
        val clicks = clickMode.clicks(isOnPads)
        for (step in steps) {
            val sounds = suppressed.soundsToPlay(step.step, step.triggers.map { it.sound })
            for (trigger in step.triggers) {
                if (trigger.sound in sounds) {
                    output.send(VoiceEvent.Trigger(DrumKit.voiceIndex(trigger.sound), step.frame, trigger.gain))
                }
            }
            val click = step.click
            if (clicks && click != null) {
                output.send(VoiceEvent.Trigger(DrumKit.clickVoice(click), step.frame))
            }
        }
        history.record(steps)
        val audible = now - output.latencyFrames
        _currentStep.value = history.step(audible)
        updateCountIn(audible)
    }

    private fun start(pattern: PatternData, fromStep: Int, countsIn: Boolean, recording: Boolean) {
        stop()
        val generation = startGeneration
        if (!ensureStarted() || generation != startGeneration) {
            return
        }
        var start = output.renderedFrames + (START_DELAY * sampleRate).toLong()
        if (countsIn) {
            val layout = pattern.meter.layout
            val schedule = CountInSchedule(layout, start, layout.stepDuration(pattern.bpm) * sampleRate)
            // Count-ins always click, whatever Click says (spec §10).
            for (click in schedule.clicks) {
                output.send(VoiceEvent.Trigger(DrumKit.clickVoice(click.kind), click.frame))
            }
            countIn = schedule
            start = schedule.endFrame
        }
        playbackStart = HitPlacement.Upcoming(fromStep, start)
        scheduler = PlaybackScheduler(sampleRate, fromStep, start)
        replacePattern(pattern)
        _playingUuid.value = pattern.uuid
        if (recording) {
            _takePhase.value = if (countsIn) TakePhase.COUNTING_IN else TakePhase.RECORDING
        }
        tick()
    }

    /** Keeps [pattern] and builds its playback: the only place a [PatternPlayback] is built. */
    private fun replacePattern(pattern: PatternData) {
        this.pattern = pattern
        playback = PatternPlayback(pattern.bpm, pattern.meter, pattern.bars, pattern.swing, pattern.steps)
    }

    /** Shows the count reaching the speaker, and turns the count-in into recording when the pattern starts. */
    private fun updateCountIn(frame: Long) {
        val schedule = countIn
        if (_takePhase.value != TakePhase.COUNTING_IN || schedule == null) {
            return
        }
        if (frame >= schedule.endFrame) {
            _takePhase.value = TakePhase.RECORDING
            _countInNumber.value = null
            countIn = null
            return
        }
        _countInNumber.value = schedule.number(frame)
    }

    private fun ensureStarted(): Boolean {
        if (!outputStarted) {
            outputStarted = output.start()
        }
        return outputStarted
    }

    private fun releaseOutput() {
        if (outputStarted) {
            output.suspend()
            outputStarted = false
        }
    }

    companion object {
        /** How far ahead of the audio clock steps are scheduled, in seconds. */
        const val LOOKAHEAD = 0.12

        /** The delay before the first step, so it isn't squeezed into a render already under way. */
        const val START_DELAY = 0.02

        /** How long audio stays ready after the last sound before it's released. */
        const val RELEASE_DELAY_MS = 30_000L

        /** A pad tap's level (spec §4). */
        const val PAD_GAIN = 0.85f
    }
}
