package app.koma.launcher.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.content.IntentCompat
import kotlinx.coroutines.launch
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.koma.launcher.data.Alarm
import app.koma.launcher.data.ClockMath
import app.koma.launcher.data.FocusEngine
import app.koma.launcher.data.AlarmCtl
import app.koma.launcher.data.RingInfo
import app.koma.launcher.data.RingLog
import app.koma.launcher.data.RingPrefs
import app.koma.launcher.data.RingService
import app.koma.launcher.data.TimeFmt
import app.koma.launcher.data.TimerCtl
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.graphics.graphicsLayer
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TimeZone

private val tabNames = listOf("ALARMS", "WORLD", "STOPWATCH", "TIMER", "FOCUS")
private val tabGlyphs = listOf("n_alarm", "n_world", "n_watch", "n_timer", "n_focus")

// ---- shared bits -------------------------------------------------------------------------------

private fun openBatterySettings(ctx: Context, state: Int) {
    // App info always opens. The one-tap "allow" dialog silently does nothing on some Samsung phones, so it is only a fallback.
    val intents = listOf(
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)),
        Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)),
        Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(android.provider.Settings.ACTION_SETTINGS),
    )
    for (i in intents) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            android.widget.Toast.makeText(ctx, "Tap Battery, then choose Unrestricted", android.widget.Toast.LENGTH_LONG).show()
            return
        } catch (e: Exception) {
        }
    }
    android.widget.Toast.makeText(ctx, "Open Settings, Apps, Koma, Battery, Unrestricted", android.widget.Toast.LENGTH_LONG).show()
}

internal fun needNotifications(state: HomeState, ctx: Context) {
    if (Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) state.request(Manifest.permission.POST_NOTIFICATIONS)
}

/** Width that lets [BlockText] draw [text] at a given block size. */
private fun blockWidth(text: String, block: Float): androidx.compose.ui.unit.Dp {
    var cells = 0
    for (ch in text) cells += if (ch == ':') 1 else 3
    cells += (text.length - 1).coerceAtLeast(0)
    return (cells * block).dp
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier, bold: Boolean = false) {
    val pal = LocalPalette.current
    BasicText(text, style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10, bold = bold), modifier = modifier, maxLines = 1)
}

/** A row of small squares. [lit] says which are on. */
@Composable
private fun Squares(count: Int, modifier: Modifier = Modifier, lit: (Int) -> Boolean) {
    val pal = LocalPalette.current
    Row(modifier.fillMaxWidth().height(7.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until count) {
            Box(Modifier.weight(1f).fillMaxHeight().background(if (lit(i)) pal.tiles[0] else pal.onGround.copy(alpha = 0.18f)))
        }
    }
}

