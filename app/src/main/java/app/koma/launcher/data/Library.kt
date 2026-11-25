package app.koma.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Small atomic JSON file in the app's private storage. */
class JsonFile(context: Context, name: String) {
    private val file = File(context.filesDir, name)

    fun read(): String? = try {
        if (file.exists()) file.readText() else null
    } catch (e: Exception) {
        null
    }

    fun write(text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        tmp.renameTo(file)
    }
}

/** kind: 0 text, 1 checklist (body holds lines "[ ] item" / "[x] item"). trashed: when it was moved to the bin, 0 if not. */
data class Note(
    val id: String,
    val title: String,
    val body: String,
    val updated: Long,
    val color: Int = -1,
    val pinned: Boolean = false,
    val kind: Int = 0,
    val label: String = "",
    val archived: Boolean = false,
    val trashed: Long = 0,
    val created: Long = 0,
)

data class Sub(val id: String, val text: String, val done: Boolean = false)

/**
 * due is an epoch day (-1 = no date). at is the minute of the day it is due (-1 = no time). remind is how many minutes
 * before that time to notify (-1 = no reminder, 0 = at the time). priority: 0 none, 1 low, 2 medium, 3 high.
 * repeat: 0 never, 1 daily, 2 weekly, 3 monthly. list "" = Inbox.
 */
data class Task(
    val id: String,
    val text: String,
    val done: Boolean = false,
    val notes: String = "",
    val due: Long = -1,
    val at: Int = -1,
    val remind: Int = -1,
    val priority: Int = 0,
    val list: String = "",
    val repeat: Int = 0,
    val subs: List<Sub> = emptyList(),
    val created: Long = 0,
    val doneAt: Long = 0,
)

/** Notes and tasks live on the phone in two small JSON files. */
class Library(context: Context) {
    private val notesFile = JsonFile(context, "notes.json")
    private val tasksFile = JsonFile(context, "tasks.json")

    fun loadNotes(): List<Note> = try {
        val a = JSONArray(notesFile.read() ?: "[]")
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Note(
                o.getString("id"), o.optString("title"), o.optString("body"), o.optLong("updated"), o.optInt("color", -1), o.optBoolean("pinned"),
                kind = o.optInt("kind"), label = o.optString("label"), archived = o.optBoolean("archived"),
                trashed = o.optLong("trashed"), created = o.optLong("created"),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveNotes(notes: List<Note>) {
        val a = JSONArray()
        notes.forEach {
            a.put(
                JSONObject().put("id", it.id).put("title", it.title).put("body", it.body)
                    .put("updated", it.updated).put("color", it.color).put("pinned", it.pinned)
                    .put("kind", it.kind).put("label", it.label).put("archived", it.archived)
                    .put("trashed", it.trashed).put("created", it.created)
            )
        }
        notesFile.write(a.toString())
    }

    fun loadTasks(): List<Task> = try {
        val a = JSONArray(tasksFile.read() ?: "[]")
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Task(
                o.getString("id"), o.optString("text"), o.optBoolean("done"),
                notes = o.optString("notes"), due = o.optLong("due", -1), at = o.optInt("at", -1), remind = o.optInt("remind", -1),
                priority = o.optInt("priority"),
                list = o.optString("list"), repeat = o.optInt("repeat"),
                subs = o.optJSONArray("subs")?.let { sa ->
                    List(sa.length()) { j ->
                        val x = sa.getJSONObject(j)
                        Sub(x.getString("id"), x.optString("text"), x.optBoolean("done"))
                    }
                } ?: emptyList(),
                created = o.optLong("created"), doneAt = o.optLong("doneAt"),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveTasks(tasks: List<Task>) {
        val a = JSONArray()
        tasks.forEach { t ->
            val subs = JSONArray()
            t.subs.forEach { subs.put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done)) }
            a.put(
                JSONObject().put("id", t.id).put("text", t.text).put("done", t.done).put("notes", t.notes)
                    .put("due", t.due).put("at", t.at).put("remind", t.remind).put("priority", t.priority).put("list", t.list).put("repeat", t.repeat)
                    .put("subs", subs).put("created", t.created).put("doneAt", t.doneAt)
            )
        }
        tasksFile.write(a.toString())
    }
}
