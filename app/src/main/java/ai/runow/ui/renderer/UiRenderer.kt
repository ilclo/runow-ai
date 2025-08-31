@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ai.runow.ui.renderer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun UiScreen(
    screenName: String,
    dispatch: (String) -> Unit,                     // <<< lambda al posto di ActionDispatcher
    uiState: MutableMap<String, Any>,
    designerMode: Boolean = false,
    scaffoldPadding: PaddingValues = PaddingValues(0.dp)
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
    var overlayHeightPx by remember { mutableStateOf(0) }
    val overlayHeightDp = with(LocalDensity.current) { (overlayHeightPx / density).dp } // px -> dp robusto

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(scaffoldPadding) // evita contenuti sotto la top bar
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp, end = 16.dp, top = 16.dp,
                    bottom = if (designerMode) overlayHeightDp + 32.dp else 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val blocks = layout!!.optJSONArray("blocks") ?: JSONArray()
            for (i in 0 until blocks.length()) {
                val block = blocks.optJSONObject(i) ?: continue
                val path = "/blocks/$i"
                RenderBlock(
                    block = block,
                    dispatch = dispatch,
                    uiState = uiState,
                    designerMode = designerMode,
                    path = path,
                    menus = menus,
                    onSelect = { p -> selectedPath = p }
                )
            }
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
                onReset = {
                    UiLoader.resetPublished(ctx, screenName)
                    layout = UiLoader.loadLayout(ctx, screenName)
                    selectedPath = null
                    tick++
                },
                onOverlayHeight = { overlayHeightPx = it }
            )
        }
    }
}

