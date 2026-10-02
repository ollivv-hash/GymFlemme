package fr.olivier.forme.feature.sport.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordsAndVolumeTest {
    private fun s(id: Long, min: Int?, vararg sets: Pair<Double, Int>) =
        SessionSets(id, min, sets.map { (load, reps) -> WorkSet(load, reps) })

    @Test
    fun `load volume is the sum of load times reps`() {
        assertEquals(600.0, VolumeCalculator.loadVolume(listOf(WorkSet(20.0, 10), WorkSet(20.0, 10), WorkSet(20.0, 10))), 0.0)
    }

    @Test
    fun `best load only counts sets at or above the minimum target reps`() {
        assertEquals(20.0, RecordsCalculator.bestLoad(s(1, 8, 20.0 to 10, 25.0 to 5))!!, 0.0)
        assertEquals(25.0, RecordsCalculator.bestLoad(s(1, null, 20.0 to 10, 25.0 to 1))!!, 0.0)
    }

    @Test
    fun `records over the history`() {
        val history = listOf(s(1, 10, 20.0 to 10, 20.0 to 10), s(2, 10, 22.5 to 10, 22.5 to 8))
        val records = RecordsCalculator.records(history)
        assertEquals(22.5, records.bestLoadKg!!, 0.0)
        assertEquals(405.0, records.bestVolumeKg!!, 0.0)
    }

    @Test
    fun `new records strictly beat the previous best`() {
        val previous = listOf(s(1, 10, 20.0 to 10, 20.0 to 10, 20.0 to 10))
        val equal = RecordsCalculator.newRecords(s(2, 10, 20.0 to 10, 20.0 to 10, 20.0 to 10), previous)
        assertFalse(equal.any)
        val better = RecordsCalculator.newRecords(s(3, 10, 22.5 to 10, 20.0 to 10, 20.0 to 10), previous)
        assertEquals(22.5, better.loadKg!!, 0.0)
        assertEquals(625.0, better.volumeKg!!, 0.0)
    }

    @Test
    fun `a first performance is not a record`() {
        val first = RecordsCalculator.newRecords(s(1, 12, 10.0 to 12), emptyList())
        assertNull(first.loadKg)
        assertNull(first.volumeKg)
    }

    @Test
    fun `heavier load with too few reps is not a load record but may be a volume record`() {
        val previous = listOf(s(1, 10, 20.0 to 10))
        val result = RecordsCalculator.newRecords(s(2, 10, 30.0 to 8), previous)
        assertNull(result.loadKg)
        assertTrue(result.volumeKg!! > 200.0)
    }

    @Test
    fun `session volume never mixes units`() {
        val volume = VolumeCalculator.session(
            listOf(
                ExerciseSets(MuscleGroup.PECTORAUX, ExerciseType.WEIGHTED, false, listOf(WorkSet(15.0, 10), WorkSet(15.0, 10))),
                ExerciseSets(MuscleGroup.DOS, ExerciseType.WEIGHTED, false, listOf(WorkSet(25.0, 12))),
                ExerciseSets(MuscleGroup.PECTORAUX, ExerciseType.WEIGHTED, false, listOf(WorkSet(10.0, 10))),
                ExerciseSets(MuscleGroup.ABDOS, ExerciseType.BODYWEIGHT, false, listOf(WorkSet(reps = 12), WorkSet(reps = 12))),
                ExerciseSets(MuscleGroup.ABDOS, ExerciseType.TIMED, true, listOf(WorkSet(durationSec = 25), WorkSet(durationSec = 25))),
                ExerciseSets(MuscleGroup.CARDIO, ExerciseType.CARDIO, false, emptyList()),
            ),
        )
        assertEquals(700.0, volume.totalKg, 0.0)
        assertEquals(400.0, volume.kgByGroup[MuscleGroup.PECTORAUX]!!, 0.0)
        assertEquals(300.0, volume.kgByGroup[MuscleGroup.DOS]!!, 0.0)
        assertEquals(24, volume.bodyweightReps)
        assertEquals(100, volume.timedSec)
    }
}
