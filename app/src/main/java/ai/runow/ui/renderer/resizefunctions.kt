package ai.runow.ui.renderer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ---------- SPEED DIAL (3 modalità) ---------- */

@Composable
fun ModeSpeedDial(
    mode: AppMode,
    onChange: (AppMode) -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    if (!visible) return
    var expanded by remember { mutableStateOf(false) }

    Box(modifier, contentAlignment = Alignment.BottomEnd) {
        if (expanded) {
            // Menu verticale semplice (pulito e stabile)
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(bottom = 72.dp, end = 8.dp)
            ) {
                SmallFab(
                    selected = mode == AppMode.Real,
                    label = "Reale",
                    icon = Icons.Filled.Visibility,
                    onClick = { onChange(AppMode.Real); expanded = false }
                )
                SmallFab(
                    selected = mode == AppMode.Designer,
                    label = "Designer",
                    icon = Icons.Filled.Build,
                    onClick = { onChange(AppMode.Designer); expanded = false }
                )
                SmallFab(
                    selected = mode == AppMode.Resize,
                    label = "Resize",
                    icon = Icons.Filled.Tune,
                    onClick = { onChange(AppMode.Resize); expanded = false }
                )
            }
        }

        FloatingActionButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = "Modalità"
            )
        }
    }
}

/* Overload per eventuali chiamate legacy: ModeSpeedDial(visible=..., current=..., onPick=...) */
@Composable
fun ModeSpeedDial(
    visible: Boolean,
    current: AppMode,
    onPick: (AppMode) -> Unit,
    modifier: Modifier = Modifier
) = ModeSpeedDial(mode = current, onChange = onPick, modifier = modifier, visible = visible)

@Composable
private fun SmallFab(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        text  = { Text(label) },
        containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor   = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.height(40.dp)
    )
}

/* ---------- HUD avviso modalità Resize (trasparente, 10s o tap) ---------- */

@Composable
fun ResizeHud(
    visible: Boolean,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!visible) return

    var show by remember { mutableStateOf(true) }

    // Sparisce dopo 10s
    LaunchedEffect(Unit) {
        delay(10_000)
        show = false
        onDismiss()
    }

    if (show) {
        Box(
            modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp)
                .clickable {
                    show = false
                    onDismiss()
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.35f), // “trasparente” ma leggibile
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "Modalità resize: • Tieni premuto un blocco per abilitarne il ridimensionamento (pulse) • Trascina i bordi per ridimensionare • Tieni premuto di nuovo per abilitare lo spostamento: trascinando si scambia con i vicini quando si sovrappone a sufficienza.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

/* ---------- Stato e interactor per resize/spostamento in runtime ---------- */

@Stable
class ResizeMoveState {
    var isActiveForResize by mutableStateOf(false) // true dopo primo long press (pulse)
    var isMoveEnabled     by mutableStateOf(false) // true dopo secondo long press
    var lastPulseKey      by mutableStateOf(0)     // per triggerare l’animazione pulse

    fun activateWithPulse() {
        isActiveForResize = true
        lastPulseKey++
    }
    fun reset() {
        isActiveForResize = false
        isMoveEnabled = false
    }
}

@Composable
fun rememberResizeMoveState(): ResizeMoveState = remember { ResizeMoveState() }

private fun Rect.translate(delta: Offset): Rect =
    Rect(left + delta.x, top + delta.y, right + delta.x, bottom + delta.y)

private fun horizontalOverlapRatio(a: Rect, b: Rect): Float {
    val left = maxOf(a.left, b.left)
    val right = minOf(a.right, b.right)
    val overlap = (right - left).coerceAtLeast(0f)
    val width = minOf(a.width, b.width)
    return if (width <= 0f) 0f else overlap / width
}

/**
 * Wrapper di gesture e feedback (pulse + drag) da applicare al contenuto del blocco.
 *
 * @param enabled true solo in AppMode.Resize
 * @param state   rememberResizeMoveState()
 * @param index   indice del blocco tra i fratelli
 * @param neighborsBoundsProvider bounds globali dei fratelli (in ordine)
 * @param overlapToleranceFraction soglia [0..1] per richiedere lo swap
 * @param onSwapRequest callback per invertire from<->to (gestita a livello di riga)
 */
@Composable
fun ResizeMoveInteractor(
    enabled: Boolean,
    state: ResizeMoveState,
    index: Int,
    neighborsBoundsProvider: () -> List<Rect>,
    overlapToleranceFraction: Float = 0.35f,
    onSwapRequest: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) } // offset X; per colonne usa Y analogamente
    var dragging by remember { mutableStateOf(false) }

    // Effetto PULSE quando il blocco viene abilitato al resize
    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(state.lastPulseKey) {
        if (state.isActiveForResize) {
            pulseScale.snapTo(1f)
            pulseScale.animateTo(1.08f, tween(120))
            pulseScale.animateTo(1f, tween(150))
        }
    }

    val gestures = if (enabled) {
        Modifier
            // Long‑press: 1° attiva resize (pulse); 2° abilita spostamento
            .pointerInput(index, state.isActiveForResize, state.isMoveEnabled) {
                detectTapGestures(
                    onLongPress = {
                        if (!state.isActiveForResize) {
                            state.activateWithPulse()
                        } else {
                            state.isMoveEnabled = true
                        }
                    }
                )
            }
            // Drag orizzontale per spostare/scambiare (solo se move abilitato)
            .pointerInput(index, state.isMoveEnabled) {
                detectDragGestures(
                    onDragStart = {
                        if (state.isMoveEnabled) dragging = true
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        if (!state.isMoveEnabled) return@detectDragGestures

                        val newX = dragOffset.value + drag.x
                        scope.launch { dragOffset.snapTo(newX) } // <- sospesa: dentro launch

                        val bounds = neighborsBoundsProvider()
                        val me = bounds.getOrNull(index) ?: return@detectDragGestures
                        val moved = me.translate(Offset(newX, 0f))

                        fun maybeSwapWith(target: Int) {
                            val other = bounds.getOrNull(target) ?: return
                            if (horizontalOverlapRatio(moved, other) >= overlapToleranceFraction) {
                                onSwapRequest(index, target)
                            }
                        }
                        maybeSwapWith(index - 1)
                        maybeSwapWith(index + 1)
                    },
                    onDragCancel = {
                        dragging = false
                        scope.launch { dragOffset.animateTo(0f, tween(120)) }
                    },
                    onDragEnd = {
                        dragging = false
                        scope.launch { dragOffset.animateTo(0f, tween(120)) }
                    }
                )
            }
    } else Modifier

    Box(
        modifier
            .graphicsLayer {
                val s = if (state.isActiveForResize) pulseScale.value else 1f
                scaleX = s
                scaleY = s
                translationX = dragOffset.value
            }
            .then(gestures)
    ) { content() }
}