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

    // Fallback vuoto + asset disabilitati (come avevi)
    fun loadLayout(ctx: Context, screen: String): JSONObject? {
        readFileText(File(ctx.filesDir, "published/ui/$screen.json"))?.let { return JSONObject(it) }
        readFileText(File(ctx.filesDir, "drafts/ui/$screen.json"))?.let { return JSONObject(it) }
        // Se vuoi riattivare gli asset, de-commenta le 2 righe seguenti:
        // readAssetText(ctx, "configs/ui/$screen.json")?.let { return JSONObject(it) }
        return JSONObject("""{"blocks": []}""")
    }

    // 👉 Comodità: da dove è stato caricato (published/draft/asset/empty)
    fun layoutSource(ctx: Context, screen: String): String {
        if (File(ctx.filesDir, "published/ui/$screen.json").exists()) return "published"
        if (File(ctx.filesDir, "drafts/ui/$screen.json").exists()) return "draft"
        // if (readAssetText(ctx, "configs/ui/$screen.json") != null) return "asset"
        return "empty"
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

    // 👉 Bonifica totale: elimina tutti i layout locali (published + drafts)
    fun resetAll(ctx: Context) {
        File(ctx.filesDir, "published").deleteRecursively()
        File(ctx.filesDir, "drafts").deleteRecursively()
    }

    // Per mostrare un picker di schermate locali (senza asset)
    fun listScreens(ctx: Context): List<String> {
        val names = linkedSetOf<String>()
        File(ctx.filesDir, "published/ui").listFiles()
            ?.filter { it.isFile && it.extension == "json" }?.forEach { names += it.nameWithoutExtension }
        File(ctx.filesDir, "drafts/ui").listFiles()
            ?.filter { it.isFile && it.extension == "json" }?.forEach { names += it.nameWithoutExtension }
        return names.toList().sorted()
    }
}
