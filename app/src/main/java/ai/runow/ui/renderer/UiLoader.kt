package ai.runow.ui.renderer

import android.content.Context
import org.json.JSONObject
import java.io.File

object UiLoader {
    // Preferenze per il "progetto attivo"
    private const val PREFS = "ui_designer"
    private const val KEY_ACTIVE_PROJECT = "active_project"

    // ---------------------------
    // Helper IO
    // ---------------------------
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

    private fun projectsRoot(ctx: Context) = File(ctx.filesDir, "projects")
    private fun sanitize(name: String) = name.replace(Regex("[^A-Za-z0-9_\\-]"), "_").take(50)
    private fun projectDir(ctx: Context, project: String) = File(projectsRoot(ctx), sanitize(project))
    private fun draftsDir(ctx: Context, project: String) = File(projectDir(ctx, project), "drafts/ui")
    private fun publishedDir(ctx: Context, project: String) = File(projectDir(ctx, project), "published/ui")

    // ---------------------------
    // Gestione progetto attivo
    // ---------------------------
    fun getActiveProject(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACTIVE_PROJECT, null)

    fun setActiveProject(ctx: Context, name: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACTIVE_PROJECT, sanitize(name)).apply()
    }

    fun clearActiveProject(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_ACTIVE_PROJECT).apply()
    }

    // ---------------------------
    // Progetti
    // ---------------------------
    fun listProjects(ctx: Context): List<String> =
        projectsRoot(ctx).listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted()
            ?: emptyList()

    fun createProject(ctx: Context, name: String): Boolean {
        val n = sanitize(name)
        val d = draftsDir(ctx, n)
        val p = publishedDir(ctx, n)
        return try {
            d.mkdirs(); p.mkdirs()
            // crea home vuota in bozza se non esiste
            val home = File(d, "home.json")
            if (!home.exists()) writeFileText(home, """{"blocks": []}""") else true
        } catch (_: Throwable) { false }
    }

    // ---------------------------
    // Schermate
    // ---------------------------
    // Overload senza argomenti per compatibilità (se veniva usato altrove)
    fun listScreens(): List<String> = listOf("home")

    fun listScreens(ctx: Context): List<String> = listScreens(ctx, getActiveProject(ctx))

    // Overload con 2 argomenti (quello davvero usato da UiRenderer)
    fun listScreens(ctx: Context, project: String?): List<String> {
        val prj = project?.takeIf { it.isNotBlank() } ?: return listOf("home")
        val drafts = draftsDir(ctx, prj)
        val publ = publishedDir(ctx, prj)
        val set = linkedSetOf<String>()
        drafts.listFiles { f -> f.isFile && f.extension == "json" }?.forEach { set += it.nameWithoutExtension }
        publ.listFiles { f -> f.isFile && f.extension == "json" }?.forEach { set += it.nameWithoutExtension }
        return if (set.isEmpty()) listOf("home") else set.sorted()
    }

    // Overload con 3 argomenti (se in passato c’era un terzo flag, lo ignoriamo)
    fun listScreens(ctx: Context, project: String?, @Suppress("UNUSED_PARAMETER") includeDraftsFirst: Boolean): List<String> =
        listScreens(ctx, project)

    // ---------------------------
    // Load/Save Layout
    // ---------------------------
    // Fallback "vuoto" per partire senza run/settings/music
    fun loadLayout(ctx: Context, screen: String): JSONObject? {
        val prj = getActiveProject(ctx)
        if (!prj.isNullOrBlank()) {
            readFileText(File(publishedDir(ctx, prj), "$screen.json"))?.let { return JSONObject(it) }
            readFileText(File(draftsDir(ctx, prj), "$screen.json"))?.let { return JSONObject(it) }
            return JSONObject("""{"blocks": []}""")
        } else {
            // legacy single‑project
            readFileText(File(ctx.filesDir, "published/ui/$screen.json"))?.let { return JSONObject(it) }
            readFileText(File(ctx.filesDir, "drafts/ui/$screen.json"))?.let { return JSONObject(it) }
            // assets disabilitati per evitare layout precompilati
            // val asset = readAssetText(ctx, "configs/ui/$screen.json")
            // if (asset != null) return JSONObject(asset)
            return JSONObject("""{"blocks": []}""")
        }
    }

    fun loadDraft(ctx: Context, screen: String): JSONObject? {
        val prj = getActiveProject(ctx)
        val file = if (!prj.isNullOrBlank())
            File(draftsDir(ctx, prj), "$screen.json")
        else File(ctx.filesDir, "drafts/ui/$screen.json")
        return readFileText(file)?.let { JSONObject(it) }
    }

    fun saveDraft(ctx: Context, screen: String, json: JSONObject): Boolean {
        val prj = getActiveProject(ctx)
        val file = if (!prj.isNullOrBlank())
            File(draftsDir(ctx, prj), "$screen.json")
        else File(ctx.filesDir, "drafts/ui/$screen.json")
        return writeFileText(file, json.toString(2))
    }

    fun publish(ctx: Context, screen: String): Boolean {
        val prj = getActiveProject(ctx)
        return if (!prj.isNullOrBlank()) {
            val d = File(draftsDir(ctx, prj), "$screen.json")
            val p = File(publishedDir(ctx, prj), "$screen.json")
            val txt = readFileText(d) ?: return false
            writeFileText(p, txt)
        } else {
            val d = File(ctx.filesDir, "drafts/ui/$screen.json")
            val p = File(ctx.filesDir, "published/ui/$screen.json")
            val txt = readFileText(d) ?: return false
            writeFileText(p, txt)
        }
    }

    fun resetPublished(ctx: Context, screen: String): Boolean {
        val prj = getActiveProject(ctx)
        val p = if (!prj.isNullOrBlank())
            File(publishedDir(ctx, prj), "$screen.json")
        else File(ctx.filesDir, "published/ui/$screen.json")
        return if (p.exists()) p.delete() else true
    }
}
