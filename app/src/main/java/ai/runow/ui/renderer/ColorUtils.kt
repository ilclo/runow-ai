package ai.runow.ui.renderer

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import org.json.JSONObject

/** Risolve un colore da stringa: HEX (#RRGGBB[AA]) o ruolo Material (es. "primary", "surfaceVariant"). */
fun parseColorOrRole(value: String?, colorScheme: ColorScheme = androidx.compose.material3.MaterialTheme.colorScheme): Color?

/** Restituisce un colore “on” leggibile sopra bg (in base a luminanza/contrasto). */
fun bestOnColor(bg: Color, colorScheme: ColorScheme = androidx.compose.material3.MaterialTheme.colorScheme): Color

/** Utility interna: converte "#RRGGBB"/"#AARRGGBB" in Color o null. */
fun colorFromHexOrNull(hex: String?): Color?

/** Eventuale brush gradiente letto da JSON (direction: "horizontal"/"vertical", colors: [...]). */
fun brushFromJsonOrNull(gradient: JSONObject?, colorScheme: ColorScheme = androidx.compose.material3.MaterialTheme.colorScheme): Brush?
