package it.marino8383.lasttime.sync

import it.marino8383.lasttime.data.Counter
import it.marino8383.lasttime.data.CounterMode

/**
 * I campi di un Counter che viaggiano col gruppo, ognuno scritto **una volta sola**:
 * come si legge dal contatore per mandarlo su e come si riapplica quando arriva
 * dall'altro telefono.
 *
 * Prima ogni campo andava aggiunto in tre punti (il payload in Groups, l'inserimento e
 * l'aggiornamento in SyncEngine.applyRemote): dimenticarne uno dava un campo che si
 * manda ma non si riceve, e che nessuno nota finché i due telefoni non divergono.
 * Un campo condiviso nuovo si aggiunge qui e basta.
 *
 * Restano fuori, gestiti a mano in Groups.payload e SyncEngine.applyRemote perché hanno
 * regole proprie: uuid e updatedMs (identità e last-write-wins), nextBellAtMs e
 * scheduledResetMs (fallback per i telefoni con versioni vecchie), lastByName (solo per
 * intestare gli avvisi, non si salva). I campi locali — campanella accesa, snooze,
 * nascosto, vista, ordine — non devono mai finire qui: vedi Counter.
 */
object SyncedCounter {

    private class Campo(
        val key: String,
        val read: (Counter) -> Any?,
        val write: (Counter, Map<String, Any?>) -> Counter,
    )

    private fun Map<String, Any?>.long(key: String) = (this[key] as? Number)?.toLong()

    /** Il valore se la chiave c'è (anche null), altrimenti [keep]: vedi i campi degli Stati. */
    private fun Map<String, Any?>.stringOr(key: String, keep: String?) = if (containsKey(key)) this[key] as? String else keep

    private val CAMPI: List<Campo> = listOf(
        Campo("name", { it.name }) { c, d -> c.copy(name = d["name"] as? String ?: c.name) },
        Campo("startMs", { it.startMs }) { c, d -> c.copy(startMs = d.long("startMs") ?: c.startMs) },
        Campo("bellMinutes", { it.bellMinutes }) { c, d -> c.copy(bellMinutes = d.long("bellMinutes")) },
        Campo("bellMode", { it.bellMode }) { c, d -> c.copy(bellMode = d["bellMode"] as? String ?: "INTERVAL") },
        Campo("bellRepeat", { it.bellRepeat }) { c, d -> c.copy(bellRepeat = d["bellRepeat"] as? Boolean ?: true) },
        // L'archivio è condiviso: un ciclo finisce per tutti e riprende per tutti.
        Campo("archived", { it.archived }) { c, d -> c.copy(archived = d["archived"] as? Boolean ?: false) },
        Campo("archivedMs", { it.archivedMs }) { c, d -> c.copy(archivedMs = d.long("archivedMs")) },
        Campo("historyFromMs", { it.historyFromMs }) { c, d -> c.copy(historyFromMs = d.long("historyFromMs")) },
        // Puntano all'ultimo round vero: è quello che le regole del server lasciano ancora
        // correggere (solo endMs), per "Correggi l'ultimo riavvio".
        Campo("lastRoundUuid", { it.lastRoundUuid }) { c, d -> c.copy(lastRoundUuid = d["lastRoundUuid"] as? String) },
        Campo("lastRoundStartMs", { it.lastRoundStartMs }) { c, d -> c.copy(lastRoundStartMs = d.long("lastRoundStartMs")) },
        // Modalità: cambia come si conta e le regole del server per i round di questo
        // contatore (niente più append-only sui Giornalieri, vedi firestore.rules).
        Campo("mode", { it.mode }) { c, d -> c.copy(mode = d["mode"] as? String ?: CounterMode.PRECISO) },
        Campo("dailyBellMinuteOfDay", { it.dailyBellMinuteOfDay }) { c, d ->
            c.copy(dailyBellMinuteOfDay = (d["dailyBellMinuteOfDay"] as? Number)?.toInt())
        },
        // Arrotondamento del Fatto: una regola del timer, vale per tutti.
        Campo("roundMinutes", { it.roundMinutes }) { c, d ->
            c.copy(roundMinutes = (d["roundMinutes"] as? Number)?.toInt())
        },
        // Modalità Stati: l'elenco e lo stato in corso valgono per tutti, nota compresa.
        // Chiave assente = il telefono dall'altra parte è di prima degli Stati e non li
        // conosce: si tiene quello che c'è. Senza, un suo riavvio cancellava elenco e
        // stato a tutti (successo davvero con la 0.26.0). Null presente = "nessuno", vale.
        Campo("states", { it.states }) { c, d -> c.copy(states = d.stringOr("states", c.states)) },
        Campo("currentState", { it.currentState }) { c, d -> c.copy(currentState = d.stringOr("currentState", c.currentState)) },
        Campo("currentNote", { it.currentNote }) { c, d -> c.copy(currentNote = d.stringOr("currentNote", c.currentNote)) },
        // Chi l'ha condiviso per primo (vedi Counter.creatorUid). Non deve mai tornare a
        // null solo perché una scrittura remota vecchia, di prima che il campo esistesse,
        // non lo portava con sé.
        Campo("creatorUid", { it.creatorUid }) { c, d -> c.copy(creatorUid = d["creatorUid"] as? String ?: c.creatorUid) },
    )

    /** I campi condivisi di [counter], pronti per Firestore. */
    fun payload(counter: Counter): Map<String, Any?> = CAMPI.associate { it.key to it.read(counter) }

    /** [counter] con sopra i campi condivisi arrivati dal gruppo in [data]. */
    fun apply(counter: Counter, data: Map<String, Any?>): Counter =
        CAMPI.fold(counter) { c, campo -> campo.write(c, data) }
}
