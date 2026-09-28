package it.marino8383.lasttime.data

/*
 * Modalità Stati: un contatore che è sempre in uno stato preciso (o in nessuno), e ogni
 * cambio chiude lo stato vecchio e apre il nuovo. Solo calcoli puri, come BellRules:
 * niente database né Android, così si coprono con test JVM (vedi app/src/test).
 *
 * Il modello riusa i round: un round chiuso è un intervallo passato in uno stato
 * ([Round.state]), quello in corso vive sul contatore (startMs + [Counter.currentState]).
 * Uno stato null è "nessuno": c'è, conta nello storico, ma non entra nelle percentuali —
 * per l'umore, ore senza niente di registrato non sono ore di felicità.
 */

/** Gli stati di un contatore: [Counter.states] è il testo con uno stato per riga. */
fun parseStates(raw: String?): List<String> =
    raw.orEmpty()
        .split('\n', ',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

fun joinStates(states: List<String>): String? =
    states.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        .joinToString("\n").ifEmpty { null }

/** Elenchi pronti da proporre in creazione. */
val STATE_PRESETS: List<Pair<String, List<String>>> = listOf(
    "Ammalato / Sano" to listOf("Ammalato", "Sano"),
    "Umore" to listOf("Felice", "Stanco", "Arrabbiato", "Annoiato"),
)

/** Tempo passato in uno stato, per lo storico. */
data class StateStat(
    val state: String?,
    val totalMs: Long,
    /** Quante volte ci si è entrati (intervalli), quello in corso compreso. */
    val times: Int,
    /** Percentuale sul tempo in stati veri: null per "nessuno", che non conta. */
    val percent: Double?,
)

/**
 * Tempo per stato, sui round chiusi più l'intervallo in corso ([currentState] da
 * [currentStartMs] a [now]; null se non ce n'è uno, come su un archiviato, dove l'ultimo
 * intervallo è già stato chiuso nello storico). Ordinati dal più lungo. I round "solo
 * conteggio" non hanno durata e restano fuori.
 */
fun stateStats(
    rounds: List<Round>,
    currentState: String?,
    currentStartMs: Long?,
    now: Long,
): List<StateStat> {
    val intervalli = rounds.filter { !it.noTime }.map { it.state to (it.endMs - it.startMs).coerceAtLeast(0) } +
        listOfNotNull(currentStartMs?.let { currentState to (now - it).coerceAtLeast(0) })
    val perStato = intervalli.groupBy({ it.first }, { it.second })
    val totaleVeri = perStato.filterKeys { it != null }.values.sumOf { it.sum() }
    return perStato.map { (stato, durate) ->
        val tot = durate.sum()
        StateStat(
            state = stato,
            totalMs = tot,
            times = durate.size,
            percent = if (stato != null && totaleVeri > 0) tot * 100.0 / totaleVeri else null,
        )
    }.sortedByDescending { it.totalMs }
}

/**
 * Nuovo stato del contatore dopo un cambio ad [atMs]: il round del vecchio stato va
 * chiuso a parte (CounterActions.changeState), qui solo il contatore.
 */
fun Counter.changedState(state: String?, atMs: Long, note: String?): Counter = copy(
    startMs = atMs,
    currentState = state,
    currentNote = note?.trim()?.takeIf { it.isNotEmpty() },
    scheduledResetMs = null,
)