@Composable
private fun TapCard(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Box(
        modifier.height(48.dp).clip(Mid).background(if (selected) pal.onGround else cardBg()).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { BasicText(text, style = monoStyle(if (selected) pal.ground else pal.onGround, 12, bold = true)) }
}

// ---- the app -----------------------------------------------------------------------------------

/** Koma's own clock: alarms, world clock, stopwatch, timer, and Focus, all in the Focus look. */
@Composable
fun ClockApp(state: HomeState) {
    val pal = state.palette
    val clock = state.clock
    LaunchedEffect(Unit) { clock.refresh() }
    val now by produceState(ZonedDateTime.now()) {
        while (true) {
            value = ZonedDateTime.now()
            awaitVisible()
            delay(20_000L)
        }
    }
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var pickZone by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var converter by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val chrome = !(state.clockTab == 4 && focusEditing.value)
    val land = androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    var groupOpen by remember { mutableStateOf<String?>(null) }
    val openName = groupOpen?.let { id -> if (id.isEmpty()) "Ungrouped" else clock.groups.firstOrNull { it.id == id }?.name }
    BackHandler(enabled = state.clockTab == 0 && groupOpen != null) { groupOpen = null }

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize().background(pal.ground).statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp)) {
        if (land && chrome) {
            Column(
                Modifier.padding(vertical = 8.dp).width(64.dp).fillMaxHeight().clip(RoundedCornerShape(32.dp)).background(surfaceColor(0.1f)).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                tabNames.forEachIndexed { i, _ ->
                    val on = i == state.clockTab
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(if (on) pal.tiles[0] else Color.Transparent).clickable { state.clockTab = i },
                        contentAlignment = Alignment.Center,
                    ) { Glyph(tabGlyphs[i], if (on) pal.ink else pal.onGround.copy(alpha = 0.55f), 22.dp, 1.8f) }
                }
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (chrome) {
                val sub = when (state.clockTab) {
                    0 -> if (openName != null) {
                        val ms = clock.alarms.filter { x -> if (groupOpen == "") clock.groups.none { it.id == x.group } else x.group == groupOpen }
                        if (ms.isEmpty()) "NO ALARMS" else "${ms.count { it.on }} OF ${ms.size} ON"
                    } else clock.upcoming()?.let { (a, at) -> "NEXT ${ClockMath.parts(a.hour, a.minute, true).first} / IN ${ClockMath.until(now, at).uppercase()}" } ?: "NO ALARM ON"
                    1 -> "${1 + clock.zones.size} PLACE${if (clock.zones.isEmpty()) "" else "S"}"
                    2 -> if (Stopwatch.running.value) "RUNNING" else "READY"
                    3 -> if (state.prefs.timerEnd > 0L) "RUNNING" else if (state.prefs.timerLeft > 0L) "PAUSED" else "READY"
                    else -> {
                        val today = LocalDate.now()
                        val zone = ZoneId.systemDefault()
                        val todays = FocusEngine.runs.filter { Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() == today }
                        "TODAY ${minText(todays.sumOf { it.minutes })} / ${todays.size} SESSION${if (todays.size == 1) "" else "S"}"
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(surfaceColor(0.1f)).clickable { if (state.clockTab == 0 && groupOpen != null) groupOpen = null else state.exitApp() }, contentAlignment = Alignment.Center) {
                        Glyph("back", pal.onGround, 22.dp, 2.4f)
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        BasicText(if (state.clockTab == 0 && openName != null) openName else "Clock", style = dispStyle(pal.onGround, 30), maxLines = 1)
                        Label(sub)
                    }
                    Box(Modifier.size(48.dp).clip(CircleShape).background(surfaceColor(0.1f)).clickable { state.clockPage = "settings" }, contentAlignment = Alignment.Center) {
                        Glyph("settings", pal.onGround, 22.dp, 2.2f)
                    }
                    if (state.clockTab == 0 || state.clockTab == 1) {
                        Box(
                            Modifier.padding(start = 8.dp).size(48.dp).clip(CircleShape).background(pal.tiles[0]).clickable {
                                if (state.clockTab == 0) editing = Alarm(clock.newId(), 7, 0, 0, group = groupOpen?.takeIf { it.isNotEmpty() } ?: "", snooze = state.prefs.defaultSnooze) else pickZone = { id -> clock.addZone(id) }
                            },
                            contentAlignment = Alignment.Center,
                        ) { Glyph("plus", pal.ink, 22.dp, 2.6f) }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.clockTab) {
                    0 -> AlarmsPage(state, now, groupOpen, { groupOpen = it }) { editing = it }
                    1 -> WorldPage(state, { pickZone = { id -> clock.addZone(id) } }, { converter = true })
                    2 -> StopwatchPage(state)
                    3 -> TimerPage(state)
                    else -> FocusScreen(state, embedded = true)
                }
            }
            if (chrome && !land) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp).clip(CircleShape).background(surfaceColor(0.1f)).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tabNames.forEachIndexed { i, name ->
                        val on = i == state.clockTab
                        Row(
                            Modifier.weight(if (on) 2.3f else 1f).height(52.dp).clip(CircleShape)
                                .background(if (on) pal.tiles[0] else Color.Transparent).clickable { state.clockTab = i },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                        ) {
                            Glyph(tabGlyphs[i], if (on) pal.ink else pal.onGround.copy(alpha = 0.55f), 22.dp, 1.8f)
                            if (on) {
                                Spacer(Modifier.width(7.dp))
                                BasicText(name, style = monoStyle(pal.ink, 9, bold = true), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        }
        editing?.let { a ->
            AlarmEditor(state, a, isNew = clock.alarms.none { it.id == a.id }, onDone = { editing = null })
        }
        if (converter) {
            Box(Modifier.fillMaxSize().background(pal.ground).pointerInput(Unit) { detectTapGestures { } }) {
                ConverterPage(state, { converter = false }, { cb -> pickZone = cb })
            }
            BackHandler { converter = false }
        }
        pickZone?.let { cb ->
            Box(Modifier.fillMaxSize().background(pal.ground).pointerInput(Unit) { detectTapGestures { } }) {
                ZonePicker(state, { id -> cb(id); pickZone = null }, { pickZone = null })
            }
            BackHandler { pickZone = null }
        }
        state.clockPage?.let { pg ->
            Box(Modifier.fillMaxSize().background(pal.ground)) {
                when (pg) {
                    "font" -> ClockFontSettings(state) { state.clockPage = "widget" }
                    "widget" -> ClockStyleSettings(state, { state.clockPage = "settings" }, { state.clockPage = "font" })
                    else -> ClockSettingsPage(state) { state.clockPage = null }
                }
            }
            BackHandler { state.clockPage = when (pg) { "font" -> "widget"; "widget" -> "settings"; else -> null } }
        }
    }
}

/** One action tile: a small caption and a big word at the bottom left, an icon chip at the top right. */
@Composable
private fun ActionTile(word: String, cap: String, glyph: String, primary: Boolean, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val a = if (enabled) 1f else 0.4f
    val fg = if (primary) pal.ink else pal.onGround
    val land = androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    Box(
        modifier.fillMaxHeight().clip(TileShape).background(if (primary) pal.tiles[0].copy(alpha = a) else cardBg())
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(
            Modifier.align(Alignment.TopEnd).padding(if (land) 8.dp else 12.dp).size(if (land) 26.dp else 34.dp).clip(CircleShape)
                .background(if (primary) pal.ink.copy(alpha = a) else surfaceColor(0.14f)),
            contentAlignment = Alignment.Center,
        ) { Glyph(glyph, if (primary) pal.tiles[0] else pal.onGround.copy(alpha = a), if (land) 12.dp else 16.dp, 2.4f) }
        Column(Modifier.align(Alignment.BottomStart).padding(start = if (land) 12.dp else 18.dp, bottom = if (land) 8.dp else 14.dp)) {
            BasicText(cap, style = monoStyle(fg.copy(alpha = 0.65f * a), 9, bold = true), maxLines = 1)
            BasicText(word, style = dispStyle(fg.copy(alpha = a), if (land) (if (primary) 20 else 15) else if (primary) 28 else 22), maxLines = 1, softWrap = false)
        }
    }
}

private fun capFor(word: String) = when (word) {
    "RESET" -> "CLEAR"; "LAP" -> "MARK"; "MIN" -> "ADD ONE"; "END" -> "DISMISS"
    "START" -> "READY"; "STOP", "PAUSE" -> "RUNNING"; "RESUME" -> "PAUSED"; "RESTART" -> "AGAIN"
    else -> ""
}

@Composable
private fun RowScope.SideBtn(glyph: String, label: String, enabled: Boolean = true, onClick: () -> Unit) =
    ActionTile(label, capFor(label), if (glyph == "+1") "plus" else glyph, false, Modifier.weight(0.75f), enabled, onClick)

@Composable
private fun RowScope.MainBtn(glyph: String, label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) =
    ActionTile(label, capFor(label), glyph, true, modifier, enabled, onClick)

// ---- alarms ------------------------------------------------------------------------------------

private fun daysText(days: Int): String = ClockMath.daysText(days, dayNames)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmsPage(state: HomeState, now: ZonedDateTime, open: String?, setOpen: (String?) -> Unit, onEdit: (Alarm) -> Unit) {
    val pal = state.palette
    val clock = state.clock
    val ctx = LocalContext.current
    val is24 = TimeFmt.is24()
    var groupSheet by remember { mutableStateOf<String?>(null) }
    val groups = clock.groups
    val up = clock.upcoming()
    fun ordered(list: List<Alarm>) = list.sortedWith(compareBy({ !it.on }, { ClockMath.next(it, now)?.toEpochSecond() ?: Long.MAX_VALUE }))
    fun members(gid: String): List<Alarm> =
        if (gid.isEmpty()) clock.alarms.filter { a -> groups.none { it.id == a.group } } else clock.alarms.filter { it.group == gid }
    LaunchedEffect(open, groups.size) {
        if (open != null && open.isNotEmpty() && groups.none { it.id == open }) setOpen(null)
    }

    var sel by remember { mutableStateOf(setOf<String>()) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(open) { sel = emptySet() }
    LaunchedEffect(sel) { confirm = false }
    BackHandler(enabled = sel.isNotEmpty()) { sel = emptySet() }

    @Composable
    fun AlarmRow(a: Alarm) {
        val (t, s) = ClockMath.parts(a.hour, a.minute, is24)
        val line = listOfNotNull(daysText(a.days), a.label.trim().takeIf { it.isNotEmpty() }?.uppercase()).joinToString("  /  ")
        val inText = if (a.on) ClockMath.next(a, now)?.let { "IN " + ClockMath.until(now, it).uppercase() } else null
        val picked = a.id in sel
        val selecting = sel.isNotEmpty()
        val shape = Mid
        Row(
            Modifier.fillMaxWidth().clip(shape).background(if (picked) surfaceColor(0.14f) else cardBg())
                .then(if (picked) Modifier.border(1.5.dp, pal.tiles[0], shape) else Modifier)
                .combinedClickable(
                    onClick = { if (selecting) sel = if (picked) sel - a.id else sel + a.id else onEdit(a) },
                    onLongClick = { sel = if (picked) sel - a.id else sel + a.id },
                )
                .padding(start = if (selecting) 16.dp else 20.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selecting) {
                Box(
                    Modifier.padding(end = 14.dp).size(24.dp).clip(CircleShape).background(if (picked) pal.tiles[0] else Color.Transparent)
                        .border(1.5.dp, if (picked) pal.tiles[0] else pal.onGround.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (picked) Glyph("check", pal.ink, 14.dp, 3f) }
            }
            Column(Modifier.weight(1f).alpha(if (a.on || selecting) 1f else 0.4f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BlockText(t, pal.onGround, Modifier.width(blockWidth(t, 6.4f)).height(28.dp))
                    if (s.isNotEmpty()) BasicText(s, style = monoStyle(pal.onGround, 12, bold = true))
                    Spacer(Modifier.weight(1f))
                    if (inText != null) Label(inText, Modifier.padding(end = 4.dp))
                }
                Label(line, bold = false)
            }
            if (!selecting) KToggle(a.on) { on -> clock.setOn(a.id, on); if (on) needNotifications(state, ctx) }
        }
    }

    @Composable
    fun SelBar(all: List<Alarm>) {
        Column(Modifier.fillMaxWidth().clip(Mid).background(deepBg()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(surfaceColor(0.1f)).clickable { sel = emptySet() }, contentAlignment = Alignment.Center) {
                    Glyph("close", pal.onGround, 16.dp, 2.4f)
                }
                Label("${sel.size} SELECTED", Modifier.weight(1f).padding(start = 12.dp), bold = true)
                Pill(if (sel.size == all.size) "NONE" else "ALL", surfaceColor(0.12f), pal.onGround, height = 34) {
                    sel = if (sel.size == all.size) emptySet() else all.map { it.id }.toSet()
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("TURN ON", surfaceColor(0.12f), pal.onGround, Modifier.weight(1f), height = 42) { clock.setAll(sel, true); needNotifications(state, ctx); sel = emptySet() }
                Pill("TURN OFF", surfaceColor(0.12f), pal.onGround, Modifier.weight(1f), height = 42) { clock.setAll(sel, false); sel = emptySet() }
                Pill(if (confirm) "SURE?" else "DELETE", pal.onGround, pal.ground, Modifier.weight(1f), height = 42) {
                    if (confirm) { clock.deleteMany(sel); sel = emptySet() } else confirm = true
                }
            }
        }
    }

    @Composable
    fun GroupCard(title: String, gid: String) {
        val mine = members(gid)
        val next = mine.filter { it.on }.mapNotNull { x -> ClockMath.next(x, now)?.let { x to it } }.minByOrNull { it.second.toEpochSecond() }
        val sub = if (mine.isEmpty()) "NO ALARMS" else "${mine.count { it.on }} OF ${mine.size} ON" +
            (next?.let { (x, _) -> "  /  NEXT " + ClockMath.text(x.hour, x.minute, is24) } ?: "")
        Row(
            Modifier.fillMaxWidth().clip(Mid).background(cardBg()).clickable { setOpen(gid) }.padding(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                BasicText(title, style = bodyStyle(pal.onGround, 20, medium = true), maxLines = 1)
                Label(sub)
            }
            if (mine.isNotEmpty()) {
                val ids = mine.map { it.id }.toSet()
                KToggle(mine.any { it.on }) { on -> clock.setAll(ids, on); if (on) needNotifications(state, ctx) }
            }
            Box(Modifier.padding(start = 6.dp).graphicsLayer(rotationZ = 180f)) { Glyph("back", pal.onGround.copy(alpha = 0.45f), 18.dp, 2.4f) }
        }
    }

    val inGroup = open != null && (open.isEmpty() || groups.any { it.id == open })
    if (inGroup) {
        val gid = open
        val g = groups.firstOrNull { it.id == gid }
        val mine = ordered(members(gid))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (g != null && sel.isEmpty()) {
                item(key = "edit") {
                    Row(
                        Modifier.fillMaxWidth().clip(Mid).background(deepBg()).clickable { groupSheet = g.id }.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Label("RENAME OR DELETE THIS GROUP", Modifier.weight(1f))
                        Glyph("settings", pal.onGround.copy(alpha = 0.55f), 18.dp, 2.2f)
                    }
                }
            }
            if (sel.isNotEmpty()) item(key = "sel") { SelBar(mine) }
            if (mine.isEmpty()) {
                item(key = "empty") {
                    Column(Modifier.fillMaxWidth().clip(Big).background(cardBg()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BasicText("No alarms here", style = dispStyle(pal.onGround, 26))
                        BasicText("Tap the + at the top to add one.", style = bodyStyle(pal.onGround.copy(alpha = 0.6f), 15))
                    }
                }
            }
            items(mine, key = { it.id }) { AlarmRow(it) }
            item(key = "pad") { Spacer(Modifier.height(4.dp)) }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "hero") {
                Box(Modifier.fillMaxWidth().height(150.dp).clip(Big).background(cardBg())) {
                    if (up != null) {
                        val (t, s) = ClockMath.parts(up.first.hour, up.first.minute, is24)
                        BlockText(t, pal.onGround, Modifier.fillMaxSize().padding(start = 26.dp, end = 26.dp, top = 50.dp, bottom = 30.dp))
                        Label("NEXT ALARM" + if (s.isNotEmpty()) "  $s" else "", Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 20.dp))
                        Label("IN ${ClockMath.until(now, up.second).uppercase()}", Modifier.align(Alignment.TopEnd).padding(end = 24.dp, top = 20.dp))
                    } else {
                        BlockText("00:00", pal.onGround.copy(alpha = 0.22f), Modifier.fillMaxSize().padding(start = 26.dp, end = 26.dp, top = 50.dp, bottom = 30.dp))
                        Label("NEXT ALARM", Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 20.dp))
                        Label("NONE ON", Modifier.align(Alignment.TopEnd).padding(end = 24.dp, top = 20.dp))
                    }
                }
            }
            item(key = "g_") { GroupCard("Ungrouped", "") }
            groups.forEach { g -> item(key = "g_" + g.id) { GroupCard(g.name, g.id) } }
            item(key = "newgroup") {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp).clip(Mid).background(deepBg()).clickable { groupSheet = "new" }.padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph("plus", pal.onGround.copy(alpha = 0.6f), 18.dp, 2.4f)
                    Spacer(Modifier.width(8.dp))
                    Label("NEW GROUP")
                }
            }
            item(key = "pad") { Spacer(Modifier.height(4.dp)) }
        }
    }
    groupSheet?.let { id ->
        val existing = groups.firstOrNull { it.id == id }
        var name by remember(id) { mutableStateOf(existing?.name ?: "") }
        Sheet(onDismiss = { groupSheet = null }, header = {
            Label(if (existing == null) "NEW GROUP" else "EDIT GROUP", bold = true)
        }) {
            Row(Modifier.fillMaxWidth().clip(Mid).background(cardBg()).padding(horizontal = 20.dp, vertical = 18.dp)) {
                Field(name, { name = it.take(20) }, "Work, Gym, Weekend...", Modifier.fillMaxWidth())
            }
            if (existing != null) TapCard("DELETE GROUP", false, Modifier.fillMaxWidth()) { clock.deleteGroup(id); groupSheet = null; setOpen(null) }
            SlideTrack("SLIDE TO SAVE", "check", reverse = false, knobBg = pal.onGround, knobInk = pal.ground) {
                if (existing == null) clock.addGroup(name) else clock.renameGroup(id, name)
                groupSheet = null
            }
        }
    }
}

