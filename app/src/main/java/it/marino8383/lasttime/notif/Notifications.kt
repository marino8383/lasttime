package it.marino8383.lasttime.notif

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import it.marino8383.lasttime.MainActivity
import it.marino8383.lasttime.R
import it.marino8383.lasttime.bellLabel
import it.marino8383.lasttime.data.Counter

object Notifications {
    const val CHANNEL_BELL = "bell"
    const val CHANNEL_SECRET = "bell_secret"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BELL, "Campanelle", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avvisi di tempo sforato"
            }
        )
        // Canale per i timer lucchettati (v26): mai contenuti sul lockscreen, nessun badge
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SECRET, "Timer lucchettati", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avvisi anonimi per i timer nella sezione segreta"
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                setShowBadge(false)
            }
        )
    }

    fun notifyBell(context: Context, counter: Counter) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val tap = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Per i lucchettati: notifica anonima, nessun nome nel payload (v26)
        val secret = counter.secret
        val channel = if (secret) CHANNEL_SECRET else CHANNEL_BELL
        val title = if (secret) "🔔 Un timer lucchettato" else "🔔 ${counter.name}"
        val text = if (secret) "Tempo sforato!"
        else "Tempo sforato: oltre ${bellLabel(counter.bellMinutes ?: 0)}"

        val notification = Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(counter.id.toInt(), notification)
    }
}
