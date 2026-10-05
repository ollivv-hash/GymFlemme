package fr.olivier.forme.feature.sport.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CoachingTest {
    private fun session(id: Long, vararg sets: Pair<Double, Int>) =
        SessionSets(id, 8, sets.map { (load, reps) -> WorkSet(loadKg = load, reps = reps) })

    @Test
    fun epleyEstimate() {
        assertEquals(26.67, OneRepMax.estimate(20.0, 10)!!, 0.01)
        assertEquals(40.0, OneRepMax.estimate(40.0, 1)!!, 0.0)
        assertNull(OneRepMax.estimate(20.0, 15)) // too many reps to be reliable
        assertNull(OneRepMax.estimate(null, 10))
        assertNull(OneRepMax.estimate(20.0, 0))
        // 22.5 × 8 (28.5) beats 20 × 12 (28.0)
        assertEquals(28.5, OneRepMax.best(listOf(WorkSet(loadKg = 20.0, reps = 12), WorkSet(loadKg = 22.5, reps = 8)))!!, 0.01)
    }

    @Test
    fun plateauAfterThreeSessionsWithoutProgress() {
        val progressing = listOf(
            session(1, 20.0 to 10),
            session(2, 20.0 to 11),
            session(3, 22.5 to 8),
            session(4, 22.5 to 9), // +1 rep: progress
        )
        assertNull(PlateauDetector.detect(progressing, 2.5))

        val stalled = progressing + listOf(
            session(5, 22.5 to 9),
            session(6, 22.5 to 8),
            session(7, 22.5 to 9, 22.5 to 7),
        )
        val plateau = PlateauDetector.detect(stalled, 2.5)!!
        assertEquals(3, plateau.sessions)
        assertEquals(22.5 * (1 + 9 / 30.0), plateau.bestOneRepMaxKg, 0.01)
        assertEquals(20.0, plateau.resetLoadKg!!, 0.0) // 22.5 × 0.9 = 20.25 → 20
        val advice = PlateauDetector.advice(plateau)
        assertEquals(3, advice.size)
        assertTrue(advice[1].startsWith("Repars à 20 kg"))

        // Too short a history: no verdict.
        assertNull(PlateauDetector.detect(stalled.take(3), 2.5))
        // A new best ends the plateau.
        assertNull(PlateauDetector.detect(stalled + session(8, 25.0 to 8), 2.5))
    }

    @Test
    fun deloadAfterSixWeeksInARow() {
        val current = LocalDate.of(2026, 10, 5) // Monday
        fun weeks(vararg sets: Int) = sets.withIndex().associate { (i, s) -> current.minusWeeks(i + 1L) to s }

        // 6 complete weeks of 30 sets: advised (the current week does not count).
        assertEquals(6, DeloadAdvisor.trainingStreak(weeks(30, 30, 30, 30, 30, 30), current))
        assertEquals(DeloadAdvice(6, 0), DeloadAdvisor.advise(weeks(30, 30, 30, 30, 30, 30), current, plateaus = 0))
        // A week off breaks the streak.
        assertEquals(2, DeloadAdvisor.trainingStreak(weeks(30, 30, 0, 30, 30, 30, 30), current))
        // A light week (12 sets vs ~30) was a deload: the streak restarts after it.
        assertEquals(3, DeloadAdvisor.trainingStreak(weeks(30, 30, 30, 12, 30, 30, 30), current))
        assertNull(DeloadAdvisor.advise(weeks(30, 30, 30, 12, 30, 30, 30), current, plateaus = 2))
        // Three exercises stalling are enough on their own.
        val byPlateaus = DeloadAdvisor.advise(weeks(30, 30), current, plateaus = 3)!!
        assertTrue(byPlateaus.message.startsWith("3 exercices qui stagnent"))
        assertTrue(DeloadAdvice(7, 4).message.startsWith("7 semaines d'entraînement d'affilée et 4 exercices"))
    }
}
