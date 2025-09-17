package ai.runow.ui.renderer

import org.json.JSONArray
import org.json.JSONObject


fun newProgress() = JSONObject("""{ "type":"Progress", "label":"Avanzamento", "value": 40, "color": "primary", "showPercent": true }""")


fun newRow() = JSONObject(
    """{"type":"Row","gapDp":8,"items":[
      {"type":"SectionHeader","title":"A"},
      {"type":"SpacerH","mode":"expand"},
      {"type":"SectionHeader","title":"B"}
  ]}"""
)

fun newAlert()    = JSONObject("""{ "type":"Alert", "severity":"info", "title":"Titolo avviso", "message":"Testo dell'avviso", "actionId": "" }""")


fun newImage()    = JSONObject("""{ "type":"Image", "source":"res:ic_launcher_foreground", "heightDp": 160, "corner": 12, "contentScale":"fit" }""")


fun newSectionHeader() = JSONObject("""{"type":"SectionHeader","title":"Nuova sezione"}""")


fun newButtonRow() = JSONObject(
    """
{"type":"ButtonRow","align":"center","buttons":[
{"label":"Start","style":"primary","icon":"play_arrow","size":"md","tint":"default","shape":"rounded","corner":20,"pressEffect":"scale","actionId":"start_run"},
{"label":"Pausa","style":"tonal","icon":"pause","size":"md","tint":"default","shape":"rounded","corner":20,"actionId":"pause_run"},
{"label":"Stop","style":"outlined","icon":"stop","size":"md","tint":"error","shape":"rounded","corner":20,"actionId":"stop_run","confirm":true}
]}
""".trimIndent()
)

fun newList() = JSONObject(
    """
{"type":"List","align":"start","items":[
{"title":"Voce 1","subtitle":"Sottotitolo 1","actionId":"list:1"},
{"title":"Voce 2","subtitle":"Sottotitolo 2","actionId":"list:2"}
]}
""".trimIndent()
)


fun newSpacer()   = JSONObject("""{"type":"Spacer","height":8}""")


fun newDividerV() = JSONObject("""{"type":"DividerV","thickness":1,"height":24}""")


fun newCard() = JSONObject(
    """
{"type":"Card","variant":"elevated","clickActionId":"nav:run",
"blocks":[
{"type":"SectionHeader","title":"Card esempio","style":"titleSmall","align":"start"},
{"type":"Divider"}
]}
""".trimIndent()
)

fun newFab() = JSONObject("""{"type":"Fab","icon":"play_arrow","label":"Start","variant":"extended","actionId":"start_run"}""")


fun newChipRow() = JSONObject(
    """
{"type":"ChipRow","chips":[
{"label":"Easy","bind":"level","value":"easy"},
{"label":"Medium","bind":"level","value":"medium"},
{"label":"Hard","bind":"level","value":"hard"}
], "textSizeSp":14}
""".trimIndent()
)

fun newSlider() = JSONObject("""{"type":"Slider","label":"Pace","bind":"pace","min":3.0,"max":7.0,"step":0.1,"unit":" min/km"}""")

fun newToggle() = JSONObject("""{"type":"Toggle","label":"Attiva opzione","bind":"toggle_1"}""")


fun newTabs() = JSONObject(
    """
{"type":"Tabs","initialIndex":0,"tabs":[
{"label":"Tab 1","blocks":[{"type":"SectionHeader","title":"Tab 1","style":"titleSmall","align":"start"}]},
{"label":"Tab 2","blocks":[{"type":"SectionHeader","title":"Tab 2","style":"titleSmall","align":"start"}]}
]}
""".trimIndent()
)



fun newMetricsGrid() = JSONObject("""{"type":"MetricsGrid","columns":2,"tiles":[{"label":"Pace"},{"label":"Heart"}]}""")


fun newIconButton(menuId: String = "more_menu") =
    JSONObject("""{"type":"IconButton","icon":"more_vert","openMenuId":"$menuId"}""")

fun newMenu(menuId: String = "more_menu") = JSONObject(
    """
{"type":"Menu","id":"$menuId","items":[
{"icon":"tune","label":"Layout Lab","actionId":"open_layout_lab"},
{"icon":"palette","label":"Theme Lab","actionId":"open_theme_lab"},
{"icon":"settings","label":"Impostazioni","actionId":"nav:settings"}
]}
""".trimIndent()
)

fun newSpacerH() = JSONObject("""{"type":"SpacerH","mode":"expand","widthDp":16}""")

