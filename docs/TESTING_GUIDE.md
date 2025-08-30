# runow AI — GUIDA RAPIDA TEST (≤ 15 minuti)

Questa guida ti porta a: installare l’APK, fare 1 test simulato, 1 corsa “smoke” reale breve, e inviare un pacchetto debug utile.

---

## 0) Prerequisiti
- Telefono Android con ~200 MB liberi.
- Accesso al repository (per scaricare l’APK).

---

## 1) Installa l’APK (2 min)
1. Vai su **GitHub → Actions → Android Debug Build**.
2. Apri l’ultimo run → **Artifacts** → scarica `app-debug` (APK o ZIP con dentro l’APK).
3. Copia l’APK sul telefono → aprilo → **Installa**.
   - Se richiesto: “Consenti da origini sconosciute” per l’app con cui apri l’APK.

---

## 2) Setup iniziale (1 min)
- Al primo avvio completa il **wizard**:
  - Distanza default **5 km**, Coach **Standard**, Metronomo **Quando serve**.
  - Lascia attivi gli avvisi su **HR**, **Pace**, **Split km**, **Ultimi 400 m** (se disponibili).

---

## 3) Test **simulato** (Sim Runner) — 6 min
1. Apri **Impostazioni → Debug → Sim Runner**.
2. Seleziona **Scenario**: `easy_30` e imposta **Velocità x4**.
3. Avvia. **Cosa aspettarsi**:
   - Annunci **KM split** regolari.
   - Al massimo **1 rinforzo** motivazionale ogni 3–4 min.
   - Nessun “bombardamento” (cap annunci/10’ rispettato).
4. Tocca l’ultimo messaggio per **Explain‑why** (vedi soglie/valori/punteggio).

> Se vuoi stressare: prova `fast_start` (partenza troppo veloce) o `gps_drop` (perdita segnale): deve uscire **1** avviso coerente e poi stabilizzarsi.

---

## 4) Test **reale** “smoke” (5–10 min, facoltativo)
- Scheda centrale:
  - **Obiettivo** 5 km (ok anche se non li fai tutti).
  - **Coach ON**, preset **Standard**, Metronomo **Quando serve**.
  - Musica a piacere (Spotify o Libreria).
- Parti, inserisci **2–3 cambi di ritmo** (più veloce / più lento) per innescare feedback su **Pace** e **Metronomo**.

---

## 5) Invio **Debug Bundle** (1 min)
- Al termine: **Esporta Debug Bundle** (bottone nel post‑run) → ottieni un `.zip`.
- Invia il file (o allegalo a un’issue) con due righe:
  - **Mi aspettavo:** …
  - **Ho visto:** …

---

## 6) Cosa valutare (check rapido: 1–5)
- **Chiarezza** messaggi (brevi, utili, non ripetitivi): ☐1 ☐2 ☐3 ☐4 ☐5  
- **Metronomo** (solo quando serve, non invasivo): ☐1 ☐2 ☐3 ☐4 ☐5  
- **Tema/lettura** (contrasto, dimensioni, forme): ☐1 ☐2 ☐3 ☐4 ☐5  
- **Stabilità** (nessun crash, fluidità): ☐1 ☐2 ☐3 ☐4 ☐5

---

## 7) Risoluzione rapida problemi
- **Tema non cambia** → Impostazioni → Debug → **Theme Lab** (disattiva “dynamic color”) e salva.
- **Spotify non risponde** → apri l’app Spotify una volta, poi riprova.
- **Troppi annunci** → Impostazioni → Coach → **Verbositá: Bassa**.
- **Metronomo fastidioso** → Metti **Solo quando serve** o **Off**.

Grazie!
