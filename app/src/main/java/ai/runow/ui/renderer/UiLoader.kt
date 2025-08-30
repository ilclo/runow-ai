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
        // precedence: published (local) -> asset default
        val pub = File(ctx.filesDir, "published/ui/$screen.json")
        readFileText(pub)?.let { return JSONObject(it) }
        val asset = readAssetText(ctx, "configs/ui/$screen.json") ?: return null
        return JSONObject(asset)
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

    fun listScreens(): List<String> = listOf("run", "settings", "music")
}
