@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ai.runow.ui.renderer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/* =========================
 * ENTRYPOINT
 * ========================= */

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

    // Ricompone anche al variare di tick (anteprima live)
    val menus by remember(layout, tick) { mutableStateOf(collectMenus(layout!!)) }
    var selectedPath by remember(screenName) { mutableStateOf<String?>(null) }

    var overlayHeightPx by remember { mutableStateOf(0) }
    val overlayHeightDp = with(LocalDensity.current) { (overlayHeightPx / density).dp }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = if (designerMode) overlayHeightDp + 32.dp else 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val blocks = layout!!.optJSONArray("blocks") ?: JSONArray()
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
                    onSelect = { p -> selectedPath = p }
                )
            }
        }

        if (designerMode) {
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
				topPadding = scaffoldPadding.calculateTopPadding(),     // <‑‑ NEW
				onOverlayHeight = { overlayHeightPx = it }
			)
        }
    }
}

/* =========================
 * RENDERER
 * ========================= */

@Composable
private fun RenderBlock(
    block: JSONObject,
    dispatch: (String) -> Unit,
    uiState: MutableMap<String, Any>,
    designerMode: Boolean,
    path: String,
    menus: Map<String, JSONArray>,
    onSelect: (String) -> Unit
) {
    val borderSelected = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)

    // wrapper con overlay selezione (in DesignerMode cattura il tap su tutta l'area)
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
                // Overlay "trasparente" per selezionare sempre il blocco
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
				LinearProgressIndicator(
					progress = { value / 100f },
					trackColor = color.copy(alpha = 0.25f),
					color = color,
					modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp))
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
			val height = block.optDouble("heightDp", 160.0).toFloat().dp
			val corner = block.optDouble("corner", 12.0).toFloat().dp
			val scale = when (block.optString("contentScale","fit")) {
				"crop" -> ContentScale.Crop
				else   -> ContentScale.Fit
			}

			// Solo risorse locali: res:<nome>
			val resId = if (source.startsWith("res:"))
				LocalContext.current.resources.getIdentifier(source.removePrefix("res:"), "drawable", LocalContext.current.packageName)
			else 0

			Surface(shape = RoundedCornerShape(corner), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
				if (resId != 0) {
					// Material3 non include coil: mostriamo l’immagine di drawable
					androidx.compose.foundation.Image(
						painter = painterResource(resId),
						contentDescription = null,
						modifier = Modifier.fillMaxWidth().height(height),
						contentScale = scale
					)
				} else {
					// Placeholder
					Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
						Text("Image: ${if (source.isBlank()) "(not set)" else source}", style = MaterialTheme.typography.labelMedium)
					}
				}
			}
		}

        "Card" -> Wrapper {
            val variant = block.optString("variant", "elevated")
            val clickAction = block.optString("clickActionId", "")
            val inner: @Composable () -> Unit = {
                val innerBlocks = block.optJSONArray("blocks") ?: JSONArray()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 0 until innerBlocks.length()) {
                        val b = innerBlocks.optJSONObject(i) ?: continue
                        val p2 = "$path/blocks/$i"
                        RenderBlock(b, dispatch, uiState, designerMode, p2, menus, onSelect)
                    }
                }
            }
            val mod = Modifier
                .fillMaxWidth()
                .then(
                    if (clickAction.isNotBlank() && !designerMode)
                        Modifier.clickable(onClick = { dispatch(clickAction) })
                    else Modifier
                )

            when (variant) {
                "outlined" -> OutlinedCard(mod) { Column(Modifier.padding(12.dp)) { inner() } }
                "filled" -> Card(mod) { Column(Modifier.padding(12.dp)) { inner() } }
                else -> ElevatedCard(mod) { Column(Modifier.padding(12.dp)) { inner() } }
            }
        }

        "Tabs" -> Wrapper {
            val tabs = block.optJSONArray("tabs") ?: JSONArray()
            var idx by remember(path) { mutableStateOf(block.optInt("initialIndex", 0).coerceAtLeast(0)) }
            val count = tabs.length().coerceAtLeast(1)
            if (idx >= count) idx = 0
            val labels = (0 until count).map {
                tabs.optJSONObject(it)?.optString("label", "Tab ${it + 1}") ?: "Tab ${it + 1}"
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
            val tab = tabs.optJSONObject(idx)
            val blocks2 = tab?.optJSONArray("blocks") ?: JSONArray()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (k in 0 until blocks2.length()) {
                    val b = blocks2.optJSONObject(k) ?: continue
                    val p2 = "$path/tabs/$idx/blocks/$k"
                    RenderBlock(b, dispatch, uiState, designerMode, p2, menus, onSelect)
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
                    val corner = btn.optDouble("corner", 20.0).toFloat().dp
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

                    val baseMod = Modifier
                        .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha, rotationZ = rotation)
                        .then(
                            if (!btn.optDouble("heightDp", Double.NaN).isNaN())
                                Modifier.height(btn.optDouble("heightDp", Double.NaN).toFloat().dp)
                            else sizeModifier(sizeKey)
                        )

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
            Column {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    ListItem(
                        headlineContent = {
                            Text(
                                item.optString("title", ""),
                                style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyLarge),
                                color = parseColorOrRole(block.optString("textColor", ""))
                                    ?: LocalContentColor.current
                            )
                        },
                        supportingContent = {
                            val sub = item.optString("subtitle", "")
                            if (sub.isNotBlank()) Text(
                                sub,
                                style = applyTextStyleOverrides(block, MaterialTheme.typography.bodyMedium),
                                color = parseColorOrRole(block.optString("textColor", ""))
                                    ?: LocalContentColor.current
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
            val thick = block.optDouble("thickness", 1.0).toFloat().dp
            val padStart = block.optDouble("padStart", 0.0).toFloat().dp
            val padEnd = block.optDouble("padEnd", 0.0).toFloat().dp
            Divider(modifier = Modifier.padding(start = padStart, end = padEnd), thickness = thick)
        }

        "DividerV" -> {
            val thickness = block.optDouble("thickness", 1.0).toFloat().dp
            val height = block.optDouble("height", 24.0).toFloat().dp
            VerticalDivider(modifier = Modifier.height(height), thickness = thickness)
        }

        "Spacer" -> {
            Spacer(Modifier.height((block.optDouble("height", 8.0)).toFloat().dp))
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

        "Page" -> Wrapper {
            // A high-level page scaffold with optional top bar and bottom nav.
            val title = block.optString("title", "")
            val contentScrollable = block.optBoolean("contentScrollable", true)

            // Top bar config
            val topBarObj = block.optJSONObject("appBar")
            val hasTopBar = topBarObj != null
            val actions = topBarObj?.optJSONArray("actions") ?: JSONArray()

            // Bottom bar config
            val bottomObj = block.optJSONObject("bottomBar")
            val hasBottom = bottomObj != null
            val bottomItems = bottomObj?.optJSONArray("items") ?: JSONArray()
            var selectedBottom by remember { mutableStateOf(0) }

            Scaffold(
                topBar = {
                    if (hasTopBar) {
                        TopAppBar(
                            title = { Text(if (title.isNotBlank()) title else topBarObj.optString("title","")) },
                            actions = {
                                for (i in 0 until actions.length()) {
                                    val a = actions.optJSONObject(i) ?: continue
                                    val ic = a.optString("icon","")
                                    val act = a.optString("actionId","")
                                    IconButton(onClick = { if (act.isNotBlank()) dispatch(act) }) {
                                        NamedIconEx(ic, null)
                                    }
                                }
                            }
                        )
                    }
                },
                bottomBar = {
                    if (hasBottom) {
                        NavigationBar {
                            for (i in 0 until bottomItems.length()) {
                                val it = bottomItems.optJSONObject(i) ?: continue
                                val label = it.optString("label","")
                                val ic = it.optString("icon","")
                                val act = it.optString("actionId","")
                                NavigationBarItem(
                                    selected = selectedBottom == i,
                                    onClick = {
                                        selectedBottom = i
                                        if (act.isNotBlank()) dispatch(act)
                                    },
                                    icon = { NamedIconEx(ic, null) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                }
            ) { paddings ->
                val innerBlocks = block.optJSONArray("content") ?: block.optJSONArray("blocks") ?: JSONArray()
                val colMod = Modifier
                    .fillMaxSize()
                    .padding(paddings)

                if (contentScrollable) {
                    Column(colMod.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (i in 0 until innerBlocks.length()) {
                            val b = innerBlocks.optJSONObject(i) ?: continue
                            val p2 = "$path/content/$i"
                            RenderBlock(b, dispatch, uiState, designerMode, p2, menus, onSelect)
                        }
                    }
                } else {
                    Column(colMod, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (i in 0 until innerBlocks.length()) {
                            val b = innerBlocks.optJSONObject(i) ?: continue
                            val p2 = "$path/content/$i"
                            RenderBlock(b, dispatch, uiState, designerMode, p2, menus, onSelect)
                        }
                    }
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

/* =========================
 * GRID
 * ========================= */

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

/* =========================
 * DESIGNER OVERLAYY
 * ========================= */

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
    topPadding: Dp, // <‑‑ NEW
    onOverlayHeight: (Int) -> Unit
) {
    var showInspector by remember { mutableStateOf(false) }
    val selectedBlock = selectedPath?.let { jsonAtPath(layout, it) as? JSONObject }
    val canMove = selectedPath?.let { getParentAndIndex(layout, it) != null } == true

    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(12.dp)
            .onGloballyPositioned { onOverlayHeight(it.size.height) },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Palette
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
                    val path = insertBlockAndReturnPath(layout, selectedPath, newSpacer(), "after")
                    setSelectedPath(path); onLayoutChange()
                }) { Icon(Icons.Filled.LibraryAdd, null); Spacer(Modifier.width(6.dp)); Text("Spacer") }

                FilledTonalButton(onClick = {
                    val path = insertBlockAndReturnPath(layout, selectedPath, JSONObject().put("type", "Divider"), "after")
                    setSelectedPath(path); onLayoutChange()
                }) { Icon(Icons.Filled.LibraryAdd, null); Spacer(Modifier.width(6.dp)); Text("Divider") }

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
                    val path = insertBlockAndReturnPath(layout, selectedPath, newDividerV(), "after")
                    setSelectedPath(path); onLayoutChange()
                }) { Icon(Icons.Filled.MoreHoriz, null); Spacer(Modifier.width(6.dp)); 
                FilledTonalButton(onClick = {
                    val path = insertBlockAndReturnPath(layout, selectedPath, newPage(), "after")
                    setSelectedPath(path); onLayoutChange()
                }) { Icon(Icons.Filled.Widgets, null); Spacer(Modifier.width(6.dp)); Text("Page") }
Text("DividerV") }
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
                }) { Icon(Icons.Filled.Settings, null); Spacer(Modifier.width(6.dp)); Text("Toggle") }

                FilledTonalButton(onClick = {
                    val path = insertBlockAndReturnPath(layout, selectedPath, newTabs(), "after")
                    setSelectedPath(path); onLayoutChange()
                }) { Icon(Icons.Filled.MoreHoriz, null); Spacer(Modifier.width(6.dp)); Text("Tabs") }

                FilledTonalButton(onClick = {
                    val path = insertBlockAndReturnPath(layout, selectedPath, newMetricsGrid(), "after")
                    setSelectedPath(path); onLayoutChange()
                }) { Icon(Icons.Filled.Widgets, null); Spacer(Modifier.width(6.dp)); Text("MetricsGrid") }
            }
        }

        // Selezione + Azioni salvataggio
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
                        val newPath = moveAndReturnNewPath(layout, selectedPath!!, -1)
                        setSelectedPath(newPath); onLayoutChange()
                    }) { Icon(Icons.Filled.KeyboardArrowUp, null); Spacer(Modifier.width(4.dp)); Text("Su") }

                    OutlinedButton(onClick = {
                        val newPath = moveAndReturnNewPath(layout, selectedPath!!, +1)
                        setSelectedPath(newPath); onLayoutChange()
                    }) { Icon(Icons.Filled.KeyboardArrowDown, null); Spacer(Modifier.width(4.dp)); Text("Giu") }

                    OutlinedButton(onClick = { duplicate(layout, selectedPath!!); onLayoutChange() }) {
                        Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("Duplica")
                    }

                    TextButton(
                        onClick = { remove(layout, selectedPath!!); setSelectedPath(null); onLayoutChange() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(4.dp)); Text("Elimina") }

                    Button(onClick = { showInspector = true }, enabled = selectedBlock != null) {
                        Text("Proprietà…")
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

    // === FULL SCREEN INSPECTOR (no scrim, preview ancorata in alto) ===
	if (showInspector && selectedBlock != null && selectedPath != null) {
		// working: copia del nodo reale; modifichiamo SOLO questa
		val working = remember(selectedPath) { JSONObject(selectedBlock.toString()) }
		var previewTick by remember { mutableStateOf(0) }
		val bumpPreview: () -> Unit = { previewTick++ }

		// Back fisico chiude l'inspector (non l'app)
		BackHandler(enabled = true) { showInspector = false }

		// Overlay a schermo intero, senza scrim, che intercetta i tocchi (non clicchi sotto)
		Box(
			Modifier
				.fillMaxSize()
				.clickable(
					indication = null,
					interactionSource = remember { MutableInteractionSource() }
				) { /* swallow clicks */ }
		) {
			// ANTEPRIMA ANCORATA IN ALTO (sotto la top bar)
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
						// Render della COPIA: niente designerMode, niente dispatch
						RenderBlock(
							block = working,
							dispatch = { /* no-op in anteprima */ },
							uiState = mutableMapOf(), // anteprima non tocca lo stato reale
							designerMode = false,
							path = selectedPath,
							menus = emptyMap(),
							onSelect = {}
						)
					}
				}
			}

			// PANNELLO PROPRIETÀ IN BASSO (fisso), scrollabile
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
						"ButtonRow" -> ButtonRowInspectorPanel(working, onChange = bumpPreview)
						"SectionHeader" -> SectionHeaderInspectorPanel(working, onChange = bumpPreview)
						"Progress" -> ProgressInspectorPanel(working, onChange = bumpPreview)
						"Alert"    -> AlertInspectorPanel(working, onChange = bumpPreview)
						"Image"    -> ImageInspectorPanel(working, onChange = bumpPreview)
						"ChipRow"     -> ChipRowInspectorPanel(working, onChange = bumpPreview)
                        "Slider"      -> SliderInspectorPanel(working, onChange = bumpPreview)
                        "Toggle"      -> ToggleInspectorPanel(working, onChange = bumpPreview)
                        "Tabs"        -> TabsInspectorPanel(working, onChange = bumpPreview)
                        "MetricsGrid" -> MetricsGridInspectorPanel(working, onChange = bumpPreview)
else -> Text("Inspector non ancora implementato per ${working.optString("type")}")
					}
					Spacer(Modifier.height(8.dp))
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						TextButton(onClick = { showInspector = false }) { Text("Annulla") }
						Spacer(Modifier.weight(1f))
						Button(onClick = {
							// Commit: sovrascrivi il nodo reale con la working copy
							replaceAtPath(layout, selectedPath, working)
							showInspector = false
							onLayoutChange()
						}) { Text("OK") }
					}
				}
			}
		}
	}
}