@Composable
private fun AlarmEditor(state: HomeState, a: Alarm, isNew: Boolean, onDone: () -> Unit) {
    val pal = state.palette
    val ctx = LocalContext.current
    val is24 = TimeFmt.is24()
    var min by remember(a.id) { mutableStateOf(a.hour * 60 + a.minute) }
    var days by remember(a.id) { mutableStateOf(a.days) }
    var name by remember(a.id) { mutableStateOf(a.label) }
    var group by remember(a.id) { mutableStateOf(a.group) }
    fun setMin(v: Int) { min = ((v % 1440) + 1440) % 1440 }
    val h = min / 60
    val m = min % 60
    val (t, s) = ClockMath.parts(h, m, is24)
    val next = ClockMath.next(a.copy(hour = h, minute = m, days = days, skip = 0L), ZonedDateTime.now())

    Sheet(onDismiss = onDone, header = {
        Label((if (isNew) "NEW ALARM" else "EDIT ALARM"), bold = true)
    }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Label("RINGS " + ClockMath.text(h, m, is24), Modifier.weight(1f))
            Label(if (next != null) "IN ${ClockMath.until(ZonedDateTime.now(), next).uppercase()}" else "NEVER")
        }
        TimeWheels(min, is24) { setMin(it) }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            ClockMath.dayLetters.forEachIndexed { d, l ->
                val on = days and (1 shl d) != 0
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(if (on) pal.tiles[0] else Color.Transparent)
                        .border(1.5.dp, if (on) pal.tiles[0] else pal.onGround.copy(alpha = 0.22f), CircleShape)
                        .clickable { days = days xor (1 shl d) },
                    contentAlignment = Alignment.Center,
                ) { BasicText(l, style = bodyStyle(if (on) pal.ink else pal.onGround.copy(alpha = 0.6f), 16, medium = true)) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("ONCE" to 0, "WEEKDAYS" to 31, "WEEKENDS" to 96, "DAILY" to 127).forEach { (n, v) ->
                val on = days == v
                Box(
                    Modifier.clip(CircleShape).background(if (on) surfaceColor(0.12f) else Color.Transparent).clickable { days = v }.padding(horizontal = 12.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) { BasicText(n, style = monoStyle(if (on) pal.onGround else pal.onGround.copy(alpha = 0.45f), 10, bold = on)) }
            }
        }
        Row(Modifier.fillMaxWidth().clip(Mid).background(cardBg()).padding(horizontal = 20.dp, vertical = 16.dp)) {
            Field(name, { name = it.take(30) }, "Label (optional)", Modifier.fillMaxWidth())
        }
        if (state.clock.groups.isNotEmpty()) {
            ChipRow(listOf("" to "No group") + state.clock.groups.map { it.id to it.name }, group, title = "Group") { group = it }
        }
        if (!isNew) {
            Box(Modifier.fillMaxWidth().height(44.dp).clip(CircleShape).clickable { state.clock.delete(a.id); onDone() }, contentAlignment = Alignment.Center) {
                BasicText("DELETE ALARM", style = monoStyle(pal.onGround.copy(alpha = 0.55f), 11, bold = true))
            }
        }
        SlideTrack("SLIDE TO SAVE", "check", reverse = false, knobBg = pal.onGround, knobInk = pal.ground) {
            state.clock.save(a.copy(hour = h, minute = m, skip = 0L, days = days, label = name.trim(), group = group, on = true))
            needNotifications(state, ctx)
            onDone()
        }
    }
}

