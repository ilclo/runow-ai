package ai.runow.ui.renderer

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StyledTopBarPinned(
    cfg: JSONObject,
    scrollBehavior: TopAppBarScrollBehavior
) {
    val cs = MaterialTheme.colorScheme
    val style = cfg.optJSONObject("container")?.optString("style", "surface") ?: "surface"
    val corner = cfg.optJSONObject("container")?.optDouble("corner", 0.0)?.toFloat() ?: 0f
    val shapeName = cfg.optJSONObject("container")?.optString("shape", "rounded") ?: "rounded"
    val borderTh = cfg.optJSONObject("container")?.optDouble("borderThicknessDp", 0.0)?.toFloat() ?: 0f
    val bgColor = parseColorOrRole(cfg.optJSONObject("container")?.optString("customColor","")) ?: cs.surface
    val borderCol = parseColorOrRole(cfg.optJSONObject("container")?.optString("borderColor","")) ?: cs.outline
    val onColor = bestOnColor(bgColor) ?: cs.onSurface
    val title = cfg.optString("title", "topbar")
    val subtitle = cfg.optString("subtitle", "")
    val barColors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        titleContentColor = onColor,
        navigationIconContentColor = onColor,
        actionIconContentColor = onColor
    )
    val shape: Shape = when (shapeName.lowercase()) {
        "cut"  -> CutCornerShape(corner.dp)
        "pill" -> RoundedCornerShape(percent = 50)
        else   -> RoundedCornerShape(corner.dp)
    }
    Surface(
        color = bgColor,
        contentColor = onColor,
        shape = shape,
        border = if (borderTh > 0f) BorderStroke(borderTh.dp, borderCol) else null,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        TopAppBar(
            title = {
                if (subtitle.isBlank()) {
                    Text(title, color = onColor, style = MaterialTheme.typography.titleLarge)
                } else {
                    Column {
                        Text(title, color = onColor, style = MaterialTheme.typography.titleLarge)
                        Text(subtitle, color = onColor.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
                    }
                }
            },
            colors = barColors,
            scrollBehavior = scrollBehavior
        )
    }
}

// Helpers colore locali (privati per evitare collisioni)
private fun bestOnColor(bg: Color): Color {
    // stima semplice: testo chiaro su sfondi scuri e viceversa
    val y = 0.2126 * bg.red + 0.7152 * bg.green + 0.0722 * bg.blue
    return if (y < 0.5) Color.White else Color.Black
}
private fun parseColorOrRole(raw: String?): Color? {
    if (raw.isNullOrBlank()) return null
    return if (raw.startsWith("#")) runCatching { Color(android.graphics.Color.parseColor(raw)) }.getOrNull()
    else when (raw) {
        "primary" -> MaterialTheme.colorScheme.primary
        "surface" -> MaterialTheme.colorScheme.surface
        "tertiary"-> MaterialTheme.colorScheme.tertiary
        "onSurface" -> MaterialTheme.colorScheme.onSurface
        else -> null
    }
}
