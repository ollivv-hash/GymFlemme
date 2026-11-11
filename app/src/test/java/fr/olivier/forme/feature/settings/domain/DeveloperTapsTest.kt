package fr.olivier.forme.feature.settings.domain

import fr.olivier.forme.feature.settings.domain.DeveloperTaps.Result
import org.junit.Assert.assertEquals
import org.junit.Test

class DeveloperTapsTest {
    private fun taps(times: List<Long>): List<Result> = DeveloperTaps().let { t -> times.map(t::tap) }

    @Test
    fun sevenTapsInARowTurnTheModeOn() {
        val results = taps((0L until 7).map { it * 500 })
        assertEquals(
            listOf(Result.Silent, Result.Silent, Result.Silent, Result.Countdown(3), Result.Countdown(2), Result.Countdown(1), Result.Enabled),
            results,
        )
    }

    @Test
    fun aLongPauseStartsTheCountAgain() {
        val results = taps(listOf(0, 500, 1_000, 1_500, 2_000, 2_500, 10_000))
        assertEquals(Result.Silent, results.last())
    }

    @Test
    fun theCountStartsAgainAfterTheModeIsOn() {
        val t = DeveloperTaps()
        repeat(7) { t.tap(it * 100L) }
        assertEquals(Result.Silent, t.tap(800))
    }
}
