package app.koma.launcher.ui

import app.koma.launcher.data.MusicArg
import app.koma.launcher.data.ClockArg
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.koma.launcher.data.Gestures
import app.koma.launcher.data.Defaults
import app.koma.launcher.data.Kind
import app.koma.launcher.data.Tile
import app.koma.launcher.data.children
import app.koma.launcher.data.allowedSizes
import app.koma.launcher.data.sizesFor

/** Top bar while editing: X throws the changes away, DONE keeps them. The caption says what is selected. */
@Composable
fun EditTopBar(state: HomeState) {
    val pal = state.palette
    val sel = state.tiles.firstOrNull { it.id == state.selectedId }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarIcon("close", modifier = Modifier.tourTarget("edit_cancel")) { state.cancelEdit() }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            BasicText("EDIT LAYOUT", style = dispStyle(pal.onGround, 20))
            BasicText(
                if (sel != null) "${(sel.label.ifEmpty { sel.kind.title }).uppercase()}  /  ${sel.w}X${sel.h}" else "TAP A TILE TO SELECT IT",
                style = monoStyle(pal.onGround.copy(alpha = 0.6f), 9), maxLines = 1,
            )
        }
        Pill("DONE", pal.tiles[0], pal.ink, Modifier.tourTarget("edit_done"), height = 38) { state.finishEdit() }
    }
}

