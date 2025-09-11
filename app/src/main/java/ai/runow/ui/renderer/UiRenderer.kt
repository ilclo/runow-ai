
package ai.runow.ui.renderer


import ai.runow.ui.renderer.DesignerRoot
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
@@ -86,11 +88,11 @@ import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- Risorse font del modulo app ---
import ai.runow.R


// --- Composable minimi -------------------------------------------------------
private val DEFAULT_COLOR_SWATCH = listOf(
"#000000","#333333","#666666","#999999","#CCCCCC","#FFFFFF",
@@ -134,6 +136,8 @@ fun ExposedDropdown(
}
}
}


@Composable
private fun roleOrHex(s: String?): Color? {
val v = s?.trim().orEmpty()
@@ -543,4 +547,3829 @@ private fun TextInspectorPanel(working: JSONObject, onChange: () -> Unit) {
onChange()
}

    var fontFamily by remember { mutableStateOf(working.optString("fontFamily","")) }
    ExposedDropdown(
        value = if (fontFamily.isBlank()) "(default)" else fontFamily,
        label = "fontFamily",
        options = FONT_FAMILY_OPTIONS,   
    ) { sel ->
        val v = if (sel == "(default)") "" else sel
        fontFamily = v
        if (v.isBlank()) working.remove("fontFamily") else working.put("fontFamily", v)
        onChange()
    }

    val fontRes = remember { mutableStateOf(working.optString("fontRes","")) }
    OutlinedTextField(
        value = fontRes.value,
        onValueChange = { v ->
            fontRes.value = v
            if (v.isBlank()) working.remove("fontRes") else working.put("fontRes", v)
            onChange()
        },
        label = { Text("fontRes (es. res:inter_regular)") }
    )

    val textColor = remember { mutableStateOf(working.optString("textColor","")) }
    NamedColorPickerPlus(current = textColor.value, label = "textColor") { hex ->
        textColor.value = hex
        if (hex.isBlank()) working.remove("textColor") else working.put("textColor", hex)
        onChange()
    }

    // corsivo
    var italic by remember { mutableStateOf(working.optBoolean("italic", false)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = italic, onCheckedChange = {
            italic = it; working.put("italic", it); onChange()
        })
        Spacer(Modifier.width(8.dp))
        Text("Italic")
    }
}




private fun luminance(c: Color): Double {
    // sRGB luminance approx.
    fun ch(x: Double) = if (x <= 0.03928) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4)
    val r = ch(c.red.toDouble())
    val g = ch(c.green.toDouble())
    val b = ch(c.blue.toDouble())
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

@Composable
private fun AddBarItemButtons(
    arr: JSONArray,
    onChange: () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        androidx.compose.material3.Button(
            onClick = {
                arr.put(JSONObject("""{"type":"icon","icon":"more_vert","actionId":""}"""))
                onChange()
            }
        ) { androidx.compose.material3.Text("+ Icon") }

        androidx.compose.material3.OutlinedButton(
            onClick = {
                arr.put(JSONObject("""{"type":"button","label":"Azione","style":"text","actionId":""}"""))
                onChange()
            }
        ) { androidx.compose.material3.Text("+ Button") }

        androidx.compose.material3.TextButton(
            onClick = {
                arr.put(JSONObject("""{"type":"spacer","mode":"fixed","widthDp":16}"""))
                onChange()
            }
        ) { androidx.compose.material3.Text("+ Spacer") }
    }
}

internal fun collectSidePanelIds(layout: JSONObject): List<String> {
    val arr = layout.optJSONArray("sidePanels") ?: return emptyList()
    val out = mutableListOf<String>()
    for (i in 0 until arr.length()) {
        val p = arr.optJSONObject(i) ?: continue
        val id = p.optString("id").trim()
        if (id.isNotBlank()) out += id
    }
    return out
}


