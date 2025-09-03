package ai.runow.ui.renderer

import android.content.Context
import org.json.JSONObject
import java.io.File

object UiLoader {
    // --- IO helper ---
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

    // --- Defaults ---
    /** Layout di fallback: pagina vuota (nessun blocco). */
    fun defaultBlank(): JSONObject = JSONObject("""{"blocks":[]}""")

    // --- Lettura stati ---
    /** Bozza locale (modifiche non pubblicate). */
    fun loadDraft(ctx: Context, screen: String): JSONObject? {
        val d = File(ctx.filesDir, "drafts/ui/$screen.json")
        return readFileText(d)?.let { JSONObject(it) }
    }

    /** Stato pubblicato (locale). */
    fun loadPublished(ctx: Context, screen: String): JSONObject? {
        val p = File(ctx.filesDir, "published/ui/$screen.json")
        return readFileText(p)?.let { JSONObject(it) }
    }

    /**
     * Layout “di progetto”: prima published, altrimenti asset (configs/ui/<screen>.json).
     * Nota: NON è usata per lo startup, dove preferiamo partire vuoti (vedi loadInitial).
     */
    fun loadLayout(ctx: Context, screen: String): JSONObject? {
        loadPublished(ctx, screen)?.let { return it }
        val asset = readAssetText(ctx, "configs/ui/$screen.json") ?: return null
        return JSONObject(asset)
    }

    /**
     * Startup “pulito”: draft -> published -> blank.
     * Niente asset di default, così non partiamo con schermate precompilate.
     */
    fun loadInitial(ctx: Context, screen: String): JSONObject {
        loadDraft(ctx, screen)?.let { return it }
        loadPublished(ctx, screen)?.let { return it }
        return defaultBlank()
    }

    // --- Salvataggi ---
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

    // Eventuale discovery (per ora statica)
    fun listScreens(): List<String> = listOf("run", "settings", "music")
}
