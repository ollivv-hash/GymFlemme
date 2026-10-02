package fr.olivier.forme.feature.sport.data

import androidx.room.withTransaction
import fr.olivier.forme.core.db.FormeDatabase
import fr.olivier.forme.feature.sport.domain.RotationResolver
import fr.olivier.forme.feature.sport.domain.RotationTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemplateRepository @Inject constructor(
    private val db: FormeDatabase,
    private val dao: TemplateDao,
    private val sessionDao: SessionDao,
) {
    fun observeAll(): Flow<List<WorkoutTemplateEntity>> = dao.observeAll()
    fun observeLines(templateId: Long): Flow<List<TemplateLineWithExercise>> = dao.observeLines(templateId)
    suspend fun get(id: Long): WorkoutTemplateEntity? = dao.getById(id)
    suspend fun getLines(templateId: Long): List<TemplateLineWithExercise> = dao.getLines(templateId)

    /** Next template of the rotation, recomputed whenever templates or finished sessions change. */
    fun observeProposed(): Flow<WorkoutTemplateEntity?> =
        combine(dao.observeAll(), sessionDao.observeFinishedTemplateIds()) { templates, finished ->
            val nextId = RotationResolver.next(
                templates.map { RotationTemplate(it.id, it.orderInRotation, it.archived) },
                finished,
            )
            templates.firstOrNull { it.id == nextId }
        }

    suspend fun save(template: WorkoutTemplateEntity, lines: List<TemplateExerciseEntity>): Long =
        dao.saveTemplate(template, lines)

    suspend fun setArchived(id: Long, archived: Boolean) {
        val template = dao.getById(id) ?: return
        // An archived template leaves the rotation; un-archiving keeps it out until re-added explicitly.
        dao.update(template.copy(archived = archived, orderInRotation = if (archived) null else template.orderInRotation))
    }

    /** [orderedIds] become the rotation, in this order; every other template leaves it. */
    suspend fun saveRotation(orderedIds: List<Long>) = db.withTransaction {
        val all = dao.getAll()
        dao.updateAll(
            all.map { t ->
                val index = orderedIds.indexOf(t.id)
                t.copy(orderInRotation = if (index >= 0) index + 1 else null)
            },
        )
    }

    /** Lines of a past session, to create a template variant from it. */
    suspend fun linesFromSession(sessionId: Long): List<TemplateExerciseEntity> =
        sessionDao.getSessionLines(sessionId).map { (line, _) ->
            TemplateExerciseEntity(
                templateId = 0,
                exerciseId = line.exerciseId,
                position = line.position,
                targetSets = line.targetSets,
                targetRepsMin = line.targetRepsMin,
                targetRepsMax = line.targetRepsMax,
                targetDurationSec = line.targetDurationSec,
                optional = line.optional,
                restSec = line.restSec,
                supersetGroup = line.supersetGroup,
            )
        }
}
