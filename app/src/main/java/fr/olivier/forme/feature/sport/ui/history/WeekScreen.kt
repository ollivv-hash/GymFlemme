package fr.olivier.forme.feature.sport.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.core.settings.TrainingGoals
import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.feature.sport.domain.MuscleGroup
import fr.olivier.forme.feature.sport.domain.TrainedSet
import fr.olivier.forme.feature.sport.domain.WeekStats
import fr.olivier.forme.feature.sport.domain.WeeklyStats
import fr.olivier.forme.ui.components.SectionTitle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import fr.olivier.forme.feature.heart.data.HeartRepository
import fr.olivier.forme.feature.bodyweight.data.BodyWeightRepository
import fr.olivier.forme.feature.nutrition.data.NutritionRepository
import fr.olivier.forme.feature.nutrition.data.bmrPerDay
import fr.olivier.forme.feature.nutrition.data.netKcal
import fr.olivier.forme.feature.nutrition.data.restDayKcal
import fr.olivier.forme.feature.nutrition.domain.DayIntake
import fr.olivier.forme.feature.nutrition.domain.EnergyMath
import fr.olivier.forme.feature.nutrition.domain.NutritionMath
import fr.olivier.forme.feature.nutrition.domain.NutritionWeek
import fr.olivier.forme.feature.heart.domain.HeartWeek
import fr.olivier.forme.feature.heart.domain.SessionHeartLine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt
import javax.inject.Inject

data class WeekUi(
    val stats: WeekStats,
    val goals: TrainingGoals = TrainingGoals(),
    val streak: Int = 0,
    val isCurrentWeek: Boolean = true,
    /** Days with a finished session over the last [CALENDAR_WEEKS] weeks. */
    val trainedDays: Set<LocalDate> = emptySet(),
    val calendarStart: LocalDate,
    val today: LocalDate,
    /** Heart rate of the shown week's sessions (V2.1); sessions = 0 when none was measured. */
    val heart: HeartWeek = HeartWeek.of(emptyList()),
    /** Nutrition of the shown week (V2.2); null until loaded. */
    val nutrition: NutritionWeek? = null,
)

const val CALENDAR_WEEKS = 12

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeekViewModel @Inject constructor(
    sessions: SessionRepository,
    settings: SettingsRepository,
    heartRepository: HeartRepository,
    nutrition: NutritionRepository,
    weights: BodyWeightRepository,
    private val clock: Clock,
) : ViewModel() {
    private val today = LocalDate.now(clock)
    private val currentWeek = WeeklyStats.weekStart(today)
    private val shownWeek = MutableStateFlow(currentWeek)
    /** Enough history for the calendar and the streak (one year). */
    private val since = currentWeek.minusWeeks(52)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val heartWeek = shownWeek.flatMapLatest { week ->
        heartRepository.observeRange(week, week.plusDays(7)).map { list ->
            HeartWeek.of(
                list.map { h ->
                    val series = h.series
                    SessionHeartLine(h.avgBpm, series.bpm.count { it > 0 } * series.intervalSec, h.zoneSeconds, h.kcal)
                },
            )
        }
    }

    private val lastWeight = weights.observeAll().map { list -> list.maxByOrNull { it.date }?.weightKg }

    /** Protein target from the current weight; calorie target of each day = rest-day target + its sessions. */
    private val nutritionWeek = shownWeek.flatMapLatest { week ->
        combine(
            nutrition.observeRange(week, week.plusDays(6)),
            nutrition.profile,
            lastWeight,
            nutrition.creatineDates(),
            heartRepository.observeRange(week, week.plusDays(7)),
        ) { entries, profile, weight, creatine, hearts ->
            val intakes = entries.groupBy { it.date }.map { (date, day) ->
                DayIntake(date, day.sumOf { it.proteinG }, day.sumOf { it.kcal ?: 0.0 }, day.any { it.isWhey })
            }
            val bmr = profile.bmrPerDay(weight, week.year)
            val sessionKcal = hearts.groupBy { it.seriesStart.atZone(clock.zone).toLocalDate() }
                .mapValues { (_, list) -> list.netKcal(bmr) ?: 0.0 }
            val base = profile.restDayKcal(weight, week.year)
            NutritionWeek.of(
                weekStart = week,
                today = today,
                intakes = intakes,
                creatineDates = creatine,
                proteinTargetG = NutritionMath.proteinTarget(weight, profile.proteinTargetGPerKg),
                kcalTargetOf = base?.let { b -> { d: LocalDate -> EnergyMath.target(b, sessionKcal[d])!!.totalKcal } },
            )
        }
    }

    private val extras = combine(heartWeek, nutritionWeek) { heart, food -> heart to food }

    val ui = combine(
        shownWeek,
        sessions.observeWorkingSetsSince(since.atStartOfDay(clock.zone).toInstant()),
        sessions.observeFinished(),
        settings.trainingGoals,
        extras,
    ) { week, rows, finished, goals, (heart, food) ->
        val zone = clock.zone
        val sets = rows.map { TrainedSet(it.startedAt.atZone(zone).toLocalDate(), it.muscleGroup, it.type) }
        val sessionDates = finished.map { it.startedAt.atZone(zone).toLocalDate() }
        val calendarStart = currentWeek.minusWeeks((CALENDAR_WEEKS - 1).toLong())
        WeekUi(
            stats = WeeklyStats.week(sets, sessionDates, week),
            goals = goals,
            streak = WeeklyStats.streak(sessionDates, goals.sessionsPerWeek, today),
            isCurrentWeek = week == currentWeek,
            trainedDays = sessionDates.filter { it >= calendarStart }.toSet(),
            calendarStart = calendarStart,
            today = today,
            heart = heart,
            nutrition = food,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        WeekUi(WeekStats(currentWeek, emptyMap(), 0), calendarStart = currentWeek, today = today),
    )

    fun shift(weeks: Long) = shownWeek.update { (it.plusWeeks(weeks)).coerceAtMost(currentWeek) }
}

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Fmt.locale)

