package fr.olivier.forme.feature.badges

import fr.olivier.forme.feature.badges.domain.Award
import fr.olivier.forme.feature.badges.domain.Badge
import fr.olivier.forme.feature.badges.domain.BadgeConfig
import fr.olivier.forme.feature.badges.domain.BadgeHistory
import fr.olivier.forme.feature.badges.domain.BadgeKind
import fr.olivier.forme.feature.badges.domain.BadgeMigration
import fr.olivier.forme.feature.badges.domain.Badges
import fr.olivier.forme.feature.badges.domain.SessionFact
import fr.olivier.forme.feature.badges.domain.Tier
import fr.olivier.forme.feature.badges.domain.WaterDay
import fr.olivier.forme.feature.sport.domain.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** V2.18: every rule of the specification (§6), every tier, points and levels; a badge is dated when first met. */
class BadgesTest {
    /** A Monday. */
    private val monday = LocalDate.of(2026, 9, 7)
    private val today = LocalDate.of(2027, 12, 31)

    private fun session(day: LocalDate, hour: Int = 18, minutes: Long = 60, template: Long? = null) =
        SessionFact(day.atTime(hour, 0), day.atTime(hour, 0).plusMinutes(minutes), template)

    private fun earned(history: BadgeHistory) = Badges.earned(history)

    private fun BadgeHistory.tier(badge: Badge, tier: Tier) = earned(this)[Award(badge, tier)]
    private fun BadgeHistory.badge(badge: Badge) = earned(this)[Award(badge)]

    @Test
    fun nothingWithoutHistory() {
        assertTrue(earned(BadgeHistory(today)).isEmpty())
        assertEquals(1, Badges.level(0).number)
    }

    @Test
    fun sessionTiersAndOneOffsGiveTheDayOfTheNthSession() {
        // One session a day, unsorted on purpose.
        val sessions = List(150) { session(monday.plusDays(it.toLong())) }.shuffled(kotlin.random.Random(1))
        val h = BadgeHistory(today, sessions = sessions)
        assertEquals(monday, h.badge(Badge.NOT_DEAD))
        assertEquals(monday.plusDays(4), h.badge(Badge.WELL_STARTED))
        assertEquals(monday.plusDays(9), h.tier(Badge.REPEAT_OFFENDER, Tier.BRONZE))
        assertEquals(monday.plusDays(49), h.tier(Badge.REPEAT_OFFENDER, Tier.SILVER))
        assertEquals(monday.plusDays(149), h.tier(Badge.REPEAT_OFFENDER, Tier.GOLD))
        assertNull(h.tier(Badge.REPEAT_OFFENDER, Tier.PLATINUM))
        // Monday to Wednesday: 3 sessions in the first week.
        assertEquals(monday.plusDays(2), h.badge(Badge.REGULAR))
        assertEquals(150.0, Badges.progress(h).getValue(Badge.REPEAT_OFFENDER), 0.0)
    }

    @Test
    fun weekRunCountsFromTheFirstSessionOfTheWeekCompletingIt() {
        // Weeks 0, 1, a gap, then weeks 3 to 6 (Thursdays): the run of 4 is completed in week 6.
        val weeks = listOf(0L, 1L, 3L, 4L, 5L, 6L)
        val h = BadgeHistory(today, sessions = weeks.map { session(monday.plusWeeks(it).plusDays(3)) })
        assertEquals(monday.plusWeeks(6).plusDays(3), h.tier(Badge.BEATS_THE_COUCH, Tier.BRONZE))
        assertNull(h.tier(Badge.BEATS_THE_COUCH, Tier.SILVER))

        // 52 weeks in a row across the change of year (ISO weeks: Monday 28/12/2026 and Monday 04/01/2027 follow).
        val year = BadgeHistory(today, sessions = List(52) { session(monday.plusWeeks(it.toLong()).plusDays(6)) })
        assertEquals(monday.plusWeeks(11).plusDays(6), year.tier(Badge.BEATS_THE_COUCH, Tier.SILVER))
        assertEquals(monday.plusWeeks(51).plusDays(6), year.tier(Badge.BEATS_THE_COUCH, Tier.PLATINUM))
    }

