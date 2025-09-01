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
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/* =========================
 *  ENTRY POINT
 * ========================= */

@Composable
fun UiScreen(
    screenName: String,
    dispatch: (String) -> Unit,
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

    // Ricompone anche al variare di tick (usato per anteprima live)
    val menus by remember(layout, tick) { mutableStateOf(collectMenus(layout!!)) }
    var selectedPath by remember(screenName) { mutableStateOf<String?>(null) }
    var overlayHeightPx by remember { mutableStateOf(0) }
    val overlayHeightDp = with(LocalDensity.current) { (overlayHeightPx / density).dp }

    Box(Modifier.fillMaxSize()) {

        Column(
            Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
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
                // Anteprima live: bump di 'tick'
                onLiveChange = { tick++ },
                // Persistenza draft/pubblica/reset: bump e/o ricarico
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
                dispatch = dispatch,
                uiState = uiState,
                onOverlayHeight = { overlayHeightPx = it }
            )
        }
    }
}

/* =========================
 *  RENDERER
 * ========================= */

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

    // wrapper con overlay selezione (in Designer Mode cattura il tap su tutta l'area)
    @Composable
    fun Wrapper(content: @Composable ()->Unit) {
        if (designerMode) {
            Box {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(0.dp),
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
                        content()
                    }
                }
                // Overlay "trasparente" per selezionare sempre il blocco
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable(onClick = { onSelect(path) })
                )
            }
        } else {
            Box { content() }
        }
    }

    when (block.optString("type")) {
        "AppBar" -> Wrapper {
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

        "IconButton" -> Wrapper {
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
                }) { NamedIconEx(iconName, null) }

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
                                    if (ic.isNotBlank()) NamedIconEx(ic, null)
                                }
                            )
                        }
                    }
                }
            }
        }

        "Card" -> Wrapper {
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
                .then(if (clickAction.isNotBlank() && !designerMode) Modifier.clickable(onClick = { dispatch(clickAction) }) else Modifier)
            when (variant) {
                "outlined" -> OutlinedCard(mod) { Column(Modifier.padding(12.dp)) { inner() } }
                "filled"   -> Card(mod)        { Column(Modifier.padding(12.dp)) { inner() } }
                else       -> ElevatedCard(mod){ Column(Modifier.padding(12.dp)) { inner() } }
            }
        }

        "Tabs" -> Wrapper {
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
                        onClick = { idx = i },
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

        "SectionHeader" -> Wrapper {
            val style = mapTextStyle(block.optString("style", "titleMedium"))
            val align = mapTextAlign(block.optString("align", "start"))
            val spaceTop = block.optDouble("spaceTop", 0.0).toFloat().dp
            val spaceBottom = block.optDouble("spaceBottom", 0.0).toFloat().dp
            val padStart = block.optDouble("padStart", 0.0).toFloat().dp
            val padEnd = block.optDouble("padEnd", 0.0).toFloat().dp
            val clickAction = block.optString("clickActionId","")
            val textColor = parseColorOrRole(block.optString("textColor",""))

            if (spaceTop > 0.dp) Spacer(Modifier.height(spaceTop))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = padStart, end = padEnd)
                    .then(if (clickAction.isNotBlank() && !designerMode) Modifier.clickable { dispatch(clickAction) } else Modifier)
            ) {
                Text(
                    text = block.optString("title", ""),
                    style = applyTextStyleOverrides(block, style),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = align,
                    color = textColor ?: LocalContentColor.current
                )
                val sub = block.optString("subtitle", "")
                if (sub.isNotBlank()) {
                    Text(
                        sub,
                        style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyMedium),
                        textAlign = align,
                        modifier = Modifier.fillMaxWidth(),
                        color = textColor ?: LocalContentColor.current
                    )
                }
            }
            if (spaceBottom > 0.dp) Spacer(Modifier.height(spaceBottom))
        }

        "MetricsGrid" -> Wrapper {
            val tiles = block.optJSONArray("tiles") ?: JSONArray()
            val cols = block.optInt("columns", 2).coerceIn(1,3)
            GridSection(tiles, cols, uiState)
        }

        "ButtonRow" -> Wrapper {
            val align = when (block.optString("align")) {
                "start" -> Arrangement.Start
                "end" -> Arrangement.End
                "space_between" -> Arrangement.SpaceBetween
                "space_around" -> Arrangement.SpaceAround
                "space_evenly" -> Arrangement.SpaceEvenly
                else -> Arrangement.Center
            }
            val buttons = block.optJSONArray("buttons") ?: JSONArray()
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = align) {
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
                    var (container, content, border) = mapButtonColors(styleKey, tintKey)
                    run {
                        val __hex = btn.optString("customColor","")
                        val __col = parseColorOrRole(__hex)
                        if (__col != null) {
                            container = __col
                            content = bestOnColor(__col)
                        }
                    }
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

        "ChipRow" -> Wrapper {
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
                            label = { Text(label, style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyMedium), color = parseColorOrRole(block.optString("textColor","")) ?: LocalContentColor.current) },
                            leadingIcon = if (current == v) { { Icon(Icons.Filled.Check, null) } } else null
                        )
                    } else {
                        val current = (uiState[bind] as? Boolean) ?: false
                        FilterChip(
                            selected = current,
                            onClick = { uiState[bind] = !current },
                            label = { Text(label, style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyMedium), color = parseColorOrRole(block.optString("textColor","")) ?: LocalContentColor.current) },
                            leadingIcon = if (current) { { Icon(Icons.Filled.Check, null) } } else null
                        )
                    }
                }
            }
        }

        "Toggle" -> Wrapper {
            val label = block.optString("label","")
            val bind = block.optString("bind","")
            val v = (uiState[bind] as? Boolean) ?: false
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = v, onCheckedChange = { uiState[bind] = it })
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
        }

        "Slider" -> Wrapper {
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
                    uiState[bind] = if (step >= 1f) round(it/step)*step else it
                },
                valueRange = min..max
            )
        }

        "List" -> Wrapper {
            val items = block.optJSONArray("items") ?: JSONArray()
            Column {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    ListItem(
                        headlineContent = { Text(item.optString("title",""), style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyLarge), color = parseColorOrRole(block.optString("textColor","")) ?: LocalContentColor.current) },
                        supportingContent = {
                            val sub = item.optString("subtitle","")
                            if (sub.isNotBlank()) Text(sub, style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyMedium), color = parseColorOrRole(block.optString("textColor","")) ?: LocalContentColor.current)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(onClick = { dispatch(item.optString("actionId", "")) })
                    )
                    Divider()
                }
            }
        }

        "Carousel" -> Wrapper { 
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Carousel (placeholder)", style = MaterialTheme.typography.titleSmall)
                    Text("Le immagini saranno gestite in una fase successiva.")
                }
            }
        }

        "Fab" -> Wrapper {
            val icon = block.optString("icon","play_arrow")
            val label = block.optString("label","")
            val size = block.optString("size","regular")
            val variant = block.optString("variant","regular")
            val action = block.optString("actionId","")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                when (variant) {
                    "extended" -> ExtendedFloatingActionButton(
                        onClick = { dispatch(action) },
                        icon = { NamedIconEx(icon, null) },
                        text = { Text(label.ifBlank { "Azione" }) }
                    )
                    else -> when (size) {
                        "small" -> SmallFloatingActionButton(onClick = { dispatch(action) }) { NamedIconEx(icon, null) }
                        "large" -> LargeFloatingActionButton(onClick = { dispatch(action) }) { NamedIconEx(icon, null) }
                        else -> FloatingActionButton(onClick = { dispatch(action) }) { NamedIconEx(icon, null) }
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

/* =========================
 *  GRID
 * ========================= */

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

/* =========================
 *  DESIGNER OVERLAY
 * ========================= */

@Composable
private fun BoxScope.DesignerOverlay(
    screenName: String,
    layout: JSONObject,
    selectedPath: String?,
    setSelectedPath: (String?) -> Unit,
    onLiveChange: () -> Unit,
    onLayoutChange: () -> Unit,
    onSaveDraft: () -> Unit,
    onPublish: () -> Unit,
    onReset: () -> Unit,
    dispatch: (String) -> Unit, uiState: MutableMap<String, Any>, onOverlayHeight: (Int) -> Unit
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
                    OutlinedButton( onClick = {
                        val newPath = moveAndReturnNewPath(layout, selectedPath!!, -1)
                        setSelectedPath(newPath); onLayoutChange()
                    }) { Icon(Icons.Filled.KeyboardArrowUp, null); Spacer(Modifier.width(4.dp)); Text("Su") }
                    OutlinedButton( onClick = {
                        val newPath = moveAndReturnNewPath(layout, selectedPath!!, +1)
                        setSelectedPath(newPath); onLayoutChange()
                    }) { Icon(Icons.Filled.KeyboardArrowDown, null); Spacer(Modifier.width(4.dp)); Text("Giu") }
                    OutlinedButton( onClick = {
                        duplicate(layout, selectedPath!!); onLayoutChange()
                    }) { Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("Duplica") }
                    TextButton(
                        onClick = { remove(layout, selectedPath!!); setSelectedPath(null); onLayoutChange() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(4.dp)); Text("Elimina") }
                    Button(onClick = { showInspector = true }
                    ) { Text("Proprietà...") }
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

    if (showInspector && selectedBlock != null && selectedPath != null) {
        // Ogni inspector:
        // - sheet trasparente (containerColor alpha 0.85)
        // - anteprima live: onLiveChange() su ogni modifica
        // - OK/ANNULLA: su annulla ripristina backup
        val onApply = { showInspector = false; onLayoutChange() }
        val onCancel = { showInspector = false; onLiveChange() } // già ripristinato dentro inspector
        when (selectedBlock.optString("type")) {
            "ButtonRow"     -> ButtonRowInspector(layout, selectedPath, onApply, onCancel, onLiveChange)
            "SectionHeader" -> SectionHeaderInspector(layout, selectedPath, onApply, onCancel, onLiveChange)
            "Spacer"        -> SpacerInspector(layout, selectedPath, onApply, onCancel, onLiveChange)
            "Divider"       -> DividerInspector(layout, selectedPath, onApply, onCancel, onLiveChange)
            "DividerV"      -> DividerVInspector(layout, selectedPath, onApply, onCancel, onLiveChange)
            "Card"          -> CardInspector(layout, selectedPath, onApply, onCancel, onLiveChange)
            "Fab"           -> FabInspector(layout, selectedPath, onApply, onCancel, onLiveChange)
            "IconButton"    -> IconButtonInspector(layout, selectedPath, onApply, onCancel, onLiveChange)
            "List"         -> ListInspector(layout, selectedPath, onApply, onCancel, onLiveChange)
            "ChipRow"      -> ChipRowInspector(layout, selectedPath, onApply, onCancel, onLiveChange)
        }
    }
}

/* =========================
 *  INSPECTORS (trasparenti + live preview + ok/annulla)
 * ========================= */

@Composable
private fun ButtonRowInspector(
    layout: JSONObject,
    path: String,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    onLive: () -> Unit
) {
    var open by remember { mutableStateOf(true) }
    val block = (jsonAtPath(layout, path) as JSONObject)
    val backup = remember { JSONObject(block.toString()) }

    fun closeApply() { open = false; onApply() }
    fun closeCancel() {
        replaceAtPath(layout, path, backup)
        open = false
        onCancel()
    }

    ModalBottomSheet(
        onDismissRequest = { closeCancel() },
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        scrimColor = Color.Black.copy(alpha = 0.32f)
    ) {
        val buttons = block.optJSONArray("buttons") ?: JSONArray().also { block.put("buttons", it) }
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
                align = it; block.put("align", it); onLive()
            }

            Divider()

            Text("Bottoni", style = MaterialTheme.typography.titleMedium)
            for (i in 0 until buttons.length()) {
                val btn = buttons.getJSONObject(i)
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Bottone ${i+1}", style = MaterialTheme.typography.labelLarge)
                            Row {
                                IconButton(onClick = { moveInArray(buttons, i, -1); onLive() }) { Icon(Icons.Filled.KeyboardArrowUp, null) }
                                IconButton(onClick = { moveInArray(buttons, i, +1); onLive() }) { Icon(Icons.Filled.KeyboardArrowDown, null) }
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

                        OutlinedTextField(value = label.value, onValueChange = {
                            label.value = it; btn.put("label", it); onLive()
                        }, label = { Text("label") })

                        IconPickerField(value = icon, label = "icon", onSelected = {
                            icon.value = it; btn.put("icon", it); onLive()
                        })

                        ExposedDropdown(value = style, label = "style", options = listOf("primary","tonal","outlined","text")) {
                            style = it; btn.put("style", it); onLive()
                        }
                        ExposedDropdown(value = size,  label = "size",  options = listOf("sm","md","lg")) {
                            size = it; btn.put("size", it); onLive()
                        }
                        ExposedDropdown(value = shape, label = "shape", options = listOf("rounded","pill","cut")) {
                            shape = it; btn.put("shape", it); onLive()
                        }
                        StepperField(label = "corner (dp)", state = cornerStr, step = 2.0) { v ->
                            btn.put("corner", v); onLive()
                        }

                        // Colori
                        ColorRolePicker(value = tint, label = "tint (ruolo)", options = listOf("default","success","warning","error")) {
                            tint = it; btn.put("tint", it); onLive()
                        }
                        NamedColorPicker(
                            currentHexOrEmpty = customColor.value,
                            label = "customColor",
                            onPickHex = { hex ->
                                customColor.value = hex;
                                if (hex.isBlank()) btn.remove("customColor") else btn.put("customColor", hex)
                                onLive()
                            }
                        )

                        ExposedDropdown(value = press, label = "pressEffect", options = listOf("none","scale")) {
                            press = it; btn.put("pressEffect", it); onLive()
                        }
                        OutlinedTextField(value = action.value, onValueChange = {
                            action.value = it; btn.put("actionId", it); onLive()
                        }, label = { Text("actionId (es. nav:settings)") })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { buttons.put(JSONObject("{\"label\":\"Nuovo\",\"style\":\"text\",\"icon\":\"add\",\"actionId\":\"\"}")); onLive() }) { Text("+ Aggiungi bottone") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { closeCancel() }) { Text("Annulla") }
                Button(onClick = { closeApply() }) { Text("OK") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeaderInspector(
    layout: JSONObject,
    path: String,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    onLive: () -> Unit
) {
    var open by remember { mutableStateOf(true) }
    val block = (jsonAtPath(layout, path) as JSONObject)
    val backup = remember { JSONObject(block.toString()) }

    fun closeApply() { open = false; onApply() }
    fun closeCancel() {
        replaceAtPath(layout, path, backup)
        open = false
        onCancel()
    }

    ModalBottomSheet(
        onDismissRequest = { closeCancel() },
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        scrimColor = Color.Black.copy(alpha = 0.32f)
    ) {
        val title = remember { mutableStateOf(block.optString("title","")) }
        val subtitle = remember { mutableStateOf(block.optString("subtitle","")) }
        var style by remember { mutableStateOf(block.optString("style","titleMedium")) }
        var align by remember { mutableStateOf(block.optString("align","start")) }
        val spaceTop = remember { mutableStateOf(block.optDouble("spaceTop", 0.0).toString()) }
        val spaceBottom = remember { mutableStateOf(block.optDouble("spaceBottom", 0.0).toString()) }
        val padStart = remember { mutableStateOf(block.optDouble("padStart", 0.0).toString()) }
        val padEnd = remember { mutableStateOf(block.optDouble("padEnd", 0.0).toString()) }
        val clickAction = remember { mutableStateOf(block.optString("clickActionId","")) }
        var textColor by remember { mutableStateOf(block.optString("textColor","")) }

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("SectionHeader - Proprietà", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = title.value, onValueChange = {
                title.value = it; block.put("title", it); onLive()
            }, label = { Text("Titolo") })
            OutlinedTextField(value = subtitle.value, onValueChange = {
                subtitle.value = it; block.put("subtitle", it); onLive()
            }, label = { Text("Sottotitolo (opz.)") })

            ExposedDropdown(value = style, label = "style", options = listOf("displaySmall","headlineSmall","titleLarge","titleMedium","titleSmall","bodyLarge","bodyMedium")) {
                style = it; block.put("style", it); onLive()
            }
            ExposedDropdown(value = align, label = "align", options = listOf("start","center","end")) {
                align = it; block.put("align", it); onLive()
            }

            // Stepper +/- per spaziature e padding
            StepperField("spaceTop (dp)", spaceTop, 2.0) { v -> block.put("spaceTop", v); onLive() }
            StepperField("spaceBottom (dp)", spaceBottom, 2.0) { v -> block.put("spaceBottom", v); onLive() }
            StepperField("padStart (dp)", padStart, 2.0) { v -> block.put("padStart", v); onLive() }
            StepperField("padEnd (dp)", padEnd, 2.0) { v -> block.put("padEnd", v); onLive() }

            // Colore testo
            ColorRolePicker(value = textColor, label = "textColor (ruolo o #RRGGBB)", options = COLOR_ROLES_WITH_EMPTY) {
                textColor = it
                if (it.isBlank()) block.remove("textColor") else block.put("textColor", it)
                onLive()
            }

            // Tipografia granulare
            val textSize = remember { mutableStateOf(
                block.optDouble("textSizeSp", Double.NaN).let { if (it.isNaN()) "" else it.toString() }
            ) }
            var fontFamily by remember { mutableStateOf(block.optString("fontFamily","")) }
            var fontWeight by remember { mutableStateOf(block.optString("fontWeight","")) }

            StepperField("textSize (sp)", textSize, 1.0) { v ->
                if (v <= 0.0) { block.remove("textSizeSp") } else { block.put("textSizeSp", v) }
                onLive()
            }

            ExposedDropdown(
                value = if (fontFamily.isBlank()) "(default)" else fontFamily,
                label = "fontFamily",
                options = listOf("(default)","serif","monospace","cursive")
            ) { sel ->
                val v = if (sel == "(default)") "" else sel
                fontFamily = v
                if (v.isBlank()) block.remove("fontFamily") else block.put("fontFamily", v)
                onLive()
            }

            ExposedDropdown(
                value = if (fontWeight.isBlank()) "(default)" else fontWeight,
                label = "fontWeight",
                options = listOf("(default)","w300","w400","w500","w600","w700")
            ) { sel ->
                val v = if (sel == "(default)") "" else sel
                fontWeight = v
                if (v.isBlank()) block.remove("fontWeight") else block.put("fontWeight", v)
                onLive()
            }

            // Palette colori nominali
            NamedColorPicker(
                currentHexOrEmpty = textColor,
                label = "textColor (palette nomi)",
                onPickHex = { hex ->
                    textColor = hex
                    if (hex.isBlank()) block.remove("textColor") else block.put("textColor", hex)
                    onLive()
                }
            )
            Divider()
            Text("Azione al tap", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = clickAction.value, onValueChange = {
                clickAction.value = it; block.put("clickActionId", it); onLive()
            }, label = { Text("clickActionId (es. nav:settings)") })

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { closeCancel() }) { Text("Annulla") }
                Button(onClick = { closeApply() }) { Text("OK") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SpacerInspector(
    layout: JSONObject,
    path: String,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    onLive: () -> Unit
) {
    var open by remember { mutableStateOf(true) }
    val block = (jsonAtPath(layout, path) as JSONObject)
    val backup = remember { JSONObject(block.toString()) }

    fun closeApply() { open = false; onApply() }
    fun closeCancel() { replaceAtPath(layout, path, backup); open = false; onCancel() }

    ModalBottomSheet(
        onDismissRequest = { closeCancel() },
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        scrimColor = Color.Black.copy(alpha = 0.32f)
    ) {
        val height = remember { mutableStateOf(block.optDouble("height", 8.0).toString()) }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Spacer - Proprietà", style = MaterialTheme.typography.titleMedium)
            StepperField("height (dp)", height, 2.0) { v -> block.put("height", v); onLive() }
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { closeCancel() }) { Text("Annulla") }
                Button(onClick = { closeApply() }) { Text("OK") }
            }
        }
    }
}

@Composable
private fun DividerInspector(
    layout: JSONObject,
    path: String,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    onLive: () -> Unit
) {
    var open by remember { mutableStateOf(true) }
    val block = (jsonAtPath(layout, path) as JSONObject)
    val backup = remember { JSONObject(block.toString()) }
    fun closeApply() { open = false; onApply() }
    fun closeCancel() { replaceAtPath(layout, path, backup); open = false; onCancel() }

    ModalBottomSheet(
        onDismissRequest = { closeCancel() },
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        scrimColor = Color.Black.copy(alpha = 0.32f)
    ) {
        val thickness = remember { mutableStateOf(block.optDouble("thickness", 1.0).toString()) }
        val padStart = remember { mutableStateOf(block.optDouble("padStart", 0.0).toString()) }
        val padEnd = remember { mutableStateOf(block.optDouble("padEnd", 0.0).toString()) }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Divider - Proprietà", style = MaterialTheme.typography.titleMedium)
            StepperField("thickness (dp)", thickness, 1.0) { v -> block.put("thickness", v); onLive() }
            StepperField("padStart (dp)", padStart, 2.0) { v -> block.put("padStart", v); onLive() }
            StepperField("padEnd (dp)", padEnd, 2.0) { v -> block.put("padEnd", v); onLive() }
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { closeCancel() }) { Text("Annulla") }
                Button(onClick = { closeApply() }) { Text("OK") }
            }
        }
    }
}

@Composable
private fun DividerVInspector(
    layout: JSONObject,
    path: String,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    onLive: () -> Unit
) {
    var open by remember { mutableStateOf(true) }
    val block = (jsonAtPath(layout, path) as JSONObject)
    val backup = remember { JSONObject(block.toString()) }
    fun closeApply() { open = false; onApply() }
    fun closeCancel() { replaceAtPath(layout, path, backup); open = false; onCancel() }

    ModalBottomSheet(
        onDismissRequest = { closeCancel() },
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        scrimColor = Color.Black.copy(alpha = 0.32f)
    ) {
        val thickness = remember { mutableStateOf(block.optDouble("thickness", 1.0).toString()) }
        val height = remember { mutableStateOf(block.optDouble("height", 24.0).toString()) }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("DividerV - Proprietà", style = MaterialTheme.typography.titleMedium)
            StepperField("thickness (dp)", thickness, 1.0) { v -> block.put("thickness", v); onLive() }
            StepperField("height (dp)", height, 2.0) { v -> block.put("height", v); onLive() }
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { closeCancel() }) { Text("Annulla") }
                Button(onClick = { closeApply() }) { Text("OK") }
            }
        }
    }
}

