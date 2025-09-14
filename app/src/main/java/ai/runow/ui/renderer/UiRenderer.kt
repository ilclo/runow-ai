@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)


package ai.runow.ui.renderer




import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.WindowInsets as FWindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides as FSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imePadding
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.draw.*
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput // used with detectVerticalDragGestures
import androidx.compose.ui.unit.IntOffset
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ai.runow.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
// Gesti
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
// Layout/insets utili
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.imePadding
// Density e math
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt


private fun <T> MutableList<T>.swap(a: Int, b: Int) {
    if (a != b && a in indices && b in indices) {
        val t = this[a]; this[a] = this[b]; this[b] = t
    }
}

@Composable
private fun ResizableRow(
    rowBlock: JSONObject,
    path: String,
    uiState: MutableMap<String, Any>,
    menus: Map<String, JSONArray>,
    dispatch: (String) -> Unit,
    designerMode: Boolean = false,
    onSelect: (String) -> Unit,
    onOpenInspector: (String) -> Unit
) {
    val items = rowBlock.optJSONArray("items") ?: JSONArray()
    val count = items.length()
    if (count == 0) return

    val density = LocalDensity.current

    val gap = rowBlock.optDouble("gapDp", 8.0).toFloat().dp
    val sizing = rowBlock.optString("sizing", "flex") // "flex" | "fixed" | "scroll"
    val resizable = rowBlock.optBoolean("resizable", true)

    var rowWidthPx by remember(path) { mutableStateOf(0f) }
    var rowHeightPx by remember(path) { mutableStateOf(0f) }

    val weights = remember(path, count) {
        MutableList(count) { idx ->
            val it = items.optJSONObject(idx)
            when {
                it == null -> 0f
                it.optString("type") == "SpacerH" && it.optString("mode","fixed") == "fixed" -> 0f
                else -> {
                    val w = it.optDouble("weight", Double.NaN)
                    if (!w.isNaN()) w.toFloat().coerceIn(0.05f, 0.95f) else 1f / count
                }
            }
        }.toMutableStateList()
    }
    val widthsDp = remember(path, count) {
        MutableList(count) { idx ->
            val it = items.optJSONObject(idx)
            it?.optDouble("widthDp", Double.NaN)?.takeIf { d -> !d.isNaN() }?.toFloat() ?: 120f
        }.toMutableStateList()
    }
    val heightsDp = remember(path, count) {
        MutableList(count) { idx ->
            val it = items.optJSONObject(idx)
            it?.optDouble("heightDp", Double.NaN)?.takeIf { d -> !d.isNaN() }?.toFloat() ?: 0f
        }.toMutableStateList()
    }
    val unlocked = remember(path, count) { MutableList(count) { false }.toMutableStateList() }

    var activeEdge by remember(path) { mutableStateOf(-1) }

    Row(
        Modifier
            .fillMaxWidth()
            .let { if (sizing == "scroll") it.horizontalScroll(rememberScrollState()) else it }
            .onGloballyPositioned {
                rowWidthPx  = it.size.width.toFloat()
                rowHeightPx = it.size.height.toFloat()
            }
// colore delle guide (tono tenue)
            val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            
            Row(
                Modifier
                    .fillMaxWidth()
                    .let { if (sizing == "scroll") it.horizontalScroll(rememberScrollState()) else it }
                    .onGloballyPositioned { coords ->
                        rowWidthPx  = coords.size.width.toFloat()
                        rowHeightPx = coords.size.height.toFloat()
                    }
                    .drawBehind {
                        if (activeEdge >= 0 && count > 1 && rowWidthPx > 0f) {
                            var acc = 0f
                            for (j in 0 until count) {
                                val wPx = when (sizing) {
                                    "flex"  -> rowWidthPx * weights[j].coerceAtLeast(0f)
                                    "fixed" -> widthsDp[j].dp.toPx()
                                    else    -> widthsDp[j].dp.toPx()
                                }
                                acc += wPx
                                if (j < count - 1) {
                                    val thick = if (j == activeEdge) 3f else 1.5f
                                    drawLine(
                                        color = dividerColor,
                                        start = Offset(acc, 0f),
                                        end   = Offset(acc, size.height),
                                        strokeWidth = thick
                                    )
                                }
                            }
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically
            ) {
        for (i in 0 until count) {
            val child = items.optJSONObject(i) ?: continue

            val isFixedSpacer = child.optString("type") == "SpacerH" &&
                    child.optString("mode","fixed") == "fixed"
            if (isFixedSpacer) {
                Spacer(Modifier.width(child.optDouble("widthDp", 16.0).toFloat().dp))
                continue
            }

            val cellBase = when (sizing) {
                "flex"   -> Modifier.weight(weights[i].coerceAtLeast(0.0001f), fill = true)
                "fixed",
                "scroll" -> Modifier.width(widthsDp[i].dp)
                else     -> Modifier.weight(1f, fill = true)
            }.then(if (heightsDp[i] > 0f) Modifier.height(heightsDp[i].dp) else Modifier)

            Box(
                cellBase
                    .then(
                        if (resizable && !designerMode)
                            Modifier.combinedClickable(
                                onClick = {}, // no-op single tap
                                onDoubleClick = { unlocked[i] = !unlocked[i] }
                            )
                        else Modifier
                    )
            ) {
                val childPath = "$path/items/$i"
                RenderBlock(
                    block = child,
                    dispatch = dispatch,
                    uiState = uiState,
                    designerMode = designerMode,
                    path = childPath,
                    menus = menus,
                    onSelect = onSelect,
                    onOpenInspector = onOpenInspector
                )

                if (resizable && !designerMode && unlocked[i]) {
                    // Handle verticale (destra) per regolare la larghezza
                    if (i < count - 1) {
                        ResizeHandleX(
                            align = Alignment.CenterEnd,
                            onDragStart = { activeEdge = i },
                            onDrag = { deltaPx ->
                                when (sizing) {
                                    "flex" -> {
                                        val deltaW = if (rowWidthPx > 0f) deltaPx / rowWidthPx else 0f
                                        applyProportionalDelta(weights, i, deltaW, items)
                                    }
                                    "fixed" -> {
                                        val step = 8f
                                        val deltaDp = pxToDp(deltaPx, density)
                                        widthsDp[i] = snapDp(widthsDp[i] + deltaDp, step)
                                        val others = (0 until count).filter { it != i }
                                        if (others.isNotEmpty()) {
                                            val share = -deltaDp / others.size
                                            others.forEach { j ->
                                                widthsDp[j] = snapDp(
                                                    (widthsDp[j] + share).coerceAtLeast(48f), step
                                                )
                                                items.optJSONObject(j)
                                                    ?.put("widthDp", widthsDp[j].toDouble())
                                            }
                                        }
                                        child.put("widthDp", widthsDp[i].toDouble())
                                    }
                                    "scroll" -> {
                                        val step = 8f
                                        val deltaDp = pxToDp(deltaPx, density)
                                        widthsDp[i] = snapDp(widthsDp[i] + deltaDp, step)
                                        child.put("widthDp", widthsDp[i].toDouble())
                                    }
                                    else -> Unit
                                }
                            },
                            onDragEnd = { activeEdge = -1 }
                        )
                    }

                    // Handle orizzontali (su/giù) per variare l'altezza locale
                    ResizeHandleY(
                        align = Alignment.BottomCenter,
                        onDrag = { dyPx ->
                            val step = 8f
                            val base = if (heightsDp[i] > 0f) heightsDp[i] else pxToDp(rowHeightPx, density)
                            val newH = snapDp(base + pxToDp(dyPx, density), step)
                            heightsDp[i] = newH.coerceAtLeast(32f)
                            child.put("heightDp", heightsDp[i].toDouble())
                        }
                    )
                    ResizeHandleY(
                        align = Alignment.TopCenter,
                        onDrag = { dyPx ->
                            val step = 8f
                            val base = if (heightsDp[i] > 0f) heightsDp[i] else pxToDp(rowHeightPx, density)
                            val newH = snapDp(base - pxToDp(dyPx, density), step)
                            heightsDp[i] = newH.coerceAtLeast(32f)
                            child.put("heightDp", heightsDp[i].toDouble())
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ResizeHandleX(
    align: Alignment,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit = {}
) {
    val handleW = 12.dp
    Box(
        Modifier
            .align(align)
            .fillMaxHeight()
            .width(handleW)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> onDrag(delta) },
                onDragStarted = { onDragStart() },
                onDragStopped = { onDragEnd() }
            )
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    )
}

@Composable
private fun BoxScope.ResizeHandleY(
    align: Alignment,
    onDrag: (Float) -> Unit
) {
    val handleH = 10.dp
    Box(
        Modifier
            .align(align)
            .fillMaxWidth()
            .height(handleH)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> onDrag(delta) }
            )
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    )
}



private fun snapPercent5(v: Float): Float {
    val snaps = (v * 20f).roundToInt().coerceIn(1, 19)
    return snaps / 20f
}
private fun snapDp(v: Float, step: Float = 8f): Float =
    ((v / step).roundToInt() * step).coerceAtLeast(step)

private fun pxToDp(px: Float, density: Density): Float =
    with(density) { px.toDp().value }




private fun applyProportionalDelta(
    weights: MutableList<Float>,
    i: Int,
    delta: Float,
    items: JSONArray
) {
    if (weights.isEmpty()) return
    val oldWi = weights[i]
    val newWi = (oldWi + delta).coerceIn(0.05f, 0.95f)
    val applied = newWi - oldWi
    if (applied == 0f) return

    val othersIdx = (0 until weights.size).filter { it != i }
    if (othersIdx.isEmpty()) return

    val share = -applied / othersIdx.size
    othersIdx.forEach { j ->
        weights[j] = (weights[j] + share).coerceIn(0.05f, 0.95f)
    }
    weights[i] = newWi

    for (k in 0 until weights.size) weights[k] = snapPercent5(weights[k])
    val sum = weights.sum().takeIf { it > 0f } ?: 1f
    for (k in 0 until weights.size) {
        weights[k] = (weights[k] / sum).coerceIn(0.05f, 0.95f)
        items.optJSONObject(k)?.put("weight", weights[k].toDouble())
    }
}



/* “Cursori” fittizi (se vorrai usare pointer icon su Desktop li puoi implementare) */
private fun Modifier.cursorForResizeHoriz(): Modifier = this
private fun Modifier.cursorForResizeVert(): Modifier = this


@Composable
private fun ScreenScaffoldWithPinnedTopBar(
    layout: JSONObject,
    dispatch: (String) -> Unit,
    uiState: MutableMap<String, Any>,
    designerMode: Boolean,
    menus: Map<String, JSONArray>,
    selectedPathSetter: (String) -> Unit,
    extraPaddingBottom: Dp,
    scaffoldPadding: PaddingValues
) {
    val title = layout.optString("topTitle", "")
    val topActions = layout.optJSONArray("topActions") ?: JSONArray()      // legacy fallback
    val bottomBarNode  = layout.optJSONObject("bottomBar")
    val bottomBarItems = bottomBarNode?.optJSONArray("items")
    val fab   = layout.optJSONObject("fab")
    val topBarConf = layout.optJSONObject("topBar")

    // pinned/enterAlways/exitUntilCollapsed
    val topScrollBehavior =
        when (topBarConf?.optString("scroll", "none")) {
            "pinned"             -> TopAppBarDefaults.pinnedScrollBehavior()
            "enterAlways"        -> TopAppBarDefaults.enterAlwaysScrollBehavior()
            "exitUntilCollapsed" -> TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            else                 -> null
        }

    Scaffold(
        modifier = if (topScrollBehavior != null)
            Modifier.nestedScroll(topScrollBehavior.nestedScrollConnection)
        else Modifier,

        // niente riduzione del contenuto: gestiamo noi gli insets
        contentWindowInsets = FWindowInsets(0, 0, 0, 0),

        topBar = {
            if (topBarConf != null) {
                RenderTopBar(topBarConf, dispatch, topScrollBehavior)
            } else if (title.isNotBlank() || topActions.length() > 0) {
                TopAppBar(
                    title = { Text(title) },
                    actions = {
                        for (i in 0 until topActions.length()) {
                            val a = topActions.optJSONObject(i) ?: continue
                            IconButton(onClick = { dispatch(a.optString("actionId")) }) {
                                NamedIconEx(a.optString("icon", "more_vert"), null)
                            }
                        }
                    }
                )
            }
        },

        bottomBar = {
            if (bottomBarItems != null && bottomBarItems.length() > 0) {
                BottomAppBar {
                    for (i in 0 until bottomBarItems.length()) {
                        val it = bottomBarItems.optJSONObject(i) ?: continue
                        when (it.optString("type", "button")) {
                            "button" -> {
                                val act = it.optString("actionId")
                                IconButton(onClick = { if (act.isNotBlank()) dispatch(act) }) {
                                    NamedIconEx(it.optString("icon", "radio_button_unchecked"), null)
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            }
        },

        floatingActionButton = {
            if (fab != null) {
                val act = fab.optString("actionId")
                FloatingActionButton(onClick = { if (act.isNotBlank()) dispatch(act) }) {
                    NamedIconEx(fab.optString("icon", "add"), null)
                }
            }
        }
    ) { innerPadding ->

    val blocks = layout.optJSONArray("blocks") ?: JSONArray()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(scaffoldPadding)
                .padding(bottom = extraPaddingBottom)
                .windowInsetsPadding(FWindowInsets.safeDrawing.only(FSides.Horizontal))
        ) {
            for (i in 0 until blocks.length()) {
                val b = blocks.optJSONObject(i) ?: continue
                val path = "/blocks/$i"
                RenderBlock(
                    block = b,
                    dispatch = dispatch,
                    uiState = uiState,
                    designerMode = designerMode,
                    path = path,
                    menus = menus,
                    onSelect = { selectedPathSetter(it) },
                    onOpenInspector = { selectedPathSetter(it) }
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyledTopBarPinned(
cfg: JSONObject,
scrollBehavior: TopAppBarScrollBehavior
) {
val cs = MaterialTheme.colorScheme

// === DEFAULT richiesti ===
val style     = cfg.optString("style", "surface")      // default: surface
val titleStr  = cfg.optString("title", "topbar")       // default: "topbar"
val shapeName = cfg.optString("shape", "rounded")      // default: rounded
val corner    = cfg.optDouble("corner", 0.0).toFloat() // default: 0
val thick     = cfg.optDouble("thick", 2.0).toFloat()  // default: 2 dp

// Colori scuri accattivanti di default (restano sovrascrivibili da cfg)
val defaultBg = cs.surface.copy(alpha = 1f)            // dark surface del tema
val bgColor   = parseColorOrRole(cfg.optString("customColor","")) ?: defaultBg
val onColor   = bestOnColor(bgColor) ?: cs.onSurface
val borderCol = parseColorOrRole(cfg.optString("borderColor","")) ?: cs.outline

val shape = when (shapeName.lowercase()) {
"cut"  -> CutCornerShape(corner.dp)
"pill" -> RoundedCornerShape(percent = 50)
else   -> RoundedCornerShape(corner.dp)
}

// Surface come “contenitore” per supportare shape + border,
// mentre TopAppBar ha container trasparente (così non duplichiamo sfondi)
Surface(
color = bgColor,
shape = shape,
border = if (thick > 0f) BorderStroke(thick.dp, borderCol) else null,
tonalElevation = 0.dp,
shadowElevation = 0.dp
) {
TopAppBar(
title = {
Text(
text = titleStr,
color = onColor,
style = MaterialTheme.typography.titleLarge
)
},
colors = TopAppBarDefaults.topAppBarColors(
containerColor = Color.Transparent, // lo sfondo lo fornisce la Surface
titleContentColor = onColor,
navigationIconContentColor = onColor,
actionIconContentColor = onColor
),
// NB: qui puoi aggiungere navigationIcon/actions leggendo dal cfg
// senza dipendere da componenti custom (NamedIconEx ecc.)
scrollBehavior = scrollBehavior
)
}
}


// --- Composable minimi -------------------------------------------------------
private val DEFAULT_COLOR_SWATCH = listOf(
"#000000","#333333","#666666","#999999","#CCCCCC","#FFFFFF",
"#E53935","#FB8C00","#FDD835","#43A047","#1E88E5","#8E24AA"
)

// ---------------------------------------------------------------------
// LabeledField
// ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExposedDropdown(
value: String,
label: String,
options: List<String>,
modifier: Modifier = Modifier,
onSelect: (String) -> Unit
) {
var expanded by remember { mutableStateOf(false) }
var internal by remember { mutableStateOf(value) }

Column(modifier) {
Text(label, style = MaterialTheme.typography.labelMedium)
ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
OutlinedTextField(
value = internal,
onValueChange = {},
readOnly = true,
trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
modifier = Modifier.menuAnchor().fillMaxWidth()
)
ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
options.forEach { opt ->
DropdownMenuItem(
text = { Text(opt) },
onClick = {
internal = opt
expanded = false
onSelect(opt)
}
)
}
}
}
}
}


@Composable
fun LabeledField(
label: String,
modifier: Modifier = Modifier,
content: @Composable () -> Unit
) {
Column(modifier) {
Text(label, style = MaterialTheme.typography.labelMedium)
Spacer(Modifier.height(6.dp))
content()
}
}


// ---------------------------------------------------------------------
// SegmentedButtons  (UNICA versione: selected / onSelect)
// ---------------------------------------------------------------------
@Composable
fun SegmentedButtons(
options: List<String>,
selected: String,
modifier: Modifier = Modifier,               // <— prima
onSelect: (String) -> Unit                   // <— ultimo
) {
Row(
modifier = modifier,
horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
options.forEach { opt ->
FilterChip(
selected = opt == selected,
onClick = { onSelect(opt) },
label = { Text(opt) }
)
}
}
}


// ---------------------------------------------------------------------
// ColorRow  — due overload: a) Color, b) hex stringhe
// ---------------------------------------------------------------------
@Composable
fun ColorRow(
current: String,
modifier: Modifier = Modifier,
onPick: (String) -> Unit
) {
val swatches = listOf(
"#000000", "#333333", "#666666", "#999999", "#CCCCCC", "#FFFFFF",
"#E53935", "#8E24AA", "#3949AB", "#1E88E5", "#039BE5", "#00897B",
"#43A047", "#7CB342", "#FDD835", "#FB8C00"
)
Row(modifier.horizontalScroll(rememberScrollState())) {
swatches.forEach { hex ->
val c = colorFromHex(hex) ?: Color.Black
val sel = current.equals(hex, ignoreCase = true)
Box(
Modifier
.size(28.dp)
.padding(2.dp)
.clip(RoundedCornerShape(6.dp))
.background(c)
.border(
2.dp,
if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
RoundedCornerShape(6.dp)
)
.clickable { onPick(hex) }
)
Spacer(Modifier.width(6.dp))
}
}
}



private fun Color.toHex(withAlpha: Boolean = false): String {
val a = (alpha * 255).toInt().coerceIn(0, 255)
val r = (red   * 255).toInt().coerceIn(0, 255)
val g = (green * 255).toInt().coerceIn(0, 255)
val b = (blue  * 255).toInt().coerceIn(0, 255)
return if (withAlpha) String.format("#%02X%02X%02X%02X", a, r, g, b)
else            String.format("#%02X%02X%02X", r, g, b)
}


private fun colorFromHex(hex: String?): Color? {
if (hex.isNullOrBlank()) return null
return try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { null }
}



// Helpers colore
private fun parseHexColorOrNull(s: String): Color? {
val hex = s.trim().removePrefix("#")
return try {
when (hex.length) {
6 -> {
val r = hex.substring(0, 2).toInt(16)
val g = hex.substring(2, 4).toInt(16)
val b = hex.substring(4, 6).toInt(16)
Color(r, g, b)
}
8 -> {
val a = hex.substring(0, 2).toInt(16)
val r = hex.substring(2, 4).toInt(16)
val g = hex.substring(4, 6).toInt(16)
val b = hex.substring(6, 8).toInt(16)
Color(r, g, b, a)
}
else -> null
}
} catch (_: Throwable) { null }
}

private fun fontFamilyFor(name: String?): FontFamily? =
when (name?.lowercase()?.trim()) {
null, "", "(default)", "default" -> null
"sans", "sansserif", "inter", "roboto", "urbanist", "poppins",
"manrope", "mulish", "rubik", "space_grotesk", "ibm_plex_sans" -> FontFamily.SansSerif
"serif", "ibm_plex_serif", "noto_serif", "inknut_antiqua", "playfair" -> FontFamily.Serif
"mono", "monospace", "ibm_plex_mono", "jetbrains_mono", "space_mono" -> FontFamily.Monospace
"cursive", "sacramento" -> FontFamily.Cursive
else -> null
}

private val FONT_FAMILY_OPTIONS: List<String> = listOf(
"(default)",
"inter", "poppins", "rubik", "manrope", "mulish", "urbanist",
"space_grotesk", "ibm_plex_sans", "ibm_plex_mono", "jetbrains_mono",
"inknut_antiqua", "playfair", "sacramento", "space_mono"
)

private fun fontFamilyFromName(name: String?): FontFamily? {
if (name.isNullOrBlank() || name == "(default)") return null
return when (name) {
"inter" -> FontFamily(
Font(R.font.inter_regular, FontWeight.Normal, FontStyle.Normal),
Font(R.font.inter_medium,  FontWeight.Medium, FontStyle.Normal),
Font(R.font.inter_semibold,FontWeight.SemiBold, FontStyle.Normal),
Font(R.font.inter_bold,    FontWeight.Bold,   FontStyle.Normal),
Font(R.font.inter_italic,  FontWeight.Normal, FontStyle.Italic)
)
"poppins" -> FontFamily(
Font(R.font.poppins_regular, FontWeight.Normal),
Font(R.font.poppins_medium,  FontWeight.Medium),
Font(R.font.poppins_semibold,FontWeight.SemiBold),
Font(R.font.poppins_bold,    FontWeight.Bold),
Font(R.font.poppins_italic,  FontWeight.Normal, FontStyle.Italic)
)
"rubik" -> FontFamily(
Font(R.font.rubik_regular, FontWeight.Normal),
Font(R.font.rubik_medium,  FontWeight.Medium),
Font(R.font.rubik_semibold,FontWeight.SemiBold),
Font(R.font.rubik_bold,    FontWeight.Bold),
Font(R.font.rubik_italic,  FontWeight.Normal, FontStyle.Italic)
)
"manrope" -> FontFamily(
Font(R.font.manrope_regular, FontWeight.Normal),
Font(R.font.manrope_medium,  FontWeight.Medium),
Font(R.font.manrope_semibold,FontWeight.SemiBold),
Font(R.font.manrope_bold,    FontWeight.Bold)
)
"mulish" -> FontFamily(
Font(R.font.mulish_regular, FontWeight.Normal),
Font(R.font.mulish_medium,  FontWeight.Medium),
Font(R.font.mulish_semibold,FontWeight.SemiBold),
Font(R.font.mulish_bold,    FontWeight.Bold),
Font(R.font.mulish_italic,  FontWeight.Normal, FontStyle.Italic)
)
"urbanist" -> FontFamily(
Font(R.font.urbanist_regular, FontWeight.Normal),
Font(R.font.urbanist_medium,  FontWeight.Medium),
Font(R.font.urbanist_semibold,FontWeight.SemiBold),
Font(R.font.urbanist_bold,    FontWeight.Bold),
Font(R.font.urbanist_italic,  FontWeight.Normal, FontStyle.Italic)
)
"space_grotesk" -> FontFamily(
Font(R.font.space_grotesk_regular, FontWeight.Normal),
Font(R.font.space_grotesk_medium,  FontWeight.Medium),
Font(R.font.space_grotesk_semibold,FontWeight.SemiBold),
Font(R.font.space_grotesk_bold,    FontWeight.Bold)
)
"ibm_plex_sans" -> FontFamily(
Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
Font(R.font.ibm_plex_sans_medium,  FontWeight.Medium),
Font(R.font.ibm_plex_sans_semibold,FontWeight.SemiBold),
Font(R.font.ibm_plex_sans_bold,    FontWeight.Bold),
Font(R.font.ibm_plex_sans_italic,  FontWeight.Normal, FontStyle.Italic)
)
"ibm_plex_mono" -> FontFamily(
Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
Font(R.font.ibm_plex_mono_medium,  FontWeight.Medium),
Font(R.font.ibm_plex_mono_bold,    FontWeight.Bold),
Font(R.font.ibm_plex_mono_italic,  FontWeight.Normal, FontStyle.Italic)
)
"jetbrains_mono" -> FontFamily(
Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
Font(R.font.jetbrains_mono_medium,  FontWeight.Medium),
Font(R.font.jetbrains_mono_bold,    FontWeight.Bold),
Font(R.font.jetbrains_mono_italic,  FontWeight.Normal, FontStyle.Italic)
)
"inknut_antiqua" -> FontFamily(
Font(R.font.inknut_antiqua_regular, FontWeight.Normal),
Font(R.font.inknut_antiqua_medium,  FontWeight.Medium),
Font(R.font.inknut_antiqua_semibold, FontWeight.SemiBold),
Font(R.font.inknut_antiqua_bold,    FontWeight.Bold)
)

"playfair" -> FontFamily(
Font(R.font.playfair_regular,        FontWeight.Normal),
// se presenti:
Font(R.font.playfair_italic,         FontWeight.Normal, FontStyle.Italic),
Font(R.font.playfair_semibold,       FontWeight.SemiBold),
Font(R.font.playfair_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
Font(R.font.playfair_bold,           FontWeight.Bold),
Font(R.font.playfair_bold_italic,    FontWeight.Bold, FontStyle.Italic)
)

"sacramento" -> FontFamily(
// normalmente ha solo Regular
Font(R.font.sacramento_regular, FontWeight.Normal)
)

"space_mono" -> FontFamily(
Font(R.font.space_mono_regular,     FontWeight.Normal),
Font(R.font.space_mono_italic,      FontWeight.Normal, FontStyle.Italic),
Font(R.font.space_mono_bold,        FontWeight.Bold),
Font(R.font.space_mono_bold_italic, FontWeight.Bold,   FontStyle.Italic)
)

else -> null
}
}

private val FONT_WEIGHT_OPTIONS: List<Pair<String, FontWeight>> = listOf(
"Thin" to FontWeight.Thin,
"ExtraLight" to FontWeight.ExtraLight,
"Light" to FontWeight.Light,
"Normal" to FontWeight.Normal,
"Medium" to FontWeight.Medium,
"SemiBold" to FontWeight.SemiBold,
"Bold" to FontWeight.Bold,
"ExtraBold" to FontWeight.ExtraBold,
"Black" to FontWeight.Black
)

private fun labelOfWeight(w: FontWeight): String =
FONT_WEIGHT_OPTIONS.firstOrNull { it.second == w }?.first ?: "Normal"



@Composable
fun SimpleTextInspectorPanel(
working: org.json.JSONObject,
onChange: () -> Unit
) {
// ALIGN
LabeledField("Allineamento") {
val alignOptions = listOf("start","center","end")
var sel by remember { mutableStateOf(working.optString("align","start")) }
SegmentedButtons(alignOptions, sel, onSelect = {
sel = it
working.put("align", it)
onChange()
}, modifier = Modifier.fillMaxWidth())
}

Spacer(Modifier.height(8.dp))

// SIZE (sp) — elenco rapido
LabeledField("Dimensione testo (sp)") {
val sizes = listOf("(default)","12","13","14","16","18","20","22","24","28","32","36")
val cur = working.optDouble("textSizeSp", Double.NaN)
val value = if (cur.isNaN()) "(default)" else cur.toInt().toString()
ExposedDropdown(
value = value,
label = "textSizeSp",
options = sizes
) { pick ->
if (pick == "(default)") working.remove("textSizeSp")
else working.put("textSizeSp", pick.toDouble())
onChange()
}
}

Spacer(Modifier.height(8.dp))

// WEIGHT
LabeledField("Peso (fontWeight)") {
val weights = listOf("(default)","w300","w400","w500","w600","w700","w800","w900")
val cur = working.optString("fontWeight","")
val v = if (cur.isBlank()) "(default)" else cur
ExposedDropdown(
value = v,
label = "fontWeight",
options = weights
) { pick ->
if (pick == "(default)") working.remove("fontWeight")
else working.put("fontWeight", pick)
onChange()
}
}

Spacer(Modifier.height(8.dp))

// FONT FAMILY (extended)
LabeledField("Font") {
val cur = working.optString("fontFamily","")
val v = if (cur.isBlank()) "(default)" else cur
ExposedDropdown(
value = v,
label = "fontFamily",
options = FONT_FAMILY_OPTIONS
) { pick ->
if (pick == "(default)") working.remove("fontFamily")
else working.put("fontFamily", pick)
onChange()
}
}

Spacer(Modifier.height(8.dp))

// TEXT COLOR
LabeledField("Colore testo") {
val current = working.optString("textColor","")
ColorRow(current = current) { picked ->
if (picked.isBlank()) working.remove("textColor")
else working.put("textColor", picked)
onChange()
}
}
}

private fun parseFontWeight(v: String?): FontWeight? = when (v?.lowercase()) {
"w100" -> FontWeight.W100
"w200" -> FontWeight.W200
"w300" -> FontWeight.W300
"w400" -> FontWeight.W400
"w500" -> FontWeight.W500
"w600" -> FontWeight.W600
"w700" -> FontWeight.W700
"w800" -> FontWeight.W800
"w900" -> FontWeight.W900
else   -> null
}

@Composable
private fun colorsFallback() = MaterialTheme.colorScheme




@Composable
private fun TextInspectorPanel(working: JSONObject, onChange: () -> Unit) {
Text("Text – Proprietà", style = MaterialTheme.typography.titleMedium)

val text = remember { mutableStateOf(working.optString("text","")) }
OutlinedTextField(
value = text.value,
onValueChange = { text.value = it; working.put("text", it); onChange() },
label = { Text("Contenuto") }
)

var align by remember { mutableStateOf(working.optString("align","start")) }
ExposedDropdown(
value = align, label = "align",
options = listOf("start","center","end")
) { sel -> align = sel; working.writeAlign(sel); onChange() }

val textSize = remember {
mutableStateOf(working.optDouble("textSizeSp", Double.NaN).let { if (it.isNaN()) "" else it.toString() })
}
ExposedDropdown(
value = if (textSize.value.isBlank()) "(default)" else textSize.value,
label = "textSize (sp)",
options = listOf("(default)","8","9","10","11","12","14","16","18","20","22","24","28","32","36")
) { sel ->
val v = if (sel == "(default)") "" else sel
textSize.value = v
if (v.isBlank()) working.remove("textSizeSp") else working.put("textSizeSp", v.toDouble())
onChange()
}

var fontWeight by remember { mutableStateOf(working.optString("fontWeight","")) }
ExposedDropdown(
value = if (fontWeight.isBlank()) "(default)" else fontWeight,
label = "fontWeight",
options = listOf("(default)","w300","w400","w500","w600","w700")
) { sel ->
val v = if (sel == "(default)") "" else sel
fontWeight = v
if (v.isBlank()) working.remove("fontWeight") else working.put("fontWeight", v)
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
private fun RenderRootScaffold(
    layout: JSONObject,
    dispatch: (String) -> Unit,
    uiState: MutableMap<String, Any>,
    designerMode: Boolean,
    menus: Map<String, JSONArray>,
    selectedPathSetter: (String) -> Unit,
    extraPaddingBottom: Dp,
    scaffoldPadding: PaddingValues
) {
    ScreenScaffoldWithPinnedTopBar(
        layout = layout,
        dispatch = dispatch,
        uiState = uiState,
        designerMode = designerMode,
        menus = menus,
        selectedPathSetter = selectedPathSetter,
        extraPaddingBottom = extraPaddingBottom,
        scaffoldPadding = scaffoldPadding
    )
}

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
val menus = remember(layout, tick) { collectMenus(layout!!) }
var selectedPath by remember(screenName) { mutableStateOf<String?>(null) }

// Stato barra designer in basso (per lasciare spazio ai contenuti)
var overlayHeightPx by remember { mutableStateOf(0) }
val overlayHeightDp = with(LocalDensity.current) { overlayHeightPx.toDp() }

// Modalità designer persistente per schermata
var designMode by rememberSaveable(screenName) { mutableStateOf(designerMode) }

// ---- Live preview del root (page + topBar) mentre si edita nel RootInspector ----
var previewRoot: JSONObject? by remember { mutableStateOf<JSONObject?>(null) }
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

// ... dentro UiScreen, dopo aver calcolato 'effectiveLayout'
Box(Modifier.fillMaxSize()) {
    // ====== SFONDO PAGINA ======
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
            onPublish = {
                UiLoader.saveDraft(ctx, screenName, layout!!)
                UiLoader.publish(ctx, screenName)
            },
            onReset = {
                UiLoader.resetPublished(ctx, screenName)
                layout = UiLoader.loadLayout(ctx, screenName)
                selectedPath = null
                tick++
            },
            topPadding = scaffoldPadding.calculateTopPadding(),
            onOverlayHeight = { overlayHeightPx = it },
            onOpenRootInspector = { /* gestito sotto */ },
            onRootLivePreview = { previewRoot = it }
        )
    }

    // ====== LEVETTA LATERALE: DESIGNER ↔ ANTEPRIMA ======
    DesignSwitchKnob(
        isDesigner = designMode,
        onToggle = { designMode = !designMode }
    )
} // <- chiude Box
} // <- chiude UiScreen


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
*/
private data class ContainerColors(
val background: Color,
val onBackground: Color,
val defaultBorder: Color
)

@Composable
private fun mapContainerColors(style: String, cfg: JSONObject?): ContainerColors {
val surface = MaterialTheme.colorScheme.surface
val onSurface = MaterialTheme.colorScheme.onSurface
val outline = MaterialTheme.colorScheme.outline

// Color di base impostabile dall’utente (palette/ruoli)
val userBg = parseColorOrRole(cfg?.optString("color", "") ?: "")

val background = when (style.lowercase()) {
"text", "outlined" -> Color.Transparent // SOLO testo o SOLO bordo
else -> userBg ?: surface
}
val onBackground = if (userBg != null) bestOnColor(userBg) else onSurface
val defaultBorder = outline

return ContainerColors(background, onBackground, defaultBorder)
}

// ====================== CONTENITORE UNIFICATO RUNTIME ======================
private data class ResolvedContainer(
val shape: CornerBasedShape,
val bgBrush: Brush?,             // null -> trasparente
val bgAlpha: Float,
val border: BorderStroke?,       // null -> nessun bordo
val elevation: Dp,               // ombra
val widthMode: String,
val heightMode: String,
val widthDp: Dp,
val heightDp: Dp,
val widthFraction: Float?,
val contentColor: Color,
val borderMode: String
)

@Composable
private fun resolveContainer(cfg: JSONObject?): ResolvedContainer {
val cs = colorsFallback()   // palette m3
val style = cfg?.optString("style", "surface") ?: "surface"  // text | outlined | tonal | primary | surface
val shapeName = cfg?.optString("shape", "rounded") ?: "rounded"
val corner = cfg?.optDouble("corner", 12.0)?.toFloat() ?: 12f
val elevationDp = cfg?.optDouble("elevationDp", if (style in listOf("surface","primary","tonal")) 1.0 else 0.0)?.toFloat() ?: 0f

val shape: CornerBasedShape = when (shapeName) {
"pill"    -> RoundedCornerShape(percent = 50)
"cut"     -> CutCornerShape(corner.dp)
else      -> RoundedCornerShape(corner.dp)
}

val baseBg = when (style) {
"text"     -> Color.Transparent
"outlined" -> Color.Transparent
"topbottom"-> Color.Transparent
"tonal"    -> cs.surfaceVariant
"primary"  -> cs.primary
"full"     -> cs.surface
else       -> cs.surface
}

// eventuale customColor (hex o ruolo) – per ruoli null => fallback a baseBg
val customColor = parseColorOrRole(cfg?.optString("customColor",""))
val bgColor = customColor ?: baseBg

// gradiente (grad2 opzionale, default a singolo colore)
val g1 = parseColorOrRole(cfg?.optString("gradient1","")) ?: bgColor
val g2 = parseColorOrRole(cfg?.optString("gradient2",""))
val orientation = cfg?.optString("gradientOrientation","horizontal") ?: "horizontal"
val bgBrush: Brush? = if (style == "text") {
null
} else if (g2 != null) {
if (orientation == "vertical") Brush.linearGradient(listOf(g1, g2), start = Offset(0f,0f), end = Offset(0f,1000f))
else Brush.linearGradient(listOf(g1, g2), start = Offset(0f,0f), end = Offset(1000f,0f))
} else {
SolidColor(g1)
}

val bgAlpha = cfg?.optDouble("bgAlpha", 1.0)?.toFloat()?.coerceIn(0f,1f) ?: 1f

// border
val borderMode = cfg?.optString("borderMode", if (style=="outlined") "full" else "none") ?: "none"
val borderThickness = cfg?.optDouble("borderThicknessDp", if (borderMode!="none") 1.0 else 0.0)?.toFloat() ?: 0f
val borderColor = parseColorOrRole(cfg?.optString("borderColor","")) ?: Color.Black
val border: BorderStroke? = if (style == "text" || borderMode == "none" || borderThickness <= 0f) null
else BorderStroke(borderThickness.dp, borderColor)

// content color
val contentColor = if (style == "text") LocalContentColor.current
else if (customColor != null || g2 != null) bestOnColor(g1)
else when (style) {
"primary" -> cs.onPrimary
"tonal"   -> cs.onSurface
else      -> cs.onSurface
}

// dimensioni
val widthMode = cfg?.optString("widthMode","wrap") ?: "wrap"     // wrap | fill | fixed_dp | fraction
val heightMode = cfg?.optString("heightMode","wrap") ?: "wrap"   // wrap | fixed_dp
val widthDp = (cfg?.optDouble("widthDp", 160.0) ?: 160.0).toFloat().dp  // default 160 come richiesto
val heightDp = (cfg?.optDouble("heightDp", 48.0) ?: 48.0).toFloat().dp
val widthFraction = cfg?.optDouble("widthFraction", Double.NaN)?.takeIf { !it.isNaN() }?.toFloat()

return ResolvedContainer(
shape = shape,
bgBrush = if (style == "outlined") null else bgBrush,
bgAlpha = if (style == "text") 0f else bgAlpha,
border = border,
elevation = elevationDp.dp,
widthMode = widthMode,
heightMode = heightMode,
widthDp = widthDp,
heightDp = heightDp,
widthFraction = widthFraction,
contentColor = contentColor,
borderMode = borderMode
)
}


private fun Modifier.topBottomBorder(width: Dp, color: Color) = this.then(
Modifier.drawBehind {
val w = width.toPx()
// top
drawLine(color, start = Offset(0f, 0f + w/2f), end = Offset(size.width, 0f + w/2f), strokeWidth = w)
// bottom
drawLine(color, start = Offset(0f, size.height - w/2f), end = Offset(size.width, size.height - w/2f), strokeWidth = w)
}
)

@Composable
fun StyledContainer(
cfg: JSONObject,
modifier: Modifier = Modifier,
contentPadding: PaddingValues? = null,
content: @Composable BoxScope.() -> Unit
) {
// === Stile e shape ===
val style = cfg.optString("style", "full").lowercase()
val shapeName = cfg.optString("shape", "rounded")
val corner = cfg.optDouble("corner", 12.0).toFloat()
val cs = MaterialTheme.colorScheme

val shape = when (shapeName) {
"cut"       -> CutCornerShape(corner.dp)
"pill"      -> RoundedCornerShape(percent = 50)
"topBottom" -> RoundedCornerShape(0.dp)
else        -> RoundedCornerShape(corner.dp)
}

// === Dimensioni ===
val widthMode = cfg.optString("widthMode", "wrap")             // wrap | fill | fixed_dp | fraction
val heightMode = cfg.optString("heightMode", "wrap")           // wrap | fixed_dp
val widthDp = cfg.optDouble("widthDp", 160.0).toFloat().dp
val heightDp = cfg.optDouble("heightDp", 0.0).toFloat().dp
val widthFraction = cfg.optDouble("widthFraction", Double.NaN)
.let { if (it.isNaN()) null else it.toFloat().coerceIn(0f, 1f) }

// Outer wrapper per ancoraggio di crescita (sx/centro/dx) rispetto al parent
val outerAlignment = when (cfg.optString("hAlign", "start")) {
"center" -> Alignment.Center
"end"    -> Alignment.CenterEnd
else     -> Alignment.CenterStart
}
// Se la larghezza è controllata (fill/fraction/fixed) ancoriamo sul parent full width,
// se è wrap lasciamo intatto il modifier passato.
val outerMod = if (widthMode == "wrap") modifier else modifier.fillMaxWidth()

// Modificatore dimensionale applicato al contenitore vero e proprio
val sizeMod =
when (widthMode) {
"fill"      -> Modifier.fillMaxWidth()
"fixed_dp"  -> Modifier.width(widthDp)
"fraction"  -> Modifier.fillMaxWidth(widthFraction ?: 1f)
else        -> Modifier
}.then(
when (heightMode) {
"fixed_dp", "fixed" -> Modifier.height(heightDp)
else                -> Modifier
}
)

// === Colori / Gradiente ===
val customColor = parseColorOrRole(cfg.optString("customColor", ""))
val bgAlpha = cfg.optDouble("bgAlpha", 1.0).toFloat().coerceIn(0f, 1f)

// Gradiente (nuovo oggetto) + legacy color1/color2 con fix di color2
val gradObj = cfg.optJSONObject("gradient")
val gradBrush: Brush? = gradObj?.let { g ->
val arr = g.optJSONArray("colors")
val cols = (0 until (arr?.length() ?: 0))
.mapNotNull { i -> parseColorOrRole(arr!!.optString(i)) }
if (cols.size >= 2) {
if (g.optString("direction", "vertical") == "horizontal")
Brush.horizontalGradient(cols)
else
Brush.verticalGradient(cols)
} else null
} ?: run {
// Legacy: color1 / color2 – ora supportano sia ruoli che HEX (fix)
val c1s = cfg.optString("color1", "")
val c2s = cfg.optString("color2", "")
val a = c1s.takeIf { it.isNotBlank() }?.let { parseColorOrRole(it) }
?: c1s.takeIf { it.isNotBlank() }?.let {
runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
}
val b = c2s.takeIf { it.isNotBlank() }?.let { parseColorOrRole(it) }
?: c2s.takeIf { it.isNotBlank() }?.let {
runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
}
if (a != null && b != null) {
if (cfg.optString("gradientDirection", "horizontal") == "vertical")
Brush.verticalGradient(listOf(a, b))
else
Brush.horizontalGradient(listOf(a, b))
} else null
}

val baseBgColor = customColor ?: when (style) {
"primary" -> cs.primary
"tonal"   -> cs.surfaceVariant
else      -> cs.surface
}
val transparentBg = cfg.optBoolean("transparentBg", false)

// === Immagine di sfondo ===
val imageCfg = cfg.optJSONObject("image")
val imageSrc = imageCfg?.optString("source", "").orEmpty()
val imageScale = when (imageCfg?.optString("contentScale","fill")) {
"fit"  -> ContentScale.Fit
"crop" -> ContentScale.Crop
else   -> ContentScale.FillBounds
}
val imageAlpha = imageCfg?.optDouble("alpha", 1.0)?.toFloat()?.coerceIn(0f,1f) ?: 1f
val ctx = LocalContext.current
val resId = if (imageSrc.startsWith("res:"))
ctx.resources.getIdentifier(imageSrc.removePrefix("res:"), "drawable", ctx.packageName)
else 0
val uriStr = when {
imageSrc.startsWith("uri:")     -> imageSrc.removePrefix("uri:")
imageSrc.startsWith("content:") -> imageSrc
imageSrc.startsWith("file:")    -> imageSrc
else -> null
}
val bmp = if (uriStr != null) rememberImageBitmapFromUri(uriStr) else null

// === Bordi & separatori ===
val thick = when {
cfg.has("thick")               -> cfg.optDouble("thick", 0.0).toFloat()
cfg.has("borderThicknessDp")   -> cfg.optDouble("borderThicknessDp", 0.0).toFloat()
style == "outlined" || style == "topbottom" -> 1f
else -> 0f
}.coerceAtLeast(0f)
val borderColor = parseColorOrRole(cfg.optString("borderColor","")) ?: cs.outline
val drawSeparators = style == "topbottom" && thick > 0f
val showFill = (style in listOf("full","surface","primary","tonal")) && !transparentBg
val showBorder = (style == "outlined" && thick > 0f) ||
((style in listOf("full","surface","primary","tonal")) && thick > 0f)

// === Elevazione ===
val elevationDp = cfg.optDouble("elevationDp", if (showFill) 1.0 else 0.0).toFloat().coerceAtLeast(0f)

// === Modificatori finali del contenitore ===
val base = if (elevationDp > 0f) sizeMod.shadow(elevationDp.dp, shape, clip = false) else sizeMod
val withSeparators = if (drawSeparators) base.topBottomBorder(thick.dp, borderColor) else base
val withBorder = if (showBorder && !drawSeparators) withSeparators.border(BorderStroke(thick.dp, borderColor), shape) else withSeparators
val clipped = withBorder.clip(shape)

// === Allineamenti interni del contenuto ===
val hAlign = cfg.optString("hAlign", "start")
val vAlign = cfg.optString("vAlign", "center")
val innerAlignment = when (hAlign) {
"center" -> when (vAlign) {
"top"    -> Alignment.TopCenter
"bottom" -> Alignment.BottomCenter
else     -> Alignment.Center
}
"end" -> when (vAlign) {
"top"    -> Alignment.TopEnd
"bottom" -> Alignment.BottomEnd
else     -> Alignment.CenterEnd
}
else -> when (vAlign) {
"top"    -> Alignment.TopStart
"bottom" -> Alignment.BottomStart
else     -> Alignment.CenterStart
}
}

// Se widthMode è wrap NON forziamo l'inner a riempire la larghezza: si adatta al contenuto.
val innerPad = contentPadding?.let { Modifier.padding(it) } ?: Modifier
val contentContainerMod = if (widthMode == "wrap") innerPad else innerPad.fillMaxWidth()

// === Render ===
Box(modifier = outerMod, contentAlignment = outerAlignment) {
Box(modifier = clipped) {
// Layer di background (solo se non "text")
if (style != "text") {
if (showFill && gradBrush == null) {
Box(Modifier.matchParentSize().background(baseBgColor.copy(alpha = bgAlpha)))
}
if (imageSrc.isNotBlank()) {
when {
resId != 0 -> Image(
painter = painterResource(resId),
contentDescription = null,
contentScale = imageScale,
modifier = Modifier.matchParentSize(),
alpha = imageAlpha
)
bmp != null -> Image(
bitmap = bmp,
contentDescription = null,
contentScale = imageScale,
modifier = Modifier.matchParentSize(),
alpha = imageAlpha
)
}
}
if (showFill && gradBrush != null) {
Box(Modifier.matchParentSize().background(gradBrush).alpha(bgAlpha))
}
}

// Contenuto (allineabile)
Box(modifier = contentContainerMod, contentAlignment = Alignment.Center) {
Box(modifier = Modifier.align(innerAlignment)) {
content()
}
}
}
}
}





/* =========================================================
* PAGE BACKGROUND (colore/gradient/immagine a tutta pagina)
* ========================================================= */
@Composable
private fun BoxScope.RenderPageBackground(cfg: JSONObject?) {
val color = parseColorOrRole(cfg?.optString("color","")) ?: MaterialTheme.colorScheme.background
val brush = cfg?.optJSONObject("gradient")?.let { g ->
val arr = g.optJSONArray("colors")
val cols = (0 until (arr?.length() ?: 0)).mapNotNull { i -> parseColorOrRole(arr!!.optString(i)) }
if (cols.size >= 2) {
if (g.optString("direction","vertical") == "horizontal") Brush.horizontalGradient(cols)
else Brush.verticalGradient(cols)
} else null
}
val img = cfg?.optJSONObject("image")?.let { j ->
val src = j.optString("source","")
val scale = when (j.optString("contentScale","fill")) {
"fit" -> ContentScale.Fit
"crop" -> ContentScale.Crop
else -> ContentScale.FillBounds
}
val alpha = j.optDouble("alpha", 1.0).toFloat().coerceIn(0f, 1f)

if (src.startsWith("res:")) {
val ctx = LocalContext.current
val id = ctx.resources.getIdentifier(src.removePrefix("res:"), "drawable", ctx.packageName)
if (id != 0) Triple("res", id.toString(), scale) to alpha else null
} else if (src.startsWith("uri:") || src.startsWith("content:") || src.startsWith("file:")) {
Triple("uri", src.removePrefix("uri:"), scale) to alpha
} else null
}

// Layering: colore -> immagine -> gradient
Box(Modifier.matchParentSize().background(color))
img?.let { pair ->
val (info, alpha) = pair
val (kind, payload, scale) = info
if (kind == "res") {
Image(
painter = painterResource(payload.toInt()),
contentDescription = null,
contentScale = scale,
modifier = Modifier.matchParentSize(),
alpha = alpha
)
} else {
val bmp = rememberImageBitmapFromUri(payload)
if (bmp != null) {
Image(
bitmap = bmp,
contentDescription = null,
contentScale = scale,
modifier = Modifier.matchParentSize(),
alpha = alpha
)
}
}
}
brush?.let { b ->
Box(Modifier.matchParentSize().background(b))
}
}

/* =========================================================
* ROOT SCAFFOLD (top bar, bottom bar, fab, scroll)
* ========================================================= */
@Composable
private fun Scaffold(
layout: JSONObject,
dispatch: (String) -> Unit,
uiState: MutableMap<String, Any>,
designerMode: Boolean,
menus: Map<String, JSONArray>,
selectedPathSetter: (String) -> Unit,
extraPaddingBottom: Dp,
scaffoldPadding: PaddingValues
) {
val title = layout.optString("topTitle", "")
val topActions = layout.optJSONArray("topActions") ?: JSONArray() // legacy
val bottomButtons = layout.optJSONArray("bottomButtons") ?: JSONArray() // legacy
val bottomBarNode = layout.optJSONObject("bottomBar")
val bottomBarCfg = bottomBarNode?.optJSONObject("container")
val bottomBarItems = bottomBarNode?.optJSONArray("items")
val fab = layout.optJSONObject("fab")
val scroll = layout.optBoolean("scroll", true)
val topBarConf = layout.optJSONObject("topBar")

// Scroll behavior della TopAppBar (solo se definito in topBar)
val topScrollBehavior = when (topBarConf?.optString("scroll", "none")) {
"pinned" -> TopAppBarDefaults.pinnedScrollBehavior()
"enterAlways" -> TopAppBarDefaults.enterAlwaysScrollBehavior()
"exitUntilCollapsed" -> TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
else -> null
}

Scaffold(
modifier = if (topScrollBehavior != null)
Modifier.nestedScroll(topScrollBehavior.nestedScrollConnection)
else Modifier,
topBar = {
if (topBarConf != null) {
RenderTopBar(topBarConf, dispatch, topScrollBehavior)
} else {
// Fallback legacy: topTitle/topActions
if (title.isNotBlank() || topActions.length() > 0) {
TopAppBar(
title = { Text(title) },
actions = {
for (i in 0 until topActions.length()) {
val a = topActions.optJSONObject(i) ?: continue
IconButton(onClick = { dispatch(a.optString("actionId")) }) {
NamedIconEx(a.optString("icon", "more_vert"), null)
}
}
}
)
}
}
},
bottomBar = {
// Preferisci i nuovi items se presenti; altrimenti legacy bottomButtons
val items = bottomBarItems ?: if (bottomButtons.length() > 0) {
JSONArray().apply {
for (i in 0 until bottomButtons.length()) {
val it = bottomButtons.optJSONObject(i) ?: continue
put(JSONObject().apply {
put("type","button")
put("label", it.optString("label","Button"))
put("actionId", it.optString("actionId",""))
put("style","text")
})
}
}
} else null

if (items != null && items.length() > 0) {
StyledContainer(
cfg = bottomBarCfg ?: JSONObject(), 
contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
) {
Row(
Modifier.fillMaxWidth(),
horizontalArrangement = Arrangement.spacedBy(8.dp),
verticalAlignment = Alignment.CenterVertically
) {
RenderBarItemsRow(items, dispatch)
}
}
}
},
floatingActionButton = {
fab?.let {
FloatingActionButton(onClick = { dispatch(it.optString("actionId", "")) }) {
NamedIconEx(it.optString("icon", "play_arrow"), null)
}
}
},
containerColor = Color.Transparent // per vedere sfondo/gradient dietro le top bar "text/outlined"
) { innerPadding ->
val blocks = layout.optJSONArray("blocks") ?: JSONArray()

val host: @Composable () -> Unit = {
Column(
Modifier
.fillMaxSize()
.padding(innerPadding)
.padding(scaffoldPadding)
.padding(
start = 16.dp,
end = 16.dp,
top = 16.dp,
bottom = extraPaddingBottom
),
verticalArrangement = Arrangement.spacedBy(12.dp)
) {
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
onSelect = { p -> selectedPathSetter(p) },
onOpenInspector = { p -> selectedPathSetter(p) }
)
}
}
}

if (scroll) {
Column(Modifier.verticalScroll(rememberScrollState())) { host() }
} else {
host()
}
}
}

/* =========================================================
* RENDER ITEMS DI BARRA (Top/Bottom) – icone, bottoni, spacer
* ========================================================= */
@Composable
private fun RowScope.RenderBarItemsRow(items: JSONArray, dispatch: (String) -> Unit) {
for (i in 0 until items.length()) {
val it = items.optJSONObject(i) ?: continue
val type = it.optString("type").ifBlank {
// heurstica retrocompatibile
if (it.has("label")) "button" else "icon"
}
when (type) {
"spacer" -> {
when (it.optString("mode","fixed")) {
"expand" -> Spacer(Modifier.weight(1f))
else -> Spacer(Modifier.width((it.optDouble("widthDp", 16.0).toFloat().dp)))
}
}
"button" -> {
val label = it.optString("label","")
val style = it.optString("style","text")
val actionId = it.optString("actionId","")
when (style) {
"outlined" -> OutlinedButton(onClick = { dispatch(actionId) }) { Text(label) }
"tonal"    -> FilledTonalButton(onClick = { dispatch(actionId) }) { Text(label) }
"primary"  -> Button(onClick = { dispatch(actionId) }) { Text(label) }
else       -> TextButton(onClick = { dispatch(actionId) }) { Text(label) }
}
}
else -> {
val icon = it.optString("icon","more_vert")
val actionId = it.optString("actionId","")
val openMenuId = it.optString("openMenuId","")
var expanded by remember { mutableStateOf(false) }
Box {
IconButton(onClick = {
if (openMenuId.isNotBlank() || actionId.startsWith("open_menu:")) {
expanded = true
} else if (actionId.isNotBlank()) {
dispatch(actionId)
}
}) { NamedIconEx(icon, null) }

val menuId = if (openMenuId.isNotBlank()) openMenuId else actionId.removePrefix("open_menu:")
// il vero contenuto del menu è gestito come blocco "Menu" nel layout
// qui ci limitiamo a segnalare l'azione di apertura
DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
DropdownMenuItem(
text = { Text("Esegui ${if (menuId.isBlank()) "azione" else "menu:$menuId"}") },
onClick = { expanded = false; dispatch(actionId.ifBlank { "open_menu:$menuId" }) }
)
}
}
}
}
}
}

/* =========================================================
* TOP BAR – usa StyledContainer e items azioni con SpacerH
* ========================================================= */
@Composable
private fun RenderTopBar(
cfg: JSONObject,
dispatch: (String) -> Unit,
scrollBehavior: TopAppBarScrollBehavior? = null
) {
val variant = cfg.optString("variant", "small")
val title = cfg.optString("title", "")
val subtitle = cfg.optString("subtitle", "")

val rounded = RoundedCornerShape(
topStart = 0.dp, topEnd = 0.dp,
bottomStart = (cfg.optDouble("roundedBottomStart", 0.0).toFloat()).dp,
bottomEnd   = (cfg.optDouble("roundedBottomEnd",   0.0).toFloat()).dp
)

// Container unificato con fallback dai campi legacy
val containerCfg = cfg.optJSONObject("container")?.let { JSONObject(it.toString()) } ?: JSONObject()
// Legacy gradient
cfg.optJSONObject("gradient")?.let { g ->
if (!containerCfg.has("gradient")) containerCfg.put("gradient", JSONObject(g.toString()))
}
// Legacy containerColor
cfg.optString("containerColor","").takeIf { it.isNotBlank() }?.let { col ->
if (!containerCfg.has("customColor")) containerCfg.put("customColor", col)
if (!containerCfg.has("style")) containerCfg.put("style", "surface")
}

val resolved = resolveContainer(containerCfg)
val titleColor     = parseColorOrRole(cfg.optString("titleColor", ""))   ?: resolved.contentColor
val actionsColor   = parseColorOrRole(cfg.optString("actionsColor", "")) ?: titleColor

val colors = TopAppBarDefaults.topAppBarColors(
containerColor = Color.Transparent, // sfondo lo gestisce StyledContainer
titleContentColor = titleColor,
actionIconContentColor = actionsColor,
navigationIconContentColor = actionsColor
)

val actions = cfg.optJSONArray("actions") ?: JSONArray()

StyledContainer(
cfg = containerCfg,
modifier = Modifier
.fillMaxWidth()
.clip(rounded) // arrotondamento inferiore legacy
) {
when (variant) {
"center" -> CenterAlignedTopAppBar(
title = { TitleSubtitle(title, subtitle, titleColor) },
actions = { RenderBarItemsRow(actions, dispatch) },
colors = colors,
scrollBehavior = scrollBehavior
)
"medium" -> MediumTopAppBar(
title = { TitleSubtitle(title, subtitle, titleColor) },
actions = { RenderBarItemsRow(actions, dispatch) },
colors = colors,
scrollBehavior = scrollBehavior
)
"large" -> LargeTopAppBar(
title = { TitleSubtitle(title, subtitle, titleColor) },
actions = { RenderBarItemsRow(actions, dispatch) },
colors = colors,
scrollBehavior = scrollBehavior
)
else -> TopAppBar(
title = { TitleSubtitle(title, subtitle, titleColor) },
actions = { RenderBarItemsRow(actions, dispatch) },
colors = colors,
scrollBehavior = scrollBehavior
)
}

if (cfg.optBoolean("divider", false)) {
Divider()
}
}
}

@Composable
private fun TitleSubtitle(title: String, subtitle: String, titleColor: Color) {
if (subtitle.isBlank()) {
Text(title)
} else {
Column {
Text(title, style = MaterialTheme.typography.titleLarge)
Text(subtitle, style = MaterialTheme.typography.labelMedium, color = titleColor.copy(alpha = 0.8f))
}
}
}

/* =========================================================
* OVERLAY DESIGNER (palette + azioni + inspector)
* ========================================================= */

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
topPadding: Dp,
onOverlayHeight: (Int) -> Unit,
onOpenRootInspector: () -> Unit,
onRootLivePreview: (JSONObject?) -> Unit
) {
var showInspector by remember { mutableStateOf(false) }
var showRootInspector by remember { mutableStateOf(false) }
val selectedBlock = selectedPath?.let { jsonAtPath(layout, it) as? JSONObject }

Column(
Modifier
.align(Alignment.BottomCenter)
.fillMaxWidth()
.padding(12.dp)
.onGloballyPositioned { onOverlayHeight(it.size.height) },
verticalArrangement = Arrangement.spacedBy(8.dp)
) {
// ===== PALETTE =====
Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 8.dp) {
Row(
Modifier.padding(10.dp).horizontalScroll(rememberScrollState()),
horizontalArrangement = Arrangement.spacedBy(8.dp),
verticalAlignment = Alignment.CenterVertically
) {
Text("Palette:", style = MaterialTheme.typography.labelLarge)

FilledTonalButton(onClick = {
val path = insertBlockAndReturnPath(layout, selectedPath, newProgress(), "after")
setSelectedPath(path); onLayoutChange()
}) { Icon(Icons.Filled.Flag, null); Spacer(Modifier.width(6.dp)); Text("Progress") }

FilledTonalButton(onClick = {
val path = insertBlockAndReturnPath(layout, selectedPath, newRow(), "after")
setSelectedPath(path); onLayoutChange()
}) { Icon(Icons.Filled.GridOn, null); Spacer(Modifier.width(6.dp)); Text("Row") }

FilledTonalButton(onClick = {
val path = insertBlockAndReturnPath(layout, selectedPath, newAlert(), "after")
setSelectedPath(path); onLayoutChange()
}) { Icon(Icons.Filled.Warning, null); Spacer(Modifier.width(6.dp)); Text("Alert") }

FilledTonalButton(onClick = {
val path = insertBlockAndReturnPath(layout, selectedPath, newImage(), "after")
setSelectedPath(path); onLayoutChange()
}) { Icon(Icons.Filled.Image, null); Spacer(Modifier.width(6.dp)); Text("Image") }

FilledTonalButton(onClick = {
val path = insertBlockAndReturnPath(layout, selectedPath, newSectionHeader(), "after")
setSelectedPath(path); onLayoutChange()
}) { Icon(Icons.Filled.LibraryAdd, null); Spacer(Modifier.width(6.dp)); Text("SectionHeader") }

FilledTonalButton(onClick = {
val path = insertBlockAndReturnPath(layout, selectedPath, newButtonRow(), "after")
setSelectedPath(path); onLayoutChange()
}) { Icon(Icons.Filled.LibraryAdd, null); Spacer(Modifier.width(6.dp)); Text("ButtonRow") }

FilledTonalButton(onClick = {
val path = insertBlockAndReturnPath(layout, selectedPath, newList(), "after")
setSelectedPath(path); onLayoutChange()
}) { Icon(Icons.Filled.List, null); Spacer(Modifier.width(6.dp)); Text("List") }

FilledTonalButton(onClick = {
val path = insertBlockAndReturnPath(layout, selectedPath, newSpacer(), "after")
setSelectedPath(path); onLayoutChange()
}) { Icon(Icons.Filled.SpaceBar, null); Spacer(Modifier.width(6.dp)); Text("Spacer") }

FilledTonalButton(onClick = {
val path = insertBlockAndReturnPath(layout, selectedPath, JSONObject().put("type", "Divider"), "after")
setSelectedPath(path); onLayoutChange()
}) { Icon(Icons.Filled.HorizontalRule, null); Spacer(Modifier.width(6.dp)); Text("Divider") }

FilledTonalButton(onClick = {
val path = insertBlockAndReturnPath(layout, selectedPath, newDividerV(), "after")
setSelectedPath(path); onLayoutChange()
}) { Icon(Icons.Filled.MoreVert, null); Spacer(Modifier.width(6.dp)); Text("DividerV") }

FilledTonalButton(onClick = {
val iconPath = insertIconMenuReturnIconPath(layout, selectedPath)
setSelectedPath(iconPath); onLayoutChange()
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
val path = insertBlockAndReturnPath(layout, selectedPath, newChipRow(), "after")
setSelectedPath(path); onLayoutChange()
}) { Icon(Icons.Filled.Palette, null); Spacer(Modifier.width(6.dp)); Text("ChipRow") }

FilledTonalButton(onClick = {
val path = insertBlockAndReturnPath(layout, selectedPath, newSlider(), "after")
setSelectedPath(path); onLayoutChange()
}) { Icon(Icons.Filled.Tune, null); Spacer(Modifier.width(6.dp)); Text("Slider") }

FilledTonalButton(onClick = {
val path = insertBlockAndReturnPath(layout, selectedPath, newToggle(), "after")
setSelectedPath(path); onLayoutChange()
}) { Icon(Icons.Filled.ToggleOn, null); Spacer(Modifier.width(6.dp)); Text("Toggle") }

FilledTonalButton(onClick = {
val path = insertBlockAndReturnPath(layout, selectedPath, newTabs(), "after")
setSelectedPath(path); onLayoutChange()
}) { Icon(Icons.Filled.Tab, null); Spacer(Modifier.width(6.dp)); Text("Tabs") }

FilledTonalButton(onClick = {
val path = insertBlockAndReturnPath(layout, selectedPath, newMetricsGrid(), "after")
setSelectedPath(path); onLayoutChange()
}) { Icon(Icons.Filled.GridOn, null); Spacer(Modifier.width(6.dp)); Text("MetricsGrid") }
}
}

// ===== SELEZIONE + SALVATAGGIO =====
Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 8.dp) {
Column(
Modifier
.padding(10.dp)
.fillMaxWidth(),
verticalArrangement = Arrangement.spacedBy(8.dp)
) {
Row(
modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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

Button(onClick = { showInspector = true }, enabled = selectedBlock != null) {
Icon(Icons.Filled.Settings, null); Spacer(Modifier.width(6.dp)); Text("Proprietà…")
}

OutlinedButton(onClick = { showRootInspector = true }) {
Icon(Icons.Filled.Tune, null); Spacer(Modifier.width(6.dp)); Text("Layout…")
}
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

// ===== INSPECTOR BLOCCHI =====
if (showInspector && selectedBlock != null && selectedPath != null) {
val working = remember(selectedPath) { JSONObject(selectedBlock.toString()) }
var previewTick by remember { mutableStateOf(0) }
val bumpPreview: () -> Unit = { previewTick++ }

BackHandler(enabled = true) { showInspector = false }

Box(
Modifier
.fillMaxSize()
.clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
) {
// ANTEPRIMA
val previewTopPad = topPadding + 8.dp
Surface(
modifier = Modifier
.align(Alignment.TopCenter)
.padding(start = 12.dp, end = 12.dp, top = previewTopPad)
.shadow(10.dp, RoundedCornerShape(16.dp))
.fillMaxWidth(),
shape = RoundedCornerShape(16.dp),
tonalElevation = 6.dp
) {
Column(Modifier.padding(12.dp)) {
Text("Anteprima", style = MaterialTheme.typography.labelLarge)
Spacer(Modifier.height(8.dp))
key(previewTick) {
RenderBlock(
block = working,
dispatch = { },
uiState = mutableMapOf(),
designerMode = false,
path = selectedPath,
menus = emptyMap(),
onSelect = {}
)
}
}
}

// PANNELLO BASSO
Surface(
modifier = Modifier
.align(Alignment.BottomCenter)
.fillMaxWidth()
.fillMaxHeight(0.6f),
shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
tonalElevation = 8.dp
) {
Column(
Modifier
.fillMaxSize()
.verticalScroll(rememberScrollState())
.padding(16.dp),
verticalArrangement = Arrangement.spacedBy(12.dp)
) {
when (working.optString("type")) {
"Card"          -> CardInspectorPanel(working, onChange = bumpPreview)
"ButtonRow"     -> { ButtonRowInspectorPanel(working, onChange = bumpPreview); ContainerEditorSection(working, onChange = bumpPreview) }
"SectionHeader" -> { SectionHeaderInspectorPanel(working, onChange = bumpPreview); ContainerEditorSection(working, onChange = bumpPreview) }
"Progress"      -> ProgressInspectorPanel(working, onChange = bumpPreview)
"Alert"         -> AlertInspectorPanel(working, onChange = bumpPreview)
"Image"         -> ImageInspectorPanel(working, onChange = bumpPreview)
"ChipRow"       -> ChipRowInspectorPanel(working, onChange = bumpPreview)
"Slider"        -> SliderInspectorPanel(working, onChange = bumpPreview)
"Toggle"        -> ToggleInspectorPanel(working, onChange = bumpPreview)
"Tabs"          -> TabsInspectorPanel(working, onChange = bumpPreview)
"MetricsGrid"   -> MetricsGridInspectorPanel(working, onChange = bumpPreview)
"List"          -> { ListInspectorPanel(working, onChange = bumpPreview); ContainerEditorSection(working, onChange = bumpPreview) }
else            -> Text("Inspector non ancora implementato per ${working.optString("type")}")
}
Spacer(Modifier.height(8.dp))
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
TextButton(onClick = { showInspector = false }) { Text("Annulla") }
Spacer(Modifier.weight(1f))
Button(onClick = {
replaceAtPath(layout, selectedPath, working)
showInspector = false
onLayoutChange()
}) { Text("OK") }
}
}
}
}
}

// ===== ROOT LAYOUT INSPECTOR =====
if (showRootInspector) {
val working = remember { JSONObject(layout.toString()) }
var dummyTick by remember { mutableStateOf(0) }

val onChange: () -> Unit = {
onRootLivePreview(JSONObject(working.toString())) // nuova istanza => recomposition garantita
}

BackHandler(enabled = true) {
onRootLivePreview(null) // chiudi preview
showRootInspector = false
}

// ANTEPRIMA in alto della BottomBar (proiettata)
val previewTopPad = topPadding + 8.dp
val hasBottomPreview = working.optJSONObject("bottomBar")?.optJSONArray("items")?.length() ?: 0 > 0 ||
working.optJSONArray("bottomButtons")?.length() ?: 0 > 0
if (hasBottomPreview) {
Surface(
modifier = Modifier
.align(Alignment.TopCenter)
.padding(start = 12.dp, end = 12.dp, top = previewTopPad)
.shadow(10.dp, RoundedCornerShape(16.dp))
.fillMaxWidth(),
shape = RoundedCornerShape(16.dp),
tonalElevation = 6.dp
) {
Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
Text("Anteprima Bottom Bar", style = MaterialTheme.typography.labelLarge)
val bb = working.optJSONObject("bottomBar")
val cont = bb?.optJSONObject("container")
val items = bb?.optJSONArray("items") ?: run {
// fallback legacy
val legacy = working.optJSONArray("bottomButtons") ?: JSONArray()
JSONArray().apply {
for (i in 0 until legacy.length()) {
val it = legacy.optJSONObject(i) ?: continue
put(JSONObject().apply {
put("type","button")
put("label", it.optString("label","Button"))
put("actionId", it.optString("actionId",""))
put("style","text")
})
}
}
}
StyledContainer(cont ?: JSONObject(), Modifier.fillMaxWidth(), contentPadding = PaddingValues(8.dp)) {
Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
RenderBarItemsRow(items) { /* anteprima: no-op */ }
}
}
}
}
}

Surface(
modifier = Modifier
.align(Alignment.BottomCenter)
.fillMaxWidth()
.fillMaxHeight(0.75f),
shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
tonalElevation = 8.dp
) {
Column(
Modifier
.fillMaxSize()
.verticalScroll(rememberScrollState())
.padding(16.dp),
verticalArrangement = Arrangement.spacedBy(12.dp)
) {
// PAGE (sfondo) – live sullo schermo
Divider(); Text("Page (sfondo)", style = MaterialTheme.typography.titleMedium)
val page = working.optJSONObject("page") ?: JSONObject().also { working.put("page", it) }
PageInspectorPanel(page, onChange)

// Top bar – live sullo schermo
Divider(); Text("Top Bar (estetica)", style = MaterialTheme.typography.titleMedium)
var topBarEnabled by remember { mutableStateOf(working.optJSONObject("topBar") != null) }
Row(verticalAlignment = Alignment.CenterVertically) {
Switch(
checked = topBarEnabled,
onCheckedChange = {
topBarEnabled = it
if (it) {
if (!working.has("topBar")) {
working.put("topBar", JSONObject().apply {
put("variant", "small")
put("title", "topbar")
put("scroll", "pinned")
// Testo chiaro su toni scuri (surface scuro -> onSurface di solito è chiaro nel tema dark)
put("titleColor", "onSurface")
put("actionsColor", "onSurface")
put("divider", false)
put("container", JSONObject().apply {
put("style", "surface")
put("corner", 0)
put("borderMode", "full")
put("borderThicknessDp", 2)
// opzionale: imposta un bordo visibile scuro/chiaro a seconda del tema
// put("borderColor", "outline")
})
put("actions", JSONArray())
})
}

} else {
working.remove("topBar")
}
onChange()
}
)
Spacer(Modifier.width(8.dp))
Text("Abilita Top Bar estetico")
}
working.optJSONObject("topBar")?.let { tb ->
TopBarInspectorPanel(tb, onChange)
// Editor contenitore unificato
ContainerEditorSection(tb, key = "container", title = "TopBar – Contenitore", onChange = onChange)
// Editor azioni (icone, bottoni, spacer)
BarItemsEditor(
owner = tb,
arrayKey = "actions",
title = "TopBar – Azioni",
onChange = onChange
)
}

// Bottom bar estetica (preview in alto)
Divider(); Text("Bottom Bar (estetica)", style = MaterialTheme.typography.titleMedium)
var bottomEnabled by remember { mutableStateOf(working.optJSONObject("bottomBar") != null) }
Row(verticalAlignment = Alignment.CenterVertically) {
Switch(
checked = bottomEnabled,
onCheckedChange = {
bottomEnabled = it
if (it) {
if (!working.has("bottomBar")) {
working.put("bottomBar", JSONObject().apply {
put("container", JSONObject().apply {
put("style","surface")
put("borderMode","none")
put("corner", 0)
})
put("items", JSONArray())
})
}
} else {
working.remove("bottomBar")
}
onChange()
}
)
Spacer(Modifier.width(8.dp))
Text("Abilita stile custom per Bottom Bar")
}
working.optJSONObject("bottomBar")?.let { bb ->
ContainerEditorSection(bb, key = "container", title = "BottomBar – Contenitore", onChange = onChange)
BarItemsEditor(owner = bb, arrayKey = "items", title = "BottomBar – Items", onChange = onChange)
}

// VARI – Scroll on/off, FAB (il resto del root)
Divider(); RootInspectorPanel(working, onChange)

Spacer(Modifier.height(8.dp))
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
TextButton(onClick = {
onRootLivePreview(null)
showRootInspector = false
}) { Text("Annulla") }
Spacer(Modifier.weight(1f))
Button(onClick = {
// Commit nel layout originale
val keys = listOf("page","topBar","topTitle","topActions","bottomBar","bottomButtons","fab","scroll")
keys.forEach { k -> layout.put(k, working.opt(k)) }
onRootLivePreview(null)
showRootInspector = false
}) { Text("OK") }
}
}
}
}
}

/* =========================================================
* OVERLAY INGRANAGGIO PER CONTENITORI
* ========================================================= */
@Composable
private fun ContainerOverlayGear(
designerMode: Boolean,
path: String,
onOpenInspector: (String) -> Unit,
content: @Composable () -> Unit
) {
if (!designerMode) {
content()
return
}
Box {
content()
IconButton(
onClick = { onOpenInspector(path) },
modifier = Modifier
.align(Alignment.BottomEnd)
.size(30.dp)
.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
) {
Icon(Icons.Filled.Settings, contentDescription = "Proprietà contenitore")
}
}
}

/* =========================================================
* RENDERER BLOCCHI
* ========================================================= */

@Composable
private fun RenderBlock(
block: JSONObject,
dispatch: (String) -> Unit,
uiState: MutableMap<String, Any>,
designerMode: Boolean,
path: String,
menus: Map<String, JSONArray>,
onSelect: (String) -> Unit,
onOpenInspector: (String) -> Unit = {}
) {
val borderSelected = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)

@Composable
fun Wrapper(content: @Composable () -> Unit) {
if (designerMode) {
Box {
OutlinedCard(
modifier = Modifier.fillMaxWidth().padding(0.dp),
border = borderSelected,
shape = RoundedCornerShape(12.dp)
) {
Column(Modifier.padding(12.dp)) {
Text(
block.optString("type", ""),
style = MaterialTheme.typography.labelSmall,
color = MaterialTheme.colorScheme.primary
)
Spacer(Modifier.height(6.dp))
// Se il blocco ha "container", avvolgo con StyledContainer
val containerCfg = block.optJSONObject("container")
if (containerCfg != null) {
StyledContainer(containerCfg, Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
content()
}
} else {
content()
}
}
}
Box(Modifier.matchParentSize().clickable(onClick = { onSelect(path) }))
}
} else {
val containerCfg = block.optJSONObject("container")
if (containerCfg != null) {
StyledContainer(containerCfg, Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
content()
}
} else {
Box { content() }
}
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
}) {
NamedIconEx(iconName, null)
}

val menuId = if (openMenuId.isNotBlank()) openMenuId else actionId.removePrefix("open_menu:")
val items = menus[menuId]
if (items != null) {
DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
for (i in 0 until items.length()) {
val it = items.optJSONObject(i) ?: continue
DropdownMenuItem(
text = { Text(it.optString("label", "")) },
onClick = { expanded = false; dispatch(it.optString("actionId", "")) },
leadingIcon = {
val ic = it.optString("icon", "")
if (ic.isNotBlank()) NamedIconEx(ic, null)
}
)
}
}
}
}
}

"Row", "row" -> ResizableRow(
    rowBlock = block,
    path = path,
    uiState = uiState,
    menus = menus,
    dispatch = dispatch,
    designerMode = designerMode,
    onSelect = onSelect,
    onOpenInspector = onOpenInspector
)



"Progress" -> Wrapper {
val label = block.optString("label","")
val value = block.optDouble("value", 0.0).toFloat().coerceIn(0f, 100f)
val color = parseColorOrRole(block.optString("color","")) ?: MaterialTheme.colorScheme.primary
val showPercent = block.optBoolean("showPercent", true)

Column {
if (label.isNotBlank()) Text(label, style = MaterialTheme.typography.bodyMedium)
LinearProgressIndicator(
progress = value / 100f,
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
val height = (block.optDouble("heightDp", 160.0).toFloat()).dp
val corner = (block.optDouble("corner", 12.0).toFloat()).dp
val scale = when (block.optString("contentScale","fit")) {
"crop" -> ContentScale.Crop
"fill" -> ContentScale.FillBounds
else   -> ContentScale.Fit
}

val isRes = source.startsWith("res:")
val resId = if (isRes)
LocalContext.current.resources.getIdentifier(source.removePrefix("res:"), "drawable", LocalContext.current.packageName)
else 0

Surface(shape = RoundedCornerShape(corner), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
when {
isRes && resId != 0 -> {
Image(
painter = painterResource(resId),
contentDescription = null,
modifier = Modifier.fillMaxWidth().height(height),
contentScale = scale
)
}
source.startsWith("uri:") || source.startsWith("content:") || source.startsWith("file:") -> {
val bmp = rememberImageBitmapFromUri(source.removePrefix("uri:"))
if (bmp != null) {
Image(
bitmap = bmp,
contentDescription = null,
modifier = Modifier.fillMaxWidth().height(height),
contentScale = scale
)
} else {
Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
Text("Image: (seleziona sorgente)", style = MaterialTheme.typography.labelMedium)
}
}
}
else -> {
Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
Text("Image: ${if (source.isBlank()) "(not set)" else source}", style = MaterialTheme.typography.labelMedium)
}
}
}
}
}

"Card" -> {
val clickAction = block.optString("clickActionId","")

val innerContent: @Composable () -> Unit = {
val innerBlocks = block.optJSONArray("blocks") ?: JSONArray()
Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
for (i in 0 until innerBlocks.length()) {
val b = innerBlocks.optJSONObject(i) ?: continue
val p2 = "$path/blocks/$i"
RenderBlock(b, dispatch, uiState, designerMode, p2, menus, onSelect, onOpenInspector)
}
}
}

// Mappatura legacy -> nuovo container
val legacyVariant = block.optString("variant","")
val containerCfg = block.optJSONObject("container") ?: JSONObject().apply {
when (legacyVariant) {
"outlined" -> put("style","outlined")
"filled"   -> put("style","primary")
"elevated" -> { put("style","surface"); put("elevationDp", 2) }
else       -> put("style","surface")
}
}

val baseMod = Modifier
.fillMaxWidth()
.then(if (clickAction.isNotBlank() && !designerMode) Modifier.clickable { dispatch(clickAction) } else Modifier)

ContainerOverlayGear(designerMode, path, onOpenInspector) {
StyledContainer(
cfg = containerCfg,
modifier = baseMod,
contentPadding = PaddingValues(12.dp)
) {
innerContent()
}
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

ContainerOverlayGear(designerMode, path, onOpenInspector) {
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
val tab = tabs.optJSONObject(idx) ?: JSONObject()
val blocks2 = tab.optJSONArray("blocks") ?: JSONArray()
Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
for (k in 0 until blocks2.length()) {
val b = blocks2.optJSONObject(k) ?: continue
val p2 = "$path/tabs/$idx/blocks/$k"
RenderBlock(b, dispatch, uiState, designerMode, p2, menus, onSelect, onOpenInspector)
}
}
}
}
"SectionHeader" -> Wrapper {
val title = block.optString("title","")
val subtitle = block.optString("subtitle","")
val align = mapTextAlign(block.readAlign())

val baseTitle = MaterialTheme.typography.titleMedium
val stTitle   = applyTextStyleOverrides(block, baseTitle)

Column(Modifier.fillMaxWidth()) {
if (title.isNotBlank())
Text(title, style = stTitle, textAlign = align, modifier = Modifier.fillMaxWidth())

if (subtitle.isNotBlank()) {
val baseSub = MaterialTheme.typography.bodyMedium
val stSub   = applyTextStyleOverrides(block, baseSub)
Text(subtitle, style = stSub, textAlign = align, modifier = Modifier.fillMaxWidth())
}
}
}
"MetricsGrid" -> Wrapper {
val tiles = block.optJSONArray("tiles") ?: JSONArray()
val cols = block.optInt("columns", 2).coerceIn(1, 3)
GridSection(tiles, cols, uiState)
}

"ButtonRow" -> {
val align = when (block.optString("align", "center")) {
"start" -> Arrangement.Start
"end" -> Arrangement.End
"space_between" -> Arrangement.SpaceBetween
"space_around" -> Arrangement.SpaceAround
"space_evenly" -> Arrangement.SpaceEvenly
else -> Arrangement.Center
}
val buttons = block.optJSONArray("buttons") ?: JSONArray()

val rowContent: @Composable () -> Unit = {
Row(Modifier.fillMaxWidth(), horizontalArrangement = align) {
for (i in 0 until buttons.length()) {
val b = buttons.getJSONObject(i)
val label = b.optString("label", "")
val action = b.optString("actionId", "")
when (b.optString("style", "text")) {
"outlined" -> OutlinedButton(onClick = { if (action.isNotBlank()) dispatch(action) }) { IconText(label, b.optString("icon", "")) }
"tonal"    -> FilledTonalButton(onClick = { if (action.isNotBlank()) dispatch(action) }) { IconText(label, b.optString("icon", "")) }
"primary"  -> Button(onClick = { if (action.isNotBlank()) dispatch(action) }) { IconText(label, b.optString("icon", "")) }
else       -> TextButton(onClick = { if (action.isNotBlank()) dispatch(action) }) { IconText(label, b.optString("icon", "")) }
}
}
}
}
val cont = block.optJSONObject("container")
if (cont != null) StyledContainer(cont, contentPadding = PaddingValues(8.dp)) { rowContent() } else rowContent()
}

"ChipRow" -> Wrapper {
val chips = block.optJSONArray("chips") ?: JSONArray()
val isSingle = (0 until chips.length()).any { chips.optJSONObject(it)?.has("value") == true }
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
for (i in 0 until chips.length()) {
val c = chips.optJSONObject(i) ?: continue
val label = c.optString("label", "")
val bind = c.optString("bind", "")
if (isSingle) {
val v = c.opt("value")?.toString() ?: ""
val current = uiState[bind]?.toString()
FilterChip(
selected = current == v,
onClick = { uiState[bind] = v },
label = {
Text(
label,
style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyMedium),
color = parseColorOrRole(block.optString("textColor", ""))
?: LocalContentColor.current
)
},
leadingIcon = if (current == v) {
{ Icon(Icons.Filled.Check, null) }
} else null
)
} else {
val current = (uiState[bind] as? Boolean) ?: false
FilterChip(
selected = current,
onClick = { uiState[bind] = !current },
label = {
Text(
label,
style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyMedium),
color = parseColorOrRole(block.optString("textColor", ""))
?: LocalContentColor.current
)
},
leadingIcon = if (current) {
{ Icon(Icons.Filled.Check, null) }
} else null
)
}
}
}
}

