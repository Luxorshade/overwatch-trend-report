# OW Trend Report

App Android che mostra il report settimanale di audio, hashtag e novità di Overwatch
per i video su interazioni, voiceline e doppiatori italiani.

## Come funziona

- Ogni mercoledì alle 14:47 un agent scrive un nuovo report in `data/reports/` e aggiorna `data/index.json`.
- L'app scarica i file da questo repository pubblico (`raw.githubusercontent.com`), li salva per l'uso offline
  e ogni 4 ore controlla se c'è un report nuovo per mandarti una notifica.

## Struttura

```
app/                      codice Android (Kotlin + Jetpack Compose)
data/config.json          fonti dati attive (web / Dashboard Instagram)
data/index.json           elenco dei report
data/reports/*.json       un file per settimana
AGENT_PROMPT.md           istruzioni dell'agent settimanale
```

## Compilare con Android Studio

1. Clona il repository e apri la cartella principale con Android Studio (File → Open).
2. Alla prima apertura Android Studio scarica Gradle e le librerie: accetta se propone di creare il Gradle wrapper.
3. Per provarla: collega il telefono e premi ▶ Run.
4. Per l'APK firmato: Build → Generate Signed App Bundle / APK → APK → usa la tua chiave.

Requisiti: Android 8.0 o superiore.

## Attivare la Dashboard Instagram

In `data/config.json` metti `"enabled": true` sotto `instagramDashboard`. Il mercoledì all'ora del report
il computer deve essere acceso, con Chrome aperto e l'estensione Claude in Chrome attiva.
