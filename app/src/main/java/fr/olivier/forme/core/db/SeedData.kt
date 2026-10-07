package fr.olivier.forme.core.db

import fr.olivier.forme.feature.library.data.ExerciseLibraryLinks
import fr.olivier.forme.feature.sport.data.ExerciseEntity
import fr.olivier.forme.feature.sport.data.SessionEntity
import fr.olivier.forme.feature.sport.data.SessionExerciseEntity
import fr.olivier.forme.feature.sport.data.SetEntryEntity
import fr.olivier.forme.feature.sport.data.TemplateExerciseEntity
import fr.olivier.forme.feature.sport.data.WorkoutTemplateEntity
import fr.olivier.forme.feature.sport.domain.ExerciseType.BODYWEIGHT
import fr.olivier.forme.feature.sport.domain.ExerciseType.CARDIO
import fr.olivier.forme.feature.sport.domain.ExerciseType.TIMED
import fr.olivier.forme.feature.sport.domain.ExerciseType.WEIGHTED
import fr.olivier.forme.feature.sport.domain.MuscleGroup
import fr.olivier.forme.feature.sport.domain.SessionExerciseStatus
import java.time.Duration
import java.time.Instant

/**
 * Initial content written at database creation.
 *
 * Current loads are stored as a hidden "seed" session (template B, finished the day before install):
 * it pre-fills the "last time" values, positions the rotation (next = C) and acts as the baseline
 * for records. It is excluded from history and from progression suggestions.
 */
object SeedData {
    const val ROWER_DEFAULT_SEC = 5 * 60

    private var nextExerciseId = 1L
    private fun ex(
        name: String,
        group: MuscleGroup,
        type: fr.olivier.forme.feature.sport.domain.ExerciseType,
        sets: Int,
        reps: Int? = null,
        durationSec: Int? = null,
        increment: Double = 2.5,
        perSide: Boolean = false,
        loadNote: String? = null,
    ) = ExerciseEntity(
        id = nextExerciseId++,
        name = name,
        muscleGroup = group,
        type = type,
        perSide = perSide,
        loadIncrementKg = increment,
        defaultTargetSets = sets,
        defaultRepsMin = reps,
        defaultRepsMax = reps,
        defaultDurationSec = durationSec,
        loadNote = loadNote,
        libraryId = ExerciseLibraryLinks.BY_NAME[name],
    )

    val rower = ex("Rameur", MuscleGroup.CARDIO, CARDIO, sets = 1, durationSec = ROWER_DEFAULT_SEC)
    val shoulderPress = ex("Shoulder press", MuscleGroup.EPAULES, WEIGHTED, 3, 10)
    val chestPress = ex("Chest press", MuscleGroup.PECTORAUX, WEIGHTED, 3, 10)
    val pecMachine = ex("Pec machine", MuscleGroup.PECTORAUX, WEIGHTED, 3, 10)
    val pullDown = ex("Pull down", MuscleGroup.DOS, WEIGHTED, 3, 12)
    val pullDownNeutral = ex("Pull down prise neutre", MuscleGroup.DOS, WEIGHTED, 3, 12)
    val rowingMachine = ex("Rowing machine", MuscleGroup.DOS, WEIGHTED, 3, 10)
    val lateralRaises = ex("Élévations latérales", MuscleGroup.EPAULES, WEIGHTED, 2, 15, increment = 1.0)
    val crunchMachine = ex("Abdominal crunch machine", MuscleGroup.ABDOS, WEIGHTED, 3, 15)
    val romanChair = ex("Chaise romaine", MuscleGroup.ABDOS, BODYWEIGHT, 3, 15)
    val sidePlank = ex("Planche latérale", MuscleGroup.ABDOS, TIMED, 3, durationSec = 25, perSide = true)
    val bicepsCurl = ex("Curl biceps", MuscleGroup.BRAS, WEIGHTED, 2, 12)
    val legCurl = ex("Leg curl", MuscleGroup.JAMBES, WEIGHTED, 3, 12)
    val abductors = ex("Abducteurs machine", MuscleGroup.JAMBES, WEIGHTED, 3, 12)
    val hackSquat = ex("Hack squat", MuscleGroup.JAMBES, WEIGHTED, 3, 12, loadNote = "Chariot vide + X kg")
    val standingCalves = ex("Mollets debout", MuscleGroup.JAMBES, WEIGHTED, 3, 15)

    val exercises = listOf(
        rower, shoulderPress, chestPress, pecMachine, pullDown, pullDownNeutral, rowingMachine, lateralRaises,
        crunchMachine, romanChair, sidePlank, bicepsCurl, legCurl, abductors, hackSquat, standingCalves,
    )

