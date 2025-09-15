package ai.runow.ui.renderer

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

@Composable
internal fun ResizableRow(
    rowBlock: JSONObject,
    path: String,
    gap: Dp,
    scrollable: Boolean,
    dispatch: (String) -> Unit,
    uiState: MutableMap<String, Any>,
    menus: Map<String, JSONArray>,
) {
    val items = rowBlock.optJSONArray("items") ?: JSONArray()
    val density = LocalDensity.current

    // Pesi correnti (solo non‑scrollabile)
    val weightsState = remember(path) { mutableStateOf(loadOrInitWeights(rowBlock)) }

    var activeResize by remember(path) { mutableStateOf<Int?>(null) }
    var activeMove by remember(path) { mutableStateOf<Int?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onTap = { activeResize = null; activeMove = null }) }
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val count = items.length()
            for (i in 0 until count) {
                val child = items.optJSONObject(i) ?: continue
                val childPath = "$path/items/$i"
                val itemMod = if (!scrollable) {
                    val w = weightsState.value.getOrNull(i) ?: 1f
                    Modifier.weight(w, fill = true)
                } else Modifier

                Box(itemMod) {
                    RenderBlock(child, dispatch, uiState, designerMode = false, path = childPath, menus = menus, onSelect = {}, onOpenInspector = {})

                    // Gesture layer
                    Box(
                        Modifier
                            .matchParentSize()
                            .pointerInput(i) {
                                detectTapGestures(
                                    onLongPress = { activeResize = i; activeMove = null },
                                    onDoubleTap = { activeMove = if (activeMove == i) null else i }
                                )
                            }
                    )

                    // Maniglie quando in resize su questo item
                    if (activeResize == i) {
                        ResizeHandles(
                            onResizeHorizontal = { deltaPx, isRightEdge, rowWidthPx ->
                                if (!scrollable) {
                                    val newWeights = applyHorizontalDeltaToWeights(
                                        current = weightsState.value,
                                        index = i,
                                        deltaPx = if (isRightEdge) deltaPx else -deltaPx,
                                        rowWidthPx = rowWidthPx
                                    )
                                    weightsState.value = newWeights
                                    commitWeightsToRow(rowBlock, newWeights)
                                } else {
                                    val dp = with(density) { deltaPx.toDp() }
                                    growChildWidthDp(child, dp * (if (isRightEdge) 1f else -1f))
                                }
                            },
                            onResizeVertical = { deltaPx, isBottomEdge ->
                                val dp = with(density) { deltaPx.toDp() }
                                growChildHeightDp(child, if (isBottomEdge) dp else -dp)
                            },
                            guideColor = Color.Black.copy(alpha = 0.6f)
                        )
                    }

                    // Riordino tra fratelli (semplice) quando in move mode
                    if (activeMove == i) {
                        DraggableReorderOverlay(
                            index = i,
                            count = count,
                            onMove = { from, to ->
                                if (from != to) {
                                    moveInArray(items, from, to)
                                    if (!scrollable) {
                                        val ws = weightsState.value.toMutableList()
                                        if (from in ws.indices && to in ws.indices) {
                                            val w = ws.removeAt(from)
                                            ws.add(to, w)
                                            weightsState.value = ws
                                            commitWeightsToRow(rowBlock, ws)
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ResizeHandles(
    onResizeHorizontal: (deltaPx: Float, isRightEdge: Boolean, rowWidthPx: Float) -> Unit,
    onResizeVertical:   (deltaPx: Float, isBottomEdge: Boolean) -> Unit,
    guideColor: Color
) {
    val density = LocalDensity.current
    val handleW = 14.dp
    val handleH = 14.dp
    var rowWidthPx by remember { mutableStateOf(1f) }

    Box(Modifier.matchParentSize().onGloballyPositioned { coords -> rowWidthPx = coords.size.width.toFloat().coerceAtLeast(1f) })

    // Left
    Box(
        Modifier.fillMaxHeight().width(handleW).align(Alignment.CenterStart)
            .drawWithContent {
                drawContent()
                drawLine(color = guideColor, start = Offset(x = size.width, y = 0f), end = Offset(x = size.width, y = size.height), strokeWidth = with(density) { 1.dp.toPx() })
            }
            .pointerInput(Unit) { detectDragGestures { c, drag -> c.consume(); onResizeHorizontal(drag.x, false, rowWidthPx) } }
    )
    // Right
    Box(
        Modifier.fillMaxHeight().width(handleW).align(Alignment.CenterEnd)
            .drawWithContent {
                drawContent()
                drawLine(color = guideColor, start = Offset(x = 0f, y = 0f), end = Offset(x = 0f, y = size.height), strokeWidth = with(density) { 1.dp.toPx() })
            }
            .pointerInput(Unit) { detectDragGestures { c, drag -> c.consume(); onResizeHorizontal(drag.x, true, rowWidthPx) } }
    )
    // Top
    Box(
        Modifier.fillMaxWidth().height(handleH).align(Alignment.TopCenter)
            .drawWithContent {
                drawContent()
                drawLine(color = guideColor, start = Offset(x = 0f, y = size.height), end = Offset(x = size.width, y = size.height), strokeWidth = with(density) { 1.dp.toPx() })
            }
            .pointerInput(Unit) { detectDragGestures { c, drag -> c.consume(); onResizeVertical(drag.y, false) } }
    )
    // Bottom
    Box(
        Modifier.fillMaxWidth().height(handleH).align(Alignment.BottomCenter)
            .drawWithContent {
                drawContent()
                drawLine(color = guideColor, start = Offset(x = 0f, y = 0f), end = Offset(x = size.width, y = 0f), strokeWidth = with(density) { 1.dp.toPx() })
            }
            .pointerInput(Unit) { detectDragGestures { c, drag -> c.consume(); onResizeVertical(drag.y, true) } }
    )
}

@Composable
private fun BoxScope.DraggableReorderOverlay(
    index: Int,
    count: Int,
    onMove: (from: Int, to: Int) -> Unit
) {
    val density = LocalDensity.current
    var accDx by remember { mutableStateOf(0f) }
    Box(
        Modifier.matchParentSize().pointerInput(index, count) {
            detectDragGestures(
                onDrag = { change, drag ->
                    change.consume()
                    accDx += drag.x
                    val stepPx = with(density) { 48.dp.toPx() }
                    while (accDx > stepPx && index < count - 1) { onMove(index, index + 1); accDx -= stepPx }
                    while (accDx < -stepPx && index > 0) { onMove(index, index - 1); accDx += stepPx }
                },
                onDragEnd = { accDx = 0f }
            )
        }
    )
}

// Helpers pesi/dimensioni ----------------------------------------------------
internal fun loadOrInitWeights(row: JSONObject): MutableList<Float> {
    val items = row.optJSONArray("items") ?: JSONArray()
    val ws = MutableList(items.length()) { 1f }
    var any = false
    for (i in 0 until items.length()) {
        val c = items.optJSONObject(i) ?: continue
        val w = c.optDouble("weight", Double.NaN)
        if (!w.isNaN()) { ws[i] = w.toFloat(); any = true }
    }
    if (!any) {
        val sum = ws.sum().takeIf { it > 0f } ?: 1f
        for (i in ws.indices) ws[i] = ws[i] / sum
    }
    return ws
}

internal fun applyHorizontalDeltaToWeights(current: List<Float>, index: Int, deltaPx: Float, rowWidthPx: Float): MutableList<Float> {
    val ws = current.toMutableList()
    val total = ws.sum().takeIf { it > 0f } ?: 1f
    val deltaW = (deltaPx / rowWidthPx) * total
    if (deltaW == 0f) return ws
    val n = ws.size
    val others = (n - 1).coerceAtLeast(1)
    ws[index] = (ws[index] + deltaW).coerceAtLeast(0.1f)
    val share = (deltaW / others)
    for (i in ws.indices) if (i != index) ws[i] = (ws[i] - share).coerceAtLeast(0.1f)
    val scale = total / ws.sum()
    for (i in ws.indices) ws[i] = ws[i] * scale
    return ws
}

internal fun commitWeightsToRow(row: JSONObject, weights: List<Float>) {
    val items = row.optJSONArray("items") ?: return
    for (i in 0 until items.length()) items.optJSONObject(i)?.put("weight", weights.getOrNull(i)?.toDouble() ?: 1.0)
}

internal fun growChildWidthDp(child: JSONObject, deltaDp: Dp) {
    val cont = child.optJSONObject("container") ?: JSONObject().also { child.put("container", it) }
    val cur = cont.optDouble("widthDp", Double.NaN)
    val base = if (cur.isNaN()) 160.0 else cur
    cont.put("widthMode", "fixed_dp")
    cont.put("widthDp", (base + deltaDp.value).coerceAtLeast(48.0))
}

internal fun growChildHeightDp(child: JSONObject, deltaDp: Dp) {
    val cont = child.optJSONObject("container") ?: JSONObject().also { child.put("container", it) }
    val cur = cont.optDouble("heightDp", Double.NaN)
    val base = if (cur.isNaN()) 48.0 else cur
    cont.put("heightMode", "fixed_dp")
    cont.put("heightDp", (base + deltaDp.value).coerceAtLeast(24.0))
}