/* =========================
 * FULL SCREEN INSPECTOR SHELL
 * ========================= */

@Composable
private fun FullScreenInspector(
    block: JSONObject,
    path: String,
    onClose: () -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    body: @Composable (JSONObject, () -> Unit) -> Unit,
) {
    // wrapper overlay dentro lo screen (no Dialog => nessun dimming dello sfondo)
    Box(
        Modifier
            .fillMaxSize()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.BottomCenter)
                .shadow(8.dp),
            color = MaterialTheme.colorScheme.surface, // opaco
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                // Header + Preview ancorata
                Surface(tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Proprietà • ${block.optString("type")}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            IconButton(onClick = onClose) {
                                Icon(Icons.Filled.Close, contentDescription = "Chiudi")
                            }
                        }

                        // PREVIEW
                        PreviewOfBlock(block = block)
                    }
                }

                Divider()

                // Corpo proprietà (scrollabile) + bottoni
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    body(block) {} // onLive gestito dai singoli body

                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onCancel) { Text("Annulla") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onApply) { Text("OK") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewOfBlock(block: JSONObject) {
    // Per la preview usiamo RenderBlock con dispatch no-op e uiState vuoto
    val dummyState = remember { mutableMapOf<String, Any>() }
    val menus = remember { emptyMap<String, JSONArray>() }
    val copy = remember(block) { JSONObject(block.toString()) }
    RenderBlock(
        block = copy,
        dispatch = { },
        uiState = dummyState,
        designerMode = false,
        path = "/preview",
        menus = menus,
        onSelect = {}
    )
}

/* =========================
 * INSPECTOR BODIES (solo contenuti, shell già gestita)
 * ========================= */

@Composable
private fun ButtonRowInspectorBody(block: JSONObject, onLive: () -> Unit) {
    val buttons = block.optJSONArray("buttons") ?: JSONArray().also { block.put("buttons", it) }

    Text("ButtonRow – Proprietà riga", style = MaterialTheme.typography.titleMedium)

    var align by remember { mutableStateOf(block.optString("align", "center")) }
    ExposedDropdown(
        value = align,
        label = "align",
        options = listOf("start", "center", "end", "space_between", "space_around", "space_evenly")
    ) { sel ->
        align = sel; block.put("align", sel); onLive()
    }

    Divider()
    Text("Bottoni", style = MaterialTheme.typography.titleMedium)

    for (i in 0 until buttons.length()) {
        val btn = buttons.getJSONObject(i)
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface // opaca
            )
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Bottone ${i + 1}", style = MaterialTheme.typography.labelLarge)
                    Row {
                        IconButton(onClick = { moveInArray(buttons, i, -1); onLive() }) { Icon(Icons.Filled.KeyboardArrowUp, null) }
                        IconButton(onClick = { moveInArray(buttons, i, +1); onLive() }) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                        IconButton(onClick = { removeAt(buttons, i); onLive() }) {
                            Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                val label = remember { mutableStateOf(btn.optString("label", "")) }
                val icon = remember { mutableStateOf(btn.optString("icon", "")) }
                var style by remember { mutableStateOf(btn.optString("style", "primary")) }
                var size by remember { mutableStateOf(btn.optString("size", "md")) } // mapping legacy
                val cornerStr = remember { mutableStateOf(btn.optDouble("corner", 20.0).toString()) }
                var press by remember { mutableStateOf(if (btn.optString("pressEffect", "none") == "scale") "scale" else "none") }
                val action = remember { mutableStateOf(btn.optString("actionId", "")) }
                val customColor = remember { mutableStateOf(btn.optString("customColor", "")) }

                OutlinedTextField(
                    value = label.value,
                    onValueChange = { label.value = it; btn.put("label", it); onLive() },
                    label = { Text("label") }
                )

                IconPickerField(value = icon, label = "icon") { sel ->
                    icon.value = sel; btn.put("icon", sel); onLive()
                }

                ExposedDropdown(
                    value = style, label = "style",
                    options = listOf("primary", "tonal", "outlined", "text")
                ) { style = it; btn.put("style", it); onLive() }

                // Dimensione più granulare (xs/sm/md/lg/xl) mantenendo compatibilità
                ExposedDropdown(
                    value = size, label = "size",
                    options = listOf("xs", "sm", "md", "lg", "xl")
                ) { sel -> size = sel; btn.put("size", sel); onLive() }

                // Corner con step 1.0
                StepperField(label = "corner (dp)", state = cornerStr, step = 1.0) { v ->
                    btn.put("corner", v); onLive()
                }

                // Solo customColor (override completo, con palette)
                NamedColorPickerPlus(
                    current = customColor.value,
                    label = "customColor (palette)", allowRoles = true,
                    onPick = { hex ->
                        customColor.value = hex
                        if (hex.isBlank()) btn.remove("customColor") else btn.put("customColor", hex)
                        onLive()
                    }
                )

                // Press effects estesi (in render è attivo 'scale'; gli altri sono no‑op al momento)
                ExposedDropdown(
                    value = press, label = "pressEffect",
                    options = listOf("none", "scale", "glow", "tilt")
                ) { sel -> press = sel; btn.put("pressEffect", sel); onLive() }

                OutlinedTextField(
                    value = action.value,
                    onValueChange = { action.value = it; btn.put("actionId", it); onLive() },
                    label = { Text("actionId (es. nav:settings)") }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            buttons.put(JSONObject("{\"label\":\"Nuovo\",\"style\":\"text\",\"icon\":\"add\",\"actionId\":\"\"}"))
            onLive()
        }) { Text("+ Aggiungi bottone") }
    }
}

@Composable
private fun SectionHeaderInspectorBody(block: JSONObject, onLive: () -> Unit) {
    val title = remember { mutableStateOf(block.optString("title", "")) }
    val subtitle = remember { mutableStateOf(block.optString("subtitle", "")) }

    var style by remember { mutableStateOf(block.optString("style", "titleMedium")) }
    var align by remember { mutableStateOf(block.optString("align", "start")) }
    val clickAction = remember { mutableStateOf(block.optString("clickActionId", "")) }

    // Tipografia granulare
    val textSize = remember {
        mutableStateOf(
            block.optDouble("textSizeSp", Double.NaN).let { if (it.isNaN()) "" else it.toString() }
        )
    }

    Text("SectionHeader – Proprietà", style = MaterialTheme.typography.titleMedium)

    OutlinedTextField(
        value = title.value,
        onValueChange = { title.value = it; block.put("title", it); onLive() },
        label = { Text("Titolo") }
    )
    OutlinedTextField(
        value = subtitle.value,
        onValueChange = { subtitle.value = it; block.put("subtitle", it); onLive() },
        label = { Text("Sottotitolo (opz.)") }
    )

    ExposedDropdown(
        value = style, label = "style",
        options = listOf(
            "displaySmall", "headlineSmall",
            "titleLarge", "titleMedium", "titleSmall",
            "bodyLarge", "bodyMedium"
        )
    ) { style = it; block.put("style", it); onLive() }

    ExposedDropdown(
        value = align, label = "align",
        options = listOf("start", "center", "end")
    ) { align = it; block.put("align", it); onLive() }

    ExposedDropdown(
        value = if (textSize.value.isBlank()) "(default)" else textSize.value,
        label = "textSize (sp)",
        options = listOf("(default)", "8", "9", "10", "11", "12", "14", "16", "18", "20", "22", "24", "28", "32", "36")
    ) { sel ->
        val v = if (sel == "(default)") "" else sel
        textSize.value = v
        if (v.isBlank()) block.remove("textSizeSp") else block.put("textSizeSp", v.toDouble())
        onLive()
    }

    Divider()
    Text("Azione al tap", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = clickAction.value,
        onValueChange = { clickAction.value = it; block.put("clickActionId", it); onLive() },
        label = { Text("clickActionId (es. nav:settings)") }
    )
}

@Composable
private fun SpacerInspectorBody(block: JSONObject, onLive: () -> Unit) {
    val height = remember { mutableStateOf(block.optDouble("height", 8.0).toString()) }

    Text("Spacer – Proprietà", style = MaterialTheme.typography.titleMedium)
    StepperField("height (dp)", height, 2.0) { v -> block.put("height", v); onLive() }
}

@Composable
private fun DividerInspectorBody(block: JSONObject, onLive: () -> Unit) {
    val thickness = remember { mutableStateOf(block.optDouble("thickness", 1.0).toString()) }
    val padStart = remember { mutableStateOf(block.optDouble("padStart", 0.0).toString()) }
    val padEnd = remember { mutableStateOf(block.optDouble("padEnd", 0.0).toString()) }

    Text("Divider – Proprietà", style = MaterialTheme.typography.titleMedium)
    StepperField("thickness (dp)", thickness, 1.0) { v -> block.put("thickness", v); onLive() }
    StepperField("padStart (dp)", padStart, 2.0) { v -> block.put("padStart", v); onLive() }
    StepperField("padEnd (dp)", padEnd, 2.0) { v -> block.put("padEnd", v); onLive() }
}

@Composable
private fun DividerVInspectorBody(block: JSONObject, onLive: () -> Unit) {
    val thickness = remember { mutableStateOf(block.optDouble("thickness", 1.0).toString()) }
    val height = remember { mutableStateOf(block.optDouble("height", 24.0).toString()) }

    Text("DividerV – Proprietà", style = MaterialTheme.typography.titleMedium)
    StepperField("thickness (dp)", thickness, 1.0) { v -> block.put("thickness", v); onLive() }
    StepperField("height (dp)", height, 2.0) { v -> block.put("height", v); onLive() }
}

@Composable
private fun CardInspectorBody(block: JSONObject, onLive: () -> Unit) {
    var variant by remember { mutableStateOf(block.optString("variant", "elevated")) }
    val action = remember { mutableStateOf(block.optString("clickActionId", "")) }

    Text("Card – Proprietà", style = MaterialTheme.typography.titleMedium)
    ExposedDropdown(
        value = variant, label = "variant",
        options = listOf("elevated", "outlined", "filled")
    ) { variant = it; block.put("variant", it); onLive() }

    OutlinedTextField(
        value = action.value,
        onValueChange = { action.value = it; block.put("clickActionId", it); onLive() },
        label = { Text("clickActionId (es. nav:run)") }
    )
}

@Composable
private fun FabInspectorBody(block: JSONObject, onLive: () -> Unit) {
    val icon = remember { mutableStateOf(block.optString("icon", "play_arrow")) }
    val label = remember { mutableStateOf(block.optString("label", "Start")) }
    var variant by remember { mutableStateOf(block.optString("variant", "regular")) }
    var size by remember { mutableStateOf(block.optString("size", "regular")) }
    val action = remember { mutableStateOf(block.optString("actionId", "start_run")) }

    Text("Fab – Proprietà", style = MaterialTheme.typography.titleMedium)

    IconPickerField(icon, "icon") { sel -> icon.value = sel; block.put("icon", sel); onLive() }
    OutlinedTextField(value = label.value, onValueChange = {
        label.value = it; block.put("label", it); onLive()
    }, label = { Text("label") })

    ExposedDropdown(
        value = variant, label = "variant",
        options = listOf("regular", "extended")
    ) { variant = it; block.put("variant", it); onLive() }

    ExposedDropdown(
        value = size, label = "size",
        options = listOf("small", "regular", "large")
    ) { size = it; block.put("size", it); onLive() }

    OutlinedTextField(
        value = action.value,
        onValueChange = { action.value = it; block.put("actionId", it); onLive() },
        label = { Text("actionId") }
    )
}

@Composable
private fun IconButtonInspectorBody(block: JSONObject, onLive: () -> Unit) {
    val icon = remember { mutableStateOf(block.optString("icon", "more_vert")) }
    val action = remember { mutableStateOf(block.optString("actionId", "")) }
    val openMenuId = remember { mutableStateOf(block.optString("openMenuId", "")) }

    Text("IconButton – Proprietà", style = MaterialTheme.typography.titleMedium)

    IconPickerField(icon, "icon") { sel -> icon.value = sel; block.put("icon", sel); onLive() }

    OutlinedTextField(
        value = action.value,
        onValueChange = { action.value = it; block.put("actionId", it); onLive() },
        label = { Text("actionId (es. nav:settings o open_menu:<id>)") }
    )

    OutlinedTextField(
        value = openMenuId.value,
        onValueChange = { openMenuId.value = it; block.put("openMenuId", it); onLive() },
        label = { Text("openMenuId (se non usi actionId=open_menu:...)") }
    )
}

@Composable
private fun ListInspectorBody(block: JSONObject, onLive: () -> Unit) {
    val textSize = remember {
        mutableStateOf(block.optDouble("textSizeSp", Double.NaN).let { if (it.isNaN()) "" else it.toString() })
    }
    var fontFamily by remember { mutableStateOf(block.optString("fontFamily", "")) }
    var fontWeight by remember { mutableStateOf(block.optString("fontWeight", "")) }
    var textColor by remember { mutableStateOf(block.optString("textColor", "")) }

    Text("List – Proprietà testo", style = MaterialTheme.typography.titleMedium)

    StepperField("textSize (sp)", textSize, 1.0) { v ->
        if (v <= 0.0) block.remove("textSizeSp") else block.put("textSizeSp", v)
        onLive()
    }

    ExposedDropdown(
        value = if (fontFamily.isBlank()) "(default)" else fontFamily,
        label = "fontFamily",
        options = listOf("(default)", "sans", "serif", "monospace", "cursive")
    ) { sel ->
        val v = if (sel == "(default)") "" else sel
        fontFamily = v
        if (v.isBlank()) block.remove("fontFamily") else block.put("fontFamily", v)
        onLive()
    }

    ExposedDropdown(
        value = if (fontWeight.isBlank()) "(default)" else fontWeight,
        label = "fontWeight",
        options = listOf("(default)", "w300", "w400", "w500", "w600", "w700")
    ) { sel ->
        val v = if (sel == "(default)") "" else sel
        fontWeight = v
        if (v.isBlank()) block.remove("fontWeight") else block.put("fontWeight", v)
        onLive()
    }

    NamedColorPickerPlus(
        current = textColor,
        label = "textColor",
        onPick = { hex ->
            textColor = hex
            if (hex.isBlank()) block.remove("textColor") else block.put("textColor", hex)
            onLive()
        }
    )
}

@Composable
private fun ChipRowInspectorBody(block: JSONObject, onLive: () -> Unit) {
    val textSize = remember {
        mutableStateOf(block.optDouble("textSizeSp", Double.NaN).let { if (it.isNaN()) "" else it.toString() })
    }
    var fontFamily by remember { mutableStateOf(block.optString("fontFamily", "")) }
    var fontWeight by remember { mutableStateOf(block.optString("fontWeight", "")) }
    var textColor by remember { mutableStateOf(block.optString("textColor", "")) }

    Text("ChipRow – Proprietà testo", style = MaterialTheme.typography.titleMedium)

    StepperField("textSize (sp)", textSize, 1.0) { v ->
        if (v <= 0.0) block.remove("textSizeSp") else block.put("textSizeSp", v)
        onLive()
    }

    ExposedDropdown(
        value = if (fontFamily.isBlank()) "(default)" else fontFamily,
        label = "fontFamily",
        options = listOf("(default)", "sans", "serif", "monospace", "cursive")
    ) { sel ->
        val v = if (sel == "(default)") "" else sel
        fontFamily = v
        if (v.isBlank()) block.remove("fontFamily") else block.put("fontFamily", v)
        onLive()
    }

    ExposedDropdown(
        value = if (fontWeight.isBlank()) "(default)" else fontWeight,
        label = "fontWeight",
        options = listOf("(default)", "w300", "w400", "w500", "w600", "w700")
    ) { sel ->
        val v = if (sel == "(default)") "" else sel
        fontWeight = v
        if (v.isBlank()) block.remove("fontWeight") else block.put("fontWeight", v)
        onLive()
    }

    NamedColorPickerPlus(
        current = textColor,
        label = "textColor",
        onPick = { hex ->
            textColor = hex
            if (hex.isBlank()) block.remove("textColor") else block.put("textColor", hex)
            onLive()
        }
    )
}

/* =========================
 * HELPERS: mapping, pickers, utils
 * ========================= */

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
    "sm" -> Modifier.height(36.dp)
    "lg" -> Modifier.height(52.dp)
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
    "home", "menu", "close", "more_horiz",
    "directions_run", "directions_walk", "directions_bike",
    "fitness_center", "timer", "timer_off", "watch_later",
    "map", "my_location", "place", "speed",
    "bolt", "local_fire_department", "sports_score"
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
            label = {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current
                )
            },
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
            label = {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current
                )
            },
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
        "directions_run" -> Icons.Filled.DirectionsRun
        "home" -> Icons.Filled.Home
        "menu" -> Icons.Filled.Menu
        "close" -> Icons.Filled.Close
        "more_horiz" -> Icons.Filled.MoreHoriz
        else -> null
    }
    if (image != null) {
        Icon(image, contentDescription)
    } else {
        Text(".")
    }
}

