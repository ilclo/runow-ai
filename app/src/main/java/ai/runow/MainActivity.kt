package ai.runow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
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
    enum class Route { Home, HomeUI, ThemeLab, Gallery, RunUI, SettingsUI, MusicUI, LayoutLab }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var theme by remember { mutableStateOf(ThemeState()) }
            val snackHost = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()

            // semplice nav stack
            val navStack = remember { mutableStateListOf(Route.Home) }
            var labTarget by remember { mutableStateOf<String?>(null) }
            fun current() = navStack.last()
            fun push(r: Route) { navStack.add(r) }
            fun pop() { if (navStack.size > 1) navStack.removeAt(navStack.lastIndex) }

            // back hardware
            val canGoBack = navStack.size > 1
            BackHandler(enabled = canGoBack) { pop() }

            // stato UI per bind (MVP)
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
                            "settings"   -> push(Route.SettingsUI)
                            "theme_lab"  -> push(Route.ThemeLab)
                            "gallery"    -> push(Route.Gallery)
                            "layout_lab" -> { labTarget = "run"; push(Route.LayoutLab) }
                            "run"        -> push(Route.RunUI)
                            "music"      -> push(Route.MusicUI)
                            "home_json"  -> push(Route.HomeUI)
                            "home"       -> push(Route.HomeUI)
                        }
                    },
                    showSnack = { msg -> scope.launch { snackHost.showSnackbar(message = msg) } }
                )
            }

            RunowTheme(theme) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            navigationIcon = if (canGoBack) {
                                { IconButton(onClick = { pop() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
                            } else null,
                            title = {
                                Text(
                                    when(current()){
                                        Route.Home -> "runow AI"
                                        Route.HomeUI -> "Home (JSON)"
                                        Route.ThemeLab -> "Theme Lab"
                                        Route.Gallery -> "Component Gallery"
                                        Route.RunUI -> "Corsa (JSON)"
                                        Route.SettingsUI -> "Impostazioni (JSON)"
                                        Route.MusicUI -> "Musica (JSON)"
                                        Route.LayoutLab -> "Layout Lab"
                                    }
                                )
                            },
                            actions = {
                                when (current()) {
                                    Route.RunUI, Route.SettingsUI, Route.MusicUI, Route.HomeUI -> {
                                        IconButton(onClick = {
                                            labTarget = when (current()) {
                                                Route.RunUI -> "run"
                                                Route.SettingsUI -> "settings"
                                                Route.MusicUI -> "music"
                                                Route.HomeUI -> "home"
                                                else -> "run"
                                            }
                                            push(Route.LayoutLab)
                                        }) { Icon(Icons.Filled.Tune, contentDescription = "Edit layout") }
                                    }
                                    else -> {}
                                }
                            }
                        )
                    },
                    snackbarHost = { SnackbarHost(snackHost) }
                ) { _ ->
                    when (current()) {
                        Route.Home -> HomeScreen(
                            onOpenThemeLab = { push(Route.ThemeLab) },
                            onOpenGallery  = { push(Route.Gallery) },
                            onOpenRun      = { push(Route.RunUI) },
                            onOpenSettings = { push(Route.SettingsUI) },
                            onOpenMusic    = { push(Route.MusicUI) },
                            onOpenLayoutLab= { labTarget = "run"; push(Route.LayoutLab) },
                            onOpenHomeJson = { push(Route.HomeUI) }
                        )
                        Route.ThemeLab -> ThemeLabScreen(
                            state = theme,
                            onStateChange = { theme = it },
                            onOpenGallery = { push(Route.Gallery) }
                        )
                        Route.Gallery   -> ComponentGalleryScreen()
                        Route.HomeUI    -> UiScreen("home", dispatcher, uiState)
                        Route.RunUI     -> UiScreen("run", dispatcher, uiState)
                        Route.SettingsUI-> UiScreen("settings", dispatcher, uiState)
                        Route.MusicUI   -> UiScreen("music", dispatcher, uiState)
                        Route.LayoutLab -> LayoutLabScreen(initialScreen = labTarget ?: "run")
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
    onOpenLayoutLab: () -> Unit,
    onOpenHomeJson: () -> Unit
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
                headlineContent = { Text("Home (JSON)") },
                supportingContent = { Text("Homepage renderizzata da layout JSON") },
                modifier = Modifier.clickable { onOpenHomeJson() }
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
