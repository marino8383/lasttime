package it.marino8383.lasttime.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.marino8383.lasttime.LastTimeApp
import it.marino8383.lasttime.data.Round
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Bottoni della notifica campanella: Scarta (solo chiudi) e Fatto (restart del contatore). */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val counterId = intent.data?.lastPathSegment?.toLongOrNull() ?: return

        when (intent.action) {
            Notifications.ACTION_DISMISS -> Notifications.cancel(context, counterId)

            Notifications.ACTION_DONE -> {
                val result = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val app = context.applicationContext as LastTimeApp
                        val dao = app.db.counterDao()
                        val counter = dao.byId(counterId)
                        if (counter != null) {
                            val now = System.currentTimeMillis()
                            app.db.roundDao().insert(
                                Round(counterId = counter.id, startMs = counter.startMs, endMs = now)
                            )
                            dao.update(
                                counter.copy(startMs = now, bellNotified = false, snoozeUntilMs = null)
                            )
                            AlarmScheduler.scheduleNext(context)
                        }
                        Notifications.cancel(context, counterId)
                    } finally {
                        result.finish()
                    }
                }
            }
        }
    }
}