@Composable
private fun RenderBlock(
    block: JSONObject,
    dispatch: (String) -> Unit,
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
                    Text(
                        block.optString("type",""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
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
                        FilledTonalButton(onClick = { dispatch(a.optString("actionId")) }) {
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
                    } else if (actionId.isNotBlank()) {
                        dispatch(actionId)
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
                                onClick = { expanded = false; dispatch(it.optString("actionId","")) },
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
                        RenderBlock(b, dispatch, uiState, designerMode, p2, menus, onSelect)
                    }
                }
            }
            val mod = Modifier
                .fillMaxWidth()
                .clickable(enabled = clickAction.isNotBlank()) { dispatch(clickAction) }
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
            val labels = (0 until count).map {
                tabs.optJSONObject(it)?.optString("label", "Tab ${it+1}") ?: "Tab ${it+1}"
            }
            TabRow(selectedTabIndex = idx) {
                labels.forEachIndexed { i, label ->
                    Tab(
                        selected = i == idx,
                        onClick = { onSelect(path); idx = i },
                        text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val tab = tabs.optJSONObject(idx)
            val blocks2 = tab?.optJSONArray("blocks") ?: JSONArray()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (k in 0 until blocks2.length()) {
                    val b = blocks2.optJSONObject(k) ?: continue
                    val p2 = "$path/tabs/$idx/blocks/$k"
                    RenderBlock(b, dispatch, uiState, designerMode, p2, menus, onSelect)
                }
            }
        }

        "SectionHeader" -> wrap {
            val style = mapTextStyle(block.optString("style", "titleMedium"))
            val align = mapTextAlign(block.optString("align", "start"))
            val spaceTop = block.optDouble("spaceTop", 0.0).toFloat().dp
            val spaceBottom = block.optDouble("spaceBottom", 0.0).toFloat().dp
            val padStart = block.optDouble("padStart", 0.0).toFloat().dp
            val padEnd = block.optDouble("padEnd", 0.0).toFloat().dp
            val clickAction = block.optString("clickActionId","")

            if (spaceTop > 0.dp) Spacer(Modifier.height(spaceTop))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = padStart, end = padEnd)
                    .then(if (clickAction.isNotBlank()) Modifier.clickable { dispatch(clickAction) } else Modifier)
            ) {
                Text(
                    text = block.optString("title", ""),
                    style = style,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = align
                )
                val sub = block.optString("subtitle", "")
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.bodyMedium, textAlign = align, modifier = Modifier.fillMaxWidth())
                }
            }
            if (spaceBottom > 0.dp) Spacer(Modifier.height(spaceBottom))
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
                "space_around" -> Arrangement.SpaceAround
                "space_evenly" -> Arrangement.SpaceEvenly
                else -> Arrangement.Center
            }
            val buttons = block.optJSONArray("buttons") ?: JSONArray()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = align) {
                for (i in 0 until buttons.length()) {
                    val btn = buttons.optJSONObject(i) ?: continue
                    val label = btn.optString("label", "Button")
                    val styleKey = btn.optString("style","primary")
                    val action = btn.optString("actionId","")
                    val confirm = btn.optBoolean("confirm", false)
                    val sizeKey = btn.optString("size","md")
                    val tintKey = btn.optString("tint","default")
                    val shapeKey = btn.optString("shape","rounded")
                    val corner = btn.optDouble("corner", 20.0).toFloat().dp
                    val pressEffect = btn.optString("pressEffect","none") == "scale"
                    val icon = btn.optString("icon","")

                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(targetValue = if (pressEffect && pressed) 0.96f else 1f, label = "btnScale")

                    val shape = when (shapeKey) {
                        "pill" -> RoundedCornerShape(50)
                        "cut" -> CutCornerShape(corner)
                        else -> RoundedCornerShape(corner)
                    }
                    val (container, content, border) = mapButtonColors(styleKey, tintKey)

                    val baseMod = Modifier
                        .graphicsLayer(scaleX = scale, scaleY = scale)
                        .then(sizeModifier(sizeKey))

                    Spacer(Modifier.width(6.dp))
                    val contentSlot: @Composable ()->Unit = {
                        if (icon.isNotBlank()) {
                            IconText(label = label, icon = icon)
                        } else {
                            Text(label)
                        }
                    }
                    when(styleKey){
                        "outlined" -> OutlinedButton(
                            onClick = { if (!confirm) dispatch(action) else dispatch(action) },
                            shape = shape,
                            border = BorderStroke(border, content),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = content),
                            interactionSource = interaction,
                            modifier = baseMod
                        ) { contentSlot() }
                        "tonal"    -> FilledTonalButton(
                            onClick = { dispatch(action) },
                            shape = shape,
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = container, contentColor = content),
                            interactionSource = interaction,
                            modifier = baseMod
                        ) { contentSlot() }
                        "text"     -> TextButton(
                            onClick = { dispatch(action) },
                            shape = shape,
                            colors = ButtonDefaults.textButtonColors(contentColor = content),
                            interactionSource = interaction,
                            modifier = baseMod
                        ) { contentSlot() }
                        else       -> Button(
                            onClick = { dispatch(action) },
                            shape = shape,
                            colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
                            interactionSource = interaction,
                            modifier = baseMod
                        ) { contentSlot() }
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
                            .clickable { dispatch(it.optString("actionId","")) }
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
                        onClick = { dispatch(action) },
                        icon = { NamedIcon(icon, null) },
                        text = { Text(label.ifBlank { "Azione" }) }
                    )
                    else -> when (size) {
                        "small" -> SmallFloatingActionButton(onClick = { dispatch(action) }) { NamedIcon(icon, null) }
                        "large" -> LargeFloatingActionButton(onClick = { dispatch(action) }) { NamedIcon(icon, null) }
                        else -> FloatingActionButton(onClick = { dispatch(action) }) { NamedIcon(icon, null) }
                    }
                }
            }
        }

        "Divider" -> {
            val thick = block.optDouble("thickness", 1.0).toFloat().dp
            val padStart = block.optDouble("padStart", 0.0).toFloat().dp
            val padEnd = block.optDouble("padEnd", 0.0).toFloat().dp
            Divider(modifier = Modifier.padding(start = padStart, end = padEnd), thickness = thick)
        }

        "DividerV" -> {
            val thickness = block.optDouble("thickness", 1.0).toFloat().dp
            val height = block.optDouble("height", 24.0).toFloat().dp
            VerticalDivider(modifier = Modifier.height(height), thickness = thickness)
        }

        "Spacer" -> { Spacer(Modifier.height((block.optDouble("height", 8.0)).toFloat().dp)) }

        "Menu" -> {
            if (designerMode) {
                ElevatedCard { Text("Menu: ${block.optString("id")}  (${block.optJSONArray("items")?.length() ?: 0} voci)", Modifier.padding(8.dp)) }
            }
        }

        else -> {
            if (designerMode) {
                Surface(tonalElevation = 1.dp) { Text("Blocco non supportato: ${block.optString("type")}", color = Color.Red) }
            }
        }
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
    onReset: () -> Unit,
    onOverlayHeight: (Int) -> Unit
) {
    var showInspector by remember { mutableStateOf(false) }
    val selectedBlock = selectedPath?.let { jsonAtPath(layout, it) as? JSONObject }
    val canMove = selectedPath?.let { getParentAndIndex(layout, it) != null } == true

    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(12.dp)
            .onGloballyPositioned { onOverlayHeight(it.size.height) },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Palette
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 8.dp) {
            Row(
                Modifier
                    .padding(10.dp)
                    .horizontalScroll(rememberScrollState()),
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
                    val iconPath = insertIconMenuReturnIconPath(layout, selectedPath)
                    setSelectedPath(iconPath)
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

        // Selezione + Azioni salvataggio
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 8.dp) {
            Column(
                Modifier
                    .padding(10.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Selezione:", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(enabled = canMove, onClick = {
                        val newPath = moveAndReturnNewPath(layout, selectedPath!!, -1)
                        setSelectedPath(newPath); onLayoutChange()
                    }) { Icon(Icons.Filled.KeyboardArrowUp, null); Spacer(Modifier.width(4.dp)); Text("Su") }
                    OutlinedButton(enabled = canMove, onClick = {
                        val newPath = moveAndReturnNewPath(layout, selectedPath!!, +1)
                        setSelectedPath(newPath); onLayoutChange()
                    }) { Icon(Icons.Filled.KeyboardArrowDown, null); Spacer(Modifier.width(4.dp)); Text("Giu") }
                    OutlinedButton(enabled = selectedPath != null, onClick = {
                        duplicate(layout, selectedPath!!); onLayoutChange()
                    }) { Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("Duplica") }
                    TextButton(
                        enabled = selectedPath != null,
                        onClick = { remove(layout, selectedPath!!); setSelectedPath(null); onLayoutChange() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(4.dp)); Text("Elimina") }
                    Button(
                        enabled = selectedBlock != null && selectedBlock.optString("type") in listOf("ButtonRow","SectionHeader","Spacer","Divider","DividerV","Card","Fab"),
                        onClick = { showInspector = true }
                    ) { Text("Proprieta...") }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onSaveDraft) { Text("Salva bozza") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onPublish) { Text("Pubblica") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onReset) { Text("Reset") }
                }
            }
        }
    }

    if (showInspector && selectedBlock != null) {
        when (selectedBlock.optString("type")) {
            "ButtonRow"     -> ButtonRowInspector(selectedBlock, onClose = { showInspector = false; onLayoutChange() })
            "SectionHeader" -> SectionHeaderInspector(selectedBlock, onClose = { showInspector = false; onLayoutChange() })
            "Spacer"        -> SpacerInspector(selectedBlock, onClose = { showInspector = false; onLayoutChange() })
            "Divider"       -> DividerInspector(selectedBlock, onClose = { showInspector = false; onLayoutChange() })
            "DividerV"      -> DividerVInspector(selectedBlock, onClose = { showInspector = false; onLayoutChange() })
            "Card"          -> CardInspector(selectedBlock, onClose = { showInspector = false; onLayoutChange() })
            "Fab"           -> FabInspector(selectedBlock, onClose = { showInspector = false; onLayoutChange() })
        }
    }
}

