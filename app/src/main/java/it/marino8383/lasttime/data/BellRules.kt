package it.marino8383.lasttime.data

/*
 * Regole della campanella e del riavvio: solo calcoli puri sullo stato di un Counter,
 * niente database, niente rete, niente Android. Stanno qui da sole apposta — sono il
 * pezzo più delicato dell'app e l'unico che si può coprire con test JVM semplici
 * (vedi app/src/test). Chi scrive sul database passa da CounterActions.
 */

/** Valori validi di [Counter.mode]. */
object CounterMode {
    const val PRECISO = "PRECISO"
    const val GIORNALIERO = "GIORNALIERO"
}

/**
 * Prossima campanella ricalcolata da zero a partire da [fromMs], secondo la modalità:
 * PRECISO = [bellMinutes] dopo quell'istante; GIORNALIERO = N giorni dopo quella **data**
 * all'orario fisso [dailyBellMinuteOfDay]. Null se la campanella non è configurata.
 *
 * È l'unico punto che sa come si ricalcola una scadenza: prima c'erano nove copie e
 * solo alcune distinguevano le due modalità (una Giornaliera ripresa dall'archivio
 * perdeva l'orario fisso e suonava a N×24 ore dal momento del ripristino).
 */
fun nextBellFrom(mode: String, bellMinutes: Long?, dailyBellMinuteOfDay: Int?, fromMs: Long): Long? =
    if (mode == CounterMode.GIORNALIERO) {
        val days = bellMinutes?.div(1440)
        if (days != null && dailyBellMinuteOfDay != null) nextDailyBellAtMs(fromMs, days, dailyBellMinuteOfDay) else null
    } else {
        bellMinutes?.let { fromMs + it * 60_000 }
    }

/** Vedi [nextBellFrom]. */
fun Counter.bellFrom(fromMs: Long): Long? = nextBellFrom(mode, bellMinutes, dailyBellMinuteOfDay, fromMs)

/**
 * Soglia di "scaduta da poco": entro questo ritardo il Fatto mantiene il ritmo senza
 * chiedere. In percentuale del periodo (configurabile in Opzioni; default 3%:
 * 8 h → ~15 min, 1 min → ~2 s).
 */
fun bellLateThreshold(stepMs: Long, percent: Int): Long = stepMs * percent / 100

fun advanceToFuture(from: Long, stepMs: Long, now: Long): Long {
    var next = from
    while (next <= now) next += stepMs
    return next
}

/**
 * Da quanto è sforata una ricorrente (null se non applicabile o se non è suonata senza
 * risposta). Conta solo se davvero suonata (bellNotified): una ciclica a metà ciclo non
 * è "in ritardo". Serve a decidere se il Fatto deve chiedere. Vale anche a campanella
 * silenziata: spegnerla toglie la notifica, non il ritmo — vedi Counter.restarted.
 */
fun Counter.bellLatenessMs(now: Long): Long? {
    if (bellMinutes == null || !bellRepeat || !bellNotified || nextBellAtMs == null) return null
    // la scadenza suonata resta in nextBellAtMs (non si auto-avanza più)
    return (now - nextBellAtMs).takeIf { it >= 0 }
}

/**
 * Simmetrico di [bellLatenessMs]: quanto manca alla campanella di una ricorrente non
 * ancora scaduta. Serve a decidere se un Fatto anticipato (fatto un po' prima del
 * previsto) mantiene comunque il ritmo, come già succede per un ritardo lieve — anche
 * a campanella silenziata, stesso motivo.
 */
fun Counter.bellEarlinessMs(now: Long): Long? {
    if (bellMinutes == null || !bellRepeat || bellNotified || nextBellAtMs == null) return null
    return (nextBellAtMs - now).takeIf { it > 0 }
}

