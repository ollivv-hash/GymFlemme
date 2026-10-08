package fr.olivier.forme.feature.sport.ui.home

import fr.olivier.forme.feature.badges.ui.observeNew
import fr.olivier.forme.feature.badges.ui.NewBadgesCard
import fr.olivier.forme.feature.badges.data.BadgeRepository
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.feature.bodyweight.data.BodyWeightRepository
import fr.olivier.forme.feature.bodyweight.domain.MovingAverage
import fr.olivier.forme.feature.bodyweight.domain.WeightPoint
import fr.olivier.forme.feature.bodyweight.ui.WeightEntryDialog
import fr.olivier.forme.feature.nutrition.ui.JournalViewModel
import fr.olivier.forme.feature.nutrition.ui.ProteinGauge
import androidx.compose.material3.Checkbox
import fr.olivier.forme.feature.sport.data.SessionEntity
import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.feature.sport.data.TemplateRepository
import fr.olivier.forme.feature.ai.data.AiAnalysis
import fr.olivier.forme.feature.library.data.LibraryRepository
import fr.olivier.forme.feature.library.domain.LibraryLabels
import fr.olivier.forme.feature.sport.domain.CatchUpAdvice
import fr.olivier.forme.feature.sport.domain.Comeback
import fr.olivier.forme.feature.sport.domain.CatchUpAdvisor
import fr.olivier.forme.feature.sport.domain.DeloadAdvisor
import fr.olivier.forme.feature.sport.domain.MuscleStats
import fr.olivier.forme.feature.sport.domain.Muscles
import fr.olivier.forme.feature.sport.domain.PlannedLine
import fr.olivier.forme.feature.sport.domain.TrainedSet
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.feature.sport.domain.WeeklyStats
import fr.olivier.forme.core.settings.SettingsRepository
import kotlinx.coroutines.flow.combine
import fr.olivier.forme.feature.sport.ui.templates.title
import fr.olivier.forme.ui.components.SectionTitle
import fr.olivier.forme.ui.theme.accentButtonColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import fr.olivier.forme.core.humor.Quip
import fr.olivier.forme.core.humor.QuipText
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class WeightSummary(val lastKg: Double, val lastDate: LocalDate, val average7Kg: Double)

/** Home line under the greeting (V2.16): welcome back after a break, or last week's goal missed. */
data class HomeNudge(val welcomeBack: Boolean = false, val missedGoalNext: String? = null)