/* ===== Inspector: ButtonRow ===== */
@Composable
private fun ButtonRowInspector(block: JSONObject, onClose: () -> Unit) {
    var open by remember { mutableStateOf(true) }
    val buttons = block.optJSONArray("buttons") ?: JSONArray().also { block.put("buttons", it) }
    if (!open) return
    ModalBottomSheet(onDismissRequest = { open = false; onClose() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("ButtonRow - Proprietà riga", style = MaterialTheme.typography.titleMedium)
            var align by remember { mutableStateOf(block.optString("align","center")) }
            ExposedDropdown(value = align, label = "align", options = listOf("start","center","end","space_between","space_around","space_evenly")) {
                align = it; block.put("align", it)
            }

            Divider()

            Text("Bottoni", style = MaterialTheme.typography.titleMedium)
            for (i in 0 until buttons.length()) {
                val btn = buttons.getJSONObject(i)
                ElevatedCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Bottone ${i+1}", style = MaterialTheme.typography.labelLarge)
                            Row {
                                IconButton(onClick = { moveInArray(buttons, i, -1) }) { Icon(Icons.Filled.KeyboardArrowUp, null) }
                                IconButton(onClick = { moveInArray(buttons, i, +1) }) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                            }
                        }
                        val label = remember { mutableStateOf(btn.optString("label","")) }
                        val icon  = remember { mutableStateOf(btn.optString("icon","")) }
                        var style by remember { mutableStateOf(btn.optString("style","primary")) }
                        var size  by remember { mutableStateOf(btn.optString("size","md")) }
                        var tint  by remember { mutableStateOf(btn.optString("tint","default")) }
                        var shape by remember { mutableStateOf(btn.optString("shape","rounded")) }
                        val cornerStr = remember { mutableStateOf(btn.optDouble("corner", 20.0).toString()) }
                        var press by remember { mutableStateOf(if (btn.optString("pressEffect","none")=="scale") "scale" else "none") }
                        val action= remember { mutableStateOf(btn.optString("actionId","")) }
                        val customColor = remember { mutableStateOf(btn.optString("customColor","")) }

                        OutlinedTextField(value = label.value, onValueChange = { label.value = it; btn.put("label", it) }, label = { Text("label") })
                        OutlinedTextField(value = icon.value,  onValueChange = { icon.value  = it; btn.put("icon", it) },  label = { Text("icon (Material name)") })
                        ExposedDropdown(value = style, label = "style", options = listOf("primary","tonal","outlined","text")) { style = it; btn.put("style", it) }
                        ExposedDropdown(value = size,  label = "size",  options = listOf("sm","md","lg")) { size = it; btn.put("size", it) }
                        ExposedDropdown(value = tint,  label = "tint",  options = listOf("default","success","warning","error")) { tint = it; btn.put("tint", it) }
                        ExposedDropdown(value = shape, label = "shape", options = listOf("rounded","pill","cut")) { shape = it; btn.put("shape", it) }
                        OutlinedTextField(value = cornerStr.value, onValueChange = {
                            cornerStr.value = it
                            it.toDoubleOrNull()?.let { v -> btn.put("corner", v) }
                        }, label = { Text("corner (dp)") })
                        ExposedDropdown(value = press, label = "pressEffect", options = listOf("none","scale")) { press = it; btn.put("pressEffect", it) }
                        OutlinedTextField(value = customColor.value, onValueChange = {
                            customColor.value = it; btn.put("customColor", it)
                        }, label = { Text("customColor (#RRGGBB opz.)") })
                        OutlinedTextField(value = action.value, onValueChange = { action.value = it; btn.put("actionId", it) }, label = { Text("actionId (es. nav:settings)") })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { buttons.put(JSONObject("{\"label\":\"Nuovo\",\"style\":\"text\",\"icon\":\"add\",\"actionId\":\"\"}")) }) { Text("+ Aggiungi bottone") }
                OutlinedButton(onClick = { open = false; onClose() }) { Text("Chiudi") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/* ===== Inspector: SectionHeader ===== */
@Composable
private fun SectionHeaderInspector(block: JSONObject, onClose: () -> Unit) {
    var open by remember { mutableStateOf(true) }
    if (!open) return
    val ctx = LocalContext.current
    val title = remember { mutableStateOf(block.optString("title","")) }
    val subtitle = remember { mutableStateOf(block.optString("subtitle","")) }
    var style by remember { mutableStateOf(block.optString("style","titleMedium")) }
    var align by remember { mutableStateOf(block.optString("align","start")) }
    val spaceTop = remember { mutableStateOf(block.optDouble("spaceTop", 0.0).toString()) }
    val spaceBottom = remember { mutableStateOf(block.optDouble("spaceBottom", 0.0).toString()) }
    val padStart = remember { mutableStateOf(block.optDouble("padStart", 0.0).toString()) }
    val padEnd = remember { mutableStateOf(block.optDouble("padEnd", 0.0).toString()) }
    val clickAction = remember { mutableStateOf(block.optString("clickActionId","")) }
    val newPageId = remember {
        val base = title.value.lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_')
        mutableStateOf(if (base.isBlank()) "nuova_pagina" else base)
    }

    ModalBottomSheet(onDismissRequest = { open = false; onClose() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("SectionHeader - Proprietà", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = title.value, onValueChange = { title.value = it; block.put("title", it) }, label = { Text("Titolo") })
            OutlinedTextField(value = subtitle.value, onValueChange = { subtitle.value = it; block.put("subtitle", it) }, label = { Text("Sottotitolo (opz.)") })
            ExposedDropdown(value = style, label = "style", options = listOf("displaySmall","headlineSmall","titleLarge","titleMedium","titleSmall","bodyLarge","bodyMedium")) {
                style = it; block.put("style", it)
            }
            ExposedDropdown(value = align, label = "align", options = listOf("start","center","end")) {
                align = it; block.put("align", it)
            }
            OutlinedTextField(value = spaceTop.value, onValueChange = { spaceTop.value = it; it.toDoubleOrNull()?.let { v -> block.put("spaceTop", v) } }, label = { Text("spaceTop (dp)") })
            OutlinedTextField(value = spaceBottom.value, onValueChange = { spaceBottom.value = it; it.toDoubleOrNull()?.let { v -> block.put("spaceBottom", v) } }, label = { Text("spaceBottom (dp)") })
            OutlinedTextField(value = padStart.value, onValueChange = { padStart.value = it; it.toDoubleOrNull()?.let { v -> block.put("padStart", v) } }, label = { Text("padStart (dp)") })
            OutlinedTextField(value = padEnd.value, onValueChange = { padEnd.value = it; it.toDoubleOrNull()?.let { v -> block.put("padEnd", v) } }, label = { Text("padEnd (dp)") })

            Divider()
            Text("Azione al tap", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = clickAction.value, onValueChange = { clickAction.value = it; block.put("clickActionId", it) }, label = { Text("clickActionId (es. nav:settings)") })

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = newPageId.value, onValueChange = { newPageId.value = it }, label = { Text("Nuova pagina id") })
                Button(onClick = {
                    val screenId = newPageId.value.ifBlank { "nuova_pagina" }
                    val newLayout = JSONObject("""{ "blocks": [ { "type":"SectionHeader", "title":"$screenId" } ] }""")
                    UiLoader.saveDraft(ctx, screenId, newLayout) // crea bozza della nuova pagina
                    block.put("clickActionId", "nav:$screenId")
                    clickAction.value = "nav:$screenId"
                }) { Text("Crea pagina e collega") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { open = false; onClose() }) { Text("Chiudi") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/* ===== Inspector: Spacer ===== */
@Composable
private fun SpacerInspector(block: JSONObject, onClose: () -> Unit) {
    var open by remember { mutableStateOf(true) }
    if (!open) return
    val height = remember { mutableStateOf(block.optDouble("height", 8.0).toString()) }
    ModalBottomSheet(onDismissRequest = { open = false; onClose() }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Spacer - Proprietà", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = height.value, onValueChange = { height.value = it; it.toDoubleOrNull()?.let { v -> block.put("height", v) } }, label = { Text("height (dp)") })
            OutlinedButton(onClick = { open = false; onClose() }) { Text("Chiudi") }
        }
    }
}

/* ===== Inspector: Divider (orizzontale) ===== */
@Composable
private fun DividerInspector(block: JSONObject, onClose: () -> Unit) {
    var open by remember { mutableStateOf(true) }
    if (!open) return
    val thickness = remember { mutableStateOf(block.optDouble("thickness", 1.0).toString()) }
    val padStart = remember { mutableStateOf(block.optDouble("padStart", 0.0).toString()) }
    val padEnd = remember { mutableStateOf(block.optDouble("padEnd", 0.0).toString()) }
    ModalBottomSheet(onDismissRequest = { open = false; onClose() }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Divider - Proprietà", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = thickness.value, onValueChange = { thickness.value = it; it.toDoubleOrNull()?.let { v -> block.put("thickness", v) } }, label = { Text("thickness (dp)") })
            OutlinedTextField(value = padStart.value, onValueChange = { padStart.value = it; it.toDoubleOrNull()?.let { v -> block.put("padStart", v) } }, label = { Text("padStart (dp)") })
            OutlinedTextField(value = padEnd.value, onValueChange = { padEnd.value = it; it.toDoubleOrNull()?.let { v -> block.put("padEnd", v) } }, label = { Text("padEnd (dp)") })
            OutlinedButton(onClick = { open = false; onClose() }) { Text("Chiudi") }
        }
    }
}

/* ===== Inspector: DividerV (verticale) ===== */
@Composable
private fun DividerVInspector(block: JSONObject, onClose: () -> Unit) {
    var open by remember { mutableStateOf(true) }
    if (!open) return
    val thickness = remember { mutableStateOf(block.optDouble("thickness", 1.0).toString()) }
    val height = remember { mutableStateOf(block.optDouble("height", 24.0).toString()) }
    ModalBottomSheet(onDismissRequest = { open = false; onClose() }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("DividerV - Proprietà", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = thickness.value, onValueChange = { thickness.value = it; it.toDoubleOrNull()?.let { v -> block.put("thickness", v) } }, label = { Text("thickness (dp)") })
            OutlinedTextField(value = height.value, onValueChange = { height.value = it; it.toDoubleOrNull()?.let { v -> block.put("height", v) } }, label = { Text("height (dp)") })
            OutlinedButton(onClick = { open = false; onClose() }) { Text("Chiudi") }
        }
    }
}

/* ===== Inspector: Card ===== */
@Composable
private fun CardInspector(block: JSONObject, onClose: () -> Unit) {
    var open by remember { mutableStateOf(true) }
    if (!open) return
    var variant by remember { mutableStateOf(block.optString("variant","elevated")) }
    val action = remember { mutableStateOf(block.optString("clickActionId","")) }
    ModalBottomSheet(onDismissRequest = { open = false; onClose() }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Card - Proprietà", style = MaterialTheme.typography.titleMedium)
            ExposedDropdown(value = variant, label = "variant", options = listOf("elevated","outlined","filled")) { variant = it; block.put("variant", it) }
            OutlinedTextField(value = action.value, onValueChange = { action.value = it; block.put("clickActionId", it) }, label = { Text("clickActionId (es. nav:run)") })
            OutlinedButton(onClick = { open = false; onClose() }) { Text("Chiudi") }
        }
    }
}

/* ===== Inspector: Fab ===== */
@Composable
private fun FabInspector(block: JSONObject, onClose: () -> Unit) {
    var open by remember { mutableStateOf(true) }
    if (!open) return
    val icon = remember { mutableStateOf(block.optString("icon","play_arrow")) }
    val label = remember { mutableStateOf(block.optString("label","Start")) }
    var variant by remember { mutableStateOf(block.optString("variant","regular")) }
    var size by remember { mutableStateOf(block.optString("size","regular")) }
    val action = remember { mutableStateOf(block.optString("actionId","start_run")) }
    ModalBottomSheet(onDismissRequest = { open = false; onClose() }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Fab - Proprietà", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = icon.value, onValueChange = { icon.value = it; block.put("icon", it) }, label = { Text("icon") })
            OutlinedTextField(value = label.value, onValueChange = { label.value = it; block.put("label", it) }, label = { Text("label") })
            ExposedDropdown(value = variant, label = "variant", options = listOf("regular","extended")) { variant = it; block.put("variant", it) }
            ExposedDropdown(value = size, label = "size", options = listOf("small","regular","large")) { size = it; block.put("size", it) }
            OutlinedTextField(value = action.value, onValueChange = { action.value = it; block.put("actionId", it) }, label = { Text("actionId") })
            OutlinedButton(onClick = { open = false; onClose() }) { Text("Chiudi") }
        }
    }
}

/* ===== Helpers: mapping e util ===== */

@Composable
private fun mapTextStyle(key: String): TextStyle = when (key) {
    "headlineSmall" -> MaterialTheme.typography.headlineSmall
    "titleLarge" -> MaterialTheme.typography.titleLarge
    "titleSmall" -> MaterialTheme.typography.titleSmall
    "bodyLarge" -> MaterialTheme.typography.bodyLarge
    "bodyMedium" -> MaterialTheme.typography.bodyMedium
    else -> MaterialTheme.typography.titleMedium
}

@Composable
private fun mapTextAlign(key: String): TextAlign = when (key) {
    "center" -> TextAlign.Center
    "end" -> TextAlign.End
    else -> TextAlign.Start
}

private fun sizeModifier(size: String): Modifier = when (size) {
    "sm" -> Modifier.height(36.dp)
    "lg" -> Modifier.height(52.dp)
    else -> Modifier.height(40.dp) // md default
}

@Composable
private fun IconText(label: String, icon: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NamedIcon(icon, null)
        Text(label)
    }
}

