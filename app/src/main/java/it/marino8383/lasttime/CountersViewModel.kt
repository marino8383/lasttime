package it.marino8383.lasttime

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import it.marino8383.lasttime.AppSettings
import androidx.lifecycle.viewModelScope
import it.marino8383.lasttime.data.Counter
import it.marino8383.lasttime.data.Round
import it.marino8383.lasttime.data.advanceToFuture
import it.marino8383.lasttime.data.create
import it.marino8383.lasttime.data.save
import it.marino8383.lasttime.data.restarted
import it.marino8383.lasttime.notif.AlarmScheduler
import it.marino8383.lasttime.notif.Notifications
import it.marino8383.lasttime.sync.Groups
import it.marino8383.lasttime.sync.SyncEngine
import kotlinx.coroutines.flow.SharingStarted
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
            if (step != null && start != counter.startMs &&
                counter.bellEnabled && counter.bellMode != "FIXED"
            ) {
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

    fun deleteCounter(counter: Counter) {
        viewModelScope.launch {
            db.counterDao().delete(counter) // i round seguono in cascata
            AlarmScheduler.scheduleNext(getApplication())
        }
    }

    /**
     * Archivia (v23): il round in corso viene chiuso e loggato, poi il timer si congela.
     * Le query di campanella e reset programmato filtrano già archived = 0, ma il reset
     * pendente va cancellato o al ripristino scatterebbe subito perché ormai nel passato.
     */
    fun archiveCounter(counter: Counter) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            db.roundDao().insert(Round(counterId = counter.id, startMs = counter.startMs, endMs = now))
            db.counterDao().save(
                counter.copy(
                    archived = true,
                    archivedMs = now,
                    snoozeUntilMs = null,
                    scheduledResetMs = null,
                )
            )
            Notifications.cancel(getApplication(), counter.id)
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
                    nextBellAtMs = if (step != null && counter.bellEnabled) now + step else null,
                )
            )
            AlarmScheduler.scheduleNext(getApplication())
        }
    }

    /** Chiude il round corrente (loggandolo) e riparte da adesso. */
    fun restart(counter: Counter) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            db.roundDao().insert(Round(counterId = counter.id, startMs = counter.startMs, endMs = now))
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
            db.roundDao().insert(Round(counterId = counter.id, startMs = counter.startMs, endMs = at))
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
            db.roundDao().insert(
                Round(counterId = counter.id, startMs = at, endMs = at, noTime = true)
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
            db.roundDao().insert(Round(counterId = counter.id, startMs = counter.startMs, endMs = now))
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

    /**
     * Condivide un contatore. Se esiste gia' un gruppo (la coppia di sempre) ci entra
     * dentro senza chiedere niente a nessuno e ritorna null: l'invito serve solo per
     * far entrare una persona nuova, non per ogni timer.
     * Se invece il gruppo va creato, ritorna il codice da dettare all'altro.
     */
    fun shareCounter(counter: Counter, myName: String, onDone: (String?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val esistente = db.counterDao().shared().firstNotNullOfOrNull { it.sharedGroupId }
                val groupId = esistente ?: Groups.create(myName)
                val codice = if (esistente == null) Groups.invite(groupId) else null

                val condiviso = counter.copy(sharedGroupId = groupId)
                db.counterDao().save(condiviso)
                Groups.push(groupId, condiviso)
                SyncEngine.listen(getApplication(), groupId)
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
            if (esito is Groups.JoinResult.Ok) SyncEngine.listen(getApplication(), esito.groupId)
            onDone(esito)
        }
    }

    /**
     * Smette di condividere: il contatore resta qui com'e', semplicemente non parla
     * piu' col gruppo. La copia degli altri continua per conto suo.
     */
    fun unshare(counter: Counter) {
        viewModelScope.launch {
            db.counterDao().save(counter.copy(sharedGroupId = null))
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
