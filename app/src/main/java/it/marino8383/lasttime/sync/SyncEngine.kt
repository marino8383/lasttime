package it.marino8383.lasttime.sync

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.ListenerRegistration
import it.marino8383.lasttime.AppSettings
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

    /**
     * L'ultimo errore di scrittura verso il gruppo, per la diagnostica.
     *
     * I push falliscono in silenzio di proposito — un problema di rete passeggero non deve
     * disturbare nessuno — ma un rifiuto stabile resta invisibile e da fuori sembra solo
     * che l'app "non si allinei". Qui almeno si puo' leggere cosa ha risposto il server.
     */
    @Volatile
    var ultimoErrorePush: String? = null
        private set

    /**
     * Diario degli sganciamenti e delle ricuciture, per la diagnostica. Serve a rispondere
     * a una domanda sola: quando un timer smette di risultare condiviso, e' stato questo
     * codice a deciderlo oppure e' successo altrove? Senza, le due cose sono
     * indistinguibili da fuori.
     */
    private val diario = ArrayDeque<String>()

    fun diario(): List<String> = synchronized(diario) { diario.toList() }

    private fun annota(riga: String) = synchronized(diario) {
        diario.addLast(riga)
        while (diario.size > 8) diario.removeFirst()
    }

    /** Eventi arrivati prima del loro contatore, in attesa di poter essere inseriti. */
    private val pendingRounds = java.util.concurrent.ConcurrentLinkedQueue<Map<String, Any?>>()
    private const val MAX_PENDING = 500

    private suspend fun drainPendingRounds(app: LastTimeApp) {
        if (pendingRounds.isEmpty()) return
        val da = ArrayList<Map<String, Any?>>(pendingRounds.size)
        while (true) {
            val e = pendingRounds.poll() ?: break
            da.add(e)
        }
        // chi ancora non trova il suo contatore si ri-accoda da sé
        da.forEach { applyRemoteRound(app, it) }
    }

    private val db get() = FirebaseFirestore.getInstance()

    private fun nowClock() = it.marino8383.lasttime.formatClock(System.currentTimeMillis())

    /**
     * Attacca i listener a tutti i gruppi di cui faccio parte — non solo a quelli da cui
     * ho gia' un contatore in casa. Un telefono appena entrato, o rimasto senza condivisi,
     * deve comunque ricevere quello che gli viene condiviso.
     */
    fun start(context: Context) {
        if (Cloud.uid == null) return
        val app = context.applicationContext as LastTimeApp
        scope.launch {
            gruppiNoti(app).forEach { listen(app, it) }
        }
    }

    private suspend fun gruppiNoti(app: LastTimeApp): Set<String> =
        AppSettings.groups(app) + app.db.counterDao().shared().mapNotNull { it.sharedGroupId }

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
        if (listeners.containsKey(groupId)) return

        // I contatori si agganciano per primi, di proposito: un evento che arriva prima
        // del suo contatore non avrebbe a cosa attaccarsi. La coda sotto copre comunque
        // il caso, ma l'ordine giusto fa sì che di solito non serva.
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
                            detach(app, groupId, change.document.id)
                        } else {
                            applyRemote(app, groupId, change.document.data)
                        }
                    }
                    // ora che i contatori ci sono, gli eventi orfani hanno dove andare
                    drainPendingRounds(app)
                }
            }

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
    }

    suspend fun applyRemote(app: LastTimeApp, groupId: String, data: Map<String, Any?>?) {
        data ?: return
        val uuid = data["uuid"] as? String ?: return

        // L'abbiamo tolto noi da questo telefono: se il server ce lo ripropone ancora,
        // vuol dire che la rimozione non era andata a buon fine (rete assente al momento).
        // Si ritenta la rimozione, non lo si fa rientrare con tanto di notifiche.
        if (AppSettings.removedFromGroup(app).contains(uuid)) {
            scope.launch {
                try {
                    Groups.remove(groupId, uuid)
                    AppSettings.clearRemovedFromGroup(app, uuid)
                    annota("${nowClock()} rimozione di $uuid ritentata con successo")
                } catch (t: Throwable) {
                    Log.w(TAG, "ritentativo di rimozione di $uuid fallito", t)
                }
            }
            return
        }

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
                    archived = data["archived"] as? Boolean ?: false,
                    archivedMs = (data["archivedMs"] as? Number)?.toLong(),
                    historyFromMs = (data["historyFromMs"] as? Number)?.toLong(),
                    lastRoundUuid = data["lastRoundUuid"] as? String,
                    lastRoundStartMs = (data["lastRoundStartMs"] as? Number)?.toLong(),
                )
            )
            aligned[uuid] = remoteUpdated
            AlarmScheduler.scheduleNext(app)
            return
        }

        // Ricucitura: il contatore c'e', ma qui non risulta piu' condiviso mentre nel
        // gruppo il suo documento esiste ancora. Vuol dire che si e' sganciato per errore.
        // Si riattacca subito, senza aspettare che arrivi una modifica piu' recente —
        // altrimenti due copie allineate resterebbero separate per sempre, ognuna
        // convinta di essere a posto.
        if (local.sharedGroupId == null) {
            Log.i(TAG, "contatore $uuid riagganciato al gruppo $groupId")
            annota("${nowClock()} riagganciato ${local.name}")
            val riagganciato = local.copy(sharedGroupId = groupId)
            dao.updateRaw(riagganciato)
            SyncWorker.refresh(app)
            // Mentre era sganciato le modifiche restavano in casa: pushIfShared esce
            // subito se il contatore non e' condiviso. Ora che lo e' di nuovo, quello che
            // e' successo nel frattempo va mandato su, altrimenti resta indietro per
            // sempre — nessuno lo rimanderebbe mai.
            if (riagganciato.updatedMs > remoteUpdated) {
                annota("${nowClock()} rimando su ${local.name}, era indietro sul server")
                pushIfShared(riagganciato)
            }
        }

        // Last-write-wins: se la nostra copia e' piu' recente, la remota non ci interessa
        if (remoteUpdated <= local.updatedMs) return

        val movedStart = startMs != local.startMs
        val step = bellMinutes?.times(60_000)
        val archived = data["archived"] as? Boolean ?: false
        var updated = local.copy(
            name = name,
            startMs = startMs,
            bellMinutes = bellMinutes,
            bellMode = bellMode,
            bellRepeat = bellRepeat,
            updatedMs = remoteUpdated,
            sharedGroupId = groupId,
            // Archivio condiviso: se l'altro ha chiuso il ciclo, si chiude anche qui
            archived = archived,
            archivedMs = (data["archivedMs"] as? Number)?.toLong(),
            historyFromMs = (data["historyFromMs"] as? Number)?.toLong(),
            lastRoundUuid = data["lastRoundUuid"] as? String,
            lastRoundStartMs = (data["lastRoundStartMs"] as? Number)?.toLong(),
        )
        // Quando rifare la scadenza locale. Oltre al caso ovvio — l'altro ha fatto
        // ripartire il timer, e qui lo "sforato" si spegne da solo — ce ne sono due che
        // erano scoperti:
        //  - la campanella e' stata configurata o cambiata dall'altro dopo la condivisione
        //  - non abbiamo nessuna scadenza, tipico di un contatore arrivato senza campanella
        //    e configurato solo dopo: il badge compariva ma non c'era niente da suonare
        // Una FIXED tiene il suo ritmo quando si sposta solo l'inizio, ma se il ritmo
        // stesso cambia o manca del tutto va comunque ricalcolata.
        val cambiataCampanella = bellMinutes != local.bellMinutes
        val senzaScadenza = local.nextBellAtMs == null
        val rifasa = step != null &&
            (cambiataCampanella || senzaScadenza || (movedStart && bellMode != "FIXED"))
        if (rifasa) {
            updated = updated.copy(
                nextBellAtMs = startMs + step!!,
                bellNotified = false,
                snoozeUntilMs = null,
            )
        } else if (step == null) {
            // campanella tolta dall'altro: qui sparisce anche la scadenza
            updated = updated.copy(nextBellAtMs = null, bellNotified = false, snoozeUntilMs = null)
        }
        dao.updateRaw(updated) // updateRaw: updatedMs e' quello remoto, non va ritimbrato
        aligned[uuid] = remoteUpdated
        // Appena archiviato da un altro: via anche l'eventuale notifica ancora a video
        if (archived && !local.archived) Notifications.cancel(app, local.id)
        // Tornato in linea: l'altro deve saperlo, perché da adesso il timer conta di nuovo
        // e la campanella ricomincia a suonare anche a lui.
        if (local.archived && !archived && local.notifyOnRemote) {
            val chi = (data["lastByName"] as? String)?.takeIf { it.isNotBlank() && it != Cloud.myName }
            if (chi != null) {
                Notifications.notifySharedEvent(app, updated, chi, "Ripreso dall'archivio: il timer conta di nuovo.")
            }
        }
        AlarmScheduler.scheduleNext(app)
    }

    /**
     * Verifica leggera di un solo contatore prima di farlo ripartire: serve solo a sapere
     * se nel frattempo e' gia' stato riavviato altrove, non un giro di allineamento intero.
     * A differenza di [syncOnce] non tocca gli altri gruppi ne' lo storico dei round, quindi
     * resta rapida anche con piu' timer condivisi in casa.
     */
    suspend fun checkCounter(app: LastTimeApp, groupId: String, uuid: String) {
        Cloud.ensureSignedIn() ?: return
        try {
            val doc = db.collection("groups").document(groupId)
                .collection("counters").document(uuid)
                .get().await()
            doc.data?.let { applyRemote(app, groupId, it) }
        } catch (t: Throwable) {
            Log.w(TAG, "verifica del contatore $uuid prima del riavvio fallita", t)
        }
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
        val gruppi = gruppiNoti(app)
        if (gruppi.isEmpty()) {
            SyncStatus.fallito()
            return true
        }
        gruppi.forEach { groupId ->
            try {
                val remoti = db.collection("groups").document(groupId)
                    .collection("counters").get().await()
                    .documents.associate { doc -> doc.id to doc.data }

                // remoto -> locale
                remoti.values.forEach { applyRemote(app, groupId, it) }

                // La lista locale si rilegge qui, non prima: applyRemote puo' aver appena
                // riagganciato un contatore, e con una lista vecchia resterebbe fuori dal
                // giro di invio proprio nel momento in cui ne ha piu' bisogno.
                val contatori = app.db.counterDao().shared()
                    .filter { it.sharedGroupId == groupId }

                // locale -> remoto, solo dove siamo davvero piu' avanti
                Groups.recentRounds(groupId).forEach { applyRemoteRound(app, it) }
                drainPendingRounds(app)

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
     * due telefoni e' lo stesso documento, quindi non si sdoppia mai — ma se esiste gia'
     * e l'endMs e' diverso, e' una correzione dell'ultimo riavvio arrivata da un altro
     * telefono: e' l'unico cambiamento che un round puo' subire dopo essere stato scritto.
     */
    private suspend fun applyRemoteRound(app: LastTimeApp, data: Map<String, Any?>?) {
        data ?: return
        val uuid = data["uuid"] as? String ?: return
        val endMs = (data["endMs"] as? Number)?.toLong() ?: return
        val esistente = app.db.roundDao().byUuid(uuid)
        if (esistente != null) {
            if (esistente.endMs != endMs) app.db.roundDao().correctEndMs(esistente.id, endMs)
            return
        }
        val counterUuid = data["counterUuid"] as? String ?: return
        val counter = app.db.counterDao().byUuid(counterUuid)
        if (counter == null) {
            // Il contatore non è ancora arrivato: l'evento aspetta invece di essere perso.
            // Era il bug per cui chi entrava in un gruppo vedeva lo storico vuoto: i round
            // arrivavano prima del contatore e venivano scartati, senza più tornare.
            if (pendingRounds.size < MAX_PENDING) pendingRounds.add(data)
            return
        }
        val startMs = (data["startMs"] as? Number)?.toLong() ?: return
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
                ultimoErrorePush = null
            } catch (t: Throwable) {
                Log.w(TAG, "push dell'evento ${round.uuid} fallito", t)
                ultimoErrorePush = "evento di ${counter.name}: ${t.message}"
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
    private suspend fun detach(app: LastTimeApp, groupId: String, uuid: String) {
        val locale = app.db.counterDao().byUuid(uuid) ?: return
        if (locale.sharedGroupId == null) return

        // Firestore segnala una rimozione anche quando la sua cache si riallinea col
        // server, non solo quando qualcuno ha davvero smesso di condividere. Prendere
        // per buono quel segnale significa sganciare un timer per una riconnessione
        // sfortunata — in silenzio, e senza che l'utente capisca perché da quel momento
        // non si sincronizza più. Prima di toccare qualcosa si chiede al server.
        val esiste = try {
            db.collection("groups").document(groupId)
                .collection("counters").document(uuid)
                .get(Source.SERVER).await().exists()
        } catch (t: Throwable) {
            // Senza risposta non si decide: meglio restare condivisi e riprovare dopo
            Log.w(TAG, "verifica della rimozione di $uuid non riuscita", t)
            annota("${nowClock()} rimozione di ${locale.name}: verifica fallita, ignorata")
            return
        }
        if (esiste) {
            Log.i(TAG, "rimozione di $uuid ignorata: sul server il documento c'e' ancora")
            annota("${nowClock()} rimozione di ${locale.name} ignorata: c'e' ancora")
            return
        }

        annota("${nowClock()} SGANCIATO ${locale.name}: sul server non c'e' piu'")
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
                ultimoErrorePush = null
            } catch (t: Throwable) {
                Log.w(TAG, "push del contatore ${counter.uuid} fallito", t)
                ultimoErrorePush = "contatore ${counter.name}: ${t.message}"
            }
        }
    }
}
