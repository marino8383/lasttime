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
import it.marino8383.lasttime.data.CounterMode
import it.marino8383.lasttime.data.ROUND_STEPS
import it.marino8383.lasttime.data.STATE_PRESETS
import it.marino8383.lasttime.data.joinStates
import it.marino8383.lasttime.data.parseStates
import it.marino8383.lasttime.data.roundNearestMs
import it.marino8383.lasttime.formatClock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALIAN)
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.ITALIAN)

/**
 * Bottom sheet di creazione/modifica contatore (pattern A della v3):
 * nome + inizio (adesso — con quick pick degli orari tondi — oppure data/ora scelta;
 * futuro da data/ora -> clamp ad adesso, v16) + arrotondamento del Fatto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCounterSheet(
    counter: Counter?,
    onDismiss: () -> Unit,
    onSave: (name: String, startMs: Long, mode: String, roundMinutes: Int?, states: String?, initialState: String?) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(counter?.name ?: "") }
    var mode by remember { mutableStateOf(counter?.mode ?: CounterMode.PRECISO) }
    var startNow by remember { mutableStateOf(counter == null) }
    var startMs by remember { mutableLongStateOf(counter?.startMs ?: System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    // Quick pick su "Adesso" (solo in creazione): null = adesso al secondo.
    var pickedMs by remember { mutableStateOf<Long?>(null) }
    var roundMinutes by remember { mutableStateOf(counter?.roundMinutes) }
    val giornaliero = mode == CounterMode.GIORNALIERO
    val stati = mode == CounterMode.STATI
    // Stati: il testo così come lo si scrive, uno per riga; e da quale si parte.
    var statesText by remember { mutableStateOf(parseStates(counter?.states).joinToString("\n")) }
    val statesList = parseStates(statesText)
    var initialState by remember { mutableStateOf<String?>(null) }
    var initialChosen by remember { mutableStateOf(false) }
    // Finché non si sceglie a mano, si parte dal primo stato dell'elenco.
    val initial = if (initialChosen) initialState?.takeIf { it in statesList } else statesList.firstOrNull()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            SheetTitle(
                title = if (counter == null) "Nuovo contatore" else "Modifica contatore",
                onClose = onDismiss,
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

            // La modalità si sceglie solo in creazione. Su un contatore che esiste già,
            // soprattutto se ha storico vero, cambiarla al volo da qui — senza spiegare cosa
            // succede e senza nessun controllo su chi lo fa — è esattamente quello che
            // "Converti in Giornaliera" (Riparti avanzato) sostituisce apposta.
            if (counter == null) {
                Text(
                    "MODALITÀ",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !giornaliero,
                        onClick = { mode = CounterMode.PRECISO },
                        label = { Text("Al secondo") },
                    )
                    FilterChip(
                        selected = giornaliero,
                        onClick = { mode = CounterMode.GIORNALIERO },
                        label = { Text("A giorni") },
                    )
                    FilterChip(
                        selected = stati,
                        onClick = { mode = CounterMode.STATI },
                        label = { Text("Stati") },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            if (stati) {
                Text(
                    "STATI",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (counter == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        STATE_PRESETS.forEach { (label, preset) ->
                            FilterChip(
                                selected = statesList == preset,
                                onClick = {
                                    statesText = preset.joinToString("\n")
                                    if (name.isBlank()) name = if (preset.size == 2) preset.first() else label
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = statesText,
                    onValueChange = { statesText = it },
                    label = { Text("Uno per riga (es. Felice, Stanco…)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (counter == null)
                        "Uno stato alla volta: ogni cambio chiude quello di prima nello storico. " +
                            "C'è sempre anche “nessuno”, che non entra nelle percentuali."
                    else
                        "Rinominare uno stato non cambia lo storico già scritto.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (counter == null && statesList.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "SI PARTE DA",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    StateChips(
                        states = statesList,
                        selected = initial,
                        onSelect = {
                            initialChosen = true
                            initialState = it
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            Text(
                when {
                    giornaliero -> "ULTIMO EVENTO"
                    stati -> "DA QUANDO"
                    else -> "INIZIO"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (counter == null && mode == CounterMode.PRECISO) {
                // Nuovo timer al secondo: "Adesso" col quick pick degli orari tondi.
                // FilterChip non si presta al tieni-premuto-e-scorri, quindi qui è un
                // componente a sé; "Data e ora" resta sotto com'era.
                QuickStartPick(
                    selected = startNow,
                    pickedMs = pickedMs,
                    onPick = {
                        startNow = true
                        pickedMs = it
                    },
                )
                Spacer(Modifier.height(8.dp))
                FilterChip(
                    selected = !startNow,
                    onClick = {
                        startNow = false
                        pickedMs = null
                    },
                    label = { Text("Data e ora") },
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = startNow,
                        onClick = { startNow = true },
                        label = {
                            Text(
                                when {
                                    giornaliero -> "Oggi"
                                    counter == null -> "Adesso"
                                    stati -> "Da adesso"
                                    else -> "Riparti da adesso"
                                }
                            )
                        },
                    )
                    FilterChip(
                        selected = !startNow,
                        onClick = { startNow = false },
                        label = { Text(if (giornaliero) "Data" else "Data e ora") },
                    )
                }
            }

            if (!startNow) {
                Spacer(Modifier.height(8.dp))
                val zoned = Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDatePicker = true }) {
                        Text("📅 ${dateFmt.format(zoned)}")
                    }
                    if (!giornaliero) {
                        OutlinedButton(onClick = { showTimePicker = true }) {
                            Text("🕐 ${timeFmt.format(zoned)}")
                        }
                    }
                }
            }

            if (mode == CounterMode.PRECISO) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "ARROTONDA IL FATTO",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf<Int?>(null) + ROUND_STEPS).forEach { step ->
                        FilterChip(
                            selected = roundMinutes == step,
                            onClick = { roundMinutes = step },
                            label = {
                                Text(
                                    when (step) {
                                        null -> "No"
                                        60 -> "1 h"
                                        else -> "$step′"
                                    }
                                )
                            },
                        )
                    }
                }
                roundMinutes?.let { step ->
                    // Esempio concreto con l'ora di adesso: si capisce più di una regola.
                    val ora = System.currentTimeMillis()
                    val tondo = roundNearestMs(ora, step)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Il Fatto vale all'orario tondo più vicino: toccato adesso vale " +
                            "${formatClock(tondo)}" +
                            (if (tondo > ora) ", e fino ad allora il timer continua: riparte da solo a quell'ora." else "."),
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                enabled = name.isNotBlank() && (!stati || statesList.isNotEmpty()),
                onClick = {
                    val nowMs = System.currentTimeMillis()
                    val chosen = if (startNow) pickedMs ?: nowMs else startMs
                    if (!startNow && chosen > nowMs) {
                        Toast.makeText(context, "⚠️ Data nel futuro: riparto da adesso", Toast.LENGTH_SHORT).show()
                    }
                    // Il futuro vale solo se scelto col quick pick: parte più tardi, da solo.
                    val start = if (startNow && pickedMs != null) chosen else chosen.coerceAtMost(nowMs)
                    onSave(name, start, mode, roundMinutes, joinStates(statesList), initial)
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
