@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import ai.runow.ui.renderer.UiScreen
import java.util.Locale
// MainActivity.kt (in alto con gli altri import)
import ai.runow.ui.renderer.DesignerRoot


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                var currentScreen by remember { mutableStateOf("run") }
                val uiState = remember { mutableStateMapOf<String, Any>() }
                var designerMode by remember { mutableStateOf(true) }

                // Dispatcher come semplice lambda (niente classi/interfacce → zero conflitti)
                val dispatch: (String) -> Unit = { actionId ->
                    when {
                        actionId.startsWith("nav:") -> currentScreen = actionId.removePrefix("nav:")
                        actionId == "open_layout_lab" -> designerMode = true
                        actionId == "close_layout_lab" -> designerMode = false
                        // TODO: altre azioni (start_run, pause_run, ecc.)
                    }
                }

                Scaffold(
                    topBar = {
                        val pretty = currentScreen.replaceFirstChar { ch ->
                            if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
                        }
                        TopAppBar(
                            title = { Text(pretty) },
                            actions = {
                                IconButton(onClick = { designerMode = !designerMode }) {
                                    Icon(
                                        imageVector = if (designerMode) Icons.Filled.Close else Icons.Filled.Tune,
                                        contentDescription = null
                                    )
                                }
                                IconButton(onClick = { currentScreen = "settings" }) {
                                    Icon(Icons.Filled.Settings, contentDescription = null)
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    UiScreen(
                        screenName = currentScreen,
                        dispatch = dispatch,            // <<< passiamo la lambda
                        uiState = uiState,
                        designerMode = designerMode,
                        scaffoldPadding = innerPadding  // evita contenuti sotto la top bar
                    )
                }
            }
        }
    }
}
