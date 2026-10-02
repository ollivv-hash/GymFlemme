package fr.olivier.forme.feature.sport.data

import androidx.room.withTransaction
import fr.olivier.forme.core.db.FormeDatabase
import fr.olivier.forme.feature.sport.domain.CardioResult
import fr.olivier.forme.feature.sport.domain.ExerciseReport
import fr.olivier.forme.feature.sport.domain.ExerciseSets
import fr.olivier.forme.feature.sport.domain.ExerciseType
import fr.olivier.forme.feature.sport.domain.NewRecords
import fr.olivier.forme.feature.sport.domain.PreviousComparison
import fr.olivier.forme.feature.sport.domain.PreviousPerformance
import fr.olivier.forme.feature.sport.domain.RecordsCalculator
import fr.olivier.forme.feature.sport.domain.ReportSet
import fr.olivier.forme.feature.sport.domain.SessionExerciseStatus
import fr.olivier.forme.feature.sport.domain.SessionReport
import fr.olivier.forme.feature.sport.domain.SessionSets
import fr.olivier.forme.feature.sport.domain.SupersetRules
import fr.olivier.forme.feature.sport.domain.VolumeCalculator
import fr.olivier.forme.feature.sport.domain.WorkSet
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

fun SetEntryEntity.toWorkSet() = WorkSet(loadKg, reps, durationSec, rir)
fun ExerciseSetRow.toWorkSet() = WorkSet(loadKg, reps, durationSec, rir)

/** Groups history rows into per-session sets (keeps chronological order). */
fun List<ExerciseSetRow>.toSessionSets(): List<SessionSets> =
    groupBy { it.sessionId }.map { (sessionId, rows) ->
        SessionSets(sessionId, rows.first().targetRepsMin, rows.map { it.toWorkSet() })
    }