@Composable
private fun CardInspector(
    layout: JSONObject,
    path: String,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    onLive: () -> Unit
) {
    var open by remember { mutableStateOf(true) }
    val block = (jsonAtPath(layout, path) as JSONObject)
    val backup = remember { JSONObject(block.toString()) }
    fun closeApply() { open = false; onApply() }
    fun closeCancel() { replaceAtPath(layout, path, backup); open = false; onCancel() }

    ModalBottomSheet(
        onDismissRequest = { closeCancel() },
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        scrimColor = Color.Black.copy(alpha = 0.32f)
    ) {
        var variant by remember { mutableStateOf(block.optString("variant","elevated")) }
        val action = remember { mutableStateOf(block.optString("clickActionId","")) }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Card - Proprietà", style = MaterialTheme.typography.titleMedium)
            ExposedDropdown(value = variant, label = "variant", options = listOf("elevated","outlined","filled")) {
                variant = it; block.put("variant", it); onLive()
            }
            OutlinedTextField(value = action.value, onValueChange = {
                action.value = it; block.put("clickActionId", it); onLive()
            }, label = { Text("clickActionId (es. nav:run)") })
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { closeCancel() }) { Text("Annulla") }
                Button(onClick = { closeApply() }) { Text("OK") }
            }
        }
    }
}

