package com.stephenmorris.drums.data

import com.stephenmorris.drums.core.Level
import com.stephenmorris.drums.core.MeterId
import com.stephenmorris.drums.core.PatternData
import com.stephenmorris.drums.core.SamplePatterns
import com.stephenmorris.drums.core.Sound
import com.stephenmorris.drums.core.StepGrid
import com.stephenmorris.drums.core.Swing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PatternStoreTest {
    private val dao = FakePatternDao()
    private var seeded = false
    private val store = PatternStore(dao, { seeded }, { seeded = true }, { 5_000 })

    @Test
    fun seedsTheNineSamplesInOrder() = runBlocking {
        store.seedIfNeeded()
        assertEquals(SamplePatterns.all.map { it.name }, store.patterns.first().map { it.name })
        assertTrue(seeded)
    }

    // Deleting every pattern doesn't bring the samples back on the next launch.
    @Test
    fun seedsOnlyOnce() = runBlocking {
        store.seedIfNeeded()
        store.patterns.first().forEach { store.delete(it.uuid) }
        store.seedIfNeeded()
        assertTrue(store.patterns.first().isEmpty())
    }

    @Test
    fun doesNotSeedOverExistingPatterns() = runBlocking {
        dao.upsert(PatternEntity.from(PatternData(uuid = "mine", createdAt = 1)))
        store.seedIfNeeded()
        assertEquals(listOf("mine"), store.patterns.first().map { it.uuid })
        assertTrue(seeded)
    }

    @Test
    fun createNamesAndOrdersTheNewPattern() = runBlocking {
        store.seedIfNeeded()
        val created = store.create()
        val all = store.patterns.first()
        assertEquals(created.uuid, all.last().uuid)
        assertEquals("New Pattern", created.name)
        assertEquals(100, created.bpm)
        assertEquals(MeterId.FOUR_FOUR, created.meter)
        assertEquals(1, created.bars)
        assertTrue(created.steps.isEmpty)
        assertEquals("New Pattern 2", store.create().name)
    }

    @Test
    fun roundTripsEveryField() = runBlocking {
        val pattern = PatternData(
            uuid = "u",
            name = "Odd",
            createdAt = 7,
            bpm = 133,
            meter = MeterId.SEVEN_EIGHT,
            bars = 3,
            swing = Swing.SIXTEENTH_HEAVY,
            steps = StepGrid.EMPTY.with(Sound.RIDE, 5, Level.ACCENT),
        )
        store.save(pattern)
        assertEquals(pattern.copy(editedAt = 5_000), store.pattern("u").first())
    }

    @Test
    fun corruptRowsReadSafely() = runBlocking {
        dao.upsert(PatternEntity("x", "Bad", 1, 0, 120, "9/8", "x", 2, "nope"))
        val read = store.pattern("x").first()!!
        assertEquals(MeterId.FOUR_FOUR, read.meter)
        assertEquals(Swing.OFF, read.swing)
        assertTrue(read.steps.isEmpty)
    }

    @Test
    fun duplicateCopiesUnderTheNextName() = runBlocking {
        store.seedIfNeeded()
        val waltz = store.patterns.first().first { it.name == "Waltz" }
        val copy = store.duplicate(waltz.uuid)!!
        assertEquals("Waltz 2", copy.name)
        assertEquals(waltz.steps, copy.steps)
        assertNotEquals(waltz.uuid, copy.uuid)
        assertEquals(copy.uuid, store.patterns.first().last().uuid)
    }
}
