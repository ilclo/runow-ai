package ai.runow.ui.renderer

import org.json.JSONArray
import org.json.JSONObject

/**
 * Adattatore Core‑8 -> legacy JSON del renderer attuale.
 *
 * Convenzioni:
 * - La "pagina Core8" è una JSONArray di blocchi.
 * - Un blocco con type = "topbar" viene "alzato" a layout.topBar.
 * - Tutti gli altri blocchi vengono mappati e messi in layout.blocks.
 */
object Core8Adapter {

    /** Converte una pagina Core‑8 (JSONArray di blocchi) nel layout legacy atteso dal renderer. */
    fun pageToLegacy(page: JSONArray): JSONObject {
        val layout = JSONObject()
        val blocks = JSONArray()

        for (i in 0 until page.length()) {
            val b = page.optJSONObject(i) ?: continue
            when (b.optString("type").lowercase()) {
                "topbar" -> layout.put("topBar", mapTopBar(b))
                else     -> blocks.put(mapBlock(b))
            }
        }

        // NB: il tuo renderer accetta "blocks" a root oppure page.blocks.
        // Qui scegliamo root.blocks per semplicità; se preferisci page.blocks, sostituisci con:
        // layout.put("page", JSONObject().put("blocks", blocks))
        layout.put("blocks", blocks)
        return layout
    }

    // ---------- MAPPATURE SINGOLO BLOCCO -------------------------------------

    private fun mapBlock(b: JSONObject): JSONObject {
        return when (b.optString("type").lowercase()) {
            "section", "section_header", "sectionheader" -> mapSectionHeader(b)
            "row"                                        -> mapRow(b)
            "card"                                       -> mapCard(b)
            "buttonrow", "buttons_row", "buttons"        -> mapButtonRow(b)
            "divider"                                    -> JSONObject().put("type", "Divider")
            else -> {
                // Fallback: mostra almeno un titolo per non perdere informazioni
                val title = b.optString("title",
                    b.optString("text",
                        b.optJSONObject("text")?.optString("title", b.optString("id","Block"))))
                JSONObject().apply {
                    put("type", "SectionHeader")
                    if (title.isNotBlank()) put("title", title)
                }
            }
        }
    }

    private fun mapSectionHeader(b: JSONObject): JSONObject {
        val j = JSONObject().put("type", "SectionHeader")

        val tObj    = b.optJSONObject("text")
        val title   = b.optString("title", tObj?.optString("title") ?: tObj?.optString("value") ?: "")
        val sub     = b.optString("subtitle", tObj?.optString("subtitle") ?: "")
        val align   = b.optString("align")

        if (title.isNotBlank())   j.put("title", title)
        if (sub.isNotBlank())     j.put("subtitle", sub)
        if (align.isNotBlank())   j.put("align", align)

        // alcune proprietà di stile del testo (se presenti)
        tObj?.optString("style")?.takeIf { it.isNotBlank() }?.let { j.put("style", it) }
        tObj?.optString("textColor")?.takeIf { it.isNotBlank() }?.let { j.put("textColor", it) }
        tObj?.optDouble("textSizeSp")?.takeIf { it.isFinite() }?.let { j.put("textSizeSp", it) }
        tObj?.optString("fontFamily")?.takeIf { it.isNotBlank() }?.let { j.put("fontFamily", it) }
        tObj?.optString("fontWeight")?.takeIf { it.isNotBlank() }?.let { j.put("fontWeight", it) }

        b.optJSONObject("container")?.let { j.put("container", mapContainer(it)) }
        return j
    }

