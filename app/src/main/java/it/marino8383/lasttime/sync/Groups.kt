package it.marino8383.lasttime.sync

import com.google.firebase.firestore.FirebaseFirestore
import it.marino8383.lasttime.data.Counter
import it.marino8383.lasttime.data.Round
import kotlinx.coroutines.tasks.await

/**
 * Struttura su Firestore:
 *
 *   groups/{groupId}                      gruppo di condivisione (una coppia, una famiglia)
 *   groups/{groupId}/members/{uid}        chi ne fa parte, col nome che ha scelto
 *   groups/{groupId}/counters/{uuid}      i contatori condivisi in quel gruppo
 *   invites/{codice}                      codice d'ingresso, scade dopo 24 ore
 *
 * Un gruppo puo' contenere piu' contatori: si invita una persona una volta sola e poi
 * ci si condivide dentro quello che serve, senza rifare il giro dell'invito.
 */
object Groups {

    private const val INVITE_HOURS = 24L

    /** Alfabeto senza caratteri che si confondono a mano: niente I, O, 0, 1. */
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val CODE_LENGTH = 6

    private val db get() = FirebaseFirestore.getInstance()

    private fun newCode(): String =
        (1..CODE_LENGTH).map { ALPHABET.random() }.joinToString("")

    /** Crea un gruppo nuovo con me dentro. Ritorna l'id del gruppo. */
    suspend fun create(myName: String): String {
        val uid = Cloud.uid ?: error("nessuna identita': cloud non collegato")
        val now = System.currentTimeMillis()
        val group = db.collection("groups").document()
        group.set(mapOf("createdBy" to uid, "createdMs" to now)).await()
        // Il primo membro non passa da un invito: e' chi ha creato il gruppo.
        group.collection("members").document(uid)
            .set(mapOf("name" to myName, "joinedMs" to now)).await()
        return group.id
    }

    /** Genera un codice d'ingresso per [groupId], valido 24 ore. */
    suspend fun invite(groupId: String): String {
        val uid = Cloud.uid ?: error("nessuna identita': cloud non collegato")
        val code = newCode()
        db.collection("invites").document(code).set(
            mapOf(
                "groupId" to groupId,
                "createdBy" to uid,
                "createdMs" to System.currentTimeMillis(),
                "expiresMs" to System.currentTimeMillis() + INVITE_HOURS * 3_600_000,
            )
        ).await()
        return code
    }

    sealed interface JoinResult {
        data class Ok(val groupId: String) : JoinResult
        data object CodeNotFound : JoinResult
        data object Expired : JoinResult
        data class Failed(val message: String) : JoinResult
    }

    /**
     * Entra in un gruppo con un codice. Il codice viene riscritto dentro il documento
     * del membro perche' le regole di sicurezza lo rileggano: e' l'unico modo che ha il
     * server di verificare che un non membro abbia davvero il diritto di entrare.
     */
    suspend fun join(code: String, myName: String): JoinResult {
        val uid = Cloud.uid ?: return JoinResult.Failed("cloud non collegato")
        val clean = code.trim().uppercase()
        return try {
            val snap = db.collection("invites").document(clean).get().await()
            if (!snap.exists()) return JoinResult.CodeNotFound
            val groupId = snap.getString("groupId") ?: return JoinResult.CodeNotFound
            val expires = snap.getLong("expiresMs") ?: 0
            if (expires <= System.currentTimeMillis()) return JoinResult.Expired

            db.collection("groups").document(groupId)
                .collection("members").document(uid)
                .set(
                    mapOf(
                        "name" to myName,
                        "joinedMs" to System.currentTimeMillis(),
                        "code" to clean,
                    )
                ).await()
            JoinResult.Ok(groupId)
        } catch (t: Throwable) {
            JoinResult.Failed(t.message ?: "ingresso fallito")
        }
    }

    /** Nomi dei membri di un gruppo, per uid. */
    suspend fun members(groupId: String): Map<String, String> = try {
        db.collection("groups").document(groupId).collection("members").get().await()
            .documents.associate { it.id to (it.getString("name") ?: "?") }
    } catch (t: Throwable) {
        emptyMap()
    }

