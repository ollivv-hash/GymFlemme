package fr.olivier.forme.feature.sport.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.core.settings.RestTimerState
import fr.olivier.forme.core.backup.AutoBackupScheduler
import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.feature.heart.data.HeartSessionRecorder
import fr.olivier.forme.feature.heart.data.LiveHeart
import fr.olivier.forme.feature.sport.data.CardioEntryEntity
import fr.olivier.forme.feature.sport.data.ExerciseEntity
import fr.olivier.forme.feature.sport.data.ExerciseRepository
import fr.olivier.forme.feature.sport.data.SessionEntity
import fr.olivier.forme.feature.sport.data.SessionExerciseEntity
import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.feature.sport.data.SetEntryEntity
import fr.olivier.forme.feature.sport.data.toWorkSet
import fr.olivier.forme.feature.sport.domain.ExerciseType
import fr.olivier.forme.feature.sport.domain.Prefill
import fr.olivier.forme.feature.sport.domain.ProgressionRule
import fr.olivier.forme.feature.sport.domain.SessionExerciseStatus
import fr.olivier.forme.feature.sport.domain.SetTarget
import fr.olivier.forme.feature.sport.domain.SupersetRules
import fr.olivier.forme.feature.sport.domain.Suggestion
import fr.olivier.forme.feature.sport.domain.WorkSet
import fr.olivier.forme.feature.sport.timer.RestTimer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToLong

/** What the previous sessions say about an exercise; loaded once per exercise. */
data class ExerciseHistoryHint(
    val lastTime: List<SetEntryEntity>,
    val lastCardio: CardioEntryEntity?,
    val suggestion: Suggestion?,
)

data class CardioDraft(val durationSec: Int, val distanceM: Int?, val calories: Int?)

data class ExerciseUi(
    val line: SessionExerciseEntity,
    val exercise: ExerciseEntity,
    val sets: List<SetEntryEntity>,
    val cardio: CardioEntryEntity?,
    val hint: ExerciseHistoryHint?,
    val draft: WorkSet,
    val cardioDraft: CardioDraft,
) {
    val workingSets get() = sets.filterNot { it.isWarmup }
    val finished get() = line.status != SessionExerciseStatus.PENDING
}

