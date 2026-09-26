package it.marino8383.lasttime.ui

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
import it.marino8383.lasttime.sync.Updater
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
    joinCode: String? = null,
    onJoinHandled: () -> Unit = {},
) {
    val counters by vm.counters.collectAsStateWithLifecycle()
    val archived by vm.archived.collectAsStateWithLifecycle()
    val hiddenCounters by vm.hidden.collectAsStateWithLifecycle()
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
    var showHidden by remember { mutableStateOf(false) }
    var archiveTarget by remember { mutableStateOf<Counter?>(null) }
    var historyTarget by remember { mutableStateOf<Counter?>(null) }
    var editTarget by remember { mutableStateOf<Counter?>(null) }
    var deleteTarget by remember { mutableStateOf<Counter?>(null) }
    var restartTarget by remember { mutableStateOf<Counter?>(null) }
    var advancedTarget by remember { mutableStateOf<Counter?>(null) }
    var lateBellTarget by remember { mutableStateOf<Counter?>(null) }
    var bellTarget by remember { mutableStateOf<Counter?>(null) }
    var shareTarget by remember { mutableStateOf<Counter?>(null) }
    var resumeTarget by remember { mutableStateOf<Counter?>(null) }
    var staleTarget by remember { mutableStateOf<CountersViewModel.FreshCheck?>(null) }
    // Un controllo al giorno, all'apertura. Una versione nuova non esce quattro volte all'ora.
    var novita by remember { mutableStateOf<Updater.Novita?>(null) }
    LaunchedEffect(Unit) { novita = Updater.controlla(context) }

    BackHandler(enabled = showArchive) { showArchive = false }
    BackHandler(enabled = showHidden) { showHidden = false }

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
            // niente FAB in vista tabellone (v4), archivio (v24) o nascosti (v28)
            if (!flipMode && !showArchive && !showHidden) {
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
                    onResume = { resumeTarget = it },
                    onDelete = { deleteTarget = it },
                )
                return@Column
            }
            if (showHidden) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp, 16.dp, 20.dp, 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { showHidden = false }) {
                        Text("←", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(
                        "🙈 Nascosti",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (hiddenCounters.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Niente di nascosto.\nSull'occhio 🙈 di un contatore lo togli dai piedi\nsenza fermarlo.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 40.dp)) {
                        items(hiddenCounters, key = { it.id }) { counter ->
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
                                    onToggleHidden = { vm.setHidden(counter, !counter.hidden) },
                                )
                            }
                        }
                    }
                }
                return@Column
            }
            Header(
                flipMode = flipMode,
                onFlip = { flipMode = !flipMode },
                onOptions = { showOptions = true },
                onArchive = { showArchive = true },
                onHidden = { showHidden = true },
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
                                onToggleHidden = { vm.setHidden(counter, !counter.hidden) },
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
            onDiagnostics = {
                showOptions = false
                showDiagnostics = true
            },
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

    // Link d'invito toccato: conferma esplicita prima di entrare nel gruppo
    joinCode?.let { code ->
        JoinInviteDialog(
            code = code,
            onDismiss = onJoinHandled,
            onJoin = { myName, onDone ->
                vm.joinGroup(code, myName) { esito ->
                    onDone(
                        when (esito) {
                            is Groups.JoinResult.Ok ->
                                "✅ Sei nel gruppo. I timer condivisi compaiono fra qualche secondo."
                            Groups.JoinResult.CodeNotFound -> "⚠️ Codice non trovato."
                            Groups.JoinResult.Expired -> "⚠️ Codice scaduto: fattene mandare uno nuovo."
                            is Groups.JoinResult.Failed -> "⚠️ " + esito.message
                        }
                    )
                }
            },
        )
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
        val roundsFlow = remember(target.id, counter.historyFromMs) { vm.roundsFor(counter) }
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

    restartTarget?.let { target ->
        val counter = counters.firstOrNull { it.id == target.id } ?: target
        AlertDialog(
            onDismissRequest = { restartTarget = null },
            title = { Text("Riparti") },
            text = { Text("Vuoi far ripartire il timer “${counter.name}”? Il round corrente verrà salvato nello storico.") },
            confirmButton = {
                TextButton(onClick = {
                    restartTarget = null
                    // Su un condiviso prima si verifica: potrebbe averlo già fatto l'altro
                    vm.checkBeforeRestart(counter) { esito ->
                        if (esito.moved) {
                            staleTarget = esito
                            return@checkBeforeRestart
                        }
                        val fresco = esito.counter
                        // Ricorrente suonata da molto: prima di ripartire si chiede della campanella
                        val lateness = fresco.bellLatenessMs(System.currentTimeMillis())
                        val threshold = fresco.bellMinutes
                            ?.let { bellLateThreshold(it * 60_000, AppSettings.latePercent(context)) }
                        if (lateness != null && threshold != null && lateness > threshold) {
                            lateBellTarget = fresco
                        } else {
                            vm.restart(fresco)
                        }
                    }
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
            onCorrectLast = { at ->
                vm.correctLastRestart(counter, at) { esito ->
                    Toast.makeText(context, esito, Toast.LENGTH_SHORT).show()
                }
                advancedTarget = null
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

    novita?.let { nuova ->
        AlertDialog(
            onDismissRequest = { novita = null },
            title = { Text("⬆️ Aggiornamento disponibile") },
            text = {
                Column {
                    Text("C'è la versione ${nuova.versionName}. Tu hai la ${BuildConfig.VERSION_NAME}.")
                    if (nuova.notes.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            nuova.notes,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Si installa sopra questa, senza perdere timer né storico. " +
                            "Android chiederà conferma prima di installare.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Updater.scaricaEInstalla(context, nuova)
                    novita = null
                }) { Text("Aggiorna", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { novita = null }) { Text("Più tardi") }
            },
        )
    }

    staleTarget?.let { esito ->
        val counter = esito.counter
        AlertDialog(
            onDismissRequest = { staleTarget = null },
            title = { Text("⚠️ Era già stato fatto ripartire") },
            text = {
                Column {
                    // Il "quanto fa" in testa: è la cosa che serve per decidere, molto
                    // più dell'orario esatto. Non c'è nessuna soglia sotto — si avvisa
                    // ogni volta che il valore è cambiato rispetto a quello che vedevi.
                    val da = formatDurationTwoParts(System.currentTimeMillis() - counter.startMs)
                    Text(
                        (esito.movedBy?.let { "$it l'ha" } ?: "Qualcun altro l'ha") +
                            " fatto ripartire $da fa, alle ${formatClock(counter.startMs)}, " +
                            "e sul tuo telefono non era ancora arrivato."
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Farlo ripartire di nuovo aggiungerebbe un secondo evento allo storico, " +
                            "a $da di distanza dal primo.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { staleTarget = null }) {
                    Text("Va bene, era già fatto", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.restart(counter)
                    staleTarget = null
                }) { Text("Riparti comunque", color = MaterialTheme.colorScheme.error) }
            },
        )
    }

    resumeTarget?.let { counter ->
        // Di default il giro precedente si chiude: riprendere un timer archiviato è
        // ricominciare un ciclo. Chi vuole la continuità la chiede esplicitamente.
        var tieniStorico by remember(counter.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { resumeTarget = null },
            title = { Text("▶️ Riprendere il timer?") },
            text = {
                Column {
                    Text(
                        "“${counter.name}” torna fra gli attivi e parte un round nuovo da adesso." +
                            if (counter.sharedGroupId != null)
                                " Essendo condiviso, torna in linea per tutti."
                            else ""
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Tieni lo storico del giro precedente",
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Spento, storico e statistiche ripartono da adesso",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = tieniStorico,
                            onCheckedChange = { tieniStorico = it },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.resumeCounter(counter, keepHistory = tieniStorico)
                    resumeTarget = null
                }) { Text("Riprendi") }
            },
            dismissButton = {
                TextButton(onClick = { resumeTarget = null }) { Text("No") }
            },
        )
    }

    archiveTarget?.let { counter ->
        AlertDialog(
            onDismissRequest = { archiveTarget = null },
            title = { Text("📦 Archiviare il timer?") },
            text = {
                Column {
                    Text(
                        "“${counter.name}” finisce in archivio: il round in corso viene " +
                            "salvato nello storico e il timer si ferma. Puoi riprenderlo quando vuoi.",
                    )
                    // Un ciclo finisce insieme: il gruppo resta, così alla prossima volta
                    // basta riesumarlo senza rifare inviti.
                    if (counter.sharedGroupId != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Essendo condiviso si ferma per tutti, e resta pronto da " +
                                "riprendere insieme quando servirà.",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
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
    onArchive: () -> Unit,
    onHidden: () -> Unit,
    syncStato: SyncStatus.Stato?,
    onSync: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(20.dp, 20.dp, 8.dp, 10.dp),
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
            Spacer(Modifier.width(2.dp))
        }
        // Icone vettoriali uniformi (niente emoji miste): 40dp invece dei 48dp di
        // default, altrimenti su un telefono stretto il titolo finisce schiacciato
        // a sinistra — la diagnostica e' stata spostata dentro Opzioni apposta.
        HeaderIcon(if (flipMode) Icons.Filled.ViewList else Icons.Filled.GridView, "Tabellone", onFlip)
        HeaderIcon(Icons.Filled.Archive, "Archivio", onArchive)
        HeaderIcon(Icons.Filled.VisibilityOff, "Nascosti", onHidden)
        HeaderIcon(Icons.Filled.Settings, "Opzioni", onOptions)
    }
}

@Composable
private fun HeaderIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            icon, contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(21.dp),
        )
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
    onToggleHidden: () -> Unit,
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

    // pointerInput riavvia il suo blocco solo quando cambia la chiave, e qui la chiave è
    // l'id, che non cambia mai. Senza questi, i gestori continuerebbero a chiamare le
    // lambda catturate alla prima composizione — cioè a consegnare il contatore com'era
    // allora. rememberUpdatedState fa sì che puntino sempre all'ultima versione.
    val restartOra by rememberUpdatedState(onRestart)
    val advancedOra by rememberUpdatedState(onAdvancedRestart)

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
                detectTapGestures(onDoubleTap = { restartOra() })
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
                    // Le viste sono quelle che hanno davvero qualcosa da dire: senza una
                    // scadenza programmata, countdown e orario non esistono. Ciclare su tre
                    // modalità fisse faceva sembrare il chip rotto — toccavi e non cambiava
                    // niente, perché due delle tre ricadevano sullo stesso testo.
                    val orario = nextRing ?: counter.nextBellAtMs
                    val viste = buildList {
                        // con un rinvio in corso il countdown viene per primo: è l'informazione
                        // del momento, e si vede senza dover toccare
                        if (snoozePending != null && remaining != null) {
                            add("⏰ ${formatDurationTwoParts(remaining)}")
                        }
                        add("${if (muted) "🔕" else "🔔"} $label")
                        if (snoozePending == null) {
                            remaining?.let { add("⏰ ${formatDurationTwoParts(it)}") }
                            overdue?.let { add("⏰ +${formatDurationTwoParts(it)}") }
                        }
                        orario?.let { add("🕐 ${formatRingTime(it)}") }
                    }
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
                        // Cliccabile solo se c'è più di una vista: un chip che non reagisce
                        // è meglio di un chip che reagisce senza cambiare nulla.
                        modifier = if (viste.size > 1) {
                            Modifier.clickable { chipMode = (chipMode + 1) % viste.size }
                        } else {
                            Modifier
                        },
                    ) {
                        Text(
                            viste[chipMode % viste.size],
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
                    Icon(
                        Icons.Filled.History, contentDescription = "Storico",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                // Acceso (colore primario) = condiviso: sulla card si vede a colpo d'occhio
                IconButton(onClick = onShare) {
                    Icon(
                        Icons.Filled.Group, contentDescription = "Condividi",
                        tint = if (counter.sharedGroupId != null) MaterialTheme.colorScheme.primary
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
                                onTap = { restartOra() },
                                onDoubleTap = { advancedOra() },
                            )
                        },
                ) {
                    Icon(
                        Icons.Filled.Refresh, contentDescription = "Riparti",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Nasconde (continua a contare, sparisce da lista/tabellone e notifiche);
                // mostra di nuovo se e' gia' nascosto — vedi Counter.hidden. L'eliminazione
                // non ha piu' un'icona qui: si fa con lo swipe, come sull'archivio.
                IconButton(onClick = onToggleHidden) {
                    Icon(
                        if (counter.hidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (counter.hidden) "Mostra" else "Nascondi",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