"Toggle" -> Wrapper {
val label = block.optString("label", "")
val bind = block.optString("bind", "")
val v = (uiState[bind] as? Boolean) ?: false
Row(verticalAlignment = Alignment.CenterVertically) {
Switch(checked = v, onCheckedChange = { uiState[bind] = it })
Spacer(Modifier.width(8.dp))
Text(label)
}
}

"Slider" -> Wrapper {
val label = block.optString("label", "")
val bind = block.optString("bind", "")
val min = block.optDouble("min", 0.0).toFloat()
val max = block.optDouble("max", 10.0).toFloat()
val step = block.optDouble("step", 1.0).toFloat()
var value by remember { mutableStateOf(((uiState[bind] as? Number)?.toFloat()) ?: min) }
Text("$label: ${"%.1f".format(value)}${block.optString("unit", "")}")
Slider(
value = value,
onValueChange = {
value = it
uiState[bind] = if (step >= 1f) round(it / step) * step else it
},
valueRange = min..max
)
}

"List" -> Wrapper {
val items = block.optJSONArray("items") ?: JSONArray()
val align = mapTextAlign(block.readAlign())
val textColor = parseColorOrRole(block.optString("textColor", ""))
Column(Modifier.fillMaxWidth()) {
for (i in 0 until items.length()) {
val item = items.optJSONObject(i) ?: continue
ListItem(
headlineContent = {
Text(
item.optString("title", ""),
style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyLarge),
color = textColor ?: LocalContentColor.current,
textAlign = align,
modifier = Modifier.fillMaxWidth()
)
},
supportingContent = {
val sub = item.optString("subtitle", "")
if (sub.isNotBlank()) {
Text(
sub,
style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyMedium),
color = textColor ?: LocalContentColor.current,
textAlign = align,
modifier = Modifier.fillMaxWidth()
)
}
},
modifier = Modifier.fillMaxWidth()
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
val icon = block.optString("icon", "play_arrow")
val label = block.optString("label", "")
val size = block.optString("size", "regular")
val variant = block.optString("variant", "regular")
val action = block.optString("actionId", "")
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
val thick = (block.optDouble("thickness", 1.0).toFloat()).dp
val padStart = (block.optDouble("padStart", 0.0).toFloat()).dp
val padEnd = (block.optDouble("padEnd", 0.0).toFloat()).dp
Divider(modifier = Modifier.padding(start = padStart, end = padEnd), thickness = thick)
}

"DividerV" -> {
val thickness = (block.optDouble("thickness", 1.0).toFloat()).dp
val height = (block.optDouble("height", 24.0).toFloat()).dp
VerticalDivider(modifier = Modifier.height(height), thickness = thickness)
}

"Spacer" -> {
Spacer(Modifier.height((block.optDouble("height", 8.0).toFloat().dp)))
}

"Menu" -> {
if (designerMode) {
ElevatedCard {
Text(
"Menu: ${block.optString("id")} (${block.optJSONArray("items")?.length() ?: 0} voci)",
Modifier.padding(8.dp)
)
}
}
}

else -> {
if (designerMode) {
Surface(tonalElevation = 1.dp) {
Text("Blocco non supportato: ${block.optString("type")}", color = Color.Red)
}
}
}
}
}

