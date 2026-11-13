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
    /** Room for an underline under the icon, kept whether or not it's drawn, so the icon doesn't move. */
    private val underlinable: Boolean = false,
    private val underlined: Boolean = false,
    private val draw: DrawScope.(Color) -> Unit,
) : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() {
        val reserve = if (underlinable) UNDERLINE_SPACE.dp.toPx() else 0f
        val side = minOf(size.width, size.height - reserve)
        val left = (size.width - side) / 2
        translate(left, 0f) {
            scale(side / grid, pivot = Offset.Zero) { draw(color) }
        }
        if (underlined) {
            // A 2 dp line under the icon (spec §10: Click while on, Record while recording).
            drawRect(color, Offset(left, size.height - 2.dp.toPx()), Size(side, 2.dp.toPx()))
        }
    }

    private companion object {
        /** The underline and the space above it. */
        const val UNDERLINE_SPACE = 6f
    }
}

/** Drums' own icons, beside the SDK's [com.thelightphone.sdk.ui.LightIcons]. */
object DrumsIcons {
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
