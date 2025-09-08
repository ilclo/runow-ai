@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.ui.unit.ExperimentalUnitApi::class
)

package ai.runow.ui.renderer

import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round


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
    val baseColor  = parseColorOrRole(cfg.optString("containerColor","surface")) ?: MaterialTheme.colorScheme.surface
    val textColor  = parseColorOrRole(cfg.optString("textColor","")) ?: bestOnColor(baseColor)
    val title      = cfg.optString("title","")

    BackHandler(enabled = true) { onClose() }

    Box(Modifier.fillMaxSize()) {
        // leggero scrim per catturare il tap fuori e dare focus al menu, senza coprire la sidebar dietro
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
            color = baseColor.copy(alpha = alphaBox),        // semitrasparente: si intravede il background
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
                                val keepOpen = act.startsWith("sidepanel:open:")
                                if (!keepOpen) onClose()           // resta aperto se sto aprendo una sidebar
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
internal fun RenderBlock(
    block: JSONObject,
    dispatch: (String) -> Unit,
    uiState: MutableMap<String, Any>,
    designerMode: Boolean,
    path: String,
    menus: Map<String, JSONArray>,
    onSelect: (String) -> Unit,
    onOpenInspector: (String) -> Unit
) {
    // --- Intercettori per menu centrale/sidepanel ---
    when (block.optString("type")) {
        "IconButton" -> {
            val icon = block.optString("icon", "more_vert")
            val openMenuId = block.optString("openMenuId", "")
            val actionId = block.optString("actionId", "")

            IconButton(onClick = {
                when {
                    openMenuId.isNotBlank() -> dispatch("open_menu:$openMenuId")
                    actionId.startsWith("open_menu:") -> dispatch(actionId)
                    else -> if (actionId.isNotBlank()) dispatch(actionId)
                }
            }) { NamedIconEx(icon, null) }
            return
        }
        "Menu" -> {
            // Il contenuto del menu viene reso dal Center Overlay.
            // In designer mostriamo un chip cliccabile che apre l’overlay per anteprima rapida.
            if (designerMode) {
                val id = block.optString("id","(menu)")
                TextButton(onClick = { if (id.isNotBlank()) dispatch("open_menu:$id") }) {
                    NamedIconEx("more_vert", null)
                    Spacer(Modifier.width(6.dp))
                    Text("Menu: $id")
                }
            }
            return
        }
    }

    // --- qui sotto resta il TUO corpo stabile già funzionante ---
    // ...
}


@Composable
internal fun CenteredSheet(
    onDismiss: () -> Unit,
    maxHeightFraction: Float = 0.75f,
    backdropAlpha: Float = 0.25f,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        // backdrop
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backdropAlpha))
                .clickable { onDismiss() }
        )
        // sheet centrato
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .fillMaxHeight(maxHeightFraction)
                .padding(horizontal = 12.dp)
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content
            )
        }
    }
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

    val side = panel.optString("side", "left")
    val width = panel.optDouble("widthDp", 320.0).toFloat().dp
    val height = panel.optDouble("heightDp", 0.0).toFloat().let { if (it > 0) it.dp else Dp.Unspecified }
    val items = panel.optJSONArray("items") ?: JSONArray()

    val baseScrimAlpha = panel.optDouble("scrimAlpha", 0.25).toFloat().coerceIn(0f, 1f)
    val scrimAlpha = if (dimBehind) baseScrimAlpha else 0f
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
                                onSelect = {},                 // aggiunto
                                onOpenInspector = {}           // aggiunto
                            )
                        }
                    }
                }
            }
        }
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

/* =========================================================
 * ENTRY ROOT CHIAMATA DA MainActivity
 * ========================================================= */
