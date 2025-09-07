@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.ui.unit.ExperimentalUnitApi::class
)

package ai.runow.ui.renderer

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/* =========================================================
 * ENTRY ROOT (per demo)
 * ========================================================= */
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
 * SCHERMATA
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
    var layout: JSONObject? by remember(screenName) { mutableStateOf(UiLoader.loadLayout(ctx, screenName)) }
    var tick by remember { mutableStateOf(0) }

    if (layout == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Layout '$screenName' non trovato", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    // Raccolta menù dai blocchi
    val menus: Map<String, JSONArray> = remember(layout, tick) { collectMenus(layout!!) }
    // Id pannelli laterali disponibili
    val sidePanelIds: List<String> = remember(layout, tick) { collectSidePanelIds(layout!!) }

    // Path selezionato per ispettore blocchi
    var selectedPath by remember(screenName) { mutableStateOf<String?>(null) }
    // Stato pannello laterale aperto
    var openedSidePanelId by remember { mutableStateOf<String?>(null) }
    val openSidePanel: (String) -> Unit = { id ->
        if (id.isNotBlank()) openedSidePanelId = id
    }

    // Stato menu designer centrale
    var designMode by rememberSaveable(screenName) { mutableStateOf(designerMode) }
    var centerOverlaySize by remember { mutableStateOf(IntSize.Zero) }

    Box(Modifier.fillMaxSize()) {

        RenderRootScaffold(
            layout = layout!!,
            dispatch = dispatch,
            uiState = uiState,
            designerMode = designMode,
            menus = menus,
            sidePanelIds = sidePanelIds,
            selectedPathSetter = { selectedPath = it },
            scaffoldPadding = scaffoldPadding,
            openSidePanel = openSidePanel
        )

        // Overlay pannelli laterali
        RenderSidePanelsOverlay(
            layout = layout!!,
            openedId = openedSidePanelId,
            onDismiss = { openedSidePanelId = null }
        )

        // Menu Designer centrale (fisso, trasparente, scroll)
        if (designMode) {
            DesignerOverlayCenter(
                screenName = screenName,
                layout = layout!!,
                selectedPath = selectedPath,
                setSelectedPath = { selectedPath = it },
                onLayoutChange = {
                    UiLoader.saveDraft(ctx, screenName, layout!!)
                    layout = JSONObject(layout.toString())
                    tick++
                },
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
                onOpenPanel = { openedSidePanelId = it },
                onOverlaySize = { centerOverlaySize = it },
                menus = menus,
                sidePanelIds = sidePanelIds
            )
        }

        DesignSwitchKnob(
            isDesigner = designMode,
            onToggle = { designMode = !designMode }
        )
    }
}

/* =========================================================
 * TOGGLE MODALITÀ DESIGNER
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
                detectVerticalDragGestures { _, dy ->
                    offsetY = (offsetY + dy).coerceIn(-maxDragPx, maxDragPx)
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
 * RESOLVE + STYLED CONTAINER (riuso per topbar, bottombar, card, sidepanel…)
 * ========================================================= */

private data class ResolvedContainer(
    val style: String,                 // filled, tonal, outlined, text, lines
    val containerColor: Color?,        // colore sfondo (eventuale)
    val contentColor: Color,           // colore testo/icone
    val borderColor: Color?,           // colore bordo/linee
    val borderWidth: Dp,               // spessore bordo
    val corner: Dp,                    // raggio angolo
    val alpha: Float,                  // trasparenza
    val brush: Brush?,                 // gradiente
    val image: ImageBitmap?,           // bitmap immagine (opzionale)
    val imageScale: ContentScale       // crop/fit
)

@Composable
private fun resolveContainer(cfg: JSONObject?): ResolvedContainer {
    val cs = MaterialTheme.colorScheme
    val style = cfg?.optString("style", "filled") ?: "filled"
    val corner = Dp(cfg?.optDouble("corner", 12.0)?.toFloat() ?: 12f)
    val borderWidth = Dp(cfg?.optDouble("borderWidthDp", 0.0)?.toFloat() ?: 0f)
    val alpha = cfg?.optDouble("alpha", 1.0)?.toFloat()?.coerceIn(0f, 1f) ?: 1f

    val containerColor = parseColorOrRole(cfg?.optString("containerColor", "")) ?: when (style) {
        "tonal"  -> cs.secondaryContainer
        "filled" -> cs.surfaceVariant
        else     -> null
    }
    val borderColor = parseColorOrRole(cfg?.optString("borderColor", "")) ?: cs.outline
    val explicitContent = parseColorOrRole(cfg?.optString("contentColor", ""))

    val brush = cfg?.optJSONObject("gradient")?.let { brushFromJson(it) }

    // immagine
    val imgObj = cfg?.optJSONObject("image")
    val imgUri = imgObj?.optString("uri", "")?.takeIf { it.isNotBlank() }
    val bitmap = rememberImageBitmapFromUri(imgUri)
    val imageScale = when (imgObj?.optString("scale", "fit")) {
        "crop" -> ContentScale.Crop
        else   -> ContentScale.Fit
    }

    val baseBg = containerColor ?: (if (style == "text" || style == "outlined" || style == "lines") Color.Transparent else cs.surfaceVariant)
    val contentColor = explicitContent ?: bestOnColor(
        // stima colore di testo su sfondo; se c'è gradiente/immagine non possiamo calcolare, fallback su onSurface
        if (containerColor != null) containerColor.copy(alpha = alpha) else cs.surface
    )

    return ResolvedContainer(
        style = style,
        containerColor = if (containerColor != null) containerColor.copy(alpha = alpha) else null,
        contentColor = contentColor,
        borderColor = borderColor,
        borderWidth = borderWidth,
        corner = corner,
        alpha = alpha,
        brush = brush,
        image = bitmap,
        imageScale = imageScale
    )
}

@Composable
private fun StyledContainer(
    cfg: JSONObject?,
    modifier: Modifier = Modifier,
    fillPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val rc = resolveContainer(cfg)
    val shape: CornerBasedShape = RoundedCornerShape(rc.corner)

    Box(modifier.clip(shape)) {
        // Layer: immagine sotto
        rc.image?.let {
            Image(
                painter = BitmapPainter(it),
                contentDescription = null,
                contentScale = rc.imageScale,
                modifier = Modifier.matchParentSize()
            )
        }
        // Layer: gradiente
        rc.brush?.let {
            Box(Modifier.matchParentSize().background(it))
        }
        // Layer: colore
        rc.containerColor?.let {
            Box(Modifier.matchParentSize().background(it))
        }

        // Contenuto con padding (testo/icone con contentColor)
        CompositionLocalProvider(LocalContentColor provides rc.contentColor) {
            Box(Modifier.fillMaxSize().padding(fillPadding)) {
                content()
            }
        }

        // Bordo o "lines"
        when (rc.style) {
            "lines" -> {
                Divider(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                    thickness = rc.borderWidth.coerceAtLeast(1.dp),
                    color = rc.borderColor ?: LocalContentColor.current.copy(alpha = 0.65f)
                )
                Divider(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    thickness = rc.borderWidth.coerceAtLeast(1.dp),
                    color = rc.borderColor ?: LocalContentColor.current.copy(alpha = 0.65f)
                )
            }
            "outlined" -> {
                Box(
                    Modifier
                        .matchParentSize()
                        .border(
                            BorderStroke(
                                width = rc.borderWidth.coerceAtLeast(1.dp),
                                color = rc.borderColor ?: LocalContentColor.current.copy(alpha = 0.65f)
                            ),
                            shape
                        )
                )
            }
        }
    }
}

/* =========================================================
 * TOP BAR
 * ========================================================= */
