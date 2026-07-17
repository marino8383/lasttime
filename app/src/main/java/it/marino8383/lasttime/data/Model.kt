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
import kotlinx.coroutines.flow.Flow

/**
 * Un contatore "da quanto tempo non...". startMs è l'inizio del round corrente;
 * i round conclusi vivono nella tabella rounds. Tutto in UTC epoch millis.
 */
@Entity(tableName = "counters")
data class Counter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
    val scheduledResetMs: Long? = null,
    val createdMs: Long,
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
    indices = [Index("counterId", "endMs")],
)
data class Round(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val counterId: Long,
    val startMs: Long,
    val endMs: Long,
    /** Giro perso "solo conteggio": vale per le statistiche di frequenza ma non per le durate. */
    val noTime: Boolean = false,
)

@Dao
interface CounterDao {
    @Query("SELECT * FROM counters WHERE archived = 0 ORDER BY createdMs")
    fun activeCounters(): Flow<List<Counter>>

    @Query("SELECT * FROM counters WHERE archived = 1 ORDER BY createdMs")
    fun archivedCounters(): Flow<List<Counter>>

    /** Prossimo evento campanella: squillo programmato oppure rinvio, il più vicino. */
    @Query(
        "SELECT MIN(CASE " +
            "WHEN nextBellAtMs IS NOT NULL AND snoozeUntilMs IS NOT NULL THEN MIN(nextBellAtMs, snoozeUntilMs) " +
            "WHEN nextBellAtMs IS NOT NULL THEN nextBellAtMs " +
            "ELSE snoozeUntilMs END) " +
            "FROM counters WHERE archived = 0 AND bellEnabled = 1 " +
            "AND (nextBellAtMs IS NOT NULL OR snoozeUntilMs IS NOT NULL)"
    )
    suspend fun nextBellDeadline(): Long?

    @Query(
        "SELECT * FROM counters WHERE archived = 0 AND bellEnabled = 1 " +
            "AND ((nextBellAtMs IS NOT NULL AND nextBellAtMs <= :now) " +
            "OR (snoozeUntilMs IS NOT NULL AND snoozeUntilMs <= :now))"
    )
    suspend fun dueBellCounters(now: Long): List<Counter>

    @Query("SELECT * FROM counters WHERE id = :id")
    suspend fun byId(id: Long): Counter?

    @Insert
    suspend fun insert(counter: Counter): Long

    @Update
    suspend fun update(counter: Counter)

    @Delete
    suspend fun delete(counter: Counter)
}

@Dao
interface RoundDao {
    @Insert
    suspend fun insert(round: Round)

    @Query("SELECT * FROM rounds WHERE counterId = :counterId ORDER BY endMs DESC")
    fun roundsFor(counterId: Long): Flow<List<Round>>
}

@Database(entities = [Counter::class, Round::class], version = 4, exportSchema = false)
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

/** Soglia di "scaduta da poco": entro questo ritardo il Fatto mantiene il ritmo senza chiedere. */
fun bellLateThreshold(stepMs: Long): Long = minOf(5 * 60_000L, stepMs / 2)

fun advanceToFuture(from: Long, stepMs: Long, now: Long): Long {
    var next = from
    while (next <= now) next += stepMs
    return next
}

/**
 * Da quanto è suonata l'ultima scadenza di una ricorrente attiva (null se non
 * applicabile o non ancora suonata). Serve a decidere se il Fatto deve chiedere.
 */
fun Counter.bellLatenessMs(now: Long): Long? {
    val step = bellMinutes?.times(60_000) ?: return null
    if (!bellRepeat || !bellEnabled || nextBellAtMs == null) return null
    return (now - (nextBellAtMs - step)).takeIf { it >= 0 }
}

/**
 * Restart del contatore ("Fatto" o ↺): round chiuso altrove, qui il nuovo stato.
 * Ricorrente: scaduta da poco → mantiene il ritmo comunque; altrimenti segue il
 * bellMode (INTERVAL: X da adesso; FIXED: il ritmo non cambia). Singola non ancora
 * suonata: segue il nuovo round. Singola già suonata: si spegne ("e bona").
 */
fun Counter.restarted(now: Long): Counter {
    val step = bellMinutes?.times(60_000)
    var nextBell = nextBellAtMs
    var enabled = bellEnabled
    if (step != null && bellEnabled) {
        if (bellRepeat) {
            val lateness = bellLatenessMs(now)
            val slightlyLate = lateness != null && lateness <= bellLateThreshold(step)
            nextBell = if (nextBellAtMs != null && (bellMode == "FIXED" || slightlyLate)) {
                advanceToFuture(nextBellAtMs, step, now)
            } else {
                now + step
            }
        } else {
            if (nextBellAtMs == null) enabled = false else nextBell = now + step
        }
    }
    return copy(
        startMs = now,
        bellNotified = false,
        snoozeUntilMs = null,
        nextBellAtMs = nextBell,
        bellEnabled = enabled,
    )
}
