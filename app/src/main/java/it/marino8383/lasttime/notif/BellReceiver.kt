package it.marino8383.lasttime.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.marino8383.lasttime.LastTimeApp
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
                val due = dao.dueBellCounters(System.currentTimeMillis())
                due.forEach { counter ->
                    Notifications.notifyBell(context, counter)
                    dao.update(counter.copy(bellNotified = true))
                }
                AlarmScheduler.scheduleNext(context)
            } finally {
                result.finish()
            }
        }
    }
}