/* =========================================================
* GRID
* ========================================================= */

@Composable
private fun GridSection(tiles: JSONArray, cols: Int, uiState: MutableMap<String, Any>) {
val rows = mutableListOf<List<JSONObject>>()
var current = mutableListOf<JSONObject>()
for (i in 0 until tiles.length()) {
tiles.optJSONObject(i)?.let { current.add(it) }
if (current.size == cols) {
rows.add(current.toList()); current = mutableListOf()
}
}
if (current.isNotEmpty()) rows.add(current)

Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
rows.forEach { row ->
Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
row.forEach { t ->
ElevatedCard(Modifier.weight(1f)) {
Column(Modifier.padding(12.dp)) {
Text(t.optString("label", ""), style = MaterialTheme.typography.labelMedium)
Text("—", style = MaterialTheme.typography.headlineSmall)
}
}
}
repeat((cols - row.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
}
}
}
}

/* =========================================================
* ROOT INSPECTOR SUB-PANNELLI
* ========================================================= */

@Composable
private fun RootInspectorPanel(working: JSONObject, onChange: () -> Unit) {
// Scroll on/off
Text("Layout (root) – Opzioni", style = MaterialTheme.typography.titleMedium)

var scroll by remember { mutableStateOf(working.optBoolean("scroll", true)) }
Row(verticalAlignment = Alignment.CenterVertically) {
Switch(checked = scroll, onCheckedChange = {
scroll = it; working.put("scroll", it); onChange()
})
Spacer(Modifier.width(8.dp)); Text("Contenuto scrollabile")
}

Divider(); Text("Floating Action Button", style = MaterialTheme.typography.titleSmall)
val fab = working.optJSONObject("fab") ?: JSONObject().also { working.put("fab", it) }
var fabEnabled by remember { mutableStateOf(fab.optBoolean("enabled", false)) }
Row(verticalAlignment = Alignment.CenterVertically) {
Switch(checked = fabEnabled, onCheckedChange = {
fabEnabled = it
if (!it) working.remove("fab") else {
if (!fab.has("icon")) fab.put("icon","add")
fab.put("enabled", true)
working.put("fab", fab)
}
onChange()
})
Spacer(Modifier.width(8.dp)); Text("Abilita FAB")
}
if (fabEnabled) {
val icon = remember { mutableStateOf(fab.optString("icon","add")) }
IconPickerField(icon, "icon") { sel -> icon.value = sel; fab.put("icon", sel); onChange() }
val label = remember { mutableStateOf(fab.optString("label","")) }
OutlinedTextField(label.value, { label.value = it; fab.put("label", it); onChange() }, label = { Text("label (solo extended)") })
val action = remember { mutableStateOf(fab.optString("actionId","")) }
OutlinedTextField(action.value, { action.value = it; fab.put("actionId", it); onChange() }, label = { Text("actionId") })
}
}

