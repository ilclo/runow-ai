# Layout Lab — Spec & Flussi (v1)

## Obiettivo
Consentire al designer/fondatore di **comporre schermate** (Run, Settings, Music) dal telefono:
- aggiungendo/spegnendo **blocchi** (AppBar, Tabs, ButtonRow, ChipRow, Slider, MetricsGrid, Carousel, List, Divider/Spacer);
- cambiando **ordine**, **icone**, **testi**, **stile** (primary/tonal/outlined/text), **tint** (success/warning/error);
- regolando **dimensioni** con una **griglia 12‑colonne** (span 1..12, size sm/md/lg, densità).

La configurazione salvata è valida **anche in produzione**:
1) **Config pubblicata locale** (priorità più alta, attiva subito sul device)
2) **Config remota firmata** (opzionale)
3) **Asset di default** (fallback)

## Modalità Designer (come si entra)
- Gesture nascosta (es. tap 7× su “Versione” in Impostazioni) + pin semplice.
- Banner “Designer Mode” visibile solo in questa modalità.

## Flussi
- **Preview**: provi modifiche senza scrivere file; reset all’uscita.
- **Bozza**: salvi una proposta in locale (non attiva).
- **Pubblica**: promuove la bozza a **config attiva** sul device (subito usata dall’app).
- **Esporta**: crea uno ZIP con:
  - `configs/ui/*.json` (run.json, settings.json, music.json…)
  - `configs/theme.json` (opzionale)
  - `layout_manifest.json` con: `schema_version`, `config_version`, `created_at`, `created_by`, `checksum`, `notes`.

## Editor (cosa puoi fare)
- **Lista blocchi** della schermata: drag&drop per l’ordine, toggle visibilità, icona e titolo.
- **Griglia 12‑colonne**: per blocchi “a griglia” (MetricsGrid / ButtonRow) i figli (tile/bottoni) hanno `span` (1..12).
- **Densità**: slider globale “Compatto ↔ Arioso” (scala spaziatura/token).
- **Tabs**: aggiungi/rinomina/riordina tab; ciascuna tab ha i suoi `blocks`.
- **Icone**: catalogo Material Icons (ricerca), più icone branding registrate.
- **Azioni**: per Button/List scegli `actionId` dal **registro** (vedi `docs/ACTIONS.md`).
- **Bind**: per Toggle/Chip/Slider scegli il percorso di stato (es. `coach.enabled`, `session.goal.distanceKm`).

## Validazione & Sicurezza
- **Validator** al salvataggio: JSON conforme allo **schema** (vedi `docs/UI_SCHEMA.md`), vincoli span (1..12), actionId esistente, bind noto.
- Se **fallisce**: evidenzia errori; non permette “Pubblica”.
- **Backup** dell’ultima config attiva; **Revert** in un tap.
- **Kill‑switch** per blocchi interi (feature‑flags).

## Precedenze (caricamento)
1. `PublishedConfig` locale (se presente e valida)
2. Config remota firmata (opz., se versione > locale/asset)
3. Asset: `configs/ui/*.json` inclusi nell’app
