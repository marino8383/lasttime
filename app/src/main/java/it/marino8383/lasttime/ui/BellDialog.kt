package it.marino8383.lasttime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.marino8383.lasttime.data.Counter
import it.marino8383.lasttime.formatRingTime

private enum class BellUnit(val label: String, val minutes: Long) {
    MINUTES("minuti", 1),
    HOURS("ore", 60),
    DAYS("giorni", 60 * 24),
}

private fun sectionLabel(text: String) = text

/**
 * Dialog campanella. Singola (suona una volta e si spegne) o ricorrente (si riarma
 * ogni X fino a disattivazione, con ritmo "dal Fatto" o fisso). L'aggancio ("da quando")
 * è automatico quando è ovvio, chiesto esplicitamente solo nel caso ambiguo.
 */
@Composable
fun BellDialog(
    counter: Counter,
    onDismiss: () -> Unit,
    onSave: (
        bellMinutes: Long?,
        bellRepeat: Boolean,
        bellMode: String,
        bellEnabled: Boolean,
        nextBellAtMs: Long?,
    ) -> Unit,
) {
    val existing = counter.bellMinutes
    val initialUnit = when {
        existing == null -> BellUnit.HOURS
        existing % BellUnit.DAYS.minutes == 0L -> BellUnit.DAYS
        existing % BellUnit.HOURS.minutes == 0L -> BellUnit.HOURS
        else -> BellUnit.MINUTES
    }
    var unit by remember { mutableStateOf(initialUnit) }
    var amountText by remember {
        mutableStateOf(existing?.let { (it / initialUnit.minutes).toString() } ?: "")
    }
    var repeat by remember { mutableStateOf(if (existing != null) counter.bellRepeat else false) }
    var mode by remember { mutableStateOf(counter.bellMode) }
    var enabled by remember { mutableStateOf(if (existing != null) counter.bellEnabled else true) }
    var anchorFromStart by remember { mutableStateOf(true) }

    val amount = amountText.toLongOrNull()?.takeIf { it > 0 }
    val bellMinutes = amount?.times(unit.minutes)

    // "Da quando": ovvio se la scadenza da inizio timer è già passata (=> da adesso)
    // o se il timer è appena partito (le due opzioni coincidono); altrimenti si chiede.
    val now = System.currentTimeMillis()
    val stepMs = bellMinutes?.times(60_000)
    val fromStart = stepMs?.let { counter.startMs + it }
    val fromNow = stepMs?.let { now + it }
    val startExpired = fromStart != null && fromStart <= now
    val ambiguous = fromStart != null && !startExpired && (now - counter.startMs) >= 60_000
    val nextBellAt = when {
        stepMs == null -> null
        startExpired -> fromNow
        ambiguous && !anchorFromStart -> fromNow
        else -> fromStart
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔔 Avvisami dopo") },
        text = {
            Column {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter(Char::isDigit) },
                    label = { Text("Quanto") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BellUnit.entries.forEach { u ->
                        FilterChip(
                            selected = unit == u,
                            onClick = { unit = u },
                            label = { Text(u.label) },
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    sectionLabel("TIPO"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !repeat,
                        onClick = { repeat = false },
                        label = { Text("Singola", fontSize = 11.sp) },
                    )
                    FilterChip(
                        selected = repeat,
                        onClick = { repeat = true },
                        label = { Text("Ricorrente ↻", fontSize = 11.sp) },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (repeat) "Si riarma ogni volta, finché non la spegni."
                    else "Suona una volta e si spegne (i rimandi valgono comunque).",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (repeat) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        sectionLabel("DOPO IL “FATTO”"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = mode == "INTERVAL",
                            onClick = { mode = "INTERVAL" },
                            label = { Text("Riparti da adesso", fontSize = 11.sp) },
                        )
                        FilterChip(
                            selected = mode == "FIXED",
                            onClick = { mode = "FIXED" },
                            label = { Text("Mantieni il ritmo", fontSize = 11.sp) },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (mode == "FIXED")
                            "Es. pillola ogni 8 h: se confermi in ritardo di 1 h, la prossima suona dopo 7 h."
                        else
                            "La prossima parte da quando premi Fatto (o ↺).",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (ambiguous) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        sectionLabel("DA QUANDO"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = anchorFromStart,
                            onClick = { anchorFromStart = true },
                            label = { Text("Da inizio timer", fontSize = 11.sp) },
                        )
                        FilterChip(
                            selected = !anchorFromStart,
                            onClick = { anchorFromStart = false },
                            label = { Text("Da adesso", fontSize = 11.sp) },
                        )
                    }
                }

                nextBellAt?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "🔔 suonerà ${formatRingTime(it)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (existing != null) {
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
                enabled = bellMinutes != null,
                onClick = { onSave(bellMinutes, repeat, mode, enabled, nextBellAt) },
            ) { Text("Salva") }
        },
        dismissButton = {
            if (existing != null) {
                TextButton(onClick = { onSave(null, false, mode, true, null) }) {
                    Text("Rimuovi", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Annulla") }
            }
        },
    )
}
