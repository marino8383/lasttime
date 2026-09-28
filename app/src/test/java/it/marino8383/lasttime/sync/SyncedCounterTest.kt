package it.marino8383.lasttime.sync

import it.marino8383.lasttime.data.Counter
import it.marino8383.lasttime.data.CounterMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * La tabella dei campi condivisi: quello che parte da un telefono deve arrivare uguale
 * sull'altro, e le scelte locali non devono mai viaggiare.
 */
class SyncedCounterTest {

    private val mio = Counter(
        name = "Tachipirina",
        startMs = 1_000,
        createdMs = 0,
        bellMinutes = 480,
        bellMode = "FIXED",
        bellRepeat = false,
        archived = true,
        archivedMs = 2_000,
        historyFromMs = 500,
        lastRoundUuid = "r1",
        lastRoundStartMs = 900,
        mode = CounterMode.GIORNALIERO,
        dailyBellMinuteOfDay = 900,
        creatorUid = "uid-mio",
        roundMinutes = 15,
        // locali: non devono viaggiare
        bellEnabled = false,
        viewMode = "COMPACT",
        hidden = true,
        sortOrder = 7,
    )

    @Test
    fun andataERitorno_iCampiCondivisiArrivanoUguali() {
        val suo = Counter(name = "", startMs = 0, createdMs = 0)
        val arrivato = SyncedCounter.apply(suo, SyncedCounter.payload(mio))
        assertEquals(
            mio.copy(
                id = suo.id, uuid = suo.uuid, createdMs = suo.createdMs,
                bellEnabled = suo.bellEnabled, viewMode = suo.viewMode,
                hidden = suo.hidden, sortOrder = suo.sortOrder,
            ),
            arrivato,
        )
    }

    @Test
    fun leSceltelocaliNonViaggiano() {
        val chiavi = SyncedCounter.payload(mio).keys
        for (locale in listOf("bellEnabled", "bellNotified", "snoozeUntilMs", "viewMode", "hidden", "sortOrder", "notifyOnRemote", "sharedGroupId")) {
            assertFalse("$locale è locale, non deve andare al gruppo", locale in chiavi)
        }
    }

    @Test
    fun creatorUid_nonTornaANullDaUnaScritturaVecchia() {
        val vecchia = SyncedCounter.payload(mio) - "creatorUid"
        assertEquals("uid-mio", SyncedCounter.apply(mio, vecchia).creatorUid)
    }

    @Test
    fun payloadVecchio_senzaCampiNuovi_prendeIDefault() {
        val minimo = mapOf<String, Any?>("name" to "X", "startMs" to 5L)
        val c = SyncedCounter.apply(mio, minimo)
        assertEquals(CounterMode.PRECISO, c.mode)
        assertEquals("INTERVAL", c.bellMode)
        assertEquals(true, c.bellRepeat)
        assertEquals(false, c.archived)
    }
}
