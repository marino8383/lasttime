package it.marino8383.lasttime.sync

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import it.marino8383.lasttime.LastTimeApp
import it.marino8383.lasttime.data.Counter
import it.marino8383.lasttime.notif.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

    fun listen(app: LastTimeApp, groupId: String) {
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
                    snap.documents.forEach { doc -> applyRemote(app, groupId, doc.data) }
                }
            }
    }

    private suspend fun applyRemote(app: LastTimeApp, groupId: String, data: Map<String, Any?>?) {
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
