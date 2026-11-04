package fr.olivier.forme.feature.nutrition.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.core.settings.TrainingGoals
import fr.olivier.forme.feature.bodyweight.data.BodyWeightRepository
import fr.olivier.forme.feature.settings.ui.TrainingGoalsSection
import fr.olivier.forme.feature.heart.domain.HeartZones
import fr.olivier.forme.feature.heart.domain.Sex
import fr.olivier.forme.feature.nutrition.data.NutritionRepository
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
import fr.olivier.forme.feature.nutrition.domain.EnergyMath
import fr.olivier.forme.feature.nutrition.domain.NutritionGoal
import fr.olivier.forme.feature.nutrition.domain.NutritionMath
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.SectionTitle
import fr.olivier.forme.ui.components.Stepper
import fr.olivier.forme.ui.components.parseDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

/** Whey label fields kept as text while editing (decimal comma allowed). */
data class WheyText(val protein: String = "", val kcal: String = "", val carbs: String = "", val fat: String = "")

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: NutritionRepository,
    private val settings: SettingsRepository,
    weights: BodyWeightRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _profile = MutableStateFlow(ProfileEntity())
    val profile = _profile.asStateFlow()
    private val _whey = MutableStateFlow(WheyText())
    val whey = _whey.asStateFlow()
    private val _birthYear = MutableStateFlow("")
    val birthYear = _birthYear.asStateFlow()
    private val _height = MutableStateFlow("")
    val height = _height.asStateFlow()
    val currentYear: Int = LocalDate.now(clock).year
    val lastWeight = weights.observeAll().map { list -> list.maxByOrNull { it.date }?.weightKg }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The form is shown once the stored profile is read: filled in before, it was overwritten by the late read (a
     * birth year typed on the first launch vanished), and "Enregistrer" could store an empty profile over it.
     */
    /** Training goals (moved here from Réglages in 2.12.1), saved with the profile. */
    private val _goals = MutableStateFlow(TrainingGoals())
    val goals = _goals.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded = _loaded.asStateFlow()

    init {
        viewModelScope.launch {
            val p = repository.currentProfile()
            _profile.value = p
            fun t(v: Double?) = v?.let { Fmt.number(it, 2) }.orEmpty()
            _whey.value = WheyText(t(p.wheyProteinPer100g), t(p.wheyKcalPer100g), t(p.wheyCarbsPer100g), t(p.wheyFatPer100g))
            _birthYear.value = p.birthYear?.toString().orEmpty()
            _height.value = p.heightCm?.toString().orEmpty()
            _goals.value = settings.trainingGoals.first()
            _loaded.value = true
        }
    }

    fun update(transform: (ProfileEntity) -> ProfileEntity) = _profile.update(transform)
    fun updateGoals(goals: TrainingGoals) {
        _goals.value = goals.coerced()
    }

    fun updateWhey(transform: (WheyText) -> WheyText) = _whey.update(transform)
    fun updateBirthYear(text: String) {
        _birthYear.value = text.filter(Char::isDigit).take(4)
    }

    fun updateHeight(text: String) {
        _height.value = text.filter(Char::isDigit).take(3)
    }

    /** A plausible height in cm, else null. */
    fun parsedHeight(): Int? = _height.value.toIntOrNull()?.takeIf { it in 120..230 }

    /** Changing the goal drops a personalised calorie target, which was set for the previous goal. */
    fun setGoal(goal: NutritionGoal?) = update { it.copy(nutritionGoal = goal, kcalTargetOverride = null) }

    /** A plausible birth year (10 to 100 years old), else null. */
    fun parsedBirthYear(): Int? = _birthYear.value.toIntOrNull()?.takeIf { it in currentYear - 100..currentYear - 10 }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        if (!_loaded.value) return@launch
        val w = _whey.value
        repository.saveProfile(
            _profile.value.copy(
                wheyProteinPer100g = parseDecimal(w.protein),
                wheyKcalPer100g = parseDecimal(w.kcal),
                wheyCarbsPer100g = parseDecimal(w.carbs),
                wheyFatPer100g = parseDecimal(w.fat),
                firstName = _profile.value.firstName?.trim()?.takeIf { it.isNotEmpty() },
                birthYear = parsedBirthYear(),
                heightCm = parsedHeight(),
            ),
        )
        settings.setTrainingGoals(_goals.value)
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onDone: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val p by viewModel.profile.collectAsStateWithLifecycle()
    val whey by viewModel.whey.collectAsStateWithLifecycle()
    val weight by viewModel.lastWeight.collectAsStateWithLifecycle()
    val birthYear by viewModel.birthYear.collectAsStateWithLifecycle()
    val height by viewModel.height.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    fun round1(v: Double) = (v * 10).roundToInt() / 10.0

    Scaffold(topBar = { FormeTopBar("Profil", onDone) { TextButton(onClick = { viewModel.save(onDone) }, enabled = loaded) { Text("Enregistrer") } } }) { padding ->
        if (!loaded) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // V2.22: "Salut {prénom}" on the home screen.
            OutlinedTextField(
                value = p.firstName.orEmpty(),
                onValueChange = { v -> viewModel.update { it.copy(firstName = v.take(30)) } },
                label = { Text("Prénom (accueil)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            SectionTitle("Toi (fréquence cardiaque)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = birthYear,
                    onValueChange = viewModel::updateBirthYear,
                    label = { Text("Année de naissance") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Sex.entries.forEach { sex ->
                    FilterChip(
                        selected = p.sex == sex,
                        onClick = { viewModel.update { it.copy(sex = if (it.sex == sex) null else sex) } },
                        label = { Text(sex.label) },
                    )
                }
            }
            val age = viewModel.parsedBirthYear()?.let { HeartZones.age(it, viewModel.currentYear) }
            val advised = age?.let(HeartZones::hrMaxFromAge)
            val hrMax = p.hrMaxOverride ?: advised
            Stepper(
                label = "FC max (bpm)",
                valueText = hrMax?.toString() ?: "—",
                onMinus = { (hrMax ?: 180).let { v -> viewModel.update { it.copy(hrMaxOverride = (v - 1).coerceIn(120, 230)) } } },
                onPlus = { (hrMax ?: 180).let { v -> viewModel.update { it.copy(hrMaxOverride = (v + 1).coerceIn(120, 230)) } } },
                onValueEntered = { t -> t.toIntOrNull()?.let { v -> viewModel.update { it.copy(hrMaxOverride = v.coerceIn(120, 230)) } } },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                when {
                    advised == null -> "Saisis ton année de naissance pour obtenir la FC max conseillée."
                    p.hrMaxOverride == null -> "$age ans : valeur conseillée (208 − 0,7 × âge)."
                    else -> "Valeur personnalisée · conseillée pour $age ans : $advised bpm."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (p.hrMaxOverride != null && advised != null) {
                TextButton(onClick = { viewModel.update { it.copy(hrMaxOverride = null) } }) { Text("Revenir à la valeur conseillée") }
            }
            Text(
                "Âge, sexe et poids servent aux zones cardio et à l'estimation des calories de séance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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

            SectionTitle("Objectif calories")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NutritionGoal.entries.forEach { goal ->
                    FilterChip(
                        selected = p.nutritionGoal == goal,
                        onClick = { viewModel.setGoal(if (p.nutritionGoal == goal) null else goal) },
                        label = { Text(goal.label) },
                    )
                }
            }
            val goal = p.nutritionGoal
            if (goal == null) {
                Text("Choisis un objectif pour suivre tes calories.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(goal.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = height,
                    onValueChange = viewModel::updateHeight,
                    label = { Text("Taille (cm)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                val advisedKcal = EnergyMath.advisedBase(weight, viewModel.parsedHeight(), age, p.sex, goal)
                val kcal = p.kcalTargetOverride ?: advisedKcal
                Stepper(
                    label = "Jour de repos (kcal)",
                    valueText = kcal?.let { Fmt.number(it.toDouble(), 0) } ?: "—",
                    onMinus = { (kcal ?: 2000).let { v -> viewModel.update { it.copy(kcalTargetOverride = (v - 50).coerceIn(1000, 6000)) } } },
                    onPlus = { (kcal ?: 2000).let { v -> viewModel.update { it.copy(kcalTargetOverride = (v + 50).coerceIn(1000, 6000)) } } },
                    onValueEntered = { t -> t.toIntOrNull()?.let { v -> viewModel.update { it.copy(kcalTargetOverride = v.coerceIn(1000, 6000)) } } },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    when {
                        advisedKcal == null -> "Renseigne année de naissance, sexe, taille et une pesée pour la valeur conseillée."
                        p.kcalTargetOverride == null -> "Valeur conseillée (Mifflin-St Jeor × 1,4, ${goal.label.lowercase()})."
                        else -> "Valeur personnalisée · conseillée : ${Fmt.number(advisedKcal.toDouble(), 0)} kcal."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (p.kcalTargetOverride != null && advisedKcal != null) {
                    TextButton(onClick = { viewModel.update { it.copy(kcalTargetOverride = null) } }) { Text("Revenir à la valeur conseillée") }
                }
                Text(
                    "Les jours d'entraînement, la dépense mesurée des séances s'ajoute à cet objectif.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TrainingGoalsSection(goals, viewModel::updateGoals)

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

            Spacer(Modifier.height(24.dp)) // Saving is done with "Enregistrer" in the top bar.
        }
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
