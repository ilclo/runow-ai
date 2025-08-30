package ai.runow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import ai.runow.ui.*

enum class Screen { Home, ThemeLab, Gallery }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var state by remember { mutableStateOf(ThemeState()) }
            var screen by remember { mutableStateOf(Screen.Home) }

            RunowTheme(state) {
                Scaffold(
                    topBar = {
                        TopAppBar(title = { Text("runow AI") })
                    }
                ) { padding ->
                    when (screen) {
                        Screen.Home -> HomeScreen(
                            onOpenThemeLab = { screen = Screen.ThemeLab },
                            onOpenGallery  = { screen = Screen.Gallery }
                        )
                        Screen.ThemeLab -> ThemeLabScreen(
                            state = state,
                            onStateChange = { state = it },
                            onOpenGallery = { screen = Screen.Gallery }
                        )
                        Screen.Gallery -> ComponentGalleryScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onOpenThemeLab: () -> Unit, onOpenGallery: () -> Unit) {
    Surface {
        Column {
            ListItem(
                headlineContent = { Text("Theme Lab") },
                supportingContent = { Text("Modifica preset, colori dinamici e forma componenti") },
                modifier = androidx.compose.ui.Modifier.clickable { onOpenThemeLab() }
            )
            Divider()
            ListItem(
                headlineContent = { Text("Component Gallery") },
                supportingContent = { Text("Anteprima bottoni, chip, card, slider, text field") },
                modifier = androidx.compose.ui.Modifier.clickable { onOpenGallery() }
            )
        }
    }
}