/** Quanto un Fatto adesso cade fuori dal ritmo di una ricorrente. */
enum class RhythmDeviation {
    /** Non c'è un ritmo da confrontare (singola, senza campanella, a metà ciclo). */
    NONE,
    /** Anticipo o ritardo entro la soglia: si mantiene il ritmo senza chiedere. */
    SLIGHT,
    /** Oltre la soglia, in un verso o nell'altro: si chiede cosa fare (LateBellDialog). */
    LARGE,
}

/**
 * Unico punto che decide "fuori soglia sì o no". Lo usano sia la conferma del ↺ (per
 * sapere se aprire LateBellDialog) sia [restarted] (per sapere se mantenere il ritmo):
 * se lo calcolassero ognuno per conto suo, basterebbe ritoccarne uno per avere un Fatto
 * che non chiede niente e poi non mantiene il ritmo.
 */
fun Counter.rhythmDeviation(now: Long, latePercent: Int): RhythmDeviation {
    val step = bellMinutes?.times(60_000) ?: return RhythmDeviation.NONE
    val scarto = bellLatenessMs(now) ?: bellEarlinessMs(now) ?: return RhythmDeviation.NONE
    return if (scarto <= bellLateThreshold(step, latePercent)) RhythmDeviation.SLIGHT else RhythmDeviation.LARGE
}

/**
 * Prossima scadenza "mantenendo il ritmo" rispetto alla scadenza precedente
 * [nextBellAtMs]: sempre almeno un passo dopo di lei, mai lei stessa invariata — un
 * Fatto anticipato arriva con [now] ancora prima di [nextBellAtMs], e [advanceToFuture]
 * da solo la lascerebbe ferma lì, come se questo Fatto non avesse chiuso nessun giro.
 */
fun keepRhythmNextBell(nextBellAtMs: Long, step: Long, now: Long): Long =
    advanceToFuture(nextBellAtMs + step, step, now)

/**
 * Restart del contatore ("Fatto" o ↺): round chiuso altrove, qui il nuovo stato.
 * Ricorrente: la campanella si resetta e riparte — scaduta da poco o anticipata di
 * poco (stessa soglia, simmetrica) → mantiene il ritmo; altrimenti segue il bellMode
 * (INTERVAL: X da adesso; FIXED: il ritmo non cambia mai). Singola: il reset la
 * disattiva sempre.
 */
fun Counter.restarted(now: Long, latePercent: Int): Counter {
    val step = bellMinutes?.times(60_000)
    var nextBell = nextBellAtMs
    var enabled = bellEnabled
    // Nessun controllo su bellEnabled: spegnere la campanella toglie la notifica, non
    // la cadenza. Chi l'ha spenta deve comunque poter leggere quando scade il giro.
    if (step != null) {
        if (bellRepeat) {
            val slight = rhythmDeviation(now, latePercent) == RhythmDeviation.SLIGHT
            nextBell = if (nextBellAtMs != null && (bellMode == "FIXED" || slight)) {
                keepRhythmNextBell(nextBellAtMs, step, now)
            } else {
                bellFrom(now)
            }
        } else {
            enabled = false
            nextBell = null
        }
    }
    return copy(
        startMs = now,
        bellNotified = false,
        snoozeUntilMs = null,
        nextBellAtMs = nextBell,
        bellEnabled = enabled,
        scheduledResetMs = null, // l'ultimo comando vince: un restart annulla il reset programmato
    )
}

/**
 * Come [restarted], ma per un istante scelto a mano (Riparti avanzato: chip rapide,
 * data/ora, "con orario"): niente euristica di ritardo/anticipo, e la prossima scadenza
 * si ricalcola sempre da quell'istante — **anche sulle FIXED**. "Il ritmo fisso non
 * dipende da quando confermi" vale per il Fatto spontaneo (restarted), non per una
 * correzione esplicita della storia: chi va a scegliere a mano un orario preciso si
 * aspetta che la scadenza segua quello, non un'ancora vecchia scollegata (bug segnalato
 * da Fabrizio il 27/09, due volte: prima su "corregge l'ultimo riavvio", poi qui).
 */
