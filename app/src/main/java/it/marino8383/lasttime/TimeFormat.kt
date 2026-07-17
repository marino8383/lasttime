package it.marino8383.lasttime

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Modalità di visualizzazione del tempo trascorso, ciclabile toccando le cifre. */
enum class ViewMode {
    FULL, YEARS, DAYS, MINUTES, SECONDS;

    fun next(): ViewMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun from(value: String): ViewMode = entries.firstOrNull { it.name == value } ?: FULL
    }
}

private const val SEC_PER_MINUTE = 60L
private const val SEC_PER_HOUR = 3_600L
private const val SEC_PER_DAY = 86_400L
private const val SEC_PER_YEAR = 31_536_000L // 365 giorni

data class Breakdown(
    val years: Long,
    val days: Long,
    val hours: Long,
    val minutes: Long,
    val seconds: Long,
)

fun breakdown(elapsedMs: Long): Breakdown {
    var s = (elapsedMs / 1000).coerceAtLeast(0)
    val years = s / SEC_PER_YEAR; s %= SEC_PER_YEAR
    val days = s / SEC_PER_DAY; s %= SEC_PER_DAY
    val hours = s / SEC_PER_HOUR; s %= SEC_PER_HOUR
    val minutes = s / SEC_PER_MINUTE
    val seconds = s % SEC_PER_MINUTE
    return Breakdown(years, days, hours, minutes, seconds)
}

/**
 * Coppie (valore, etichetta) da mostrare in card: es. FULL -> [("3","g"),("4","h"),("05","m"),("12","s")].
 * Anni e giorni compaiono solo se necessari, come nel mockup.
 */
fun timeParts(elapsedMs: Long, mode: ViewMode): List<Pair<String, String>> {
    val totalSec = (elapsedMs / 1000).coerceAtLeast(0)
    return when (mode) {
        ViewMode.FULL -> {
            val b = breakdown(elapsedMs)
            buildList {
                if (b.years > 0) add(b.years.toString() to "a")
                if (b.years > 0 || b.days > 0) add(b.days.toString() to "g")
                add(b.hours.toString() to "h")
                add(String.format(Locale.ITALIAN, "%02d", b.minutes) to "m")
                add(String.format(Locale.ITALIAN, "%02d", b.seconds) to "s")
            }
        }
        ViewMode.YEARS -> listOf(String.format(Locale.ITALIAN, "%.4f", totalSec / SEC_PER_YEAR.toDouble()) to "anni")
        ViewMode.DAYS -> listOf(String.format(Locale.ITALIAN, "%.2f", totalSec / SEC_PER_DAY.toDouble()) to "giorni")
        ViewMode.MINUTES -> listOf(String.format(Locale.ITALIAN, "%,d", totalSec / SEC_PER_MINUTE) to "min")
        ViewMode.SECONDS -> listOf(String.format(Locale.ITALIAN, "%,d", totalSec) to "s")
    }
}

private val dateTimeFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ITALIAN)
private val shortDateTimeFmt = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.ITALIAN)

fun formatDateTime(epochMs: Long): String =
    dateTimeFmt.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

fun formatShortDateTime(epochMs: Long): String =
    shortDateTimeFmt.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

private data class DurUnit(val sec: Long, val one: String, val many: String)

private val durUnits = listOf(
    DurUnit(SEC_PER_YEAR, "anno", "anni"),
    DurUnit(2_592_000, "mese", "mesi"), // 30 giorni
    DurUnit(SEC_PER_DAY, "giorno", "giorni"),
    DurUnit(SEC_PER_HOUR, "h", "h"),
    DurUnit(SEC_PER_MINUTE, "min", "min"),
    DurUnit(1, "s", "s"),
)

/**
 * Durata a due componenti (v18): unità dominante + successiva, omessa se zero.
 * Es. "1 h e 5 min", "6 giorni e 4 h", "1 anno e 4 mesi", "45 s".
 */
fun formatDurationTwoParts(durationMs: Long): String {
    val s = (durationMs / 1000).coerceAtLeast(0)
    val idx = durUnits.indexOfFirst { s >= it.sec }
    if (idx == -1) return "0 s"
    val unit = durUnits[idx]
    val value = s / unit.sec
    val first = "$value ${if (value == 1L) unit.one else unit.many}"
    if (idx == durUnits.lastIndex) return first
    val next = durUnits[idx + 1]
    val rem = (s % unit.sec) / next.sec
    return if (rem > 0) "$first e $rem ${if (rem == 1L) next.one else next.many}" else first
}

/** Descrizione della campanella, es. "🔔 3 g" / "🔔 45 min". */
fun bellLabel(bellMinutes: Long): String = when {
    bellMinutes % (24 * 60) == 0L -> "${bellMinutes / (24 * 60)} g"
    bellMinutes % 60 == 0L -> "${bellMinutes / 60} h"
    else -> "$bellMinutes min"
}
