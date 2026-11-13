package com.stephenmorris.drums.audio

/** An [AudioOutput] whose clock and latency the test sets, recording every event sent. */
class FakeAudioOutput(override val sampleRate: Int = 48_000) : AudioOutput {
    override var renderedFrames = 0L
    override var latencyFrames = 0L
    var startResult = true
    var isStarted = false
    var suspendCount = 0
    val events = mutableListOf<VoiceEvent>()

    val triggers: List<VoiceEvent.Trigger>
        get() = events.filterIsInstance<VoiceEvent.Trigger>()

    override fun send(event: VoiceEvent): Boolean {
        events += event
        return true
    }

    override fun start(): Boolean {
        isStarted = startResult
        return startResult
    }

    override fun suspend() {
        isStarted = false
        suspendCount += 1
    }
}
