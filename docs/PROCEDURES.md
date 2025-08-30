# runow AI — PROCEDURE OPERATIVE

Questa guida spiega **come provare l’app**, **modificarne l’aspetto** (temi/immagini), **lanciare i test** (anche senza correre),
**aprire bug con i dati giusti** e **pubblicare una nuova build** — senza toccare codice.

> Target: Fondatore/tester non tecnico + chi sviluppa.

---

## Indice
1. [Scaricare e installare l’APK](#1-scaricare-e-installare-lapk)
2. [Aggiornare le impostazioni della sessione e del coach](#2-aggiornare-le-impostazioni-della-sessione-e-del-coach)
3. [Modificare tema (colori/forme) con Theme Lab o via file](#3-modificare-tema-coloriforme-con-theme-lab-o-via-file)
4. [Aggiornare immagini/branding](#4-aggiornare-immaginibranding)
5. [Selezione playlist: Spotify o Libreria libera](#5-selezione-playlist-spotify-o-libreria-libera)
6. [Eseguire i test senza correre (Sim Runner + Golden Runs)](#6-eseguire-i-test-senza-correre-sim-runner--golden-runs)
7. [Eseguire un test reale “smoke”](#7-eseguire-un-test-reale-smoke)
8. [Esportare il Debug Bundle e aprire un bug](#8-esportare-il-debug-bundle-e-aprire-un-bug)
9. [Abilitare/Disabilitare funzioni (Feature Flags)](#9-abilitatedisabilitare-funzioni-feature-flags)
10. [Pubblicare una nuova build (GitHub Actions)](#10-pubblicare-una-nuova-build-github-actions)
11. [Ripristino/clean install](#11-ripristinoclean-install)
12. [Opzionale: build locale con Android Studio](#12-opzionale-build-locale-con-android-studio)
13. [FAQ rapide](#13-faq-rapide)

---

## 1) Scaricare e installare l’APK

**Prerequisiti**
- Un telefono Android con almeno ~200 MB liberi.
- Accesso al repository GitHub.

**Passi**
1. Vai su **GitHub → Actions → Android Debug Build**.
2. Apri l’ultimo *run* (spesso in cima alla lista).
3. In basso, sezione **Artifacts** → scarica **`app-debug`** (ZIP o APK).
4. Se hai scaricato uno ZIP, estrai l’`APK`.
5. Copia l’APK sul telefono (via USB, Drive, o scaricandolo direttamente dal telefono).
6. Sul telefono, abilita l’installazione da sorgenti sconosciute:
   - **Impostazioni → App → Accesso speciale alle app → Installa app sconosciute**  
     Abilita per l’app con cui aprirai l’APK (es. Files/Chrome).
7. Tocca l’APK → **Installa**.

> Aggiornare una build: basta reinstallare sopra (non serve disinstallare) a meno che non indicato diversamente.

---

## 2) Aggiornare le impostazioni della sessione e del coach

Le impostazioni di default sono in `configs/default_coach_config.json`.  
Puoi anche cambiarle **in-app** (Setup/Impostazioni) per la singola sessione.

**Cosa puoi regolare dal setup iniziale / Impostazioni**
- **Metronomo**: Off / Solo quando serve / Sempre
- **Messaggi in corsa**: HR, passo (min/km), cadenza, split km, ultimi 400 m
- **Verbositá**: Bassa / Standard / Alta
- **Tono/Mood**: Neutro / Energico / Empatico
- **Distanza default** per le corse (es. 5 km)
- **Coach**: ON/OFF e preset di coach (Basso / Standard / Alto)

**Modifica via file**
- Cambia il JSON, salva e fai **push** → GitHub Actions produce un nuovo APK.
- Se il JSON è malformato, l’app carica un preset **di fallback** e segnala l’errore nel Theme/Config Lab.

---

## 3) Modificare tema (colori/forme) con Theme Lab o via file

### A) Theme Lab (in‑app, consigliato)
1. Apri **Impostazioni → Debug → Theme Lab**.
2. Cambia **preset** (Sport Light/Dark, High Contrast, Minimal), **colori**, **angoli**, **font**, **spaziature**.
3. Salva come **preset locale** o **Esporta** per aggiornare `configs/theme.json`.

### B) Modifica via file
- Apri `configs/theme.json` e cambia:
  - `selected_preset` (preset attivo di default),
  - `use_dynamic_color` (true = colori dinamici di Android),
  - palette, forme (pill/square), tipografia, ecc.
- Fai **push** → nuova build automatica.

> **Consiglio**: usa la **Component Gallery** (Impostazioni → Debug) per vedere tutti i bottoni/chip/card col nuovo tema.

---

## 4) Aggiornare immagini/branding

Sostituisci i file nella cartella `branding/` **con lo stesso nome**:

| File | Uso | Suggerimenti |
|---|---|---|
| `app_icon_foreground.png` | icona app | 1024×1024 |
| `logo_wordmark.svg` | marchio (toolbar/menu) | Preferisci SVG |
| `splash_logo.svg` | splash screen | Sfondo trasparente |
| `coach_avatar.png` | avatar coach | 512×512 PNG |
| `hero_run.jpg` | hero Home | 1600×900, compressa |
| `empty_state.png` | illustrazioni “vuoto” | 800×600 |

Dopo il **push**, l’APK si rigenera. Nessun codice da toccare.

---

## 5) Selezione playlist: Spotify o Libreria libera

**Spotify (opzionale)**
- In app, scheda Musica: provider **Spotify**.  
- *Suggeriti* (deeplink) oppure **Cerca in Spotify** → “Condividi con runow AI” per salvare la URI.  
- L’app **controlla** Spotify (play/pausa/skip). Niente streaming interno.

**Libreria libera**
- Provider **Libreria**: scegli playlist **CC0/PD** incluse (Easy/Tempo/Intervals/Long).
- Filtri **Artista/Genere/Mood/Tempo** per creare mix locali.  
- Metronomo integrato (Off / Solo quando serve / Sempre).

---

## 6) Eseguire i test senza correre (Sim Runner + Golden Runs)

1. Apri **Impostazioni → Debug → Sim Runner**.
2. Scegli uno **scenario** da `configs/golden_runs.json` (es. `easy_30`, `intervals_6x800`).
3. (Opzionale) Carica un tuo **GPX/TCX**.
4. Usa lo **slider velocità** (x1..x8) per comprimere una corsa lunga in pochi minuti.
5. Avvia: controlla che gli **annunci** corrispondano alla sequenza **attesa** (indicata nello scenario).
6. Prova **Chaos mode** (GPS jitter, batteria bassa) per vedere la resilienza del coach.

**Cosa osservare**
- Latenza voce bassa (messaggi rapidi).
- Nessun “bombardamento”: max messaggi/10’ rispettato.
- Metronomo solo quando serve e per finestre limitate.
- **Explain‑why**: tocca l’ultimo messaggio per vedere soglie/valori/punteggio.

---

## 7) Eseguire un test reale “smoke”

1. Imposta **Distanza default** (es. 5 km) e preset **Coach Standard**.
2. Attiva **Coach ON** e **Metronomo: Quando serve**.
3. Avvia corsa, fai **10–20 minuti** con 2–3 cambi di ritmo.
4. Al termine, apri il **Debrief**: controlla split, eventi, suggerimenti.
5. Se qualcosa non torna, **esporta il Debug Bundle** (sotto).

---

## 8) Esportare il Debug Bundle e aprire un bug

**Export**
- In **Post‑run**, tocca **Esporta Debug Bundle** → salva un `.zip` con:
  - `run_<id>.ndjson` (timeline eventi, frasi, punteggi),
  - `state_snapshots.json` (stato campionato ogni 5s),
  - `config_active.json` (soglie/pesi/flag),
  - `device.json` (modello/OS).

**Aprire un’issue (bug)**
- Titolo chiaro (es. _“Annuncio HR ripetuto 3x in 2’ nella run 2025‑09‑02”_).
- Allegare **ZIP** del debug bundle.
- Scrivere cosa ti aspettavi e cosa è successo.

---

## 9) Abilitare/Disabilitare funzioni (Feature Flags)

File: `feature-flags/flags.json`

```json
{
  "enable_theme_lab": true,
  "enable_component_gallery": true,
  "show_debug_hud": false,
  "enable_spotify": false,
  "enable_breath_feature": false,
  "enable_hills_meteo": false,
  "enable_challenges": false,
  "enable_community": false
}