@Composable
private fun RenderTopBar(
    cfg: JSONObject,
    dispatch: (String) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    openSidePanel: (String) -> Unit
) {
    val variant = cfg.optString("variant", "small")
    val title = cfg.optString("title", "")
    val subtitle = cfg.optString("subtitle", "")
    val actions = cfg.optJSONArray("actions") ?: JSONArray()

    val actionsSlot: @Composable RowScope.() -> Unit = {
        for (i in 0 until actions.length()) {
            val a = actions.optJSONObject(i) ?: continue
            val sideId = a.optString("openSidePanelId", "")
            IconButton(onClick = {
                if (sideId.isNotBlank()) openSidePanel(sideId)
                else dispatch(a.optString("actionId"))
            }) {
                NamedIconEx(a.optString("icon", "more_vert"), null)
            }
        }
    }

    val containerCfg = cfg.optJSONObject("container")
    StyledContainer(
        cfg = containerCfg,
        modifier = Modifier.fillMaxWidth()
    ) {
        val titleSlot: @Composable () -> Unit = {
            if (subtitle.isBlank()) {
                Text(title)
            } else {
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(subtitle, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        when (variant) {
            "center" -> CenterAlignedTopAppBar(
                title = { titleSlot() },
                actions = actionsSlot,
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
            "medium" -> MediumTopAppBar(
                title = { titleSlot() },
                actions = actionsSlot,
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.mediumTopAppBarColors(containerColor = Color.Transparent)
            )
            "large" -> LargeTopAppBar(
                title = { titleSlot() },
                actions = actionsSlot,
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = Color.Transparent)
            )
            else -> TopAppBar(
                title = { titleSlot() },
                actions = actionsSlot,
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    }
}

/* =========================================================
 * ROOT SCAFFOLD
 * ========================================================= */
@Composable
private fun RenderRootScaffold(
    layout: JSONObject,
    dispatch: (String) -> Unit,
    uiState: MutableMap<String, Any>,
    designerMode: Boolean,
    menus: Map<String, JSONArray>,
    sidePanelIds: List<String>,
    selectedPathSetter: (String) -> Unit,
    scaffoldPadding: PaddingValues,
    openSidePanel: (String) -> Unit
) {
    val scroll = layout.optBoolean("scroll", true)

    val topBarConf = layout.optJSONObject("topBar")
    val bottomButtons = layout.optJSONArray("bottomButtons") ?: JSONArray()
    val fab = layout.optJSONObject("fab")

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
            topBarConf?.let {
                RenderTopBar(it, dispatch, topScrollBehavior, openSidePanel)
            }
        },
        bottomBar = {
            if (bottomButtons.length() > 0) {
                val container = layout.optJSONObject("bottomBarContainer")
                StyledContainer(
                    cfg = container,
                    modifier = Modifier.fillMaxWidth(),
                    fillPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until bottomButtons.length()) {
                            val btn = bottomButtons.optJSONObject(i) ?: continue
                            val label = btn.optString("label", "Button")
                            val action = btn.optString("actionId", "")
                            val sideId = btn.optString("openSidePanelId", "")
                            TextButton(onClick = {
                                if (sideId.isNotBlank()) openSidePanel(sideId) else dispatch(action)
                            }) {
                                Text(label)
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            fab?.let {
                FloatingActionButton(onClick = {
                    val sideId = it.optString("openSidePanelId", "")
                    if (sideId.isNotBlank()) openSidePanel(sideId)
                    else dispatch(it.optString("actionId", ""))
                }) {
                    NamedIconEx(it.optString("icon", "play_arrow"), null)
                }
            }
        }
    ) { innerPadding ->
        val blocks = layout.optJSONArray("blocks") ?: JSONArray()

        val host: @Composable () -> Unit = {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(scaffoldPadding)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
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
                        sidePanelIds = sidePanelIds,
                        onSelect = { p -> selectedPathSetter(p) },
                        onOpenInspector = { p -> selectedPathSetter(p) },
                        openSidePanel = openSidePanel
                    )
                }
            }
        }
        if (scroll) {
            Column(Modifier.verticalScroll(rememberScrollState())) { host() }
        } else host()
    }
}

/* =========================================================
 * OVERLAY PANNELLI LATERALI
 * ========================================================= */
@Composable
private fun RenderSidePanelsOverlay(
    layout: JSONObject,
    openedId: String?,
    onDismiss: () -> Unit
) {
    val panels = layout.optJSONArray("sidePanels") ?: JSONArray()
    val panel = (0 until panels.length()).mapNotNull { panels.optJSONObject(it) }
        .firstOrNull { it.optString("id") == openedId }

    val anim = panel?.optString("animation", "slide_from_left") ?: "slide_from_left"
    val side = panel?.optString("side", "left") ?: "left"
    val widthDp = Dp(panel?.optDouble("widthDp", 320.0)?.toFloat() ?: 320f)
    val heightDp = Dp(panel?.optDouble("heightDp", 0.0)?.toFloat() ?: 0f) // 0 => wrap

    val enter = when (anim) {
        "slide_from_right" -> slideInHorizontally(animationSpec = tween(220)) { it }
        "slide_from_top" -> slideInVertically(animationSpec = tween(220)) { -it }
        "slide_from_bottom" -> slideInVertically(animationSpec = tween(220)) { it }
        "fade_scale" -> fadeIn(animationSpec = tween(180))
        else -> slideInHorizontally(animationSpec = tween(220)) { -it }
    }
    val exit = when (anim) {
        "slide_from_right" -> slideOutHorizontally(animationSpec = tween(200)) { it }
        "slide_from_top" -> slideOutVertically(animationSpec = tween(200)) { -it }
        "slide_from_bottom" -> slideOutVertically(animationSpec = tween(200)) { it }
        "fade_scale" -> fadeOut(animationSpec = tween(160))
        else -> slideOutHorizontally(animationSpec = tween(200)) { -it }
    }

    Box(Modifier.fillMaxSize()) {
        // scrim
        AnimatedVisibility(visible = openedId != null, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(onClick = onDismiss)
            )
        }

        AnimatedVisibility(visible = openedId != null, enter = enter, exit = exit) {
            val container = panel?.optJSONObject("container")
            StyledContainer(
                cfg = container,
                modifier = Modifier
                    .align(
                        when (side) {
                            "right" -> Alignment.CenterEnd
                            "top" -> Alignment.TopCenter
                            "bottom" -> Alignment.BottomCenter
                            else -> Alignment.CenterStart
                        }
                    )
                    .width(if (side == "left" || side == "right") widthDp else Modifier.wrapContentWidth().let { widthDp }.let { it })
                    .then(
                        if (heightDp.value > 0f) Modifier.height(heightDp)
                        else Modifier.wrapContentHeight()
                    )
            ) {
                Column(
                    Modifier
                        .widthIn(max = widthDp)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(panel?.optString("title", "") ?: "")
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Chiudi") }
                    }
                    val blocks = panel?.optJSONArray("blocks") ?: JSONArray()
                    for (i in 0 until blocks.length()) {
                        val b = blocks.optJSONObject(i) ?: continue
                        RenderBlock(
                            block = b,
                            dispatch = {},
                            uiState = mutableMapOf(),
                            designerMode = false,
                            path = "/sidePanels/${panel?.optString("id")}/blocks/$i",
                            menus = emptyMap(),
                            sidePanelIds = emptyList(),
                            onSelect = {},
                            onOpenInspector = {},
                            openSidePanel = {}
                        )
                    }
                }
            }
        }
    }
}

/* =========================================================
 * DESIGNER OVERLAY (CENTRALE)
 * ========================================================= */
@Composable
private fun BoxScope.DesignerOverlayCenter(
    screenName: String,
    layout: JSONObject,
    selectedPath: String?,
    setSelectedPath: (String?) -> Unit,
    onLayoutChange: () -> Unit,
    onPublish: () -> Unit,
    onReset: () -> Unit,
    onOpenPanel: (String) -> Unit,
    onOverlaySize: (IntSize) -> Unit,
    menus: Map<String, JSONArray>,
    sidePanelIds: List<String>
) {
    val scrollState = rememberScrollState()
    val bg = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
    val shape = RoundedCornerShape(16.dp)

    Surface(
        tonalElevation = 8.dp,
        shape = shape,
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth(0.96f)
            .fillMaxHeight(0.82f)
            .onGloballyPositioned { onOverlaySize(IntSize(it.size.width, it.size.height)) }
            .shadow(12.dp, shape)
            .background(Color.Transparent)
    ) {
        Box(
            Modifier
                .background(bg, shape)
                .padding(12.dp)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Layout – Palette e Azioni", style = MaterialTheme.typography.titleMedium)

                // PALETTE
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    }) { Icon(Icons.Filled.Widgets, null); Spacer(Modifier.width(6.dp)); Text("ButtonRow") }

                    FilledTonalButton(onClick = {
                        val path = insertBlockAndReturnPath(layout, selectedPath, newList(), "after")
                        setSelectedPath(path); onLayoutChange()
                    }) { Icon(Icons.Filled.List, null); Spacer(Modifier.width(6.dp)); Text("List") }

                    FilledTonalButton(onClick = {
                        val path = insertBlockAndReturnPath(layout, selectedPath, newSpacer(), "after")
                        setSelectedPath(path); onLayoutChange()
                    }) { Icon(Icons.Filled.SpaceBar, null); Spacer(Modifier.width(6.dp)); Text("SpacerV") }

                    FilledTonalButton(onClick = {
                        val path = insertBlockAndReturnPath(layout, selectedPath, newDividerV(), "after")
                        setSelectedPath(path); onLayoutChange()
                    }) { Icon(Icons.Filled.MoreVert, null); Spacer(Modifier.width(6.dp)); Text("DividerV") }

                    FilledTonalButton(onClick = {
                        val iconPath = insertIconMenuReturnIconPath(layout, selectedPath)
                        setSelectedPath(iconPath); onLayoutChange()
                    }) { Icon(Icons.Filled.MoreHoriz, null); Spacer(Modifier.width(6.dp)); Text("Icon+Menu") }

                    FilledTonalButton(onClick = {
                        val path = insertBlockAndReturnPath(layout, selectedPath, newCard(), "after")
                        setSelectedPath(path); onLayoutChange()
                    }) { Icon(Icons.Filled.ViewAgenda, null); Spacer(Modifier.width(6.dp)); Text("Card") }

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

                // SELEZIONE / SALVAZIONE
                ElevatedCard {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Selezione blocco", style = MaterialTheme.typography.labelLarge)
                            Row {
                                OutlinedButton(onClick = {
                                    selectedPath?.let {
                                        val newPath = moveAndReturnNewPath(layout, it, -1)
                                        setSelectedPath(newPath); onLayoutChange()
                                    }
                                }) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null); Text("Su") }
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(onClick = {
                                    selectedPath?.let {
                                        val newPath = moveAndReturnNewPath(layout, it, +1)
                                        setSelectedPath(newPath); onLayoutChange()
                                    }
                                }) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null); Text("Giù") }
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(onClick = {
                                    selectedPath?.let { duplicate(layout, it); onLayoutChange() }
                                }) { Icon(Icons.Filled.ContentCopy, contentDescription = null); Text("Duplica") }
                                Spacer(Modifier.width(8.dp))
                                TextButton(
                                    onClick = {
                                        selectedPath?.let { remove(layout, it); setSelectedPath(null); onLayoutChange() }
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) { Icon(Icons.Filled.Delete, contentDescription = null); Text("Elimina") }
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(onClick = { UiLoader.saveDraft(LocalContext.current, screenName, layout); }) { Text("Salva bozza") }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = onPublish) { Text("Pubblica") }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = onReset) { Text("Reset") }
                        }
                    }
                }

                // ROOT INSPECTOR (modifiche istantanee)
                RootInspectorPanel(
                    working = layout,
                    onChange = { onLayoutChange() },
                    sidePanelIds = sidePanelIds
                )
            }
        }
    }
}

