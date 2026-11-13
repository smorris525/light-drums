package com.stephenmorris.drums.pattern

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stephenmorris.drums.DrumsViewModel
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.ui.LightThemeTokens

/** The pattern screen's view model. A stub until Task 7. */
class PatternViewModel(context: SealedLightContext) : DrumsViewModel<Unit>(context)

/** One pattern: every sound's steps (spec §8). A stub until Task 7. */
class PatternScreen(sealedActivity: SealedLightActivity, private val uuid: String) :
    LightScreen<Unit, PatternViewModel>(sealedActivity) {

    override val viewModelClass: Class<PatternViewModel>
        get() = PatternViewModel::class.java

    override fun createViewModel() = PatternViewModel(lightContext)

    @Composable
    override fun Content() {
        Box(Modifier.fillMaxSize().background(LightThemeTokens.colors.background))
    }
}
