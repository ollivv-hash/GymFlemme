package fr.olivier.forme.feature.nutrition

import fr.olivier.forme.feature.heart.domain.Sex
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
import fr.olivier.forme.feature.nutrition.data.SupplementDefaults
import fr.olivier.forme.feature.nutrition.data.advisedRestDayKcal
import fr.olivier.forme.feature.nutrition.data.proteinTargetG
import fr.olivier.forme.feature.nutrition.data.restDayKcal
import fr.olivier.forme.feature.nutrition.domain.ActivityLevel
import fr.olivier.forme.feature.nutrition.domain.EnergyMath
import fr.olivier.forme.feature.nutrition.domain.NutritionGoal
import fr.olivier.forme.feature.nutrition.domain.ProfileBounds
import fr.olivier.forme.feature.onboarding.Onboarding
import fr.olivier.forme.feature.onboarding.OnboardingAnswers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** V2.25: calculated / personalised targets, activity, under-16 rule, wizard answers. */
class ProfileTargetsTest {
    private val year = 2026
    private val olivier = ProfileEntity(birthYear = 1980, sex = Sex.MALE, heightCm = 178, nutritionGoal = NutritionGoal.BULK)

    @Test
    fun withoutActivityTheTargetIsTheOneOfBefore() {
        // BMR = 10 × 80 + 6.25 × 178 − 5 × 46 + 5 = 1 687.5 ; × 1.4 × 1.10 = 2 598.75 → 2 600.
        assertEquals(2600, olivier.restDayKcal(80.0, year))
        assertEquals(2600, olivier.copy(activityLevel = ActivityLevel.MODERATE).restDayKcal(80.0, year))
    }

    @Test
    fun theActivityChangesTheCalculatedTarget() {
        assertEquals(2230, olivier.copy(activityLevel = ActivityLevel.SEDENTARY).restDayKcal(80.0, year)) // × 1.2
        assertEquals(2970, olivier.copy(activityLevel = ActivityLevel.VERY_ACTIVE).restDayKcal(80.0, year)) // × 1.6
    }

    @Test
    fun theCalculatedTargetFollowsTheWeightButNotAPersonalisedOne() {
        assertEquals(2600, olivier.restDayKcal(80.0, year))
        assertEquals(2680, olivier.restDayKcal(85.0, year))
        val custom = olivier.copy(kcalTargetOverride = 2400)
        assertEquals(2400, custom.restDayKcal(80.0, year))
        assertEquals(2400, custom.restDayKcal(85.0, year))
        assertEquals(2400, custom.copy(nutritionGoal = NutritionGoal.CUT, activityLevel = ActivityLevel.ACTIVE).restDayKcal(85.0, year))
        // Both are shown: the calculated one goes on.
        assertEquals(2680, custom.advisedRestDayKcal(85.0, year))
        // Switch off: back to the calculation.
        assertEquals(2680, custom.copy(kcalTargetOverride = null).restDayKcal(85.0, year))
    }

    @Test
    fun proteinCalculatedOrPersonalised() {
        assertEquals(144.0, olivier.proteinTargetG(80.0)!!, 0.001) // 1.8 g/kg
        assertEquals(153.0, olivier.proteinTargetG(85.0)!!, 0.001)
        assertEquals(160.0, olivier.copy(proteinTargetOverrideG = 160).proteinTargetG(85.0)!!, 0.0)
        assertEquals(160.0, olivier.copy(proteinTargetOverrideG = 160).proteinTargetG(null)!!, 0.0)
        assertNull(olivier.proteinTargetG(null))
    }

    @Test
    fun under16NoCalorieTarget() {
        val teen = olivier.copy(birthYear = year - 15, kcalTargetOverride = 2500)
        assertTrue(EnergyMath.isMinor(teen.birthYear, year))
        assertNull(teen.restDayKcal(60.0, year))
        assertFalse(EnergyMath.isMinor(year - 16, year))
        assertFalse(EnergyMath.isMinor(null, year))
    }

    @Test
    fun aPersonalisedTargetUnderTheBasalMetabolismIsFlagged() {
        val bmr = EnergyMath.bmr(80.0, 178, 46, Sex.MALE)
        assertTrue(EnergyMath.belowBmr(1500, bmr))
        assertFalse(EnergyMath.belowBmr(1800, bmr))
        assertFalse(EnergyMath.belowBmr(null, bmr))
        assertFalse(EnergyMath.belowBmr(1500, null))
    }

    @Test
    fun bounds() {
        assertEquals(178, ProfileBounds.height("178"))
        assertNull(ProfileBounds.height("99"))
        assertNull(ProfileBounds.height("251"))
        assertEquals(year - 13, ProfileBounds.birthYear("${year - 13}", year))
        assertNull(ProfileBounds.birthYear("${year - 12}", year))
        assertNull(ProfileBounds.birthYear("${year - 101}", year))
        assertNull(ProfileBounds.weight(29.9))
        assertEquals(300.0, ProfileBounds.weight(300.0)!!, 0.0)
    }

    @Test
    fun theWizardAnswersMakeTheProfile() {
        val p = OnboardingAnswers(
            firstName = " Léa ", birthYear = 1995, sex = Sex.FEMALE, heightCm = 165, weightKg = 60.0, goal = NutritionGoal.MAINTAIN,
            activity = ActivityLevel.LIGHT, wheyProteinPerDoseG = 24.0, kcalOverride = 2000,
        ).applyTo(ProfileEntity())
        assertEquals("Léa", p.firstName)
        assertEquals(80.0, p.wheyProteinPer100g!!, 0.001) // 24 g in 30 g
        assertEquals(30.0, p.wheyScoopG, 0.0)
        assertEquals(ActivityLevel.LIGHT, p.activityLevel)
        assertEquals(2000, p.kcalTargetOverride)
        assertEquals(ProfileEntity.DEFAULT_PROTEIN_G_PER_KG, p.proteinTargetGPerKg, 0.0)
        // Everything skipped: the defaults of a new install.
        assertEquals(ProfileEntity(), OnboardingAnswers().applyTo(ProfileEntity()))
    }

    @Test
    fun theWizardIsForNewInstallsOnly() {
        assertFalse(Onboarding.hasData(ProfileEntity(), hasWeighIn = false))
        assertTrue(Onboarding.hasData(ProfileEntity(), hasWeighIn = true))
        assertTrue(Onboarding.hasData(ProfileEntity(birthYear = 1980), hasWeighIn = false))
        assertTrue(Onboarding.hasData(ProfileEntity(nutritionGoal = NutritionGoal.CUT), hasWeighIn = false))
    }

    @Test
    fun creatineThreeGramsForANewInstallFiveForOlderData() {
        assertEquals(3.0, SupplementDefaults.all.first { it.isCreatine }.amount, 0.0)
        assertEquals(5.0, SupplementDefaults.legacy.first { it.isCreatine }.amount, 0.0)
    }
}
