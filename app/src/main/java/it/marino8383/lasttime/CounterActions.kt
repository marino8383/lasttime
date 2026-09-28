package it.marino8383.lasttime

import android.content.Context
import it.marino8383.lasttime.data.Counter
import it.marino8383.lasttime.data.CounterMode
import it.marino8383.lasttime.data.LateBellChoice
import it.marino8383.lasttime.data.Round
import it.marino8383.lasttime.data.add
import it.marino8383.lasttime.data.changedState
import it.marino8383.lasttime.data.doneTargetMs
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
 * Gli Stati hanno il loro ingresso, [changeState]; [restart] li rimanda lì.
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
        if (counter.mode == CounterMode.STATI) {
            // Uno Stati non "riparte": al massimo resta nello stesso stato con un
            // intervallo nuovo (un reset programmato arrivato da chissà dove).
            changeState(context, counter, counter.currentState, atMs, counter.currentNote)
            return
        }
        if (counter.mode != CounterMode.GIORNALIERO && counter.startMs > atMs) {
            // Timer creato per partire più tardi (quick pick su un orario tondo futuro) e
            // non ancora partito: non c'è nessun giro da chiudere, parte semplicemente da qui.
            db.counterDao().save(counter.restartedAt(atMs))
        } else if (counter.mode == CounterMode.GIORNALIERO) {
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
     * Modalità STATI: da [atMs] il contatore è in [state] (null = nessuno). L'intervallo
     * dello stato vecchio si chiude nello storico con la sua nota, e diventa l'ultimo round
     * — così Correggi/Annulla dallo storico funzionano come per un riavvio.
     */
    suspend fun changeState(context: Context, counter: Counter, state: String?, atMs: Long, note: String? = null) {
        val db = db(context)
        var next = counter.changedState(state, atMs, note)
        if (counter.startMs < atMs) {
            val round = db.roundDao().add(closingRound(counter, atMs), counter)
            next = next.copy(lastRoundUuid = round.uuid, lastRoundStartMs = round.startMs)
        }
        db.counterDao().save(next)
        Notifications.cancel(context, counter.id)
        AlarmScheduler.scheduleNext(context)
    }

    /** Il round che chiude l'intervallo in corso ad [endMs]; sugli Stati porta con sé stato e nota. */
    private fun closingRound(counter: Counter, endMs: Long) = Round(
        counterId = counter.id,
        startMs = counter.startMs,
        endMs = endMs,
        state = counter.currentState,
        note = counter.currentNote,
    )

    /**
     * "Fatto" toccato a [now], dalla card o dalla notifica. Se il timer arrotonda
     * (Counter.roundMinutes) vale l'orario tondo più vicino: nel passato riparte da lì,
     * nel futuro diventa un reset programmato a quell'ora — il timer continua a contare
     * fino ad allora, e sui condivisi l'altro vede il "riparte alle…".
     */
    suspend fun done(context: Context, counter: Counter, now: Long, rhythm: Rhythm = Rhythm.Auto) {
        val target = counter.doneTargetMs(now)
        if (target > now) {
            db(context).counterDao().save(counter.copy(scheduledResetMs = target))
            Notifications.cancel(context, counter.id)
            AlarmScheduler.scheduleNext(context)
        } else {
            restart(context, counter, target, rhythm)
        }
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
        // né su un timer che doveva ancora partire: non c'è un giro da chiudere
        if (counter.mode != CounterMode.GIORNALIERO && counter.startMs < now) {
            val round = db.roundDao().add(closingRound(counter, now), counter)
            archived = archived.copy(lastRoundUuid = round.uuid, lastRoundStartMs = round.startMs)
        }
        db.counterDao().save(archived)
        Notifications.cancel(context, counter.id)
        AlarmScheduler.scheduleNext(context)
    }
}
