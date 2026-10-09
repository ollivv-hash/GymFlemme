package fr.olivier.forme.feature.badges.ui

import android.content.res.Resources
import fr.olivier.forme.R
import fr.olivier.forme.core.humor.QuipStyle
import fr.olivier.forme.feature.badges.domain.Challenge
import fr.olivier.forme.feature.sport.domain.Fmt
import java.time.YearMonth
import java.time.format.TextStyle

/** Texts of a monthly challenge (V2.19): name, goal, announcement, success, progress unit. */
data class ChallengeTexts(val name: Int, val goal: Int, val announce: Int, val success: Int, val unit: Int) {
    companion object {
        fun of(challenge: Challenge): ChallengeTexts = when (challenge) {
            Challenge.TWELVE_STRIKES -> ChallengeTexts(R.string.challenge_name_twelve_strikes, R.string.challenge_goal_twelve_strikes, R.string.challenge_announce_twelve_strikes, R.string.challenge_success_twelve_strikes, R.string.challenge_unit_twelve_strikes)
            Challenge.ROWER_OF_THE_MONTH -> ChallengeTexts(R.string.challenge_name_rower_of_the_month, R.string.challenge_goal_rower_of_the_month, R.string.challenge_announce_rower_of_the_month, R.string.challenge_success_rower_of_the_month, R.string.challenge_unit_rower_of_the_month)
            Challenge.RECORD_HUNTER -> ChallengeTexts(R.string.challenge_name_record_hunter, R.string.challenge_goal_record_hunter, R.string.challenge_announce_record_hunter, R.string.challenge_success_record_hunter, R.string.challenge_unit_record_hunter)
            Challenge.NO_BLANK_WEEK -> ChallengeTexts(R.string.challenge_name_no_blank_week, R.string.challenge_goal_no_blank_week, R.string.challenge_announce_no_blank_week, R.string.challenge_success_no_blank_week, R.string.challenge_unit_no_blank_week)
            Challenge.CURIOUS -> ChallengeTexts(R.string.challenge_name_curious, R.string.challenge_goal_curious, R.string.challenge_announce_curious, R.string.challenge_success_curious, R.string.challenge_unit_curious)
            Challenge.PROTEIN_DATE -> ChallengeTexts(R.string.challenge_name_protein_date, R.string.challenge_goal_protein_date, R.string.challenge_announce_protein_date, R.string.challenge_success_protein_date, R.string.challenge_unit_protein_date)
            Challenge.TRIPLE_SESSION -> ChallengeTexts(R.string.challenge_name_triple_session, R.string.challenge_goal_triple_session, R.string.challenge_announce_triple_session, R.string.challenge_success_triple_session, R.string.challenge_unit_triple_session)
        }
    }
}

object ChallengeWording {
    fun name(resources: Resources, challenge: Challenge): String = resources.getString(ChallengeTexts.of(challenge).name)

    /** Start of the month: the announcement, or "Défi du mois : {objectif}" in serious mode. */
    fun announce(resources: Resources, challenge: Challenge, style: QuipStyle): String {
        val texts = ChallengeTexts.of(challenge)
        return if (style.serious) resources.getString(R.string.challenge_neutral_announce, resources.getString(texts.goal)) else resources.getString(texts.announce)
    }

    /** Challenge met: its message, or "Défi du mois réussi" in serious mode. */
    fun success(resources: Resources, challenge: Challenge, style: QuipStyle): String =
        if (style.serious) resources.getString(R.string.challenge_neutral_success) else resources.getString(ChallengeTexts.of(challenge).success)

    fun monthName(month: YearMonth): String = month.month.getDisplayName(TextStyle.FULL_STANDALONE, Fmt.locale).lowercase(Fmt.locale)

    /** "Défi d'octobre 2026", "Défi de mars 2027". */
    fun badge(resources: Resources, month: YearMonth): String {
        val name = monthName(month)
        val elided = name.first() in "aeiouyàâéèêîôû"
        return resources.getString(if (elided) R.string.challenge_badge_elided else R.string.challenge_badge, name, month.year)
    }

    /** "Défi d'octobre" (current month, no year). */
    fun ofMonth(resources: Resources, month: YearMonth): String = badge(resources, month).removeSuffix(" ${month.year}")
}
