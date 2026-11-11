package fr.olivier.forme.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.R
import fr.olivier.forme.feature.heart.domain.Sex
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
import fr.olivier.forme.feature.nutrition.data.SupplementDefaults
import fr.olivier.forme.feature.nutrition.data.advisedRestDayKcal
import fr.olivier.forme.feature.nutrition.domain.ActivityLevel
import fr.olivier.forme.feature.nutrition.domain.EnergyMath
import fr.olivier.forme.feature.nutrition.domain.NutritionGoal
import fr.olivier.forme.feature.nutrition.domain.NutritionMath
import fr.olivier.forme.feature.nutrition.domain.ProfileBounds
import fr.olivier.forme.feature.nutrition.ui.ActivityChips
import fr.olivier.forme.feature.nutrition.ui.CustomTarget
import fr.olivier.forme.feature.nutrition.ui.KCAL_CUSTOM_TAG
import fr.olivier.forme.feature.nutrition.ui.PROTEIN_CUSTOM_TAG
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.ui.components.parseDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

/** The wizard's fields as typed (text kept while editing), and the step shown: 0 to [OnboardingDraft.LAST]. */
data class OnboardingDraft(
    val step: Int = 0,
    val firstName: String = "",
    val birthYear: String = "",
    val sex: Sex? = null,
    val height: String = "",
    val weight: String = "",
    val goal: NutritionGoal? = null,
    val activity: ActivityLevel? = null,
    val wheyProtein: String = "",
    val creatine: String = Fmt.number(SupplementDefaults.CREATINE_DEFAULT_G),
    val kcalOverride: Int? = null,
    val proteinOverrideG: Int? = null,
) {
    companion object {
        /** You, body, goal, supplements, then the summary. */
        const val LAST = 4
        const val QUESTIONS = 4
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val onboarding: Onboarding, private val clock: Clock) : ViewModel() {
    private val _draft = MutableStateFlow(OnboardingDraft())
    val draft = _draft.asStateFlow()
    val currentYear: Int = LocalDate.now(clock).year

    fun edit(transform: (OnboardingDraft) -> OnboardingDraft) = _draft.update(transform)

    fun birthYear(d: OnboardingDraft = _draft.value) = ProfileBounds.birthYear(d.birthYear, currentYear)
    fun height(d: OnboardingDraft = _draft.value) = ProfileBounds.height(d.height)
    fun weight(d: OnboardingDraft = _draft.value) = ProfileBounds.weight(parseDecimal(d.weight))
    fun minor(d: OnboardingDraft = _draft.value) = EnergyMath.isMinor(birthYear(d), currentYear)

    /** A field typed but out of bounds blocks "Suivant" (an empty field is fine: it can be filled in later). */
    fun stepValid(d: OnboardingDraft = _draft.value): Boolean = when (d.step) {
        1 -> (d.birthYear.isEmpty() || birthYear(d) != null) && (d.height.isEmpty() || height(d) != null) &&
            (d.weight.isEmpty() || weight(d) != null)
        3 -> (d.wheyProtein.isEmpty() || parseDecimal(d.wheyProtein)?.let { it in 1.0..60.0 } == true) &&
            (d.creatine.isEmpty() || parseDecimal(d.creatine)?.let { it in 0.5..20.0 } == true)
        else -> true
    }

    fun next() = edit { it.copy(step = (it.step + 1).coerceAtMost(OnboardingDraft.LAST)) }
    fun back() = edit { it.copy(step = (it.step - 1).coerceAtLeast(0)) }

    /** "Passer": this screen's answers are dropped, then the next screen. */
    fun skipStep() = edit { d ->
        val cleared = when (d.step) {
            0 -> d.copy(firstName = "")
            1 -> d.copy(birthYear = "", sex = null, height = "", weight = "")
            2 -> d.copy(goal = null, activity = null)
            3 -> d.copy(wheyProtein = "", creatine = "")
            else -> d.copy(kcalOverride = null, proteinOverrideG = null)
        }
        cleared.copy(step = (d.step + 1).coerceAtMost(OnboardingDraft.LAST))
    }

    /** The profile the answers make, for the calculated targets of the summary. */
    fun profile(d: OnboardingDraft = _draft.value): ProfileEntity = answers(d).applyTo(ProfileEntity())

    fun calculatedKcal(d: OnboardingDraft = _draft.value): Int? =
        if (minor(d)) null else profile(d).advisedRestDayKcal(weight(d), currentYear)

    fun calculatedProteinG(d: OnboardingDraft = _draft.value): Int? =
        NutritionMath.proteinTarget(weight(d), ProfileEntity.DEFAULT_PROTEIN_G_PER_KG)?.roundToInt()

    fun answers(d: OnboardingDraft = _draft.value) = OnboardingAnswers(
        firstName = d.firstName.trim().takeIf { it.isNotEmpty() }?.take(30),
        birthYear = birthYear(d),
        sex = d.sex,
        heightCm = height(d),
        weightKg = weight(d),
        goal = d.goal,
        activity = d.activity,
        wheyProteinPerDoseG = if (minor(d)) null else parseDecimal(d.wheyProtein)?.takeIf { it in 1.0..60.0 },
        creatineG = if (minor(d)) null else parseDecimal(d.creatine)?.takeIf { it in 0.5..20.0 },
        kcalOverride = if (minor(d)) null else d.kcalOverride,
        proteinOverrideG = d.proteinOverrideG,
    )

    fun finish(onDone: () -> Unit) = viewModelScope.launch {
        onboarding.save(answers(), LocalDate.now(clock))
        onDone()
    }

    /** Closed without answering (back on the first screen): not shown again. */
    fun close(onDone: () -> Unit) = viewModelScope.launch {
        onboarding.skipAll()
        onDone()
    }
}

const val ONBOARDING_NEXT_TAG = "onboarding_next"
const val ONBOARDING_SKIP_TAG = "onboarding_skip"

/**
 * First launch (V2.25): you, your body, your goal, your supplements, then the targets calculated (editable) before
 * validating. Each screen can be skipped; back returns to the previous one. Neutral texts.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val d by viewModel.draft.collectAsStateWithLifecycle()
    BackHandler { if (d.step == 0) viewModel.close(onDone) else viewModel.back() }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().imePadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
            val progress = (d.step + 1).toFloat() / (OnboardingDraft.LAST + 1)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(
                if (d.step < OnboardingDraft.QUESTIONS) stringResource(R.string.onboarding_step, d.step + 1, OnboardingDraft.QUESTIONS)
                else stringResource(R.string.onboarding_summary_step),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (d.step) {
                    0 -> YouStep(d, viewModel)
                    1 -> BodyStep(d, viewModel)
                    2 -> GoalStep(d, viewModel)
                    3 -> SupplementsStep(d, viewModel)
                    else -> SummaryStep(d, viewModel)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (d.step > 0) OutlinedButton(onClick = viewModel::back) { Text(stringResource(R.string.onboarding_back)) }
                Spacer(Modifier.weight(1f))
                if (d.step < OnboardingDraft.LAST) {
                    TextButton(onClick = viewModel::skipStep, modifier = Modifier.testTag(ONBOARDING_SKIP_TAG)) { Text(stringResource(R.string.onboarding_skip)) }
                    Button(onClick = viewModel::next, enabled = viewModel.stepValid(d), modifier = Modifier.padding(start = 8.dp).testTag(ONBOARDING_NEXT_TAG)) {
                        Text(stringResource(R.string.onboarding_next))
                    }
                } else {
                    Button(onClick = { viewModel.finish(onDone) }, modifier = Modifier.testTag(ONBOARDING_NEXT_TAG)) { Text(stringResource(R.string.onboarding_validate)) }
                }
            }
        }
    }
}

@Composable
private fun Title(title: Int, intro: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NumberField(value: String, label: String, error: String?, decimal: Boolean = false, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun YouStep(d: OnboardingDraft, vm: OnboardingViewModel) {
    Title(R.string.onboarding_you_title, R.string.onboarding_you_intro)
    OutlinedTextField(
        value = d.firstName,
        onValueChange = { v -> vm.edit { it.copy(firstName = v.take(30)) } },
        label = { Text(stringResource(R.string.onboarding_first_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BodyStep(d: OnboardingDraft, vm: OnboardingViewModel) {
    Title(R.string.onboarding_body_title, R.string.onboarding_body_intro)
    val years = ProfileBounds.birthYears(vm.currentYear)
    NumberField(
        d.birthYear,
        stringResource(R.string.onboarding_birth_year),
        if (d.birthYear.isNotEmpty() && vm.birthYear(d) == null) stringResource(R.string.birth_year_error, years.first, years.last) else null,
    ) { v -> vm.edit { it.copy(birthYear = v.filter(Char::isDigit).take(4)) } }
    Text(stringResource(R.string.onboarding_sex), style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sex.entries.forEach { sex ->
            FilterChip(selected = d.sex == sex, onClick = { vm.edit { it.copy(sex = if (it.sex == sex) null else sex) } }, label = { Text(sex.label) })
        }
    }
    NumberField(
        d.height,
        stringResource(R.string.onboarding_height),
        if (d.height.isNotEmpty() && vm.height(d) == null) stringResource(R.string.height_error, ProfileBounds.HEIGHT_CM.first, ProfileBounds.HEIGHT_CM.last) else null,
    ) { v -> vm.edit { it.copy(height = v.filter(Char::isDigit).take(3)) } }
    NumberField(
        d.weight,
        stringResource(R.string.onboarding_weight),
        if (d.weight.isNotEmpty() && vm.weight(d) == null) {
            stringResource(R.string.weight_error, ProfileBounds.WEIGHT_KG.start.toInt(), ProfileBounds.WEIGHT_KG.endInclusive.toInt())
        } else null,
        decimal = true,
    ) { v -> vm.edit { it.copy(weight = v.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5)) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalStep(d: OnboardingDraft, vm: OnboardingViewModel) {
    Title(R.string.onboarding_goal_title, R.string.onboarding_goal_intro)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NutritionGoal.entries.forEach { goal ->
            FilterChip(selected = d.goal == goal, onClick = { vm.edit { it.copy(goal = if (it.goal == goal) null else goal) } }, label = { Text(goal.label) })
        }
    }
    d.goal?.let { Text(it.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Text(stringResource(R.string.activity_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    ActivityChips(d.activity ?: ActivityLevel.MODERATE) { level -> vm.edit { it.copy(activity = level) } }
}

@Composable
private fun SupplementsStep(d: OnboardingDraft, vm: OnboardingViewModel) {
    Title(R.string.onboarding_supplements_title, R.string.onboarding_supplements_intro)
    if (vm.minor(d)) {
        Text(stringResource(R.string.minor_advice), style = MaterialTheme.typography.bodyMedium)
        return
    }
    NumberField(
        d.wheyProtein,
        stringResource(R.string.onboarding_whey),
        if (d.wheyProtein.isNotEmpty() && parseDecimal(d.wheyProtein)?.let { it in 1.0..60.0 } != true) stringResource(R.string.onboarding_whey_error) else null,
        decimal = true,
    ) { v -> vm.edit { it.copy(wheyProtein = v.take(4)) } }
    NumberField(
        d.creatine,
        stringResource(R.string.onboarding_creatine),
        if (d.creatine.isNotEmpty() && parseDecimal(d.creatine)?.let { it in 0.5..20.0 } != true) stringResource(R.string.onboarding_creatine_error) else null,
        decimal = true,
    ) { v -> vm.edit { it.copy(creatine = v.take(4)) } }
}

@Composable
private fun SummaryStep(d: OnboardingDraft, vm: OnboardingViewModel) {
    Title(R.string.onboarding_summary_title, R.string.onboarding_summary_intro)
    val answers = vm.answers(d)
    listOfNotNull(
        answers.firstName,
        answers.birthYear?.let { stringResource(R.string.onboarding_summary_age, vm.currentYear - it) },
        answers.heightCm?.let { "$it cm" },
        answers.weightKg?.let { Fmt.kg(it) },
        answers.goal?.label,
    ).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), style = MaterialTheme.typography.bodyLarge) }

    Text(stringResource(R.string.onboarding_kcal), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    when {
        vm.minor(d) -> Text(stringResource(R.string.minor_advice), style = MaterialTheme.typography.bodyMedium)
        answers.goal == null -> Text(stringResource(R.string.onboarding_kcal_no_goal), style = MaterialTheme.typography.bodyMedium)
        else -> {
            if (vm.calculatedKcal(d) == null) Text(stringResource(R.string.onboarding_kcal_missing), style = MaterialTheme.typography.bodyMedium)
            CustomTarget(vm.calculatedKcal(d), d.kcalOverride, "kcal", ProfileBounds.KCAL, 50, 2000, KCAL_CUSTOM_TAG) { v -> vm.edit { it.copy(kcalOverride = v) } }
        }
    }
    Text(stringResource(R.string.onboarding_protein), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    if (vm.calculatedProteinG(d) == null) Text(stringResource(R.string.onboarding_protein_missing), style = MaterialTheme.typography.bodyMedium)
    CustomTarget(vm.calculatedProteinG(d), d.proteinOverrideG, "g", ProfileBounds.PROTEIN_G, 5, 150, PROTEIN_CUSTOM_TAG) { v -> vm.edit { it.copy(proteinOverrideG = v) } }
    Text(stringResource(R.string.onboarding_summary_later), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
