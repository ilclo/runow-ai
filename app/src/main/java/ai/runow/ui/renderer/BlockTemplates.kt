package ai.runow.ui.renderer

import org.json.JSONArray
import org.json.JSONObject

/** Di seguito template minimi compatibili con il renderer corrente. */

fun newProgress(): JSONObject = JSONObject().apply {
    put("type", "progress")
    put("indeterminate", true)
}

fun newRow(): JSONObject = JSONObject().apply {
    put("type", "row")
    put("gapDp", 8)
    put("scrollable", false)
    put("items", JSONArray())
}

fun newAlert(): JSONObject = JSONObject().apply {
    put("type", "alert")
    put("style", "info") // info|warning|error|success
    put("text", "Alert")
}

fun newImage(): JSONObject = JSONObject().apply {
    put("type", "image")
    put("uri", "")
    put("contentScale", "crop")
    put("corner", 0)
}

fun newSectionHeader(): JSONObject = JSONObject().apply {
    put("type", "section_header")
    put("title", "Section")
    put("subtitle", "")
}

fun newButtonRow(): JSONObject = JSONObject().apply {
    put("type", "button_row")
    put("items", JSONArray()) // ciascun item: { "type":"button","label":"...","actionId":"...", "style":"filled|tonal|outlined|text" }
}

fun newList(): JSONObject = JSONObject().apply {
    put("type", "list")
    put("items", JSONArray()) // elementi lista
}

fun newSpacer(): JSONObject = JSONObject().apply {
    put("type", "spacer")
    put("heightDp", 16)
}

fun newDividerV(): JSONObject = JSONObject().apply {
    put("type", "divider")
    put("orientation", "vertical")
}

fun newCard(): JSONObject = JSONObject().apply {
    put("type", "card")
    put("blocks", JSONArray())
}

fun newFab(): JSONObject = JSONObject().apply {
    put("type", "fab")
    put("icon", "add")
    put("actionId", "")
}

fun newChipRow(): JSONObject = JSONObject().apply {
    put("type", "chip_row")
    put("items", JSONArray())
}

fun newSlider(): JSONObject = JSONObject().apply {
    put("type", "slider")
    put("value", 0.5)
    put("step", 0.0)
    put("rangeMin", 0.0)
    put("rangeMax", 1.0)
}

fun newToggle(): JSONObject = JSONObject().apply {
    put("type", "toggle")
    put("checked", false)
    put("label", "Toggle")
}

fun newTabs(): JSONObject = JSONObject().apply {
    put("type", "tabs")
    put("tabs", JSONArray().apply {
        put(JSONObject().apply { put("label", "Tab 1") })
        put(JSONObject().apply { put("label", "Tab 2") })
    })
    put("selected", 0)
}

fun newMetricsGrid(): JSONObject = JSONObject().apply {
    put("type", "metrics_grid")
    put("items", JSONArray())
}

fun newIconButton(): JSONObject = JSONObject().apply {
    put("type", "button")
    put("style", "icon")
    put("icon", "more_vert")
    put("actionId", "")
}

fun newMenu(): JSONObject = JSONObject().apply {
    put("type", "menu")
    put("items", JSONArray())
}
