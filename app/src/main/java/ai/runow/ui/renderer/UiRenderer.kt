@file:OptIn(ExperimentalMaterial3Api::class)

package ai.runow.ui.renderer

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.draw.*
// gesture & pointer
// Android
import android.content.Intent

// Compose graphics & text
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput // used with detectVerticalDragGestures
import androidx.compose.ui.unit.IntOffset
// Math helpers
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import androidx.compose.ui.input.pointer.pointerInput
// Animation
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
// Pointer & nested scroll
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
// Layout callbacks
import androidx.compose.ui.layout.onGloballyPositioned
// Text overflow
// Image bitmap utils
// Intent flags usati nell'image picker
// Shape (evita import doppi "RoundedCornerShape")
// (se serve) altre shape:
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
// shape astratta usata in ResolvedContainer
import androidx.compose.foundation.shape.CornerBasedShape
// Intent usato nel picker immagini
// math usato dallo Slider e dallo stepper
// --- Foundation ---
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
// --- Material 3 ---
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
// --- Activity / back / picker ---
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
// --- Android / IO utili ---
import android.net.Uri
import android.graphics.BitmapFactory
// --- Immagini / painter ---
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
// --- JSON & coroutines ---
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
// --- Risorse font del modulo app ---
import ai.runow.R



// --- Composable minimi -------------------------------------------------------
private val DEFAULT_COLOR_SWATCH = listOf(
    "#000000","#333333","#666666","#999999","#CCCCCC","#FFFFFF",
    "#E53935","#FB8C00","#FDD835","#43A047","#1E88E5","#8E24AA"
)

// ---------------------------------------------------------------------
// LabeledField
// ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposedDropdown(
    value: String,
    label: String,
    options: List<String>,
    modifier: Modifier = Modifier,               // <— prima
    onSelect: (String) -> Unit                   // <— ultimo, niente duplicati
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { onSelect(opt); expanded = false }
                )
            }
        }
    }
}
@Composable
private fun roleOrHex(s: String?): Color? {
    val v = s?.trim().orEmpty()
    if (v.isBlank()) return null
    if (v.startsWith("#")) return try { Color(android.graphics.Color.parseColor(v)) } catch (_: Throwable) { null }

    val cs = MaterialTheme.colorScheme
    return when (v) {
        "primary" -> cs.primary
        "onPrimary" -> cs.onPrimary
        "primaryContainer" -> cs.primaryContainer
        "onPrimaryContainer" -> cs.onPrimaryContainer
        "secondary" -> cs.secondary
        "onSecondary" -> cs.onSecondary
        "secondaryContainer" -> cs.secondaryContainer
        "onSecondaryContainer" -> cs.onSecondaryContainer
        "tertiary" -> cs.tertiary
        "onTertiary" -> cs.onTertiary
        "tertiaryContainer" -> cs.tertiaryContainer
        "onTertiaryContainer" -> cs.onTertiaryContainer
        "surface" -> cs.surface
        "onSurface" -> cs.onSurface
        "surfaceVariant" -> cs.surfaceVariant
        "onSurfaceVariant" -> cs.onSurfaceVariant
        "background" -> cs.background
        "onBackground" -> cs.onBackground
        "error" -> cs.error
        "onError" -> cs.onError
        "errorContainer" -> cs.errorContainer
        "onErrorContainer" -> cs.onErrorContainer
        "outline" -> cs.outline
        else -> null
    }
}

@Composable
fun LabeledField(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        content()
    }
}


// ---------------------------------------------------------------------
// SegmentedButtons  (UNICA versione: selected / onSelect)
// ---------------------------------------------------------------------
@Composable
fun SegmentedButtons(
    options: List<String>,
    selected: String,
    modifier: Modifier = Modifier,               // <— prima
    onSelect: (String) -> Unit                   // <— ultimo
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            FilterChip(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                label = { Text(opt) }
            )
        }
    }
}


