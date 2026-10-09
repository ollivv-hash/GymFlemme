package fr.olivier.forme.feature.badges.ui

import fr.olivier.forme.R
import fr.olivier.forme.core.humor.Quip
import fr.olivier.forme.feature.badges.domain.Badge

/** Resources of a badge (V2.17): its name (feminine one when it agrees), its condition and its message. */
data class BadgeTexts(val name: Int, val nameFeminine: Int?, val condition: Int, val quip: Quip) {
    fun name(feminine: Boolean): Int = nameFeminine?.takeIf { feminine } ?: name

    companion object {
        fun of(badge: Badge): BadgeTexts = when (badge) {
            Badge.NOT_DEAD -> BadgeTexts(R.string.badge_name_not_dead, R.string.badge_name_not_dead_f, R.string.badge_condition_not_dead, Quip.BADGE_NOT_DEAD)
            Badge.WELL_STARTED -> BadgeTexts(R.string.badge_name_well_started, R.string.badge_name_well_started_f, R.string.badge_condition_well_started, Quip.BADGE_WELL_STARTED)
            Badge.REPEAT_OFFENDER -> BadgeTexts(R.string.badge_name_repeat_offender, null, R.string.badge_condition_repeat_offender, Quip.BADGE_REPEAT_OFFENDER)
            Badge.PILLAR -> BadgeTexts(R.string.badge_name_pillar, null, R.string.badge_condition_pillar, Quip.BADGE_PILLAR)
            Badge.BEATS_THE_COUCH -> BadgeTexts(R.string.badge_name_beats_the_couch, R.string.badge_name_beats_the_couch_f, R.string.badge_condition_beats_the_couch, Quip.BADGE_BEATS_THE_COUCH)
            Badge.EARLY_BIRD -> BadgeTexts(R.string.badge_name_early_bird, R.string.badge_name_early_bird_f, R.string.badge_condition_early_bird, Quip.BADGE_EARLY_BIRD)
            Badge.REGULAR -> BadgeTexts(R.string.badge_name_regular, R.string.badge_name_regular_f, R.string.badge_condition_regular, Quip.BADGE_REGULAR)
            Badge.FIRST_RECORD -> BadgeTexts(R.string.badge_name_first_record, null, R.string.badge_condition_first_record, Quip.BADGE_FIRST_RECORD)
            Badge.ALL_ROUNDER -> BadgeTexts(R.string.badge_name_all_rounder, null, R.string.badge_condition_all_rounder, Quip.BADGE_ALL_ROUNDER)
            Badge.CREATINE_LOYAL -> BadgeTexts(R.string.badge_name_creatine_loyal, null, R.string.badge_condition_creatine_loyal, Quip.BADGE_CREATINE_LOYAL)
        }
    }
}