/* ---- Color parsing / pickers ---- */

@Composable
private fun parseColorOrRole(value: String?): Color? {
    if (value.isNullOrBlank()) return null
    val v = value.trim()

    // Hex
    if (v.startsWith("#") && (v.length == 7 || v.length == 9)) {
        return try {
            Color(android.graphics.Color.parseColor(v))
        } catch (_: Exception) {
            null
        }
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

private val NAMED_COLORS = linkedMapOf(
    "Red" to 0xFFE53935,
    "Pink" to 0xFFD81B60,
    "Purple" to 0xFF8E24AA,
    "DeepPurple" to 0xFF5E35B1,
    "Indigo" to 0xFF3949AB,
    "Blue" to 0xFF1E88E5,
    "LightBlue" to 0xFF039BE5,
    "Cyan" to 0xFF00ACC1,
    "Teal" to 0xFF00897B,
    "Green" to 0xFF43A047,
    "LightGreen" to 0xFF7CB342,
    "Lime" to 0xFFC0CA33,
    "Yellow" to 0xFFFDD835,
    "Amber" to 0xFFFFB300,
    "Orange" to 0xFFFB8C00,
    "DeepOrange" to 0xFFF4511E,
    "Brown" to 0xFF6D4C41,
    "BlueGrey" to 0xFF546E7A
)

@Composable
private fun NamedColorPickerPlus(
    currentHexOrEmpty: String,
    label: String,
    onPickHex: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val display = if (currentHexOrEmpty.isBlank()) "(default)" else currentHexOrEmpty

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current)
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            NAMED_COLORS.forEach { (name, argb) ->
                val c = Color(argb)
                DropdownMenuItem(
                    leadingIcon = {
                        Box(Modifier.size(16.dp).background(c, RoundedCornerShape(3.dp)))
                    },
                    text = { Text(name) },
                    onClick = {
                        val hex = "#%06X".format(0xFFFFFF and argb.toInt())
                        onPickHex(hex); expanded = false
                    }
                )
            }
            DropdownMenuItem(text = { Text("(default)") }, onClick = { onPickHex(""); expanded = false })
        }
    }
}

