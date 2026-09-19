package it.marino8383.lasttime.sync

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Stato del collegamento al cloud, mostrato in 🩺 Diagnostica. */
sealed interface CloudState {
    /** Non ci ho ancora provato. */
    data object Idle : CloudState
    data object Connecting : CloudState
    /** Identita' anonima ottenuta: da qui in poi si puo' entrare in un gruppo. */
    data class Ready(val uid: String) : CloudState
    data class Failed(val message: String) : CloudState
}

/**
 * Identita' del dispositivo sul progetto Firebase.
 *
 * E' anonima di proposito: nessuna mail, nessuna password, nessuna schermata di login.
 * Serve solo a dare a questo telefono un nome opaco con cui entrare in un gruppo di
 * timer condivisi. Chi non condivide niente non se ne accorge mai.
 *
 * Il login richiede rete: se il telefono e' offline al primo avvio fallisce, e si
 * riprova al prossimo avvio o quando si apre la diagnostica. Non e' un problema,
 * perche' senza condivisione l'app funziona esattamente come prima.
 */
object Cloud {

    private const val TAG = "LastTimeCloud"

    private val _state = MutableStateFlow<CloudState>(CloudState.Idle)
    val state: StateFlow<CloudState> = _state

    val uid: String? get() = (_state.value as? CloudState.Ready)?.uid

    /** Idempotente: se l'identita' c'e' gia' non fa nulla. */
    fun connect() {
        if (_state.value is CloudState.Ready || _state.value is CloudState.Connecting) return
        _state.value = CloudState.Connecting
        try {
            val auth = FirebaseAuth.getInstance()
            val current = auth.currentUser
            if (current != null) {
                _state.value = CloudState.Ready(current.uid)
                return
            }
            auth.signInAnonymously()
                .addOnSuccessListener { result ->
                    val id = result.user?.uid
                    _state.value = if (id != null) CloudState.Ready(id)
                    else CloudState.Failed("login riuscito ma senza identita'")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "login anonimo fallito", e)
                    _state.value = CloudState.Failed(e.message ?: "login anonimo fallito")
                }
        } catch (t: Throwable) {
            Log.w(TAG, "Firebase non inizializzato", t)
            _state.value = CloudState.Failed(t.message ?: "Firebase non inizializzato")
        }
    }

    /** Dopo un errore si puo' ritentare: [connect] da solo non riproverebbe. */
    fun retry() {
        if (_state.value is CloudState.Failed) _state.value = CloudState.Idle
        connect()
    }
}
