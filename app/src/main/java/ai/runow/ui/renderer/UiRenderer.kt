@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.ui.unit.ExperimentalUnitApi::class
)

package ai.runow.ui.renderer

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.round

/* =========================================================
 * API pubbliche / entry point
 * ========================================================= */

@Composable
fun DesignerRoot() {
    val uiState = remember { mutableMapOf<String, Any>() }
    val dispatch: (String) -> Unit = { /* route azioni app */ }
    UiScreen(
        screenName = "home",
        dispatch = dispatch,
        uiState = uiState,
        designerMode = true,                  // <— così vedi tutto subito
        scaffoldPadding = PaddingValues(0.dp)
    )
}

/* Schermata JSON */
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
    if (layout == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Layout '$screenName' non trovato", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val menus by remember(layout) { mutableStateOf(collectMenus(layout!!)) }

    // Side panels (runtime)
    var openSidePanelId by remember { mutableStateOf<String?>(null) }
    val localDispatch = wrapDispatchForSidePanels(
        openPanelSetter = { openSidePanelId = it },
        appDispatch = dispatch
    )

    // toggle designer
    var designMode by rememberSaveable(screenName) { mutableStateOf(designerMode) }

    Box(Modifier.fillMaxSize()) {
        RenderRootScaffold(
            layout = layout!!,
            dispatch = localDispatch,
            uiState = uiState,
            designerMode = designMode,
            menus = menus,
            scaffoldPadding = scaffoldPadding
        )

        // Overlay side panels
        RenderSidePanelsOverlay(
            layout = layout!!,
            openPanelId = openSidePanelId,
            onClose = { openSidePanelId = null },
            dispatch = localDispatch
        )

        // Knob commutatore
        DesignSwitchKnob(
            isDesigner = designMode,
            onToggle = { designMode = !designMode }
        )
    }
}

/* =========================================================
 * Knob laterale (trascinabile) per commutare Designer/Anteprima
 * ========================================================= */
@Composable
private fun BoxScope.DesignSwitchKnob(
    isDesigner: Boolean,
    onToggle: () -> Unit
) {
    var offsetY by remember { mutableStateOf(0f) }
    val maxDragPx = with(LocalDensity.current) { 220.dp.toPx() }
    val dragState = rememberDraggableState { delta ->
        offsetY = (offsetY + delta).coerceIn(-maxDragPx, maxDragPx)
    }

    FloatingActionButton(
        onClick = onToggle,
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .offset { IntOffset(0, offsetY.toInt()) }
            .draggable(state = dragState, orientation = Orientation.Vertical),
        containerColor = if (isDesigner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (isDesigner) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = CircleShape
    ) {
        Icon(if (isDesigner) Icons.Filled.Build else Icons.Filled.Visibility, contentDescription = "Designer toggle")
    }
}

/* =========================================================
 * Container helpers
 * ========================================================= */

private enum class BorderMode { None, Full, TopBottom }

private data class ResolvedContainer(
    val background: Color,
    val contentColor: Color,
    val shape: androidx.compose.ui.graphics.Shape,
    val elevation: Dp,
    val borderColor: Color,
    val borderWidth: Dp,
    val borderMode: BorderMode,
    val brush: Brush?
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
    val style   = cfg?.optString("style", "surface") ?: "surface"
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

    return ResolvedContainer(
        background = bg,
        contentColor = content,
        shape = shape,
        elevation = elevation,
        borderColor = borderColor,
        borderWidth = borderW,
        borderMode = borderMode,
        brush = if (style == "text") null else brush
    )
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
                BorderMode.TopBottom -> Modifier // (divideri sopra/sotto li aggiungi dentro)
                BorderMode.None      -> Modifier
            }
        )
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)

    CompositionLocalProvider(LocalContentColor provides r.contentColor) {
        Column(base) {
            if (r.borderMode == BorderMode.TopBottom) Divider(thickness = r.borderWidth, color = r.borderColor)
            Box(Modifier.padding(contentPadding)) { content() }
            if (r.borderMode == BorderMode.TopBottom) Divider(thickness = r.borderWidth, color = r.borderColor)
        }
    }
}

/* =========================================================
 * Page background
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
    Box(Modifier.fillMaxSize().background(color))
    brush?.let { b -> Box(Modifier.fillMaxSize().background(b)) }
}

/* =========================================================
 * Root Scaffold (+ fallback legacy)
 * ========================================================= */
