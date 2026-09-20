package it.marino8383.lasttime.sync

import com.google.firebase.firestore.FirebaseFirestore
import it.marino8383.lasttime.data.Counter
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
     * Solo i campi condivisi: nome, inizio del round e configurazione della campanella.
     * Restano fuori di proposito viewMode, bellEnabled, bellNotified e snoozeUntilMs,
     * che sono scelte del singolo telefono.
     */
    fun payload(counter: Counter): Map<String, Any?> = mapOf(
        "uuid" to counter.uuid,
        "name" to counter.name,
        "startMs" to counter.startMs,
        "bellMinutes" to counter.bellMinutes,
        "bellMode" to counter.bellMode,
        "bellRepeat" to counter.bellRepeat,
        "updatedMs" to counter.updatedMs,
    )

    /** Scrive (o aggiorna) un contatore dentro il gruppo. */
    suspend fun push(groupId: String, counter: Counter) {
        db.collection("groups").document(groupId)
            .collection("counters").document(counter.uuid)
            .set(payload(counter)).await()
    }
}
