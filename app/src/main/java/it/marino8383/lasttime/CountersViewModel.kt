package it.marino8383.lasttime

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import it.marino8383.lasttime.AppSettings
import androidx.lifecycle.viewModelScope
import it.marino8383.lasttime.data.Counter
import it.marino8383.lasttime.data.Round
import it.marino8383.lasttime.data.advanceToFuture
import it.marino8383.lasttime.data.add
import it.marino8383.lasttime.data.create
import it.marino8383.lasttime.data.save
import it.marino8383.lasttime.data.saveLocal
import it.marino8383.lasttime.data.restarted
import it.marino8383.lasttime.notif.AlarmScheduler
import it.marino8383.lasttime.notif.Notifications
import it.marino8383.lasttime.sync.Cloud
import it.marino8383.lasttime.sync.Groups
import it.marino8383.lasttime.sync.SyncEngine
import it.marino8383.lasttime.sync.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class CountersViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as LastTimeApp).db

    val counters = db.counterDao().activeCounters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val archived = db.counterDao().archivedCounters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Riepilogo round per contatore, per le card d'archivio. */
    val roundSummaries = db.roundDao().summaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** I round del ciclo in corso: quelli prima del confine non si contano più. */
    fun roundsFor(counter: Counter) =
        db.roundDao().roundsFor(counter.id, counter.historyFromMs ?: 0)

    fun addCounter(name: String, startMs: Long, bellMinutes: Long?) {
        viewModelScope.launch {
            // Data nel futuro -> clamp ad adesso (v16)
            val start = startMs.coerceAtMost(System.currentTimeMillis())
            db.counterDao().create(
                Counter(
                    name = name.trim(),
                    startMs = start,
                    bellMinutes = bellMinutes,
                    createdMs = System.currentTimeMillis(),
                )
            )
            AlarmScheduler.scheduleNext(getApplication())
        }
    }

    fun updateCounter(counter: Counter) {
        viewModelScope.launch {
            val clamped = counter.copy(startMs = counter.startMs.coerceAtMost(System.currentTimeMillis()))
            db.counterDao().save(clamped)
            AlarmScheduler.scheduleNext(getApplication())
        }
    }

    /**
     * Modifica dalla ✏️: nome e inizio del round. Se l'inizio si sposta la campanella va
     * rifatta, altrimenti resta agganciata a un round che non esiste più (era il bug per
     * cui "riparti da ieri alle 15" lasciava lo squillo all'orario vecchio).
     *
     * Eccezione voluta: una ricorrente in modalità FIXED non si tocca. Tenere il ritmo
     * a prescindere da quando confermi è esattamente il suo motivo di esistere — la
     * pillola delle 8 resta delle 8 anche se correggi l'ora in cui l'hai presa.
     */
    fun editCounter(counter: Counter, name: String, startMs: Long) {
        viewModelScope.launch {
            val start = startMs.coerceAtMost(System.currentTimeMillis())
            val step = counter.bellMinutes?.times(60_000)
            var updated = counter.copy(name = name.trim(), startMs = start)
            if (step != null && start != counter.startMs && counter.bellMode != "FIXED") {
                updated = updated.copy(
                    nextBellAtMs = start + step,
                    bellNotified = false,
                    snoozeUntilMs = null,
                )
            }
            db.counterDao().save(updated)
            Notifications.cancel(getApplication(), counter.id)
            AlarmScheduler.scheduleNext(getApplication())
        }
    }

    /**
     * Elimina il contatore da questo telefono. Se era condiviso lo toglie prima dal
     * gruppo, così sugli altri telefoni non resta una copia agganciata al vuoto: là
     * il timer sopravvive, con il suo storico, e torna autonomo. Non si cancella mai
     * niente per conto di qualcun altro.
     */
    fun deleteCounter(counter: Counter) {
        viewModelScope.launch {
            counter.sharedGroupId?.let { gruppo ->
                // Segnato prima ancora di provare: se il tentativo fallisce (rete assente
                // proprio ora) il prossimo giro di sync ritrova il contatore sul server e,
                // vedendo questo segno, ritenta la rimozione invece di farlo rientrare.
                AppSettings.addRemovedFromGroup(getApplication(), counter.uuid)
                runCatching { Groups.remove(gruppo, counter.uuid) }
                    .onSuccess { AppSettings.clearRemovedFromGroup(getApplication(), counter.uuid) }
            }
            db.counterDao().delete(counter) // i round seguono in cascata
            Notifications.cancel(getApplication(), counter.id)
            SyncWorker.refresh(getApplication())
            refreshGroupLabels()
            AlarmScheduler.scheduleNext(getApplication())
        }
    }

    /**
     * Archivia (v23): il round in corso viene chiuso e loggato, poi il timer si congela.
     * Le query di campanella e reset programmato filtrano già archived = 0, ma il reset
     * pendente va cancellato o al ripristino scatterebbe subito perché ormai nel passato.
     *
     * Su un timer condiviso l'archiviazione vale **per tutti**: un ciclo — la tachipirina
     * di questa influenza — finisce insieme e si riprende insieme. Il gruppo resta in
     * piedi, così alla prossima volta basta riesumarlo senza rifare inviti.
     */
    fun archiveCounter(counter: Counter) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val round = db.roundDao().add(Round(counterId = counter.id, startMs = counter.startMs, endMs = now), counter)
            db.counterDao().save(
                counter.copy(
                    archived = true,
                    archivedMs = now,
                    snoozeUntilMs = null,
                    scheduledResetMs = null,
                    lastRoundUuid = round.uuid,
                    lastRoundStartMs = round.startMs,
                )
            )
            Notifications.cancel(getApplication(), counter.id)
            AlarmScheduler.scheduleNext(getApplication())
        }
    }

    /**
     * Riprendi dall'archivio: torna fra gli attivi con un round nuovo da adesso, e su un
     * timer condiviso torna in linea **per tutti**.
     *
     * Con [keepHistory] a false si comincia un ciclo pulito: i round del giro precedente
     * non vengono cancellati — sono append-only e non si riscrive la storia di nessuno —
     * ma escono da storico e statistiche, che ripartono da qui.
     */
    fun resumeCounter(counter: Counter, keepHistory: Boolean = true) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val step = counter.bellMinutes?.times(60_000)
            db.counterDao().save(
                counter.copy(
                    archived = false,
                    archivedMs = null,
                    startMs = now,
                    bellNotified = false,
                    snoozeUntilMs = null,
                    // la campanella riparte da adesso, qualunque fosse il ritmo di prima
                    nextBellAtMs = if (step != null) now + step else null,
                    historyFromMs = if (keepHistory) counter.historyFromMs else now,
                )
            )
            AlarmScheduler.scheduleNext(getApplication())
        }
    }

    /** Esito della verifica prima di un'azione su un timer condiviso. */
    data class FreshCheck(
        val counter: Counter,
        /** true se nel frattempo qualcun altro l'ha fatto ripartire. */
        val moved: Boolean,
        val movedBy: String?,
    )

    /**
     * Prima di far ripartire un timer condiviso conviene chiedere al gruppo com'è messo.
     *
     * Il caso da evitare: l'app dormiva, l'altro ha già dato la dose, tu vedi una
     * schermata vecchia e premi ↺ convinto che non l'abbia fatto nessuno. Nello storico
     * finirebbero due eventi e il conteggio direbbe una dose in più — su una medicina è
     * un errore vero, non un fastidio estetico.
     *
     * La regola per avvisare non è una soglia di tempo ma un fatto: se ciò che vedevi
     * quando hai deciso non è più vero, la decisione va riproposta.
     */
    fun checkBeforeRestart(counter: Counter, onDone: (FreshCheck) -> Unit) {
        viewModelScope.launch {
            // Si rilegge sempre dal database. La copia che arriva dalla UI può essere
            // vecchia — un gestore di gesti in Compose sopravvive alle ricomposizioni e
            // continua a consegnare il contatore com'era quando è stato creato — e
            // riscriverla tale e quale significherebbe riportare indietro campi che nel
            // frattempo sono cambiati, sharedGroupId per primo.
            val counter = db.counterDao().byId(counter.id) ?: counter
            val groupId = counter.sharedGroupId
            if (groupId == null) {
                onDone(FreshCheck(counter, moved = false, movedBy = null))
                return@launch
            }
            // Solo questo contatore, non un giro di allineamento su tutti i gruppi: prima
            // c'era syncOnce(), che tirava giu' anche gli altri gruppi e 200 round a testa
            // — lento proprio nel momento in cui contava rispondere in fretta.
            withTimeoutOrNull(4_000) { SyncEngine.checkCounter(getApplication(), groupId, counter.uuid) }
            val fresco = db.counterDao().byId(counter.id) ?: counter
            val spostato = fresco.startMs != counter.startMs
            onDone(
                FreshCheck(
                    counter = fresco,
                    moved = spostato,
                    movedBy = if (spostato) db.roundDao().lastAuthor(counter.id) else null,
                )
            )
        }
    }

    /** Chiude il round corrente (loggandolo) e riparte da adesso. */
    fun restart(counter: Counter) {
        viewModelScope.launch {
            val counter = db.counterDao().byId(counter.id) ?: counter
            val now = System.currentTimeMillis()
            val round = db.roundDao().add(Round(counterId = counter.id, startMs = counter.startMs, endMs = now), counter)
            db.counterDao().save(
                counter.restarted(now, AppSettings.latePercent(getApplication()))
                    .copy(lastRoundUuid = round.uuid, lastRoundStartMs = round.startMs)
            )
            Notifications.cancel(getApplication(), counter.id)
            AlarmScheduler.scheduleNext(getApplication())
        }
    }

    /**
     * Riparti avanzato (v25), istante nel passato: il round chiuso finisce a [atMs]
     * e il nuovo round parte da lì. Vincolato all'inizio del round attuale.
     */
    fun restartAt(counter: Counter, atMs: Long) {
        viewModelScope.launch {
            val counter = db.counterDao().byId(counter.id) ?: counter
            val at = atMs.coerceAtMost(System.currentTimeMillis())
            if (at < counter.startMs) return@launch // la UI valida già; qui è solo difesa
            val round = db.roundDao().add(Round(counterId = counter.id, startMs = counter.startMs, endMs = at), counter)
            db.counterDao().save(
                counter.restarted(at, AppSettings.latePercent(getApplication()))
                    .copy(lastRoundUuid = round.uuid, lastRoundStartMs = round.startMs)
            )
            Notifications.cancel(getApplication(), counter.id)
            AlarmScheduler.scheduleNext(getApplication())
        }
    }

    /**
     * Corregge l'ORARIO dell'ultimo riavvio già fatto — non ne aggiunge uno nuovo, sposta
     * il confine fra il round appena chiuso e quello in corso. Vincolato a non retrocedere
     * oltre l'inizio di quel round (si mangerebbe il round prima) né a finire nel futuro.
     *
     * Su un condiviso lo può fare chiunque nel gruppo, non solo chi ha fatto il riavvio
     * sbagliato: tocca solo endMs dell'ULTIMO round, mai la storia più vecchia né quella
     * di un altro round — sono le regole del server a garantirlo, non questo codice.
     */
    fun correctLastRestart(counter: Counter, atMs: Long, onDone: (String) -> Unit) {
        viewModelScope.launch {
            var counter = db.counterDao().byId(counter.id) ?: counter
            val groupId = counter.sharedGroupId
            if (groupId != null) {
                // Come checkBeforeRestart: si riverifica lo stato vero prima di agire,
                // non si corregge alla cieca su una copia che potrebbe essere vecchia.
                withTimeoutOrNull(4_000) { SyncEngine.checkCounter(getApplication(), groupId, counter.uuid) }
                counter = db.counterDao().byId(counter.id) ?: counter
            }
            val roundUuid = counter.lastRoundUuid
            val round = roundUuid?.let { db.roundDao().byUuid(it) }
            if (round == null) {
                onDone("⚠️ Nessun riavvio recente da correggere.")
                return@launch
            }
            val now = System.currentTimeMillis()
            val at = atMs.coerceIn(round.startMs, now)
            if (at != atMs) {
                onDone("⚠️ Deve restare fra l'inizio di quel round e adesso.")
                return@launch
            }
            // Timbro anti-rimbalzo (vedi Round.endMsUpdatedAt): non basta "at" per dire
            // qual e' la versione piu' recente se due correzioni arrivano fuori ordine.
            val stampMs = System.currentTimeMillis()
            if (groupId != null) {
                try {
                    Groups.correctRoundEnd(groupId, round.uuid, at, stampMs)
                } catch (t: Throwable) {
                    onDone("⚠️ Non sono riuscito a salvarlo sul gruppo: ${t.message}")
                    return@launch
                }
            }
            // La campanella scivola della stessa differenza: e' un aggiustamento
            // dell'orario, non un nuovo riavvio da adesso.
            val delta = at - round.endMs
            db.roundDao().correctEndMs(round.id, at, stampMs)
            db.counterDao().save(
                counter.copy(
                    startMs = at,
                    nextBellAtMs = counter.nextBellAtMs?.plus(delta),
                )
            )
            Notifications.cancel(getApplication(), counter.id)
            AlarmScheduler.scheduleNext(getApplication())
            onDone("✅ Corretto.")
        }
    }

    /** Reset programmato nel futuro: il timer continua e si resetta da solo a [atMs]. L'ultimo comando vince. */
    fun scheduleReset(counter: Counter, atMs: Long) {
        viewModelScope.launch {
            db.counterDao().save(counter.copy(scheduledResetMs = atMs))
            AlarmScheduler.scheduleNext(getApplication())
        }
    }

    fun cancelScheduledReset(counter: Counter) {
        viewModelScope.launch {
            db.counterDao().save(counter.copy(scheduledResetMs = null))
            AlarmScheduler.scheduleNext(getApplication())
        }
    }

    /** Giro perso "solo conteggio" (v27): evento con data approssimativa, senza durata. */
    fun addMissedEvent(counter: Counter, atMs: Long) {
        viewModelScope.launch {
            val at = atMs.coerceAtMost(System.currentTimeMillis())
            db.roundDao().add(
                Round(counterId = counter.id, startMs = at, endMs = at, noTime = true),
                counter,
            )
        }
    }

    /** Scelta dell'utente quando fa Fatto/↺ con una ricorrente scaduta da molto. */
    enum class LateBellChoice { KEEP_RHYTHM, FROM_NOW, DISABLE }

    /** Come [restart], ma con la decisione esplicita sulla campanella in ritardo. */
    fun restartWithBellChoice(counter: Counter, choice: LateBellChoice) {
        viewModelScope.launch {
            val counter = db.counterDao().byId(counter.id) ?: counter
            val now = System.currentTimeMillis()
            val step = (counter.bellMinutes ?: 0) * 60_000
            val round = db.roundDao().add(Round(counterId = counter.id, startMs = counter.startMs, endMs = now), counter)
            val base = counter.copy(
                startMs = now,
                bellNotified = false,
                snoozeUntilMs = null,
                scheduledResetMs = null,
                lastRoundUuid = round.uuid,
                lastRoundStartMs = round.startMs,
            )
            val updated = when (choice) {
                LateBellChoice.KEEP_RHYTHM ->
                    base.copy(nextBellAtMs = advanceToFuture(counter.nextBellAtMs ?: now, step, now))
                LateBellChoice.FROM_NOW ->
                    base.copy(nextBellAtMs = now + step)
                LateBellChoice.DISABLE ->
                    base.copy(bellEnabled = false)
            }
            db.counterDao().save(updated)
            Notifications.cancel(getApplication(), counter.id)
            AlarmScheduler.scheduleNext(getApplication())
        }
    }

    /** Rimanda la campanella: ri-notifica tra [snoozeMinutes] minuti, il contatore continua. */
    fun snooze(counter: Counter, snoozeMinutes: Long) {
        viewModelScope.launch {
            db.counterDao().saveLocal(
                counter.copy(
                    bellNotified = true,
                    bellEnabled = true, // il rinvio deve poter suonare anche se la singola si era spenta
                    snoozeUntilMs = System.currentTimeMillis() + snoozeMinutes * 60_000,
                )
            )
            Notifications.cancel(getApplication(), counter.id)
            AlarmScheduler.scheduleNext(getApplication())
        }
    }

    // ---------------------------------------------------------------- condivisione

    /** Un gruppo di condivisione, etichettato con chi c'e' dentro oltre a me. */
    data class GroupInfo(val id: String, val label: String)

    /**
     * Etichetta di ogni gruppo, per scriverlo sulla card ("condiviso con Vale").
     * Sta qui e non nella card perche' i nomi vivono su Firestore: leggerli a ogni
     * ridisegno vorrebbe dire una lettura di rete al secondo.
     */
    private val _groupLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val groupLabels: StateFlow<Map<String, String>> = _groupLabels

    fun refreshGroupLabels() {
        viewModelScope.launch {
            val ids = (AppSettings.groups(getApplication()) +
                db.counterDao().shared().mapNotNull { it.sharedGroupId }).toList()
            val io = Cloud.uid
            _groupLabels.value = ids.associateWith { id ->
                Groups.members(id).filterKeys { it != io }.values
                    .joinToString(", ").ifBlank { "nessun altro" }
            }
        }
    }

    /** I gruppi di cui faccio gia' parte, per far scegliere dove mandare un timer. */
    fun myGroups(onDone: (List<GroupInfo>) -> Unit) {
        viewModelScope.launch {
            val ids = (AppSettings.groups(getApplication()) +
                db.counterDao().shared().mapNotNull { it.sharedGroupId }).toList()
            val io = Cloud.uid
            onDone(
                ids.map { id ->
                    val nomi = Groups.members(id).filterKeys { it != io }.values
                    GroupInfo(id, nomi.joinToString(", ").ifBlank { "gruppo senza nomi" })
                }
            )
        }
    }

    /**
     * Condivide un contatore. Con [groupId] valorizzato lo manda in un gruppo che esiste
     * gia' e non serve nessun invito: ritorna null. Con [groupId] a null crea un gruppo
     * nuovo e ritorna il codice da passare alla persona da invitare.
     *
     * La scelta e' esplicita di proposito: prendere sempre "il primo gruppo che c'e'"
     * renderebbe impossibile avere un timer con una persona e un altro con un'altra.
     */
    fun shareCounter(
        counter: Counter,
        myName: String,
        groupId: String?,
        onDone: (String?, String?) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val gruppo = groupId ?: Groups.create(myName)
                val codice = if (groupId == null) Groups.invite(gruppo) else null
                AppSettings.addGroup(getApplication(), gruppo)

                // save() timbra updatedMs e manda su da solo. Un Groups.push esplicito
                // qui rispedirebbe la copia col timestamp vecchio, sovrascrivendo quella
                // appena scritta: il documento nascerebbe già arretrato e ogni confronto
                // successivo fra le due copie partirebbe storto.
                val condiviso = counter.copy(sharedGroupId = gruppo)
                db.counterDao().save(condiviso)
                SyncEngine.listen(getApplication(), gruppo)
                SyncEngine.pushHistory(getApplication(), condiviso)
                SyncWorker.refresh(getApplication())
                refreshGroupLabels()
                onDone(codice, null)
            } catch (t: Throwable) {
                onDone(null, t.message ?: "condivisione fallita")
            }
        }
    }

    /** Codice nuovo per far entrare un'altra persona in un gruppo che esiste gia'. */
    fun newInvite(groupId: String, onDone: (String?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                onDone(Groups.invite(groupId), null)
            } catch (t: Throwable) {
                onDone(null, t.message ?: "invito fallito")
            }
        }
    }

    fun joinGroup(code: String, myName: String, onDone: (Groups.JoinResult) -> Unit) {
        viewModelScope.launch {
            val esito = Groups.join(code, myName)
            if (esito is Groups.JoinResult.Ok) {
                AppSettings.addGroup(getApplication(), esito.groupId)
                SyncEngine.listen(getApplication(), esito.groupId)
                SyncWorker.refresh(getApplication())
                refreshGroupLabels()
            }
            onDone(esito)
        }
    }

    /**
     * Smette di condividere, per tutti. Il contatore esce dal gruppo e ogni telefono che
     * ce l'aveva se lo tiene, con il suo storico, come timer autonomo: da lì in poi le
     * due copie vivono vite separate.
     *
     * L'alternativa — "esco solo io e gli altri continuano" — avrebbe senso da tre persone
     * in su, ma richiederebbe di tenere traccia di chi partecipa a quale contatore. In due
     * le due cose coincidono, perché chi resta è comunque solo.
     */
    fun unshare(counter: Counter) {
        viewModelScope.launch {
            counter.sharedGroupId?.let { gruppo ->
                runCatching { Groups.remove(gruppo, counter.uuid) }
            }
            db.counterDao().save(counter.copy(sharedGroupId = null))
            // Se non resta piu' niente di condiviso, il giro periodico si spegne da solo
            SyncWorker.refresh(getApplication())
            refreshGroupLabels()
        }
    }

    /** Allineamento su richiesta: e' il tap sull'indicatore in intestazione. */
    fun syncNow() {
        viewModelScope.launch {
            // niente da allineare, niente giro: non ha senso disturbare la rete
            if (db.counterDao().shared().isEmpty()) return@launch
            SyncEngine.syncOnce(getApplication())
        }
    }

    fun setNotifyOnRemote(counter: Counter, value: Boolean) {
        viewModelScope.launch {
            db.counterDao().saveLocal(counter.copy(notifyOnRemote = value))
        }
    }

    fun membersOf(groupId: String, onDone: (Map<String, String>) -> Unit) {
        viewModelScope.launch { onDone(Groups.members(groupId)) }
    }

    fun cycleViewMode(counter: Counter) {
        viewModelScope.launch {
            val next = ViewMode.from(counter.viewMode).next()
            db.counterDao().saveLocal(counter.copy(viewMode = next.name))
        }
    }
}