@Singleton
class SessionRepository @Inject constructor(
    private val db: FormeDatabase,
    private val dao: SessionDao,
    private val templateDao: TemplateDao,
    private val exerciseDao: ExerciseDao,
    private val clock: Clock,
) {
    fun observeActive(): Flow<SessionEntity?> = dao.observeActiveSession()
    fun observeSession(id: Long): Flow<SessionEntity?> = dao.observeSession(id)
    fun observeLines(sessionId: Long): Flow<List<SessionLineWithExercise>> = dao.observeSessionLines(sessionId)
    fun observeSets(sessionId: Long): Flow<List<SetEntryEntity>> = dao.observeSets(sessionId)
    fun observeCardio(sessionId: Long): Flow<List<CardioEntryEntity>> = dao.observeCardio(sessionId)
    fun observeRecent(limit: Int): Flow<List<SessionEntity>> = dao.observeRecentSessions(limit)
    fun observeFinished(): Flow<List<SessionEntity>> = dao.observeFinishedSessions()
    fun observeExerciseHistory(exerciseId: Long): Flow<List<ExerciseSetRow>> = dao.observeExerciseHistory(exerciseId)
    fun observeCardioHistory(exerciseId: Long): Flow<List<CardioEntryEntity>> = dao.observeCardioHistory(exerciseId)

    /** Starts a session from a template (null = free session); resumes the running one if any. */
    suspend fun start(templateId: Long?): Long = db.withTransaction {
        dao.getActiveSession()?.let { return@withTransaction it.id }
        val template = templateId?.let { templateDao.getById(it) }
        val sessionId = dao.insertSession(
            SessionEntity(
                templateId = template?.id,
                templateNameSnapshot = template?.name,
                templateCodeSnapshot = template?.code,
                startedAt = clock.instant(),
            ),
        )
        if (template != null) {
            dao.insertSessionExercises(
                templateDao.getLines(template.id).mapIndexed { index, (line, _) ->
                    SessionExerciseEntity(
                        sessionId = sessionId,
                        exerciseId = line.exerciseId,
                        position = index,
                        targetSets = line.targetSets,
                        targetRepsMin = line.targetRepsMin,
                        targetRepsMax = line.targetRepsMax,
                        targetDurationSec = line.targetDurationSec,
                        restSec = line.restSec,
                        optional = line.optional,
                        supersetGroup = line.supersetGroup,
                    )
                },
            )
        }
        sessionId
    }

    /** Appends a catalogue exercise, with its default target. */
    suspend fun addExercise(sessionId: Long, exerciseId: Long) = db.withTransaction {
        val exercise = exerciseDao.getById(exerciseId) ?: return@withTransaction
        val position = (dao.getSessionLines(sessionId).maxOfOrNull { it.line.position } ?: -1) + 1
        dao.insertSessionExercise(
            SessionExerciseEntity(
                sessionId = sessionId,
                exerciseId = exerciseId,
                position = position,
                targetSets = exercise.defaultTargetSets,
                targetRepsMin = exercise.defaultRepsMin,
                targetRepsMax = exercise.defaultRepsMax,
                targetDurationSec = exercise.defaultDurationSec,
            ),
        )
    }

    suspend fun setStatus(lineId: Long, status: SessionExerciseStatus) = dao.setSessionExerciseStatus(lineId, status)

    /** Moves a line by [delta] positions (−1 = up) and renumbers positions. */
    suspend fun moveLine(sessionId: Long, lineId: Long, delta: Int) = db.withTransaction {
        val lines = dao.getSessionLines(sessionId).map { it.line }.toMutableList()
        val from = lines.indexOfFirst { it.id == lineId }
        val to = from + delta
        if (from < 0 || to !in lines.indices) return@withTransaction
        lines.add(to, lines.removeAt(from))
        dao.updateSessionExercises(withNormalizedSupersets(lines))
    }

    /** Links a line with the next one as a superset, or unlinks them (running session only). */
    suspend fun toggleSuperset(sessionId: Long, lineId: Long) = db.withTransaction {
        val lines = dao.getSessionLines(sessionId).map { it.line }
        val index = lines.indexOfFirst { it.id == lineId }
        val groups = SupersetRules.toggleWithNext(lines.map { it.supersetGroup }, index)
        dao.updateSessionExercises(lines.mapIndexed { i, l -> l.copy(position = i, supersetGroup = groups[i]) })
    }

    private fun withNormalizedSupersets(lines: List<SessionExerciseEntity>): List<SessionExerciseEntity> {
        val groups = SupersetRules.normalize(lines.map { it.supersetGroup })
        return lines.mapIndexed { i, l -> l.copy(position = i, supersetGroup = groups[i]) }
    }

    /**
     * Busy machine: replaces [line]'s exercise by [newExerciseId] in this session only (the template is untouched).
     * Targets of the slot are kept when both exercises have the same type, otherwise the new exercise's defaults
     * are used. If working sets were already done on the original, it is kept (marked done) and the replacement
     * is inserted right after it.
     */
    suspend fun replaceExercise(sessionId: Long, line: SessionExerciseEntity, newExerciseId: Long) = db.withTransaction {
        val old = exerciseDao.getById(line.exerciseId) ?: return@withTransaction
        val new = exerciseDao.getById(newExerciseId) ?: return@withTransaction
        val targets = if (old.type == new.type) {
            line
        } else {
            line.copy(
                targetSets = new.defaultTargetSets,
                targetRepsMin = new.defaultRepsMin,
                targetRepsMax = new.defaultRepsMax,
                targetDurationSec = new.defaultDurationSec,
            )
        }
        val replacement = targets.copy(
            exerciseId = new.id,
            status = SessionExerciseStatus.PENDING,
            replacedExerciseId = line.replacedExerciseId ?: line.exerciseId,
        )
        val sets = dao.getSets(sessionId).filter { it.exerciseId == old.id }
        val started = sets.any { !it.isWarmup } || dao.getCardio(sessionId).any { it.exerciseId == old.id }
        if (!started) {
            dao.deleteSetsOf(sessionId, old.id) // warm-ups of the machine that was abandoned
            dao.updateSessionExercises(listOf(replacement))
        } else {
            val lines = dao.getSessionLines(sessionId).map { it.line }.toMutableList()
            val index = lines.indexOfFirst { it.id == line.id }
            lines[index] = line.copy(status = SessionExerciseStatus.DONE)
            val newId = dao.insertSessionExercise(replacement.copy(id = 0, position = index + 1))
            lines.add(index + 1, replacement.copy(id = newId))
            dao.updateSessionExercises(withNormalizedSupersets(lines))
        }
    }

    /** Removes an exercise from the running session together with its sets. */
    suspend fun removeLine(sessionId: Long, line: SessionExerciseEntity) = db.withTransaction {
        dao.deleteSetsOf(sessionId, line.exerciseId)
        dao.deleteSessionExercise(line.id)
        dao.updateSessionExercises(withNormalizedSupersets(dao.getSessionLines(sessionId).map { it.line }))
    }

    suspend fun logSet(set: SetEntryEntity): Long = dao.insertSet(set.copy(completedAt = clock.instant()))

    suspend fun deleteSet(id: Long) = dao.deleteSet(id)

    suspend fun saveCardio(entry: CardioEntryEntity): Long = dao.upsertCardio(entry)

    suspend fun deleteCardio(id: Long) = dao.deleteCardio(id)

    /** Values of the last time (seed included) used to pre-fill the sets. */
    suspend fun lastWorkingSets(exerciseId: Long, currentSessionId: Long): List<SetEntryEntity> =
        dao.getLastWorkingSets(exerciseId, currentSessionId, includeSeed = true)

    suspend fun lastCardio(exerciseId: Long, currentSessionId: Long): CardioEntryEntity? =
        dao.getLastCardio(exerciseId, currentSessionId)

    /** Last real (non-seed) performance, input of the progression rule. */
    suspend fun previousPerformance(exerciseId: Long, currentSessionId: Long): PreviousPerformance? {
        val sets = dao.getLastWorkingSets(exerciseId, currentSessionId, includeSeed = false)
        if (sets.isEmpty()) return null
        val target = dao.getSessionExercise(sets.first().sessionId, exerciseId)
        return PreviousPerformance(
            sets = sets.map { it.toWorkSet() },
            targetSets = target?.targetSets,
            targetRepsMax = target?.targetRepsMax,
            targetDurationSec = target?.targetDurationSec,
        )
    }

    suspend fun finish(sessionId: Long, feeling: Int?, notes: String?) {
        val session = dao.getSession(sessionId) ?: return
        dao.updateSession(session.copy(endedAt = clock.instant(), feeling = feeling, notes = notes?.trim()?.ifEmpty { null }))
    }

    suspend fun updateFeedback(sessionId: Long, feeling: Int?, notes: String?) {
        val session = dao.getSession(sessionId) ?: return
        dao.updateSession(session.copy(feeling = feeling, notes = notes?.trim()?.ifEmpty { null }))
    }

    suspend fun delete(sessionId: Long) = dao.deleteSession(sessionId)

    suspend fun buildReport(sessionId: Long): SessionReport? {
        val session = dao.getSession(sessionId) ?: return null
        val zone = clock.zone
        val lines = dao.getSessionLines(sessionId)
        val sets = dao.getSets(sessionId)
        val cardio = dao.getCardio(sessionId)

        val exercises = lines.map { (line, exercise) ->
            val exerciseSets = sets.filter { it.exerciseId == exercise.id }.sortedBy { it.completedAt }
            val working = exerciseSets.filterNot { it.isWarmup }.map { it.toWorkSet() }
            val records = if (exercise.type == ExerciseType.WEIGHTED && working.isNotEmpty()) {
                val previous = dao.getExerciseHistory(exercise.id, sessionId)
                    .filter { it.startedAt < session.startedAt }
                    .toSessionSets()
                RecordsCalculator.newRecords(SessionSets(sessionId, line.targetRepsMin, working), previous)
            } else {
                NewRecords(null, null)
            }
            ExerciseReport(
                exerciseId = exercise.id,
                name = exercise.name +
                    (line.replacedExerciseId?.let { exerciseDao.getById(it) }?.let { " (remplace ${it.name})" } ?: "") +
                    (supersetPartner(lines, line)?.let { " (superset avec ${it.name})" } ?: ""),
                muscleGroup = exercise.muscleGroup,
                type = exercise.type,
                perSide = exercise.perSide,
                skipped = line.status == SessionExerciseStatus.SKIPPED && exerciseSets.isEmpty(),
                sets = exerciseSets.map { ReportSet(it.toWorkSet(), it.isWarmup) },
                cardio = cardio.firstOrNull { it.exerciseId == exercise.id }
                    ?.let { CardioResult(it.durationSec, it.distanceM, it.pacePer500mSec, it.caloriesDisplayed) },
                newRecords = records,
            )
        }

        val previous = session.templateId?.let { templateId ->
            dao.getPreviousSessionOfTemplate(templateId, session.startedAt.toEpochMilli())
        }?.let { prev ->
            PreviousComparison(LocalDateTime.ofInstant(prev.startedAt, zone), volumeOf(prev.id))
        }

        return SessionReport(
            startedAt = LocalDateTime.ofInstant(session.startedAt, zone),
            durationMin = session.endedAt?.let { Duration.between(session.startedAt, it).toMinutes().toInt() },
            templateCode = session.templateCodeSnapshot,
            templateName = session.templateNameSnapshot,
            feeling = session.feeling,
            notes = session.notes,
            exercises = exercises,
            volume = VolumeCalculator.session(
                exercises.map { ex ->
                    ExerciseSets(ex.muscleGroup, ex.type, ex.perSide, ex.sets.filterNot { it.isWarmup }.map { it.work })
                },
            ),
            previous = previous,
        )
    }

    private fun supersetPartner(lines: List<SessionLineWithExercise>, line: SessionExerciseEntity): ExerciseEntity? =
        line.supersetGroup?.let { g -> lines.firstOrNull { it.line.supersetGroup == g && it.line.id != line.id }?.exercise }

    private suspend fun volumeOf(sessionId: Long): fr.olivier.forme.feature.sport.domain.SessionVolume {
        val sets = dao.getSets(sessionId).filterNot { it.isWarmup }
        return VolumeCalculator.session(
            dao.getSessionLines(sessionId).map { (_, exercise) ->
                ExerciseSets(
                    exercise.muscleGroup,
                    exercise.type,
                    exercise.perSide,
                    sets.filter { it.exerciseId == exercise.id }.map { it.toWorkSet() },
                )
            },
        )
    }
}
