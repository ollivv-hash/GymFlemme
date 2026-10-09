package fr.olivier.forme.feature.badges

import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.R
import fr.olivier.forme.core.db.context
import fr.olivier.forme.core.humor.QuipStyle
import fr.olivier.forme.feature.badges.domain.Award
import fr.olivier.forme.feature.badges.domain.Badge
import fr.olivier.forme.feature.badges.domain.BadgeConfig
import fr.olivier.forme.feature.badges.domain.BadgeKind
import fr.olivier.forme.feature.badges.domain.Tier
import fr.olivier.forme.feature.badges.ui.BadgeTexts
import fr.olivier.forme.feature.badges.ui.BadgeWording
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** V2.18: every badge, tier and level has its texts, in humour and serious mode, agreed with the profile. */
@RunWith(AndroidJUnit4::class)
class BadgeTextsTest {
    private val resources = context().resources
    private val styles = listOf(QuipStyle(), QuipStyle(feminine = true), QuipStyle(serious = true), QuipStyle(serious = true, feminine = true))

    private fun awards() = Badge.entries.flatMap { badge ->
        if (badge.kind == BadgeKind.TIERED) Tier.entries.map { Award(badge, it) } else listOf(Award(badge))
    }

    @Test
    fun everyAwardHasANameAndAMessageInEveryStyle() {
        awards().forEach { award ->
            styles.forEach { style ->
                val text = BadgeWording.message(resources, award, style)
                assertTrue("$award $style: $text", text.isNotBlank() && !Regex("%\\d*\\$?[sd]").containsMatchIn(text))
                assertTrue(BadgeWording.title(resources, award, style.feminine).isNotBlank())
            }
            val texts = BadgeTexts.of(award.badge)
            if (award.badge.kind == BadgeKind.TIERED) {
                assertEquals(Tier.entries.size, resources.getStringArray(texts.tierMessages!!).size)
                assertNotNull(texts.unit)
            } else {
                assertNotNull("${award.badge} has a condition", texts.condition)
            }
        }
    }

    @Test
    fun wordingFollowsTheCatalogue() {
        val silver = Award(Badge.REPEAT_OFFENDER, Tier.SILVER)
        assertEquals("Cinquante séances. Tu fais partie des meubles.", BadgeWording.message(resources, silver, QuipStyle()))
        assertEquals("Badge Récidiviste : palier Argent obtenu", BadgeWording.message(resources, silver, QuipStyle(serious = true)))
        assertEquals("Badge obtenu : Pas morte", BadgeWording.message(resources, Award(Badge.NOT_DEAD), QuipStyle(serious = true, feminine = true)))
        assertEquals("Elle est revenue ! On t'avait gardé ta place.", BadgeWording.message(resources, Award(Badge.RETURN_OF_THE_KING), QuipStyle(feminine = true)))
        assertEquals("Déménageuse — Platine", BadgeWording.title(resources, Award(Badge.MOVER, Tier.PLATINUM), feminine = true))
    }

    @Test
    fun everyLevelHasATitle() {
        assertEquals(BadgeConfig.LEVELS.size, resources.getStringArray(R.array.badge_level_titles).size)
        assertEquals(BadgeConfig.LEVELS.size, resources.getStringArray(R.array.badge_level_titles_f).size)
        assertEquals("Flemmarde repentie", BadgeWording.levelDisplay(resources, 2, QuipStyle(feminine = true)))
        assertEquals("Niveau 2", BadgeWording.levelDisplay(resources, 2, QuipStyle(serious = true)))
    }
}
