package com.stephenmorris.drums.library

import com.stephenmorris.drums.audio.FakeAudioOutput
import com.stephenmorris.drums.audio.Transport
import com.stephenmorris.drums.core.SamplePatterns
import com.stephenmorris.drums.data.FakePatternDao
import com.stephenmorris.drums.data.PatternStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryModelTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private var seeded = false
    private val store = PatternStore(FakePatternDao(), { seeded }, { seeded = true }, { 1_000 })
    private val transport = Transport(FakeAudioOutput()) {}
    private val model = LibraryModel(store, transport, scope)

    @AfterTest
    fun tearDown() = scope.cancel()

    // Deleting the pattern that's playing stops it at once.
    @Test
    fun deletingThePlayingPatternStopsIt() = runBlocking {
        store.seedIfNeeded()
        val playing = store.patterns.first()[0]
        transport.play(playing, 0)
        model.delete(playing.uuid)
        assertNull(transport.playingUuid.value)
        assertTrue(store.patterns.first().none { it.uuid == playing.uuid })
    }

    @Test
    fun deletingTheLastLeavesEditing() = runBlocking {
        val only = store.create()
        model.toggleEditing()
        assertTrue(model.isEditing.value)
        model.delete(only.uuid)
        assertFalse(model.isEditing.value)
    }

    @Test
    fun createLeavesEditing() = runBlocking {
        model.toggleEditing()
        val created = model.create()
        assertFalse(model.isEditing.value)
        assertEquals("New Pattern", created.name)
    }

    @Test
    fun listsThePatterns() = runBlocking {
        store.seedIfNeeded()
        assertEquals(SamplePatterns.all.size, model.patterns.value.size)
    }
}
