package it.marino8383.lasttime

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.marino8383.lasttime.data.Counter
import it.marino8383.lasttime.data.Round
import it.marino8383.lasttime.notif.AlarmScheduler
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
            db.counterDao().update(counter.copy(startMs = now, bellNotified = false))
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
