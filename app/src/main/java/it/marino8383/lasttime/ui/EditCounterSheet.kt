package it.marino8383.lasttime.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marino8383.lasttime.data.Counter
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALIAN)
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.ITALIAN)

/**
 * Bottom sheet di creazione/modifica contatore (pattern A della v3):
 * nome + inizio (adesso oppure data/ora scelta; futuro -> clamp ad adesso, v16).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCounterSheet(
    counter: Counter?,
    onDismiss: () -> Unit,
    onSave: (name: String, startMs: Long) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(counter?.name ?: "") }
    var startNow by remember { mutableStateOf(counter == null) }
    var startMs by remember { mutableLongStateOf(counter?.startMs ?: System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Text(
                if (counter == null) "Nuovo contatore" else "Modifica contatore",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome (es. “Bevuto acqua”)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            Text(
                "INIZIO",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = startNow,
                    onClick = { startNow = true },
                    label = { Text(if (counter == null) "Adesso" else "Riparti da adesso") },
                )
                FilterChip(
                    selected = !startNow,
                    onClick = { startNow = false },
                    label = { Text("Data e ora") },
                )
            }

            if (!startNow) {
                Spacer(Modifier.height(8.dp))
                val zoned = Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDatePicker = true }) {
                        Text("📅 ${dateFmt.format(zoned)}")
                    }
                    OutlinedButton(onClick = { showTimePicker = true }) {
                        Text("🕐 ${timeFmt.format(zoned)}")
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    val nowMs = System.currentTimeMillis()
                    val chosen = if (startNow) nowMs else startMs
                    if (!startNow && chosen > nowMs) {
                        Toast.makeText(context, "⚠️ Data nel futuro: riparto da adesso", Toast.LENGTH_SHORT).show()
                    }
                    onSave(name, chosen.coerceAtMost(nowMs))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Salva", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = startMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { selected ->
                        val date = Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate()
                        val time = Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault()).toLocalTime()
                        startMs = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Annulla") }
            },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimePicker) {
        val zoned = Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault())
        val timeState = rememberTimePickerState(
            initialHour = zoned.hour,
            initialMinute = zoned.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startMs = zoned.toLocalDate()
                        .atTime(timeState.hour, timeState.minute)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
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
