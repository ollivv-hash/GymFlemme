package fr.olivier.forme.feature.sport.domain

data class ExerciseSets(
    val muscleGroup: MuscleGroup,
    val type: ExerciseType,
    val perSide: Boolean,
    val sets: List<WorkSet>,
)

/**
 * Volume of a session. Units are never mixed: kg for WEIGHTED, total reps for BODYWEIGHT,
 * total time (both sides counted) for TIMED.
 */
data class SessionVolume(
    val totalKg: Double,
    val kgByGroup: Map<MuscleGroup, Double>,
    val bodyweightReps: Int,
    val timedSec: Int,
)

object VolumeCalculator {
    fun loadVolume(sets: List<WorkSet>): Double = sets.sumOf { (it.loadKg ?: 0.0) * (it.reps ?: 0) }

    fun session(exercises: List<ExerciseSets>): SessionVolume {
        val kgByGroup = linkedMapOf<MuscleGroup, Double>()
        var reps = 0
        var timed = 0
        exercises.forEach { ex ->
            when (ex.type) {
                ExerciseType.WEIGHTED -> {
                    val v = loadVolume(ex.sets)
                    if (v > 0) kgByGroup[ex.muscleGroup] = (kgByGroup[ex.muscleGroup] ?: 0.0) + v
                }
                ExerciseType.BODYWEIGHT -> reps += ex.sets.sumOf { it.reps ?: 0 }
                ExerciseType.TIMED -> timed += ex.sets.sumOf { it.durationSec ?: 0 } * (if (ex.perSide) 2 else 1)
                ExerciseType.CARDIO -> Unit
            }
        }
        return SessionVolume(kgByGroup.values.sum(), kgByGroup, reps, timed)
    }
}
