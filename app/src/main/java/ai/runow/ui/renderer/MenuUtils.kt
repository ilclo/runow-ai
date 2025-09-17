package ai.runow.ui.renderer

import org.json.JSONArray
import org.json.JSONObject

/**
 * Raccoglie i menu definiti nello screen in una mappa (id -> JSONArray).
 * - Cerca in layout["menus"] se presente (forma { "id": [ ... ] }).
 * - Supporta fallback: layout["menus"] come array di { "id": "...", "items":[...] }.
 * - Non lancia eccezioni: restituisce sempre una mappa (anche vuota).
 */
fun collectMenus(layout: JSONObject): Map<String, JSONArray> {
    val out = LinkedHashMap<String, JSONArray>()

    // Caso 1: oggetto { id: [...] }
    layout.optJSONObject("menus")?.let { obj ->
        obj.keys().forEach { key ->
            val arr = obj.optJSONArray(key) ?: return@forEach
            out[key] = arr
        }
        return out
    }

    // Caso 2: array di oggetti { id, items }
    layout.optJSONArray("menus")?.let { arr ->
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val id = m.optString("id", "")
            if (id.isBlank()) continue
            val items = m.optJSONArray("items") ?: JSONArray()
            out[id] = items
        }
    }

    return out
}



//  ---------------------------------------------------------------

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

