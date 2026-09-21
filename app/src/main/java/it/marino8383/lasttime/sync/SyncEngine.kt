package it.marino8383.lasttime.sync

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import it.marino8383.lasttime.LastTimeApp
import it.marino8383.lasttime.data.Counter
import it.marino8383.lasttime.data.Round
import it.marino8383.lasttime.notif.AlarmScheduler
import it.marino8383.lasttime.notif.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Tiene allineati i contatori condivisi finche' l'app e' aperta.
 *
 * Il verso remoto -> locale passa dai listener di Firestore, che sono istantanei.
 * Il verso locale -> remoto e' esplicito: chi scrive chiama [pushIfShared].
 *
 * Gli allarmi restano una faccenda locale: dal gruppo arriva lo *stato* (quando e'
 * ripartito il timer, ogni quanto suona), e ogni telefono si programma la sua sveglia.
 * Cosi' l'app continua a funzionare offline come ha sempre fatto.
 */
object SyncEngine {

    private const val TAG = "LastTimeSync"

    /** Oltre questo scarto un evento non e' "appena accaduto" e non merita una notifica. */
    private const val NOTIFICA_FRESCA_MS = 10 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listeners = mutableMapOf<String, ListenerRegistration>()

    /**
     * Ultimo updatedMs che abbiamo gia' allineato, per uuid. Serve a non rimbalzare:
     * quando applichiamo una modifica remota il DB locale cambia, e senza questa
     * memoria la rimanderemmo indietro all'infinito.
     */
    private val aligned = mutableMapOf<String, Long>()

    private val db get() = FirebaseFirestore.getInstance()

    /** Attacca i listener ai gruppi di cui fa parte almeno un contatore locale. */
    fun start(context: Context) {
        if (Cloud.uid == null) return
        val app = context.applicationContext as LastTimeApp
        scope.launch {
            val groups = app.db.counterDao().shared().mapNotNull { it.sharedGroupId }.toSet()
            groups.forEach { listen(app, it) }
        }
    }

    fun stop() {
        listeners.values.forEach { it.remove() }
        listeners.clear()
    }

    /** Manda su lo storico che questo contatore aveva gia', al momento della condivisione. */
    suspend fun pushHistory(app: LastTimeApp, counter: Counter) {
        val groupId = counter.sharedGroupId ?: return
        app.db.roundDao().recentFor(counter.id, 200).forEach { round ->
            try {
                Groups.pushRound(groupId, round, counter.uuid)
            } catch (t: Throwable) {
                Log.w(TAG, "push storico fallito", t)
            }
        }
    }

