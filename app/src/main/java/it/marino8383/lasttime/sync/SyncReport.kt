package it.marino8383.lasttime.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import it.marino8383.lasttime.AppSettings
import it.marino8383.lasttime.LastTimeApp
import kotlinx.coroutines.tasks.await

/**
 * Fotografia dello stato di condivisione, per la 🩺 Diagnostica.
 *
 * Esiste perché i fallimenti della sincronizzazione sono silenziosi per costruzione: una
 * scrittura rifiutata dal server viene registrata nel log e basta, così l'app non si mette
 * a urlare per un problema di rete passeggero. Il rovescio è che un problema **non**
 * passeggero — per esempio l'appartenenza al gruppo persa — resta invisibile, e da fuori
 * si vede solo che "non si allinea". Qui i numeri si guardano in faccia.
 */
data class ContatoreReport(
    val nome: String,
    val localeMs: Long,
    val remotoMs: Long?,
)

data class GruppoReport(
    val groupId: String,
    /** Il mio documento di membro esiste? Se no, il server rifiuta tutto quello che scrivo. */
    val sonoMembro: Boolean,
    val puoScrivere: Boolean,
    val errore: String?,
    val contatori: List<ContatoreReport>,
)

object SyncReport {

    /**
     * Le letture chiedono espressamente il **server**: una diagnostica servita dalla cache
     * direbbe bugie rassicuranti proprio nei casi in cui serve — senza rete o senza
     * permessi risponderebbe con l'ultima copia buona invece di dire che non ci arriva.
     */
    suspend fun raccogli(app: LastTimeApp): List<GruppoReport> {
        val uid = Cloud.ensureSignedIn() ?: return emptyList()
        val db = FirebaseFirestore.getInstance()
        val gruppi = AppSettings.groups(app) +
            app.db.counterDao().shared().mapNotNull { it.sharedGroupId }

        return gruppi.map { groupId ->
            try {
                val membro = db.collection("groups").document(groupId)
                    .collection("members").document(uid).get(Source.SERVER).await().exists()

                val remoti = db.collection("groups").document(groupId)
                    .collection("counters").get(Source.SERVER).await()
                    .documents.associate { doc ->
                        doc.id to (doc.getLong("updatedMs") ?: 0L)
                    }

                // Prova di scrittura vera: aggiornare il proprio documento di membro e'
                // innocuo e passa dalle stesse regole delle scritture che contano.
                val puoScrivere = try {
                    db.collection("groups").document(groupId)
                        .collection("members").document(uid)
                        .update("lastSeenMs", System.currentTimeMillis()).await()
                    true
                } catch (t: Throwable) {
                    false
                }

                val locali = app.db.counterDao().shared()
                    .filter { it.sharedGroupId == groupId }
                    .map { ContatoreReport(it.name, it.updatedMs, remoti[it.uuid]) }

                GruppoReport(groupId, membro, puoScrivere, null, locali)
            } catch (t: Throwable) {
                GruppoReport(groupId, false, false, t.message ?: "lettura fallita", emptyList())
            }
        }
    }
}
