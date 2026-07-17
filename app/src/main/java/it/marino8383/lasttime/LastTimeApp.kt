package it.marino8383.lasttime

import android.app.Application
import androidx.room.Room
import it.marino8383.lasttime.data.AppDatabase

class LastTimeApp : Application() {

    val db: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "lasttime.db").build()
    }
}
