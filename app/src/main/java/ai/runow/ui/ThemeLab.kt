package ai.runow.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ThemeLabScreen(
    state: ThemeState,
    onStateChange: (ThemeState) -> Unit,
    onOpenGallery: () -> Unit
) {
    val isAtLeast31 = Build.VERSION.SDK_INT >= 31
    val scroll = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp)
    ) {
        Text("Theme Lab", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        // Preset
        Text("Preset", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        FlowRowMain {
            PresetChip("Sport Light", state.preset == Preset.SportLight) {
                onStateChange(state.copy(preset = Preset.SportLight))
            }
            PresetChip("Sport Dark", state.preset == Preset.SportDark) {
                onStateChange(state.copy(preset = Preset.SportDark))
            }
            PresetChip("High Contrast", state.preset == Preset.HighContrast) {
                onStateChange(state.copy(preset = Preset.HighContrast))
            }
            PresetChip("Minimal", state.preset == Preset.Minimal) {
                onStateChange(state.copy(preset = Preset.Minimal))
            }
        }

        Spacer(Modifier.height(16.dp))
        // Schema luce/buio
        Text("Tema chiaro/scuro", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        FlowRowMain {
            PresetChip("Segui sistema", state.darkMode == null) {
                onStateChange(state.copy(darkMode = null))
            }
            PresetChip("Chiaro", state.darkMode == false) {
                onStateChange(state.copy(darkMode = false))
            }
            PresetChip("Scuro", state.darkMode == true) {
                onStateChange(state.copy(darkMode = true))
            }
        }

        Spacer(Modifier.height(16.dp))
        // Dynamic color
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = state.dynamicColor && isAtLeast31,
                onCheckedChange = { onStateChange(state.copy(dynamicColor = it)) },
                enabled = isAtLeast31
            )
            Spacer(Modifier.width(8.dp))
            Text("Colori dinamici (Material You) ${if (!isAtLeast31) "(non supportato < Android 12)" else ""}")
        }

        Spacer(Modifier.height(16.dp))
        // Forma
        Text("Forma componenti", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        FlowRowMain {
            PresetChip("Squadrato", state.shape == ShapeStyle.Square) {
                onStateChange(state.copy(shape = ShapeStyle.Square))
            }
            PresetChip("Arrotondato", state.shape == ShapeStyle.Rounded) {
                onStateChange(state.copy(shape = ShapeStyle.Rounded))
            }
            PresetChip("Pill", state.shape == ShapeStyle.Pill) {
                onStateChange(state.copy(shape = ShapeStyle.Pill))
            }
        }

        Spacer(Modifier.height(24.dp))
        FilledTonalButton(onClick = onOpenGallery) {
            Icon(Icons.Filled.Palette, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Apri Component Gallery")
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun FlowRowMain(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

@Composable
private fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) { { Icon(Icons.Filled.Check, contentDescription = null) } } else null
    )
}
