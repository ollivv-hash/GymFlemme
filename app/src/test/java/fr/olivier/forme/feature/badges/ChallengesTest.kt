package fr.olivier.forme.feature.badges

import fr.olivier.forme.feature.badges.domain.BadgeHistory
import fr.olivier.forme.feature.badges.domain.Challenge
import fr.olivier.forme.feature.badges.domain.ChallengeEligibility
import fr.olivier.forme.feature.badges.domain.Challenges
import fr.olivier.forme.feature.badges.domain.SessionFact
import fr.olivier.forme.feature.badges.domain.WaterDay
import fr.olivier.forme.feature.badges.reminder.ChallengeReminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/** V2.19: monthly challenges — eligibility, deterministic draw never repeating last month, progress of each one. */
class ChallengesTest {
    private val october = YearMonth.of(2026, 10)
    private val all = ChallengeEligibility(hasRower = true, mealsLastMonth = true, rotationSize = 3, waterDaysLastMonth = 5)

    private fun session(day: LocalDate, template: Long? = null) = SessionFact(day.atTime(18, 0), day.atTime(19, 0), template)
    private fun progress(challenge: Challenge, h: BadgeHistory, month: YearMonth = october) = Challenges.progress(challenge, month, h)

    @Test
    fun eligibilityFollowsTheData() {
        assertEquals(Challenge.entries, Challenges.eligible(all))
        val none = Challenges.eligible(ChallengeEligibility(hasRower = false, mealsLastMonth = false, rotationSize = 2))
        assertFalse(Challenge.ROWER_OF_THE_MONTH in none)
        assertFalse(Challenge.PROTEIN_DATE in none)
        assertFalse(Challenge.TRIPLE_SESSION in none)
        assertTrue(Challenge.TWELVE_STRIKES in none)
        // V2.29: Hydraté after water recorded on 5 days of the month before.
        assertFalse(Challenge.HYDRATED in none)
        assertFalse(Challenge.HYDRATED in Challenges.eligible(all.copy(waterDaysLastMonth = 4)))
        assertTrue(Challenge.HYDRATED in Challenges.eligible(all.copy(waterDaysLastMonth = 5)))
    }

    @Test
    fun hydratedCountsTheDaysWithTheGoalReachedInTheMonth() {
        val goal = 2_500
        val days = (1..25).map { d -> WaterDay(october.atDay(d), if (d % 5 == 0) 2_000 else 2_600, goal) } + // 20 reached
            WaterDay(LocalDate.of(2026, 9, 30), 3_000, goal) // September: not counted
        val p = progress(Challenge.HYDRATED, BadgeHistory(LocalDate.of(2026, 10, 31), water = days))
        assertEquals(20.0, p.value, 0.0)
        assertEquals(20.0, p.goal, 0.0)
        assertEquals(october.atDay(24), p.completedOn)
        val short = progress(Challenge.HYDRATED, BadgeHistory(LocalDate.of(2026, 10, 31), water = days.drop(1)))
        assertNull(short.completedOn)
    }

    @Test
    fun drawIsDeterministicAndNeverLastMonthsChallenge() {
        val eligible = Challenges.eligible(all)
        // The same year and month always give the same challenge (reinstall, restore).
        assertEquals(Challenges.draw(october, eligible, null), Challenges.draw(october, eligible, null))
        // Two years in a row, each month drawn after the previous one: never the same twice in a row.
        var previous: Challenge? = null
        val drawn = (0L until 24L).map { i ->
            Challenges.draw(october.plusMonths(i), eligible, previous)!!.also {
                assertNotEquals(previous, it)
                previous = it
            }
        }
        // Spread over the challenges.
        assertTrue(drawn.toSet().size >= 5)
        // A single eligible challenge is drawn even if it was last month's.
        assertEquals(Challenge.TWELVE_STRIKES, Challenges.draw(october, listOf(Challenge.TWELVE_STRIKES), Challenge.TWELVE_STRIKES))
        assertNull(Challenges.draw(october, emptyList(), null))
    }

    @Test
    fun twelveSessionsInTheMonth() {
        // Every other day from 29 September: 13 sessions in October, the 12th on the 23rd.
        val days = (0L until 14L).map { october.atDay(1).minusDays(2).plusDays(2 * it) }
        val p = progress(Challenge.TWELVE_STRIKES, BadgeHistory(LocalDate.of(2026, 11, 5), days.map { session(it) }))
        assertEquals(13.0, p.value, 0.0)
        assertEquals(LocalDate.of(2026, 10, 23), p.completedOn)
    }