/* ---- Readability helper ---- */

private fun bestOnColor(bg: Color): Color {
    val l = 0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue
    return if (l < 0.5f) Color.White else Color.Black
}

/* =========================
 * JSON utils
 * ========================= */

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

/* ---- Blueprints ---- */

private fun newSectionHeader() = JSONObject(
    """{"type":"SectionHeader","title":"Nuova sezione"}""".trimIndent()
)

private fun newButtonRow() = JSONObject(
    """
    {"type":"ButtonRow","align":"center","buttons":[
      {"label":"Start","style":"primary","icon":"play_arrow","size":"md","tint":"default","shape":"rounded","corner":20,"pressEffect":"scale","actionId":"start_run"},
      {"label":"Pausa","style":"tonal","icon":"pause","size":"md","tint":"default","shape":"rounded","corner":20,"actionId":"pause_run"},
      {"label":"Stop","style":"outlined","icon":"stop","size":"md","tint":"error","shape":"rounded","corner":20,"actionId":"stop_run","confirm":true}
    ]}
    """.trimIndent()
)

private fun newSpacer() = JSONObject("""{"type":"Spacer","height":8}""".trimIndent())

private fun newDividerV() = JSONObject(
    """{"type":"DividerV","thickness":1,"height":24}""".trimIndent()
)

