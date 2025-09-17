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




// ----------------------------------------------------------------------------------------




private fun fontFamilyFor(name: String?): FontFamily? =
    when (name?.lowercase()?.trim()) {
        null, "", "(default)", "default" -> null
        "sans", "sansserif", "inter", "roboto", "urbanist", "poppins",
        "manrope", "mulish", "rubik", "space_grotesk", "ibm_plex_sans" -> FontFamily.SansSerif
        "serif", "ibm_plex_serif", "noto_serif", "inknut_antiqua", "playfair" -> FontFamily.Serif
        "mono", "monospace", "ibm_plex_mono", "jetbrains_mono", "space_mono" -> FontFamily.Monospace
        "cursive", "sacramento" -> FontFamily.Cursive
        else -> null
    }

private val FONT_FAMILY_OPTIONS: List<String> = listOf(
    "(default)",
    "inter", "poppins", "rubik", "manrope", "mulish", "urbanist",
    "space_grotesk", "ibm_plex_sans", "ibm_plex_mono", "jetbrains_mono",
    "inknut_antiqua", "playfair", "sacramento", "space_mono"
)

private fun fontFamilyFromName(name: String?): FontFamily? {
    if (name.isNullOrBlank() || name == "(default)") return null
    return when (name) {
        "inter" -> FontFamily(
            Font(R.font.inter_regular, FontWeight.Normal, FontStyle.Normal),
            Font(R.font.inter_medium,  FontWeight.Medium, FontStyle.Normal),
            Font(R.font.inter_semibold,FontWeight.SemiBold, FontStyle.Normal),
            Font(R.font.inter_bold,    FontWeight.Bold,   FontStyle.Normal),
            Font(R.font.inter_italic,  FontWeight.Normal, FontStyle.Italic)
        )
        "poppins" -> FontFamily(
            Font(R.font.poppins_regular, FontWeight.Normal),
            Font(R.font.poppins_medium,  FontWeight.Medium),
            Font(R.font.poppins_semibold,FontWeight.SemiBold),
            Font(R.font.poppins_bold,    FontWeight.Bold),
            Font(R.font.poppins_italic,  FontWeight.Normal, FontStyle.Italic)
        )
        "rubik" -> FontFamily(
            Font(R.font.rubik_regular, FontWeight.Normal),
            Font(R.font.rubik_medium,  FontWeight.Medium),
            Font(R.font.rubik_semibold,FontWeight.SemiBold),
            Font(R.font.rubik_bold,    FontWeight.Bold),
            Font(R.font.rubik_italic,  FontWeight.Normal, FontStyle.Italic)
        )
        "manrope" -> FontFamily(
            Font(R.font.manrope_regular, FontWeight.Normal),
            Font(R.font.manrope_medium,  FontWeight.Medium),
            Font(R.font.manrope_semibold,FontWeight.SemiBold),
            Font(R.font.manrope_bold,    FontWeight.Bold)
        )
        "mulish" -> FontFamily(
            Font(R.font.mulish_regular, FontWeight.Normal),
            Font(R.font.mulish_medium,  FontWeight.Medium),
            Font(R.font.mulish_semibold,FontWeight.SemiBold),
            Font(R.font.mulish_bold,    FontWeight.Bold),
            Font(R.font.mulish_italic,  FontWeight.Normal, FontStyle.Italic)
        )
        "urbanist" -> FontFamily(
            Font(R.font.urbanist_regular, FontWeight.Normal),
            Font(R.font.urbanist_medium,  FontWeight.Medium),
            Font(R.font.urbanist_semibold,FontWeight.SemiBold),
            Font(R.font.urbanist_bold,    FontWeight.Bold),
            Font(R.font.urbanist_italic,  FontWeight.Normal, FontStyle.Italic)
        )
        "space_grotesk" -> FontFamily(
            Font(R.font.space_grotesk_regular, FontWeight.Normal),
            Font(R.font.space_grotesk_medium,  FontWeight.Medium),
            Font(R.font.space_grotesk_semibold,FontWeight.SemiBold),
            Font(R.font.space_grotesk_bold,    FontWeight.Bold)
        )
        "ibm_plex_sans" -> FontFamily(
            Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
            Font(R.font.ibm_plex_sans_medium,  FontWeight.Medium),
            Font(R.font.ibm_plex_sans_semibold,FontWeight.SemiBold),
            Font(R.font.ibm_plex_sans_bold,    FontWeight.Bold),
            Font(R.font.ibm_plex_sans_italic,  FontWeight.Normal, FontStyle.Italic)
        )
        "ibm_plex_mono" -> FontFamily(
            Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
            Font(R.font.ibm_plex_mono_medium,  FontWeight.Medium),
            Font(R.font.ibm_plex_mono_bold,    FontWeight.Bold),
            Font(R.font.ibm_plex_mono_italic,  FontWeight.Normal, FontStyle.Italic)
        )
        "jetbrains_mono" -> FontFamily(
            Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
            Font(R.font.jetbrains_mono_medium,  FontWeight.Medium),
            Font(R.font.jetbrains_mono_bold,    FontWeight.Bold),
            Font(R.font.jetbrains_mono_italic,  FontWeight.Normal, FontStyle.Italic)
        )
        "inknut_antiqua" -> FontFamily(
            Font(R.font.inknut_antiqua_regular, FontWeight.Normal),
            Font(R.font.inknut_antiqua_medium,  FontWeight.Medium),
            Font(R.font.inknut_antiqua_semibold, FontWeight.SemiBold),
            Font(R.font.inknut_antiqua_bold,    FontWeight.Bold)
        )

        "playfair" -> FontFamily(
            Font(R.font.playfair_regular,        FontWeight.Normal),
// se presenti:
            Font(R.font.playfair_italic,         FontWeight.Normal, FontStyle.Italic),
            Font(R.font.playfair_semibold,       FontWeight.SemiBold),
            Font(R.font.playfair_semibold_italic,FontWeight.SemiBold, FontStyle.Italic),
            Font(R.font.playfair_bold,           FontWeight.Bold),
            Font(R.font.playfair_bold_italic,    FontWeight.Bold, FontStyle.Italic)
        )

        "sacramento" -> FontFamily(
// normalmente ha solo Regular
            Font(R.font.sacramento_regular, FontWeight.Normal)
        )

        "space_mono" -> FontFamily(
            Font(R.font.space_mono_regular,     FontWeight.Normal),
            Font(R.font.space_mono_italic,      FontWeight.Normal, FontStyle.Italic),
            Font(R.font.space_mono_bold,        FontWeight.Bold),
            Font(R.font.space_mono_bold_italic, FontWeight.Bold,   FontStyle.Italic)
        )

        else -> null
    }
}

private fun parseFontWeight(v: String?): FontWeight? = when (v?.lowercase()) {
    "w100" -> FontWeight.W100
    "w200" -> FontWeight.W200
    "w300" -> FontWeight.W300
    "w400" -> FontWeight.W400
    "w500" -> FontWeight.W500
    "w600" -> FontWeight.W600
    "w700" -> FontWeight.W700
    "w800" -> FontWeight.W800
    "w900" -> FontWeight.W900
    else   -> null
}

private fun labelOfWeight(w: FontWeight): String =
    FONT_WEIGHT_OPTIONS.firstOrNull { it.second == w }?.first ?: "Normal"
