package ai.runow.ui.renderer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object UiLoader {

    /* ========== I/O di base ========== */

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

    /* ========== Helper struttura ========== */

    /**
     * Garantisce che il JSON sia un root valido con almeno "blocks": []
     */
    private fun ensureSkeleton(json: JSONObject?): JSONObject {
        val root = json ?: JSONObject()
        if (!root.has("blocks")) root.put("blocks", JSONArray())
        return root
    }

    /* ========== Caricamento / salvataggio ========== */

    /**
     * Precedenza:
     *  1) published/ui/<screen>.json (se presente)
     *  2) drafts/ui/<screen>.json (se presente)
     *  3) SKELETON VUOTO {"blocks":[]}
     *
     * Non si fa più fallback su assets per evitare pagine precompilate all’avvio.
     */
    fun loadLayout(ctx: Context, screen: String): JSONObject? {
        val published = File(ctx.filesDir, "published/ui/$screen.json")
        readFileText(published)?.let { return ensureSkeleton(JSONObject(it)) }

        val draft = File(ctx.filesDir, "drafts/ui/$screen.json")
        readFileText(draft)?.let { return ensureSkeleton(JSONObject(it)) }

        // Se vuoi mantenere gli asset per *alcune* schermate (es. "settings"),
        // scommenta il blocco sotto e aggiungi i nomi allo set:
        //
        // val useAssetsFor = setOf("settings", "music")
        // if (screen in useAssetsFor) {
        //     readAssetText(ctx, "configs/ui/$screen.json")?.let {
        //         return ensureSkeleton(JSONObject(it))
        //     }
        // }

        // Default: root vuoto
        return ensureSkeleton(null)
    }

    fun loadDraft(ctx: Context, screen: String): JSONObject? {
        val d = File(ctx.filesDir, "drafts/ui/$screen.json")
        return readFileText(d)?.let { ensureSkeleton(JSONObject(it)) }
    }

    fun saveDraft(ctx: Context, screen: String, json: JSONObject): Boolean {
        val d = File(ctx.filesDir, "drafts/ui/$screen.json")
        return writeFileText(d, ensureSkeleton(json).toString(2))
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

    /** Rimuove l’eventuale bozza locale. */
    fun resetDraft(ctx: Context, screen: String): Boolean {
        val d = File(ctx.filesDir, "drafts/ui/$screen.json")
        return if (d.exists()) d.delete() else true
    }

    /** Comodo per ripartire da zero: cancella bozza + pubblicato. */
    fun resetAll(ctx: Context, screen: String): Boolean {
        val a = resetDraft(ctx, screen)
        val b = resetPublished(ctx, screen)
        return a && b
    }

    /**
     * Elenco schermate “note” per l’app. Per ora lo lasciamo statico per
     * mantenere compatibilità, ma in futuro possiamo farlo dinamico
     * scansionando file locali.
     */
    fun listScreens(): List<String> = listOf("run", "settings", "music")
}
