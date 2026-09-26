package it.marino8383.lasttime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marino8383.lasttime.data.Counter
import it.marino8383.lasttime.data.RoundSummary
import it.marino8383.lasttime.formatDurationTwoParts
import it.marino8383.lasttime.ui.theme.OnPrimaryContainer
import it.marino8383.lasttime.ui.theme.PrimaryContainer

/**
 * Pagina archivio (v24): i timer congelati, con storico consultabile, ripresa e
 * eliminazione. Gli archiviati non compaiono in home né sul tabellone.
 */
@Composable
fun ArchiveScreen(
    counters: List<Counter>,
    summaries: List<RoundSummary>,
    now: Long,
    onBack: () -> Unit,
    onHistory: (Counter) -> Unit,
    onResume: (Counter) -> Unit,
    onDelete: (Counter) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp, 16.dp, 20.dp, 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Text("←", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                "📦 Archivio",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (counters.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Archivio vuoto.\nSwipe a sinistra su un contatore per archiviarlo.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp)) {
                items(counters, key = { it.id }) { counter ->
                    SwipeableCard(
                        onSwipeDelete = { onDelete(counter) },
                        onSwipeArchive = null, // già archiviato
                    ) {
                        ArchivedCard(
                            counter = counter,
                            summary = summaries.firstOrNull { it.counterId == counter.id },
                            now = now,
                            onHistory = { onHistory(counter) },
                            onResume = { onResume(counter) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchivedCard(
    counter: Counter,
    summary: RoundSummary?,
    now: Long,
    onHistory: () -> Unit,
    onResume: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp, 18.dp, 18.dp, 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    counter.name.uppercase(),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PrimaryContainer,
                    contentColor = OnPrimaryContainer,
                ) {
                    Text(
                        "📦 archivio",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
                    )
                }
            }

            val since = counter.archivedMs?.let { formatDurationTwoParts(now - it) }
            Text(
                buildString {
                    if (since != null) append("in archivio da $since") else append("in archivio")
                    val rounds = summary?.rounds ?: 0
                    append(" · $rounds round")
                    summary?.lastDurationMs?.let { append(" · ultimo: ${formatDurationTwoParts(it)}") }
                },
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onHistory) {
                    Icon(
                        Icons.Filled.History, contentDescription = "Storico",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onResume) {
                    Icon(
                        Icons.Filled.PlayArrow, contentDescription = "Riprendi",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
