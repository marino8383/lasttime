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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marino8383.lasttime.data.Counter
import it.marino8383.lasttime.formatDateTime
import it.marino8383.lasttime.formatRingTime
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALIAN)
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.ITALIAN)

private data class QuickChip(val label: String, val minutesAgo: Long)

private val quickChips = listOf(
    QuickChip("Adesso", 0),
    QuickChip("10 min fa", 10),
    QuickChip("30 min fa", 30),
    QuickChip("1 h fa", 60),
    QuickChip("1 g fa", 60 * 24),
)

/**
 * Riparti avanzato (v25) + giri persi (v27). Doppio tap su ↺:
 * riparti da un istante scelto (chip rapide o data/ora), reset programmato
 * nel futuro, eventi "solo conteggio" con data approssimativa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedRestartSheet(
    counter: Counter,
    isOwner: Boolean = true,
    onDismiss: () -> Unit,
    onRestartAt: (Long) -> Unit,
    onSchedule: (Long) -> Unit,
    onCancelSchedule: () -> Unit,
    onAddMissed: (Long) -> Unit,
    onAddTimedEvent: (Long) -> Unit,
    onCorrectLast: (Long) -> Unit,
    onConvertToDaily: () -> Unit = {},
) {
    val context = LocalContext.current

    // -1 = data/ora personalizzata; altrimenti minuti fa
    var quickSel by remember { mutableLongStateOf(0L) }
    var customMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var missedMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var missedAdded by remember { mutableIntStateOf(0) }
    var showConvertConfirm by remember { mutableStateOf(false) }

    val nowMs = System.currentTimeMillis()
    val target = if (quickSel >= 0) nowMs - quickSel * 60_000 else customMs
    val lastRoundStart = counter.lastRoundStartMs
    // Dentro l'ultimo round chiuso: non e' un riavvio nuovo, e' l'orario di quello gia'
    // fatto che si sposta. Oltre quel confine si mangerebbe il round prima, e resta bloccato.
    val correctable = lastRoundStart != null && target >= lastRoundStart && target < counter.startMs
    val beforeRound = target < (lastRoundStart ?: counter.startMs)
    val isFuture = target > nowMs + 1_000

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            SheetTitle(
                title = "↺ Riparti — ${counter.name.uppercase()}",
                onClose = onDismiss,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(14.dp))

            counter.scheduledResetMs?.let { scheduled ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "⏲ Reset programmato ${formatRingTime(scheduled)}",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancelSchedule) {
                        Text("Annulla", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Text(
                "RIPARTI DA",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                quickChips.take(3).forEach { chip ->
                    FilterChip(
                        selected = quickSel == chip.minutesAgo,
                        onClick = { quickSel = chip.minutesAgo },
                        label = { Text(chip.label, fontSize = 11.sp) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                quickChips.drop(3).forEach { chip ->
                    FilterChip(
                        selected = quickSel == chip.minutesAgo,
                        onClick = { quickSel = chip.minutesAgo },
                        label = { Text(chip.label, fontSize = 11.sp) },
                    )
                }
                FilterChip(
                    selected = quickSel < 0,
                    onClick = { quickSel = -1 },
                    label = { Text("Data e ora", fontSize = 11.sp) },
                )
            }

            if (quickSel < 0) {
                Spacer(Modifier.height(8.dp))
                DateTimeRow(valueMs = customMs, onChange = { customMs = it })
            }

            Spacer(Modifier.height(8.dp))
            when {
                beforeRound -> Text(
                    if (lastRoundStart != null)
                        "⚠️ Andrebbe oltre l'inizio dell'ultimo round (dal ${formatDateTime(lastRoundStart)}): si mangerebbe quello prima."
                    else
                        "⚠️ Prima dell'inizio del round attuale (dal ${formatDateTime(counter.startMs)})",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
                correctable -> Text(
                    "✏️ Corregge l'ultimo riavvio già fatto (${formatRingTime(counter.startMs)}) portandolo a " +
                        "${formatRingTime(target)}. Non aggiunge un round nuovo.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                isFuture -> Text(
                    "⏲ Il timer continua e si resetta da solo ${formatRingTime(target)}. Un nuovo comando sostituirà questa programmazione.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                else -> Text(
                    "Il round attuale verrà chiuso ${formatRingTime(target)} e salvato nello storico.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                enabled = !beforeRound,
                onClick = {
                    when {
                        isFuture -> onSchedule(target)
                        correctable -> onCorrectLast(target)
                        else -> onRestartAt(target)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        isFuture -> "⏲ Programma il reset"
                        correctable -> "✏️ Correggi l'ultimo riavvio"
                        else -> "↺ Riparti"
                    },
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(14.dp))

            Text(
                "🔢 EVENTI DIMENTICATI",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "“Con orario” chiude il round a quell'istante e il timer riparte da lì (inserisci in ordine cronologico, il più vecchio per primo). " +
                    "“Solo conteggio” per date approssimative: conta nel “Quante volte” ma è escluso da media e più lungo.",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            DateTimeRow(valueMs = missedMs, onChange = { missedMs = it })

            val timedOk = missedMs >= counter.startMs && missedMs <= nowMs
            if (!timedOk) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (missedMs < counter.startMs)
                        "⚠️ “Con orario” vale solo dentro il round attuale (dal ${formatDateTime(counter.startMs)}); per date più vecchie usa “Solo conteggio”."
                    else
                        "⚠️ “Con orario” non può essere nel futuro.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = timedOk,
                    onClick = {
                        onAddTimedEvent(missedMs)
                        missedAdded++
                        Toast.makeText(context, "↺ Round chiuso: il timer riparte da lì", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Text("➕ Con orario")
                }
                OutlinedButton(onClick = {
                    onAddMissed(missedMs)
                    missedAdded++
                    Toast.makeText(context, "🔢 Evento aggiunto al conteggio", Toast.LENGTH_SHORT).show()
                }) {
                    Text("➕ Solo conteggio")
                }
                if (missedAdded > 0) {
                    Text(
                        "aggiunti: $missedAdded",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Poco usata di proposito: in fondo, dopo tutto il resto. Solo per chi ha
            // condiviso questo timer (isOwner=true anche sui non condivisi, dove non c'è
            // nessun altro da proteggere) — vedi Counter.creatorUid.
            if (isOwner) {
                Spacer(Modifier.height(18.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(14.dp))
                Text(
                    "🗓️ MODALITÀ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Passa a “a giorni”: conta le date in cui è successo, non il tempo trascorso. Lo storico resta, la campanella si azzera e va rifatta. Non si torna indietro da qui.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showConvertConfirm = true }) {
                    Text("🗓️ Converti in Giornaliera")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showConvertConfirm) {
        AlertDialog(
            onDismissRequest = { showConvertConfirm = false },
            title = { Text("Convertire in Giornaliera?") },
            text = {
                Text(
                    "“${counter.name}” passa a contare date invece che tempo trascorso. " +
                        "Lo storico resta tutto, ma da qui la campanella va riconfigurata e " +
                        "non c'è modo di tornare indietro da questa schermata."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConvertConfirm = false
                    onConvertToDaily()
                }) { Text("Converti") }
            },
            dismissButton = {
                TextButton(onClick = { showConvertConfirm = false }) { Text("Annulla") }
            },
        )
    }
}

/** Coppia di bottoni data + ora con i rispettivi picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeRow(valueMs: Long, onChange: (Long) -> Unit) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val zoned = Instant.ofEpochMilli(valueMs).atZone(ZoneId.systemDefault())

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { showDate = true }) {
            Text("📅 ${dateFmt.format(zoned)}")
        }
        OutlinedButton(onClick = { showTime = true }) {
            Text("🕐 ${timeFmt.format(zoned)}")
        }
    }

    if (showDate) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = valueMs)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { selected ->
                        val date = Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate()
                        val time = zoned.toLocalTime()
                        onChange(date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Annulla") } },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTime) {
        val timeState = rememberTimePickerState(
            initialHour = zoned.hour,
            initialMinute = zoned.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(
                        zoned.toLocalDate().atTime(timeState.hour, timeState.minute)
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    )
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Annulla") } },
            text = { TimePicker(state = timeState) },
        )
    }
}
