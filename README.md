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
- ✅ Milestone 5 — campanella singola o ricorrente: la singola suona una volta e si spegne (i rimandi valgono comunque), la ricorrente si riarma da sola ogni X fino a disattivazione (con ritmo "dal Fatto" o fisso); aggancio "da quando" automatico quando ovvio (soglia da inizio timer già superata → da adesso) o chiesto esplicitamente nel caso ambiguo, con anteprima dell'orario di squillo; ora di squillo sempre visibile sulla card e countdown sui rinvii (v0.4.2–v0.5, migrazione Room 3→4)
- ✅ Milestone 6 — campanella rifinita sul campo: singola/ricorrente senza riarmo automatico (riarmo solo con Fatto/reset/Rimanda), tolleranza "mantieni il ritmo" in % configurabile (⚙️ Opzioni), chip a 3 viste col countdown automatico sui rinvii, canali con suono e vibrazione, footer con versione e data build (v0.5.x–v0.6.x)
- ✅ Milestone 7 — riparti avanzato (v25) e giri persi (v27): doppio tap su ↺ apre la maschera con chip rapide (adesso/10m/30m/1h/1g fa) e data/ora precisa, vincolo all'inizio del round attuale, reset programmato nel futuro (badge ⏲ sulla card, esecuzione automatica via AlarmManager, notifica di avvenuto reset, l'ultimo comando vince), eventi "solo conteggio" con data approssimativa (v0.7)
- ✅ Milestone 8 — vista tabellone Solari (🚉 in header): un board split-flap per contatore con animazione a placche (scaleY sul cardine, v9), stile stazione (placche quasi nere, cifre bianche, targhetta SFORATO ambra), barra opzioni Spezzato/Anni/Mesi/Giorni/Minuti/Secondi con toggle "Mostra anni"/"Mostra secondi" persistiti, gruppo anni auto-rimosso se a zero, placche auto-ridimensionate, niente controlli e doppio tap sul board = riparti (v0.8)
- Prossime: swipe elimina/archivia, sezione segreta con PIN, archivio
