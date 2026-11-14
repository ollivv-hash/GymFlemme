package fr.olivier.forme.feature.badges.domain

import fr.olivier.forme.feature.sport.domain.MuscleGroup
import java.time.LocalTime

/**
 * Every threshold, point and level of the badges (V2.18), in one place: adjusting a value here is enough, the
 * computation, the screens and the tests read them from here.
 */
object BadgeConfig {
    // --- Points ---
    val TIER_POINTS = mapOf(Tier.BRONZE to 10, Tier.SILVER to 25, Tier.GOLD to 50, Tier.PLATINUM to 100)
    const val ONE_OFF_POINTS = 10
    const val SECRET_POINTS = 15

    // --- Tiered badges: Bronze, Argent, Or, Platine (in the measure's unit) ---
    val TIERS: Map<Badge, List<Double>> = mapOf(
        Badge.REPEAT_OFFENDER to listOf(10.0, 50.0, 150.0, 300.0), // finished sessions
        Badge.BEATS_THE_COUCH to listOf(4.0, 12.0, 26.0, 52.0), // weeks in a row with a session
        Badge.MOVER to listOf(10_000.0, 50_000.0, 200_000.0, 500_000.0), // kg lifted (10 t … 500 t)
        Badge.GALLEY_SLAVE to listOf(10_000.0, 50_000.0, 200_000.0, 500_000.0), // metres rowed (10 km … 500 km)
        Badge.RECORD_COLLECTOR to listOf(5.0, 25.0, 75.0, 150.0), // records beaten
        Badge.CREATINE_LOYAL to listOf(7.0, 30.0, 90.0, 365.0), // days in a row
        Badge.GOLDEN_SHAKER to listOf(7.0, 30.0, 100.0, 250.0), // days with the protein target reached
        Badge.ALL_ROUNDER to listOf(10.0, 20.0, 35.0, 50.0), // different exercises
        Badge.CAMEL_REPENTANT to listOf(7.0, 30.0, 100.0, 250.0), // days with the hydration goal reached (V2.29)
        Badge.ENDLESS_SOURCE to listOf(3.0, 7.0, 21.0, 60.0), // days in a row with the hydration goal reached
    )

    // --- One-off badges ---
    const val NOT_DEAD_SESSIONS = 1
    const val WELL_STARTED_SESSIONS = 5
    const val REGULAR_SESSIONS_IN_A_WEEK = 3
    /** Session started strictly before. */
    val EARLY_BEFORE: LocalTime = LocalTime.of(8, 0)
    /** Session started at or after. */
    val NIGHT_FROM: LocalTime = LocalTime.of(21, 0)
    /** "Pas de jaloux": every one of these groups in the same week (Cardio and Autre left out). */
    val ALL_GROUPS: Set<MuscleGroup> = setOf(
        MuscleGroup.PECTORAUX, MuscleGroup.EPAULES, MuscleGroup.DOS, MuscleGroup.BRAS, MuscleGroup.ABDOS, MuscleGroup.JAMBES,
    )

    // --- Secret badges ---
    const val MONDAY_SESSIONS = 4
    /** Session strictly longer than this. */
    const val MARATHON_MINUTES = 75L
    /** Days between two sessions for "Le retour du roi". */
    const val RETURN_DAYS = 30L
    const val ANNIVERSARY_DAYS = 365L
    /** With a session in the days before the anniversary. */
    const val ANNIVERSARY_RECENT_DAYS = 30L

    // --- Hydration (V2.29) ---
    /** Oasis: hydration counted on a day without a session, in ml. */
    const val OASIS_ML = 3_000
    /** Hydraté is drawn only after water recorded on at least this many days of the month before. */
    const val HYDRATED_MIN_DAYS_BEFORE = 5

    // --- Rower (Galérien) ---
    const val ROWER_LIBRARY_ID = "rowing-machine"
    const val ROWER_NAME = "Rameur"

    // --- Monthly challenges (V2.19) ---
    const val CHALLENGE_POINTS = 20
    /** Goal of each challenge in its unit (Zéro semaine blanche: every week of the month, Triple séance: per template). */
    val CHALLENGE_GOALS: Map<Challenge, Double> = mapOf(
        Challenge.TWELVE_STRIKES to 12.0, // sessions
        Challenge.ROWER_OF_THE_MONTH to 30_000.0, // metres rowed
        Challenge.RECORD_HUNTER to 3.0, // records beaten
        Challenge.NO_BLANK_WEEK to 1.0, // session(s) each week whose Monday is in the month
        Challenge.CURIOUS to 3.0, // exercises never done before
        Challenge.PROTEIN_DATE to 20.0, // days with the protein target reached
        Challenge.TRIPLE_SESSION to 3.0, // sessions of each template of the rotation
        Challenge.HYDRATED to 20.0, // days with the hydration goal reached (V2.29)
    )
    /** Triple séance is drawn only with at least this many templates in the rotation. */
    const val TRIPLE_SESSION_MIN_TEMPLATES = 3

    // --- Levels: points needed for level 1, 2, … ---
    val LEVELS: List<Int> = listOf(0, 30, 100, 250, 500, 900, 1400)

    fun points(award: Award): Int = when {
        award.tier != null -> TIER_POINTS.getValue(award.tier)
        award.badge.kind == BadgeKind.SECRET -> SECRET_POINTS
        else -> ONE_OFF_POINTS
    }
}
