package app.koma.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.koma.launcher.data.Sub
import app.koma.launcher.data.Task
import app.koma.launcher.data.Tasks
import app.koma.launcher.data.TimeFmt
import java.util.UUID

/** Tasks and reminders: today at a glance, chips for views and lists, a card per item, quick add, and an editor for each. */
@Composable
fun TasksScreen(state: HomeState) {
    val pal = state.palette
    val ctx = LocalContext.current
    var view by remember { mutableStateOf("today") }
    var listSel by remember { mutableStateOf("*") }
    var text by remember { mutableStateOf("") }
    val today = Tasks.today()
    val all = state.tasks
    val lists = all.map { it.list }.filter { it.isNotEmpty() }.distinct().sorted()
    val sel = if (listSel == "*" || listSel.isEmpty() || listSel in lists) listSel else "*"
    val scoped = all.filter { sel == "*" || it.list == sel }
    val open = scoped.filter { !it.done }

    // The card at the top counts everything that belongs to today: due today (done or not) and anything late.
    val dayList = all.filter { it.due == today || (!it.done && it.due in 0L until today) }
    val dayDone = dayList.count { it.done }
    val dayTotal = dayList.size
    val dayLeft = dayTotal - dayDone
    val lateCount = all.count { !it.done && it.due in 0L until today }
    val nextOpen = all.filter { !it.done && it.due > today }.minByOrNull { it.due }
    val nowMin = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
    val nextTimed = all.filter { !it.done && it.due == today && it.at >= nowMin }.minByOrNull { it.at }
    val nextText = when {
        lateCount > 0 -> "$lateCount late"
        nextTimed != null -> "Next " + Tasks.clock(nextTimed.at, TimeFmt.pattern())
        dayLeft > 0 -> "Due today"
        nextOpen != null -> "Next " + Tasks.label(nextOpen.due)
        else -> "All clear"
    }

    val sections: List<Pair<String, List<Task>>> = when (view) {
        "today" -> listOf(
            "OVERDUE" to Tasks.sortOpen(open.filter { it.due in 0L until today }),
            "" to (Tasks.sortOpen(open.filter { it.due == today }) + scoped.filter { it.done && it.due == today }.sortedByDescending { it.doneAt }),
            "UPCOMING" to Tasks.sortOpen(open.filter { it.due > today }).take(6),
        )
        "upcoming" -> open.filter { it.due > today }.sortedWith(Tasks.openOrder)
            .groupBy { it.due }.map { (d, l) -> Tasks.label(d).uppercase() to l }
        "done" -> listOf("" to scoped.filter { it.done }.sortedByDescending { it.doneAt })
        else -> listOf(
            "OVERDUE" to Tasks.sortOpen(open.filter { it.due in 0L until today }),
            "TODAY" to Tasks.sortOpen(open.filter { it.due == today }),
            "UPCOMING" to Tasks.sortOpen(open.filter { it.due > today }),
            "NO DATE" to Tasks.sortOpen(open.filter { it.due < 0 }),
            "DONE" to scoped.filter { it.done }.sortedByDescending { it.doneAt },
        )
    }.filter { it.second.isNotEmpty() }
    val rows: List<Pair<String, Task?>> = buildList {
        sections.forEach { (title, list) ->
            if (title.isNotEmpty()) add(title to null)
            list.forEach { add(title to it) }
        }
    }

    // Enter on the keyboard adds what was typed straight away; the + button opens the editor for everything else.
    fun add(blank: Boolean): String? {
        val p = Tasks.parse(text)
        val due = if (p.due >= 0) p.due else if (view == "today" && sel == "*") today else -1L
        val id = state.addTask(p.text, due, if (sel == "*") "" else sel, p.priority, p.at, if (p.at >= 0) 0 else -1, blank)
        if (id != null && p.at >= 0) needNotifications(state, ctx)
        text = ""
        return id
    }
    fun submit() { add(false) }
    fun openNew() {
        val id = add(true) ?: return
        state.taskNewId = id
        state.taskEditId = id
    }

    Box(Modifier.fillMaxSize()) {
        val viewIds = listOf("today", "upcoming", "all", "done")
        AppPage(
            "Reminders",
            if (dayTotal == 0) "NOTHING DUE TODAY" else "$dayLeft LEFT TODAY${if (lateCount > 0) " / $lateCount LATE" else ""}",
            onBack = { state.exitApp() },
            actions = {
                if (view == "done" && scoped.any { it.done }) CircleBtn("trash") { state.clearDoneTasks() }
            },
            nav = {
                PillNav(
                    listOf("cal" to "TODAY", "next" to "UPCOMING", "all" to "ALL", "check" to "DONE"),
                    viewIds.indexOf(if (sel != "*" && view == "all") "all" else view).coerceAtLeast(0),
                ) { listSel = "*"; view = viewIds[it] }
            },
        ) {
            // Today at a glance.
            val land = androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
            Box(Modifier.fillMaxWidth().height(if (land) 108.dp else 176.dp).clip(RoundedCornerShape(30.dp)).background(pal.tiles[0]).padding(horizontal = 22.dp, vertical = if (land) 12.dp else 18.dp)) {
                Column(Modifier.align(Alignment.BottomStart)) {
                    BasicText(
                        "TODAY / " + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEE d", java.util.Locale.getDefault())).uppercase(),
                        style = monoStyle(pal.ink, 12, bold = true),
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        BasicText("$dayDone", style = dispStyle(pal.ink, if (land) 52 else 80))
                        // The total is drawn as an outline.
                        BasicText("/$dayTotal", style = dispStyle(pal.ink, if (land) 52 else 80).copy(drawStyle = Stroke(width = 3f)))
                    }
                }
                Column(Modifier.align(Alignment.BottomEnd), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText("$dayLeft left", style = bodyStyle(pal.ink, 16))
                    BasicText(nextText, style = bodyStyle(pal.ink.copy(alpha = 0.8f), 16))
                }
            }

            // Views, then your lists.
            val chips = lists.map { "list:$it" to it }
            val chipOn = if (sel != "*") "list:$sel" else ""
            if (chips.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { (id, name) ->
                    val on = id == chipOn
                    Box(
                        Modifier.height(40.dp).clip(RoundedCornerShape(50)).background(if (on) pal.tiles[0] else surfaceColor())
                            .clickable {
                                if (id == chipOn) listSel = "*"
                                else {
                                    listSel = id.removePrefix("list:")
                                    view = "all"
                                }
                            }.padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) { BasicText(name, style = bodyStyle(if (on) pal.ink else pal.onGround, 15, medium = true), maxLines = 1) }
                }
            }

            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rows.isEmpty()) {
                    item(key = "empty") {
                        BasicText(
                            when (view) {
                                "today" -> "Nothing due today."
                                "upcoming" -> "Nothing coming up."
                                "done" -> "No finished tasks yet."
                                else -> "Nothing here yet. Add a task or reminder below."
                            },
                            style = bodyStyle(pal.onGround.copy(alpha = 0.6f), 15),
                            modifier = Modifier.padding(top = 16.dp, start = 4.dp),
                        )
                    }
                }
                items(rows, key = { (h, t) -> t?.id ?: "h:$h" }) { (title, t) ->
                    if (t == null) SectionLabel(title)
                    else TaskRow(state, t, today, showDate = title == "UPCOMING")
                }
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(pal.tiles[0].copy(alpha = 0.14f)).padding(start = 22.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Field(text, { text = it }, "Add a task, or try: call mom 5pm", Modifier.weight(1f), onDone = { submit() }, keepKeyboard = true)
                Box(Modifier.size(48.dp).clip(CircleShape).background(pal.tiles[0]).clickable { openNew() }, contentAlignment = Alignment.Center) {
                    Glyph("plus", pal.ink, 22.dp, 2.6f)
                }
            }
            Spacer(Modifier.height(2.dp))
        }

        val editing = state.taskEditId?.let { id -> state.tasks.firstOrNull { it.id == id } }
        if (editing != null) TaskEditor(state, editing, lists)
    }
}

