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

    // ---- vista tabellone Solari ----

    fun flipUnit(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("flip_unit", "SPEZZATO") ?: "SPEZZATO"

    fun setFlipUnit(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("flip_unit", value).apply()
    }

    fun flipShowYears(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("flip_show_years", true)

    fun setFlipShowYears(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("flip_show_years", value).apply()
    }

    fun flipShowSeconds(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean("flip_show_seconds", true)

    fun setFlipShowSeconds(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("flip_show_seconds", value).apply()
    }
}
