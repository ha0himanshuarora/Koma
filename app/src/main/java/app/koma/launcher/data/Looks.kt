package app.koma.launcher.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/** A saved look: layout, theme, icons, fonts and optionally the wallpaper. */
data class Look(val id: String, val name: String, val wall: Boolean, val time: Long, val palette: String, val font: String)

/** Looks live in the app's private folder: one small JSON file each, plus a JPEG when the wallpaper was saved. */
class LookStore(context: Context) {
    private val dir = File(context.filesDir, "looks").also { it.mkdirs() }

    fun wallFile(id: String) = File(dir, "$id.jpg")

    fun list(): List<Look> = (dir.listFiles { f -> f.extension == "json" } ?: emptyArray()).mapNotNull { f ->
        try {
            val o = JSONObject(f.readText())
            val id = o.getString("id")
            Look(id, o.optString("name", "Look"), wallFile(id).exists(), o.optLong("time"), o.optString("palette_id"), o.optString("font"))
        } catch (e: Exception) {
            null
        }
    }.sortedByDescending { it.time }

    fun read(id: String): JSONObject? = try { JSONObject(File(dir, "$id.json").readText()) } catch (e: Exception) { null }

    fun write(id: String, o: JSONObject) {
        File(dir, "$id.json").writeText(o.toString())
    }

    fun delete(id: String) {
        File(dir, "$id.json").delete()
        wallFile(id).delete()
    }
}
