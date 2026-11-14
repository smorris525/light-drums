package com.stephenmorris.drums.audio

import com.stephenmorris.drums.core.ClickKind
import com.stephenmorris.drums.core.SamplePatterns
import com.stephenmorris.drums.core.Sound
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test

/**
 * Writes each sound and both clicks to 16-bit mono 48 kHz WAVs in `tool/build/kit-export/`, at 0.8
 * output gain, to judge by ear. Runs only when EXPORT_DRUMS_KIT is set:
 * `EXPORT_DRUMS_KIT=1 ./gradlew :tool:testDebugUnitTest --tests '*KitExportTest' --rerun-tasks`
 */
class KitExportTest {
    @Test
    fun exportsTheKit() {
        if (System.getenv("EXPORT_DRUMS_KIT") == null) {
            return
        }
        val directory = File("build/kit-export").apply { mkdirs() }
        for (sound in Sound.entries) {
            File(directory, "${sound.raw}.wav").writeBytes(wav(ElectronicKit.samples(sound, SAMPLE_RATE)))
        }
        for (kind in ClickKind.entries) {
            File(directory, "click-${kind.name.lowercase()}.wav").writeBytes(wav(ClickSound.samples(kind, SAMPLE_RATE)))
        }
    }

    /**
     * Slow Rock's first 8 seconds through the app's whole sound path (transport, mixer, levelling, compressor and
     * limiter, at full output gain), skipping only the AudioTrack: what the app sends to the speaker.
     */
    @Test
    fun exportsSlowRockThroughTheMix() {
        if (System.getenv("EXPORT_DRUMS_KIT") == null) {
            return
        }
        val renderer = VoiceRenderer(
            DrumKit.voices(SAMPLE_RATE),
            gain = 1f,
            compressor = Compressor(SAMPLE_RATE),
            limiter = Limiter(sampleRate = SAMPLE_RATE),
        )
        val output = object : AudioOutput {
            override val sampleRate = SAMPLE_RATE.toInt()
            override val renderedFrames get() = renderer.renderedFrames
            override val latencyFrames = 0L
            override fun send(event: VoiceEvent) = renderer.send(event)
            override fun start() = true
            override fun suspend() = Unit
        }
        val transport = Transport(output) {}
        transport.play(SamplePatterns.seeded(0)[0], 0)
        val mix = FloatArray(8 * SAMPLE_RATE.toInt())
        val block = FloatArray(480)
        var written = 0
        while (written < mix.size) {
            transport.tick()
            renderer.render(block.size, block)
            block.copyInto(mix, written, 0, minOf(block.size, mix.size - written))
            written += block.size
        }
        File(File("build/kit-export").apply { mkdirs() }, "funk-break-mix.wav").writeBytes(wav(mix, gain = 1f))
    }

    private fun wav(samples: FloatArray, gain: Float = 0.8f): ByteArray {
        val dataBytes = samples.size * 2
        val buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + dataBytes).put("WAVEfmt ".toByteArray())
        buffer.putInt(16).putShort(1).putShort(1).putInt(SAMPLE_RATE.toInt()).putInt(SAMPLE_RATE.toInt() * 2)
        buffer.putShort(2).putShort(16).put("data".toByteArray()).putInt(dataBytes)
        for (sample in samples) {
            buffer.putShort(((sample * gain).coerceIn(-1f, 1f) * 32_767).toInt().toShort())
        }
        return buffer.array()
    }

    private companion object {
        const val SAMPLE_RATE = 48_000.0
    }
}
