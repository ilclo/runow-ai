package ai.runow.ui.renderer

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** Mapping robusto string -> TextAlign */
fun mapTextAlign(s: String?): TextAlign = when (s?.lowercase()?.trim()) {
    "start", "left" -> TextAlign.Start
    "center", "centre", "middle" -> TextAlign.Center
    "end", "right" -> TextAlign.End
    "justify", "justified" -> TextAlign.Justify
    else -> TextAlign.Start
}

/** Legge "align" da JSON e ritorna TextAlign */
fun readAlign(obj: JSONObject, default: String = "start"): TextAlign =
    mapTextAlign(obj.optString("align", default))

/** Scrive "align" nel JSON coerente con TextAlign */
fun writeAlign(obj: JSONObject, align: TextAlign) {
    val v = when (align) {
        TextAlign.Start -> "start"
        TextAlign.Center -> "center"
        TextAlign.End -> "end"
        TextAlign.Justify -> "justify"
        else -> "start"
    }
    obj.put("align", v)
}

/** Font family di base senza dipendere da R.font */
private fun fontFamilyFor(name: String?): FontFamily? = when (name?.lowercase()?.trim()) {
    null, "", "(default)", "default" -> null
    "sans", "sansserif", "inter", "roboto", "urbanist", "poppins",
    "manrope", "mulish", "rubik", "space_grotesk", "ibm_plex_sans" -> FontFamily.SansSerif
    "serif", "ibm_plex_serif", "noto_serif", "inknut_antiqua", "playfair" -> FontFamily.Serif
    "mono", "monospace", "ibm_plex_mono", "jetbrains_mono", "space_mono" -> FontFamily.Monospace
    "cursive", "sacramento" -> FontFamily.Cursive
    else -> null
}

/**
 * Applica override (facoltativi) a uno stile base.
 * Supporta: "sizeSp" (Double), "bold" (Boolean), "italic" (Boolean),
 * "weight" (String/Int), "color" (String hex/ruolo), "font" (String).
 */
@Composable
fun applyTextStyleOverrides(base: TextStyle, cfg: JSONObject): TextStyle {
    var s = base

    // dimensione
    val sizeSp = cfg.optDouble("sizeSp", Double.NaN)
    if (!sizeSp.isNaN()) s = s.copy(fontSize = sizeSp.sp)

    // peso o flag "bold"
    val bold = cfg.optBoolean("bold", false)
    val weightStr = cfg.optString("weight", "").lowercase()
    val weight: FontWeight? = when {
        bold -> FontWeight.Bold
        weightStr.startsWith("w") -> weightStr.removePrefix("w").toIntOrNull()?.let { FontWeight(it) }
        weightStr == "light" -> FontWeight.Light
        weightStr == "medium" -> FontWeight.Medium
        weightStr == "semibold" || weightStr == "semi_bold" -> FontWeight.SemiBold
        weightStr == "bold" -> FontWeight.Bold
        weightStr == "black" -> FontWeight.Black
        else -> null
    }
    if (weight != null) s = s.copy(fontWeight = weight)

    // italic
    val italic = cfg.optBoolean("italic", false)
    if (italic) s = s.copy(fontStyle = FontStyle.Italic)

    // font family
    val fontName = cfg.optString("font", "")
    fontFamilyFor(fontName)?.let { s = s.copy(fontFamily = it) }

    // colore
    parseColorOrRole(cfg.optString("color", ""))?.let { s = s.copy(color = it) }

    return s
}
