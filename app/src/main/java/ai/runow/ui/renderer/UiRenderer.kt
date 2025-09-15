@file:OptIn(ExperimentalMaterial3Api::class)

package ai.runow.ui.renderer


import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.IntSize

enum class AppMode { Real, Designer, Resize }
val LocalAppMode = compositionLocalOf { AppMode.Real }

@Composable
private fun BoxScope.ResizeHandleX(
    align: Alignment,                  // Alignment.CenterStart (sx) o CenterEnd (dx)
    onDragStart: () -> Unit = {},
    onDrag: (dxPx: Float) -> Unit,
    onDragEnd: () -> Unit = {}
) {
    val handleW = 14.dp
    val density = LocalDensity.current
    Box(
        Modifier
            .align(align)
            .fillMaxHeight()
            .width(handleW)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x)
                    }
                )
            }
            .drawBehind {
                // traccia guida verticale sul bordo dell'handle
                val x = if (align == Alignment.CenterStart) size.width else 0f
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = with(density) { 1.dp.toPx() }
                )
            }
    )
}

// ======== HANDLE ORIZZONTALE (ridimensiona in altezza) ========
@Composable
private fun BoxScope.ResizeHandleY(
    align: Alignment,                  // Alignment.TopCenter (top) o BottomCenter (bottom)
    onDragStart: () -> Unit = {},
    onDrag: (dyPx: Float) -> Unit,
    onDragEnd: () -> Unit = {}
) {
    val handleH = 14.dp
    val density = LocalDensity.current
    Box(
        Modifier
            .align(align)
            .fillMaxWidth()
            .height(handleH)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.y)
                    }
                )
            }
            .drawBehind {
                // traccia guida orizzontale sul bordo dell'handle
                val y = if (align == Alignment.TopCenter) size.height else 0f
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = with(density) { 1.dp.toPx() }
                )
            }
    )
}

// ======== GRUPPO DI HANDLE (4 lati) + calcolo larghezza Box ========
@Composable
private fun BoxScope.ResizeHandles(
    onResizeHorizontal: (deltaPx: Float, isRightEdge: Boolean, rowWidthPx: Float) -> Unit,
    onResizeVertical: (deltaPx: Float, isBottomEdge: Boolean) -> Unit,
    guideColor: Color
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    // serve per normalizzare i delta orizzontali in % se vuoi
    val rowWidthPx = boxSize.width.toFloat().coerceAtLeast(1f)

    // cattura dimensioni del Box che contiene il blocco
    Box(
        Modifier
            .matchParentSize()
            .onGloballyPositioned { boxSize = it.size }
    )

    // SX
    ResizeHandleX(
        align = Alignment.CenterStart,
        onDrag = { dx -> onResizeHorizontal(dx, /*isRightEdge=*/false, rowWidthPx) }
    )

    // DX
    ResizeHandleX(
        align = Alignment.CenterEnd,
        onDrag = { dx -> onResizeHorizontal(dx, /*isRightEdge=*/true, rowWidthPx) }
    )

    // TOP
    ResizeHandleY(
        align = Alignment.TopCenter,
        onDrag = { dy -> onResizeVertical(dy, /*isBottomEdge=*/false) }
    )

    // BOTTOM
    ResizeHandleY(
        align = Alignment.BottomCenter,
        onDrag = { dy -> onResizeVertical(dy, /*isBottomEdge=*/true) }
    )
}

@Composable
private fun ResizableRow(
rowBlock: JSONObject,
path: String,
gap: Dp,
scrollable: Boolean,
dispatch: (String) -> Unit,
uiState: MutableMap<String, Any>,
menus: Map<String, JSONArray>
) {
val items = rowBlock.optJSONArray("items") ?: JSONArray()
val density = LocalDensity.current
val guideColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)

// Stato: pesi correnti (solo per non scrollabile)
val weightsState = remember(path) { mutableStateOf(loadOrInitWeights(rowBlock)) }

// Stato: indice elemento in "resize unlocked" (long-press) o "move mode" (double-tap)
var activeResize by remember(path) { mutableStateOf<Int?>(null) }
var activeMove by remember(path) { mutableStateOf<Int?>(null) }

// Tap fuori dai blocchi = chiude modalità
Column(
Modifier
.fillMaxWidth()
.pointerInput(Unit) {
detectTapGestures(onTap = {
activeResize = null
activeMove = null
})
}
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
@@ -177,287 +296,86 @@
} else {
// Scrollabile: larghezza fissa in dp sul container del child
val dp = with(density) { deltaPx.toDp() }
growChildWidthDp(child, dp * (if (isRightEdge) 1f else -1f))
}
},
onResizeVertical = { deltaPx, isBottomEdge ->
val dp = with(density) { deltaPx.toDp() }
growChildHeightDp(child, if (isBottomEdge) dp else -dp)
},
guideColor = guideColor
)
}

// Riordino tra fratelli con drag orizzontale (dopo double-tap)
if (activeMove == i) {
DraggableReorderOverlay(
index = i,
count = count,
onMove = { from, to ->
if (from != to) {
moveInArray(items, from, to)
// riallinea anche i pesi se presenti
if (!scrollable) {
val ws = weightsState.value.toMutableList()
if (from in ws.indices && to in ws.indices) {
val w = ws.removeAt(from)
ws.add(to, w)
weightsState.value = ws
commitWeightsToRow(rowBlock, ws)
}
}
activeMove = to
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
private fun BoxScope.DraggableReorderOverlay(
index: Int,
count: Int,
onMove: (from: Int, to: Int) -> Unit
) {
val density = LocalDensity.current
var accDx by remember { mutableStateOf(0f) }

Box(
Modifier
.matchParentSize()
.pointerInput(index, count) {
detectDragGestures(
onDrag = { change, drag ->
change.consume()
accDx += drag.x
val stepPx = with(density) { 48.dp.toPx() } // soglia semplice per scambiare slot
while (accDx > stepPx && index < count - 1) {
onMove(index, index + 1)
accDx -= stepPx
}
while (accDx < -stepPx && index > 0) {
onMove(index, index - 1)
accDx += stepPx
}
},
onDragEnd = { accDx = 0f }
)
}
)
}

// ---- Gestione pesi per righe non scrollabili -------------------------------

private fun loadOrInitWeights(row: JSONObject): MutableList<Float> {
val items = row.optJSONArray("items") ?: JSONArray()
// se esistono pesi, li leggiamo, altrimenti li inizializziamo tutti = 1f (saltando SpacerH fixed)
val ws = MutableList(items.length()) { 1f }
var any = false
for (i in 0 until items.length()) {
