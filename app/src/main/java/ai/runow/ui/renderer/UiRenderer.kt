@file:OptIn(ExperimentalMaterial3Api::class)

package ai.runow.ui.renderer

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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets



@Composable
private fun ResizableRow(
    rowBlock: JSONObject,
    path: String,
    uiState: MutableMap<String, Any>,
    menus: Map<String, JSONArray>,
    dispatch: (String) -> Unit,
    designerMode: Boolean,
    onSelect: (String) -> Unit,
    onOpenInspector: (String) -> Unit
) {
    val items = rowBlock.optJSONArray("items") ?: JSONArray()
    val gap = rowBlock.optDouble("gapDp", 8.0).toFloat().dp
    val sizing = rowBlock.optString("sizing", "flex")  // "flex" | "fixed" | "scroll"
    val resizable = rowBlock.optBoolean("resizable", true)

    // Misura larghezza/altezza della row per conversioni px<->percentuali
    var rowWidthPx by remember(path) { mutableStateOf(0f) }
    var rowHeightPx by remember(path) { mutableStateOf(0f) }

    // Stato interno: pesi/width/height + sblocco per cella
    val count = items.length()
    val weights = remember(path, count) {
        MutableList(count) { idx ->
            val it = items.optJSONObject(idx)
            when {
                it == null -> 0.0f
                it.optString("type") == "SpacerH" && it.optString("mode","fixed") == "fixed" -> 0.0f
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
            it?.optDouble("widthDp", Double.NaN)?.takeIf { !it.isNaN() }?.toFloat() ?: 120f
        }.toMutableStateList()
    }
    val heightsDp = remember(path, count) {
        MutableList(count) { idx ->
            val it = items.optJSONObject(idx)
            it?.optDouble("heightDp", Double.NaN)?.takeIf { !it.isNaN() }?.toFloat() ?: 0f // 0 = auto
        }.toMutableStateList()
    }
    val unlocked = remember(path, count) { MutableList(count) { false }.toMutableStateList() }

    // Disegno “righelli” mentre si trascina: indice bordo attivo (-1 = none)
    var activeEdge by remember(path) { mutableStateOf(-1) }

    Row(
        Modifier
            .fillMaxWidth()
            .let { if (sizing == "scroll") it.horizontalScroll(rememberScrollState()) else it }
            .onGloballyPositioned { coords ->
                rowWidthPx = coords.size.width.toFloat()
                rowHeightPx = coords.size.height.toFloat()
            }
            .drawBehind {
                if (activeEdge >= 0 && rowWidthPx > 0f) {
                    // righe verticali ai confini tra celle (colore tenue)
                    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    var acc = 0f
                    for (i in 0 until count) {
                        val wPx = when (sizing) {
                            "flex" -> rowWidthPx * weights[i].coerceAtLeast(0f)
                            "fixed" -> with(LocalDensity.current) { widthsDp[i].dp.toPx() }
                            else -> with(LocalDensity.current) { widthsDp[i].dp.toPx() } // scroll
                        }
                        acc += wPx
                        // bordo tra i e i+1
                        if (i < count - 1) {
                            val thick = if (i == activeEdge) 3f else 1.5f
                            drawLine(
                                color = lineColor,
                                start = Offset(acc, 0f),
                                end = Offset(acc, size.height),
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

            // Se SpacerH fixed lo tratto separato (non ridimensionabile)
            val isFixedSpacer = child.optString("type") == "SpacerH" && child.optString("mode","fixed") == "fixed"
            if (isFixedSpacer) {
                Spacer(Modifier.width(child.optDouble("widthDp", 16.0).toFloat().dp))
                continue
            }

            // Modificatore base della cella in base al sizing
            val cellBase = when (sizing) {
                "flex" -> {
                    val w = weights[i].coerceAtLeast(0.0001f) // RowScope.weight richiede > 0
                    Modifier.weight(w, fill = true)
                }
                "fixed", "scroll" -> Modifier.width(widthsDp[i].dp)
                else -> Modifier.weight(1f, fill = true)
            }.then(
                if (heightsDp[i] > 0f) Modifier.height(heightsDp[i].dp) else Modifier
            )

            Box(
                cellBase
                    // doppio tap: sblocca/lock la cella (solo se resizable == true e non in designer)
                    .pointerInput(resizable, designerMode, unlocked[i]) {
                        if (resizable && !designerMode) {
                            androidx.compose.foundation.gestures.detectTapGestures(
                                onDoubleTap = { unlocked[i] = !unlocked[i] }
                            )
                        }
                    }
            ) {
                // Contenuto reale del blocco
                val p2 = "$path/items/$i"
                RenderBlock(child, dispatch, uiState, designerMode, p2, menus, onSelect, onOpenInspector)

                // Maniglie: visibili solo se sbloccato e resizable
                if (resizable && !designerMode && unlocked[i]) {
                    // Orizzontale (lato destro): non l’ultima cella
                    if (i < count - 1) {
                        ResizeHandleX(
                            align = Alignment.CenterEnd,
                            onDragStart = { activeEdge = i },
                            onDrag = { deltaPx ->
                                when (sizing) {
                                    "flex" -> {
                                        val deltaW = (deltaPx / (rowWidthPx.takeIf { it > 0f } ?: 1f))
                                        applyProportionalDelta(weights, i, deltaW, items)
                                    }
                                    "fixed" -> {
                                        val step = 8f
                                        widthsDp[i] = snapDp(widthsDp[i] + pxToDp(deltaPx), step)
                                        // ridistribuisci il delta in parti uguali sugli altri (se non scroll)
                                        val others = (0 until count).filter { it != i }
                                        if (others.isNotEmpty()) {
                                            val share = -pxToDp(deltaPx) / others.size
                                            others.forEach { j ->
                                                widthsDp[j] = snapDp((widthsDp[j] + share).coerceAtLeast(48f), step)
                                            }
                                        }
                                        // salva nelle JSON (opzionale, non persistente su disco)
                                        child.put("widthDp", widthsDp[i].toDouble())
                                        others.forEach { j -> items.optJSONObject(j)?.put("widthDp", widthsDp[j].toDouble()) }
                                    }
                                    "scroll" -> {
                                        val step = 8f
                                        widthsDp[i] = snapDp(widthsDp[i] + pxToDp(deltaPx), step)
                                        child.put("widthDp", widthsDp[i].toDouble())
                                    }
                                }
                            },
                            onDragEnd = { activeEdge = -1 }
                        )
                    }
                    // Verticale (basso)
                    ResizeHandleY(
                        align = Alignment.BottomCenter,
                        onDrag = { dyPx ->
                            val step = 8f
                            val newH = snapDp((heightsDp[i].takeIf { it > 0f } ?: pxToDp(rowHeightPx)) + pxToDp(dyPx), step)
                            heightsDp[i] = newH.coerceAtLeast(32f)
                            child.put("heightDp", heightsDp[i].toDouble())
                        }
                    )
                    // Verticale (alto): ancora il bordo superiore
                    ResizeHandleY(
                        align = Alignment.TopCenter,
                        onDrag = { dyPx ->
                            val step = 8f
                            val newH = snapDp((heightsDp[i].takeIf { it > 0f } ?: pxToDp(rowHeightPx)) - pxToDp(dyPx), step)
                            heightsDp[i] = newH.coerceAtLeast(32f)
                            child.put("heightDp", heightsDp[i].toDouble())
                        }
                    )
                }
            }
        }
    }
}

/* ---- Handle orizzontale (dx/sx) ---- */
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
            .cursorForResizeHoriz()
            .pointerInput(Unit) {
                androidx.compose.foundation.gestures.detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x)
                    }
                )
            }
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    )
}