/** Groups shown in the volume chart (cardio is tracked by duration, not sets). */
private val volumeGroups = MuscleGroup.entries.filter { it != MuscleGroup.CARDIO }

@Composable
fun WeekContent(viewModel: WeekViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val week = ui.stats
    LazyColumn(Modifier.padding(horizontal = 16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { viewModel.shift(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Semaine précédente") }
                Text(
                    if (ui.isCurrentWeek) "Cette semaine" else
                        "Semaine du ${week.weekStart.format(dayMonth)} au ${week.weekStart.plusDays(6).format(dayMonth)}",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.shift(1) }, enabled = !ui.isCurrentWeek) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Semaine suivante")
                }
            }
        }
        item {
            val reached = week.sessions >= ui.goals.sessionsPerWeek
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${week.sessions}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (reached) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(" / ${ui.goals.sessionsPerWeek} séances", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Text(
                        when {
                            ui.streak >= 2 -> "🔥 Objectif tenu ${ui.streak} semaines d'affilée"
                            ui.streak == 1 -> "Objectif tenu la semaine dernière"
                            else -> "Objectif : ${ui.goals.sessionsPerWeek} séances par semaine"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (ui.heart.sessions > 0) {
            item { HeartWeekCard(ui.heart) }
        }
        ui.nutrition?.takeIf { it.loggedDays > 0 }?.let { food -> item { NutritionWeekCard(food) } }
        item {
            SectionTitle("Séries par groupe musculaire")
            Text(
                "Zone repère : ${ui.goals.minSets} à ${ui.goals.maxSets} séries par semaine (hors échauffement, groupe principal).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        volumeGroups.forEach { group ->
            val count = week.setsByGroup[group] ?: 0
            if (group == MuscleGroup.AUTRE && count == 0) return@forEach
            item(key = group.name) { VolumeBar(group.label, count, ui.goals) }
        }
        item {
            SectionTitle("Régularité (12 semaines)")
            TrainingCalendar(ui)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** One muscle group: count, and a bar with the recommended band drawn behind it. */
@Composable
private fun VolumeBar(label: String, count: Int, goals: TrainingGoals) {
    val scaleMax = maxOf(goals.maxSets + 5, count).toFloat()
    val band = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val fill = when {
        count < goals.minSets -> MaterialTheme.colorScheme.onSurfaceVariant
        count <= goals.maxSets -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        Text(label, modifier = Modifier.width(92.dp), style = MaterialTheme.typography.bodyMedium)
        Canvas(Modifier.weight(1f).height(18.dp)) {
            val r = CornerRadius(6.dp.toPx())
            drawRoundRect(track, size = size, cornerRadius = r)
            val x0 = size.width * goals.minSets / scaleMax
            val x1 = size.width * goals.maxSets / scaleMax
            drawRect(band, topLeft = Offset(x0, 0f), size = Size(x1 - x0, size.height))
            if (count > 0) drawRoundRect(fill, size = Size(size.width * count / scaleMax, size.height), cornerRadius = r)
        }
        Text(
            "$count",
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.titleMedium,
            color = fill,
        )
    }
}

/** 7 rows (Mon → Sun) × 12 columns (weeks); filled when a session was done that day. */
@Composable
private fun TrainingCalendar(ui: WeekUi) {
    val on = MaterialTheme.colorScheme.tertiary
    val off = MaterialTheme.colorScheme.surfaceContainerHigh
    val todayRing = MaterialTheme.colorScheme.primary
    Row {
        Column(Modifier.padding(end = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
            listOf("L", "", "M", "", "V", "", "D").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.height(20.dp))
            }
        }
        Canvas(Modifier.weight(1f).height(140.dp)) {
            val cols = CALENDAR_WEEKS
            val gap = 4.dp.toPx()
            val cell = minOf((size.width - gap * (cols - 1)) / cols, (size.height - gap * 6) / 7)
            for (c in 0 until cols) for (r in 0 until 7) {
                val day = ui.calendarStart.plusWeeks(c.toLong()).plusDays(r.toLong())
                if (day > ui.today) continue
                val topLeft = Offset(c * (cell + gap), r * (cell + gap))
                drawRoundRect(if (day in ui.trainedDays) on else off, topLeft, Size(cell, cell), CornerRadius(3.dp.toPx()))
                if (day == ui.today) {
                    drawRoundRect(todayRing, topLeft, Size(cell, cell), CornerRadius(3.dp.toPx()), style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
    Text(
        "${ui.trainedDays.size} séances sur 12 semaines",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** Heart rate of the week: measured sessions, average, time at working intensity, calories. */
@Composable
private fun HeartWeekCard(heart: HeartWeek) {
    SectionTitle("Fréquence cardiaque")
    Text(
        listOfNotNull(
            "${heart.sessions} séance${if (heart.sessions > 1) "s" else ""} mesurée${if (heart.sessions > 1) "s" else ""}",
            heart.avgBpm?.let { "FC moyenne $it bpm" },
        ).joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        listOfNotNull(
            "zone 3 et + : ${(heart.highZoneSeconds / 60.0).roundToInt()} min",
            heart.kcal.takeIf { it > 0 }?.let { "${it.roundToInt()} kcal dépensées" },
        ).joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

/** Nutrition of the week: averages over logged days, days on target, supplements. */
@Composable
private fun NutritionWeekCard(food: NutritionWeek) {
    SectionTitle("Nutrition")
    Text(
        "${food.loggedDays} jour${if (food.loggedDays > 1) "s" else ""} saisi${if (food.loggedDays > 1) "s" else ""} sur ${food.elapsedDays}",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        listOfNotNull(
            food.avgProteinG?.let { "protéines ${it.roundToInt()} g/j" },
            food.proteinTargetDays?.let { "cible atteinte $it/${food.loggedDays}" },
        ).joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        listOfNotNull(
            food.avgKcal?.let { "${Fmt.number(it, 0)} kcal/j" },
            food.kcalTargetDays?.let { "dans l'objectif (±10 %) $it/${food.loggedDays}" },
        ).joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        "Whey ${food.wheyDays}/${food.elapsedDays} · créatine ${food.creatineDays}/${food.elapsedDays}",
        style = MaterialTheme.typography.bodyMedium,
    )
}
