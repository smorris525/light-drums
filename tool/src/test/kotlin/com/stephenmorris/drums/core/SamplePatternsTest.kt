package com.stephenmorris.drums.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SamplePatternsTest {
    private val all = SamplePatterns.all

    @Test
    fun samplesInOrder() {
        assertEquals(
            listOf("Funk Break", "House", "Soul", "Math Rock", "Balkan Dance"),
            all.map { it.name },
        )
    }

    @Test
    fun settings() {
        assertEquals(listOf(96, 124, 80, 112, 260), all.map { it.bpm })
        assertEquals(
            listOf(MeterId.FOUR_FOUR, MeterId.FOUR_FOUR, MeterId.SIX_EIGHT, MeterId.FIVE_FOUR, MeterId.SEVEN_EIGHT),
            all.map { it.meter },
        )
        assertEquals(listOf(2, 1, 4, 2, 2), all.map { it.bars })
        assertEquals(
            listOf(Swing.SIXTEENTH_LIGHT, Swing.SIXTEENTH_LIGHT, Swing.OFF, Swing.OFF, Swing.OFF),
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

    @Test
    fun noStepHasBothHats() {
        for (seed in all) {
            val shared = seed.steps.hits(Sound.HAT).keys intersect seed.steps.hits(Sound.OPEN_HAT).keys
            assertTrue(shared.isEmpty(), "${seed.name}: $shared")
        }
    }

    @Test
    fun samplesUseEverySound() {
        val used = all.flatMap { seed -> Sound.entries.filter { seed.steps.hits(it).isNotEmpty() } }.toSet()
        assertEquals(Sound.entries.toSet(), used)
    }

    // Spot checks against the grids in SamplePatterns.
    @Test
    fun namedGrids() {
        val funk = all[0].steps
        assertEquals(
            mapOf(4 to Level.ACCENT, 7 to Level.HIT, 9 to Level.HIT, 12 to Level.ACCENT, 15 to Level.HIT) +
                mapOf(20 to Level.ACCENT, 23 to Level.HIT, 25 to Level.HIT, 28 to Level.ACCENT) +
                mapOf(30 to Level.HIT, 31 to Level.HIT),
            funk.hits(Sound.SNARE),
        )
        assertEquals(mapOf(14 to Level.HIT, 30 to Level.HIT), funk.hits(Sound.OPEN_HAT))
        assertTrue(funk.hits(Sound.TOM).isEmpty())

        val house = all[1].steps
        assertEquals(mapOf(0 to Level.ACCENT, 4 to Level.HIT, 8 to Level.HIT, 12 to Level.HIT), house.hits(Sound.KICK))
        assertEquals(setOf(2, 6, 10, 14), house.hits(Sound.OPEN_HAT).keys)
        assertEquals((1 until 16 step 2).toSet(), house.hits(Sound.HAT).keys)
        assertEquals(listOf(7, 10, 14), house.hits(Sound.SIDESTICK).keys.sorted())
        assertEquals(listOf(9, 12, 14), house.hits(Sound.TOM).keys.sorted())

        val soul = all[2].steps
        assertEquals(
            mapOf(0 to Level.ACCENT, 5 to Level.HIT, 6 to Level.ACCENT, 11 to Level.HIT, 12 to Level.ACCENT) +
                mapOf(17 to Level.HIT, 18 to Level.ACCENT, 20 to Level.HIT, 23 to Level.HIT),
            soul.hits(Sound.KICK),
        )
        assertEquals((1 until 24 step 3).toList(), soul.hits(Sound.SHAKER).keys.sorted())
        assertEquals(mapOf(23 to Level.HIT), soul.hits(Sound.OPEN_HAT))

        val rock = all[3].steps
        assertEquals(
            mapOf(36 to Level.ACCENT, 37 to Level.HIT, 38 to Level.HIT, 39 to Level.HIT),
            rock.hits(Sound.TOM),
        )
        assertEquals(setOf(0, 12, 20, 32), rock.hits(Sound.HAT).filterValues { it == Level.ACCENT }.keys)
        assertEquals(mapOf(0 to Level.ACCENT), rock.hits(Sound.RIDE))

        val balkan = all[4].steps
        assertEquals(setOf(0, 2, 4, 7, 9), balkan.hits(Sound.HAT).filterValues { it == Level.ACCENT }.keys)
        assertEquals(mapOf(2 to Level.ACCENT, 6 to Level.HIT, 9 to Level.ACCENT), balkan.hits(Sound.SNARE))
        assertEquals(mapOf(0 to Level.ACCENT, 4 to Level.HIT, 7 to Level.ACCENT, 11 to Level.HIT), balkan.hits(Sound.KICK))
        assertEquals(mapOf(11 to Level.ACCENT, 12 to Level.HIT, 13 to Level.HIT), balkan.hits(Sound.TOM))
        assertTrue(balkan.hits(Sound.RIDE).isEmpty())
    }

    @Test
    fun everySampleHasItsOwnId() {
        assertEquals(all.size, all.map { it.uuid }.toSet().size)
    }

    @Test
    fun idsAreFixed() {
        assertEquals("1195CA2F-3355-46EA-88B5-488AAC1C7C47", all.first().uuid)
        assertEquals("84A63DE6-A788-4D21-8A2B-28BD5B631604", all.last().uuid)
    }

    @Test
    fun seededPatternsAreInOrderAndUnedited() {
        val seeded = SamplePatterns.seeded(1000)
        assertEquals((1000L..1004L).toList(), seeded.map { it.createdAt })
        assertTrue(seeded.all { it.editedAt == 0L })
        assertEquals(all.map { it.name }, seeded.map { it.name })
        assertEquals(all.map { it.uuid }, seeded.map { it.uuid })
        assertEquals(all[2].steps, seeded[2].steps)
    }
}
