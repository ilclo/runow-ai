@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.ui.unit.ExperimentalUnitApi::class
)

package ai.runow.ui.renderer

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/* =========================================================
 * API pubbliche/internal esposte ad altri file del package
 * ========================================================= */

@Composable
fun DesignerRoot() {
    val uiState = remember { mutableMapOf<String, Any>() }
    val dispatch: (String) -> Unit = { /* TODO routing azioni app */ }
    UiScreen(
        screenName = "home",
        dispatch = dispatch,
        uiState = uiState,
        designerMode = false,
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

    // Menù raccolti dal layout
    val menus by remember(layout) { mutableStateOf(collectMenus(layout!!)) }

    // Side panels (runtime)
    var openSidePanelId by remember { mutableStateOf<String?>(null) }
    val localDispatch = wrapDispatchForSidePanels(
        openPanelSetter = { openSidePanelId = it },
        appDispatch = dispatch
    )

    // overlay designer opzionale (qui teniamo solo la levetta)
    var designMode by rememberSaveable(screenName) { mutableStateOf(designerMode) }
    var overlayHeightPx by remember { mutableStateOf(0) }
    val overlayHeightDp = with(LocalDensity.current) { overlayHeightPx.toDp() }

    Box(Modifier.fillMaxSize()) {
        RenderRootScaffold(
            layout = layout!!,
            dispatch = localDispatch,
            uiState = uiState,
            designerMode = designMode,
            menus = menus,
            extraPaddingBottom = if (designMode) overlayHeightDp + 32.dp else 16.dp,
            scaffoldPadding = scaffoldPadding
        )

        // Overlay side panels
        RenderSidePanelsOverlay(
            layout = layout!!,
            openPanelId = openSidePanelId,
            onClose = { openSidePanelId = null },
            dispatch = localDispatch
        )

        // Knob commutatore (solo estetico)
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

    FloatingActionButton(
        onClick = onToggle,
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .offset { IntOffset(0, offsetY.toInt()) }
            .pointerInput(Unit) {
                androidx.compose.foundation.gestures.detectVerticalDragGestures { _, dragAmount ->
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

private fun Modifier.topBottomBorder(color: Color, thickness: Dp): Modifier = this.then(
    androidx.compose.ui.draw.drawBehind {
        val sw = thickness.toPx().coerceAtLeast(1f)
        val yTop = sw / 2f
        val yBot = size.height - sw / 2f
        drawLine(color, androidx.compose.ui.geometry.Offset(0f, yTop), androidx.compose.ui.geometry.Offset(size.width, yTop), strokeWidth = sw)
        drawLine(color, androidx.compose.ui.geometry.Offset(0f, yBot), androidx.compose.ui.geometry.Offset(size.width, yBot), strokeWidth = sw)
    }
)

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
            Column(Modifier.padding(contentPadding)) { content() }
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
    brush?.let { b ->
        Box(Modifier.fillMaxSize().background(b))
    }
}

/* =========================================================
 * Root Scaffold
 * ========================================================= */
@Composable
private fun RenderRootScaffold(
    layout: JSONObject,
    dispatch: (String) -> Unit,
    uiState: MutableMap<String, Any>,
    designerMode: Boolean,
    menus: Map<String, JSONArray>,
    extraPaddingBottom: Dp,
    scaffoldPadding: PaddingValues
) {
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
                        menus = menus
                    )
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            RenderPageBackground(layout.optJSONObject("page"))
            if (scroll) {
                Column(Modifier.verticalScroll(rememberScrollState())) { host() }
            } else {
                host()
            }
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

    // Container unificato (usa StyledContainer)
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
            else 0

            Surface(shape = RoundedCornerShape(corner), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
                when {
                    isRes && resId != 0 -> {
                        Image(
                            painter = androidx.compose.ui.res.painterResource(resId),
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
            val innerBlocks = block.optJSONArray("blocks") ?: JSONArray()
            val containerCfg = block.optJSONObject("container") ?: JSONObject().apply {
                when (block.optString("variant","")) {
                    "outlined" -> put("style","outlined")
                    "filled"   -> put("style","primary")
                    "elevated" -> { put("style","surface"); put("elevationDp", 2) }
                    else       -> put("style","surface")
                }
            }
            val baseMod = Modifier
                .fillMaxWidth()
                .then(if (clickAction.isNotBlank()) Modifier.clickable { dispatch(clickAction) } else Modifier)

            StyledContainer(
                cfg = containerCfg,
                modifier = baseMod,
                contentPadding = PaddingValues(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 0 until innerBlocks.length()) {
                        val b = innerBlocks.optJSONObject(i) ?: continue
                        val p2 = "$path/blocks/$i"
                        RenderBlock(b, dispatch, uiState, designerMode, p2, menus)
                    }
                }
            }
        }

        "Tabs" -> containerWrap {
            val tabs = block.optJSONArray("tabs") ?: JSONArray()
            var idx by remember(path) { mutableStateOf(block.optInt("initialIndex", 0).coerceAtLeast(0)) }
            val count = tabs.length().coerceAtLeast(1)
            if (idx >= count) idx = 0
            val labels = (0 until count).map {
                tabs.optJSONObject(it)?.optString("label", "Tab ${it+1}") ?: "Tab ${it+1}"
            }

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
                    RenderBlock(b, dispatch, uiState, designerMode, p2, menus)
                }
            }
        }

        "SectionHeader" -> containerWrap {
            val style = mapTextStyle(block.optString("style", "titleMedium"))
            val align = mapTextAlign(block.optString("align", "start"))
            val clickAction = block.optString("clickActionId", "")
            val textColor = parseColorOrRole(block.optString("textColor", ""))

            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (clickAction.isNotBlank()) Modifier.clickable { dispatch(clickAction) } else Modifier)
            ) {
                Text(
                    text = block.optString("title", ""),
                    style = applyTextStyleOverrides(block, style),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = align,
                    color = textColor ?: LocalContentColor.current
                )
                val sub = block.optString("subtitle", "")
                if (sub.isNotBlank()) {
                    Text(
                        sub,
                        style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyMedium),
                        textAlign = align,
                        modifier = Modifier.fillMaxWidth(),
                        color = textColor ?: LocalContentColor.current
                    )
                }
            }
        }

        "MetricsGrid" -> containerWrap {
            val tiles = block.optJSONArray("tiles") ?: JSONArray()
            val cols = block.optInt("columns", 2).coerceIn(1, 3)
            GridSection(tiles, cols)
        }

        "ButtonRow" -> containerWrap {
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
                horizontalArrangement = align
            ) {
                for (i in 0 until buttons.length()) {
                    val btn = buttons.optJSONObject(i) ?: continue
                    val label = btn.optString("label", "Button")
                    val styleKey = btn.optString("style", "primary")
                    val action = btn.optString("actionId", "")
                    val confirm = btn.optBoolean("confirm", false)
                    val sizeKey = btn.optString("size", "md")
                    val tintKey = btn.optString("tint", "default")
                    val shapeKey = btn.optString("shape", "rounded")
                    val corner = Dp(btn.optDouble("corner", 20.0).toFloat())
                    val pressKey = btn.optString("pressEffect", "none")
                    val icon = btn.optString("icon", "")

                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val scale by animateFloatAsState(targetValue = if (pressKey == "scale" && pressed) 0.96f else 1f, label = "btnScale")

                    val shape = when (shapeKey) {
                        "pill" -> RoundedCornerShape(50)
                        "cut" -> CutCornerShape(corner)
                        else -> RoundedCornerShape(corner)
                    }

                    var (container, content, border) = mapButtonColors(styleKey, tintKey)
                    parseColorOrRole(btn.optString("customColor", ""))?.let { col ->
                        container = col
                        content = bestOnColor(col)
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
                        .graphicsLayer(scaleX = scale, scaleY = scale)
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

        "ChipRow" -> containerWrap {
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
                            label = { Text(label) },
                            leadingIcon = if (current == v) { { Icon(Icons.Filled.Check, null) } } else null
                        )
                    } else {
                        val current = (uiState[bind] as? Boolean) ?: false
                        FilterChip(
                            selected = current,
                            onClick = { uiState[bind] = !current },
                            label = { Text(label) },
                            leadingIcon = if (current) { { Icon(Icons.Filled.Check, null) } } else null
                        )
                    }
                }
            }
        }

        "Toggle" -> containerWrap {
            val label = block.optString("label", "")
            val bind = block.optString("bind", "")
            val v = (uiState[bind] as? Boolean) ?: false
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = v, onCheckedChange = { uiState[bind] = it })
                Spacer(Modifier.width(8.dp))
                Text(label)
            }
        }

        "Slider" -> containerWrap {
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

        "List" -> containerWrap {
            val items = block.optJSONArray("items") ?: JSONArray()
            val align = mapTextAlign(block.optString("align", "start"))
            val textColor = parseColorOrRole(block.optString("textColor",""))
            Column {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    ListItem(
                        headlineContent = {
                            Text(
                                item.optString("title", ""),
                                color = textColor ?: LocalContentColor.current,
                                textAlign = align,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        supportingContent = {
                            val sub = item.optString("subtitle", "")
                            if (sub.isNotBlank()) Text(
                                sub,
                                color = textColor ?: LocalContentColor.current,
                                textAlign = align,
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

        "Fab" -> containerWrap {
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
            val thick = Dp(block.optDouble("thickness", 1.0).toFloat())
            val padStart = Dp(block.optDouble("padStart", 0.0).toFloat())
            val padEnd = Dp(block.optDouble("padEnd", 0.0).toFloat())
            Divider(modifier = Modifier.padding(start = padStart, end = padEnd), thickness = thick)
        }

        "DividerV" -> {
            val thickness = Dp(block.optDouble("thickness", 1.0).toFloat())
            val height = Dp(block.optDouble("height", 24.0).toFloat())
            androidx.compose.material3.VerticalDivider(modifier = Modifier.height(height), thickness = thickness)
        }

        "Spacer" -> {
            Spacer(Modifier.height(Dp(block.optDouble("height", 8.0).toFloat())))
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
 * Grid
 * ========================================================= */
@Composable
private fun GridSection(tiles: JSONArray, cols: Int) {
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
 * Helpers: mapping, pickers, utils
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

/* ---- Icon helper (internal per SidePanels) ---- */
@Composable
internal fun NamedIconEx(name: String?, contentDescription: String?) {
    val ctx = LocalContext.current
    if (name.isNullOrBlank()) {
        Icon(Icons.Filled.MoreHoriz, contentDescription)
        return
    }

    // icona da risorsa drawable
    if (name.startsWith("res:")) {
        val resName = name.removePrefix("res:")
        val id = ctx.resources.getIdentifier(resName, "drawable", ctx.packageName)
        if (id != 0) { Icon(androidx.compose.ui.res.painterResource(id), contentDescription); return }
    }

    // icona raster via uri/content/file
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

    val image = when (name) {
        "settings" -> Icons.Filled.Settings
        "more_vert" -> Icons.Filled.MoreVert
        "tune" -> Icons.Filled.Tune
        "play_arrow" -> Icons.Filled.PlayArrow
        "pause" -> Icons.Filled.Pause
        "stop" -> Icons.Filled.Stop
        "add" -> Icons.Filled.Add
        "flag" -> Icons.Filled.Flag
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
    if (image != null) Icon(image, contentDescription) else Icon(Icons.Filled.MoreHoriz, contentDescription)
}

/* ---- Color parsing ---- */
@Composable
internal fun parseColorOrRole(value: String?): Color? {
    val v = value?.trim().orEmpty()
    if (v.isEmpty()) return null
    if (v.equals("transparent", true)) return Color.Transparent

    if (v.startsWith("#")) {
        return runCatching { Color(android.graphics.Color.parseColor(v)) }.getOrNull()
    }

    val key = if (v.startsWith("role:", true)) v.substringAfter(':') else v
    val cs = MaterialTheme.colorScheme
    return when (key.lowercase()) {
        "primary" -> cs.primary
        "onprimary" -> cs.onPrimary
        "secondary" -> cs.secondary
        "onsecondary" -> cs.onSecondary
        "tertiary" -> cs.tertiary
        "ontertiary" -> cs.onTertiary
        "surface" -> cs.surface
        "onsurface" -> cs.onSurface
        "surfacevariant" -> cs.surfaceVariant
        "onsurfacevariant" -> cs.onSurfaceVariant
        "background" -> cs.background
        "onbackground" -> cs.onBackground
        "outline" -> cs.outline
        "inverseprimary" -> cs.inversePrimary
        "inverseonsurface" -> cs.inverseOnSurface
        "error" -> cs.error
        "onerror" -> cs.onError
        else -> null
    }
}

internal fun bestOnColor(bg: Color): Color {
    // luminance (sRGB, gamma corrected) — versione autonoma
    fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }
    val L = 0.2126 * channel(bg.red) + 0.7152 * channel(bg.green) + 0.0722 * channel(bg.blue)
    return if (L > 0.5) Color.Black else Color.White
}

/* ---- Bitmap loader ---- */
@Composable
internal fun rememberImageBitmapFromUri(uri: String?): ImageBitmap? {
    if (uri.isNullOrBlank()) return null
    val ctx = LocalContext.current
    return remember(uri) {
        try {
            if (uri.startsWith("res:")) {
                val resName = uri.removePrefix("res:")
                val id = ctx.resources.getIdentifier(resName, "drawable", ctx.packageName)
                if (id != 0) {
                    val dr = ContextCompat.getDrawable(ctx, id) as? android.graphics.drawable.BitmapDrawable
                    dr?.bitmap?.asImageBitmap()
                } else null
            } else {
                ctx.contentResolver.openInputStream(Uri.parse(uri)).use { input ->
                    val bmp = BitmapFactory.decodeStream(input)
                    bmp?.asImageBitmap()
                }
            }
        } catch (_: Exception) { null }
    }
}

/* ---- Text style overrides ---- */
@Composable
private fun applyTextStyleOverrides(node: JSONObject, base: TextStyle): TextStyle {
    var st = base

    val size = node.optDouble("textSizeSp", Double.NaN)
    if (!size.isNaN()) st = st.copy(fontSize = TextUnit(size.toFloat(), TextUnitType.Sp))

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

    val styleKey = node.optString("fontStyle", "")
    val italicFlag = node.optBoolean("italic", false)
    if (styleKey.equals("italic", true) || italicFlag) {
        st = st.copy(fontStyle = FontStyle.Italic)
    } else if (styleKey.equals("normal", true)) {
        st = st.copy(fontStyle = FontStyle.Normal)
    }

    val familyKey = node.optString("fontFamily", "")
    val family = when (familyKey) {
        "serif"     -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        "cursive"   -> FontFamily.Cursive
        "sans"      -> FontFamily.SansSerif
        else        -> null
    }
    if (family != null) st = st.copy(fontFamily = family)

    val letterSp = node.optDouble("letterSpacingSp", Double.NaN)
    if (!letterSp.isNaN()) st = st.copy(letterSpacing = TextUnit(letterSp.toFloat(), TextUnitType.Sp))

    val lineH = node.optDouble("lineHeightSp", Double.NaN)
    if (!lineH.isNaN()) st = st.copy(lineHeight = TextUnit(lineH.toFloat(), TextUnitType.Sp))

    return st
}

/* =========================================================
 * Menus collector (internal per SidePanels)
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
            is JSONArray -> { for (i in 0 until n.length()) walk(n.opt(i)) }
        }
    }
    walk(root)
    return map
}