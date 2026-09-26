package it.marino8383.lasttime

import android.app.Application
import androidx.room.Room
import it.marino8383.lasttime.data.AppDatabase
import it.marino8383.lasttime.data.MIGRATION_1_2
import it.marino8383.lasttime.data.MIGRATION_2_3
import it.marino8383.lasttime.data.MIGRATION_3_4
import it.marino8383.lasttime.data.MIGRATION_4_5
import it.marino8383.lasttime.data.MIGRATION_5_6
import it.marino8383.lasttime.data.MIGRATION_6_7
import it.marino8383.lasttime.data.MIGRATION_7_8
import it.marino8383.lasttime.data.MIGRATION_8_9
import it.marino8383.lasttime.data.MIGRATION_9_10
import it.marino8383.lasttime.data.MIGRATION_10_11
import it.marino8383.lasttime.data.MIGRATION_11_12
import it.marino8383.lasttime.data.MIGRATION_12_13
import it.marino8383.lasttime.data.MIGRATION_13_14
import it.marino8383.lasttime.data.MIGRATION_14_15
import it.marino8383.lasttime.data.MIGRATION_15_16
import it.marino8383.lasttime.notif.Notifications
import it.marino8383.lasttime.sync.Cloud
import it.marino8383.lasttime.sync.SyncStatus

class LastTimeApp : Application() {

    val db: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "lasttime.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        Cloud.connect()
        SyncStatus.init(this)
        Cloud.myName = AppSettings.myName(this)
    }
}
