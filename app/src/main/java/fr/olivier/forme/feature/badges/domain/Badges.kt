package fr.olivier.forme.feature.badges.domain

import fr.olivier.forme.feature.sport.domain.MuscleGroup
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class BadgeKind { TIERED, ONE_OFF, SECRET }

/**
 * The GymFlemme badges (V2.18): effort and regularity, never body weight. Ids are stored: never rename them
 * (REPEAT_OFFENDER, BEATS_THE_COUCH, ALL_ROUNDER, CREATINE_LOYAL and the one-off ones keep their V2.17 ids).
 */
enum class Badge(val kind: BadgeKind) {
    REPEAT_OFFENDER(BadgeKind.TIERED),
    BEATS_THE_COUCH(BadgeKind.TIERED),
    MOVER(BadgeKind.TIERED),
    GALLEY_SLAVE(BadgeKind.TIERED),
    RECORD_COLLECTOR(BadgeKind.TIERED),
    CREATINE_LOYAL(BadgeKind.TIERED),
    GOLDEN_SHAKER(BadgeKind.TIERED),
    ALL_ROUNDER(BadgeKind.TIERED),
    /** V2.29: days with the hydration goal reached (not in a row). */
    CAMEL_REPENTANT(BadgeKind.TIERED),
    /** V2.29: days in a row with the hydration goal reached. */
    ENDLESS_SOURCE(BadgeKind.TIERED),

    NOT_DEAD(BadgeKind.ONE_OFF),
    WELL_STARTED(BadgeKind.ONE_OFF),
    REGULAR(BadgeKind.ONE_OFF),
    FIRST_RECORD(BadgeKind.ONE_OFF),
    EARLY_BIRD(BadgeKind.ONE_OFF),
    NIGHT_OWL(BadgeKind.ONE_OFF),
    TRIPLE_A(BadgeKind.ONE_OFF),
    NO_JEALOUSY(BadgeKind.ONE_OFF),

    MONDAY_FEARLESS(BadgeKind.SECRET),
    SPORTY_SUNDAY(BadgeKind.SECRET),
    MARATHON(BadgeKind.SECRET),
    RETURN_OF_THE_KING(BadgeKind.SECRET),
    ANNIVERSARY(BadgeKind.SECRET),
    NEW_YEAR(BadgeKind.SECRET),
    /** V2.29: at least 3 L counted on a day without a session. */
    OASIS(BadgeKind.SECRET),
    ;

    companion object {
        fun byId(id: String): Badge? = entries.firstOrNull { it.name == id }
    }
}

/** Stored as its [number] (0 = no tier). */
enum class Tier(val number: Int) {
    BRONZE(1), SILVER(2), GOLD(3), PLATINUM(4);

    companion object {
        fun of(number: Int): Tier? = entries.firstOrNull { it.number == number }
    }
}

/** A badge earned: a one-off or secret badge ([tier] null), or one tier of a tiered badge. */
data class Award(val badge: Badge, val tier: Tier? = null)

/** A finished real session: started, ended (local time), from which template. */
data class SessionFact(val start: LocalDateTime, val end: LocalDateTime, val templateId: Long? = null)

/** Everything the badges are computed from, already filtered by the rules of the specification (§6). */
data class BadgeHistory(
    val today: LocalDate,
    /** Finished real sessions holding at least one set or cardio entry. */
    val sessions: List<SessionFact> = emptyList(),
    /** One entry per exercise beating its record in a session (equalled records do not count). */
    val recordDays: List<LocalDate> = emptyList(),
    /** Each exercise done (working set or cardio) and the day. */
    val exercisesDone: List<Pair<Long, LocalDate>> = emptyList(),
    /** Load × reps of each working set of a WEIGHTED exercise. */
    val liftedKg: List<Pair<LocalDate, Double>> = emptyList(),
    /** Distance of each rower entry. */
    val rowedMetres: List<Pair<LocalDate, Int>> = emptyList(),
    val creatineDays: List<LocalDate> = emptyList(),
    /** Days whose protein total reached that day's target. */
    val proteinDays: List<LocalDate> = emptyList(),
    /** Main muscle group of each working set, and the day. */
    val musclesDone: List<Pair<MuscleGroup, LocalDate>> = emptyList(),
    /** Templates of the active rotation. */
    val rotation: Set<Long> = emptySet(),
    /** V2.29: hydration counted each day with an intake, and that day's goal (frozen). */
    val water: List<WaterDay> = emptyList(),
)

