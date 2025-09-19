package ai.runow.ui.renderer.adapter

import org.json.JSONArray
import org.json.JSONObject

/**
 * Adapter: traduce i JSON "Core‑8" (type, props, children) nei blocchi
 * capiti dal renderer (RenderBlock / topBar / sidePanels / Menu).
 *
 * Convenzioni Core‑8 usate qui:
 *  - Screen:   { "type":"screen", "background":{...}, "app_bar":{...}, "children":[ ... ],
 *               "menus":[ ... ], "sheets":[ ... ] }
 *  - Button:   { "type":"button", "text":"...", "actionId":"...", "variant":"primary|tonal|outlined|text",
 *               "icon":"search", "fullWidth":true }
 *  - Card:     { "type":"card", "style":"surface|outlined|tonal|primary", "image":{uri|res|fit}, "title":"...", "subtitle":"...",
 *               "body":"...", "actions":[ {button} ], "actionId":"(click intera card opz.)" }
 *  - ListItem: { "type":"list_item", "title":"...", "subtitle":"...", "actionId":"(facolt.)" }
 *  - AppBar:   { "type":"app_bar", "title":"...", "subtitle":"", "variant":"small|center|medium|large",
 *               "actions":[ {"icon":"more_vert","actionId":"..."}, {"icon":"menu","openMenuId":"main"} ],
 *               "container":{ ...stile... } }
 *  - Menu:     { "type":"menu", "id":"main", "items":[ {"label":"Impostazioni","icon":"settings","actionId":"settings"} ] }
 *  - Sheet:    { "type":"sheet", "id":"filters", "title":"Filtri", "side":"top|left|right",
 *               "widthDp":320, "heightDp":0, "items":[ ...blocchi core‑8... ], "scrimAlpha":0.25 }
 */
object Core8Adapter {

    /** Traduce un "screen" Core‑8 nel layout JSON del renderer (usabile subito da UiScreen). */
    fun fromCore8Screen(core: JSONObject): JSONObject {
        require(core.optString("type") == "screen") { "Root Core‑8 deve essere type=screen" }

        // Layout base
        val layout = obj(
            "page" to mapPage(core.optJSONObject("background")),
            "blocks" to JSONArray()
        )

        // App bar (topBar “estetico” del renderer)
        core.optJSONObject("app_bar")?.let { layout.put("topBar", mapAppBar(it)) }

        // Menù (registrati come blocchi “Menu” così il renderer li raccoglie)
        val menus = core.optJSONArray("menus") ?: JSONArray()
        for (i in 0 until menus.length()) {
            mapMenu(menus.getJSONObject(i))?.let { (layout.getJSONArray("blocks")).put(it) }
        }

        // Sheet/side panels (overlay “top/left/right”)
        val sheets = core.optJSONArray("sheets") ?: JSONArray()
        if (sheets.length() > 0) {
            val arr = JSONArray()
            for (i in 0 until sheets.length()) mapSheet(sheets.getJSONObject(i))?.let(arr::put)
            if (arr.length() > 0) layout.put("sidePanels", arr)
        }

        // Body
        val outBlocks = layout.getJSONArray("blocks")
        val children = core.optJSONArray("children") ?: JSONArray()
        for (i in 0 until children.length()) {
            toBlock(children.getJSONObject(i))?.let(outBlocks::put)
        }

        return layout
    }

    /** Traduce un singolo nodo Core‑8 in un blocco del renderer. */
    fun toBlock(node: JSONObject): JSONObject? = when (node.optString("type")) {
        "button"     -> mapButton(node)
        "card"       -> mapCard(node)
        "list_item"  -> mapListItem(node)
        "app_bar"    -> mapAppBarAsBlock(node) // fallback “inline”
        "menu"       -> mapMenu(node)          // blocco registrabile
        "sheet"      -> null                   // non è un block: viene mappato a sidePanels a livello layout
        else         -> null
    }