/** Hour and minute wheels (with AM/PM on a 12 hour clock) over a single minute-of-day value. */
@Composable
internal fun TimeWheels(min: Int, is24: Boolean, onMin: (Int) -> Unit) {
    val pal = LocalPalette.current
    val h = min / 60
    val m = min % 60
    fun set(v: Int) = onMin(((v % 1440) + 1440) % 1440)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Box(Modifier.align(Alignment.Center).fillMaxWidth().height(64.dp).clip(Mid).background(deepBg()))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Wheel(
                        if (is24) h else h % 12, if (is24) 24 else 12,
                        { set((if (is24) it else it + (if (h >= 12) 12 else 0)) * 60 + m) },
                        Modifier.weight(1f),
                        fmt = { v -> "%02d".format(if (!is24 && v == 0) 12 else v) },
                    )
                    Wheel(m, 60, { set(h * 60 + it) }, Modifier.weight(1f))
                }
            }
            if (!is24) {
                Column(Modifier.padding(start = 12.dp).width(76.dp).clip(Mid).background(deepBg()).padding(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("AM" to false, "PM" to true).forEach { (n, pm) ->
                        val on = (h >= 12) == pm
                        Box(
                            Modifier.fillMaxWidth().height(58.dp).clip(Mid).background(if (on) pal.onGround else Color.Transparent)
                                .clickable { set((h % 12 + (if (pm) 12 else 0)) * 60 + m) },
                            contentAlignment = Alignment.Center,
                        ) { BasicText(n, style = monoStyle(if (on) pal.ground else pal.onGround.copy(alpha = 0.55f), 14, bold = true)) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Row(Modifier.weight(1f)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Label("HOUR") }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Label("MIN") }
            }
            if (!is24) Spacer(Modifier.width(88.dp))
        }
    }
}

// ---- world clock -------------------------------------------------------------------------------

private val popularZones = listOf(
    "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles", "America/Toronto", "America/Mexico_City",
    "America/Sao_Paulo", "Europe/London", "Europe/Paris", "Europe/Berlin", "Europe/Madrid", "Europe/Istanbul", "Europe/Moscow",
    "Africa/Cairo", "Africa/Johannesburg", "Africa/Lagos", "Asia/Dubai", "Asia/Kolkata", "Asia/Dhaka", "Asia/Bangkok",
    "Asia/Singapore", "Asia/Hong_Kong", "Asia/Shanghai", "Asia/Tokyo", "Asia/Seoul", "Australia/Sydney", "Pacific/Auckland", "UTC",
)

private val zoneAliases = mapOf(
    "Asia/Kolkata" to "india mumbai delhi bombay calcutta bangalore chennai hyderabad ist",
    "America/New_York" to "usa united states eastern est edt",
    "America/Chicago" to "usa united states central cst cdt",
    "America/Denver" to "usa united states mountain mst mdt",
    "America/Los_Angeles" to "usa united states pacific pst pdt california san francisco seattle",
    "Europe/London" to "uk united kingdom britain england gmt bst",
    "Asia/Shanghai" to "china beijing cst",
    "Asia/Dubai" to "uae emirates abu dhabi",
    "Asia/Ho_Chi_Minh" to "vietnam saigon",
    "Asia/Yangon" to "myanmar burma rangoon",
    "Asia/Kathmandu" to "nepal",
    "Asia/Karachi" to "pakistan",
    "Europe/Kyiv" to "ukraine kiev",
    "Pacific/Auckland" to "new zealand nz",
    "Australia/Sydney" to "australia aest melbourne canberra",
    "UTC" to "utc gmt coordinated universal zulu",
)

private data class ZInfo(val id: String, val city: String, val region: String, val long: String, val search: String)

private fun buildZones(): List<ZInfo> =
    ZoneId.getAvailableZoneIds()
        .filter { id -> id == "UTC" || (id.contains('/') && !id.startsWith("Etc") && !id.startsWith("SystemV") && !id.startsWith("US/") && !id.startsWith("Canada/")) }
        .map { id ->
            val long = TimeZone.getTimeZone(id).getDisplayName(false, TimeZone.LONG, Locale.getDefault())
            ZInfo(
                id, ClockMath.cityName(id), id.substringBefore('/').replace('_', ' '), long,
                (id.replace('_', ' ') + " " + long + " " + (zoneAliases[id] ?: "")).lowercase(),
            )
        }
        .sortedWith(compareBy({ it.region }, { it.city }))

private fun gmtText(z: ZonedDateTime): String {
    val s = z.offset.totalSeconds
    if (s == 0) return "GMT"
    val a = Math.abs(s)
    return "GMT" + (if (s < 0) "-" else "+") + a / 3600 + (if (a % 3600 != 0) ":%02d".format((a % 3600) / 60) else "")
}

private fun isDst(z: ZonedDateTime): Boolean = z.zone.rules.isDaylightSavings(z.toInstant())

private fun clockText(z: ZonedDateTime, is24: Boolean): String = ClockMath.text(z.hour, z.minute, is24)

@Composable
private fun SubHeader(state: HomeState, title: String, sub: String, onBack: () -> Unit) {
    val pal = state.palette
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(surfaceColor(0.1f)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Glyph("back", pal.onGround, 22.dp, 2.4f)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            BasicText(title, style = dispStyle(pal.onGround, 30))
            Label(sub)
        }
    }
}