/** Hydration of a day (V2.29): [countedMl] against the goal of that day. */
data class WaterDay(val date: LocalDate, val countedMl: Int, val goalMl: Int) {
    val reached get() = countedMl >= goalMl
}

/** Global level: [number] from 1, [floor] the points it needed, [next] those of the next level (null at the top). */
data class Level(val number: Int, val points: Int, val floor: Int, val next: Int?)

/**
 * The day each badge (and each tier) was earned, recomputed from the whole history: the day its condition was first
 * met. A badge never goes away once stored, even if the history changes afterwards.
 */
object Badges {
    private fun weekStart(day: LocalDate) = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun earned(history: BadgeHistory): Map<Award, LocalDate> {
        val result = mutableMapOf<Award, LocalDate>()
        tiers(history).forEach { (badge, reached) -> reached.forEach { (tier, day) -> result[Award(badge, tier)] = day } }
        oneOffs(history).forEach { (badge, day) -> result[Award(badge)] = day }
        return result
    }

    /** Current value of the measure of each tiered badge (streaks: the current run). */
    fun progress(history: BadgeHistory): Map<Badge, Double> = mapOf(
        Badge.REPEAT_OFFENDER to history.sessions.size.toDouble(),
        Badge.BEATS_THE_COUCH to currentWeekRun(history).toDouble(),
        Badge.MOVER to history.liftedKg.sumOf { it.second },
        Badge.GALLEY_SLAVE to history.rowedMetres.sumOf { it.second }.toDouble(),
        Badge.RECORD_COLLECTOR to history.recordDays.size.toDouble(),
        Badge.CREATINE_LOYAL to currentCreatineRun(history).toDouble(),
        Badge.GOLDEN_SHAKER to history.proteinDays.distinct().size.toDouble(),
        Badge.ALL_ROUNDER to history.exercisesDone.map { it.first }.distinct().size.toDouble(),
        Badge.CAMEL_REPENTANT to waterGoalDays(history).size.toDouble(),
        Badge.ENDLESS_SOURCE to currentWaterRun(history).toDouble(),
    )

    /** V2.29: days with the hydration goal reached. */
    fun waterGoalDays(history: BadgeHistory): List<LocalDate> = history.water.filter { it.reached }.map { it.date }.distinct().sorted()

    /**
     * Days whose protein total reached that day's target, computed with the weight in force that day: the last
     * weigh-in on or before it, else the first one. No weigh-in at all: no day counts.
     */
    fun proteinDays(totals: List<Pair<LocalDate, Double>>, weights: List<Pair<LocalDate, Double>>, gPerKg: Double): List<LocalDate> {
        val sorted = weights.sortedBy { it.first }
        val first = sorted.firstOrNull() ?: return emptyList()
        return totals.filter { (day, protein) ->
            val weight = sorted.lastOrNull { !it.first.isAfter(day) } ?: first
            protein >= weight.second * gPerKg
        }.map { it.first }.sorted()
    }

    /** The rower: linked to the library's rower, or named "Rameur" when not linked. */
    fun isRower(name: String, libraryId: String?): Boolean =
        libraryId == BadgeConfig.ROWER_LIBRARY_ID || (libraryId == null && name.trim().equals(BadgeConfig.ROWER_NAME, ignoreCase = true))

    fun points(awards: Collection<Award>): Int = awards.sumOf(BadgeConfig::points)

    /** Badges, tiers and monthly challenges met (V2.19). */
    fun points(awards: Collection<Award>, challengesMet: Int): Int = points(awards) + challengesMet * BadgeConfig.CHALLENGE_POINTS

    fun level(points: Int): Level {
        val number = BadgeConfig.LEVELS.count { points >= it }.coerceAtLeast(1)
        return Level(number, points, BadgeConfig.LEVELS[number - 1], BadgeConfig.LEVELS.getOrNull(number))
    }

    // --- Tiered ---

