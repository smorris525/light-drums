package com.stephenmorris.drums.pattern

/** How a pattern's screens hand back to the library: open another pattern, or the pattern was deleted. */
sealed interface PatternExit {
    data class Open(val uuid: String) : PatternExit

    data object Deleted : PatternExit
}
