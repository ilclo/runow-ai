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

    /** Carica il layout con precedenza: published -> draft -> VUOTO (niente asset). */
    fun loadLayout(ctx: Context, screen: String): JSONObject {
        val pub = File(ctx.filesDir, "published/ui/$screen.json")
        readFileText(pub)?.let { return JSONObject(it) }

        val draft = File(ctx.filesDir, "drafts/ui/$screen.json")
        readFileText(draft)?.let { return JSONObject(it) }

        // Avvio "pulito": nessun fallback all'asset per evitare run/settings/music.
        return JSONObject("""{"blocks":[]}""")
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

    /** Restituisce la lista di schermate salvate localmente (pubblicate o in bozza). */
    fun listScreens(ctx: Context): List<String> {
        val names = linkedSetOf<String>()
        val pubDir = File(ctx.filesDir, "published/ui")
        val draDir = File(ctx.filesDir, "drafts/ui")

        pubDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.forEach {
            names += it.name.removeSuffix(".json")
        }
        draDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.forEach {
            names += it.name.removeSuffix(".json")
        }
        return names.toList().sorted()
    }

    /** Back-compat: evita di forzare run/settings/music. */
    @Deprecated("Usa listScreens(ctx)")
    fun listScreens(): List<String> = emptyList()
}
