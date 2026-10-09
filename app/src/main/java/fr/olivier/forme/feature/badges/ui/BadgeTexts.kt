package fr.olivier.forme.feature.badges.ui

import android.content.res.Resources
import fr.olivier.forme.R
import fr.olivier.forme.core.humor.QuipStyle
import fr.olivier.forme.feature.badges.domain.Award
import fr.olivier.forme.feature.badges.domain.Badge
import fr.olivier.forme.feature.badges.domain.Tier

/**
 * Resources of a badge (V2.18): its name (feminine one when it agrees), its condition (one-off and secret badges) or
 * unit (tiered badges), and its message(s): one per tier (string array) or a single one.
 */
data class BadgeTexts(
    val name: Int,
    val nameFeminine: Int? = null,
    val condition: Int? = null,
    val unit: Int? = null,
    val tierMessages: Int? = null,
    val message: Int? = null,
    val messageFeminine: Int? = null,
) {
    companion object {
        fun of(badge: Badge): BadgeTexts = when (badge) {
            Badge.REPEAT_OFFENDER -> BadgeTexts(R.string.badge_name_repeat_offender, unit = R.string.badge_unit_repeat_offender, tierMessages = R.array.badge_msg_repeat_offender)
            Badge.BEATS_THE_COUCH -> BadgeTexts(R.string.badge_name_beats_the_couch, R.string.badge_name_beats_the_couch_f, unit = R.string.badge_unit_beats_the_couch, tierMessages = R.array.badge_msg_beats_the_couch)
            Badge.MOVER -> BadgeTexts(R.string.badge_name_mover, R.string.badge_name_mover_f, unit = R.string.badge_unit_mover, tierMessages = R.array.badge_msg_mover)
            Badge.GALLEY_SLAVE -> BadgeTexts(R.string.badge_name_galley_slave, R.string.badge_name_galley_slave_f, unit = R.string.badge_unit_galley_slave, tierMessages = R.array.badge_msg_galley_slave)
            Badge.RECORD_COLLECTOR -> BadgeTexts(R.string.badge_name_record_collector, R.string.badge_name_record_collector_f, unit = R.string.badge_unit_record_collector, tierMessages = R.array.badge_msg_record_collector)
            Badge.CREATINE_LOYAL -> BadgeTexts(R.string.badge_name_creatine_loyal, unit = R.string.badge_unit_creatine_loyal, tierMessages = R.array.badge_msg_creatine_loyal)
            Badge.GOLDEN_SHAKER -> BadgeTexts(R.string.badge_name_golden_shaker, unit = R.string.badge_unit_golden_shaker, tierMessages = R.array.badge_msg_golden_shaker)
            Badge.ALL_ROUNDER -> BadgeTexts(R.string.badge_name_all_rounder, unit = R.string.badge_unit_all_rounder, tierMessages = R.array.badge_msg_all_rounder)

            Badge.NOT_DEAD -> BadgeTexts(R.string.badge_name_not_dead, R.string.badge_name_not_dead_f, R.string.badge_condition_not_dead, message = R.string.badge_msg_not_dead, messageFeminine = R.string.badge_msg_not_dead_f)
            Badge.WELL_STARTED -> BadgeTexts(R.string.badge_name_well_started, R.string.badge_name_well_started_f, R.string.badge_condition_well_started, message = R.string.badge_msg_well_started)
            Badge.REGULAR -> BadgeTexts(R.string.badge_name_regular, R.string.badge_name_regular_f, R.string.badge_condition_regular, message = R.string.badge_msg_regular)
            Badge.FIRST_RECORD -> BadgeTexts(R.string.badge_name_first_record, condition = R.string.badge_condition_first_record, message = R.string.badge_msg_first_record)
            Badge.EARLY_BIRD -> BadgeTexts(R.string.badge_name_early_bird, R.string.badge_name_early_bird_f, R.string.badge_condition_early_bird, message = R.string.badge_msg_early_bird, messageFeminine = R.string.badge_msg_early_bird_f)
            Badge.NIGHT_OWL -> BadgeTexts(R.string.badge_name_night_owl, condition = R.string.badge_condition_night_owl, message = R.string.badge_msg_night_owl)
            Badge.TRIPLE_A -> BadgeTexts(R.string.badge_name_triple_a, condition = R.string.badge_condition_triple_a, message = R.string.badge_msg_triple_a)
            Badge.NO_JEALOUSY -> BadgeTexts(R.string.badge_name_no_jealousy, condition = R.string.badge_condition_no_jealousy, message = R.string.badge_msg_no_jealousy)

            Badge.MONDAY_FEARLESS -> BadgeTexts(R.string.badge_name_monday_fearless, condition = R.string.badge_condition_monday_fearless, message = R.string.badge_msg_monday_fearless)
            Badge.SPORTY_SUNDAY -> BadgeTexts(R.string.badge_name_sporty_sunday, condition = R.string.badge_condition_sporty_sunday, message = R.string.badge_msg_sporty_sunday)
            Badge.MARATHON -> BadgeTexts(R.string.badge_name_marathon, condition = R.string.badge_condition_marathon, message = R.string.badge_msg_marathon)
            Badge.RETURN_OF_THE_KING -> BadgeTexts(R.string.badge_name_return_of_the_king, R.string.badge_name_return_of_the_king_f, R.string.badge_condition_return_of_the_king, message = R.string.badge_msg_return_of_the_king, messageFeminine = R.string.badge_msg_return_of_the_king_f)
            Badge.ANNIVERSARY -> BadgeTexts(R.string.badge_name_anniversary, condition = R.string.badge_condition_anniversary, message = R.string.badge_msg_anniversary)
            Badge.NEW_YEAR -> BadgeTexts(R.string.badge_name_new_year, condition = R.string.badge_condition_new_year, message = R.string.badge_msg_new_year)
        }
    }
}

