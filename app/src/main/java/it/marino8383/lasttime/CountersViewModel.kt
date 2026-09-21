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

class CountersViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as LastTimeApp).db

    val counters = db.counterDao().activeCounters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val archived = db.counterDao().archivedCounters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Riepilogo round per contatore, per le card d'archivio. */
    val roundSummaries = db.roundDao().summaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun roundsFor(counterId: Long) = db.roundDao().roundsFor(counterId)

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
                runCatching { Groups.remove(gruppo, counter.uuid) }
            }
            db.counterDao().delete(counter) // i round seguono in cascata
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
     * Se era condiviso **esce dal gruppo**: un timer fermo dentro una condivisione sarebbe
     * un ibrido senza senso — direbbe "in archivio da 3 giorni" mentre continua a ricevere
     * aggiornamenti e a spostarsi sotto. Gli altri tengono la loro copia, attiva e autonoma.
     */
    fun archiveCounter(counter: Counter) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            db.roundDao().add(Round(counterId = counter.id, startMs = counter.startMs, endMs = now), counter)
            counter.sharedGroupId?.let { gruppo ->
                runCatching { Groups.remove(gruppo, counter.uuid) }
            }
            db.counterDao().save(
                counter.copy(
                    archived = true,
                    archivedMs = now,
                    snoozeUntilMs = null,
                    scheduledResetMs = null,
                    sharedGroupId = null,
                )
            )
            Notifications.cancel(getApplication(), counter.id)
            SyncWorker.refresh(getApplication())
            refreshGroupLabels()
            AlarmScheduler.scheduleNext(getApplication())
        }
    }

    /** Riprendi dall'archivio (v23): torna fra gli attivi con un round nuovo da adesso. */
    fun resumeCounter(counter: Counter) {
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
                )
            )
            AlarmScheduler.scheduleNext(getApplication())
        }
    }

    /** Chiude il round corrente (loggandolo) e riparte da adesso. */
    fun restart(counter: Counter) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            db.roundDao().add(Round(counterId = counter.id, startMs = counter.startMs, endMs = now), counter)
            db.counterDao().save(counter.restarted(now, AppSettings.latePercent(getApplication())))
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
            val at = atMs.coerceAtMost(System.currentTimeMillis())
            if (at < counter.startMs) return@launch // la UI valida già; qui è solo difesa
            db.roundDao().add(Round(counterId = counter.id, startMs = counter.startMs, endMs = at), counter)
            db.counterDao().save(counter.restarted(at, AppSettings.latePercent(getApplication())))
            Notifications.cancel(getApplication(), counter.id)
            AlarmScheduler.scheduleNext(getApplication())
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
            val now = System.currentTimeMillis()
            val step = (counter.bellMinutes ?: 0) * 60_000
            db.roundDao().add(Round(counterId = counter.id, startMs = counter.startMs, endMs = now), counter)
            val base = counter.copy(startMs = now, bellNotified = false, snoozeUntilMs = null, scheduledResetMs = null)
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
            db.counterDao().save(
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
            val ids = db.counterDao().shared().mapNotNull { it.sharedGroupId }.distinct()
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
            val ids = db.counterDao().shared().mapNotNull { it.sharedGroupId }.distinct()
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

                val condiviso = counter.copy(sharedGroupId = gruppo)
                db.counterDao().save(condiviso)
                Groups.push(gruppo, condiviso)
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
            db.counterDao().save(counter.copy(notifyOnRemote = value))
        }
    }

    fun membersOf(groupId: String, onDone: (Map<String, String>) -> Unit) {
        viewModelScope.launch { onDone(Groups.members(groupId)) }
    }

    fun cycleViewMode(counter: Counter) {
        viewModelScope.launch {
            val next = ViewMode.from(counter.viewMode).next()
            db.counterDao().save(counter.copy(viewMode = next.name))
        }
    }
}
