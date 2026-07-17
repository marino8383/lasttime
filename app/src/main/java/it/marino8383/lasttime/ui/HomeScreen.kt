package it.marino8383.lasttime.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.marino8383.lasttime.BuildConfig
import it.marino8383.lasttime.CountersViewModel
import it.marino8383.lasttime.ViewMode
import it.marino8383.lasttime.bellLabel
import it.marino8383.lasttime.data.Counter
import it.marino8383.lasttime.data.bellLateThreshold
import it.marino8383.lasttime.data.bellLatenessMs
import it.marino8383.lasttime.formatDateTime
import it.marino8383.lasttime.formatDurationTwoParts
import it.marino8383.lasttime.formatRingTime
import it.marino8383.lasttime.notif.AlarmScheduler
import it.marino8383.lasttime.timeParts
import it.marino8383.lasttime.ui.theme.OnErrorContainer
import it.marino8383.lasttime.ui.theme.OnPrimaryContainer
import it.marino8383.lasttime.ui.theme.PrimaryContainer
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    vm: CountersViewModel,
    snoozeCounterId: Long? = null,
    onSnoozeHandled: () -> Unit = {},
) {
    val counters by vm.counters.collectAsStateWithLifecycle()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }

    var showAdd by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var historyTarget by remember { mutableStateOf<Counter?>(null) }
    var editTarget by remember { mutableStateOf<Counter?>(null) }
    var deleteTarget by remember { mutableStateOf<Counter?>(null) }
    var restartTarget by remember { mutableStateOf<Counter?>(null) }
    var lateBellTarget by remember { mutableStateOf<Counter?>(null) }
    var bellTarget by remember { mutableStateOf<Counter?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Text(
                "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})  ·  build ${BuildConfig.BUILD_TIME}  ·  powered by Marino8383",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 6.dp),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Nuovo contatore")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Header(onDiagnostics = { showDiagnostics = true })
            if (counters.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nessun contatore.\nTocca + per crearne uno.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 120.dp)) {
                    items(counters, key = { it.id }) { counter ->
                        CounterCard(
                            counter = counter,
                            now = now,
                            onCycleView = { vm.cycleViewMode(counter) },
                            onHistory = { historyTarget = counter },
                            onRestart = { restartTarget = counter },
                            onEdit = { editTarget = counter },
                            onBell = { bellTarget = counter },
                            onDelete = { deleteTarget = counter },
                        )
                    }
                }
            }
        }
    }

    if (showDiagnostics) {
        DiagnosticsSheet(onDismiss = { showDiagnostics = false })
    }

    // "Rimanda" dalla notifica: maschera di snooze appena i contatori sono caricati
    snoozeCounterId?.let { id ->
        counters.firstOrNull { it.id == id }?.let { counter ->
            SnoozeDialog(
                counter = counter,
                onDismiss = onSnoozeHandled,
                onSave = { minutes ->
                    vm.snooze(counter, minutes)
                    onSnoozeHandled()
                },
            )
        }
    }

    historyTarget?.let { target ->
        // Prende la versione aggiornata del contatore (es. dopo un restart a sheet aperto)
        val counter = counters.firstOrNull { it.id == target.id } ?: target
        val roundsFlow = remember(target.id) { vm.roundsFor(target.id) }
        val rounds by roundsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
        HistorySheet(
            counter = counter,
            now = now,
            rounds = rounds,
            onDismiss = { historyTarget = null },
        )
    }

    if (showAdd) {
        EditCounterSheet(
            counter = null,
            onDismiss = { showAdd = false },
            onSave = { name, startMs ->
                vm.addCounter(name, startMs, bellMinutes = null)
                showAdd = false
            },
        )
    }

    editTarget?.let { counter ->
        EditCounterSheet(
            counter = counter,
            onDismiss = { editTarget = null },
            onSave = { name, startMs ->
                vm.updateCounter(counter.copy(name = name.trim(), startMs = startMs))
                editTarget = null
            },
        )
    }

    restartTarget?.let { counter ->
        AlertDialog(
            onDismissRequest = { restartTarget = null },
            title = { Text("Riparti") },
            text = { Text("Vuoi far ripartire il timer “${counter.name}”? Il round corrente verrà salvato nello storico.") },
            confirmButton = {
                TextButton(onClick = {
                    // Ricorrente suonata da molto: prima di ripartire si chiede della campanella
                    val lateness = counter.bellLatenessMs(now)
                    val threshold = counter.bellMinutes?.let { bellLateThreshold(it * 60_000) }
                    if (lateness != null && threshold != null && lateness > threshold) {
                        lateBellTarget = counter
                    } else {
                        vm.restart(counter)
                    }
                    restartTarget = null
                }) { Text("Sì") }
            },
            dismissButton = {
                TextButton(onClick = { restartTarget = null }) { Text("No") }
            },
        )
    }

    lateBellTarget?.let { counter ->
        LateBellDialog(
            counter = counter,
            now = now,
            onDismiss = { lateBellTarget = null },
            onChoose = { choice ->
                vm.restartWithBellChoice(counter, choice)
                lateBellTarget = null
            },
        )
    }

    deleteTarget?.let { counter ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Elimina") },
            text = { Text("Vuoi eliminare il timer “${counter.name}” in modo permanente? Anche lo storico dei round andrà perso.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCounter(counter)
                    deleteTarget = null
                }) { Text("Sì, elimina", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("No") }
            },
        )
    }

    val context = LocalContext.current
    bellTarget?.let { counter ->
        BellDialog(
            counter = counter,
            onDismiss = { bellTarget = null },
            onSave = { minutes, repeat, mode, enabled, nextBellAt ->
                vm.updateCounter(
                    counter.copy(
                        bellMinutes = minutes,
                        bellRepeat = repeat,
                        bellMode = mode,
                        bellEnabled = enabled,
                        bellNotified = false,
                        snoozeUntilMs = null,
                        nextBellAtMs = nextBellAt,
                    )
                )
                if (minutes != null && enabled) AlarmScheduler.ensureExactAlarmPermission(context)
                bellTarget = null
            },
        )
    }
}