@Composable
fun DesignerRoot() {
    val uiState = remember { mutableMapOf<String, Any>() }
    val dispatch: (String) -> Unit = { /* TODO: instrada azioni app */ }

    // Schermata unica "home": UiLoader farà fallback su {"blocks":[]}
    UiScreen(
        screenName = "home",
        dispatch = dispatch,
        uiState = uiState,
        designerMode = true,                 // parte in designer, ma c'è la levetta
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

    // Menù raccolti + selezione corrente
    val menus by remember(layout, tick) { mutableStateOf(collectMenus(layout!!)) }
    var selectedPath by remember(screenName) { mutableStateOf<String?>(null) }

    // Spazio per l'overlay designer
    var overlayHeightPx by remember { mutableStateOf(0) }
    val overlayHeightDp = with(LocalDensity.current) { overlayHeightPx.toDp() }

    // Modalità designer
    var designMode by rememberSaveable(screenName) { mutableStateOf(designerMode) }

    // Pannelli laterali runtime
    var openSidePanelId by remember { mutableStateOf<String?>(null) }
    var openCenterMenuId by remember { mutableStateOf<String?>(null) }
    
    val localDispatch = wrapDispatchForOverlays(
        openPanelSetter = { openSidePanelId = it },
        openMenuSetter  = { openCenterMenuId = it },
        appDispatch     = dispatch
    )

    // Live preview root (page/topBar) opzionale: tieni NULL se non la usi ora
    var previewRoot: JSONObject? by remember { mutableStateOf(null) }
    fun mergeForPreview(base: JSONObject, preview: JSONObject): JSONObject {
        val out = JSONObject(base.toString())
        listOf("page", "topBar").forEach { k -> if (preview.has(k)) out.put(k, preview.opt(k)) }
        return out
    }
    val effectiveLayout = remember(layout, previewRoot) {
        if (previewRoot != null) mergeForPreview(layout!!, previewRoot!!) else layout!!
    }

    Box(Modifier.fillMaxSize()) {
        // Scaffold principale
        RenderRootScaffold(
            layout = effectiveLayout,
            dispatch = localDispatch,
            uiState = uiState,
            designerMode = designMode,
            menus = menus,
            selectedPathSetter = { selectedPath = it },
            extraPaddingBottom = if (designMode) overlayHeightDp + 32.dp else 16.dp,
            scaffoldPadding = scaffoldPadding
        )

        RenderSidePanelsOverlay(
            layout       = layout!!,
            openPanelId  = openSidePanelId,
            onClose      = { openSidePanelId = null },
            dispatch     = localDispatch,
            menus        = menus,
            dimBehind    = openCenterMenuId == null   // quando il menu centrale è aperto, niente scrim pesante
        )
        RenderCenterMenuOverlay(
            layout      = layout!!,
            openMenuId  = openCenterMenuId,
            onClose     = { openCenterMenuId = null },
            menus       = menus,
            dispatch    = localDispatch
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
                    layout = JSONObject(layout!!.toString())
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
                onOpenRootInspector = { /* no-op */ },
                onRootLivePreview = { previewRoot = it }
            )
        }
        // Knob laterale
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

private data class ResolvedContainer(
    val background: Color,
    val contentColor: Color,
    val shape: Shape,
    val elevation: Dp,
    val borderColor: Color,
    val borderWidth: Dp,
    val borderMode: BorderMode,
    val brush: Brush?,
    val bgImage: BgImage?
)

private data class BgImage(
    val resId: Int?,          // preferito se presente
    val uriString: String?,   // alternativamente URI
    val scale: ContentScale,
    val alpha: Float
)

@Composable
private fun mapContainerColors(style: String, tint: String, customHex: String?): Triple<Color, Color, Color> {
    val cs = MaterialTheme.colorScheme
    val (baseContainer, baseContent) = when (tint) {
        "success" -> cs.tertiary to cs.onTertiary
        "warning" -> Color(0xFFFFF3CD) to Color(0xFF664D03)
        "error"   -> cs.errorContainer to cs.onErrorContainer
        else      -> cs.primary to cs.onPrimary
    }

    var container = when (style) {
        "tonal"    -> cs.secondaryContainer
        "outlined",
        "text"     -> Color.Transparent
        "surface"  -> cs.surface
        else       -> baseContainer // "primary" = filled
    }

    var content = when (style) {
        "tonal"    -> cs.onSecondaryContainer
        "outlined",
        "text"     -> when (tint) {
            "success" -> cs.tertiary
            "warning" -> Color(0xFF8D6E63)
            "error"   -> cs.error
            else      -> cs.primary
        }
        "surface"  -> cs.onSurface
        else       -> baseContent
    }

    customHex?.let { hex ->
        parseColorOrRole(hex)?.let { c ->
            container = c
            content = bestOnColor(c)
        }
    }

    val defaultBorder = when (style) {
        "outlined" -> content
        else       -> parseColorOrRole("outline") ?: content
    }

    return Triple(container, content, defaultBorder)
}

@Composable
private fun resolveContainer(cfg: JSONObject?): ResolvedContainer {
    val style   = cfg?.optString("style", "surface") ?: "surface"  // "primary","tonal","outlined","text","surface"
    val tint    = cfg?.optString("tint", "default") ?: "default"
    val custom  = cfg?.optString("customColor","")?.takeIf { it.isNotBlank() }
    val (bg, content, defaultBorder) = mapContainerColors(style, tint, custom)

    val shapeKey = cfg?.optString("shape","rounded") ?: "rounded"
    val cornerDp = Dp(cfg?.optDouble("corner", 12.0)?.toFloat() ?: 12f)
    val shape = when (shapeKey) {
        "pill" -> RoundedCornerShape(50)
        "cut"  -> CutCornerShape(cornerDp)
        else   -> RoundedCornerShape(cornerDp)
    }

    val defaultElev = when (style) {
        "surface","primary","tonal" -> 1.0
        else -> 0.0
    }
    val elevation = Dp((cfg?.optDouble("elevationDp", defaultElev) ?: defaultElev).toFloat())

    val borderMode = when (cfg?.optString("borderMode","")) {
        "topBottom" -> BorderMode.TopBottom
        "full"      -> BorderMode.Full
        "none"      -> BorderMode.None
        else        -> if (style == "outlined") BorderMode.Full else BorderMode.None
    }
    val borderColor = parseColorOrRole(cfg?.optString("borderColor","")) ?: defaultBorder
    val borderW = Dp((cfg?.optDouble("borderThicknessDp", if (borderMode != BorderMode.None) 1.0 else 0.0) ?: 0.0).toFloat())

    // Gradient opzionale
    val brush = cfg?.optJSONObject("gradient")?.let { g ->
        val arr = g.optJSONArray("colors")
        val cols = (0 until (arr?.length() ?: 0)).mapNotNull { i -> parseColorOrRole(arr!!.optString(i)) }
        if (cols.size >= 2) {
            if (g.optString("direction","vertical") == "horizontal") Brush.horizontalGradient(cols)
            else Brush.verticalGradient(cols)
        } else null
    }

    // Immagine opzionale (res: o uri:/content:/file:)
    val bgImage = cfg?.optJSONObject("image")?.let { img ->
        val src = img.optString("source", "")
        val scale = when (img.optString("contentScale","crop")) {
            "fit" -> ContentScale.Fit
            "fill" -> ContentScale.FillBounds
            else -> ContentScale.Crop
        }
        val alpha = img.optDouble("alpha", 1.0).toFloat().coerceIn(0f, 1f)
        if (src.startsWith("res:")) {
            val ctx = LocalContext.current
            val id = ctx.resources.getIdentifier(src.removePrefix("res:"), "drawable", ctx.packageName)
            if (id != 0) BgImage(id, null, scale, alpha) else null
        } else if (src.startsWith("uri:") || src.startsWith("content:") || src.startsWith("file:")) {
            BgImage(null, src.removePrefix("uri:"), scale, alpha)
        } else null
    }

    return ResolvedContainer(
        background = bg,
        contentColor = content,
        shape = shape,
        elevation = elevation,
        borderColor = borderColor,
        borderWidth = borderW,
        borderMode = borderMode,
        brush = if (style == "text") null else brush, // "text" resta trasparente
        bgImage = if (style == "text") null else bgImage
    )
}

private fun Modifier.topBottomBorder(color: Color, thickness: Dp): Modifier = drawBehind {
    val sw = thickness.toPx().coerceAtLeast(1f)
    val yTop = sw / 2f
    val yBot = size.height - sw / 2f
    drawLine(color, Offset(0f, yTop), Offset(size.width, yTop), strokeWidth = sw)
    drawLine(color, Offset(0f, yBot), Offset(size.width, yBot), strokeWidth = sw)
}

@Composable
private fun StyledContainer(
    cfg: JSONObject?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit
) {
    val r = resolveContainer(cfg)
    val base = modifier
        .shadow(r.elevation, r.shape, clip = false)
        .clip(r.shape)
        .then(if (r.brush != null) Modifier.background(r.brush!!) else Modifier.background(r.background))
        .then(
            when (r.borderMode) {
                BorderMode.Full      -> Modifier.border(r.borderWidth, SolidColor(r.borderColor), r.shape)
                BorderMode.TopBottom -> Modifier.topBottomBorder(r.borderColor, r.borderWidth)
                BorderMode.None      -> Modifier
            }
        )
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)

    CompositionLocalProvider(LocalContentColor provides r.contentColor) {
        Box(base) {
            // Immagine sotto al contenuto
            r.bgImage?.let { bg ->
                if (bg.resId != null) {
                    Image(
                        painter = painterResource(bg.resId),
                        contentDescription = null,
                        contentScale = bg.scale,
                        modifier = Modifier.fillMaxSize(),
                        alpha = bg.alpha
                    )
                } else if (!bg.uriString.isNullOrBlank()) {
                    val bmp = rememberImageBitmapFromUri(bg.uriString)
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = null,
                            contentScale = bg.scale,
                            modifier = Modifier.fillMaxSize(),
                            alpha = bg.alpha
                        )
                    }
                }
            }
            Column(Modifier.padding(contentPadding)) {
                content()
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
    Box(Modifier.fillMaxSize().background(color))
    img?.let { pair ->
        val (info, alpha) = pair
        val (kind, payload, scale) = info
        if (kind == "res") {
            Image(
                painter = painterResource(payload.toInt()),
                contentDescription = null,
                contentScale = scale,
                modifier = Modifier.fillMaxSize(),
                alpha = alpha
            )
        } else {
            val bmp = rememberImageBitmapFromUri(payload)
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = scale,
                    modifier = Modifier.fillMaxSize(),
                    alpha = alpha
                )
            }
        }
    }
    brush?.let { b ->
        Box(Modifier.fillMaxSize().background(b))
    }
}

