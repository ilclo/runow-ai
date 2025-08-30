package ai.runow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import ai.runow.ui.RunowTheme
import ai.runow.ui.ThemeState
import ai.runow.ui.ThemeLabScreen
import ai.runow.ui.ComponentGalleryScreen
import ai.runow.ui.renderer.ActionDispatcher
import ai.runow.ui.renderer.LayoutLabScreen
import ai.runow.ui.renderer.UiScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    enum class Route { Home, ThemeLab, Gallery, RunUI, SettingsUI, MusicUI, LayoutLab }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var theme by remember { mutableStateOf(ThemeState()) }
            var route by remember { mutableStateOf(Route.Home) }
            val snackHost = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()

            // stato UI semplice per i bind (MVP)
            val uiState = remember {
                mutableStateMapOf<String, Any>(
                    "coach.enabled" to false,
                    "coach.preset" to "standard",
                    "coach.verbosity" to "standard",
                    "coach.alerts.hr" to true,
                    "coach.alerts.pace" to true,
                    "coach.alerts.split" to true,
                    "coach.alerts.last400m" to false,
                    "music.provider" to "spotify",
                    "music.metronome" to "out_of_range",
                    "session.goal.mode" to "distance",
                    "session.goal.distanceKm" to 5,
                    "profile.defaultDistanceKm" to 5
                )
            }

            val dispatcher = remember {
                ActionDispatcher(
                    navigateTo = { dest ->
                        when (dest) {
                            "settings"   -> route = Route.SettingsUI
                            "theme_lab"  -> route = Route.ThemeLab
                            "gallery"    -> route = Route.Gallery
                            else         -> {}
                        }
                    },
                    showSnack = { msg ->
                        scope.launch { snackHost.showSnackbar(message = msg) }
                    }
                )
            }

            RunowTheme(theme) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    when(route){
                                        Route.Home -> "runow AI"
                                        Route.ThemeLab -> "Theme Lab"
                                        Route.Gallery -> "Component Gallery"
                                        Route.RunUI -> "Corsa (JSON)"
                                        Route.SettingsUI -> "Impostazioni (JSON)"
                                        Route.MusicUI -> "Musica (JSON)"
                                        Route.LayoutLab -> "Layout Lab"
                                    }
                                )
                            }
                        )
                    },
                    snackbarHost = { SnackbarHost(snackHost) }
                ) { _ ->
                    when (route) {
                        Route.Home -> HomeScreen(
                            onOpenThemeLab = { route = Route.ThemeLab },
                            onOpenGallery  = { route = Route.Gallery },
                            onOpenRun      = { route = Route.RunUI },
                            onOpenSettings = { route = Route.SettingsUI },
                            onOpenMusic    = { route = Route.MusicUI },
                            onOpenLayoutLab= { route = Route.LayoutLab }
                        )
                        Route.ThemeLab -> ThemeLabScreen(
                            state = theme,
                            onStateChange = { theme = it },
                            onOpenGallery = { route = Route.Gallery }
                        )
                        Route.Gallery -> ComponentGalleryScreen()
                        Route.RunUI -> UiScreen("run", dispatcher, uiState)
                        Route.SettingsUI -> UiScreen("settings", dispatcher, uiState)
                        Route.MusicUI -> UiScreen("music", dispatcher, uiState)
                        Route.LayoutLab -> LayoutLabScreen(onPublished = { /* no-op */ })
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    onOpenThemeLab: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenRun: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMusic: () -> Unit,
    onOpenLayoutLab: () -> Unit
) {
    Surface {
        Column {
            ListItem(
                headlineContent = { Text("Theme Lab") },
                supportingContent = { Text("Colori e forme (Design Tokens)") },
                modifier = Modifier.clickable { onOpenThemeLab() }
            )
            Divider()
            ListItem(
                headlineContent = { Text("Component Gallery") },
                supportingContent = { Text("Anteprima bottoni, chip, card, slider, text field") },
                modifier = Modifier.clickable { onOpenGallery() }
            )
            Divider()
            ListItem(
                headlineContent = { Text("Corsa (JSON)") },
                supportingContent = { Text("Schermata renderizzata dal layout JSON") },
                modifier = Modifier.clickable { onOpenRun() }
            )
            Divider()
            ListItem(
                headlineContent = { Text("Impostazioni (JSON)") },
                supportingContent = { Text("Schermata renderizzata dal layout JSON") },
                modifier = Modifier.clickable { onOpenSettings() }
            )
            Divider()
            ListItem(
                headlineContent = { Text("Musica (JSON)") },
                supportingContent = { Text("Schermata renderizzata dal layout JSON") },
                modifier = Modifier.clickable { onOpenMusic() }
            )
            Divider()
            ListItem(
                headlineContent = { Text("Layout Lab") },
                supportingContent = { Text("Riordina blocchi, aggiungi Lap, Bozza/Pubblica/Reset") },
                modifier = Modifier.clickable { onOpenLayoutLab() }
            )
        }
    }
}
