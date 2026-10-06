package fr.olivier.forme.feature.nutrition.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.olivier.forme.feature.nutrition.domain.DayTotals
import fr.olivier.forme.feature.nutrition.domain.KcalTarget
import fr.olivier.forme.feature.nutrition.domain.MacroTargets
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

/** Protein target reached (ring and advice): going beyond it is fine. */
internal val ReachedGreen = Color(0xFF43A047)

/**
 * The day at a glance: large rings for calories and protein (eaten / target, what is left), small ones for carbs
 * and fat against their advised targets, then the share of energy of each macro.
 */
@Composable
fun DayRings(
    totals: DayTotals,
    kcalTarget: KcalTarget?,
    proteinTargetG: Double?,
    macroTargets: MacroTargets?,
    sessionKcal: Double?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            val kcalTotal = kcalTarget?.totalKcal?.toDouble()
            ProgressRing(
                value = totals.kcal,
                target = kcalTotal,
                unit = "kcal",
                label = "Calories",
                color = if (kcalTotal != null && totals.kcal > kcalTotal * 1.1) scheme.tertiary else scheme.primary,
                caption = kcalTotal?.let { leftCaption(it - totals.kcal, "") } ?: "objectif : voir profil",
                large = true,
            )
            ProgressRing(
                value = totals.proteinG,
                target = proteinTargetG,
                unit = "g",
                label = "Protéines",
                color = if (proteinTargetG != null && totals.proteinG >= proteinTargetG) ReachedGreen else scheme.primary,
                caption = proteinTargetG?.let {
                    if (totals.proteinG >= it) "objectif atteint" else leftCaption(it - totals.proteinG, " g")
                } ?: "ajoute une pesée",
                large = true,
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            ProgressRing(totals.carbsG, macroTargets?.carbsG?.toDouble(), "g", "Glucides", scheme.secondary)
            ProgressRing(totals.fatG, macroTargets?.fatG?.toDouble(), "g", "Lipides", scheme.secondary)
        }
        if (kcalTarget != null && kcalTarget.sessionKcal > 0) {
            Text(
                "Objectif : repos ${Fmt.number(kcalTarget.baseKcal.toDouble(), 0)} + séances ${kcalTarget.sessionKcal} kcal",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        NutritionMath.energySplit(totals)?.let {
            Text(
                "Énergie : protéines ${it.proteinPct} % · glucides ${it.carbsPct} % · lipides ${it.fatPct} %",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (kcalTarget == null) SessionSpendLine(totals.kcal, sessionKcal)
    }
}

private fun leftCaption(left: Double, unit: String): String =
    if (left >= 0) "reste ${Fmt.number(left, 0)}$unit" else "+${Fmt.number(-left, 0)}$unit"

/**
 * Progress ring drawn with Compose Canvas: [value] / [target] written in the centre, [label] below.
 * Without a target, only the value is shown on an empty ring.
 */
@Composable
fun ProgressRing(
    value: Double,
    target: Double?,
    unit: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    caption: String? = null,
    large: Boolean = false,
) {
    val size = if (large) 132.dp else 84.dp
    val stroke = if (large) 12.dp else 8.dp
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val fraction = if (target != null && target > 0) (value / target).toFloat().coerceIn(0f, 1f) else 0f
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val width = stroke.toPx()
                val inset = width / 2
                val arcSize = Size(this.size.width - width, this.size.height - width)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(width))
                if (fraction > 0f) {
                    drawArc(color, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(width, cap = StrokeCap.Round))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    Fmt.number(value, 0),
                    style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    target?.let { "/ ${Fmt.number(it, 0)} $unit" } ?: unit,
                    style = if (large) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (large && caption != null) {
                    Text(caption, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
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