data class ActiveSessionUi(
    val session: SessionEntity? = null,
    val exercises: List<ExerciseUi> = emptyList(),
    val expandedLineId: Long? = null,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    private val sessions: SessionRepository,
    private val exercises: ExerciseRepository,
    private val settings: SettingsRepository,
    private val restTimer: RestTimer,
    private val autoBackup: AutoBackupScheduler,
    private val heartRecorder: HeartSessionRecorder,
) : ViewModel() {

    private val hints = MutableStateFlow<Map<Long, ExerciseHistoryHint>>(emptyMap())
    private val drafts = MutableStateFlow<Map<Long, WorkSet>>(emptyMap())
    private val cardioDrafts = MutableStateFlow<Map<Long, CardioDraft>>(emptyMap())
    private val manualExpanded = MutableStateFlow<Long?>(null)

    /** Exercises whose note was written during this session: shown at the next session, not now. */
    private val _notedThisSession = MutableStateFlow<Set<Long>>(emptySet())
    val notedThisSession: StateFlow<Set<Long>> = _notedThisSession

    val restTimerState: StateFlow<RestTimerState?> =
        restTimer.state.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val catalog = exercises.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val session = sessions.observeActive().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Live heart rate from the watch (V2.1); recording starts with the session and keeps going off screen. */
    val liveHeart: StateFlow<LiveHeart> = heartRecorder.state

    init {
        viewModelScope.launch {
            session.filterNotNull().map { it.id }.distinctUntilChanged().collect { heartRecorder.start(it) }
        }
    }

    /** After the Bluetooth permission was granted (or Bluetooth turned on). */
    fun retryHeart() = viewModelScope.launch { heartRecorder.retry() }

    private data class Raw(
        val session: SessionEntity?,
        val lines: List<Pair<SessionExerciseEntity, ExerciseEntity>>,
        val sets: List<SetEntryEntity>,
        val cardio: List<CardioEntryEntity>,
    )

    // Built on the repository flow (not the StateFlow above) so the first value is the real DB state.
    private val raw: Flow<Raw?> = sessions.observeActive().flatMapLatest { s ->
        if (s == null) {
            flowOf(Raw(null, emptyList(), emptyList(), emptyList()))
        } else {
            combine(sessions.observeLines(s.id), sessions.observeSets(s.id), sessions.observeCardio(s.id)) { lines, sets, cardio ->
                Raw(s, lines.map { it.line to it.exercise }, sets, cardio)
            }
        }
    }

    val ui: StateFlow<ActiveSessionUi> = combine(raw, hints, drafts, cardioDrafts, manualExpanded) { r, h, d, c, expanded ->
        if (r == null) return@combine ActiveSessionUi()
        val items = r.lines.map { (line, exercise) ->
            val sets = r.sets.filter { it.exerciseId == exercise.id }
            val hint = h[exercise.id]
            val cardio = r.cardio.firstOrNull { it.exerciseId == exercise.id }
            ExerciseUi(
                line = line,
                exercise = exercise,
                sets = sets,
                cardio = cardio,
                hint = hint,
                draft = d[line.id] ?: Prefill.nextSet(
                    doneThisSession = sets.filterNot { it.isWarmup }.map { it.toWorkSet() },
                    lastTime = hint?.lastTime.orEmpty().map { it.toWorkSet() },
                    target = SetTarget(line.targetRepsMin, line.targetRepsMax, line.targetDurationSec),
                ),
                cardioDraft = c[line.id] ?: (cardio ?: hint?.lastCardio)?.let { CardioDraft(it.durationSec, it.distanceM, it.caloriesDisplayed) }
                    ?: CardioDraft(line.targetDurationSec ?: 300, null, null),
            )
        }
        r.session?.let { s -> r.lines.map { it.second }.distinctBy { it.id }.filterNot { it.id in h }.forEach { loadHint(s.id, it) } }
        val expandedId = when (expanded) {
            COLLAPSED -> null
            else -> expanded?.takeIf { id -> items.any { it.line.id == id } } ?: items.firstOrNull { !it.finished }?.line?.id
        }
        ActiveSessionUi(r.session, items, expandedId, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveSessionUi())

    private val loadingHints = mutableSetOf<Long>()

    private fun loadHint(sessionId: Long, exercise: ExerciseEntity) {
        if (!loadingHints.add(exercise.id)) return
        viewModelScope.launch {
            val hint = ExerciseHistoryHint(
                lastTime = sessions.lastWorkingSets(exercise.id, sessionId),
                lastCardio = sessions.lastCardio(exercise.id, sessionId),
                suggestion = ProgressionRule.suggest(
                    exercise.type,
                    exercise.loadIncrementKg,
                    sessions.previousPerformance(exercise.id, sessionId),
                ),
            )
            hints.update { it + (exercise.id to hint) }
        }
    }

    /** Tapping the expanded exercise collapses it. */
    fun expand(lineId: Long) = manualExpanded.update { current ->
        if (current == lineId || (current == null && ui.value.expandedLineId == lineId)) COLLAPSED else lineId
    }

    fun updateDraft(item: ExerciseUi, transform: (WorkSet) -> WorkSet) =
        drafts.update { it + (item.line.id to transform(item.draft)) }

    fun changeLoad(item: ExerciseUi, steps: Int) = updateDraft(item) {
        val next = ((it.loadKg ?: 0.0) + steps * item.exercise.loadIncrementKg).coerceAtLeast(0.0)
        it.copy(loadKg = (next * 100).roundToLong() / 100.0)
    }

    fun applySuggestion(item: ExerciseUi) {
        val suggestion = item.hint?.suggestion ?: return
        updateDraft(item) { Prefill.apply(it, suggestion) }
    }

    fun updateCardio(item: ExerciseUi, transform: (CardioDraft) -> CardioDraft) =
        cardioDrafts.update { it + (item.line.id to transform(item.cardioDraft)) }

    fun validateSet(item: ExerciseUi, warmup: Boolean) = viewModelScope.launch {
        val sessionId = session.value?.id ?: return@launch
        val d = item.draft
        sessions.logSet(
            SetEntryEntity(
                sessionId = sessionId,
                exerciseId = item.exercise.id,
                setIndex = if (warmup) -1 else item.workingSets.size,
                loadKg = d.loadKg,
                reps = d.reps,
                durationSec = d.durationSec,
                rir = if (warmup) null else d.rir,
                isWarmup = warmup,
                completedAt = java.time.Instant.EPOCH, // replaced by the repository clock
            ),
        )
        drafts.update { it - item.line.id }
        if (warmup) return@launch
        val doneCount = item.workingSets.size + 1
        val completesExercise = doneCount >= item.line.targetSets && !item.finished
        if (completesExercise) {
            sessions.setStatus(item.line.id, SessionExerciseStatus.DONE)
            manualExpanded.value = null
        }
        val partner = partnerOf(item)
        if (partner != null) {
            val partnerFinished = partner.finished || partner.workingSets.size >= partner.line.targetSets
            val isLast = item.line.position > partner.line.position
            // Superset: go straight to the other exercise, rest only after the pair.
            if (!SupersetRules.restAfterSet(isLast, partnerFinished)) return@launch
        }
        startRest(item, doneCount, completesExercise)
    }

    private suspend fun startRest(item: ExerciseUi, doneCount: Int, completesExercise: Boolean) {
        val rest = item.line.restSec ?: settings.currentDefaultRestSec()
        val label = if (completesExercise) {
            val next = ui.value.exercises.firstOrNull { !it.finished && it.line.id != item.line.id }
            next?.let { "Ensuite : ${it.exercise.name}" } ?: "Dernier exercice terminé"
        } else {
            "${item.exercise.name} — série ${doneCount + 1}"
        }
        restTimer.start(rest, label)
    }

    /** The other exercise of the superset, if [item] belongs to one. */
    fun partnerOf(item: ExerciseUi): ExerciseUi? = item.line.supersetGroup?.let { g ->
        ui.value.exercises.firstOrNull { it.line.supersetGroup == g && it.line.id != item.line.id }
    }

    fun toggleSuperset(item: ExerciseUi) = viewModelScope.launch {
        session.value?.id?.let { sessions.toggleSuperset(it, item.line.id) }
    }

    fun deleteSet(set: SetEntryEntity) = viewModelScope.launch { sessions.deleteSet(set.id) }

    fun saveCardio(item: ExerciseUi) = viewModelScope.launch {
        val sessionId = session.value?.id ?: return@launch
        val d = item.cardioDraft
        sessions.saveCardio(
            CardioEntryEntity(
                id = item.cardio?.id ?: 0,
                sessionId = sessionId,
                exerciseId = item.exercise.id,
                durationSec = d.durationSec,
                distanceM = d.distanceM,
                pacePer500mSec = Prefill.pacePer500m(d.durationSec, d.distanceM),
                caloriesDisplayed = d.calories,
            ),
        )
        cardioDrafts.update { it - item.line.id }
        if (!item.finished) sessions.setStatus(item.line.id, SessionExerciseStatus.DONE)
        manualExpanded.value = null
    }

    fun setSkipped(item: ExerciseUi, skipped: Boolean) = viewModelScope.launch {
        sessions.setStatus(item.line.id, if (skipped) SessionExerciseStatus.SKIPPED else SessionExerciseStatus.PENDING)
        manualExpanded.value = null
    }

    fun reopen(item: ExerciseUi) = viewModelScope.launch {
        sessions.setStatus(item.line.id, SessionExerciseStatus.PENDING)
        manualExpanded.value = item.line.id
    }

    fun saveNote(item: ExerciseUi, note: String) = viewModelScope.launch {
        exercises.setNextNote(item.exercise.id, note)
        _notedThisSession.update { if (note.isBlank()) it - item.exercise.id else it + item.exercise.id }
    }

    fun dismissNote(item: ExerciseUi) = viewModelScope.launch { exercises.setNextNote(item.exercise.id, null) }

    /** All exercises (archived included), to name the replaced one. */
    val allExercises = exercises.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun replace(item: ExerciseUi, exerciseId: Long) = viewModelScope.launch {
        session.value?.id?.let { sessions.replaceExercise(it, item.line, exerciseId) }
        manualExpanded.value = null
    }

    fun move(item: ExerciseUi, delta: Int) = viewModelScope.launch {
        session.value?.id?.let { sessions.moveLine(it, item.line.id, delta) }
    }

    fun remove(item: ExerciseUi) = viewModelScope.launch {
        session.value?.id?.let { sessions.removeLine(it, item.line) }
    }

    fun addExercise(exerciseId: Long) = viewModelScope.launch {
        session.value?.id?.let { sessions.addExercise(it, exerciseId) }
    }

    private companion object {
        const val COLLAPSED = -1L
    }

    fun extendRest(deltaSec: Int) = viewModelScope.launch { restTimer.extend(deltaSec, "Repos") }
    fun stopRest() = viewModelScope.launch { restTimer.cancel() }

    fun finish(feeling: Int?, notes: String, then: (Long) -> Unit) = viewModelScope.launch {
        val id = session.value?.id ?: return@launch
        restTimer.cancel()
        sessions.finish(id, feeling, notes)
        heartRecorder.stop(id, save = true)
        autoBackup.requestNow()
        then(id)
    }

    fun abandon(then: () -> Unit) = viewModelScope.launch {
        val id = session.first()?.id ?: return@launch
        restTimer.cancel()
        heartRecorder.stop(id, save = false)
        sessions.delete(id)
        then()
    }
}
