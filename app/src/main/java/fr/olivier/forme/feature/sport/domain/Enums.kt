package fr.olivier.forme.feature.sport.domain

/** Stored as TEXT in Room (enum name): never rename a constant without a migration. */
enum class MuscleGroup(val label: String) {
    PECTORAUX("Pectoraux"),
    EPAULES("Épaules"),
    DOS("Dos"),
    BRAS("Bras"),
    ABDOS("Abdos"),
    JAMBES("Jambes"),
    CARDIO("Cardio"),
    AUTRE("Autre"),
}

enum class ExerciseType(val label: String) {
    /** Load + reps. */
    WEIGHTED("Charge + reps"),
    /** Reps, optional extra load. */
    BODYWEIGHT("Poids du corps"),
    /** Duration per set, optionally per side. */
    TIMED("Durée"),
    /** Duration, distance, pace, calories. */
    CARDIO("Cardio"),
}

enum class SessionExerciseStatus { PENDING, DONE, SKIPPED }
