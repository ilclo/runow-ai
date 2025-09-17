package ai.runow.ui.renderer

import org.json.JSONArray
import org.json.JSONObject

/** Ritorna il nodo (JSONObject/JSONArray/valore) alla path tipo "/blocks/3/items/1". */
fun jsonAtPath(root: JSONObject, path: String): Any?

/** Sostituisce il nodo alla path con newNode. Crea strutture intermedie se serve. */
fun replaceAtPath(root: JSONObject, path: String, newNode: Any)

/** Inserisce item in un JSONArray alla posizione index (0..length). */
fun insertAt(array: JSONArray, index: Int, item: Any)

/** Rimuove l’elemento in posizione index da un JSONArray e lo restituisce. */
fun removeAt(array: JSONArray, index: Int): Any?

/** Sposta l’elemento `from` → `to` nello stesso JSONArray. */
fun moveInArray(array: JSONArray, from: Int, to: Int)

/** Deep-copy “sicura” di un JSONObject (per duplicazioni). */
fun duplicate(obj: JSONObject): JSONObject

/** Inserisce un blocco e restituisce la path del nuovo blocco. */
fun insertBlockAndReturnPath(root: JSONObject, parentPath: String, newBlock: JSONObject, atIndex: Int? = null): String

/** Inserisce un’icona in un menu e ritorna la path dell’icona. */
fun insertIconMenuReturnIconPath(root: JSONObject, menuPath: String, iconCfg: JSONObject, atIndex: Int? = null): String

/** Sposta un nodo tra container (stessa pagina) e restituisce la nuova path. */
fun moveAndReturnNewPath(root: JSONObject, fromPath: String, toParentPath: String, toIndex: Int? = null): String
