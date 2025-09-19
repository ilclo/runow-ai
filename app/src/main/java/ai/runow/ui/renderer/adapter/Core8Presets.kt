package ai.runow.ui.renderer.adapter

import org.json.JSONArray
import org.json.JSONObject

/**
 * Preset di esempio in formato Core‑8 (riusabili).
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
object Core8Presets {

    fun primaryButton(
        label: String = "Avvia",
        action: String? = null,
        icon: String? = "play_arrow"
    ): JSONObject = JSONObject().apply {
        put("t", "button")
        put("text", label)
        put("variant", "primary")
        icon?.let { put("icon", it) }
        action?.let { put("action", it) }
    }

    fun cardMedium(
        title: String = "Titolo",
        subtitle: String = "Sottotitolo",
        body: String = "",
        imageUrl: String? = null,
        primaryAction: String? = null
    ): JSONObject = JSONObject().apply {
        put("t", "container")
        put("container", JSONObject().apply {
            put("style", "surface")
            put("corner", 12)
        })

        val content = JSONArray()

        if (!imageUrl.isNullOrBlank()) {
            content.put(JSONObject().apply {
                put("t", "image")
                put("src", imageUrl)
                put("style", JSONObject().put("heightDp", 160))
            })
        }
        if (title.isNotBlank()) {
            content.put(JSONObject().apply {
                put("t", "text")
                put("text", title)
                put("style", JSONObject().put("sizeSp", 20).put("weight", "w600"))
            })
        }
        if (subtitle.isNotBlank()) {
            content.put(JSONObject().apply {
                put("t", "text")
                put("text", subtitle)
                put("style", JSONObject().put("sizeSp", 14))
            })
        }
        if (body.isNotBlank()) {
            content.put(JSONObject().apply { put("t", "text"); put("text", body) })
        }
        if (!primaryAction.isNullOrBlank()) {
            content.put(JSONObject().apply {
                put("t", "button")
                put("text", "Azione")
                put("variant", "primary")
                put("action", primaryAction)
            })
        }
        put("content", content)
    }

    fun listItem(
        title: String = "Elemento",
        subtitle: String? = null,
        action: String? = null,
        leadingIcon: String? = null
    ): JSONObject = JSONObject().apply {
        put("t", "list")
        val items = JSONArray().put(JSONObject().apply {
            put("title", title)
            subtitle?.let { put("subtitle", it) }
            action?.let { put("action", it) }
            leadingIcon?.let { put("icon", it) }
        })
        put("items", items)
    }

    fun appBar(title: String = "Titolo"): JSONObject =
        JSONObject().apply { put("t", "appbar"); put("title", title) }

    fun sheet(text: String = "Contenuto"): JSONObject =
        JSONObject().apply { put("t", "sheet"); put("body", text) }

    fun menu(vararg entries: Pair<String, String>): JSONObject =
        JSONObject().apply {
            put("t", "menu")
            put("id", "menu")
            val arr = JSONArray()
            entries.forEach { (label, action) ->
                arr.put(JSONObject().apply { put("label", label); put("action", action) })
            }
            put("items", arr)
        }
}
