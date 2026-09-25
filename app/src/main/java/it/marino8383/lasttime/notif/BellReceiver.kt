package it.marino8383.lasttime.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.marino8383.lasttime.AppSettings
import it.marino8383.lasttime.LastTimeApp
import it.marino8383.lasttime.data.Round
import it.marino8383.lasttime.data.add
import it.marino8383.lasttime.data.restarted
import it.marino8383.lasttime.data.save
import it.marino8383.lasttime.data.saveLocal
import it.marino8383.lasttime.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
                    app.db.roundDao().add(
                        Round(counterId = counter.id, startMs = counter.startMs, endMs = at),
                        counter,
                    )
                    dao.save(counter.restarted(at, AppSettings.latePercent(context)))
                    Notifications.notifyScheduledReset(context, counter)
                }

                var due = dao.dueBellCounters(now)

                // Se fra le scadenze c'è un timer condiviso, prima di disturbare qualcuno
                // vale la pena chiedere: l'altro potrebbe averlo appena fatto ripartire e
                // noi non saperlo ancora. È l'unico momento in cui una lettura di rete in
                // più si ripaga — non un controllo periodico più fitto, ma una verifica
                // proprio nell'istante in cui stiamo per suonare.
                //
                // Il timeout è corto di proposito: un receiver ha una finestra di pochi
                // secondi, e senza rete deve suonare comunque come ha sempre fatto.
                if (due.any { it.sharedGroupId != null }) {
                    withTimeoutOrNull(4_000) { SyncEngine.syncOnce(app) }
                    // rilette dopo l'allineamento: quelle rifasate non sono più in scadenza
                    due = dao.dueBellCounters(System.currentTimeMillis())
                }

                due.forEach { counter ->
                    // Nascosto: continua a suonare "internamente" (bellNotified si alza
                    // comunque, cosi' risulta sforato appena lo si torna a guardare), ma
                    // niente notifica vera — e' proprio quello che "nascosto" vuol dire.
                    if (!counter.hidden) Notifications.notifyBell(context, counter)
                    // rinvio consumato; la scadenza suonata resta in nextBellAtMs
                    // (serve per "mantieni il ritmo" e per mostrare "sforata da X")
                    val snooze = counter.snoozeUntilMs?.takeIf { it > now }
                    dao.saveLocal(counter.copy(bellNotified = true, snoozeUntilMs = snooze))
                }
                AlarmScheduler.scheduleNext(context)
            } finally {
                result.finish()
            }
        }
    }
}
