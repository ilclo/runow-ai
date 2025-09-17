package ai.runow.ui.renderer

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.ColorScheme
import org.json.JSONObject

/** "start"/"center"/"end"/"justify" -> TextAlign. */
fun mapTextAlign(value: String?): TextAlign?

/** Legge l’allineamento da JSON (chiave: "align" o personalizzabile). */
fun readAlign(obj: JSONObject, key: String = "align"): TextAlign?

/** Scrive l’allineamento su JSON. */
fun writeAlign(obj: JSONObject, align: TextAlign, key: String = "align")

/** Applica override tipografici (size/weight/lineHeight/color ecc.) al TextStyle base. */
fun applyTextStyleOverrides(base: TextStyle, cfg: JSONObject, colorScheme: ColorScheme = androidx.compose.material3.MaterialTheme.colorScheme): TextStyle
