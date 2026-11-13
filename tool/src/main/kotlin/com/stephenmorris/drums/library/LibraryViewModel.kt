package com.stephenmorris.drums.library

import androidx.lifecycle.viewModelScope
import com.stephenmorris.drums.DrumsViewModel
import com.thelightphone.sdk.SealedLightContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The library screen's view model: its [LibraryModel] once the store and transport are ready. */
class LibraryViewModel(context: SealedLightContext) : DrumsViewModel<Unit>(context) {
    val model: StateFlow<LibraryModel?> = ready
        .map { ready -> ready?.let { LibraryModel(it.store, it.transport, viewModelScope, it.saver) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Deletes the pattern in the view model's scope, which outlives the library's composition: the confirmation
     * screen covers the library, cancelling any scope the library's composables hold.
     */
    fun delete(uuid: String) {
        viewModelScope.launch { model.value?.delete(uuid) }
    }
}