/* =========================================================
 * RENDER BLOCCHI
 * ========================================================= */
@Composable
private fun RenderBlock(
    block: JSONObject,
    dispatch: (String) -> Unit,
    uiState: MutableMap<String, Any>,
    designerMode: Boolean,
    path: String,
    menus: Map<String, JSONArray>,
    sidePanelIds: List<String>,
    onSelect: (String) -> Unit,
    onOpenInspector: (String) -> Unit,
    openSidePanel: (String) -> Unit
) {
    val borderSelected = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)

    @Composable
    fun Wrapper(content: @Composable () -> Unit) {
        if (designerMode) {
            Box {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    border = borderSelected,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(block.optString("type", ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        content()
                    }
                }
                Box(Modifier.matchParentSize().clickable(onClick = { onSelect(path) }))
            }
        } else {
            Box { content() }
        }
    }

    when (block.optString("type")) {
        "IconButton" -> Wrapper {
            val iconName = block.optString("icon", "more_vert")
            val openMenuId = block.optString("openMenuId", "")
            val actionId = block.optString("actionId", "")
            val panelId = block.optString("openSidePanelId", "")
            var expanded by remember { mutableStateOf(false) }

            Box {
                IconButton(onClick = {
                    when {
                        panelId.isNotBlank() -> openSidePanel(panelId)
                        openMenuId.isNotBlank() || actionId.startsWith("open_menu:") -> expanded = true
                        actionId.isNotBlank() -> dispatch(actionId)
                    }
                }) { NamedIconEx(iconName, null) }

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
            val height = Dp(block.optDouble("heightDp", 160.0).toFloat())
            val corner = Dp(block.optDouble("corner", 12.0).toFloat())
            val scale = when (block.optString("contentScale","fit")) {
                "crop" -> ContentScale.Crop
                else   -> ContentScale.Fit
            }

            val resId = if (source.startsWith("res:"))
                LocalContext.current.resources.getIdentifier(source.removePrefix("res:"), "drawable", LocalContext.current.packageName)
            else 0

            Surface(shape = RoundedCornerShape(corner), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                if (resId != 0) {
                    Image(
                        painter = painterResource(resId),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(height),
                        contentScale = scale
                    )
                } else {
                    val bmp = rememberImageBitmapFromUri(source)
                    if (bmp != null) {
                        Image(
                            painter = BitmapPainter(bmp),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(height),
                            contentScale = scale
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
                            Text("Image: ${if (source.isBlank()) "(not set)" else source}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        "Card" -> {
            val variant = block.optString("variant","elevated")
            val clickAction = block.optString("clickActionId","")
            val container = block.optJSONObject("container")

            val innerContent: @Composable () -> Unit = {
                val innerBlocks = block.optJSONArray("blocks") ?: JSONArray()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 0 until innerBlocks.length()) {
                        val b = innerBlocks.optJSONObject(i) ?: continue
                        val p2 = "$path/blocks/$i"
                        RenderBlock(b, dispatch, uiState, designerMode, p2, menus, sidePanelIds, onSelect, onOpenInspector, openSidePanel)
                    }
                }
            }

            val baseMod = Modifier
                .fillMaxWidth()
                .then(if (clickAction.isNotBlank() && !designerMode) Modifier.clickable { dispatch(clickAction) } else Modifier)

            ContainerOverlayGear(designerMode, path, onOpenInspector) {
                // Usare StyledContainer per tutto il contenitore
                StyledContainer(cfg = container, modifier = baseMod, fillPadding = PaddingValues(12.dp)) {
                    innerContent()
                }
            }
        }

        "Tabs" -> {
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
                        RenderBlock(b, dispatch, uiState, designerMode, p2, menus, sidePanelIds, onSelect, onOpenInspector, openSidePanel)
                    }
                }
            }
        }

        "SectionHeader" -> Wrapper {
            val style = mapTextStyle(block.optString("style", "titleMedium"))
            val alignTxt = mapTextAlign(block.optString("align", "start"))
            val clickAction = block.optString("clickActionId", "")
            val textColor = parseColorOrRole(block.optString("textColor", ""))

            Column(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (clickAction.isNotBlank() && !designerMode)
                            Modifier.clickable { dispatch(clickAction) }
                        else Modifier
                    )
            ) {
                Text(
                    text = block.optString("title", ""),
                    style = applyTextStyleOverrides(block, style),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = alignTxt,
                    color = textColor ?: LocalContentColor.current
                )
                val sub = block.optString("subtitle", "")
                if (sub.isNotBlank()) {
                    Text(
                        sub,
                        style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyMedium),
                        textAlign = alignTxt,
                        modifier = Modifier.fillMaxWidth(),
                        color = textColor ?: LocalContentColor.current
                    )
                }
            }
        }

        "MetricsGrid" -> Wrapper {
            val tiles = block.optJSONArray("tiles") ?: JSONArray()
            val cols = block.optInt("columns", 2).coerceIn(1, 3)
            GridSection(tiles, cols, uiState)
        }

        "ButtonRow" -> Wrapper {
            val align = when (block.optString("align")) {
                "start" -> Arrangement.Start
                "end" -> Arrangement.End
                "space_between" -> Arrangement.SpaceBetween
                "space_around" -> Arrangement.SpaceAround
                "space_evenly" -> Arrangement.SpaceEvenly
                else -> Arrangement.Center
            }
            val buttons = block.optJSONArray("buttons") ?: JSONArray()
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = align,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until buttons.length()) {
                    val btn = buttons.optJSONObject(i) ?: continue
                    val kind = btn.optString("kind", "button")
                    if (kind == "spacer") {
                        Spacer(Modifier.width(Dp(btn.optDouble("widthDp", 12.0).toFloat())))
                        continue
                    }
                    val label = btn.optString("label", "Button")
                    val styleKey = btn.optString("style", "primary")
                    val action = btn.optString("actionId", "")
                    val sideId = btn.optString("openSidePanelId", "")
                    val sizeKey = btn.optString("size", "md")
                    val tintKey = btn.optString("tint", "default")
                    val shapeKey = btn.optString("shape", "rounded")
                    val corner = Dp(btn.optDouble("corner", 20.0).toFloat())
                    val pressKey = btn.optString("pressEffect", "none")
                    val icon = btn.optString("icon", "")

                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (pressKey == "scale" && pressed) 0.96f else 1f, label = "btnScale"
                    )
                    val alpha by animateFloatAsState(
                        targetValue = if (pressKey == "alpha" && pressed) 0.6f else 1f, label = "btnAlpha"
                    )
                    val rotation by animateFloatAsState(
                        targetValue = if (pressKey == "rotate" && pressed) -2f else 0f, label = "btnRot"
                    )

                    val shape = when (shapeKey) {
                        "pill" -> RoundedCornerShape(50)
                        "cut" -> CutCornerShape(corner)
                        else -> RoundedCornerShape(corner)
                    }

                    var (container, content, border) = mapButtonColors(styleKey, tintKey)
                    run {
                        val hex = btn.optString("customColor", "")
                        val col = parseColorOrRole(hex)
                        if (col != null) {
                            container = col
                            content = bestOnColor(col)
                        }
                    }

                    val heightMod =
                        if (!btn.optDouble("heightDp", Double.NaN).isNaN())
                            Modifier.height(Dp(btn.optDouble("heightDp", Double.NaN).toFloat()))
                        else sizeModifier(sizeKey)

                    val widthMod =
                        if (!btn.optDouble("widthDp", Double.NaN).isNaN())
                            Modifier.width(Dp(btn.optDouble("widthDp", Double.NaN).toFloat()))
                        else Modifier

                    val baseMod = Modifier
                        .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha, rotationZ = rotation)
                        .then(heightMod)
                        .then(widthMod)

                    Spacer(Modifier.width(6.dp))

                    val contentSlot: @Composable () -> Unit = {
                        if (icon.isNotBlank()) {
                            IconText(label = label, icon = icon)
                        } else {
                            Text(label)
                        }
                    }

                    when (styleKey) {
                        "outlined" -> OutlinedButton(
                            onClick = {
                                if (sideId.isNotBlank()) openSidePanel(sideId) else dispatch(action)
                            },
                            shape = shape,
                            border = BorderStroke(width = border, color = content),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = content),
                            interactionSource = interaction,
                            modifier = baseMod
                        ) { contentSlot() }

                        "tonal" -> FilledTonalButton(
                            onClick = {
                                if (sideId.isNotBlank()) openSidePanel(sideId) else dispatch(action)
                            },
                            shape = shape,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = container,
                                contentColor = content
                            ),
                            interactionSource = interaction,
                            modifier = baseMod
                        ) { contentSlot() }

                        "text" -> TextButton(
                            onClick = {
                                if (sideId.isNotBlank()) openSidePanel(sideId) else dispatch(action)
                            },
                            shape = shape,
                            colors = ButtonDefaults.textButtonColors(contentColor = content),
                            interactionSource = interaction,
                            modifier = baseMod
                        ) { contentSlot() }

                        else -> Button(
                            onClick = {
                                if (sideId.isNotBlank()) openSidePanel(sideId) else dispatch(action)
                            },
                            shape = shape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = container,
                                contentColor = content
                            ),
                            interactionSource = interaction,
                            modifier = baseMod
                        ) { contentSlot() }
                    }
                }
            }
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
                            label = { Text(label, style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyMedium)) },
                            leadingIcon = if (current == v) { { Icon(Icons.Filled.Check, null) } } else null
                        )
                    } else {
                        val current = (uiState[bind] as? Boolean) ?: false
                        FilterChip(
                            selected = current,
                            onClick = { uiState[bind] = !current },
                            label = { Text(label, style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyMedium)) },
                            leadingIcon = if (current) { { Icon(Icons.Filled.Check, null) } } else null
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
            val alignTxt = mapTextAlign(block.optString("align", "start"))
            val textColor = parseColorOrRole(block.optString("textColor",""))
            Column {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    ListItem(
                        headlineContent = {
                            Text(
                                item.optString("title", ""),
                                style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyLarge),
                                color = textColor ?: LocalContentColor.current,
                                textAlign = alignTxt,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        supportingContent = {
                            val sub = item.optString("subtitle", "")
                            if (sub.isNotBlank()) Text(
                                sub,
                                style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyMedium),
                                color = textColor ?: LocalContentColor.current,
                                textAlign = alignTxt,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(onClick = { dispatch(item.optString("actionId", "")) })
                    )
                    Divider()
                }
            }
        }

        "Divider" -> {
            val thick = Dp(block.optDouble("thickness", 1.0).toFloat())
            val padStart = Dp(block.optDouble("padStart", 0.0).toFloat())
            val padEnd = Dp(block.optDouble("padEnd", 0.0).toFloat())
            Divider(modifier = Modifier.padding(start = padStart, end = padEnd), thickness = thick)
        }

        "DividerV" -> {
            val thickness = Dp(block.optDouble("thickness", 1.0).toFloat())
            val height = Dp(block.optDouble("height", 24.0).toFloat())
            VerticalDivider(modifier = Modifier.height(height), thickness = thickness)
        }

        "Spacer" -> {
            Spacer(Modifier.height(Dp(block.optDouble("height", 8.0).toFloat())))
        }

        "Menu" -> {
            if (designerMode) {
                ElevatedCard { Text("Menu: ${block.optString("id")} (${block.optJSONArray("items")?.length() ?: 0})", Modifier.padding(8.dp)) }
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
 * CONTAINER OVERLAY GEAR
 * ========================================================= */
@Composable
private fun ContainerOverlayGear(
    designerMode: Boolean,
    path: String,
    onOpenInspector: (String) -> Unit,
    content: @Composable () -> Unit
) {
    if (!designerMode) { content(); return }
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
 * ROOT INSPECTOR (modifiche istantanee)
 * ========================================================= */
@Composable
private fun RootInspectorPanel(
    working: JSONObject,
    onChange: () -> Unit,
    sidePanelIds: List<String>
) {
    Text("Layout – Proprietà", style = MaterialTheme.typography.titleMedium)

    // Scroll on/off
    Row(verticalAlignment = Alignment.CenterVertically) {
        val value = remember { mutableStateOf(working.optBoolean("scroll", true)) }
        Switch(checked = value.value, onCheckedChange = {
            value.value = it; working.put("scroll", it); onChange()
        })
        Spacer(Modifier.width(8.dp)); Text("Contenuto scrollabile")
    }

    Divider(); Text("Top Bar", style = MaterialTheme.typography.titleSmall)
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
                            put("container", JSONObject().apply { put("style","filled") })
                        })
                    }
                } else working.remove("topBar")
                onChange()
            }
        )
        Spacer(Modifier.width(8.dp)); Text("Abilita TopBar")
    }
    working.optJSONObject("topBar")?.let { TopBarInspectorPanel(it, onChange, sidePanelIds) }

    Divider(); Text("Bottom Bar", style = MaterialTheme.typography.titleSmall)
    val bottomButtons = working.optJSONArray("bottomButtons") ?: JSONArray().also { working.put("bottomButtons", it) }
    // Container della bottom bar
    val bottomContainer = working.optJSONObject("bottomBarContainer") ?: JSONObject().also {
        it.put("style", "filled")
        working.put("bottomBarContainer", it)
    }
    ContainerStyleSection(bottomContainer, onChange)

    for (i in 0 until bottomButtons.length()) {
        val itx = bottomButtons.getJSONObject(i)
        ElevatedCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Button ${i+1}", style = MaterialTheme.typography.labelLarge)
                    Row {
                        IconButton(onClick = { moveInArray(bottomButtons, i, -1); onChange() }) { Icon(Icons.Filled.KeyboardArrowUp, null) }
                        IconButton(onClick = { moveInArray(bottomButtons, i, +1); onChange() }) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                        IconButton(onClick = { removeAt(bottomButtons, i); onChange() }) { Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error) }
                    }
                }
                val lbl = remember { mutableStateOf(itx.optString("label","")) }
                OutlinedTextField(lbl.value, { lbl.value = it; itx.put("label", it); onChange() }, label = { Text("label") })
                val act = remember { mutableStateOf(itx.optString("actionId","")) }
                OutlinedTextField(act.value, { act.value = it; itx.put("actionId", it); onChange() }, label = { Text("actionId") })

                // Side panel linker
                var selPanel by remember { mutableStateOf(itx.optString("openSidePanelId","")) }
                ExposedDropdown(
                    value = if (selPanel.isBlank()) "(nessuno)" else selPanel,
                    label = "openSidePanelId",
                    options = listOf("(nessuno)") + sidePanelIds
                ) { sel ->
                    selPanel = if (sel == "(nessuno)") "" else sel
                    if (selPanel.isBlank()) itx.remove("openSidePanelId") else itx.put("openSidePanelId", selPanel)
                    onChange()
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Button(onClick = {
        bottomButtons.put(JSONObject("""{"label":"Azione","actionId":""}""")); onChange()
    }) { Text("+ Aggiungi bottone") }

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

        // SidePanel dal FAB
        var selPanel by remember { mutableStateOf(fab.optString("openSidePanelId","")) }
        ExposedDropdown(
            value = if (selPanel.isBlank()) "(nessuno)" else selPanel,
            label = "openSidePanelId",
            options = listOf("(nessuno)") + sidePanelIds
        ) { sel ->
            selPanel = if (sel == "(nessuno)") "" else sel
            if (selPanel.isBlank()) fab.remove("openSidePanelId") else fab.put("openSidePanelId", selPanel)
            onChange()
        }
    }

    Divider(); Text("Side Panels", style = MaterialTheme.typography.titleSmall)
    val panels = working.optJSONArray("sidePanels") ?: JSONArray().also { working.put("sidePanels", it) }
    for (i in 0 until panels.length()) {
        val p = panels.getJSONObject(i)
        ElevatedCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Panel ${p.optString("id","")}", style = MaterialTheme.typography.labelLarge)
                    Row {
                        IconButton(onClick = { moveInArray(panels, i, -1); onChange() }) { Icon(Icons.Filled.KeyboardArrowUp, null) }
                        IconButton(onClick = { moveInArray(panels, i, +1); onChange() }) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                        IconButton(onClick = { removeAt(panels, i); onChange() }) { Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error) }
                    }
                }
                val id = remember { mutableStateOf(p.optString("id","panel_${i+1}")) }
                OutlinedTextField(id.value, {
                    id.value = it; if (it.isBlank()) p.remove("id") else p.put("id", it); onChange()
                }, label = { Text("id") })

                var side by remember { mutableStateOf(p.optString("side","left")) }
                ExposedDropdown(
                    value = side, label = "side",
                    options = listOf("left","right","top","bottom")
                ) { sel -> side = sel; p.put("side", sel); onChange() }

                var anim by remember { mutableStateOf(p.optString("animation","slide_from_left")) }
                ExposedDropdown(
                    value = anim, label = "animation",
                    options = listOf("slide_from_left","slide_from_right","slide_from_top","slide_from_bottom","fade_scale")
                ) { sel -> anim = sel; p.put("animation", sel); onChange() }

                val width = remember { mutableStateOf(p.optDouble("widthDp",320.0).toString()) }
                StepperField("width (dp)", width, 16.0) { v -> p.put("widthDp", v.coerceAtLeast(200.0)); onChange() }

                val height = remember { mutableStateOf(p.optDouble("heightDp",0.0).toString()) }
                StepperField("height (dp, 0 = wrap)", height, 16.0) { v -> if (v <= 0.0) p.remove("heightDp") else p.put("heightDp", v); onChange() }

                val title = remember { mutableStateOf(p.optString("title","")) }
                OutlinedTextField(title.value, { title.value = it; p.put("title", it); onChange() }, label = { Text("title") })

                // Container panel
                val cont = p.optJSONObject("container") ?: JSONObject().also { p.put("container", it) }
                ContainerStyleSection(cont, onChange)

                // (Opzionale) blocchi del pannello
                if (!p.has("blocks")) {
                    p.put("blocks", JSONArray().put(JSONObject("""{"type":"SectionHeader","title":"Pannello"}""")))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Button(onClick = { panels.put(JSONObject("""{"id":"panel_${panels.length()+1}","side":"left","animation":"slide_from_left","widthDp":320,"container":{"style":"filled"}}""")); onChange() }) {
        Text("+ Aggiungi side panel")
    }
}

