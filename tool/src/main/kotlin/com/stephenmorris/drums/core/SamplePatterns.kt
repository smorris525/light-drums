package com.stephenmorris.drums.core

/** A sample pattern's contents, before it's stored. */
data class PatternSeed(
    /** A fixed id per sample. */
    val uuid: String,
    val name: String,
    val bpm: Int,
    val meter: MeterId,
    val bars: Int,
    val swing: Swing,
    val steps: StepGrid,
)

/**
 * The patterns seeded on first launch: five well-known grooves that between them use four meters, 1/16 swing, accents
 * against quiet hits, lengths of 1, 2 and 4 bars, and every sound. In each grid, X is an accent, x a hit, o an open hat (in the hat
 * row) and . a rest, grouped by beat, with bars split by |.
 */
object SamplePatterns {
    val all: List<PatternSeed> by lazy {
        listOf(
            PatternSeed(
                "1195CA2F-3355-46EA-88B5-488AAC1C7C47", "Funk Break", 96, MeterId.FOUR_FOUR, 2, Swing.SIXTEENTH_LIGHT,
                grid(
                    Sound.HAT to "X.x. X.x. X.x. X.o. | X.x. X.x. X.x. X.o.",
                    Sound.SNARE to ".... X..x .x.. X..x | .... X..x .x.. X.xx",
                    Sound.KICK to "X.x. .... ..x. .... | X.x. .... ..xx ....",
                    Sound.SHAKER to ".... x... .... x... | .... x... .... x...",
                ),
            ),
            PatternSeed(
                "B984E438-9F13-47F6-B72C-3EA83043FDD9", "House", 124, MeterId.FOUR_FOUR, 1, Swing.SIXTEENTH_LIGHT,
                grid(
                    Sound.HAT to ".xox .xox .xox .xox",
                    Sound.SIDESTICK to ".... ...x ..x. ..x.",
                    Sound.SNARE to ".... X... .... X...",
                    Sound.KICK to "X... x... x... x...",
                    Sound.TOM to ".... .... .x.. x.x.",
                ),
            ),
            PatternSeed(
                "F224ED03-82DD-474B-9AA7-60F2DEA4CE36", "Soul", 80, MeterId.SIX_EIGHT, 4, Swing.OFF,
                grid(
                    Sound.HAT to "Xxx Xxx | Xxx Xxx | Xxx Xxx | Xxx Xxo",
                    Sound.SHAKER to ".x. .x. | .x. .x. | .x. .x. | .x. .x.",
                    Sound.SNARE to "... X.. | ... X.. | ... X.. | ... X..",
                    Sound.KICK to "X.. ..x | X.. ..x | X.. ..x | X.x ..x",
                ),
            ),
            PatternSeed(
                "0F63B7A6-D918-4B87-845B-3C6E80BBE1FC", "Math Rock", 112, MeterId.FIVE_FOUR, 2, Swing.OFF,
                grid(
                    Sound.HAT to "X.x. x.x. x.x. X.x. x.x. | X.x. x.x. x.x. X.x. ....",
                    Sound.RIDE to "X... .... .... .... .... | .... .... .... .... ....",
                    Sound.SNARE to ".... X... .... X... .... | .... X... ...x X... ....",
                    Sound.KICK to "X... ..x. x... .... x.x. | X... ..x. x... .... x...",
                    Sound.TOM to ".... .... .... .... .... | .... .... .... .... Xxxx",
                ),
            ),
            PatternSeed(
                "84A63DE6-A788-4D21-8A2B-28BD5B631604", "Balkan Dance", 260, MeterId.SEVEN_EIGHT, 2, Swing.OFF,
                grid(
                    Sound.HAT to "Xx Xx Xx. | Xx Xx ...",
                    Sound.SNARE to ".. X. ..x | .. X. ...",
                    Sound.KICK to "X. .. x.. | X. .. x..",
                    Sound.TOM to ".. .. ... | .. .. Xxx",
                ),
            ),
        )
    }

    /** The samples ready to store, in order: created a millisecond apart from [now], and unedited. */
    fun seeded(now: Long): List<PatternData> = all.mapIndexed { index, seed ->
        PatternData(
            uuid = seed.uuid,
            name = seed.name,
            createdAt = now + index,
            bpm = seed.bpm,
            meter = seed.meter,
            bars = seed.bars,
            swing = seed.swing,
            steps = seed.steps,
        )
    }

    /**
     * A grid from one text row per sound, a character per step: `X` an accent, `x` a hit, `.` a rest, and `o` an
     * open hat (a hit on [Sound.OPEN_HAT], written in the hat's row). Spaces and `|` are only for reading.
     */
    private fun grid(vararg rows: Pair<Sound, String>): StepGrid {
        var grid = StepGrid.EMPTY
        for ((sound, row) in rows) {
            row.filterNot { it == ' ' || it == '|' }.forEachIndexed { step, mark ->
                grid = when (mark) {
                    'X' -> grid.with(sound, step, Level.ACCENT)
                    'x' -> grid.with(sound, step, Level.HIT)
                    'o' -> grid.with(Sound.OPEN_HAT, step, Level.HIT)
                    '.' -> grid
                    else -> error("Unknown step mark '$mark' in the $sound row")
                }
            }
        }
        return grid
    }
}
