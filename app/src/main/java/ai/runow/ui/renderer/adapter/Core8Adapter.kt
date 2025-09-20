package ai.runow.ui.renderer.adapter

import org.json.JSONArray
import org.json.JSONObject

/**
 * Adatta JSON Core‑8 (blocchi) al JSON "legacy" atteso dal renderer attuale.
 *
 * Convenzioni Core‑8 usate qui (semplificate):
 * - sectionHeader : { type:"sectionHeader", text, align?, fontWeight?, fontFamily?, textSizeSp?, textColor?, container? }
 * - row           : { type:"row", items:[ ...blocchi... ], spacingDp?, align?, container? }
 * - card          : { type:"card", content:[ ...blocchi... ], container? }
 * - buttonRow     : { type:"buttonRow", buttons:[ {label,actionId,style?,icon?}, ... ], container? }
 * - topBar        : { type:"topBar", variant?, title?, scroll?, titleColor?, actionsColor?, divider?, container?, actions?[...] }
 *
 * La page Core‑8 è un JSONArray di nodi; "topBar" viene estratto e messo a livello root.
 */
object Core8Adapter {

    fun pageToLegacy(page: JSONArray): JSONObject {
        val layout = JSONObject()
        val blocks = JSONArray()

        for (i in 0 until page.length()) {
            val node = page.optJSONObject(i) ?: continue
            when (node.optString("type")) {
                "topBar" -> layout.put("topBar", mapTopBar(node))
                else     -> blocks.put(mapBlock(node))
            }
        }

        layout.put("blocks", blocks)
        return layout
    }

    // ---- blocchi ----

    private fun mapBlock(b: JSONObject): JSONObject = when (b.optString("type")) {
        "sectionHeader" -> mapSectionHeader(b)
        "row"           -> mapRow(b)
        "card"          -> mapCard(b)
        "buttonRow"     -> mapButtonRow(b)
        else            -> JSONObject(b.toString()) // passthrough
    }

    private fun mapSectionHeader(node: JSONObject): JSONObject {
        val out = JSONObject()
        out.put("type", "SectionHeader")
        out.put("text", node.optString("text", ""))

        node.optString("align", null)?.let       { out.put("align", it) }
        node.optString("fontWeight", null)?.let  { out.put("fontWeight", it) }
        node.optString("fontFamily", null)?.let  { out.put("fontFamily", it) }
        if (node.has("textSizeSp")) out.put("textSizeSp", node.optDouble("textSizeSp"))
        node.optString("textColor", null)?.let   { out.put("textColor", it) }
        node.optJSONObject("container")?.let     { out.put("container", mapContainer(it)) }

        return out
    }

    private fun mapRow(node: JSONObject): JSONObject {
        val out = JSONObject()
        out.put("type", "Row")

        val srcChildren = node.optJSONArray("items") ?: node.optJSONArray("children") ?: JSONArray()
        val items = JSONArray()
        for (j in 0 until srcChildren.length()) {
            val ch = srcChildren.optJSONObject(j) ?: continue
            items.put(mapBlock(ch))
        }
        out.put("items", items)

        if (node.has("spacingDp")) out.put("spacing", node.optDouble("spacingDp"))
        node.optString("align", null)?.let     { out.put("align", it) }
        node.optJSONObject("container")?.let   { out.put("container", mapContainer(it)) }

        return out
    }

    private fun mapCard(node: JSONObject): JSONObject {
        val out = JSONObject()
        out.put("type", "Card")

        node.optJSONObject("container")?.let { out.put("container", mapContainer(it)) }

        val src = node.optJSONArray("content") ?: JSONArray()
        val content = JSONArray()
        for (j in 0 until src.length()) {
            val ch = src.optJSONObject(j) ?: continue
            content.put(mapBlock(ch))
        }
        // il renderer legacy delle Card normalmente guarda "content"
        out.put("content", content)

        return out
    }

    private fun mapButtonRow(node: JSONObject): JSONObject {
        val out = JSONObject()
        out.put("type", "ButtonRow")

        val src = node.optJSONArray("buttons") ?: JSONArray()
        val buttons = JSONArray()
        for (j in 0 until src.length()) {
            val b = src.optJSONObject(j) ?: continue
            val btn = JSONObject()
            btn.put("label", b.optString("label", ""))
            btn.put("actionId", b.optString("actionId", ""))
            btn.put("style", b.optString("style", "filled"))
            b.optString("icon", null)?.let { btn.put("icon", it) }
            buttons.put(btn)
        }
        out.put("buttons", buttons)

        node.optJSONObject("container")?.let { out.put("container", mapContainer(it)) }

        return out
    }

    // ---- top bar root ----

    private fun mapTopBar(node: JSONObject): JSONObject {
        val out = JSONObject()
        out.put("variant", node.optString("variant", "small"))
        out.put("title",   node.optString("title",   ""))
        out.put("scroll",  node.optString("scroll",  "none"))

        node.optString("titleColor",   null)?.let { out.put("titleColor", it) }
        node.optString("actionsColor", null)?.let { out.put("actionsColor", it) }
        out.put("divider", node.optBoolean("divider", false))

        node.optJSONObject("container")?.let { out.put("container", mapContainer(it)) }

        val actionsOut = JSONArray()
        val actionsIn  = node.optJSONArray("actions") ?: JSONArray()
        for (j in 0 until actionsIn.length()) {
            val a = actionsIn.optJSONObject(j) ?: continue
            val act = JSONObject()
            act.put("type",     a.optString("type", "icon"))
            a.optString("icon", null)?.let  { act.put("icon", it) }
            a.optString("label", null)?.let { act.put("label", it) }
            act.put("actionId", a.optString("actionId", ""))
            act.put("style",    a.optString("style", "text"))
            actionsOut.put(act)
        }
        out.put("actions", actionsOut)

        return out
    }

    // ---- container ----

    private fun mapContainer(node: JSONObject): JSONObject {
        val out = JSONObject()

        out.put("style",  node.optString("style", "surface"))
        out.put("corner", node.optDouble("corner", 12.0))

        node.optString("customColor", null)?.let     { out.put("customColor", it) }
        node.optString("borderMode",  null)?.let     { out.put("borderMode",  it) }
        if (node.has("borderThicknessDp")) out.put("borderThicknessDp", node.optDouble("borderThicknessDp"))
        node.optString("borderColor", null)?.let     { out.put("borderColor", it) }

        if (node.has("bgAlpha")) out.put("bgAlpha", node.optDouble("bgAlpha"))
        node.optString("gradient1", null)?.let { out.put("gradient1", it) }
        node.optString("gradient2", null)?.let { out.put("gradient2", it) }

        // immagine e scaling: passthrough; il tuo renderer già gestisce image { source, contentScale, alpha }
        node.optJSONObject("image")?.let { out.put("image", it) }

        // sizing
        node.optString("widthMode", null)?.let  { out.put("widthMode", it) }
        if (node.has("fixedWidthDp")) out.put("fixedWidthDp", node.optDouble("fixedWidthDp"))

        return out
    }
}
