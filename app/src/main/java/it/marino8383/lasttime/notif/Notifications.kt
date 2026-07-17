package it.marino8383.lasttime.notif

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import it.marino8383.lasttime.MainActivity
import it.marino8383.lasttime.R
import it.marino8383.lasttime.bellLabel
import it.marino8383.lasttime.data.Counter

object Notifications {
    const val CHANNEL_BELL = "bell"
    const val CHANNEL_SECRET = "bell_secret"

    const val ACTION_DONE = "it.marino8383.lasttime.action.DONE"
    const val ACTION_DISMISS = "it.marino8383.lasttime.action.DISMISS"
    const val EXTRA_SNOOZE_COUNTER_ID = "snoozeCounterId"

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
            // Scarta: chiude la notifica, il contatore continua
            .addAction(actionBuilder(context, counter.id, ACTION_DISMISS, "Scarta"))
            // Fatto: round loggato, il contatore riparte da adesso
            .addAction(actionBuilder(context, counter.id, ACTION_DONE, "Fatto"))
            // Rimanda: apre la maschera per scegliere di quanto rinviare la campanella
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Rimanda",
                    PendingIntent.getActivity(
                        context, counter.id.toInt(),
                        Intent(context, MainActivity::class.java)
                            .setData(Uri.parse("lasttime://snooze/${counter.id}"))
                            .putExtra(EXTRA_SNOOZE_COUNTER_ID, counter.id)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).build()
            )
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(counter.id.toInt(), notification)
    }

    private fun actionBuilder(
        context: Context,
        counterId: Long,
        action: String,
        label: String,
    ): Notification.Action = Notification.Action.Builder(
        null,
        label,
        PendingIntent.getBroadcast(
            context, counterId.toInt(),
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(action)
                .setData(Uri.parse("lasttime://counter/$counterId")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ),
    ).build()

    fun cancel(context: Context, counterId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(counterId.toInt())
    }
}
