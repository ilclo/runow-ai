package ai.runow.ui.renderer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun UiScreen(
    screenName: String,
    dispatcher: ActionDispatcher,
    uiState: MutableMap<String, Any>,
    designerMode: Boolean = false
) {
    val ctx = LocalContext.current
    var layout by remember(screenName) { mutableStateOf(UiLoader.loadLayout(ctx, screenName)) }
    var tick by remember { mutableStateOf(0) }
    if (layout == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Layout '$screenName' non trovato", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val menus by remember(layout, tick) { mutableStateOf(collectMenus(layout!!)) }
    var selectedPath by remember(screenName) { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val blocks = layout!!.optJSONArray("blocks") ?: JSONArray()
            for (i in 0 until blocks.length()) {
                val block = blocks.optJSONObject(i) ?: continue
                val path = "/blocks/$i"
                RenderBlock(
                    block = block,
                    dispatcher = dispatcher,
                    uiState = uiState,
                    designerMode = designerMode,
                    path = path,
                    menus = menus,
                    onSelect = { p -> selectedPath = p }
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        if (designerMode) {
            DesignerOverlay(
                screenName = screenName,
                layout = layout!!,
                selectedPath = selectedPath,
                setSelectedPath = { selectedPath = it },
                onLayoutChange = {
                    UiLoader.saveDraft(ctx, screenName, layout!!)
                    layout = JSONObject(layout.toString())
                    tick++
                },
                onSaveDraft = { UiLoader.saveDraft(ctx, screenName, layout!!) },
                onPublish = { UiLoader.saveDraft(ctx, screenName, layout!!); UiLoader.publish(ctx, screenName) },
                onReset = { UiLoader.resetPublished(ctx, screenName); layout = UiLoader.loadLayout(ctx, screenName); selectedPath = null; tick++ }
            )
        }
    }
}

@Composable
private fun RenderBlock(
    block: JSONObject,
    dispatcher: ActionDispatcher,
    uiState: MutableMap<String, Any>,
    designerMode: Boolean,
    path: String,
    menus: Map<String, JSONArray>,
    onSelect: (String) -> Unit
) {
    val borderSelected = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    val wrap: @Composable (content: @Composable ()->Unit) -> Unit = { content ->
        if (designerMode) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                border = borderSelected,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(block.optString("type",""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.clickable { onSelect(path) }) { content() }
                }
            }
        } else {
            Box { content() }
        }
    }

    when (block.optString("type")) {
        "AppBar" -> wrap {
            Text(block.optString("title", ""), style = MaterialTheme.typography.titleLarge)
            val actions = block.optJSONArray("actions") ?: JSONArray()
            if (actions.length() > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 0 until actions.length()) {
                        val a = actions.optJSONObject(i) ?: continue
                        FilledTonalButton(onClick = { dispatcher.dispatch(a.optString("actionId")) }) {
                            Text(a.optString("icon", "action"))
                        }
                    }
                }
            }
        }

        "IconButton" -> wrap {
            val iconName = block.optString("icon", "more_vert")
            val openMenuId = block.optString("openMenuId", "")
            val actionId = block.optString("actionId", "")
            var expanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = {
                    if (openMenuId.isNotBlank() || actionId.startsWith("open_menu:")) {
                        expanded = true
                    } else {
                        dispatcher.dispatch(actionId)
                    }
                }) { NamedIcon(iconName, null) }
                val menuId = if (openMenuId.isNotBlank()) openMenuId else actionId.removePrefix("open_menu:")
                val items = menus[menuId]
                if (items != null) {
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        for (i in 0 until items.length()) {
                            val it = items.optJSONObject(i) ?: continue
                            DropdownMenuItem(
                                text = { Text(it.optString("label","")) },
                                onClick = { expanded = false; dispatcher.dispatch(it.optString("actionId","")) },
                                leadingIcon = {
                                    val ic = it.optString("icon","")
                                    if (ic.isNotBlank()) NamedIcon(ic, null)
                                }
                            )
                        }
                    }
                }
            }
        }

        "Card" -> wrap {
            val variant = block.optString("variant","elevated")
            val clickAction = block.optString("clickActionId","")
            val inner = @Composable {
                val innerBlocks = block.optJSONArray("blocks") ?: JSONArray()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 0 until innerBlocks.length()) {
                        val b = innerBlocks.optJSONObject(i) ?: continue
                        val p2 = "$path/blocks/$i"
                        RenderBlock(b, dispatcher, uiState, designerMode, p2, menus, onSelect)
                    }
                }
            }
            val mod = Modifier.fillMaxWidth().clickable(enabled = clickAction.isNotBlank()) { dispatcher.dispatch(clickAction) }
            when (variant) {
                "outlined" -> OutlinedCard(mod) { Column(Modifier.padding(12.dp)) { inner() } }
                "filled"   -> Card(mod)        { Column(Modifier.padding(12.dp)) { inner() } }
                else       -> ElevatedCard(mod){ Column(Modifier.padding(12.dp)) { inner() } }
            }
        }

        "Tabs" -> wrap {
            val tabs = block.optJSONArray("tabs") ?: JSONArray()
            var idx by remember { mutableStateOf(0) }
            val count = tabs.length().coerceAtLeast(1)
            if (idx >= count) idx = 0
            val labels = (0 until count).map { tabs.optJSONObject(it)?.optString("label", "Tab ${it+1}") ?: "Tab ${it+1}" }
            TabRow(selectedTabIndex = idx) {
                labels.forEachIndexed { i, label ->
                    Tab(selected = i==idx, onClick = { onSelect(path); idx = i }, text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
            Spacer(Modifier.height(8.dp))
            val tab = tabs.optJSONObject(idx)
            val blocks2 = tab?.optJSONArray("blocks") ?: JSONArray()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (k in 0 until blocks2.length()) {
                    val b = blocks2.optJSONObject(k) ?: continue
                    val p2 = "$path/tabs/$idx/blocks/$k"
                    RenderBlock(b, dispatcher, uiState, designerMode, p2, menus, onSelect)
                }
            }
        }

        "SectionHeader" -> wrap {
            Text(block.optString("title", ""), style = MaterialTheme.typography.titleMedium)
            val sub = block.optString("subtitle", "")
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium)
        }

        "MetricsGrid" -> wrap {
            val tiles = block.optJSONArray("tiles") ?: JSONArray()
            val cols = block.optInt("columns", 2).coerceIn(1,3)
            GridSection(tiles, cols, uiState)
        }

        "ButtonRow" -> wrap {
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
                        "outlined" -> OutlinedButton(onClick = { if (confirm) { /* TODO: confirm */ } ; dispatcher.dispatch(action) }) { content() }
                        "tonal"    -> FilledTonalButton(onClick = { dispatcher.dispatch(action) }) { content() }
                        "text"     -> TextButton(onClick = { dispatcher.dispatch(action) }) { content() }
                        else       -> Button(onClick = { dispatcher.dispatch(action) }) { content() }
                    }
                }
            }
        }

        "ChipRow" -> wrap {
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

        "Toggle" -> wrap {
            val label = block.optString("label","")
            val bind = block.optString("bind","")
            val v = (uiState[bind] as? Boolean) ?: false
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = v, onCheckedChange = { uiState[bind] = it })
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
        }

        "Slider" -> wrap {
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

        "List" -> wrap {
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

        "Carousel" -> wrap {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Carousel (placeholder)", style = MaterialTheme.typography.titleSmall)
                    Text("Le immagini saranno gestite in una fase successiva.")
                }
            }
        }

        "Fab" -> wrap {
            val icon = block.optString("icon","play_arrow")
            val label = block.optString("label","")
            val size = block.optString("size","regular")
            val variant = block.optString("variant","regular")
            val action = block.optString("actionId","")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                when (variant) {
                    "extended" -> ExtendedFloatingActionButton(
                        onClick = { dispatcher.dispatch(action) },
                        icon = { NamedIcon(icon, null) },
                        text = { Text(label.ifBlank { "Azione" }) }
                    )
                    else -> when (size) {
                        "small" -> SmallFloatingActionButton(onClick = { dispatcher.dispatch(action) }) { NamedIcon(icon, null) }
                        "large" -> LargeFloatingActionButton(onClick = { dispatcher.dispatch(action) }) { NamedIcon(icon, null) }
                        else -> FloatingActionButton(onClick = { dispatcher.dispatch(action) }) { NamedIcon(icon, null) }
                    }
                }
            }
        }

        "Divider" -> { Divider() }

        "DividerV" -> {
            val thickness = block.optDouble("thickness", 1.0).toFloat().dp
            val height = block.optDouble("height", 24.0).toFloat().dp
            VerticalDivider(modifier = Modifier.height(height), thickness = thickness)
        }

        "Spacer" -> { Spacer(Modifier.height((block.optDouble("height", 8.0)).toFloat().dp)) }

        "Menu" -> { if (designerMode) ElevatedCard { Text("Menu: ${block.optString("id")}  (${block.optJSONArray("items")?.length() ?: 0} voci)", Modifier.padding(8.dp)) } }

        else -> { if (designerMode) Surface(tonalElevation = 1.dp) { Text("Blocco non supportato: ${block.optString("type")}", color = Color.Red) } }
    }
}