    private fun tiers(h: BadgeHistory): Map<Badge, Map<Tier, LocalDate>> {
        val sessionDays = h.sessions.map { it.start.toLocalDate() }
        fun counted(days: List<LocalDate>) = days.map { it to 1.0 }
        val firstDone = h.exercisesDone.groupBy({ it.first }, { it.second }).values.map { it.min() }
        return mapOf(
            Badge.REPEAT_OFFENDER to cumulative(Badge.REPEAT_OFFENDER, counted(sessionDays)),
            Badge.BEATS_THE_COUCH to streak(Badge.BEATS_THE_COUCH, sessionDays, ::weekStart) { it.plusWeeks(1) },
            Badge.MOVER to cumulative(Badge.MOVER, h.liftedKg),
            Badge.GALLEY_SLAVE to cumulative(Badge.GALLEY_SLAVE, h.rowedMetres.map { (day, m) -> day to m.toDouble() }),
            Badge.RECORD_COLLECTOR to cumulative(Badge.RECORD_COLLECTOR, counted(h.recordDays)),
            Badge.CREATINE_LOYAL to streak(Badge.CREATINE_LOYAL, h.creatineDays, { it }) { it.plusDays(1) },
            Badge.GOLDEN_SHAKER to cumulative(Badge.GOLDEN_SHAKER, counted(h.proteinDays.distinct())),
            Badge.ALL_ROUNDER to cumulative(Badge.ALL_ROUNDER, counted(firstDone)),
            Badge.CAMEL_REPENTANT to cumulative(Badge.CAMEL_REPENTANT, counted(waterGoalDays(h))),
            Badge.ENDLESS_SOURCE to streak(Badge.ENDLESS_SOURCE, waterGoalDays(h), { it }) { it.plusDays(1) },
        )
    }

    /** Day the running total of [events] first reached each tier. */
    private fun cumulative(badge: Badge, events: List<Pair<LocalDate, Double>>): Map<Tier, LocalDate> {
        val thresholds = BadgeConfig.TIERS.getValue(badge)
        val result = mutableMapOf<Tier, LocalDate>()
        var total = 0.0
        for ((day, value) in events.sortedBy { it.first }) {
            total += value
            Tier.entries.forEachIndexed { i, tier -> if (tier !in result && total >= thresholds[i]) result[tier] = day }
        }
        return result
    }

    /**
     * Day each tier's run of consecutive units (weeks, days) was first reached: the first day of the unit completing
     * the run.
     */
    private fun streak(
        badge: Badge,
        days: List<LocalDate>,
        unitOf: (LocalDate) -> LocalDate,
        next: (LocalDate) -> LocalDate,
    ): Map<Tier, LocalDate> {
        val thresholds = BadgeConfig.TIERS.getValue(badge)
        val firstDayByUnit = days.groupBy(unitOf).mapValues { (_, d) -> d.min() }.toSortedMap()
        val result = mutableMapOf<Tier, LocalDate>()
        var run = 0
        var previous: LocalDate? = null
        for ((unit, first) in firstDayByUnit) {
            run = if (previous != null && next(previous) == unit) run + 1 else 1
            previous = unit
            Tier.entries.forEachIndexed { i, tier -> if (tier !in result && run >= thresholds[i]) result[tier] = first }
        }
        return result
    }

    /** Weeks in a row with a session, up to now; the current week never breaks the run. */
    private fun currentWeekRun(h: BadgeHistory): Int {
        val weeks = h.sessions.map { weekStart(it.start.toLocalDate()) }.toSet()
        var week = weekStart(h.today).let { if (it in weeks) it else it.minusWeeks(1) }
        var run = 0
        while (week in weeks) {
            run++
            week = week.minusWeeks(1)
        }
        return run
    }

    /** Days in a row with creatine, up to now; today, not ticked yet, never breaks the run. */
    private fun currentCreatineRun(h: BadgeHistory): Int {
        val days = h.creatineDays.toSet()
        var day = if (h.today in days) h.today else h.today.minusDays(1)
        var run = 0
        while (day in days) {
            run++
            day = day.minusDays(1)
        }
        return run
    }

    /** Days in a row with the hydration goal reached, up to now; today, not reached yet, never breaks the run. */
    private fun currentWaterRun(h: BadgeHistory): Int {
        val days = waterGoalDays(h).toSet()
        var day = if (h.today in days) h.today else h.today.minusDays(1)
        var run = 0
        while (day in days) {
            run++
            day = day.minusDays(1)
        }
        return run
    }

    // --- One-off and secret ---