    /**
     * Solo i campi condivisi, elencati in [SyncedCounter]; qui si aggiungono quelli con
     * regole proprie. Restano fuori di proposito viewMode, bellEnabled, bellNotified e
     * snoozeUntilMs, che sono scelte del singolo telefono.
     *
     * nextBellAtMs viaggia calcolato, non ricalcolato da ogni telefono per conto suo:
     * le correzioni manuali (Riparti avanzato, correggi/annulla l'ultimo riavvio) hanno
     * regole diverse da un Fatto spontaneo — "mantieni il ritmo" vale per l'uno e non
     * per l'altro — e un altro telefono, vedendo solo il risultato (startMs cambiato),
     * non può distinguerli e rischia di ricalcolare una scadenza diversa (bug scoperto
     * il 27/09). Stesso motivo per scheduledResetMs: senza, un reset programmato non si
     * vedeva affatto sull'altro telefono finché non scattava.
     */
    fun payload(counter: Counter): Map<String, Any?> = SyncedCounter.payload(counter) + mapOf(
        "uuid" to counter.uuid,
        "updatedMs" to counter.updatedMs,
        "nextBellAtMs" to counter.nextBellAtMs,
        "scheduledResetMs" to counter.scheduledResetMs,
        // Chi ha scritto per ultimo. Non si salva in locale: serve solo a intestare gli
        // avvisi ("Vale ha ripreso Tachipirina"), dove non c'è un round che porti la firma.
        "lastByName" to Cloud.myName.takeIf { it.isNotBlank() },
    )

    /**
     * Toglie un contatore dal gruppo. Non cancella niente a nessuno: sugli altri telefoni
     * la copia resta, con tutto il suo storico, e torna semplicemente autonoma.
     */
    suspend fun remove(groupId: String, uuid: String) {
        // Una lapide, non una cancellazione: con il documento sparito, un telefono che in
        // quel momento non ascoltava (app chiusa) al giro dopo vedeva "questo ce l'ho io e
        // sul server manca" e lo rimandava su — e il timer eliminato tornava fuori a chi
        // l'aveva eliminato. La lapide invece c'è, è più recente, e dice a tutti di
        // sganciarsi (SyncEngine.applyRemote). I telefoni vecchi la ignorano: senza nome
        // non la prendono per un contatore.
        db.collection("groups").document(groupId)
            .collection("counters").document(uuid)
            .set(mapOf("uuid" to uuid, "removed" to true, "updatedMs" to System.currentTimeMillis()))
            .await()
    }

    /** Il documento di un contatore è una lapide: vedi [remove]. */
    fun isTombstone(data: Map<String, Any?>?): Boolean = data?.get("removed") == true

    fun roundPayload(round: Round, counterUuid: String): Map<String, Any?> = mapOf(
        "uuid" to round.uuid,
        "counterUuid" to counterUuid,
        "startMs" to round.startMs,
        "endMs" to round.endMs,
        "noTime" to round.noTime,
        "byName" to round.byName,
        "endMsUpdatedAt" to round.endMsUpdatedAt,
        "state" to round.state,
        "note" to round.note,
    )

    /** Un evento nello storico del gruppo. Append-only: si scrive e non si tocca piu'. */
    suspend fun pushRound(groupId: String, round: Round, counterUuid: String) {
        db.collection("groups").document(groupId)
            .collection("rounds").document(round.uuid)
            .set(roundPayload(round, counterUuid)).await()
    }

    /** Gli ultimi eventi del gruppo, per l'allineamento periodico. */
    suspend fun recentRounds(groupId: String, max: Long = 200): List<Map<String, Any?>> =
        db.collection("groups").document(groupId).collection("rounds")
            .orderBy("endMs", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(max)
            .get().await()
            .documents.mapNotNull { it.data }

    /** Scrive (o aggiorna) un contatore dentro il gruppo. */
    suspend fun push(groupId: String, counter: Counter) {
        db.collection("groups").document(groupId)
            .collection("counters").document(counter.uuid)
            .set(payload(counter)).await()
    }

    /**
     * Corregge SOLO l'endMs di un round (e il suo timbro): l'unica scrittura che le regole
     * del server concedono su un round già scritto, e solo se è ancora l'ultimo di quel
     * contatore. Fallisce (rifiutata dal server) se nel frattempo non lo è più.
     */
    suspend fun correctRoundEnd(groupId: String, roundUuid: String, endMs: Long, stampMs: Long) {
        db.collection("groups").document(groupId)
            .collection("rounds").document(roundUuid)
            .update(mapOf("endMs" to endMs, "endMsUpdatedAt" to stampMs)).await()
    }

    /**
     * Cancella un round dal gruppo: solo per la modalità GIORNALIERO, dove le regole del
     * server lo concedono su qualunque round di quel contatore, non solo l'ultimo.
     */
    suspend fun deleteRound(groupId: String, roundUuid: String) {
        db.collection("groups").document(groupId)
            .collection("rounds").document(roundUuid)
            .delete().await()
    }
}
