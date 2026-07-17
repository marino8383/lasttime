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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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

/**
 * Dialog campanella: "avvisami dopo X minuti/ore/giorni", scelta del ritmo dopo il Fatto
 * (riparti da adesso / mantieni il ritmo) e interruttore on/off senza perdere la configurazione.
 */
@Composable
fun BellDialog(
    counter: Counter,
    onDismiss: () -> Unit,
    onSave: (bellMinutes: Long?, bellMode: String, bellEnabled: Boolean) -> Unit,
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
    var mode by remember { mutableStateOf(counter.bellMode) }
    var enabled by remember { mutableStateOf(counter.bellEnabled) }
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

                Spacer(Modifier.height(14.dp))
                Text(
                    "DOPO IL “FATTO”",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == "INTERVAL",
                        onClick = { mode = "INTERVAL" },
                        label = { Text("Riparti da adesso", fontSize = 11.sp) },
                    )
                    FilterChip(
                        selected = mode == "FIXED",
                        onClick = { mode = "FIXED" },
                        label = { Text("Mantieni il ritmo", fontSize = 11.sp) },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (mode == "FIXED")
                        "Es. pillola ogni 8 h: se confermi con 1 h di ritardo, la prossima suona dopo 7 h."
                    else
                        "La prossima campanella parte da quando premi Fatto (o ↺).",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (existing != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (enabled) "Campanella attiva" else "Campanella spenta 🔕",
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null,
                onClick = { onSave(amount!! * unit.minutes, mode, enabled) },
            ) { Text("Salva") }
        },
        dismissButton = {
            if (existing != null) {
                TextButton(onClick = { onSave(null, mode, true) }) {
                    Text("Rimuovi", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Annulla") }
            }
        },
    )
}
