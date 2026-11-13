package com.stephenmorris.drums.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.ui.LightThemeTokens

/** An icon drawn on a square [grid], scaled to whatever size it's given, in [color]. */
private class DrawnIcon(
    private val grid: Float,
    private val color: Color,
    /** Whether the icon keeps room for an underline, drawn or not, so it doesn't move. */
    private val underlinable: Boolean = false,
    private val underlined: Boolean = false,
    private val draw: DrawScope.(Color) -> Unit,
) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        // An underlinable icon is given a box UNDERLINABLE_SIZE_UNITS across (see [DrumsIcons.UNDERLINABLE_SIZE_UNITS])
        // and draws itself at its neighbours' size in the middle of it: the same size and centre line as they have,
        // with room for the underline below whether it's drawn or not, so nothing moves.
        val box = minOf(size.width, size.height)
        val side = if (underlinable) box * BAR_ICON_UNITS / DrumsIcons.UNDERLINABLE_SIZE_UNITS else box
        val left = (size.width - side) / 2
        val top = (size.height - side) / 2
        translate(left, top) {
            scale(side / grid, pivot = Offset.Zero) { draw(color) }
        }
        if (underlined) {
            // A 2 dp line under the icon (spec §10: Click while on, Record while recording).
            val gap = (size.height - side) / 2
            drawRect(color, Offset(left, top + side + gap / 2 - 1.dp.toPx()), Size(side, 2.dp.toPx()))
        }
    }

    private companion object {
        /** A bar icon's size, in grid units: the SDK's. */
        const val BAR_ICON_UNITS = 2f
    }
}

/** Drums' own icons, beside the SDK's [com.thelightphone.sdk.ui.LightIcons]. */
object DrumsIcons {
    /**
     * The box, in grid units, to give an underlinable icon (metronome, record) in a bar: the SDK's 2 units for the
     * icon, plus room above and below for its underline.
     */
    const val UNDERLINABLE_SIZE_UNITS = 2.8f

    /**
     * Cancel: the SDK's close X, its margin trimmed so it fills its box as the confirm triangle beside it does, on a
     * 26.2 grid (the X's 6.9 to 33.1 of the SDK's 40).
     */
    @Composable
    fun cancel(): Painter {
        val color = LightThemeTokens.colors.content
        return remember(color) {
            DrawnIcon(26.2f, color) { tint ->
                val cross = Path().apply {
                    moveTo(26.2f, 24.1f)
                    lineTo(15.2f, 13.1f)
                    lineTo(26.2f, 2.1f)
                    lineTo(24.1f, 0f)
                    lineTo(13.1f, 11f)
                    lineTo(2.1f, 0f)
                    lineTo(0f, 2.1f)
                    lineTo(11f, 13.1f)
                    lineTo(0f, 24.1f)
                    lineTo(2.1f, 26.2f)
                    lineTo(13.1f, 15.2f)
                    lineTo(24.1f, 26.2f)
                    close()
                }
                drawPath(cross, tint)
            }
        }
    }

    /** Live pads: 2 x 2 rounded squares out to the edges of a 24 grid. */
    @Composable
    fun pads(): Painter {
        val color = LightThemeTokens.colors.content
        return remember(color) {
            DrawnIcon(24f, color) { tint ->
                for ((x, y) in listOf(0.9f to 0.9f, 13.5f to 0.9f, 0.9f to 13.5f, 13.5f to 13.5f)) {
                    drawRoundRect(tint, Offset(x, y), Size(9.6f, 9.6f), CornerRadius(2.5f), style = Stroke(1.8f))
                }
            }
        }
    }

    /** The step grid: the SDK dialpad's dots, 4 x 4, on a 40 grid. */
    @Composable
    fun stepGrid(): Painter {
        val color = LightThemeTokens.colors.content
        return remember(color) {
            DrawnIcon(40f, color) { tint ->
                val diameter = 6f
                val pitch = (40f - diameter) / 3
                for (row in 0 until 4) {
                    for (column in 0 until 4) {
                        drawCircle(tint, diameter / 2, Offset(column * pitch + diameter / 2, row * pitch + diameter / 2))
                    }
                }
            }
        }
    }

    /** A metronome: a tapering case, its arm swung out to the right, the weight clear of it, on a 40 grid. */
    @Composable
    fun metronome(underlined: Boolean = false): Painter {
        val color = LightThemeTokens.colors.content
        return remember(color, underlined) {
            DrawnIcon(40f, color, underlinable = true, underlined = underlined) { tint ->
                val case = Path().apply {
                    moveTo(11.5f, 1.5f)
                    lineTo(18.5f, 1.5f)
                    lineTo(28f, 38.5f)
                    lineTo(2f, 38.5f)
                    close()
                    moveTo(11f, 31f)
                    lineTo(33f, 7f)
                }
                drawPath(case, tint, style = Stroke(3f))
                drawCircle(tint, 5f, Offset(33f, 7f))
            }
        }
    }

    /** Record: a solid circle, a little inside its 24 place, as a solid circle looks bigger than other icons. */
    @Composable
    fun record(underlined: Boolean = false): Painter {
        val color = LightThemeTokens.colors.content
        return remember(color, underlined) {
            DrawnIcon(24f, color, underlinable = true, underlined = underlined) { tint -> drawCircle(tint, 11f, Offset(12f, 12f)) }
        }
    }

    /** A count-in beat, drawn in record's place: the bottom bar holds only icons alongside four others. */
    @Composable
    fun countIn(number: Int): Painter {
        val color = LightThemeTokens.colors.content
        val measurer: TextMeasurer = rememberTextMeasurer()
        val style = LightThemeTokens.typography.heading.copy(color = color)
        return remember(number, color) {
            val layout = measurer.measure("$number", style)
            object : Painter() {
                override val intrinsicSize: Size = Size.Unspecified

                override fun DrawScope.onDraw() {
                    drawText(
                        layout,
                        topLeft = Offset(
                            (size.width - layout.size.width) / 2,
                            (size.height - layout.size.height) / 2,
                        ),
                    )
                }
            }
        }
    }
}
