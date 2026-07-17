package it.marino8383.lasttime.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Le sveglie di AlarmManager non sopravvivono al riavvio e vanno riviste se
 * cambia l'ora o il fuso: qui si ri-registra la prossima campanella.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                val result = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        AlarmScheduler.scheduleNext(context)
                    } finally {
                        result.finish()
                    }
                }
            }
        }
    }
}
