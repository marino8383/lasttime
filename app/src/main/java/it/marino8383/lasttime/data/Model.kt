package it.marino8383.lasttime.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import it.marino8383.lasttime.sync.Cloud
import it.marino8383.lasttime.sync.SyncEngine
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Un contatore "da quanto tempo non...". startMs è l'inizio del round corrente;
 * i round conclusi vivono nella tabella rounds. Tutto in UTC epoch millis.
 */
@Entity(tableName = "counters", indices = [Index(value = ["uuid"], unique = true)])
data class Counter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * Identità stabile del contatore fra dispositivi: l'id autoincrementale è locale,
     * due telefoni assegnano lo stesso numero a contatori diversi.
     */
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val startMs: Long,
    val viewMode: String = "FULL",
    val bellMinutes: Long? = null,
    val bellNotified: Boolean = false,
    /** Se valorizzato, la campanella è stata rimandata: ri-notifica a quest'ora. */
    val snoozeUntilMs: Long? = null,
    /** Campanella accesa/spenta senza perderne la configurazione ("Scarta" la spegne). */
    val bellEnabled: Boolean = true,
    /** INTERVAL = prossima campanella X dopo il Fatto; FIXED = mantiene il ritmo (solo per le ricorrenti). */
    val bellMode: String = "INTERVAL",
    /** Prossimo squillo programmato; null = nessuno (es. singola già suonata). */
    val nextBellAtMs: Long? = null,
    /** false = singola (suona una volta e si spegne), true = ricorrente (si riarma ogni X). */
    val bellRepeat: Boolean = true,
    val secret: Boolean = false,
    val archived: Boolean = false,
    /** Quando è stato archiviato; il timer resta congelato a quell'istante. */
    val archivedMs: Long? = null,
    /**
     * Confine dello storico: i round finiti prima di questo istante restano nel database
     * ma non compaiono più in storico e statistiche. Serve a "riparti pulito" senza
     * cancellare niente — i round sono append-only di proposito, così una persona non
     * può riscrivere la storia dell'altra. Null = tieni tutto.
     */
    val historyFromMs: Long? = null,
    val scheduledResetMs: Long? = null,
    val createdMs: Long,
    /** Ultima scrittura: è il timestamp su cui si risolveranno i conflitti fra dispositivi. */
    val updatedMs: Long = 0,
    /**
     * Gruppo con cui questo contatore è condiviso; null = solo su questo telefono.
     * È un campo locale, non si sincronizza: è il telefono che decide cosa condivide.
     */
    val sharedGroupId: String? = null,
    /**
     * Avvisami quando un altro fa ripartire questo timer condiviso. Locale come tutte le
     * scelte sulle notifiche: se a te interessa e all'altro no, ognuno fa come vuole.
     */
    val notifyOnRemote: Boolean = true,
)

