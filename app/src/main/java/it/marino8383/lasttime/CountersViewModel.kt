package it.marino8383.lasttime

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import it.marino8383.lasttime.AppSettings
import androidx.lifecycle.viewModelScope
import it.marino8383.lasttime.data.Counter
import it.marino8383.lasttime.data.Round
import it.marino8383.lasttime.data.advanceToFuture
import it.marino8383.lasttime.data.restarted
import it.marino8383.lasttime.notif.AlarmScheduler
import it.marino8383.lasttime.notif.Notifications
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CountersViewModel(app: Application) : AndroidViewModel(app) {

    private val db = (app as LastTimeApp).db

    val counters = db.counterDao().activeCounters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun roundsFor(counterId: Long) = db.roundDao().roundsFor(counterId)

    fun addCounter(name: String, startMs: Long, bellMinutes: Long?) {
        viewModelScope.launch {
            // Data nel futuro -> clamp ad adesso (v16)
            val start = startMs.coerceAtMost(System.currentTimeMillis())
            db.counterDao().insert(
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
            db.counterDao().update(clamped)
            AlarmScheduler.scheduleNext(getApplication())
        }
    }

    fun deleteCounter(counter: Counter) {
        viewModelScope.launch {
            db.counterDao().delete(counter) // i round seguono in cascata
            AlarmScheduler.scheduleNext(getApplication())
        }
    }

    /** Chiude il round corrente (loggandolo) e riparte da adesso. */
    fun restart(counter: Counter) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            db.roundDao().insert(Round(counterId = counter.id, startMs = counter.startMs, endMs = now))
            db.counterDao().update(counter.restarted(now, AppSettings.latePercent(getApplication())))
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
            db.counterDao().update(counter.restarted(at, AppSettings.latePercent(getApplication())))
            Notifications.cancel(getApplication(), counter.id)
            AlarmScheduler.scheduleNext(getApplication())
        }
    }

    /** Reset programmato nel futuro: il timer continua e si resetta da solo a [atMs]. L'ultimo comando vince. */
    fun scheduleReset(counter: Counter, atMs: Long) {
        viewModelScope.launch {
            db.counterDao().update(counter.copy(scheduledResetMs = atMs))
            AlarmScheduler.scheduleNext(getApplication())
        }
    }

    fun cancelScheduledReset(counter: Counter) {
        viewModelScope.launch {
            db.counterDao().update(counter.copy(scheduledResetMs = null))
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
            db.counterDao().update(updated)
            Notifications.cancel(getApplication(), counter.id)
            AlarmScheduler.scheduleNext(getApplication())
        }
    }

    /** Rimanda la campanella: ri-notifica tra [snoozeMinutes] minuti, il contatore continua. */
    fun snooze(counter: Counter, snoozeMinutes: Long) {
        viewModelScope.launch {
            db.counterDao().update(
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

    fun cycleViewMode(counter: Counter) {
        viewModelScope.launch {
            val next = ViewMode.from(counter.viewMode).next()
            db.counterDao().update(counter.copy(viewMode = next.name))
        }
    }
}