@Composable
private fun mapButtonColors(style: String, tint: String): Triple<Color, Color, Dp> {
    val cs = MaterialTheme.colorScheme
    val (baseContainer, baseContent) = when (tint) {
        "success" -> cs.tertiary to cs.onTertiary
        "warning" -> Color(0xFFFFD54F) to Color(0xFF3E2723)
        "error"   -> cs.errorContainer to cs.onErrorContainer
        else      -> cs.primary to cs.onPrimary
    }
    return when (style) {
        "outlined" -> Triple(Color.Transparent, when (tint) {
            "success" -> cs.tertiary
            "warning" -> Color(0xFF8D6E63)
            "error"   -> cs.error
            else      -> cs.primary
        }, 1.dp)
        "text"     -> Triple(Color.Transparent, when (tint) {
            "success" -> cs.tertiary
            "warning" -> Color(0xFF8D6E63)
            "error"   -> cs.error
            else      -> cs.primary
        }, 0.dp)
        "tonal"    -> Triple(cs.secondaryContainer, cs.onSecondaryContainer, 0.dp)
        else       -> Triple(baseContainer, baseContent, 0.dp)
    }
}

private fun moveAndReturnNewPath(root: JSONObject, path: String, delta: Int): String {
    val p = getParentAndIndex(root, path) ?: return path
    val (arr, idx) = p
    val newIdx = (idx + delta).coerceIn(0, arr.length()-1)
    if (newIdx == idx) return path
    val items = mutableListOf<Any?>()
    for (i in 0 until arr.length()) items.add(arr.get(i))
    val item = items.removeAt(idx)
    items.add(newIdx, item)
    while (arr.length() > 0) arr.remove(arr.length()-1)
    items.forEach { arr.put(it) }
    val parentPath = path.substringBeforeLast("/")
    return "$parentPath/$newIdx"
}

