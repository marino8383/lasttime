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
    // _v2: i canali creati in v0.3 erano senza vibrazione/suono espliciti e le
    // impostazioni di un canale esistente sono immutabili -> canali nuovi
    const val CHANNEL_BELL = "bell_v2"
    const val CHANNEL_SECRET = "bell_secret_v2"
    const val CHANNEL_SHARED = "shared_v1"

    const val ACTION_DONE = "it.marino8383.lasttime.action.DONE"
    const val ACTION_DISMISS = "it.marino8383.lasttime.action.DISMISS"
    const val EXTRA_SNOOZE_COUNTER_ID = "snoozeCounterId"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("bell")
        nm.deleteNotificationChannel("bell_secret")

        val vibration = longArrayOf(0, 400, 200, 400)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BELL, "Campanelle", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avvisi di tempo sforato"
                enableVibration(true)
                vibrationPattern = vibration
                setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    Notification.AUDIO_ATTRIBUTES_DEFAULT,
                )
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SHARED, "Timer condivisi", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Quando un'altra persona fa ripartire un timer condiviso"
                enableVibration(true)
            }
        )
        // Canale per i timer lucchettati (v26): mai contenuti sul lockscreen, nessun badge
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SECRET, "Timer lucchettati", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avvisi anonimi per i timer nella sezione segreta"
                lockscreenVisibility = Notification.VISIBILITY_SECRET
                setShowBadge(false)
                enableVibration(true)
                vibrationPattern = vibration
                setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    Notification.AUDIO_ATTRIBUTES_DEFAULT,
                )
            }
        )
    }

    /**
     * "Vale ha fatto ripartire Tachipirina." Canale a parte e importanza normale: non e'
     * una sveglia da svegliare la casa, e' un'informazione — e va potuta silenziare dalle
     * impostazioni di sistema senza perdere le campanelle.
     */
    /** Come [notifySharedRestart] ma con un testo qualsiasi: archivio, ripresa, ecc. */
    fun notifySharedEvent(context: Context, counter: Counter, byName: String, testo: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val secret = counter.secret
        val notification = Notification.Builder(context, if (secret) CHANNEL_SECRET else CHANNEL_SHARED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                if (secret) "👥 Un timer lucchettato"
                else "👥 $byName: ${counter.name}"
            )
            .setContentText(testo)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(2_000_000 + counter.id.toInt(), notification)
    }

    fun notifySharedRestart(context: Context, counter: Counter, byName: String, atMs: Long) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val secret = counter.secret
        val notification = Notification.Builder(context, if (secret) CHANNEL_SECRET else CHANNEL_SHARED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                if (secret) "👥 Un timer lucchettato è ripartito"
                else "👥 $byName: ${counter.name}"
            )
            .setContentText(
                if (secret) "Riavviato da un'altra persona."
                else "Fatto ripartire ${it.marino8383.lasttime.formatRingTime(atMs)}."
            )
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .setAutoCancel(true)
            .build()

        // id separato da campanella (id) e reset programmato (1M+id)
        context.getSystemService(NotificationManager::class.java)
            .notify(2_000_000 + counter.id.toInt(), notification)
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

    /** Avviso informativo (senza azioni) dell'avvenuto reset programmato (v25/v26). */
    fun notifyScheduledReset(context: Context, counter: Counter) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val secret = counter.secret
        val notification = Notification.Builder(context, if (secret) CHANNEL_SECRET else CHANNEL_BELL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                if (secret) "⏲ Reset programmato eseguito: un timer lucchettato"
                else "⏲ Reset programmato eseguito: ${counter.name}"
            )
            .setContentText("Il timer è ripartito.")
            .setContentIntent(
                PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(1_000_000 + counter.id.toInt(), notification)
    }
}
