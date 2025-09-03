package ai.runow.ui.renderer

import android.content.Context
import org.json.JSONObject
import java.io.File

object UiLoader {
    private fun readFileText(file: File): String? = try {
        if (file.exists()) file.readText(Charsets.UTF_8) else null
    } catch (_: Throwable) { null }

    private fun writeFileText(file: File, text: String): Boolean = try {
        file.parentFile?.mkdirs()
        file.writeText(text, Charsets.UTF_8)
        true
    } catch (_: Throwable) { false }

    private fun emptyDocument(): JSONObject = JSONObject("""{"blocks": []}""")

    /**
     * Regola richiesta: avvio "pulito".
     * Non carichiamo più i layout di default dagli asset.
     * Se non esistono bozza o pubblicato locali, ritorniamo un documento vuoto.
     */
    fun loadLayout(ctx: Context, screen: String): JSONObject {
        val draft = File(ctx.filesDir, "drafts/ui/$screen.json")
        readFileText(draft)?.let { return JSONObject(it) }

        val pub = File(ctx.filesDir, "published/ui/$screen.json")
        readFileText(pub)?.let { return JSONObject(it) }

        // start from an empty page
        return emptyDocument()
    }

    fun loadDraft(ctx: Context, screen: String): JSONObject? {
        val d = File(ctx.filesDir, "drafts/ui/$screen.json")
        return readFileText(d)?.let { JSONObject(it) }
    }

    fun saveDraft(ctx: Context, screen: String, json: JSONObject): Boolean {
        val d = File(ctx.filesDir, "drafts/ui/$screen.json")
        return writeFileText(d, json.toString(2))
    }

    fun publish(ctx: Context, screen: String): Boolean {
        val d = File(ctx.filesDir, "drafts/ui/$screen.json")
        val p = File(ctx.filesDir, "published/ui/$screen.json")
        val txt = readFileText(d) ?: return false
        return writeFileText(p, txt)
    }

    fun resetPublished(ctx: Context, screen: String): Boolean {
        val p = File(ctx.filesDir, "published/ui/$screen.json")
        return if (p.exists()) p.delete() else true
    }

    /**
     * Manteniamo la firma esistente; l’elenco schermate è gestito altrove.
     * Il contenuto iniziale sarà vuoto grazie a loadLayout().
     */
    fun listScreens(): List<String> = listOf("run", "settings", "music")
}
