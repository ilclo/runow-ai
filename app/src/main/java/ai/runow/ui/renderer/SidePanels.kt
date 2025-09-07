package ai.runow.ui.renderer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

/* ---------------------------------------------------------
 * Dispatch wrapper per side panels
 * --------------------------------------------------------- */
internal fun wrapDispatchForSidePanels(
    openPanelSetter: (String?) -> Unit,
    appDispatch: (String) -> Unit
): (String) -> Unit = { actionId ->
    when {
        actionId.startsWith("sidepanel:open:") -> openPanelSetter(actionId.removePrefix("sidepanel:open:"))
        actionId == "sidepanel:close" -> openPanelSetter(null)
        else -> appDispatch(actionId)
    }
}

/* ---------------------------------------------------------
 * Overlay che rende i pannelli laterali runtime
 * layout.json: "sidePanels": [{ id, title?, side, width, height, container, items, scrimAlpha?, animMs? }]
 * side: "left" | "right" | "top"
 * --------------------------------------------------------- */
@Composable
internal fun RenderSidePanelsOverlay(
    layout: JSONObject,
    openPanelId: String?,
    onClose: () -> Unit,
    dispatch: (String) -> Unit
) {
    val panels = layout.optJSONArray("sidePanels") ?: JSONArray()
    if (openPanelId == null || panels.length() == 0) return

    val panel = (0 until panels.length())
        .asSequence()
        .mapNotNull { panels.optJSONObject(it) }
        .firstOrNull { it.optString("id") == openPanelId }
        ?: return

    val side = panel.optString("side", "left")
    val widthDp = panel.optDouble("width", 320.0).dp
    val heightDp = panel.optDouble("height", 0.0).let { if (it <= 0.0) Dp.Unspecified else it.dp }
    val containerCfg = panel.optJSONObject("container")
    val items = panel.optJSONArray("items") ?: JSONArray()

    // Scrim
    val scrimAlpha = panel.optDouble("scrimAlpha", 0.25).toFloat().coerceIn(0f, 1f)
    val scrimColor = Color.Black.copy(alpha = scrimAlpha)

    // Animazioni
    val animMs = panel.optInt("animMs", 240).coerceIn(120, 600)
    val enter = when (side) {
        "right" -> slideInHorizontally(animationSpec = tween(animMs)) { it } + fadeIn()
        "top"   -> slideInVertically(animationSpec = tween(animMs)) { -it } + fadeIn()
        else    -> slideInHorizontally(animationSpec = tween(animMs)) { -it } + fadeIn()
    }
    val exit = when (side) {
        "right" -> slideOutHorizontally(animationSpec = tween(animMs)) { it } + fadeOut()
        "top"   -> slideOutVertically(animationSpec = tween(animMs)) { -it } + fadeOut()
        else    -> slideOutHorizontally(animationSpec = tween(animMs)) { -it } + fadeOut()
    }

    // Menù raccolti per i blocks del pannello
    val menus = remember(layout) { collectMenus(layout) }

    Box(
        Modifier
            .fillMaxSize()
            .background(color = Color.Transparent)
    ) {
        // SCRIM cliccabile per chiudere
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(animMs)),
            exit = fadeOut(animationSpec = tween(animMs))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(scrimColor)
                    .clickable { onClose() }
            )
        }

        // PANNELLO
        val contentAlign = when (side) {
            "right" -> Alignment.CenterEnd
            "top" -> Alignment.TopCenter
            else -> Alignment.CenterStart
        }
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = contentAlign
        ) {
            AnimatedVisibility(
                visible = true,
                enter = enter,
                exit = exit
            ) {
                PanelContainer(
                    cfg = containerCfg,
                    width = if (side == "top") Dp.Unspecified else widthDp,
                    height = if (side == "top") heightDp else Dp.Unspecified
                ) {
                    // Header opzionale (titolo + close)
                    val title = panel.optString("title", "")
                    if (title.isNotBlank()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = onClose) {
                                NamedIconEx("close", "close")
                            }
                        }
                    }

                    // Contenuto scrollabile: blocks del pannello
                    val scroll = rememberScrollState()
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(scroll)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (i in 0 until items.length()) {
                            val block = items.optJSONObject(i) ?: continue
                            val path = "/sidePanels/${panel.optString("id")}/items/$i"
                            RenderBlock(
                                block = block,
                                dispatch = dispatch,
                                uiState = mutableMapOf(),
                                designerMode = false,
                                path = path,
                                menus = menus
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ---------------------------------------------------------
 * Contenitore pannello (coerente con StyledContainer)
 * --------------------------------------------------------- */
@Composable
private fun PanelContainer(
    cfg: JSONObject?,
    width: Dp,
    height: Dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val radius = (cfg?.optDouble("cornerRadius", 16.0) ?: 16.0).dp
    val borderWidth = (cfg?.optDouble("borderWidth", 1.0) ?: 1.0).dp
    val style = cfg?.optString("style", "filled") ?: "filled"
    val elevation = (cfg?.optDouble("shadowElevation", 8.0) ?: 8.0).dp

    val containerColor = parseColorOrRole(cfg?.optString("containerColor"))
        ?: MaterialTheme.colorScheme.surface
    val borderColor = parseColorOrRole(cfg?.optString("borderColor"))
        ?: MaterialTheme.colorScheme.outline

    val gradientBrush = cfg?.optJSONObject("gradient")?.let { g ->
        val cols = g.optJSONArray("colors")?.let { arr ->
            (0 until arr.length()).mapNotNull { idx -> parseColorOrRole(arr.optString(idx)) }
        } ?: emptyList()
        if (cols.size >= 2) {
            if (g.optString("direction", "vertical") == "horizontal")
                Brush.horizontalGradient(cols)
            else
                Brush.verticalGradient(cols)
        } else null
    }

    val shape = RoundedCornerShape(radius)

    val baseMod = Modifier
        .then(
            when {
                gradientBrush != null -> Modifier.background(gradientBrush, shape)
                style.equals("filled", true) || style.equals("tonal", true) -> Modifier.background(containerColor, shape)
                else -> Modifier
            }
        )
        .then(
            when {
                style.equals("outlined", true) || style.equals("tonal", true) ->
                    Modifier.border(width = borderWidth, color = borderColor, shape = shape)
                style.equals("text", true) -> Modifier
                else -> Modifier
            }
        )
        .clip(shape)
        .widthIn(min = 240.dp)
        .then(if (width != Dp.Unspecified) Modifier.width(width) else Modifier)
        .then(if (height != Dp.Unspecified) Modifier.height(height) else Modifier)

    Surface(
        modifier = baseMod,
        color = Color.Transparent,
        tonalElevation = if (style.equals("tonal", true)) elevation else 0.dp,
        shadowElevation = if (style.equals("filled", true) || style.equals("outlined", true)) elevation else 0.dp,
        shape = shape
    ) {
        Column(content = content)
    }
}