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

    fun loadLayout(ctx: Context, screen: String): JSONObject? {
        // 1) Preferisci bozza locale per continuare l'editing
        val d = File(ctx.filesDir, "drafts/ui/$screen.json")
        readFileText(d)?.let { return JSONObject(it) }

        // 2) Poi pubblicato
        val p = File(ctx.filesDir, "published/ui/$screen.json")
        readFileText(p)?.let { return JSONObject(it) }

        // 3) Poi asset di default
        readAssetText(ctx, "configs/ui/$screen.json")?.let { return JSONObject(it) }

        // 4) Fallback: layout vuoto
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

    // Avvio pulito: un'unica "home" vuota (non usiamo più run/settings/music)
    fun listScreens(): List<String> = listOf("home")
}
