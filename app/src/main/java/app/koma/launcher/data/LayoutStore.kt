package app.koma.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Saves the tile list as a small JSON file. Uses org.json from the platform, so no extra dependency. */
class LayoutStore(context: Context) {
    private val file = File(context.filesDir, "layout.json")

    fun load(): List<Tile> {
        if (!file.exists()) return emptyList()
        return try {
            fromJson(JSONArray(file.readText()))
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(tiles: List<Tile>) {
        val tmp = File(file.parentFile, "layout.json.tmp")
        tmp.writeText(toJson(tiles).toString())
        tmp.renameTo(file)
    }

    companion object {
        fun fromJson(arr: JSONArray): List<Tile> = (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            // A kind that no longer exists (the old timer and calculator tiles) is dropped; old Tasks tiles become Reminders.
            val kind = Kind.entries.firstOrNull { it.name == o.getString("kind").replace("TASKS", "REMINDERS") } ?: return@mapNotNull null
            Tile(
                id = o.getString("id"),
                kind = kind,
                w = o.getInt("w"),
                h = o.getInt("h"),
                arg = o.optString("arg", ""),
                label = o.optString("label", ""),
                glyph = o.optString("glyph", ""),
                color = o.optInt("color", -1),
            )
        }

        fun toJson(tiles: List<Tile>): JSONArray {
            val arr = JSONArray()
            tiles.forEach { t ->
                arr.put(
                    JSONObject()
                        .put("id", t.id).put("kind", t.kind.name).put("w", t.w).put("h", t.h)
                        .put("arg", t.arg).put("label", t.label).put("glyph", t.glyph).put("color", t.color)
                )
            }
            return arr
        }
    }
}
