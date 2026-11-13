package com.stephenmorris.drums.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SamplePatternsTest {
    private val all = SamplePatterns.all

    @Test
    fun nineSamplesInOrder() {
        assertEquals(
            listOf(
                "Slow Rock", "Shuffle in G", "Bossa Practice", "Waltz", "New Tune in 7",
                "Half-Time Ballad", "Funk Practice", "Five", "Campfire 6/8",
            ),
            all.map { it.name },
        )
    }

    @Test
    fun settings() {
        assertEquals(listOf(68, 92, 120, 144, 220, 62, 98, 132, 54), all.map { it.bpm })
        assertEquals(
            listOf(
                MeterId.FOUR_FOUR, MeterId.TWELVE_EIGHT, MeterId.FOUR_FOUR, MeterId.THREE_FOUR, MeterId.SEVEN_EIGHT,
                MeterId.FOUR_FOUR, MeterId.FOUR_FOUR, MeterId.FIVE_FOUR, MeterId.SIX_EIGHT,
            ),
            all.map { it.meter },
        )
        assertEquals(listOf(4, 1, 2, 1, 2, 2, 1, 1, 2), all.map { it.bars })
        assertEquals(
            List(6) { Swing.OFF } + Swing.SIXTEENTH_LIGHT + List(2) { Swing.OFF },
            all.map { it.swing },
        )
    }

    @Test
    fun everyHitIsInsideItsPattern() {
        for (seed in all) {
            val length = seed.meter.layout.stepsPerBar * seed.bars
            assertEquals(seed.steps, seed.steps.trimmed(length), seed.name)
            assertFalse(seed.steps.isEmpty, seed.name)
        }
    }

    // One bar of 4/4: kick on 1 (accent) and 3, snare on 2 and 4, hat on the eighths, shaker on the off-16ths.
    @Test
    fun defaultGrooveInFourFour() {
        val grid = SamplePatterns.defaultGroove(MeterId.FOUR_FOUR, 1)
        assertEquals(mapOf(0 to Level.ACCENT, 8 to Level.HIT), grid.hits(Sound.KICK))
        assertEquals(mapOf(4 to Level.ACCENT, 12 to Level.ACCENT), grid.hits(Sound.SNARE))
        assertEquals(
            mapOf(0 to Level.ACCENT) + listOf(2, 4, 6, 8, 10, 12, 14).associateWith { Level.HIT },
            grid.hits(Sound.HAT),
        )
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15), grid.hits(Sound.SHAKER).keys.sorted())
        assertTrue(grid.hits(Sound.TOM).isEmpty())
    }

    // Kick is an accent only on bar 1; the last beat of a multi-bar groove becomes a tom fill.
    @Test
    fun defaultGrooveFillsTheLastBeat() {
        val grid = SamplePatterns.defaultGroove(MeterId.FOUR_FOUR, 2)
        assertEquals(Level.HIT, grid[Sound.KICK, 16])
        assertEquals(
            mapOf(28 to Level.ACCENT, 29 to Level.HIT, 30 to Level.HIT, 31 to Level.HIT),
            grid.hits(Sound.TOM),
        )
        assertTrue((28..31).all { grid[Sound.HAT, it] == null && grid[Sound.SHAKER, it] == null && grid[Sound.SNARE, it] == null })
        assertEquals(Level.ACCENT, grid[Sound.SNARE, 20])
    }

    // Three beats: kick on 1 and 3. Eighth-based meters have no shaker: every step is a hat.
    @Test
    fun defaultGrooveInSevenEight() {
        val grid = SamplePatterns.defaultGroove(MeterId.SEVEN_EIGHT, 1)
        assertEquals(mapOf(0 to Level.ACCENT, 4 to Level.HIT), grid.hits(Sound.KICK))
        assertEquals(mapOf(2 to Level.ACCENT), grid.hits(Sound.SNARE))
        assertEquals(7, grid.hits(Sound.HAT).size)
        assertTrue(grid.hits(Sound.SHAKER).isEmpty())
    }

    // Five beats: kick on 1 and on beat floor(5/2) + 1 = 3.
    @Test
    fun defaultGrooveInFiveFour() {
        val grid = SamplePatterns.defaultGroove(MeterId.FIVE_FOUR, 1)
        assertEquals(mapOf(0 to Level.ACCENT, 8 to Level.HIT), grid.hits(Sound.KICK))
        assertEquals(mapOf(4 to Level.ACCENT, 12 to Level.ACCENT), grid.hits(Sound.SNARE))
    }

    @Test
    fun namedGrids() {
        assertEquals(
            mapOf(0 to Level.ACCENT) + listOf(2, 3, 5, 6, 8, 9, 11).associateWith { Level.HIT },
            all[1].steps.hits(Sound.RIDE),
        )
        assertEquals(listOf(0, 6, 12, 20, 26), all[2].steps.hits(Sound.SIDESTICK).keys.sorted())
        assertEquals(listOf(0, 2, 4, 6, 8, 10), all[3].steps.hits(Sound.HAT).keys.sorted())
        val ballad = all[5].steps
        assertNull(ballad[Sound.HAT, 30])
        assertEquals(Level.HIT, ballad[Sound.OPEN_HAT, 30])
        val funk = all[6].steps
        assertEquals(Level.ACCENT, funk[Sound.HAT, 0])
        assertEquals(Level.HIT, funk[Sound.HAT, 1])
        assertNull(funk[Sound.HAT, 14])
        assertEquals(Level.HIT, funk[Sound.OPEN_HAT, 14])
        assertEquals(
            mapOf(4 to Level.ACCENT, 7 to Level.HIT, 12 to Level.ACCENT, 15 to Level.HIT),
            funk.hits(Sound.SNARE),
        )
    }

    @Test
    fun everySampleHasItsOwnId() {
        assertEquals(all.size, all.map { it.uuid }.toSet().size)
    }

    @Test
    fun idsAreFixed() {
        assertEquals("B9D09209-B157-4B2E-B311-859959C0CC2C", all.first().uuid)
        assertEquals("D7C0F875-01F2-4590-ACF9-2DAD518012A4", all.last().uuid)
    }

    @Test
    fun seededPatternsAreInOrderAndUnedited() {
        val seeded = SamplePatterns.seeded(1000)
        assertEquals((1000L..1008L).toList(), seeded.map { it.createdAt })
        assertTrue(seeded.all { it.editedAt == 0L })
        assertEquals(all.map { it.name }, seeded.map { it.name })
        assertEquals(all.map { it.uuid }, seeded.map { it.uuid })
        assertEquals(all[2].steps, seeded[2].steps)
    }
}