    @Test
    fun theCurrentWeekNeverBreaksTheRun() {
        val thisMonday = LocalDate.of(2027, 1, 4)
        // Three weeks before this one, nothing yet this week: the run is still 3.
        val sessions = (1L..3L).map { session(thisMonday.minusWeeks(it).plusDays(2)) }
        assertEquals(3.0, Badges.progress(BadgeHistory(thisMonday.plusDays(3), sessions)).getValue(Badge.BEATS_THE_COUCH), 0.0)
        // A session this week extends it; a week without any session behind it breaks it.
        assertEquals(4.0, Badges.progress(BadgeHistory(thisMonday.plusDays(3), sessions + session(thisMonday))).getValue(Badge.BEATS_THE_COUCH), 0.0)
        assertEquals(0.0, Badges.progress(BadgeHistory(thisMonday.plusWeeks(1).plusDays(3), sessions)).getValue(Badge.BEATS_THE_COUCH), 0.0)
    }

    @Test
    fun tonnageAndRowerTiersAreCumulative() {
        // 2 t a week: 10 t on the 5th entry, 50 t on the 25th.
        val lifted = List(30) { monday.plusWeeks(it.toLong()) to 2_000.0 }
        val rowed = List(12) { monday.plusDays(it.toLong()) to 5_000 }
        val h = BadgeHistory(today, liftedKg = lifted, rowedMetres = rowed)
        assertEquals(monday.plusWeeks(4), h.tier(Badge.MOVER, Tier.BRONZE))
        assertEquals(monday.plusWeeks(24), h.tier(Badge.MOVER, Tier.SILVER))
        assertNull(h.tier(Badge.MOVER, Tier.GOLD))
        assertEquals(monday.plusDays(1), h.tier(Badge.GALLEY_SLAVE, Tier.BRONZE))
        assertEquals(monday.plusDays(9), h.tier(Badge.GALLEY_SLAVE, Tier.SILVER))
        assertEquals(60_000.0, Badges.progress(h).getValue(Badge.MOVER), 0.0)
        assertEquals(60_000.0, Badges.progress(h).getValue(Badge.GALLEY_SLAVE), 0.0)
    }

    @Test
    fun recordsFirstOneThenCollector() {
        val records = List(26) { monday.plusDays(it.toLong()) }
        val h = BadgeHistory(today, recordDays = records.reversed())
        assertEquals(monday, h.badge(Badge.FIRST_RECORD))
        assertEquals(monday.plusDays(4), h.tier(Badge.RECORD_COLLECTOR, Tier.BRONZE))
        assertEquals(monday.plusDays(24), h.tier(Badge.RECORD_COLLECTOR, Tier.SILVER))
    }

    @Test
    fun creatineCountsDaysInARowAndKeepsTiersAfterABreak() {
        val firstRun = List(31) { monday.plusDays(it.toLong()) }
        val secondRun = List(3) { monday.plusDays(40L + it) }
        val h = BadgeHistory(monday.plusDays(43), creatineDays = (firstRun + secondRun).shuffled(kotlin.random.Random(3)))
        assertEquals(monday.plusDays(6), h.tier(Badge.CREATINE_LOYAL, Tier.BRONZE))
        assertEquals(monday.plusDays(29), h.tier(Badge.CREATINE_LOYAL, Tier.SILVER))
        // The current run is 3 (today, not ticked yet, does not break it); tiers already earned stay.
        assertEquals(3.0, Badges.progress(h).getValue(Badge.CREATINE_LOYAL), 0.0)
        assertEquals(0.0, Badges.progress(h.copy(today = monday.plusDays(44))).getValue(Badge.CREATINE_LOYAL), 0.0)
    }

    @Test
    fun proteinDaysAreNotConsecutiveAndUseTheWeightOfTheDay() {
        val days = List(8) { monday.plusDays(2L * it) }
        val h = BadgeHistory(today, proteinDays = days)
        assertEquals(monday.plusDays(12), h.tier(Badge.GOLDEN_SHAKER, Tier.BRONZE))

        // 2 g/kg: 80 kg → 160 g until the weigh-in of day 2, then 70 kg → 140 g; before any weigh-in: the first one.
        val weights = listOf(monday.plusDays(1) to 80.0, monday.plusDays(2) to 70.0)
        val totals = listOf(monday to 159.0, monday.plusDays(1) to 160.0, monday.plusDays(2) to 140.0, monday.plusDays(3) to 139.9)
        assertEquals(listOf(monday.plusDays(1), monday.plusDays(2)), Badges.proteinDays(totals, weights, 2.0))
        assertTrue(Badges.proteinDays(totals, emptyList(), 2.0).isEmpty())
    }