@Composable
private fun ContainerStyleSection(cfg: JSONObject, onChange: () -> Unit) {
    // style
    var style by remember { mutableStateOf(cfg.optString("style","filled")) }
    ExposedDropdown(
        value = style, label = "container.style",
        options = listOf("filled","tonal","outlined","text","lines")
    ) { sel -> style = sel; cfg.put("style", sel); onChange() }

    // containerColor
    val cont = remember { mutableStateOf(cfg.optString("containerColor","")) }
    NamedColorPickerPlus(current = cont.value, label = "containerColor (opz., anche con text/outlined)", allowRoles = true) { hex ->
        cont.value = hex
        if (hex.isBlank()) cfg.remove("containerColor") else cfg.put("containerColor", hex)
        onChange()
    }

    // contentColor
    val contentColor = remember { mutableStateOf(cfg.optString("contentColor","")) }
    NamedColorPickerPlus(current = contentColor.value, label = "contentColor (opz.)", allowRoles = true) { hex ->
        contentColor.value = hex
        if (hex.isBlank()) cfg.remove("contentColor") else cfg.put("contentColor", hex)
        onChange()
    }

    // border
    val borderColor = remember { mutableStateOf(cfg.optString("borderColor","")) }
    NamedColorPickerPlus(current = borderColor.value, label = "borderColor (righe/bordo)", allowRoles = true) { hex ->
        borderColor.value = hex
        if (hex.isBlank()) cfg.remove("borderColor") else cfg.put("borderColor", hex)
        onChange()
    }
    val bw = remember { mutableStateOf(cfg.optDouble("borderWidthDp", 1.0).toString()) }
    StepperField("borderWidth (dp)", bw, 1.0) { v -> cfg.put("borderWidthDp", v.coerceAtLeast(0.0)); onChange() }

    // corner
    val corner = remember { mutableStateOf(cfg.optDouble("corner", 12.0).toString()) }
    StepperField("corner (dp)", corner, 1.0) { v -> cfg.put("corner", v.coerceAtLeast(0.0)); onChange() }

    // alpha
    val alpha = remember { mutableStateOf(cfg.optDouble("alpha", 1.0).toString()) }
    StepperField("alpha (0–1)", alpha, 0.05) { v -> cfg.put("alpha", v.coerceIn(0.0, 1.0)); onChange() }

    // gradient
    var gradEnabled by remember { mutableStateOf(cfg.optJSONObject("gradient") != null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = gradEnabled, onCheckedChange = {
            gradEnabled = it
            if (it) {
                if (!cfg.has("gradient")) cfg.put("gradient", JSONObject("""{"direction":"vertical","colors":["surface","surfaceVariant"]}"""))
            } else cfg.remove("gradient")
            onChange()
        })
        Spacer(Modifier.width(8.dp)); Text("Gradient")
    }
    cfg.optJSONObject("gradient")?.let { g ->
        val colorsArr = g.optJSONArray("colors") ?: JSONArray().also { g.put("colors", it) }
        while (colorsArr.length() < 2) colorsArr.put("surface")
        val c1 = remember { mutableStateOf(colorsArr.optString(0, "surface")) }
        val c2 = remember { mutableStateOf(colorsArr.optString(1, "surfaceVariant")) }

        NamedColorPickerPlus(current = c1.value, label = "gradient color 1", allowRoles = true) { pick ->
            c1.value = pick; colorsArr.put(0, pick); onChange()
        }
        NamedColorPickerPlus(current = c2.value, label = "gradient color 2", allowRoles = true) { pick ->
            c2.value = pick; colorsArr.put(1, pick); onChange()
        }

        var dir by remember { mutableStateOf(g.optString("direction","vertical")) }
        ExposedDropdown(value = dir, label = "direction", options = listOf("vertical","horizontal")) {
            sel -> dir = sel; g.put("direction", sel); onChange()
        }
    }

    // image
    var imgEnabled by remember { mutableStateOf(cfg.optJSONObject("image") != null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = imgEnabled, onCheckedChange = {
            imgEnabled = it
            if (it) {
                if (!cfg.has("image")) cfg.put("image", JSONObject("""{"uri":"","scale":"fit"}"""))
            } else cfg.remove("image")
            onChange()
        })
        Spacer(Modifier.width(8.dp)); Text("Immagine di sfondo")
    }
    cfg.optJSONObject("image")?.let { im ->
        val uri = remember { mutableStateOf(im.optString("uri","")) }
        OutlinedTextField(uri.value, {
            uri.value = it; im.put("uri", it); onChange()
        }, label = { Text("image.uri (res:xxx o content://)") })

        var scale by remember { mutableStateOf(im.optString("scale","fit")) }
        ExposedDropdown(value = scale, label = "image.scale", options = listOf("fit","crop")) {
            sel -> scale = sel; im.put("scale", sel); onChange()
        }
    }
}

