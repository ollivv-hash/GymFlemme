package fr.olivier.forme.core.backup

import fr.olivier.forme.feature.sport.domain.ExerciseType
import fr.olivier.forme.feature.sport.domain.MuscleGroup
import fr.olivier.forme.feature.sport.domain.SessionExerciseStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    private val sample = BackupFile(
        schemaVersion = 1,
        exportedAt = 1_790_000_000_000,
        sport = SportBackup(
            exercises = listOf(
                ExerciseDto(1, "Rameur", MuscleGroup.CARDIO, ExerciseType.CARDIO, false, 2.5, 1, null, null, 300, null, null, null, false),
                ExerciseDto(2, "Hack squat", MuscleGroup.JAMBES, ExerciseType.WEIGHTED, false, 2.5, 3, 12, 12, null, "Cran 4", "Chariot vide + X kg", "/data/p.jpg", true),
            ),
            templates = listOf(TemplateDto(1, "Bas du corps", "BAS", null, false), TemplateDto(2, "A", "A", 1, false)),
            templateExercises = listOf(TemplateExerciseDto(1, 1, 2, 0, 3, 12, 12, null, true, 120)),
            sessions = listOf(SessionDto(1, 1, "Bas du corps", "BAS", 1_789_000_000_000, 1_789_003_600_000, 4, "Très bien « ok »", false)),
            sessionExercises = listOf(SessionExerciseDto(1, 1, 2, 0, SessionExerciseStatus.SKIPPED, 3, 12, 12, null, null, true)),
            sets = listOf(SetEntryDto(1, 1, 2, 0, 22.5, 12, null, 3, false, 1_789_000_100_000)),
            cardio = listOf(CardioEntryDto(1, 1, 1, 300, 1250, 120, 60)),
        ),
        bodyWeight = listOf(BodyWeightDto(1, "2026-10-01", 81.3)),
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
    fun `newer format is rejected`() {
        val newer = BackupCodec.encode(sample.copy(schemaVersion = BackupCodec.CURRENT_VERSION + 1))
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(newer) }
    }

    @Test
    fun `garbage is rejected with a clear error`() {
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode("not json") }
    }
}
