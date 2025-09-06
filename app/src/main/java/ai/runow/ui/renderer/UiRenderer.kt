@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.ui.unit.ExperimentalUnitApi::class
)

package ai.runow.ui.renderer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
 * ENTRY ROOT CHIAMATA DA MainActivity
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
 * RENDER DI UNA SCHERMATA JSON (con Scaffold di root e levetta)
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
    val tonalElevation = Dp(cfg.optDouble("tonalElevation", 0.0).toFloat())

    // --- Colors / style ---
    val styleKey = cfg.optString("style", "")
    val tintKey  = cfg.optString("tint", "default")
    val customC  = parseColorOrRole(cfg.optString("customColor", ""))

    val containerColorBase = parseColorOrRole(cfg.optString("containerColor", "")) ?: MaterialTheme.colorScheme.surface
    val titleColor     = parseColorOrRole(cfg.optString("titleColor", ""))     ?: MaterialTheme.colorScheme.onSurface
    val subtitleColor  = parseColorOrRole(cfg.optString("subtitleColor", ""))  ?: titleColor.copy(alpha = 0.8f)
    val actionsColor   = parseColorOrRole(cfg.optString("actionsColor", ""))   ?: titleColor

    // Gradient opzionale (se presente ha priorità sul containerColor, ma viene ignorato se style=text)
    var brush: Brush? = cfg.optJSONObject("gradient")?.let { brushFromJson(it) }

    // Outline / Trasparenza legacy (compat)
    var transparent = cfg.optBoolean("transparent", false)
    var outlineObj = cfg.optJSONObject("outline")

    // Se presente "style" uniforme, calcoliamo container/border coerenti e diamo priorità
    if (styleKey.isNotBlank()) {
        val (cont, _, borderW) = mapContainerColors(styleKey, tintKey, customC)
        // style=text => trasparente, senza border. style=outlined => trasparente + border
        transparent = styleKey == "text"
        brush = if (transparent) null else brush // su 'text' disattiviamo il gradient
        outlineObj = if (styleKey == "outlined") {
            (outlineObj ?: JSONObject()).apply {
                if (!has("color")) put("color", "outline")
                if (!has("thickness")) put("thickness", borderW.value.toDouble())
            }
        } else null

        // se non è text/outlined impostiamo container uniforme
        if (!transparent && styleKey != "outlined") {
            cfg.put("containerColor", toColorKey(cont))
        }
    }

    // Dopo eventuale override 'style'
    val containerColor = parseColorOrRole(cfg.optString("containerColor", "")) ?: containerColorBase
    val hasOutline = outlineObj != null
    val outlineColor = parseColorOrRole(outlineObj?.optString("color")) ?: MaterialTheme.colorScheme.outline
    val outlineThickness = Dp(outlineObj?.optDouble("thickness", 1.0)?.toFloat() ?: 1f)

    val actions = cfg.optJSONArray("actions") ?: JSONArray()

    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        titleContentColor = titleColor,
        actionIconContentColor = actionsColor,
        navigationIconContentColor = actionsColor
    )

    val titleStyleBase = when (variant) {
        "large" -> MaterialTheme.typography.headlineSmall
        "medium" -> MaterialTheme.typography.titleLarge
        else -> MaterialTheme.typography.titleMedium
    }
    val titleStyle = applyTextStyleOverridesPrefixed(cfg, "title", titleStyleBase)
    val subtitleStyle = applyTextStyleOverridesPrefixed(cfg, "subtitle", MaterialTheme.typography.labelMedium)

    Surface(tonalElevation = tonalElevation, shape = rounded) {
        val bgBase = when {
            cfg.optString("style","").equals("text", ignoreCase = true) -> Modifier
            cfg.optBoolean("transparent", false) -> Modifier
            brush != null -> Modifier.background(brush!!)
            else -> Modifier.background(containerColor)
        }
        val withBorder = if (hasOutline) bgBase.border(BorderStroke(outlineThickness, outlineColor), rounded) else bgBase

        Box(withBorder) {
            val titleSlot: @Composable () -> Unit = {
                if (subtitle.isBlank()) {
                    Text(title, style = titleStyle, color = titleColor)
                } else {
                    Column {
                        Text(title, style = titleStyle, color = titleColor)
                        Text(subtitle, style = subtitleStyle, color = subtitleColor)
                    }
                }
            }

            val actionsSlot: @Composable RowScope.() -> Unit = {
                for (i in 0 until actions.length()) {
                    val a = actions.optJSONObject(i) ?: continue
                    IconButton(onClick = { dispatch(a.optString("actionId")) }) {
                        NamedIconEx(a.optString("icon", "more_vert"), null)
                    }
                }
            }

            when (variant) {
                "center" -> CenterAlignedTopAppBar(
                    title = { titleSlot() },
                    actions = actionsSlot,
                    colors = colors,
                    scrollBehavior = scrollBehavior
                )
                "medium" -> MediumTopAppBar(
                    title = { titleSlot() },
                    actions = actionsSlot,
                    colors = colors,
                    scrollBehavior = scrollBehavior
                )
                "large" -> LargeTopAppBar(
                    title = { titleSlot() },
                    actions = actionsSlot,
                    colors = colors,
                    scrollBehavior = scrollBehavior
                )
                else -> TopAppBar(
                    title = { titleSlot() },
                    actions = actionsSlot,
                    colors = colors,
                    scrollBehavior = scrollBehavior
                )
            }

            if (cfg.optBoolean("divider", false)) {
                Divider(Modifier.align(Alignment.BottomCenter))
            }
        }
    }
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

    val menus by remember(layout, tick) { mutableStateOf(collectMenus(layout!!)) }
    var selectedPath by remember(screenName) { mutableStateOf<String?>(null) }

    var overlayHeightPx by remember { mutableStateOf(0) }
    val overlayHeightDp = with(LocalDensity.current) { overlayHeightPx.toDp() }

    var designMode by rememberSaveable(screenName) { mutableStateOf(designerMode) }

    Box(Modifier.fillMaxSize()) {
        RenderRootScaffold(
            layout = layout!!,
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
                onPublish = { UiLoader.saveDraft(ctx, screenName, layout!!); UiLoader.publish(ctx, screenName) },
                onReset = {
                    UiLoader.resetPublished(ctx, screenName)
                    layout = UiLoader.loadLayout(ctx, screenName)
                    selectedPath = null
                    tick++
                },
                topPadding = scaffoldPadding.calculateTopPadding(),
                onOverlayHeight = { overlayHeightPx = it },
                onOpenRootInspector = { /* gestito sotto */ },
                dispatch = dispatch
            )
        }

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
 * PAGE BACKGROUND (gradiente / immagine fullscreen) + ROOT SCAFFOLD
 * ========================================================= */

@Composable
private fun RenderPageBackground(cfg: JSONObject) {
    val opacity = cfg.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f)
    val grad = brushFromJson(cfg.optJSONObject("gradient"))
    val img = cfg.optJSONObject("image")

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer(alpha = opacity)
    ) {
        if (grad != null) {
            Box(Modifier.matchParentSize().background(grad))
        }
        img?.let { j ->
            val src = j.optString("source", "")
            val scale = when (j.optString("contentScale", "crop")) {
                "fit" -> ContentScale.Fit
                else  -> ContentScale.Crop
            }
            val alpha = j.optDouble("alpha", 1.0).toFloat().coerceIn(0f, 1f)
            val p = rememberPainterFromSource(src)
            if (p != null) {
                androidx.compose.foundation.Image(
                    painter = p, contentDescription = null,
                    modifier = Modifier.matchParentSize().graphicsLayer(alpha = alpha),
                    contentScale = scale
                )
            }
        }
    }
}

/* ========= Unified press-effect (riusabile per bottoni, card, list items, ecc.) ========= */

@Composable
private fun pressEffectModifier(
    key: String,
    press: String,
    enabled: Boolean = true
): Pair<Modifier, MutableInteractionSource> {
    val src = remember(key) { MutableInteractionSource() }
    val isPressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (enabled && press == "scale" && isPressed) 0.96f else 1f,
        label = "pressScale-$key"
    )
    val alpha by animateFloatAsState(
        targetValue = if (enabled && press == "alpha" && isPressed) 0.6f else 1f,
        label = "pressAlpha-$key"
    )
    val rotation by animateFloatAsState(
        targetValue = if (enabled && press == "rotate" && isPressed) -2f else 0f,
        label = "pressRotation-$key"
    )
    val mod = if (!enabled || press == "none") Modifier else Modifier.graphicsLayer(
        scaleX = scale, scaleY = scale, alpha = alpha, rotationZ = rotation
    )
    return mod to src
}

/* ========= Unified container style resolver (background/border/shape) ========= */

private data class ResolvedContainer(
    val containerColor: Color,
    val contentColor: Color,
    val border: BorderStroke?,
    val shape: Shape,
    val elevation: Dp,
    val styleKey: String
)

