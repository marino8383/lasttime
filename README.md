# Last Time

App Android per tenere traccia di quanto tempo è passato dall'ultima volta che è successo qualcosa (eventi personali, scadenze, abitudini).

La specifica del progetto è nella cartella `mokeup/`:

- [`last-time-mockup-v27.html`](mokeup/last-time-mockup-v27.html) — mockup interattivo completo: specifica di riferimento per UI e comportamenti (tema scuro antracite/ambra, card contatori, vista tabellone split-flap stile Solari, sezione segreta con PIN, storico round, archivio).
- [`last-time-conversazione.md`](mokeup/last-time-conversazione.md) — log della progettazione, con riepilogo funzionale v27 e architettura target in fondo.

## Architettura target

Kotlin + Jetpack Compose (minSdk 26, package `it.fabriziomari.lasttime`), Room per la persistenza (`counters`, `rounds`), AlarmManager exact per le notifiche, PIN in EncryptedSharedPreferences, notifiche anonime per i timer lucchettati. Build APK via GitHub Actions. Tutto locale, nessun account o servizio esterno.

## Stato

Fase iniziale: presente la specifica (mockup + log), implementazione da avviare.
