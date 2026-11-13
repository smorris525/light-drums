package com.stephenmorris.drums.pattern

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.stephenmorris.drums.core.BarWindow
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/**
 * One dot per bar (spec §8), shown when there's more than one: filled for the bars in view. A short line marks the
 * playing bar when it's out of view. Tapping a dot shows that bar first.
 */
/** The SDK's select dot is a 22-unit vector whose circle (radius 9, stroke 2) spans 20 units: the line matches it. */
private const val DOT_WIDTH_GRID_UNITS = 20f / 22f

@Composable
fun BarDots(window: BarWindow, playingBar: Int?, onTap: (Int) -> Unit) {
    if (window.bars <= 1) {
        return
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        for (bar in 0 until window.bars) {
            Column(
                modifier = Modifier
                    .width(if (window.bars > 4) 34.dp else 40.dp)
                    .height(44.dp)
                    .lightClickable(onClickLabel = "Bar ${bar + 1} of ${window.bars}") { onTap(bar) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                LightIcon(
                    if (bar in window.barRange) LightIcons.SELECT_ON else LightIcons.SELECT_OFF,
                    size = 1f,
                    contentDescription = null,
                )
                Spacer(Modifier.height(3.dp))
                val showsPlaying = bar == playingBar && bar !in window.barRange
                Box(
                    Modifier
                        .size(width = DOT_WIDTH_GRID_UNITS.gridUnitsAsDp(), height = 2.dp)
                        .background(if (showsPlaying) LightThemeTokens.colors.content else LightThemeTokens.colors.background),
                )
            }
        }
    }
}
