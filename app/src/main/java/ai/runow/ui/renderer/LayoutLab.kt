package ai.runow.ui.renderer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun LayoutLabScreen(onPublished: () -> Unit = {}) {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf("run") }
    var message by remember { mutableStateOf<String?>(null) }
    var json by remember(screen) {
        mutableStateOf(
            UiLoader.loadDraft(ctx, screen) ?: UiLoader.loadLayout(ctx, screen) ?: JSONObject(
                """{"ui_schema_version":"1.0.0","screen":"$screen","blocks":[]}"""
            )
        )
    }

    fun refresh() {
        json = UiLoader.loadDraft(ctx, screen) ?: UiLoader.loadLayout(ctx, screen)
            ?: JSONObject("""{"ui_schema_version":"1.0.0","screen":"$screen","blocks":[]}""")
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {

        Text("Layout Lab (MVP)", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("run","settings","music").forEach {
                FilterChip(selected = it==screen, onClick = { screen = it; refresh() }, label = { Text(it) })
            }
        }

        Divider()

        // Elenco blocchi
        val blocks = json.optJSONArray("blocks") ?: JSONArray().also { json.put("blocks", it) }
        Text("Blocchi (${blocks.length()})", style = MaterialTheme.typography.titleMedium)

        for (i in 0 until blocks.length()) {
            val b = blocks.optJSONObject(i) ?: continue
            ElevatedCard {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${i+1}. ${b.optString("type")}", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = i>0, onClick = {
                            swap(blocks, i, i-1); UiLoader.saveDraft(ctx, screen, json); refresh()
                        }) { Text("↑") }
                        OutlinedButton(enabled = i<blocks.length()-1, onClick = {
                            swap(blocks, i, i+1); UiLoader.saveDraft(ctx, screen, json); refresh()
                        }) { Text("↓") }
                        TextButton(onClick = {
                            remove(blocks, i); UiLoader.saveDraft(ctx, screen, json); refresh()
                        }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Rimuovi") }

                        if (b.optString("type")=="ButtonRow") {
                            FilledTonalButton(onClick = {
                                val buttons = b.optJSONArray("buttons") ?: JSONArray().also { b.put("buttons", it) }
                                buttons.put(JSONObject("""{"label":"Lap","style":"text","icon":"flag","actionId":"lap_mark"}"""))
                                UiLoader.saveDraft(ctx, screen, json); refresh()
                            }) { Text("+ Lap") }
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = {
                // Aggiunge una ButtonRow con Start/Pausa/Stop se non c'è
                val hasRow = (0 until blocks.length()).any { blocks.optJSONObject(it)?.optString("type")=="ButtonRow" }
                if (!hasRow) {
                    blocks.put(JSONObject("""{
                        "type":"ButtonRow","align":"center",
                        "buttons":[
                          {"label":"Start","style":"primary","icon":"play_arrow","actionId":"start_run"},
                          {"label":"Pausa","style":"tonal","icon":"pause","actionId":"pause_run"},
                          {"label":"Stop","style":"outlined","icon":"stop","actionId":"stop_run","confirm":true}
                        ]
                    }""".trimIndent()))
                    UiLoader.saveDraft(ctx, screen, json); refresh()
                }
            }) { Text("Aggiungi ButtonRow standard") }

            OutlinedButton(onClick = {
                UiLoader.resetPublished(ctx, screen)
                message = "Reset eseguito. Torna alla config di default su $screen."
            }) { Text("Reset default") }
        }

        Divider()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val ok = UiLoader.saveDraft(ctx, screen, json)
                message = if (ok) "Bozza salvata per $screen." else "Errore salvataggio bozza."
            }) { Text("Salva Bozza") }

            Button(onClick = {
                val ok = UiLoader.publish(ctx, screen)
                message = if (ok) "Pubblicato $screen. Apri la schermata per vederla." else "Nessuna bozza da pubblicare."
                if (ok) onPublished()
            }) { Text("Pubblica") }
        }

        if (message != null) {
            Text(message!!, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun swap(arr: JSONArray, i: Int, j: Int) {
    if (i==j) return
    val a = arr.get(i)
    val b = arr.get(j)
    arr.put(i, b)
    arr.put(j, a)
}

private fun remove(arr: JSONArray, i: Int) {
    val list = mutableListOf<Any?>()
    for (k in 0 until arr.length()) if (k != i) list.add(arr.get(k))
    while (arr.length() > 0) arr.remove(arr.length()-1)
    list.forEach { arr.put(it) }
}
