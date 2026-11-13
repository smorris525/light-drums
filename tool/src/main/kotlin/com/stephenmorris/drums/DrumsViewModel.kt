package com.stephenmorris.drums

import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The base of every Drums screen's view model: it gets the store and transport ready, and stops sound when the tool
 * is paused. The SDK only tells the top screen, so every screen does this.
 */
abstract class DrumsViewModel<T>(context: SealedLightContext) : LightViewModel<T>() {
    private val _ready = MutableStateFlow<Drums.Ready?>(null)

    /** The store and transport, once built. */
    val ready: StateFlow<Drums.Ready?> = _ready.asStateFlow()

    init {
        viewModelScope.launch { _ready.value = Drums.prepare(context) }
    }

    override fun onAppPause() {
        ready.value?.transport?.suspend()
    }
}
