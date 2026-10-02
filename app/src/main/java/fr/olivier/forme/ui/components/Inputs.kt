package fr.olivier.forme.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Large −/+ stepper for one-handed input between two sets.
 * Tapping the value opens a numeric dialog for big jumps.
 */
@Composable
fun Stepper(
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    onValueEntered: ((String) -> Unit)? = null,
    decimal: Boolean = false,
) {
    var editing by remember { mutableStateOf(false) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 280.dp)) {
            FilledTonalIconButton(onClick = onMinus, modifier = Modifier.size(52.dp)) {
                Text("−", style = MaterialTheme.typography.headlineMedium)
            }
            Text(
                valueText,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = onValueEntered != null) { editing = true }
                    .padding(vertical = 8.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(52.dp)) {
                Text("+", style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
    if (editing && onValueEntered != null) {
        NumberInputDialog(
            title = label,
            initial = valueText.filter { it.isDigit() || it == ',' || it == '.' || it == ':' },
            decimal = decimal,
            onDismiss = { editing = false },
            onConfirm = {
                editing = false
                onValueEntered(it)
            },
        )
    }
}

@Composable
fun NumberInputDialog(
    title: String,
    initial: String,
    decimal: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Reps in reserve, 0..5; tapping the selected chip clears it. */
@Composable
fun RirChips(selected: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("RIR", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..5).forEach { value ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(if (selected == value) null else value) },
                    label = { Text(if (value == 5) "5+" else "$value") },
                )
            }
        }
    }
}

/** Parses "22,5" or "22.5"; null when invalid. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/** Parses "2:30" (min:sec) or "150" (seconds). */
fun parseDuration(text: String): Int? {
    val t = text.trim()
    if (':' in t) {
        val (m, s) = t.split(':', limit = 2)
        return (m.toIntOrNull() ?: return null) * 60 + (s.toIntOrNull() ?: return null)
    }
    return t.toIntOrNull()
}

/** Compact −/+ stepper used in configuration forms. */
@Composable
fun SmallStepper(label: String, valueText: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = onMinus, modifier = Modifier.size(40.dp)) { Text("−") }
            Text(valueText, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
            androidx.compose.material3.IconButton(onClick = onPlus, modifier = Modifier.size(40.dp)) { Text("+") }
        }
    }
}
