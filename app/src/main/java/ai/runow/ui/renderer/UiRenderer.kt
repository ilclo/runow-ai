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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/* =========================
 * ENTRYPOINT
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
                onLiveChange = { tick++ },
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

/* =========================
 * RENDERER
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

    @Composable
    fun Wrapper(content: @Composable ()->Unit) {
        if (designerMode) {
            Box {
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
                        content()
                    }
                }
                // overlay che cattura la selezione senza interferire con i contenuti
                Box(Modifier.matchParentSize().clickable { onSelect(path) })
            }
        } else {
            Box { content() }
        }
    }

    when (block.optString("type")) {

        /* ---- PAGE: contenitore semplice (Column) ---- */
        "Page" -> Wrapper {
            val inner = block.optJSONArray("blocks") ?: JSONArray()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (i in 0 until inner.length()) {
                    val b = inner.optJSONObject(i) ?: continue
                    val p2 = "$path/blocks/$i"
                    RenderBlock(b, dispatch, uiState, designerMode, p2, menus, onSelect)
                }
            }
        }

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

        "Progress" -> Wrapper {
            val label = block.optString("label","")
            val value = block.optDouble("value", 0.0).toFloat().coerceIn(0f, 100f)
            val color = parseColorOrRole(block.optString("color","")) ?: MaterialTheme.colorScheme.primary
            val showPercent = block.optBoolean("showPercent", true)

            Column {
                if (label.isNotBlank()) Text(label, style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(
                    progress = { value / 100f },
                    trackColor = color.copy(alpha = 0.25f),
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                if (showPercent) {
                    Spacer(Modifier.height(6.dp))
                    Text("${value.toInt()}%", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        "Alert" -> Wrapper {
            val severity = block.optString("severity","info")
            val (bg, fg) = when (severity) {
                "success" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                "warning" -> Color(0xFFFFF3CD) to Color(0xFF664D03)
                "error"   -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                else      -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
            }
            val title = block.optString("title","")
            val message = block.optString("message","")
            val actionId = block.optString("actionId","")

            Surface(
                color = bg,
                contentColor = fg,
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleSmall)
                    if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodyMedium)
                    if (actionId.isNotBlank()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { dispatch(actionId) }) { Text("Azione") }
                        }
                    }
                }
            }
        }

        "Image" -> Wrapper {
            val source = block.optString("source","")
            val height = block.optDouble("heightDp", 160.0).toFloat().dp
            val corner = block.optDouble("corner", 12.0).toFloat().dp
            val scale = when (block.optString("contentScale","fit")) {
                "crop" -> ContentScale.Crop
                else   -> ContentScale.Fit
            }

            val resId = if (source.startsWith("res:"))
                LocalContext.current.resources.getIdentifier(
                    source.removePrefix("res:"),
                    "drawable",
                    LocalContext.current.packageName
                )
            else 0

            Surface(shape = RoundedCornerShape(corner), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                if (resId != 0) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(resId),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(height),
                        contentScale = scale
                    )
                } else {
                    Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
                        Text("Image: ${if (source.isBlank()) "(not set)" else source}", style = MaterialTheme.typography.labelMedium)
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
                .then(if (clickAction.isNotBlank() && !designerMode) Modifier.clickable { dispatch(clickAction) } else Modifier)
            when (variant) {
                "outlined" -> OutlinedCard(mod) { Column(Modifier.padding(12.dp)) { inner() } }
                "filled"   -> Card(mod)        { Column(Modifier.padding(12.dp)) { inner() } }
                else       -> ElevatedCard(mod){ Column(Modifier.padding(12.dp)) { inner() } }
            }
        }

        "Tabs" -> Wrapper {
            val tabs = block.optJSONArray("tabs") ?: JSONArray()
            var idx by remember(path) { mutableStateOf(block.optInt("initialIndex", 0).coerceAtLeast(0)) }
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
            val clickAction = block.optString("clickActionId","")
            val textColor = parseColorOrRole(block.optString("textColor",""))

            Column(
                Modifier
                    .fillMaxWidth()
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
                            label = { Text(label, style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyMedium)) },
                            leadingIcon = if (current == v) { { Icon(Icons.Filled.Check, null) } } else null
                        )
                    } else {
                        val current = (uiState[bind] as? Boolean) ?: false
                        FilterChip(
                            selected = current,
                            onClick = { uiState[bind] = !current },
                            label = { Text(label, style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyMedium)) },
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
            val align = mapTextAlign(block.optString("align","start"))
            val textColor = parseColorOrRole(block.optString("textColor",""))
            Column {
                for (i in 0 until items.length()) {
                    val it = items.optJSONObject(i) ?: continue
                    ListItem(
                        headlineContent = {
                            Text(
                                it.optString("title",""),
                                style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyLarge),
                                color = textColor ?: LocalContentColor.current,
                                textAlign = align,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        supportingContent = {
                            val sub = it.optString("subtitle","")
                            if (sub.isNotBlank()) Text(
                                sub,
                                style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyMedium),
                                color = textColor ?: LocalContentColor.current,
                                textAlign = align,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { dispatch(it.optString("actionId","")) }
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
 * GRID
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
 * DESIGNER OVERLAY (palette + controlli) – NO inspector per semplicità/robustezza
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
    onOverlayHeight: (Int) -> Unit
) {
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

                fun add(block: JSONObject) {
                    val newPath = addSmartInsert(layout, selectedPath, block)
                    setSelectedPath(newPath); onLayoutChange()
                }

                FilledTonalButton(onClick = { add(newProgress()) }) { Icon(Icons.Filled.Flag, null); Spacer(Modifier.width(6.dp)); Text("Progress") }
                FilledTonalButton(onClick = { add(newAlert())    }) { Icon(Icons.Filled.Warning, null); Spacer(Modifier.width(6.dp)); Text("Alert") }
                FilledTonalButton(onClick = { add(newImage())    }) { Icon(Icons.Filled.Image, null); Spacer(Modifier.width(6.dp)); Text("Image") }
                FilledTonalButton(onClick = { add(newSectionHeader()) }) { Icon(Icons.Filled.LibraryAdd, null); Spacer(Modifier.width(6.dp)); Text("SectionHeader") }
                FilledTonalButton(onClick = { add(newButtonRow()) }) { Icon(Icons.Filled.LibraryAdd, null); Spacer(Modifier.width(6.dp)); Text("ButtonRow") }
                FilledTonalButton(onClick = { add(newList())     }) { Icon(Icons.Filled.List, null); Spacer(Modifier.width(6.dp)); Text("List") }
                FilledTonalButton(onClick = { add(newSpacer())   }) { Icon(Icons.Filled.SpaceBar, null); Spacer(Modifier.width(6.dp)); Text("Spacer") }
                FilledTonalButton(onClick = { add(JSONObject().put("type","Divider")) }) { Icon(Icons.Filled.HorizontalRule, null); Spacer(Modifier.width(6.dp)); Text("Divider") }
                FilledTonalButton(onClick = { add(newDividerV()) }) { Icon(Icons.Filled.MoreVert, null); Spacer(Modifier.width(6.dp)); Text("DividerV") }

                FilledTonalButton(onClick = {
                    val iconPath = insertIconMenuReturnIconPath(layout, selectedPath)
                    setSelectedPath(iconPath); onLayoutChange()
                }) { Icon(Icons.Filled.MoreVert, null); Spacer(Modifier.width(6.dp)); Text("Icon+Menu") }

                FilledTonalButton(onClick = { add(newCard())     }) { Icon(Icons.Filled.Widgets, null); Spacer(Modifier.width(6.dp)); Text("Card") }
                FilledTonalButton(onClick = { add(newFab())      }) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Fab") }
                FilledTonalButton(onClick = { add(newChipRow())  }) { Icon(Icons.Filled.Palette, null); Spacer(Modifier.width(6.dp)); Text("ChipRow") }
                FilledTonalButton(onClick = { add(newSlider())   }) { Icon(Icons.Filled.Tune, null); Spacer(Modifier.width(6.dp)); Text("Slider") }
                FilledTonalButton(onClick = { add(newToggle())   }) { Icon(Icons.Filled.ToggleOn, null); Spacer(Modifier.width(6.dp)); Text("Toggle") }
                FilledTonalButton(onClick = { add(newTabs())     }) { Icon(Icons.Filled.Tab, null); Spacer(Modifier.width(6.dp)); Text("Tabs") }
                FilledTonalButton(onClick = { add(newMetricsGrid()) }) { Icon(Icons.Filled.GridOn, null); Spacer(Modifier.width(6.dp)); Text("MetricsGrid") }

                // >>> PAGE: nuova voce
                FilledTonalButton(onClick = { add(newPage())     }) { Icon(Icons.Filled.Widgets, null); Spacer(Modifier.width(6.dp)); Text("Page") }
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
                    OutlinedButton(onClick = {
                        selectedPath?.let {
                            val newPath = moveAndReturnNewPath(layout, it, -1)
                            setSelectedPath(newPath); onLayoutChange()
                        }
                    }) { Icon(Icons.Filled.KeyboardArrowUp, null); Spacer(Modifier.width(4.dp)); Text("Su") }
                    OutlinedButton(onClick = {
                        selectedPath?.let {
                            val newPath = moveAndReturnNewPath(layout, it, +1)
                            setSelectedPath(newPath); onLayoutChange()
                        }
                    }) { Icon(Icons.Filled.KeyboardArrowDown, null); Spacer(Modifier.width(4.dp)); Text("Giù") }
                    OutlinedButton(onClick = {
                        selectedPath?.let { duplicate(layout, it); onLayoutChange() }
                    }) { Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("Duplica") }
                    TextButton(
                        onClick = {
                            selectedPath?.let { remove(layout, it); setSelectedPath(null); onLayoutChange() }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(4.dp)); Text("Elimina") }
                    // Inspector volutamente non incluso per massima stabilità
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
}

/* =========================
 * HELPERS: mapping, pickers, utils
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
    "xs" -> Modifier.height(32.dp)
    "sm" -> Modifier.height(36.dp)
    "lg" -> Modifier.height(52.dp)
    "xl" -> Modifier.height(56.dp)
    else -> Modifier.height(40.dp) // md
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

/* ---- Icon helper (Material + res:) ---- */
@Composable
private fun NamedIconEx(name: String?, contentDescription: String?) {
    val ctx = LocalContext.current
    if (name?.startsWith("res:") == true) {
        val resName = name.removePrefix("res:")
        val id = ctx.resources.getIdentifier(resName, "drawable", ctx.packageName)
        if (id != 0) { Icon(painterResource(id), contentDescription); return }
    }
    val image = when (name) {
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
        "list" -> Icons.Filled.List
        "tab" -> Icons.Filled.Tab
        "grid_on" -> Icons.Filled.GridOn
        "toggle_on" -> Icons.Filled.ToggleOn
        else -> null
    }
    if (image != null) Icon(image, contentDescription) else Text(".")
}

/* ---- Color parsing (ruoli o hex) ---- */
@Composable
private fun parseColorOrRole(value: String?): Color? {
    if (value.isNullOrBlank()) return null
    val v = value.trim()
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

/* =========================
 * JSON utils
 * ========================= */

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
            is JSONArray  -> {
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

private fun removeAt(arr: JSONArray, index: Int) {
    if (index < 0 || index >= arr.length()) return
    val tmp = mutableListOf<Any?>()
    for (i in 0 until arr.length()) if (i != index) tmp.add(arr.get(i))
    while (arr.length() > 0) arr.remove(arr.length()-1)
    tmp.forEach { arr.put(it) }
}

/* ====== SMART INSERT (nidifica nei contenitori) ====== */

// Tipi considerati "contenitori" per i blocks figli
private val CONTAINER_TYPES = setOf("Page", "Card", "Tabs")

// Risale finché trova un JSONObject di tipo contenitore
private fun findAncestorContainerPath(root: JSONObject, startPath: String?): String? {
    var p = startPath ?: return null
    repeat(64) {
        val node = jsonAtPath(root, p)
        if (node is JSONObject) {
            val t = node.optString("type","")
            if (t in CONTAINER_TYPES) return p
        }
        val cut = p.lastIndexOf('/')
        if (cut <= 0) return null
        p = p.substring(0, cut)
    }
    return null
}

private fun ensureObject(parent: JSONObject, key: String): JSONObject {
    parent.optJSONObject(key)?.let { return it }
    val o = JSONObject(); parent.put(key, o); return o
}
private fun ensureArray(parent: JSONObject, key: String): JSONArray {
    parent.optJSONArray(key)?.let { return it }
    val a = JSONArray(); parent.put(key, a); return a
}

// Inserisce in obj[key] (creando l'array se serve) e ritorna il path del nuovo elemento
private fun insertIntoChildArray(
    root: JSONObject,
    objPath: String,
    key: String,
    block: JSONObject,
    position: String
): String {
    val obj = jsonAtPath(root, objPath) as? JSONObject
        ?: return insertBlockAndReturnPath(root, objPath, block, position)
    val arr = ensureArray(obj, key)
    val idx = if (position == "before") 0 else arr.length()
    val tmp = mutableListOf<Any?>()
    for (i in 0 until arr.length()) {
        if (i == idx) tmp.add(block)
        tmp.add(arr.get(i))
    }
    if (idx == arr.length()) tmp.add(block)
    while (arr.length() > 0) arr.remove(arr.length() - 1)
    tmp.forEach { arr.put(it) }
    return "$objPath/$key/$idx"
}

// Inserimento “intelligente” dentro Page/Card/Tabs (prima tab) oppure come fratello
private fun addSmartInsert(root: JSONObject, selectedPath: String?, newBlock: JSONObject): String {
    val containerPath = findAncestorContainerPath(root, selectedPath)
    val container = containerPath?.let { jsonAtPath(root, it) as? JSONObject }
    return when (container?.optString("type","")) {
        "Page", "Card" -> insertIntoChildArray(root, containerPath!!, "blocks", newBlock, "after")
        "Tabs" -> {
            val tabs = ensureArray(container, "tabs")
            val tab0 = (tabs.optJSONObject(0) ?: JSONObject().also { tabs.put(it) })
            ensureArray(tab0, "blocks")
            insertIntoChildArray(root, "$containerPath/tabs/0", "blocks", newBlock, "after")
        }
        else -> insertBlockAndReturnPath(root, selectedPath, newBlock, "after")
    }
}

/* =========================
 * BLUEPRINTS
 * ========================= */

private fun newSectionHeader() = JSONObject("""{ "type":"SectionHeader", "title":"Nuova sezione" }""")
private fun newButtonRow() = JSONObject(
    """
    {"type":"ButtonRow","align":"center",
     "buttons":[
       {"label":"Start","style":"primary","icon":"play_arrow","size":"md","tint":"default","shape":"rounded","corner":20,"pressEffect":"scale","actionId":"start_run"},
       {"label":"Pausa","style":"tonal","icon":"pause","size":"md","tint":"default","shape":"rounded","corner":20,"actionId":"pause_run"},
       {"label":"Stop","style":"outlined","icon":"stop","size":"md","tint":"error","shape":"rounded","corner":20,"actionId":"stop_run","confirm":true}
     ]
    }
    """.trimIndent()
)
private fun newSpacer()   = JSONObject("""{ "type":"Spacer", "height": 8 }""")
private fun newDividerV() = JSONObject("""{ "type":"DividerV", "thickness": 1, "height": 24 }""")
private fun newCard() = JSONObject(
    """
    {"type":"Card","variant":"elevated","clickActionId":"",
     "blocks":[
       {"type":"SectionHeader","title": "Card esempio", "style":"titleSmall", "align":"start"},
       {"type":"Divider"}
     ]
    }
    """.trimIndent()
)
private fun newFab()      = JSONObject("""{ "type":"Fab", "icon":"play_arrow", "label":"Start", "variant":"extended", "actionId":"" }""")
private fun newIconButton(menuId: String = "more_menu") = JSONObject("""{ "type":"IconButton", "icon":"more_vert", "openMenuId":"$menuId" }""")
private fun newMenu(menuId: String = "more_menu") = JSONObject(
    """
    {"type":"Menu","id":"$menuId",
     "items":[
       {"icon":"tune","label":"Layout Lab","actionId":"open_layout_lab"},
       {"icon":"palette","label":"Theme Lab","actionId":"open_theme_lab"},
       {"icon":"settings","label":"Impostazioni","actionId":"nav:settings"}
     ]
    }
    """.trimIndent()
)
private fun newProgress()   = JSONObject("""{ "type":"Progress", "label":"Avanzamento", "value": 40, "color": "primary", "showPercent": true }""")
private fun newAlert()      = JSONObject("""{ "type":"Alert", "severity":"info", "title":"Titolo avviso", "message":"Testo dell'avviso", "actionId": "" }""")
private fun newImage()      = JSONObject("""{ "type":"Image", "source":"res:ic_launcher_foreground", "heightDp": 160, "corner": 12, "contentScale":"fit" }""")
private fun newTabs()       = JSONObject("""{ "type":"Tabs", "initialIndex": 0, "tabs":[ {"label":"Tab 1","blocks":[]}, {"label":"Tab 2","blocks":[]} ] }""")
private fun newChipRow()    = JSONObject("""{ "type":"ChipRow", "chips":[ {"label":"Easy","bind":"level","value":"easy"} ] }""")
private fun newMetricsGrid()= JSONObject("""{ "type":"MetricsGrid", "columns": 2, "tiles":[ {"label":"Pace"},{"label":"Heart"} ] }""")
private fun newSlider()     = JSONObject("""{ "type":"Slider", "label":"Pace", "bind":"pace", "min":3.0, "max":7.0, "step":0.1, "unit":" min/km" }""")
private fun newToggle()     = JSONObject("""{ "type":"Toggle", "label":"Attiva opzione", "bind":"toggle_1" }""")
private fun newList()       = JSONObject("""{ "type":"List", "align":"start", "items":[ {"title":"Voce 1","subtitle":"Sottotitolo 1","actionId":""} ] }""")
/* Page (contenitore semplice) */
private fun newPage()       = JSONObject("""{ "type":"Page", "title":"", "blocks": [] }""")

/* =========================
 * TEXT STYLE OVERRIDES
 * ========================= */

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
        "sans" -> FontFamily.SansSerif
        else -> null
    }
    if (family != null) st = st.copy(fontFamily = family)

    return st
}