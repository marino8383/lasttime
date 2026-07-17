package it.marino8383.lasttime.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.marino8383.lasttime.AppSettings
import it.marino8383.lasttime.LastTimeApp
import it.marino8383.lasttime.data.Round
import it.marino8383.lasttime.data.restarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Scatta all'orario della prima campanella in scadenza: notifica e ripianifica. */
class BellReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as LastTimeApp
                val dao = app.db.counterDao()
                val now = System.currentTimeMillis()

                // Reset programmati in scadenza: round chiuso all'istante programmato,
                // timer ripartito da lì, campanella riarmata secondo le sue regole
                dao.dueScheduledResets(now).forEach { counter ->
                    val at = counter.scheduledResetMs ?: return@forEach
                    app.db.roundDao().insert(
                        Round(counterId = counter.id, startMs = counter.startMs, endMs = at)
                    )
                    dao.update(counter.restarted(at, AppSettings.latePercent(context)))
                    Notifications.notifyScheduledReset(context, counter)
                }

                val due = dao.dueBellCounters(now)
                due.forEach { counter ->
                    Notifications.notifyBell(context, counter)
                    // rinvio consumato
                    val snooze = counter.snoozeUntilMs?.takeIf { it > now }
                    // Nessun riarmo automatico: la ricorrente tiene la scadenza suonata
                    // (serve per "mantieni il ritmo" al Fatto), la singola la consuma
                    val next = if (counter.bellRepeat) counter.nextBellAtMs
                    else counter.nextBellAtMs?.takeIf { it > now }
                    dao.update(
                        counter.copy(bellNotified = true, snoozeUntilMs = snooze, nextBellAtMs = next)
                    )
                }
                AlarmScheduler.scheduleNext(context)
            } finally {
                result.finish()
            }
        }
    }
}
