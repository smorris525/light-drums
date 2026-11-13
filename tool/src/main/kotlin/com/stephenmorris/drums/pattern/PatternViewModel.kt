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

    /** Saves still under way. The store's copy lags them, so it isn't taken back into the session meanwhile. */
    private var pendingSaves = 0

    init {
        viewModelScope.launch {
            val ready = ready.filterNotNull().first()
            val initial = ready.store.pattern(uuid).filterNotNull().first()
            val session = PatternSession(initial, ready.transport, viewModelScope) { edited ->
                pendingSaves += 1
                viewModelScope.launch {
                    ready.store.save(edited)
                    pendingSaves -= 1
                }
            }
            _session.value = session
            launch {
                ready.store.pattern(uuid).filterNotNull().collect { stored ->
                    if (pendingSaves == 0) session.refresh(stored)
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

    override fun onBackPressed(): Boolean {
        stopPlayback()
        return false
    }
}