/** A round icon button for the edit bar. */
@Composable
private fun BarIcon(glyph: String, size: Int = 40, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Box(
        modifier.size(size.dp).clip(CircleShape).background(pal.onGround.copy(alpha = 0.12f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(glyph, pal.onGround, (size / 2).dp, 2.2f) }
}

/**
 * Bottom bar while editing: one floating card. Left, the main actions (add, and options or remove for the selected
 * tile); right, grid size, theme and settings.
 */
@Composable
fun EditBar(state: HomeState) {
    val pal = state.palette
    val sel = state.tiles.firstOrNull { it.id == state.selectedId }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(
            Modifier.tourTarget("edit_bar").fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(pal.onGround.copy(alpha = 0.1f)).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // With a tile selected the bar has more to hold, so ADD shrinks to a plus and the icons to 36dp.
            Pill(if (sel != null) "+" else "+ ADD", pal.tiles[0], pal.ink, Modifier.tourTarget("edit_add"), height = 40) {
                state.stackTarget = null
                state.showPicker = true
            }
            if (sel != null) {
                Pill("OPTIONS", pal.onGround, pal.ground, Modifier.tourTarget("edit_options"), height = 40) { state.sheetId = sel.id }
                // Remove is the X on the tile itself (and in Options). This swatch is the tile's colour: tap to try the next one.
                // In a stack it follows the component showing right now, so swipe to another and it colours that one.
                val inStack = sel.kind == Kind.STACK
                val target = if (inStack) sel.children().let { k -> k.getOrNull((state.stackIndex[sel.id] ?: 0).coerceIn(0, (k.size - 1).coerceAtLeast(0))) } else sel
                if (target != null && target.kind != Kind.ALL) {
                    val n = pal.all.size
                    val cur = if (target.color >= 0) target.color % n else -1
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(if (cur >= 0) pal.all[cur] else pal.tiles[0])
                            .border(2.dp, pal.onGround.copy(alpha = 0.8f), CircleShape)
                            .clickable { if (inStack) state.recolorChild(sel.id, target.id, (cur + 1) % n) else state.recolor(sel.id, (cur + 1) % n) },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            val ic = if (sel != null) 36 else 40
            BarIcon("all", ic) { state.sheetId = null; state.showGrid = true }
            BarIcon("palette", ic) {
                state.finishEdit()
                state.open(Screen.Settings, "colour")
            }
            BarIcon("settings", ic) {
                state.finishEdit()
                state.open(Screen.Settings)
            }
        }
    }
}

/** Grid size, from the edit bar: columns across and rows that fit on one screen. */
@Composable
fun GridSheet(state: HomeState) {
    val pal = state.palette
    val prefs = state.prefs
    val close = { state.showGrid = false }
    Sheet(onDismiss = close, header = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText("GRID SIZE", style = dispStyle(pal.onGround, 22))
                BasicText(if (state.today) "${state.gridCols} ACROSS" else "${state.gridCols} ACROSS / ${state.gridRows} ROWS ON SCREEN", style = monoStyle(pal.onGround.copy(alpha = 0.6f), 10))
            }
            Pill("DONE", pal.tiles[0], pal.ink, height = 36, onClick = close)
        }
    }) {
        Group(
            "GRID",
            hint = "The home screen still scrolls, so you can keep adding rows.",
        ) {
            Dropdown(listOf("4" to "4 COLUMNS", "5" to "5 COLUMNS", "6" to "6 COLUMNS"), state.gridCols.toString(), "Across") { if (state.today) prefs.todayCols = it.toInt() else prefs.gridCols = it.toInt() }
            // The Today home sizes its rows like its columns, so rows on screen only apply to the classic home.
            if (!state.today) Dropdown(listOf("8" to "8 ROWS", "9" to "9 ROWS", "10" to "10 ROWS"), prefs.gridRows.toString(), "Rows on screen") { prefs.gridRows = it.toInt() }
        }
        Group("GAPS") {
            SwitchRow("Keep a gap when removing", "", prefs.keepGaps) { prefs.keepGaps = it }
        }
        Group("PREVIEW") {
            GroupPad {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(state.gridRows) { r ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(state.gridCols) { c ->
                                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(3.dp)).background(pal.tiles[(r + c) % pal.tiles.size].copy(alpha = 0.85f)))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A small round icon button, for rows that need an action or two on the right. */
@Composable
private fun MiniBtn(glyph: String, rotate: Float = 0f, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Box(
        Modifier.size(32.dp).clip(CircleShape).background(pal.onGround.copy(alpha = 0.12f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { rotationZ = rotate }) { Glyph(glyph, pal.onGround, 16.dp, 2.4f) }
    }
}

/** Options for the tile that was tapped in edit mode: layout, what it shows, stacking, remove. */
@Composable
fun TileSheet(state: HomeState, tile: Tile) {
    val pal = state.palette
    val close = { state.sheetId = null }
    Sheet(onDismiss = close, header = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(if (tile.color >= 0) pal.all[tile.color % pal.all.size] else pal.tiles[0]),
                contentAlignment = Alignment.Center,
            ) { Glyph(if (tile.glyph.isNotEmpty()) tile.glyph else kindGlyph(tile.kind), pal.ink, 22.dp, 2f) }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                BasicText(if (tile.kind == Kind.STACK) tile.kind.title else tile.label.ifEmpty { tile.kind.title }, style = dispStyle(pal.onGround, 21), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText("${tile.w}x${tile.h}", style = monoStyle(pal.onGround.copy(alpha = 0.6f), 10))
            }
            Pill("DONE", pal.tiles[0], pal.ink, height = 36, onClick = close)
        }
    }) {
        val sizeChoices = tile.allowedSizes(state.gridCols)
        Group("LAYOUT") {
            if (sizeChoices.size > 1) {
                ChipRow(sizeChoices.map { "${it.first}x${it.second}" to "${it.first} x ${it.second}" }, "${tile.w}x${tile.h}", title = "Size") { id ->
                    val (w, h) = id.split("x").map { it.toInt() }
                    state.resize(tile.id, w, h)
                }
            }
            // A stack has no colour of its own: each component in it is coloured separately, further down.
            if (tile.kind != Kind.ALL && tile.kind != Kind.STACK) ColourRow(tile.color) { state.recolor(tile.id, it) }
            ListRow("Order", "Move it in the grid", trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniBtn("back") { state.moveBy(tile.id, -1) }
                    MiniBtn("chev") { state.moveBy(tile.id, 1) }
                }
            })
        }

        KindOptions(state, tile)

        if (tile.kind != Kind.ALL) StackSection(state, tile)

        Pill("REMOVE TILE", pal.onGround.copy(alpha = 0.1f), pal.onGround, Modifier.fillMaxWidth(), height = 46) { state.remove(tile) }
        BasicText("Hold a tile and drag to move it. Drag its edges to resize.", style = bodyStyle(pal.onGround.copy(alpha = 0.5f), 12), modifier = Modifier.padding(horizontal = 4.dp))
    }
}