/* =========================================================
* PAGE INSPECTOR
* ========================================================= */
@Composable
private fun PageInspectorPanel(page: JSONObject, onChange: () -> Unit) {
// Colore base
val color = remember { mutableStateOf(page.optString("color","")) }
NamedColorPickerPlus(current = color.value, label = "color (sfondo pagina)", allowRoles = true) { pick ->
color.value = pick
if (pick.isBlank()) page.remove("color") else page.put("color", pick)
onChange()
}

// Gradient
Divider(); Text("Gradient (opz.)", style = MaterialTheme.typography.titleSmall)
var gradEnabled by remember { mutableStateOf(page.optJSONObject("gradient") != null) }
Row(verticalAlignment = Alignment.CenterVertically) {
Switch(checked = gradEnabled, onCheckedChange = {
gradEnabled = it
if (it) {
val g = page.optJSONObject("gradient") ?: JSONObject().also { j ->
j.put("colors", JSONArray().put("primary").put("tertiary"))
j.put("direction", "vertical")
}
page.put("gradient", g)
} else {
page.remove("gradient")
}
onChange()
})
Spacer(Modifier.width(8.dp)); Text("Abilita gradient")
}
page.optJSONObject("gradient")?.let { g ->
val colorsArr = g.optJSONArray("colors") ?: JSONArray().also { g.put("colors", it) }
while (colorsArr.length() < 2) colorsArr.put("primary")
val c1 = remember { mutableStateOf(colorsArr.optString(0, "primary")) }
val c2 = remember { mutableStateOf(colorsArr.optString(1, "tertiary")) }
NamedColorPickerPlus(current = c1.value, label = "gradient color 1", allowRoles = true) { pick ->
c1.value = pick; colorsArr.put(0, pick); onChange()
}
NamedColorPickerPlus(current = c2.value, label = "gradient color 2", allowRoles = true) { pick ->
c2.value = pick; colorsArr.put(1, pick); onChange()
}
var dir by remember { mutableStateOf(g.optString("direction","vertical")) }
ExposedDropdown(
value = dir, label = "direction",
options = listOf("vertical","horizontal")
) { sel -> dir = sel; g.put("direction", sel); onChange() }
}

// Immagine pagina
Divider(); Text("Immagine a tutta pagina (opz.)", style = MaterialTheme.typography.titleSmall)
var imgEnabled by remember { mutableStateOf(page.optJSONObject("image") != null) }
Row(verticalAlignment = Alignment.CenterVertically) {
Switch(checked = imgEnabled, onCheckedChange = {
imgEnabled = it
if (it) page.put("image", page.optJSONObject("image") ?: JSONObject().apply {
put("source","")
put("contentScale","fill")
put("alpha", 1.0)
}) else page.remove("image")
onChange()
})
Spacer(Modifier.width(8.dp)); Text("Abilita immagine di sfondo")
}
page.optJSONObject("image")?.let { img ->
ImagePickerRow(
label = "source",
current = img.optString("source",""),
onChange = { src -> img.put("source", src); onChange() },
onClear = { img.put("source",""); onChange() }
)
var scale by remember { mutableStateOf(img.optString("contentScale","fill")) }
ExposedDropdown(
value = scale, label = "contentScale",
options = listOf("fill","fit","crop")
) { sel -> scale = sel; img.put("contentScale", sel); onChange() }
val alpha = remember { mutableStateOf(img.optDouble("alpha",1.0).toString()) }
StepperField("alpha (0..1)", alpha, 0.1) { v ->
img.put("alpha", v.coerceIn(0.0,1.0)); onChange()
}
}
}