@Entity(
    tableName = "rounds",
    foreignKeys = [
        ForeignKey(
            entity = Counter::class,
            parentColumns = ["id"],
            childColumns = ["counterId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("counterId", "endMs"),
        Index(value = ["uuid"], unique = true),
    ],
)
data class Round(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Come [Counter.uuid]: serve a non duplicare lo stesso evento arrivando da due telefoni. */
    val uuid: String = UUID.randomUUID().toString(),
    val counterId: Long,
    val startMs: Long,
    val endMs: Long,
    /** Giro perso "solo conteggio": vale per le statistiche di frequenza ma non per le durate. */
    val noTime: Boolean = false,
    /**
     * Chi ha registrato l'evento, sui timer condivisi. È il nome **copiato** al momento,
     * non un riferimento al membro: così lo storico resta leggibile anche se quella
     * persona cambia telefono o esce dal gruppo. Null = l'ho fatto io, o non è condiviso.
     */
    val byName: String? = null,
)

@Dao
interface CounterDao {
    @Query("SELECT * FROM counters WHERE archived = 0 ORDER BY createdMs")
    fun activeCounters(): Flow<List<Counter>>

    @Query("SELECT * FROM counters WHERE archived = 1 ORDER BY createdMs")
    fun archivedCounters(): Flow<List<Counter>>

    /**
     * Prossimo evento campanella: squillo programmato non ancora notificato, oppure
     * rinvio pendente. Una campanella già suonata (bellNotified) NON risuona da sola:
     * si riarma solo con Fatto/reset/Rimanda.
     */
    @Query(
        "SELECT MIN(CASE WHEN bellNotified = 0 THEN nextBellAtMs ELSE snoozeUntilMs END) " +
            "FROM counters WHERE archived = 0 AND bellEnabled = 1 " +
            "AND ((bellNotified = 0 AND nextBellAtMs IS NOT NULL) " +
            "OR (bellNotified = 1 AND snoozeUntilMs IS NOT NULL))"
    )
    suspend fun nextBellDeadline(): Long?

    @Query(
        "SELECT * FROM counters WHERE archived = 0 AND bellEnabled = 1 " +
            "AND ((bellNotified = 0 AND nextBellAtMs IS NOT NULL AND nextBellAtMs <= :now) " +
            "OR (bellNotified = 1 AND snoozeUntilMs IS NOT NULL AND snoozeUntilMs <= :now))"
    )
    suspend fun dueBellCounters(now: Long): List<Counter>

    @Query("SELECT * FROM counters WHERE id = :id")
    suspend fun byId(id: Long): Counter?

    @Query("SELECT * FROM counters WHERE uuid = :uuid")
    suspend fun byUuid(uuid: String): Counter?

    /** Tutti i condivisi, archiviati compresi: il sync non guarda l'archivio. */
    @Query("SELECT * FROM counters WHERE sharedGroupId IS NOT NULL")
    suspend fun shared(): List<Counter>

    @Query("SELECT MIN(scheduledResetMs) FROM counters WHERE archived = 0 AND scheduledResetMs IS NOT NULL")
    suspend fun nextScheduledReset(): Long?

    @Query("SELECT * FROM counters WHERE archived = 0 AND scheduledResetMs IS NOT NULL AND scheduledResetMs <= :now")
    suspend fun dueScheduledResets(now: Long): List<Counter>

    @Insert
    suspend fun insertRaw(counter: Counter): Long

    @Update
    suspend fun updateRaw(counter: Counter)

    @Delete
    suspend fun delete(counter: Counter)
}

/**
 * Ogni scrittura su un contatore passa di qui, così updatedMs non può restare indietro
 * per distrazione: è il timestamp su cui la sincronizzazione deciderà chi ha ragione.
 * I metodi grezzi del DAO esistono solo perché Room li vuole generare.
 */
suspend fun CounterDao.save(counter: Counter) {
    val stamped = counter.copy(updatedMs = System.currentTimeMillis())
    updateRaw(stamped)
    // Il push sta qui e non nei singoli casi d'uso: e' l'unico modo per essere certi
    // che nessuna scrittura resti a terra, receiver delle notifiche compresi.
    SyncEngine.pushIfShared(stamped)
}

/**
 * Scrittura di soli campi **locali**: campanella accesa/spenta, rinvii, "ho gia' notificato",
 * vista delle cifre, avvisi sui condivisi.
 *
 * Non timbra updatedMs e non manda niente al gruppo, di proposito. Passando da [save] una
 * faccenda interna di questo telefono — per esempio "la campanella ha suonato" — si
 * timbrerebbe piu' recente di una modifica vera fatta dall'altra persona, e vincerebbe il
 * confronto: il riavvio dell'altro verrebbe scartato e questo telefono resterebbe indietro
 * per sempre, pur risultando sincronizzato.
 */
suspend fun CounterDao.saveLocal(counter: Counter) {
    updateRaw(counter)
}

suspend fun CounterDao.create(counter: Counter): Long =
    insertRaw(counter.copy(updatedMs = System.currentTimeMillis()))

@Dao
interface RoundDao {
    @Insert
    suspend fun insert(round: Round)

    @Query(
        "SELECT * FROM rounds WHERE counterId = :counterId AND endMs >= :fromMs " +
            "ORDER BY endMs DESC"
    )
    fun roundsFor(counterId: Long, fromMs: Long): Flow<List<Round>>

    @Query("SELECT * FROM rounds WHERE uuid = :uuid")
    suspend fun byUuid(uuid: String): Round?

    /** Fine dell'ultimo round con tempi: nessun round nuovo può cominciare prima. */
    @Query("SELECT MAX(endMs) FROM rounds WHERE counterId = :counterId AND noTime = 0")
    suspend fun lastEnd(counterId: Long): Long?

    /** Chi ha registrato l'ultimo evento, per intestare gli avvisi. */
    @Query("SELECT byName FROM rounds WHERE counterId = :counterId ORDER BY endMs DESC LIMIT 1")
    suspend fun lastAuthor(counterId: Long): String?

    /** Ultimi round di un contatore, per mandarli su quando lo si condivide. */
    @Query("SELECT * FROM rounds WHERE counterId = :counterId ORDER BY endMs DESC LIMIT :max")
    suspend fun recentFor(counterId: Long, max: Int): List<Round>

    /** Una riga per contatore, per la card d'archivio: quanti round e quanto è durato l'ultimo. */
    @Query(
        "SELECT r.counterId AS counterId, COUNT(*) AS rounds, " +
            "(SELECT r2.endMs - r2.startMs FROM rounds r2 " +
            "WHERE r2.counterId = r.counterId AND r2.noTime = 0 " +
            "AND r2.endMs >= IFNULL(c.historyFromMs, 0) " +
            "ORDER BY r2.endMs DESC LIMIT 1) AS lastDurationMs " +
            "FROM rounds r JOIN counters c ON c.id = r.counterId " +
            "WHERE r.endMs >= IFNULL(c.historyFromMs, 0) " +
            "GROUP BY r.counterId"
    )
    fun summaries(): Flow<List<RoundSummary>>
}

/**
 * Come [CounterDao.save] per i contatori: unico imbuto per gli eventi, così l'autore
 * viene timbrato e la copia va su senza che nessun caso d'uso se ne debba ricordare.
 */
suspend fun RoundDao.add(round: Round, counter: Counter) {
    // Un round non può cominciare prima della fine di quello precedente. Su un timer
    // condiviso il caso si presenta davvero: se questo telefono aveva una copia vecchia
    // di startMs, scriverebbe un evento accavallato a uno già registrato dall'altro —
    // nello storico si vedono due round che partono dallo stesso istante e i conteggi
    // si gonfiano. Qui l'inizio viene portato avanti alla fine dell'ultimo round noto.
    val ultimaFine = lastEnd(counter.id) ?: 0
    val senzaSovrapposizione =
        if (!round.noTime && round.startMs < ultimaFine && ultimaFine <= round.endMs) {
            round.copy(startMs = ultimaFine)
        } else {
            round
        }
    val firmato = if (counter.sharedGroupId != null && senzaSovrapposizione.byName == null) {
        senzaSovrapposizione.copy(byName = Cloud.myName.takeIf { it.isNotBlank() })
    } else {
        senzaSovrapposizione
    }
    insert(firmato)
    SyncEngine.pushRoundIfShared(counter, firmato)
}

/** Riepilogo dei round di un contatore (vedi [RoundDao.summaries]). */
data class RoundSummary(
    val counterId: Long,
    val rounds: Int,
    /** Durata dell'ultimo round con tempi; null se ci sono solo giri persi. */
    val lastDurationMs: Long?,
)

@Database(entities = [Counter::class, Round::class], version = 10, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun counterDao(): CounterDao
    abstract fun roundDao(): RoundDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE counters ADD COLUMN snoozeUntilMs INTEGER")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE counters ADD COLUMN bellEnabled INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE counters ADD COLUMN bellMode TEXT NOT NULL DEFAULT 'INTERVAL'")
        db.execSQL("ALTER TABLE counters ADD COLUMN nextBellAtMs INTEGER")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE counters ADD COLUMN bellRepeat INTEGER NOT NULL DEFAULT 1")
        // Backfill: da qui in poi la scadenza vive solo in nextBellAtMs
        db.execSQL(
            "UPDATE counters SET nextBellAtMs = startMs + bellMinutes * 60000 " +
                "WHERE bellMinutes IS NOT NULL AND nextBellAtMs IS NULL"
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE counters ADD COLUMN archivedMs INTEGER")
        // Archiviati di prima (non ce ne sono, ma il DB non deve restare incoerente)
        db.execSQL("UPDATE counters SET archivedMs = createdMs WHERE archived = 1 AND archivedMs IS NULL")
    }
}

/**
 * Identità stabili in vista della sincronizzazione fra dispositivi. Solo ALTER TABLE e
 * UPDATE, nessuna tabella ricreata: è l'unica forma che non può perdere dati.
 * randomblob() non è deterministica, quindi genera un valore diverso per ogni riga.
 * I nomi degli indici sono quelli che Room si aspetta (index_<tabella>_<colonna>):
 * se non corrispondono, la validazione dello schema fallisce all'apertura del database.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE counters ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE counters ADD COLUMN updatedMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE rounds ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE counters SET uuid = lower(hex(randomblob(16))) WHERE uuid = ''")
        db.execSQL("UPDATE counters SET updatedMs = createdMs WHERE updatedMs = 0")
        db.execSQL("UPDATE rounds SET uuid = lower(hex(randomblob(16))) WHERE uuid = ''")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_counters_uuid ON counters (uuid)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_rounds_uuid ON rounds (uuid)")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE counters ADD COLUMN sharedGroupId TEXT")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE counters ADD COLUMN historyFromMs INTEGER")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE counters ADD COLUMN notifyOnRemote INTEGER NOT NULL DEFAULT 1")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE rounds ADD COLUMN byName TEXT")
    }
}

/**
 * Soglia di "scaduta da poco": entro questo ritardo il Fatto mantiene il ritmo senza
 * chiedere. In percentuale del periodo (configurabile in Opzioni; default 3%:
 * 8 h → ~15 min, 1 min → ~2 s).
 */
fun bellLateThreshold(stepMs: Long, percent: Int): Long = stepMs * percent / 100

fun advanceToFuture(from: Long, stepMs: Long, now: Long): Long {
    var next = from
    while (next <= now) next += stepMs
    return next
}

/**
 * Da quanto è sforata una ricorrente attiva (null se non applicabile o se non è
 * suonata senza risposta). Conta solo se davvero suonata (bellNotified): una
 * ciclica a metà ciclo non è "in ritardo". Serve a decidere se il Fatto deve chiedere.
 */
fun Counter.bellLatenessMs(now: Long): Long? {
    if (bellMinutes == null || !bellRepeat || !bellEnabled || !bellNotified || nextBellAtMs == null) return null
    // la scadenza suonata resta in nextBellAtMs (non si auto-avanza più)
    return (now - nextBellAtMs).takeIf { it >= 0 }
}

/**
 * Restart del contatore ("Fatto" o ↺): round chiuso altrove, qui il nuovo stato.
 * Ricorrente: la campanella si resetta e riparte — sforata da poco → mantiene il
 * ritmo; altrimenti segue il bellMode (INTERVAL: X da adesso; FIXED: il ritmo non
 * cambia). Singola: il reset la disattiva sempre.
 */
fun Counter.restarted(now: Long, latePercent: Int): Counter {
    val step = bellMinutes?.times(60_000)
    var nextBell = nextBellAtMs
    var enabled = bellEnabled
    // Nessun controllo su bellEnabled: spegnere la campanella toglie la notifica, non
    // la cadenza. Chi l'ha spenta deve comunque poter leggere quando scade il giro.
    if (step != null) {
        if (bellRepeat) {
            val lateness = bellLatenessMs(now)
            val slightlyLate = lateness != null && lateness <= bellLateThreshold(step, latePercent)
            nextBell = if (nextBellAtMs != null && (bellMode == "FIXED" || slightlyLate)) {
                advanceToFuture(nextBellAtMs, step, now)
            } else {
                now + step
            }
        } else {
            enabled = false
            nextBell = null
        }
    }
    return copy(
        startMs = now,
        bellNotified = false,
        snoozeUntilMs = null,
        nextBellAtMs = nextBell,
        bellEnabled = enabled,
        scheduledResetMs = null, // l'ultimo comando vince: un restart annulla il reset programmato
    )
}
