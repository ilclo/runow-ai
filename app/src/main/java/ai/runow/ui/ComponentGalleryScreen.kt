@file:OptIn(ExperimentalMaterial3Api::class)
package ai.runow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentGalleryScreen() {
    var toggle by remember { mutableStateOf(true) }
    var checked by remember { mutableStateOf(false) }
    var radio by remember { mutableStateOf("a") }
    var slider by remember { mutableStateOf(5f) }
    var text by remember { mutableStateOf("") }
    var chipA by remember { mutableStateOf(false) }
    var chipB by remember { mutableStateOf(true) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var dropdownValue by remember { mutableStateOf("One") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Buttons", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {}) { Text("Filled") }
            FilledTonalButton(onClick = {}) { Text("Tonal") }
            OutlinedButton(onClick = {}) { Text("Outlined") }
            TextButton(onClick = {}) { Text("Text") }
            IconButton(onClick = {}) { Icon(Icons.Filled.Add, contentDescription = null) }
            ExtendedFloatingActionButton(onClick = {}, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("FAB") })
        }

        Divider()

        Text("Selection controls", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = toggle, onCheckedChange = { toggle = it })
                Spacer(Modifier.width(6.dp)); Text(if (toggle) "ON" else "OFF")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = { checked = it })
                Spacer(Modifier.width(6.dp)); Text(if (checked) "Selezionato" else "Deselezionato")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = radio=="a", onClick = { radio = "a" }); Text("A")
                Spacer(Modifier.width(8.dp))
                RadioButton(selected = radio=="b", onClick = { radio = "b" }); Text("B")
            }
        }

        Divider()

        Text("Slider", style = MaterialTheme.typography.titleMedium)
        Text("Valore: ${String.format("%.1f", slider)}")
        Slider(value = slider, onValueChange = { slider = it }, valueRange = 0f..10f)

        Divider()

        Text("Text fields & dropdown", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Scrivi qualcosa") })
        ExposedDropdownMenuBox(expanded = dropdownExpanded, onExpandedChange = { dropdownExpanded = !dropdownExpanded }) {
            OutlinedTextField(
                value = dropdownValue,
                onValueChange = {},
                readOnly = true,
                label = { Text("Dropdown") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                modifier = Modifier.menuAnchor()
            )
            ExposedDropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                listOf("One","Two","Three").forEach {
                    DropdownMenuItem(text = { Text(it) }, onClick = { dropdownValue = it; dropdownExpanded = false })
                }
            }
        }

        Divider()

        Text("Chips", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = chipA,
                onClick = { chipA = !chipA },
                label = { Text("Filter A") },
                leadingIcon = if (chipA) { { Icon(Icons.Filled.Check, null) } } else null
            )
            FilterChip(
                selected = chipB,
                onClick = { chipB = !chipB },
                label = { Text("Filter B") },
                leadingIcon = if (chipB) { { Icon(Icons.Filled.Check, null) } } else null
            )
            AssistChip(onClick = {}, label = { Text("Assist") }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
        }
    }
}
