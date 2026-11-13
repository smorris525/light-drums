package com.stephenmorris.drums.pads

import androidx.lifecycle.viewModelScope
import com.stephenmorris.drums.DrumsViewModel
import com.stephenmorris.drums.core.ClickMode
import com.stephenmorris.drums.core.PadTouchProbe
import com.stephenmorris.drums.core.RecordControl
import com.stephenmorris.drums.pattern.PatternExit
import com.thelightphone.sdk.SealedLightContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The pads screen's view model (spec §10): the beats click only while Pads shows, the touch probe that decides
 * accents, the Click setting, and ending a take when Pads is left.
 */
class PadsViewModel(context: SealedLightContext) : DrumsViewModel<PatternExit?>(context) {
    /** Learns whether this screen reports real pressure or touch size. */
    val probe = PadTouchProbe()

    private val _clickMode = MutableStateFlow(ClickMode.REC)
    val clickMode: StateFlow<ClickMode> = _clickMode.asStateFlow()

    private var showing = false

    init {
        viewModelScope.launch {
            val ready = ready.filterNotNull().first()
            _clickMode.value = ready.transport.clickMode
            ready.transport.isOnPads = showing
        }
    }

    /** Pads is showing or covered: the beats click only while it shows. */
    fun setShowing(isShowing: Boolean) {
        showing = isShowing
        ready.value?.transport?.isOnPads = isShowing
    }

    fun toggleClick() {
        val ready = ready.value ?: return
        val mode = if (_clickMode.value == ClickMode.REC) ClickMode.OFF else ClickMode.REC
        _clickMode.value = mode
        ready.transport.clickMode = mode
        viewModelScope.launch { ready.click.save(mode) }
    }

    /** Leaving Pads (or opening Settings from it) ends a take; a count-in that hasn't reached recording stops. */
    fun leavePads() {
        val transport = ready.value?.transport ?: return
        when (RecordControl.leavingPads(transport.takePhase.value)) {
            RecordControl.LeavingPads.END_TAKE -> transport.endTake()
            RecordControl.LeavingPads.STOP -> transport.stop()
            RecordControl.LeavingPads.NOTHING -> Unit
        }
    }

    override fun onCleared() {
        leavePads()
        setShowing(false)
        super.onCleared()
    }
}