/* ---- Handle verticale (alto/basso) ---- */
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
            .cursorForResizeVert()
            .pointerInput(Unit) {
                androidx.compose.foundation.gestures.detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.y)
                }
            }
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    )
}

/* ---- Helpers snapping & proporzioni (5%) ---- */
private fun snapPercent5(v: Float): Float {
    // scatti a 5%
    val snaps = (v * 20f).roundToInt().coerceIn(1, 19) // 0.05 .. 0.95
    return snaps / 20f
}
private fun snapDp(v: Float, step: Float = 8f): Float =
    ((v / step).roundToInt() * step).coerceAtLeast(step)

@Composable private fun pxToDp(px: Float): Float =
    with(LocalDensity.current) { px.toDp().value }

/* Distribuisce il delta di weight sull'elemento i
 * e lo sottrae in parti uguali agli altri (non negativi, min 5%)
 * Scrive anche nel JSON la nuova weight.
 */
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

    // snap 5% e normalizza lieve per tenere somma ≈ 1
    for (k in 0 until weights.size) {
        weights[k] = snapPercent5(weights[k])
    }
    val sum = weights.sum().takeIf { it > 0f } ?: 1f
    for (k in 0 until weights.size) {
        weights[k] = (weights[k] / sum).coerceIn(0.05f, 0.95f)
        items.optJSONObject(k)?.put("weight", weights[k].toDouble())
    }
}

/* ---- “Cursori” fittizi per dare feedback (opzionale) ---- */
private fun Modifier.cursorForResizeHoriz(): Modifier = this // placeholder per eventuale pointerIcon su Desktop
private fun Modifier.cursorForResizeVert(): Modifier = this   // idem



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
    val bottomButtons = layout.optJSONArray("bottomButtons") ?: JSONArray() // legacy fallback

    val bottomBarNode  = layout.optJSONObject("bottomBar")
    val bottomBarCfg   = bottomBarNode?.optJSONObject("container")
    val bottomBarItems = bottomBarNode?.optJSONArray("items")
    val fab   = layout.optJSONObject("fab")
    val scroll = layout.optBoolean("scroll", true)
    val topBarConf = layout.optJSONObject("topBar")

    // Pinned per difetto se 'scroll' nella top bar è "pinned"
    val topScrollBehavior =
        when (topBarConf?.optString("scroll", "none")) {
            "pinned"            -> TopAppBarDefaults.pinnedScrollBehavior()
            "enterAlways"       -> TopAppBarDefaults.enterAlwaysScrollBehavior()
            "exitUntilCollapsed"-> TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            else                -> null
        }

    Scaffold(
        // Colleghiamo lo scroll behavior della top bar
        modifier = if (topScrollBehavior != null)
            Modifier.nestedScroll(topScrollBehavior.nestedScrollConnection)
        else Modifier,

        // Evitiamo che l'IME (tastiera) o i system insets facciano “rimpicciolire” lo spazio del body
        // La TopBar resta ferma; il contenuto gestisce gli insets manualmente.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),

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
                            put("type", "button")
                            put("label", it.optString("label","Button"))
                            put("actionId", it.optString("actionId",""))
                            put("style", "text")
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
      