/* =========================================================
 * ISPETTORI: TOP BAR / LIST / ecc. (principali)
 * ========================================================= */
@Composable
private fun TopBarInspectorPanel(topBar: JSONObject, onChange: () -> Unit, sidePanelIds: List<String>) {
    Spacer(Modifier.height(8.dp))

    var variant by remember { mutableStateOf(topBar.optString("variant","small")) }
    ExposedDropdown(value = variant, label = "variant", options = listOf("small","center","medium","large")) {
        sel -> variant = sel; topBar.put("variant", sel); onChange()
    }

    val title = remember { mutableStateOf(topBar.optString("title","")) }
    OutlinedTextField(value = title.value, onValueChange = {
        title.value = it; topBar.put("title", it); onChange()
    }, label = { Text("title") })

    val subtitle = remember { mutableStateOf(topBar.optString("subtitle","")) }
    OutlinedTextField(value = subtitle.value, onValueChange = {
        subtitle.value = it; if (it.isBlank()) topBar.remove("subtitle") else topBar.put("subtitle", it); onChange()
    }, label = { Text("subtitle (opz.)") })

    var scroll by remember { mutableStateOf(topBar.optString("scroll","none")) }
    ExposedDropdown(value = scroll, label = "scroll", options = listOf("none","pinned","enterAlways","exitUntilCollapsed")) {
        sel -> scroll = sel; if (sel=="none") topBar.remove("scroll") else topBar.put("scroll", sel); onChange()
    }

    Divider()
    Text("TopBar – Container", style = MaterialTheme.typography.titleSmall)
    val container = topBar.optJSONObject("container") ?: JSONObject().also { topBar.put("container", it) }
    ContainerStyleSection(container, onChange)

    Divider(); Text("Actions", style = MaterialTheme.typography.titleSmall)
    val actions = topBar.optJSONArray("actions") ?: JSONArray().also { topBar.put("actions", it) }
    for (i in 0 until actions.length()) {
        val itx = actions.getJSONObject(i)
        ElevatedCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Action ${i+1}", style = MaterialTheme.typography.labelLarge)
                    Row {
                        IconButton(onClick = { moveInArray(actions, i, -1); onChange() }) { Icon(Icons.Filled.KeyboardArrowUp, null) }
                        IconButton(onClick = { moveInArray(actions, i, +1); onChange() }) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                        IconButton(onClick = { removeAt(actions, i); onChange() }) { Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error) }
                    }
                }
                val icon = remember { mutableStateOf(itx.optString("icon","more_vert")) }
                IconPickerField(icon, "icon") { sel -> icon.value = sel; itx.put("icon", sel); onChange() }
                val act = remember { mutableStateOf(itx.optString("actionId","")) }
                OutlinedTextField(act.value, { act.value = it; itx.put("actionId", it); onChange() }, label = { Text("actionId") })

                // SidePanel opener
                var selPanel by remember { mutableStateOf(itx.optString("openSidePanelId","")) }
                ExposedDropdown(
                    value = if (selPanel.isBlank()) "(nessuno)" else selPanel,
                    label = "openSidePanelId",
                    options = listOf("(nessuno)") + sidePanelIds
                ) { sel ->
                    selPanel = if (sel == "(nessuno)") "" else sel
                    if (selPanel.isBlank()) itx.remove("openSidePanelId") else itx.put("openSidePanelId", selPanel)
                    onChange()
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Button(onClick = { actions.put(JSONObject("""{"icon":"more_vert","actionId":""}""")); onChange() }) {
        Text("+ Aggiungi action")
    }
}

