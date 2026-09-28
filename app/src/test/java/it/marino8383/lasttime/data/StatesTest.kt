package it.marino8383.lasttime.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Modalità Stati: elenco degli stati, tempo per stato, cambio di stato. */
class StatesTest {

    private val h = 3_600_000L

    private fun round(state: String?, from: Long, to: Long, noTime: Boolean = false) =
        Round(counterId = 1, startMs = from, endMs = to, state = state, noTime = noTime)

    @Test
    fun `elenco da testo, a righe o a virgole, senza vuoti ne doppioni`() {
        assertEquals(listOf("Felice", "Stanco", "Annoiato"), parseStates(" Felice\nStanco, ,Annoiato\nFelice\n"))
        assertEquals(emptyList<String>(), parseStates(null))
        assertEquals("Ammalato\nSano", joinStates(listOf("Ammalato", " Sano ", "")))
        assertNull(joinStates(listOf(" ", "")))
    }

    @Test
    fun `tempo per stato somma i round e lo stato in corso`() {
        val rounds = listOf(
            round("Stanco", 0, 2 * h),
            round("Felice", 2 * h, 3 * h),
            round("Stanco", 3 * h, 4 * h),
        )
        val stats = stateStats(rounds, "Felice", 4 * h, 6 * h)
        val stanco = stats.first { it.state == "Stanco" }
        val felice = stats.first { it.state == "Felice" }
        assertEquals(3 * h, stanco.totalMs)
        assertEquals(2, stanco.times)
        assertEquals(3 * h, felice.totalMs)
        assertEquals(2, felice.times)
        assertEquals(50.0, stanco.percent!!, 0.001)
    }

    @Test
    fun `nessuno conta nello storico ma non nelle percentuali`() {
        val rounds = listOf(round("Ammalato", 0, h), round(null, h, 4 * h))
        val stats = stateStats(rounds, "Ammalato", 4 * h, 5 * h)
        val ammalato = stats.first { it.state == "Ammalato" }
        val nessuno = stats.first { it.state == null }
        assertEquals(100.0, ammalato.percent!!, 0.001)
        assertNull(nessuno.percent)
        assertEquals(3 * h, nessuno.totalMs)
        // ordinati dal più lungo
        assertEquals(null, stats.first().state)
    }

    @Test
    fun `senza intervallo in corso, per esempio da archiviato, conta solo lo storico`() {
        val stats = stateStats(listOf(round("Sano", 0, h)), "Sano", null, 10 * h)
        assertEquals(h, stats.single().totalMs)
        assertEquals(1, stats.single().times)
    }

    @Test
    fun `i giri solo conteggio non hanno durata e restano fuori`() {
        val stats = stateStats(listOf(round("Sano", 0, h, noTime = true)), "Sano", 0, h)
        assertEquals(1, stats.single().times)
    }

    @Test
    fun `cambiare stato sposta l'inizio, pulisce la nota vuota e toglie il reset programmato`() {
        val c = Counter(name = "Umore", startMs = 0, createdMs = 0, mode = CounterMode.STATI,
            currentState = "Stanco", currentNote = "riunione", scheduledResetMs = 99)
        val next = c.changedState("Felice", 5 * h, "  ")
        assertEquals(5 * h, next.startMs)
        assertEquals("Felice", next.currentState)
        assertNull(next.currentNote)
        assertNull(next.scheduledResetMs)
        assertEquals("febbre", c.changedState(null, h, " febbre ").currentNote)
    }
}
