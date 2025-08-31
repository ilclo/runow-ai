package ai.runow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.text.intl.Locale
import ai.runow.ui.renderer.ActionDispatcher
import ai.runow.ui.renderer.UiScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                // schermata corrente (deve corrispondere a un file JSON in configs/ui/*.json)
                var currentScreen by remember { mutableStateOf("run") }
                // stato condiviso per i bind dei controlli (slider, toggle, ecc.)
                val uiState = remember { mutableStateMapOf<String, Any>() }
                // Layout Lab on/off
                var designerMode by remember { mutableStateOf(true) }

                // Dispatcher azioni invocate dai blocchi (es. nav:settings, start_run, ecc.)
                val dispatcher = remember(currentScreen, designerMode) {
                    object : ActionDispatcher {
                        override fun dispatch(actionId: String) {
                            when {
                                actionId.startsWith("nav:") -> {
                                    currentScreen = actionId.removePrefix("nav:")
                                }
                                actionId == "open_layout_lab" -> designerMode = true
                                actionId == "close_layout_lab" -> designerMode = false
                                // TODO: aggiungi qui altre azioni (start_run, pause_run, ecc.)
                            }
                        }
                    }
                }

                Scaffold(
                    topBar = {
                        val pretty = currentScreen.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.current) else it.toString()
                        }
                        TopAppBar(
                            title = { Text(pretty) },
                            actions = {
                                // Toggle Designer Modee (Layout Lab)
                                IconButton(onClick = { designerMode = !designerMode }) {
                                    Icon(
                                        if (designerMode) Icons.Filled.Close else Icons.Filled.Tune,
                                        contentDescription = null
                                    )
                                }
                                // Shortcut a settings
                                IconButton(onClick = { currentScreen = "settings" }) {
                                    Icon(Icons.Filled.Settings, contentDescription = null)
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    UiScreen(
                        screenName = currentScreen,
                        dispatcher = dispatcher,
                        uiState = uiState,
                        designerMode = designerMode,
                        scaffoldPadding = innerPadding // <<< evita che i blocchi vadano sotto la top bar
                    )
                }
            }
        }
    }
}
