# Action IDs — UI → Dominio

## Run
- start_run      → RunController.start()
- pause_run      → RunController.pause()
- resume_run     → RunController.resume()
- stop_run       → RunController.stop(confirm=true)
- lap_mark       → RunController.markLap()

## Navigazione / Debug
- open_settings      → Navigator.to("settings")
- open_theme_lab     → Navigator.to("theme_lab")
- open_gallery       → Navigator.to("component_gallery")
- open_layout_lab    → Navigator.to("layout_lab")
- export_debug_bundle→ Debug.exportBundle()
- show_hud_toggle    → Debug.toggleHud()

## Musica
- select_playlist       → MusicController.openPlaylistPicker()
- toggle_music_provider → MusicController.toggleProvider()
- nudge_energy_up       → MusicController.nudge(+1)
- nudge_energy_down     → MusicController.nudge(-1)

## Pattern dinamici
- `nav:<page>`          → apre la schermata JSON `<page>` (es. `nav:run`, `nav:settings`, `nav:music`, `nav:home`)
- `open_menu:<id>`      → apre il `Menu` con id `<id>` se definito nel layout
