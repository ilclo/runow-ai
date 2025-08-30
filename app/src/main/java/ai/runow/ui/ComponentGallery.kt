package ai.runow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ComponentGalleryScreen() {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Component Gallery", style = MaterialTheme.typography.headlineSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {}) { Text("Filled") }
            OutlinedButton(onClick = {}) { Text("Outlined") }
            FilledTonalButton(onClick = {}) { Text("Tonal") }
        }

        var checked by remember { mutableStateOf(true) }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Switch(checked = checked, onCheckedChange = { checked = it })
            Checkbox(checked = checked, onCheckedChange = { checked = it })
            AssistChip(onClick = { checked = !checked }, label = { Text("Assist") })
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Card titolo", style = MaterialTheme.typography.titleMedium)
                Text("Testo descrittivo su due righe per vedere contrasto, spaziatura e tipografia.")
                LinearProgressIndicator(progress = if (checked) 0.6f else 0.2f, Modifier.fillMaxWidth())
            }
        }

        var slider by remember { mutableStateOf(0.5f) }
        Text("Slider")
        Slider(value = slider, onValueChange = { slider = it })

        var text by remember { mutableStateOf("") }
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("TextField") })
    }
}
