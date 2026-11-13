package com.stephenmorris.drums.core

/** User-visible text about patterns, and the rules for naming and ordering new ones. */
object PatternText {
    const val NEW_PATTERN_STEM = "New Pattern"

    /** The library's meta line, for example "68 bpm · 4/4 · 4 bars" (spec §7). Swing isn't shown. */
    fun libraryMeta(bpm: Int, meter: MeterId, bars: Int): String =
        "${Tempo.clamped(bpm)} bpm · ${meter.raw} · ${SettingsRows.length(bars)}"

    /** The name a rename saves (spec §11): what was typed, trimmed, at any length; an empty result keeps [current]. */
    fun renamed(typed: String, current: String): String = sanitizedTitle(typed) ?: current

    /** Trimmed, or `null` if nothing is left. */
    fun sanitizedTitle(raw: String): String? = raw.trim().ifEmpty { null }

    /** "New Pattern", or the lowest free "New Pattern n". */
    fun newPatternTitle(existing: List<String>): String {
        val taken = existing.toSet()
        return generateSequence(1) { it + 1 }
            .map { if (it == 1) NEW_PATTERN_STEM else "$NEW_PATTERN_STEM $it" }
            .first { it !in taken }
    }

    /** A duplicate's name: [source] plus the lowest free numeric suffix, starting at 2. */
    fun duplicateTitle(source: String, existing: List<String>): String {
        val taken = existing.toSet()
        return generateSequence(2) { it + 1 }.map { "$source $it" }.first { it !in taken }
    }

    /** A new pattern's `createdAt`: [now], but never before the newest existing pattern, so it always sorts last. */
    fun nextCreatedAt(now: Long, existing: List<PatternData>): Long =
        existing.maxOfOrNull { it.createdAt + 1 }?.let { maxOf(now, it) } ?: now
}