private fun newCard() = JSONObject(
    """
    {"type":"Card","variant":"elevated","clickActionId":"nav:run",
     "blocks":[
       {"type":"SectionHeader","title":"Card esempio","style":"titleSmall","align":"start"},
       {"type":"Divider"}
     ]}
    """.trimIndent()
)

private fun newFab() = JSONObject(
    """{"type":"Fab","icon":"play_arrow","label":"Start","variant":"extended","actionId":"start_run"}""".trimIndent()
)

private fun newIconButton(menuId: String = "more_menu") =
    JSONObject("""{"type":"IconButton","icon":"more_vert","openMenuId":"$menuId"}""".trimIndent())

private fun newMenu(menuId: String = "more_menu") = JSONObject(
    """
    {"type":"Menu","id":"$menuId","items":[
       {"icon":"tune","label":"Layout Lab","actionId":"open_layout_lab"},
       {"icon":"palette","label":"Theme Lab","actionId":"open_theme_lab"},
       {"icon":"settings","label":"Impostazioni","actionId":"nav:settings"}
    ]}
private fun newPage() = JSONObject(
    """
{
  "type": "Page",
  "title": "Nuova pagina",
  "contentScrollable": true,
  "appBar": {
    "title": "Titolo pagina",
    "actions": [ { "icon": "settings", "actionId": "nav:settings" } ]
  },
  "bottomBar": {
    "items": [
      { "label": "Home", "icon": "home", "actionId": "" },
      { "label": "Run",  "icon": "directions_run", "actionId": "" },
      { "label": "Menu", "icon": "menu", "actionId": "" }
    ]
  },
  "content": [
    { "type":"SectionHeader", "title":"Contenuto", "style":"titleMedium" },
    { "type":"Spacer", "height": 8 }
  ]
}
    """.trimIndent()
)

    """.trimIndent()
)