    // ---- MAPPINGS ----------------------------------------------------------

    private fun mapButton(node: JSONObject): JSONObject {
        val label  = node.optString("text", node.optString("label","Button"))
        val icon   = node.optString("icon","")
        val style  = when (node.optString("variant","primary")) {
            "outlined" -> "outlined"
            "tonal"    -> "tonal"
            "text"     -> "text"
            else       -> "primary"
        }
        val full   = node.optBoolean("fullWidth", false)
        val action = node.optString("actionId","")

        val btn = obj(
            "label" to label,
            "style" to style,
            "actionId" to action
        ).apply {
            if (icon.isNotBlank()) put("icon", icon)
        }

        // ButtonRow con un solo bottone (composable già presente)
        val block = obj(
            "type" to "ButtonRow",
            "align" to "center",
            "buttons" to arr(btn)
        )
        if (full) {
            block.put("container", obj("widthMode" to "fill"))
        }
        return block
    }

    private fun mapCard(node: JSONObject): JSONObject {
        val cont = obj("style" to when (node.optString("style","surface")) {
            "outlined" -> "outlined"
            "tonal"    -> "tonal"
            "primary"  -> "primary"
            else       -> "surface"
        })

        val blocks = JSONArray()

        // Media (Image)
        node.optJSONObject("image")?.let { img ->
            blocks.put(obj(
                "type" to "Image",
                "source" to img.optString("res").takeIf { it.isNotBlank() }?.let { "res:$it" }
                    ?: img.optString("uri"),
                "contentScale" to img.optString("fit","fit"),
                "heightDp" to img.optDouble("heightDp", 160.0),
                "corner" to img.optDouble("corner", 12.0)
            ))
        }

        // Titoli
        val title = node.optString("title","")
        val sub   = node.optString("subtitle","")
        if (title.isNotBlank() || sub.isNotBlank()) {
            blocks.put(obj(
                "type" to "SectionHeader",
                "title" to title,
                "subtitle" to sub
            ))
        }

        // Body (testo semplice come “subtitle” di SectionHeader, per compat)
        val body = node.optString("body","")
        if (body.isNotBlank()) {
            blocks.put(obj(
                "type" to "SectionHeader",
                "subtitle" to body
            ))
        }

        // Actions (array di button core‑8 → ButtonRow)
        val actions = node.optJSONArray("actions") ?: JSONArray()
        if (actions.length() > 0) {
            val buttons = JSONArray()
            for (i in 0 until actions.length()) {
                toBlock(actions.getJSONObject(i))?.let { b ->
                    // estraggo il singolo bottone dal ButtonRow generato
                    val only = (b.optJSONArray("buttons") ?: JSONArray()).optJSONObject(0)
                    if (only != null) buttons.put(only)
                }
            }
            blocks.put(obj("type" to "ButtonRow", "buttons" to buttons))
        }

        return obj(
            "type" to "Card",
            "container" to cont,
            "blocks" to blocks
        ).apply {
            node.optString("actionId","").takeIf { it.isNotBlank() }?.let { put("clickActionId", it) }
        }
    }

    private fun mapListItem(node: JSONObject): JSONObject {
        val item = obj(
            "title" to node.optString("title",""),
            "subtitle" to node.optString("subtitle","")
        )

        val listBlock = obj(
            "type" to "List",
            "items" to arr(item)
        )

        val action = node.optString("actionId","")
        return if (action.isNotBlank()) {
            // ListItem “tappabile”: lo incapsulo in una Card con clickActionId
            obj(
                "type" to "Card",
                "clickActionId" to action,
                "blocks" to arr(listBlock)
            )
        } else listBlock
    }

    /** AppBar come topBar (consigliato) */
    private fun mapAppBar(node: JSONObject): JSONObject {
        val tb = obj(
            "variant" to node.optString("variant","small"),
            "title" to node.optString("title",""),
            "subtitle" to node.optString("subtitle",""),
            "scroll" to node.optString("scroll","pinned"),
            "actions" to mapBarActions(node.optJSONArray("actions")),
            "container" to (node.optJSONObject("container") ?: obj("style" to "surface"))
        )
        return tb
    }

