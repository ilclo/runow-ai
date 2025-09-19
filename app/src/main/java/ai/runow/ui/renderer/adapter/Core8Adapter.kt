package ai.runow.ui.renderer.adapter

import org.json.JSONArray
import org.json.JSONObject

/**
 * Adapter Core‑8 → JSON "legacy" compatibile con UiRenderer.
 *
 * Non invoca composable: costruisce soltanto JSON che il renderer attuale già capisce.
 *
 * Tipi Core‑8 attesi (campo "t"):
 *  - text, button, image
 *  - vstack, hstack, container (contenitore generico con "content":[...])
 *  - list, appbar, sheet, menu
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
object Core8Adapter {

    /** Adatta un singolo nodo Core‑8 in un blocco UiRenderer. */
    fun nodeToLegacy(node: JSONObject): JSONObject = when (node.optString("t")) {

        "text" -> JSONObject().apply {
            // UiRenderer ha già un "SectionHeader" con override tipografici riusabili
            put("type", "SectionHeader")
            put("title", node.optString("text", ""))
            node.optString("align").takeIf { it.isNotBlank() }?.let { put("align", it) }
            node.optJSONObject("style")?.let { st ->
                st.optString("color").takeIf { it.isNotBlank() }?.let { put("textColor", it) }
                st.optDouble("sizeSp", Double.NaN).takeIf { !it.isNaN() }?.let { put("textSizeSp", it) }
                st.optString("weight").takeIf { it.isNotBlank() }?.let { put("fontWeight", it) }
                st.optString("font").takeIf { it.isNotBlank() }?.let { put("fontFamily", it) }
            }
        }

        "button" -> JSONObject().apply {
            // Mappo su ButtonRow con un solo bottone
            put("type", "ButtonRow")
            val buttons = JSONArray().put(JSONObject().apply {
                put("label", node.optString("text", "Button"))
                put("style", node.optString("variant", "primary"))
                node.optString("icon").takeIf { it.isNotBlank() }?.let { put("icon", it) }
                node.optString("action").takeIf { it.isNotBlank() }?.let { put("actionId", it) }
            })
            put("buttons", buttons)
            node.optJSONObject("container")?.let { put("container", containerToLegacy(it)) }
        }

        "image" -> JSONObject().apply {
            put("type", "Image")
            put("source", node.optString("src", ""))
            node.optJSONObject("style")?.optString("scale")?.takeIf { it.isNotBlank() }?.let { put("contentScale", it) }
            node.optJSONObject("style")?.optDouble("heightDp", Double.NaN)?.takeIf { !it.isNaN() }?.let { put("heightDp", it) }
            node.optJSONObject("style")?.optDouble("corner", Double.NaN)?.takeIf { !it.isNaN() }?.let { put("corner", it) }
        }

        "vstack", "hstack", "container" -> {
            // Contenitore generico: lo mappo su Card con "blocks":[...]
            val blocks = JSONArray()
            val content = node.optJSONArray("content") ?: JSONArray()
            for (i in 0 until content.length()) {
                content.optJSONObject(i)?.let { blocks.put(nodeToLegacy(it)) }
            }
            JSONObject().apply {
                put("type", "Card")
                put("blocks", blocks)
                node.optJSONObject("container")?.let { put("container", containerToLegacy(it)) }
            }
        }

        "list" -> JSONObject().apply {
            put("type", "List")
            val items = JSONArray()
            val src = node.optJSONArray("items") ?: JSONArray()
            for (i in 0 until src.length()) {
                val it = src.optJSONObject(i) ?: continue
                items.put(JSONObject().apply {
                    put("title", it.optString("title", "Item"))
                    it.optString("subtitle").takeIf { s -> s.isNotBlank() }?.let { s -> put("subtitle", s) }
                    it.optString("action").takeIf { s -> s.isNotBlank() }?.let { s -> put("actionId", s) }
                })
            }
            put("items", items)
        }

        "appbar" -> JSONObject().apply {
            // Semplifico mappando su SectionHeader
            put("type", "SectionHeader")
            put("title", node.optString("title", ""))
            node.optJSONObject("container")?.let { put("container", containerToLegacy(it)) }
        }

        "sheet" -> JSONObject().apply {
            put("type", "Card")
            val blocks = JSONArray()
            node.optString("body").takeIf { it.isNotBlank() }?.let { txt ->
                blocks.put(JSONObject().apply { put("type", "SectionHeader"); put("title", txt) })
            }
            put("blocks", blocks)
            node.optJSONObject("container")?.let { put("container", containerToLegacy(it)) }
        }

        "menu" -> JSONObject().apply {
            put("type", "Menu")
            put("id", node.optString("id", "menu"))
            val items = JSONArray()
            (node.optJSONArray("items") ?: JSONArray()).let { src ->
                for (i in 0 until src.length()) {
                    val it = src.optJSONObject(i) ?: continue
                    items.put(JSONObject().apply {
                        it.optString("icon").takeIf { s -> s.isNotBlank() }?.let { s -> put("icon", s) }
                        put("label", it.optString("label", ""))
                        it.optString("action").takeIf { s -> s.isNotBlank() }?.let { s -> put("actionId", s) }
                    })
                }
            }
            put("items", items)
        }

        else -> node // fallback: passthrough
    }

    /** Mappa il sotto-oggetto "container" Core‑8 in un "container" capito da StyledContainer. */
    fun containerToLegacy(c: JSONObject): JSONObject = JSONObject().apply {
        // Aspetto
        c.optString("style").takeIf { it.isNotBlank() }?.let { put("style", it) }
        c.optString("color").takeIf { it.isNotBlank() }?.let { put("customColor", it) }
        c.optDouble("bgAlpha", Double.NaN).takeIf { !it.isNaN() }?.let { put("bgAlpha", it) }

        // Gradiente (se presente)
        c.optJSONObject("gradient")?.let { g ->
            val grad = JSONObject()
            val colors = JSONArray()
            val arr = g.optJSONArray("colors") ?: JSONArray()
            for (i in 0 until arr.length()) colors.put(arr.optString(i))
            if (colors.length() >= 2) {
                grad.put("colors", colors)
                grad.put("direction", g.optString("direction", "vertical"))
                put("gradient", grad)
            }
        }

        // Forma/bordo
        c.optDouble("corner", Double.NaN).takeIf { !it.isNaN() }?.let { put("corner", it) }
        c.optString("borderMode").takeIf { it.isNotBlank() }?.let { put("borderMode", it) }
        c.optDouble("borderThicknessDp", Double.NaN).takeIf { !it.isNaN() }?.let { put("borderThicknessDp", it) }
        c.optString("borderColor").takeIf { it.isNotBlank() }?.let { put("borderColor", it) }

        // Dimensioni
        c.optJSONObject("size")?.let { s ->
            s.optString("widthMode").takeIf { it.isNotBlank() }?.let { put("widthMode", it) }
            s.optDouble("widthDp", Double.NaN).takeIf { !it.isNaN() }?.let { put("widthDp", it) }
            s.optDouble("widthFraction", Double.NaN).takeIf { !it.isNaN() }?.let { put("widthFraction", it) }
            s.optString("heightMode").takeIf { it.isNotBlank() }?.let { put("heightMode", it) }
            s.optDouble("heightDp", Double.NaN).takeIf { !it.isNaN() }?.let { put("heightDp", it) }
        }

        // Immagine di sfondo
        c.optJSONObject("image")?.let { img ->
            put("image", JSONObject().apply {
                put("source", img.optString("src", ""))
                put("contentScale", img.optString("scale", "fill"))
                put("alpha", img.optDouble("alpha", 1.0))
            })
        }
    }

    /** Adatta un array di contenuti Core‑8 di pagina in un array di blocchi legacy. */
    fun pageToLegacy(content: JSONArray): JSONArray {
        val out = JSONArray()
        for (i in 0 until content.length()) {
            val n = content.optJSONObject(i) ?: continue
            out.put(nodeToLegacy(n))
        }
        return out
    }
}
