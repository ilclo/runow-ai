package ai.runow.ui.renderer

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * UiLoader "project-aware"
 *
 * Struttura on-disk (per progetto "MyProject"):
 * filesDir/
 *   projects/
 *     MyProject/
 *       drafts/ui/<screen>.json
 *       published/ui/<screen>.json
 *
 * Attivo progetto salvato in SharedPreferences.
 * Nessun fallback automatico ad assets: l'app può partire vuota.
 */
object UiLoader {

    // ======================
    // Preferences (progetto attivo)
    // ======================
    private const val PREFS = "ui_designer"
    private const val KEY_ACTIVE = "active_project"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getActiveProject(ctx: Context): String? =
        prefs(ctx).getString(KEY_ACTIVE, null)

    fun setActiveProject(ctx: Context, name: String) {
        prefs(ctx).edit().putString(KEY_ACTIVE, name).apply()
    }

    fun clearActiveProject(ctx: Context) {
        prefs(ctx).edit().remove(KEY_ACTIVE).apply()
    }

    // ======================
    // File utils
    // ======================
    private fun File.safeMkdirs(): Boolean =
        try { if (exists()) true else mkdirs() } catch (_: Throwable) { false }

    private fun readText(file: File): String? = try {
        if (file.exists()) file.readText(Charsets.UTF_8) else null
    } catch (_: Throwable) { null }

    private fun writeText(file: File, text: String): Boolean = try {
        file.parentFile?.safeMkdirs()
        file.writeText(text, Charsets.UTF_8)
        true
    } catch (_: Throwable) { false }

    // ======================
    // Progetti
    // ======================
    fun listProjects(ctx: Context): List<String> {
        val root = File(ctx.filesDir, "projects")
        val arr = root.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
        return arr.sorted()
    }

    fun createProject(ctx: Context, name: String): Boolean {
        if (name.isBlank()) return false
        val root = File(ctx.filesDir, "projects/$name")
        val drafts = File(root, "drafts/ui")
        val published = File(root, "published/ui")
        return root.safeMkdirs() && drafts.safeMkdirs() && published.safeMkdirs()
    }

    // ======================
    // Directory di progetto
    // ======================
    private fun projectDir(ctx: Context): File? {
        val p = getActiveProject(ctx) ?: return null
        return File(ctx.filesDir, "projects/$p")
    }

    private fun draftsDir(ctx: Context): File? =
        projectDir(ctx)?.let { File(it, "drafts/ui") }

    private fun publishedDir(ctx: Context): File? =
        projectDir(ctx)?.let { File(it, "published/ui") }

    // ======================
    // Screens
    // ======================
    /**
     * Ritorna l’unione dei nomi screen presenti in drafts e in published.
     * Nessun default: se vuoto, l’app parte senza schermate.
     */
    fun listScreens(ctx: Context): List<String> {
        val d = draftsDir(ctx)
        val p = publishedDir(ctx)
        val names = linkedSetOf<String>()
        d?.listFiles()?.filter { it.isFile && it.extension == "json" }?.forEach {
            names += it.nameWithoutExtension
        }
        p?.listFiles()?.filter { it.isFile && it.extension == "json" }?.forEach {
            names += it.nameWithoutExtension
        }
        return names.toList().sorted()
    }

    // ======================
    // Lettura / Scrittura layout
    // ======================
    /**
     * Ordine di precedenza: drafts -> published.
     * Nessun fallback ad assets, così non compaiono "run/settings/music" automaticamente.
     */
    fun loadLayout(ctx: Context, screen: String): JSONObject? {
        val d = draftsDir(ctx) ?: return null
        val p = publishedDir(ctx) ?: return null

        readText(File(d, "$screen.json"))?.let { return JSONObject(it) }
        readText(File(p, "$screen.json"))?.let { return JSONObject(it) }
        return null
    }

    fun loadDraft(ctx: Context, screen: String): JSONObject? {
        val d = draftsDir(ctx) ?: return null
        return readText(File(d, "$screen.json"))?.let { JSONObject(it) }
    }

    fun saveDraft(ctx: Context, screen: String, json: JSONObject): Boolean {
        val d = draftsDir(ctx) ?: return false
        return writeText(File(d, "$screen.json"), json.toString(2))
    }

    fun publish(ctx: Context, screen: String): Boolean {
        val draft = loadDraft(ctx, screen) ?: return false
        val p = publishedDir(ctx) ?: return false
        return writeText(File(p, "$screen.json"), draft.toString(2))
    }

    fun resetPublished(ctx: Context, screen: String): Boolean {
        val p = File(publishedDir(ctx) ?: return false, "$screen.json")
        return if (p.exists()) p.delete() else true
    }
}