/* =========================================================
* EDITOR ITEMS DI BARRA (riusabile per TopBar e BottomBar)
* ========================================================= */
@Composable
private fun BarItemsEditor(
owner: JSONObject,
arrayKey: String,
title: String,
onChange: () -> Unit
) {
Divider()
Text(title, style = MaterialTheme.typography.titleSmall)
val arr = owner.optJSONArray(arrayKey) ?: JSONArray().also { owner.put(arrayKey, it) }

for (i in 0 until arr.length()) {
val item = arr.getJSONObject(i)

ElevatedCard {
Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

Row(
Modifier.fillMaxWidth(),
horizontalArrangement = Arrangement.SpaceBetween,
verticalAlignment = Alignment.CenterVertically
) {
Text("Item ${i + 1}", style = MaterialTheme.typography.labelLarge)
Row {
IconButton(onClick = { moveInArray(arr, i, -1); onChange() }) { Icon(Icons.Filled.KeyboardArrowUp, null) }
IconButton(onClick = { moveInArray(arr, i, +1); onChange() }) { Icon(Icons.Filled.KeyboardArrowDown, null) }
IconButton(onClick = { removeAt(arr, i); onChange() }) {
Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error)
}
}
}

var type by remember { mutableStateOf(item.optString("type").ifBlank { if (item.has("label")) "button" else "icon" }) }
ExposedDropdown(
value = type, label = "type",
options = listOf("icon", "button", "spacer")
) { sel ->
type = sel
item.put("type", sel)
onChange()
}

when (type) {
"icon" -> {
val icon = remember { mutableStateOf(item.optString("icon", "more_vert")) }
IconPickerField(icon, "icon") { sel ->
icon.value = sel
item.put("icon", sel)
onChange()
}
val action = remember { mutableStateOf(item.optString("actionId", "")) }
OutlinedTextField(
action.value,
{ v -> action.value = v; item.put("actionId", v); onChange() },
label = { Text("actionId (es. open_menu:menu_id)") }
)
val openMenuId = remember { mutableStateOf(item.optString("openMenuId", "")) }
OutlinedTextField(
openMenuId.value,
{ v -> openMenuId.value = v; if (v.isBlank()) item.remove("openMenuId") else item.put("openMenuId", v); onChange() },
label = { Text("openMenuId (opz.)") }
)
}

"button" -> {
val lbl = remember { mutableStateOf(item.optString("label", "")) }
OutlinedTextField(
lbl.value,
{ v -> lbl.value = v; item.put("label", v); onChange() },
label = { Text("label") }
)

val action = remember { mutableStateOf(item.optString("actionId", "")) }
OutlinedTextField(
action.value,
{ v -> action.value = v; item.put("actionId", v); onChange() },
label = { Text("actionId") }
)

// Styles must match what RenderBarItemsRow supports
var style by remember { mutableStateOf(item.optString("style", "text")) }
ExposedDropdown(
value = style, label = "style",
options = listOf("text", "outlined", "tonal", "primary")
) { sel ->
style = sel
item.put("style", sel)
onChange()
}
}

else -> { // "spacer"
var mode by remember { mutableStateOf(item.optString("mode", "fixed")) }
ExposedDropdown(
value = mode, label = "mode",
options = listOf("fixed", "expand")
) { sel ->
mode = sel
item.put("mode", sel)
onChange()
}
if (mode == "fixed") {
var width by remember { mutableStateOf(item.optDouble("widthDp", 16.0).toInt().toString()) }
ExposedDropdown(
value = width, label = "width (dp)",
options = listOf("8", "12", "16", "20", "24", "32", "40", "48", "64")
) { sel ->
width = sel
item.put("widthDp", sel.toDouble())
onChange()
}
}
}
}
}
}

