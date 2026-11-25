package app.koma.launcher.data

/** One line of a checklist note. The id only keeps rows steady while editing; it is not saved. */
data class Check(val id: Int, val text: String, val done: Boolean)

/** Helpers for notes: checklists stored as text, previews, ordering, the bin. */
object Notes {
    const val BIN_DAYS = 30
    const val BIN_MS = BIN_DAYS * 24L * 60 * 60 * 1000

    fun items(body: String): List<Check> =
        body.lines().filter { it.isNotBlank() }.mapIndexed { i, line ->
            when {
                line.startsWith("[x] ") -> Check(i, line.drop(4), true)
                line.startsWith("[ ] ") -> Check(i, line.drop(4), false)
                else -> Check(i, line.trim(), false)
            }
        }

    fun join(items: List<Check>): String =
        items.filter { it.text.isNotBlank() }.joinToString("\n") { (if (it.done) "[x] " else "[ ] ") + it.text.trim() }

    /** Plain text of a note, for sharing and search. */
    fun plain(n: Note): String =
        if (n.kind == 1) items(n.body).joinToString("\n") { (if (it.done) "x " else "- ") + it.text } else n.body

    /** What to call a note in a list: its title, else the first line of its text. */
    fun heading(n: Note): String =
        n.title.ifBlank { plain(n).lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty() }.ifBlank { "Untitled" }

    fun words(text: String): Int = text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

    fun live(all: List<Note>): List<Note> = all.filter { it.trashed == 0L && !it.archived }

    fun matches(n: Note, q: String): Boolean =
        q.isBlank() || n.title.contains(q, true) || plain(n).contains(q, true) || n.label.contains(q, true)
}