@Composable
private fun FabInspector(
    layout: JSONObject,
    path: String,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    onLive: () -> Unit
) {
    var open by remember { mutableStateOf(true) }
    val block = (jsonAtPath(layout, path) as JSONObject)
    val backup = remember { JSONObject(block.toString()) }
    fun closeApply() { open = false; onApply() }
    fun closeCancel() { replaceAtPath(layout, path, backup); open = false; onCancel() }

    ModalBottomSheet(
        onDismissRequest = { closeCancel() },
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        scrimColor = Color.Black.copy(alpha = 0.32f)
    ) {
        val icon = remember { mutableStateOf(block.optString("icon","play_arrow")) }
        val label = remember { mutableStateOf(block.optString("label","Start")) }
        var variant by remember { mutableStateOf(block.optString("variant","regular")) }
        var size by remember { mutableStateOf(block.optString("size","regular")) }
        val action = remember { mutableStateOf(block.optString("actionId","start_run")) }

        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Fab - Proprietà", style = MaterialTheme.typography.titleMedium)

            IconPickerField(icon, "icon") { sel ->
                icon.value = sel; block.put("icon", sel); onLive()
            }

            OutlinedTextField(value = label.value, onValueChange = {
                label.value = it; block.put("label", it); onLive()
            }, label = { Text("label") })

            ExposedDropdown(value = variant, label = "variant", options = listOf("regular","extended")) {
                variant = it; block.put("variant", it); onLive()
            }
            ExposedDropdown(value = size, label = "size", options = listOf("small","regular","large")) {
                size = it; block.put("size", it); onLive()
            }
            OutlinedTextField(value = action.value, onValueChange = {
                action.value = it; block.put("actionId", it); onLive()
            }, label = { Text("actionId") })
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { closeCancel() }) { Text("Annulla") }
                Button(onClick = { closeApply() }) { Text("OK") }
            }
        }
    }
}