fun Counter.restartedAt(now: Long): Counter {
    var nextBell = nextBellAtMs
    var enabled = bellEnabled
    if (bellMinutes != null) {
        if (bellRepeat) {
            nextBell = bellFrom(now)
        } else {
            enabled = false
            nextBell = null
        }
    }
    return copy(
        startMs = now,
        bellNotified = false,
        snoozeUntilMs = null,
        nextBellAtMs = nextBell,
        bellEnabled = enabled,
        scheduledResetMs = null,
    )
}

/** Scelta dell'utente quando fa Fatto/↺ con una ricorrente scaduta da molto, o
 * anticipata di molto. Per "non l'ho ancora fatto, aspetta la campanella" c'è il
 * chip "alla campanella" di Riparti avanzato (scheduleReset) — qui si arriva solo
 * dopo aver già confermato il riavvio, quindi qui si riavvia sempre davvero. */
enum class LateBellChoice { KEEP_RHYTHM, FROM_NOW, DISABLE }

/** Come [restarted], ma con la decisione esplicita presa in LateBellDialog. */
fun Counter.restartedWithChoice(now: Long, choice: LateBellChoice): Counter {
    val step = (bellMinutes ?: 0) * 60_000
    val base = copy(
        startMs = now,
        bellNotified = false,
        snoozeUntilMs = null,
        scheduledResetMs = null,
    )
    return when (choice) {
        LateBellChoice.KEEP_RHYTHM -> base.copy(nextBellAtMs = keepRhythmNextBell(nextBellAtMs ?: now, step, now))
        LateBellChoice.FROM_NOW -> base.copy(nextBellAtMs = bellFrom(now))
        LateBellChoice.DISABLE -> base.copy(bellEnabled = false)
    }
}

/**
 * Prossima scadenza di un contatore GIORNALIERO: [days] giorni dopo la **data** di
 * [fromMs] (non l'istante esatto), alle ore [minuteOfDay] (minuti da mezzanotte). Data di
 * calendario, non un offset in millisecondi: 3 giorni dal 10 gennaio sono il 13 gennaio,
 * non "72 ore dopo le 23:50 del 10".
 */
fun nextDailyBellAtMs(fromMs: Long, days: Long, minuteOfDay: Int): Long {
    val zone = java.time.ZoneId.systemDefault()
    val fromDate = java.time.Instant.ofEpochMilli(fromMs).atZone(zone).toLocalDate()
    return fromDate.plusDays(days).atStartOfDay(zone).plusMinutes(minuteOfDay.toLong())
        .toInstant().toEpochMilli()
}

/** Mezzogiorno locale della data di [epochMs]: convenzione per gli eventi Giornalieri
 * inseriti a posteriori (o dal "+1" del giorno), che non hanno un orario vero. */
fun noonOf(epochMs: Long): Long {
    val zone = java.time.ZoneId.systemDefault()
    return java.time.Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
        .atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
}

/**
 * Giorni di calendario fra due istanti, non ore trascorse/24: le 23 di ieri e le 7 di
 * oggi sono 1 giorno (una mezzanotte di mezzo), anche se sono passate solo 8 ore.
 */
fun calendarDaysBetween(fromMs: Long, toMs: Long): Long {
    val zone = java.time.ZoneId.systemDefault()
    val fromDate = java.time.Instant.ofEpochMilli(fromMs).atZone(zone).toLocalDate()
    val toDate = java.time.Instant.ofEpochMilli(toMs).atZone(zone).toLocalDate()
    return java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate)
}

/**
 * "+1" di un contatore GIORNALIERO: aggiorna la data dell'ultimo evento e la prossima
 * scadenza. Il round va registrato a parte (vedi [RoundDao.addDailyPoint]) — qui c'è solo
 * lo stato del contatore, come [restarted] per i Precisi.
 */
fun Counter.loggedDaily(now: Long): Counter {
    val nextBell = if (bellEnabled) bellFrom(now) else null
    return copy(startMs = now, bellNotified = false, snoozeUntilMs = null, nextBellAtMs = nextBell)
}
