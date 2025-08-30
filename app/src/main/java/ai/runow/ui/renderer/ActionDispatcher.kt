package ai.runow.ui.renderer

class ActionDispatcher(
    private val navigateTo: (String) -> Unit,
    private val showSnack: (String) -> Unit
) {
    fun dispatch(actionId: String) {
        when (actionId) {
            // Core corsa (stub per ora)
            "start_run" -> showSnack("Start (stub)")
            "pause_run" -> showSnack("Pausa (stub)")
            "resume_run" -> showSnack("Riprendi (stub)")
            "stop_run" -> showSnack("Stop (stub)")
            "lap_mark" -> showSnack("Lap (stub)")

            // Navigazione
            "open_settings" -> navigateTo("settings")
            "open_theme_lab" -> navigateTo("theme_lab")
            "open_gallery" -> navigateTo("gallery")

            // Musica (stub)
            "select_playlist" -> showSnack("Selettore playlist (stub)")
            "toggle_music_provider" -> showSnack("Switch provider musica (stub)")
            "nudge_energy_up" -> showSnack("Energia + (stub)")
            "nudge_energy_down" -> showSnack("Energia − (stub)")

            // Debug / varie
            "export_debug_bundle" -> showSnack("Export Debug Bundle (stub)")
            "show_hud_toggle" -> showSnack("HUD toggle (stub)")

            else -> showSnack("Action: $actionId")
        }
    }
}
