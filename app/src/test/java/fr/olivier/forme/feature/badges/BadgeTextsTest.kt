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
import fr.olivier.forme.feature.badges.domain.Challenge
import fr.olivier.forme.feature.badges.ui.BadgeTexts
import fr.olivier.forme.feature.badges.ui.ChallengeTexts
import fr.olivier.forme.feature.badges.ui.ChallengeWording
import fr.olivier.forme.feature.badges.ui.BadgeWording
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

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

    @Test
    fun everyChallengeHasItsTexts() {
        Challenge.entries.forEach { challenge ->
            styles.forEach { style ->
                assertTrue(ChallengeWording.announce(resources, challenge, style).isNotBlank())
                assertTrue(ChallengeWording.success(resources, challenge, style).isNotBlank())
            }
            assertTrue(resources.getString(ChallengeTexts.of(challenge).unit).isNotBlank())
        }
        assertEquals("Défi du mois : 12 séances dans le mois", ChallengeWording.announce(resources, Challenge.TWELVE_STRIKES, QuipStyle(serious = true)))
        assertEquals("Défi du mois réussi", ChallengeWording.success(resources, Challenge.CURIOUS, QuipStyle(serious = true)))
        assertEquals("Ce mois-ci : 30 km de rameur. Hissez haut !", ChallengeWording.announce(resources, Challenge.ROWER_OF_THE_MONTH, QuipStyle()))
    }

    @Test
    fun challengeBadgeNamesElideBeforeAVowel() {
        assertEquals("Défi d'octobre 2026", ChallengeWording.badge(resources, YearMonth.of(2026, 10)))
        assertEquals("Défi d'août 2027", ChallengeWording.badge(resources, YearMonth.of(2027, 8)))
        assertEquals("Défi de mars 2027", ChallengeWording.badge(resources, YearMonth.of(2027, 3)))
        assertEquals("Défi de janvier", ChallengeWording.ofMonth(resources, YearMonth.of(2027, 1)))
    }
}
