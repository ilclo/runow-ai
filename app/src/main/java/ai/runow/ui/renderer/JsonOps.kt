package ai.runow.ui.renderer

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

fun duplicate(root: JSONObject, path: String) {
    val p = getParentAndIndex(root, path) ?: return
    val (arr, idx) = p
    val clone = JSONObject(arr.getJSONObject(idx).toString())
    val tmp = mutableListOf<Any?>()
    for (i in 0 until arr.length()) {
        tmp.add(arr.get(i))
        if (i == idx) tmp.add(clone)
    }
    while (arr.length() > 0) arr.remove(arr.length() - 1)
    tmp.forEach { arr.put(it) }
}


fun moveInArray(arr: JSONArray, index: Int, delta: Int) {
    if (index < 0 || index >= arr.length()) return
    val newIdx = (index + delta).coerceIn(0, arr.length() - 1)
    if (newIdx == index) return
    val tmp = mutableListOf<Any?>()
    for (i in 0 until arr.length()) tmp.add(arr.get(i))
    val it = tmp.removeAt(index)
    tmp.add(newIdx, it)
    while (arr.length() > 0) arr.remove(arr.length() - 1)
    tmp.forEach { arr.put(it) }
}

fun removeAt(arr: JSONArray, index: Int) {
    if (index < 0 || index >= arr.length()) return
    val tmp = mutableListOf<Any?>()
    for (i in 0 until arr.length()) if (i != index) tmp.add(arr.get(i))
    while (arr.length() > 0) arr.remove(arr.length() - 1)
    tmp.forEach { arr.put(it) }
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

fun jsonAtPath(root: JSONObject, path: String): Any? {
    if (!path.startsWith("/")) return null
    val segs = path.trim('/').split('/')
    var node: Any = root
    var i = 0
    while (i < segs.size) {
        when (node) {
            is JSONObject -> { node = (node as JSONObject).opt(segs[i]) ?: return null; i++ }
            is JSONArray  -> {
                val idx = segs[i].toIntOrNull() ?: return null
                node = (node as JSONArray).opt(idx) ?: return null; i++
            }
            else -> return null
        }
    }
    return node
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

fun insertIconMenuReturnIconPath(root: JSONObject, selectedPath: String?): String {
    val id = "menu_" + System.currentTimeMillis().toString().takeLast(5)
    val iconPath = insertBlockAndReturnPath(root, selectedPath, newIconButton(id), "after")
    insertBlockAndReturnPath(root, iconPath, newMenu(id), "after")
    return iconPath
}


