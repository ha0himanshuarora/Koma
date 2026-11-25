package app.koma.launcher.data

import org.json.JSONArray
import org.json.JSONObject

/** One thing in a folder: an app (arg is its key) or an Android widget (arg is the widget id). */
class FolderItem(val widget: Boolean, val arg: String)

/** A drawer folder holding apps and widgets. */
data class Folder(val id: String, val name: String, val items: List<FolderItem>, val hideApps: Boolean = false)

object Folders {
    fun parse(raw: String): List<Folder> {
        if (raw.isBlank()) return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                val items = o.optJSONArray("items") ?: JSONArray()
                Folder(
                    o.getString("id"), o.optString("name", "Folder"),
                    (0 until items.length()).map { j ->
                        val it = items.getJSONObject(j)
                        FolderItem(it.optBoolean("w", false), it.optString("a"))
                    },
                    o.optBoolean("hide", false),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun encode(list: List<Folder>): String {
        val a = JSONArray()
        list.forEach { f ->
            val items = JSONArray()
            f.items.forEach { items.put(JSONObject().put("w", it.widget).put("a", it.arg)) }
            a.put(JSONObject().put("id", f.id).put("name", f.name).put("hide", f.hideApps).put("items", items))
        }
        return a.toString()
    }
}
