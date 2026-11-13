package com.stephenmorris.drums.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.stephenmorris.drums.audio.DrumsAudio
import com.stephenmorris.drums.audio.Transport
import com.stephenmorris.drums.core.Level
import com.stephenmorris.drums.core.SamplePatterns
import com.stephenmorris.drums.core.Sound
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The pattern library's view model. For now it only drives the transport for the debug controls. */
class LibraryViewModel : LightViewModel<Unit>() {
    private val _transport = MutableStateFlow<Transport?>(null)

    /** The transport once audio is ready. */
    val transport: StateFlow<Transport?> = _transport

    // DEBUG: removed in plan 3
    val slowRock = SamplePatterns.seeded(0)[0]

    init {
        viewModelScope.launch {
            val transport = DrumsAudio.prepare()
            _transport.value = transport
            while (isActive) {
                transport.tick()
                val busy = transport.playingUuid.value != null || transport.takePhase.value != null
                delay(if (busy) 10 else 1_000)
            }
        }
    }

    override fun onAppPause() {
        _transport.value?.suspend()
    }
}

/** The pattern library: the screen the tool opens on. A placeholder, with temporary audio controls. */
@InitialScreen
class LibraryScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, LibraryViewModel>(sealedActivity) {

    override val viewModelClass: Class<LibraryViewModel>
        get() = LibraryViewModel::class.java

    override fun createViewModel(): LibraryViewModel = LibraryViewModel()

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val transport by viewModel.transport.collectAsState()
        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            ) {
                LightText(text = "Drums", variant = LightTextVariant.Title)
                transport?.let { DebugControls(it, viewModel) }
            }
        }
    }
}

// DEBUG: removed in plan 3
@Composable
private fun DebugControls(transport: Transport, viewModel: LibraryViewModel) {
    val step by transport.currentStep.collectAsState()
    val phase by transport.takePhase.collectAsState()
    val count by transport.countInNumber.collectAsState()
    LightText(
        text = listOfNotNull(phase?.name?.lowercase(), count?.let { "count $it" }, step?.let { "step $it" })
            .joinToString(" · ")
            .ifEmpty { "stopped" },
        variant = LightTextVariant.Detail,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        LightText("play slow rock", LightTextVariant.Copy, Modifier.lightClickable { transport.play(viewModel.slowRock, 0) })
        LightText("record", LightTextVariant.Copy, Modifier.lightClickable { transport.record(viewModel.slowRock, 0) })
        LightText("stop", LightTextVariant.Copy, Modifier.lightClickable { transport.stop() })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (sound in listOf(Sound.KICK, Sound.SNARE, Sound.HAT)) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .border(1.dp, LightThemeTokens.colors.content)
                    .pointerInput(sound) {
                        awaitEachGesture {
                            awaitFirstDown()
                            transport.hit(sound, Level.HIT, viewModel.slowRock)
                        }
                    }
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                LightText(sound.displayName, LightTextVariant.Copy)
            }
        }
    }
}
