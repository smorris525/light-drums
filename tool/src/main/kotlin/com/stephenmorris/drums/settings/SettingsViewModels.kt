package com.stephenmorris.drums.settings

import com.stephenmorris.drums.DrumsViewModel
import com.stephenmorris.drums.session.PatternSession
import com.thelightphone.sdk.SealedLightContext

/**
 * The main Settings page's view model: opening it opens the settings session, and leaving it (by any back) commits
 * it, trimming hidden hits (spec §5).
 */
class SettingsViewModel(context: SealedLightContext, private val session: PatternSession) : DrumsViewModel<Unit>(context) {
    init {
        session.openSettings()
    }

    override fun onCleared() {
        session.closeSettings()
        super.onCleared()
    }
}

/** A Settings sub-page's view model: only there to stop sound when the tool is paused. */
class SettingsPageViewModel(context: SealedLightContext) : DrumsViewModel<Unit>(context)

/** The Tempo page's view model: leaving it (by any back) applies the typed digits. */
class TempoViewModel(context: SealedLightContext, session: PatternSession) : DrumsViewModel<Unit>(context) {
    val model = TempoModel(session.pattern.value.bpm, session::setBpm) { System.nanoTime() / 1e9 }

    override fun onCleared() {
        model.leave()
        super.onCleared()
    }
}
