package it.marino8383.lasttime

import android.content.Context
import it.marino8383.lasttime.data.Counter
import it.marino8383.lasttime.data.CounterMode
import it.marino8383.lasttime.data.LateBellChoice
import it.marino8383.lasttime.data.Round
import it.marino8383.lasttime.data.add
import it.marino8383.lasttime.data.addDailyPoint
import it.marino8383.lasttime.data.loggedDaily
import it.marino8383.lasttime.data.restarted
import it.marino8383.lasttime.data.restartedAt
import it.marino8383.lasttime.data.restartedWithChoice
import it.marino8383.lasttime.data.save
import it.marino8383.lasttime.notif.AlarmScheduler
import it.marino8383.lasttime.notif.Notifications
import it.marino8383.lasttime.sync.SyncEngine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Le azioni che chiudono un round e fanno ripartire un timer, in un posto solo.
 *
 * Le usano il ViewModel e i receiver delle notifiche allo stesso modo: prima ognuno
 * aveva la sua copia della sequenza "chiudi il round → nuovo stato → segna l'ultimo
 * round → salva → togli la notifica → riprogramma", e ognuna ne saltava un pezzo
 * diverso. Il Fatto dalla notifica, per esempio, non segnava l'ultimo round — e
 * "Annulla l'ultimo riavvio" andava poi a togliere la dose precedente.
 *
 * Una modalità nuova (Periodi) si aggiunge qui come un ramo in più di [restart].
 */
object CounterActions {

    /** Come far ripartire la campanella dopo il riavvio. */
    sealed interface Rhythm {
        /** Fatto spontaneo: soglia anticipo/ritardo e bellMode decidono da soli. */
        data object Auto : Rhythm
        /** Istante scelto a mano (Riparti avanzato): la scadenza si ricalcola da lì. */
        data object FromInstant : Rhythm
        /** Scelta esplicita fatta in LateBellDialog. */
        data class Choice(val choice: LateBellChoice) : Rhythm
    }

    private fun db(context: Context) = (context.applicationContext as LastTimeApp).db

    /**
     * La copia più recente del contatore. Si rilegge sempre dal database: quella che
     * arriva dalla UI può essere vecchia — un gestore di gesti in Compose sopravvive
     * alle ricomposizioni e continua a consegnare il contatore com'era quando è stato
     * creato — e riscriverla tale e quale riporterebbe indietro campi cambiati nel
     * frattempo, sharedGroupId per primo.
     *
     * Con [askGroup], su un condiviso si chiede prima al gruppo com'è messo: l'altro
     * potrebbe averlo già fatto ripartire e questo telefono non saperlo ancora. Timeout
     * corto: senza rete si procede con quello che c'è.
     */
    suspend fun latest(context: Context, counter: Counter, askGroup: Boolean = false): Counter {
        val dao = db(context).counterDao()
        var c = dao.byId(counter.id) ?: counter
        val groupId = c.sharedGroupId
        if (askGroup && groupId != null) {
            withTimeoutOrNull(4_000) { SyncEngine.checkCounter(context.applicationContext as LastTimeApp, groupId, c.uuid) }
            c = dao.byId(c.id) ?: c
        }
        return c
    }

    /**
     * Chiude il round in corso ad [atMs] e fa ripartire il timer da lì, secondo la
     * modalità del contatore. Il chiamante passa già la copia fresca (vedi [latest]).
     *
     * - PRECISO: round [startMs, atMs] nello storico, campanella secondo [rhythm], e il
     *   round diventa "l'ultimo" per Correggi/Annulla.
     * - GIORNALIERO: un punto in più nel calendario ("+1"); [rhythm] non conta, la
     *   campanella riparte all'orario fisso dei giorni configurati.
     */
    suspend fun restart(context: Context, counter: Counter, atMs: Long, rhythm: Rhythm = Rhythm.Auto) {
        val db = db(context)
        if (counter.mode == CounterMode.GIORNALIERO) {
            db.roundDao().addDailyPoint(Round(counterId = counter.id, startMs = atMs, endMs = atMs), counter)
            db.counterDao().save(counter.loggedDaily(atMs))
        } else {
            val round = db.roundDao().add(Round(counterId = counter.id, startMs = counter.startMs, endMs = atMs), counter)
            val next = when (rhythm) {
                Rhythm.Auto -> counter.restarted(atMs, AppSettings.latePercent(context))
                Rhythm.FromInstant -> counter.restartedAt(atMs)
                is Rhythm.Choice -> counter.restartedWithChoice(atMs, rhythm.choice)
            }
            db.counterDao().save(next.copy(lastRoundUuid = round.uuid, lastRoundStartMs = round.startMs))
        }
        Notifications.cancel(context, counter.id)
        AlarmScheduler.scheduleNext(context)
    }

    /**
     * Ferma e archivia: il round in corso viene chiuso e loggato, poi il timer si congela.
     * Le query di campanella e reset programmato filtrano già archived = 0, ma il reset
     * pendente va cancellato o al ripristino scatterebbe subito perché ormai nel passato.
     *
     * Su un Giornaliero non si chiude niente: lì un round è un evento ("+1"), e
     * archiviare non è un evento — prima ne aggiungeva uno finto alla data di oggi.
     */
    suspend fun archive(context: Context, counter: Counter) {
        val db = db(context)
        val now = System.currentTimeMillis()
        var archived = counter.copy(
            archived = true,
            archivedMs = now,
            snoozeUntilMs = null,
            scheduledResetMs = null,
        )
        if (counter.mode != CounterMode.GIORNALIERO) {
            val round = db.roundDao().add(Round(counterId = counter.id, startMs = counter.startMs, endMs = now), counter)
            archived = archived.copy(lastRoundUuid = round.uuid, lastRoundStartMs = round.startMs)
        }
        db.counterDao().save(archived)
        Notifications.cancel(context, counter.id)
        AlarmScheduler.scheduleNext(context)
    }
}