@Composable
private fun IconButtonInspector(
    layout: JSONObject,
    path: String,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    onLive: () -> Unit
) {
    var open by remember { mutableStateOf(true) }
    val block = (jsonAtPath(layout, path) as JSONObject)
    val backup = remember { JSONObject(block.toString()) }
    fun closeApply() { open = false; onApply() }
    fun closeCancel() { replaceAtPath(layout, path, backup); open = false; onCancel() }

    ModalBottomSheet(
        onDismissRequest = { closeCancel() },
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        scrimColor = Color.Black.copy(alpha = 0.32f)
    ) {
        val icon = remember { mutableStateOf(block.optString("icon","more_vert")) }
        val action = remember { mutableStateOf(block.optString("actionId","")) }
        val openMenuId = remember { mutableStateOf(block.optString("openMenuId","")) }

        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("IconButton - Proprietà", style = MaterialTheme.typography.titleMedium)

            IconPickerField(icon, "icon") { sel ->
                icon.value = sel; block.put("icon", sel); onLive()
            }

            OutlinedTextField(
                value = action.value,
                onValueChange = { action.value = it; block.put("actionId", it); onLive() },
                label = { Text("actionId (es. nav:settings o open_menu:<id>)") }
            )
            OutlinedTextField(
                value = openMenuId.value,
                onValueChange = { openMenuId.value = it; block.put("openMenuId", it); onLive() },
                label = { Text("openMenuId (se non usi actionId=open_menu:...)") }
            )

            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { closeCancel() }) { Text("Annulla") }
                Button(onClick = { closeApply() }) { Text("OK") }
            }
        }
    }
}