    private fun oneOffs(h: BadgeHistory): Map<Badge, LocalDate> {
        val sessions = h.sessions.sortedBy { it.start }
        val days = sessions.map { it.start.toLocalDate() }
        val result = mutableMapOf<Badge, LocalDate>()
        fun put(badge: Badge, day: LocalDate?) = day?.let { result[badge] = it }

        put(Badge.NOT_DEAD, days.getOrNull(BadgeConfig.NOT_DEAD_SESSIONS - 1))
        put(Badge.WELL_STARTED, days.getOrNull(BadgeConfig.WELL_STARTED_SESSIONS - 1))
        put(Badge.REGULAR, days.groupBy(::weekStart).values.mapNotNull { it.getOrNull(BadgeConfig.REGULAR_SESSIONS_IN_A_WEEK - 1) }.minOrNull())
        put(Badge.FIRST_RECORD, h.recordDays.minOrNull())
        put(Badge.EARLY_BIRD, sessions.firstOrNull { it.start.toLocalTime() < BadgeConfig.EARLY_BEFORE }?.start?.toLocalDate())
        put(Badge.NIGHT_OWL, sessions.firstOrNull { it.start.toLocalTime() >= BadgeConfig.NIGHT_FROM }?.start?.toLocalDate())
        put(Badge.TRIPLE_A, tripleA(sessions, h.rotation))
        put(Badge.NO_JEALOUSY, allGroupsInAWeek(h.musclesDone))

        val ends = sessions.map { it.end.toLocalDate() }.sorted()
        put(Badge.MONDAY_FEARLESS, ends.filter { it.dayOfWeek == DayOfWeek.MONDAY }.getOrNull(BadgeConfig.MONDAY_SESSIONS - 1))
        put(Badge.SPORTY_SUNDAY, ends.firstOrNull { it.dayOfWeek == DayOfWeek.SUNDAY })
        put(Badge.MARATHON, sessions.firstOrNull { Duration.between(it.start, it.end) > Duration.ofMinutes(BadgeConfig.MARATHON_MINUTES) }?.start?.toLocalDate())
        put(Badge.RETURN_OF_THE_KING, days.zipWithNext().firstOrNull { (before, after) -> daysWithout(before, after) >= BadgeConfig.RETURN_DAYS }?.second)
        put(Badge.ANNIVERSARY, anniversary(days, h.today))
        put(Badge.NEW_YEAR, ends.firstOrNull { it.monthValue == 1 && it.dayOfMonth == 1 })
        put(Badge.OASIS, oasis(h))
        return result
    }

    /**
     * V2.29: first day with at least 3 L counted and no session started. A day is judged once over (a session could
     * still come today), so the badge comes the next day at the earliest.
     */
    private fun oasis(h: BadgeHistory): LocalDate? {
        val sessionDays = h.sessions.map { it.start.toLocalDate() }.toSet()
        return h.water.filter { it.date.isBefore(h.today) && it.countedMl >= BadgeConfig.OASIS_ML && it.date !in sessionDays }
            .minOfOrNull { it.date }
    }

    /** Whole days without a session between two session days. */
    private fun daysWithout(before: LocalDate, after: LocalDate) = ChronoUnit.DAYS.between(before, after) - 1

    /** Day the last template of the rotation was done, in the first week holding them all. */
    private fun tripleA(sessions: List<SessionFact>, rotation: Set<Long>): LocalDate? {
        if (rotation.isEmpty()) return null
        return sessions.groupBy { weekStart(it.start.toLocalDate()) }.values.mapNotNull { week ->
            val seen = mutableSetOf<Long>()
            week.sortedBy { it.start }.firstOrNull { session ->
                session.templateId?.takeIf { it in rotation }?.let(seen::add)
                seen.containsAll(rotation)
            }?.start?.toLocalDate()
        }.minOrNull()
    }

    /** Day the last group was worked, in the first week holding them all. */
    private fun allGroupsInAWeek(done: List<Pair<MuscleGroup, LocalDate>>): LocalDate? =
        done.groupBy { weekStart(it.second) }.values.mapNotNull { week ->
            val seen = mutableSetOf<MuscleGroup>()
            week.sortedBy { it.second }.firstOrNull { (group, _) ->
                if (group in BadgeConfig.ALL_GROUPS) seen += group
                seen.containsAll(BadgeConfig.ALL_GROUPS)
            }?.second
        }.minOrNull()

    /**
     * First day X at least a year after the first session with a session in the 30 days up to X: the anniversary
     * itself when sessions were going on, else the first session after it.
     */
    private fun anniversary(days: List<LocalDate>, today: LocalDate): LocalDate? {
        val first = days.minOrNull() ?: return null
        val date = first.plusDays(BadgeConfig.ANNIVERSARY_DAYS)
        if (date.isAfter(today)) return null
        val recentFrom = date.minusDays(BadgeConfig.ANNIVERSARY_RECENT_DAYS)
        if (days.any { !it.isBefore(recentFrom) && !it.isAfter(date) }) return date
        return days.filter { it.isAfter(date) }.minOrNull()
    }
}
