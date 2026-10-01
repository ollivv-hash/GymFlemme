package fr.olivier.forme.feature.sport.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class SessionTextFormatterTest {
    @Test
    fun `summary contains date, template, sets, volume, comparison and records`() {
        val chest = ExerciseReport(
            exerciseId = 3, name = "Chest press", muscleGroup = MuscleGroup.PECTORAUX, type = ExerciseType.WEIGHTED,
            perSide = false, skipped = false,
            sets = listOf(ReportSet(WorkSet(17.5, 10, rir = 2), false), ReportSet(WorkSet(10.0, 10), true)),
            cardio = null, newRecords = NewRecords(17.5, null),
        )
        val rower = ExerciseReport(
            1, "Rameur", MuscleGroup.CARDIO, ExerciseType.CARDIO, false, false, emptyList(),
            CardioResult(300, 1250, 120, 60), NewRecords(null, null),
        )
        val plank = ExerciseReport(
            11, "Planche latérale", MuscleGroup.ABDOS, ExerciseType.TIMED, true, false,
            listOf(ReportSet(WorkSet(durationSec = 25), false)), null, NewRecords(null, null),
        )
        val curl = ExerciseReport(12, "Curl biceps", MuscleGroup.BRAS, ExerciseType.WEIGHTED, false, true, emptyList(), null, NewRecords(null, null))
        val volume = VolumeCalculator.session(listOf(ExerciseSets(MuscleGroup.PECTORAUX, ExerciseType.WEIGHTED, false, listOf(WorkSet(17.5, 10)))))
        val report = SessionReport(
            startedAt = LocalDateTime.of(2026, 10, 1, 18, 0),
            durationMin = 55, templateCode = "C", templateName = "Mixte + tronc dominant", feeling = 4, notes = "RAS",
            exercises = listOf(rower, chest, plank, curl), volume = volume,
            previous = PreviousComparison(LocalDateTime.of(2026, 9, 24, 18, 0), volume.copy(totalKg = 150.0)),
        )
        val text = SessionTextFormatter.format(report)
        listOf(
            "Séance du jeudi 1 octobre 2026 — C · Mixte + tronc dominant",
            "Durée : 55 min · Ressenti : 4/5",
            "Rameur : 5:00 · 1 250 m · 2:00 /500 m · 60 kcal",
            "Chest press : 17,5 kg × 10 (RIR 2) (+ 1 échauff.)",
            "Planche latérale : 25 s/côté",
            "Curl biceps : sauté",
            "Volume total : 175 kg (Pectoraux 175 kg)",
            "du 24/09 : +25 kg (+17 %)",
            "Records : Chest press — charge max 17,5 kg",
            "Notes : RAS",
        ).forEach { assertTrue("missing «$it» in:\n$text", text.contains(it)) }
    }
}