@Composable
private fun Header(onDiagnostics: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(20.dp, 20.dp, 20.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildAnnotatedString {
                append("Last ")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("Time") }
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDiagnostics) {
            Text("🩺", fontSize = 17.sp)
        }
    }
}

@Composable
private fun CounterCard(
    counter: Counter,
    now: Long,
    onCycleView: () -> Unit,
    onHistory: () -> Unit,
    onRestart: () -> Unit,
    onEdit: () -> Unit,
    onBell: () -> Unit,
    onDelete: () -> Unit,
) {
    // Allineato al secondo del timer: anche il countdown campanella deriva da qui,
    // così i due conteggi scattano nello stesso istante (se scala uno scala l'altro)
    val elapsed = (now - counter.startMs).coerceAtLeast(0) / 1000 * 1000
    val snoozePending = counter.snoozeUntilMs?.takeIf { it > now }
    // Sforato come da mockup: la campanella è suonata e non hai ancora fatto nulla
    // (Fatto la azzera, Rimanda apre un rinvio, Scarta la spegne)
    val over = counter.bellEnabled && counter.bellNotified && snoozePending == null
    // Prossimo squillo effettivo: rinvio pendente, oppure squillo programmato futuro
    val nextRing = when {
        counter.bellMinutes == null || !counter.bellEnabled -> null
        snoozePending != null -> snoozePending
        counter.nextBellAtMs?.let { it > now } == true -> counter.nextBellAtMs
        else -> null
    }
    // 0 = valore impostato, 1 = countdown, 2 = orario di squillo (tap sul chip per ciclare)
    var chipMode by remember(counter.id) { mutableStateOf(0) }

    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (over) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            // Doppio tap ovunque sulla card = restart con conferma (v20)
            .pointerInput(counter.id) {
                detectTapGestures(onDoubleTap = { onRestart() })
            },
    ) {
        Column(Modifier.padding(18.dp, 18.dp, 18.dp, 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    counter.name.uppercase(),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                counter.bellMinutes?.let { bell ->
                    val muted = !counter.bellEnabled
                    val remaining = nextRing?.let { (it - counter.startMs - elapsed).coerceAtLeast(0) }
                    val label = bellLabel(bell) + if (counter.bellRepeat) " ↻" else ""
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            muted -> MaterialTheme.colorScheme.surfaceContainerHigh
                            over -> MaterialTheme.colorScheme.primary
                            else -> PrimaryContainer
                        },
                        contentColor = when {
                            muted -> MaterialTheme.colorScheme.onSurfaceVariant
                            over -> MaterialTheme.colorScheme.onPrimary
                            else -> OnPrimaryContainer
                        },
                        // Tap sul chip: valore impostato -> countdown -> orario di squillo
                        modifier = Modifier.clickable { chipMode = (chipMode + 1) % 3 },
                    ) {
                        Text(
                            when {
                                muted -> "🔕 $label"
                                chipMode == 1 && remaining != null -> "⏰ ${formatDurationTwoParts(remaining)}"
                                chipMode == 2 && nextRing != null -> "🕐 ${formatRingTime(nextRing)}"
                                else -> "🔔 $label"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            val parts = timeParts(elapsed, ViewMode.from(counter.viewMode))
            Text(
                buildAnnotatedString {
                    parts.forEachIndexed { i, (value, label) ->
                        append(value)
                        withStyle(
                            SpanStyle(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        ) { append(label) }
                        if (i < parts.lastIndex) append(" ")
                    }
                },
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (over) OnErrorContainer else MaterialTheme.colorScheme.onSurface,
                style = TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier
                    .padding(top = 12.dp)
                    // Solo le cifre cambiano vista al tap (v21)
                    .clickable { onCycleView() },
            )
            Text(
                "dal ${formatDateTime(counter.startMs)}",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onHistory) {
                    Text("🕘", fontSize = 16.sp)
                }
                IconButton(onClick = onBell) {
                    Icon(
                        Icons.Filled.Notifications, contentDescription = "Campanella",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit, contentDescription = "Modifica",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRestart) {
                    Icon(
                        Icons.Filled.Refresh, contentDescription = "Riparti",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete, contentDescription = "Elimina",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