/* =========================
 *  HELPERS: mapping, pickers, utils
 * ========================= */

@Composable
private fun mapTextStyle(key: String): TextStyle = when (key) {
    "displaySmall" -> MaterialTheme.typography.displaySmall
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
    else -> Modifier.height(40.dp)
}

@Composable
private fun IconText(label: String, icon: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NamedIconEx(icon, null)
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

/* ---- Pickers ---- */

private val ICONS = listOf(
    "settings","more_vert","tune","play_arrow","pause","stop","add","flag",
    "queue_music","widgets","palette","home","menu","close","more_horiz",
    "directions_run","directions_walk","directions_bike","fitness_center",
    "timer","timer_off","watch_later","map","my_location","place","speed",
    "bolt","local_fire_department","sports_score"
)

@Composable
private fun IconPickerField(
    value: MutableState<String>,
    label: String,
    onSelected: (String)->Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = value.value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            leadingIcon = { if (value.value.isNotBlank()) NamedIconEx(value.value, null) },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ICONS.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    leadingIcon = { NamedIconEx(name, null) },
                    onClick = { onSelected(name); expanded = false }
                )
            }
        }
    }
}

private val COLOR_ROLES_WITH_EMPTY = listOf(
    "", "primary","onPrimary","secondary","onSecondary","tertiary","onTertiary","error","onError","surface","onSurface"
)

