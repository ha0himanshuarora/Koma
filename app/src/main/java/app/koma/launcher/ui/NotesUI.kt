package app.koma.launcher.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.koma.launcher.data.Check
import app.koma.launcher.data.Note
import app.koma.launcher.data.Notes
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private fun freshNote(): Note {
    val now = System.currentTimeMillis()
    return Note(UUID.randomUUID().toString(), "", "", now, created = now)
}

/** Notes: a grid or list with search, labels, archive and a bin; text or checklist notes. */
@Composable
fun NotesScreen(state: HomeState) {
    // A home tile can ask to open one note (or a new one) straight away.
    var editing by remember {
        mutableStateOf(state.noteOpenId?.let { id -> if (id == "new") freshNote() else state.notes.firstOrNull { it.id == id } })
    }
    var direct by remember { mutableStateOf(editing != null) }
    LaunchedEffect(Unit) { state.noteOpenId = null }
    val e = editing
    if (e != null) {
        NoteEditor(state, e) {
            editing = null
            if (direct) {
                direct = false
                state.goHome()
            }
        }
    } else {
        NoteList(state) { editing = it }
    }
}

@Composable
private fun NoteList(state: HomeState, onOpen: (Note) -> Unit) {
    val pal = state.palette
    val prefs = state.prefs
    var query by remember { mutableStateOf("") }
    var view by remember { mutableStateOf("all") }
    var binNote by remember { mutableStateOf<Note?>(null) }
    var confirmBin by remember { mutableStateOf(false) }

    val labels = state.notes.filter { it.trashed == 0L }.map { it.label }.filter { it.isNotEmpty() }.distinct().sorted()
    val v = if (view.startsWith("label:") && view.removePrefix("label:") !in labels) "all" else view
    val pool = when (v) {
        "archive" -> state.notes.filter { it.archived && it.trashed == 0L }
        "bin" -> state.notes.filter { it.trashed > 0L }.sortedByDescending { it.trashed }
        else -> Notes.live(state.notes)
    }
    val shown = pool.filter {
        Notes.matches(it, query) && when {
            v == "pinned" -> it.pinned
            v == "lists" -> it.kind == 1
            v.startsWith("label:") -> it.label == v.removePrefix("label:")
            else -> true
        }
    }
    val grid = prefs.notesGrid
    val open: (Note) -> Unit = { if (v == "bin") binNote = it else onOpen(it) }
    val total = Notes.live(state.notes).size

    Box(Modifier.fillMaxSize()) {
        val navIds = listOf("all", "pinned", "lists", "archive", "bin")
        AppPage(
            "Notes",
            "",
            onBack = { state.exitApp() },
            actions = {
                if (v == "bin") {
                    if (pool.isNotEmpty()) CircleBtn("trash") { confirmBin = true }
                } else {
                    CircleBtn(if (grid) "bars" else "all") { prefs.notesGrid = !grid }
                    CircleBtn("plus", accent = true) { onOpen(freshNote()) }
                }
            },
            nav = {
                PillNav(
                    listOf("note" to "ALL", "star" to "PINNED", "tasks" to "LISTS", "down" to "ARCHIVE", "trash" to "BIN"),
                    navIds.indexOf(if (v.startsWith("label:")) "all" else v).coerceAtLeast(0),
                ) { view = navIds[it] }
            },
        ) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(surfaceColor()).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph("search", pal.onGround.copy(alpha = 0.6f), 20.dp, 2f)
                Spacer(Modifier.width(10.dp))
                Field(query, { query = it }, "Search notes", Modifier.weight(1f))
            }
            val chips = labels.map { "label:$it" to it }
            if (chips.isNotEmpty() && v != "archive" && v != "bin") Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { (id, name) ->
                    val on = id == v
                    Box(
                        Modifier.height(40.dp).clip(RoundedCornerShape(50)).background(if (on) pal.tiles[0] else surfaceColor())
                            .clickable { view = if (id == v) "all" else id }.padding(horizontal = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) { BasicText(name, style = bodyStyle(if (on) pal.ink else pal.onGround, 15, medium = true), maxLines = 1) }
                }
            }
            if (shown.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                    Glyph(
                        when {
                            query.isNotBlank() -> "search"
                            v == "archive" -> "down"
                            v == "bin" -> "trash"
                            v == "pinned" -> "star"
                            v == "lists" -> "tasks"
                            else -> "note"
                        },
                        pal.onGround.copy(alpha = 0.3f), 64.dp, 1.6f,
                    )
                }
            }
            if (grid) {
                LazyVerticalStaggeredGrid(
                    StaggeredGridCells.Fixed(2), Modifier.weight(1f),
                    verticalItemSpacing = 10.dp, horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    staggeredItems(shown, key = { it.id }) { NoteCard(state, it, true) { open(it) } }
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(shown, key = { it.id }) { NoteCard(state, it, false) { open(it) } }
                }
            }
        }

        if (confirmBin) {
            val count = state.notes.count { it.trashed > 0L }
            Sheet(onDismiss = { confirmBin = false }, header = {
                Glyph("trash", pal.onGround, 28.dp, 2f)
            }) {
                Group {
                    ListRow("Delete forever", "", "trash", onClick = {
                        state.emptyBin()
                        confirmBin = false
                    })
                    ListRow("Cancel", "", "close", onClick = { confirmBin = false })
                }
            }
        }

        val b = binNote
        if (b != null) {
            Sheet(onDismiss = { binNote = null }, header = {
                BasicText(b.title.ifBlank { "Untitled" }, style = dispStyle(pal.onGround, 22), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }) {
                Group {
                    ListRow("Restore", "", "refresh", onClick = {
                        state.restoreNote(b.id)
                        binNote = null
                    })
                    ListRow("Delete forever", "", "trash", onClick = {
                        state.deleteNote(b.id)
                        binNote = null
                    })
                }
            }
        }
    }
}

