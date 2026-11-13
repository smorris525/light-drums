package com.stephenmorris.drums.core

/**
 * A saved pattern's contents (spec §3), independent of how it's stored. The defaults are a new pattern's.
 * [createdAt] orders the library, oldest first; [editedAt] is 0 until the first edit.
 */
data class PatternData(
    val uuid: String,
    val name: String = PatternText.NEW_PATTERN_STEM,
    val createdAt: Long,
    val editedAt: Long = 0,
    val bpm: Int = 100,
    val meter: MeterId = MeterId.FOUR_FOUR,
    val bars: Int = 1,
    val swing: Swing = Swing.OFF,
    val steps: StepGrid = StepGrid.EMPTY,
) {
    /** The library's meta line, for example "68 bpm · 4/4 · 4 bars" (spec §7). */
    val libraryMeta: String
        get() = PatternText.libraryMeta(bpm, meter, bars)
}
