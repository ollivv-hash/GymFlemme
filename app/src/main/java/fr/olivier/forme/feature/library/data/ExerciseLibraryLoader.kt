package fr.olivier.forme.feature.library.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.olivier.forme.core.db.FormeDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the embedded Open Exercise Illustrations library (third_party/open-exercise-illustrations) into Room.
 * Runs once per dataset version: a newer library in a future release replaces the table, the user's exercises
 * (and their libraryId) are not touched.
 */
@Singleton
class ExerciseLibraryLoader @Inject constructor(
    private val db: FormeDatabase,
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context,
) {
    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        val dao = db.libraryDao()
        if (dataStore.data.first()[LOADED_VERSION] == DATASET_VERSION && dao.count() > 0) return@withContext
        val rows = context.assets.open("$ASSET_DIR/exercises.json").use(::parse)
        db.withTransaction {
            dao.deleteAll()
            rows.chunked(200).forEach { dao.insertAll(it) }
        }
        dataStore.edit { it[LOADED_VERSION] = DATASET_VERSION }
    }

    companion object {
        const val ASSET_DIR = "exercise_library"
        /** Upstream data version + pinned commit (third_party/open-exercise-illustrations/SOURCE.md). */
        const val DATASET_VERSION = "1.0.0+2786aef"
        private val LOADED_VERSION = stringPreferencesKey("exercise_library_version")

        private val json = Json { ignoreUnknownKeys = true }

        /** Exercises then variants; a variant takes its original's fields, except what it redefines. */
        fun parse(input: InputStream): List<LibraryExerciseEntity> {
            val file = json.decodeFromString(LibraryFile.serializer(), input.bufferedReader().readText())
            val exercises = file.exercises.map { it.toEntity() }
            val byId = exercises.associateBy { it.id }
            val variants = file.variants.map { v ->
                val base = requireNotNull(byId[v.imageOf]) { "Variant ${v.id}: unknown imageOf ${v.imageOf}" }
                base.copy(
                    id = v.id,
                    name = v.name,
                    aliases = "",
                    imageOf = v.imageOf,
                    difference = v.difference,
                    equipment = v.equipment?.joinToString(",") ?: base.equipment,
                    equipmentLabel = v.equipmentLabel ?: base.equipmentLabel,
                    primaryMuscles = v.muscles?.primary?.joinToString(",") ?: base.primaryMuscles,
                    secondaryMuscles = v.muscles?.secondary?.joinToString(",") ?: base.secondaryMuscles,
                    alsoTargets = v.muscles?.alsoTargets?.joinToString(",") ?: base.alsoTargets,
                )
            }
            return exercises + variants
        }

        private fun LibraryExercise.toEntity() = LibraryExerciseEntity(
            id = id,
            name = name,
            aliases = aliases.joinToString("\n"),
            imageOf = null,
            difference = null,
            equipment = equipment.joinToString(","),
            equipmentLabel = equipmentLabel,
            machineType = machineType,
            primaryMuscles = muscles.primary.joinToString(","),
            secondaryMuscles = muscles.secondary.joinToString(","),
            alsoTargets = muscles.alsoTargets.joinToString(","),
            movementPattern = movementPattern,
            category = category,
            level = level,
            impact = impact,
            unilateral = unilateral,
            isWarmUp = isWarmUp,
            suitableFor = suitableFor.joinToString(","),
            view = view,
            frames = frames.joinToString(","),
            imageId = id,
            width = width,
            height = height,
        )
    }
}

// --- exercises.json (schema in third_party/open-exercise-illustrations/README.md) -----------------------------

@Serializable
private class LibraryFile(val version: String, val exercises: List<LibraryExercise>, val variants: List<LibraryVariant> = emptyList())

@Serializable
private class LibraryMuscles(
    val primary: List<String> = emptyList(),
    val secondary: List<String> = emptyList(),
    val alsoTargets: List<String> = emptyList(),
)

@Serializable
private class LibraryExercise(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val equipment: List<String> = emptyList(),
    val equipmentLabel: String,
    val machineType: String? = null,
    val muscles: LibraryMuscles,
    val movementPattern: String,
    val category: String,
    val unilateral: Boolean,
    val impact: String,
    val level: String,
    val suitableFor: List<String> = emptyList(),
    val isWarmUp: Boolean,
    val view: String,
    val frames: List<String>,
    val width: Int,
    val height: Int,
)

@Serializable
private class LibraryVariant(
    val id: String,
    val name: String,
    val imageOf: String,
    val difference: String? = null,
    val equipment: List<String>? = null,
    val equipmentLabel: String? = null,
    val muscles: LibraryMuscles? = null,
)
