package fr.olivier.forme.feature.badges

import fr.olivier.forme.feature.badges.domain.Badge
import fr.olivier.forme.feature.badges.domain.BadgeHistory
import fr.olivier.forme.feature.badges.domain.Badges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** V2.17: each badge is earned on the day its condition is first met. */
class BadgesTest {
    private val monday = LocalDate.of(2026, 9, 7)

    private fun at(day: LocalDate, hour: Int = 18) = day.atTime(hour, 0)

    @Test
    fun nothingWithoutHistory() {
        assertTrue(Badges.earned(BadgeHistory()).isEmpty())
    }

    @Test
    fun sessionCountsGiveTheDayOfTheNthSession() {
        // One session a day, unsorted on purpose.
        val sessions = List(12) { at(monday.plusDays(it.toLong())) }.shuffled(kotlin.random.Random(1))
        val earned = Badges.earned(BadgeHistory(sessions = sessions))
        assertEquals(monday, earned[Badge.NOT_DEAD])
        assertEquals(monday.plusDays(4), earned[Badge.WELL_STARTED])
        assertEquals(monday.plusDays(9), earned[Badge.REPEAT_OFFENDER])
        assertNull(earned[Badge.PILLAR])
        // Monday to Wednesday: 3 sessions in the first week.
        assertEquals(monday.plusDays(2), earned[Badge.REGULAR])
    }

    @Test
    fun fourWeeksInARowCountsFromTheFirstSessionOfTheFourthWeek() {
        // Weeks 1, 2, then a gap, then weeks 4 to 7: the run of 4 ends in week 7.
        val weeks = listOf(0L, 1L, 3L, 4L, 5L, 6L)
        val sessions = weeks.map { at(monday.plusWeeks(it).plusDays(2)) }
        assertEquals(monday.plusWeeks(6).plusDays(2), Badges.earned(BadgeHistory(sessions = sessions))[Badge.BEATS_THE_COUCH])
        assertNull(Badges.earned(BadgeHistory(sessions = sessions.take(4)))[Badge.BEATS_THE_COUCH])
    }

    @Test
    fun earlyBirdBeforeEightAndRegularNeedsThreeInOneWeek() {
        val sessions = listOf(at(monday, 8), at(monday.plusDays(1), 7), at(monday.plusDays(8)), at(monday.plusDays(15)))
        val earned = Badges.earned(BadgeHistory(sessions = sessions))
        assertEquals(monday.plusDays(1), earned[Badge.EARLY_BIRD]) // 8:00 sharp does not count
        assertNull(earned[Badge.REGULAR])
    }

    @Test
    fun firstRecordTenExercisesAndThirtyDaysOfCreatine() {
        val history = BadgeHistory(
            recordDays = listOf(monday.plusDays(9), monday.plusDays(3)),
            exercisesDone = (1L..10L).map { it to monday.plusDays(it) } + (1L to monday.plusDays(20)),
            creatineDays = (0L..10L).map { monday.plusDays(it) } + (12L..41L).map { monday.plusDays(it) },
        )
        val earned = Badges.earned(history)
        assertEquals(monday.plusDays(3), earned[Badge.FIRST_RECORD])
        assertEquals(monday.plusDays(10), earned[Badge.ALL_ROUNDER])
        // The first run (11 days) is too short; the second reaches 30 days on day 41.
        assertEquals(monday.plusDays(41), earned[Badge.CREATINE_LOYAL])
        assertNull(Badges.earned(history.copy(creatineDays = history.creatineDays.dropLast(1)))[Badge.CREATINE_LOYAL])
        assertNull(Badges.earned(history.copy(exercisesDone = history.exercisesDone.filter { it.first != 1L }))[Badge.ALL_ROUNDER])
    }

    @Test
    fun pillarAtTheFiftiethSession() {
        val sessions = List(50) { at(monday.plusDays(it.toLong())) }
        assertEquals(monday.plusDays(49), Badges.earned(BadgeHistory(sessions = sessions))[Badge.PILLAR])
        assertEquals(LocalDateTime.of(2026, 9, 7, 18, 0), sessions.first())
    }
}
