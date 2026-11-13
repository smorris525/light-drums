package com.stephenmorris.drums

import com.stephenmorris.drums.audio.DrumsAudio
import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.sdk.shared.LightServerData
import kotlinx.coroutines.flow.StateFlow

/** The tool's entry point. It starts building the audio early; Drums needs no server registration or push. */
@EntryPoint
object DrumsEntryPoint : LightEntryPoint {
    override suspend fun onToolCreate(serverData: StateFlow<LightServerData?>) {
        DrumsAudio.prepare()
    }

    override suspend fun onPushNotification(data: ByteArray) = Unit
}
