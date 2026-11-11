package fr.olivier.forme.feature.onboarding

import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.feature.bodyweight.data.BodyWeightRepository
import fr.olivier.forme.feature.nutrition.data.NutritionRepository
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
import fr.olivier.forme.feature.nutrition.data.SupplementEntity
import fr.olivier.forme.feature.nutrition.domain.ActivityLevel
import fr.olivier.forme.feature.nutrition.domain.NutritionGoal
import fr.olivier.forme.feature.heart.domain.Sex
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** What the first-launch wizard collected (V2.25); null = skipped or left empty. */
data class OnboardingAnswers(
    val firstName: String? = null,
    val birthYear: Int? = null,
    val sex: Sex? = null,
    val heightCm: Int? = null,
    val weightKg: Double? = null,
    val goal: NutritionGoal? = null,
    val activity: ActivityLevel? = null,
    /** Grams of protein in one dose of whey (a dose of [WHEY_DOSE_G]). */
    val wheyProteinPerDoseG: Double? = null,
    val creatineG: Double? = null,
    val kcalOverride: Int? = null,
    val proteinOverrideG: Int? = null,
) {
    /** The profile as saved: [base] (the defaults of a new install) with the answers given. */
    fun applyTo(base: ProfileEntity): ProfileEntity = base.copy(
        firstName = firstName?.trim()?.takeIf { it.isNotEmpty() } ?: base.firstName,
        birthYear = birthYear ?: base.birthYear,
        sex = sex ?: base.sex,
        heightCm = heightCm ?: base.heightCm,
        nutritionGoal = goal ?: base.nutritionGoal,
        activityLevel = activity ?: base.activityLevel,
        wheyProteinPer100g = wheyProteinPerDoseG?.let { it / WHEY_DOSE_G * 100 } ?: base.wheyProteinPer100g,
        wheyScoopG = if (wheyProteinPerDoseG != null) WHEY_DOSE_G else base.wheyScoopG,
        kcalTargetOverride = kcalOverride ?: base.kcalTargetOverride,
        proteinTargetOverrideG = proteinOverrideG ?: base.proteinTargetOverrideG,
    )

    companion object {
        const val WHEY_DOSE_G = 30.0
    }
}

/**
 * The first-launch wizard (V2.25): shown once, on a new install only. Someone who already has data (a weigh-in, or
 * a profile filled in) never sees it; their new fields stay empty, to fill in Réglages → Profil.
 */
@Singleton
class Onboarding @Inject constructor(
    private val settings: SettingsRepository,
    private val nutrition: NutritionRepository,
    private val weights: BodyWeightRepository,
) {
    suspend fun shouldShow(): Boolean {
        if (System.getProperty(SKIP_PROPERTY) == "true") return false
        if (settings.onboardingDone.first()) return false
        val existing = hasData(nutrition.currentProfile(), weights.latest() != null)
        if (existing) settings.setOnboardingDone()
        return !existing
    }

    suspend fun skipAll() = settings.setOnboardingDone()

    suspend fun save(answers: OnboardingAnswers, today: LocalDate) {
        nutrition.saveProfile(answers.applyTo(nutrition.currentProfile()))
        answers.weightKg?.let { weights.save(today, it) }
        answers.creatineG?.let { dose ->
            nutrition.supplements.first().firstOrNull(SupplementEntity::isCreatine)?.let { nutrition.updateSupplement(it.copy(amount = dose)) }
        }
        settings.setOnboardingDone()
    }

    companion object {
        /** Set by the unit tests (build.gradle.kts): they start past the wizard. */
        const val SKIP_PROPERTY = "gymflemme.skipOnboarding"

        /** Someone already using the app: a weigh-in, or a profile filled in. */
        fun hasData(profile: ProfileEntity, hasWeighIn: Boolean): Boolean =
            hasWeighIn || profile.birthYear != null || profile.heightCm != null || profile.nutritionGoal != null ||
                profile.firstName != null || profile.sex != null
    }
}
