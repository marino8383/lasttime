package it.marino8383.lasttime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marino8383.lasttime.data.Counter
import it.marino8383.lasttime.data.Round
import it.marino8383.lasttime.formatDateTime
import it.marino8383.lasttime.formatDurationTwoParts
import it.marino8383.lasttime.formatShortDateTime
import it.marino8383.lasttime.ui.theme.OnPrimaryContainer
import it.marino8383.lasttime.ui.theme.PrimaryContainer

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
) {
    val timed = rounds.filter { !it.noTime }
    val noTimeCount = rounds.size - timed.size
    val longest = timed.maxOfOrNull { it.endMs - it.startMs }
    val average = if (timed.isNotEmpty()) timed.sumOf { it.endMs - it.startMs } / timed.size else null

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
                Text(
                    "🕘 Storico — ${counter.name.uppercase()}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(14.dp))
            }

            // Round in corso
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = PrimaryContainer,
                    contentColor = OnPrimaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("ROUND IN CORSO", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            formatDurationTwoParts(now - counter.startMs),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            "dal ${formatDateTime(counter.startMs)}",
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
                longest?.let { SummaryRow("Più lungo: ${formatDurationTwoParts(it)}") }
                average?.let { SummaryRow("Media: ${formatDurationTwoParts(it)}") }
                if (rounds.isEmpty()) {
                    Text(
                        "Nessun round concluso: riparti il timer per registrare il primo.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(14.dp))
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

            // Elenco round conclusi
            if (rounds.isNotEmpty()) {
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
                                Text(
                                    "🔢 SOLO CONTEGGIO — il ${formatShortDateTime(round.endMs)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "—",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                )
                            } else {
                                Text(
                                    "${formatShortDateTime(round.startMs)} → ${formatShortDateTime(round.endMs)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
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