    private fun mapRow(b: JSONObject): JSONObject {
        val j = JSONObject().put("type", "Row")
        val gap = b.optDouble("gapDp", Double.NaN)
        if (!gap.isNaN()) j.put("gapDp", gap)

        val scrollX = b.optBoolean("scrollX", false)
        if (scrollX) j.put("scrollX", true)

        val items = JSONArray()
        val arr = b.optJSONArray("items") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val it = arr.optJSONObject(i) ?: continue
            val mapped = when (it.optString("type").lowercase()) {
                "spacer", "spacerh" -> JSONObject().apply {
                    put("type", "SpacerH")
                    it.optString("mode").takeIf { m -> m.isNotBlank() }?.let { m -> put("mode", m) }
                    val w = it.optDouble("widthDp", Double.NaN)
                    if (!w.isNaN()) put("widthDp", w)
                }
                else -> mapBlock(it).also { mb ->
                    // supporto a weight/widthDp sui figli di row
                    val w = it.optDouble("weight", Double.NaN)
                    if (!w.isNaN()) mb.put("weight", w)
                    val fix = it.optDouble("widthDp", Double.NaN)
                    if (!fix.isNaN()) mb.put("widthDp", fix)
                }
            }
            items.put(mapped)
        }
        j.put("items", items)
        return j
    }

    private fun mapCard(b: JSONObject): JSONObject {
        val j = JSONObject().put("type", "Card")
        j.put("variant", b.optString("variant", "elevated"))
        b.optString("clickActionId").takeIf { it.isNotBlank() }?.let { j.put("clickActionId", it) }
        b.optJSONObject("container")?.let { j.put("container", mapContainer(it)) }

        val inner = JSONArray()
        val content = b.optJSONArray("content") ?: b.optJSONArray("blocks") ?: JSONArray()
        for (i in 0 until content.length()) {
            val it = content.optJSONObject(i) ?: continue
            inner.put(mapBlock(it))
        }
        j.put("blocks", inner)
        return j
    }

    private fun mapButtonRow(b: JSONObject): JSONObject {
        val j = JSONObject().put("type", "ButtonRow")
        b.optString("align").takeIf { it.isNotBlank() }?.let { j.put("align", it) }

        val btns = JSONArray()
        val arr = b.optJSONArray("buttons") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val btn = arr.optJSONObject(i) ?: continue
            btns.put(JSONObject().apply {
                put("label", btn.optString("label",""))
                put("style", btn.optString("style","text"))
                btn.optString("icon").takeIf { it.isNotBlank() }?.let { put("icon", it) }
                btn.optString("actionId").takeIf { it.isNotBlank() }?.let { put("actionId", it) }
                btn.optString("size").takeIf { it.isNotBlank() }?.let { put("size", it) }
                btn.optString("tint").takeIf { it.isNotBlank() }?.let { put("tint", it) }
                btn.optString("shape").takeIf { it.isNotBlank() }?.let { put("shape", it) }
                val corner = btn.optDouble("corner", Double.NaN)
                if (!corner.isNaN()) put("corner", corner)
                if (btn.optBoolean("confirm", false)) put("confirm", true)
            })
        }
        j.put("buttons", btns)
        return j
    }

    private fun mapTopBar(b: JSONObject): JSONObject {
        val j = JSONObject()
        j.put("variant", b.optString("variant","small"))
        j.put("title", b.optString("title",""))
        b.optString("subtitle").takeIf { it.isNotBlank() }?.let { j.put("subtitle", it) }
        b.optString("scroll").takeIf { it.isNotBlank() }?.let { j.put("scroll", it) }
        b.optJSONObject("container")?.let { j.put("container", mapContainer(it)) }

        val actions = JSONArray()
        val arr = b.optJSONArray("actions") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val a = arr.optJSONObject(i) ?: continue
            actions.put(mapBarItem(a))
        }
        if (actions.length() > 0) j.put("actions", actions)
        return j
    }

    private fun mapBarItem(a: JSONObject): JSONObject {
        return when (a.optString("type").lowercase()) {
            "button" -> JSONObject().apply {
                put("type", "button")
                put("label", a.optString("label",""))
                put("style", a.optString("style","text"))
                put("actionId", a.optString("actionId",""))
            }
            "spacer" -> JSONObject().apply {
                put("type","spacer")
                a.optString("mode").takeIf { it.isNotBlank() }?.let { put("mode", it) }
                val w = a.optDouble("widthDp", Double.NaN)
                if (!w.isNaN()) put("widthDp", w)
            }
            else -> JSONObject().apply {
                // default = icona
                put("icon", a.optString("icon","more_vert"))
                a.optString("actionId").takeIf { it.isNotBlank() }?.let { put("actionId", it) }
                a.optString("openMenuId").takeIf { it.isNotBlank() }?.let { put("openMenuId", it) }
            }
        }
    }

    // ---------- CONTENITORE ---------------------------------------------------

    private fun mapContainer(c: JSONObject): JSONObject {
        val out = JSONObject()
        c.optString("style").takeIf { it.isNotBlank() }?.let { out.put("style", it) }
        val corner = c.optDouble("corner", Double.NaN)
        if (!corner.isNaN()) out.put("corner", corner)
        c.optString("borderMode").takeIf { it.isNotBlank() }?.let { out.put("borderMode", it) }
        val thick = c.optDouble("borderThicknessDp", Double.NaN)
        if (!thick.isNaN()) out.put("borderThicknessDp", thick)
        c.optString("borderColor").takeIf { it.isNotBlank() }?.let { out.put("borderColor", it) }
        c.optString("customColor").takeIf { it.isNotBlank() }?.let { out.put("customColor", it) }
        c.optString("gradient1").takeIf { it.isNotBlank() }?.let { out.put("gradient1", it) }
        c.optString("image").takeIf { it.isNotBlank() }?.let { out.put("image", it) }
        c.optString("widthMode").takeIf { it.isNotBlank() }?.let { out.put("widthMode", it) }
        val widthDp = c.optDouble("widthDp", Double.NaN)
        if (!widthDp.isNaN()) out.put("widthDp", widthDp)
        return out
    }
}