@Composable
private fun WorldPage(state: HomeState, onAdd: () -> Unit, onConvert: () -> Unit) {
    val pal = state.palette
    val clock = state.clock
    val is24 = TimeFmt.is24()
    val now by produceState(ZonedDateTime.now()) {
        while (true) {
            value = ZonedDateTime.now()
            awaitVisible()
            delay(1000L)
        }
    }
    val local = ZoneId.systemDefault()
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "hero") {
            val (t, s) = ClockMath.parts(now.hour, now.minute, is24)
            Box(Modifier.fillMaxWidth().height(190.dp).clip(Big).background(cardBg())) {
                BlockText(t, pal.onGround, Modifier.fillMaxSize().padding(start = 26.dp, end = 26.dp, top = 48.dp, bottom = 52.dp))
                Label("LOCAL TIME" + if (s.isNotEmpty()) "  $s" else "", Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 20.dp))
                Label(now.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())).uppercase(), Modifier.align(Alignment.TopEnd).padding(end = 24.dp, top = 20.dp))
                Label(
                    TimeZone.getDefault().getDisplayName(isDst(now), TimeZone.LONG, Locale.getDefault()).uppercase() + "  /  :%02d".format(now.second),
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                )
            }
        }
        item(key = "convert") {
            Row(
                Modifier.fillMaxWidth().clip(Mid).background(deepBg()).clickable(onClick = onConvert).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText("Time converter", style = bodyStyle(pal.onGround, 18, medium = true))
                    Label("PICK A TIME, SEE IT EVERYWHERE")
                }
                Glyph("globe", pal.onGround.copy(alpha = 0.7f), 24.dp, 2.2f)
            }
        }
        val rows = listOf(local.id) + clock.zones.filter { it != local.id }
        items(rows, key = { it }) { id ->
            val z = ZonedDateTime.now(ZoneId.of(id))
            val isLocal = id == local.id
            val diff = z.offset.totalSeconds - now.offset.totalSeconds
            val dh = Math.abs(diff) / 3600
            val dm = (Math.abs(diff) % 3600) / 60
            val rel = when {
                isLocal -> "YOUR TIME ZONE"
                diff == 0 -> "SAME TIME AS YOU"
                else -> buildString {
                    if (dh > 0) append("${dh}H ")
                    if (dm > 0) append("${dm}M ")
                    append(if (diff > 0) "AHEAD" else "BEHIND")
                }
            }
            val dayTag = when {
                isLocal -> ""
                z.toLocalDate().isAfter(now.toLocalDate()) -> "TOMORROW"
                z.toLocalDate().isBefore(now.toLocalDate()) -> "YESTERDAY"
                else -> ""
            }
            val (t, s) = ClockMath.parts(z.hour, z.minute, is24)
            Column(Modifier.fillMaxWidth().clip(Mid).background(cardBg()).padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BasicText(ClockMath.cityName(id), style = bodyStyle(pal.onGround, 19, medium = true), maxLines = 1)
                        Label(listOf(rel, dayTag, if (isDst(z)) "DST" else "").filter { it.isNotEmpty() }.joinToString("  /  "))
                    }
                    BlockText(t, pal.onGround, Modifier.width(blockWidth(t, 5.2f)).height(26.dp))
                    if (s.isNotEmpty()) BasicText(s, style = monoStyle(pal.onGround, 11, bold = true), modifier = Modifier.padding(start = 6.dp))
                    if (!isLocal) {
                        Box(Modifier.padding(start = 4.dp).size(36.dp).clip(CircleShape).clickable { clock.removeZone(id) }, contentAlignment = Alignment.Center) {
                            Glyph("close", pal.onGround.copy(alpha = 0.5f), 16.dp, 2.2f)
                        }
                    }
                }
                Squares(24, Modifier.padding(end = 8.dp)) { it <= z.hour }
            }
        }
        item(key = "add") {
            Row(
                Modifier.fillMaxWidth().clip(Mid).background(deepBg()).clickable(onClick = onAdd).padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph("plus", pal.onGround.copy(alpha = 0.6f), 18.dp, 2.4f)
                Spacer(Modifier.width(8.dp))
                Label("ADD A PLACE")
            }
        }
    }
}

private class ZRow(val key: String, val header: String?, val z: ZInfo?)

/** Every time zone the phone knows, grouped by region and searchable. */
@Composable
private fun ZonePicker(state: HomeState, onPick: (String) -> Unit, onBack: () -> Unit) {
    val pal = state.palette
    val is24 = TimeFmt.is24()
    var q by remember { mutableStateOf("") }
    val zones by produceState<List<ZInfo>>(emptyList()) { value = withContext(Dispatchers.Default) { buildZones() } }
    val now by produceState(ZonedDateTime.now()) {
        while (true) {
            value = ZonedDateTime.now()
            awaitVisible()
            delay(30_000L)
        }
    }
    val rows = remember(q, zones) {
        val s = q.trim().lowercase()
        val out = ArrayList<ZRow>()
        if (s.isEmpty()) {
            val byId = zones.associateBy { it.id }
            out.add(ZRow("h_pop", "POPULAR", null))
            popularZones.mapNotNull { byId[it] }.forEach { out.add(ZRow("p_" + it.id, null, it)) }
            zones.groupBy { it.region }.forEach { (r, list) ->
                out.add(ZRow("h_$r", r.uppercase() + "  /  ${list.size}", null))
                list.forEach { out.add(ZRow(it.id, null, it)) }
            }
        } else {
            val words = s.split(' ').filter { it.isNotEmpty() }
            zones.filter { z -> words.all { it in z.search } }.take(150).forEach { out.add(ZRow(it.id, null, it)) }
        }
        out
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 12.dp)) {
        SubHeader(state, "Add a place", "${zones.size} TIME ZONES") { onBack() }
        Box(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(CircleShape).background(cardBg()).padding(horizontal = 22.dp, vertical = 15.dp)) {
            Field(q, { q = it }, "Search city, country or zone", Modifier.fillMaxWidth())
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (zones.isEmpty()) item { Label("LOADING") }
            else if (rows.isEmpty()) item { BasicText("No match.", style = bodyStyle(pal.onGround.copy(alpha = 0.6f), 15), modifier = Modifier.padding(8.dp)) }
            items(rows, key = { it.key }) { r ->
                if (r.header != null) {
                    Box(Modifier.padding(start = 8.dp, top = 14.dp, bottom = 2.dp)) { Label(r.header, bold = true) }
                } else {
                    val z = r.z!!
                    val at = now.withZoneSameInstant(ZoneId.of(z.id))
                    Row(
                        Modifier.fillMaxWidth().clip(Mid).background(cardBg()).clickable { onPick(z.id) }.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            BasicText(z.city, style = bodyStyle(pal.onGround, 17, medium = true), maxLines = 1)
                            Label((z.region + "  /  " + z.long).uppercase())
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            BasicText(clockText(at, is24), style = bodyStyle(pal.onGround, 17, medium = true), maxLines = 1)
                            Label(gmtText(at) + if (isDst(at)) "  DST" else "")
                        }
                    }
                }
            }
            item(key = "pad") { Spacer(Modifier.height(8.dp)) }
        }
    }
}

