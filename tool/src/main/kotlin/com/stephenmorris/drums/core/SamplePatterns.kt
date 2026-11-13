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

/** The patterns seeded on first launch (spec §3). */
object SamplePatterns {
    val all: List<PatternSeed> by lazy {
        listOf(
            seed("B9D09209-B157-4B2E-B311-859959C0CC2C", "Slow Rock", 68, MeterId.FOUR_FOUR, 4),
            seed("E43E9BCF-742B-43ED-9F04-B3B4F620366A", "Shuffle in G", 92, MeterId.TWELVE_EIGHT, 1, steps = shuffle()),
            seed("02192050-3256-4648-B205-27F713C7CED3", "Bossa Practice", 120, MeterId.FOUR_FOUR, 2, steps = bossa()),
            seed("8F799BA6-52AD-45E1-8446-ABB23DEFEF44", "Waltz", 144, MeterId.THREE_FOUR, 1, steps = waltz()),
            seed("37A2D6F2-E4CE-4D81-85D7-398980C23123", "New Tune in 7", 220, MeterId.SEVEN_EIGHT, 2),
            seed("610037BF-9759-4BD1-8D70-BF861E513F73", "Half-Time Ballad", 62, MeterId.FOUR_FOUR, 2, steps = ballad()),
            seed(
                "CE749A6E-24C9-4A9B-A813-C7D33AA58DB5", "Funk Practice", 98, MeterId.FOUR_FOUR, 1,
                swing = Swing.SIXTEENTH_LIGHT, steps = funk(),
            ),
            seed("4AC8100B-ABB1-4382-AE2E-5FD82925A2DF", "Five", 132, MeterId.FIVE_FOUR, 1),
            seed("D7C0F875-01F2-4590-ACF9-2DAD518012A4", "Campfire 6/8", 54, MeterId.SIX_EIGHT, 2),
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
     * A basic groove for any meter: kick on 1 (accented in bar 1) and mid-bar, snare on every second beat, hats on the
     * beats' even steps (every step in eighth-based meters) with shaker between, and a tom fill over the last beat
     * when there's more than one bar.
     */
    fun defaultGroove(meter: MeterId, bars: Int): StepGrid {
        val layout = meter.layout
        val isSixteenths = layout.unit == 0.25
        val beatCount = layout.groups.size
        var grid = StepGrid.EMPTY
        var lastStart = 0
        var lastLength = 0

        for (bar in 0 until bars) {
            layout.groups.forEachIndexed { index, length ->
                val start = bar * layout.stepsPerBar + layout.starts[index]
                val number = index + 1
                if (number == 1) {
                    grid = grid.with(Sound.KICK, start, if (bar == 0) Level.ACCENT else Level.HIT)
                }
                if (beatCount >= 4 && number == beatCount / 2 + 1) {
                    grid = grid.with(Sound.KICK, start, Level.HIT)
                }
                if (beatCount == 3 && number == 3) {
                    grid = grid.with(Sound.KICK, start, Level.HIT)
                }
                if ((number - 1) % 2 == 1) {
                    grid = grid.with(Sound.SNARE, start, Level.ACCENT)
                }
                for (offset in 0 until length) {
                    grid = if (!isSixteenths || offset % 2 == 0) {
                        grid.with(Sound.HAT, start + offset, if (offset == 0 && number == 1) Level.ACCENT else Level.HIT)
                    } else {
                        grid.with(Sound.SHAKER, start + offset, Level.HIT)
                    }
                }
                lastStart = start
                lastLength = length
            }
        }

        if (bars > 1) {
            for (offset in 0 until lastLength) {
                val step = lastStart + offset
                grid = grid
                    .with(Sound.HAT, step, null)
                    .with(Sound.SHAKER, step, null)
                    .with(Sound.SNARE, step, null)
                    .with(Sound.TOM, step, if (offset == 0) Level.ACCENT else Level.HIT)
            }
        }
        return grid
    }

    private fun seed(
        uuid: String,
        name: String,
        bpm: Int,
        meter: MeterId,
        bars: Int,
        swing: Swing = Swing.OFF,
        steps: StepGrid? = null,
    ) = PatternSeed(uuid, name, bpm, meter, bars, swing, steps ?: defaultGroove(meter, bars))

    private fun StepGrid.put(sound: Sound, steps: Iterable<Int>, level: Level): StepGrid =
        steps.fold(this) { grid, step -> grid.with(sound, step, level) }

    private fun shuffle() = StepGrid.EMPTY
        .put(Sound.KICK, listOf(0), Level.ACCENT)
        .put(Sound.KICK, listOf(6), Level.HIT)
        .put(Sound.SNARE, listOf(3, 9), Level.ACCENT)
        .put(Sound.RIDE, listOf(2, 3, 5, 6, 8, 9, 11), Level.HIT)
        .put(Sound.RIDE, listOf(0), Level.ACCENT)

    private fun bossa() = StepGrid.EMPTY
        .put(Sound.KICK, listOf(6, 8, 14, 16, 22, 24, 30), Level.HIT)
        .put(Sound.KICK, listOf(0), Level.ACCENT)
        .put(Sound.SIDESTICK, listOf(0, 6, 12, 20, 26), Level.HIT)
        .put(Sound.HAT, 0 until 32 step 2, Level.HIT)

    private fun waltz() = StepGrid.EMPTY
        .put(Sound.KICK, listOf(0), Level.ACCENT)
        .put(Sound.SIDESTICK, listOf(4, 8), Level.HIT)
        .put(Sound.HAT, listOf(0, 4, 8, 2, 6, 10), Level.HIT)

    private fun ballad() = StepGrid.EMPTY
        .put(Sound.KICK, listOf(0), Level.ACCENT)
        .put(Sound.KICK, listOf(10, 16, 22, 27), Level.HIT)
        .put(Sound.SNARE, listOf(8, 24), Level.ACCENT)
        .put(Sound.HAT, 0 until 32 step 2, Level.HIT)
        .with(Sound.HAT, 30, null)
        .with(Sound.OPEN_HAT, 30, Level.HIT)

    private fun funk() = StepGrid.EMPTY
        .put(Sound.KICK, listOf(0), Level.ACCENT)
        .put(Sound.KICK, listOf(3, 10), Level.HIT)
        .put(Sound.SNARE, listOf(4, 12), Level.ACCENT)
        .put(Sound.SNARE, listOf(7, 15), Level.HIT)
        .put(Sound.HAT, (0 until 16).filter { it % 4 == 0 }, Level.ACCENT)
        .put(Sound.HAT, (0 until 16).filter { it % 4 != 0 }, Level.HIT)
        .with(Sound.HAT, 14, null)
        .with(Sound.OPEN_HAT, 14, Level.HIT)
}