Spacer(Modifier.height(8.dp))
}

AddBarItemButtons(arr = arr, onChange = onChange)
}


/* =========================================================
* TOP BAR INSPECTOR (proprietà base; azioni via BarItemsEditor)
* ========================================================= */
@Composable
private fun TopBarInspectorPanel(topBar: JSONObject, onChange: () -> Unit) {
Spacer(Modifier.height(8.dp))

var variant by remember { mutableStateOf(topBar.optString("variant","small")) }
ExposedDropdown(
value = variant, label = "variant",
options = listOf("small","center","medium","large")
) { sel -> variant = sel; topBar.put("variant", sel); onChange() }

val title = remember { mutableStateOf(topBar.optString("title","")) }
OutlinedTextField(
value = title.value,
onValueChange = { title.value = it; topBar.put("title", it); onChange() },
label = { Text("title") }
)

val subtitle = remember { mutableStateOf(topBar.optString("subtitle","")) }
OutlinedTextField(
value = subtitle.value,
onValueChange = {
subtitle.value = it
if (it.isBlank()) topBar.remove("subtitle") else topBar.put("subtitle", it)
onChange()
},

label = { Text("subtitle (opz.)") }
)

var scroll by remember { mutableStateOf(topBar.optString("scroll","none")) }
ExposedDropdown(
value = scroll, label = "scroll",
options = listOf("none","pinned","enterAlways","exitUntilCollapsed")
) { sel -> scroll = sel; if (sel=="none") topBar.remove("scroll") else topBar.put("scroll", sel); onChange() }

// Colori testo (legacy compat)
val titleColor = remember { mutableStateOf(topBar.optString("titleColor","onSurface")) }
NamedColorPickerPlus(current = titleColor.value, label = "titleColor", allowRoles = true) { pick ->
titleColor.value = pick
if (pick.isBlank()) topBar.remove("titleColor") else topBar.put("titleColor", pick)
onChange()
}

val actionsColor = remember { mutableStateOf(topBar.optString("actionsColor", topBar.optString("titleColor","onSurface"))) }
NamedColorPickerPlus(current = actionsColor.value, label = "actionsColor", allowRoles = true) { pick ->
actionsColor.value = pick
if (pick.isBlank()) topBar.remove("actionsColor") else topBar.put("actionsColor", pick)
onChange()
}

// Legacy gradient flag (mantenuto per compat; il renderer mappa in container)
val legacyGrad = remember { mutableStateOf(topBar.optJSONObject("gradient") != null) }
Row(verticalAlignment = Alignment.CenterVertically) {
Switch(checked = legacyGrad.value, onCheckedChange = {
legacyGrad.value = it
if (it) topBar.put("gradient", JSONObject().apply {
put("colors", JSONArray().put("primary").put("tertiary"))
put("direction", "vertical")
}) else topBar.remove("gradient")
onChange()
})
Spacer(Modifier.width(8.dp)); Text("Legacy gradient (compat)")
}
}

/* =========================================================
* CARD INSPECTOR + CONTAINER EDITOR
* ========================================================= */

@Composable
private fun CardInspectorPanel(working: JSONObject, onChange: () -> Unit) {
Text("Card – Proprietà", style = MaterialTheme.typography.titleMedium)

val action = remember { mutableStateOf(working.optString("clickActionId","")) }
OutlinedTextField(
value = action.value,
onValueChange = { action.value = it; working.put("clickActionId", it); onChange() },
label = { Text("clickActionId (opz.)") }
)

// Editor contenitore unificato
ContainerEditorSection(working, key = "container", title = "Card – Contenitore", onChange = onChange)
}

/* =========================================================
* CONTAINER INSPECTOR GENERICO (riusabile ovunque)
* ========================================================= */

@Composable
fun ContainerEditorSection(
owner: JSONObject,
key: String = "container",
title: String = "Contenitore",
onChange: () -> Unit
) {
Divider()
Text(title, style = MaterialTheme.typography.titleSmall)
var enabled by remember { mutableStateOf(owner.optJSONObject(key) != null) }
Row(verticalAlignment = Alignment.CenterVertically) {
Switch(checked = enabled, onCheckedChange = {
enabled = it
if (it) owner.put(key, owner.optJSONObject(key) ?: JSONObject().apply {
put("style","surface"); put("corner", 12); put("borderMode","none")
}) else owner.remove(key)
onChange()
})
Spacer(Modifier.width(8.dp)); Text("Abilita stile contenitore")
}
owner.optJSONObject(key)?.let { c ->
ContainerInspectorPanel(c, onChange)
}
}

@Composable
private fun ContainerInspectorPanel(container: JSONObject, onChange: () -> Unit) {
// STYLE: interfaccia semplificata "full / outlined / text"
val currentBorderMode = container.optString("borderMode","none")
var styleUi by remember { mutableStateOf(container.optString("style","full")) }
ExposedDropdown(
value = styleUi, label = "style",
options = listOf("text","outlined","topbottom","full","primary","tonal","surface")
) { sel -> styleUi = sel; container.put("style", sel); onChange() }


// BACKGROUND: gradient a due colori SEMPRE disponibile.
// 2° colore vuoto = singolo colore (nessun toggle).
val g1 = remember { mutableStateOf(container.optString("gradient1", container.optString("customColor",""))) }
NamedColorPickerPlus(current = g1.value, label = "bg color 1", allowRoles = true) { pick ->
g1.value = pick
if (pick.isBlank()) {
container.remove("gradient1")
container.remove("customColor")
} else {
container.put("gradient1", pick)
container.put("customColor", pick) // compat
}
onChange()
}

val g2 = remember { mutableStateOf(container.optString("gradient2","")) }
NamedColorPickerPlus(current = g2.value, label = "bg color 2 (nessuno = singolo)", allowRoles = true) { pick ->
g2.value = pick
if (pick.isBlank()) container.remove("gradient2") else container.put("gradient2", pick)
onChange()
}

var orient by remember { mutableStateOf(container.optString("gradientOrientation","horizontal")) }
ExposedDropdown(
value = orient, label = "gradient (orientation)",
options = listOf("horizontal","vertical")
) { sel -> orient = sel; container.put("gradientOrientation", sel); onChange() }

// Forma / corner
var shape by remember { mutableStateOf(container.optString("shape","rounded")) }
ExposedDropdown(
value = shape, label = "shape",
options = listOf("rounded","pill","cut")
) { sel -> shape = sel; container.put("shape", sel); onChange() }

var cornerOpt by remember { mutableStateOf(container.optDouble("corner",12.0).toInt().toString()) }
ExposedDropdown(
value = cornerOpt, label = "corner (dp)",
options = listOf("0","4","8","12","16","20","24","32")
) { sel ->
cornerOpt = sel
container.put("corner", sel.toDouble())
onChange()
}

// Elevation
var elevOpt by remember {
mutableStateOf(
(container.optDouble("elevationDp", if (styleUi == "full") 1.0 else 0.0)).toInt().toString()
)
}
ExposedDropdown(
value = elevOpt, label = "elevation (dp)",
options = listOf("0","1","2","3","4","6","8","12","16")
) { sel ->
elevOpt = sel
if (sel == "0") container.remove("elevationDp") else container.put("elevationDp", sel.toDouble())
onChange()
}

// Border mode + thickness + color (ruoli OK, niente crash)
var borderMode by remember { mutableStateOf(container.optString("borderMode", if (styleUi=="outlined") "full" else "none")) }
ExposedDropdown(
value = borderMode, label = "borderMode",
options = listOf("none","full","top_bottom")
) { sel ->
borderMode = sel; container.put("borderMode", sel); onChange()
}

val defaultTh = if (styleUi == "outlined" || styleUi == "topbottom") 1 else 0
var borderTh by remember {
mutableStateOf(
container.optDouble("borderThicknessDp", if (container.optString("borderMode","none") != "none") defaultTh.toDouble() else 0.0)
.toInt().toString()
)
}

ExposedDropdown(
value = borderTh, label = "borderThickness (dp)",
options = listOf("0","1","2","3","4","6","8")
) { sel ->
borderTh = sel
if (sel == "0") container.remove("borderThicknessDp")
else container.put("borderThicknessDp", sel.toDouble())
onChange()
}

var hAlign by remember { mutableStateOf(container.optString("hAlign","start")) }
ExposedDropdown(
value = hAlign, label = "content hAlign",
options = listOf("start","center","end")
) { sel -> hAlign = sel; container.put("hAlign", sel); onChange() }

var vAlign by remember { mutableStateOf(container.optString("vAlign","center")) }
ExposedDropdown(
value = vAlign, label = "content vAlign",
options = listOf("top","center","bottom")
) { sel -> vAlign = sel; container.put("vAlign", sel); onChange() }

val borderColor = remember { mutableStateOf(container.optString("borderColor","")) }
NamedColorPickerPlus(current = borderColor.value, label = "borderColor (ruoli OK)", allowRoles = true) { hex ->
borderColor.value = hex
if (hex.isBlank()) container.remove("borderColor") else container.put("borderColor", hex)
onChange()
}

Divider(); Text("Background image (opz.)", style = MaterialTheme.typography.titleSmall)
var imgEnabled by remember { mutableStateOf(container.optJSONObject("image") != null) }
Row(verticalAlignment = Alignment.CenterVertically) {
Switch(checked = imgEnabled, onCheckedChange = {
imgEnabled = it
if (it) container.put("image", container.optJSONObject("image") ?: JSONObject().apply {
put("source","")
put("contentScale","fill")
put("alpha", 1.0)
}) else container.remove("image")
onChange()
})
Spacer(Modifier.width(8.dp)); Text("Abilita immagine sfondo")
}
container.optJSONObject("image")?.let { img ->
ImagePickerRow(
label = "source",
current = img.optString("source",""),
onChange = { src -> img.put("source", src); onChange() },
onClear = { img.put("source",""); onChange() }
)
var scale by remember { mutableStateOf(img.optString("contentScale","fill")) }
ExposedDropdown(
value = scale, label = "contentScale",
options = listOf("fill","fit","crop")
) { sel -> scale = sel; img.put("contentScale", sel); onChange() }
val alpha = remember { mutableStateOf(img.optDouble("alpha",1.0).toString()) }
StepperField("alpha (0..1)", alpha, 0.1) { v -> img.put("alpha", v.coerceIn(0.0,1.0)); onChange() }
}

var widthMode by remember { mutableStateOf(container.optString("widthMode","wrap")) }
ExposedDropdown(
value = widthMode, label = "width",
options = listOf("wrap","fill","fixed_dp","fraction")
) { sel ->
widthMode = sel
container.put("widthMode", sel)
onChange()
}

when (widthMode) {
"fixed_dp" -> {
// Slider in dp con step regolare (es. 8dp), range safety
var w by remember { mutableStateOf(container.optDouble("widthDp", 160.0).toFloat()) }
val min = 80f
val max = 600f
val step = 8f
Slider(
value = w.coerceIn(min, max),
onValueChange = { v ->
val snapped = ((v / step).roundToInt() * step).coerceIn(min, max)
w = snapped
container.put("widthDp", snapped.toDouble())
onChange()
},
valueRange = min..max,
steps = ((max - min) / step).toInt() - 1
)
Text("${w.roundToInt()} dp", style = MaterialTheme.typography.bodySmall)
}
"fraction" -> {
// Slider percentuale con granularità 5% (da 5% a 100%)
var f by remember {
mutableStateOf(container.optDouble("widthFraction", 1.0).toFloat().coerceIn(0.05f, 1f))
}
Slider(
value = f,
onValueChange = { v ->
val snapped = ( (v * 20f).roundToInt().coerceIn(1, 20) / 20f )
f = snapped
container.put("widthFraction", snapped.toDouble())
onChange()
},
valueRange = 0.05f..1f,
steps = 19 // 5% -> 20 scatti inclusi gli estremi => steps = 20-1
)
Text("${(f * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
}
}



var heightMode by remember { mutableStateOf(container.optString("heightMode","wrap")) }
ExposedDropdown(
value = heightMode, label = "height",
options = listOf("wrap","fixed_dp")
) { sel -> heightMode = sel; container.put("heightMode", sel); onChange() }
if (heightMode == "fixed_dp") {
var h by remember { mutableStateOf(container.optDouble("heightDp", 48.0).toInt().toString()) }
ExposedDropdown(
value = h, label = "height (dp)",
options = listOf("24","32","40","48","56","64","96","128","160","200")
) { sel -> h = sel; container.put("heightDp", sel.toDouble()); onChange() }
}
}


/* =========================================================
* INSPECTOR dei vari BLOCCHI – esistenti
* ========================================================= */

@Composable
private fun ButtonRowInspectorPanel(working: JSONObject, onChange: () -> Unit) {
val buttons = working.optJSONArray("buttons") ?: JSONArray().also { working.put("buttons", it) }

Text("ButtonRow – Proprietà", style = MaterialTheme.typography.titleMedium)

var align by remember { mutableStateOf(working.optString("align", "center")) }
ExposedDropdown(
value = align,
label = "align",
options = listOf("start","center","end","space_between","space_around","space_evenly")
) { sel -> align = sel; working.writeAlign(sel); onChange() }

Divider()
Text("Bottoni", style = MaterialTheme.typography.titleMedium)

for (i in 0 until buttons.length()) {
val btn = buttons.getJSONObject(i)
ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
Row(
horizontalArrangement = Arrangement.SpaceBetween,
verticalAlignment = Alignment.CenterVertically,
modifier = Modifier.fillMaxWidth()
) {
Text("Bottone ${i + 1}", style = MaterialTheme.typography.labelLarge)
Row {
IconButton(onClick = { moveInArray(buttons, i, -1); onChange() }) { Icon(Icons.Filled.KeyboardArrowUp, null) }
IconButton(onClick = { moveInArray(buttons, i, +1); onChange() }) { Icon(Icons.Filled.KeyboardArrowDown, null) }
IconButton(onClick = { removeAt(buttons, i); onChange() }) {
Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error)
}
}
}

val label = remember { mutableStateOf(btn.optString("label", "")) }
OutlinedTextField(
value = label.value,
onValueChange = { label.value = it; btn.put("label", it); onChange() },
label = { Text("label") }
)

val icon = remember { mutableStateOf(btn.optString("icon", "")) }
IconPickerField(icon, "icon") { sel -> icon.value = sel; btn.put("icon", sel); onChange() }

var style by remember { mutableStateOf(btn.optString("style", "primary")) }
ExposedDropdown(
value = style, label = "style",
options = listOf("primary","tonal","outlined","text")
) { style = it; btn.put("style", it); onChange() }

var size by remember { mutableStateOf(btn.optString("size", "md")) }
ExposedDropdown(
value = size, label = "size",
options = listOf("xs","sm","md","lg","xl")
) { sel -> size = sel; btn.put("size", sel); onChange() }

var heightStr by remember {
mutableStateOf(btn.optDouble("heightDp", Double.NaN).let { if (it.isNaN()) "(auto)" else it.toInt().toString() })
}
ExposedDropdown(
value = heightStr, label = "height (dp)",
options = listOf("(auto)","32","36","40","44","48","52","56","64")
) { sel ->
heightStr = sel
if (sel == "(auto)") btn.remove("heightDp") else btn.put("heightDp", sel.toDouble())
onChange()
}

var widthStr by remember {
mutableStateOf(btn.optDouble("widthDp", Double.NaN).let { if (it.isNaN()) "(auto)" else it.toInt().toString() })
}
ExposedDropdown(
value = widthStr, label = "width (dp)",
options = listOf("(auto)","72","96","120","160","200")
) { sel ->
widthStr = sel
if (sel == "(auto)") btn.remove("widthDp") else btn.put("widthDp", sel.toDouble())
onChange()
}

val cornerStr = remember { mutableStateOf(btn.optDouble("corner", 20.0).toString()) }
StepperField("corner (dp)", cornerStr, 1.0) { v -> btn.put("corner", v); onChange() }

val customColor = remember { mutableStateOf(btn.optString("customColor", "")) }
NamedColorPickerPlus(
current = customColor.value,
label = "customColor (palette/ruoli)",
allowRoles = true
) { hex ->
customColor.value = hex
if (hex.isBlank()) btn.remove("customColor") else btn.put("customColor", hex)
onChange()
}

var press by remember { mutableStateOf(btn.optString("pressEffect", "none")) }
ExposedDropdown(
value = press, label = "pressEffect",
options = listOf("none","scale")
) { sel -> press = sel; btn.put("pressEffect", sel); onChange() }

val action = remember { mutableStateOf(btn.optString("actionId", "")) }
OutlinedTextField(
value = action.value,
onValueChange = { action.value = it; btn.put("actionId", it); onChange() },
label = { Text("actionId (es. nav:settings)") }
)
}
}
Spacer(Modifier.height(8.dp))
}

Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
Button(onClick = {
buttons.put(JSONObject("""{"label":"Nuovo","style":"text","icon":"add","actionId":""}"""))
onChange()
}) { Text("+ Aggiungi bottone") }
}
}

