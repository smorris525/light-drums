package com.stephenmorris.drums.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.stephenmorris.drums.core.MeterId
import com.stephenmorris.drums.core.Swing
import com.stephenmorris.drums.session.PatternSession
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/**
 * Time Signature or Swing (spec §13): the choices as rows, the current one underlined. Picking one moves the
 * underline and stays on the page.
 */
class ChoiceScreen(sealedActivity: SealedLightActivity, private val session: PatternSession, private val kind: Kind) :
    LightScreen<Unit, SettingsPageViewModel>(sealedActivity) {

    enum class Kind(val title: String) {
        TIME_SIGNATURE("Time Signature"),
        SWING("Swing"),
    }

    override val viewModelClass: Class<SettingsPageViewModel>
        get() = SettingsPageViewModel::class.java

    override fun createViewModel() = SettingsPageViewModel(lightContext)

    @Composable
    override fun Content() {
        val pattern by session.pattern.collectAsState()
        val options: List<Triple<String, Boolean, () -> Unit>> = when (kind) {
            Kind.TIME_SIGNATURE -> MeterId.entries.map { meter ->
                Triple(meter.raw, meter == pattern.meter) { session.setMeter(meter) }
            }
            Kind.SWING -> Swing.entries.map { swing ->
                Triple(swing.optionName, swing == pattern.swing) { session.setSwing(swing) }
            }
        }
        SettingsPage(kind.title, session, onBack = { goBack() }) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 0.5f.gridUnitsAsDp())) {
                for ((label, isCurrent, pick) in options) {
                    LightText(
                        label,
                        LightTextVariant.Copy,
                        underline = isCurrent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .lightClickable(onClickLabel = label, onClick = pick)
                            .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.4f.gridUnitsAsDp()),
                    )
                }
            }
        }
    }
}
