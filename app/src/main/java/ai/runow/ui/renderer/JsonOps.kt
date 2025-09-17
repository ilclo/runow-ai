package ai.runow.ui.renderer

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

/** Profondo clone via stringa */
fun duplicate(obj: JSONObject): JSONObject = JSONObject(obj.toString())

/** Sposta elemento in JSONArray (in place) */
fun moveInArray(array: JSONArray, from: Int, to: Int): JSONArray {
    if (from == to) return array
    val size = array.length()
    if (from !in 0 until size || to !in 0 until size) return array
    val tmp = array.get(from)
    removeAt(array, from)
    insertAt(array, to, tmp)
    return array
}

/** Rimuove elemento da JSONArray (in place). Restituisce l'array. */
fun removeAt(array: JSONArray, index: Int): JSONArray {
    if (index !in 0 until array.length()) return array
    val newArr = JSONArray()
    for (i in 0 until array.length()) if (i != index) newArr.put(array.get(i))
    // Copia back nel reference
    while (array.length() > 0) array.remove(array.length() - 1)
    for (i in 0 until newArr.length()) array.put(newArr.get(i))
    return array
}

/** Inserisce elemento in JSONArray (in place). Restituisce l'array. */
fun insertAt(array: JSONArray, index: Int, value: Any): JSONArray {
    val size = array.length()
    val target = max(0, min(index, size))
    val newArr = JSONArray()
    for (i in 0 until target) newArr.put(array.get(i))
    newArr.put(value)
    for (i in target until size) newArr.put(array.get(i))
    while (array.length() > 0) array.remove(array.length() - 1)
    for (i in 0 until newArr.length()) array.put(newArr.get(i))
    return array
}

/** Utility path: "/a/b/3/c" -> tokens ["a","b","3","c"] */
private fun splitPath(path: String): List<String> =
    path.trim().split("/").filter { it.isNotBlank() }

/** Ritorna il nodo (JSONObject/JSONArray/value) alla path, o null se assente. */
fun jsonAtPath(root: JSONObject, path: String): Any? {
    var cur: Any = root
    for (key in splitPath(path)) {
        cur = when (cur) {
            is JSONObject -> {
                if (key.matches(Regex("\\d+"))) return null
                if (!cur.has(key)) return null
                cur.get(key)
            }
            is JSONArray -> {
                val idx = key.toIntOrNull() ?: return null
                if (idx !in 0 until cur.length()) return null
                cur.get(idx)
            }
            else -> return null
        }
    }
    return cur
}

/**
 * Sostituisce/crea il valore alla path.
 * Se il genitore è array, usa indice; se è object, usa chiave.
 */
fun replaceAtPath(root: JSONObject, path: String, value: Any): JSONObject {
    val tokens = splitPath(path)
    if (tokens.isEmpty()) return root
    var cur: Any = root
    for (i in 0 until tokens.size - 1) {
        val k = tokens[i]
        cur = when (cur) {
            is JSONObject -> {
                if (!cur.has(k) || cur.get(k) == JSONObject.NULL) cur.put(k, JSONObject())
                cur.get(k)
            }
            is JSONArray -> {
                val idx = k.toIntOrNull() ?: return root
                if (idx !in 0..cur.length()) return root
                val next = if (idx < cur.length()) cur.get(idx) else JSONObject().also { cur.put(it) }
                next
            }
            else -> return root
        }
    }
    val last = tokens.last()
    when (cur) {
        is JSONObject -> cur.put(last, value)
        is JSONArray -> {
            val idx = last.toIntOrNull() ?: return root
            if (idx in 0 until cur.length()) {
                // replace
                val newArr = JSONArray()
                for (i in 0 until cur.length()) newArr.put(if (i == idx) value else cur.get(i))
                while (cur.length() > 0) cur.remove(cur.length() - 1)
                for (i in 0 until newArr.length()) cur.put(newArr.get(i))
            } else if (idx == cur.length()) {
                cur.put(value)
            }
        }
    }
    return root
}

/** Inserisce un block in root.blocks e ritorna il path dell'inserito. */
fun insertBlockAndReturnPath(root: JSONObject, newBlock: JSONObject, atIndex: Int? = null): String {
    val blocks = root.optJSONArray("blocks") ?: JSONArray().also { root.put("blocks", it) }
    val idx = atIndex ?: blocks.length()
    insertAt(blocks, idx, newBlock)
    return "/blocks/$idx"
}

/**
 * Inserisce una action "icon" nella topBar (array "actions").
 * Ritorna il path dell'icona inserita (es. "/topBar/actions/2").
 */
fun insertIconMenuReturnIconPath(
    layout: JSONObject,
    iconName: String,
    actionId: String = ""
): String {
    val topBar = layout.optJSONObject("topBar") ?: JSONObject().also { layout.put("topBar", it) }
    val actions = topBar.optJSONArray("actions") ?: JSONArray().also { topBar.put("actions", it) }
    val node = JSONObject().apply {
        put("type", "icon")
        put("icon", iconName)
        put("actionId", actionId)
    }
    val idx = actions.length()
    actions.put(node)
    return "/topBar/actions/$idx"
}