@Composable
private fun RenderRootScaffold(
    layout: JSONObject,
    dispatch: (String) -> Unit,
    uiState: MutableMap<String, Any>,
    designerMode: Boolean,
    menus: Map<String, JSONArray>,
    scaffoldPadding: PaddingValues
) {
    val title = layout.optString("topTitle", "")
    val topActions = layout.optJSONArray("topActions") ?: JSONArray()

    val topBarConf = layout.optJSONObject("topBar")
    val bottomBarNode = layout.optJSONObject("bottomBar")
    val bottomBarCfg = bottomBarNode?.optJSONObject("container")
    val bottomBarItemsFromLayout = bottomBarNode?.optJSONArray("items")
    val legacyBottomButtons = layout.optJSONArray("bottomButtons") ?: JSONArray()
    val fab = layout.optJSONObject("fab")
    val scroll = layout.optBoolean("scroll", true)

    fun legacyButtonsAsItems(legacy: JSONArray): JSONArray {
        if (legacy.length() == 0) return JSONArray()
        return JSONArray().apply {
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

    val topScrollBehavior = when (topBarConf?.optString("scroll", "none")) {
        "pinned" -> pinnedScrollBehavior()
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
            } else if (title.isNotBlank() || topActions.length() > 0) {
                // Fallback legacy top app bar
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
            val items: JSONArray? = bottomBarItemsFromLayout ?: run {
                val converted = legacyButtonsAsItems(legacyBottomButtons)
                if (converted.length() > 0) converted else null
            }
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (blocks.length() == 0) {
                    // Placeholder visivo quando il layout è vuoto
                    Text(
                        "Pagina vuota.\nAggiungi blocchi dal tuo layout o abilita una top/bottom bar.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                for (i in 0 until blocks.length()) {
                    val block = blocks.optJSONObject(i) ?: continue
                    val path = "/blocks/$i"
                    RenderBlock(
                        block = block,
                        dispatch = dispatch,
                        uiState = uiState,
                        designerMode = designerMode,
                        path = path,
                        menus = menus
                    )
                }
                Spacer(Modifier.height(if (designerMode) 64.dp else 16.dp))
            }
        }

        Box(Modifier.fillMaxSize()) {
            RenderPageBackground(layout.optJSONObject("page"))
            if (scroll) Column(Modifier.verticalScroll(rememberScrollState())) { host() } else host()
        }
    }
}

/* =========================================================
 * Bar items (Top/Bottom)
 * ========================================================= */
@Composable
private fun RowScope.RenderBarItemsRow(items: JSONArray, dispatch: (String) -> Unit) {
    for (i in 0 until items.length()) {
        val it = items.optJSONObject(i) ?: continue
        val type = it.optString("type").ifBlank { if (it.has("label")) "button" else "icon" }
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
                IconButton(onClick = { if (actionId.isNotBlank()) dispatch(actionId) }) {
                    NamedIconEx(icon, null)
                }
            }
        }
    }
}

/* =========================================================
 * Top Bar
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

    val containerCfg = cfg.optJSONObject("container")?.let { JSONObject(it.toString()) } ?: JSONObject()
    cfg.optJSONObject("gradient")?.let { g ->
        if (!containerCfg.has("gradient")) containerCfg.put("gradient", JSONObject(g.toString()))
    }
    cfg.optString("containerColor","").takeIf { it.isNotBlank() }?.let { col ->
        if (!containerCfg.has("customColor")) containerCfg.put("customColor", col)
        if (!containerCfg.has("style")) containerCfg.put("style", "surface")
    }

    val resolved = resolveContainer(containerCfg)
    val titleColor     = parseColorOrRole(cfg.optString("titleColor", ""))   ?: resolved.contentColor
    val actionsColor   = parseColorOrRole(cfg.optString("actionsColor", "")) ?: titleColor

    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        titleContentColor = titleColor,
        actionIconContentColor = actionsColor,
        navigationIconContentColor = actionsColor
    )

    val actions = cfg.optJSONArray("actions") ?: JSONArray()

    StyledContainer(
        cfg = containerCfg,
        modifier = Modifier.fillMaxWidth()
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
        if (cfg.optBoolean("divider", false)) Divider()
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
 * Render block
 * ========================================================= */
@Composable
internal fun RenderBlock(
    block: JSONObject,
    dispatch: (String) -> Unit,
    uiState: MutableMap<String, Any>,
    designerMode: Boolean,
    path: String,
    menus: Map<String, JSONArray>,
) {
    @Composable
    fun containerWrap(content: @Composable () -> Unit) {
        val containerCfg = block.optJSONObject("container")
        if (containerCfg != null) {
            StyledContainer(containerCfg, Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                content()
            }
        } else {
            Box { content() }
        }
    }

    when (block.optString("type")) {
        "IconButton" -> containerWrap {
            val iconName = block.optString("icon", "more_vert")
            val actionId = block.optString("actionId", "")
            IconButton(onClick = { if (actionId.isNotBlank()) dispatch(actionId) }) {
                NamedIconEx(iconName, null)
            }
        }

        "Progress" -> containerWrap {
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

        "Alert" -> containerWrap {
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

        "Image" -> containerWrap {
            val source = block.optString("source","")
            val height = Dp(block.optDouble("heightDp", 160.0).toFloat())
            val corner = Dp(block.optDouble("corner", 12.0).toFloat())
            val scale = when (block.optString("contentScale","fit")) {
                "crop" -> ContentScale.Crop
                "fill" -> ContentScale.FillBounds
                else   -> ContentScale.Fit
            }

            val isRes = source.startsWith("res:")
            val resId = if (isRes)
                LocalContext.current.resources.getIdentifier(source.removePrefix("res:"), "drawable", LocalContext.current.packageName)