/** What a component shows and does: the options that depend on its kind. Used for a tile and for each component in a stack. */
@Composable
private fun KindOptions(state: HomeState, tile: Tile) {
    val pal = state.palette
    when (tile.kind) {
        Kind.CLOCK -> {
            val clockStyle = ClockArg.style(tile.arg)
            val clockPhoto = ClockArg.image(tile.arg)
            val clockDesign = ClockArg.design(tile.arg).ifEmpty { clockPhotoDesigns.first().first }
            Group("CLOCK") {
                ChipRow(clockStyles, clockStyle.ifEmpty { state.prefs.clockStyle }, title = "Style") { state.setArg(tile.id, ClockArg.set(tile.arg, style = it)) }
            }
            if (clockStyle.ifEmpty { state.prefs.clockStyle } == "poster") Group("PHOTO", hint = "Shown beside the time. It needs a tile at least 2 wide and 2 tall. Pick how the picture is cut and where it sits.") {
                ListRow(if (clockPhoto.isEmpty()) "Add a photo" else "Change photo", "Choose from your gallery", onClick = {
                    state.sheetId = null
                    state.photoRequest = tile.id
                })
                if (clockPhoto.isNotEmpty()) {
                    ChipRow(clockPhotoDesigns, clockDesign, title = "Design") { state.setArg(tile.id, ClockArg.set(tile.arg, design = it)) }
                    ListRow("Remove photo", "Back to just the time", onClick = { state.setArg(tile.id, ClockArg.set(tile.arg, image = "")) })
                }
            }
        }
        Kind.KANJI -> {
            val c = app.koma.launcher.data.Words.parse(tile.arg)
            fun save(n: app.koma.launcher.data.WordsCfg) = state.setArg(tile.id, app.koma.launcher.data.Words.encode(n))
            Group("WORDS") {
                ChipRow(listOf("text" to "MY OWN", "day" to "TODAY'S WEEKDAY", "greet" to "A HELLO EVERY HOUR"), c.mode, title = "Show") { save(c.copy(mode = it)) }
                if (c.mode == "text") {
                    GroupPad {
                        Field(
                            value = c.text,
                            onChange = { save(c.copy(text = it.take(14))) },
                            hint = "A word, a name, anything",
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(pal.onGround.copy(alpha = 0.08f)).padding(12.dp),
                        )
                    }
                    ChipRow(app.koma.launcher.data.Words.ideas.map { it to it }, c.text, title = "Need an idea?") { save(c.copy(text = it)) }
                }
            }
            Group("LOOK") {
                ChipRow(listOf("bold" to "BOLD", "light" to "LIGHT", "serif" to "SERIF", "display" to "SOFT"), c.style, title = "Style") { save(c.copy(style = it)) }
                ChipRow(listOf("auto" to "AUTOMATIC", "stack" to "ONE LETTER PER LINE", "across" to "ALL ON ONE LINE"), c.layout, title = "Direction") { save(c.copy(layout = it)) }
                ColourRow(c.tone, "Text colour") { save(c.copy(tone = it)) }
                SwitchRow("Capital letters", "", c.caps) { save(c.copy(caps = it)) }
                SwitchRow("Faded", "A softer, quieter word", c.faded) { save(c.copy(faded = it)) }
            }
        }
        Kind.TOGGLE -> Group("SWITCH") {
            ChipRow(listOf("torch" to "TORCH", "wifi" to "WI-FI", "bt" to "BLUETOOTH", "dnd" to "DND"), tile.arg.ifEmpty { "torch" }, title = "Controls") { state.setArg(tile.id, it) }
        }
        Kind.WEATHER -> WeatherOptions(state)
        Kind.MUSIC -> {
            val musicApp = MusicArg.app(tile.arg)
            val musicStyle = MusicArg.style(tile.arg)
            val musicDesign = MusicArg.design(tile.arg).ifEmpty { clockPhotoDesigns.first().first }
            val chosen = state.apps.firstOrNull { it.key == musicApp }
            Group("MUSIC") {
                ChipRow(musicStyles, musicStyle.ifEmpty { state.prefs.musicStyle }, title = "Style") { state.setArg(tile.id, MusicArg.set(tile.arg, style = it)) }
                if (musicStyle.ifEmpty { state.prefs.musicStyle } == "poster") ChipRow(clockPhotoDesigns, musicDesign, title = "Cover design") { state.setArg(tile.id, MusicArg.set(tile.arg, design = it)) }
                ListRow("Music app", chosen?.label ?: "Automatic: the player that is playing", onClick = {
                    state.sheetId = null
                    state.openDrawer(pickFor = Pick("music", tile.id))
                })
                if (chosen != null) ListRow("Use automatic", "Playing player, last player, or default", onClick = { state.setArg(tile.id, MusicArg.set(tile.arg, app = "")) })
            }
        }
        Kind.COUNTDOWN -> {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val d = app.koma.launcher.data.Countdown.date(tile.arg) ?: java.time.LocalDate.now()
            Group("COUNTDOWN") {
                GroupPad {
                    BasicText("NAME", style = monoStyle(pal.onGround.copy(alpha = 0.6f), 10))
                    Field(
                        value = app.koma.launcher.data.Countdown.rawName(tile.arg),
                        onChange = { state.setArg(tile.id, app.koma.launcher.data.Countdown.make(d, it.take(20))) },
                        hint = "What are you counting down to?",
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(pal.onGround.copy(alpha = 0.08f)).padding(12.dp),
                    )
                }
                ListRow("Date", d.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")), onClick = {
                    android.app.DatePickerDialog(
                        ctx,
                        { _, y, m, day -> state.setArg(tile.id, app.koma.launcher.data.Countdown.make(java.time.LocalDate.of(y, m + 1, day), app.koma.launcher.data.Countdown.rawName(tile.arg))) },
                        d.year, d.monthValue - 1, d.dayOfMonth,
                    ).show()
                })
            }
        }
        Kind.TIMER -> Group("TIMER") {
            ChipRow(listOf("1", "3", "5", "10", "15", "20", "25", "30", "45", "60", "90").map { it to "$it MIN" }, tile.arg.ifEmpty { "10" }, title = "Length") { state.setArg(tile.id, it) }
        }
        Kind.BLUETOOTH -> Group("DEVICE ICONS") {
            ChipRow(app.koma.launcher.data.IconStyles.btIcons, state.prefs.btIcon, title = "Style") { state.prefs.btIcon = it }
        }
        Kind.APP -> Group("APP") {
            ListRow("App", state.apps.firstOrNull { it.key == tile.arg }?.label ?: "Choose an app", onClick = {
                state.sheetId = null
                state.openDrawer(pickFor = Pick("assign", tile.id))
            })
        }
        Kind.GROUP -> Group("GROUP") {
            ListRow("Apps in this group", "Choose which apps", onClick = {
                state.sheetId = null
                state.openDrawer(pickFor = Pick("group", tile.id))
            })
        }
        Kind.ALL -> {
            val gest = Gestures.parse(tile.arg)
            Group("GESTURES") {
                Gestures.names.forEach { (key, title) ->
                    val cur = gest[key] ?: ""
                    val appLabel = if (cur.startsWith("app:")) state.apps.firstOrNull { it.key == cur.removePrefix("app:") }?.label ?: "App" else null
                    val options = Gestures.actions + listOfNotNull(appLabel?.let { cur to "APP: ${it.uppercase()}" }) + ("app" to "OPEN AN APP...")
                    Dropdown(options, cur, title, "NOTHING") { v ->
                        if (v == "app") {
                            state.sheetId = null
                            state.openDrawer(pickFor = Pick("gesture-$key", tile.id))
                        } else state.setArg(tile.id, Gestures.set(tile.arg, key, v))
                    }
                }
            }
        }
        Kind.PHOTO -> Group("PHOTO") {
            ListRow("Photo", "Choose from your gallery", onClick = {
                state.sheetId = null
                state.photoRequest = tile.id
            })
        }
        else -> Unit
    }
}