/* =========================================================
 * ROOT SCAFFOLD (top bar, bottom bar, fab, scroll)
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
    val title = layout.optString("topTitle", "")
    val topActions = layout.optJSONArray("topActions") ?: JSONArray()
    val bottomButtons = layout.optJSONArray("bottomButtons") ?: JSONArray()
    val bottomBarNode = layout.optJSONObject("bottomBar")
    val bottomBarCfg = bottomBarNode?.optJSONObject("container")
    val bottomBarItems = bottomBarNode?.optJSONArray("items")
    val fab = layout.optJSONObject("fab")
    val scroll = layout.optBoolean("scroll", true)
    val topBarConf = layout.optJSONObject("topBar")

    val topScrollBehavior = when (topBarConf?.optString("scroll", "none")) {
        "pinned" -> TopAppBarDefaults.pinnedScrollBehavior()
        "enterAlways" -> TopAppBarDefaults.enterAlwaysScrollBehavior()
        "exitUntilCollapsed" -> TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        else -> null
    }

    Box(Modifier.fillMaxSize()) {
        // <<< BACKGROUND PAGINA (rimesso) >>>
        RenderPageBackground(layout.optJSONObject("page"))

        Scaffold(
            modifier = if (topScrollBehavior != null)
                Modifier.nestedScroll(topScrollBehavior.nestedScrollConnection)
            else Modifier,
            topBar = {
                if (topBarConf != null) {
                    RenderTopBar(topBarConf, dispatch, topScrollBehavior)
                } else {
                    // Fallback legacy con patch azioni (vedi punto 1)
                    if (title.isNotBlank() || topActions.length() > 0) {
                        TopAppBar(
                            title = { Text(title) },
                            actions = {
                                for (i in 0 until topActions.length()) {
                                    val a = topActions.optJSONObject(i) ?: continue
                                    val icon        = a.optString("icon", "more_vert")
                                    val actionId    = a.optString("actionId", "")
                                    val openMenuId  = a.optString("openMenuId", "")
                                    IconButton(onClick = {
                                        when {
                                            openMenuId.isNotBlank()               -> dispatch("open_menu:$openMenuId")
                                            actionId.startsWith("open_menu:")     -> dispatch(actionId)
                                            actionId.startsWith("sidepanel:open:")-> dispatch(actionId)
                                            actionId.isNotBlank()                 -> dispatch(actionId)
                                        }
                                    }) { NamedIconEx(icon, null) }
                                }
                            }
                        )
                    }
                }
            },
            bottomBar = {
                val items = bottomBarItems ?: if (bottomButtons.length() > 0) {
                    JSONArray().apply {
                        for (i in 0 until bottomButtons.length()) {
                            val it = bottomButtons.optJSONObject(i) ?: continue
                            put(
                                JSONObject().apply {
                                    put("type", "button")
                                    put("label", it.optString("label", "Button"))
                                    put("actionId", it.optString("actionId", ""))
                                    put("style", "text")
                                }
                            )
                        }
                    }
                } else null

                if (items != null && items.length() > 0) {
                    StyledContainer(
                        cfg = bottomBarCfg,
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
            containerColor = Color.Transparent
        ) { innerPadding ->
            val blocks = layout.optJSONArray("blocks") ?: JSONArray()

            val host: @Composable () -> Unit = {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(scaffoldPadding)
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = extraPaddingBottom),
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

            if (scroll) Column(Modifier.verticalScroll(rememberScrollState())) { host() } else host()
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
                    else -> Spacer(Modifier.width(Dp(it.optDouble("widthDp", 16.0).toFloat())))
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
            
                IconButton(onClick = {
                    val targetMenu = if (openMenuId.isNotBlank()) openMenuId else actionId.removePrefix("open_menu:")
                    if (openMenuId.isNotBlank() || actionId.startsWith("open_menu:")) {
                        dispatch("open_menu:$targetMenu")
                    } else if (actionId.isNotBlank()) {
                        dispatch(actionId)
                    }
                }) { NamedIconEx(icon, null) }
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
        bottomStart = Dp(cfg.optDouble("roundedBottomStart", 0.0).toFloat()),
        bottomEnd   = Dp(cfg.optDouble("roundedBottomEnd",   0.0).toFloat())
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
                            onSelect = {},
                            onOpenInspector = {}
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

        // onChange: aggiorna live preview di page/topBar
        val onChange: () -> Unit = {
            dummyTick++
            onRootLivePreview(working) // <-- live preview
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
                                put(
                                    JSONObject().apply {
                                        put("type", "button")
                                        put("label", it.optString("label", "Button"))
                                        put("actionId", it.optString("actionId", ""))
                                        put("style", "text")
                                    }
                                )
                            }
                        }
                    }
                    StyledContainer(cont, Modifier.fillMaxWidth(), contentPadding = PaddingValues(8.dp)) {
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
                                        put("title", working.optString("topTitle", ""))
                                        put("titleColor", "onSurface")
                                        put("actionsColor", "onSurface")
                                        put("divider", false)
                                        put("container", JSONObject().apply {
                                            put("style","surface")
                                            put("corner", 0)
                                            put("borderMode","none")
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
internal fun NamedIconEx(name: String?, contentDescription: String?) {
    val __ctx = LocalContext.current
    if (name.isNullOrBlank()) { Text("."); return }

    if (name.startsWith("res:")) {
        val resName = name.removePrefix("res:")
        val id = __ctx.resources.getIdentifier(resName, "drawable", __ctx.packageName)
        if (id != 0) { Icon(painterResource(id), contentDescription); return }
    }

    if (name.startsWith("uri:") || name.startsWith("content:") || name.startsWith("file:")) {
        val bmp = rememberImageBitmapFromUri(name.removePrefix("uri:"))
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
            return
        }
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
        "bolt" -> Icons.Filled.Bolt
        else -> null
    }
    if (image != null) Icon(image, contentDescription) else Text(".")
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
    Divider(); Text(title, style = MaterialTheme.typography.titleSmall)
    val arr = owner.optJSONArray(arrayKey) ?: JSONArray().also { owner.put(arrayKey, it) }

    for (i in 0 until arr.length()) {
        val it = arr.getJSONObject(i)
        ElevatedCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Item ${i+1}", style = MaterialTheme.typography.labelLarge)
                    Row {
                        IconButton(onClick = { moveInArray(arr, i, -1); onChange() }) { Icon(Icons.Filled.KeyboardArrowUp, null) }
                        IconButton(onClick = { moveInArray(arr, i, +1); onChange() }) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                        IconButton(onClick = { removeAt(arr, i); onChange() }) { Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error) }
                    }
                }

                var type by remember { mutableStateOf(it.optString("type").ifBlank { if (it.has("label")) "button" else "icon" }) }
                ExposedDropdown(
                    value = type, label = "type",
                    options = listOf("icon","button","spacer")
                ) { sel -> type = sel; it.put("type", sel); onChange() }

                when (type) {
                    "icon" -> {
                        val icon = remember { mutableStateOf(it.optString("icon","more_vert")) }
                        IconPickerField(icon, "icon") { sel -> icon.value = sel; it.put("icon", sel); onChange() }
                        val action = remember { mutableStateOf(it.optString("actionId","")) }
                        OutlinedTextField(action.value, { v -> action.value = v; it.put("actionId", v); onChange() }, label = { Text("actionId (es. open_menu:menu_id)") })
                        val openMenuId = remember { mutableStateOf(it.optString("openMenuId","")) }
                        OutlinedTextField(openMenuId.value, { v -> openMenuId.value = v; if (v.isBlank()) it.remove("openMenuId") else it.put("openMenuId", v); onChange() }, label = { Text("openMenuId (opz.)") })
                    }
                    "button" -> {
                        val lbl = remember { mutableStateOf(it.optString("label","")) }
                        OutlinedTextField(lbl.value, { v -> lbl.value = v; it.put("label", v); onChange() }, label = { Text("label") })
                        val action = remember { mutableStateOf(it.optString("actionId","")) }
                        OutlinedTextField(action.value, { v -> action.value = v; it.put("actionId", v); onChange() }, label = { Text("actionId") })
                        var style by remember { mutableStateOf(it.optString("style","text")) }
                        ExposedDropdown(
                            value = style, label = "style",
                            options = listOf("text","outlined","tonal","primary")
                        ) { sel -> style = sel; it.put("style", sel); onChange() }
                    }
                    else -> {
                        var mode by remember { mutableStateOf(it.optString("mode","fixed")) }
                        ExposedDropdown(
                            value = mode, label = "mode",
                            options = listOf("fixed","expand")
                        ) { sel -> mode = sel; it.put("mode", sel); onChange() }
                        if (mode == "fixed") {
                            var width by remember { mutableStateOf(it.optDouble("widthDp",16.0).toInt().toString()) }
                            ExposedDropdown(
                                value = width, label = "width (dp)",
                                options = listOf("8","12","16","20","24","32","40","48","64")
                            ) { sel ->
                                width = sel
                                it.put("widthDp", sel.toDouble())
                                onChange()
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { arr.put(JSONObject("""{"type":"icon","icon":"more_vert","actionId":""}""")); onChange() }) { Text("+ Icon") }
        OutlinedButton(onClick = { arr.put(JSONObject("""{"type":"button","label":"Azione","style":"text","actionId":""}""")); onChange() }) { Text("+ Button") }
        TextButton(onClick = { arr.put(JSONObject("""{"type":"spacer","mode":"fixed","widthDp":16}""")); onChange() }) { Text("+ Spacer") }
    }
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
        onValueChange = { subtitle.value = it; if (it.isBlank()) topBar.remove("subtitle") else topBar.put("subtitle", it); onChange() },
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
private fun ContainerEditorSection(
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
    // Style
    var style by remember { mutableStateOf(container.optString("style","surface")) }
    ExposedDropdown(
        value = style, label = "style",
        options = listOf("text","outlined","tonal","primary","surface")
    ) { sel -> style = sel; container.put("style", sel); onChange() }

    // Tint
    var tint by remember { mutableStateOf(container.optString("tint","default")) }
    ExposedDropdown(
        value = tint, label = "tint",
        options = listOf("default","success","warning","error")
    ) { sel -> tint = sel; container.put("tint", sel); onChange() }

    // CustomColor (ruoli o hex)
    val customColor = remember { mutableStateOf(container.optString("customColor","")) }
    NamedColorPickerPlus(current = customColor.value, label = "customColor (palette/ruoli)", allowRoles = true) { hex ->
        customColor.value = hex
        if (hex.isBlank()) container.remove("customColor") else container.put("customColor", hex)
        onChange()
    }

    // Forma / corner
    var shape by remember { mutableStateOf(container.optString("shape","rounded")) }
    ExposedDropdown(
        value = shape, label = "shape",
        options = listOf("rounded","pill","cut")
    ) { sel -> shape = sel; container.put("shape", sel); onChange() }

    val corner = remember { mutableStateOf(container.optDouble("corner",12.0).toString()) }
    StepperField("corner (dp)", corner, 2.0) { v ->
        container.put("corner", v.coerceAtLeast(0.0)); onChange()
    }

    // Elevation
    val elev = remember { mutableStateOf(container.optDouble("elevationDp", if (style in listOf("surface","primary","tonal")) 1.0 else 0.0).toString()) }
    StepperField("elevation (dp)", elev, 1.0) { v ->
        if (v <= 0.0) container.remove("elevationDp") else container.put("elevationDp", v)
        onChange()
    }

    // Bordo
    var borderMode by remember { mutableStateOf(container.optString("borderMode", if (style=="outlined") "full" else "none")) }
    ExposedDropdown(
        value = when (borderMode) {
            "full" -> "full"
            "topBottom" -> "topBottom"
            else -> "none"
        },
        label = "borderMode",
        options = listOf("none","full","topBottom")
    ) { sel ->
        borderMode = sel
        container.put("borderMode", sel)
        onChange()
    }

    val borderTh = remember { mutableStateOf(container.optDouble("borderThicknessDp", if (borderMode!="none") 1.0 else 0.0).toString()) }
    StepperField("borderThickness (dp)", borderTh, 1.0) { v ->
        if (v <= 0.0) container.remove("borderThicknessDp") else container.put("borderThicknessDp", v)
        onChange()
    }

    val borderColor = remember { mutableStateOf(container.optString("borderColor","")) }
    NamedColorPickerPlus(current = borderColor.value, label = "borderColor", allowRoles = true) { hex ->
        borderColor.value = hex
        if (hex.isBlank()) container.remove("borderColor") else container.put("borderColor", hex)
        onChange()
    }

    // Gradient
    Divider(); Text("Gradient (opz.)", style = MaterialTheme.typography.titleSmall)
    var gradEnabled by remember { mutableStateOf(container.optJSONObject("gradient") != null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = gradEnabled, onCheckedChange = {
            gradEnabled = it
            if (it) {
                val g = container.optJSONObject("gradient") ?: JSONObject().also { j ->
                    j.put("colors", JSONArray().put("primary").put("tertiary"))
                    j.put("direction", "vertical")
                }
                container.put("gradient", g)
            } else {
                container.remove("gradient")
            }
            onChange()
        })
        Spacer(Modifier.width(8.dp)); Text("Abilita gradient")
    }
    container.optJSONObject("gradient")?.let { g ->
        val colorsArr = g.optJSONArray("colors") ?: JSONArray().also { g.put("colors", it) }
        while (colorsArr.length() < 2) colorsArr.put("primary")
        val c1 = remember { mutableStateOf(colorsArr.optString(0, "primary")) }
        val c2 = remember { mutableStateOf(colorsArr.optString(1, "tertiary")) }

        NamedColorPickerPlus(current = c1.value, label = "gradient color 1", allowRoles = true) { pick ->
            c1.value = pick
            colorsArr.put(0, pick)
            onChange()
        }
        NamedColorPickerPlus(current = c2.value, label = "gradient color 2", allowRoles = true) { pick ->
            c2.value = pick
            colorsArr.put(1, pick)
            onChange()
        }

        var dir by remember { mutableStateOf(g.optString("direction","vertical")) }
        ExposedDropdown(
            value = dir, label = "direction",
            options = listOf("vertical","horizontal")
        ) { sel -> dir = sel; g.put("direction", sel); onChange() }
    }

    // Immagine di background (facoltativa)
    Divider(); Text("Immagine di sfondo (opz.)", style = MaterialTheme.typography.titleSmall)
    var imgEnabled by remember { mutableStateOf(container.optJSONObject("image") != null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = imgEnabled, onCheckedChange = {
            imgEnabled = it
            if (it) container.put("image", container.optJSONObject("image") ?: JSONObject().apply {
                put("source","")
                put("contentScale","crop")
                put("alpha", 1.0)
            }) else container.remove("image")
            onChange()
        })
        Spacer(Modifier.width(8.dp)); Text("Abilita immagine")
    }
    container.optJSONObject("image")?.let { img ->
        ImagePickerRow(
            label = "source",
            current = img.optString("source",""),
            onChange = { src -> img.put("source", src); onChange() },
            onClear = { img.put("source",""); onChange() }
        )
        var scale by remember { mutableStateOf(img.optString("contentScale","crop")) }
        ExposedDropdown(
            value = scale, label = "contentScale",
            options = listOf("crop","fit","fill")
        ) { sel -> scale = sel; img.put("contentScale", sel); onChange() }
        val alpha = remember { mutableStateOf(img.optDouble("alpha",1.0).toString()) }
        StepperField("alpha (0..1)", alpha, 0.1) { v ->
            img.put("alpha", v.coerceIn(0.0,1.0)); onChange()
        }
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
    ) { sel -> align = sel; working.put("align", sel); onChange() }

    Divider()
    Text("Bottoni", style = MaterialTheme.typography.titleMedium)

    // raccogli ID pannelli laterali dal layout “root” corrente (se visibile nello scope)
    val sidePanelIds = remember { collectSidePanelIds(working.optJSONObject("_root") ?: JSONObject()) } // fallback vuoto

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

                // … (gli altri campi già presenti: size, width/height, corner, customColor, press, ecc.)

                val action = remember { mutableStateOf(btn.optString("actionId", "")) }
                OutlinedTextField(
                    value = action.value,
                    onValueChange = { action.value = it; btn.put("actionId", it); onChange() },
                    label = { Text("actionId (es. nav:settings)") }
                )

                // ▼▼ NUOVO: scelta side panel (se esistono id)
                if (sidePanelIds.isNotEmpty()) {
                    var sel by remember {
                        val seed = btn.optString("actionId","")
                        mutableStateOf(
                            if (seed.startsWith("sidepanel:open:"))
                                seed.removePrefix("sidepanel:open:")
                            else "(nessuno)"
                        )
                    }
                    ExposedDropdown(
                        value = sel,
                        label = "Apri side panel (opz.)",
                        options = listOf("(nessuno)") + sidePanelIds
                    ) { pick ->
                        sel = pick
                        if (pick == "(nessuno)") {
                            // non forzo, resta actionId manuale
                        } else {
                            btn.put("actionId", "sidepanel:open:$pick")
                        }
                        onChange()
                    }
                }
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
        options = FontCatalog.FONT_FAMILY_OPTIONS
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
        label = { Text("Titolo") }
    )

    val subtitle = remember { mutableStateOf(working.optString("subtitle","")) }
    OutlinedTextField(
        value = subtitle.value,
        onValueChange = { subtitle.value = it; working.put("subtitle", it); onChange() },
        label = { Text("Sottotitolo (opz.)") }
    )

    var style by remember { mutableStateOf(working.optString("style","titleMedium")) }
    ExposedDropdown(
        value = style, label = "style",
        options = listOf("displaySmall","headlineSmall","titleLarge","titleMedium","titleSmall","bodyLarge","bodyMedium")
    ) { sel ->
        style = sel
        working.put("style", sel)
        onChange()
    }

    var align by remember { mutableStateOf(working.optString("align","start")) }
    ExposedDropdown(
        value = align, label = "align",
        options = listOf("start","center","end")
    ) { sel -> align = sel; working.put("align", sel); onChange() }

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
        options = FontCatalog.FONT_FAMILY_OPTIONS
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
    ) { sel -> align = sel; working.put("align", sel); onChange() }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExposedDropdown(
    value: String,
    label: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
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
                                modifier = Modifier.fillMaxSize()
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
                                modifier = Modifier.fillMaxSize()
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



/* ---- Color parsing / pickers ---- */

