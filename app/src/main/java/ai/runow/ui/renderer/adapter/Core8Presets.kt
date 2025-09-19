package ai.runow.ui.renderer.adapter

import org.json.JSONArray
import org.json.JSONObject

object Core8Presets {

    // -------- BUTTON PRIMARIO ----------------------------------------------
    fun ButtonPrimario(
        text: String = "Continua",
        actionId: String = "continue",
        icon: String = ""
    ): JSONObject = obj(
        "type" to "button",
        "text" to text,
        "actionId" to actionId,
        "variant" to "primary",
        "icon" to icon,
        "fullWidth" to true
    )
    fun ButtonPrimarioBlock(text: String = "Continua", actionId: String = "continue", icon: String = ""): JSONObject =
        Core8Adapter.toBlock(ButtonPrimario(text, actionId, icon))!!

    // -------- CARD “MEDIA” --------------------------------------------------
    fun CardMedia(
        title: String = "Titolo",
        subtitle: String = "Sottotitolo",
        body: String = "Testo di esempio",
        imageRes: String = "sample_header",  // usa un drawable: res:sample_header
        primaryAction: JSONObject = ButtonPrimario("Apri", "open")
    ): JSONObject = obj(
        "type" to "card",
        "style" to "surface",
        "image" to obj("res" to imageRes, "fit" to "crop", "heightDp" to 160),
        "title" to title,
        "subtitle" to subtitle,
        "body" to body,
        "actions" to JSONArray().put(primaryAction)
    )
    fun CardMediaBlock(...): JSONObject = Core8Adapter.toBlock(CardMedia(title, subtitle, body, imageRes, primaryAction))!!

    // -------- LIST ITEM -----------------------------------------------------
    fun ListItem(
        title: String = "Elemento",
        subtitle: String = "Dettagli",
        actionId: String = ""
    ): JSONObject = obj(
        "type" to "list_item",
        "title" to title,
        "subtitle" to subtitle,
        "actionId" to actionId
    )
    fun ListItemBlock(title: String = "Elemento", subtitle: String = "Dettagli", actionId: String = ""): JSONObject =
        Core8Adapter.toBlock(ListItem(title, subtitle, actionId))!!

    // -------- APP BAR -------------------------------------------------------
    fun AppBar(
        title: String = "TopBar",
        variant: String = "small",
        actions: JSONArray = JSONArray()
            .put(obj("icon" to "search", "actionId" to "search"))
            .put(obj("icon" to "more_vert", "openMenuId" to "mainMenu"))
    ): JSONObject = obj(
        "type" to "app_bar",
        "title" to title,
        "variant" to variant,
        "actions" to actions,
        "container" to obj("style" to "surface", "borderMode" to "full", "borderThicknessDp" to 2)
    )

    // -------- SHEET ---------------------------------------------------------
    fun Sheet(
        id: String = "filters",
        title: String = "Filtri",
        side: String = "top",
        items: JSONArray = JSONArray().put(ButtonPrimario("Applica", "apply_filters"))
    ): JSONObject = obj(
        "type" to "sheet",
        "id" to id,
        "title" to title,
        "side" to side,
        "widthDp" to 360,
        "heightDp" to 0,
        "scrimAlpha" to 0.25,
        "items" to items
    )

    // -------- MENU ----------------------------------------------------------
    fun Menu(
        id: String = "mainMenu",
        items: JSONArray = JSONArray()
            .put(obj("label" to "Impostazioni", "icon" to "settings", "actionId" to "settings"))
            .put(obj("label" to "Logout", "icon" to "logout", "actionId" to "logout"))
    ): JSONObject = obj(
        "type" to "menu",
        "id" to id,
        "items" to items
    )

    // -------- SCHERMATA DI ESEMPIO COMPLETA --------------------------------
    fun DemoScreen(): JSONObject {
        val core = obj(
            "type" to "screen",
            "background" to obj("color" to "background"),
            "app_bar" to AppBar("Home"),
            "menus" to JSONArray().put(Menu()),              // registra il menu 'mainMenu'
            "sheets" to JSONArray().put(Sheet()),            // registra uno sheet (side panel)
            "children" to JSONArray()
                .put(CardMedia())
                .put(ListItem())
                .put(ButtonPrimario())
        )
        return Core8Adapter.fromCore8Screen(core)
    }

    // -- helpers
    private fun obj(vararg pairs: Pair<String, Any?>): JSONObject =
        JSONObject().apply { pairs.forEach { (k, v) -> if (v != null) put(k, v) } }
}