    fun listen(app: LastTimeApp, groupId: String) {
        if (listeners.containsKey("rounds:" + groupId)) return
        listeners["rounds:" + groupId] = db.collection("groups").document(groupId)
            .collection("rounds")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.w(TAG, "listener eventi del gruppo $groupId in errore", error)
                    return@addSnapshotListener
                }
                snap ?: return@addSnapshotListener
                scope.launch {
                    // Solo aggiunte: gli eventi sono append-only, una rimozione non esiste
                    snap.documentChanges
                        .filter { it.type != DocumentChange.Type.REMOVED }
                        .forEach { applyRemoteRound(app, it.document.data) }
                }
            }

        if (listeners.containsKey(groupId)) return
        listeners[groupId] = db.collection("groups").document(groupId)
            .collection("counters")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.w(TAG, "listener del gruppo $groupId in errore", error)
                    return@addSnapshotListener
                }
                snap ?: return@addSnapshotListener
                scope.launch {
                    // Di proposito non tocca SyncStatus: l'indicatore deve misurare i giri
                    // completi di allineamento, non l'arrivo di un dato. Se il listener
                    // marcasse "aggiornato" all'aggancio, all'apertura dell'app leggeresti
                    // sempre "adesso" e non sapresti mai se il worker sta lavorando.
                    snap.documentChanges.forEach { change ->
                        if (change.type == DocumentChange.Type.REMOVED) {
                            detach(app, change.document.id)
                        } else {
                            applyRemote(app, groupId, change.document.data)
                        }
                    }
                }
            }
    }

    suspend fun applyRemote(app: LastTimeApp, groupId: String, data: Map<String, Any?>?) {
        data ?: return
        val uuid = data["uuid"] as? String ?: return
        val remoteUpdated = (data["updatedMs"] as? Number)?.toLong() ?: return
        val dao = app.db.counterDao()
        val local = dao.byUuid(uuid)

        val name = data["name"] as? String ?: return
        val startMs = (data["startMs"] as? Number)?.toLong() ?: return
        val bellMinutes = (data["bellMinutes"] as? Number)?.toLong()
        val bellMode = data["bellMode"] as? String ?: "INTERVAL"
        val bellRepeat = data["bellRepeat"] as? Boolean ?: true

        if (local == null) {
            // Prima volta che vediamo questo contatore: e' entrato nel gruppo da un altro
            // telefono. Nasce qui con la campanella calcolata in locale.
            val step = bellMinutes?.times(60_000)
            dao.insertRaw(
                Counter(
                    uuid = uuid,
                    name = name,
                    startMs = startMs,
                    bellMinutes = bellMinutes,
                    bellMode = bellMode,
                    bellRepeat = bellRepeat,
                    nextBellAtMs = if (step != null) startMs + step else null,
                    createdMs = System.currentTimeMillis(),
                    updatedMs = remoteUpdated,
                    sharedGroupId = groupId,
                )
            )
            aligned[uuid] = remoteUpdated
            AlarmScheduler.scheduleNext(app)
            return
        }

        // Last-write-wins: se la nostra copia e' piu' recente, la remota non ci interessa
        if (remoteUpdated <= local.updatedMs) return

        val movedStart = startMs != local.startMs
        val step = bellMinutes?.times(60_000)
        var updated = local.copy(
            name = name,
            startMs = startMs,
            bellMinutes = bellMinutes,
            bellMode = bellMode,
            bellRepeat = bellRepeat,
            updatedMs = remoteUpdated,
            sharedGroupId = groupId,
        )
        // L'altro ha fatto ripartire il timer: qui lo "sforato" si spegne da solo e la
        // sveglia si rifa'. Una FIXED tiene il suo ritmo, come ovunque nell'app.
        if (movedStart && step != null && bellMode != "FIXED") {
            updated = updated.copy(
                nextBellAtMs = startMs + step,
                bellNotified = false,
                snoozeUntilMs = null,
            )
        }
        dao.updateRaw(updated) // updateRaw: updatedMs e' quello remoto, non va ritimbrato
        aligned[uuid] = remoteUpdated
        AlarmScheduler.scheduleNext(app)
    }

    /**
     * Un giro completo di allineamento, senza listener: serve al worker periodico, che
     * gira ad app chiusa e non puo' tenere un collegamento aperto.
     *
     * Prima tira giu', poi manda su. L'ordine conta: spedire per primi significherebbe
     * poter sovrascrivere con una copia vecchia una modifica piu' recente dell'altro.
     */
    suspend fun syncOnce(app: LastTimeApp): Boolean {
        SyncStatus.start()
        Cloud.ensureSignedIn() ?: run {
            SyncStatus.fallito()
            return false
        }
        val locali = app.db.counterDao().shared()
        if (locali.isEmpty()) {
            SyncStatus.fallito()
            return true
        }

        locali.groupBy { it.sharedGroupId }.forEach { (groupId, contatori) ->
            groupId ?: return@forEach
            try {
                val remoti = db.collection("groups").document(groupId)
                    .collection("counters").get().await()
                    .documents.associate { doc -> doc.id to doc.data }

                // remoto -> locale
                remoti.values.forEach { applyRemote(app, groupId, it) }

                // locale -> remoto, solo dove siamo davvero piu' avanti
                Groups.recentRounds(groupId).forEach { applyRemoteRound(app, it) }

                contatori.forEach { locale ->
                    val remotoUpdated =
                        (remoti[locale.uuid]?.get("updatedMs") as? Number)?.toLong() ?: -1
                    val aggiornato = app.db.counterDao().byUuid(locale.uuid) ?: locale
                    if (aggiornato.updatedMs > remotoUpdated) {
                        Groups.push(groupId, aggiornato)
                        aligned[aggiornato.uuid] = aggiornato.updatedMs
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "allineamento del gruppo $groupId fallito", t)
                SyncStatus.fallito()
                return false
            }
        }
        AlarmScheduler.scheduleNext(app)
        SyncStatus.ok(app)
        return true
    }

    /**
     * Un evento arrivato dal gruppo. La deduplica e' l'uuid: lo stesso evento inviato da
     * due telefoni e' lo stesso documento, quindi non si sdoppia mai.
     */
    private suspend fun applyRemoteRound(app: LastTimeApp, data: Map<String, Any?>?) {
        data ?: return
        val uuid = data["uuid"] as? String ?: return
        if (app.db.roundDao().byUuid(uuid) != null) return
        val counterUuid = data["counterUuid"] as? String ?: return
        val counter = app.db.counterDao().byUuid(counterUuid) ?: return
        val startMs = (data["startMs"] as? Number)?.toLong() ?: return
        val endMs = (data["endMs"] as? Number)?.toLong() ?: return
        val byName = data["byName"] as? String
        app.db.roundDao().insert(
            Round(
                uuid = uuid,
                counterId = counter.id,
                startMs = startMs,
                endMs = endMs,
                noTime = data["noTime"] as? Boolean ?: false,
                byName = byName,
            )
        )

        // Avviso "l'ha fatto ripartire un altro". La finestra di freschezza serve a non
        // sparare 200 notifiche quando arriva lo storico di un timer appena condiviso:
        // quelli sono eventi vecchi, non e' appena successo niente.
        val appenaFatto = System.currentTimeMillis() - endMs <= NOTIFICA_FRESCA_MS
        if (counter.notifyOnRemote && appenaFatto && !byName.isNullOrBlank() &&
            byName != Cloud.myName
        ) {
            Notifications.notifySharedRestart(app, counter, byName, endMs)
        }
    }

    /** Manda su un evento appena registrato, se il contatore e' condiviso. */
    fun pushRoundIfShared(counter: Counter, round: Round) {
        val groupId = counter.sharedGroupId ?: return
        scope.launch {
            try {
                Groups.pushRound(groupId, round, counter.uuid)
            } catch (t: Throwable) {
                Log.w(TAG, "push dell'evento ${round.uuid} fallito", t)
            }
        }
    }

    /**
     * Il contatore e' uscito dal gruppo: qui resta, con tutto il suo storico, e torna
     * autonomo. Non si cancella mai niente per conto di qualcun altro — chi elimina un
     * timer condiviso elimina il suo, non il tuo.
     *
     * updateRaw e non save: non c'e' piu' nessun posto dove mandare questa modifica, e
     * ritimbrare updatedMs falserebbe i confronti se un domani si ricondividesse.
     */
    private suspend fun detach(app: LastTimeApp, uuid: String) {
        val locale = app.db.counterDao().byUuid(uuid) ?: return
        if (locale.sharedGroupId == null) return
        app.db.counterDao().updateRaw(locale.copy(sharedGroupId = null))
        aligned.remove(uuid)
        SyncWorker.refresh(app)
    }

    /** Manda su la nostra versione, se questo contatore e' condiviso. */
    fun pushIfShared(counter: Counter) {
        val groupId = counter.sharedGroupId ?: return
        if (aligned[counter.uuid] == counter.updatedMs) return // e' roba appena arrivata
        scope.launch {
            try {
                Groups.push(groupId, counter)
                aligned[counter.uuid] = counter.updatedMs
            } catch (t: Throwable) {
                Log.w(TAG, "push del contatore ${counter.uuid} fallito", t)
            }
        }
    }
}
