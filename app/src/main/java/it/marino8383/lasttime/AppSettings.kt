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

    /** Nome con cui mi presento nei gruppi condivisi. Chiesto una volta sola. */
    fun myName(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("my_name", "") ?: ""

    fun setMyName(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("my_name", value.trim()).apply()
        it.marino8383.lasttime.sync.Cloud.myName = value.trim()
    }

    /**
     * I gruppi di cui faccio parte. Salvati qui e non dedotti dai contatori condivisi:
     * senza, un telefono che al momento non ha niente di condiviso "dimentica" il gruppo,
     * non scarica quello che gli viene condiviso e alla prossima condivisione ne crea uno
     * nuovo invece di usare quello dove sta l'altra persona.
     */
    fun groups(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("groups", emptySet())?.toSet() ?: emptySet()

    fun addGroup(context: Context, groupId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // getStringSet restituisce un insieme da non modificare: se ne fa una copia
        val nuovi = prefs.getStringSet("groups", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (nuovi.add(groupId)) prefs.edit().putStringSet("groups", nuovi).apply()
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
