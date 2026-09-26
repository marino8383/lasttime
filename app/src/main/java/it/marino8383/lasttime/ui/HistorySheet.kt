package it.marino8383.lasttime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    onRemoveDayAll: (List<Round>) -> Unit = {},
) {
    val giornaliero = counter.mode == CounterMode.GIORNALIERO
    val timed = rounds.filter { !it.noTime }
    val noTimeCount = rounds.size - timed.size
    val longest = timed.maxOfOrNull { it.endMs - it.startMs }
    val average = if (timed.isNotEmpty()) timed.sumOf { it.endMs - it.startMs } / timed.size else null
    var showAddDay by remember { mutableStateOf(false) }

    // Un contatore Giornaliero raggruppa i round per data di calendario: più occorrenze
    // lo stesso giorno sono una riga sola con un contatore ×N, non righe ripetute.
    val zone = ZoneId.systemDefault()
    val dayGroups: List<List<Round>> = if (giornaliero) {
        rounds.groupBy { Instant.ofEpochMilli(it.endMs).atZone(zone).toLocalDate() }
            .values.sortedByDescending { it.first().endMs }
    } else {
        emptyList()
    }

    // Sui Giornalieri "1h" non direbbe niente: l'unità è il giorno, non ha senso sotto.
    val windows = if (giornaliero) {
        listOf("7g" to 7 * DAY_MS, "30g" to 30 * DAY_MS, "anno" to 365 * DAY_MS)
    } else {
        listOf(
            "1h" to HOUR_MS,
            "24h" to DAY_MS,
            "7g" to 7 * DAY_MS,
            "30g" to 30 * DAY_MS,
            "anno" to 365 * DAY_MS,
        )
    }

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
                    if (giornaliero) {
                        append("Eventi: ${rounds.size} · giorni attivi: ${dayGroups.size}")
                    } else {
                        append("Eventi: ${rounds.size}")
                        if (noTimeCount > 0) append(" (di cui $noTimeCount solo conteggio)")
                    }
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
                Spacer(Modifier.height(14.dp))
            }

            // Calendario: colpo d'occhio sui giorni con almeno un'occorrenza, tocca per
            // aggiungerne una (per togliere si usa la riga dell'evento più sotto)
            if (giornaliero) {
                item {
                    DailyCalendarGrid(rounds = rounds, onDayTap = onAddDay)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { showAddDay = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.height(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Aggiungi una data più lontana")
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }

            // Quante volte
            if (rounds.isNotEmpty()) {
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
                    if (!giornaliero) {
                        average?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "⏱ In media una volta ogni ${formatDurationTwoParts(it)} (solo round con tempi)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }

            // Elenco eventi per i Giornalieri: un giorno solo, non una riga per occorrenza
            if (giornaliero && dayGroups.isNotEmpty()) {
                item {
                    Text(
                        "GIORNI",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                items(dayGroups.size) { i ->
                    val group = dayGroups[i]
                    val giorno = group.first()
                    val n = group.size
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    formatDateOnly(giorno.endMs),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                val autori = group.mapNotNull { it.byName }.filter { it != Cloud.myName }.distinct()
                                if (autori.isNotEmpty()) {
                                    Text(
                                        "👤 ${autori.joinToString(", ")}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                // Distanza in giorni dal giorno precedente (più vecchio):
                                // è "quanti giorni senza" fra una volta e l'altra.
                                if (i < dayGroups.lastIndex) {
                                    val gap = calendarDaysBetween(dayGroups[i + 1].first().endMs, giorno.endMs)
                                    Text(
                                        if (gap == 1L) "+1 giorno dal precedente" else "+$gap giorni dal precedente",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            // ×N modificabile: − toglie una singola occorrenza, + ne aggiunge
                            // un'altra lo stesso giorno. Il cestino toglie il giorno intero.
                            IconButton(
                                enabled = n > 1,
                                onClick = { onRemoveDay(group.first()) },
                            ) {
                                Icon(
                                    Icons.Filled.Remove, contentDescription = "Una in meno",
                                    tint = if (n > 1) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                )
                            }
                            Text(
                                "×$n",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            IconButton(onClick = { onAddDay(giorno.endMs) }) {
                                Icon(
                                    Icons.Filled.Add, contentDescription = "Un'altra volta",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onRemoveDayAll(group) }) {
                                Icon(
                                    Icons.Filled.Delete, contentDescription = "Togli il giorno",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (i < dayGroups.size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            // Elenco round conclusi (Precisi)
            if (!giornaliero && rounds.isNotEmpty()) {
                item {
                    Text(
                        "ROUND CONCLUSI",
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
                            if (round.noTime) {
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
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
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

private val monthFmt = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.ITALIAN)
private val weekdayLabels = listOf("L", "M", "M", "G", "V", "S", "D")

/**
 * Griglia mensile per i contatori Giornalieri (v28 fase 2): colpo d'occhio sui giorni con
 * almeno un'occorrenza (verdi, con ×N se più di una), i vuoti restano neutri. Si naviga
 * mese per mese; non si può andare oltre il mese corrente. Tocca un giorno per aggiungerne
 * una occorrenza — per toglierne si usa la riga del giorno nell'elenco qui sotto.
 */
@Composable
private fun DailyCalendarGrid(rounds: List<Round>, onDayTap: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val oggi = LocalDate.now(zone)
    var mese by remember { mutableStateOf(YearMonth.from(oggi)) }
    val conteggi = remember(rounds) {
        rounds.groupingBy { Instant.ofEpochMilli(it.endMs).atZone(zone).toLocalDate() }.eachCount()
    }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { mese = mese.minusMonths(1) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Mese precedente")
            }
            Text(
                monthFmt.format(mese).replaceFirstChar { it.uppercase() },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { mese = mese.plusMonths(1) },
                enabled = mese < YearMonth.from(oggi),
            ) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Mese successivo")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            weekdayLabels.forEach { lettera ->
                Text(
                    lettera,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val primoGiorno = mese.atDay(1)
        val vuotiIniziali = primoGiorno.dayOfWeek.value - 1 // Lunedì=1 -> 0 caselle vuote
        val giorniTotali = mese.lengthOfMonth()
        val righe = (vuotiIniziali + giorniTotali + 6) / 7
        for (r in 0 until righe) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val numero = r * 7 + c - vuotiIniziali + 1
                    Box(Modifier.weight(1f).padding(2.dp).aspectRatio(1f)) {
                        if (numero in 1..giorniTotali) {
                            val data = mese.atDay(numero)
                            val n = conteggi[data] ?: 0
                            val futuro = data.isAfter(oggi)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .background(
                                        color = if (n > 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .then(
                                        if (data == oggi) Modifier.border(
                                            1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp)
                                        ) else Modifier
                                    )
                                    .clickable(enabled = !futuro) { onDayTap(data.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()) },
                            ) {
                                Spacer(Modifier.weight(1f))
                                Text(
                                    numero.toString(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (n > 0) MaterialTheme.colorScheme.onPrimary
                                    else if (futuro) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (n > 1) {
                                    Text(
                                        "×$n",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
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