@Composable
private fun ColorRolePicker(
    value: String,
    label: String,
    options: List<String>,
    onSelect: (String)->Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val display = if (value.isBlank()) "(default)" else value
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(if (opt.isBlank()) "(default)" else opt) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun StepperField(
    label: String,
    state: MutableState<String>,
    step: Double = 1.0,
    onChangeValue: (Double)->Unit
) {
    fun current(): Double = state.value.toDoubleOrNull() ?: 0.0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val v = current() - step
                state.value = "%.2f".format(max(v, -10000.0))
                onChangeValue(current())
            }) { Text("-") }
            OutlinedTextField(
                value = state.value,
                onValueChange = {
                    state.value = it
                    val num = it.toDoubleOrNull()
                    if (num != null) onChangeValue(num)
                },
                singleLine = true,
                modifier = Modifier.width(120.dp)
            )
            OutlinedButton(onClick = {
                val v = current() + step
                state.value = "%.2f".format(min(v, 10000.0))
                onChangeValue(current())
            }) { Text("+") }
        }
    }
}

/* ---- Icon helper ---- */

@Composable
private fun NamedIconEx(name: String?, contentDescription: String?) {
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

/* ---- Color parsing ---- */

@Composable
private fun parseColorOrRole(value: String?): Color? {
    if (value.isNullOrBlank()) return null
    val v = value.trim()
    // Hex
    if (v.startsWith("#") && (v.length == 7 || v.length == 9)) {
        return try { Color(android.graphics.Color.parseColor(v)) } catch (_: Exception) { null }
    }
    val cs = MaterialTheme.colorScheme
    return when (v) {
        "primary" -> cs.primary
        "onPrimary" -> cs.onPrimary
        "secondary" -> cs.secondary
        "onSecondary" -> cs.onSecondary
        "tertiary" -> cs.tertiary
        "onTertiary" -> cs.onTertiary
        "error" -> cs.error
        "onError" -> cs.onError
        "surface" -> cs.surface
        "onSurface" -> cs.onSurface
        else -> null
    }
}

/* ---- JSON utils ---- */

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
            is JSONObject -> { node = (node as JSONObject).opt(s) ?: return null; i++ }
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

private fun replaceAtPath(root: JSONObject, path: String, newNode: JSONObject) {
    val p = getParentAndIndex(root, path) ?: return
    p.first.put(p.second, JSONObject(newNode.toString()))
}

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

/* ---- Blueprints ---- */

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
@Composable
private fun ExposedDropdown(
    value: String,
    label: String,
    options: List<String>,
    onSelect: (String)->Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}
private fun duplicate(root: JSONObject, path: String) {
    val p = getParentAndIndex(root, path) ?: return
    val (arr, idx) = p
    val clone = JSONObject(arr.getJSONObject(idx).toString())
    val tmp = mutableListOf<Any?>()
    for (i in 0 until arr.length()) {
        tmp.add(arr.get(i))
        if (i == idx) tmp.add(clone)
    }
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

private fun removeAt(arr: JSONArray, index: Int) {
    if (index < 0 || index >= arr.length()) return
    val tmp = mutableListOf<Any?>()
    for (i in 0 until arr.length()) if (i != index) tmp.add(arr.get(i))
    while (arr.length() > 0) arr.remove(arr.length() - 1)
    tmp.forEach { arr.put(it) }
}

@Composable
private fun NamedIconEx_dup(name: String?, contentDescription: String?) {
    val image = when (name) {
        // base
        "settings" -> Icons.Filled.Settings
        "more_vert" -> Icons.Filled.MoreVert
        "tune" -> Icons.Filled.Tune
        "play_arrow" -> Icons.Filled.PlayArrow
        "pause" -> Icons.Filled.Pause
        "stop" -> Icons.Filled.Stop
        "add" -> Icons.Filled.Add
        "flag" -> Icons.Filled.Flag
        "queue_music" -> Icons.Filled.QueueMusic
        "widgets" -> Icons.Filled.Widgets
        "palette" -> Icons.Filled.Palette
        "home" -> Icons.Filled.Home
        "menu" -> Icons.Filled.Menu
        "close" -> Icons.Filled.Close
        "more_horiz" -> Icons.Filled.MoreHoriz
        // running & fitness
        "directions_run" -> Icons.Filled.DirectionsRun
        "directions_walk" -> Icons.Filled.DirectionsWalk
        "directions_bike" -> Icons.Filled.DirectionsBike
        "fitness_center" -> Icons.Filled.FitnessCenter
        "timer" -> Icons.Filled.Timer
        "timer_off" -> Icons.Filled.TimerOff
        "watch_later" -> Icons.Filled.WatchLater
        "map" -> Icons.Filled.Map
        "my_location" -> Icons.Filled.MyLocation
        "place" -> Icons.Filled.Place
        "speed" -> Icons.Filled.Speed
        "bolt" -> Icons.Filled.Bolt
        "local_fire_department" -> Icons.Filled.LocalFireDepartment
        "sports_score" -> Icons.Filled.SportsScore
        else -> null
    }
    if (image != null) {
        Icon(image, contentDescription)
    } else {
        val ctx = LocalContext.current
        val resName = if (name != null && name.startsWith("res:")) name.removePrefix("res:") else name ?: ""
        val resId = if (resName.isNotBlank()) ctx.resources.getIdentifier(resName, "drawable", ctx.packageName) else 0
        if (resId != 0) {
            Icon(painterResource(id = resId), contentDescription)
        } else {
            Text("·")
        }
    }
}

@Composable
private fun applyTextStyleOverrides(node: JSONObject, base: TextStyle): TextStyle {
    var st = base
    val size = node.optDouble("textSizeSp", Double.NaN)
    if (!size.isNaN()) st = st.copy(fontSize = size.sp)

    val weightKey = node.optString("fontWeight","")
    val weight = when (weightKey) {
        "w300" -> FontWeight.Light
        "w400" -> FontWeight.Normal
        "w500" -> FontWeight.Medium
        "w600" -> FontWeight.SemiBold
        "w700" -> FontWeight.Bold
        else -> null
    }
    if (weight != null) st = st.copy(fontWeight = weight)

    val familyKey = node.optString("fontFamily","")
    val family = when (familyKey) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> null
    }
    if (family != null) st = st.copy(fontFamily = family)

    return st
}

private val NAMED_COLORS = linkedMapOf(
    "Red" to 0xFFE53935, "Pink" to 0xFFD81B60, "Purple" to 0xFF8E24AA, "Deep Purple" to 0xFF5E35B1,
    "Indigo" to 0xFF3949AB, "Blue" to 0xFF1E88E5, "Light Blue" to 0xFF039BE5, "Cyan" to 0xFF00ACC1,
    "Teal" to 0xFF00897B, "Green" to 0xFF43A047, "Light Green" to 0xFF7CB342, "Lime" to 0xFFC0CA33,
    "Yellow" to 0xFFFDD835, "Amber" to 0xFFFFB300, "Orange" to 0xFFFB8C00, "Deep Orange" to 0xFFF4511E,
    "Brown" to 0xFF6D4C41, "Blue Grey" to 0xFF546E7A
)

@Composable
private fun NamedColorPicker(
    currentHexOrEmpty: String,
    label: String,
    onPickHex: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val display = if (currentHexOrEmpty.isBlank()) "(default)" else currentHexOrEmpty
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            NAMED_COLORS.forEach { (name, argb) ->
                val c = Color(argb)
                DropdownMenuItem(
                    leadingIcon = { Box(Modifier.size(16.dp).background(c, RoundedCornerShape(3.dp))) },
                    text = { Text(name) },
                    onClick = {
                        val hex = "#%06X".format(0xFFFFFF and argb.toInt())
                        onPickHex(hex); expanded = false
                    }
                )
            }
            DropdownMenuItem(text = { Text("(default)") }, onClick = { onPickHex(""); expanded = false })
        }
    }
}

