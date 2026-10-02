package fr.olivier.forme.feature.nutrition.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.feature.bodyweight.data.BodyWeightRepository
import fr.olivier.forme.feature.nutrition.data.NutritionRepository
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
import fr.olivier.forme.feature.nutrition.domain.NutritionMath
import fr.olivier.forme.feature.nutrition.reminder.CreatineReminder
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.SectionTitle
import fr.olivier.forme.ui.components.Stepper
import fr.olivier.forme.ui.components.parseDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/** Whey label fields kept as text while editing (decimal comma allowed). */
data class WheyText(val protein: String = "", val kcal: String = "", val carbs: String = "", val fat: String = "")

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: NutritionRepository,
    private val reminder: CreatineReminder,
    weights: BodyWeightRepository,
) : ViewModel() {
    private val _profile = MutableStateFlow(ProfileEntity())
    val profile = _profile.asStateFlow()
    private val _whey = MutableStateFlow(WheyText())
    val whey = _whey.asStateFlow()
    val lastWeight = weights.observeAll().map { list -> list.maxByOrNull { it.date }?.weightKg }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val p = repository.currentProfile()
            _profile.value = p
            fun t(v: Double?) = v?.let { Fmt.number(it, 2) }.orEmpty()
            _whey.value = WheyText(t(p.wheyProteinPer100g), t(p.wheyKcalPer100g), t(p.wheyCarbsPer100g), t(p.wheyFatPer100g))
        }
    }

    fun update(transform: (ProfileEntity) -> ProfileEntity) = _profile.update(transform)
    fun updateWhey(transform: (WheyText) -> WheyText) = _whey.update(transform)

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val w = _whey.value
        repository.saveProfile(
            _profile.value.copy(
                wheyProteinPer100g = parseDecimal(w.protein),
                wheyKcalPer100g = parseDecimal(w.kcal),
                wheyCarbsPer100g = parseDecimal(w.carbs),
                wheyFatPer100g = parseDecimal(w.fat),
            ),
        )
        reminder.reschedule()
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onDone: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val p by viewModel.profile.collectAsStateWithLifecycle()
    val whey by viewModel.whey.collectAsStateWithLifecycle()
    val weight by viewModel.lastWeight.collectAsStateWithLifecycle()
    var pickingTime by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    fun round1(v: Double) = (v * 10).roundToInt() / 10.0

    Scaffold(topBar = { FormeTopBar("Profil nutrition", onDone) { TextButton(onClick = { viewModel.save(onDone) }) { Text("Enregistrer") } } }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("Objectif protéines")
            Stepper(
                label = "g par kg de poids de corps",
                valueText = Fmt.number(p.proteinTargetGPerKg, 1),
                onMinus = { viewModel.update { it.copy(proteinTargetGPerKg = round1((it.proteinTargetGPerKg - 0.1).coerceAtLeast(0.8))) } },
                onPlus = { viewModel.update { it.copy(proteinTargetGPerKg = round1((it.proteinTargetGPerKg + 0.1).coerceAtMost(3.0))) } },
                onValueEntered = { t -> parseDecimal(t)?.let { v -> viewModel.update { it.copy(proteinTargetGPerKg = round1(v.coerceIn(0.8, 3.0))) } } },
                decimal = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                NutritionMath.proteinTarget(weight, p.proteinTargetGPerKg)
                    ?.let { "Soit ${it.roundToInt()} g par jour (dernier poids : ${Fmt.kg(weight!!)})" }
                    ?: "Saisis une pesée pour calculer l'objectif du jour.",
                style = MaterialTheme.typography.bodyMedium,
            )

            SectionTitle("Whey (valeurs de l'étiquette pour 100 g)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Protéines (g)", whey.protein, Modifier.weight(1f)) { v -> viewModel.updateWhey { it.copy(protein = v) } }
                NumberField("Énergie (kcal)", whey.kcal, Modifier.weight(1f)) { v -> viewModel.updateWhey { it.copy(kcal = v) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                NumberField("Glucides (g)", whey.carbs, Modifier.weight(1f)) { v -> viewModel.updateWhey { it.copy(carbs = v) } }
                NumberField("Lipides (g)", whey.fat, Modifier.weight(1f)) { v -> viewModel.updateWhey { it.copy(fat = v) } }
            }
            Stepper(
                label = "Dose (g de poudre)",
                valueText = Fmt.number(p.wheyScoopG, 0) + " g",
                onMinus = { viewModel.update { it.copy(wheyScoopG = (it.wheyScoopG - 5).coerceAtLeast(5.0)) } },
                onPlus = { viewModel.update { it.copy(wheyScoopG = it.wheyScoopG + 5) } },
                onValueEntered = { t -> parseDecimal(t)?.let { v -> viewModel.update { it.copy(wheyScoopG = v.coerceAtLeast(1.0)) } } },
                decimal = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            parseDecimal(whey.protein)?.let {
                Text("Une dose = ${Fmt.number(it * p.wheyScoopG / 100)} g de protéines", style = MaterialTheme.typography.bodyMedium)
            }

            SectionTitle("Créatine")
            Stepper(
                label = "Dose quotidienne",
                valueText = Fmt.number(p.creatineDoseG) + " g",
                onMinus = { viewModel.update { it.copy(creatineDoseG = (it.creatineDoseG - 0.5).coerceAtLeast(0.5)) } },
                onPlus = { viewModel.update { it.copy(creatineDoseG = it.creatineDoseG + 0.5) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Rappel quotidien", Modifier.weight(1f))
                Switch(
                    checked = p.creatineReminderEnabled,
                    onCheckedChange = { on ->
                        viewModel.update { it.copy(creatineReminderEnabled = on) }
                        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }
            if (p.creatineReminderEnabled) {
                OutlinedButton(onClick = { pickingTime = true }) {
                    Text("Heure du rappel : %02d:%02d".format(p.creatineReminderMinutes / 60, p.creatineReminderMinutes % 60))
                }
                Text("Pas de rappel si la prise du jour est déjà notée.", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(24.dp)) // Saving is done with "Enregistrer" in the top bar.
        }
    }

    if (pickingTime) {
        val state = rememberTimePickerState(p.creatineReminderMinutes / 60, p.creatineReminderMinutes % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.update { it.copy(creatineReminderMinutes = state.hour * 60 + state.minute) }
                    pickingTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text("Annuler") } },
        )
    }
}

@Composable
fun NumberField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == ',' || it == '.' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