@Composable
private fun parseColorOrRole(value: String?): Color? {
    if (value.isNullOrBlank()) return null
    val v = value.trim()
    if (v.equals("transparent", ignoreCase = true)) return Color.Transparent

    if (v.startsWith("#") && (v.length == 7 || v.length == 9)) {
        return try { Color(android.graphics.Color.parseColor(v)) } catch (_: Exception) { null }
    }

    val cs = MaterialTheme.colorScheme
    return when (v) {
        "primary"        -> cs.primary
        "onPrimary"      -> cs.onPrimary
        "secondary"      -> cs.secondary
        "onSecondary"    -> cs.onSecondary
        "tertiary"       -> cs.tertiary
        "onTertiary"     -> cs.onTertiary
        "error"          -> cs.error
        "onError"        -> cs.onError
        "surface"        -> cs.surface
        "surfaceVariant" -> cs.surfaceVariant
        "onSurface"      -> cs.onSurface
        "outline"        -> cs.outline
        else -> null
    }
}

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

/* ---- Readability helper ---- */
private fun bestOnColor(bg: Color): Color {
    val l = 0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue
    return if (l < 0.5f) Color.White else Color.Black
}

/* =========================================================
 * JSON utils
 * ========================================================= */

internal fun collectMenus(root: JSONObject): Map<String, JSONArray> {
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
            is JSONArray -> {
                for (i in 0 until n.length()) walk(n.opt(i))
            }
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

// Funzione composable per potere espandere in futuro a font di risorsa
@Composable
private fun applyTextStyleOverrides(node: JSONObject, base: TextStyle): TextStyle {
    var st = base

    // dimensione (sp)
    val size = node.optDouble("textSizeSp", Double.NaN)
    if (!size.isNaN()) {
        st = st.copy(fontSize = TextUnit(size.toFloat(), TextUnitType.Sp))
    }

    // peso
    when (node.optString("fontWeight", "")) {
        "w100" -> st = st.copy(fontWeight = FontWeight.Thin)
        "w200" -> st = st.copy(fontWeight = FontWeight.ExtraLight)
        "w300" -> st = st.copy(fontWeight = FontWeight.Light)
        "w400" -> st = st.copy(fontWeight = FontWeight.Normal)
        "w500" -> st = st.copy(fontWeight = FontWeight.Medium)
        "w600" -> st = st.copy(fontWeight = FontWeight.SemiBold)
        "w700" -> st = st.copy(fontWeight = FontWeight.Bold)
        "w800" -> st = st.copy(fontWeight = FontWeight.ExtraBold)
        "w900" -> st = st.copy(fontWeight = FontWeight.Black)
    }

    // stile (italic)
    val styleKey = node.optString("fontStyle", "")
    val italicFlag = node.optBoolean("italic", false)
    if (styleKey.equals("italic", ignoreCase = true) || italicFlag) {
        st = st.copy(fontStyle = FontStyle.Italic)
    } else if (styleKey.equals("normal", ignoreCase = true)) {
        st = st.copy(fontStyle = FontStyle.Normal)
    }

    // famiglia: ruoli base + custom (se in futuro aggiungerai risorse font)
    val familyKey = node.optString("fontFamily", "")
    val family = when (familyKey) {
        "serif"     -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        "cursive"   -> FontFamily.Cursive
        "sans"      -> FontFamily.SansSerif
        ""          -> null
        else        -> FontCatalog.resolveFontFamily(familyKey)
    }
    if (family != null) st = st.copy(fontFamily = family)

    // spaziatura lettere (sp) e interlinea (sp)
    val letterSp = node.optDouble("letterSpacingSp", Double.NaN)
    if (!letterSp.isNaN()) st = st.copy(letterSpacing = TextUnit(letterSp.toFloat(), TextUnitType.Sp))

    val lineH = node.optDouble("lineHeightSp", Double.NaN)
    if (!lineH.isNaN()) st = st.copy(lineHeight = TextUnit(lineH.toFloat(), TextUnitType.Sp))

    return st
}