private fun tagDate(ms: Long): String {
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = java.time.LocalDate.now()
    val days = java.time.temporal.ChronoUnit.DAYS.between(d, today)
    return when {
        days == 0L -> "TODAY"
        days in 1..6 -> d.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).uppercase()
        else -> d.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())).uppercase()
    }
}

/** A note's colour: the one picked for it, else one the palette gives it from its id. */
internal fun noteTone(tiles: List<Color>, n: Note): Color = tiles[(if (n.color >= 0) n.color else Math.abs(n.id.hashCode())) % tiles.size]

/** One note as a colourful rounded card: a small mono tag, a heavy title and a preview. */
@Composable
private fun NoteCard(state: HomeState, n: Note, grid: Boolean, onClick: () -> Unit) {
    val pal = state.palette
    val fill = noteTone(pal.tiles, n)
    val fg = pal.ink
    val items = if (n.kind == 1) Notes.items(n.body) else emptyList()
    val tagLeft = when {
        n.trashed > 0L -> {
            val left = (Notes.BIN_DAYS - (System.currentTimeMillis() - n.trashed) / 86_400_000L).coerceAtLeast(0)
            "DELETES IN $left D"
        }
        n.pinned -> "PINNED"
        n.kind == 1 -> "LIST / ${items.count { it.done }} OF ${items.size}"
        else -> tagDate(n.updated)
    }
    val tagRight = if (n.pinned && n.trashed == 0L) tagDate(n.updated) else ""
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(fill).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(tagLeft, style = monoStyle(fg.copy(alpha = 0.75f), 10, bold = true), maxLines = 1)
            if (tagRight.isNotEmpty()) BasicText(tagRight, style = monoStyle(fg.copy(alpha = 0.75f), 10, bold = true), maxLines = 1)
        }
        if (n.title.isNotBlank()) {
            BasicText(n.title, style = dispStyle(fg, if (grid) 24 else 28), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (n.kind == 1) {
            val ordered = items.filter { !it.done } + items.filter { it.done }
            if (ordered.isNotEmpty()) {
                BasicText(ordered.joinToString(", ") { it.text }, style = bodyStyle(fg.copy(alpha = 0.85f), 15), maxLines = if (grid) 5 else 3, overflow = TextOverflow.Ellipsis)
            }
        } else if (n.body.isNotBlank()) {
            BasicText(n.body, style = bodyStyle(fg.copy(alpha = 0.85f), 15), maxLines = if (grid) 8 else 3, overflow = TextOverflow.Ellipsis)
        }
        if (n.title.isBlank() && n.body.isBlank()) Glyph("note", fg.copy(alpha = 0.4f), 22.dp, 1.8f)
        if (n.label.isNotEmpty()) {
            BasicText(
                n.label.uppercase(), style = monoStyle(fg.copy(alpha = 0.85f), 9, bold = true), maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(fg.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun SmallX(onClick: () -> Unit) {
    val pal = LocalPalette.current
    Box(Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Glyph("close", pal.onGround.copy(alpha = 0.6f), 18.dp, 2.2f)
    }
}

@Composable
private fun NoteEditor(state: HomeState, note: Note, onClose: () -> Unit) {
    val pal = state.palette
    val ctx = LocalContext.current
    var title by remember(note.id) { mutableStateOf(note.title) }
    var body by remember(note.id) { mutableStateOf(if (note.kind == 1) "" else note.body) }
    var items by remember(note.id) { mutableStateOf(if (note.kind == 1) Notes.items(note.body) else emptyList()) }
    var kind by remember(note.id) { mutableIntStateOf(note.kind) }
    var pinned by remember(note.id) { mutableStateOf(note.pinned) }
    var color by remember(note.id) { mutableIntStateOf(note.color) }
    var label by remember(note.id) { mutableStateOf(note.label) }
    var archived by remember(note.id) { mutableStateOf(note.archived) }
    var newItem by remember(note.id) { mutableStateOf("") }
    var newLabel by remember(note.id) { mutableStateOf("") }
    var showOptions by remember(note.id) { mutableStateOf(false) }
    var gone by remember(note.id) { mutableStateOf(false) }

    fun current(): Note = note.copy(
        title = title, body = if (kind == 1) Notes.join(items) else body, kind = kind,
        pinned = pinned, color = color, label = label, archived = archived,
    )

    fun persist(final: Boolean) {
        if (gone) return
        val c = current()
        val blank = c.title.isBlank() && c.body.isBlank()
        val saved = state.notes.any { it.id == note.id }
        when {
            blank && !saved -> Unit
            blank && final -> state.deleteNote(note.id)
            else -> state.saveNote(c)
        }
    }
    LaunchedEffect(title, body, items, kind, pinned, color, label, archived) {
        delay(400)
        persist(false)
    }
    DisposableEffect(note.id) { onDispose { persist(true) } }

    fun addItem() {
        if (newItem.isBlank()) return
        items = items + Check((items.maxOfOrNull { it.id } ?: -1) + 1, newItem.trim(), false)
        newItem = ""
    }

    fun setKind(k: Int) {
        if (k == kind) return
        if (k == 1) {
            items = Notes.items(body)
            body = ""
        } else {
            body = items.joinToString("\n") { it.text }
            items = emptyList()
        }
        kind = k
    }

    fun share() {
        try {
            val c = current()
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, c.title)
                putExtra(Intent.EXTRA_TEXT, (if (c.title.isNotBlank()) c.title + "\n\n" else "") + Notes.plain(c))
            }
            ctx.startActivity(Intent.createChooser(send, "Share note"))
        } catch (e: Exception) {
            // nothing can take text
        }
    }

    Box(Modifier.fillMaxSize()) {
        Page(
            "Note", if (archived) "ARCHIVED" else "BUILT-IN / NOTES",
            onBack = {
                persist(true)
                onClose()
            },
            scroll = false,
            actions = {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(pal.onGround.copy(alpha = 0.14f)).clickable { pinned = !pinned },
                    contentAlignment = Alignment.Center,
                ) { Glyph("star", pal.onGround, 20.dp, 2f, filled = pinned) }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(pal.onGround).clickable { showOptions = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(3) { Box(Modifier.size(4.dp).clip(CircleShape).background(pal.ground)) }
                    }
                }
            },
        ) {
            if (color >= 0) {
                Box(Modifier.width(48.dp).height(8.dp).clip(RoundedCornerShape(50)).background(pal.tiles[color % pal.tiles.size]))
            }
            Field(title, { title = it }, "Title", Modifier.fillMaxWidth(), style = dispStyle(pal.onGround, 26))
            if (kind == 1) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val open = items.filter { !it.done }
                    val done = items.filter { it.done }
                    @Composable
                    fun row(c: Check) {
                        key(c.id) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CheckBox(c.done, pal.tiles[0], pal.ink) {
                                    items = items.map { if (it.id == c.id) it.copy(done = !it.done) else it }
                                }
                                Field(
                                    c.text, { t -> items = items.map { if (it.id == c.id) it.copy(text = t) else it } }, "Item", Modifier.weight(1f),
                                    style = bodyStyle(pal.onGround.copy(alpha = if (c.done) 0.5f else 1f), 16).copy(
                                        textDecoration = if (c.done) TextDecoration.LineThrough else TextDecoration.None,
                                    ),
                                )
                                SmallX { items = items.filterNot { it.id == c.id } }
                            }
                        }
                    }
                    open.forEach { row(it) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { Glyph("plus", pal.onGround.copy(alpha = 0.6f), 20.dp, 2.2f) }
                        Field(newItem, { newItem = it }, "Add item", Modifier.weight(1f), style = bodyStyle(pal.onGround, 16), onDone = { addItem() }, keepKeyboard = true)
                    }
                    if (done.isNotEmpty()) {
                        SectionLabel("COMPLETED ${done.size}")
                        done.forEach { row(it) }
                    }
                }
            } else {
                Field(body, { body = it }, "Write something", Modifier.fillMaxWidth().weight(1f), singleLine = false)
            }
        }

        if (showOptions) {
            val labels = (state.notes.map { it.label } + label).filter { it.isNotEmpty() }.distinct().sorted()
            Sheet(onDismiss = { showOptions = false }) {
                Group {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { Glyph("palette", pal.onGround, 22.dp, 2f) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            (listOf(-1) + pal.tiles.indices.toList()).forEach { c ->
                                Box(
                                    Modifier.size(26.dp).clip(CircleShape).background(if (c < 0) pal.onGround.copy(alpha = 0.2f) else pal.tiles[c])
                                        .then(if (color == c) Modifier.border(2.5.dp, pal.onGround, CircleShape) else Modifier)
                                        .clickable { color = c },
                                    contentAlignment = Alignment.Center,
                                ) { if (c < 0) BasicText("A", style = monoStyle(pal.onGround, 10, bold = true)) }
                            }
                        }
                    }
                }
                Group {
                    SwitchRow("Checklist", "", kind == 1) { setKind(if (it) 1 else 0) }
                    ChipRow(listOf("" to "NO LABEL") + labels.map { it to it.uppercase() }, label, title = "Label") { label = it }
                    GroupPad {
                        Field(
                            newLabel, { newLabel = it.take(24) }, "Or a new label",
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(pal.onGround.copy(alpha = 0.08f)).padding(12.dp),
                            onDone = {
                                if (newLabel.isNotBlank()) {
                                    label = newLabel.trim()
                                    newLabel = ""
                                }
                            },
                        )
                    }
                }
                Group {
                    ListRow("Share", "", "share", onClick = { share() })
                    ListRow(if (archived) "Move back to notes" else "Archive", "", "down", onClick = {
                        archived = !archived
                        if (archived) pinned = false
                        showOptions = false
                    })
                    ListRow("Move to bin", "", "trash", onClick = {
                        val c = current()
                        if (c.title.isNotBlank() || c.body.isNotBlank()) {
                            state.saveNote(c)
                            state.trashNote(note.id)
                        }
                        gone = true
                        showOptions = false
                        onClose()
                    })
                }
            }
        }
    }
}
