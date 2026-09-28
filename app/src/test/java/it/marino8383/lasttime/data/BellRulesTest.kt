package it.marino8383.lasttime.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone

/**
 * Le regole della campanella: è la parte che più facilmente si rompe spostando codice,
 * e un errore qui vuol dire una dose segnata all'ora sbagliata.
 */
class BellRulesTest {

    private val zone = ZoneId.of("Europe/Rome")
    private val min = 60_000L
    private val ore8 = 8 * 60L // minuti

    @Before
    fun fusoFisso() {
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
    }

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    /** Tachipirina: ogni 8 ore, ricorrente, partita alle 8:00. */
    private fun tachipirina(bellMode: String = "INTERVAL", repeat: Boolean = true) = Counter(
        name = "Tachipirina",
        startMs = at(2026, 9, 28, 8, 0),
        createdMs = 0,
        bellMinutes = ore8,
        bellMode = bellMode,
        bellRepeat = repeat,
        nextBellAtMs = at(2026, 9, 28, 16, 0),
    )

    private fun giornaliero(enabled: Boolean = true) = Counter(
        name = "Cacca",
        startMs = at(2026, 1, 10, 12, 0),
        createdMs = 0,
        mode = CounterMode.GIORNALIERO,
        bellMinutes = 3 * 1440L,
        dailyBellMinuteOfDay = 15 * 60,
        bellEnabled = enabled,
    )

    // ------------------------------------------------------------ bellFrom

    @Test
    fun preciso_bellFrom_eUnPassoDopo() {
        val c = tachipirina()
        val da = at(2026, 9, 28, 10, 0)
        assertEquals(da + ore8 * min, c.bellFrom(da))
    }

    @Test
    fun giornaliero_bellFrom_eAllOrarioFissoDopoNGiorni() {
        // 3 giorni dal 10 gennaio alle 23:50 sono il 13 alle 15:00, non 72 ore dopo
        val c = giornaliero()
        assertEquals(at(2026, 1, 13, 15, 0), c.bellFrom(at(2026, 1, 10, 23, 50)))
    }

    @Test
    fun senzaCampanella_bellFromNull() {
        assertNull(tachipirina().copy(bellMinutes = null).bellFrom(0))
        assertNull(giornaliero().copy(dailyBellMinuteOfDay = null).bellFrom(0))
    }

    // ------------------------------------------------------------ rhythmDeviation

    @Test
    fun deviazione_nessunaAMetaCiclo_lieveEntroSoglia_forteOltre() {
        val c = tachipirina()
        // 3% di 8 ore = 14,4 minuti. Metà ciclo: anticipo di 4 ore, oltre soglia.
        assertEquals(RhythmDeviation.LARGE, c.rhythmDeviation(at(2026, 9, 28, 12, 0), 3))
        assertEquals(RhythmDeviation.SLIGHT, c.rhythmDeviation(at(2026, 9, 28, 15, 50), 3))
        val suonata = c.copy(bellNotified = true)
        assertEquals(RhythmDeviation.SLIGHT, suonata.rhythmDeviation(at(2026, 9, 28, 16, 10), 3))
        assertEquals(RhythmDeviation.LARGE, suonata.rhythmDeviation(at(2026, 9, 28, 18, 0), 3))
        assertEquals(RhythmDeviation.NONE, c.copy(bellRepeat = false).rhythmDeviation(at(2026, 9, 28, 12, 0), 3))
        assertEquals(RhythmDeviation.NONE, c.copy(bellMinutes = null).rhythmDeviation(at(2026, 9, 28, 12, 0), 3))
    }

    // ------------------------------------------------------------ restarted (Fatto spontaneo)

    @Test
    fun fattoMoltoInRitardo_ripartonoOttoOreDaAdesso() {
        val adesso = at(2026, 9, 28, 18, 0)
        val r = tachipirina().copy(bellNotified = true).restarted(adesso, 3)
        assertEquals(adesso, r.startMs)
        assertEquals(adesso + ore8 * min, r.nextBellAtMs)
        assertFalse(r.bellNotified)
    }

    @Test
    fun fattoPocoInRitardo_mantieneIlRitmo() {
        val r = tachipirina().copy(bellNotified = true).restarted(at(2026, 9, 28, 16, 10), 3)
        assertEquals(at(2026, 9, 29, 0, 0), r.nextBellAtMs)
    }

    @Test
    fun fattoPocoInAnticipo_mantieneIlRitmo_unGiroDopo() {
        val r = tachipirina().restarted(at(2026, 9, 28, 15, 55), 3)
        assertEquals(at(2026, 9, 29, 0, 0), r.nextBellAtMs)
    }

    @Test
    fun fixed_mantieneSempreIlRitmo_ancheMoltoInRitardo() {
        val r = tachipirina(bellMode = "FIXED").copy(bellNotified = true).restarted(at(2026, 9, 29, 1, 0), 3)
        assertEquals(at(2026, 9, 29, 8, 0), r.nextBellAtMs)
    }

    @Test
    fun singola_siSpegneAlRiavvio() {
        val r = tachipirina(repeat = false).restarted(at(2026, 9, 28, 17, 0), 3)
        assertFalse(r.bellEnabled)
        assertNull(r.nextBellAtMs)
    }

    @Test
    fun riavvio_annullaIlResetProgrammatoEIlRinvio() {
        val c = tachipirina().copy(scheduledResetMs = at(2026, 9, 28, 20, 0), snoozeUntilMs = 1)
        val r = c.restarted(at(2026, 9, 28, 17, 0), 3)
        assertNull(r.scheduledResetMs)
        assertNull(r.snoozeUntilMs)
    }

    // ------------------------------------------------------------ restartedAt (orario scelto a mano)

    @Test
    fun orarioScelto_ricalcolaDaLi_ancheSulleFixed() {
        val scelto = at(2026, 9, 28, 14, 0)
        val r = tachipirina(bellMode = "FIXED").restartedAt(scelto)
        assertEquals(scelto, r.startMs)
        assertEquals(at(2026, 9, 28, 22, 0), r.nextBellAtMs)
    }

    // ------------------------------------------------------------ restartedWithChoice (LateBellDialog)

    @Test
    fun sceltaEsplicita_treStrade() {
        val adesso = at(2026, 9, 28, 20, 0)
        val c = tachipirina().copy(bellNotified = true)
        assertEquals(at(2026, 9, 29, 0, 0), c.restartedWithChoice(adesso, LateBellChoice.KEEP_RHYTHM).nextBellAtMs)
        assertEquals(adesso + ore8 * min, c.restartedWithChoice(adesso, LateBellChoice.FROM_NOW).nextBellAtMs)
        assertFalse(c.restartedWithChoice(adesso, LateBellChoice.DISABLE).bellEnabled)
    }

    // ------------------------------------------------------------ loggedDaily (+1 Giornaliero)

    @Test
    fun piuUnoGiornaliero_campanellaAllOrarioFisso() {
        val r = giornaliero().loggedDaily(at(2026, 1, 20, 21, 40))
        assertEquals(at(2026, 1, 23, 15, 0), r.nextBellAtMs)
    }

    @Test
    fun piuUnoGiornaliero_campanellaSpenta_nessunaScadenza() {
        assertNull(giornaliero(enabled = false).loggedDaily(at(2026, 1, 20, 21, 40)).nextBellAtMs)
    }
}