/** Pick a time in one place and see what it is everywhere else, with daylight saving applied for that exact date. */
@Composable
private fun ConverterPage(state: HomeState, onBack: () -> Unit, openPicker: (((String) -> Unit)) -> Unit) {
    val pal = state.palette
    val clock = state.clock
    val is24 = TimeFmt.is24()
    val local = ZoneId.systemDefault()
    val start = remember { ZonedDateTime.now() }
    var from by remember { mutableStateOf(local.id) }
    var min by remember { mutableStateOf(start.hour * 60 + start.minute) }
    var date by remember { mutableStateOf(start.toLocalDate()) }
    var ver by remember { mutableStateOf(0) }
    val fromZone = ZoneId.of(from)
    val wanted = LocalDateTime.of(date, LocalTime.of(min / 60, min % 60))
    val src = wanted.atZone(fromZone)
    val skipped = src.toLocalDateTime() != wanted
    val twice = fromZone.rules.getValidOffsets(wanted).size == 2
    val targets = (listOf(local.id) + clock.zones).distinct().filter { it != from }
    val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())

    fun reset() {
        val n = ZonedDateTime.now(fromZone)
        min = n.hour * 60 + n.minute
        date = n.toLocalDate()
        ver++
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp)) {
        SubHeader(state, "Converter", "DAYLIGHT SAVING INCLUDED", onBack)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item(key = "from") {
                Row(
                    Modifier.fillMaxWidth().clip(Mid).background(cardBg()).clickable { openPicker { id -> from = id } }.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Label("FROM")
                        BasicText(ClockMath.cityName(from), style = bodyStyle(pal.onGround, 20, medium = true), maxLines = 1)
                        Label(gmtText(src) + if (isDst(src)) "  /  DST" else "")
                    }
                    Label("CHANGE", bold = true)
                }
            }
            item(key = "when") {
                Column(Modifier.fillMaxWidth().clip(Big).background(cardBg()).padding(horizontal = 18.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(deepBg()).clickable { date = date.minusDays(1) }, contentAlignment = Alignment.Center) {
                            Glyph("back", pal.onGround, 20.dp, 2.4f)
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Label(date.format(dateFmt).uppercase(), bold = true) }
                        Box(Modifier.size(44.dp).clip(CircleShape).background(deepBg()).clickable { date = date.plusDays(1) }, contentAlignment = Alignment.Center) {
                            Box(Modifier.graphicsLayer(rotationZ = 180f)) { Glyph("back", pal.onGround, 20.dp, 2.4f) }
                        }
                    }
                    key(ver) { TimeWheels(min, is24) { min = it } }
                    if (skipped) Label("THIS TIME DOES NOT EXIST HERE (CLOCKS JUMP). SHOWN AS ${clockText(src, is24).uppercase()}")
                    if (twice) Label("THIS TIME HAPPENS TWICE HERE (CLOCKS GO BACK). FIRST ONE SHOWN")
                    TapCard("RESET TO NOW", false, Modifier.fillMaxWidth()) { reset() }
                }
            }
            item(key = "hdr") { Box(Modifier.padding(start = 8.dp, top = 8.dp)) { Label("AT THE SAME MOMENT", bold = true) } }
            items(targets, key = { "t_$it" }) { id ->
                val z = src.withZoneSameInstant(ZoneId.of(id))
                val dd = ChronoUnit.DAYS.between(src.toLocalDate(), z.toLocalDate())
                val dayTag = when {
                    dd > 0L -> "NEXT DAY"
                    dd < 0L -> "PREVIOUS DAY"
                    else -> "SAME DAY"
                }
                val (t, s) = ClockMath.parts(z.hour, z.minute, is24)
                Row(
                    Modifier.fillMaxWidth().clip(Mid).background(cardBg()).clickable {
                        from = id; min = z.hour * 60 + z.minute; date = z.toLocalDate(); ver++
                    }.padding(start = 20.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BasicText(ClockMath.cityName(id), style = bodyStyle(pal.onGround, 19, medium = true), maxLines = 1)
                        Label(listOf(dayTag, z.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())).uppercase(), gmtText(z) + if (isDst(z)) " DST" else "").joinToString("  /  "))
                    }
                    BlockText(t, pal.onGround, Modifier.width(blockWidth(t, 5.2f)).height(26.dp))
                    if (s.isNotEmpty()) BasicText(s, style = monoStyle(pal.onGround, 11, bold = true), modifier = Modifier.padding(start = 6.dp))
                }
            }
            item(key = "add") {
                Row(
                    Modifier.fillMaxWidth().clip(Mid).background(deepBg()).clickable { openPicker { id -> clock.addZone(id) } }.padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph("plus", pal.onGround.copy(alpha = 0.6f), 18.dp, 2.4f)
                    Spacer(Modifier.width(8.dp))
                    Label("ADD A PLACE")
                }
            }
            item(key = "pad") { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ---- stopwatch ---------------------------------------------------------------------------------

private fun swMain(ms: Long): String {
    val s = (ms / 1000) % 60
    val m = (ms / 60000) % 60
    val h = ms / 3_600_000
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun swFull(ms: Long) = swMain(ms) + ".%02d".format((ms / 10) % 100)

@Composable
private fun StopwatchPage(state: HomeState) {
    val pal = state.palette
    val on = Stopwatch.running.value
    val live by produceState(0L, on) {
        while (on) {
            value = Stopwatch.elapsed()
            awaitVisible()
            delay(33L)
        }
    }
    // Stopped: read the saved time directly so Reset shows at once.
    val ms = if (on) live else Stopwatch.elapsed()
    val laps = Stopwatch.laps.toList()
    val swCtx = LocalContext.current
    LaunchedEffect(on) { app.koma.launcher.data.ClockNotif.stopwatch(swCtx, on, Stopwatch.elapsed()) }
    val splits = laps.mapIndexed { i, t -> t - (if (i == 0) 0L else laps[i - 1]) }
    val best = if (splits.size >= 3) splits.min() else -1L
    val worst = if (splits.size >= 3) splits.max() else -1L

    val land = androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }

    @Composable
    fun FaceBlock(m: Modifier) {
        Box(m.clip(Big).background(cardBg())) {
            BlockText(swMain(ms), pal.onGround, Modifier.fillMaxSize().padding(start = 26.dp, end = 26.dp, top = 52.dp, bottom = 56.dp))
            Label("STOPWATCH", Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 20.dp))
            Label(if (laps.isNotEmpty()) "LAP ${laps.size + 1}  ${swFull(ms - laps.last())}" else ".%02d".format((ms / 10) % 100), Modifier.align(Alignment.TopEnd).padding(end = 24.dp, top = 20.dp))
            Squares(30, Modifier.align(Alignment.BottomCenter).padding(start = 26.dp, end = 26.dp, bottom = 22.dp)) { ms > 0L && it <= ((ms / 2000) % 30).toInt() }
        }
    }

    @Composable
    fun LapList(m: Modifier) {
        if (laps.isEmpty()) {
            Spacer(m)
        } else {
            LazyColumn(m.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(laps.indices.reversed().toList(), key = { it }) { i ->
                    val sp = splits[i]
                    val tag = when (sp) { best -> "FASTEST"; worst -> "SLOWEST"; else -> "" }
                    Row(
                        Modifier.fillMaxWidth().clip(Mid).background(cardBg()).padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText("%02d".format(i + 1), style = monoStyle(pal.onGround.copy(alpha = 0.55f), 12, bold = true), modifier = Modifier.width(34.dp))
                        BasicText("+" + swFull(sp), style = bodyStyle(pal.onGround, 18, medium = true), modifier = Modifier.weight(1f))
                        if (tag.isNotEmpty()) BasicText(tag, style = monoStyle(if (tag == "FASTEST") pal.tiles[0] else pal.onGround.copy(alpha = 0.55f), 10, bold = true), modifier = Modifier.padding(end = 12.dp))
                        BasicText(swFull(laps[i]), style = monoStyle(pal.onGround.copy(alpha = 0.65f), 12))
                    }
                }
            }
        }
    }

    @Composable
    fun SwControls(h: Int) {
        Row(Modifier.fillMaxWidth().height(if (h < 80) 76.dp else 92.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (on) SideBtn("flag", "LAP") { Stopwatch.lap() }
            else SideBtn("refresh", "RESET", enabled = ms > 0L) { if (ms > 0L) Stopwatch.reset() }
            MainBtn(if (on) "pause" else "play", if (on) "STOP" else if (ms > 0L) "RESUME" else "START", Modifier.weight(1.25f)) { Stopwatch.toggle() }
        }
    }

    if (land) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FaceBlock(Modifier.weight(1f).fillMaxWidth())
                SwControls(64)
            }
            LapList(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FaceBlock(Modifier.fillMaxWidth().height(210.dp))
            LapList(Modifier.weight(1f))
            SwControls(84)
        }
    }
}

// ---- timer -------------------------------------------------------------------------------------