    @Test
    fun differentExercisesCountOnceFromTheirFirstDay() {
        val done = (1L..20L).flatMap { id -> listOf(id to monday.plusDays(id), id to monday.plusDays(id + 30)) }
        val h = BadgeHistory(today, exercisesDone = done)
        assertEquals(monday.plusDays(10), h.tier(Badge.ALL_ROUNDER, Tier.BRONZE))
        assertEquals(monday.plusDays(20), h.tier(Badge.ALL_ROUNDER, Tier.SILVER))
        assertEquals(20.0, Badges.progress(h).getValue(Badge.ALL_ROUNDER), 0.0)
    }

    @Test
    fun hoursOfTheStartGiveEarlyBirdAndNightOwl() {
        val h = BadgeHistory(today, sessions = listOf(session(monday, 8), session(monday.plusDays(1), 20), session(monday.plusDays(2), 7), session(monday.plusDays(3), 21)))
        assertEquals(monday.plusDays(2), h.badge(Badge.EARLY_BIRD))
        assertEquals(monday.plusDays(3), h.badge(Badge.NIGHT_OWL))
    }

    @Test
    fun tripleAIsTheWholeRotationInOneWeek() {
        val rotation = setOf(1L, 2L, 3L)
        // Week 1: A, B, B (no C); week 2: C, A, B → earned on the B of week 2.
        val sessions = listOf(
            session(monday, template = 1), session(monday.plusDays(2), template = 2), session(monday.plusDays(4), template = 2),
            session(monday.plusDays(7), template = 3), session(monday.plusDays(8), template = 1), session(monday.plusDays(10), template = 2),
        )
        assertEquals(monday.plusDays(10), BadgeHistory(today, sessions, rotation = rotation).badge(Badge.TRIPLE_A))
        assertNull(BadgeHistory(today, sessions).badge(Badge.TRIPLE_A))
    }

    @Test
    fun noJealousyNeedsEveryGroupButCardioAndOtherInOneWeek() {
        val groups = BadgeConfig.ALL_GROUPS.toList()
        val week1 = groups.dropLast(1).map { it to monday } + (MuscleGroup.CARDIO to monday)
        val week2 = groups.mapIndexed { i, g -> g to monday.plusDays(7L + i % 3) }
        assertNull(BadgeHistory(today, musclesDone = week1).badge(Badge.NO_JEALOUSY))
        assertEquals(monday.plusDays(9), BadgeHistory(today, musclesDone = week1 + week2).badge(Badge.NO_JEALOUSY))
    }

    @Test
    fun secretBadges() {
        val newYear = LocalDate.of(2027, 1, 1)
        val sessions = listOf(
            session(monday), session(monday.plusDays(6)), // Monday, Sunday
            session(monday.plusDays(7), minutes = 75), session(monday.plusDays(14), minutes = 76),
            session(monday.plusDays(21)),
            // 30 whole days without a session before this one.
            session(monday.plusDays(52)),
            session(newYear, hour = 23, minutes = 90), // ends on 2 January
            session(LocalDate.of(2028, 1, 1)),
        )
        val h = BadgeHistory(LocalDate.of(2028, 6, 1), sessions)
        assertEquals(monday.plusDays(6), h.badge(Badge.SPORTY_SUNDAY))
        assertEquals(monday.plusDays(14), h.badge(Badge.MARATHON))
        assertEquals(monday.plusDays(52), h.badge(Badge.RETURN_OF_THE_KING))
        assertEquals(LocalDate.of(2028, 1, 1), h.badge(Badge.NEW_YEAR))
        // Sessions ended on Mondays: 7/9, 14/9, 21/9, 28/9.
        assertEquals(monday.plusDays(21), h.badge(Badge.MONDAY_FEARLESS))

        val gap = BadgeHistory(today, listOf(session(monday), session(monday.plusDays(30))))
        assertNull("29 days without a session", gap.badge(Badge.RETURN_OF_THE_KING))
    }

