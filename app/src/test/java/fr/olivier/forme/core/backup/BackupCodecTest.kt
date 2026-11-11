package fr.olivier.forme.core.backup

import fr.olivier.forme.feature.nutrition.data.SupplementDefaults
import fr.olivier.forme.feature.nutrition.data.SupplementUnit
import fr.olivier.forme.feature.nutrition.domain.FoodSource
import fr.olivier.forme.feature.nutrition.domain.MealType
import fr.olivier.forme.feature.sport.domain.ExerciseType
import fr.olivier.forme.feature.sport.domain.MuscleGroup
import fr.olivier.forme.feature.sport.domain.SessionExerciseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate

class BackupCodecTest {
    private val sample = BackupFile(
        schemaVersion = 2,
        exportedAt = 1_790_000_000_000,
        sport = SportBackup(
            exercises = listOf(
                ExerciseDto(1, "Rameur", MuscleGroup.CARDIO, ExerciseType.CARDIO, false, 2.5, 1, null, null, 300, null, null, null, false),
                ExerciseDto(2, "Hack squat", MuscleGroup.JAMBES, ExerciseType.WEIGHTED, false, 2.5, 3, 12, 12, null, "Cran 4", "Chariot vide + X kg", "/data/p.jpg", true, libraryId = "hack-squat"),
            ),
            templates = listOf(TemplateDto(1, "Bas du corps", "BAS", null, false), TemplateDto(2, "A", "A", 1, false)),
            templateExercises = listOf(TemplateExerciseDto(1, 1, 2, 0, 3, 12, 12, null, true, 120)),
            sessions = listOf(SessionDto(1, 1, "Bas du corps", "BAS", 1_789_000_000_000, 1_789_003_600_000, 4, "Très bien « ok »", false)),
            sessionExercises = listOf(SessionExerciseDto(1, 1, 2, 0, SessionExerciseStatus.SKIPPED, 3, 12, 12, null, null, true)),
            sets = listOf(SetEntryDto(1, 1, 2, 0, 22.5, 12, null, 3, false, 1_789_000_100_000)),
            cardio = listOf(CardioEntryDto(1, 1, 1, 300, 1250, 120, 60)),
        ),
        bodyWeight = listOf(BodyWeightDto(1, "2026-10-01", 81.3)),
        nutrition = NutritionBackup(
            profile = ProfileDto(1.8, 78.0, 380.0, 6.0, 7.5, 30.0, 5.0, true, 1200),
            foods = listOf(FoodDto(1, FoodSource.OFF, "3017624010701", "Skyr", "Siggi's", 63.0, 10.5, 4.1, 0.2, 150.0, true, 1_790_000_000_000)),
            meals = listOf(MealEntryDto(1, "2026-10-02", MealType.EXTRA, 1, "Skyr (Siggi's)", 150.0, 15.75, 94.5, null, 0.3, false, 1_790_000_100_000)),
            creatine = listOf(CreatineDto(1, "2026-10-02", 5.0, 1_790_000_200_000)),
            supplements = listOf(SupplementDto(9, "Spiruline", null, 2.0, SupplementUnit.TABLET, true, 7, true, 480)),
            supplementIntakes = listOf(SupplementIntakeDto(1, 9, "2026-10-02", 2.0, 1_790_000_300_000)),
        ),
    )

    @Test
    fun `round trip is lossless`() {
        assertEquals(sample, BackupCodec.decode(BackupCodec.encode(sample)))
    }

    @Test
    fun `unknown sections are ignored (forward compatibility)`() {
        val withExtra = BackupCodec.encode(sample).replaceFirst("{", "{\"nutrition\": {\"meals\": []},")
        assertEquals(sample, BackupCodec.decode(withExtra))
    }

    @Test
    fun `version 1 files without nutrition section are still readable`() {
        val v1 = BackupCodec.encode(sample.copy(schemaVersion = 1, nutrition = NutritionBackup()))
            .replace(Regex(",\\s*\"nutrition\": \\{[^}]*\\}"), "")
        val decoded = BackupCodec.decode(v1)
        assertEquals(1, decoded.schemaVersion)
        assertEquals(NutritionBackup(), decoded.nutrition)
        assertEquals(sample.sport, decoded.sport)
    }

    @Test
    fun `newer format is rejected`() {
        val newer = BackupCodec.encode(sample.copy(schemaVersion = BackupCodec.CURRENT_VERSION + 1))
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(newer) }
    }

    @Test
    fun `garbage is rejected with a clear error`() {
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("not json") }
    }

    /** V2.20: before v10, creatine (profile + days) becomes the first supplement; the others start off. */
    @Test
    fun `creatine of a v9 file becomes the first supplement`() {
        val (supplements, intakes) = sample.nutrition.supplementsToRestore(9)
        val creatine = supplements.single { it.isCreatine }
        assertEquals(5.0, creatine.amount, 0.0)
        assertEquals(true, creatine.reminderEnabled)
        assertEquals(1200, creatine.reminderMinutes)
        assertEquals(listOf(creatine), supplements.filter { it.active })
        assertEquals(SupplementDefaults.all.size, supplements.size)
        assertEquals(listOf(creatine.id to LocalDate.of(2026, 10, 2)), intakes.map { it.supplementId to it.date })
    }

    @Test
    fun `a v10 file keeps its supplements as they are`() {
        val (supplements, intakes) = sample.nutrition.supplementsToRestore(10)
        assertEquals(listOf("Spiruline"), supplements.map { it.name })
        assertEquals(listOf(9L), intakes.map { it.supplementId })
    }

    @Test
    fun `v13 keeps the appearance, older files have none`() {
        val v13 = sample.copy(schemaVersion = 13, appearance = AppearanceDto("LIGHT", "CORAL", dynamicColor = true))
        val decoded = BackupCodec.decode(BackupCodec.encode(v13))
        assertEquals(v13, decoded)
        assertEquals(
            fr.olivier.forme.core.settings.Appearance(
                fr.olivier.forme.core.settings.ThemeMode.LIGHT, fr.olivier.forme.core.settings.AccentColor.CORAL, true,
            ),
            decoded.appearance!!.toAppearance(),
        )
        assertEquals(null, BackupCodec.decode(BackupCodec.encode(sample)).appearance)
        // A value this version does not know falls back to the defaults.
        assertEquals(fr.olivier.forme.core.settings.Appearance(), AppearanceDto("SEPIA", "MAUVE").toAppearance())
    }
}