@Composable
private fun GridSection(tiles: JSONArray, cols: Int, uiState: MutableMap<String, Any>) {
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

/* ===== Overlay Designer ===== */

@Composable
private fun BoxScope.DesignerOverlay(
    screenName: String,
    layout: JSONObject,
    selectedPath: String?,
    setSelectedPath: (String?) -> Unit,
    onLayoutChange: () -> Unit,
    onSaveDraft: () -> Unit,
    onPublish: () -> Unit,
    onReset: () -> Unit
) {
    var showInspector by remember { mutableStateOf(false) }
    val selectedBlock = selectedPath?.let { jsonAtPath(layout, it) as? JSONObject }
    val canMove = selectedPath?.let { getParentAndIndex(layout, it) != null } == true

    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 8.dp) {
            Row(
                Modifier
                    .padding(10.dp)
                    .horizontalScroll(rememberScrollState()),  /* 👈 ora scrolla orizzontalmente */
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Palette:", style = MaterialTheme.typography.labelLarge)

                FilledTonalButton(onClick = {
                    val path = insertBlockAndReturnPath(layout, selectedPath, newSectionHeader(), "after")
                    setSelectedPath(path); onLayoutChange()
                }) { Icon(Icons.Filled.LibraryAdd, null); Spacer(Modifier.width(6.dp)); Text("SectionHeader") }

                FilledTonalButton(onClick = {
                    val path = insertBlockAndReturnPath(layout, selectedPath, newButtonRow(), "after")
                    setSelectedPath(path); onLayoutChange()
                }) { Icon(Icons.Filled.LibraryAdd, null); Spacer(Modifier.width(6.dp)); Text("ButtonRow") }

                FilledTonalButton(onClick = {
                    val path = insertBlockAndReturnPath(layout, selectedPath, newSpacer(), "after")
                    setSelectedPath(path); onLayoutChange()
                }) { Icon(Icons.Filled.LibraryAdd, null); Spacer(Modifier.width(6.dp)); Text("Spacer") }

                FilledTonalButton(onClick = {
                    val path = insertBlockAndReturnPath(layout, selectedPath, JSONObject().put("type","Divider"), "after")
                    setSelectedPath(path); onLayoutChange()
                }) { Icon(Icons.Filled.LibraryAdd, null); Spacer(Modifier.width(6.dp)); Text("Divider") }

                FilledTonalButton(onClick = {
                    insertIconMenu(layout, selectedPath)
                    // l’inserimento crea IconButton + Menu; selezioniamo l’IconButton
                    setSelectedPath(nextSiblingPath(selectedPath))
                    onLayoutChange()
                }) { Icon(Icons.Filled.MoreVert, null); Spacer(Modifier.width(6.dp)); Text("Icon+Menu") }

                FilledTonalButton(onClick = {
                    val path = insertBlockAndReturnPath(layout, selectedPath, newCard(), "after")
                    setSelectedPath(path); onLayoutChange()
                }) { Icon(Icons.Filled.Widgets, null); Spacer(Modifier.width(6.dp)); Text("Card") }

                FilledTonalButton(onClick = {
                    val path = insertBlockAndReturnPath(layout, selectedPath, newFab(), "after")
                    setSelectedPath(path); onLayoutChange()
                }) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Fab") }

                FilledTonalButton(onClick = {
                    val path = insertBlockAndReturnPath(layout, selectedPath, newDividerV(), "after")
                    setSelectedPath(path); onLayoutChange()
                }) { Icon(Icons.Filled.MoreHoriz, null); Spacer(Modifier.width(6.dp)); Text("DividerV") }
            }
        }

        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 8.dp) {
            Row(
                Modifier.padding(10.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Selezione:", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(enabled = canMove, onClick = {
                        move(layout, selectedPath!!, -1); onLayoutChange()
                    }) { Icon(Icons.Filled.KeyboardArrowUp, null); Spacer(Modifier.width(4.dp)); Text("Su") }
                    OutlinedButton(enabled = canMove, onClick = {
                        move(layout, selectedPath!!, +1); onLayoutChange()
                    }) { Icon(Icons.Filled.KeyboardArrowDown, null); Spacer(Modifier.width(4.dp)); Text("Giù") }
                    OutlinedButton(enabled = selectedPath != null, onClick = {
                        duplicate(layout, selectedPath!!); onLayoutChange()
                    }) { Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("Duplica") }
                    TextButton(
                        enabled = selectedPath != null,
                        onClick = { remove(layout, selectedPath!!); setSelectedPath(null); onLayoutChange() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(4.dp)); Text("Elimina") }

                    Button(
                        enabled = selectedBlock != null && selectedBlock.optString("type") in listOf("ButtonRow","SectionHeader"),
                        onClick = { showInspector = true }
                    ) { Text("Proprietà…") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onSaveDraft) { Text("Salva bozza") }
                    Button(onClick = onPublish) { Text("Pubblica") }
                    TextButton(onClick = onReset) { Text("Reset") }
                }
            }
        }
    }

    if (showInspector && selectedBlock != null) {
        when (selectedBlock.optString("type")) {
            "ButtonRow"     -> ButtonRowInspector(selectedBlock, onClose = { showInspector = false; onLayoutChange() })
            "SectionHeader" -> SectionHeaderInspector(selectedBlock, onClose = { showInspector = false; onLayoutChange() })
        }
    }
}

