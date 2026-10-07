package fr.olivier.forme.feature.library.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One exercise of the Open Exercise Illustrations library (CC0), read-only: reloaded from the APK assets when the
 * library version changes, never edited. Variants are rows too: they show the images of [imageId] and may differ
 * by [difference], muscles and equipment. Lists are stored comma-separated (see the `…List` accessors).
 */
@Entity(tableName = "library_exercise")
data class LibraryExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Other common names, newline-separated (some contain commas). */
    val aliases: String,
    /** Variant only: the exercise whose images it shows. */
    val imageOf: String?,
    /** Variant only: how it differs from what the images show. */
    val difference: String?,
    val equipment: String,
    val equipmentLabel: String,
    val machineType: String?,
    val primaryMuscles: String,
    val secondaryMuscles: String,
    val alsoTargets: String,
    val movementPattern: String,
    val category: String,
    val level: String,
    val impact: String,
    val unilateral: Boolean,
    val isWarmUp: Boolean,
    val suitableFor: String,
    val view: String,
    /** Frames in playback order: start,end / start,mid,end / hold. */
    val frames: String,
    /** Image folder: the exercise itself, or the original exercise for a variant. */
    val imageId: String,
    val width: Int,
    val height: Int,
) {
    val aliasList get() = aliases.split('\n').filter { it.isNotEmpty() }
    val equipmentList get() = equipment.csv()
    val primaryList get() = primaryMuscles.csv()
    val secondaryList get() = secondaryMuscles.csv()
    val alsoTargetsList get() = alsoTargets.csv()
    val suitableForList get() = suitableFor.csv()
    val frameList get() = frames.csv()
    val isVariant get() = imageOf != null

    /** Asset path of a frame, e.g. exercise_library/images/leg-press/start.webp. */
    fun framePath(frame: String) = "${ExerciseLibraryLoader.ASSET_DIR}/images/$imageId/$frame.webp"

    /** Still image for lists: the first frame. */
    val thumbnailPath get() = framePath(frameList.first())

    private fun String.csv() = split(',').filter { it.isNotEmpty() }
}

@Dao
interface LibraryDao {
    @Insert
    suspend fun insertAll(rows: List<LibraryExerciseEntity>)

    @Query("DELETE FROM library_exercise")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM library_exercise")
    suspend fun count(): Int

    @Query("SELECT * FROM library_exercise ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<LibraryExerciseEntity>>

    @Query("SELECT * FROM library_exercise WHERE id = :id")
    suspend fun get(id: String): LibraryExerciseEntity?

    @Query("SELECT id FROM library_exercise")
    suspend fun ids(): List<String>
}