@Composable
private fun resolveContainer(
    cfg: JSONObject?,
    defaultShape: Shape = RoundedCornerShape(12.dp),
    fallbackStyle: String? = null
): ResolvedContainer? {
    if (cfg == null && fallbackStyle == null) return null

    val style = cfg?.optString("style", fallbackStyle ?: "text") ?: "text"
    val tint = cfg?.optString("tint", "default") ?: "default"
    val custom = parseColorOrRole(cfg?.optString("customColor", ""))

    val (containerColor, contentColor, borderW) = mapContainerColors(style, tint, custom)

    val shapeKey = cfg?.optString("shape", "rounded") ?: "rounded"
    val corner = Dp(cfg?.optDouble("corner", 12.0)?.toFloat() ?: 12f)
    val shape = when (shapeKey) {
        "pill" -> RoundedCornerShape(50)
        "cut" -> CutCornerShape(corner)
        else -> RoundedCornerShape(corner)
    }

    val borderColor = parseColorOrRole(cfg?.optString("borderColor","")) ?: contentColor
    val borderThickness = Dp(cfg?.optDouble("borderThicknessDp", borderW.value.toDouble())?.toFloat() ?: borderW)
    val border = when (style) {
        "outlined" -> BorderStroke(borderThickness, borderColor)
        else -> null
    }

    val elevation = Dp(cfg?.optDouble("elevationDp", 0.0)?.toFloat() ?: 0f)
    return ResolvedContainer(
        containerColor = if (style == "text" || style == "outlined") Color.Transparent else containerColor,
        contentColor = contentColor,
        border = border,
        shape = shape,
        elevation = elevation,
        styleKey = style
    )
}

