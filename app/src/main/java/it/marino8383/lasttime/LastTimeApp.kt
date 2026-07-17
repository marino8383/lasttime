package it.marino8383.lasttime

import android.app.Application
import androidx.room.Room
import it.marino8383.lasttime.data.AppDatabase
import it.marino8383.lasttime.notif.Notifications

class LastTimeApp : Application() {

    val db: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "lasttime.db").build()
    }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
