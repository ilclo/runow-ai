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
import ai.runow.ui.renderer.UiScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    enum class Route { Home, HomeUI, ThemeLab, Gallery, RunUI, SettingsUI, MusicUI }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var theme by remember { mutableStateOf(ThemeState()) }
            val snackHost = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()

            // semplice nav stack
            val navStack = remember { mutableStateListOf(Route.Home) }
            fun current() = navStack.last()
            fun push(r: Route) { navStack.add(r) }
            fun pop() { if (navStack.size > 1) navStack.removeAt(navStack.lastIndex) }

            // overlay designer ON/OFF per le pagine JSON
            var designerOn by remember { mutableStateOf(false) }
            fun closeDesigner() { designerOn = false }

            // back hardware: se overlay aperto, chiudilo; altrimenti pop
            BackHandler {
                if (designerOn) closeDesigner() else pop()
            }

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
                            "settings"   -> { closeDesigner(); push(Route.SettingsUI) }
                            "theme_lab"  -> { closeDesigner(); push(Route.ThemeLab) }
                            "gallery"    -> { closeDesigner(); push(Route.Gallery) }
                            "run"        -> { closeDesigner(); push(Route.RunUI) }
                            "music"      -> { closeDesigner(); push(Route.MusicUI) }
                            "home_json"  -> { closeDesigner(); push(Route.HomeUI) }
                            "home"       -> { closeDesigner(); push(Route.HomeUI) }
                        }
                    },
                    showSnack = { msg -> scope.launch { snackHost.showSnackbar(message = msg) } }
                )
            }

            RunowTheme(theme) {
                val canGoBack = navStack.size > 1
                Scaffold(
                    topBar = {
                        TopAppBar(
                            navigationIcon = {
                                if (canGoBack) {
                                    IconButton(onClick = { if (designerOn) closeDesigner() else pop() }) {
                                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                }
                            },
                            title = {
                                Text(
                                    when(current()){
                                        Route.Home      -> "runow AI"
                                        Route.HomeUI    -> "Home (JSON)"
                                        Route.ThemeLab  -> "Theme Lab"
                                        Route.Gallery   -> "Component Gallery"
                                        Route.RunUI     -> "Corsa (JSON)"
                                        Route.SettingsUI-> "Impostazioni (JSON)"
                                        Route.MusicUI   -> "Musica (JSON)"
                                    }
                                )
                            },
                            actions = {
                                when (current()) {
                                    Route.RunUI, Route.SettingsUI, Route.MusicUI, Route.HomeUI -> {
                                        IconButton(onClick = { designerOn = !designerOn }) {
                                            Icon(Icons.Filled.Tune, contentDescription = "Designer")
                                        }
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
                            onOpenThemeLab = { closeDesigner(); push(Route.ThemeLab) },
                            onOpenGallery  = { closeDesigner(); push(Route.Gallery) },
                            onOpenRun      = { closeDesigner(); push(Route.RunUI) },
                            onOpenSettings = { closeDesigner(); push(Route.SettingsUI) },
                            onOpenMusic    = { closeDesigner(); push(Route.MusicUI) },
                            onOpenHomeJson = { closeDesigner(); push(Route.HomeUI) }
                        )
                        Route.ThemeLab -> ThemeLabScreen(
                            state = theme,
                            onStateChange = { theme = it },
                            onOpenGallery = { push(Route.Gallery) }
                        )
                        Route.Gallery   -> ComponentGalleryScreen()
                        Route.HomeUI    -> UiScreen("home", dispatcher, uiState, designerMode = designerOn)
                        Route.RunUI     -> UiScreen("run", dispatcher, uiState, designerMode = designerOn)
                        Route.SettingsUI-> UiScreen("settings", dispatcher, uiState, designerMode = designerOn)
                        Route.MusicUI   -> UiScreen("music", dispatcher, uiState, designerMode = designerOn)
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
                supportingContent = { Text("Anteprima componenti") },
                modifier = Modifier.clickable { onOpenGallery() }
            )
            Divider()
            ListItem(
                headlineContent = { Text("Home (JSON)") },
                supportingContent = { Text("Homepage renderizzata da JSON") },
                modifier = Modifier.clickable { onOpenHomeJson() }
            )
            Divider()
            ListItem(
                headlineContent = { Text("Corsa (JSON)") },
                supportingContent = { Text("Schermata renderizzata da JSON") },
                modifier = Modifier.clickable { onOpenRun() }
            )
            Divider()
            ListItem(
                headlineContent = { Text("Impostazioni (JSON)") },
                supportingContent = { Text("Schermata renderizzata da JSON") },
                modifier = Modifier.clickable { onOpenSettings() }
            )
            Divider()
            ListItem(
                headlineContent = { Text("Musica (JSON)") },
                supportingContent = { Text("Schermata renderizzata da JSON") },
                modifier = Modifier.clickable { onOpenMusic() }
            )
        }
    }
}