@Composable
private fun ToggleInspectorPanel(working: JSONObject, onChange: () -> Unit) {
Text("Toggle – Proprietà", style = MaterialTheme.typography.titleMedium)
val label = remember { mutableStateOf(working.optString("label","")) }
OutlinedTextField(value = label.value, onValueChange = {
label.value = it; working.put("label", it); onChange()
}, label = { Text("label") })
val bind = remember { mutableStateOf(working.optString("bind","toggle_1")) }
OutlinedTextField(value = bind.value, onValueChange = {
bind.value = it; working.put("bind", it); onChange()
}, label = { Text("bind (boolean)") })
}

@Composable
private fun SliderInspectorPanel(working: JSONObject, onChange: () -> Unit) {
Text("Slider – Proprietà", style = MaterialTheme.typography.titleMedium)
val label = remember { mutableStateOf(working.optString("label","")) }
OutlinedTextField(value = label.value, onValueChange = {
label.value = it; working.put("label", it); onChange()
}, label = { Text("label") })

val bind = remember { mutableStateOf(working.optString("bind","value_1")) }
OutlinedTextField(value = bind.value, onValueChange = {
bind.value = it; working.put("bind", it); onChange()
}, label = { Text("bind (numero)") })

val minS = remember { mutableStateOf(working.optDouble("min",0.0).toString()) }
val maxS = remember { mutableStateOf(working.optDouble("max",10.0).toString()) }
val stepS = remember { mutableStateOf(working.optDouble("step",1.0).toString()) }
StepperField("min", minS, 1.0) { v -> working.put("min", v); onChange() }
StepperField("max", maxS, 1.0) { v -> working.put("max", v); onChange() }
StepperField("step", stepS, 0.5) { v -> working.put("step", v); onChange() }

val unit = remember { mutableStateOf(working.optString("unit","")) }
OutlinedTextField(value = unit.value, onValueChange = {
unit.value = it; working.put("unit", it); onChange()
}, label = { Text("unit (opz.)") })
}

@Composable
private fun ChipRowInspectorPanel(working: JSONObject, onChange: () -> Unit) {
Text("ChipRow – Proprietà", style = MaterialTheme.typography.titleMedium)

val chips = working.optJSONArray("chips") ?: JSONArray().also { working.put("chips", it) }
for (i in 0 until chips.length()) {
val chip = chips.getJSONObject(i)
ElevatedCard {
Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
Text("Chip ${i+1}", style = MaterialTheme.typography.labelLarge)
Row {
IconButton(onClick = { moveInArray(chips, i, -1); onChange() }) { Icon(Icons.Filled.KeyboardArrowUp, null) }
IconButton(onClick = { moveInArray(chips, i, +1); onChange() }) { Icon(Icons.Filled.KeyboardArrowDown, null) }
IconButton(onClick = { removeAt(chips, i); onChange() }) { Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error) }
}
}
val label = remember { mutableStateOf(chip.optString("label","")) }
OutlinedTextField(value = label.value, onValueChange = {
label.value = it; chip.put("label", it); onChange()
}, label = { Text("label") })

val bind = remember { mutableStateOf(chip.optString("bind","")) }
OutlinedTextField(value = bind.value, onValueChange = {
bind.value = it; if (it.isBlank()) chip.remove("bind") else chip.put("bind", it); onChange()
}, label = { Text("bind (multi‑selezione: boolean per chip)") })

val value = remember { mutableStateOf(chip.opt("value")?.toString() ?: "") }
OutlinedTextField(value = value.value, onValueChange = {
value.value = it
if (it.isBlank()) chip.remove("value") else chip.put("value", it)
onChange()
}, label = { Text("value (opz., abilita selezione singola su bind di gruppo)") })
}
}
Spacer(Modifier.height(8.dp))
}

Button(onClick = {
chips.put(JSONObject("""{"label":"Nuovo","bind":"chip_new"}""")); onChange()
}) { Text("+ Aggiungi chip") }

Divider()
val textSize = remember {
mutableStateOf(working.optDouble("textSizeSp", Double.NaN).let { if (it.isNaN()) "" else it.toString() })
}
ExposedDropdown(
value = if (textSize.value.isBlank()) "(default)" else textSize.value,
label = "textSize (sp)",
options = listOf("(default)","8","9","10","11","12","14","16","18","20","22","24")
) { sel ->
val v = if (sel == "(default)") "" else sel
textSize.value = v
if (v.isBlank()) working.remove("textSizeSp") else working.put("textSizeSp", v.toDouble())
onChange()
}
var fontWeight by remember { mutableStateOf(working.optString("fontWeight","")) }
ExposedDropdown(
value = if (fontWeight.isBlank()) "(default)" else fontWeight, label = "fontWeight",
options = listOf("w300","w400","w500","w600","w700","(default)")
) { sel ->
val v = if (sel == "(default)") "" else sel
fontWeight = v
if (v.isBlank()) working.remove("fontWeight") else working.put("fontWeight", v)
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

val textColor = remember { mutableStateOf(working.optString("textColor","")) }
NamedColorPickerPlus(current = textColor.value, label = "textColor") { hex ->
textColor.value = hex
if (hex.isBlank()) working.remove("textColor") else working.put("textColor", hex)
onChange()
}
}

@Composable
private fun TabsInspectorPanel(working: JSONObject, onChange: () -> Unit) {
Text("Tabs – Proprietà", style = MaterialTheme.typography.titleMedium)
val tabs = working.optJSONArray("tabs") ?: JSONArray().also { working.put("tabs", it) }

val count = tabs.length().coerceAtLeast(1)
val initIdxState = remember { mutableStateOf(working.optInt("initialIndex", 0).coerceIn(0, count-1).toString()) }
ExposedDropdown(
value = initIdxState.value,
label = "initialIndex",
options = (0 until count).map { it.toString() }
) { sel -> initIdxState.value = sel; working.put("initialIndex", sel.toInt()); onChange() }

Divider()
Text("Tab", style = MaterialTheme.typography.titleMedium)

for (i in 0 until tabs.length()) {
val t = tabs.getJSONObject(i)
ElevatedCard {
Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
Text("Tab ${i+1}", style = MaterialTheme.typography.labelLarge)
Row {
IconButton(onClick = { moveInArray(tabs, i, -1); onChange() }) { Icon(Icons.Filled.KeyboardArrowUp, null) }
IconButton(onClick = { moveInArray(tabs, i, +1); onChange() }) { Icon(Icons.Filled.KeyboardArrowDown, null) }
IconButton(onClick = { removeAt(tabs, i); onChange() }) { Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error) }
}
}
val lbl = remember { mutableStateOf(t.optString("label","")) }
OutlinedTextField(lbl.value, { lbl.value = it; t.put("label", it); onChange() }, label = { Text("label") })
}
}
Spacer(Modifier.height(8.dp))
}

Button(onClick = {
tabs.put(JSONObject("""{"label":"Nuova tab","blocks":[{"type":"SectionHeader","title":"Contenuto nuova tab"}]}"""))
onChange()
}) { Text("+ Aggiungi tab") }
}

@Composable
private fun MetricsGridInspectorPanel(working: JSONObject, onChange: () -> Unit) {
Text("MetricsGrid – Proprietà", style = MaterialTheme.typography.titleMedium)

val cols = remember { mutableStateOf(working.optInt("columns", 2).toString()) }
ExposedDropdown(
value = cols.value, label = "columns",
options = listOf("1","2","3")
) { sel -> cols.value = sel; working.put("columns", sel.toInt()); onChange() }

Divider(); Text("Tiles", style = MaterialTheme.typography.titleMedium)

val tiles = working.optJSONArray("tiles") ?: JSONArray().also { working.put("tiles", it) }
for (i in 0 until tiles.length()) {
val tile = tiles.getJSONObject(i)
ElevatedCard {
Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
Text("Tile ${i+1}", style = MaterialTheme.typography.labelLarge)
Row {
IconButton(onClick = { moveInArray(tiles, i, -1); onChange() }) { Icon(Icons.Filled.KeyboardArrowUp, null) }
IconButton(onClick = { moveInArray(tiles, i, +1); onChange() }) { Icon(Icons.Filled.KeyboardArrowDown, null) }
IconButton(onClick = { removeAt(tiles, i); onChange() }) { Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error) }
}
}
val lbl = remember { mutableStateOf(tile.optString("label","")) }
OutlinedTextField(lbl.value, { lbl.value = it; tile.put("label", it); onChange() }, label = { Text("label") })
}
}
Spacer(Modifier.height(8.dp))
}
Button(onClick = { tiles.put(JSONObject("""{"label":"Nuova"}""")); onChange() }) { Text("+ Aggiungi tile") }
}

@Composable
private fun ProgressInspectorPanel(working: JSONObject, onChange: () -> Unit) {
Text("Progress – Proprietà", style = MaterialTheme.typography.titleMedium)

val label = remember { mutableStateOf(working.optString("label","")) }
OutlinedTextField(value = label.value, onValueChange = {
label.value = it; working.put("label", it); onChange()
}, label = { Text("label") })

val value = remember { mutableStateOf(working.optDouble("value", 0.0).toString()) }
StepperField("value (0–100)", value, 1.0) { v ->
working.put("value", v.coerceIn(0.0, 100.0)); onChange()
}

val showPercent = remember { mutableStateOf(working.optBoolean("showPercent", true)) }
Row(verticalAlignment = Alignment.CenterVertically) {
Switch(checked = showPercent.value, onCheckedChange = {
showPercent.value = it; working.put("showPercent", it); onChange()
})
Spacer(Modifier.width(8.dp)); Text("mostra %")
}

val color = remember { mutableStateOf(working.optString("color","primary")) }
NamedColorPickerPlus(
current = color.value,
label = "color",
allowRoles = true
) { pick ->
color.value = pick
if (pick.isBlank()) working.remove("color") else working.put("color", pick)
onChange()
}
}

@Composable
private fun AlertInspectorPanel(working: JSONObject, onChange: () -> Unit) {
Text("Alert – Proprietà", style = MaterialTheme.typography.titleMedium)

var severity by remember { mutableStateOf(working.optString("severity","info")) }
ExposedDropdown(
value = severity, label = "severity",
options = listOf("info","success","warning","error")
) { sel -> severity = sel; working.put("severity", sel); onChange() }

val title = remember { mutableStateOf(working.optString("title","")) }
OutlinedTextField(value = title.value, onValueChange = {
title.value = it; working.put("title", it); onChange()
}, label = { Text("title") })

val message = remember { mutableStateOf(working.optString("message","")) }
OutlinedTextField(value = message.value, onValueChange = {
message.value = it; working.put("message", it); onChange()
}, label = { Text("message") })

val action = remember { mutableStateOf(working.optString("actionId","")) }
OutlinedTextField(value = action.value, onValueChange = {
action.value = it; working.put("actionId", it); onChange()
}, label = { Text("actionId (opz.)") })
}

@Composable
private fun ImageInspectorPanel(working: JSONObject, onChange: () -> Unit) {
Text("Image – Proprietà", style = MaterialTheme.typography.titleMedium)

// Sorgente con picker cartelle
ImagePickerRow(
label = "source",
current = working.optString("source",""),
onChange = { src -> working.put("source", src); onChange() },
onClear = { working.put("source",""); onChange() }
)

val height = remember { mutableStateOf(working.optDouble("heightDp", 160.0).toString()) }
StepperField("height (dp)", height, 4.0) { v ->
working.put("heightDp", v.coerceAtLeast(64.0)); onChange()
}

val corner = remember { mutableStateOf(working.optDouble("corner", 12.0).toString()) }
StepperField("corner (dp)", corner, 2.0) { v ->
working.put("corner", v.coerceAtLeast(0.0)); onChange()
}

var scale by remember { mutableStateOf(working.optString("contentScale","fit")) }
ExposedDropdown(
value = scale, label = "contentScale",
options = listOf("fit","crop","fill")
) { sel -> scale = sel; working.put("contentScale", sel); onChange() }
}

