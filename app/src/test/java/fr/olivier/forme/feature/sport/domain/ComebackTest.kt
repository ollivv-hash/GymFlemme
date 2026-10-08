package fr.olivier.forme.feature.sport.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** V2.16: welcome back, lighter loads after a long break, last week's goal missed. */
class ComebackTest {
    private val today = LocalDate.of(2026, 10, 8)

    @Test
    fun welcomeBackAfterMoreThanAWeek() {
        assertFalse(Comeback.isBack(null, today)) // never trained: a first session, not a return
        assertFalse(Comeback.isBack(today.minusDays(7), today))
        assertTrue(Comeback.isBack(today.minusDays(8), today))
    }

    @Test
    fun lighterLoadsOfferedAfterMoreThanTwoWeeks() {
        assertFalse(Comeback.offersLighterLoads(null, today))
        assertFalse(Comeback.offersLighterLoads(today.minusDays(14), today))
        assertTrue(Comeback.offersLighterLoads(today.minusDays(15), today))
    }

    @Test
    fun lighterLoadIsEightyPercentRoundedToTheLoadStep() {
        assertEquals(35.0, Comeback.lighterLoad(45.0, 2.5), 1e-9) // 36 → 35
        assertEquals(37.5, Comeback.lighterLoad(47.5, 2.5), 1e-9) // 38 → 37.5
        assertEquals(40.0, Comeback.lighterLoad(52.5, 5.0), 1e-9) // 42 → 40
        assertEquals(5.0, Comeback.lighterLoad(5.0, 5.0), 1e-9) // never below one step
        assertEquals(0.0, Comeback.lighterLoad(0.0, 2.5), 1e-9)
        assertEquals(8.0, Comeback.lighterLoad(10.0, 0.0), 1e-9)
    }

    @Test
    fun missedGoalOnlyBeforeTheFirstSessionOfTheWeekAndForRegulars() {
        assertTrue(Comeback.missedLastWeek(sessionsLastWeek = 1, sessionsThisWeek = 0, goal = 3, trainedBefore = true))
        assertFalse(Comeback.missedLastWeek(3, 0, 3, true))
        assertFalse(Comeback.missedLastWeek(1, 1, 3, true))
        assertFalse(Comeback.missedLastWeek(0, 0, 3, trainedBefore = false))
    }
}