    /** AppBar inline come blocco di fallback (se vuoi metterla nel body). */
    private fun mapAppBarAsBlock(node: JSONObject): JSONObject = obj(
        "type" to "AppBar",
        "title" to node.optString("title",""),
        "actions" to mapBarActions(node.optJSONArray("actions"))
    )

    private fun mapBarActions(arr: JSONArray?): JSONArray {
        val out = JSONArray()
        val src = arr ?: JSONArray()
        for (i in 0 until src.length()) {
            val a = src.optJSONObject(i) ?: continue
            val icon = a.optString("icon","more_vert")
            val label = a.optString("label","")
            val openMenu = a.optString("openMenuId","")
            val action = a.optString("actionId","")

            if (label.isNotBlank()) {
                out.put(obj("type" to "button", "label" to label, "style" to "text",
                    "actionId" to action.ifBlank { if (openMenu.isNotBlank()) "open_menu:$openMenu" else "" }))
            } else {
                out.put(obj("type" to "icon", "icon" to icon,
                    "actionId" to action.ifBlank { if (openMenu.isNotBlank()) "open_menu:$openMenu" else "" },
                    "openMenuId" to openMenu))
            }
        }
        return out
    }

    /** Menu Core‑8 → blocco "Menu" (il renderer li raccoglie e li mostra nell’overlay). */
    private fun mapMenu(node: JSONObject): JSONObject? {
        val id = node.optString("id").trim()
        if (id.isBlank()) return null
        val items = JSONArray()
        val inArr = node.optJSONArray("items") ?: JSONArray()
        for (i in 0 until inArr.length()) {
            val it = inArr.optJSONObject(i) ?: continue
            items.put(obj(
                "label" to it.optString("label","Item"),
                "icon" to it.optString("icon",""),
                "actionId" to it.optString("actionId","")
            ))
        }
        return obj("type" to "Menu", "id" to id, "items" to items)
    }

    /** Sheet Core‑8 → side panel del renderer (top/left/right). */
    private fun mapSheet(node: JSONObject): JSONObject? {
        val id = node.optString("id").trim().ifBlank { return null }
        val side = node.optString("side","top") // NOTE: bottom non supportato dal renderer, usare "top"
        val itemsCore = node.optJSONArray("items") ?: JSONArray()
        val itemsBlocks = JSONArray().apply {
            for (i in 0 until itemsCore.length()) toBlock(itemsCore.getJSONObject(i))?.let(::put)
        }
        return obj(
            "id" to id,
            "title" to node.optString("title",""),
            "side" to side,
            "widthDp" to node.optDouble("widthDp", 320.0),
            "heightDp" to node.optDouble("heightDp", 0.0),
            "corner" to node.optDouble("corner", 16.0),
            "scrimAlpha" to node.optDouble("scrimAlpha", 0.25),
            "items" to itemsBlocks
        )
    }

    // ---- Page/background ---------------------------------------------------

    private fun mapPage(bg: JSONObject?): JSONObject {
        if (bg == null) return JSONObject()
        val page = JSONObject()
        bg.optString("color","").takeIf { it.isNotBlank() }?.let { page.put("color", it) }
        bg.optJSONObject("gradient")?.let { page.put("gradient", it) }
        bg.optJSONObject("image")?.let { page.put("image", it) }
        return page
    }

    // ---- Helpers -----------------------------------------------------------

    private fun obj(vararg pairs: Pair<String, Any?>): JSONObject =
        JSONObject().apply { pairs.forEach { (k, v) -> if (v != null) put(k, v) } }

    private fun arr(vararg values: Any?): JSONArray =
        JSONArray().apply { values.forEach { if (it != null) put(it) } }
}
