@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ai.runow.ui.renderer

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var layout by remember(screenName) { mutableStateOf(UiLoader.loadLayout(ctx, screenName)) }
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
    val overlayHeightDp = with(LocalDensity.current) { (overlayHeightPx / density).dp }

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
                onPublish = { UiLoader.saveDraft(ctx, screenName, layout!!); UiLoader.publish(ctx, screenName) },
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

    Scaffold(
        topBar = {
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
        },
        bottomBar = {
            if (bottomButtons.length() > 0) {
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

        val host = @Composable {
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
    onOpenRootInspector: () -> Unit
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
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 12.dp, end = 12.dp, top = topPadding + 8.dp)
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
        val onChange = { dummyTick++ }

        BackHandler(enabled = true) { showRootInspector = false }

		Surface(
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.fillMaxWidth()
				.fillMaxHeight(0.6f),
			shape = RoundedCornerShape(topStart = Dp(16f), topEnd = Dp(16f)),
			tonalElevation = 8.dp
		) {
			Column(
				Modifier
					.fillMaxSize()
					.verticalScroll(rememberScrollState())
					.padding(Dp(16f)),
				verticalArrangement = Arrangement.spacedBy(Dp(12f))
			) {
				RootInspectorPanel(working, onChange)
				Spacer(Modifier.height(Dp(8f)))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showRootInspector = false }) { Text("Annulla") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        // Commit nel layout originale
                        val keys = listOf("topTitle","topActions","bottomButtons","fab","scroll")
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
            Text(block.optString("title", ""), style = MaterialTheme.typography.titleLarge)
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

        "IconButton" -> Wrapper {
            val iconName = block.optString("icon", "more_vert")
            val openMenuId = block.optString("openMenuId", "")
            val actionId = block.optString("actionId", "")
            var expanded by remember { mutableStateOf(false) }

            Box {
                IconButton(onClick = {
                    if (openMenuId.isNotBlank() || actionId.startsWith("open_menu:")) {
                        expanded = true
                    } else if (actionId.isNotBlank()) {
                        dispatch(actionId)
                    }
                }) {
                    NamedIconEx(iconName, null)
                }

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
                // Compatibile con versioni Material3 dove 'progress' è Float (non lambda)
                LinearProgressIndicator(
                    progress = value / 100f,   // ✅ Float, non { value / 100f }
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

        "Card" -> {
            val variant = block.optString("variant","elevated")
            val clickAction = block.optString("clickActionId","")

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

            val baseMod = Modifier
                .fillMaxWidth()
                .then(if (clickAction.isNotBlank() && !designerMode) Modifier.clickable { dispatch(clickAction) } else Modifier)

            ContainerOverlayGear(designerMode, path, onOpenInspector) {
                when (variant) {
                    "outlined" -> OutlinedCard(baseMod) { Column(Modifier.padding(12.dp)) { innerContent() } }
                    "filled"   -> Card(baseMod)        { Column(Modifier.padding(12.dp)) { innerContent() } }
                    else       -> ElevatedCard(baseMod){ Column(Modifier.padding(12.dp)) { innerContent() } }
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
                        RenderBlock(b, dispatch, uiState, designerMode, p2, menus, onSelect, onOpenInspector)
                    }
                }
            }
        }

        "SectionHeader" -> Wrapper {
            val style = mapTextStyle(block.optString("style", "titleMedium"))
            val align = mapTextAlign(block.optString("align", "start"))
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
                    val scale by animateFloatAsState(
                        targetValue = if (pressKey == "scale" && pressed) 0.96f else 1f,
                        label = "btnScale"
                    )
                    val alpha by animateFloatAsState(
                        targetValue = if (pressKey == "alpha" && pressed) 0.6f else 1f,
                        label = "btnAlpha"
                    )
                    val rotation by animateFloatAsState(
                        targetValue = if (pressKey == "rotate" && pressed) -2f else 0f,
                        label = "btnRot"
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
            val align = mapTextAlign(block.optString("align", "start"))
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
                                textAlign = align,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        supportingContent = {
                            val sub = item.optString("subtitle", "")
                            if (sub.isNotBlank()) Text(
                                sub,
                                style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyMedium),
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

        "Carousel" -> Wrapper {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Carousel (placeholder)", style = MaterialTheme.typography.titleSmall)
                    Text("Le immagini saranno gestite in una fase successiva.")
                }
            }
        }

        "Fab" -> Wrapper {
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
            VerticalDivider(modifier = Modifier.height(height), thickness = thickness)
        }

        "Spacer" -> {
            Spacer(Modifier.height(Dp(block.optDouble("height", 8.0).toFloat())))
        }

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
 * ROOT INSPECTOR PANEL
 * ========================================================= */

@Composable
private fun RootInspectorPanel(working: JSONObject, onChange: () -> Unit) {
    Text("Layout (root) – Proprietà", style = MaterialTheme.typography.titleMedium)

    // Scroll on/off
    var scroll by remember { mutableStateOf(working.optBoolean("scroll", true)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = scroll, onCheckedChange = {
            scroll = it; working.put("scroll", it); onChange()
        })
        Spacer(Modifier.width(8.dp)); Text("Contenuto scrollabile")
    }

    Divider(); Text("Top App Bar", style = MaterialTheme.typography.titleSmall)
    val title = remember { mutableStateOf(working.optString("topTitle","")) }
    OutlinedTextField(
        value = title.value,
        onValueChange = { title.value = it; working.put("topTitle", it); onChange() },
        label = { Text("title") }
    )

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
    Button(onClick = {
        bottom.put(JSONObject("""{"label":"Azione","actionId":""}""")); onChange()
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
    Text("Bottoni", style = MaterialTheme.typography.titleMedium)

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

                var size by remember { mutableStateOf(btn.optString("size", "md")) }
                ExposedDropdown(
                    value = size, label = "size",
                    options = listOf("xs","sm","md","lg","xl")
                ) { sel -> size = sel; btn.put("size", sel); onChange() }

                var heightStr by remember {
                    mutableStateOf(btn.optDouble("heightDp", Double.NaN).let { if (it.isNaN()) "(auto)" else it.toInt().toString() })
                }
                ExposedDropdown(
                    value = heightStr, label = "height (dp)",
                    options = listOf("(auto)","32","36","40","44","48","52","56","64")
                ) { sel ->
                    heightStr = sel
                    if (sel == "(auto)") btn.remove("heightDp") else btn.put("heightDp", sel.toDouble())
                    onChange()
                }

                var widthStr by remember {
                    mutableStateOf(btn.optDouble("widthDp", Double.NaN).let { if (it.isNaN()) "(auto)" else it.toInt().toString() })
                }
                ExposedDropdown(
                    value = widthStr, label = "width (dp)",
                    options = listOf("(auto)","72","96","120","160","200")
                ) { sel ->
                    widthStr = sel
                    if (sel == "(auto)") btn.remove("widthDp") else btn.put("widthDp", sel.toDouble())
                    onChange()
                }

                val cornerStr = remember { mutableStateOf(btn.optDouble("corner", 20.0).toString()) }
                StepperField("corner (dp)", cornerStr, 1.0) { v -> btn.put("corner", v); onChange() }

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
                    options = listOf("none","scale")
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

    val source = remember { mutableStateOf(working.optString("source","")) }
    OutlinedTextField(value = source.value, onValueChange = {
        source.value = it; working.put("source", it); onChange()
    }, label = { Text("source (es. res:ic_launcher_foreground)") })

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
        options = listOf("fit","crop")
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
    NamedColorPickerPlus(current = textColor.value, label = "textColor") { hex ->
        textColor.value = hex
        if (hex.isBlank()) working.remove("textColor") else working.put("textColor", hex)
        onChange()
    }
}

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
        options = listOf("(default)","sans","serif","monospace","cursive")
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
        options = listOf("(default)","w300","w400","w500","w600","w700")
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
        else -> null
    }
    if (image != null) Icon(image, contentDescription) else Text(".")
}

/* ---- Color parsing / pickers ---- */

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
        "onSurface" -> cs.onSurface
        else -> null
    }
}

private val NAMED_SWATCHES = linkedMapOf(
    // Neutri
    "White" to 0xFFFFFFFF.toInt(),
    "Black" to 0xFF000000.toInt(),
    "Gray50" to 0xFFFAFAFA.toInt(), "Gray100" to 0xFFF5F5F5.toInt(), "Gray200" to 0xFFEEEEEE.toInt(),
    "Gray300" to 0xFFE0E0E0.toInt(), "Gray400" to 0xFFBDBDBD.toInt(), "Gray500" to 0xFF9E9E9E.toInt(),
    "Gray600" to 0xFF757575.toInt(), "Gray700" to 0xFF616161.toInt(), "Gray800" to 0xFF424242.toInt(), "Gray900" to 0xFF212121.toInt(),

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

private fun applyTextStyleOverrides(node: JSONObject, base: TextStyle): TextStyle {
    var st = base

    // dimensione testo: costruttore stabile (niente .sp)
    val size = node.optDouble("textSizeSp", Double.NaN)
    if (!size.isNaN()) {
        st = st.copy(fontSize = TextUnit(size.toFloat(), TextUnitType.Sp))
    }

    // peso
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

    // famiglia
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
