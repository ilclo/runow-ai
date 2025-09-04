package ai.runow.ui.renderer

import android.content.Context
import org.json.JSONObject
import java.io.File

object UiLoader {

    // ---------------------------
    // Helpers I/O
    // ---------------------------
    private fun readAssetText(ctx: Context, path: String): String? = try {
        ctx.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (_: Throwable) { null }

    private fun readFileText(file: File): String? = try {
        if (file.exists()) file.readText(Charsets.UTF_8) else null
    } catch (_: Throwable) { null }

    private fun writeFileText(file: File, text: String): Boolean = try {
        file.parentFile?.mkdirs()
        // scrittura "atomica" semplice: .tmp + rename
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (file.exists()) file.delete()
        tmp.renameTo(file)
        true
    } catch (_: Throwable) { false }

    // ---------------------------
    // Progetti
    // ---------------------------
    private fun sanitizeName(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9._-]"), "_").take(40).ifBlank { "untitled" }

    private fun projectsRoot(ctx: Context) = File(ctx.filesDir, "projects")
    private fun projectRoot(ctx: Context, name: String) = File(projectsRoot(ctx), sanitizeName(name))
    private fun activeFile(ctx: Context) = File(projectsRoot(ctx), "active.txt")
    private fun manifestFile(ctx: Context, name: String) = File(projectRoot(ctx, name), "project.json")

    fun listProjects(ctx: Context): List<String> {
        val root = projectsRoot(ctx)
        return root.listFiles { f -> f.isDirectory }?.map { it.name }?.sorted() ?: emptyList()
    }

    fun getActiveProject(ctx: Context): String? =
        readFileText(activeFile(ctx))?.trim()?.ifBlank { null }

    fun setActiveProject(ctx: Context, name: String): Boolean {
        projectsRoot(ctx).mkdirs()
        return writeFileText(activeFile(ctx), sanitizeName(name))
    }

    fun clearActiveProject(ctx: Context): Boolean {
        val f = activeFile(ctx)
        return if (f.exists()) f.delete() else true
    }

    private fun emptyScreen(): JSONObject = JSONObject("""{"type":"Column","blocks":[]}""")

    /**
     * Crea un nuovo progetto:
     * - cartella /files/projects/<name>/
     * - manifest
     * - drafts/ui/{run,settings,music}.json -> vuoti (nessun contenuto preimpostato)
     * - imposta come progetto attivo
     */
    fun createProject(ctx: Context, name: String): Boolean {
        val nn = sanitizeName(name)
        val root = projectRoot(ctx, nn)
        if (!root.exists()) root.mkdirs()

        val manifest = JSONObject()
            .put("name", nn)
            .put("createdAt", System.currentTimeMillis())
            .put("version", 1)
        if (!writeFileText(manifestFile(ctx, nn), manifest.toString(2))) return false

        val drafts = File(root, "drafts/ui")
        drafts.mkdirs()
        listOf("run", "settings", "music").forEach { scr ->
            val f = File(drafts, "$scr.json")
            if (!f.exists()) writeFileText(f, emptyScreen().toString())
        }

        return setActiveProject(ctx, nn)
    }

    fun deleteProject(ctx: Context, name: String): Boolean {
        val root = projectRoot(ctx, name)
        fun rmRec(f: File) {
            if (f.isDirectory) f.listFiles()?.forEach { rmRec(it) }
            f.delete()
        }
        if (root.exists()) rmRec(root)
        // se cancelli l’attivo, pulisci active.txt
        if (getActiveProject(ctx) == sanitizeName(name)) clearActiveProject(ctx)
        return true
    }

    // ---------------------------
    // Caricamento / Salvataggio layout (compat con API esistenti)
    // ---------------------------

    /**
     * Carica layout per "screen" (es.: "run", "settings", "music").
     * Precedenza:
     *  - se c’è un progetto attivo: drafts -> published (del progetto)
     *  - altrimenti: published legacy in /files -> assets -> empty
     */
    fun loadLayout(ctx: Context, screen: String): JSONObject? {
        val active = getActiveProject(ctx)
        if (active != null) {
            val proj = projectRoot(ctx, active)
            val d = File(proj, "drafts/ui/$screen.json")
            val p = File(proj, "published/ui/$screen.json")
            readFileText(d)?.let { return JSONObject(it) }
            readFileText(p)?.let { return JSONObject(it) }
            return emptyScreen()
        }

        // Legacy: nessun progetto attivo
        val pubLegacy = File(ctx.filesDir, "published/ui/$screen.json")
        readFileText(pubLegacy)?.let { return JSONObject(it) }
        readAssetText(ctx, "configs/ui/$screen.json")?.let { return JSONObject(it) }
        return emptyScreen()
    }

    fun loadDraft(ctx: Context, screen: String): JSONObject? {
        val active = getActiveProject(ctx)
        if (active != null) {
            val d = File(projectRoot(ctx, active), "drafts/ui/$screen.json")
            return readFileText(d)?.let { JSONObject(it) }
        }
        val d = File(ctx.filesDir, "drafts/ui/$screen.json")
        return readFileText(d)?.let { JSONObject(it) }
    }

    fun saveDraft(ctx: Context, screen: String, json: JSONObject): Boolean {
        val active = getActiveProject(ctx)
        if (active != null) {
            val d = File(projectRoot(ctx, active), "drafts/ui/$screen.json")
            return writeFileText(d, json.toString(2))
        }
        val d = File(ctx.filesDir, "drafts/ui/$screen.json")
        return writeFileText(d, json.toString(2))
    }

    fun publish(ctx: Context, screen: String): Boolean {
        val active = getActiveProject(ctx)
        if (active != null) {
            val proj = projectRoot(ctx, active)
            val d = File(proj, "drafts/ui/$screen.json")
            val p = File(proj, "published/ui/$screen.json")
            val txt = readFileText(d) ?: return false
            return writeFileText(p, txt)
        }
        val d = File(ctx.filesDir, "drafts/ui/$screen.json")
        val p = File(ctx.filesDir, "published/ui/$screen.json")
        val txt = readFileText(d) ?: return false
        return writeFileText(p, txt)
    }

    fun resetPublished(ctx: Context, screen: String): Boolean {
        val active = getActiveProject(ctx)
        if (active != null) {
            val p = File(projectRoot(ctx, active), "published/ui/$screen.json")
            return if (p.exists()) p.delete() else true
        }
        val p = File(ctx.filesDir, "published/ui/$screen.json")
        return if (p.exists()) p.delete() else true
    }

    /**
     * Lista schermate “note” nel progetto (o fallback legacy).
     * Finché non c’è navigazione tra pagine, manteniamo run/settings/music.
     * Se nel progetto sono presenti altri file .json, li includiamo.
     */
    fun listScreens(ctx: Context): List<String> {
        val active = getActiveProject(ctx)
        if (active != null) {
            val draftsDir = File(projectRoot(ctx, active), "drafts/ui")
            val names = draftsDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.map {
                it.name.removeSuffix(".json")
            }?.sorted() ?: emptyList()
            return if (names.isNotEmpty()) names else listOf("run", "settings", "music")
        }
        return listOf("run", "settings", "music")
    }
}