private fun tText(ms: Long): String {
    val s = ((ms + 999) / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

/** An endless scrolling column of numbers that snaps to one. Scroll it, or tap a number to jump to it. */
@Composable
private fun Wheel(
    value: Int, count: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier,
    fmt: (Int) -> String = { "%02d".format(it) },
    block: Float = 7f,
) {
    val pal = LocalPalette.current
    val itemH = 64.dp
    val total = 100_000
    val start = total / 2 - (total / 2) % count + value
    val ls = androidx.compose.foundation.lazy.rememberLazyListState(start - 1)
    val fling = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(ls)
    val scope = rememberCoroutineScope()
    val cb by rememberUpdatedState(onChange)
    val half = with(androidx.compose.ui.platform.LocalDensity.current) { itemH.toPx() / 2 }
    val center by androidx.compose.runtime.remember {
        androidx.compose.runtime.derivedStateOf { ls.firstVisibleItemIndex + 1 + if (ls.firstVisibleItemScrollOffset > half) 1 else 0 }
    }
    LaunchedEffect(ls) {
        androidx.compose.runtime.snapshotFlow { if (ls.isScrollInProgress) -1 else ls.firstVisibleItemIndex + 1 }
            .collect { if (it >= 0) cb(it % count) }
    }
    LazyColumn(state = ls, flingBehavior = fling, modifier = modifier.height(itemH * 3)) {
        items(total) { i ->
            val d = Math.abs(i - center)
            val s = fmt(i % count)
            Box(
                Modifier.fillMaxWidth().height(itemH).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    scope.launch { ls.animateScrollToItem(i - 1) }
                },
                contentAlignment = Alignment.Center,
            ) {
                val big = d == 0
                BlockText(
                    s, pal.onGround.copy(alpha = if (big) 1f else if (d == 1) 0.3f else 0.1f),
                    Modifier.width(blockWidth(s, if (big) block else block * 0.66f)).height(if (big) (block * 5f).dp else (block * 3.3f).dp),
                )
            }
        }
    }
}

@Composable
private fun TimerPage(state: HomeState) {
    val pal = state.palette
    val p = state.prefs
    val land = androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    val ctx = LocalContext.current
    val is24 = TimeFmt.is24()
    val end = p.timerEnd
    val now by produceState(System.currentTimeMillis(), end) {
        while (end > 0L) {
            value = System.currentTimeMillis()
            awaitVisible()
            delay(100L)
        }
    }
    val running = end > now
    val done = end > 0L && !running
    val paused = p.timerLeft > 0L
    val len = p.timerLen.coerceAtLeast(1L)
    val endFmt = DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", Locale.getDefault())
    // If the timer ended and nothing rang, the open app rings it itself.
    LaunchedEffect(done, end) {
        if (done) {
            delay(4000L)
            if (p.timerEnd == end && RingService.startedAt < end) {
                RingLog.add(ctx, "the timer ended and nothing rang, so the app is ringing it")
                RingService.start(ctx, TimerCtl.ringInfo(len))
            }
        }
    }

    fun begin(ms: Long) {
        val t = System.currentTimeMillis()
        p.timerLen = ms
        p.timerEnd = t + ms
        p.timerLeft = 0L
        TimerCtl.start(ctx, t + ms)
        needNotifications(state, ctx)
    }
    fun stop() {
        p.timerEnd = 0L
        p.timerLeft = 0L
        TimerCtl.cancel(ctx)
    }

    if (running || done || paused) {
        val left = if (running) end - now else if (paused) p.timerLeft else 0L
        val frac = if (done) 0f else (left.toFloat() / len).coerceIn(0f, 1f)
        val endsAt = if (running) Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault()).format(endFmt).uppercase() else ""
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box((if (land) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(210.dp)).clip(Big).background(cardBg())) {
                BlockText(if (done) "00:00" else tText(left), pal.onGround, Modifier.fillMaxSize().padding(start = 26.dp, end = 26.dp, top = 52.dp, bottom = 56.dp))
                Label(if (done) "TIME'S UP" else if (paused) "PAUSED" else "TIMER / ${minText((len / 60000L).toInt().coerceAtLeast(1))}", Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 20.dp))
                if (endsAt.isNotEmpty()) Label("RINGS $endsAt", Modifier.align(Alignment.TopEnd).padding(end = 24.dp, top = 20.dp))
                Squares(48, Modifier.align(Alignment.BottomCenter).padding(start = 26.dp, end = 26.dp, bottom = 22.dp)) { it < (frac * 48).toInt() }
            }
            if (!land) Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().height(if (land) 72.dp else 92.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SideBtn(if (done) "close" else "refresh", if (done) "END" else "RESET") { stop() }
                if (done) {
                    MainBtn("refresh", "RESTART", Modifier.weight(1f)) { begin(len) }
                } else {
                    SideBtn("+1", "MIN") {
                        p.timerLen = len + 60_000L
                        if (running) {
                            p.timerEnd = end + 60_000L
                            TimerCtl.start(ctx, end + 60_000L)
                        } else p.timerLeft = p.timerLeft + 60_000L
                    }
                    MainBtn(if (running) "pause" else "play", if (running) "PAUSE" else "RESUME", Modifier.weight(1f)) {
                        if (running) {
                            p.timerLeft = (end - System.currentTimeMillis()).coerceAtLeast(1000L)
                            p.timerEnd = 0L
                            TimerCtl.cancel(ctx)
                        } else {
                            val t = System.currentTimeMillis()
                            p.timerEnd = t + p.timerLeft
                            TimerCtl.start(ctx, t + p.timerLeft)
                            p.timerLeft = 0L
                        }
                    }
                }
            }
        }
        return
    }

    // The wheels open on what was picked, or else on the length of the last session, seconds included.
    val first = (if (state.timerSec > 0) state.timerSec else if (p.timerLen > 0L) (p.timerLen / 1000L).toInt() else 600).coerceIn(0, 99 * 3600 + 59 * 60 + 59)
    var h by remember { mutableStateOf(first / 3600) }
    var m by remember { mutableStateOf(first / 60 % 60) }
    var s by remember { mutableStateOf(first % 60) }
    var ver by remember { mutableStateOf(0) }
    fun set(nh: Int, nm: Int, ns: Int) {
        h = nh; m = nm; s = ns
        state.timerSec = nh * 3600 + nm * 60 + ns
        ver++
    }
    val totalSec = h * 3600L + m * 60L + s
    val ends = ZonedDateTime.now().plusSeconds(totalSec).format(endFmt).uppercase()

    @Composable
    fun WheelsCard(mod: Modifier) {
        Column(mod.clip(Big).background(cardBg()).padding(horizontal = 18.dp, vertical = if (land) 12.dp else 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Label("TIMER", Modifier.weight(1f))
                Label(if (totalSec > 0) "RINGS $ends" else "SCROLL TO SET")
            }
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.align(Alignment.Center).fillMaxWidth().height(64.dp).clip(Mid).background(deepBg()))
                androidx.compose.runtime.key(ver) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Wheel(h, 100, { if (it != h) { h = it; state.timerSec = h * 3600 + m * 60 + s } }, Modifier.weight(1f))
                        Wheel(m, 60, { if (it != m) { m = it; state.timerSec = h * 3600 + m * 60 + s } }, Modifier.weight(1f))
                        Wheel(s, 60, { if (it != s) { s = it; state.timerSec = h * 3600 + m * 60 + s } }, Modifier.weight(1f))
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("HOURS", "MIN", "SEC").forEach { Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Label(it) } }
            }
        }
    }
    val presets = listOf(Triple(5, "5", "MIN"), Triple(10, "10", "MIN"), Triple(30, "30", "MIN"), Triple(60, "1", "HOUR"))

    if (land) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1.5f).fillMaxHeight().verticalScroll(rememberScrollState())) { WheelsCard(Modifier.fillMaxWidth()) }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                presets.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { (mins, big, unit) ->
                            PresetCard(big, unit, totalSec == mins * 60L, Modifier.weight(1f).fillMaxHeight()) { set(mins / 60, mins % 60, 0) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SideBtn("refresh", "RESET", enabled = totalSec > 0) { if (totalSec > 0) set(0, 0, 0) }
                    MainBtn("play", "START", Modifier.weight(1f), enabled = totalSec > 0) { if (totalSec > 0) begin(totalSec * 1000L) }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.fillMaxWidth().clip(Big).background(cardBg()).padding(horizontal = 18.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Label("TIMER", Modifier.weight(1f))
                    Label(if (totalSec > 0) "RINGS $ends" else "SCROLL TO SET")
                }
                Box(Modifier.fillMaxWidth()) {
                    Box(Modifier.align(Alignment.Center).fillMaxWidth().height(64.dp).clip(Mid).background(deepBg()))
                    androidx.compose.runtime.key(ver) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Wheel(h, 100, { if (it != h) { h = it; state.timerSec = h * 3600 + m * 60 + s } }, Modifier.weight(1f))
                            Wheel(m, 60, { if (it != m) { m = it; state.timerSec = h * 3600 + m * 60 + s } }, Modifier.weight(1f))
                            Wheel(s, 60, { if (it != s) { s = it; state.timerSec = h * 3600 + m * 60 + s } }, Modifier.weight(1f))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf("HOURS", "MIN", "SEC").forEach { Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Label(it) } }
                }
            }
            Row(Modifier.fillMaxWidth().height(76.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Triple(5, "5", "MIN"), Triple(10, "10", "MIN"), Triple(30, "30", "MIN"), Triple(60, "1", "HOUR")).forEach { (mins, big, unit) ->
                    PresetCard(big, unit, totalSec == mins * 60L, Modifier.weight(1f).fillMaxHeight()) { set(mins / 60, mins % 60, 0) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(92.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SideBtn("refresh", "RESET", enabled = totalSec > 0) { if (totalSec > 0) set(0, 0, 0) }
            MainBtn("play", "START", Modifier.weight(1f), enabled = totalSec > 0) { if (totalSec > 0) begin(totalSec * 1000L) }
        }
    }
}

// ---- settings ----------------------------------------------------------------------------------

@Composable
internal fun ClockSettingsPage(state: HomeState, onBack: () -> Unit) {
    val pal = state.palette
    val prefs = state.prefs
    val ctx = LocalContext.current
    var pickTimer by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val uri = r.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            val v = uri?.toString() ?: "none"
            val value = if (v == RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString()) "" else v
            if (pickTimer) prefs.timerTone = value else prefs.alarmTone = value
        }
    }
    fun pick(timer: Boolean) {
        pickTimer = timer
        val cur = RingPrefs.uri(if (timer) prefs.timerTone else prefs.alarmTone)
        launcher.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, cur)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, if (timer) "Timer ringtone" else "Alarm ringtone"),
        )
    }
    Page("Clock", "CLOCK / SETTINGS", onBack = onBack) {
        Group("TIME", "Used by the clock, alarms, timer and everything else that shows a time.") {
            Dropdown(listOf("system" to "Follow the phone", "12" to "12 hour, AM and PM", "24" to "24 hour"), prefs.clockHour, "Time format") { state.setClockHour(it) }
        }
        Group("SOUND") {
            VolumeRow(state)
            ListRow("Alarm ringtone", RingPrefs.name(ctx, prefs.alarmTone), "alarm", pal.tiles[0], onClick = { pick(false) })
            ListRow("Timer ringtone", RingPrefs.name(ctx, prefs.timerTone), "hourglass", pal.tiles[1 % pal.tiles.size], onClick = { pick(true) })
            SwitchRow("Vibrate", "", prefs.ringVibrate) { prefs.ringVibrate = it }
        }
        Group("RING SCREEN", "How an alarm or timer looks when it goes off.") {
            Dropdown(
                listOf("classic" to "CLASSIC", "quiet" to "QUIET", "tiles" to "TILES", "split" to "SPLIT", "orbit" to "ORBIT"),
                prefs.ringStyle, "Style",
            ) { prefs.ringStyle = it }
        }
        Group("SNOOZE", "Snoozing is set here, for every alarm.") {
            SwitchRow("Auto snooze", "Snooze by itself after a minute, then stop when none are left.", prefs.autoSnooze) { prefs.autoSnooze = it }
            Dropdown(
                listOf("5" to "5 minutes", "10" to "10 minutes", "15" to "15 minutes", "20" to "20 minutes", "30" to "30 minutes"),
                prefs.defaultSnooze.toString(), "Snooze length",
            ) { prefs.defaultSnooze = it.toInt() }
            Dropdown(
                listOf("0" to "No snooze", "1" to "1 time", "2" to "2 times", "3" to "3 times", "5" to "5 times", "10" to "10 times"),
                prefs.snoozeMax.toString(), "Most snoozes per alarm",
            ) { prefs.snoozeMax = it.toInt() }
        }
        Group("TIMER") {
            SwitchRow("Auto turn off", "Close the timer by itself after a minute.", prefs.autoStopTimer) { prefs.autoStopTimer = it }
        }
        if (android.os.Build.VERSION.SDK_INT >= 34 && !ctx.getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()) {
            Group("LOCK SCREEN", "Lets an alarm light up the screen and open over the lock screen.") {
                ListRow("Show alarms over the lock screen", "Off. Tap to allow.", "lock", pal.tiles[3 % pal.tiles.size], onClick = {
                    try {
                        ctx.startActivity(
                            Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + ctx.packageName))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (e: Exception) {
                    }
                })
            }
        }
        val allowed = androidx.core.app.NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        val am2 = ctx.getSystemService(android.app.AlarmManager::class.java)
        val unrestricted = ctx.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
        Group("TEST", "Try these first. If one stays silent, the events list at the bottom says why.") {
            ListRow("Ring now", "Plays the alarm sound and opens the alarm screen.", "play", pal.tiles[0], onClick = {
                RingLog.add(ctx, "test: ring now")
                RingService.start(ctx, RingInfo("alarm", "__test", "Test alarm", "7:00", "AM", 1))
            })
            ListRow("Ring in 10 seconds", "Lock the phone and wait. This uses the real alarm clock.", "alarm", pal.tiles[1 % pal.tiles.size], onClick = {
                AlarmCtl.at(ctx, "__test", System.currentTimeMillis() + 10_000L)
            })
        }
        Group("HEALTH") {
            ListRow(
                "Notifications", if (allowed) "Allowed. Tap to open." else "Blocked. Tap to allow.", "info", pal.tiles[2 % pal.tiles.size],
                onClick = {
                    try {
                        ctx.startActivity(
                            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (e: Exception) {
                        try {
                            ctx.startActivity(
                                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } catch (e2: Exception) {
                        }
                    }
                },
            )
            val sysNext = am2.nextAlarmClock
            ListRow(
                "Next alarm the phone has", if (sysNext == null) "None scheduled"
                else java.time.Instant.ofEpochMilli(sysNext.triggerTime).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE d MMM  HH:mm:ss", Locale.getDefault())),
                "alarm", pal.tiles[0],
            )
            ListRow(
                "Battery use", if (unrestricted) "Unrestricted. Tap to open the setting." else "Not unrestricted. Tap, then Battery, then Unrestricted.", "settings", pal.tiles[4 % pal.tiles.size],
                onClick = { openBatterySettings(ctx, 1) },
            )
        }
        Group("WIDGET") {
            ListRow("Clock widget", "Style, font, digit tone, seconds", "clock", pal.tiles[2 % pal.tiles.size], onClick = { state.clockPage = "widget" })
        }
    }
}

/** The alarm volume, shared with the phone's own alarm volume. Tap or drag the bars. */
@Composable
private fun VolumeRow(state: HomeState) {
    val pal = state.palette
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(android.media.AudioManager::class.java) }
    val max = remember { am.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM).coerceAtLeast(1) }
    val vol by produceState(am.getStreamVolume(android.media.AudioManager.STREAM_ALARM)) {
        while (true) {
            awaitVisible()
            value = am.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
            delay(300L)
        }
    }
    val cur by rememberUpdatedState(vol)
    fun set(v: Int) {
        try {
            am.setStreamVolume(android.media.AudioManager.STREAM_ALARM, v.coerceIn(0, max), 0)
        } catch (e: Exception) {
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                BasicText("Alarm volume", style = bodyStyle(pal.onGround, 17, medium = true))
                Label("SAME AS THE PHONE'S ALARM VOLUME")
            }
            BasicText(if (vol == 0) "OFF" else "$vol / $max", style = monoStyle(pal.onGround, 12, bold = true))
        }
        Row(
            Modifier.fillMaxWidth().height(46.dp)
                .pointerInput(max) {
                    detectTapGestures { o ->
                        val v = ((o.x / size.width) * max).toInt() + 1
                        set(if (v == cur && v == 1) 0 else v)
                    }
                }
                .pointerInput(max) {
                    detectHorizontalDragGestures { change, _ -> set(((change.position.x / size.width) * max).toInt() + 1) }
                },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (i in 1..max) {
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(if (i <= vol) pal.tiles[0] else deepBg()))
            }
        }
    }
}
