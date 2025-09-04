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

    // ====== MODIFICA QUI: rendi "vuoto" il fallback ======
    fun loadLayout(ctx: Context, screen: String): JSONObject? {
        // 1) published
        readFileText(File(ctx.filesDir, "published/ui/$screen.json"))?.let { return JSONObject(it) }
        // 2) draft
        readFileText(File(ctx.filesDir, "drafts/ui/$screen.json"))?.let { return JSONObject(it) }
        // 3) asset (DISABILITATO per partire vuoto) -> commentato:
        // val asset = readAssetText(ctx, "configs/ui/$screen.json")
        // if (asset != null) return JSONObject(asset)

        // 4) FALLBACK: layout minimo vuoto (modificabile dal Designer)
        return JSONObject("""{"blocks": []}""")
    }
    // =====================================================

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

    // Se in qualche punto listi le schermate, parti pure da solo "home"
    fun listScreens(): List<String> = listOf("home")
}
