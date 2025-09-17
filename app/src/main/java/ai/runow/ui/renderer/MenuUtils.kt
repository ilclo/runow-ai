package ai.runow.ui.renderer

import org.json.JSONArray
import org.json.JSONObject

/**
 * Raccoglie i menu definiti nello screen in una mappa (id -> JSONArray).
 * - Cerca in layout["menus"] se presente (forma { "id": [ ... ] }).
 * - Supporta fallback: layout["menus"] come array di { "id": "...", "items":[...] }.
 * - Non lancia eccezioni: restituisce sempre una mappa (anche vuota).
 */
fun collectMenus(layout: JSONObject): Map<String, JSONArray> {
    val out = LinkedHashMap<String, JSONArray>()

    // Caso 1: oggetto { id: [...] }
    layout.optJSONObject("menus")?.let { obj ->
        obj.keys().forEach { key ->
            val arr = obj.optJSONArray(key) ?: return@forEach
            out[key] = arr
        }
        return out
    }

    // Caso 2: array di oggetti { id, items }
    layout.optJSONArray("menus")?.let { arr ->
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val id = m.optString("id", "")
            if (id.isBlank()) continue
            val items = m.optJSONArray("items") ?: JSONArray()
            out[id] = items
        }
    }

    return out
}
