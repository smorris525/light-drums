package com.stephenmorris.drums.sound

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stephenmorris.drums.DrumsViewModel
import com.stephenmorris.drums.core.Sound
import com.stephenmorris.drums.session.PatternSession
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.ui.LightThemeTokens

/** The sound screen's view model. */
class SoundViewModel(context: SealedLightContext) : DrumsViewModel<Unit>(context)

/** One sound's steps (spec §9). A stub until Task 8. */
class SoundScreen(sealedActivity: SealedLightActivity, private val session: PatternSession, private val sound: Sound) :
    LightScreen<Unit, SoundViewModel>(sealedActivity) {

    override val viewModelClass: Class<SoundViewModel>
        get() = SoundViewModel::class.java

    override fun createViewModel() = SoundViewModel(lightContext)

    @Composable
    override fun Content() {
        Box(Modifier.fillMaxSize().background(LightThemeTokens.colors.background))
    }
}