@Composable
private fun RoundBtn(glyph: String, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(pal.onGround.copy(alpha = 0.12f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(glyph, pal.onGround, 16.dp, 2.4f)
    }
}

/** One task as a card: a square tick, the title, and small badges (or the due date for later days) on the right. */
@Composable
private fun TaskRow(state: HomeState, t: Task, today: Long, showDate: Boolean) {
    val pal = state.palette
    val n = pal.tiles.size
    val late = !t.done && t.due in 0L until today
    val ring = if (t.priority > 0) pal.tiles[t.priority % n] else pal.onGround.copy(alpha = 0.45f)
    val box = RoundedCornerShape(9.dp)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(surfaceColor(0.07f)).clickable { state.taskEditId = t.id }
            .padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clickable { state.toggleTask(t.id) }, contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(30.dp).clip(box)
                    .background(if (t.done) pal.tiles[0] else Color.Transparent)
                    .border(2.dp, if (t.done) pal.tiles[0] else ring, box),
                contentAlignment = Alignment.Center,
            ) { if (t.done) Glyph("check", pal.ink, 16.dp, 3.2f) }
        }
        Column(Modifier.weight(1f).padding(vertical = 14.dp, horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText(
                t.text.ifBlank { "Untitled" },
                style = bodyStyle(pal.onGround.copy(alpha = if (t.done) 0.45f else 1f), 18, medium = t.priority > 0 || late).copy(
                    textDecoration = if (t.done) TextDecoration.LineThrough else TextDecoration.None,
                ),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            // A reminder says when it goes off, with a bell.
            if (t.at >= 0 && !t.done) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Glyph("n_alarm", if (t.remind >= 0) pal.onGround else pal.onGround.copy(alpha = 0.4f), 13.dp, 2.2f)
                BasicText(
                    Tasks.clock(t.at, TimeFmt.pattern()) + if (t.remind > 0) " / " + Tasks.remindNames[Tasks.remindMins.indexOf(t.remind).coerceAtLeast(0)].uppercase() else "",
                    style = monoStyle(pal.onGround.copy(alpha = 0.7f), 11, bold = true), maxLines = 1,
                )
            }
        }
        if (!t.done) {
            if (showDate && t.due > today) {
                BasicText(Tasks.label(t.due).uppercase(), style = monoStyle(pal.onGround.copy(alpha = 0.75f), 12, bold = true), maxLines = 1)
            } else {
                val badges = buildList {
                    if (late) add("LATE" to pal.tiles[3 % n])
                    if (t.priority > 0) add("!".repeat(t.priority) to pal.tiles[0])
                    if (t.list.isNotEmpty()) add(t.list.uppercase().take(8) to pal.tiles[2 % n])
                    if (t.repeat != 0) add("REPEATS" to pal.tiles[1 % n])
                    if (t.subs.isNotEmpty()) add("${t.subs.count { it.done }}/${t.subs.size}" to pal.tiles[4 % n])
                }.take(2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    badges.forEach { (label, colour) ->
                        BasicText(
                            label, style = monoStyle(pal.ink, 11, bold = true), maxLines = 1,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(colour).padding(horizontal = 11.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Everything about one task, saved as you change it. */
@Composable
private fun TaskEditor(state: HomeState, t: Task, lists: List<String>) {
    val pal = state.palette
    val ctx = LocalContext.current
    var timeOpen by remember(t.id) { mutableStateOf(false) }
    val today = Tasks.today()
    var title by remember(t.id) { mutableStateOf(t.text) }
    var notes by remember(t.id) { mutableStateOf(t.notes) }
    var step by remember(t.id) { mutableStateOf("") }
    var newList by remember(t.id) { mutableStateOf("") }
    fun cur(): Task = state.tasks.firstOrNull { it.id == t.id } ?: t
    fun edit(f: (Task) -> Task) = state.updateTask(f(cur()))
    val isNew = state.taskNewId == t.id
    val close = { state.closeTaskEditor() }
    val field = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(pal.onGround.copy(alpha = 0.08f)).padding(12.dp)

    Sheet(onDismiss = close, header = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(title, { title = it; edit { x -> x.copy(text = it) } }, if (isNew) "New task or reminder" else "Task", Modifier.weight(1f), style = dispStyle(pal.onGround, 22))
            Pill(if (isNew) "ADD" else "DONE", pal.tiles[0], pal.ink, height = 36, onClick = close)
        }
    }) {
        Group {
            GroupPad {
                Field(
                    notes, { notes = it; edit { x -> x.copy(notes = it) } }, "Notes",
                    Modifier.fillMaxWidth().heightIn(min = 64.dp), singleLine = false, style = bodyStyle(pal.onGround, 15),
                )
            }
        }

        Group("WHEN") {
            val dueId = when {
                t.due < 0 -> "none"
                t.due == today -> "today"
                t.due == today + 1 -> "tomorrow"
                t.due == today + 7 -> "week"
                else -> "custom"
            }
            val opts = buildList {
                add("none" to "NO DATE")
                add("today" to "TODAY")
                add("tomorrow" to "TOMORROW")
                add("week" to "NEXT WEEK")
                if (dueId == "custom") add("custom" to Tasks.full(t.due).uppercase())
            }
            Dropdown(opts, dueId, "Due") { id ->
                edit { x ->
                    when (id) {
                        "none" -> x.copy(due = -1, repeat = 0, at = -1, remind = -1)
                        "today" -> x.copy(due = today)
                        "tomorrow" -> x.copy(due = today + 1)
                        "week" -> x.copy(due = today + 7)
                        else -> x
                    }
                }
            }
            if (t.due >= 0) {
                ListRow("Date", Tasks.full(t.due), trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoundBtn("back") { edit { x -> x.copy(due = x.due - 1) } }
                        RoundBtn("chev") { edit { x -> x.copy(due = x.due + 1) } }
                    }
                })
            }
            // A time makes it a reminder: pick it on the wheels, then choose how early to be told.
            ListRow("Time", if (t.at >= 0) Tasks.clock(t.at, TimeFmt.pattern()) else "No time", glyph = "clock", onClick = { timeOpen = !timeOpen })
            if (timeOpen) {
                GroupPad {
                    TimeWheels(if (t.at >= 0) t.at else 9 * 60, TimeFmt.is24()) { m ->
                        edit { x -> x.copy(at = m, due = if (x.due < 0) today else x.due, remind = if (x.remind < 0) 0 else x.remind) }
                        needNotifications(state, ctx)
                    }
                }
                if (t.at >= 0) ListRow("Clear time", "Back to a plain task", onClick = { edit { x -> x.copy(at = -1, remind = -1) }; timeOpen = false })
            }
            if (t.at >= 0) {
                val idx = Tasks.remindMins.indexOf(t.remind).coerceAtLeast(0)
                Dropdown(Tasks.remindNames.mapIndexed { i, s -> Tasks.remindMins[i].toString() to s.uppercase() }, Tasks.remindMins[idx].toString(), "Remind") { v ->
                    edit { x -> x.copy(remind = v.toInt()) }
                    if (v.toInt() >= 0) needNotifications(state, ctx)
                }
            }
            Dropdown(Tasks.repeatNames.mapIndexed { i, s -> i.toString() to s.uppercase() }, t.repeat.toString(), "Repeat") { v ->
                edit { x ->
                    val r = v.toInt()
                    x.copy(repeat = r, due = if (r != 0 && x.due < 0) today else x.due)
                }
            }
        }

        Group("ORGANISE") {
            Dropdown(Tasks.priorityNames.mapIndexed { i, s -> i.toString() to s.uppercase() }, t.priority.toString(), "Priority") { v ->
                edit { x -> x.copy(priority = v.toInt()) }
            }
            val names = (lists + listOf(t.list)).filter { it.isNotEmpty() }.distinct()
            Dropdown(listOf("" to "INBOX") + names.map { it to it.uppercase() }, t.list, "List") { v -> edit { x -> x.copy(list = v) } }
            GroupPad {
                Field(
                    newList, { newList = it.take(24) }, "Or a new list", field,
                    onDone = {
                        if (newList.isNotBlank()) {
                            val name = newList.trim()
                            edit { x -> x.copy(list = name) }
                            newList = ""
                        }
                    },
                )
            }
        }

        Group("STEPS", hint = if (t.subs.isNotEmpty()) "${t.subs.count { it.done }} of ${t.subs.size} done" else null) {
            t.subs.forEach { s ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CheckBox(s.done, pal.tiles[0], pal.ink) {
                        edit { x -> x.copy(subs = x.subs.map { if (it.id == s.id) it.copy(done = !it.done) else it }) }
                    }
                    BasicText(
                        s.text,
                        style = bodyStyle(pal.onGround.copy(alpha = if (s.done) 0.5f else 1f), 15).copy(
                            textDecoration = if (s.done) TextDecoration.LineThrough else TextDecoration.None,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    RoundBtn("close") { edit { x -> x.copy(subs = x.subs.filterNot { it.id == s.id }) } }
                }
            }
            GroupPad {
                Field(
                    step, { step = it }, "Add a step", field,
                    keepKeyboard = true,
                    onDone = {
                        if (step.isNotBlank()) {
                            val text = step.trim()
                            edit { x -> x.copy(subs = x.subs + Sub(UUID.randomUUID().toString(), text)) }
                            step = ""
                        }
                    },
                )
            }
        }

        Group {
            ListRow(
                if (t.done) "Reopen task" else "Mark as done",
                if (!t.done && t.repeat != 0) "Moves to its next date" else "",
                onClick = {
                    state.toggleTask(t.id)
                    close()
                },
            )
            ListRow("Delete task", "Remove it for good", onClick = { state.deleteTask(t.id) })
        }
    }
}