/** Add a tile: every kind of tile Koma has, as one searchable list. */
@Composable
fun PickerSheet(state: HomeState) {
    val pal = state.palette
    val n = pal.tiles.size
    fun placed() {
        state.showPicker = false
        if (state.screen != Screen.Home) {
            state.goHome()
            state.startEdit()
        }
    }
    val stackOn = state.stackTarget
    var q by remember { mutableStateOf("") }
    Sheet(onDismiss = {
        state.stackTarget = null
        state.showPicker = false
    }, header = {
        BasicText(if (stackOn != null) "STACK WITH" else "ADD COMPONENT", style = dispStyle(pal.onGround, 22))
        BasicText(if (stackOn != null) "ONLY TILES THAT FIT THE STACK'S SIZE" else "TAP TO PLACE / RESIZE AFTERWARDS IN EDIT MODE", style = monoStyle(pal.onGround.copy(alpha = 0.6f), 9))
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(16.dp)).background(pal.onGround.copy(alpha = 0.1f)).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph("search", pal.onGround.copy(alpha = 0.7f), 20.dp, 2f)
            Spacer(Modifier.width(10.dp))
            Field(q, { q = it }, "Search tiles", Modifier.weight(1f))
        }
    }) {
        if (q.isBlank()) {
            Group("QUICK SHORTCUTS") {
                Dropdown(Defaults.roleList.map { it.first to it.first }, "", "Common apps", "CHOOSE") { label ->
                    val glyph = Defaults.roleList.first { it.first == label }.second
                    val t = state.addRole(label, glyph)
                    placed()
                    state.selectedId = t.id
                }
            }
        }
        val hasAll = state.tiles.any { it.kind == Kind.ALL }
        val kinds = Kind.values().filter { it != Kind.ALL || !hasAll }.filter { it != Kind.STACK }
            .filter { stackOn == null || it != Kind.SPACE }
            .filter {
                val tgt = state.tiles.firstOrNull { t -> t.id == stackOn }
                stackOn == null || tgt == null || (it != Kind.ALL && it != Kind.GROUP && it != Kind.PHOTO && (tgt.w to tgt.h) in it.sizesFor(state.gridCols))
            }.filter {
                val t = q.trim()
                t.isEmpty() || it.title.contains(t, ignoreCase = true) || it.desc.contains(t, ignoreCase = true)
            }
        fun choose(kind: Kind) {
            when (kind) {
                Kind.APP -> {
                    state.showPicker = false
                    state.stackTarget = null
                    state.openDrawer(pickFor = if (stackOn != null) Pick("stack", stackOn) else Pick("add", null))
                }
                Kind.GROUP -> {
                    val t = state.add(Kind.GROUP)
                    state.showPicker = false
                    state.openDrawer(pickFor = Pick("group", t.id))
                }
                Kind.WIDGET -> {
                    state.showPicker = false
                    state.showWidgets = true
                }
                Kind.PHOTO -> {
                    val t = state.add(Kind.PHOTO)
                    placed()
                    state.photoRequest = t.id
                }
                else -> {
                    val t = state.add(kind)
                    placed()
                    state.selectedId = stackOn ?: t.id
                }
            }
        }
        // Components as colourful cards, two across.
        if (kinds.isEmpty()) BasicText("Nothing matches.", style = bodyStyle(pal.onGround.copy(alpha = 0.7f), 14))
        kinds.chunked(2).forEachIndexed { r, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { c, kind ->
                    Column(
                        Modifier.weight(1f).heightIn(min = 118.dp).clip(RoundedCornerShape(20.dp)).background(pal.tiles[(r * 2 + c) % n])
                            .clickable { choose(kind) }.padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Glyph(kindGlyph(kind), pal.ink, 26.dp, 2f)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            BasicText(kind.title, style = dispStyle(pal.ink, 18), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicText(kind.desc, style = bodyStyle(pal.ink.copy(alpha = 0.7f), 11), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** The installed Android widgets, to pick one for a Widget tile. */
@Composable
fun WidgetChooser(state: HomeState) {
    val pal = state.palette
    val ctx = LocalContext.current
    val providers = remember { state.widgets.providers() }
    fun choose(info: AppWidgetProviderInfo) {
        state.showWidgets = false
        val id = state.widgets.allocate()
        state.pendingWidget = when {
            !state.widgets.bindIfAllowed(id, info) -> PendingWidget(id, info, "bind")
            info.configure != null -> PendingWidget(id, info, "configure")
            else -> {
                state.addWidgetTile(id, info)
                state.afterWidgetAdd()
                null
            }
        }
    }
    Sheet(onDismiss = {
        state.stackTarget = null
        state.folderTarget = null
        state.showWidgets = false
    }, header = {
        BasicText("ANDROID WIDGETS", style = dispStyle(pal.onGround, 22))
        BasicText("DRAWN BY THE APP, FRAMED BY KOMA", style = monoStyle(pal.onGround.copy(alpha = 0.6f), 9))
    }) {
        Group("INSTALLED") {
            if (providers.isEmpty()) GroupPad { BasicText("No widgets are installed on this phone.", style = bodyStyle(pal.onGround.copy(alpha = 0.7f), 14)) }
            providers.forEach { info ->
                val (w, h) = state.widgets.cells(info)
                ListRow(
                    info.loadLabel(ctx.packageManager).toString(),
                    "${info.provider.packageName} / ${w}x${h}",
                    "widget", pal.tiles[0],
                    onClick = { choose(info) },
                )
            }
        }
    }
}


/** Weather tile options: units, which cities, and what the tile shows. */
@Composable
internal fun WeatherOptions(state: HomeState) {
    val prefs = state.prefs
    Group("WEATHER") {
        ChipRow(listOf("auto" to "AUTO", "c" to "CELSIUS", "f" to "FAHRENHEIT"), prefs.weatherUnit, title = "Temperature") { state.setWeatherSource(unit = it) }
    }
    PlaceSearch(state, second = false)
    PlaceSearch(state, second = true)
    Group("SHOW") {
        SwitchRow("High and low", "Today's range", prefs.weatherHiLo) { prefs.weatherHiLo = it }
        SwitchRow("City name", "Under the temperature", prefs.weatherCity) { prefs.weatherCity = it }
        SwitchRow("Cat", "The cat on the large tile", prefs.weatherCat) { prefs.weatherCat = it }
    }
}

/** One card that chooses a weather place: the current one, or a city found by name. The second place has no "my location". */
@Composable
private fun PlaceSearch(state: HomeState, second: Boolean) {
    val pal = state.palette
    val prefs = state.prefs
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<app.koma.launcher.data.WeatherRepo.Place>>(emptyList()) }
    // null = nothing asked yet; otherwise a message under the field.
    var note by remember { mutableStateOf<String?>(null) }
    val current = if (second) prefs.weatherPlace2 else prefs.weatherPlace
    val chosenName = current.split("|").drop(2).joinToString("|")

    suspend fun runSearch() {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            note = if (q.isEmpty()) null else "Type at least 2 letters."
            return
        }
        note = "Searching..."
        val found = app.koma.launcher.data.WeatherRepo.search(q)
        // Ignore an answer for text that has since been changed.
        if (q != query.trim()) return
        results = found ?: emptyList()
        note = when {
            found == null -> "Can't reach the city search. Check your internet connection."
            found.isEmpty() -> "No city found for \"$q\"."
            else -> null
        }
    }
    // Search as you type, after a short pause.
    androidx.compose.runtime.LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList(); note = null
        } else {
            kotlinx.coroutines.delay(450)
            runSearch()
        }
    }

    val none = if (second) "NONE" else "MY LOCATION"
    Group(if (second) "SECOND PLACE" else "PLACE", if (second) "Shown beside the first place on the wide sizes." else null) {
        ChipRow(listOf("" to none, "x" to if (chosenName.isEmpty()) "CHOSEN CITY" else chosenName.uppercase().take(16)), if (current.isEmpty()) "" else "x", title = "Show") {
            if (it.isEmpty()) {
                if (second) state.setWeatherSource(place2 = "") else state.setWeatherSource(place = "")
            }
        }
        GroupPad {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(pal.onGround.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph("search", pal.onGround.copy(alpha = 0.6f), 18.dp, 2f)
                Spacer(Modifier.width(10.dp))
                Field(query, { query = it }, "Search a city, e.g. Paris", Modifier.weight(1f), onDone = { scope.launch { runSearch() } })
            }
            note?.let { BasicText(it, style = bodyStyle(pal.onGround.copy(alpha = 0.7f), 13)) }
        }
        results.forEach { r ->
            ListRow(r.label, "", "sun", pal.tiles[0], onClick = {
                val value = "${r.lat}|${r.lon}|${r.label.substringBefore(',')}"
                if (second) state.setWeatherSource(place2 = value) else state.setWeatherSource(place = value)
                results = emptyList()
                query = ""
                note = null
            })
        }
    }
}

/** In a tile's options: put another component in the same spot (a stack), reorder or drop what is stacked, or unstack. */
@Composable
private fun StackSection(state: HomeState, tile: Tile) {
    val pal = state.palette
    val kids = remember(tile.arg) { tile.children() }
    var openId by remember(tile.id) { mutableStateOf<String?>(null) }
    if (tile.kind != Kind.STACK) {
        Group("STACK") {
            ListRow("Stack with another tile", "Several in one spot. Swipe to flip through.", onClick = {
                state.sheetId = null
                state.stackTarget = tile.id
                state.showPicker = true
            })
        }
        return
    }
    Group("STACK") {
        ChipRow(listOf("" to "SLIDE UP / DOWN", "h" to "SLIDE LEFT / RIGHT"), tile.label, title = "Direction") { state.setStackDirection(tile.id, it) }
        kids.forEachIndexed { i, c ->
            val name = c.label.ifEmpty {
                if (c.kind == Kind.APP) state.apps.firstOrNull { it.key == c.arg }?.label ?: "App" else c.kind.title
            }
            val isOpen = openId == c.id
            Row(
                Modifier.fillMaxWidth().background(if (isOpen) pal.onGround.copy(alpha = 0.08f) else Color.Transparent).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { openId = if (isOpen) null else c.id }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(9.dp))
                            .background(if (c.color >= 0) pal.all[c.color % pal.all.size] else pal.tiles[stackSlot(tile, i) % pal.tiles.size]),
                        contentAlignment = Alignment.Center,
                    ) { Glyph(if (c.glyph.isNotEmpty()) c.glyph else kindGlyph(c.kind), pal.ink, 16.dp, 2f) }
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        BasicText("${i + 1}  $name", style = bodyStyle(pal.onGround, 15), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText(if (isOpen) "COLOUR AND OPTIONS BELOW" else "TAP FOR COLOUR AND OPTIONS", style = monoStyle(pal.onGround.copy(alpha = 0.5f), 9))
                    }
                }
                MiniBtn("chev", -90f) { state.moveInStack(tile.id, c.id, -1) }
                MiniBtn("chev", 90f) { state.moveInStack(tile.id, c.id, 1) }
                MiniBtn("close") { state.removeFromStack(tile.id, c.id) }
            }
        }
        ListRow("Add to stack", "Another tile of the same size", onClick = {
            state.sheetId = null
            state.stackTarget = tile.id
            state.showPicker = true
        })
        ListRow("Unstack", "Put them back as separate tiles", onClick = { state.unstack(tile.id) })
    }
    // Options of the component picked above. Its size is the stack's, so there is no size choice here.
    val open = kids.firstOrNull { it.id == openId }
    if (open != null) {
        val label = open.label.ifEmpty { if (open.kind == Kind.APP) state.apps.firstOrNull { it.key == open.arg }?.label ?: "App" else open.kind.title }
        Group(
            "${label.uppercase()} IN THIS STACK",
            hint = "Size follows the stack (${tile.w}x${tile.h})",
        ) {
            if (open.kind != Kind.ALL) ColourRow(open.color) { state.recolorChild(tile.id, open.id, it) }
            ListRow("Move to home screen", "Take it out of the stack as its own tile", onClick = { state.extractFromStack(tile.id, open.id) })
        }
        KindOptions(state, open)
    }
}
