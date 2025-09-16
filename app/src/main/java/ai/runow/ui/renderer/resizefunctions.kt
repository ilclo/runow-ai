package ai.runow.ui.renderer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * =========================
 *        SPEED-DIAL
 * =========================
 */

/**
 * Bottone flottante unico per selezionare la modalità dell’app (Real / Designer / Resize).
 * Sostituisce il “vecchio” bottone: usa SOLO questo.
 */
@Composable
fun AppModeSpeedDial(
    current: AppMode,
    onPick: (AppMode) -> Unit,
    modifier: Modifier = Modifier,
    expandedInitially: Boolean = false
) {
    var expanded by remember { mutableStateOf(expandedInitially) }

    Box(modifier.fillMaxSize()) {
        // Backdrop per click fuori se aperto
        if (expanded) {
            Box(
                Modifier
                    .matchParentSize()
                    .alpha(0.01f) // trasparente ma cliccabile
                    .pointerInput(Unit) { detectTapGestures(onTap = { expanded = false }) }
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.End
        ) {
            if (expanded) {
                SmallModeFab(
                    label = "Real",
                    selected = current == AppMode.Real,
                    onClick = { onPick(AppMode.Real); expanded = false }
                )
                Spacer(Modifier.height(10.dp))
                SmallModeFab(
                    label = "Designer",
                    selected = current == AppMode.Designer,
                    onClick = { onPick(AppMode.Designer); expanded = false }
                )
                Spacer(Modifier.height(10.dp))
                SmallModeFab(
                    label = "Resize",
                    selected = current == AppMode.Resize,
                    onClick = { onPick(AppMode.Resize); expanded = false }
                )
                Spacer(Modifier.height(8.dp))
            }

            FloatingActionButton(
                onClick = { expanded = !expanded },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Text(
                    text = when (current) {
                        AppMode.Real -> "R"
                        AppMode.Designer -> "D"
                        AppMode.Resize -> "Z"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun SmallModeFab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.tertiary
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onTertiary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = bg,
        contentColor = fg,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 6.dp,
        onClick = onClick
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/**
 * =========================
 *     RESIZE HINT OVERLAY
 * =========================
 */

/**
 * Controller “plug&play” per mostrare il banner trasparente di istruzioni in Resize Mode:
 * - compare ogni volta che si entra in modalità Resize
 * - resta per 10s o scompare subito al tap
 */
@Composable
fun ResizeHintOverlayController(
    mode: AppMode,
    modifier: Modifier = Modifier,
    timeoutMillis: Long = 10_000L
) {
    var visible by remember(mode) { mutableStateOf(mode == AppMode.Resize) }

    // Se entro in Resize ⇒ (ri)mostra
    LaunchedEffect(mode) {
        if (mode == AppMode.Resize) visible = true
    }

    if (mode == AppMode.Resize && visible) {
        ResizeHintOverlay(
            onDismiss = { visible = false },
            modifier = modifier,
            timeoutMillis = timeoutMillis
        )
    }
}

/**
 * Overlay testuale leggero e trasparente.
 * Niente “fondino pieno” per far vedere chiaramente ciò che c’è dietro.
 */
@Composable
fun ResizeHintOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    timeoutMillis: Long = 10_000L
) {
    // auto-hide
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(timeoutMillis)
        onDismiss()
    }

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
    ) {
        // pill trasparente con testo, nessun background opaco
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                "Modalità RESIZE: • Tap prolungato su un blocco per abilitarne il ridimensionamento (effetto pulse). " +
                        "• Trascina i bordi per allungare/accorciare. " +
                        "• Trascina il blocco per spostarlo; al superamento della tolleranza i blocchi si scambiano.",
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .background(Color.Transparent)
                    .padding(6.dp)
            )
        }
    }
}

/**
 * =========================
 *   RESIZE / MOVE WRAPPER
 * =========================
 */

/**
 * Stato d’interazione per un singolo blocco in Resize Mode
 */
@Stable
class ResizeMoveState(
    initialActive: Boolean = false,
    initialMoveEnabled: Boolean = false
) {
    var isActiveForResize by mutableStateOf(initialActive)
    var isMoveEnabled by mutableStateOf(initialMoveEnabled)
    var lastPulseKey by mutableStateOf(0)

    fun activateWithPulse() {
        isActiveForResize = true
        // trigger pulse
        lastPulseKey++
    }

    fun deactivate() {
        isActiveForResize = false
        isMoveEnabled = false
    }
}

@Composable
fun rememberResizeMoveState(): ResizeMoveState = remember { ResizeMoveState() }

/**
 * Wrapper da applicare al contenuto del BLOCCO quando si è in Resize Mode.
 * Gestisce:
 *  - tap prolungato ⇒ attiva resize + PULSE
 *  - drag del blocco se move abilitato (long press successivo abilita anche lo spostamento)
 *  - swap con blocchi vicini quando l’overlap supera una soglia (tolleranza)
 *
 * NOTA: i veri “handle” per ridimensionare i bordi sono già in ResizableRow.kt.
 * Questo wrapper abilita/indica “attivo” e si occupa del MOVE e della parte estetica (pulse).
 */

@Composable
fun ResizeMoveInteractor(
    enabled: Boolean,                  // true solo in AppMode.Resize
    state: ResizeMoveState,            // rememberResizeMoveState()
    index: Int,                        // posizione del blocco nella riga/colonna
    neighborsBoundsProvider: () -> List<Rect>, // bounds globali dei fratelli (in ordine)
    overlapToleranceFraction: Float = 0.35f,   // soglia di sovrapposizione per lo swap [0..1]
    onSwapRequest: (from: Int, to: Int) -> Unit, // chiamato quando va scambiato
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val scope = rememberCoroutineScope()

    val dragOffset = remember { Animatable(0f) }  // spostamento visuale orizzontale (righe)
    var dragging by remember { mutableStateOf(false) }

    // Effetto PULSE quando attivo per resize
    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(state.lastPulseKey) {
        if (state.isActiveForResize) {
            pulseScale.snapTo(1f)
            pulseScale.animateTo(1.06f, animationSpec = tween(120))
            pulseScale.animateTo(1f, animationSpec = tween(150))
        }
    }

    // Gesture layer (solo in Resize Mode)
    val gestureMod = if (enabled) {
        Modifier
            .pointerInput(index, state.isActiveForResize, state.isMoveEnabled) {
                detectTapGestures(
                    onLongPress = {
                        // Primo long-press: abilita resize + pulse
                        if (!state.isActiveForResize) {
                            state.activateWithPulse()
                        } else {
                            // Secondo long-press: abilita lo spostamento del blocco
                            state.isMoveEnabled = true
                        }
                    }
                )
            }
            .pointerInput(index, state.isMoveEnabled) {
                // Drag del blocco solo se move abilitato
                detectDragGestures(
                    onDragStart = { if (state.isMoveEnabled) dragging = true },
                    onDragEnd = {
                        if (dragging) {
                            dragging = false
                            // rilasciato: torna a 0 con animazione
                            scope.launch { dragOffset.animateTo(0f, tween(120)) }
                        }
                    },
                    onDragCancel = {
                        dragging = false
                        scope.launch { dragOffset.animateTo(0f, tween(120)) }
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        if (!state.isMoveEnabled) return@detectDragGestures

                        // Offset orizzontale (riga). Se per te è colonna, cambia in drag.y
                        val newX = dragOffset.value + drag.x
                        scope.launch { dragOffset.snapTo(newX) }

                        // Calcolo swap su overlap con vicini
                        val bounds = neighborsBoundsProvider()
                        val me = bounds.getOrNull(index) ?: return@detectDragGestures

                        // rettangolo "spostato" del blocco attuale
                        val moved = me.translate(Offset(newX, 0f))

                        // controlla overlap con il vicino di destra e sinistra
                        fun maybeSwapWith(targetIndex: Int) {
                            val other = bounds.getOrNull(targetIndex) ?: return
                            val ratio = horizontalOverlapRatio(moved, other)
                            if (ratio >= overlapToleranceFraction) {
                                onSwapRequest(index, targetIndex)
                            }
                        }

                        maybeSwapWith(index - 1)
                        maybeSwapWith(index + 1)
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
            .then(gestureMod)
    ) {
        content()
    }
}

/**
 * Ritorna il rapporto di sovrapposizione orizzontale tra due rettangoli: 0..1
 * 0 = nessuna sovrapposizione, 1 = sovrapposizione completa sul lato minore.
 */
fun horizontalOverlapRatio(a: Rect, b: Rect): Float {
    val left = max(a.left, b.left)
    val right = min(a.right, b.right)
    val overlap = (right - left).coerceAtLeast(0f)
    val base = min(a.width, b.width).coerceAtLeast(1f)
    return (overlap / base).coerceIn(0f, 1f)
}

/**
 * Utility per tradurre un Rect
 */
private fun Rect.translate(delta: Offset): Rect =
    Rect(Offset(left + delta.x, top + delta.y), Offset(right + delta.x, bottom + delta.y))