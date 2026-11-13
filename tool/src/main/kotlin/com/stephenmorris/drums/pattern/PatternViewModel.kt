package com.stephenmorris.drums.pattern

import androidx.lifecycle.viewModelScope
import com.stephenmorris.drums.DrumsViewModel
import com.stephenmorris.drums.session.PatternSession
import com.thelightphone.sdk.SealedLightContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The pattern screen's view model: builds the [PatternSession] the pattern and sound screens share, keeps it in step
 * with the store and the playhead, and stops playback on the way back to the library (spec §6).
 */
class PatternViewModel(context: SealedLightContext, uuid: String) : DrumsViewModel<Unit>(context) {
    private val _session = MutableStateFlow<PatternSession?>(null)
    val session: StateFlow<PatternSession?> = _session.asStateFlow()

    init {
        viewModelScope.launch {
            val ready = ready.filterNotNull().first()
            val initial = ready.store.pattern(uuid).filterNotNull().first()
            val session = PatternSession(initial, ready.transport, viewModelScope, ready.saver::save)
            _session.value = session
            launch {
                // The store emits each save after it lands, by which time newer edits may exist: only the latest
                // copy saved is taken back.
                ready.store.pattern(uuid).filterNotNull().collect { stored ->
                    if (ready.saver.isCurrent(stored)) session.refresh(stored)
                }
            }
            launch {
                session.playhead.collect { step -> step?.let(session::playheadMoved) }
            }
        }
    }

    /** Leaving for the library stops playback (spec §6). */
    fun stopPlayback() {
        ready.value?.transport?.stop()
    }

    /**
     * The screen was removed: back to the library, by the top bar or the system back key, which reaches the activity
     * without asking the view model. Covering the screen with the sound screen doesn't clear it.
     */
    override fun onCleared() {
        stopPlayback()
        super.onCleared()
    }
}