/** "Pour rattraper" in the new session sheet (V2.13): the lagging muscles and the template catching up best. */
data class CatchUpUi(val advice: CatchUpAdvice, val title: String)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessions: SessionRepository,
    templates: TemplateRepository,
    library: LibraryRepository,
    private val badges: BadgeRepository,
    private val weights: BodyWeightRepository,
    private val settings: SettingsRepository,
    private val analysis: AiAnalysis,
    private val clock: Clock,
) : ViewModel() {
    private fun <T> kotlinx.coroutines.flow.Flow<T>.state(initial: T) =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val proposed = templates.observeProposed().state(null)
    val templates = templates.observeAll().map { list -> list.filterNot { it.archived } }.state(emptyList())
    val active = sessions.observeActive().state(null)
    val recent = sessions.observeRecent(5).state(emptyList())
    val weight = weights.observeAll().map { entries ->
        val last = entries.maxByOrNull { it.date } ?: return@map null
        val avg = MovingAverage.sevenDays(entries.map { WeightPoint(it.date, it.weightKg) }).last().weightKg
        WeightSummary(last.weightKg, last.date, avg)
    }.state(null)

    /** "2/3": finished sessions this week (Monday → Sunday) vs the weekly goal. */
    val weekProgress = combine(sessions.observeFinished(), settings.trainingGoals) { finished, goals ->
        val start = WeeklyStats.weekStart(LocalDate.now(clock))
        finished.count { WeeklyStats.weekStart(it.startedAt.atZone(clock.zone).toLocalDate()) == start } to goals.sessionsPerWeek
    }.state(null)

    /** Deload advice (6 complete weeks in a row, or 3 exercises stalling), unless dismissed for this week. */
    val deload = run {
        val currentWeek = WeeklyStats.weekStart(LocalDate.now(clock))
        val since = currentWeek.minusWeeks(DeloadAdvisor.STREAK_WEEKS + 6L)
        combine(
            sessions.observeWorkingSetsSince(since.atStartOfDay(clock.zone).toInstant()),
            sessions.observePlateauCount(currentWeek.minusWeeks(3).atStartOfDay(clock.zone).toInstant()),
            settings.deloadDismissedWeek,
        ) { sets, plateaus, dismissed ->
            if (dismissed == currentWeek) return@combine null
            val perWeek = sets.groupingBy { WeeklyStats.weekStart(it.startedAt.atZone(clock.zone).toLocalDate()) }.eachCount()
            DeloadAdvisor.advise(perWeek, currentWeek, plateaus)
        }.state(null)
    }

    /** Template catching up the lagging muscles of the current week better than the rotation's, if any. */
    val catchUp = run {
        val currentWeek = WeeklyStats.weekStart(LocalDate.now(clock))
        val musclesById = library.observeAll().map { entries ->
            entries.associate { it.id to Muscles(it.primaryList, it.secondaryList + it.alsoTargetsList) }
        }
        val week = combine(
            sessions.observeWorkingSetsSince(currentWeek.atStartOfDay(clock.zone).toInstant()),
            sessions.observeFinished(),
            musclesById,
        ) { rows, finished, muscles ->
            val sets = rows.map { row ->
                TrainedSet(row.startedAt.atZone(clock.zone).toLocalDate(), row.muscleGroup, row.type) to row.libraryId?.let(muscles::get)
            }
            val done = finished.count { WeeklyStats.weekStart(it.startedAt.atZone(clock.zone).toLocalDate()) == currentWeek }
            Triple(MuscleStats.week(sets, currentWeek), done, muscles)
        }
        combine(week, settings.trainingGoals, this.templates, proposed, templates.observePlannedLines()) { (muscleWeek, done, muscles), goals, active, next, lines ->
            val byTemplate = lines.groupBy { it.templateId }
            val planned = active.map { t ->
                t.id to byTemplate[t.id].orEmpty().map { PlannedLine(it.targetSets, it.type, it.libraryId?.let(muscles::get), it.optional) }
            }
            CatchUpAdvisor.advise(muscleWeek, done, goals.muscleMinSets, planned, next?.id)?.let { advice ->
                CatchUpUi(advice, active.first { it.id == advice.suggested.templateId }.title())
            }
        }.state(null)
    }

    val nudge = combine(sessions.observeFinished(), settings.trainingGoals, proposed) { finished, goals, next ->
        val today = LocalDate.now(clock)
        val days = finished.map { it.startedAt.atZone(clock.zone).toLocalDate() }
        if (Comeback.isBack(days.maxOrNull(), today)) return@combine HomeNudge(welcomeBack = true)
        val week = WeeklyStats.weekStart(today)
        val lastWeek = week.minusWeeks(1)
        val missed = Comeback.missedLastWeek(
            sessionsLastWeek = days.count { it >= lastWeek && it < week },
            sessionsThisWeek = days.count { it >= week },
            goal = goals.sessionsPerWeek,
            trainedBefore = days.any { it < lastWeek },
        )
        HomeNudge(missedGoalNext = next?.title()?.takeIf { missed })
    }.state(HomeNudge())

    /** Badges earned today or yesterday and not acknowledged yet (V2.17). */
    val newBadges = badges.observeNew().state(emptyList())

    init {
        // Badges of the history (silent for the past ones: after the update or a restore), and those earned outside
        // the app (creatine taken from the notification), announced here.
        viewModelScope.launch {
            runCatching { badges.evaluate() }.onFailure { android.util.Log.e("Forme", "Badge evaluation failed", it) }
        }
    }

    fun dismissBadges() = viewModelScope.launch { badges.markSeen(newBadges.value) }

    fun dismissDeload() = viewModelScope.launch { settings.dismissDeload(WeeklyStats.weekStart(LocalDate.now(clock))) }

    /** Hands today's session and nutrition to the AI app (V2.4). */
    fun analyse() = analysis.launch(LocalDate.now(clock))

    fun today(): LocalDate = LocalDate.now(clock)
    fun now(): LocalTime = LocalTime.now(clock)
    val zone: ZoneId get() = clock.zone

    fun start(templateId: Long?, then: () -> Unit) = viewModelScope.launch {
        sessions.start(templateId)
        then()
    }

    fun saveWeight(date: LocalDate, kg: Double) = viewModelScope.launch { weights.save(date, kg) }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Fmt.locale)

