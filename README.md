# Last Time

App Android per tenere traccia di quanto tempo è passato dall'ultima volta che è successo qualcosa (eventi personali, scadenze, abitudini).

La specifica del progetto è nella cartella `mokeup/`:

- [`last-time-mockup-v27.html`](mokeup/last-time-mockup-v27.html) — mockup interattivo completo: specifica di riferimento per UI e comportamenti (tema scuro antracite/ambra, card contatori, vista tabellone split-flap stile Solari, sezione segreta con PIN, storico round, archivio).
- [`last-time-conversazione.md`](mokeup/last-time-conversazione.md) — log della progettazione, con riepilogo funzionale v27 e architettura target in fondo.

## Architettura target

Kotlin + Jetpack Compose (minSdk 26, package `it.fabriziomari.lasttime`), Room per la persistenza (`counters`, `rounds`), AlarmManager exact per le notifiche, PIN in EncryptedSharedPreferences, notifiche anonime per i timer lucchettati. Build APK via GitHub Actions. Tutto locale, nessun account o servizio esterno.

## Build

L'APK di debug viene compilato da GitHub Actions a ogni push su `main` (workflow [`build.yml`](.github/workflows/build.yml)) ed è scaricabile dagli artifacts del run (`lasttime-debug-apk`).

## Stato

- ✅ Milestone 1 — home con card contatori (crea/modifica/elimina/riparti con log round su Room), cambio vista toccando le cifre, campanella con evidenza sforato (v0.1)
- ✅ Milestone 2 — storico per contatore: round in corso e conclusi, riepilogo (eventi, più lungo, media), "Quante volte" per finestra temporale, ritmo medio, durate a due componenti (v0.2)
- ✅ Milestone 3 — notifiche reali: AlarmManager esatto (una sveglia sulla prossima campanella in scadenza, ripianificata a ogni evento), receiver per riavvio/cambio ora/fuso, canale notifiche normale + canale anonimo `VISIBILITY_SECRET` per i futuri timer lucchettati, richiesta permessi notifiche e sveglie esatte (v0.3)
- ✅ Milestone 3.1 — diagnostica notifiche (🩺 in header): stato di permesso notifiche, sveglie esatte ed esenzione ottimizzazione batteria con bottoni per sistemare ciascun punto (v0.3.1)
- ✅ Milestone 4 — azioni sulla notifica: Scarta (chiude, il contatore continua), Fatto (round loggato e restart), Rimanda (maschera con quantità e unità minuti/ore/giorni/mesi, default 10 minuti); snooze persistito su DB con migrazione Room 1→2 (v0.4)
- ✅ Milestone 4.1 — ritmo campanella per contatore: "Riparti da adesso" (X dopo il Fatto) o "Mantieni il ritmo" (X dopo la scadenza precedente, es. pillola ogni 8h confermata con 1h di ritardo → suona dopo 7h); Scarta ora spegne la campanella; interruttore on/off della campanella con chip 🔕 sulla card (v0.4.1, migrazione Room 2→3)
- Prossime: vista tabellone Solari, swipe elimina/archivia, riparti avanzato con reset programmato e giri persi, sezione segreta con PIN, archivio
