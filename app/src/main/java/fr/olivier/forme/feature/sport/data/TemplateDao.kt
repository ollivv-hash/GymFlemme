package fr.olivier.forme.feature.sport.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Query("SELECT * FROM workout_template ORDER BY archived, orderInRotation IS NULL, orderInRotation, code, name")
    fun observeAll(): Flow<List<WorkoutTemplateEntity>>

    @Query("SELECT * FROM workout_template WHERE id = :id")
    suspend fun getById(id: Long): WorkoutTemplateEntity?

    @Query("SELECT * FROM workout_template")
    suspend fun getAll(): List<WorkoutTemplateEntity>

    @Insert
    suspend fun insert(template: WorkoutTemplateEntity): Long

    @Insert
    suspend fun insertAll(templates: List<WorkoutTemplateEntity>)

    @Update
    suspend fun update(template: WorkoutTemplateEntity)

    @Update
    suspend fun updateAll(templates: List<WorkoutTemplateEntity>)

    @Transaction
    @Query("SELECT * FROM template_exercise WHERE templateId = :templateId ORDER BY position")
    suspend fun getLines(templateId: Long): List<TemplateLineWithExercise>

    @Transaction
    @Query("SELECT * FROM template_exercise WHERE templateId = :templateId ORDER BY position")
    fun observeLines(templateId: Long): Flow<List<TemplateLineWithExercise>>

    @Query("SELECT * FROM template_exercise")
    suspend fun getAllLines(): List<TemplateExerciseEntity>

    @Insert
    suspend fun insertLines(lines: List<TemplateExerciseEntity>)

    @Query("DELETE FROM template_exercise WHERE templateId = :templateId")
    suspend fun deleteLines(templateId: Long)

    /** Replaces the whole content of a template (positions are renumbered by the caller). */
    @Transaction
    suspend fun saveTemplate(template: WorkoutTemplateEntity, lines: List<TemplateExerciseEntity>): Long {
        val id = if (template.id == 0L) insert(template) else template.id.also { update(template) }
        deleteLines(id)
        insertLines(lines.mapIndexed { index, line -> line.copy(id = 0, templateId = id, position = index) })
        return id
    }

    @Query("DELETE FROM template_exercise")
    suspend fun deleteAllLines()

    @Query("DELETE FROM workout_template")
    suspend fun deleteAll()
}