@Composable
private fun SectionHeaderInspectorPanel(working: JSONObject, onChange: () -> Unit) {
Text("SectionHeader – Proprietà", style = MaterialTheme.typography.titleMedium)

val title = remember { mutableStateOf(working.optString("title","")) }
OutlinedTextField(
value = title.value,
onValueChange = { title.value = it; working.put("title", it); onChange() },
label = { Text("title") }
)

// allineamento
var align by remember { mutableStateOf(working.optString("align","start")) }
ExposedDropdown(
value = align, label = "align",
options = listOf("start","center","end")
) { sel -> align = sel; working.writeAlign(sel); onChange() }

// font size (sp)
val textSize = remember {
mutableStateOf(working.optDouble("textSizeSp", Double.NaN).let { if (it.isNaN()) "" else it.toString() })
}
ExposedDropdown(
value = if (textSize.value.isBlank()) "(default)" else textSize.value,
label = "textSize (sp)",
options = listOf("(default)","14","16","18","20","22","24","28","32","36")
) { sel ->
val v = if (sel == "(default)") "" else sel
textSize.value = v
if (v.isBlank()) working.remove("textSizeSp") else working.put("textSizeSp", v.toDouble())
onChange()
}

// peso + famiglia + colore
var fontWeight by remember { mutableStateOf(working.optString("fontWeight","")) }
ExposedDropdown(
value = if (fontWeight.isBlank()) "(default)" else fontWeight,
label = "fontWeight",
options = listOf("w300","w400","w500","w600","w700","(default)")
) { sel ->
val v = if (sel == "(default)") "" else sel
fontWeight = v
if (v.isBlank()) working.remove("fontWeight") else working.put("fontWeight", v)
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

val textColor = remember { mutableStateOf(working.optString("textColor","")) }
NamedColorPickerPlus(current = textColor.value, label = "textColor") { hex ->
textColor.value = hex
if (hex.isBlank()) working.remove("textColor") else working.put("textColor", hex)
onChange()
}
}


/* --------- ListInspectorPanel --------- */
@Composable
private fun ListInspectorPanel(working: JSONObject, onChange: () -> Unit) {
Text("List – Proprietà testo", style = MaterialTheme.typography.titleMedium)

val textSize = remember { mutableStateOf(working.optDouble("textSizeSp", Double.NaN).let { if (it.isNaN()) "" else it.toString() }) }
ExposedDropdown(
value = if (textSize.value.isBlank()) "(default)" else textSize.value,
label = "textSize (sp)",
options = listOf("(default)","8","9","10","11","12","14","16","18","20","22","24")
) { sel ->
val v = if (sel == "(default)") "" else sel
textSize.value = v
if (v.isBlank()) working.remove("textSizeSp") else working.put("textSizeSp", v.toDouble())
onChange()
}

var align by remember { mutableStateOf(working.optString("align","start")) }
ExposedDropdown(
value = align, label = "align",
options = listOf("start","center","end")
) { sel -> align = sel; working.writeAlign(sel); onChange() }

var fontFamily by remember { mutableStateOf(working.optString("fontFamily", "")) }
ExposedDropdown(
value = if (fontFamily.isBlank()) "(default)" else fontFamily,
label = "fontFamily",
options = listOf("sans","serif","monospace","cursive","(default)")
) { sel ->
val v = if (sel == "(default)") "" else sel
fontFamily = v
if (v.isBlank()) working.remove("fontFamily") else working.put("fontFamily", v)
onChange()
}

var fontWeight by remember { mutableStateOf(working.optString("fontWeight", "")) }
ExposedDropdown(
value = if (fontWeight.isBlank()) "(default)" else fontWeight,
label = "fontWeight",
options = listOf("w300","w400","w500","w600","w700","(default)")
) { sel ->
val v = if (sel == "(default)") "" else sel
fontWeight = v
if (v.isBlank()) working.remove("fontWeight") else working.put("fontWeight", v)
onChange()
}

val textColor = remember { mutableStateOf(working.optString("textColor","")) }
NamedColorPickerPlus(
current = textColor.value,
label = "textColor"
) { hex ->
textColor.value = hex
if (hex.isBlank()) working.remove("textColor") else working.put("textColor", hex)
onChange()
}
}

/* =========================================================
* HELPERS: mapping, pickers, utils
* ========================================================= */


// --- ALIGN helpers: singola fonte di verità ---
private fun JSONObject.readAlign(): String =
optString("align", optString("textAlign", "start")).lowercase()

private fun JSONObject.writeAlign(value: String) {
put("align", value.lowercase())
// teniamo compat ma rimuoviamo la chiave legacy se presente
remove("textAlign")
}

private fun mapTextAlign(v: String): TextAlign = when (v.lowercase()) {
"center" -> TextAlign.Center
"end", "right" -> TextAlign.End
else -> TextAlign.Start
}


private fun sizeModifier(size: String): Modifier = when (size) {
"xs" -> Modifier.height(32.dp)
"sm" -> Modifier.height(36.dp)
"lg" -> Modifier.height(52.dp)
"xl" -> Modifier.height(56.dp)
else -> Modifier.height(40.dp)
}

@Composable
private fun IconText(label: String, icon: String) {
Row(
verticalAlignment = Alignment.CenterVertically,
horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
NamedIconEx(icon, null)
Text(label)
}
}

@Composable
private fun mapButtonColors(style: String, tint: String): Triple<Color, Color, Dp> {
val cs = MaterialTheme.colorScheme

val (baseContainer, baseContent) = when (tint) {
"success" -> Pair(cs.tertiary, cs.onTertiary)
"warning" -> Pair(Color(0xFFFFD54F), Color(0xFF3E2723))
"error"   -> Pair(cs.errorContainer, cs.onErrorContainer)
else      -> Pair(cs.primary, cs.onPrimary)
}

return when (style) {
"outlined" -> Triple(
Color.Transparent,
when (tint) {
"success" -> cs.tertiary
"warning" -> Color(0xFF8D6E63)
"error"   -> cs.error
else      -> cs.primary
},
1.dp
)
"text" -> Triple(
Color.Transparent,
when (tint) {
"success" -> cs.tertiary
"warning" -> Color(0xFF8D6E63)
"error"   -> cs.error
else      -> cs.primary
},
0.dp
)
"tonal" -> Triple(cs.secondaryContainer, cs.onSecondaryContainer, 0.dp)
else -> Triple(baseContainer, baseContent, 0.dp)
}
}

/* ---- Pickers ---- */

private val ICONS = listOf(
"settings", "more_vert", "tune",
"play_arrow", "pause", "stop", "add",
"flag", "queue_music", "widgets", "palette",
"home", "menu", "close", "more_horiz", "list", "tab", "grid_on",
"directions_run", "directions_walk", "directions_bike",
"fitness_center", "timer", "timer_off", "watch_later",
"map", "my_location", "place", "speed",
"bolt", "local_fire_department", "sports_score", "toggle_on"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconPickerField(
value: MutableState<String>,
label: String,
onSelected: (String) -> Unit
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


@Composable
private fun StepperField(
label: String,
state: MutableState<String>,
step: Double = 1.0,
onChangeValue: (Double) -> Unit
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

/* ---- Image Picker row (riusabile) ---- */

@Composable
private fun ImagePickerRow(
label: String,
current: String,
onChange: (String) -> Unit,
onClear: () -> Unit,
previewHeight: Dp = 80.dp
) {
val ctx = LocalContext.current
val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
if (uri != null) {
try {
ctx.contentResolver.takePersistableUriPermission(
uri,
Intent.FLAG_GRANT_READ_URI_PERMISSION
)
} catch (_: Exception) { /* permesso già preso o non necessario */ }
onChange("uri:${uri}")
}
}

Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
OutlinedTextField(
value = current,
onValueChange = { onChange(it) },
label = { Text(label) },
modifier = Modifier.weight(1f)
)
OutlinedButton(onClick = { launcher.launch(arrayOf("image/*")) }) { Text("Sfoglia…") }
TextButton(onClick = { onClear() }) { Text("Rimuovi") }
}
if (current.isNotBlank()) {
val isRes = current.startsWith("res:")
val isUri = current.startsWith("uri:") || current.startsWith("content:") || current.startsWith("file:")
Box(
Modifier
.fillMaxWidth()
.height(previewHeight)
.clip(RoundedCornerShape(8.dp))
) {
when {
isRes -> {
val id = ctx.resources.getIdentifier(current.removePrefix("res:"), "drawable", ctx.packageName)
if (id != 0) {
Image(
painter = painterResource(id),
contentDescription = null,
contentScale = ContentScale.Crop,
modifier = Modifier.matchParentSize()
)
}
}
isUri -> {
val bmp = rememberImageBitmapFromUri(current.removePrefix("uri:"))
if (bmp != null) {
Image(
bitmap = bmp,
contentDescription = null,
contentScale = ContentScale.Crop,
modifier = Modifier.matchParentSize()
)
}
}
}
}
}
}
}

/* ---- Caricamento bitmap da URI ---- */

@Composable
private fun rememberImageBitmapFromUri(uriString: String?): ImageBitmap? {
val ctx = LocalContext.current
val key = uriString ?: ""
val state by produceState<ImageBitmap?>(null, key) {
if (key.isBlank()) {
value = null
return@produceState
}
val real = key
value = withContext(Dispatchers.IO) {
try {
val uri = Uri.parse(real)
ctx.contentResolver.openInputStream(uri)?.use { input ->
BitmapFactory.decodeStream(input)?.asImageBitmap()
}
} catch (e: Exception) {
null
}
}
}
return state
}

/* ---- Icon helper ---- */

@Composable
private fun NamedIconEx(name: String?, contentDescription: String?) {
val __ctx = LocalContext.current
if (name.isNullOrBlank()) {
Text("."); return
}

// icona da risorsa drawable
if (name.startsWith("res:")) {
val resName = name.removePrefix("res:")
val id = __ctx.resources.getIdentifier(resName, "drawable", __ctx.packageName)
if (id != 0) { Icon(painterResource(id), contentDescription); return }
}

// icona da uri/file/content (raster)
if (name.startsWith("uri:") || name.startsWith("content:") || name.startsWith("file:")) {
val bmp = rememberImageBitmapFromUri(name.removePrefix("uri:"))
if (bmp != null) {
Image(
bitmap = bmp,
contentDescription = contentDescription,
modifier = Modifier.size(24.dp)
)
return
}
}

// icone Material note
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
"bolt" -> Icons.Filled.Bolt
else -> null
}
if (image != null) Icon(image, contentDescription) else Text(".")
}


@Composable
private fun parseColorOrRole(source: String?): Color? {
if (source == null || source.isBlank()) return null
val cs = MaterialTheme.colorScheme
return when (source.lowercase()) {
"primary"      -> cs.primary
"onprimary"    -> cs.onPrimary
"secondary"    -> cs.secondary
"onsecondary"  -> cs.onSecondary
"tertiary"     -> cs.tertiary
"ontertiary"   -> cs.onTertiary
"surface"      -> cs.surface
"onsurface"    -> cs.onSurface
"background"   -> cs.background
"onbackground" -> cs.onBackground
"error"        -> cs.error
"onerror"      -> cs.onError
"success"      -> cs.tertiary       // mappatura “di comodo” per ruoli custom
"warning"      -> cs.secondary
"info"         -> cs.primary
else -> runCatching { Color(android.graphics.Color.parseColor(source)) }.getOrNull()
}
}

private fun bestOnColor(bg: Color): Color =
if (bg.luminance() > 0.5f) Color.Black else Color.White


private val NAMED_SWATCHES = linkedMapOf(
// Neutri
"White" to 0xFFFFFFFF.toInt(),
"Black" to 0xFF000000.toInt(),
"Gray50" to 0xFFFAFAFA.toInt(), "Gray100" to 0xFFF5F5F5.toInt(), "Gray200" to 0xFFEEEEEE.toInt(),
"Gray300" to 0xFFE0E0E0.toInt(), "Gray400" to 0xFFBDBDBD.toInt(),
"Gray500" to 0xFF9E9E9E.toInt(), "Gray600" to 0xFF757575.toInt(), "Gray700" to 0xFF616161.toInt(), "Gray800" to 0xFF424242.toInt(), "Gray900" to 0xFF212121.toInt(),

// Material-like
"Red" to 0xFFE53935.toInt(), "RedDark" to 0xFFC62828.toInt(), "RedLight" to 0xFFEF5350.toInt(),
"Pink" to 0xFFD81B60.toInt(), "PinkDark" to 0xFFC2185B.toInt(), "PinkLight" to 0xFFF06292.toInt(),
"Purple" to 0xFF8E24AA.toInt(), "PurpleDark" to 0xFF6A1B9A.toInt(), "PurpleLight" to 0xFFBA68C8.toInt(),
"DeepPurple" to 0xFF5E35B1.toInt(), "Indigo" to 0xFF3949AB.toInt(),
"Blue" to 0xFF1E88E5.toInt(), "BlueDark" to 0xFF1565C0.toInt(), "BlueLight" to 0xFF64B5F6.toInt(),
"LightBlue" to 0xFF039BE5.toInt(), "Cyan" to 0xFF00ACC1.toInt(),
"Teal" to 0xFF00897B.toInt(), "TealLight" to 0xFF26A69A.toInt(),
"Green" to 0xFF43A047.toInt(), "GreenDark" to 0xFF2E7D32.toInt(), "GreenLight" to 0xFF66BB6A.toInt(),
"LightGreen" to 0xFF7CB342.toInt(), "Lime" to 0xFFC0CA33.toInt(),
"Yellow" to 0xFFFDD835.toInt(), "Amber" to 0xFFFFB300.toInt(),
"Orange" to 0xFFFB8C00.toInt(), "DeepOrange" to 0xFFF4511E.toInt(),
"Brown" to 0xFF6D4C41.toInt(), "BlueGrey" to 0xFF546E7A.toInt()
)

private val MATERIAL_ROLES = listOf(
"primary","secondary","tertiary","error","surface",
"onPrimary","onSecondary","onTertiary","onSurface","onError"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NamedColorPickerPlus(
current: String,
label: String,
allowRoles: Boolean = false,
onPick: (String) -> Unit
) {
var expanded by remember { mutableStateOf(false) }
val display = if (current.isBlank()) "(default)" else current

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
if (allowRoles) {
val cs = MaterialTheme.colorScheme
fun roleColor(role: String): Color = when (role) {
"primary","onPrimary"     -> cs.primary
"secondary","onSecondary" -> cs.secondary
"tertiary","onTertiary"   -> cs.tertiary
"error","onError"         -> cs.error
"surface","onSurface"     -> cs.surface
else -> cs.primary
}
MATERIAL_ROLES.forEach { role ->
val c = roleColor(role)
DropdownMenuItem(
leadingIcon = { Box(Modifier.size(16.dp).background(c, RoundedCornerShape(3.dp))) },
text = { Text(role) },
onClick = { onPick(role); expanded = false }
)
}
Divider()
}
NAMED_SWATCHES.forEach { (name, argb) ->
val c = Color(argb)
DropdownMenuItem(
leadingIcon = { Box(Modifier.size(16.dp).background(c, RoundedCornerShape(3.dp))) },
text = { Text(name) },
onClick = {
val hex = "#%06X".format(argb and 0xFFFFFF)
onPick(hex); expanded = false
}
)
}
DropdownMenuItem(text = { Text("(default)") }, onClick = { onPick(""); expanded = false })
}
}
}


private fun collectMenus(root: JSONObject): Map<String, JSONArray> {
val map = mutableMapOf<String, JSONArray>()
fun walk(n: Any?) {
when (n) {
is JSONObject -> {
if (n.optString("type") == "Menu") {
val id = n.optString("id", "")
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
node = (node as JSONArray).opt(idx) ?: return null; i++
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

private fun insertBlockAndReturnPath(root: JSONObject, selectedPath: String?, block: JSONObject, position: String): String {
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

val insertIndex = when (position) { "before" -> idx; else -> idx + 1 }.coerceIn(0, parentArr.length())

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
val newIdx = (idx + delta).coerceIn(0, arr.length() - 1)
if (newIdx == idx) return path

val items = mutableListOf<Any?>()
for (i in 0 until arr.length()) items.add(arr.get(i))
val item = items.removeAt(idx)
items.add(newIdx, item)

while (arr.length() > 0) arr.remove(arr.length() - 1)
items.forEach { arr.put(it) }

val parentPath = path.substringBeforeLast("/")
return "$parentPath/$newIdx"
}

private fun moveInArray(arr: JSONArray, index: Int, delta: Int) {
if (index < 0 || index >= arr.length()) return
val newIdx = (index + delta).coerceIn(0, arr.length() - 1)
if (newIdx == index) return
val tmp = mutableListOf<Any?>()
for (i in 0 until arr.length()) tmp.add(arr.get(i))
val it = tmp.removeAt(index)
tmp.add(newIdx, it)
while (arr.length() > 0) arr.remove(arr.length() - 1)
tmp.forEach { arr.put(it) }
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
while (arr.length() > 0) arr.remove(arr.length() - 1)
tmp.forEach { arr.put(it) }
}

private fun remove(root: JSONObject, path: String) {
val p = getParentAndIndex(root, path) ?: return
val (arr, idx) = p
val tmp = mutableListOf<Any?>()
for (i in 0 until arr.length()) if (i != idx) tmp.add(arr.get(i))
while (arr.length() > 0) arr.remove(arr.length() - 1)
tmp.forEach { arr.put(it) }
}

private fun removeAt(arr: JSONArray, index: Int) {
if (index < 0 || index >= arr.length()) return
val tmp = mutableListOf<Any?>()
for (i in 0 until arr.length()) if (i != index) tmp.add(arr.get(i))
while (arr.length() > 0) arr.remove(arr.length() - 1)
tmp.forEach { arr.put(it) }
}

/* =========================================================
* BLUEPRINTS
* ========================================================= */

private fun newSectionHeader() = JSONObject("""{"type":"SectionHeader","title":"Nuova sezione"}""")

private fun newButtonRow() = JSONObject(
"""
{"type":"ButtonRow","align":"center","buttons":[
{"label":"Start","style":"primary","icon":"play_arrow","size":"md","tint":"default","shape":"rounded","corner":20,"pressEffect":"scale","actionId":"start_run"},
{"label":"Pausa","style":"tonal","icon":"pause","size":"md","tint":"default","shape":"rounded","corner":20,"actionId":"pause_run"},
{"label":"Stop","style":"outlined","icon":"stop","size":"md","tint":"error","shape":"rounded","corner":20,"actionId":"stop_run","confirm":true}
]}
""".trimIndent()
)

private fun newSpacer()   = JSONObject("""{"type":"Spacer","height":8}""")
private fun newDividerV() = JSONObject("""{"type":"DividerV","thickness":1,"height":24}""")

private fun newCard() = JSONObject(
"""
{"type":"Card","variant":"elevated","clickActionId":"nav:run",
"blocks":[
{"type":"SectionHeader","title":"Card esempio","style":"titleSmall","align":"start"},
{"type":"Divider"}
]}
""".trimIndent()
)

private fun newFab() = JSONObject("""{"type":"Fab","icon":"play_arrow","label":"Start","variant":"extended","actionId":"start_run"}""")

private fun newIconButton(menuId: String = "more_menu") =
JSONObject("""{"type":"IconButton","icon":"more_vert","openMenuId":"$menuId"}""")

private fun newMenu(menuId: String = "more_menu") = JSONObject(
"""
{"type":"Menu","id":"$menuId","items":[
{"icon":"tune","label":"Layout Lab","actionId":"open_layout_lab"},
{"icon":"palette","label":"Theme Lab","actionId":"open_theme_lab"},
{"icon":"settings","label":"Impostazioni","actionId":"nav:settings"}
]}
""".trimIndent()
)

private fun newProgress() = JSONObject("""{ "type":"Progress", "label":"Avanzamento", "value": 40, "color": "primary", "showPercent": true }""")
private fun newAlert()    = JSONObject("""{ "type":"Alert", "severity":"info", "title":"Titolo avviso", "message":"Testo dell'avviso", "actionId": "" }""")
private fun newImage()    = JSONObject("""{ "type":"Image", "source":"res:ic_launcher_foreground", "heightDp": 160, "corner": 12, "contentScale":"fit" }""")

private fun newTabs() = JSONObject(
"""
{"type":"Tabs","initialIndex":0,"tabs":[
{"label":"Tab 1","blocks":[{"type":"SectionHeader","title":"Tab 1","style":"titleSmall","align":"start"}]},
{"label":"Tab 2","blocks":[{"type":"SectionHeader","title":"Tab 2","style":"titleSmall","align":"start"}]}
]}
""".trimIndent()
)

private fun newChipRow() = JSONObject(
"""
{"type":"ChipRow","chips":[
{"label":"Easy","bind":"level","value":"easy"},
{"label":"Medium","bind":"level","value":"medium"},
{"label":"Hard","bind":"level","value":"hard"}
], "textSizeSp":14}
""".trimIndent()
)

private fun newMetricsGrid() = JSONObject("""{"type":"MetricsGrid","columns":2,"tiles":[{"label":"Pace"},{"label":"Heart"}]}""")

private fun newSlider() = JSONObject("""{"type":"Slider","label":"Pace","bind":"pace","min":3.0,"max":7.0,"step":0.1,"unit":" min/km"}""")

private fun newToggle() = JSONObject("""{"type":"Toggle","label":"Attiva opzione","bind":"toggle_1"}""")

private fun newList() = JSONObject(
"""
{"type":"List","align":"start","items":[
{"title":"Voce 1","subtitle":"Sottotitolo 1","actionId":"list:1"},
{"title":"Voce 2","subtitle":"Sottotitolo 2","actionId":"list:2"}
]}
""".trimIndent()
)

/* =========================================================
* TEXT STYLE OVERRIDES
* ========================================================= */
@Composable
private fun applyTextStyleOverrides(owner: JSONObject, base: TextStyle): TextStyle {
var st = base
val sizeSp = owner.optDouble("textSizeSp", Double.NaN)
if (!sizeSp.isNaN()) st = st.copy(fontSize = sizeSp.sp)
parseFontWeight(owner.optString("fontWeight","").takeIf { it.isNotBlank() })?.let {
st = st.copy(fontWeight = it)
}
fontFamilyFromName(owner.optString("fontFamily","").takeIf { it.isNotBlank() })?.let {
st = st.copy(fontFamily = it)
}
parseColorOrRole(owner.optString("textColor","").takeIf { it.isNotBlank() })?.let {
st = st.copy(color = it)
}
if (owner.optBoolean("italic", false)) {
st = st.copy(fontStyle = FontStyle.Italic)
}
return st
}

private fun newRow() = JSONObject(
"""{"type":"Row","gapDp":8,"items":[
       {"type":"SectionHeader","title":"A"},
       {"type":"SpacerH","mode":"expand"},
       {"type":"SectionHeader","title":"B"}
   ]}"""
)
private fun newSpacerH() = JSONObject("""{"type":"SpacerH","mode":"expand","widthDp":16}""")