@Composable
private fun StyledSurface(
    cfg: JSONObject?,
    defaultShape: Shape = RoundedCornerShape(12.dp),
    modifier: Modifier = Modifier,
    fallbackStyle: String? = null,
    content: @Composable () -> Unit
) {
    val r = resolveContainer(cfg, defaultShape, fallbackStyle) ?: run {
        // Nessuna personalizzazione: fallback a contenuto nudo
        Box(modifier) { content() }
        return
    }
    if (r.styleKey == "text") {
        Box(modifier) { content() }
    } else {
        Surface(
            shape = r.shape,
            color = r.containerColor,
            contentColor = r.contentColor,
            tonalElevation = r.elevation,
            modifier = modifier.then(if (r.border != null) Modifier.border(r.border, r.shape) else Modifier)
        ) { content() }
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
    selectedPathSetter: (String) -> Unit,
    extraPaddingBottom: Dp,
    scaffoldPadding: PaddingValues
) {
    val title = layout.optString("topTitle", "")
    val topActions = layout.optJSONArray("topActions") ?: JSONArray()
    val bottomButtons = layout.optJSONArray("bottomButtons") ?: JSONArray()
    val fab = layout.optJSONObject("fab")
    val scroll = layout.optBoolean("scroll", true)
    val topBarConf = layout.optJSONObject("topBar")
    val pageBg = layout.optJSONObject("pageBackground")
    val bottomBarStyle = layout.optJSONObject("bottomBarStyle")

    val topScrollBehavior = when (topBarConf?.optString("scroll", "none")) {
        "pinned" -> TopAppBarDefaults.pinnedScrollBehavior()
        "enterAlways" -> TopAppBarDefaults.enterAlwaysScrollBehavior()
        "exitUntilCollapsed" -> TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        else -> null
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        pageBg?.let { RenderPageBackground(it) }

        Scaffold(
            modifier = if (topScrollBehavior != null)
                Modifier.nestedScroll(topScrollBehavior.nestedScrollConnection)
            else Modifier,
            containerColor = Color.Transparent,
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
                if (bottomButtons.length() > 0) {
                    StyledSurface(
                        cfg = bottomBarStyle,
                        defaultShape = RoundedCornerShape(0.dp), // bar rettangolare
                        fallbackStyle = "surface" // se assente, usa container pieno (come prima)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (i in 0 until bottomButtons.length()) {
                                val btn = bottomButtons.optJSONObject(i) ?: continue
                                val label = btn.optString("label", "Button")
                                val action = btn.optString("actionId", "")
                                TextButton(onClick = { dispatch(action) }) { Text(label) }
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                fab?.let {
                    when (it.optString("variant", "regular")) {
                        "extended" -> ExtendedFloatingActionButton(
                            onClick = { dispatch(it.optString("actionId", "")) },
                            icon = { NamedIconEx(it.optString("icon", "play_arrow"), null) },
                            text = {
                                val style = applyTextStyleOverrides(it, MaterialTheme.typography.bodyLarge)
                                val color = parseColorOrRole(it.optString("textColor",""))
                                    ?: LocalContentColor.current
                                Text(it.optString("label", ""), style = style, color = color)
                            }
                        )
                        else -> FloatingActionButton(onClick = { dispatch(it.optString("actionId", "")) }) {
                            NamedIconEx(it.optString("icon", "play_arrow"), null)
                        }
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
    dispatch: (String) -> Unit
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
                        "ButtonRow"     -> ButtonRowInspectorPanel(working, onChange = bumpPreview)
                        "SectionHeader" -> SectionHeaderInspectorPanel(working, onChange = bumpPreview)
                        "Progress"      -> ProgressInspectorPanel(working, onChange = bumpPreview)
                        "Alert"         -> AlertInspectorPanel(working, onChange = bumpPreview)
                        "Image"         -> ImageInspectorPanel(working, onChange = bumpPreview)
                        "ChipRow"       -> ChipRowInspectorPanel(working, onChange = bumpPreview)
                        "Slider"        -> SliderInspectorPanel(working, onChange = bumpPreview)
                        "Toggle"        -> ToggleInspectorPanel(working, onChange = bumpPreview)
                        "Tabs"          -> TabsInspectorPanel(working, onChange = bumpPreview)
                        "MetricsGrid"   -> MetricsGridInspectorPanel(working, onChange = bumpPreview)
                        "List"          -> ListInspectorPanel(working, onChange = bumpPreview)
                        "Card"          -> CardInspectorPanel(working, onChange = bumpPreview)
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

    // ===== ROOT LAYOUT INSPECTOR + ANTEPRIMA =====
    if (showRootInspector) {
        val working = remember { JSONObject(layout.toString()) }
        var dummyTick by remember { mutableStateOf(0) }
        val onChange: () -> Unit = { dummyTick++ }

        BackHandler(enabled = true) { showRootInspector = false }

        Box(Modifier.fillMaxSize()) {
            working.optJSONObject("pageBackground")?.let { cfg ->
                key(dummyTick) { RenderPageBackground(cfg) }
            }
            working.optJSONObject("topBar")?.let { tb ->
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
                    key(dummyTick) { RenderTopBar(tb, dispatch, null) }
                }
            }
        }

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
                RootInspectorPanel(working, onChange, dispatch)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showRootInspector = false }) { Text("Annulla") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        val keys = listOf("pageBackground","topBar","topTitle","topActions","bottomButtons","fab","scroll","bottomBarStyle")
                        keys.forEach { k -> layout.put(k, working.opt(k)) }
                        showRootInspector = false
                        onLayoutChange()
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
        content(); return
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
        "AppBar" -> Wrapper {
            val style = applyTextStyleOverrides(block, MaterialTheme.typography.titleLarge)
            val color = parseColorOrRole(block.optString("textColor","")) ?: LocalContentColor.current
            // Container opzionale
            StyledSurface(cfg = block.optJSONObject("container")) {
                Column(Modifier.fillMaxWidth()) {
                    Text(block.optString("title", ""), style = style, color = color)
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
            }
        }

        "IconButton" -> Wrapper {
            val iconName = block.optString("icon", "more_vert")
            val openMenuId = block.optString("openMenuId", "")
            val actionId = block.optString("actionId", "")
            var expanded by remember { mutableStateOf(false) }

            StyledSurface(cfg = block.optJSONObject("container")) {
                Box {
                    IconButton(onClick = {
                        if (openMenuId.isNotBlank() || actionId.startsWith("open_menu:")) {
                            expanded = true
                        } else if (actionId.isNotBlank()) {
                            dispatch(actionId)
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
        }

        "Progress" -> Wrapper {
            StyledSurface(cfg = block.optJSONObject("container")) {
                val label = block.optString("label","")
                val value = block.optDouble("value", 0.0).toFloat().coerceIn(0f, 100f)
                val color = parseColorOrRole(block.optString("color","")) ?: MaterialTheme.colorScheme.primary
                val showPercent = block.optBoolean("showPercent", true)

                Column {
                    if (label.isNotBlank()) {
                        val style = applyTextStyleOverridesPrefixed(block, "label", MaterialTheme.typography.bodyMedium)
                        val tColor = parseColorOrRole(block.optString("labelTextColor","")) ?: LocalContentColor.current
                        Text(label, style = style, color = tColor)
                    }
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
        }

        "Alert" -> Wrapper {
            val containerCfg = block.optJSONObject("container")
            if (containerCfg != null) {
                // Override completo container
                StyledSurface(cfg = containerCfg) {
                    AlertInner(block, dispatch)
                }
            } else {
                // default severity colors
                val severity = block.optString("severity","info")
                val (bg, fg) = when (severity) {
                    "success" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                    "warning" -> Color(0xFFFFF3CD) to Color(0xFF664D03)
                    "error"   -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                    else      -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                }
                Surface(
                    color = bg,
                    contentColor = fg,
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) { AlertInner(block, dispatch) }
            }
        }

        "Image" -> Wrapper {
            StyledSurface(cfg = block.optJSONObject("container")) {
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
                        androidx.compose.foundation.Image(
                            painter = painterResource(resId),
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
            val clickAction = block.optString("clickActionId","")
            val press = block.optString("pressEffect","none")
            val (pressMod, interaction) = pressEffectModifier(path, press, enabled = clickAction.isNotBlank() && !designerMode)

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

            val baseClickable = if (clickAction.isNotBlank() && !designerMode)
                Modifier.then(pressMod).clickable(interactionSource = interaction, indication = null) { dispatch(clickAction) }
            else Modifier

            ContainerOverlayGear(designerMode, path, onOpenInspector) {
                val containerCfg = block.optJSONObject("container")
                if (containerCfg != null) {
                    StyledSurface(cfg = containerCfg, modifier = baseClickable) {
                        Column(Modifier.padding(12.dp)) { innerContent() }
                    }
                } else {
                    // Fallback legacy Card varianti
                    val variant = block.optString("variant","elevated")
                    val cardMod = Modifier.then(baseClickable)
                    when (variant) {
                        "outlined" -> OutlinedCard(cardMod) { Column(Modifier.padding(12.dp)) { innerContent() } }
                        "filled"   -> Card(cardMod)        { Column(Modifier.padding(12.dp)) { innerContent() } }
                        else       -> ElevatedCard(cardMod){ Column(Modifier.padding(12.dp)) { innerContent() } }
                    }
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

            val tabLabelStyle = applyTextStyleOverridesPrefixed(block, "tab", MaterialTheme.typography.bodyMedium)
            val tabLabelColor = parseColorOrRole(block.optString("tabTextColor",""))

            ContainerOverlayGear(designerMode, path, onOpenInspector) {
                StyledSurface(cfg = block.optJSONObject("container")) {
                    TabRow(selectedTabIndex = idx) {
                        labels.forEachIndexed { i, label ->
                            Tab(
                                selected = i == idx,
                                onClick = { idx = i },
                                text = {
                                    Text(
                                        label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = tabLabelStyle,
                                        color = tabLabelColor ?: LocalContentColor.current
                                    )
                                }
                            )
                        }
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
            StyledSurface(cfg = block.optJSONObject("container")) {
                val style = mapTextStyle(block.optString("style", "titleMedium"))
                val align = mapTextAlign(block.optString("align", "start"))
                val clickAction = block.optString("clickActionId", "")
                val textColor = parseColorOrRole(block.optString("textColor", ""))

                val st = applyTextStyleOverrides(block, style)

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
                        style = st,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = align,
                        color = textColor ?: LocalContentColor.current
                    )
                    val sub = block.optString("subtitle", "")
                    if (sub.isNotBlank()) {
                        val subStyle = applyTextStyleOverridesPrefixed(block, "subtitle", MaterialTheme.typography.bodyMedium)
                        val subColor = parseColorOrRole(block.optString("subtitleTextColor","")) ?: LocalContentColor.current
                        Text(
                            sub,
                            style = subStyle,
                            textAlign = align,
                            modifier = Modifier.fillMaxWidth(),
                            color = subColor
                        )
                    }
                }
            }
        }

        "MetricsGrid" -> Wrapper {
            StyledSurface(cfg = block.optJSONObject("container")) {
                val tiles = block.optJSONArray("tiles") ?: JSONArray()
                val cols = block.optInt("columns", 2).coerceIn(1, 3)
                val tStyle = applyTextStyleOverrides(block, MaterialTheme.typography.labelMedium)
                val tColor = parseColorOrRole(block.optString("textColor","")) ?: LocalContentColor.current
                GridSection(tiles, cols, uiState, tStyle, tColor)
            }
        }

        "ButtonRow" -> Wrapper {
            StyledSurface(cfg = block.optJSONObject("container")) {
                val align = when (block.optString("align")) {
                    "start" -> Arrangement.Start
                    "end" -> Arrangement.End
                    "space_between" -> Arrangement.SpaceBetween
                    "space_around" -> Arrangement.SpaceAround
                    "space_evenly" -> Arrangement.SpaceEvenly
                    else -> Arrangement.Center
                }
                val buttons = block.optJSONArray("buttons") ?: JSONArray()

                val labelStyle = applyTextStyleOverrides(block, MaterialTheme.typography.bodyMedium)
                val labelColor = parseColorOrRole(block.optString("textColor",""))

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = align,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until buttons.length()) {
                        val btn = buttons.optJSONObject(i) ?: continue

                        // HSpacer
                        if (btn.optString("type") == "HSpacer") {
                            val w = Dp(btn.optDouble("widthDp", 8.0).toFloat())
                            Spacer(Modifier.width(w))
                            continue
                        }

                        val label = btn.optString("label", "Button")
                        val styleKey = btn.optString("style", "primary")
                        val action = btn.optString("actionId", "")
                        val confirm = btn.optBoolean("confirm", false)
                        val tintKey = btn.optString("tint", "default")
                        val shapeKey = btn.optString("shape", "rounded")
                        val corner = Dp(btn.optDouble("corner", 20.0).toFloat())
                        val pressKey = btn.optString("pressEffect", "none")
                        val icon = btn.optString("icon", "")

                        val (pressMod, interaction) = pressEffectModifier("$path/btn/$i", pressKey, true)

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
                            else Modifier.height(40.dp)

                        val widthMod =
                            if (!btn.optDouble("widthDp", Double.NaN).isNaN())
                                Modifier.width(Dp(btn.optDouble("widthDp", Double.NaN).toFloat()))
                            else Modifier

                        val baseMod = Modifier
                            .then(pressMod)
                            .then(heightMod)
                            .then(widthMod)

                        Spacer(Modifier.width(6.dp))

                        val contentSlot: @Composable () -> Unit = {
                            if (icon.isNotBlank()) {
                                IconText(
                                    label = label,
                                    icon = icon,
                                    textStyle = labelStyle,
                                    textColor = labelColor
                                )
                            } else {
                                Text(
                                    label,
                                    style = labelStyle,
                                    color = labelColor ?: LocalContentColor.current
                                )
                            }
                        }

                        when (styleKey) {
                            "outlined" -> OutlinedButton(
                                onClick = { if (!confirm) dispatch(action) else dispatch(action) },
                                shape = shape,
                                border = BorderStroke(width = border, color = content),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = content),
                                interactionSource = interaction,
                                modifier = baseMod
                            ) { contentSlot() }

                            "tonal" -> FilledTonalButton(
                                onClick = { dispatch(action) },
                                shape = shape,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = container,
                                    contentColor = content
                                ),
                                interactionSource = interaction,
                                modifier = baseMod
                            ) { contentSlot() }

                            "text" -> TextButton(
                                onClick = { dispatch(action) },
                                shape = shape,
                                colors = ButtonDefaults.textButtonColors(contentColor = content),
                                interactionSource = interaction,
                                modifier = baseMod
                            ) { contentSlot() }

                            else -> Button(
                                onClick = { dispatch(action) },
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
        }

        "ChipRow" -> Wrapper {
            StyledSurface(cfg = block.optJSONObject("container")) {
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
        }

        "Toggle" -> Wrapper {
            StyledSurface(cfg = block.optJSONObject("container")) {
                val label = block.optString("label", "")
                val bind = block.optString("bind", "")
                val v = (uiState[bind] as? Boolean) ?: false
                val style = applyTextStyleOverridesPrefixed(block, "label", MaterialTheme.typography.bodyMedium)
                val color = parseColorOrRole(block.optString("labelTextColor","")) ?: LocalContentColor.current
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = v, onCheckedChange = { uiState[bind] = it })
                    Spacer(Modifier.width(8.dp))
                    Text(label, style = style, color = color)
                }
            }
        }

        "Slider" -> Wrapper {
            StyledSurface(cfg = block.optJSONObject("container")) {
                val label = block.optString("label", "")
                val bind = block.optString("bind", "")
                val min = block.optDouble("min", 0.0).toFloat()
                val max = block.optDouble("max", 10.0).toFloat()
                val step = block.optDouble("step", 1.0).toFloat()
                var value by remember { mutableStateOf(((uiState[bind] as? Number)?.toFloat()) ?: min) }
                val style = applyTextStyleOverridesPrefixed(block, "label", MaterialTheme.typography.bodyMedium)
                val color = parseColorOrRole(block.optString("labelTextColor","")) ?: LocalContentColor.current
                Text("$label: ${"%.1f".format(value)}${block.optString("unit", "")}", style = style, color = color)
                Slider(
                    value = value,
                    onValueChange = {
                        value = it
                        uiState[bind] = if (step >= 1f) round(it / step) * step else it
                    },
                    valueRange = min..max
                )
            }
        }

        "List" -> Wrapper {
            StyledSurface(cfg = block.optJSONObject("container")) {
                val items = block.optJSONArray("items") ?: JSONArray()
                val align = mapTextAlign(block.optString("align", "start"))
                val textColor = parseColorOrRole(block.optString("textColor",""))
                val style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyLarge)
                val subStyle = applyTextStyleOverridesPrefixed(block, "subtitle", MaterialTheme.typography.bodyMedium)
                val subColor = parseColorOrRole(block.optString("subtitleTextColor","")) ?: textColor ?: LocalContentColor.current

                val press = block.optString("pressEffect","none")

                Column {
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val (pressMod, isrc) = pressEffectModifier("$path/item/$i", press, enabled = true)
                        ListItem(
                            headlineContent = {
                                Text(
                                    item.optString("title", ""),
                                    style = style,
                                    color = textColor ?: LocalContentColor.current,
                                    textAlign = align,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            supportingContent = {
                                val sub = item.optString("subtitle", "")
                                if (sub.isNotBlank()) Text(
                                    sub,
                                    style = subStyle,
                                    color = subColor,
                                    textAlign = align,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(pressMod)
                                .padding(vertical = 4.dp)
                                .clickable(
                                    interactionSource = isrc,
                                    indication = null
                                ) { dispatch(item.optString("actionId", "")) }
                        )
                        Divider()
                    }
                }
            }
        }

        "Carousel" -> Wrapper {
            StyledSurface(cfg = block.optJSONObject("container")) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Carousel (placeholder)", style = MaterialTheme.typography.titleSmall)
                        Text("Le immagini saranno gestite in una fase successiva.")
                    }
                }
            }
        }

        "Fab" -> Wrapper {
            val icon = block.optString("icon", "play_arrow")
            val label = block.optString("label", "")
            val size = block.optString("size", "regular")
            val variant = block.optString("variant", "regular")
            val action = block.optString("actionId", "")
            val style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyLarge)
            val color = parseColorOrRole(block.optString("textColor","")) ?: LocalContentColor.current
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                when (variant) {
                    "extended" -> ExtendedFloatingActionButton(
                        onClick = { dispatch(action) },
                        icon = { NamedIconEx(icon, null) },
                        text = { Text(label.ifBlank { "Azione" }, style = style, color = color) }
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

        "Spacer" -> { Spacer(Modifier.height(Dp(block.optDouble("height", 8.0).toFloat()))) }

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

@Composable
private fun AlertInner(block: JSONObject, dispatch: (String) -> Unit) {
    val fgDefault = LocalContentColor.current
    val title = block.optString("title","")
    val message = block.optString("message","")
    val actionId = block.optString("actionId","")

    val titleStyle = applyTextStyleOverridesPrefixed(block, "title", MaterialTheme.typography.titleSmall)
    val msgStyle   = applyTextStyleOverridesPrefixed(block, "message", MaterialTheme.typography.bodyMedium)
    val tColor = parseColorOrRole(block.optString("titleTextColor","")) ?: fgDefault
    val mColor = parseColorOrRole(block.optString("messageTextColor","")) ?: fgDefault

    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title.isNotBlank()) Text(title, style = titleStyle, color = tColor)
        if (message.isNotBlank()) Text(message, style = msgStyle, color = mColor)
        if (actionId.isNotBlank()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { dispatch(actionId) }) { Text("Azione") }
            }
        }
    }
}

/* =========================================================
 * GRID
 * ========================================================= */

@Composable
private fun GridSection(
    tiles: JSONArray,
    cols: Int,
    uiState: MutableMap<String, Any>,
    labelStyle: TextStyle,
    labelColor: Color
) {
    val rows = mutableListOf<List<JSONObject>>()
    var current = mutableListOf<JSONObject>()
    for (i in 0 until tiles.length()) {
        tiles.optJSONObject(i)?.let { current.add(it) }
        if (current.size == cols) { rows.add(current.toList()); current = mutableListOf() }
    }
    if (current.isNotEmpty()) rows.add(current)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    ElevatedCard(Modifier.weight(1f)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(t.optString("label", ""), style = labelStyle, color = labelColor)
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
 * ROOT INSPECTOR PANEL
 * ========================================================= */

@Composable
private fun RootInspectorPanel(
    working: JSONObject,
    onChange: () -> Unit,
    dispatch: (String) -> Unit
) {
    Text("Layout (root) – Proprietà", style = MaterialTheme.typography.titleMedium)

    var scroll by remember { mutableStateOf(working.optBoolean("scroll", true)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = scroll, onCheckedChange = {
            scroll = it; working.put("scroll", it); onChange()
        })
        Spacer(Modifier.width(8.dp)); Text("Contenuto scrollabile")
    }

    Divider(); Text("Page background", style = MaterialTheme.typography.titleSmall)
    PageBackgroundInspectorPanel(working, onChange, dispatch)

    Divider(); Text("Top Bar (estetica)", style = MaterialTheme.typography.titleSmall)

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
                            put("containerColor", "surface")
                            put("titleColor", "onSurface")
                            put("actionsColor", "onSurface")
                            put("roundedBottomStart", 0)
                            put("roundedBottomEnd", 0)
                            put("tonalElevation", 0)
                            put("divider", false)
                        })
                    }
                } else working.remove("topBar")
                onChange()
            }
        )
        Spacer(Modifier.width(8.dp))
        Text("Usa topBar estetico (consigliato)")
    }

    if (topBarEnabled) {
        val tb = working.optJSONObject("topBar")!!
        TopBarInspectorPanel(tb, onChange)
        Spacer(Modifier.height(8.dp))
        Text(
            "Nota: con topBar attivo, i campi legacy ‘Top App Bar’ qui sotto vengono ignorati.",
            style = MaterialTheme.typography.labelSmall,
            color = LocalContentColor.current.copy(alpha = 0.7f)
        )
    }

    val actions = working.optJSONArray("topActions") ?: JSONArray().also { working.put("topActions", it) }
    for (i in 0 until actions.length()) {
        val itx = actions.getJSONObject(i)
        ElevatedCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
                OutlinedTextField(
                    value = act.value,
                    onValueChange = { act.value = it; itx.put("actionId", it); onChange() },
                    label = { Text("actionId") }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Button(onClick = {
        actions.put(JSONObject("""{"icon":"more_vert","actionId":""}""")); onChange()
    }) { Text("+ Aggiungi action") }

    Divider(); Text("Bottom Bar", style = MaterialTheme.typography.titleSmall)
    val bottom = working.optJSONArray("bottomButtons") ?: JSONArray().also { working.put("bottomButtons", it) }
    for (i in 0 until bottom.length()) {
        val itx = bottom.getJSONObject(i)
        ElevatedCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Button ${i+1}", style = MaterialTheme.typography.labelLarge)
                    Row {
                        IconButton(onClick = { moveInArray(bottom, i, -1); onChange() }) { Icon(Icons.Filled.KeyboardArrowUp, null) }
                        IconButton(onClick = { moveInArray(bottom, i, +1); onChange() }) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                        IconButton(onClick = { removeAt(bottom, i); onChange() }) { Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error) }
                    }
                }
                val lbl = remember { mutableStateOf(itx.optString("label","")) }
                OutlinedTextField(lbl.value, { lbl.value = it; itx.put("label", it); onChange() }, label = { Text("label") })
                val act = remember { mutableStateOf(itx.optString("actionId","")) }
                OutlinedTextField(act.value, { act.value = it; itx.put("actionId", it); onChange() }, label = { Text("actionId") })
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Button(onClick = { bottom.put(JSONObject("""{"label":"Azione","actionId":""}""")); onChange() }) {
        Text("+ Aggiungi bottone")
    }

    // Stile della BottomBar
    Divider(); Text("Bottom Bar – Stile contenitore", style = MaterialTheme.typography.titleSmall)
    ContainerStyleEditorForNode(working, nodeKey = "bottomBarStyle", onChange = onChange)

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

        Divider()
        TextStyleEditor(node = fab, title = "Testo FAB", prefix = "", onChange = onChange)
    }
}

/* =========================================================
 * INSPECTOR dei vari BLOCCHI
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
    Text("Elementi (bottoni / spaziatori)", style = MaterialTheme.typography.titleMedium)

    for (i in 0 until buttons.length()) {
        val btn = buttons.getJSONObject(i)

        if (btn.optString("type") == "HSpacer") {
            ElevatedCard {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("HSpacer ${i + 1}", style = MaterialTheme.typography.labelLarge)
                        Row {
                            IconButton(onClick = { moveInArray(buttons, i, -1); onChange() }) { Icon(Icons.Filled.KeyboardArrowUp, null) }
                            IconButton(onClick = { moveInArray(buttons, i, +1); onChange() }) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                            IconButton(onClick = { removeAt(buttons, i); onChange() }) {
                                Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    val width = btn.optDouble("widthDp", 12.0)
                    NumericDropdown(
                        value = width,
                        label = "width (dp)",
                        options = listOf(4.0, 6.0, 8.0, 12.0, 16.0, 20.0, 24.0, 32.0),
                        includeDefault = false
                    ) { v -> btn.put("widthDp", v ?: 12.0); onChange() }
                }
            }
            Spacer(Modifier.height(8.dp))
            continue
        }

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

                val h = btn.optDouble("heightDp", Double.NaN)
                NumericDropdown(
                    value = if (h.isNaN()) null else h,
                    label = "height (dp)",
                    options = listOf(32.0, 36.0, 40.0, 44.0, 48.0, 52.0, 56.0, 64.0),
                    includeDefault = true,
                    defaultLabel = "(auto)"
                ) { v -> if (v == null) btn.remove("heightDp") else btn.put("heightDp", v); onChange() }

                val w = btn.optDouble("widthDp", Double.NaN)
                NumericDropdown(
                    value = if (w.isNaN()) null else w,
                    label = "width (dp)",
                    options = listOf(72.0, 96.0, 120.0, 160.0, 200.0, 240.0),
                    includeDefault = true,
                    defaultLabel = "(auto)"
                ) { v -> if (v == null) btn.remove("widthDp") else btn.put("widthDp", v); onChange() }

                val corner = btn.optDouble("corner", 20.0)
                NumericDropdown(
                    value = corner,
                    label = "corner (dp)",
                    options = listOf(0.0, 4.0, 8.0, 12.0, 16.0, 20.0, 24.0, 32.0)
                ) { v -> btn.put("corner", v ?: 20.0); onChange() }

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
                    options = listOf("none","scale","alpha","rotate")
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
            buttons.put(JSONObject("""{"label":"Nuovo","style":"text","icon":"add","actionId":""}""")); onChange()
        }) { Text("+ Aggiungi bottone") }

        OutlinedButton(onClick = {
            buttons.put(newHSpacerItem()); onChange()
        }) { Text("+ Aggiungi HSpacer") }
    }

    Divider()
    Text("Stile testo (etichette bottoni)", style = MaterialTheme.typography.titleSmall)
    TextStyleEditor(node = working, title = "Testo bottoni", prefix = "", onChange = onChange)

    Divider()
    Text("Stile contenitore (intera riga)", style = MaterialTheme.typography.titleSmall)
    ContainerStyleEditorForNode(working, nodeKey = "container", onChange = onChange)
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

    Divider()
    TextStyleEditor(node = working, title = "Stile testo (label)", prefix = "label", onChange = onChange)

    Divider()
    ContainerStyleEditorForNode(working, nodeKey = "container", onChange = onChange)
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

    val minOpt = listOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0)
    val maxOpt = listOf(5.0, 7.0, 10.0, 15.0, 20.0)
    val stepOpt = listOf(0.1, 0.2, 0.5, 1.0, 2.0)

    NumericDropdown(working.optDouble("min", 0.0), "min", minOpt) { v -> working.put("min", v ?: 0.0); onChange() }
    NumericDropdown(working.optDouble("max", 10.0), "max", maxOpt) { v -> working.put("max", v ?: 10.0); onChange() }
    NumericDropdown(working.optDouble("step", 1.0), "step", stepOpt) { v -> working.put("step", v ?: 1.0); onChange() }

    val unit = remember { mutableStateOf(working.optString("unit","")) }
    OutlinedTextField(value = unit.value, onValueChange = {
        unit.value = it; working.put("unit", it); onChange()
    }, label = { Text("unit (opz.)") })

    Divider()
    TextStyleEditor(node = working, title = "Stile testo (label)", prefix = "label", onChange = onChange)

    Divider()
    ContainerStyleEditorForNode(working, nodeKey = "container", onChange = onChange)
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

    Button(onClick = { chips.put(JSONObject("""{"label":"Nuovo","bind":"chip_new"}""")); onChange() }) {
        Text("+ Aggiungi chip")
    }

    Divider()
    TextStyleEditor(node = working, title = "Stile testo chip", prefix = "", onChange = onChange)

    Divider()
    ContainerStyleEditorForNode(working, nodeKey = "container", onChange = onChange)
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

    Divider()
    TextStyleEditor(node = working, title = "Stile testo etichette", prefix = "tab", onChange = onChange)

    Divider()
    ContainerStyleEditorForNode(working, nodeKey = "container", onChange = onChange)
}

@Composable
private fun MetricsGridInspectorPanel(working: JSONObject, onChange: () -> Unit) {
    Text("MetricsGrid – Proprietà", style = MaterialTheme.typography.titleMedium)

    val cols = remember { mutableStateOf(working.optInt("columns", 2).toString()) }
    ExposedDropdown(
        value = cols.value, label = "columns",
        options = listOf("1","2","3")
    ) { sel -> cols.value = sel; working.put("columns", sel.toInt()); onChange() }

    Divider()
    TextStyleEditor(node = working, title = "Stile testo (etichette tiles)", prefix = "", onChange = onChange)

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

    Divider()
    ContainerStyleEditorForNode(working, nodeKey = "container", onChange = onChange)
}

@Composable
private fun ProgressInspectorPanel(working: JSONObject, onChange: () -> Unit) {
    Text("Progress – Proprietà", style = MaterialTheme.typography.titleMedium)

    val label = remember { mutableStateOf(working.optString("label","")) }
    OutlinedTextField(value = label.value, onValueChange = {
        label.value = it; working.put("label", it); onChange()
    }, label = { Text("label") })

    NumericDropdown(
        value = working.optDouble("value", 0.0),
        label = "value (0–100)",
        options = listOf(0.0,10.0,20.0,30.0,40.0,50.0,60.0,70.0,80.0,90.0,100.0)
    ) { v -> working.put("value", (v ?: 0.0).coerceIn(0.0, 100.0)); onChange() }

    val showPercent = remember { mutableStateOf(working.optBoolean("showPercent", true)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = showPercent.value, onCheckedChange = {
            showPercent.value = it; working.put("showPercent", it); onChange()
        })
        Spacer(Modifier.width(8.dp)); Text("mostra %")
    }

    val color = remember { mutableStateOf(working.optString("color","primary")) }
    NamedColorPickerPlus(current = color.value, label = "color", allowRoles = true) { pick ->
        color.value = pick
        if (pick.isBlank()) working.remove("color") else working.put("color", pick)
        onChange()
    }

    Divider()
    TextStyleEditor(node = working, title = "Stile testo (label)", prefix = "label", onChange = onChange)

    Divider()
    ContainerStyleEditorForNode(working, nodeKey = "container", onChange = onChange)
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

    Divider()
    TextStyleEditor(node = working, title = "Titolo – stile testo", prefix = "title", onChange = onChange)
    TextStyleEditor(node = working, title = "Messaggio – stile testo", prefix = "message", onChange = onChange)

    Divider()
    Text("Override contenitore (opzionale)", style = MaterialTheme.typography.titleSmall)
    ContainerStyleEditorForNode(working, nodeKey = "container", onChange = onChange)
}

@Composable
private fun ImageInspectorPanel(working: JSONObject, onChange: () -> Unit) {
    Text("Image – Proprietà", style = MaterialTheme.typography.titleMedium)

    val source = remember { mutableStateOf(working.optString("source","")) }
    OutlinedTextField(value = source.value, onValueChange = {
        source.value = it; working.put("source", it); onChange()
    }, label = { Text("source (es. res:ic_launcher_foreground)") })

    NumericDropdown(
        value = working.optDouble("heightDp", 160.0),
        label = "height (dp)",
        options = listOf(64.0, 96.0, 128.0, 160.0, 200.0, 240.0, 320.0)
    ) { v -> working.put("heightDp", (v ?: 160.0).coerceAtLeast(64.0)); onChange() }

    NumericDropdown(
        value = working.optDouble("corner", 12.0),
        label = "corner (dp)",
        options = listOf(0.0, 8.0, 12.0, 16.0, 20.0, 24.0, 32.0)
    ) { v -> working.put("corner", (v ?: 12.0).coerceAtLeast(0.0)); onChange() }

    var scale by remember { mutableStateOf(working.optString("contentScale","fit")) }
    ExposedDropdown(value = scale, label = "contentScale", options = listOf("fit","crop")) {
        sel -> scale = sel; working.put("contentScale", sel); onChange()
    }

    Divider()
    ContainerStyleEditorForNode(working, nodeKey = "container", onChange = onChange)
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
        style = sel; working.put("style", sel); onChange()
    }

    var align by remember { mutableStateOf(working.optString("align","start")) }
    ExposedDropdown(
        value = align, label = "align",
        options = listOf("start","center","end")
    ) { sel -> align = sel; working.put("align", sel); onChange() }

    Divider()
    TextStyleEditor(node = working, title = "Stile testo (titolo)", prefix = "", onChange = onChange)
    TextStyleEditor(node = working, title = "Stile testo (sottotitolo)", prefix = "subtitle", onChange = onChange)

    Divider()
    ContainerStyleEditorForNode(working, nodeKey = "container", onChange = onChange)
}

@Composable
private fun TopBarInspectorPanel(topBar: JSONObject, onChange: () -> Unit) {
    Spacer(Modifier.height(8.dp))

    var variant by remember { mutableStateOf(topBar.optString("variant","small")) }
    ExposedDropdown(
        value = variant, label = "variant",
        options = listOf("small","center","medium","large")
    ) { sel -> variant = sel; topBar.put("variant", sel); onChange() }

    val title = remember { mutableStateOf(topBar.optString("title","")) }
    OutlinedTextField(value = title.value, onValueChange = {
        title.value = it; topBar.put("title", it); onChange()
    }, label = { Text("title") })

    val subtitle = remember { mutableStateOf(topBar.optString("subtitle","")) }
    OutlinedTextField(value = subtitle.value, onValueChange = {
        subtitle.value = it; if (it.isBlank()) topBar.remove("subtitle") else topBar.put("subtitle", it); onChange()
    }, label = { Text("subtitle (opz.)") })

    var scroll by remember { mutableStateOf(topBar.optString("scroll","none")) }
    ExposedDropdown(
        value = scroll, label = "scroll",
        options = listOf("none","pinned","enterAlways","exitUntilCollapsed")
    ) { sel -> scroll = sel; if (sel=="none") topBar.remove("scroll") else topBar.put("scroll", sel); onChange() }

    Divider(); Text("Stile (shortcut rapido sfondo/bordo)", style = MaterialTheme.typography.titleSmall)
    val style = remember { mutableStateOf(topBar.optString("style","")) }
    ExposedDropdown(
        value = if (style.value.isBlank()) "(none)" else style.value,
        label = "style",
        options = listOf("(none)","primary","tonal","outlined","text")
    ) { sel ->
        val v = if (sel == "(none)") "" else sel
        style.value = v
        if (v.isBlank()) topBar.remove("style") else topBar.put("style", v)
        onChange()
    }

    val tint = remember { mutableStateOf(topBar.optString("tint","default")) }
    ExposedDropdown(
        value = tint.value, label = "tint",
        options = listOf("default","success","warning","error")
    ) { sel -> tint.value = sel; topBar.put("tint", sel); onChange() }

    val customColor = remember { mutableStateOf(topBar.optString("customColor","")) }
    NamedColorPickerPlus(current = customColor.value, label = "customColor (opz.)", allowRoles = true) { pick ->
        customColor.value = pick
        if (pick.isBlank()) topBar.remove("customColor") else topBar.put("customColor", pick); onChange()
    }

    // Trasparenza / Outline legacy (compat)
    Divider()
    var transparent by remember { mutableStateOf(topBar.optBoolean("transparent", false)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = transparent, onCheckedChange = {
            transparent = it; if (!it) topBar.remove("transparent") else topBar.put("transparent", true); onChange()
        })
        Spacer(Modifier.width(8.dp)); Text("Trasparente (legacy)")
    }

    var outlineEnabled by remember { mutableStateOf(topBar.optJSONObject("outline") != null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = outlineEnabled, onCheckedChange = {
            outlineEnabled = it
            if (it) {
                val o = topBar.optJSONObject("outline") ?: JSONObject().also {
                    it.put("color", "outline"); it.put("thickness", 1)
                }
                topBar.put("outline", o)
            } else topBar.remove("outline")
            onChange()
        })
        Spacer(Modifier.width(8.dp)); Text("Mostra bordo (legacy)")
    }
    topBar.optJSONObject("outline")?.let { o ->
        val col = remember { mutableStateOf(o.optString("color", "outline")) }
        NamedColorPickerPlus(current = col.value, label = "outline.color", allowRoles = true) { pick ->
            col.value = pick; o.put("color", pick); onChange()
        }
        NumericDropdown(
            value = o.optDouble("thickness", 1.0),
            label = "outline.thickness (dp)",
            options = listOf(0.5, 1.0, 1.5, 2.0, 3.0, 4.0)
        ) { v -> o.put("thickness", (v ?: 1.0).coerceAtLeast(0.5)); onChange() }
    }

    Divider()
    val containerColor = remember { mutableStateOf(topBar.optString("containerColor","surface")) }
    NamedColorPickerPlus(current = containerColor.value, label = "containerColor", allowRoles = true) { pick ->
        containerColor.value = pick
        if (pick.isBlank()) topBar.remove("containerColor") else topBar.put("containerColor", pick)
        onChange()
    }

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

    val subtitleColor = remember { mutableStateOf(topBar.optString("subtitleColor", "")) }
    NamedColorPickerPlus(current = subtitleColor.value, label = "subtitleColor (opz.)", allowRoles = true) { pick ->
        subtitleColor.value = pick
        if (pick.isBlank()) topBar.remove("subtitleColor") else topBar.put("subtitleColor", pick)
        onChange()
    }

    Divider()
    Text("Gradient (opz.)", style = MaterialTheme.typography.titleSmall)
    var gradEnabled by remember { mutableStateOf(topBar.optJSONObject("gradient") != null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = gradEnabled, onCheckedChange = {
            gradEnabled = it
            if (it) {
                val g = topBar.optJSONObject("gradient") ?: JSONObject().also { j ->
                    j.put("colors", JSONArray().put("primary").put("tertiary"))
                    j.put("direction", "vertical")
                }
                topBar.put("gradient", g)
            } else topBar.remove("gradient")
            onChange()
        })
        Spacer(Modifier.width(8.dp)); Text("Abilita gradient")
    }
    topBar.optJSONObject("gradient")?.let { g ->
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

    Divider()
    NumericDropdown(
        value = topBar.optDouble("roundedBottomStart", 0.0),
        label = "roundedBottomStart (dp)",
        options = listOf(0.0, 2.0, 4.0, 6.0, 8.0, 12.0, 16.0, 20.0, 24.0, 32.0)
    ) { v -> topBar.put("roundedBottomStart", (v ?: 0.0).coerceAtLeast(0.0)); onChange() }

    NumericDropdown(
        value = topBar.optDouble("roundedBottomEnd", 0.0),
        label = "roundedBottomEnd (dp)",
        options = listOf(0.0, 2.0, 4.0, 6.0, 8.0, 12.0, 16.0, 20.0, 24.0, 32.0)
    ) { v -> topBar.put("roundedBottomEnd", (v ?: 0.0).coerceAtLeast(0.0)); onChange() }

    NumericDropdown(
        value = topBar.optDouble("tonalElevation", 0.0),
        label = "tonalElevation (dp)",
        options = listOf(0.0, 1.0, 2.0, 3.0, 4.0, 6.0, 8.0, 12.0)
    ) { v -> topBar.put("tonalElevation", (v ?: 0.0).coerceAtLeast(0.0)); onChange() }

    Divider(); Text("Title – stile testo", style = MaterialTheme.typography.titleSmall)
    TextStyleEditor(node = topBar, title = "Title", prefix = "title", allowColor = false, onChange = onChange)
    Divider(); Text("Subtitle – stile testo", style = MaterialTheme.typography.titleSmall)
    TextStyleEditor(node = topBar, title = "Subtitle", prefix = "subtitle", allowColor = false, onChange = onChange)

    val divider = remember { mutableStateOf(topBar.optBoolean("divider", false)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = divider.value, onCheckedChange = {
            divider.value = it
            if (!it) topBar.remove("divider") else topBar.put("divider", true)
            onChange()
        })
        Spacer(Modifier.width(8.dp)); Text("Divider inferiore")
    }

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
                OutlinedTextField(value = act.value, onValueChange = {
                    act.value = it; itx.put("actionId", it); onChange()
                }, label = { Text("actionId") })
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

    TextStyleEditor(node = working, title = "Stile testo elementi", prefix = "", onChange = onChange)
    TextStyleEditor(node = working, title = "Stile testo sottotitolo", prefix = "subtitle", onChange = onChange)

    Divider()
    Text("Effetto pressione sugli elementi", style = MaterialTheme.typography.titleSmall)
    var press by remember { mutableStateOf(working.optString("pressEffect", "none")) }
    ExposedDropdown(
        value = press, label = "pressEffect",
        options = listOf("none","scale","alpha","rotate")
    ) { sel -> press = sel; working.put("pressEffect", sel); onChange() }

    Divider()
    ContainerStyleEditorForNode(working, nodeKey = "container", onChange = onChange)
}

@Composable
private fun CardInspectorPanel(working: JSONObject, onChange: () -> Unit) {
    Text("Card – Proprietà", style = MaterialTheme.typography.titleMedium)
    val action = remember { mutableStateOf(working.optString("clickActionId","")) }
    OutlinedTextField(action.value, { action.value = it; working.put("clickActionId", it); onChange() }, label = { Text("clickActionId (opz.)") })

    var press by remember { mutableStateOf(working.optString("pressEffect", "none")) }
    ExposedDropdown(
        value = press, label = "pressEffect",
        options = listOf("none","scale","alpha","rotate")
    ) { sel -> press = sel; working.put("pressEffect", sel); onChange() }

    Divider()
    Text("Stile contenitore", style = MaterialTheme.typography.titleSmall)
    // Disattiviamo "variant" legacy se si usa il nuovo container
    if (working.has("variant")) {
        Text(
            "Nota: se imposti 'container.style', la proprietà legacy 'variant' viene ignorata.",
            style = MaterialTheme.typography.labelSmall,
            color = LocalContentColor.current.copy(alpha = 0.7f)
        )
    }
    ContainerStyleEditorForNode(working, nodeKey = "container", onChange = onChange)
}

/* =========================================================
 * PAGE BACKGROUND INSPECTOR
 * ========================================================= */
@Composable
private fun PageBackgroundInspectorPanel(
    working: JSONObject,
    onChange: () -> Unit,
    dispatch: (String) -> Unit
) {
    val pb = working.optJSONObject("pageBackground") ?: JSONObject().also { working.put("pageBackground", it) }

    NumericDropdown(
        value = pb.optDouble("opacity", 1.0),
        label = "opacity (0..1)",
        options = listOf(0.0, 0.25, 0.5, 0.75, 1.0)
    ) { v -> pb.put("opacity", (v ?: 1.0).coerceIn(0.0, 1.0)); onChange() }

    Divider()
    Text("Gradient", style = MaterialTheme.typography.titleSmall)
    var gradEnabled by remember { mutableStateOf(pb.optJSONObject("gradient") != null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = gradEnabled, onCheckedChange = {
            gradEnabled = it
            if (it) {
                val g = pb.optJSONObject("gradient") ?: JSONObject().also { j ->
                    j.put("colors", JSONArray().put("surface").put("primary"))
                    j.put("direction", "vertical")
                }
                pb.put("gradient", g)
            } else pb.remove("gradient")
            onChange()
        })
        Spacer(Modifier.width(8.dp)); Text("Abilita gradient")
    }
    pb.optJSONObject("gradient")?.let { g ->
        val colorsArr = g.optJSONArray("colors") ?: JSONArray().also { g.put("colors", it) }
        while (colorsArr.length() < 2) colorsArr.put("surface")
        val c1 = remember { mutableStateOf(colorsArr.optString(0, "surface")) }
        val c2 = remember { mutableStateOf(colorsArr.optString(1, "primary")) }

        NamedColorPickerPlus(current = c1.value, label = "color 1", allowRoles = true) { pick ->
            c1.value = pick; colorsArr.put(0, pick); onChange()
        }
        NamedColorPickerPlus(current = c2.value, label = "color 2", allowRoles = true) { pick ->
            c2.value = pick; colorsArr.put(1, pick); onChange()
        }
        var dir by remember { mutableStateOf(g.optString("direction","vertical")) }
        ExposedDropdown(
            value = dir, label = "direction",
            options = listOf("vertical","horizontal")
        ) { sel -> dir = sel; g.put("direction", sel); onChange() }
    }

    Divider()
    Text("Immagine", style = MaterialTheme.typography.titleSmall)
    var imgEnabled by remember { mutableStateOf(pb.optJSONObject("image") != null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = imgEnabled, onCheckedChange = {
            imgEnabled = it
            if (it) {
                val im = pb.optJSONObject("image") ?: JSONObject().also { j ->
                    j.put("source", ""); j.put("contentScale", "crop"); j.put("alpha", 1.0)
                }
                pb.put("image", im)
            } else pb.remove("image")
            onChange()
        })
        Spacer(Modifier.width(8.dp)); Text("Abilita immagine fullscreen")
    }
    pb.optJSONObject("image")?.let { im ->
        val src = remember { mutableStateOf(im.optString("source","")) }
        OutlinedTextField(
            value = src.value,
            onValueChange = { src.value = it; im.put("source", it); onChange() },
            label = { Text("source (es. res:wallpaper)") }
        )
        var scale by remember { mutableStateOf(im.optString("contentScale","crop")) }
        ExposedDropdown(value = scale, label = "contentScale", options = listOf("crop","fit")) {
            sel -> scale = sel; im.put("contentScale", sel); onChange()
        }

        NumericDropdown(
            value = im.optDouble("alpha", 1.0),
            label = "alpha (0..1)",
            options = listOf(0.0, 0.25, 0.5, 0.75, 1.0)
        ) { v -> im.put("alpha", (v ?: 1.0).coerceIn(0.0, 1.0)); onChange() }

        var query by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Cerca immagini…") }, modifier = Modifier.weight(1f))
            Button(onClick = { if (query.isNotBlank()) dispatch("search_bg_image:$query") }) {
                Icon(Icons.Filled.Search, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Cerca")
            }
        }
        Text(
            "Nota: l’azione \"search_bg_image:<query>\" deve essere gestita dall’app per restituire una sorgente e impostarla in 'source'.",
            style = MaterialTheme.typography.labelSmall,
            color = LocalContentColor.current.copy(alpha = 0.7f)
        )
    }
}

/* =========================================================
 * HELPERS: mapping, pickers, utils
 * ========================================================= */

@Composable
private fun brushFromJson(grad: JSONObject?): Brush? {
    if (grad == null) return null
    val cols = grad.optJSONArray("colors")?.let { arr ->
        (0 until arr.length()).mapNotNull { idx -> parseColorOrRole(arr.optString(idx)) }
    } ?: emptyList()
    if (cols.size < 2) return null
    return if (grad.optString("direction", "vertical") == "horizontal") {
        Brush.horizontalGradient(cols)
    } else {
        Brush.verticalGradient(cols)
    }
}

@Composable
private fun rememberPainterFromSource(source: String?): Painter? {
    if (source.isNullOrBlank()) return null
    val ctx = LocalContext.current
    if (source.startsWith("res:")) {
        val resName = source.removePrefix("res:")
        val id = ctx.resources.getIdentifier(resName, "drawable", ctx.packageName)
        return if (id != 0) painterResource(id) else null
    }
    return null
}

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

@Composable
private fun IconText(
    label: String,
    icon: String,
    textStyle: TextStyle?,
    textColor: Color?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NamedIconEx(icon, null)
        Text(label, style = textStyle ?: MaterialTheme.typography.bodyMedium, color = textColor ?: LocalContentColor.current)
    }
}

/* ---- Unified color mapping for containers (riusa la logica dei bottoni) ---- */
@Composable
private fun mapContainerColors(style: String, tint: String, custom: Color?): Triple<Color, Color, Dp> {
    val cs = MaterialTheme.colorScheme

    // Base palette per tint
    val (baseContainer, baseContent) = when (tint) {
        "success" -> cs.tertiary to cs.onTertiary
        "warning" -> Color(0xFFFFD54F) to Color(0xFF3E2723)
        "error"   -> cs.errorContainer to cs.onErrorContainer
        else      -> cs.primary to cs.onPrimary
    }

    return when (style) {
        "outlined" -> Triple(Color.Transparent, when (tint) {
            "success" -> cs.tertiary
            "warning" -> Color(0xFF8D6E63)
            "error"   -> cs.error
            else      -> cs.primary
        }, 1.dp)
        "text" -> Triple(Color.Transparent, when (tint) {
            "success" -> cs.tertiary
            "warning" -> Color(0xFF8D6E63)
            "error"   -> cs.error
            else      -> cs.primary
        }, 0.dp)
        "tonal" -> Triple(cs.secondaryContainer, cs.onSecondaryContainer, 0.dp)
        // "primary" / default filled
        else -> {
            val cont = custom ?: baseContainer
            Triple(cont, bestOnColor(cont), 0.dp)
        }
    }
}

/* Back-compat: bottoni continuano a usare questa funzione */
@Composable
private fun mapButtonColors(style: String, tint: String): Triple<Color, Color, Dp> {
    return mapContainerColors(style, tint, null)
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
    "bolt", "local_fire_department", "sports_score", "toggle_on",
    "search"
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

/* ---- Numeric dropdown (granulare) ---- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumericDropdown(
    value: Double?,
    label: String,
    options: List<Double>,
    includeDefault: Boolean = false,
    defaultLabel: String = "(default)",
    format: (Double) -> String = {
        if (kotlin.math.abs(it - it.toInt()) < 0.0001) it.toInt().toString() else "%.2f".format(it)
    },
    onSelect: (Double?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val display = value?.let { format(it) } ?: defaultLabel

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
            if (includeDefault) {
                DropdownMenuItem(text = { Text(defaultLabel) }, onClick = { onSelect(null); expanded = false })
                Divider()
            }
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(format(opt)) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}

/* ---- Editor riutilizzabile per stile contenitore ---- */
@Composable
private fun ContainerStyleEditorForNode(
    node: JSONObject,
    nodeKey: String,
    onChange: () -> Unit
) {
    var enabled by remember { mutableStateOf(node.optJSONObject(nodeKey) != null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = enabled,
            onCheckedChange = {
                enabled = it
                if (it && node.optJSONObject(nodeKey) == null) node.put(nodeKey, JSONObject().apply { put("style","primary") })
                if (!it) node.remove(nodeKey)
                onChange()
            }
        )
        Spacer(Modifier.width(8.dp)); Text("Abilita stile contenitore")
    }
    if (!enabled) return

    val c = node.optJSONObject(nodeKey)!!

    var style by remember { mutableStateOf(c.optString("style","primary")) }
    ExposedDropdown(
        value = style, label = "style",
        options = listOf("primary","tonal","outlined","text")
    ) { sel -> style = sel; c.put("style", sel); onChange() }

    var tint by remember { mutableStateOf(c.optString("tint","default")) }
    ExposedDropdown(
        value = tint, label = "tint",
        options = listOf("default","success","warning","error")
    ) { sel -> tint = sel; c.put("tint", sel); onChange() }

    val customColor = remember { mutableStateOf(c.optString("customColor","")) }
    NamedColorPickerPlus(current = customColor.value, label = "customColor (opz.)", allowRoles = true) { pick ->
        customColor.value = pick
        if (pick.isBlank()) c.remove("customColor") else c.put("customColor", pick)
        onChange()
    }

    var shape by remember { mutableStateOf(c.optString("shape","rounded")) }
    ExposedDropdown(
        value = shape, label = "shape",
        options = listOf("rounded","pill","cut")
    ) { sel -> shape = sel; c.put("shape", sel); onChange() }

    NumericDropdown(
        value = c.optDouble("corner", 12.0),
        label = "corner (dp)",
        options = listOf(0.0, 4.0, 8.0, 12.0, 16.0, 20.0, 24.0, 32.0)
    ) { v -> c.put("corner", v ?: 12.0); onChange() }

    NumericDropdown(
        value = c.optDouble("elevationDp", 0.0),
        label = "elevation (dp)",
        options = listOf(0.0, 1.0, 2.0, 3.0, 4.0, 6.0, 8.0, 12.0)
    ) { v -> c.put("elevationDp", v ?: 0.0); onChange() }

    if (style == "outlined") {
        NumericDropdown(
            value = c.optDouble("borderThicknessDp", 1.0),
            label = "borderThickness (dp)",
            options = listOf(0.5, 1.0, 1.5, 2.0, 3.0, 4.0)
        ) { v -> c.put("borderThicknessDp", v ?: 1.0); onChange() }
        val borderColor = remember { mutableStateOf(c.optString("borderColor","")) }
        NamedColorPickerPlus(current = borderColor.value, label = "borderColor (opz.)", allowRoles = true) { pick ->
            borderColor.value = pick
            if (pick.isBlank()) c.remove("borderColor") else c.put("borderColor", pick)
            onChange()
        }
    }
}

/* ---- Editor stile testo (riutilizzato ovunque) ---- */
@Composable
private fun TextStyleEditor(
    node: JSONObject,
    title: String,
    prefix: String = "",
    allowColor: Boolean = true,
    onChange: () -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleSmall)

    val sizeKey = if (prefix.isBlank()) "textSizeSp" else "${prefix}TextSizeSp"
    val famKey  = if (prefix.isBlank()) "fontFamily" else "${prefix}FontFamily"
    val wKey    = if (prefix.isBlank()) "fontWeight" else "${prefix}FontWeight"
    val colKey  = if (prefix.isBlank()) "textColor" else "${prefix}TextColor"

    val sizeVal = node.optDouble(sizeKey, Double.NaN)
    NumericDropdown(
        value = if (sizeVal.isNaN()) null else sizeVal,
        label = "textSize (sp)",
        options = listOf(10.0, 12.0, 14.0, 16.0, 18.0, 20.0, 24.0, 28.0, 32.0),
        includeDefault = true
    ) { v ->
        if (v == null) node.remove(sizeKey) else node.put(sizeKey, v)
        onChange()
    }

    var fontFamily by remember { mutableStateOf(node.optString(famKey, "")) }
    ExposedDropdown(
        value = if (fontFamily.isBlank()) "(default)" else fontFamily,
        label = "fontFamily",
        options = listOf("(default)","default","sans","serif","monospace","cursive")
    ) { sel ->
        val v = if (sel == "(default)") "" else sel
        fontFamily = v
        if (v.isBlank()) node.remove(famKey) else node.put(famKey, v)
        onChange()
    }

    var fontWeight by remember { mutableStateOf(node.optString(wKey, "")) }
    ExposedDropdown(
        value = if (fontWeight.isBlank()) "(default)" else fontWeight,
        label = "fontWeight",
        options = listOf("(default)","w300","w400","w500","w600","w700","w800","w900")
    ) { sel ->
        val v = if (sel == "(default)") "" else sel
        fontWeight = v
        if (v.isBlank()) node.remove(wKey) else node.put(wKey, v)
        onChange()
    }

    if (allowColor) {
        val textColor = remember { mutableStateOf(node.optString(colKey,"")) }
        NamedColorPickerPlus(
            current = textColor.value,
            label = "textColor",
            allowRoles = true
        ) { hex ->
            textColor.value = hex
            if (hex.isBlank()) node.remove(colKey) else node.put(colKey, hex)
            onChange()
        }
    }
}

/* ---- Icon helper ---- */

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
        "search" -> Icons.Filled.Search
        else -> null
    }
    if (image != null) Icon(image, contentDescription) else Text(".")
}

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

private val NAMED_SWATCHES = linkedMapOf(
    "White" to 0xFFFFFFFF.toInt(), "Black" to 0xFF000000.toInt(),
    "Gray50" to 0xFFFAFAFA.toInt(), "Gray100" to 0xFFF5F5F5.toInt(), "Gray200" to 0xFFEEEEEE.toInt(),
    "Gray300" to 0xFFE0E0E0.toInt(), "Gray400" to 0xFFBDBDBD.toInt(), "Gray500" to 0xFF9E9E9E.toInt(),
    "Gray600" to 0xFF757575.toInt(), "Gray700" to 0xFF616161.toInt(), "Gray800" to 0xFF424242.toInt(), "Gray900" to 0xFF212121.toInt(),
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
    "onPrimary","onSecondary","onTertiary","onSurface","onError","outline"
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
                    "outline"                 -> cs.outline
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

private fun toColorKey(c: Color): String {
    // utility per memorizzare una scelta di colore: usa #RRGGBB
    val r = (c.red * 255).toInt().coerceIn(0,255)
    val g = (c.green * 255).toInt().coerceIn(0,255)
    val b = (c.blue * 255).toInt().coerceIn(0,255)
    return "#%02X%02X%02X".format(r,g,b)
}

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
        "w800" -> FontWeight.ExtraBold
        "w900" -> FontWeight.Black
        else -> null
    }
    if (weight != null) st = st.copy(fontWeight = weight)

    val familyKey = node.optString("fontFamily", "")
    val family = when (familyKey) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        "default" -> FontFamily.Default
        "sans" -> FontFamily.SansSerif
        else -> null
    }
    if (family != null) st = st.copy(fontFamily = family)
    return st
}

private fun applyTextStyleOverridesPrefixed(node: JSONObject, prefix: String, base: TextStyle): TextStyle {
    var st = base
    val size = node.optDouble("${prefix}TextSizeSp", Double.NaN)
    if (!size.isNaN()) st = st.copy(fontSize = TextUnit(size.toFloat(), TextUnitType.Sp))
    val weightKey = node.optString("${prefix}FontWeight", "")
    val weight = when (weightKey) {
        "w300" -> FontWeight.Light
        "w400" -> FontWeight.Normal
        "w500" -> FontWeight.Medium
        "w600" -> FontWeight.SemiBold
        "w700" -> FontWeight.Bold
        "w800" -> FontWeight.ExtraBold
        "w900" -> FontWeight.Black
        else -> null
    }
    if (weight != null) st = st.copy(fontWeight = weight)
    val familyKey = node.optString("${prefix}FontFamily", "")
    val family = when (familyKey) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        "default" -> FontFamily.Default
        "sans" -> FontFamily.SansSerif
        else -> null
    }
    if (family != null) st = st.copy(fontFamily = family)
    return st
}

/* =========================================================
 * JSON utils
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
      {"label":"Start","style":"primary","icon":"play_arrow","heightDp":40,"tint":"default","shape":"rounded","corner":20,"pressEffect":"scale","actionId":"start_run"},
      {"type":"HSpacer","widthDp":12},
      {"label":"Pausa","style":"tonal","icon":"pause","heightDp":40,"tint":"default","shape":"rounded","corner":20,"actionId":"pause_run"},
      {"type":"HSpacer","widthDp":12},
      {"label":"Stop","style":"outlined","icon":"stop","heightDp":40,"tint":"error","shape":"rounded","corner":20,"actionId":"stop_run","confirm":true}
    ]}
    """.trimIndent()
)

private fun newHSpacerItem(widthDp: Int = 12) = JSONObject("""{"type":"HSpacer","widthDp":$widthDp}""")

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

