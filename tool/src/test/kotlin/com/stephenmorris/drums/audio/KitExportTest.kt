package com.stephenmorris.drums.audio

import com.stephenmorris.drums.core.ClickKind
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

    private fun wav(samples: FloatArray): ByteArray {
        val dataBytes = samples.size * 2
        val buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + dataBytes).put("WAVEfmt ".toByteArray())
        buffer.putInt(16).putShort(1).putShort(1).putInt(SAMPLE_RATE.toInt()).putInt(SAMPLE_RATE.toInt() * 2)
        buffer.putShort(2).putShort(16).put("data".toByteArray()).putInt(dataBytes)
        for (sample in samples) {
            buffer.putShort(((sample * 0.8f).coerceIn(-1f, 1f) * 32_767).toInt().toShort())
        }
        return buffer.array()
    }

    private companion object {
        const val SAMPLE_RATE = 48_000.0
    }
}