/* =========================
 * TEXT STYLE OVERRIDES
 * ========================= */

private fun applyTextStyleOverrides(node: JSONObject, base: TextStyle): TextStyle {
    var st = base
    val size = node.optDouble("textSizeSp", Double.NaN)
    if (!size.isNaN()) st = st.copy(fontSize = size.sp)

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
@Composable
private fun ButtonRowInspectorPanel(working: JSONObject, onChange: () -> Unit) {
    val buttons = working.optJSONArray("buttons") ?: JSONArray().also { working.put("buttons", it) }

    Text("ButtonRow – Proprietà", style = MaterialTheme.typography.titleMedium)

    var align by remember { mutableStateOf(working.optString("align", "center")) }
    ExposedDropdown(
        value = align,
        label = "align",
        options = listOf("start","center","end","space_between","space_around","space_evenly")
    ) { sel ->
        align = sel
        working.put("align", sel)
        onChange()
    }

    Divider()
    Text("Bottoni", style = MaterialTheme.typography.titleMedium)

    for (i in 0 until buttons.length()) {
        val btn = buttons.getJSONObject(i)
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface // pannello opaco
            )
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Bottone ${i + 1}", style = MaterialTheme.typography.labelLarge)
                    Row {
                        IconButton(onClick = { moveInArray(buttons, i, -1); onChange() }) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
                        }
                        IconButton(onClick = { moveInArray(buttons, i, +1); onChange() }) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                        }
                        IconButton(onClick = { removeAt(buttons, i); onChange() }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                val label = remember { mutableStateOf(btn.optString("label", "")) }
                OutlinedTextField(
                    value = label.value,
                    onValueChange = {
                        label.value = it
                        btn.put("label", it)
                        onChange()
                    },
                    label = { Text("label") }
                )

                val icon = remember { mutableStateOf(btn.optString("icon", "")) }
                IconPickerField(icon, "icon") { sel ->
                    icon.value = sel
                    btn.put("icon", sel)
                    onChange()
                }

                var style by remember { mutableStateOf(btn.optString("style", "primary")) }
                ExposedDropdown(
                    value = style,
                    label = "style",
                    options = listOf("primary","tonal","outlined","text")
                ) {
                    style = it
                    btn.put("style", it)
                    onChange()
                }

                var size by remember { mutableStateOf(btn.optString("size", "md")) }
                ExposedDropdown(
                    value = size,
                    label = "size",
                    options = listOf("xs","sm","md","lg","xl")
                ) {
                    size = it
                    btn.put("size", it)
                    onChange()
                }

                // Corner con step = 1.0
                val cornerStr = remember { mutableStateOf(btn.optDouble("corner", 20.0).toString()) }
                StepperField("corner (dp)", cornerStr, 1.0) { v ->
                    btn.put("corner", v)
                    onChange()
                }

                // Solo customColor con palette
                val customColor = remember { mutableStateOf(btn.optString("customColor", "")) }
                NamedColorPickerPlus(
                    current = customColor.value,
                    label = "customColor (palette)",
                    onPick = { hex ->
                        customColor.value = hex
                        if (hex.isBlank()) btn.remove("customColor") else btn.put("customColor", hex)
                        onChange()
                    }
                )

                var press by remember {
                    mutableStateOf(btn.optString("pressEffect", "none").let { if (it == "scale") "scale" else "none" })
                }
                ExposedDropdown(
                    value = press,
                    label = "pressEffect",
                    options = listOf("none","scale","glow","tilt")
                ) { sel ->
                    press = sel
                    btn.put("pressEffect", sel)
                    onChange()
                }

                val action = remember { mutableStateOf(btn.optString("actionId", "")) }
                OutlinedTextField(
                    value = action.value,
                    onValueChange = {
                        action.value = it
                        btn.put("actionId", it)
                        onChange()
                    },
                    label = { Text("actionId (es. nav:settings)") }
                )
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            buttons.put(JSONObject("{\"label\":\"Nuovo\",\"style\":\"text\",\"icon\":\"add\",\"actionId\":\"\"}"))
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
                        IconButton(onClick = { moveInArray(chips, i, -1); onChange() }) {
                            Icon(Icons.Filled.KeyboardArrowUp, null)
                        }
                        IconButton(onClick = { moveInArray(chips, i, +1); onChange() }) {
                            Icon(Icons.Filled.KeyboardArrowDown, null)
                        }
                        IconButton(onClick = { removeAt(chips, i); onChange() }) {
                            Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error)
                        }
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
    // Opzioni testi (riuso logica già presente altrove)
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

    for (i in 0 until tabs.length()) {
        val tab = tabs.getJSONObject(i)
        ElevatedCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tab ${i+1}", style = MaterialTheme.typography.labelLarge)
                    Row {
                        IconButton(onClick = { moveInArray(tabs, i, -1); onChange() }) {
                            Icon(Icons.Filled.KeyboardArrowUp, null)
                        }
                        IconButton(onClick = { moveInArray(tabs, i, +1); onChange() }) {
                            Icon(Icons.Filled.KeyboardArrowDown, null)
                        }
                        IconButton(onClick = { removeAt(tabs, i); onChange() }) {
                            Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                val label = remember { mutableStateOf(tab.optString("label","Tab ${i+1}")) }
                OutlinedTextField(value = label.value, onValueChange = {
                    label.value = it; tab.put("label", it); onChange()
                }, label = { Text("label") })
                Text(
                    "I contenuti delle tab si modificano dal canvas selezionando i blocchi sotto ogni tab.",
                    style = MaterialTheme.typography.bodySmall
                )
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

    val cols = remember { mutableStateOf(working.optInt("columns",2).toString()) }
    StepperField("columns (1–3)", cols, 1.0) { v ->
        val vv = v.coerceIn(1.0, 3.0)
        working.put("columns", vv.toInt()); onChange()
    }

    val tiles = working.optJSONArray("tiles") ?: JSONArray().also { working.put("tiles", it) }
    for (i in 0 until tiles.length()) {
        val t = tiles.getJSONObject(i)
        ElevatedCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tile ${i+1}", style = MaterialTheme.typography.labelLarge)
                    Row {
                        IconButton(onClick = { moveInArray(tiles, i, -1); onChange() }) {
                            Icon(Icons.Filled.KeyboardArrowUp, null)
                        }
                        IconButton(onClick = { moveInArray(tiles, i, +1); onChange() }) {
                            Icon(Icons.Filled.KeyboardArrowDown, null)
                        }
                        IconButton(onClick = { removeAt(tiles, i); onChange() }) {
                            Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                val label = remember { mutableStateOf(t.optString("label","")) }
                OutlinedTextField(value = label.value, onValueChange = {
                    label.value = it; t.put("label", it); onChange()
                }, label = { Text("label") })
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    Button(onClick = {
        tiles.put(JSONObject("""{"label":"Nuova metrica"}""")); onChange()
    }) { Text("+ Aggiungi tile") }
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
    // ruoli Material + palette esistente
    // se vuoi escludere i ruoli, passa allowRoles = false
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
        onValueChange = {
            title.value = it
            working.put("title", it)
            onChange()
        },
        label = { Text("Titolo") }
    )

    val subtitle = remember { mutableStateOf(working.optString("subtitle","")) }
    OutlinedTextField(
        value = subtitle.value,
        onValueChange = {
            subtitle.value = it
            working.put("subtitle", it)
            onChange()
        },
        label = { Text("Sottotitolo (opz.)") }
    )

    var style by remember { mutableStateOf(working.optString("style","titleMedium")) }
    ExposedDropdown(
        value = style,
        label = "style",
        options = listOf("displaySmall","headlineSmall","titleLarge","titleMedium","titleSmall","bodyLarge","bodyMedium")
    ) {
        style = it
        working.put("style", it)
        onChange()
    }

    var align by remember { mutableStateOf(working.optString("align","start")) }
    ExposedDropdown(
        value = align,
        label = "align",
        options = listOf("start","center","end")
    ) {
        align = it
        working.put("align", it)
        onChange()
    }

    // Text size solo da menu (stessa logica già usata altrove)
    val textSize = remember {
        mutableStateOf(
            working.optDouble("textSizeSp", Double.NaN).let { if (it.isNaN()) "" else it.toString() }
        )
    }
    ExposedDropdown(
        value = if (textSize.value.isBlank()) "(default)" else textSize.value,
        label = "textsize (sp)",
        options = listOf("(default)","8","9","10","11","12","14","16","18","20","22","24","28","32","36")
    ) { sel ->
        val v = if (sel == "(default)") "" else sel
        textSize.value = v
        if (v.isBlank()) working.remove("textSizeSp") else working.put("textSizeSp", v.toDouble())
        onChange()
    }
}

/* ---- Extended palette & color picker with Material roles ---- */
private val NAMED_SWATCHES = linkedMapOf(
    // Neutri
    "White" to 0xFFFFFFFF, "Black" to 0xFF000000,
    "Gray50" to 0xFFFAFAFA, "Gray100" to 0xFFF5F5F5, "Gray200" to 0xFFEEEEEE,
    "Gray300" to 0xFFE0E0E0, "Gray400" to 0xFFBDBDBD, "Gray500" to 0xFF9E9E9E,
    "Gray600" to 0xFF757575, "Gray700" to 0xFF616161, "Gray800" to 0xFF424242, "Gray900" to 0xFF212121,

    // Material-like
    "Red" to 0xFFE53935, "RedDark" to 0xFFC62828, "RedLight" to 0xFFEF5350,
    "Pink" to 0xFFD81B60, "PinkDark" to 0xFFC2185B, "PinkLight" to 0xFFF06292,
    "Purple" to 0xFF8E24AA, "PurpleDark" to 0xFF6A1B9A, "PurpleLight" to 0xFFBA68C8,
    "DeepPurple" to 0xFF5E35B1, "Indigo" to 0xFF3949AB,
    "Blue" to 0xFF1E88E5, "BlueDark" to 0xFF1565C0, "BlueLight" to 0xFF64B5F6,
    "LightBlue" to 0xFF039BE5, "Cyan" to 0xFF00ACC1,
    "Teal" to 0xFF00897B, "TealLight" to 0xFF26A69A,
    "Green" to 0xFF43A047, "GreenDark" to 0xFF2E7D32, "GreenLight" to 0xFF66BB6A,
    "LightGreen" to 0xFF7CB342, "Lime" to 0xFFC0CA33,
    "Yellow" to 0xFFFDD835, "Amber" to 0xFFFFB300,
    "Orange" to 0xFFFB8C00, "DeepOrange" to 0xFFF4511E,
    "Brown" to 0xFF6D4C41, "BlueGrey" to 0xFF546E7A
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
                    "primary","onPrimary"   -> cs.primary
                    "secondary","onSecondary" -> cs.secondary
                    "tertiary","onTertiary" -> cs.tertiary
                    "error","onError"       -> cs.error
                    "surface","onSurface"   -> cs.surface
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
                        val hex = "#%06X".format(0xFFFFFF and argb.toInt())
                        onPick(hex); expanded = false
                    }
                )
            }
            DropdownMenuItem(text = { Text("(default)") }, onClick = { onPick(""); expanded = false })
        }
    }
}

