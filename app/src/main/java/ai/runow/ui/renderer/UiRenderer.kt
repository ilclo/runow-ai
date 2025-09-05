@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.ui.unit.ExperimentalUnitApi::class
)

package ai.runow.ui.renderer

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CompositionLocalProvider
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilledTonalButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.zIndex
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
 * TOP BAR ESTETICA
 * ========================================================= */
@Composable
private fun RenderTopBar(
    cfg: JSONObject,
    dispatch: (String) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior?
) {
    val variant = cfg.optString("variant", "small") // small | center | medium | large
    val rounded = RoundedCornerShape(
        bottomStart = Dp(cfg.optDouble("roundedBottomStart", 0.0).toFloat()),
        bottomEnd   = Dp(cfg.optDouble("roundedBottomEnd",   0.0).toFloat())
    )
    val tonalElevation = Dp(cfg.optDouble("tonalElevation", 0.0).toFloat())
    val opacity = cfg.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f)

    val brush = brushFromJson(cfg.optJSONObject("gradient"))
    val containerColor = parseColorOrRole(cfg.optString("containerColor","surface")) ?: MaterialTheme.colorScheme.surface
    val titleColor = parseColorOrRole(cfg.optString("titleColor","onSurface")) ?: MaterialTheme.colorScheme.onSurface
    val actionsColor = parseColorOrRole(cfg.optString("actionsColor","onSurface")) ?: MaterialTheme.colorScheme.onSurface

    val title = cfg.optString("title","")
    val subtitle = cfg.optString("subtitle","")
    val actions = cfg.optJSONArray("actions") ?: JSONArray()

    Surface(
        tonalElevation = tonalElevation,
        shape = rounded,
        modifier = Modifier.graphicsLayer(alpha = opacity)
    ) {
        val bgMod = if (brush != null) Modifier.background(brush) else Modifier.background(containerColor)
        Box(bgMod) {
            val colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = titleColor,
                actionIconContentColor = actionsColor,
                navigationIconContentColor = actionsColor
            )
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
                    title = { Text(if (subtitle.isBlank()) title else "$title\n$subtitle") },
                    actions = actionsSlot,
                    colors = colors,
                    scrollBehavior = scrollBehavior
                )
                "medium" -> MediumTopAppBar(
                    title = {
                        Column {
                            Text(title)
                            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    actions = actionsSlot,
                    colors = colors,
                    scrollBehavior = scrollBehavior
                )
                "large" -> LargeTopAppBar(
                    title = {
                        Column {
                            Text(title)
                            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    actions = actionsSlot,
                    colors = colors,
                    scrollBehavior = scrollBehavior
                )
                else -> SmallTopAppBar(
                    title = {
                        Column {
                            Text(title)
                            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    actions = actionsSlot,
                    colors = colors,
                    scrollBehavior = scrollBehavior
                )
            }
        }
    }
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

    // Menù raccolti dal layout + selezione corrente
    val menus by remember(layout, tick) { mutableStateOf(collectMenus(layout!!)) }
    var selectedPath by remember(screenName) { mutableStateOf<String?>(null) }

    // Stato barra designer in basso (per lasciare spazio ai contenuti)
    var overlayHeightPx by remember { mutableStateOf(0) }
    val overlayHeightDp = with(LocalDensity.current) { overlayHeightPx.toDp() }

    // Modalità designer persistente per schermata
    var designMode by rememberSaveable(screenName) { mutableStateOf(designerMode) }

    Box(Modifier.fillMaxSize()) {
        // ====== CONTENUTO con Scaffold di ROOT ======
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
                onOpenRootInspector = { /* gestito sotto */ }
            )
        }

        // ====== LEVETTA LATERALE: DESIGNER ↔ ANTEPRIMA ======
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
    val fab = layout.optJSONObject("fab")
    val scroll = layout.optBoolean("scroll", true)

    val pageBg = layout.optJSONObject("pageBackground")
    val topBarConf = layout.optJSONObject("topBar")
    val bottomBarConf = layout.optJSONObject("bottomBar")

    val topScrollBehavior = when (topBarConf?.optString("scroll", "none")) {
        "pinned" -> TopAppBarDefaults.pinnedScrollBehavior()
        "enterAlways" -> TopAppBarDefaults.enterAlwaysScrollBehavior()
        "exitUntilCollapsed" -> TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        else -> null
    }

    Box(Modifier.fillMaxSize()) {
        // Background pagina prima dello Scaffold
        pageBg?.let { RenderPageBackground(it) }

        Scaffold(
            modifier = if (topScrollBehavior != null)
                Modifier.nestedScroll(topScrollBehavior.nestedScrollConnection)
            else Modifier,
            containerColor = Color.Transparent,

            topBar = {
                if (topBarConf != null) {
                    RenderTopBar(topBarConf, dispatch, topScrollBehavior)
                } else {
                    // Fallback legacy: topTitle/topActions
                    if (title.isNotBlank() || topActions.length() > 0) {
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
                }
            },

            bottomBar = {
                when {
                    bottomBarConf != null -> RenderBottomBar(bottomBarConf, bottomButtons, dispatch)
                    bottomButtons.length() > 0 -> {
                        // Fallback legacy (come prima)
                        Surface(tonalElevation = 3.dp) {
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
                }