    @Test
    fun anniversaryNeedsARecentSession() {
        val first = LocalDate.of(2025, 3, 10)
        val anniversary = first.plusDays(365)
        // A session in the 30 days before: the anniversary itself.
        val active = BadgeHistory(anniversary.plusDays(10), listOf(session(first), session(anniversary.minusDays(30))))
        assertEquals(anniversary, active.badge(Badge.ANNIVERSARY))
        // Not yet a year.
        assertNull(active.copy(today = anniversary.minusDays(1)).badge(Badge.ANNIVERSARY))
        // No session around it: the first session after it.
        val back = BadgeHistory(anniversary.plusDays(100), listOf(session(first), session(anniversary.minusDays(31)), session(anniversary.plusDays(40))))
        assertEquals(anniversary.plusDays(40), back.badge(Badge.ANNIVERSARY))
        assertNull(back.copy(sessions = back.sessions.dropLast(1)).badge(Badge.ANNIVERSARY))
    }

    @Test
    fun rowerIsTheLinkedOneOrNamedRameur() {
        assertTrue(Badges.isRower("Rameur Concept2", BadgeConfig.ROWER_LIBRARY_ID))
        assertTrue(Badges.isRower(" rameur ", null))
        assertFalse(Badges.isRower("Rameur", "seated-row"))
        assertFalse(Badges.isRower("Rowing machine", null))
    }

    @Test
    fun pointsAndLevels() {
        val awards = listOf(
            Award(Badge.REPEAT_OFFENDER, Tier.BRONZE), Award(Badge.REPEAT_OFFENDER, Tier.SILVER), // 35
            Award(Badge.NOT_DEAD), Award(Badge.WELL_STARTED), // 20
            Award(Badge.MARATHON), // 15
        )
        assertEquals(70, Badges.points(awards))
        assertEquals(2, Badges.level(70).number)
        assertEquals(30, Badges.level(70).floor)
        assertEquals(100, Badges.level(70).next)
        assertEquals(3, Badges.level(100).number)
        assertEquals(7, Badges.level(1_400).number)
        assertNull(Badges.level(5_000).next)
        assertEquals(10 + 25 + 50 + 100, Tier.entries.sumOf { BadgeConfig.points(Award(Badge.MOVER, it)) })
    }

    @Test
    fun configCoversEveryTieredBadgeWithIncreasingThresholds() {
        Badge.entries.filter { it.kind == BadgeKind.TIERED }.forEach { badge ->
            val thresholds = BadgeConfig.TIERS.getValue(badge)
            assertEquals(Tier.entries.size, thresholds.size)
            assertEquals(thresholds.sorted(), thresholds)
        }
        assertEquals(Badge.entries.count { it.kind == BadgeKind.TIERED }, BadgeConfig.TIERS.size)
    }

    @Test
    fun v1BadgesBecomeTheMatchingTier() {
        assertEquals("REPEAT_OFFENDER" to 2, BadgeMigration.fromV1("PILLAR"))
        assertEquals("REPEAT_OFFENDER" to 1, BadgeMigration.fromV1("REPEAT_OFFENDER"))
        assertEquals("CREATINE_LOYAL" to 2, BadgeMigration.fromV1("CREATINE_LOYAL"))
        assertEquals("ALL_ROUNDER" to 1, BadgeMigration.fromV1("ALL_ROUNDER"))
        assertEquals("BEATS_THE_COUCH" to 1, BadgeMigration.fromV1("BEATS_THE_COUCH"))
        listOf("NOT_DEAD", "WELL_STARTED", "EARLY_BIRD", "REGULAR", "FIRST_RECORD").forEach { assertEquals(it to 0, BadgeMigration.fromV1(it)) }
        // Every converted id is a badge of V2.18, with a tier only when it has tiers.
        listOf("NOT_DEAD", "WELL_STARTED", "REPEAT_OFFENDER", "PILLAR", "BEATS_THE_COUCH", "EARLY_BIRD", "REGULAR", "FIRST_RECORD", "ALL_ROUNDER", "CREATINE_LOYAL")
            .map(BadgeMigration::fromV1)
            .forEach { (id, tier) -> assertEquals(Badge.valueOf(id).kind == BadgeKind.TIERED, tier > 0) }
    }

