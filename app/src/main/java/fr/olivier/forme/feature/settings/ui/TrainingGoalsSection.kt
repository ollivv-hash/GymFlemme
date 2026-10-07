package fr.olivier.forme.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.core.settings.TrainingGoals
import fr.olivier.forme.ui.components.SectionTitle
import fr.olivier.forme.ui.components.Stepper

/** Sessions per week and the weekly sets bands (groups, muscles); shown in Config → Profil since 2.12.1. */
@Composable
fun TrainingGoalsSection(goals: TrainingGoals, onChange: (TrainingGoals) -> Unit) {
    SectionTitle("Objectifs d'entraînement")
    Stepper(
        label = "Séances par semaine",
        valueText = "${goals.sessionsPerWeek}",
        onMinus = { onChange(goals.copy(sessionsPerWeek = goals.sessionsPerWeek - 1)) },
        onPlus = { onChange(goals.copy(sessionsPerWeek = goals.sessionsPerWeek + 1)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Stepper(
            label = "Séries / groupe : min",
            valueText = "${goals.minSets}",
            onMinus = { onChange(goals.copy(minSets = goals.minSets - 1)) },
            onPlus = { onChange(goals.copy(minSets = goals.minSets + 1, maxSets = maxOf(goals.maxSets, goals.minSets + 1))) },
            modifier = Modifier.weight(1f),
        )
        Stepper(
            label = "max",
            valueText = "${goals.maxSets}",
            onMinus = { onChange(goals.copy(maxSets = maxOf(goals.minSets, goals.maxSets - 1))) },
            onPlus = { onChange(goals.copy(maxSets = goals.maxSets + 1)) },
            modifier = Modifier.weight(1f),
        )
    }
    // V2.12: band of the "Muscles" view (large muscles of the library), set apart from the groups' one.
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Stepper(
            label = "Séries / muscle : min",
            valueText = "${goals.muscleMinSets}",
            onMinus = { onChange(goals.copy(muscleMinSets = goals.muscleMinSets - 1)) },
            onPlus = {
            onChange(
                goals.copy(muscleMinSets = goals.muscleMinSets + 1, muscleMaxSets = maxOf(goals.muscleMaxSets, goals.muscleMinSets + 1)),
            )
            },
            modifier = Modifier.weight(1f),
        )
        Stepper(
            label = "max",
            valueText = "${goals.muscleMaxSets}",
            onMinus = { onChange(goals.copy(muscleMaxSets = maxOf(goals.muscleMinSets, goals.muscleMaxSets - 1))) },
            onPlus = { onChange(goals.copy(muscleMaxSets = goals.muscleMaxSets + 1)) },
            modifier = Modifier.weight(1f),
        )
    }
    if (goals.muscleMinSets != SettingsRepository.DEFAULT_MIN_SETS || goals.muscleMaxSets != SettingsRepository.DEFAULT_MAX_SETS) {
        TextButton(
            onClick = {
            onChange(goals.copy(muscleMinSets = SettingsRepository.DEFAULT_MIN_SETS, muscleMaxSets = SettingsRepository.DEFAULT_MAX_SETS))
            },
        ) { Text("Revenir à ${SettingsRepository.DEFAULT_MIN_SETS}–${SettingsRepository.DEFAULT_MAX_SETS} par muscle") }
    }
}
