package fr.olivier.forme.core.humor

import android.content.res.Resources
import fr.olivier.forme.R
import java.time.LocalTime
import kotlin.random.Random

/**
 * An entry of the humour catalogue (V2.15, res/values/humor.xml): its variants, its neutral version for serious mode
 * and, when the wording agrees with the user's gender, the feminine versions aligned item by item.
 */
enum class Quip(
    val humor: Int,
    val neutral: Int,
    val humorFeminine: Int? = null,
    val neutralFeminine: Int? = null,
) {
    LOADING(R.array.humor_loading, R.string.neutral_loading),
    GREETING_MORNING(R.array.humor_greeting_morning, R.string.neutral_greeting_morning),
    GREETING_NOON(R.array.humor_greeting_noon, R.string.neutral_greeting_noon),
    GREETING_AFTERNOON(R.array.humor_greeting_afternoon, R.string.neutral_greeting_afternoon),
    GREETING_EVENING(R.array.humor_greeting_evening, R.string.neutral_greeting_evening),
    GREETING_NIGHT(R.array.humor_greeting_night, R.string.neutral_greeting_night),
    START(R.array.humor_start, R.string.neutral_start, neutralFeminine = R.string.neutral_start_f),
    REST_DONE(R.array.humor_rest_done, R.string.neutral_rest_done),
    SET_DONE(R.array.humor_set_done, R.string.neutral_set_done),
    EXERCISE_DONE(R.array.humor_exercise_done, R.string.neutral_exercise_done),
    PROGRESSION(R.array.humor_progression, R.string.neutral_progression, humorFeminine = R.array.humor_progression_f),
    RECORD(R.array.humor_record, R.string.neutral_record),
    SESSION_DONE(R.array.humor_session_done, R.string.neutral_session_done, humorFeminine = R.array.humor_session_done_f),
    CREATINE(R.array.humor_creatine, R.string.neutral_creatine),
    CREATINE_STREAK(R.array.humor_creatine_streak, R.string.neutral_creatine_streak),
    PROTEIN_MISSING(R.array.humor_protein_missing, R.string.neutral_protein_missing),
    PROTEIN_ALMOST(R.array.humor_protein_almost, R.string.neutral_protein_almost),
    PROTEIN_REACHED(R.array.humor_protein_reached, R.string.neutral_protein_reached),
    EMPTY_SESSIONS(R.array.humor_empty_sessions, R.string.neutral_empty_sessions),
    EMPTY_RECORDS(R.array.humor_empty_records, R.string.neutral_empty_records),
    EMPTY_MEALS(R.array.humor_empty_meals, R.string.neutral_empty_meals),
    EMPTY_CATALOGUE(R.array.humor_empty_catalogue, R.string.neutral_empty_catalogue),
    EMPTY_EXERCISE_HISTORY(R.array.humor_empty_exercise_history, R.string.neutral_empty_exercise_history),
    ;

    companion object {
        /** At most this many grams of protein missing: "Presque !" may be drawn. */
        const val PROTEIN_ALMOST_G = 30

        fun proteinMissing(missingG: Int): Quip = if (missingG <= PROTEIN_ALMOST_G) PROTEIN_ALMOST else PROTEIN_MISSING

        /** Greeting slot by local time: morning 5–11 h, noon 11–14 h, afternoon 14–18 h, evening 18–23 h, night. */
        fun greeting(time: LocalTime): Quip = when (time.hour) {
            in 5..10 -> GREETING_MORNING
            in 11..13 -> GREETING_NOON
            in 14..17 -> GREETING_AFTERNOON
            in 18..22 -> GREETING_EVENING
            else -> GREETING_NIGHT
        }
    }
}

/** How quips are worded: neutral in serious mode, feminine agreement when the profile says so. */
data class QuipStyle(val serious: Boolean = false, val feminine: Boolean = false)

object Quips {
    /** A variant index other than [last] (the same one only when there is a single variant). */
    fun nextIndex(size: Int, last: Int?, random: Random = Random.Default): Int {
        if (size <= 1) return 0
        if (last == null || last !in 0 until size) return random.nextInt(size)
        return (last + 1 + random.nextInt(size - 1)) % size
    }

    /** The text of variant [index] of [quip], formatted with [args]. */
    fun text(resources: Resources, quip: Quip, index: Int, style: QuipStyle, vararg args: Any): String {
        val template = if (style.serious) {
            resources.getString(quip.neutralFeminine?.takeIf { style.feminine } ?: quip.neutral)
        } else {
            val variants = resources.getStringArray(quip.humorFeminine?.takeIf { style.feminine } ?: quip.humor)
            variants[index.coerceIn(0, variants.size - 1)]
        }
        return if (args.isEmpty()) template else String.format(java.util.Locale.FRANCE, template, *args)
    }
}
