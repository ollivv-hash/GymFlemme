package fr.olivier.forme.feature.settings.domain

/**
 * Taps on the version number in À propos (V2.23), as in Android's settings: 7 taps in a row turn the developer mode
 * on; a pause longer than [maxGapMs] starts the count again.
 */
class DeveloperTaps(private val needed: Int = 7, private val maxGapMs: Long = 3_000) {
    sealed interface Result {
        /** Nothing to say yet (the first taps). */
        data object Silent : Result

        /** [left] taps still needed: shown from 3 left. */
        data class Countdown(val left: Int) : Result

        /** The 7th tap: turn the mode on. */
        data object Enabled : Result
    }

    private var count = 0
    private var lastAt = Long.MIN_VALUE

    fun tap(nowMs: Long): Result {
        count = if (lastAt != Long.MIN_VALUE && nowMs - lastAt <= maxGapMs) count + 1 else 1
        lastAt = nowMs
        val left = needed - count
        return when {
            left <= 0 -> Result.Enabled.also { count = 0; lastAt = Long.MIN_VALUE }
            left <= 3 -> Result.Countdown(left)
            else -> Result.Silent
        }
    }
}
