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
    val keepOpen = act.startsWith("sidepanel:open:")
    if (!keepOpen) onClose()
    if (act.isNotBlank()) dispatch(act)


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
    when (block.optString("type")) {    when (block.optString("type")) {
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

    // --- Intercettori per menu centrale/sidepanel ---
    when (block.optString("type")) {    when (block.optString("type")) {
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

    when (block.optString("type")) {
        "SectionHeader" -> {
            val title = block.optString("title","")
            val subtitle = block.optString("subtitle","")
            val style = mapTextStyle(block.optString("style","titleMedium"))
            val st = applyTextStyleOverrides(block, style)
            val align = mapTextAlign(block.optString("align","start"))
            Column(Modifier.fillMaxWidth()) {
                Text(title, style = st, textAlign = align)
                if (subtitle.isNotBlank())
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, textAlign = align)
            }
        }

        "Spacer" -> {
            Spacer(Modifier.height(Dp(block.optDouble("height", 8.0).toFloat())))
        }

        "Divider" -> Divider()

        "DividerV" -> {
            val h = Dp(block.optDouble("height",24.0).toFloat())
            val th = Dp(block.optDouble("thickness",1.0).toFloat())
            Box(Modifier.height(h).width(th).background(MaterialTheme.colorScheme.outlineVariant))
        }

        "Card" -> {
            val clickAction = block.optString("clickActionId","")
            StyledContainer(block.optJSONObject("container")) {
                ElevatedCard(
                    onClick = { if (clickAction.isNotBlank()) dispatch(clickAction) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val children = block.optJSONArray("blocks") ?: JSONArray()
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 0 until children.length()) {
                            RenderBlock(
                                block = children.getJSONObject(i),
                                dispatch = dispatch,
                                uiState = uiState,
                                designerMode = designerMode,
                                path = "$path/blocks/$i",
                                menus = menus,
                                onSelect = onSelect,
                                onOpenInspector = onOpenInspector
                            )
                        }
                    }
                }
            }
        }

        "Image" -> {
            val src = block.optString("source","")
            val corner = Dp(block.optDouble("corner",12.0).toFloat())
            val h = Dp(block.optDouble("heightDp",160.0).toFloat())
            val scale = when (block.optString("contentScale","fit")) {
                "crop" -> ContentScale.Crop
                "fill" -> ContentScale.FillBounds
                else   -> ContentScale.Fit
            }
            val shape = RoundedCornerShape(corner)
            Box(Modifier.fillMaxWidth().height(h).clip(shape)) {
                when {
                    src.startsWith("res:") -> {
                        val ctx = LocalContext.current
                        val id = ctx.resources.getIdentifier(src.removePrefix("res:"), "drawable", ctx.packageName)
                        if (id != 0) Image(painterResource(id), null, Modifier.fillMaxSize(), scale)
                    }
                    src.startsWith("uri:") || src.startsWith("content:") || src.startsWith("file:") -> {
                        rememberImageBitmapFromUri(src.removePrefix("uri:"))?.let { bmp ->
                            Image(bmp, null, Modifier.fillMaxSize(), scale)
                        }
                    }
                }
            }
        }

        "List" -> {
            val items = block.optJSONArray("items") ?: JSONArray()
            Column(Modifier.fillMaxWidth()) {
                for (i in 0 until items.length()) {
                    val it = items.getJSONObject(i)
                    val title = it.optString("title","")
                    val subtitle = it.optString("subtitle","")
                    val action = it.optString("actionId","")
                    ListItem(
                        headlineContent = { Text(title) },
                        supportingContent = { if (subtitle.isNotBlank()) Text(subtitle) },
                        modifier = Modifier.clickable { if (action.isNotBlank()) dispatch(action) }
                    )
                    Divider()
                }
            }
        }

        "Progress" -> {
            val label = block.optString("label","")
            val v = block.optDouble("value",0.0).toFloat() / 100f
            val show = block.optBoolean("showPercent", true)
            Column {
                if (label.isNotBlank()) Text(label)
                LinearProgressIndicator(progress = v)
                if (show) Text("${(v*100).toInt()}%")
            }
        }

        "Alert" -> {
            val sev = block.optString("severity","info")
            val title = block.optString("title","")
            val msg = block.optString("message","")
            val action = block.optString("actionId","")
            val (bg, fg) = when (sev) {
                "success" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
                "warning" -> Color(0xFFFFF3CD) to Color(0xFF664D03)
                "error"   -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                else      -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleMedium)
                    if (msg.isNotBlank()) Text(msg)
                    if (action.isNotBlank()) TextButton(onClick = { dispatch(action) }) { Text("Azione") }
                }
            }
        }

        "ButtonRow" -> {
            val align = when (block.optString("align","center")) {
                "start" -> Arrangement.Start
                "end" -> Arrangement.End
                "space_between" -> Arrangement.SpaceBetween
                "space_around" -> Arrangement.SpaceAround
                "space_evenly" -> Arrangement.SpaceEvenly
                else -> Arrangement.Center
            }
            val buttons = block.optJSONArray("buttons") ?: JSONArray()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = align) {
                for (i in 0 until buttons.length()) {
                    val b = buttons.getJSONObject(i)
                    val label = b.optString("label","")
                    val action = b.optString("actionId","")
                    when (b.optString("style","text")) {
                        "outlined" -> OutlinedButton(onClick = { if (action.isNotBlank()) dispatch(action) }) { IconText(label, b.optString("icon","")) }
                        "tonal"    -> FilledTonalButton(onClick = { if (action.isNotBlank()) dispatch(action) }) { IconText(label, b.optString("icon","")) }
                        "primary"  -> Button(onClick = { if (action.isNotBlank()) dispatch(action) }) { IconText(label, b.optString("icon","")) }
                        else       -> TextButton(onClick = { if (action.isNotBlank()) dispatch(action) }) { IconText(label, b.optString("icon","")) }
                    }
                }
            }
        }

        "ChipRow" -> {
            val chips = block.optJSONArray("chips") ?: JSONArray()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until chips.length()) {
                    val c = chips.getJSONObject(i)
                    AssistChip(
                        onClick = { /* no-op demo */ },
                        label = { Text(c.optString("label","")) }
                    )
                }
            }
        }

        "Slider" -> {
            val bind = block.optString("bind","value_1")
            val min = block.optDouble("min",0.0).toFloat()
            val max = block.optDouble("max",10.0).toFloat()
            val step = block.optDouble("step",1.0).toFloat()
            val v = remember { mutableStateOf((uiState[bind] as? Float) ?: min) }
            Column {
                Text(block.optString("label",""))
                Slider(
                    value = v.value,
                    onValueChange = {
                        val snapped = ((it - min)/step).toInt() * step + min
                        v.value = snapped.coerceIn(min, max)
                        uiState[bind] = v.value
                    },
                    valueRange = min..max
                )
            }
        }

        "Toggle" -> {
            val bind = block.optString("bind","toggle_1")
            val v = remember { mutableStateOf((uiState[bind] as? Boolean) ?: false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = v.value, onCheckedChange = {
                    v.value = it; uiState[bind] = it
                })
                Spacer(Modifier.width(8.dp))
                Text(block.optString("label","