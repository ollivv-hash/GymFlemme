package fr.olivier.forme.feature.sport.ui.home

import fr.olivier.forme.core.update.LocalUpdateFeature
import fr.olivier.forme.R
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import fr.olivier.forme.feature.hydration.data.IntakeSource
import fr.olivier.forme.feature.hydration.domain.HydrationMath
import fr.olivier.forme.feature.hydration.ui.AddDrinkSheet
import fr.olivier.forme.feature.hydration.ui.WaterSnackbarEffect
import fr.olivier.forme.feature.hydration.ui.WaterIntroCard
import fr.olivier.forme.feature.hydration.ui.WaterViewModel
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import fr.olivier.forme.ui.theme.BentoDims
import fr.olivier.forme.ui.theme.LocalBento
import fr.olivier.forme.feature.nutrition.domain.EnergyMath
import fr.olivier.forme.core.humor.rememberQuip
import fr.olivier.forme.core.humor.rememberQuipStyle
import fr.olivier.forme.feature.badges.domain.Challenge
import fr.olivier.forme.feature.badges.ui.ChallengeTexts
import fr.olivier.forme.feature.badges.ui.ChallengeWording
import fr.olivier.forme.feature.badges.ui.BadgeWording
import fr.olivier.forme.feature.badges.ui.LevelViewModel
import fr.olivier.forme.feature.sport.ui.home.bento.*
import fr.olivier.forme.feature.badges.ui.LevelCard
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
import fr.olivier.forme.feature.nutrition.data.dose
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
import fr.olivier.forme.feature.sport.domain.SessionDurations
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
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
/** The session tile's line: "5 exercices · ~40 min" (V2.22). */
data class ProposedInfo(val exercises: Int, val typicalMinutes: Int?)

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

    /** V2.22: number of exercises and typical length of the proposed session (session tile). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val proposedInfo = proposed.flatMapLatest { t ->
        if (t == null) {
            flowOf(null)
        } else {
            combine(templates.observeLines(t.id), sessions.observeFinished()) { lines, finished ->
                val minutes = finished.filter { it.templateId == t.id }.sortedByDescending { it.startedAt }
                    .mapNotNull { s -> s.endedAt?.let { java.time.Duration.between(s.startedAt, it).toMinutes() } }
                ProposedInfo(lines.size, SessionDurations.typicalMinutes(minutes))
            }
        }
    }.state(null)
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

    init {
        // Badges of the history (shown once in "Tes badges déjà gagnés" after the update, silently after a restore),
        // and those earned outside the app (creatine taken from the notification): announced by BadgeAnnouncer.
        viewModelScope.launch {
            runCatching { badges.evaluate() }.onFailure { android.util.Log.e("Forme", "Badge evaluation failed", it) }
        }
    }

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

/**
 * Home (V2.22, "bento"): the greeting, then a grid of tiles: next session, calories and proteins, challenge of the
 * month and supplements, level. The grid scrolls under the floating navigation bar.
 */