    @Test
    fun rowerRecordsCuriousAndProteinCountOnlyInTheMonth() {
        val h = BadgeHistory(
            today = LocalDate.of(2026, 10, 31),
            rowedMetres = listOf(LocalDate.of(2026, 9, 30) to 20_000, LocalDate.of(2026, 10, 3) to 15_000, LocalDate.of(2026, 10, 20) to 15_000),
            recordDays = listOf(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 9)),
            exercisesDone = listOf(
                1L to LocalDate.of(2026, 9, 1), 1L to LocalDate.of(2026, 10, 1), // done before: not new
                2L to LocalDate.of(2026, 10, 4), 3L to LocalDate.of(2026, 10, 6), 4L to LocalDate.of(2026, 10, 8),
            ),
            proteinDays = (1..20).map { LocalDate.of(2026, 10, it) },
        )
        assertEquals(LocalDate.of(2026, 10, 20), progress(Challenge.ROWER_OF_THE_MONTH, h).completedOn)
        assertEquals(30_000.0, progress(Challenge.ROWER_OF_THE_MONTH, h).value, 0.0)
        assertNull(progress(Challenge.RECORD_HUNTER, h).completedOn)
        assertEquals(2.0, progress(Challenge.RECORD_HUNTER, h).value, 0.0)
        assertEquals(LocalDate.of(2026, 10, 8), progress(Challenge.CURIOUS, h).completedOn)
        assertEquals(LocalDate.of(2026, 10, 20), progress(Challenge.PROTEIN_DATE, h).completedOn)
    }

    @Test
    fun noBlankWeekCountsTheWeeksWhoseMondayIsInTheMonth() {
        // October 2026: Mondays 5, 12, 19, 26; the week of Monday 26 ends on Sunday 1 November.
        val sessions = listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 21))
        val h = BadgeHistory(LocalDate.of(2026, 11, 1), sessions.map { session(it) })
        val partial = progress(Challenge.NO_BLANK_WEEK, h)
        assertEquals(4.0, partial.goal, 0.0)
        // The 1st belongs to the week of Monday 28 September; weeks of the 5th, 12th (Sunday 18) and 19th are done.
        assertEquals(3.0, partial.value, 0.0)
        assertNull(partial.completedOn)
        // Sunday 1 November completes the week of Monday 26 October.
        val done = progress(Challenge.NO_BLANK_WEEK, h.copy(sessions = h.sessions + session(LocalDate.of(2026, 11, 1))))
        assertEquals(LocalDate.of(2026, 11, 1), done.completedOn)
    }

    @Test
    fun tripleSessionNeedsEveryTemplateThreeTimes() {
        val rotation = setOf(1L, 2L, 3L)
        val sessions = (1..9).map { session(LocalDate.of(2026, 10, it * 2), template = ((it - 1) % 3 + 1).toLong()) }
        val h = BadgeHistory(LocalDate.of(2026, 10, 31), sessions, rotation = rotation)
        val p = progress(Challenge.TRIPLE_SESSION, h)
        assertEquals(9.0, p.goal, 0.0)
        assertEquals(LocalDate.of(2026, 10, 18), p.completedOn)
        val missing = progress(Challenge.TRIPLE_SESSION, h.copy(sessions = sessions.dropLast(1)))
        assertEquals(8.0, missing.value, 0.0)
        assertNull(missing.completedOn)
    }

    @Test
    fun notificationOnTheFirstOfTheMonthAtNine() {
        val zone = ZoneId.of("Europe/Paris")
        assertEquals(ZonedDateTime.of(2026, 11, 1, 9, 0, 0, 0, zone), ChallengeReminder.nextTrigger(ZonedDateTime.of(2026, 10, 9, 10, 0, 0, 0, zone)))
        assertEquals(ZonedDateTime.of(2026, 10, 1, 9, 0, 0, 0, zone), ChallengeReminder.nextTrigger(ZonedDateTime.of(2026, 10, 1, 8, 59, 0, 0, zone)))
        assertEquals(ZonedDateTime.of(2027, 1, 1, 9, 0, 0, 0, zone), ChallengeReminder.nextTrigger(ZonedDateTime.of(2026, 12, 1, 9, 0, 0, 0, zone)))
    }
}
