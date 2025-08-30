# runow AI

**Obiettivo**: setup semplice, coach vocale affidabile e musica (Spotify o libreria libera) con metronomo “quando serve”.

## Come provo l’APK
1. Vai su **GitHub > Actions > Android Debug Build**.
2. Apri l’ultimo run e scarica l’**artifact** `app-debug`.
3. Installa l’APK (sideload) sul telefono.

## Dove trovo le impostazioni
- Tab centrale: Obiettivo (km/tempo), Coach ON/OFF + preset, Musica + preset, Start.
- Impostazioni: verbosità, avvisi (HR/Pace/Cadenza/Split/400m), metronomo, distanza default.

## Debug rapido
- Attiva **HUD sviluppatore** in Impostazioni > Debug.
- Tap sull’ultimo messaggio per **Explain‑why**.
- Esporta **Debug bundle** (ZIP) con un tap e allegalo a un’issue.

## Struttura
- `coach-engine`: regole + MessageSelector (deterministico, testabile).
- `sim-runner`: GPX/TCX + profili HR/cadenza per test senza correre.
- `music`: Spotify App Remote **o** OpenAudio (CC0/PD).
- `configs/`: JSON di default (coach, musica, frasi, golden runs).