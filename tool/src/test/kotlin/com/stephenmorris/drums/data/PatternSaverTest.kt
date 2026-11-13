package com.stephenmorris.drums.data

import com.stephenmorris.drums.core.Level
import com.stephenmorris.drums.core.PatternData
import com.stephenmorris.drums.core.Sound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PatternSaverTest {
    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
    private val dao = FakePatternDao()
    private val store = PatternStore(dao, { true }, {}, { 9_000 })
    private val saver = PatternSaver(store, scope)
    private val base = PatternData(uuid = "p", createdAt = 1)
    private val first = base.copy(steps = base.steps.with(Sound.KICK, 0, Level.HIT))
    private val second = first.copy(steps = first.steps.with(Sound.KICK, 4, Level.HIT))

    @AfterTest
    fun tearDown() = scope.cancel()

    // Review finding: a copy the store emits for an earlier save mustn't be taken back over a later edit.
    @Test
    fun anEarlierSaveIsNotCurrent() {
        saver.save(first)
        saver.save(second)
        assertFalse(saver.isCurrent(first.copy(editedAt = 9_000)))
        assertTrue(saver.isCurrent(second.copy(editedAt = 9_000)))
    }

    @Test
    fun aPatternNeverSavedHereIsCurrent() = assertTrue(saver.isCurrent(base))

    // Review finding: saves run in the saver's own scope, so a screen going away mid-save can't cancel them.
    @Test
    fun savesOutliveTheCallersScope() = runBlocking {
        val caller = CoroutineScope(Job() + Dispatchers.Unconfined)
        caller.cancel()
        saver.save(second)
        assertEquals(second.steps, store.pattern("p").first()?.steps)
    }

    @Test
    fun savesLandInOrder() = runBlocking {
        saver.save(first)
        saver.save(second)
        saver.save(first.copy(name = "Last"))
        assertEquals("Last", store.pattern("p").first()?.name)
    }

    // A delete queues behind earlier saves and drops later ones, so a pattern deleted mid-save stays deleted.
    @Test
    fun aDeleteStaysDeleted() = runBlocking {
        saver.save(first)
        saver.delete("p")
        saver.save(second)
        assertEquals(null, store.pattern("p").first())
        assertTrue(saver.isCurrent(second))
    }
}