@Composable
private fun ListInspectorPanel(working: JSONObject, onChange: () -> Unit) {
    Text("List – Proprietà testo", style = MaterialTheme.typography.titleMedium)

    val textSize = remember { mutableStateOf(working.optDouble("textSizeSp", Double.NaN).let { if (it.isNaN()) "" else it.toString() }) }
    ExposedDropdown(
        value = if (textSize.value.isBlank()) "(default)" else textSize.value, label = "textSize (sp)",
        options = listOf("(default)","12","14","16","18","20","22","24")
    ) { sel ->
        val v = if (sel == "(default)") "" else sel
        textSize.value = v
        if (v.isBlank()) working.remove("textSizeSp") else working.put("textSizeSp", v.toDouble())
        onChange()
    }

    var align by remember { mutableStateOf(working.optString("align","start")) }
    ExposedDropdown(value = align, label = "align", options = listOf("start","center","end")) {
        sel -> align = sel; working.put("align", sel); onChange()
    }

    var fontFamily by remember { mutableStateOf(working.optString("fontFamily", "")) }
    ExposedDropdown(
        value = if (fontFamily.isBlank()) "(default)" else fontFamily, label = "fontFamily",
        options = listOf("(default)","sans","serif","monospace","cursive")
    ) { sel ->
        val v = if (sel == "(default)") "" else sel
        fontFamily = v
        if (v.isBlank()) working.remove("fontFamily") else working.put("fontFamily", v)
        onChange()
    }

    var fontWeight by remember { mutableStateOf(working.optString("fontWeight", "")) }
    ExposedDropdown(
        value = if (fontWeight.isBlank()) "(default)" else fontWeight, label = "fontWeight",
        options = listOf("(default)","w300","w400","w500","w600","w700")
    ) { sel ->
        val v = if (sel == "(default)") "" else sel
        fontWeight = v
        if (v.isBlank()) working.remove("fontWeight") else working.put("fontWeight", v)
        onChange()
    }

    val textColor = remember { mutableStateOf(working.optString("textColor","")) }
    NamedColorPickerPlus(current = textColor.value, label = "textColor", allowRoles = true) { hex ->
        textColor.value = hex
        if (hex.isBlank()) working.remove("textColor") else working.put("textColor", hex)
        onChange()
    }
}

/* =========================================================
 * ISPETTORI BLOCCHI (sintetici ma completi)
 * ========================================================= */

@Composable private fun ButtonRowInspectorPanel(working: JSONObject, onChange: () -> Unit) {
    Text("ButtonRow – Proprietà", style = MaterialTheme.typography.titleMedium)

    var align by remember { mutableStateOf(working.optString("align", "center")) }
    ExposedDropdown(value = align, label = "align",
        options = listOf("start","center","end","space_between","space_around","space_evenly")
    ) { sel -> align = sel; working.put("align", sel); onChange() }

    Divider(); Text("Bottoni", style = MaterialTheme.typography.titleMedium)
    val buttons = working.optJSONArray("buttons") ?: JSONArray().also { working.put("buttons", it) }

    for (i in 0 until buttons.length()) {
        val btn = buttons.getJSONObject(i)
        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (btn.optString("kind","button")=="spacer") "Spacer ${i+1}" else "Bottone ${i+1}", style = MaterialTheme.typography.labelLarge)
                    Row {
                        IconButton(onClick = { moveInArray(buttons, i, -1); onChange() }) { Icon(Icons.Filled.KeyboardArrowUp, null) }
                        IconButton(onClick = { moveInArray(buttons, i, +1); onChange() }) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                        IconButton(onClick = { removeAt(buttons, i); onChange() }) { Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error) }
                    }
                }

                var kind by remember { mutableStateOf(btn.optString("kind","button")) }
                ExposedDropdown(value = kind, label = "kind", options = listOf("button","spacer")) { sel ->
                    kind = sel; btn.put("kind", sel); onChange()
                }

                if (kind == "spacer") {
                    val w = remember { mutableStateOf(btn.optDouble("widthDp", 12.0).toString()) }
                    StepperField("widthDp", w, 4.0) { v -> btn.put("widthDp", v.coerceAtLeast(4.0)); onChange() }
                } else {
                    val label = remember { mutableStateOf(btn.optString("label", "")) }
                    OutlinedTextField(label.value, { label.value = it; btn.put("label", it); onChange() }, label = { Text("label") })

                    val icon = remember { mutableStateOf(btn.optString("icon", "")) }
                    IconPickerField(icon, "icon") { sel -> icon.value = sel; btn.put("icon", sel); onChange() }

                    var style by remember { mutableStateOf(btn.optString("style", "primary")) }
                    ExposedDropdown(
                        value = style, label = "style",
                        options = listOf("primary","tonal","outlined","text")
                    ) { style = it; btn.put("style", it); onChange() }

                    var size by remember { mutableStateOf(btn.optString("size", "md")) }
                    ExposedDropdown(value = size, label = "size", options = listOf("xs","sm","md","lg","xl")) {
                        sel -> size = sel; btn.put("size", sel); onChange()
                    }

                    val customColor = remember { mutableStateOf(btn.optString("customColor", "")) }
                    NamedColorPickerPlus(current = customColor.value, label = "customColor (palette/ruoli)", allowRoles = true) { hex ->
                        customColor.value = hex
                        if (hex.isBlank()) btn.remove("customColor") else btn.put("customColor", hex)
                        onChange()
                    }

                    // Apertura SidePanel
                    var selPanel by remember { mutableStateOf(btn.optString("openSidePanelId","")) }
                    ExposedDropdown(
                        value = if (selPanel.isBlank()) "(nessuno)" else selPanel,
                        label = "openSidePanelId",
                        options = listOf("(nessuno)") + sidePanelIds
                    ) { sel ->
                        selPanel = if (sel == "(nessuno)") "" else sel
                        if (selPanel.isBlank()) btn.remove("openSidePanelId") else btn.put("openSidePanelId", selPanel)
                        onChange()
                    }

                    val action = remember { mutableStateOf(btn.optString("actionId", "")) }
                    OutlinedTextField(
                        value = action.value,
                        onValueChange = { action.value = it; btn.put("actionId", it); onChange() },
                        label = { Text("actionId") }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            buttons.put(JSONObject("""{"kind":"button","label":"Nuovo","style":"text","icon":"add","size":"md","actionId":""}"""))
            onChange()
        }) { Text("+ Aggiungi bottone") }
        OutlinedButton(onClick = {
            buttons.put(JSONObject("""{"kind":"spacer","widthDp":12}""")); onChange()
        }) { Text("+ Aggiungi spacer") }
    }
}