private fun moveInArray(arr: JSONArray, index: Int, delta: Int) {
    if (index < 0 || index >= arr.length()) return
    val newIdx = (index + delta).coerceIn(0, arr.length()-1)
    if (newIdx == index) return
    val tmp = mutableListOf<Any?>()
    for (i in 0 until arr.length()) tmp.add(arr.get(i))
    val it = tmp.removeAt(index)
    tmp.add(newIdx, it)
    while (arr.length() > 0) arr.remove(arr.length()-1)
    tmp.forEach { arr.put(it) }
}

@Composable
private fun ExposedDropdown(value: String, label: String, options: List<String>, onSelect: (String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { onSelect(it); expanded = false }) }
        }
    }
}

@Composable
private fun NamedIcon(name: String?, contentDescription: String?) {
    val image = when (name) {
        "settings"       -> Icons.Filled.Settings
        "more_vert"      -> Icons.Filled.MoreVert
        "tune"           -> Icons.Filled.Tune
        "play_arrow"     -> Icons.Filled.PlayArrow
        "pause"          -> Icons.Filled.Pause
        "stop"           -> Icons.Filled.Stop
        "add"            -> Icons.Filled.Add
        "flag"           -> Icons.Filled.Flag
        "queue_music"    -> Icons.Filled.QueueMusic
        "widgets"        -> Icons.Filled.Widgets
        "palette"        -> Icons.Filled.Palette
        "directions_run" -> Icons.Filled.DirectionsRun
        "home"           -> Icons.Filled.Home
        "menu"           -> Icons.Filled.Menu
        "close"          -> Icons.Filled.Close
        "more_horiz"     -> Icons.Filled.MoreHoriz
        else             -> null
    }
    if (image != null) {
        Icon(image, contentDescription)
    } else {
        Text(".")
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

private fun jsonAtPath(root: JSONObject, path: String): Any? {
    if (!path.startsWith("/")) return null
    val segs = path.trim('/').split('/')
    var node: Any = root
    var i = 0
    while (i < segs.size) {
        when (node) {
            is JSONObject -> { node = (node as JSONObject).opt(segs[i]) ?: return null; i++ }
            is JSONArray  -> {
                val idx = segs[i].toIntOrNull() ?: return null
                node = (node as JSONArray).opt(idx) ?: return null
                i++
            }
            else -> return null
        }
    }
    return node
}

private fun getParentAndIndex(root: JSONObject, path: String): Pair<JSONArray, Int>? {
    if (!path.startsWith("/")) return null
    val segs = path.trim('/').split('/')
    var node: Any = root
    var parentArr: JSONArray? = null
    var index = -1
    var i = 0
    while (i < segs.size) {
        val s = segs[i]
        when (node) {
            is JSONObject -> {
                node = (node as JSONObject).opt(s) ?: return null
                i++
            }
            is JSONArray -> {
                val idx = s.toIntOrNull() ?: return null
                parentArr = node as JSONArray
                index = idx
                node = parentArr.opt(idx) ?: return null
                i++
            }
        }
    }
    return if (parentArr != null && index >= 0) parentArr!! to index else null
}

private fun newSectionHeader() = JSONObject(
    """{ "type":"SectionHeader", "title":"Nuova sezione" }""".trimIndent()
)

private fun newButtonRow() = JSONObject(
    """{
  "type":"ButtonRow","align":"center",
  "buttons":[
    {"label":"Start","style":"primary","icon":"play_arrow","size":"md","tint":"default","shape":"rounded","corner":20,"pressEffect":"scale","actionId":"start_run"},
    {"label":"Pausa","style":"tonal","icon":"pause","size":"md","tint":"default","shape":"rounded","corner":20,"actionId":"pause_run"},
    {"label":"Stop","style":"outlined","icon":"stop","size":"md","tint":"error","shape":"rounded","corner":20,"actionId":"stop_run","confirm":true}
  ]
}""".trimIndent()
)

private fun newSpacer() = JSONObject("""{ "type":"Spacer", "height": 8 }""".trimIndent())

private fun newDividerV() = JSONObject("""{ "type":"DividerV", "thickness": 1, "height": 24 }""".trimIndent())

private fun newCard() = JSONObject(
    """{
  "type":"Card","variant":"elevated","clickActionId":"nav:run",
  "blocks":[ { "type":"SectionHeader", "title": "Card esempio", "style":"titleSmall", "align":"start" }, { "type":"Divider" } ]
}""".trimIndent()
)

private fun newFab() = JSONObject(
    """{ "type":"Fab", "icon":"play_arrow", "label":"Start", "variant":"extended", "actionId":"start_run" }""".trimIndent()
)

private fun newIconButton(menuId: String = "more_menu") = JSONObject(
    """{ "type":"IconButton", "icon":"more_vert", "openMenuId":"$menuId" }""".trimIndent()
)

private fun newMenu(menuId: String = "more_menu") = JSONObject(
    """{
  "type":"Menu","id":"$menuId",
  "items":[
    {"icon":"tune","label":"Layout Lab","actionId":"open_layout_lab"},
    {"icon":"palette","label":"Theme Lab","actionId":"open_theme_lab"},
    {"icon":"settings","label":"Impostazioni","actionId":"nav:settings"}
  ]
}""".trimIndent()
)

private fun insertBlockAndReturnPath(
    root: JSONObject,
    selectedPath: String?,
    block: JSONObject,
    position: String
): String {
    val parentArr: JSONArray
    val idx: Int
    val parentPath: String

    if (selectedPath != null) {
        val pair = getParentAndIndex(root, selectedPath)
        if (pair != null) {
            parentArr = pair.first
            idx = pair.second
            parentPath = selectedPath.substringBeforeLast("/")
        } else {
            parentArr = root.optJSONArray("blocks") ?: JSONArray().also { root.put("blocks", it) }
            idx = parentArr.length() - 1
            parentPath = "/blocks"
        }
    } else {
        parentArr = root.optJSONArray("blocks") ?: JSONArray().also { root.put("blocks", it) }
        idx = parentArr.length() - 1
        parentPath = "/blocks"
    }

    val insertIndex = when (position) { "before" -> idx; else -> idx + 1 }
        .coerceIn(0, parentArr.length())

    val tmp = mutableListOf<Any?>()
    for (i in 0 until parentArr.length()) {
        if (i == insertIndex) tmp.add(block)
        tmp.add(parentArr.get(i))
    }
    if (insertIndex == parentArr.length()) tmp.add(block)

    while (parentArr.length() > 0) parentArr.remove(parentArr.length() - 1)
    tmp.forEach { parentArr.put(it) }

    return "$parentPath/$insertIndex"
}

private fun insertIconMenuReturnIconPath(root: JSONObject, selectedPath: String?): String {
    val id = "menu_" + System.currentTimeMillis().toString().takeLast(5)
    val iconPath = insertBlockAndReturnPath(root, selectedPath, newIconButton(id), "after")
    insertBlockAndReturnPath(root, iconPath, newMenu(id), "after")
    return iconPath
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
