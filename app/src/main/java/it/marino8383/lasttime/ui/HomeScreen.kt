package it.marino8383.lasttime.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
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
import it.marino8383.lasttime.AppSettings
import it.marino8383.lasttime.BuildConfig
import it.marino8383.lasttime.CountersViewModel
import it.marino8383.lasttime.ViewMode
import it.marino8383.lasttime.bellLabel
import it.marino8383.lasttime.data.Counter
import it.marino8383.lasttime.data.bellLateThreshold
import it.marino8383.lasttime.data.bellLatenessMs
import it.marino8383.lasttime.formatDateTime
import it.marino8383.lasttime.formatClock
import it.marino8383.lasttime.formatDurationTwoParts
import it.marino8383.lasttime.formatRingTime
import it.marino8383.lasttime.notif.AlarmScheduler
import it.marino8383.lasttime.sync.Cloud
import it.marino8383.lasttime.sync.CloudState
import it.marino8383.lasttime.sync.Groups
import it.marino8383.lasttime.sync.SyncEngine
import it.marino8383.lasttime.sync.SyncStatus
import it.marino8383.lasttime.sync.SyncWorker
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
    val archived by vm.archived.collectAsStateWithLifecycle()
    val roundSummaries by vm.roundSummaries.collectAsStateWithLifecycle()
    val groupLabels by vm.groupLabels.collectAsStateWithLifecycle()
    val syncStato by SyncStatus.stato.collectAsStateWithLifecycle()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    // Il solo passare del tempo puo' rendere vecchio l'allineamento, anche senza che
    // succeda niente: ogni mezzo minuto lo stato va riconsiderato.
    LaunchedEffect(now / 30_000) { SyncStatus.ricalcola() }

    val context = LocalContext.current
    val cloud by Cloud.state.collectAsStateWithLifecycle()
    LaunchedEffect(cloud) {
        if (cloud is CloudState.Ready) {
            SyncEngine.start(context)
            SyncWorker.refresh(context)
            vm.refreshGroupLabels()
        }
    }

    var flipMode by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showArchive by remember { mutableStateOf(false) }
    var archiveTarget by remember { mutableStateOf<Counter?>(null) }
    var historyTarget by remember { mutableStateOf<Counter?>(null) }
    var editTarget by remember { mutableStateOf<Counter?>(null) }
    var deleteTarget by remember { mutableStateOf<Counter?>(null) }
    var restartTarget by remember { mutableStateOf<Counter?>(null) }
    var advancedTarget by remember { mutableStateOf<Counter?>(null) }
    var lateBellTarget by remember { mutableStateOf<Counter?>(null) }
    var bellTarget by remember { mutableStateOf<Counter?>(null) }
    var shareTarget by remember { mutableStateOf<Counter?>(null) }

    BackHandler(enabled = showArchive) { showArchive = false }

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
            // niente FAB in vista tabellone (v4) né in archivio (v24)
            if (!flipMode && !showArchive) {
                FloatingActionButton(
                    onClick = { showAdd = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Nuovo contatore")
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (showArchive) {
                ArchiveScreen(
                    counters = archived,
                    summaries = roundSummaries,
                    now = now,
                    onBack = { showArchive = false },
                    onHistory = { historyTarget = it },
                    onResume = { vm.resumeCounter(it) },
                    onDelete = { deleteTarget = it },
                )
                return@Column
            }
            Header(
                flipMode = flipMode,
                onFlip = { flipMode = !flipMode },
                onOptions = { showOptions = true },
                onDiagnostics = { showDiagnostics = true },
                onArchive = { showArchive = true },
                // l'indicatore esiste solo se c'e' davvero qualcosa di condiviso
                syncStato = syncStato.takeIf { counters.any { c -> c.sharedGroupId != null } },
                onSync = { vm.syncNow() },
            )
            if (flipMode) {
                FlipView(
                    counters = counters,
                    now = now,
                    onBoardDoubleTap = { restartTarget = it },
                )
            } else if (counters.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nessun contatore.\nTocca + per crearne uno.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 120.dp)) {
                    items(counters, key = { it.id }) { counter ->
                        // swipe destra = elimina, sinistra = archivia, entrambi con conferma (v23/v24)
                        SwipeableCard(
                            onSwipeDelete = { deleteTarget = counter },
                            onSwipeArchive = { archiveTarget = counter },
                        ) {
                            CounterCard(
                                counter = counter,
                                now = now,
                                sharedWith = counter.sharedGroupId?.let { groupLabels[it] },
                                onCycleView = { vm.cycleViewMode(counter) },
                                onHistory = { historyTarget = counter },
                                onRestart = { restartTarget = counter },
                                onAdvancedRestart = { advancedTarget = counter },
                                onEdit = { editTarget = counter },
                                onBell = { bellTarget = counter },
                                onShare = { shareTarget = counter },
                                onDelete = { deleteTarget = counter },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showOptions) {
        OptionsSheet(
            onDismiss = { showOptions = false },
            onJoin = { code, myName, onDone ->
                vm.joinGroup(code, myName) { esito ->
                    onDone(
                        when (esito) {
                            is Groups.JoinResult.Ok ->
                                "✅ Sei nel gruppo. I timer condivisi compaiono fra qualche secondo."
                            Groups.JoinResult.CodeNotFound -> "⚠️ Codice non trovato."
                            Groups.JoinResult.Expired -> "⚠️ Codice scaduto: fattene dare uno nuovo."
                            is Groups.JoinResult.Failed -> "⚠️ " + esito.message
                        }
                    )
                }
            },
        )
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
                vm.editCounter(counter, name, startMs)
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
                    val threshold = counter.bellMinutes
                        ?.let { bellLateThreshold(it * 60_000, AppSettings.latePercent(context)) }
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

    advancedTarget?.let { target ->
        val counter = counters.firstOrNull { it.id == target.id } ?: target
        AdvancedRestartSheet(
            counter = counter,
            onDismiss = { advancedTarget = null },
            onRestartAt = { at ->
                vm.restartAt(counter, at)
                advancedTarget = null
            },
            onSchedule = { at ->
                vm.scheduleReset(counter, at)
                advancedTarget = null
            },
            onCancelSchedule = { vm.cancelScheduledReset(counter) },
            onAddMissed = { at -> vm.addMissedEvent(counter, at) },
            // evento con orario: round chiuso lì e timer rifasato, la maschera resta aperta
            onAddTimedEvent = { at -> vm.restartAt(counter, at) },
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

    archiveTarget?.let { counter ->
        AlertDialog(
            onDismissRequest = { archiveTarget = null },
            title = { Text("📦 Archiviare il timer?") },
            text = {
                Text(
                    "“${counter.name}” finisce in archivio: il round in corso viene salvato " +
                        "nello storico e il timer si ferma. Puoi riprenderlo quando vuoi."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.archiveCounter(counter)
                    archiveTarget = null
                }) { Text("Sì, archivia") }
            },
            dismissButton = {
                TextButton(onClick = { archiveTarget = null }) { Text("No") }
            },
        )
    }

    shareTarget?.let { target ->
        val counter = counters.firstOrNull { it.id == target.id } ?: target
        ShareSheet(
            counter = counter,
            onDismiss = { shareTarget = null },
            onShare = { myName, groupId, onDone -> vm.shareCounter(counter, myName, groupId, onDone) },
            onGroups = { onDone -> vm.myGroups(onDone) },
            onNewInvite = { groupId, onDone -> vm.newInvite(groupId, onDone) },
            onUnshare = { vm.unshare(counter) },
            onNotifyOnRemote = { vm.setNotifyOnRemote(counter, it) },
            onMembers = { groupId, onDone -> vm.membersOf(groupId, onDone) },
        )
    }

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
private fun Header(
    flipMode: Boolean,
    onFlip: () -> Unit,
    onOptions: () -> Unit,
    onDiagnostics: () -> Unit,
    onArchive: () -> Unit,
    syncStato: SyncStatus.Stato?,
    onSync: () -> Unit,
) {
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
        syncStato?.let { stato ->
            val vecchio = stato is SyncStatus.Stato.Vecchio
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (vecchio) MaterialTheme.colorScheme.errorContainer else PrimaryContainer,
                contentColor = if (vecchio) OnErrorContainer else OnPrimaryContainer,
                modifier = Modifier.clickable { onSync() },
            ) {
                Text(
                    when (stato) {
                        is SyncStatus.Stato.InCorso -> "⟳"
                        is SyncStatus.Stato.Aggiornato -> "✓ ${formatClock(stato.atMs)}"
                        is SyncStatus.Stato.Vecchio ->
                            stato.atMs?.let { "⚠ ${formatClock(it)}" } ?: "⚠ mai"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        IconButton(onClick = onFlip) {
            Text(if (flipMode) "🗂" else "🚉", fontSize = 17.sp)
        }
        IconButton(onClick = onArchive) {
            Text("📦", fontSize = 17.sp)
        }
        IconButton(onClick = onDiagnostics) {
            Text("🩺", fontSize = 17.sp)
        }
        IconButton(onClick = onOptions) {
            Text("⚙️", fontSize = 17.sp)
        }
    }
}

@Composable
private fun CounterCard(
    counter: Counter,
    now: Long,
    sharedWith: String?,
    onCycleView: () -> Unit,
    onHistory: () -> Unit,
    onRestart: () -> Unit,
    onAdvancedRestart: () -> Unit,
    onEdit: () -> Unit,
    onBell: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    // Allineato al secondo del timer: anche il countdown campanella deriva da qui,
    // così i due conteggi scattano nello stesso istante (se scala uno scala l'altro)
    val elapsed = (now - counter.startMs).coerceAtLeast(0) / 1000 * 1000
    val snoozePending = counter.snoozeUntilMs?.takeIf { it > now }
    // Scaduta = la soglia è passata. Non dipende dalla campanella accesa: è un fatto
    // dell'orologio, e va letto anche da chi ha scelto di non farsi notificare.
    val scaduta = counter.nextBellAtMs?.let { it <= now } == true && snoozePending == null
    // L'allarme visivo vale anche a campanella spenta: spegnere la notifica significa
    // "non svegliarmi", non "nascondimi che il giro è scaduto".
    val over = scaduta
    // Prossimo squillo effettivo: rinvio pendente, oppure squillo programmato futuro
    val nextRing = when {
        counter.bellMinutes == null -> null
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
                    // da quanto è sforata, sulla stessa griglia dei secondi del timer
                    val overdue = if (scaduta) counter.nextBellAtMs
                        ?.let { (counter.startMs + elapsed - it).coerceAtLeast(0) } else null
                    val label = bellLabel(bell) + if (counter.bellRepeat) " ↻" else ""
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            scaduta -> MaterialTheme.colorScheme.primary
                            muted -> MaterialTheme.colorScheme.surfaceContainerHigh
                            else -> PrimaryContainer
                        },
                        contentColor = when {
                            scaduta -> MaterialTheme.colorScheme.onPrimary
                            muted -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> OnPrimaryContainer
                        },
                        // Tap sul chip: valore impostato -> countdown -> orario di squillo
                        modifier = Modifier.clickable { chipMode = (chipMode + 1) % 3 },
                    ) {
                        Text(
                            when {
                                chipMode == 1 && remaining != null -> "⏰ ${formatDurationTwoParts(remaining)}"
                                chipMode == 1 && overdue != null -> "⏰ +${formatDurationTwoParts(overdue)}"
                                chipMode == 2 && nextRing != null -> "🕐 ${formatRingTime(nextRing)}"
                                chipMode == 2 && counter.nextBellAtMs != null ->
                                    "🕐 ${formatRingTime(counter.nextBellAtMs)}"
                                // rinvio attivo: countdown in evidenza senza dover toccare
                                snoozePending != null && remaining != null ->
                                    "⏰ ${formatDurationTwoParts(remaining)}"
                                else -> "${if (muted) "🔕" else "🔔"} $label"
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
            counter.sharedGroupId?.let {
                Text(
                    "👥 condiviso con ${sharedWith ?: "…"}",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            counter.scheduledResetMs?.takeIf { it > now }?.let {
                Text(
                    "⏲ reset programmato ${formatRingTime(it)}",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // sforata da quanto: timer che avanza, sincronizzato ai secondi del contatore.
            // Si vede anche a campanella spenta: è l'informazione che serve per decidere.
            if (scaduta) {
                counter.nextBellAtMs?.let { deadline ->
                    val overdueLine = (counter.startMs + elapsed - deadline).coerceAtLeast(0)
                    Text(
                        "${if (counter.bellEnabled) "🔔" else "🔕"} sforata da ${formatDurationTwoParts(overdueLine)}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnErrorContainer,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

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
                // 👥 acceso = condiviso: sulla card si vede a colpo d'occhio
                IconButton(onClick = onShare) {
                    Text(
                        "👥",
                        fontSize = 15.sp,
                        color = if (counter.sharedGroupId != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // ↺: tap = riparti con conferma, doppio tap = riparti avanzato (v25)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .pointerInput(counter.id) {
                            detectTapGestures(
                                onTap = { onRestart() },
                                onDoubleTap = { onAdvancedRestart() },
                            )
                        },
                ) {
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