fun SessionEntity.label() =
    listOfNotNull(templateCodeSnapshot, templateNameSnapshot).joinToString(" · ").ifEmpty { "Séance libre" }

fun SessionEntity.durationMin(): Long? = endedAt?.let { Duration.between(startedAt, it).toMinutes() }

@Composable
fun HomeScreen(
    onOpenSession: () -> Unit,
    onOpenSessionDetail: (Long) -> Unit,
    onOpenWeight: () -> Unit,
    onOpenNutrition: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    journal: JournalViewModel = hiltViewModel(),
) {
    val nutrition by journal.ui.collectAsStateWithLifecycle()
    val proposed by viewModel.proposed.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val weight by viewModel.weight.collectAsStateWithLifecycle()
    val weekProgress by viewModel.weekProgress.collectAsStateWithLifecycle()
    val deload by viewModel.deload.collectAsStateWithLifecycle()
    val catchUp by viewModel.catchUp.collectAsStateWithLifecycle()
    val nudge by viewModel.nudge.collectAsStateWithLifecycle()
    val newBadges by viewModel.newBadges.collectAsStateWithLifecycle()
    var addingWeight by remember { mutableStateOf(false) }
    var choosingSession by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // V2.15: greeting of the time slot, drawn when the home screen opens.
            // V2.16: after a break, a welcome back replaces it; a missed goal last week adds a line.
            val greeting = remember { Quip.greeting(viewModel.now()) }
            QuipText(
                if (nudge.welcomeBack) Quip.WELCOME_BACK else greeting,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = if (nudge.missedGoalNext == null) 12.dp else 2.dp),
            )
            nudge.missedGoalNext?.let { next ->
                QuipText(
                    Quip.MISSED_GOAL,
                    next,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            NewBadgesCard(newBadges, onDismiss = viewModel::dismissBadges, modifier = Modifier.padding(bottom = 12.dp))
            val running = active
            if (running != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Séance en cours", style = MaterialTheme.typography.labelLarge)
                        Text(running.label(), style = MaterialTheme.typography.headlineSmall)
                        Button(onClick = onOpenSession, colors = accentButtonColors(), modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp)) {
                            Text("Reprendre")
                        }
                    }
                }
            } else {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Entraînement", style = MaterialTheme.typography.labelLarge)
                        weekProgress?.let { (done, goal) ->
                            Text(
                                "Cette semaine : $done/$goal séances" + if (done >= goal) " ✓" else "",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (done >= goal) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Button(
                            colors = accentButtonColors(),
                            onClick = { choosingSession = true },
                            modifier = Modifier.fillMaxWidth().height(64.dp).padding(top = 12.dp),
                        ) { Text("Nouvelle séance", style = MaterialTheme.typography.titleLarge) }
                    }
                }
            }

            deload?.let { advice ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Semaine de décharge conseillée", style = MaterialTheme.typography.titleMedium)
                        Text(advice.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        TextButton(onClick = viewModel::dismissDeload, modifier = Modifier.align(Alignment.End)) { Text("Compris") }
                    }
                }
            }

            SectionTitle("Nutrition du jour")
            Card(Modifier.fillMaxWidth().clickable(onClick = onOpenNutrition)) {
                Column(Modifier.padding(16.dp)) {
                    ProteinGauge(nutrition.totals, nutrition.proteinTargetG)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = nutrition.creatineTaken, onCheckedChange = journal::setCreatine)
                        Text("Créatine ${Fmt.number(nutrition.profile.creatineDoseG)} g prise")
                    }
                }
            }

            OutlinedButton(onClick = viewModel::analyse, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Analyse IA du jour")
            }

            SectionTitle("Poids")
            Card(Modifier.fillMaxWidth().clickable(onClick = onOpenWeight)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val w = weight
                        if (w == null) {
                            Text("Aucune pesée")
                        } else {
                            Text(Fmt.kg(w.lastKg), style = MaterialTheme.typography.headlineSmall)
                            Text("le ${w.lastDate.format(dayFormat)} · moyenne 7 j : ${Fmt.kg(w.average7Kg)}")
                        }
                    }
                    FilledTonalButton(onClick = { addingWeight = true }) { Text("+ pesée") }
                }
            }

            SectionTitle("Dernières séances")
            if (recent.isEmpty()) QuipText(Quip.EMPTY_SESSIONS, style = MaterialTheme.typography.bodyMedium)
            recent.forEach { session ->
                ListItem(
                    modifier = Modifier.clickable { onOpenSessionDetail(session.id) },
                    headlineContent = { Text(session.label()) },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                session.startedAt.atZone(viewModel.zone).format(dayFormat),
                                session.durationMin()?.let { "$it min" },
                            ).joinToString(" · "),
                        )
                    },
                )
            }
        }
    }

    if (choosingSession) {
        NewSessionSheet(
            proposed = proposed?.let { it.id to it.title() },
            others = templates.filter { it.id != proposed?.id }.map { it.id to it.title() },
            catchUp = catchUp,
            onStart = { id ->
                choosingSession = false
                viewModel.start(id, onOpenSession)
            },
            onDismiss = { choosingSession = false },
        )
    }

    if (addingWeight) {
        WeightEntryDialog(
            initialKg = weight?.lastKg ?: 75.0,
            today = viewModel.today(),
            onSave = { date, kg ->
                viewModel.saveWeight(date, kg)
                addingWeight = false
            },
            onDismiss = { addingWeight = false },
        )
    }
}

