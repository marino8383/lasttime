package it.marino8383.lasttime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marino8383.lasttime.data.Counter
import it.marino8383.lasttime.formatDurationTwoParts

private enum class SnoozeUnit(val label: String, val minutes: Long) {
    MINUTES("minuti", 1),
    HOURS("ore", 60),
    DAYS("giorni", 60 * 24),
    MONTHS("mesi", 60 * 24 * 30),
}

/** Maschera "Rimanda": ri-avvisa tra X minuti/ore/giorni/mesi (default 10 minuti). */
@Composable
fun SnoozeDialog(
    counter: Counter,
    onDismiss: () -> Unit,
    onSave: (snoozeMinutes: Long) -> Unit,
) {
    var unit by remember { mutableStateOf(SnoozeUnit.MINUTES) }
    var amountText by remember { mutableStateOf("10") }
    val amount = amountText.toLongOrNull()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⏰ Rimanda — ${counter.name}") },
        text = {
            Column {
                Text(
                    "Tra quanto ti riavviso?",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter(Char::isDigit) },
                    label = { Text("Quanto") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SnoozeUnit.entries.forEach { u ->
                        FilterChip(
                            selected = unit == u,
                            onClick = { unit = u },
                            label = { Text(u.label, fontSize = 11.sp) },
                        )
                    }
                }
                amount?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "🔔 tra ${formatDurationTwoParts(it * unit.minutes * 60_000)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null,
                onClick = { onSave(amount!! * unit.minutes) },
            ) { Text("Rimanda") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        },
    )
}
