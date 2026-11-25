# Istruzioni per l'agent settimanale

Questo è il testo che l'attività programmata (ogni mercoledì alle 14:47, ora italiana) segue.
Se cambi qualcosa qui, aggiorna anche il testo dell'attività programmata.

## Compito

1. Leggi `data/config.json` del repository `Luxorshade/overwatch-trend-report`.
   - Se `sources.web.enabled` è true: cerca sul web.
   - Se `sources.instagramDashboard.enabled` è true: apri con Claude in Chrome la Dashboard professionale
     di Instagram → Reels Trends e leggi audio e hashtag in tendenza. Se Chrome non è raggiungibile,
     continua solo con il web e scrivilo nelle `notes`.
2. Raccogli, per la settimana corrente:
   - **Novità Overwatch** (3-5): nuovi eroi, stagioni, eventi, patch, nuove voiceline/interazioni,
     notizie sul doppiaggio italiano. Per ognuna un'idea di video sulla nicchia
     "interazioni, voiceline e doppiatori italiani di Overwatch".
   - **Audio di tendenza** (5-10), preferendo fonti italiane; se non ce ne sono, usa liste internazionali
     recenti e dillo nelle `notes`. Per ognuno: come si usa il trend e un'idea Overwatch.
   - **Hashtag** (massimo 5): mix tra tag del gioco, community italiana, formato e tag della settimana.
3. Scrivi il report in `data/reports/AAAA-MM-GG.json` (data del mercoledì) seguendo lo stesso
   formato di `data/reports/2026-11-25.json`, in italiano, parafrasando le fonti (niente testo copiato).
4. Aggiungi la voce in cima a `data/index.json` e aggiorna `updatedAt`.
5. Controlla che entrambi i file JSON siano validi, poi fai commit e push su `main`
   con messaggio `Report settimanale AAAA-MM-GG`.
6. Invia una notifica breve con il riassunto del report.

## Regole

- Non inventare dati: se qualcosa non si trova, scrivilo nelle `notes`.
- Campo `stage` dell'audio: `in crescita`, `al picco`, `in calo`, oppure il mese/`stagionale` se la fonte non lo dice.
- Non toccare il codice dell'app (`app/`) né `config.json`.