    /** Current working loads (kg) of exercises whose load is known. */
    val currentLoads: Map<ExerciseEntity, Double> = linkedMapOf(
        shoulderPress to 20.0,
        chestPress to 15.0,
        pecMachine to 10.0,
        pullDown to 25.0,
        pullDownNeutral to 22.5,
        rowingMachine to 20.0,
        lateralRaises to 6.0,
        crunchMachine to 15.0,
        legCurl to 15.0,
        abductors to 20.0,
        hackSquat to 10.0,
    )

    val templateA = WorkoutTemplateEntity(id = 1, name = "Poussée + tronc", code = "A", orderInRotation = 1)
    val templateB = WorkoutTemplateEntity(id = 2, name = "Tirage + tronc", code = "B", orderInRotation = 2)
    val templateC = WorkoutTemplateEntity(id = 3, name = "Mixte + tronc dominant", code = "C", orderInRotation = 3)
    val templateLower = WorkoutTemplateEntity(id = 4, name = "Bas du corps", code = "BAS", orderInRotation = null)
    val templates = listOf(templateA, templateB, templateC, templateLower)

    private fun lines(template: WorkoutTemplateEntity, vararg specs: Line): List<TemplateExerciseEntity> =
        specs.mapIndexed { index, spec ->
            TemplateExerciseEntity(
                templateId = template.id,
                exerciseId = spec.exercise.id,
                position = index,
                targetSets = spec.sets,
                targetRepsMin = spec.reps,
                targetRepsMax = spec.reps,
                targetDurationSec = spec.durationSec,
                optional = spec.optional,
            )
        }

    private data class Line(
        val exercise: ExerciseEntity,
        val sets: Int,
        val reps: Int? = null,
        val durationSec: Int? = null,
        val optional: Boolean = false,
    )

    private val rowerLine = Line(rower, 1, durationSec = ROWER_DEFAULT_SEC)

    val templateLines: List<TemplateExerciseEntity> =
        lines(
            templateA,
            rowerLine,
            Line(shoulderPress, 3, 10),
            Line(chestPress, 3, 10),
            Line(lateralRaises, 2, 15),
            Line(crunchMachine, 3, 15),
            Line(romanChair, 3, 12),
        ) + lines(
            templateB,
            rowerLine,
            Line(pullDown, 3, 12),
            Line(rowingMachine, 3, 12),
            Line(romanChair, 3, 12),
            Line(sidePlank, 3, durationSec = 25),
            Line(bicepsCurl, 2, 12, optional = true),
        ) + lines(
            templateC,
            rowerLine,
            Line(chestPress, 3, 10),
            Line(pullDownNeutral, 3, 12),
            Line(crunchMachine, 3, 15),
            Line(romanChair, 3, 12),
        ) + lines(
            templateLower,
            rowerLine,
            Line(hackSquat, 3, 12),
            Line(legCurl, 3, 12),
            Line(abductors, 3, 12),
            Line(standingCalves, 3, 15, optional = true),
        )

    const val SEED_SESSION_ID = 1L

    fun seedSession(now: Instant): SessionEntity {
        val startedAt = now.minus(Duration.ofDays(1))
        return SessionEntity(
            id = SEED_SESSION_ID,
            templateId = templateB.id,
            templateNameSnapshot = templateB.name,
            templateCodeSnapshot = templateB.code,
            startedAt = startedAt,
            endedAt = startedAt.plus(Duration.ofHours(1)),
            notes = "seed",
            isSeed = true,
        )
    }

    fun seedSessionLines(): List<SessionExerciseEntity> =
        currentLoads.keys.mapIndexed { index, exercise ->
            SessionExerciseEntity(
                sessionId = SEED_SESSION_ID,
                exerciseId = exercise.id,
                position = index,
                status = SessionExerciseStatus.DONE,
                targetSets = exercise.defaultTargetSets,
                targetRepsMin = exercise.defaultRepsMin,
                targetRepsMax = exercise.defaultRepsMax,
            )
        }

    fun seedSets(now: Instant): List<SetEntryEntity> {
        val completedAt = seedSession(now).startedAt
        return currentLoads.flatMap { (exercise, load) ->
            (0 until exercise.defaultTargetSets).map { index ->
                SetEntryEntity(
                    sessionId = SEED_SESSION_ID,
                    exerciseId = exercise.id,
                    setIndex = index,
                    loadKg = load,
                    reps = exercise.defaultRepsMax,
                    completedAt = completedAt,
                )
            }
        }
    }
}