/* ---- Readability helper ---- */
private fun bestOnColor(bg: Color): Color {
    val l = 0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue
    return if (l < 0.5f) Color.White else Color.Black
}

@Composable
private fun ListInspector(
    layout: JSONObject,
    path: String,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    onLive: () -> Unit
) {
    var open by remember { mutableStateOf(true) }
    val block = (jsonAtPath(layout, path) as JSONObject)
    val backup = remember { JSONObject(block.toString()) }
    fun closeApply() { open = false; onApply() }
    fun closeCancel() { replaceAtPath(layout, path, backup); open = false; onCancel() }

    ModalBottomSheet(
        onDismissRequest = { closeCancel() },
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        scrimColor = Color.Black.copy(alpha = 0.32f)
    ) {
        val textSize = remember { mutableStateOf(
            block.optDouble("textSizeSp", Double.NaN).let { if (it.isNaN()) "" else it.toString() }
        ) }
        var fontFamily by remember { mutableStateOf(block.optString("fontFamily","")) }
        var fontWeight by remember { mutableStateOf(block.optString("fontWeight","")) }
        var textColor  by remember { mutableStateOf(block.optString("textColor","")) }

        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Text("List - Proprietà testo", style = MaterialTheme.typography.titleMedium)

            StepperField("textSize (sp)", textSize, 1.0) { v ->
                if (v <= 0.0) block.remove("textSizeSp") else block.put("textSizeSp", v); onLive()
            }

            ExposedDropdown(
                value = if (fontFamily.isBlank()) "(default)" else fontFamily,
                label = "fontFamily",
                options = listOf("(default)","serif","monospace","cursive")
            ) { sel ->
                val v = if (sel == "(default)") "" else sel
                fontFamily = v
                if (v.isBlank()) block.remove("fontFamily") else block.put("fontFamily", v)
                onLive()
            }

            ExposedDropdown(
                value = if (fontWeight.isBlank()) "(default)" else fontWeight,
                label = "fontWeight",
                options = listOf("(default)","w300","w400","w500","w600","w700")
            ) { sel ->
                val v = if (sel == "(default)") "" else sel
                fontWeight = v
                if (v.isBlank()) block.remove("fontWeight") else block.put("fontWeight", v)
                onLive()
            }

            NamedColorPicker(
                currentHexOrEmpty = textColor,
                label = "textColor",
                onPickHex = { hex ->
                    textColor = hex
                    if (hex.isBlank()) block.remove("textColor") else block.put("textColor", hex)
                    onLive()
                }
            )

            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { closeCancel() }) { Text("Annulla") }
                Button(onClick = { closeApply() }) { Text("OK") }
            }
        }
    }
}

