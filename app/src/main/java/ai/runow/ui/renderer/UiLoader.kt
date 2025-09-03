package ai.runow.ui.renderer

import android.content.Context
import org.json.JSONObject
import java.io.File

object UiLoader {
    private fun readAssetText(ctx: Context, path: String): String? = try {
        ctx.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (_: Throwable) { null }

    private fun readFileText(file: File): String? = try {
        if (file.exists()) file.readText(Charsets.UTF_8) else null
    } catch (_: Throwable) { null }

    private fun writeFileText(file: File, text: String): Boolean = try {
        file.parentFile?.mkdirs()
        file.writeText(text, Charsets.UTF_8)
        true
    } catch (_: Throwable) { false }

    private fun blank(screen: String): JSONObject =
        JSONObject("""{"screen":"$screen","blocks":[]}""")

    /**
     * Carica il layout pubblicato se presente; in caso contrario
     * restituisce **sempre** un layout vuoto (niente fallback da asset).
     * Così l'app parte da "foglio bianco".
     */
    fun loadLayout(ctx: Context, screen: String): JSONObject {
        val pub = File(ctx.filesDir, "published/ui/$screen.json")
        readFileText(pub)?.let { return JSONObject(it) }

        // NIENTE fallback da assets: partiamo vuoti per il Designer.
        // Se vuoi ripristinare un asset in futuro:
        // readAssetText(ctx, "configs/ui/$screen.json")?.let { return JSONObject(it) }

        return blank(screen)
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
     * Niente default "run/settings/music". Torna una lista vuota.
     * (Se in futuro vuoi elencare i file esistenti su disco,
     * cambia la firma accettando il Context e scandisci drafts/published.)
     */
    fun listScreens(): List<String> = emptyList()
}
