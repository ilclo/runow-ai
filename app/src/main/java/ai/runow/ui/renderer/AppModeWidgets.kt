package ai.runow.ui.renderer

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

@Composable
internal fun BoxScope.ResizeHud(onExit: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 8.dp,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp, start = 12.dp, end = 12.dp)) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Tune, contentDescription = null)
            Text("Modalità resize attiva: long‑press per bordi drag, doppio tap per riordino; tap fuori per uscire.",
                style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onExit) { Text("Esci") }
        }
    }
}

private enum class Mode { Preview, Resize, Designer }

@Composable
internal fun BoxScope.ModeSpeedDial(
    isDesigner: Boolean,
    isResize: Boolean,
    onPick: (Mode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val current = when { isDesigner -> Mode.Designer; isResize -> Mode.Resize; else -> Mode.Preview }
    val anchorMod = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)
    Box(anchorMod) {
        FloatingActionButton(onClick = {
            val next = when (current) { Mode.Preview -> Mode.Designer; Mode.Designer -> Mode.Resize; Mode.Resize -> Mode.Preview }
            onPick(next)
        }) {
            val icon = when (current) { Mode.Designer -> Icons.Filled.Build; Mode.Resize -> Icons.Filled.Tune; else -> Icons.Filled.Visibility }
            Icon(icon, contentDescription = "Cambia modalità")
        }
        if (expanded) {
            // opzionalmente potresti aggiungere uno scrim cliccabile
        }
    }
}
