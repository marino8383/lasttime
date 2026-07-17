package it.marino8383.lasttime.notif

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import it.marino8383.lasttime.LastTimeApp

/**
 * Un'unica sveglia esatta sul prossimo evento campanella: allo scatto il receiver
 * notifica i contatori sforati e ripianifica. Mai WorkManager (timing inaffidabile).
 */
object AlarmScheduler {

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0,
            Intent(context, BellReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    suspend fun scheduleNext(context: Context) {
        val app = context.applicationContext as LastTimeApp
        val bell = app.db.counterDao().nextBellDeadline()
        val reset = app.db.counterDao().nextScheduledReset()
        val next = listOfNotNull(bell, reset).minOrNull()
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context)
        am.cancel(pi)
        if (next == null) return

        val at = maxOf(next, System.currentTimeMillis() + 1_000)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            // Permesso exact negato: meglio una sveglia imprecisa che nessuna
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Su Android 12+ porta l'utente alla schermata per consentire le sveglie esatte, se serve. */
    fun ensureExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val am = context.getSystemService(AlarmManager::class.java)
        if (am.canScheduleExactAlarms()) return
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            // alcune ROM non espongono la schermata: pazienza, resta la sveglia imprecisa
        }
    }
}