private fun newProgress() = JSONObject(
    """{ "type":"Progress", "label":"Avanzamento", "value": 40, "color": "primary", "showPercent": true }"""
)

private fun newAlert() = JSONObject(
    """{ "type":"Alert", "severity":"info", "title":"Titolo avviso", "message":"Testo dell'avviso", "actionId": "" }"""
)

private fun newImage() = JSONObject(
    """{ "type":"Image", "source":"res:ic_launcher_foreground", "heightDp": 160, "corner": 12, "contentScale":"fit" }"""
)

/* =========================
 * NEW INSPECTOR PANELS
 * ========================= */

@Composable
private fun ChipRowInspectorPanel(working: JSONObject, onChange: () -> Unit) {
    Text("ChipRow – Proprietà", style = MaterialTheme.typography.titleMedium)

    // Stile testo (solo menu a tendina / palette)
    val textSize = remember { mutableStateOf(working.optDouble("textSizeSp", Double.NaN).let { if (it.isNaN()) "" else it.toString() }) }
    var fontFamily by remember { mutableStateOf(working.optString("fontFamily","")) }
    var fontWeight by remember { mutableStateOf(working.optString("fontWeight","")) }
    var textColor by remember { mutableStateOf(working.optString("textColor","")) }

    ExposedDropdown(
        value = if (textSize.value.isBlank()) "(default)" else textSize.value,
        label = "textSize (sp)",
        options = listOf("(default)","8","9","10","11","12","14","16","18","20","22")
    ) { sel ->
        val v = if (sel == "(default)") "" else sel
        textSize.value = v
        if (v.isBlank()) working.remove("textSizeSp") else working.put("textSizeSp", v.toDouble())
        onChange()
    }

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

    NamedColorPickerPlus(current = textColor, label = "textColor") { pick ->
        textColor = pick
        if (pick.isBlank()) working.remove("textColor") else working.put("textColor", pick)
        onChange()
    }

    Divider()
    Text("Chips", style = MaterialTheme.typography.titleMedium)

    val chips = working.optJSONArray("chips") ?: JSONArray().also { working.put("chips", it) }
    for (i in 0 until chips.length()) {
        val c = chips.getJSONObject(i)
        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Chip ${i+1}", style = MaterialTheme.typography.labelLarge)
                    Row {
                        IconButton(onClick = { moveInArray(chips, i, -1); onChange() }) { Icon(Icons.Filled.KeyboardArrowUp, null) }
                        IconButton(onClick = { moveInArray(chips, i, +1); onChange() }) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                        IconButton(onClick = { removeAt(chips, i); onChange() }) { Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error) }
                    }
                }
                val lbl  = remember { mutableStateOf(c.optString("label","")) }
                val bind = remember { mutableStateOf(c.optString("bind", working.optString("bind",""))) }
                val valS = remember { mutableStateOf(c.optString("value","")) }

                OutlinedTextField(lbl.value,  { lbl.value  = it; c.put("label", it); onChange() },  label = { Text("label") })
                OutlinedTextField(bind.value, { bind.value = it; c.put("bind",  it); onChange() },  label = { Text("bind (stato)") })
                OutlinedTextField(valS.value, {
                    valS.value = it
                    if (it.isBlank()) c.remove("value") else c.put("value", it)   // vuoto => multi‑selezione (no chiave 'value')
                    onChange()
                }, label = { Text("value (lascia vuoto per multi)") })
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            val obj = JSONObject().put("label","Nuovo").put("bind","chip_bind")
            chips.put(obj); onChange()
        }) { Text("+ Aggiungi chip") }
    }
}

