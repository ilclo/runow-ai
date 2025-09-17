package ai.runow.ui.renderer

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

@Composable
fun parseColorOrRole(source: String?): Color? {
    if (source == null || source.isBlank()) return null
    val cs = MaterialTheme.colorScheme
    return when (source.lowercase()) {
        "primary"      -> cs.primary
        "onprimary"    -> cs.onPrimary
        "secondary"    -> cs.secondary
        "onsecondary"  -> cs.onSecondary
        "tertiary"     -> cs.tertiary
        "ontertiary"   -> cs.onTertiary
        "surface"      -> cs.surface
        "onsurface"    -> cs.onSurface
        "background"   -> cs.background
        "onbackground" -> cs.onBackground
        "error"        -> cs.error
        "onerror"      -> cs.onError
        "success"      -> cs.tertiary       // mappatura “di comodo” per ruoli custom
        "warning"      -> cs.secondary
        "info"         -> cs.primary
        else -> runCatching { Color(android.graphics.Color.parseColor(source)) }.getOrNull()
    }
}

fun bestOnColor(bg: Color): Color =
    if (bg.luminance() > 0.5f) Color.Black else Color.White


fun colorFromHex(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { null }
}

fun parseHexColorOrNull(s: String): Color? {
    val hex = s.trim().removePrefix("#")
    return try {
        when (hex.length) {
            6 -> {
                val r = hex.substring(0, 2).toInt(16)
                val g = hex.substring(2, 4).toInt(16)
                val b = hex.substring(4, 6).toInt(16)
                Color(r, g, b)
            }
            8 -> {
                val a = hex.substring(0, 2).toInt(16)
                val r = hex.substring(2, 4).toInt(16)
                val g = hex.substring(4, 6).toInt(16)
                val b = hex.substring(6, 8).toInt(16)
                Color(r, g, b, a)
            }
            else -> null
        }
    } catch (_: Throwable) { null }
}

fun Color.toHex(withAlpha: Boolean = false): String {
    val a = (alpha * 255).toInt().coerceIn(0, 255)
    val r = (red   * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue  * 255).toInt().coerceIn(0, 255)
    return if (withAlpha) String.format("#%02X%02X%02X%02X", a, r, g, b)
    else            String.format("#%02X%02X%02X", r, g, b)
}