/**
 * "Nouvelle séance" sheet: the session proposed by the rotation first, then the other templates, then a free session.
 * Starting one closes the sheet and opens the session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewSessionSheet(
    proposed: Pair<Long, String>?,
    others: List<Pair<Long, String>>,
    catchUp: CatchUpUi?,
    onStart: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(NEW_SESSION_SHEET_TAG)) {
            item {
                Text("Nouvelle séance", style = MaterialTheme.typography.headlineSmall)
                QuipText(
                    Quip.START,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                )
            }
            if (proposed != null) {
                item {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Séance proposée · suite de la rotation", style = MaterialTheme.typography.labelLarge)
                            Text(proposed.second, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Button(
                                onClick = { onStart(proposed.first) },
                                colors = accentButtonColors(),
                                modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 12.dp),
                            ) { Text("Démarrer", style = MaterialTheme.typography.titleMedium) }
                        }
                    }
                }
            }
            if (catchUp != null) {
                item { CatchUpCard(catchUp, onStart = { onStart(catchUp.advice.suggested.templateId) }) }
            }
            if (others.isNotEmpty()) {
                item { SectionTitle("Autres séances") }
                items(others, key = { it.first }) { (id, title) ->
                    ListItem(
                        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = { Icon(Icons.Filled.PlayArrow, contentDescription = "Démarrer $title", tint = MaterialTheme.colorScheme.tertiary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onStart(id) },
                    )
                }
            }
            item {
                OutlinedButton(onClick = { onStart(null) }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp)) {
                    Text("Séance libre")
                }
            }
        }
    }
}

/** "Muscles en retard : Ischio-jambiers 4/10 · Mollets 0/10", then the template that catches up best and what it brings. */
@Composable
private fun CatchUpCard(catchUp: CatchUpUi, onStart: () -> Unit) {
    val advice = catchUp.advice
    val shown = advice.lagging.take(MAX_LAGGING_SHOWN)
    val more = advice.lagging.size - shown.size
    Card(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Muscles en retard : " + shown.joinToString(" · ") { "${LibraryLabels.muscle(it.muscle)} ${Fmt.number(it.done)}/${it.min}" } +
                    (if (more > 0) " · +$more autre${if (more > 1) "s" else ""}" else ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("Pour rattraper : ${catchUp.title}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(
                advice.lagging.mapNotNull { l -> advice.suggested.gains[l.muscle]?.let { "+${Fmt.number(it)} ${LibraryLabels.muscle(l.muscle).lowercase()}" } }
                    .joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
            )
            FilledTonalButton(onClick = onStart, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Démarrer ${catchUp.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private const val MAX_LAGGING_SHOWN = 5

internal const val NEW_SESSION_SHEET_TAG = "new-session-sheet"
