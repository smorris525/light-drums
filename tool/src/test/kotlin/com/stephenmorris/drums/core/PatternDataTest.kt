package com.stephenmorris.drums.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PatternDataTest {
    @Test
    fun newPatternsHaveTheSpecDefaults() {
        val pattern = PatternData(uuid = "u", createdAt = 1)
        assertEquals("New Pattern", pattern.name)
        assertEquals(100, pattern.bpm)
        assertEquals(MeterId.FOUR_FOUR, pattern.meter)
        assertEquals(1, pattern.bars)
        assertEquals(Swing.OFF, pattern.swing)
        assertTrue(pattern.steps.isEmpty)
        assertEquals(0L, pattern.editedAt)
    }

    @Test
    fun libraryMeta() {
        assertEquals("68 BPM · 4/4 · 4 bars", SamplePatterns.seeded(0)[0].libraryMeta)
    }
}
