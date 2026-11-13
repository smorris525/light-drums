package com.stephenmorris.drums.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Process
import android.util.Log

/**
 * Streams the mixer to the speaker through one low-latency [AudioTrack] (spec §4), from a writer thread at audio
 * priority. Build it off the main thread: [create] renders the kit.
 */
class TrackAudioOutput private constructor(override val sampleRate: Int) : AudioOutput {
    private val renderer = VoiceRenderer(
        DrumKit.voices(sampleRate.toDouble()),
        gain = 1f,
        compressor = Compressor(sampleRate.toDouble()),
        limiter = Limiter(sampleRate = sampleRate.toDouble()),
    )
    private val timestamp = AudioTimestamp()
    private val clock = OutputClock()
    private var track: AudioTrack? = null
    private var writer: Thread? = null
    private var bufferFrames = BLOCK_FRAMES * 2

    @Volatile
    private var running = false
    private var loggedLatency = false

    override val renderedFrames: Long
        get() = renderer.renderedFrames

    override val latencyFrames: Long
        get() {
            val track = track ?: return bufferFrames.toLong()
            if (!running || !track.getTimestamp(timestamp)) {
                return bufferFrames.toLong()
            }
            val frames = clock.latency(
                renderer.renderedFrames,
                timestamp.framePosition,
                timestamp.nanoTime,
                System.nanoTime(),
                sampleRate,
            )
            if (!loggedLatency) {
                loggedLatency = true
                Log.d(TAG, "rate $sampleRate Hz, buffer $bufferFrames frames, latency ${frames * 1000 / sampleRate} ms")
            }
            return frames
        }

    override fun send(event: VoiceEvent): Boolean = renderer.send(event)

    override fun start(): Boolean {
        if (running && writer?.isAlive == true) {
            return true
        }
        // A writer that died on a write error (the audio server restarted, say) leaves a dead track: start afresh.
        tearDown()
        val built = track ?: build() ?: return false
        track = built
        return try {
            built.play()
            clock.trackStarted(renderer.renderedFrames)
            running = true
            loggedLatency = false
            writer = Thread(::writeLoop, "DrumsAudio").apply { start() }
            true
        } catch (exception: IllegalStateException) {
            Log.e(TAG, "AudioTrack wouldn't play", exception)
            false
        }
    }

    override fun suspend() = tearDown()

    private fun tearDown() {
        if (!running && writer == null && track == null) {
            return
        }
        running = false
        writer?.join()
        writer = null
        track?.let {
            Log.d(TAG, "suspended, underruns ${it.underrunCount}")
            it.stop()
            it.release()
        }
        track = null
    }

    private fun writeLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val block = FloatArray(BLOCK_FRAMES)
        val track = track ?: return
        while (running) {
            renderer.render(BLOCK_FRAMES, block)
            if (track.write(block, 0, BLOCK_FRAMES, AudioTrack.WRITE_BLOCKING) < 0) {
                Log.e(TAG, "AudioTrack write failed")
                return
            }
        }
    }

    private fun build(): AudioTrack? {
        val minimum = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        bufferFrames = maxOf(minimum / Float.SIZE_BYTES, BLOCK_FRAMES * 2)
        return try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .build(),
                )
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferFrames * Float.SIZE_BYTES)
                .build()
                .takeIf { it.state == AudioTrack.STATE_INITIALIZED }
        } catch (exception: Exception) {
            Log.e(TAG, "AudioTrack build failed", exception)
            null
        }
    }

    companion object {
        private const val TAG = "DrumsAudio"

        /** Frames rendered per write. */
        const val BLOCK_FRAMES = 192

        /** Renders the kit at the device's native rate. Slow: call off the main thread. */
        fun create(): TrackAudioOutput =
            TrackAudioOutput(AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC))
    }
}
