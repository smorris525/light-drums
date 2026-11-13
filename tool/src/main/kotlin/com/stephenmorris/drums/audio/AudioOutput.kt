package com.stephenmorris.drums.audio

/** Where the mixer's sound goes, and its clock. The transport only talks to this, so tests can fake it. */
interface AudioOutput {
    val sampleRate: Int

    /** Frames rendered so far: the clock that trigger frames refer to. It never goes back, across suspends too. */
    val renderedFrames: Long

    /** Frames between the render clock and the speaker, for placing the playhead and recorded hits. */
    val latencyFrames: Long

    /** Main thread only. `false` if the event was dropped. */
    fun send(event: VoiceEvent): Boolean

    /** Starts sound if it isn't already going. Idempotent; `false` if output can't start. */
    fun start(): Boolean

    /** Stops sound and releases the output. Idempotent. */
    fun suspend()
}

/** How far the speaker is behind what's been rendered. */
object OutputLatency {
    /**
     * Frames written but not yet heard: [written] less the frames played, extrapolated from an output timestamp
     * ([timestampFrame] heard at [timestampNanos]) to [nowNanos]. Never negative.
     */
    fun frames(written: Long, timestampFrame: Long, timestampNanos: Long, nowNanos: Long, sampleRate: Int): Long {
        val played = timestampFrame + (nowNanos - timestampNanos) * sampleRate / 1_000_000_000L
        return maxOf(0L, written - played)
    }
}

/**
 * Latency against one output track. A new track's position starts at 0 while the render clock keeps counting across
 * restarts, so only frames rendered since [trackStarted] count as written to it.
 */
class OutputClock {
    @Volatile
    private var trackStartFrame = 0L

    /** Call as a new track starts, before its writer renders anything. */
    fun trackStarted(renderedFrames: Long) {
        trackStartFrame = renderedFrames
    }

    fun latency(renderedFrames: Long, timestampFrame: Long, timestampNanos: Long, nowNanos: Long, sampleRate: Int): Long =
        OutputLatency.frames(renderedFrames - trackStartFrame, timestampFrame, timestampNanos, nowNanos, sampleRate)
}
