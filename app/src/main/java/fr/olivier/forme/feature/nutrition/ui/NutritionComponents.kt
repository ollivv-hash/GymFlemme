package fr.olivier.forme.feature.nutrition.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.olivier.forme.feature.nutrition.domain.DayTotals
import fr.olivier.forme.feature.nutrition.domain.NutritionMath
import fr.olivier.forme.feature.nutrition.domain.Per100
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.ui.components.Stepper
import fr.olivier.forme.ui.components.parseDecimal
import kotlin.math.roundToInt

/** Protein gauge: consumed / target, with what is left. */
@Composable
fun ProteinGauge(totals: DayTotals, targetG: Double?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row {
            Text(
                "${totals.proteinG.roundToInt()} g",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                targetG?.let { " / ${it.roundToInt()} g de protéines" } ?: " de protéines",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }
        if (targetG != null && targetG > 0) {
            val progress = (totals.proteinG / targetG).toFloat()
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(12.dp).padding(vertical = 2.dp),
                color = MaterialTheme.colorScheme.tertiary,
            )
            val left = targetG - totals.proteinG
            Text(
                if (left > 0) "Il reste ${left.roundToInt()} g" else "Objectif atteint (+${(-left).roundToInt()} g)",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text("Ajoute une pesée pour calculer l'objectif.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Calories eaten vs spent in the day's sessions (measured by the watch or estimated from heart rate). */
@Composable
fun SessionSpendLine(eatenKcal: Double, sessionKcal: Double?) {
    sessionKcal ?: return
    Text(
        "Dépense des séances : ${sessionKcal.roundToInt()} kcal · apport net ${(eatenKcal - sessionKcal).roundToInt()} kcal",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.tertiary,
    )
}

/** Calories, carbs and fat of the day, with the share of energy of each macro. */
@Composable
fun MacroSummary(totals: DayTotals, modifier: Modifier = Modifier) {
    val split = NutritionMath.energySplit(totals)
    Column(modifier) {
        Text(
            "${totals.kcal.roundToInt()} kcal · glucides ${totals.carbsG.roundToInt()} g · lipides ${totals.fatG.roundToInt()} g",
            style = MaterialTheme.typography.bodyLarge,
        )
        split?.let {
            Text(
                "Énergie : protéines ${it.proteinPct} % · glucides ${it.carbsPct} % · lipides ${it.fatPct} %",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun macrosLine(proteinG: Double, kcal: Double?, carbsG: Double?, fatG: Double?): String = listOfNotNull(
    "${Fmt.number(proteinG)} g prot.",
    kcal?.let { "${it.roundToInt()} kcal" },
    carbsG?.let { "G ${Fmt.number(it)}" },
    fatG?.let { "L ${Fmt.number(it)}" },
).joinToString(" · ")

/** Quantity in grams with live nutrient preview and portion shortcuts. */
@Composable
fun QuantityDialog(
    title: String,
    per100: Per100,
    initialG: Double,
    portions: List<Double>,
    confirmLabel: String,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
    extraAction: (@Composable () -> Unit)? = null,
) {
    var grams by remember { mutableDoubleStateOf(initialG) }
    val m = NutritionMath.forQuantity(per100, grams)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Stepper(
                    label = "Quantité",
                    valueText = "${Fmt.number(grams, 0)} g",
                    onMinus = { grams = (grams - 10).coerceAtLeast(5.0) },
                    onPlus = { grams += 10 },
                    onValueEntered = { t -> parseDecimal(t)?.let { if (it > 0) grams = it } },
                    decimal = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    portions.distinct().forEach { p ->
                        AssistChip(onClick = { grams = p }, label = { Text("${Fmt.number(p, 0)} g") })
                    }
                }
                Text(macrosLine(m.proteinG, m.kcal, m.carbsG, m.fatG), style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Pour 100 g : " + macrosLine(per100.proteinG, per100.kcal, per100.carbsG, per100.fatG),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                extraAction?.invoke()
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(grams) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
