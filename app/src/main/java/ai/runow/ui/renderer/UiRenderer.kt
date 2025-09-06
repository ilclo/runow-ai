
@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.ui.unit.ExperimentalUnitApi::class
)

package ai.runow.ui.renderer

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.launch
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
 * SCHERMATA JSON + MODALITA' DESIGNER
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
    var layout by remember(screenName) {
        mutableStateOf(UiLoader.loadLayout(ctx, screenName) ?: JSONObject("""{"blocks":[]}"""))
    }
    var tick by remember { mutableStateOf(0) }

    // Menù raccolti + SidePanel ids
    val menus by remember(layout, tick) { mutableStateOf(collectMenus(layout)) }
    val sidePanelIds by remember(layout, tick) { mutableStateOf(collectSidePanelIds(layout)) }

    // Modalità designer persistente per schermata
    var designMode by rememberSaveable(screenName) { mutableStateOf(designerMode) }

    // Pannelli laterali aperti
    var openSidePanelId by remember { mutableStateOf<String?>(null) }
    var sidePanelEffectOverride by remember { mutableStateOf<String?>(null) }

    // Altezze barre (per overlay centrale)
    var topBarHeightPx by remember { mutableStateOf(0) }
    var bottomBarHeightPx by remember { mutableStateOf(0) }
    val topBarHeightDp = with(LocalDensity.current) { topBarHeightPx.toDp() }
    val bottomBarHeightDp = with(LocalDensity.current) { bottomBarHeightPx.toDp() }

    // Selezione blocco
    var selectedPath by remember(screenName) { mutableStateOf<String?>(null) }

    // Cambi live non salvati: applichiamo direttamente a "layout", ma salviamo solo su OK
    fun onLiveChange() { tick++ }
    fun onLayoutChangePersist() { UiLoader.saveDraft(ctx, screenName, layout); tick++ }

    Box(Modifier.fillMaxSize()) {

        RenderRootScaffold(
            layout = layout,
            dispatch = { act ->
                when {
                    act.startsWith("open_side_panel:") -> {
                        val id = act.removePrefix("open_side_panel:")
                        openSidePanelId = id
                        sidePanelEffectOverride = null
                    }
                    act == "close_side_panel" -> { openSidePanelId = null; sidePanelEffectOverride = null }
                    else -> dispatch(act)
                }
            },
            uiState = uiState,
            designerMode = designMode,
            menus = menus,
            selectedPathSetter = { selectedPath = it },
            extraPaddingBottom = if (designMode) 96.dp else 16.dp, // lascia un po' di spazio per la palette
            scaffoldPadding = scaffoldPadding,
            onTopBarHeight = { topBarHeightPx = it },
            onBottomBarHeight = { bottomBarHeightPx = it },
            openSidePanel = { id, eff -> openSidePanelId = id; sidePanelEffectOverride = eff }
        )

        // Overlay pannelli laterali
        RenderSidePanelsOverlay(
            layout = layout,
            visiblePanelId = openSidePanelId,
            effectOverride = sidePanelEffectOverride,
            setVisiblePanelId = { openSidePanelId = it; sidePanelEffectOverride = null },
            dispatch = dispatch,
            uiState = uiState,
            designerMode = designMode,
            menus = menus
        )

        if (designMode) {
            DesignerOverlay(
                screenName = screenName,
                layout = layout,
                selectedPath = selectedPath,
                setSelectedPath = { selectedPath = it },
                onLiveChange = { onLiveChange() },
                onLayoutChange = { onLayoutChangePersist() },
                onSaveDraft = { UiLoader.saveDraft(ctx, screenName, layout) },
                onPublish = { UiLoader.saveDraft(ctx, screenName, layout); UiLoader.publish(ctx, screenName) },
                onReset = {
                    UiLoader.resetPublished(ctx, screenName)
                    layout = UiLoader.loadLayout(ctx, screenName) ?: JSONObject("""{"blocks":[]}""")
                    selectedPath = null
                    tick++
                },
                avoidTop = topBarHeightDp,
                avoidBottom = bottomBarHeightDp,
                dispatch = dispatch,
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
 * KNOB laterale per commutare Designer/Anteprima
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
    scaffoldPadding: PaddingValues,
    onTopBarHeight: (Int) -> Unit = {},
    onBottomBarHeight: (Int) -> Unit = {},
    openSidePanel: (String, String?) -> Unit = { _, _ -> }
) {
    val title = layout.optString("topTitle", "")
    val topActions = layout.optJSONArray("topActions") ?: JSONArray()
    val topBarConf = layout.optJSONObject("topBar")

    val bottomButtons = layout.optJSONArray("bottomButtons") ?: JSONArray()
    val fab = layout.optJSONObject("fab")
    val scroll = layout.optBoolean("scroll", true)

    // Scroll behavior della TopAppBar (solo se definito in topBar)
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
            if (topBarConf != null) {
                Box(Modifier.onGloballyPositioned { onTopBarHeight(it.size.height) }) {
                    RenderTopBar(topBarConf, dispatch, topScrollBehavior)
                }
            } else {
                // Fallback legacy: topTitle/topActions
                if (title.isNotBlank() || topActions.length() > 0) {
                    Box(Modifier.onGloballyPositioned { onTopBarHeight(it.size.height) }) {
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
                } else {
                    LaunchedEffect(Unit) { onTopBarHeight(0) }
                }
            }
        },
        bottomBar = {
            if (bottomButtons.length() > 0) {
                StyledContainer(cfg = layout.optJSONObject("bottomBar")?.optJSONObject("container")) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { onBottomBarHeight(it.size.height) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until bottomButtons.length()) {
                            val btn = bottomButtons.optJSONObject(i) ?: continue
                            val label = btn.optString("label", "Button")
                            val action = btn.optString("actionId", "")
                            val openPanelId = btn.optString("openSidePanelId", "")
                            val effectOverride = btn.optString("sidePanelEffect", "")

                            TextButton(onClick = {
                                if (openPanelId.isNotBlank()) {
                                    openSidePanel(openPanelId, effectOverride.ifBlank { null })
                                } else {
                                    dispatch(action)
                                }
                            }) { Text(label) }
                        }
                    }
                }
            } else {
                LaunchedEffect(Unit) { onBottomBarHeight(0) }
            }
        },
        floatingActionButton = {
            fab?.let {
                FloatingActionButton(onClick = { dispatch(it.optString("actionId", "")) }) {
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
                        onOpenInspector = { p -> selectedPathSetter(p) },
                        openSidePanel = openSidePanel
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

/* =========================================================
 * TOP BAR con StyledContainer
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
        bottomEnd = Dp(cfg.optDouble("roundedBottomEnd", 0.0).toFloat())
    )
    val tonalElevation = Dp(cfg.optDouble("tonalElevation", 0.0).toFloat())

    val containerCfg = cfg.optJSONObject("container")
    val container = resolveContainer(containerCfg, fallbackStyle = "filled")

    val titleColor = parseColorOrRole(cfg.optString("titleColor", "")) ?: container.contentColor ?: MaterialTheme.colorScheme.onSurface
    val actionsColor = parseColorOrRole(cfg.optString("actionsColor", "")) ?: titleColor

    val actions = cfg.optJSONArray("actions") ?: JSONArray()

    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,      // sfondo gestito da StyledContainer
        titleContentColor = titleColor,
        actionIconContentColor = actionsColor,
        navigationIconContentColor = actionsColor
    )

    Surface(tonalElevation = tonalElevation, shape = rounded, color = Color.Transparent) {
        StyledContainer(cfg = containerCfg, shapeOverride = rounded) {
            val titleSlot: @Composable () -> Unit = {
                if (subtitle.isBlank()) {
                    Text(title, color = titleColor)
                } else {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge, color = titleColor)
                        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = titleColor.copy(alpha = 0.9f))
                    }
                }
            }

            val actionsSlot: @Composable RowScope.() -> Unit = {
                for (i in 0 until actions.length()) {
                    val a = actions.optJSONObject(i) ?: continue
                    val openPanelId = a.optString("openSidePanelId", "")
                    val effectOverride = a.optString("sidePanelEffect", "")
                    IconButton(onClick = {
                        if (openPanelId.isNotBlank()) {
                            // Propagazione come dispatch standard
                            dispatch("open_side_panel:$openPanelId")
                        } else {
                            dispatch(a.optString("actionId"))
                        }
                    }) {
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

/* =========================================================
 * OVERLAY DESIGNER (palette + azioni + inspector)
 *  - Pannello centrale fisso, scrollabile, semi-trasparente
 *  - NIENTE anteprime: le modifiche sono applicate LIVE e salvate solo su OK
 * ========================================================= */

@Composable
private fun BoxScope.DesignerOverlay(
    screenName: String,
    layout: JSONObject,
    selectedPath: String?,
    setSelectedPath: (String?) -> Unit,
    onLiveChange: () -> Unit,
    onLayoutChange: () -> Unit,           // persist (bozza) su OK o comandi espliciti
    onSaveDraft: () -> Unit,
    onPublish: () -> Unit,
    onReset: () -> Unit,
    avoidTop: Dp,
    avoidBottom: Dp,
    dispatch: (String) -> Unit,
    sidePanelIds: List<String>
) {
    var showInspector by remember { mutableStateOf(false) }
    var showRootInspector by remember { mutableStateOf(false) }

    // Pannello centrale (Root Inspector + Palette)
    Surface(
        modifier = Modifier
  