@Composable
private fun SliderInspectorPanel(working: JSONObject, onChange: () -> Unit) {
    Text("Slider – Proprietà", style = MaterialTheme.typography.titleMedium)

    val label = remember { mutableStateOf(working.optString("label","")) }
    OutlinedTextField(label.value, { label.value = it; working.put("label", it); onChange() }, label = { Text("label") })

    val bind  = remember { mutableStateOf(working.optString("bind","")) }
    OutlinedTextField(bind.value,  { bind.value  = it; working.put("bind", it); onChange() },   label = { Text("bind") })

    val minV  = remember { mutableStateOf(working.optDouble("min", 0.0).toString()) }
    val maxV  = remember { mutableStateOf(working.optDouble("max",10.0).toString()) }
    val stepV = remember { mutableStateOf(working.optDouble("step",1.0).toString()) }
    StepperField("min",  minV,  0.5) { v -> working.put("min",  v); onChange() }
    StepperField("max",  maxV,  0.5) { v -> working.put("max",  v); onChange() }
    StepperField("step", stepV, 0.1) { v -> working.put("step", v); onChange() }

    val unit = remember { mutableStateOf(working.optString("unit","")) }
    OutlinedTextField(unit.value, { unit.value = it; working.put("unit", it); onChange() }, label = { Text("unit (es. min/km)") })
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
    ) { sel ->
        initIdxState.value = sel
        working.put("initialIndex", sel.toInt())
        onChange()
    }

    Divider()
    Text("Tab", style = MaterialTheme.typography.titleMedium)

    for (i in 0 until tabs.length()) {
        val t = tabs.getJSONObject(i)
        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Tab ${i+1}", style = MaterialTheme.typography.labelLarge)
                    Row {
                        IconButton(onClick = { moveInArray(tabs, i, -1); onChange() }) { Icon(Icons.Filled.KeyboardArrowUp, null) }
                        IconButton(onClick = { moveInArray(tabs, i, +1); onChange() }) { Icon(Icons.Filled.KeyboardArrowDown, null) }
                        IconButton(onClick = { removeAt(tabs, i); onChange() }) { Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error) }
                    }
                }
                val lbl = remember { mutableStateOf(t.optString("label","")) }
                OutlinedTextField(lbl.value, { lbl.value = it; t.put("label", it); onChange() }, label = { Text("label") })
                // I contenuti (blocks) si editano dal canvas principale selezionando i blocchi dentro la Tab.
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = {
            val obj = JSONObject().put("label","Nuova").put("blocks", JSONArray())
            tabs.put(obj); onChange()
        }) { Text("+ Aggiungi tab") }
    }
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
    Text("Tiles", style = MaterialTheme.typography.titleMedium)

    val tiles = working.optJSONArray("tiles") ?: JSONArray().also { working.put("tiles", it) }
    for (i in 0 until tiles.length()) {
        val tile = tiles.getJSONObject(i)
        ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { tiles.put(JSONObject().put("label","Nuova")); onChange() }) { Text("+ Aggiungi tile") }
    }
}

/* =========================
 * BLUEPRINTS (nuovi)
 * ========================= */

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

private fun newMetricsGrid() = JSONObject(
    """{"type":"MetricsGrid","columns":2,"tiles":[{"label":"Pace"},{"label":"Heart"}]}""".trimIndent()
)

private fun newSlider() = JSONObject(
    """{"type":"Slider","label":"Pace","bind":"pace","min":3.0,"max":7.0,"step":0.1,"unit":" min/km"}""".trimIndent()
)

private fun newToggle() = JSONObject(
    """{"type":"Toggle","label":"Attiva opzione","bind":"toggle_1"}"""
)
