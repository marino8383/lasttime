package it.marino8383.lasttime.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.marino8383.lasttime.CounterActions
import it.marino8383.lasttime.LastTimeApp
import it.marino8383.lasttime.data.firstReminderAt
import it.marino8383.lasttime.data.nextReminderAt
import it.marino8383.lasttime.data.reminderEligible
import it.marino8383.lasttime.data.saveLocal
import it.marino8383.lasttime.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Scatta all'orario della prima campanella (o reset, o promemoria) in scadenza: notifica e ripianifica. */
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
                dao.dueScheduledResets(now).forEach { dueCounter ->
                    // scheduledResetMs e' sincronizzato: su un condiviso entrambi i
                    // telefoni possono avere la stessa sveglia di sistema. Prima di
                    // eseguire alla cieca si riverifica lo stato vero — l'altro
                    // potrebbe averlo gia' fatto ripartire o aver annullato il reset
                    // nel frattempo, altrimenti si rischia un doppio round.
                    val counter = CounterActions.latest(context, dueCounter, askGroup = true)
                    val at = counter.scheduledResetMs?.takeIf { it <= System.currentTimeMillis() } ?: return@forEach
                    // sparito (tolto dal gruppo) o archiviato nel frattempo: niente da fare
                    if (counter.archived || dao.byId(counter.id) == null) return@forEach
                    CounterActions.restart(context, counter, at)
                    Notifications.notifyScheduledReset(context, counter)
                }

                var due = dao.dueBellCounters(now)
                var remind = dao.dueReminders(now)

                // Se fra le scadenze c'è un timer condiviso, prima di disturbare qualcuno
                // vale la pena chiedere: l'altro potrebbe averlo appena fatto ripartire e
                // noi non saperlo ancora. È l'unico momento in cui una lettura di rete in
                // più si ripaga — non un controllo periodico più fitto, ma una verifica
                // proprio nell'istante in cui stiamo per suonare. Vale anche per i
                // promemoria: ricordare una dose che l'altro ha già dato è peggio che tacere.
                //
                // Il timeout è corto di proposito: un receiver ha una finestra di pochi
                // secondi, e senza rete deve suonare comunque come ha sempre fatto.
                if ((due + remind).any { it.sharedGroupId != null }) {
                    withTimeoutOrNull(4_000) { SyncEngine.syncOnce(app) }
                    // rilette dopo l'allineamento: quelle rifasate non sono più in scadenza
                    val adesso = System.currentTimeMillis()
                    due = dao.dueBellCounters(adesso)
                    remind = dao.dueReminders(adesso)
                }

                due.forEach { counter ->
                    // Nascosto: continua a suonare "internamente" (bellNotified si alza
                    // comunque, cosi' risulta sforato appena lo si torna a guardare), ma
                    // niente notifica vera — e' proprio quello che "nascosto" vuol dire.
                    if (!counter.hidden) Notifications.notifyBell(context, counter)
                    // rinvio consumato; la scadenza suonata resta in nextBellAtMs
                    // (serve per "mantieni il ritmo" e per mostrare "sforata da X")
                    val snooze = counter.snoozeUntilMs?.takeIf { it > now }
                    // Suonata e senza risposta: si arma il promemoria. Parte dal momento in
                    // cui doveva suonare — la scadenza, o la fine del rinvio se è un rinvio che
                    // scade — e riparte da capo a ogni squillo (anche dopo un "Rimanda").
                    val dovevaSuonare = (if (counter.bellNotified) counter.snoozeUntilMs else counter.nextBellAtMs) ?: now
                    val step = counter.bellMinutes?.times(60_000)
                    val armato = if (counter.reminderEligible() && step != null) {
                        counter.copy(remindAtMs = firstReminderAt(dovevaSuonare, now, step), remindCount = 0)
                    } else {
                        counter.copy(remindAtMs = null, remindCount = 0)
                    }
                    dao.saveLocal(armato.copy(bellNotified = true, snoozeUntilMs = snooze))
                }

                // Promemoria: la campanella è suonata, nessuno ha risposto. Dicitura a parte,
                // così non si scambia per una campanella nuova.
                remind.forEach { counter ->
                    val adesso = System.currentTimeMillis()
                    val step = counter.bellMinutes?.times(60_000)
                    val previsto = counter.remindAtMs
                    if (!counter.reminderEligible() || step == null || previsto == null) {
                        dao.saveLocal(counter.copy(remindAtMs = null, remindCount = 0))
                        return@forEach
                    }
                    val mandati = counter.remindCount + 1
                    if (!counter.hidden) Notifications.notifyReminder(context, counter, mandati, adesso)
                    dao.saveLocal(
                        counter.copy(
                            remindCount = mandati,
                            remindAtMs = nextReminderAt(previsto, adesso, mandati, step),
                        )
                    )
                }
                AlarmScheduler.scheduleNext(context)
            } finally {
                result.finish()
            }
        }
    }
}
