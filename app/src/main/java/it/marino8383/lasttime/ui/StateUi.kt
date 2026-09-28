package it.marino8383.lasttime.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import it.marino8383.lasttime.data.parseStates
import it.marino8383.lasttime.formatClock
import it.marino8383.lasttime.formatDateOnly
import it.marino8383.lasttime.formatDateTime
import it.marino8383.lasttime.formatDurationTwoParts
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Come si legge uno stato a video: null è "nessuno". */
fun stateLabel(state: String?): String = state ?: "nessuno"

/**
 * Gli stati di un contatore come chip, più "nessuno" in fondo. Serve sia in creazione
 * (da quale si parte) sia sulla card e nella maschera di cambio (dove si va).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StateChips(
    states: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    includeNone: Boolean = true,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        states.forEach { s ->
            FilterChip(selected = selected == s, onClick = { onSelect(s) }, label = { Text(s) })
        }
        if (includeNone) {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("— nessuno") },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Cambio di stato "avanzato": verso quale stato, da quando (adesso o un orario passato,
 * mai prima dell'inizio dello stato in corso) e con che nota. Sopra, la nota dello stato
 * in corso si può scrivere o correggere finché dura.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StateChangeSheet(
    counter: Counter,
    now: Long,
    onDismiss: () -> Unit,
    onChange: (state: String?, atMs: Long?, note: String?) -> Unit,
    onSaveCurrentNote: (String) -> Unit,
) {
    val context = LocalContext.current

    val states = parseStates(counter.states)
    // Si propone il primo stato diverso da quello in corso: è quasi sempre dove si va.
    var target by remember { mutableStateOf(states.firstOrNull { it != counter.currentState }) }
    var currentNote by remember { mutableStateOf(counter.currentNote.orEmpty()) }
    var note by remember { mutableStateOf("") }
    var fromNow by remember { mutableStateOf(true) }
    var atMs by remember { mutableLongStateOf(now) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            SheetTitle(title = "Cambia stato — ${counter.name}", onClose = onDismiss)
            Spacer(Modifier.height(12.dp))

            Text(
                "${stateLabel(counter.currentState)} da ${formatDurationTwoParts(now - counter.startMs)}",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "dal ${formatDateTime(counter.startMs)}",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = currentNote,
                onValueChange = { currentNote = it },
                label = { Text("Nota su questo stato") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (currentNote.trim() != counter.currentNote.orEmpty()) {
                TextButton(onClick = {
                    onSaveCurrentNote(currentNote)
                    Toast.makeText(context, "✅ Nota salvata", Toast.LENGTH_SHORT).show()
                }) { Text("Salva la nota") }
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel("CAMBIA IN")
            Spacer(Modifier.height(8.dp))
            StateChips(states = states, selected = target, onSelect = { target = it })

            Spacer(Modifier.height(14.dp))
            SectionLabel("DA QUANDO")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = fromNow, onClick = { fromNow = true }, label = { Text("Adesso") })
                FilterChip(selected = !fromNow, onClick = { fromNow = false }, label = { Text("Data e ora") })
            }
            if (!fromNow) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDatePicker = true }) { Text("📅 ${formatDateOnly(atMs)}") }
                    OutlinedButton(onClick = { showTimePicker = true }) { Text("🕐 ${formatClock(atMs)}") }
                }
            }

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Nota (facoltativa, es. “febbre”)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            val stessoStato = target == counter.currentState && fromNow
            Button(
                enabled = !stessoStato,
                onClick = {
                    if (!fromNow && (atMs < counter.startMs || atMs > System.currentTimeMillis())) {
                        Toast.makeText(
                            context,
                            "⚠️ Deve stare fra l'inizio dello stato in corso e adesso.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        onChange(target, if (fromNow) null else atMs, note)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Passa a ${stateLabel(target)}", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = atMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { selected ->
                        // Il DatePicker dà la data a mezzanotte UTC: si rilegge come data e
                        // si rimette sull'ora già scelta nel fuso locale (come altrove).
                        val date = Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate()
                        val time = Instant.ofEpochMilli(atMs).atZone(ZoneId.systemDefault()).toLocalTime()
                        atMs = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Annulla") } },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimePicker) {
        val zoned = Instant.ofEpochMilli(atMs).atZone(ZoneId.systemDefault())
        val timeState = rememberTimePickerState(initialHour = zoned.hour, initialMinute = zoned.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    atMs = zoned.toLocalDate().atTime(timeState.hour, timeState.minute)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Annulla") } },
            text = { TimePicker(state = timeState) },
        )
    }
}