@Composable
fun HomeScreen(
    onOpenSession: () -> Unit,
    onOpenNutrition: () -> Unit,
    onOpenBadges: (tab: Int) -> Unit = {},
    onOpenUpdate: () -> Unit = {},
    onOpenWater: () -> Unit = {},
    onNeedsFood: (drinkId: Long) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    journal: JournalViewModel = hiltViewModel(),
    level: LevelViewModel = hiltViewModel(),
    water: WaterViewModel = hiltViewModel(),
) {
    val nutrition by journal.ui.collectAsStateWithLifecycle()
    val sessionKcal by journal.sessionKcal.collectAsStateWithLifecycle()
    val proposed by viewModel.proposed.collectAsStateWithLifecycle()
    val proposedInfo by viewModel.proposedInfo.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()
    val weekProgress by viewModel.weekProgress.collectAsStateWithLifecycle()
    val deload by viewModel.deload.collectAsStateWithLifecycle()
    val catchUp by viewModel.catchUp.collectAsStateWithLifecycle()
    val nudge by viewModel.nudge.collectAsStateWithLifecycle()
    val board by level.board.collectAsStateWithLifecycle()
    var choosingSession by remember { mutableStateOf(false) }
    val waterToday by water.today.collectAsStateWithLifecycle()
    val waterSettings by water.hydrationSettings.collectAsStateWithLifecycle()
    val drinks by water.drinks.collectAsStateWithLifecycle()
    val containers by water.containers.collectAsStateWithLifecycle()
    var choosingDrink by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    WaterSnackbarEffect(water.events, snackbar, water::undo, onNeedsFood, water::resumePending)
    val c = LocalBento.current
    val resources = LocalContext.current.resources
    val quipStyle = rememberQuipStyle()

    val running = active
    val sessionState = SessionTileState(
        running = running != null,
        title = running?.label() ?: proposed?.title(),
        week = weekProgress,
        exercises = proposedInfo?.exercises,
        typicalMinutes = proposedInfo?.typicalMinutes,
    )
    val hasMeals = nutrition.entries.isNotEmpty()
    val calories = CaloriesTileState(
        kcal = nutrition.totals.kcal.roundToInt().takeIf { hasMeals },
        target = EnergyMath.target(nutrition.restDayKcal, sessionKcal)?.totalKcal,
    )
    val proteins = ProteinTileState(
        grams = nutrition.totals.proteinG.roundToInt().takeIf { hasMeals },
        target = nutrition.proteinTargetG?.roundToInt(),
    )
    val challenge = board?.current?.let { (month, ch, progress) ->
        val rower = ch == Challenge.ROWER_OF_THE_MONTH
        ChallengeTileState(
            month = ChallengeWording.ofMonth(resources, month),
            name = ChallengeWording.name(resources, ch),
            value = progress.value,
            goal = progress.goal,
            valueText = measure(rower, progress.value),
            goalText = measure(rower, progress.goal),
            unit = stringResource(ChallengeTexts.of(ch).unit),
            done = progress.completedOn != null,
        )
    }
    val supplements = nutrition.supplements.takeIf { it.isNotEmpty() }?.let { days ->
        val onlyCreatine = days.size == 1 && days.first().supplement.isCreatine
        val creatineOn = days.any { it.supplement.isCreatine }
        val streak = nutrition.creatineStreak
        SupplementsTileState(
            title = if (onlyCreatine) "Créatine" else "Compléments",
            streak = when {
                !creatineOn -> null
                streak >= 2 -> "$streak jours d'affilée"
                streak == 1 -> "1 jour"
                else -> "Série à lancer"
            },
            items = days.take(MAX_SUPPLEMENTS).map { (s, taken) ->
                SupplementItem(s.id, if (onlyCreatine) "${s.dose()} pris" else "${s.name} ${s.dose()}", taken)
            },
            more = (days.size - MAX_SUPPLEMENTS).coerceAtLeast(0),
        )
    }

    val waterTile = waterToday?.let { today ->
        val default = containers.firstOrNull { it.isDefault } ?: containers.firstOrNull()
        val args = arrayOf<Any>(HydrationMath.liters(today.countedMl), HydrationMath.liters(today.goal.totalMl), HydrationMath.liters(today.remainingMl))
        WaterTileState(
            countedMl = today.countedMl,
            goalMl = today.goal.totalMl,
            bonusMl = today.goal.bonusMl,
            line = rememberQuip(waterTileQuip(today.countedMl, today.goal.totalMl), *args),
            defaultMl = default?.volumeMl,
            defaultDrink = drinks.firstOrNull { it.id == default?.defaultDrinkId }?.name,
        )
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = BentoDims.screenMargin, end = BentoDims.screenMargin, top = 26.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(BentoDims.gap),
        ) {
            // V2.15: greeting of the time slot, drawn when the home screen opens; V2.16: after a break, a welcome
            // back replaces it, and a missed goal last week adds a line.
            val greeting = rememberQuip(if (nudge.welcomeBack) Quip.WELCOME_BACK else remember { Quip.greeting(viewModel.now()) })
            HomeHeader(
                hello = nutrition.profile.firstName?.let { "Salut $it" },
                greeting = greeting,
                missedGoal = nudge.missedGoalNext?.let { rememberQuip(Quip.MISSED_GOAL, it) },
                onAnalyse = viewModel::analyse,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            SessionTile(
                state = sessionState,
                startLabel = rememberQuip(Quip.START_BUTTON) ?: stringResource(R.string.neutral_start_button),
                onStart = {
                    when {
                        running != null -> onOpenSession()
                        proposed != null -> viewModel.start(proposed!!.id, onOpenSession)
                        else -> choosingSession = true
                    }
                },
                onChoose = { choosingSession = true },
            )
            // V2.28 ("github" variant): a new version of the app.
            LocalUpdateFeature.current?.HomeCard(onOpenUpdate, Modifier)
            deload?.let { DeloadTile(it.message, onDismiss = viewModel::dismissDeload) }
            TileRow(
                left = { m -> CaloriesTile(calories, onOpenNutrition, m) },
                right = { m -> ProteinTile(proteins, onOpenNutrition, m) },
            )
            // V2.29: the half-width tiles go by pairs in this order (water, supplements, challenge); one left alone
            // takes the whole width.
            // V2.29: the first use of the hydration, until answered.
            val intro = waterSettings?.takeIf { !it.introSeen }
            val introToday = waterToday
            if (intro != null && introToday != null) WaterIntroCard(introToday, intro, onStart = water::introStart, onLater = water::introLater)
            HalfTiles(
                waterTile?.let { state -> { m: Modifier -> WaterTile(state, onOpen = onOpenWater, onAdd = water::addDefault, onChoose = { choosingDrink = true }, modifier = m) } },
                supplements?.let { state ->
                    { m: Modifier ->
                        SupplementsTile(
                            state,
                            onToggle = { item, taken -> nutrition.supplements.firstOrNull { it.supplement.id == item.id }?.let { journal.setSupplement(it.supplement, taken) } },
                            onMore = onOpenNutrition,
                            modifier = m,
                        )
                    }
                },
                challenge?.let { state -> { m: Modifier -> ChallengeTile(state, { onOpenBadges(2) }, m) } },
            )

            board?.level?.let { lv ->
                LevelTile(
                    LevelTileState(lv.number, BadgeWording.levelDisplay(resources, lv.number, quipStyle), lv.points, lv.floor, lv.next),
                    onOpen = { onOpenBadges(0) },
                )
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (choosingDrink) {
        AddDrinkSheet(
            drinks = drinks,
            containers = containers,
            onAdd = { id, ml -> water.add(id, ml, IntakeSource.HOME_TILE) },
            onDismiss = { choosingDrink = false },
        )
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
}

/** Secondary line of the water tile (V2.29): nothing yet, under half, over half, goal reached. */
internal fun waterTileQuip(countedMl: Int, goalMl: Int): Quip = when {
    countedMl <= 0 -> Quip.WATER_TILE_EMPTY
    countedMl >= goalMl -> Quip.WATER_TILE_REACHED
    countedMl * 2 < goalMl -> Quip.WATER_TILE_LOW
    else -> Quip.WATER_TILE_HIGH
}

/** Supplement boxes shown on the home tile; the others are in Nutrition. */
private const val MAX_SUPPLEMENTS = 3

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