    // --- Hydration (V2.29) ---

    private fun water(day: LocalDate, ml: Int, goal: Int = 2_500) = WaterDay(day, ml, goal)

    @Test
    fun camelRepentantCountsTheDaysWithTheGoalReachedNotInARow() {
        // Every other day reached: 7 days reached on the 13th day.
        val days = (0L until 30L).map { monday.plusDays(it) }.mapIndexed { i, d -> water(d, if (i % 2 == 0) 2_600 else 1_000) }
        val h = BadgeHistory(today, water = days)
        assertEquals(monday.plusDays(12), h.tier(Badge.CAMEL_REPENTANT, Tier.BRONZE))
        assertNull(h.tier(Badge.CAMEL_REPENTANT, Tier.SILVER))
        assertEquals(15.0, Badges.progress(h).getValue(Badge.CAMEL_REPENTANT), 0.0)
        // Never a streak: no Source intarissable.
        assertNull(h.tier(Badge.ENDLESS_SOURCE, Tier.BRONZE))
        // The goal of each day counts: 2.6 L is not enough on a 3 L day.
        assertTrue(earned(BadgeHistory(today, water = (0L..10L).map { water(monday.plusDays(it), 2_600, goal = 3_000) })).isEmpty())
    }

    @Test
    fun endlessSourceNeedsDaysInARow() {
        val run = (0L until 7L).map { water(monday.plusDays(it), 3_000) }
        val h = BadgeHistory(today, water = run)
        assertEquals(monday.plusDays(2), h.tier(Badge.ENDLESS_SOURCE, Tier.BRONZE))
        assertEquals(monday.plusDays(6), h.tier(Badge.ENDLESS_SOURCE, Tier.SILVER))
        assertEquals(monday.plusDays(6), h.tier(Badge.CAMEL_REPENTANT, Tier.BRONZE))
        // A day missed breaks the run.
        val broken = BadgeHistory(today, water = run.filterNot { it.date == monday.plusDays(2) })
        assertEquals(monday.plusDays(5), broken.tier(Badge.ENDLESS_SOURCE, Tier.BRONZE))
        // Current run: today not reached yet does not break it.
        val ongoing = BadgeHistory(monday.plusDays(7), water = run)
        assertEquals(7.0, Badges.progress(ongoing).getValue(Badge.ENDLESS_SOURCE), 0.0)
    }

    @Test
    fun oasisIsThreeLitresOnADayWithoutASessionOnceTheDayIsOver() {
        val tuesday = monday.plusDays(1)
        // 3 L on a session day: no.
        val withSession = BadgeHistory(today, sessions = listOf(session(monday)), water = listOf(water(monday, 3_200)))
        assertNull(withSession.badge(Badge.OASIS))
        // 2.9 L on a rest day: no.
        assertNull(BadgeHistory(today, water = listOf(water(tuesday, 2_900))).badge(Badge.OASIS))
        // 3 L on a rest day: yes, dated that day.
        assertEquals(tuesday, BadgeHistory(today, water = listOf(water(tuesday, 3_000))).badge(Badge.OASIS))
        // Today is not over: not yet.
        assertNull(BadgeHistory(tuesday, water = listOf(water(tuesday, 3_500))).badge(Badge.OASIS))
        assertEquals(BadgeKind.SECRET, Badge.OASIS.kind)
    }

    @Test
    fun hydrationTiersAreWorthTheUsualPoints() {
        assertEquals(listOf(7.0, 30.0, 100.0, 250.0), BadgeConfig.TIERS.getValue(Badge.CAMEL_REPENTANT))
        assertEquals(listOf(3.0, 7.0, 21.0, 60.0), BadgeConfig.TIERS.getValue(Badge.ENDLESS_SOURCE))
        assertEquals(listOf(10, 25, 50, 100), Tier.entries.map { BadgeConfig.points(Award(Badge.CAMEL_REPENTANT, it)) })
        assertEquals(BadgeConfig.SECRET_POINTS, BadgeConfig.points(Award(Badge.OASIS)))
    }

    @Test
    fun hydrationBadgesAreIdempotent() {
        val h = BadgeHistory(today, water = (0L until 30L).map { water(monday.plusDays(it), 3_000) })
        assertEquals(earned(h), earned(h))
    }
}