/* ===== Inspector: ButtonRow (già esistente) ===== */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ButtonRowInspector(block: JSONObject, onClose: () -> Unit) {
    var open by remember { mutableStateOf(true) }
    val buttons = block.optJSONArray("buttons") ?: JSONArray().also { block.put("buttons", it) }
    if (!open) return
    ModalBottomSheet(onDismissRequest = { open = false; onClose() }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ButtonRow – Proprietà", style = MaterialTheme.typography.titleMedium)
            for (i in 0 until buttons.length()) {
                val btn = buttons.getJSONObject(i)
                ElevatedCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Bottone ${i+1}", style = MaterialTheme.typography.labelLarge)
                        val label = remember { mutableStateOf(btn.optString("label","")) }
                        val icon  = remember { mutableStateOf(btn.optString("icon","")) }
                        var style by remember { mutableStateOf(btn.optString("style","primary")) }
                        var size  by remember { mutableStateOf(btn.optString("size","md")) }
                        var tint  by remember { mutableStateOf(btn.optString("tint","default")) }
                        val action= remember { mutableStateOf(btn.optString("actionId","")) }

                        OutlinedTextField(value = label.value, onValueChange = { label.value = it; btn.put("label", it) }, label = { Text("Label") })
                        OutlinedTextField(value = icon.value,  onValueChange = { icon.value  = it; btn.put("icon", it) },  label = { Text("Icon (Material name)") })
                        ExposedDropdown(value = style, label = "Style", options = listOf("primary","tonal","outlined","text")) { style = it; btn.put("style", it) }
                        ExposedDropdown(value = size,  label = "Size",  options = listOf("sm","md","lg")) { size = it; btn.put("size", it) }
                        ExposedDropdown(value = tint,  label = "Tint",  options = listOf("default","success","warning","error")) { tint = it; btn.put("tint", it) }
                        OutlinedTextField(value = action.value, onValueChange = { action.value = it; btn.put("actionId", it) }, label = { Text("actionId") })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { buttons.put(JSONObject("""{"label":"Nuovo","style":"text","icon":"add","actionId":""}""")) }) { Text("+ Aggiungi bottone") }
                OutlinedButton(onClick = { open = false; onClose() }) { Text("Chiudi") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ===== Inspector: SectionHeader ===== */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionHeaderInspector(block: JSONObject, onClose: () -> Unit) {
    var open by remember { mutableStateOf(true) }
    if (!open) return
    val title = remember { mutableStateOf(block.optString("title","")) }
    val subtitle = remember { mutableStateOf(block.optString("subtitle","")) }
    ModalBottomSheet(onDismissRequest = { open = false; onClose() }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("SectionHeader – Proprietà", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = title.value, onValueChange = { title.value = it; block.put("title", it) }, label = { Text("Titolo") })
            OutlinedTextField(value = subtitle.value, onValueChange = { subtitle.value = it; block.put("subtitle", it) }, label = { Text("Sottotitolo (opz.)") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { open = false; onClose() }) { Text("Chiudi") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExposedDropdown(value: String, label: String, options: List<String>, onSelect: (String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(value = value, onValueChange = {}, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, modifier = Modifier.menuAnchor())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { onSelect(it); expanded = false }) }
        }
    }
}

/* ===== Utils: icon mapping, menu collection, JSON manip ===== */

@Composable
private fun NamedIcon(name: String?, contentDescription: String?) {
    when (name) {
        "settings"       -> Icon(Icons.Filled.Settings, contentDescription)
        "more_vert"      -> Icon(Icons.Filled.MoreVert, contentDescription)
        "tune"           -> Icon(Icons.Filled.Tune, contentDescription)
        "play_arrow"     -> Icon(Icons.Filled.PlayArrow, contentDescription)
        "pause"          -> Icon(Icons.Filled.Pause, contentDescription)
        "stop"           -> Icon(Icons.Filled.Stop, contentDescription)
        "add"            -> Icon(Icons.Filled.Add, contentDescription)
        "flag"           -> Icon(Icons.Filled.Flag, contentDescription)
        "queue_music"    -> Icon(Icons.Filled.QueueMusic, contentDescription)
        "widgets"        -> Icon(Icons.Filled.Widgets, contentDescription)
        "palette"        -> Icon(Icons.Filled.Palette, contentDescription)
        "directions_run" -> Icon(Icons.Filled.DirectionsRun, contentDescription)
        "home"           -> Icon(Icons.Filled.Home, contentDescription)
        "menu"           -> Icon(Icons.Filled.Menu, contentDescription)
        "close"          -> Icon(Icons.Filled.Close, contentDescription)
        "more_horiz"     -> Icon(Icons.Filled.MoreHoriz, contentDescription)
        else             -> Text("•")
    }
}

private fun collectMenus(root: JSONObject): Map<String, JSONArray> {
    val map = mutableMapOf<String, JSONArray>()
    fun walk(n: Any?) {
        when (n) {
            is JSONObject -> {
                if (n.optString("type") == "Menu") {
                    val id = n.optString("id","")
                    val items = n.optJSONArray("items") ?: JSONArray()
                    if (id.isNotBlank()) map[id] = items
                }
                n.keys().forEachRemaining { key -> walk(n.opt(key)) }
            }
            is JSONArray -> { for (i in 0 until n.length()) walk(n.opt(i)) }
        }
    }
    walk(root)
    return map
}

// JSON path helpers
private fun jsonAtPath(root: JSONObject, path: String): Any? {
    if (!path.startsWith("/")) return null
    val segs = path.trim('/').split('/')
    var node: Any = root; var i = 0
    while (i < segs.size) {
        when (node) {
            is JSONObject -> { node = (node as JSONObject).opt(segs[i]) ?: return null; i++ }
            is JSONArray  -> { val idx = segs[i].toIntOrNull() ?: return null; node = (node as JSONArray).opt(idx) ?: return null; i++ }
            else -> return null
        }
    }
    return node
}
private fun getParentAndIndex(root: JSONObject, path: String): Pair<JSONArray, Int>? {
    if (!path.startsWith("/")) return null
    val segs = path.trim('/').split('/')
    var node: Any = root; var parentArr: JSONArray? = null; var index = -1; var i = 0
    while (i < segs.size) {
        val s = segs[i]
        when (node) {
            is JSONObject -> { node = (node as JSONObject).opt(s) ?: return null; i++ }
            is JSONArray  -> { val idx = s.toIntOrNull() ?: return null; parentArr = node as JSONArray; index = idx; node = parentArr.opt(idx) ?: return null; i++ }
        }
    }
    return if (parentArr != null && index >= 0) parentArr!! to index else null
}

/* inserisci + restituisci il path del nuovo blocco (auto-select) */
private fun insertBlockAndReturnPath(root: JSONObject, selectedPath: String?, block: JSONObject, position: String): String {
    val (arr, idx, parentPath) = if (selectedPath != null) {
        val p = getParentAndIndex(root, selectedPath) ?: run {
            val a = root.optJSONArray("blocks") ?: JSONArray().also { root.put("blocks", it) }
            Triple(a, a.length()-1, "/blocks")
        }
        val pp = selectedPath.substringBeforeLast("/")
        Triple(p.first, p.second, pp)
    } else {
        val a = root.optJSONArray("blocks") ?: JSONArray().also { root.put("blocks", it) }
        Triple(a, a.length()-1, "/blocks")
    }
    val insertIndex = when (position) { "before" -> idx; else -> idx + 1 }.coerceIn(0, arr.length())
    val tmp = mutableListOf<Any?>()
    for (i in 0 until arr.length()) { if (i == insertIndex) tmp.add(block); tmp.add(arr.get(i)) }
    if (insertIndex == arr.length()) tmp.add(block)
    while (arr.length() > 0) arr.remove(arr.length()-1)
    tmp.forEach { arr.put(it) }
    return "$parentPath/$insertIndex"
}
private fun nextSiblingPath(selectedPath: String?): String? {
    if (selectedPath == null) return "/blocks/0"
    val pp = selectedPath.substringBeforeLast("/")
    val idx = selectedPath.substringAfterLast("/").toIntOrNull() ?: return null
    return "$pp/${idx+1}"
}

private fun move(root: JSONObject, path: String, delta: Int) {
    val p = getParentAndIndex(root, path) ?: return
    val (arr, idx) = p
    val newIdx = (idx + delta).coerceIn(0, arr.length()-1)
    if (newIdx == idx) return
    val item = arr.get(idx)
    val tmp = mutableListOf<Any?>()
    for (i in 0 until arr.length()) if (i != idx) tmp.add(arr.get(i))
    tmp.add(newIdx, item)
    while (arr.length() > 0) arr.remove(arr.length()-1)
    tmp.forEach { arr.put(it) }
}
private fun duplicate(root: JSONObject, path: String) {
    val p = getParentAndIndex(root, path) ?: return
    val (arr, idx) = p
    val clone = JSONObject(arr.getJSONObject(idx).toString())
    val tmp = mutableListOf<Any?>()
    for (i in 0 until arr.length()) { tmp.add(arr.get(i)); if (i == idx) tmp.add(clone) }
    while (arr.length() > 0) arr.remove(arr.length()-1)
    tmp.forEach { arr.put(it) }
}
private fun remove(root: JSONObject, path: String) {
    val p = getParentAndIndex(root, path) ?: return
    val (arr, idx) = p
    val tmp = mutableListOf<Any?>()
    for (i in 0 until arr.length()) if (i != idx) tmp.add(arr.get(i))
    while (arr.length() > 0) arr.remove(arr.length()-1)
    tmp.forEach { arr.put(it) }
}

/* Template blocchi */
private fun newSectionHeader() = JSONObject("""{ "type":"SectionHeader", "title":"Nuova sezione" }""".trimIndent())
private fun newButtonRow() = JSONObject("""{
  "type":"ButtonRow","align":"center",
  "buttons":[
    {"label":"Start","style":"primary","icon":"play_arrow","size":"md","actionId":"start_run"},
    {"label":"Pausa","style":"tonal","icon":"pause","size":"md","actionId":"pause_run"},
    {"label":"Stop","style":"outlined","icon":"stop","size":"md","actionId":"stop_run","confirm":true}
  ]
}""".trimIndent())
private fun newSpacer() = JSONObject("""{ "type":"Spacer", "height": 8 }""".trimIndent())
private fun newDividerV() = JSONObject("""{ "type":"DividerV", "thickness": 1, "height": 24 }""".trimIndent())
private fun newCard() = JSONObject("""{
  "type":"Card","variant":"elevated","clickActionId":"nav:run",
  "blocks":[ { "type":"SectionHeader", "title": "Card esempio" }, { "type":"Divider" } ]
}""".trimIndent())
private fun newFab() = JSONObject("""{ "type":"Fab", "icon":"play_arrow", "label":"Start", "variant":"extended", "actionId":"start_run" }""".trimIndent())
private fun newIconButton(menuId: String = "more_menu") = JSONObject("""{ "type":"IconButton", "icon":"more_vert", "openMenuId":"$menuId" }""".trimIndent())
private fun newMenu(menuId: String = "more_menu") = JSONObject("""{
  "type":"Menu","id":"$menuId",
  "items":[
    {"icon":"tune","label":"Layout Lab","actionId":"open_layout_lab"},
    {"icon":"palette","label":"Theme Lab","actionId":"open_theme_lab"},
    {"icon":"settings","label":"Impostazioni","actionId":"nav:settings"}
  ]
}""".trimIndent())
private fun insertIconMenu(root: JSONObject, selectedPath: String?) {
    val id = "menu_" + System.currentTimeMillis().toString().takeLast(5)
    insertBlockAndReturnPath(root, selectedPath, newIconButton(id), "after")
    insertBlockAndReturnPath(root, selectedPath, newMenu(id), "after")
}