/** Badge texts worded for the user: gender agreement, serious mode. */
object BadgeWording {
    fun name(resources: Resources, badge: Badge, feminine: Boolean): String {
        val texts = BadgeTexts.of(badge)
        return resources.getString(texts.nameFeminine?.takeIf { feminine } ?: texts.name)
    }

    fun tier(resources: Resources, tier: Tier): String = resources.getStringArray(R.array.badge_tiers)[tier.number - 1]

    /** "Récidiviste — Argent", or the name alone. */
    fun title(resources: Resources, award: Award, feminine: Boolean): String =
        name(resources, award.badge, feminine) + (award.tier?.let { " — " + tier(resources, it) } ?: "")

    /** The message of an award; serious mode: "Badge {nom} : palier {palier} obtenu" or "Badge obtenu : {nom}". */
    fun message(resources: Resources, award: Award, style: QuipStyle): String {
        val texts = BadgeTexts.of(award.badge)
        val name = name(resources, award.badge, style.feminine)
        val tier = award.tier
        if (style.serious) {
            return if (tier != null) resources.getString(R.string.badge_neutral_tier, name, tier(resources, tier)) else resources.getString(R.string.badge_neutral, name)
        }
        if (tier != null) return resources.getStringArray(texts.tierMessages ?: error("${award.badge} has no tiers"))[tier.number - 1]
        return resources.getString(texts.messageFeminine?.takeIf { style.feminine } ?: texts.message ?: error("${award.badge} has no message"))
    }

    /** The title of a level, agreed; never replaced (level-up message in serious mode). */
    fun levelTitle(resources: Resources, level: Int, feminine: Boolean): String =
        resources.getStringArray(if (feminine) R.array.badge_level_titles_f else R.array.badge_level_titles)[level - 1]

    /** The title shown for a level: "Niveau {n}" in serious mode. */
    fun levelDisplay(resources: Resources, level: Int, style: QuipStyle): String =
        if (style.serious) resources.getString(R.string.badge_level_serious, level) else levelTitle(resources, level, style.feminine)
}
