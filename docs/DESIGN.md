# DESIGN

- **Principi**: core minimale, moduli indipendenti, determinismo in‑run, on‑device first.
- **Moduli**: app-ui, core, coach-engine, music, storage, sim-runner, feature-flags.
- **Porte**: `MusicProvider`, `TtsProvider`, `SensorsPort`.
- **Decisioni chiave**:
  - In‑run: Regole + punteggio. No LLM.
  - Offline: opzionale LLM per debrief/piani (pre‑cache).
  - Musica: controllo Spotify **o** libreria libera.