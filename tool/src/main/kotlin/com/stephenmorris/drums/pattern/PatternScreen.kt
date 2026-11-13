package com.stephenmorris.drums.pattern

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.stephenmorris.drums.core.Sound
import com.stephenmorris.drums.session.OverviewLayout
import com.stephenmorris.drums.session.PatternSession
import com.stephenmorris.drums.settings.SettingsScreen
import com.stephenmorris.drums.sound.SoundScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp

/** One pattern (spec §8): every sound's steps for the bars in view, and play. Tapping a sound opens it. */
class PatternScreen(sealedActivity: SealedLightActivity, private val uuid: String) :
    LightScreen<Unit, PatternViewModel>(sealedActivity) {

    override val viewModelClass: Class<PatternViewModel>
        get() = PatternViewModel::class.java

    override fun createViewModel() = PatternViewModel(lightContext, uuid)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val session by viewModel.session.collectAsState()
        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                session?.let { Pattern(it) }
            }
        }
    }

    @Composable
    private fun Pattern(session: PatternSession) {
        val pattern by session.pattern.collectAsState()
        val window by session.window.collectAsState()
        val isPlaying by session.isPlaying.collectAsState()
        val playhead by session.playhead.collectAsState()

        Column(modifier = Modifier.fillMaxSize()) {
            LightTopBar(
                leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = {
                    viewModel.stopPlayback()
                    goBack()
                }, contentDescription = "Back"),
                center = LightTopBarCenter.Text(pattern.name),
                rightButton = LightBarButton.LightIcon(LightIcons.LIST, onClick = null, contentDescription = "Actions"),
            )
            BarDots(window, playhead?.let(window::bar), session::showBar)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp()),
            ) {
                val rowHeight = minOf(48f, (maxHeight.value - 8f) / Sound.entries.size)
                val layout = OverviewLayout(maxWidth.value - NAME_WIDTH.value, window, pattern.meter)
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    TickRow(layout, window, playhead, rowHeight)
                    for (sound in Sound.entries) {
                        OverviewRow(sound, pattern.steps, layout, window, rowHeight) {
                            navigateTo({ SoundScreen(it, session, sound) })
                        }
                    }
                }
            }
            LightBottomBar(
                items = listOf(
                    LightBarButton.LightIcon(
                        LightIcons.SETTINGS,
                        onClick = { navigateTo({ SettingsScreen(it, session) }) },
                        contentDescription = "Settings",
                    ),
                    LightBarButton.LightIcon(
                        if (isPlaying) LightIcons.STOP else LightIcons.PLAY,
                        onClick = session::togglePlay,
                        contentDescription = if (isPlaying) "Stop" else "Play",
                    ),
                    LightBarButton.LightIcon(LightIcons.LIST, onClick = null, contentDescription = "Pads", enabled = false),
                ),
            )
        }
    }
}
