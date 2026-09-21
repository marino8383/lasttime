package it.marino8383.lasttime.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Stato dell'allineamento dei timer condivisi, per l'indicatore in intestazione.
 *
 * Ha senso solo se c'è qualcosa di condiviso: chi non condivide non deve vederlo affatto.
 * Le tre situazioni sono quelle che servono davvero a chi guarda: sono allineato,
 * ci sto lavorando, oppure quello che vedi potrebbe essere vecchio — e in quest'ultimo
 * caso conta sapere *da quando*, per decidere se fidarsi.
 */
object SyncStatus {

    /** Oltre questo scarto dall'ultimo giro riuscito non garantiamo piu' niente. */
    private const val FRESCO_MS = 20 * 60 * 1000L

    private const val PREFS = "settings"
    private const val KEY_LAST_OK = "last_sync_ok_ms"

    sealed interface Stato {
        /** Allineati: [atMs] e' l'ora dell'ultima conferma. */
        data class Aggiornato(val atMs: Long) : Stato
        data object InCorso : Stato
        /** Potenzialmente vecchio: [atMs] e' l'ultimo allineamento riuscito, se c'e' mai stato. */
        data class Vecchio(val atMs: Long?) : Stato
    }

    private val _stato = MutableStateFlow<Stato>(Stato.Vecchio(null))
    val stato: StateFlow<Stato> = _stato

    private var lastOkMs: Long? = null
    private var inCorso = 0

    fun init(context: Context) {
        lastOkMs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_OK, 0L).takeIf { it > 0 }
        ricalcola()
    }

    fun start() {
        inCorso++
        ricalcola()
    }

    fun ok(context: Context) {
        if (inCorso > 0) inCorso--
        lastOkMs = System.currentTimeMillis()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_OK, lastOkMs!!).apply()
        ricalcola()
    }

    fun fallito() {
        if (inCorso > 0) inCorso--
        ricalcola()
    }

    /** Da richiamare ogni tanto: il passare del tempo da solo puo' rendere vecchio lo stato. */
    fun ricalcola() {
        val ultimo = lastOkMs
        _stato.value = when {
            inCorso > 0 -> Stato.InCorso
            ultimo != null && System.currentTimeMillis() - ultimo <= FRESCO_MS -> Stato.Aggiornato(ultimo)
            else -> Stato.Vecchio(ultimo)
        }
    }
}
