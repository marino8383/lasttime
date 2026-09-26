package it.marino8383.lasttime.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marino8383.lasttime.data.Counter
import it.marino8383.lasttime.data.nextDailyBellAtMs
import it.marino8383.lasttime.formatRingTime

/**
 * Campanella per un contatore GIORNALIERO (v28): "ogni N giorni, alle HH:MM". Niente
 * chip minuti/ore/secondi (sempre giorni) né "mantieni il ritmo" — la prossima scadenza
 * dipende solo dalla data dell'ultimo evento, non da quando confermi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyBellDialog(
    counter: Counter,
    onDismiss: () -> Unit,
    onSave: (bellMinutes: Long?, dailyBellMinuteOfDay: Int?, enabled: Boolean, nextBellAtMs: Long?) -> Unit,
) {
    val existingDays = counter.bellMinutes?.div(1440)
    var amountText by remember { mutableStateOf(existingDays?.toString() ?: "") }
    var minuteOfDay by remember { mutableStateOf(counter.dailyBellMinuteOfDay ?: 20 * 60) }
    var enabled by remember { mutableStateOf(if (existingDays != null) counter.bellEnabled else true) }
    var showTimePicker by remember { mutableStateOf(false) }

    val days = amountText.toLongOrNull()?.takeIf { it > 0 }
    val nextBellAt = days?.let { nextDailyBellAtMs(counter.startMs, it, minuteOfDay) }
    val hh = minuteOfDay / 60
    val mm = minuteOfDay % 60

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            SheetTitle(
                title = "🔔 Avvisami dopo",
                onClose = onDismiss,
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter(Char::isDigit).take(3) },
                        label = { Text("Ogni quanti giorni") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(onClick = { showTimePicker = true }) {
                        Text("🕐 %02d:%02d".format(hh, mm))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Se passano tanti giorni senza un evento, suona all'orario scelto — " +
                        "non nel cuore della notte.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                nextBellAt?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "🔔 suonerà ${formatRingTime(it)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (existingDays != null) {
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
                enabled = days != null,
                onClick = {
                    onSave(days?.times(1440), minuteOfDay, enabled, nextBellAt)
                },
            ) { Text("Salva") }
        },
        dismissButton = {
            if (existingDays != null) {
                TextButton(onClick = { onSave(null, null, true, null) }) {
                    Text("Rimuovi", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )

    if (showTimePicker) {
        val timeState = rememberTimePickerState(initialHour = hh, initialMinute = mm, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    minuteOfDay = timeState.hour * 60 + timeState.minute
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Annulla") }
            },
            text = { TimePicker(state = timeState) },
        )
    }
}