@Composable private fun ToggleInspectorPanel(working: JSONObject, onChange: () -> Unit) {
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

@Composable private fun SliderInspectorPanel(working: JSONObject, onChange: () -> Unit) {
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

@Composable private fun ChipRowInspectorPanel(working: JSONObject, onChange: () -> Unit) {
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
                }, label = { Text("bind") })

                val value = remember { mutableStateOf(chip.opt("value")?.toString() ?: "") }
                OutlinedTextField(value = value.value, onValueChange = {
                    value.value = it
                    if (it.isBlank()) chip.remove("value") else chip.put("value", it)
                    onChange()
                }, label = { Text("value (opz.)") })
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Button(onClick = { chips.put(JSONObject("""{"label":"Nuovo","bind":"chip_new"}""")); onChange() }) { Text("+ Aggiungi chip") }
}

@Composable private fun TabsInspectorPanel(working: JSONObject, onChange: () -> Unit) {
    Text("Tabs – Proprietà", style = MaterialTheme.typography.titleMedium)
    val tabs = working.optJSONArray("tabs") ?: JSONArray().also { working.put("tabs", it) }
    val count = tabs.length().coerceAtLeast(1)
    val initIdxState = remember { mutableStateOf(working.optInt("initialIndex", 0).coerceIn(0, count-1).toString()) }
    ExposedDropdown(value = initIdxState.value, label = "initialIndex", options = (0 until count).map { it.toString() }) {
        sel -> initIdxState.value = sel; working.put("initialIndex", sel.toInt()); onChange()
    }
    Divider(); Text("Tab", style = MaterialTheme.typography.titleMedium)
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

@Composable private fun MetricsGridInspectorPanel(working: JSONObject, onChange: () -> Unit) {
    Text("MetricsGrid – Proprietà", style = MaterialTheme.typography.titleMedium)
    val cols = remember { mutableStateOf(working.optInt("columns", 2).toString()) }
    ExposedDropdown(value = cols.value, label = "columns", options = listOf("1","2","3")) {
        sel -> cols.value = sel; working.put("columns", sel.toInt()); onChange()
    }
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

@Composable private fun ProgressInspectorPanel(working: JSONObject, onChange: () -> Unit) {
    Text("Progress – Proprietà", style = MaterialTheme.typography.titleMedium)
    val label = remember { mutableStateOf(working.optString("label","")) }
    OutlinedTextField(value = label.value, onValueChange = {
        label.value = it; working.put("label", it); onChange()
    }, label = { Text("label") })
    val value = remember { mutableStateOf(working.optDouble("value", 0.0).toString()) }
    StepperField("value (0–100)", value, 1.0) { v -> working.put("value", v.coerceIn(0.0, 100.0)); onChange() }
    val showPercent = remember { mutableStateOf(working.optBoolean("showPercent", true)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = showPercent.value, onCheckedChange = {
            showPercent.value = it; working.put("showPercent", it); onChange()
        })
        Spacer(Modifier.width(8.dp)); Text("mostra %")
    }
    val color = remember { mutableStateOf(working.optString("color","primary")) }
    NamedColorPickerPlus(current = color.value, label = "color", allowRoles = true) { pick ->
        color.value = pick; if (pick.isBlank()) working.remove("color") else working.put("color", pick); onChange()
    }
}

@Composable private fun AlertInspectorPanel(working: JSONObject, onChange: () -> Unit) {
    Text("Alert – Proprietà", style = MaterialTheme.typography.titleMedium)
    var severity by remember { mutableStateOf(working.optString("severity","info")) }
    ExposedDropdown(value = severity, label = "severity", options = listOf("info","success","warning","error")) {
        sel -> severity = sel; working.put("severity", sel); onChange()
    }
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

@Composable private fun ImageInspectorPanel(working: JSONObject, onChange: () -> Unit) {
    Text("Image – Proprietà", style = MaterialTheme.typography.titleMedium)
    val source = remember { mutableStateOf(working.optString("source","")) }
    OutlinedTextField(value = source.value, onValueChange = {
        source.value = it; working.put("source", it); onChange()
    }, label = { Text("source (res:ic_launcher_foreground o content://)") })
    val height = remember { mutableStateOf(working.optDouble("heightDp", 160.0).toString()) }
    StepperField("height (dp)", height, 4.0) { v -> working.put("heightDp", v.coerceAtLeast(64.0)); onChange() }
    val corner = remember { mutableStateOf(working.optDouble("corner", 12.0).toString()) }
    StepperField("corner (dp)", corner, 2.0) { v -> working.put("corner", v.coerceAtLeast(0.0)); onChange() }
    var scale by remember { mutableStateOf(working.optString("contentScale","fit")) }
    ExposedDropdown(value = scale, label = "contentScale", options = listOf("fit","crop")) {
        sel -> scale = sel; working.put("contentScale", sel); onChange()
    }
}

@Composable private fun SectionHeaderInspectorPanel(working: JSONObject, onChange: () -> Unit) {
    Text("SectionHeader – Proprietà", style = MaterialTheme.typography.titleMedium)
    val title = remember { mutableStateOf(working.optString("title","")) }
    OutlinedTextField(value = title.value, onValueChange = {
        title.value = it; working.put("title", it); onChange()
    }, label = { Text("Titolo") })
    val subtitle = remember { mutableStateOf(working.optString("subtitle","")) }
    OutlinedTextField(value = subtitle.value, onValueChange = {
        subtitle.value = it; working.put("subtitle", it); onChange()
    }, label = { Text("Sottotitolo (opz.)") })
    var style by remember { mutableStateOf(working.optString("style","titleMedium")) }
    ExposedDropdown(
        value = style, label = "style",
        options = listOf("displaySmall","headlineSmall","titleLarge","titleMedium","titleSmall","bodyLarge","bodyMedium")
    ) { sel -> style = sel; working.put("style", sel); onChange() }
    var align by remember { mutableStateOf(working.optString("align","start")) }
    ExposedDropdown(value = align, label = "align", options = listOf("start","center","end")) {
        sel -> align = sel; working.put("align", sel); onChange()
    }
    val textSize = remember {
        mutableStateOf(working.optDouble("textSizeSp", Double.NaN).let { if (it.isNaN()) "" else it.toString() })
    }
    ExposedDropdown(
        value = if (textSize.value.isBlank()) "(default)" else textSize.value,
        label = "textSize (sp)",
        options = listOf("(default)","12","14","16","18","20","22","24","28","32","36")
    ) { sel ->
        val v = if (sel == "(default)") "" else sel
        textSize.value = v
        if (v.isBlank()) working.remove("textSizeSp") else working.put("textSizeSp", v.toDouble())
        onChange()
    }
    var fontWeight by remember { mutableStateOf(working.optString("fontWeight","")) }
    ExposedDropdown(
        value = if (fontWeight.isBlank()) "(default)" else fontWeight, label = "fontWeight",
        options = listOf("(default)","w300","w400","w500","w600","w700")
    ) { sel ->
        val v = if (sel == "(default)") "" else sel
        fontWeight = v
        if (v.isBlank()) working.remove("fontWeight") else working.put("fontWeight", v)
        onChange()
    }
    var fontFamily by remember { mutableStateOf(working.optString("fontFamily","")) }
    ExposedDropdown(
        value = if (fontFamily.isBlank()) "(default)" else fontFamily, label = "fontFamily",
        options = listOf("(default)","sans","serif","monospace","cursive")
    ) { sel ->
        val v = if (sel == "(default)") "" else sel
        fontFamily = v
        if (v.isBlank()) working.remove("fontFamily") else working.put("fontFamily", v)
        onChange()
    }
    val textColor = remember { mutableStateOf(working.optString("textColor","")) }
    NamedColorPickerPlus(current = textColor.value, label = "textColor", allowRoles = true) { hex ->
        textColor.value = hex
        if (hex.isBlank()) working.remove("textColor") else working.put("textColor", hex)
        onChange()
    }
}

/* =========================================================
 * PICKER / UI HELPERS
 * ========================================================= */
private val ICONS = listOf(
    "settings", "more_vert", "tune",
    "play_arrow", "pause", "stop", "add",
    "flag", "queue_music", "widgets", "palette",
    "home", "menu", "close", "more_horiz", "list", "tab", "grid_on",
    "directions_run", "directions_walk", "directions_bike",
    "fitness_center", "timer", "timer_off", "watch_later",
    "map", "my_location", "place", "speed",
    "bolt", "local_fire_department", "sports_score", "toggle_on", "image", "view_agenda"
)

@Composable
private fun NamedIconEx(name: String?, contentDescription: String?) {
    val __ctx = LocalContext.current
    if (name?.startsWith("res:") == true) {
        val resName = name.removePrefix("res:")
        val id = __ctx.resources.getIdentifier(resName, "drawable", __ctx.packageName)
        if (id != 0) { Icon(painterResource(id), contentDescription); return }
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
        "image" -> Icons.Filled.Image
        "view_agenda" -> Icons.Filled.ViewAgenda
        else -> null
    }
    if (image != null) Icon(image, contentDescription) else Text(".")
}

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
            label = { Text(label) },
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
            label = { Text(label) },
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

/* ---- Text helpers ---- */
@Composable private fun mapTextStyle(key: String): TextStyle = when (key) {
    "displaySmall" -> MaterialTheme.typography.displaySmall
    "headlineSmall" -> MaterialTheme.typography.headlineSmall
    "titleLarge" -> MaterialTheme.typography.titleLarge
    "titleSmall" -> MaterialTheme.typography.titleSmall
    "bodyLarge" -> MaterialTheme.typography.bodyLarge
    "bodyMedium" -> MaterialTheme.typography.bodyMedium
    else -> MaterialTheme.typography.titleMedium
}

@Composable private fun mapTextAlign(key: String): TextAlign = when (key) {
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

@Composable private fun IconText(label: String, icon: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NamedIconEx(icon, null); Text(label)
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

/* ---- Color & Image helpers ---- */
@Composable
private fun parseColorOrRole(value: String?): Color? {
    if (value.isNullOrBlank()) return null
    val v = value.trim()
    if (v.startsWith("#") && (v.length == 7 || v.length == 9)) {
        return try { Color(android.graphics.Color.parseColor(v)) } catch (_: Exception) { null }
    }
    val cs = MaterialTheme.colorScheme
    return when (v) {
        "primary" -> cs.primary
        "onPrimary" -> cs.onPrimary
        "secondary" -> cs.secondary
        "onSecondary" -> cs.onSecondary
        "tertiary" -> cs.tertiary
        "onTertiary" -> cs.onTertiary
        "error" -> cs.error
        "onError" -> cs.onError
        "surface" -> cs.surface
        "surfaceVariant" -> cs.surfaceVariant
        "onSurface" -> cs.onSurface
        "outline" -> cs.outline
        else -> null
    }
}

@Composable
private fun brushFromJson(g: JSONObject): Brush? {
    val cols = g.optJSONArray("colors")?.let { arr ->
        (0 until arr.length()).mapNotNull { idx -> parseColorOrRole(arr.optString(idx)) }
    } ?: emptyList()
    if (cols.size < 2) return null
    return if (g.optString("direction", "vertical") == "horizontal")
        Brush.horizontalGradient(colors = cols)
    else
        Brush.verticalGradient(colors = cols)
}

@Composable
private fun rememberImageBitmapFromUri(uriStr: String?): ImageBitmap? {
    val ctx = LocalContext.current
    return remember(uriStr) {
        try {
            if (uriStr.isNullOrBlank()) return@remember null
            if (uriStr.startsWith("res:")) {
                val resName = uriStr.removePrefix("res:")
                val id = ctx.resources.getIdentifier(resName, "drawable", ctx.packageName)
                if (id != 0) return@remember ImageBitmap.imageResource(ctx.resources, id)
                return@remember null
            }
            val uri = Uri.parse(uriStr)
            ctx.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let { bmp ->
                    bmp.asImageBitmap()
                }
            }
        } catch (_: Throwable) { null }
    }
}

/* ---- Readability helper ---- */
private fun bestOnColor(bg: Color): Color {
    val l = 0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue
    return if (l < 0.5f) Color.White else Color.Black
}

/* =========================================================
 * JSON utils (path, insert, move, duplicate, remove)
 * ========================================================= */
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

private fun collectSidePanelIds(root: JSONObject): List<String> {
    val res = mutableListOf<String>()
    val arr = root.optJSONArray("sidePanels") ?: return emptyList()
    for (i in 0 until arr.length()) {
        val p = arr.optJSONObject(i) ?: continue
        val id = p.optString("id", "")
        if (id.isNotBlank()) res.add(id)
    }
    return res
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
      {"kind":"button","label":"Start","style":"primary","icon":"play_arrow","size":"md","tint":"default","shape":"rounded","corner":20,"pressEffect":"scale","actionId":"start_run"},
      {"kind":"spacer","widthDp":12},
      {"kind":"button","label":"Pausa","style":"tonal","icon":"pause","size":"md","tint":"default","shape":"rounded","corner":20,"actionId":"pause_run"},
      {"kind":"spacer","widthDp":12},
      {"kind":"button","label":"Stop","style":"outlined","icon":"stop","size":"md","tint":"error","shape":"rounded","corner":20,"actionId":"stop_run"}
    ]}
    """.trimIndent()
)

private fun newSpacer()   = JSONObject("""{"type":"Spacer","height":8}""")
private fun newDividerV() = JSONObject("""{"type":"DividerV","thickness":1,"height":24}""")

private fun newCard() = JSONObject(
    """
    {"type":"Card","variant":"elevated","clickActionId":"",
     "container":{"style":"outlined","corner":12,"borderWidthDp":1,"borderColor":"outline"},
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
private fun applyTextStyleOverrides(node: JSONObject, base: TextStyle): TextStyle {
    var st = base

    val size = node.optDouble("textSizeSp", Double.NaN)
    if (!size.isNaN()) st = st.copy(fontSize = TextUnit(size.toFloat(), TextUnitType.Sp))

    val weightKey = node.optString("fontWeight", "")
    val weight = when (weightKey) {
        "w300" -> FontWeight.Light
        "w400" -> FontWeight.Normal
        "w500" -> FontWeight.Medium
        "w600" -> FontWeight.SemiBold
        "w700" -> FontWeight.Bold
        else -> null
    }
    if (weight != null) st = st.copy(fontWeight = weight)

    val familyKey = node.optString("fontFamily", "")
    val family = when (familyKey) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        "sans" -> FontFamily.SansSerif
        else -> null
    }
    if (family != null) st = st.copy(fontFamily = family)

    return st
}

/* =========================================================
 * NOTE:
 * - UiLoader deve esistere nel tuo progetto (persistenza bozza/pubblicazione).
 *   Qui lo uso come già presente nel tuo codice.
 * ========================================================= */