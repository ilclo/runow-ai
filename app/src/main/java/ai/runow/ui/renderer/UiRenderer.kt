package ai.runow.ui.renderer

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun UiScreen(
    screenName: String,
    dispatcher: ActionDispatcher,
    uiState: MutableMap<String, Any>
) {
    val ctx = LocalContext.current
    val layout = remember(screenName) { UiLoader.loadLayout(ctx, screenName) }
    if (layout == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Layout '$screenName' non trovato", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    val blocks = layout.optJSONArray("blocks") ?: JSONArray()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        for (i in 0 until blocks.length()) {
            val block = blocks.optJSONObject(i) ?: continue
            RenderBlock(block, dispatcher, uiState)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun RenderBlock(
    block: JSONObject,
    dispatcher: ActionDispatcher,
    uiState: MutableMap<String, Any>
) {
    when (block.optString("type")) {
        "AppBar" -> {
            Text(
                text = block.optString("title", ""),
                style = MaterialTheme.typography.titleLarge
            )
            val actions = block.optJSONArray("actions") ?: JSONArray()
            if (actions.length() > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 0 until actions.length()) {
                        val a = actions.optJSONObject(i) ?: continue
                        FilledTonalButton(onClick = {
                            dispatcher.dispatch(a.optString("actionId"))
                        }) { Text(a.optString("icon", "action")) }
                    }
                }
            }
        }
        "Tabs" -> {
            val tabs = block.optJSONArray("tabs") ?: JSONArray()
            var idx by remember { mutableStateOf(0) }
            val count = tabs.length().coerceAtLeast(1)
            if (idx >= count) idx = 0
            val labels = (0 until count).map { tabs.optJSONObject(it)?.optString("label", "Tab ${it+1}") ?: "Tab ${it+1}" }
            TabRow(selectedTabIndex = idx) {
                labels.forEachIndexed { i, label ->
                    Tab(selected = i==idx, onClick = { idx = i }, text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
            Spacer(Modifier.height(8.dp))
            val tab = tabs.optJSONObject(idx)
            val blocks2 = tab?.optJSONArray("blocks") ?: JSONArray()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (i in 0 until blocks2.length()) {
                    val b = blocks2.optJSONObject(i) ?: continue
                    RenderBlock(b, dispatcher, uiState)
                }
            }
        }
        "SectionHeader" -> {
            Text(block.optString("title", ""), style = MaterialTheme.typography.titleMedium)
            val sub = block.optString("subtitle", "")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium)
        }
        "MetricsGrid" -> {
            val tiles = block.optJSONArray("tiles") ?: JSONArray()
            val cols = block.optInt("columns", 2).coerceIn(1,3)
            GridSection(tiles, cols, uiState)
        }
        "ButtonRow" -> {
            val align = when (block.optString("align")) {
                "start" -> Arrangement.Start
                "end" -> Arrangement.End
                "space_between" -> Arrangement.SpaceBetween
                else -> Arrangement.Center
            }
            val buttons = block.optJSONArray("buttons") ?: JSONArray()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = align) {
                for (i in 0 until buttons.length()) {
                    val btn = buttons.optJSONObject(i) ?: continue
                    val label = btn.optString("label", "Button")
                    val style = btn.optString("style","primary")
                    val action = btn.optString("actionId","")
                    val confirm = btn.optBoolean("confirm", false)
                    val content: @Composable ()->Unit = { Text(label) }
                    Spacer(Modifier.width(6.dp))
                    when(style){
                        "outlined" -> OutlinedButton(onClick = { if (confirm) { /* TODO: conferma */ } ; dispatcher.dispatch(action) }) { content() }
                        "tonal"    -> FilledTonalButton(onClick = { dispatcher.dispatch(action) }) { content() }
                        "text"     -> TextButton(onClick = { dispatcher.dispatch(action) }) { content() }
                        else       -> Button(onClick = { dispatcher.dispatch(action) }) { content() }
                    }
                }
            }
        }
        "ChipRow" -> {
            val chips = block.optJSONArray("chips") ?: JSONArray()
            val isSingle = (0 until chips.length()).any { chips.optJSONObject(it)?.has("value") == true }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until chips.length()) {
                    val c = chips.optJSONObject(i) ?: continue
                    val label = c.optString("label","")
                    val bind = c.optString("bind","")
                    if (isSingle) {
                        val v = c.opt("value")?.toString() ?: ""
                        val current = uiState[bind]?.toString()
                        FilterChip(
                            selected = current == v,
                            onClick = { uiState[bind] = v },
                            label = { Text(label) },
                            leadingIcon = if (current == v) { { Icon(Icons.Filled.Check, null) } } else null
                        )
                    } else {
                        val current = (uiState[bind] as? Boolean) ?: false
                        FilterChip(
                            selected = current,
                            onClick = { uiState[bind] = !current },
                            label = { Text(label) },
                            leadingIcon = if (current) { { Icon(Icons.Filled.Check, null) } } else null
                        )
                    }
                }
            }
        }
        "Toggle" -> {
            val label = block.optString("label","")
            val bind = block.optString("bind","")
            val v = (uiState[bind] as? Boolean) ?: false
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = v, onCheckedChange = { uiState[bind] = it })
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
        }
        "Slider" -> {
            val label = block.optString("label","")
            val bind = block.optString("bind","")
            val min = block.optDouble("min", 0.0).toFloat()
            val max = block.optDouble("max", 10.0).toFloat()
            val step = block.optDouble("step", 1.0).toFloat()
            var value by remember { mutableStateOf(((uiState[bind] as? Number)?.toFloat()) ?: min) }
            Text("$label: ${"%.1f".format(value)} ${block.optString("unit","")}")
            Slider(
                value = value,
                onValueChange = {
                    value = it
                    uiState[bind] = if (step >= 1f) kotlin.math.round(it/step)*step else it
                },
                valueRange = min..max
            )
        }
        "List" -> {
            val items = block.optJSONArray("items") ?: JSONArray()
            Column {
                for (i in 0 until items.length()) {
                    val it = items.optJSONObject(i) ?: continue
                    ListItem(
                        headlineContent = { Text(it.optString("title","")) },
                        supportingContent = { Text(it.optString("subtitle","")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { dispatcher.dispatch(it.optString("actionId","")) }
                    )
                    Divider()
                }
            }
        }
        "Carousel" -> {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Carousel (placeholder)", style = MaterialTheme.typography.titleSmall)
                    Text("Le immagini saranno gestite in una fase successiva.")
                }
            }
        }
        "Spacer" -> {
            Spacer(Modifier.height((block.optDouble("height", 8.0)).toFloat().dp))
        }
        "Divider" -> {
            Divider()
        }
        else -> {
            Text("Blocco non supportato: ${block.optString("type")}", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun GridSection(tiles: JSONArray, cols: Int, uiState: MutableMap<String, Any>) {
    // MVP: griglia semplice per righe; span ignorato per ora
    val rows = mutableListOf<List<JSONObject>>()
    var current = mutableListOf<JSONObject>()
    for (i in 0 until tiles.length()) {
        tiles.optJSONObject(i)?.let { current.add(it) }
        if (current.size == cols) { rows.add(current.toList()); current = mutableListOf() }
    }
    if (current.isNotEmpty()) rows.add(current)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    ElevatedCard(Modifier.weight(1f)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(t.optString("label",""), style = MaterialTheme.typography.labelMedium)
                            Text("—", style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
                repeat((cols - row.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
