package ai.runow.ui.renderer

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

/**
 * Interpreta stringhe colore:
 *  - "#RRGGBB" / "#AARRGGBB"
 *  - ruoli Material: primary, onPrimary, secondary, surface, background, error, outline, ecc.
 *  - stringa vuota -> null
 */
@Composable
fun parseColorOrRole(s: String?): Color? {
    if (s.isNullOrBlank()) return null
    val role = s.trim().lowercase()

    // 1) Hex
    parseHexColorOrNull(role)?.let { return it }

    // 2) Ruoli Material
    val cs = MaterialTheme.colorScheme
    return when (role) {
        "primary" -> cs.primary
        "onprimary" -> cs.onPrimary
        "primarycontainer" -> cs.primaryContainer
        "onprimarycontainer" -> cs.onPrimaryContainer

        "secondary" -> cs.secondary
        "onsecondary" -> cs.onSecondary
        "secondarycontainer" -> cs.secondaryContainer
        "onsecondarycontainer" -> cs.onSecondaryContainer

        "tertiary" -> cs.tertiary
        "ontertiary" -> cs.onTertiary
        "tertiarycontainer" -> cs.tertiaryContainer
        "ontertiarycontainer" -> cs.onTertiaryContainer

        "surface" -> cs.surface
        "onsurface" -> cs.onSurface
        "surfacevariant" -> cs.surfaceVariant
        "onsurfacevariant" -> cs.onSurfaceVariant

        "background" -> cs.background
        "onbackground" -> cs.onBackground

        "error" -> cs.error
        "onerror" -> cs.onError
        "errorcontainer" -> cs.errorContainer
        "onerrorcontainer" -> cs.onErrorContainer

        "outline" -> cs.outline
        "outlinevariant" -> cs.outlineVariant
        "inverseonSurface" -> cs.inverseOnSurface
        "inversesurface" -> cs.inverseSurface
        "scrim" -> cs.scrim
        else -> null
    }
}

/** Contrasto semplice: bianco su fondi scuri, nero su fondi chiari. */
fun bestOnColor(bg: Color): Color =
    if (bg.luminance() < 0.5f) Color.White else Color.Black

/** "#RRGGBB" / "#AARRGGBB" -> Color (o null se invalido) */
fun colorFromHex(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { null }
}

/** Parser hex più tollerante (senza #) */
fun parseHexColorOrNull(s: String?): Color? {
    if (s.isNullOrBlank()) return null
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

/** Estensione: Color -> "#RRGGBB" o "#AARRGGBB" */
fun Color.toHex(withAlpha: Boolean = false): String {
    val a = (alpha * 255).toInt().coerceIn(0, 255)
    val r = (red   * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue  * 255).toInt().coerceIn(0, 255)
    return if (withAlpha) String.format("#%02X%02X%02X%02X", a, r, g, b)
    else                  String.format("#%02X%02X%02X", r, g, b)
}
