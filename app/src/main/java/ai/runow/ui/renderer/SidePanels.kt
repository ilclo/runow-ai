@file:OptIn(
    androidx.compose.animation.ExperimentalAnimationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package ai.runow.ui.renderer

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

/* ---------------------------------------------------------
 * Raccolta ID dei pannelli laterali presenti nel layout
 * --------------------------------------------------------- */
fun collectSidePanelIds(root: JSONObject): List<String> {
    val out = mutableListOf<String>()
    val arr = root.optJSONArray("sidePanels") ?: return emptyList()
    for (i in 0 until arr.length()) {
        val p = arr.optJSONObject(i) ?: continue
        val id = p.optString("id", "").trim()
        if (id.isNotEmpty()) out += id
    }
    return out
}

/* ---------------------------------------------------------
 * Overlay runtime: visualizza il pannello laterale aperto
 * action prefix supportato: "open_side_panel:<id>"
 * --------------------------------------------------------- */
@Composable
fun RenderSidePanelsOverlay(
    layout: JSONObject,
    openPanelId: String?,
    onClose: () -> Unit,
    dispatch: (String) -> Unit
) {
    if (openPanelId == null) return
    val panels = layout.optJSONArray("sidePanels") ?: return
    val panel = (0 until panels.length())
        .asSequence()
        .mapNotNull { panels.optJSONObject(it) }
        .firstOrNull { it.optString("id") == openPanelId } ?: return

    val side = panel.optString("side", "right")
    val style = panel.optString("style", "tonal") // "text" | "outlined" | "tonal" | "filled"
    val widthDp = Dp(panel.optDouble("widthDp", 300.0).toFloat())
    val heightDp = Dp(panel.optDouble("heightDp", 320.0).toFloat())
    val corner = Dp(panel.optDouble("cornerDp", 16.0).toFloat())
    val scrimAlpha = panel.optDouble("scrimAlpha", 0.45).toFloat().coerceIn(0f, 1f)
    val anim = panel.optString("anim", "slide") // "slide" | "scale"
    val blocks = panel.optJSONArray("blocks") ?: JSONArray()

    // Colori e bordo stile pannello
    val containerColor = parseColorOrRole(panel.optString("containerColor", "surface")) ?: MaterialTheme.colorScheme.surface
    val contentColor   = parseColorOrRole(panel.optString("contentColor",   "onSurface")) ?: MaterialTheme.colorScheme.onSurface
    val borderColor    = parseColorOrRole(panel.optString("borderColor",    "outline")) ?: MaterialTheme.colorScheme.outline
    val borderWidth    = Dp(panel.optDouble("borderWidthDp", if (style == "outlined") 1.0 else 0.0).toFloat())

    // Gradient opzionale
    val brush = panel.optJSONObject("gradient")?.let { g ->
        val cols = g.optJSONArray("colors")?.let { arr ->
            (0 until arr.length()).mapNotNull { parseColorOrRole(arr.optString(it)) }
        } ?: emptyList()
        if (cols.size >= 2) {
            if (g.optString("direction","vertical") == "horizontal")
                Brush.horizontalGradient(cols)
            else
                Brush.verticalGradient(cols)
        } else null
    }

    val density = LocalDensity.current
    val enterH = slideInHorizontally(animationSpec = tween(240)) { full -> if (side == "left") -full else full }
    val exitH  = slideOutHorizontally(animationSpec = tween(200)) { full -> if (side == "left") -full else full }
    val enterV = slideInVertically(animationSpec = tween(240)) { full -> if (side == "top") -full else full }
    val exitV  = slideOutVertically(animationSpec = tween(200)) { full -> if (side == "top") -full else full }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(onClick = onClose),
        contentAlignment = when (side) {
            "left"  -> Alignment.CenterStart
            "right" -> Alignment.CenterEnd
            "top"   -> Alignment.TopCenter
            else    -> Alignment.BottomCenter
        }
    ) {
        val basePanel = @Composable {
            val shape = RoundedCornerShape(corner)
            var mod = Modifier
                .then(if (side == "top" || side == "bottom") Modifier.fillMaxWidth().height(heightDp) else Modifier.width(widthDp).fillMaxHeight())
                .background(
                    color = when (style) {
                        "text"     -> Color.Transparent
                        "outlined" -> Color.Transparent
                        "tonal"    -> MaterialTheme.colorScheme.secondaryContainer
                        else       -> containerColor
                    },
                    shape = shape
                )
            if (brush != null && style != "text" && style != "outlined") {
                mod = mod.background(brush, shape)
            }
            if (style == "outlined") {
                mod = mod.then(Modifier.border(borderWidth, borderColor, shape))
            }

            Surface(
                modifier = mod.clickable(enabled = false) {}, // blocca il click-through
                shape = shape,
                color = Color.Transparent,
                contentColor = contentColor,
                tonalElevation = if (style == "tonal") 3.dp else 0.dp
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(panel.optString("title",""), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = onClose) { NamedIconEx("close", "chiudi") }
                    }
                    for (i in 0 until blocks.length()) {
                        val b = blocks.optJSONObject(i) ?: continue
                        val p = "/sidePanels/${panel.optString("id")}/blocks/$i"
                        // riuso del renderer blocchi esistente
                        RenderBlock(
                            block = b,
                            dispatch = dispatch,
                            uiState = mutableMapOf(),
                            designerMode = false,
                            path = p,
                            menus = collectMenus(layout),
                            onSelect = {}
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = true,
            enter = when {
                anim == "scale" -> scaleIn(animationSpec = tween(200)) + fadeIn(tween(200))
                side == "top" || side == "bottom" -> enterV + fadeIn(tween(180))
                else -> enterH + fadeIn(tween(180))
            },
            exit = when {
                anim == "scale" -> scaleOut(animationSpec = tween(180)) + fadeOut(tween(180))
                side == "top" || side == "bottom" -> exitV + fadeOut(tween(160))
                else -> exitH + fadeOut(tween(160))
            }
        ) {
            // Box separato per non propagare il click sullo sfondo
            Box(Modifier.clickable(enabled = false) {}) { basePanel() }
        }
    }
}

/* ---------------------------------------------------------
 * Dialog di configurazione dei pannelli laterali
 * (accessibile dal DesignerOverlay)
 * --------------------------------------------------------- */
@Composable
fun SidePanelsManagerDialog(
    layout: JSONObject,
    onChange: () -> Unit,
    onDismiss: () -> Unit
) {
    val arr = layout.optJSONArray("sidePanels") ?: JSONArray().also { layout.put("sidePanels", it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Chiudi") }
        },
        title = { Text("Pannelli laterali", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    ElevatedCard {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Panel ${p.optString("id","")}", style = MaterialTheme.typography.labelLarge)
                                Row {
                                    IconButton(onClick = { moveInArray(arr, i, -1); onChange() }) { Icon(Icons.Filled.KeyboardArrowUp, null) }
                                    IconButton(onClick = { moveInArray(arr, i, +1); onChange() }) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                                    IconButton(onClick = { removeAt(arr, i); onChange() }) { Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error) }
                                }
                            }
                            // id
                            val id = remember { mutableStateOf(p.optString("id", "panel_${System.currentTimeMillis().toString().takeLast(4)}")) }
                            OutlinedTextField(id.value, {
                                id.value = it; p.put("id", it); onChange()
                            }, label = { Text("id") })

                            // titolo
                            val title = remember { mutableStateOf(p.optString("title","")) }
                            OutlinedTextField(title.value, {
                                title.value = it; if (it.isBlank()) p.remove("title") else p.put("title", it); onChange()
                            }, label = { Text("title (opz.)") })

                            // lato
                            var side by remember { mutableStateOf(p.optString("side","right")) }
                            ExposedDropdown(
                                value = side, label = "side",
                                options = listOf("left","right","top","bottom")
                            ) { sel -> side = sel; p.put("side", sel); onChange() }

                            // stile
                            var style by remember { mutableStateOf(p.optString("style","tonal")) }
                            ExposedDropdown(
                                value = style, label = "style",
                                options = listOf("text","outlined","tonal","filled")
                            ) { sel -> style = sel; p.put("style", sel); onChange() }

                            // dimensioni
                            val w = remember { mutableStateOf(p.optDouble("widthDp", 300.0).toString()) }
                            val h = remember { mutableStateOf(p.optDouble("heightDp", 320.0).toString()) }
                            StepperField("width (dp) – per left/right", w, 10.0) { v -> p.put("widthDp", v.coerceAtLeast(160.0)); onChange() }
                            StepperField("height (dp) – per top/bottom", h, 10.0) { v -> p.put("heightDp", v.coerceAtLeast(160.0)); onChange() }

                            // colori
                            val container = remember { mutableStateOf(p.optString("containerColor","surface")) }
                            NamedColorPickerPlus(container.value, "containerColor", allowRoles = true) {
                                container.value = it; if (it.isBlank()) p.remove("containerColor") else p.put("containerColor", it); onChange()
                            }
                            val content = remember { mutableStateOf(p.optString("contentColor","onSurface")) }
                            NamedColorPickerPlus(content.value, "contentColor", allowRoles = true) {
                                content.value = it; if (it.isBlank()) p.remove("contentColor") else p.put("contentColor", it); onChange()
                            }

                            // gradient opzionale
                            var gradEnabled by remember { mutableStateOf(p.optJSONObject("gradient") != null) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = gradEnabled, onCheckedChange = {
                                    gradEnabled = it
                                    if (it) {
                                        val g = p.optJSONObject("gradient") ?: JSONObject().apply {
                                            put("colors", JSONArray().put("secondaryContainer").put("tertiaryContainer"))
                                            put("direction", "vertical")
                                        }
                                        p.put("gradient", g)
                                    } else p.remove("gradient")
                                    onChange()
                                })
                                Spacer(Modifier.width(8.dp)); Text("Gradient (opz.)")
                            }
                            p.optJSONObject("gradient")?.let { g ->
                                val colors = g.optJSONArray("colors") ?: JSONArray().also { g.put("colors", it) }
                                while (colors.length() < 2) colors.put("secondaryContainer")
                                val c1 = remember { mutableStateOf(colors.optString(0, "secondaryContainer")) }
                                val c2 = remember { mutableStateOf(colors.optString(1, "tertiaryContainer")) }
                                NamedColorPickerPlus(c1.value, "grad color 1", allowRoles = true) { pick ->
                                    c1.value = pick; colors.put(0, pick); onChange()
                                }
                                NamedColorPickerPlus(c2.value, "grad color 2", allowRoles = true) { pick ->
                                    c2.value = pick; colors.put(1, pick); onChange()
                                }
                                var dir by remember { mutableStateOf(g.optString("direction","vertical")) }
                                ExposedDropdown(value = dir, label = "direction", options = listOf("vertical","horizontal")) { sel ->
                                    dir = sel; g.put("direction", sel); onChange()
                                }
                            }

                            // bordo
                            val borderColor = remember { mutableStateOf(p.optString("borderColor","outline")) }
                            NamedColorPickerPlus(borderColor.value, "borderColor", allowRoles = true) {
                                borderColor.value = it; if (it.isBlank()) p.remove("borderColor") else p.put("borderColor", it); onChange()
                            }
                            val borderW = remember { mutableStateOf(p.optDouble("borderWidthDp", if (style=="outlined") 1.0 else 0.0).toString()) }
                            StepperField("borderWidth (dp)", borderW, 1.0) { v -> p.put("borderWidthDp", v.coerceAtLeast(0.0)); onChange() }
                            val corner = remember { mutableStateOf(p.optDouble("cornerDp",16.0).toString()) }
                            StepperField("corner (dp)", corner, 2.0) { v -> p.put("cornerDp", v.coerceAtLeast(0.0)); onChange() }

                            // animazioni
                            var anim by remember { mutableStateOf(p.optString("anim","slide")) }
                            ExposedDropdown(value = anim, label = "anim", options = listOf("slide","scale")) {
                                sel -> anim = sel; p.put("anim", sel); onChange()
                            }
                            val scrim = remember { mutableStateOf(p.optDouble("scrimAlpha",0.45).toString()) }
                            StepperField("scrimAlpha (0..1)", scrim, 0.05) { v -> p.put("scrimAlpha", v.coerceIn(0.0,1.0)); onChange() }

                            // blocchi interni (placeholder semplice)
                            if (!p.has("blocks")) p.put("blocks", JSONArray().put(JSONObject("""{"type":"SectionHeader","title":"Menu laterale"}""")))
                            Text("Contenuto: modifica i blocks in JSON (sarà gestito dal designer come per gli altri contenitori).", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                OutlinedButton(onClick = {
                    val np = JSONObject().apply {
                        put("id", "panel_${System.currentTimeMillis().toString().takeLast(4)}")
                        put("title", "Nuovo pannello")
                        put("side", "right")
                        put("style","tonal")
                        put("widthDp", 300)
                        put("cornerDp", 16)
                        put("blocks", JSONArray().put(JSONObject("""{"type":"SectionHeader","title":"Nuovo pannello"}""")))
                    }
                    arr.put(np); onChange()
                }) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Aggiungi pannello")
                }
            }
        }
    )
}

/* ---------------------------------------------------------
 * Piccolo wrapper: usa il dispatch dell'app ma intercetta
 * "open_side_panel:<id>" per aprire il pannello in locale
 * --------------------------------------------------------- */
fun wrapDispatchForSidePanels(
    openPanelSetter: (String?) -> Unit,
    appDispatch: (String) -> Unit
): (String) -> Unit = { action ->
    if (action.startsWith("open_side_panel:")) {
        openPanelSetter(action.removePrefix("open_side_panel:").trim())
    } else {
        appDispatch(action)
    }
}