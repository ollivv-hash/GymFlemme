package fr.olivier.forme.feature.library.domain

import fr.olivier.forme.feature.library.data.LibraryExerciseEntity
import fr.olivier.forme.feature.library.data.LibraryRepository
import fr.olivier.forme.feature.sport.data.ExerciseEntity
import fr.olivier.forme.feature.sport.domain.ExerciseType

/** Filters of the library screen (V2.10); null = no filter. */
data class LibraryFilter(
    val query: String = "",
    /** A main muscle key ("quads"). */
    val muscle: String? = null,
    /** An equipment key ("dumbbell"), or [BODYWEIGHT] for no equipment. */
    val equipment: String? = null,
    /** A place key ("gym", "home"…). */
    val place: String? = null,
) {
    val active get() = muscle != null || equipment != null || place != null

    companion object {
        const val BODYWEIGHT = "none"
    }
}

/** Browsing the library and copying an entry into the user's catalogue (V2.10). */
object LibraryCatalog {
    fun filter(entries: List<LibraryExerciseEntity>, filter: LibraryFilter): List<LibraryExerciseEntity> =
        LibraryRepository.search(entries, filter.query).filter { e ->
            (filter.muscle == null || filter.muscle in e.primaryList) &&
                (filter.equipment == null || filter.equipment in e.equipmentList ||
                    (filter.equipment == LibraryFilter.BODYWEIGHT && e.equipmentList.isEmpty())) &&
                (filter.place == null || filter.place in e.suitableForList)
        }

    /** Main muscles present in the library, in the order of the French labels. */
    fun muscles(entries: List<LibraryExerciseEntity>): List<String> {
        val used = entries.flatMap { it.primaryList }.toSet()
        return LibraryLabels.muscles.keys.filter { it in used } + (used - LibraryLabels.muscles.keys).sorted()
    }

    /** Equipment present in the library, bodyweight first, then by French label. */
    fun equipment(entries: List<LibraryExerciseEntity>): List<String> {
        val used = entries.flatMap { it.equipmentList }.toSet()
        val bodyweight = if (entries.any { it.equipmentList.isEmpty() }) listOf(LibraryFilter.BODYWEIGHT) else emptyList()
        return bodyweight + used.sortedBy { equipmentLabel(it) }
    }

    fun equipmentLabel(key: String) = if (key == LibraryFilter.BODYWEIGHT) "Poids du corps" else LibraryLabels.equipment[key] ?: key

    /**
     * The catalogue exercise created by "Ajouter à mon catalogue": English name, group and type deduced from the
     * library (validated in step 1), linked to its illustration; targets as for a new exercise, to adjust later.
     */
    fun newExercise(entry: LibraryExerciseEntity): ExerciseEntity {
        val type = LibraryLabels.exerciseTypeOf(entry)
        val base = ExerciseEntity(
            name = entry.name,
            muscleGroup = LibraryLabels.muscleGroupOf(entry),
            type = type,
            libraryId = entry.id,
        )
        return when (type) {
            ExerciseType.WEIGHTED, ExerciseType.BODYWEIGHT -> base.copy(defaultRepsMin = 10, defaultRepsMax = 12)
            ExerciseType.TIMED -> base.copy(defaultDurationSec = 30, perSide = entry.unilateral)
            ExerciseType.CARDIO -> base.copy(defaultTargetSets = 1, defaultDurationSec = 20 * 60)
        }
    }
}
