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

    /** Prossima scadenza campanella: soglia normale, oppure il rinvio se già notificata e rimandata. */
    @Query(
        "SELECT MIN(CASE WHEN bellNotified = 0 THEN startMs + bellMinutes * 60000 ELSE snoozeUntilMs END) " +
            "FROM counters WHERE archived = 0 AND bellMinutes IS NOT NULL AND (bellNotified = 0 OR snoozeUntilMs IS NOT NULL)"
    )
    suspend fun nextBellDeadline(): Long?

    @Query(
        "SELECT * FROM counters WHERE archived = 0 AND bellMinutes IS NOT NULL " +
            "AND ((bellNotified = 0 AND startMs + bellMinutes * 60000 <= :now) " +
            "OR (bellNotified = 1 AND snoozeUntilMs IS NOT NULL AND snoozeUntilMs <= :now))"
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

@Database(entities = [Counter::class, Round::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun counterDao(): CounterDao
    abstract fun roundDao(): RoundDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE counters ADD COLUMN snoozeUntilMs INTEGER")
    }
}
