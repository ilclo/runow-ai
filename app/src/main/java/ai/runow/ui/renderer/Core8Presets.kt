package ai.runow.ui.renderer

import org.json.JSONArray
import org.json.JSONObject

object Core8Presets {

    fun topBar(
        title: String,
        variant: String = "small",
        subtitle: String? = null
    ): JSONObject = JSONObject().apply {
        put("type", "topbar")
        put("title", title)
        put("variant", variant)
        subtitle?.takeIf { it.isNotBlank() }?.let { put("subtitle", it) }
        // esempio: azione menu
        put("actions", JSONArray().put(JSONObject().apply {
            put("type", "icon")
            put("icon", "more_vert")
            put("openMenuId", "more")
        }))
    }

    fun sectionHeader(
        title: String,
        subtitle: String? = null,
        align: String? = null
    ): JSONObject = JSONObject().apply {
        put("type", "section")
        put("title", title)
        subtitle?.takeIf { it.isNotBlank() }?.let { put("subtitle", it) }
        align?.takeIf { it.isNotBlank() }?.let { put("align", it) }
        // esempio stile testo
        put("text", JSONObject().apply {
            put("textColor", "")
            put("fontWeight", "w600")
        })
    }

    fun row(vararg children: JSONObject, gapDp: Double = 8.0, scrollX: Boolean = false): JSONObject =
        JSONObject().apply {
            put("type", "row")
            put("gapDp", gapDp)
            if (scrollX) put("scrollX", true)
            val arr = JSONArray()
            children.forEach { arr.put(it) }
            put("items", arr)
        }

    fun spacerH(widthDp: Double? = null, expand: Boolean = false): JSONObject =
        JSONObject().apply {
            put("type", "spacer")
            if (expand) put("mode","expand")
            widthDp?.let { put("widthDp", it) }
        }

    fun card(vararg blocks: JSONObject, variant: String = "elevated"): JSONObject =
        JSONObject().apply {
            put("type", "card")
            put("variant", variant)
            val arr = JSONArray()
            blocks.forEach { arr.put(it) }
            put("content", arr)
        }

    fun buttonRow(vararg buttons: JSONObject, align: String = "start"): JSONObject =
        JSONObject().apply {
            put("type", "buttonrow")
            put("align", align)
            val arr = JSONArray()
            buttons.forEach { arr.put(it) }
            put("buttons", arr)
        }

    fun button(
        label: String,
        style: String = "filled",
        actionId: String = ""
    ): JSONObject = JSONObject().apply {
        put("label", label)
        put("style", style)
        if (actionId.isNotBlank()) put("actionId", actionId)
    }

    /** Pagina demo: topbar + card con section header + button row */
    fun demoPage(): JSONArray = JSONArray().apply {
        put(topBar(title = "Core‑8 demo", variant = "small"))
        put(
            card(
                sectionHeader("Sezione A", "Sottotitolo", align = "start"),
                buttonRow(
                    button("Conferma", style = "filled", actionId = "ok"),
                    button("Annulla",  style = "text",   actionId = "cancel")
                )
            )
        )
        put(
            row(
                sectionHeader("Colonna 1").apply { put("weight", 1.0) },
                spacerH(widthDp = 8.0),
                sectionHeader("Colonna 2").apply { put("weight", 1.0) }
            )
        )
    }
}
