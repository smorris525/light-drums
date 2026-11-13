package com.stephenmorris.drums.data

import com.stephenmorris.drums.core.ClickMode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ClickPreferenceTest {
    private var stored: String? = null
    private val preference = ClickPreference({ stored }, { stored = it })

    @Test
    fun defaultsToRec() = runBlocking { assertEquals(ClickMode.REC, preference.load()) }

    @Test
    fun roundTrips() = runBlocking {
        preference.save(ClickMode.OFF)
        assertEquals("off", stored)
        assertEquals(ClickMode.OFF, preference.load())
    }

    @Test
    fun unknownReadsAsRec() = runBlocking {
        stored = "on"
        assertEquals(ClickMode.REC, preference.load())
    }
}