@Composable
private fun ChipRowInspector(
    layout: JSONObject,
    path: String,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    onLive: () -> Unit
) {
    var open by remember { mutableStateOf(true) }
    val block = (jsonAtPath(layout, path) as JSONObject)
    val backup = remember { JSONObject(block.toString()) }
    fun closeApply() { open = false; onApply() }
    fun closeCancel() { replaceAtPath(layout, path, backup); open = false; onCancel() }

    ModalBottomSheet(
        onDismissRequest = { closeCancel() },
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        scrimColor = Color.Black.copy(alpha = 0.32f)
    ) {
        val textSize = remember { mutableStateOf(
            block.optDouble("textSizeSp", Double.NaN).let { if (it.isNaN()) "" else it.toString() }
        ) }
        var fontFamily by remember { mutableStateOf(block.optString("fontFamily","")) }
        var fontWeight by remember { mutableStateOf(block.optString("fontWeight","")) }
        var textColor  by remember { mutableStateOf(block.optString("textColor","")) }

        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {

            Text("ChipRow - Proprietà testo", style = MaterialTheme.typography.titleMedium)

            StepperField("textSize (sp)", textSize, 1.0) { v ->
                if (v <= 0.0) block.remove("textSizeSp") else block.put("textSizeSp", v); onLive()
            }

            ExposedDropdown(
                value = if (fontFamily.isBlank()) "(default)" else fontFamily,
                label = "fontFamily",
                options = listOf("(default)","serif","monospace","cursive")
            ) { sel ->
                val v = if (sel == "(default)") "" else sel
                fontFamily = v
                if (v.isBlank()) block.remove("fontFamily") else block.put("fontFamily", v)
                onLive()
            }

            ExposedDropdown(
                value = if (fontWeight.isBlank()) "(default)" else fontWeight,
                label = "fontWeight",
                options = listOf("(default)","w300","w400","w500","w600","w700")
            ) { sel ->
                val v = if (sel == "(default)") "" else sel
                fontWeight = v
                if (v.isBlank()) block.remove("fontWeight") else block.put("fontWeight", v)
                onLive()
            }

            NamedColorPicker(
                currentHexOrEmpty = textColor,
                label = "textColor",
                onPickHex = { hex ->
                    textColor = hex
                    if (hex.isBlank()) block.remove("textColor") else block.put("textColor", hex)
                    onLive()
                }
            )

            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { closeCancel() }) { Text("Annulla") }
                Button(onClick = { closeApply() }) { Text("OK") }
            }
        }
    }
}

@Composable
private fun DockedInspectorShell(
    layout: JSONObject,
    path: String,
    dispatch: (String) -> Unit,
    uiState: MutableMap<String, Any>,
    title: String,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val menus = remember(layout) { collectMenus(layout) }
    val block = remember(layout, path) { jsonAtPath(layout, path) as? JSONObject }

    // Overlay full-screen, super trasparente
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(Modifier.fillMaxSize()) {
            // PREVIEW PINNATA IN ALTO
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.06f)) {
                Column(Modifier.fillMaxWidth().padding(12.dp),
                       verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.labelMedium)
                    if (block != null) {
                        // Render senza designer overlay, per anteprima pulita
                        RenderBlock(
                            block = block,
                            dispatch = dispatch,
                            uiState = uiState,
                            designerMode = false,
                            path = path,
                            menus = menus,
                            onSelect = { }
                        )
                    } else {
                        Text("Blocco non trovato", color = Color.Red)
                    }
                }
            }

            Divider()

            // PANNELLO IMPOSTAZIONI CHE OCCUPA IL RESTO DELLO SCHERMO
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Proprietà", style = MaterialTheme.typography.titleMedium)
                        Row {
                            TextButton(onClick = onCancel) { Text("Indietro") }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = onApply) { Text("OK") }
                        }
                    }
                    content()
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
