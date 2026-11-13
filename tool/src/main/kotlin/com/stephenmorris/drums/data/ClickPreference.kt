package com.stephenmorris.drums.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.stephenmorris.drums.core.ClickMode
import com.thelightphone.sdk.SealedLightContext
import kotlinx.coroutines.flow.first

/** The Click setting (spec §10), kept on this phone. Anything unknown or missing reads as rec. */
class ClickPreference(private val read: suspend () -> String?, private val write: suspend (String) -> Unit) {
    suspend fun load(): ClickMode = ClickMode.fromStored(read())

    suspend fun save(mode: ClickMode) = write(mode.raw)

    companion object {
        private val CLICK = stringPreferencesKey("click")

        /** The setting in the tool's preferences. */
        fun open(context: SealedLightContext) = ClickPreference(
            read = { context.dataStore.data.first()[CLICK] },
            write = { value -> context.dataStore.edit { it[CLICK] = value } },
        )
    }
}
