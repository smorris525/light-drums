package com.stephenmorris.drums.library

import com.stephenmorris.drums.audio.Transport
import com.stephenmorris.drums.core.PatternData
import com.stephenmorris.drums.data.PatternSaver
import com.stephenmorris.drums.data.PatternStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

/** The library's state and actions (spec §7): the patterns, edit mode, new and delete. */
class LibraryModel(
    private val store: PatternStore,
    private val transport: Transport,
    scope: CoroutineScope,
    /** Queues deletes behind unfinished saves, so a pattern deleted mid-save stays deleted. */
    private val saver: PatternSaver? = null,
) {
    /** Every pattern, oldest first. */
    val patterns: StateFlow<List<PatternData>> = store.patterns.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    fun toggleEditing() {
        _isEditing.value = !_isEditing.value
    }

    /** A new pattern at the bottom of the library. Leaves edit mode. */
    suspend fun create(): PatternData {
        _isEditing.value = false
        return store.create()
    }

    /** Deletes the pattern, stopping it first if it's playing. Deleting the last pattern leaves edit mode. */
    suspend fun delete(uuid: String) {
        transport.patternDeleted(uuid)
        if (saver != null) saver.delete(uuid) else store.delete(uuid)
        if (store.patterns.first().none { it.uuid != uuid }) {
            _isEditing.value = false
        }
    }
}
