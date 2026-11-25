package app.koma.launcher.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Dates, ordering, repeating and quick-add parsing for tasks. */
object Tasks {
    val repeatNames = listOf("Never", "Daily", "Weekly", "Monthly")
    val priorityNames = listOf("None", "Low", "Medium", "High")

    /** How early a reminder goes off, in minutes before the due time. */
    val remindMins = listOf(-1, 0, 5, 15, 30, 60, 1440)
    val remindNames = listOf("Off", "At the time", "5 min before", "15 min before", "30 min before", "1 hour before", "1 day before")

    fun today(): Long = LocalDate.now().toEpochDay()

    /** Open tasks: by date (undated last), then priority, then oldest first. */
    val openOrder: Comparator<Task> = compareBy<Task>(
        { if (it.due < 0) Long.MAX_VALUE else it.due }, { if (it.at < 0) Int.MAX_VALUE else it.at }, { -it.priority }, { it.created },
    )

    fun sortOpen(list: List<Task>): List<Task> = list.filter { !it.done }.sortedWith(openOrder)

    private fun step(kind: Int, d: Long): Long = when (kind) {
        1 -> d + 1
        2 -> d + 7
        else -> LocalDate.ofEpochDay(d).plusMonths(1).toEpochDay()
    }

    /** The date a repeating task moves to when it is completed: the next one that is not in the past. */
    fun next(t: Task): Long {
        val today = today()
        var d = if (t.due < 0) today else t.due
        for (i in 0 until 400) {
            d = step(t.repeat, d)
            if (d >= today) break
        }
        return d
    }

    /** Ticks a task. A repeating one moves to its next date instead of finishing, and a finished one reopens. */
    fun toggled(list: List<Task>, id: String): List<Task> {
        val now = System.currentTimeMillis()
        return list.map { t ->
            when {
                t.id != id -> t
                !t.done && t.repeat != 0 -> t.copy(due = next(t), subs = t.subs.map { it.copy(done = false) })
                t.done -> t.copy(done = false, doneAt = 0)
                else -> t.copy(done = true, doneAt = now)
            }
        }
    }

    /** The moment a reminder should go off, or null when the task has no date, time or reminder. */
    fun trigger(t: Task): Long? {
        if (t.due < 0 || t.at < 0 || t.remind < 0) return null
        val due = LocalDate.ofEpochDay(t.due).atTime(t.at / 60, t.at % 60).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return due - t.remind * 60_000L
    }

    /** A time of day for display, in the given pattern (TimeFmt.pattern()). */
    fun clock(at: Int, pattern: String): String = LocalTime.of(at / 60, at % 60).format(fmt(pattern))

    /** "5:30 PM", or "Tomorrow 5:30 PM" once the date matters; just the date for a task with no time. */
    fun whenText(t: Task, pattern: String, withDay: Boolean): String = when {
        t.at < 0 -> if (t.due >= 0 && withDay) label(t.due) else ""
        t.due >= 0 && t.due != today() && withDay -> label(t.due) + " " + clock(t.at, pattern)
        else -> clock(t.at, pattern)
    }

    private fun fmt(p: String) = DateTimeFormatter.ofPattern(p, Locale.getDefault())

    /** Short human date: Today, Tomorrow, a weekday within the week, otherwise 4 Dec. */
    fun label(due: Long): String {
        val diff = due - today()
        val date = LocalDate.ofEpochDay(due)
        return when {
            diff == 0L -> "Today"
            diff == 1L -> "Tomorrow"
            diff == -1L -> "Yesterday"
            diff in 2L..6L -> date.format(fmt("EEEE"))
            date.year == LocalDate.now().year -> date.format(fmt("d MMM"))
            else -> date.format(fmt("d MMM yyyy"))
        }
    }

    /** Full date for the editor: Fri 27 Nov. */
    fun full(due: Long): String = LocalDate.ofEpochDay(due).format(fmt("EEE d MMM"))

    class Parsed(val text: String, val due: Long, val priority: Int, val at: Int)

    private val timeWord = Regex("^(\\d{1,2})(?::(\\d{2}))?(am|pm)?$")

    /** "5pm", "5:30pm" or "17:30" as a minute of the day; a bare number is not a time. -1 when it is not one. */
    private fun timeOf(word: String): Int {
        val m = timeWord.matchEntire(word.lowercase()) ?: return -1
        val ap = m.groupValues[3]
        if (m.groupValues[2].isEmpty() && ap.isEmpty()) return -1
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        if (min > 59) return -1
        if (ap.isNotEmpty()) {
            if (h !in 1..12) return -1
            h = h % 12 + if (ap == "pm") 12 else 0
        } else if (h > 23) return -1
        return h * 60 + min
    }

    /**
     * Quick add: leading ! marks priority (up to three). The last words can set a date (today, tomorrow) and a time
     * (5pm, 17:30), in either order, with an optional "at": "call mom tomorrow at 5pm". A time alone means the next one.
     */
    fun parse(input: String): Parsed {
        var s = input.trim()
        var pr = 0
        while (s.startsWith("!") && pr < 3) {
            pr++
            s = s.drop(1)
        }
        s = s.trim()
        val words = s.split(" ").toMutableList()
        var due = -1L
        var at = -1
        repeat(2) {
            if (words.size < 2) return@repeat
            val last = words.last().lowercase()
            val t = timeOf(last)
            when {
                t >= 0 && at < 0 -> {
                    at = t
                    words.removeAt(words.lastIndex)
                    if (words.size > 1 && words.last().equals("at", true)) words.removeAt(words.lastIndex)
                }
                due < 0 && last == "today" -> { due = today(); words.removeAt(words.lastIndex) }
                due < 0 && (last == "tomorrow" || last == "tmrw") -> { due = today() + 1; words.removeAt(words.lastIndex) }
                else -> Unit
            }
        }
        if (at >= 0 && due < 0) {
            val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
            due = if (at > nowMin) today() else today() + 1
        }
        return Parsed(words.joinToString(" ").trim(), due, pr, at)
    }
}
