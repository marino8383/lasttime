package it.marino8383.lasttime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marino8383.lasttime.data.CounterMode
import it.marino8383.lasttime.data.Counter
import it.marino8383.lasttime.data.Round
import it.marino8383.lasttime.data.calendarDaysBetween
import it.marino8383.lasttime.formatDateOnly
import it.marino8383.lasttime.formatDateTime
import it.marino8383.lasttime.sync.Cloud
import it.marino8383.lasttime.formatDurationTwoParts
import it.marino8383.lasttime.formatShortDateTime
import it.marino8383.lasttime.ui.theme.OnPrimaryContainer
import it.marino8383.lasttime.ui.theme.PrimaryContainer
import java.time.Instant
import java.time.ZoneOffset

private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 86_400_000L

/**
 * Storico per contatore (v17/v18/v22 + giri persi v27): round in corso,
 * riepilogo (eventi, più lungo, media sui soli round con tempi),
 * "Quante volte" per finestra su tutti gli eventi, ritmo medio, elenco round.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    counter: Counter,
    now: Long,
    rounds: List<Round>,
    onDismiss: () -> Unit,
    onAddDay: (Long) -> Unit = {},
    onRemoveDay: (Round) -> Unit = {},
) {
    val giornaliero = counter.mode == CounterMode.GIORNALIERO
    val timed = rounds.filter { !it.noTime }
    val noTimeCount = rounds.size - timed.size
    val longest = timed.maxOfOrNull { it.endMs - it.startMs }
    val average = if (timed.isNotEmpty()) timed.sumOf { it.endMs - it.startMs } / timed.size else null
    var showAddDay by remember { mutableStateOf(false) }

    val windows = listOf(
        "1h" to HOUR_MS,
        "24h" to DAY_MS,
        "7g" to 7 * DAY_MS,
        "30g" to 30 * DAY_MS,
        "anno" to 365 * DAY_MS,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 28.dp),
        ) {
            item {
                SheetTitle(
                    title = "🕘 Storico — ${counter.name.uppercase()}",
                    onClose = onDismiss,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(14.dp))
            }

            // Round in corso, oppure lo stato di congelamento se il timer è archiviato (v24)
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = PrimaryContainer,
                    contentColor = OnPrimaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        val archivedMs = counter.archivedMs.takeIf { counter.archived }
                        Text(
                            if (archivedMs != null) "📦 IN ARCHIVIO" else "ROUND IN CORSO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.5.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                archivedMs != null -> "⏸ fermo da ${formatDurationTwoParts(now - archivedMs)}"
                                giornaliero -> {
                                    val giorni = calendarDaysBetween(counter.startMs, now)
                                    if (giorni == 1L) "1 giorno" else "$giorni giorni"
                                }
                                else -> formatDurationTwoParts(now - counter.startMs)
                            },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            when {
                                archivedMs != null -> "archiviato il ${formatDateTime(archivedMs)}"
                                giornaliero -> "ultima volta il ${formatDateOnly(counter.startMs)}"
                                else -> "dal ${formatDateTime(counter.startMs)}"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // Riepilogo
            item {
                val eventsLabel = buildString {
                    append("Eventi: ${rounds.size}")
                    if (noTimeCount > 0) append(" (di cui $noTimeCount solo conteggio)")
                }
                SummaryRow(eventsLabel)
                if (!giornaliero) {
                    longest?.let { SummaryRow("Più lungo: ${formatDurationTwoParts(it)}") }
                    average?.let { SummaryRow("Media: ${formatDurationTwoParts(it)}") }
                }
                if (rounds.isEmpty()) {
                    Text(
                        if (giornaliero) "Nessun evento: tocca “+1” per registrare il primo."
                        else "Nessun round concluso: riparti il timer per registrare il primo.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (giornaliero) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { showAddDay = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.height(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Aggiungi un giorno dimenticato")
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // Quante volte
            if (rounds.isNotEmpty() && !giornaliero) {
                item {
                    Text(
                        "📊 QUANTE VOLTE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        windows.forEach { (label, windowMs) ->
                            val count = rounds.count { it.endMs >= now - windowMs }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(
                                    Modifier.padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        count.toString(),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    average?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "⏱ In media una volta ogni ${formatDurationTwoParts(it)} (solo round con tempi)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }

            // Elenco round conclusi (per i Giornalieri, elenco eventi)
            if (rounds.isNotEmpty()) {
                item {
                    Text(
                        if (giornaliero) "EVENTI" else "ROUND CONCLUSI",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                items(rounds.size) { i ->
                    val round = rounds[i]
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (giornaliero) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        formatDateOnly(round.endMs),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    round.byName?.takeIf { it != Cloud.myName }?.let { chi ->
                                        Text(
                                            "👤 $chi",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                // Distanza in giorni dall'evento precedente (più vecchio):
                                // è "quanti giorni senza" fra un evento e l'altro.
                                if (i < rounds.lastIndex) {
                                    val gap = calendarDaysBetween(rounds[i + 1].endMs, round.endMs)
                                    Text(
                                        if (gap == 0L) "stesso giorno" else if (gap == 1L) "+1 giorno" else "+$gap giorni",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(end = 4.dp),
                                    )
                                }
                                IconButton(onClick = { onRemoveDay(round) }) {
                                    Icon(
                                        Icons.Filled.Delete, contentDescription = "Togli",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            } else if (round.noTime) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "🔢 SOLO CONTEGGIO — il ${formatShortDateTime(round.endMs)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    round.byName?.takeIf { it != Cloud.myName }?.let { chi ->
                                        Text(
                                            "👤 $chi",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                Text(
                                    "—",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                )
                            } else {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${formatShortDateTime(round.startMs)} → ${formatShortDateTime(round.endMs)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    // La firma viaggia sempre — dall'altra parte serve a sapere
                                    // chi e' stato — ma a video si mostra solo se non sei tu,
                                    // altrimenti sarebbe una riga in piu' su ogni round.
                                    round.byName?.takeIf { it != Cloud.myName }?.let { chi ->
                                        Text(
                                            "👤 $chi",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                Text(
                                    formatDurationTwoParts(round.endMs - round.startMs),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                        if (i < rounds.size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }

    if (showAddDay) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = now)
        DatePickerDialog(
            onDismissRequest = { showAddDay = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { selected ->
                        // Il DatePicker incodifica sempre la data scelta a mezzanotte UTC,
                        // qualunque sia il fuso del telefono: va letta come data in UTC e
                        // solo dopo ricostruita a mezzogiorno nel fuso locale, altrimenti nei
                        // fusi indietro rispetto a UTC risulterebbe il giorno prima.
                        val date = Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate()
                        val noonLocale = date.atTime(12, 0)
                            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        onAddDay(noonLocale)
                    }
                    showAddDay = false
                }) { Text("Aggiungi") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDay = false }) { Text("Annulla") }
            },
        ) {
            DatePicker(state = dateState)
        }
    }
}

@Composable
private fun SummaryRow(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}
