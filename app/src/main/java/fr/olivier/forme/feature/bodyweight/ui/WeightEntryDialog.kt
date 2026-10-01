package fr.olivier.forme.feature.bodyweight.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.ui.components.Stepper
import fr.olivier.forme.ui.components.parseDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val dateLabel = DateTimeFormatter.ofPattern("EEEE d MMMM", Fmt.locale)

/** Quick weigh-in: ±0.1 kg stepper pre-filled with the last weight; date defaults to today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightEntryDialog(
    initialKg: Double,
    today: LocalDate,
    onSave: (LocalDate, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var weight by remember { mutableDoubleStateOf(initialKg) }
    var date by remember { mutableStateOf(today) }
    var pickingDate by remember { mutableStateOf(false) }
    fun round(v: Double) = (v * 10).roundToInt() / 10.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle pesée") },
        text = {
            Column {
                Stepper(
                    label = "Poids",
                    valueText = Fmt.kg(weight),
                    onMinus = { weight = round(weight - 0.1) },
                    onPlus = { weight = round(weight + 0.1) },
                    onValueEntered = { text -> parseDecimal(text)?.let { weight = round(it) } },
                    decimal = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { pickingDate = true }) {
                    Text(if (date == today) "Aujourd'hui" else date.format(dateLabel))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(date, weight) }, enabled = weight > 0) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )

    if (pickingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().coerceAtMost(today)
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
        ) { DatePicker(state) }
    }
}
