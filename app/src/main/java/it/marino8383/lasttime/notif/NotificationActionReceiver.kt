package it.marino8383.lasttime.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.marino8383.lasttime.LastTimeApp
import it.marino8383.lasttime.data.Round
import it.marino8383.lasttime.data.restarted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Bottoni della notifica campanella: Scarta (spegne la campanella) e Fatto (restart del contatore). */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val counterId = intent.data?.lastPathSegment?.toLongOrNull() ?: return
        val action = intent.action ?: return
        if (action != Notifications.ACTION_DISMISS && action != Notifications.ACTION_DONE) return

        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as LastTimeApp
                val dao = app.db.counterDao()
                val counter = dao.byId(counterId)
                if (counter != null) {
                    val now = System.currentTimeMillis()
                    when (action) {
                        // Scarta: il contatore continua, la campanella si spegne (🔕 sulla card)
                        Notifications.ACTION_DISMISS ->
                            dao.update(counter.copy(bellEnabled = false, snoozeUntilMs = null))

                        // Fatto: round loggato, riparte; la prossima campanella segue il bellMode
                        Notifications.ACTION_DONE -> {
                            app.db.roundDao().insert(
                                Round(counterId = counter.id, startMs = counter.startMs, endMs = now)
                            )
                            dao.update(counter.restarted(now))
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
