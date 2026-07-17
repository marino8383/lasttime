package it.marino8383.lasttime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marino8383.lasttime.data.Counter

private enum class BellUnit(val label: String, val minutes: Long) {
    MINUTES("minuti", 1),
    HOURS("ore", 60),
    DAYS("giorni", 60 * 24),
}

/** Dialog campanella: "avvisami dopo X minuti/ore/giorni"; il contatore sforato si evidenzia in ambra. */
@Composable
fun BellDialog(
    counter: Counter,
    onDismiss: () -> Unit,
    onSave: (bellMinutes: Long?) -> Unit,
) {
    val existing = counter.bellMinutes
    val initialUnit = when {
        existing == null -> BellUnit.HOURS
        existing % BellUnit.DAYS.minutes == 0L -> BellUnit.DAYS
        existing % BellUnit.HOURS.minutes == 0L -> BellUnit.HOURS
        else -> BellUnit.MINUTES
    }
    var unit by remember { mutableStateOf(initialUnit) }
    var amountText by remember {
        mutableStateOf(existing?.let { (it / initialUnit.minutes).toString() } ?: "")
    }
    val amount = amountText.toLongOrNull()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔔 Avvisami dopo") },
        text = {
            Column {
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
                    BellUnit.entries.forEach { u ->
                        FilterChip(
                            selected = unit == u,
                            onClick = { unit = u },
                            label = { Text(u.label) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Oltre la soglia il contatore viene evidenziato.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null,
                onClick = { onSave(amount!! * unit.minutes) },
            ) { Text("Salva") }
        },
        dismissButton = {
            if (existing != null) {
                TextButton(onClick = { onSave(null) }) {
                    Text("Rimuovi", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Annulla") }
            }
        },
    )
}