// ---------------------------------------------------------------------
// ColorRow  — due overload: a) Color, b) hex stringhe
// ---------------------------------------------------------------------
@Composable
fun ColorRow(
    current: String,
    modifier: Modifier = Modifier,
    onPick: (String) -> Unit
) {
    val swatches = listOf(
        "#000000", "#333333", "#666666", "#999999", "#CCCCCC", "#FFFFFF",
        "#E53935", "#8E24AA", "#3949AB", "#1E88E5", "#039BE5", "#00897B",
        "#43A047", "#7CB342", "#FDD835", "#FB8C00"
    )
    Row(modifier.horizontalScroll(rememberScrollState())) {
        swatches.forEach { hex ->
            val c = colorFromHex(hex) ?: Color.Black
            val sel = current.equals(hex, ignoreCase = true)
            Box(
                Modifier
                    .size(28.dp)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(c)
                    .border(
                        2.dp,
                        if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { onPick(hex) }
            )
            Spacer(Modifier.width(6.dp))
        }
    }
}



private fun Color.toHex(withAlpha: Boolean = false): String {
    val a = (alpha * 255).toInt().coerceIn(0, 255)
    val r = (red   * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue  * 255).toInt().coerceIn(0, 255)
    return if (withAlpha) String.format("#%02X%02X%02X%02X", a, r, g, b)
           else            String.format("#%02X%02X%02X", r, g, b)
}


private fun colorFromHex(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { null }
}



// Helpers colore
private fun parseHexColorOrNull(s: String): Color? {
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

private fun fontFamilyFor(name: String?): FontFamily? =
    when (name?.lowercase()?.trim()) {
        null, "", "(default)", "default" -> null
        "sans", "sansserif", "inter", "roboto", "urbanist", "poppins",
        "manrope", "mulish", "rubik", "space_grotesk", "ibm_plex_sans" -> FontFamily.SansSerif
        "serif", "ibm_plex_serif", "noto_serif" -> FontFamily.Serif
        "mono", "monospace", "ibm_plex_mono", "jetbrains_mono" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        else -> null
    }

private val FONT_FAMILY_OPTIONS: List<String> = listOf(
    "(default)",
    "inter", "poppins", "rubik", "manrope", "mulish", "urbanist",
    "space_grotesk", "ibm_plex_sans", "ibm_plex_mono", "jetbrains_mono"
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
        else -> null
    }
}

private val FONT_WEIGHT_OPTIONS: List<Pair<String, FontWeight>> = listOf(
    "Thin" to FontWeight.Thin,
    "ExtraLight" to FontWeight.ExtraLight,
    "Light" to FontWeight.Light,
    "Normal" to FontWeight.Normal,
    "Medium" to FontWeight.Medium,
    "SemiBold" to FontWeight.SemiBold,
    "Bold" to FontWeight.Bold,
    "ExtraBold" to FontWeight.ExtraBold,
    "Black" to FontWeight.Black
)

private fun labelOfWeight(w: FontWeight): String =
    FONT_WEIGHT_OPTIONS.firstOrNull { it.second == w }?.first ?: "Normal"



@Composable
fun SimpleTextInspectorPanel(
    working: org.json.JSONObject,
    onChange: () -> Unit
) {
    // ALIGN
    LabeledField("Allineamento") {
        val alignOptions = listOf("start","center","end")
        var sel by remember { mutableStateOf(working.optString("align","start")) }
        SegmentedButtons(alignOptions, sel, onSelect = {
            sel = it
            working.put("align", it)
            onChange()
        }, modifier = Modifier.fillMaxWidth())
    }

    Spacer(Modifier.height(8.dp))

    // SIZE (sp) — elenco rapido
    LabeledField("Dimensione testo (sp)") {
        val sizes = listOf("(default)","12","13","14","16","18","20","22","24","28","32","36")
        val cur = working.optDouble("textSizeSp", Double.NaN)
        val value = if (cur.isNaN()) "(default)" else cur.toInt().toString()
        ExposedDropdown(
            value = value,
            label = "textSizeSp",
            options = sizes
        ) { pick ->
            if (pick == "(default)") working.remove("textSizeSp")
            else working.put("textSizeSp", pick.toDouble())
            onChange()
        }
    }

    Spacer(Modifier.height(8.dp))

    // WEIGHT
    LabeledField("Peso (fontWeight)") {
        val weights = listOf("(default)","w300","w400","w500","w600","w700","w800","w900")
        val cur = working.optString("fontWeight","")
        val v = if (cur.isBlank()) "(default)" else cur
        ExposedDropdown(
            value = v,
            label = "fontWeight",
            options = weights
        ) { pick ->
            if (pick == "(default)") working.remove("fontWeight")
            else working.put("fontWeight", pick)
            onChange()
        }
    }

    Spacer(Modifier.height(8.dp))

    // FONT FAMILY (extended)
    LabeledField("Font") {
        val cur = working.optString("fontFamily","")
        val v = if (cur.isBlank()) "(default)" else cur
        ExposedDropdown(
            value = v,
            label = "fontFamily",
            options = FONT_FAMILY_OPTIONS
        ) { pick ->
            if (pick == "(default)") working.remove("fontFamily")
            else working.put("fontFamily", pick)
            onChange()
        }
    }

    Spacer(Modifier.height(8.dp))

    // TEXT COLOR
    LabeledField("Colore testo") {
        val current = working.optString("textColor","")
        ColorRow(current = current) { picked ->
            if (picked.isBlank()) working.remove("textColor")
            else working.put("textColor", picked)
            onChange()
        }
    }
}




private fun resolveTextColor(working: JSONObject): Color? =
    parseColorOrRole(working.optString("textColor", ""))

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

@Composable
private fun colorsFallback() = MaterialTheme.colorScheme




@Composable
private fun TextInspectorPanel(working: JSONObject, onChange: () -> Unit) {
    Text("Text – Proprietà", style = MaterialTheme.typography.titleMedium)

    val text = remember { mutableStateOf(working.optString("text","")) }
    OutlinedTextField(
        value = text.value,
        onValueChange = { text.value = it; working.put("text", it); onChange() },
        label = { Text("Contenuto") }
    )

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

    var fontFamily by remem