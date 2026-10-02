package it.marino8383.lasttime.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ------------------------------------------------------------ orari tondi

    @Test
    fun arrotondamenti_primaDopoEPiuVicino() {
        val t = at(2026, 9, 28, 14, 19) + 30_000 // 14:19:30
        assertEquals(at(2026, 9, 28, 14, 15), roundFloorMs(t, 15))
        assertEquals(at(2026, 9, 28, 14, 30), roundCeilMs(t, 15))
        assertEquals(at(2026, 9, 28, 14, 15), roundNearestMs(t, 15))
        assertEquals(at(2026, 9, 28, 14, 20), roundNearestMs(t, 5))
        assertEquals(at(2026, 9, 28, 14, 0), roundNearestMs(t, 60))
        // già tondo: resta com'è
        assertEquals(at(2026, 9, 28, 14, 15), roundCeilMs(at(2026, 9, 28, 14, 15), 15))
    }

    @Test
    fun aPariDistanza_vinceQuelloDopo() {
        // 14:22:30 è a metà fra 14:15 e 14:30: meglio segnare una dose più tardi che prima
        assertEquals(at(2026, 9, 28, 14, 30), roundNearestMs(at(2026, 9, 28, 14, 22) + 30_000, 15))
    }

    @Test
    fun quickPick_alle1419() {
        val adesso = at(2026, 9, 28, 14, 19) + 30_000
        assertEquals(
            listOf(
                at(2026, 9, 28, 14, 0),
                at(2026, 9, 28, 14, 15),
                adesso,
                at(2026, 9, 28, 14, 20),
                at(2026, 9, 28, 14, 30),
                at(2026, 9, 28, 15, 0),
            ),
            quickStartTimes(adesso),
        )
    }

    @Test
    fun quickPick_alle1500_nienteDoppione() {
        // alle 15:00:20 il tondo 15:00 è "adesso": due 15:00 in fila non avrebbero senso
        val adesso = at(2026, 9, 28, 15, 0) + 20_000
        val fila = quickStartTimes(adesso)
        assertEquals(1, fila.count { formatHm(it) == "15:00" })
        assertEquals(at(2026, 9, 28, 15, 0), fila.single { isQuickNow(it, adesso) })
    }

    @Test
    fun quickPick_primaScelta_eIlTondoPiuVicino() {
        assertEquals(at(2026, 9, 28, 15, 0), quickStartDefault(at(2026, 9, 28, 14, 59)))
        assertEquals(at(2026, 9, 28, 14, 20), quickStartDefault(at(2026, 9, 28, 14, 19)))
        // ed è sempre una delle caselle proposte
        val t = at(2026, 9, 28, 14, 59) + 40_000
        assertTrue(quickStartDefault(t) in quickStartTimes(t))
    }

    private fun formatHm(ms: Long) =
        java.time.Instant.ofEpochMilli(ms).atZone(zone).let { "%02d:%02d".format(it.hour, it.minute) }

    @Test
    fun fattoArrotondato() {
        val c = tachipirina().copy(roundMinutes = 15)
        // senza arrotondamento: l'istante preciso
        assertEquals(at(2026, 9, 28, 14, 19), tachipirina().doneTargetMs(at(2026, 9, 28, 14, 19)))
        // 14:19 → 14:15 (nel passato: riparte da lì)
        assertEquals(at(2026, 9, 28, 14, 15), c.doneTargetMs(at(2026, 9, 28, 14, 19)))
        // 14:23 → 14:30 (nel futuro: il chiamante lo programma)
        assertEquals(at(2026, 9, 28, 14, 30), c.doneTargetMs(at(2026, 9, 28, 14, 23)))
        // il tondo prima dell'inizio del giro non vale: si prende quello dopo
        val appenaPartito = c.copy(startMs = at(2026, 9, 28, 14, 17))
        assertEquals(at(2026, 9, 28, 14, 30), appenaPartito.doneTargetMs(at(2026, 9, 28, 14, 19)))
        // i Giornalieri non si arrotondano
        assertEquals(at(2026, 1, 20, 14, 19), giornaliero().copy(roundMinutes = 15).doneTargetMs(at(2026, 1, 20, 14, 19)))
    }

    @Test
    fun avvioArrotondato_ilTondoPiuVicino() {
        // senza arrotondamento: l'istante preciso
        assertEquals(at(2026, 9, 28, 14, 2), startTargetMs(at(2026, 9, 28, 14, 2), null))
        // 5′: 14:02 → 14:00 (passato, parte da lì); 14:03 → 14:05 (futuro, resta da partire)
        assertEquals(at(2026, 9, 28, 14, 0), startTargetMs(at(2026, 9, 28, 14, 2), 5))
        assertEquals(at(2026, 9, 28, 14, 5), startTargetMs(at(2026, 9, 28, 14, 3), 5))
        // a metà esatta vince quello dopo, come per il Fatto
        assertEquals(at(2026, 9, 28, 14, 5), startTargetMs(at(2026, 9, 28, 14, 2) + 30_000, 5))
        // gli altri arrotondamenti
        assertEquals(at(2026, 9, 28, 14, 15), startTargetMs(at(2026, 9, 28, 14, 19), 15))
        assertEquals(at(2026, 9, 28, 14, 30), startTargetMs(at(2026, 9, 28, 14, 23), 15))
        assertEquals(at(2026, 9, 28, 15, 0), startTargetMs(at(2026, 9, 28, 14, 31), 60))
        // già tondo: resta com'è
        assertEquals(at(2026, 9, 28, 14, 0), startTargetMs(at(2026, 9, 28, 14, 0), 15))
    }

    @Test
    fun avvioArrotondato_nonScendeSottoIlLimite() {
        // Riprendi alle 14:19 un timer archiviato alle 14:17: il 14:15 si accavallerebbe
        assertEquals(
            at(2026, 9, 28, 14, 30),
            startTargetMs(at(2026, 9, 28, 14, 19), 15, notBefore = at(2026, 9, 28, 14, 17)),
        )
    }

    // ------------------------------------------------------------ promemoria

    private val p45 = 45 * min

    @Test
    fun promemoria_dopoIlDoppio_poiTriploEQuadruplo() {
        val due = at(2026, 9, 28, 10, 0)
        // la campanella suona (qualche secondo dopo la scadenza): primo promemoria dopo 90′
        val r1 = firstReminderAt(due, due + 2_000, p45)
        assertEquals(at(2026, 9, 28, 11, 30), r1)
        // mandato il primo: il successivo dopo altri 135′ (3P)
        val r2 = nextReminderAt(r1, r1 + 1_000, 1, p45)
        assertEquals(at(2026, 9, 28, 13, 45), r2)
        // poi 180′ (4P)
        assertEquals(at(2026, 9, 28, 16, 45), nextReminderAt(r2!!, r2 + 1_000, 2, p45))
    }

    @Test
    fun promemoria_siFermaAlMassimo() {
        val t = at(2026, 9, 28, 10, 0)
        assertEquals(null, nextReminderAt(t, t, MAX_REMINDERS, p45))
        assertTrue(nextReminderAt(t, t, MAX_REMINDERS - 1, p45) != null)
    }

    @Test
    fun promemoria_telefonoInRitardo_nonSuonaSubito() {
        // la campanella delle 10:00 viene processata alle 15:00 (telefono spento): il primo
        // promemoria non è un passato già scaduto, cade un periodo dopo adesso
        val due = at(2026, 9, 28, 10, 0)
        val adesso = at(2026, 9, 28, 15, 0)
        assertEquals(adesso + p45, firstReminderAt(due, adesso, p45))
        // e anche il successivo, se il promemoria è stato processato molto dopo il previsto
        val previsto = at(2026, 9, 28, 11, 30)
        assertTrue(nextReminderAt(previsto, adesso, 1, p45)!! > adesso)
    }

    @Test
    fun promemoria_soloPerPrecisiConCampanellaAccesa() {
        val c = tachipirina()
        assertTrue(c.reminderEligible())
        assertFalse(c.copy(bellEnabled = false).reminderEligible())
        assertFalse(c.copy(bellMinutes = null).reminderEligible())
        assertFalse(giornaliero().reminderEligible())
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
