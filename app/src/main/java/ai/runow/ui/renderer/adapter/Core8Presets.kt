package ai.runow.ui.renderer.adapter

import org.json.JSONArray
import org.json.JSONObject

object Core8Presets {

    fun topBar(title: String): JSONObject = JSONObject().apply {
        put("type", "topBar")
        put("variant", "small")
        put("title", title)
        put("scroll", "pinned")
    }

    fun sectionHeader(text: String): JSONObject = JSONObject().apply {
        put("type", "sectionHeader")
        put("text", text)
    }

    fun buttonRow(vararg buttons: Pair<String,String>): JSONObject = JSONObject().apply {
        put("type", "buttonRow")
        val arr = JSONArray()
        buttons.forEach { (label, action) ->
            arr.put(JSONObject().apply {
                put("label", label)
                put("actionId", action)
                put("style", "filled")
            })
        }
        put("buttons", arr)
    }

    fun row(vararg children: JSONObject): JSONObject = JSONObject().apply {
        put("type", "row")
        val arr = JSONArray()
        children.forEach { arr.put(it) }
        put("items", arr)
    }

    fun card(vararg children: JSONObject): JSONObject = JSONObject().apply {
        put("type", "card")
        val arr = JSONArray()
        children.forEach { arr.put(it) }
        put("content", arr)
    }

    /** Pagina demo per un test rapido con RenderCore8Page */
    fun demoPage(): JSONArray = JSONArray().apply {
        put(topBar("Esempio"))
        put(sectionHeader("Titolo sezione"))
        put(card(sectionHeader("Dentro la card")))
        put(buttonRow("OK" to "ok", "Annulla" to "cancel"))
    }
}
