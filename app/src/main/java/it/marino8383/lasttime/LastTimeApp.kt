package it.marino8383.lasttime

import android.app.Application
import androidx.room.Room
import it.marino8383.lasttime.data.AppDatabase
import it.marino8383.lasttime.data.MIGRATION_1_2
import it.marino8383.lasttime.data.MIGRATION_2_3
import it.marino8383.lasttime.data.MIGRATION_3_4
import it.marino8383.lasttime.notif.Notifications

class LastTimeApp : Application() {

    val db: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "lasttime.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
