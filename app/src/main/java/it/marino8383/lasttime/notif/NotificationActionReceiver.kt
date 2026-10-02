package it.marino8383.lasttime.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.marino8383.lasttime.CounterActions
import it.marino8383.lasttime.LastTimeApp
import it.marino8383.lasttime.data.saveLocal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Bottoni della notifica campanella: Scarta (spegne la campanella) e Fatto (restart del contatore). */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val counterId = intent.data?.lastPathSegment?.toLongOrNull() ?: return
        val action = intent.action ?: return
        if (action != Notifications.ACTION_DISMISS && action != Notifications.ACTION_DONE) return
        val seenStartMs = intent.getLongExtra(Notifications.EXTRA_SEEN_START_MS, -1L).takeIf { it >= 0 }

        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as LastTimeApp
                val dao = app.db.counterDao()
                val counter = dao.byId(counterId)
                if (counter != null) {
                    when (action) {
                        // Scarta: il contatore continua, la campanella si spegne (🔕 sulla card)
                        Notifications.ACTION_DISMISS ->
                            dao.saveLocal(
                                counter.copy(bellEnabled = false, snoozeUntilMs = null, remindAtMs = null, remindCount = 0)
                            )

                        // Fatto: stessa strada del ↺ sulla card (CounterActions), così
                        // l'ultimo round resta correggibile e i Giornalieri fanno il loro +1.
                        Notifications.ACTION_DONE -> {
                            val fresco = CounterActions.latest(context, counter, askGroup = true)
                            // Stessa regola di checkBeforeRestart: se il timer non è più
                            // quello che la notifica mostrava — l'altro l'ha già fatto
                            // ripartire, o l'ho già fatto io dall'app — un secondo Fatto
                            // sarebbe una dose in più nello storico. Da una notifica non si
                            // può chiedere conferma, quindi nel dubbio non si riavvia.
                            val giaFatto = seenStartMs != null && fresco.startMs != seenStartMs
                            if (!giaFatto && !fresco.archived) {
                                CounterActions.done(context, fresco, System.currentTimeMillis())
                            }
                        }
                    }
                    AlarmScheduler.scheduleNext(context)
                }
                Notifications.cancel(context, counterId)
            } finally {
                result.finish()
            }
        }
    }
}
