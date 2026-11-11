package fr.olivier.forme.feature.sharing

import fr.olivier.forme.feature.nutrition.domain.DishList
import fr.olivier.forme.feature.sport.data.ExerciseEntity
import fr.olivier.forme.feature.sport.data.TemplateLineWithExercise
import fr.olivier.forme.feature.sport.data.WorkoutTemplateEntity

/** How an exercise of the file was found in one's catalogue (V2.27). */
enum class MatchKind { LIBRARY, NAME, CHOSEN, NEW }

/** An exercise of the file and the exercise of the catalogue it will use; null [exercise] = to create. */
data class ExerciseMatch(val shared: SharedExercise, val exercise: ExerciseEntity?, val kind: MatchKind)

object TemplateImport {
    /**
     * Each exercise of the file, in order: the same library exercise in the catalogue, else the same name (case and
     * accents ignored), else to create. Archived exercises are left out.
     */
    fun match(shared: List<SharedExercise>, catalog: List<ExerciseEntity>): List<ExerciseMatch> {
        val active = catalog.filterNot { it.archived }
        return shared.sortedBy { it.position }.map { s ->
            s.libraryId?.let { id -> active.firstOrNull { it.libraryId == id } }?.let { return@map ExerciseMatch(s, it, MatchKind.LIBRARY) }
            active.firstOrNull { DishList.fold(it.name.trim()) == DishList.fold(s.name.trim()) }
                ?.let { return@map ExerciseMatch(s, it, MatchKind.NAME) }
            ExerciseMatch(s, null, MatchKind.NEW)
        }
    }

    /** "{nom} ({auteur})" or "{nom} (importé)" when the name is taken, then numbered if needed. */
    fun importName(name: String, author: String?, existing: Collection<String>): String {
        val taken = existing.map { DishList.fold(it.trim()) }.toSet()
        fun free(n: String) = DishList.fold(n) !in taken
        val base = name.trim()
        if (free(base)) return base
        val tagged = "$base (${author?.trim()?.takeIf { it.isNotEmpty() } ?: "importé"})"
        if (free(tagged)) return tagged
        return (2..99).map { "$tagged $it" }.firstOrNull(::free) ?: "$tagged ${System.currentTimeMillis()}"
    }

    /** The template as shared: structure only (no load, note, photo, history). */
    fun export(template: WorkoutTemplateEntity, lines: List<TemplateLineWithExercise>, author: String?, appVersion: String) =
        SharedTemplateFile(
            appVersion = appVersion,
            author = author?.trim()?.takeIf { it.isNotEmpty() },
            template = SharedTemplate(
                name = template.name,
                code = template.code,
                exercises = lines.sortedBy { it.line.position }.mapIndexed { i, (line, e) ->
                    SharedExercise(
                        position = i + 1,
                        libraryId = e.libraryId,
                        name = e.name,
                        type = e.type.name,
                        muscleGroup = e.muscleGroup.name,
                        perSide = e.perSide,
                        loadIncrementKg = e.loadIncrementKg,
                        targetSets = line.targetSets,
                        targetRepsMin = line.targetRepsMin,
                        targetRepsMax = line.targetRepsMax,
                        targetDurationSec = line.targetDurationSec,
                        restSec = line.restSec,
                        optional = line.optional,
                        supersetGroup = line.supersetGroup,
                    )
                },
            ),
        )

    /** A new exercise of the catalogue from the file's values (the defaults of its first line). */
    fun newExercise(s: SharedExercise) = ExerciseEntity(
        name = s.name.trim(),
        muscleGroup = s.group!!,
        type = s.exerciseType!!,
        perSide = s.perSide,
        loadIncrementKg = s.loadIncrementKg,
        defaultTargetSets = s.targetSets,
        defaultRepsMin = s.targetRepsMin,
        defaultRepsMax = s.targetRepsMax,
        defaultDurationSec = s.targetDurationSec,
        libraryId = s.libraryId,
    )

    /** A file name from the template's name: "B · Dos" → "b-dos.gymflemme". */
    fun fileName(name: String): String =
        DishList.fold(name).replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "modele" } + ".gymflemme"
}
