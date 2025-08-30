# Layout Lab — Spec & Flussi (v1)

## Obiettivo
Permettere di comporre schermate dal telefono (anche in prod), senza codice:
- aggiungere/spegnere blocchi (AppBar, Tabs, ButtonRow, ChipRow, Slider, MetricsGrid, Carousel, List, Divider/Spacer);
- cambiare ordine, icone, testi, stile (primary/tonal/outlined/text), tint (success/warning/error);
- regolare dimensioni con griglia 12-colonne (span 1..12, size sm/md/lg) e densità (compact/default/airy).

## Priorità di caricamento
1. **PublishedConfig locale** (salvata dal Layout Lab sul device)
2. **Config remota firmata** (opzionale)
3. **Asset di default** (inclusi nell’app)

## Modalità Designer (ingresso)
- Tap ×7 su “Versione” (Impostazioni) + PIN semplice → banner “Designer Mode”.

## Stati
- **Preview**: provi senza salvare (reset all’uscita).
- **Bozza**: salvi una proposta (non attiva).
- **Pubblica**: rende attiva la bozza sul device (subito usata dall’app).
- **Esporta**: genera ZIP con `configs/ui/*.json` + `configs/theme.json` (opz.) + `layout_manifest.json` (version, checksum, note).

## Validazione & sicurezza
- Validatore schema (obbligatorio) al salvataggio; se fallisce non puoi “Pubblicare”.
- Backup dell’ultima config attiva; **Revert** in 1 tap.
- Kill-switch (feature-flag) per blocchi o sezioni.
