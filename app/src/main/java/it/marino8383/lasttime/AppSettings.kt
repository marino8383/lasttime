package it.marino8383.lasttime

import android.content.Context

/** Opzioni dell'app, persistite in SharedPreferences. */
object AppSettings {
    private const val PREFS = "settings"
    private const val KEY_LATE_PERCENT = "late_percent"
    const val DEFAULT_LATE_PERCENT = 3

    /** Tolleranza "mantieni il ritmo" in % del periodo campanella. */
    fun latePercent(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_LATE_PERCENT, DEFAULT_LATE_PERCENT)

    fun setLatePercent(context: Context, value: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_LATE_PERCENT, value.coerceIn(1, 50)).apply()
    }
}
