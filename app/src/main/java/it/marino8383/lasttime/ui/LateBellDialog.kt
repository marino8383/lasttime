package it.marino8383.lasttime.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marino8383.lasttime.CountersViewModel.LateBellChoice
import it.marino8383.lasttime.data.Counter
import it.marino8383.lasttime.data.bellEarlinessMs
import it.marino8383.lasttime.data.bellLatenessMs
import it.marino8383.lasttime.data.keepRhythmNextBell
import it.marino8383.lasttime.formatDurationTwoParts
import it.marino8383.lasttime.formatRingTime

/**
 * Fatto/↺ su una ricorrente scaduta da molto: si chiede cosa fare della campanella,
 * proponendo come default il ritmo scelto in fase di setup, con gli orari calcolati.
 */
@Composable
fun LateBellDialog(
    counter: Counter,
    now: Long,
    onDismiss: () -> Unit,
    onChoose: (LateBellChoice) -> Unit,
) {
    val step = (counter.bellMinutes ?: 0) * 60_000
    val keepAt = keepRhythmNextBell(counter.nextBellAtMs ?: now, step, now)
    val fromNowAt = now + step
    val earliness = counter.bellEarlinessMs(now)
    val lateness = counter.bellLatenessMs(now) ?: 0

    var choice by remember {
        mutableStateOf(if (counter.bellMode == "FIXED") LateBellChoice.KEEP_RHYTHM else LateBellChoice.FROM_NOW)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            SheetTitle(
                title = "↺ Riparti",
                onClose = onDismiss,
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column {
                Text(
                    if (earliness != null)
                        "Hai anticipato di ${formatDurationTwoParts(earliness)} rispetto alla campanella. E la prossima?"
                    else
                        "La campanella è suonata ${formatDurationTwoParts(lateness)} fa. E la prossima?",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                ChoiceRow(
                    selected = choice == LateBellChoice.KEEP_RHYTHM,
                    title = "Mantieni il ritmo",
                    subtitle = "prossima ${formatRingTime(keepAt)}",
                    onClick = { choice = LateBellChoice.KEEP_RHYTHM },
                )
                // Ha senso solo in anticipo: non e' un Fatto adesso, e' "non ancora" — il
                // round resta aperto e si chiude da solo quando arriva la campanella,
                // come il chip "alla campanella" di Riparti avanzato quando e' nel futuro.
                if (earliness != null) {
                    ChoiceRow(
                        selected = choice == LateBellChoice.SCHEDULE,
                        title = "⏲ Aspetta la campanella",
                        subtitle = "si resetta da solo alle ${formatRingTime(counter.nextBellAtMs ?: now)}",
                        onClick = { choice = LateBellChoice.SCHEDULE },
                    )
                }
                ChoiceRow(
                    selected = choice == LateBellChoice.FROM_NOW,
                    title = "Riparti da adesso",
                    subtitle = "prossima ${formatRingTime(fromNowAt)}",
                    onClick = { choice = LateBellChoice.FROM_NOW },
                )
                ChoiceRow(
                    selected = choice == LateBellChoice.DISABLE,
                    title = "Disattiva la campanella 🔕",
                    subtitle = "il timer riparte senza avvisi",
                    onClick = { choice = LateBellChoice.DISABLE },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoose(choice) }) {
                Text(if (choice == LateBellChoice.SCHEDULE) "Programma" else "Riparti")
            }
        },
        dismissButton = {},
    )
}

@Composable
private fun ChoiceRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Column {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