internal fun wrapDispatchForOverlays(
    openPanelSetter: (String?) -> Unit,
    openMenuSetter: (String?) -> Unit,
    appDispatch: (String) -> Unit
): (String) -> Unit = { actionId ->
    when {
        actionId.startsWith("sidepanel:open:") -> openPanelSetter(actionId.removePrefix("sidepanel:open:"))
        actionId == "sidepanel:close"         -> openPanelSetter(null)

        actionId.startsWith("open_menu:")     -> openMenuSetter(actionId.removePrefix("open_menu:"))
        actionId == "menu:close"              -> openMenuSetter(null)

        else -> appDispatch(actionId)
    }
}

@Composable
internal fun RenderSidePanelsOverlay(
    layout: JSONObject,
    openPanelId: String?,
    onClose: () -> Unit,
    dispatch: (String) -> Unit,
    menus: Map<String, JSONArray>,
    dimBehind: Boolean
) {
    BackHandler(enabled = true) { onClose() }

    val panels = layout.optJSONArray("sidePanels") ?: JSONArray()
    if (openPanelId == null || panels.length() == 0) return

    val panel = (0 until panels.length())
        .asSequence()
        .mapNotNull { panels.optJSONObject(it) }
        .firstOrNull { it.optString("id") == openPanelId }
        ?: return

    val side   = panel.optString("side", "left")
    val width  = panel.optDouble("widthDp", 320.0).toFloat().dp
    val height = panel.optDouble("heightDp", 0.0).toFloat().let { if (it > 0) it.dp else Dp.Unspecified }
    val items  = panel.optJSONArray("items") ?: JSONArray()

    val scrimAlpha = (if (dimBehind) panel.optDouble("scrimAlpha", 0.25) else 0.0).toFloat().coerceIn(0f,1f)
    val scrimColor = Color.Black.copy(alpha = scrimAlpha)
    val ms = panel.optInt("animMs", 240).coerceIn(120, 600)

    val enter = when (side) {
        "right" -> slideInHorizontally(animationSpec = tween(ms)) { it } + fadeIn()
        "top"   -> slideInVertically(animationSpec = tween(ms)) { -it } + fadeIn()
        else    -> slideInHorizontally(animationSpec = tween(ms)) { -it } + fadeIn()
    }
    val exit = when (side) {
        "right" -> slideOutHorizontally(animationSpec = tween(ms)) { it } + fadeOut()
        "top"   -> slideOutVertically(animationSpec = tween(ms)) { -it } + fadeOut()
        else    -> slideOutHorizontally(animationSpec = tween(ms)) { -it } + fadeOut()
    }

    Box(Modifier.fillMaxSize()) {
        if (scrimAlpha > 0f) {
            AnimatedVisibility(visible = true, enter = fadeIn(tween(ms)), exit = fadeOut(tween(ms))) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(scrimColor)
                        .clickable { onClose() }
                )
            }
        } else {
            Box(Modifier.fillMaxSize().clickable { onClose() })
        }

        val alignment = when (side) {
            "right" -> Alignment.CenterEnd
            "top"   -> Alignment.TopCenter
            else    -> Alignment.CenterStart
        }

        Box(Modifier.fillMaxSize(), contentAlignment = alignment) {
            AnimatedVisibility(visible = true, enter = enter, exit = exit) {
                Surface(
                    shape = RoundedCornerShape(panel.optDouble("corner", 16.0).toFloat().dp),
                    tonalElevation = panel.optDouble("tonalElevation", 8.0).toFloat().dp,
                    shadowElevation = panel.optDouble("shadowElevation", 8.0).toFloat().dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .then(if (side == "top") Modifier.widthIn(min = 260.dp) else Modifier.width(width))
                        .then(if (side == "top" && height != Dp.Unspecified) Modifier.height(height) else Modifier)
                ) {
                    Column(
                        Modifier
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val title = panel.optString("title", "")
                        if (title.isNotBlank()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(title, style = MaterialTheme.typography.titleMedium)
                                IconButton(onClick = onClose) { NamedIconEx("close", "Close") }
                            }
                        }
                        for (i in 0 until items.length()) {
                            val block = items.optJSONObject(i) ?: continue
                            val path = "/sidePanels/${panel.optString("id")}/items/$i"
                            RenderBlock(
                                block = block,
                                dispatch = dispatch,
                                uiState = mutableMapOf(),
                                designerMode = false,
                                path = path,
                                menus = menus,
                                onSelect = {},
                                onOpenInspector = {}
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
internal fun RenderCenterMenuOverlay(
    layout: JSONObject,
    openMenuId: String?,
    onClose: () -> Unit,
    menus: Map<String, JSONArray>,
    dispatch: (String) -> Unit
) {
    if (openMenuId == null) return
    val items = menus[openMenuId] ?: return

    val cfg = layout.optJSONObject("centerMenuOverlay") ?: JSONObject()
    val widthFrac  = cfg.optDouble("widthFraction", 0.90).toFloat().coerceIn(0.5f, 1f)
    val maxHFrac   = cfg.optDouble("maxHeightFraction", 0.70).toFloat().coerceIn(0.3f, 0.95f)
    val cornerDp   = cfg.optDouble("corner", 18.0).toFloat().dp
    val alphaBox   = cfg.optDouble("alpha", 0.88).toFloat().coerceIn(0.35f, 1f)
    val scrimAlpha = cfg.optDouble("scrimAlpha", 0.08).toFloat().coerceIn(0f, 0.35f)
    val baseColor  = parseColorOrRole(cfg.optString("containerColor", "surface"))
        ?: MaterialTheme.colorScheme.surface
    val textColor  = parseColorOrRole(cfg.optString("textColor", ""))
        ?: bestOnColor(baseColor)
    val title      = cfg.optString("title", "")

    BackHandler(enabled = true) { onClose() }

    Box(Modifier.fillMaxSize()) {
        // scrim cliccabile per chiudere
        if (scrimAlpha > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable { onClose() }
            )
        } else {
            Box(Modifier.fillMaxSize().clickable { onClose() })
        }

        Surface(
            color = baseColor.copy(alpha = alphaBox),
            contentColor = textColor,
            shape = RoundedCornerShape(cornerDp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(widthFrac)
                .fillMaxHeight(maxHFrac)
        ) {
            Column(
                Modifier
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (title.isNotBlank()) title else openMenuId,
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = onClose) { NamedIconEx("close", "Chiudi menu") }
                }

                Spacer(Modifier.height(6.dp))

                // voci del menu
                for (i in 0 until items.length()) {
                    val it = items.optJSONObject(i) ?: continue
                    val label = it.optString("label", "")
                    val icon  = it.optString("icon", "")
                    val act   = it.optString("actionId", "")

                    ListItem(
                        headlineContent = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent  = { if (icon.isNotBlank()) NamedIconEx(icon, label) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                // resta aperto solo se sto aprendo una sidebar
                                val keepOpen = act.startsWith("sidepanel:open:")
                                if (!keepOpen) onClose()
                                if (act.isNotBlank()) dispatch(act)
                            }
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                    )
                    Divider()
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}



@Composable
fun DesignerRoot() {
    val uiState = remember { mutableMapOf<String, Any>() }
    val dispatch: (String) -> Unit = { /* TODO: instrada azioni app */ }

    UiScreen(
        screenName = "home",
        dispatch = dispatch,
        uiState = uiState,
        designerMode = true,
        scaffoldPadding = PaddingValues(0.dp)
    )
}


/* =========================================================
 * RENDER DI UNA SCHERMATA JSON (con Scaffold di root e levetta)
 * ========================================================= */

@Composable
fun UiScreen(
    screenName: String,
    dispatch: (String) -> Unit,
    uiState: MutableMap<String, Any>,
    designerMode: Boolean = false,
    scaffoldPadding: PaddingValues = PaddingValues(0.dp)
) {
    val ctx = LocalContext.current
    var layout: JSONObject? by remember(screenName) {
        mutableStateOf(UiLoader.loadLayout(ctx, screenName))
    }
    var tick by remember { mutableStateOf(0) }

    if (layout == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Layout '$screenName' non trovato", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    // Menù raccolti dal layout + selezione corrente
    val menus by remember(layout, tick) { mutableStateOf(collectMenus(layout!!)) }
    var selectedPath by remember(screenName) { mutableStateOf<String?>(null) }

    // Stato barra designer in basso (per lasciare spazio ai contenuti)
    var overlayHeightPx by remember { mutableStateOf(0) }
    val overlayHeightDp = with(LocalDensity.current) { overlayHeightPx.toDp() }

    // Modalità designer persistente per schermata
    var designMode by rememberSaveable(screenName) { mutableStateOf(designerMode) }

    // ---- Live preview del root (page + topBar) mentre si edita nel RootInspector ----
    var previewRoot: JSONObject? by remember { mutableStateOf(null) }
    fun mergeForPreview(base: JSONObject, preview: JSONObject): JSONObject {
        val out = JSONObject(base.toString())
        listOf("page", "topBar").forEach { k ->
            if (preview.has(k)) out.put(k, preview.opt(k))
        }
        return out
    }
    val effectiveLayout = remember(layout, previewRoot) {
        if (previewRoot != null) mergeForPreview(layout!!, previewRoot!!) else layout!!
    }

    Box(Modifier.fillMaxSize()) {
        // ====== SFONDO PAGINA (colore/gradient/immagine) ======
        RenderPageBackground(effectiveLayout.optJSONObject("page"))

        // ====== CONTENUTO con Scaffold di ROOT ======
        RenderRootScaffold(
            layout = effectiveLayout,
            dispatch = dispatch,
            uiState = uiState,
            designerMode = designMode,
            menus = menus,
            selectedPathSetter = { selectedPath = it },
            extraPaddingBottom = if (designMode) overlayHeightDp + 32.dp else 16.dp,
            scaffoldPadding = scaffoldPadding
        )

        if (designMode) {
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
                topPadding = scaffoldPadding.calculateTopPadding(),
                onOverlayHeight = { overlayHeightPx = it },
                onOpenRootInspector = { /* gestito sotto */ },
                onRootLivePreview = { previewRoot = it }   // << live preview page/topBar
            )
        }

        // ====== LEVETTA LATERALE: DESIGNER ↔ ANTEPRIMA ======
        DesignSwitchKnob(
            isDesigner = designMode,
            onToggle = { designMode = !designMode }
        )
    }
}

/* =========================================================
 * KNOB laterale (trascinabile) per commutare Designer/Anteprima
 * ========================================================= */
@Composable
private fun BoxScope.DesignSwitchKnob(
    isDesigner: Boolean,
    onToggle: () -> Unit
) {
    var offsetY by remember { mutableStateOf(0f) }
    val maxDragPx = with(LocalDensity.current) { 220.dp.toPx() }

    FloatingActionButton(
        onClick = onToggle,
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .offset { IntOffset(0, offsetY.toInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    offsetY = (offsetY + dragAmount).coerceIn(-maxDragPx, maxDragPx)
                }
            },
        containerColor = if (isDesigner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (isDesigner) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = CircleShape
    ) {
        Icon(if (isDesigner) Icons.Filled.Build else Icons.Filled.Visibility, contentDescription = "Designer toggle")
    }
}

/* =========================================================
 * STYLED CONTAINER – wrapper unificato per stile/forma/bordo/bg
 * ========================================================= */

private enum class BorderMode { None, Full, TopBottom }

private fun borderModeOf(raw: String?): BorderMode = when (raw?.lowercase()) {
    "full" -> BorderMode.Full
    "top_bottom", "topbottom", "top-bottom", "tb", "horizontal_lines" -> BorderMode.TopBottom
    else -> BorderMode.None
}

private enum class SizeMode { Wrap, Fill, FixedDp, Fraction }

/** Immagine di sfondo opzionale del contenitore */
private data class BgImage(
    val resId: Int?,          // preferito se presente (es. "res:header_bg")
    val uriString: String?,   // alternativamente URI ("uri:", "content:", "file:")
    val scale: ContentScale,
    val alpha: Float
)

/**
 * Colori base del contenitore in base allo style + eventuale customColor.
 * NB: 'tint' è ignorato (manteniamo il parametro solo per retro-compatibilità JSON).
 