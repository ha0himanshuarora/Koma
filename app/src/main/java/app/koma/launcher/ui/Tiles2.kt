package app.koma.launcher.ui

import app.koma.launcher.data.TimeFmt
import android.content.Context
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.koma.launcher.data.AppInfo
import app.koma.launcher.data.AppLimits
import app.koma.launcher.data.FocusEngine
import app.koma.launcher.data.Bags
import app.koma.launcher.data.CallRow
import app.koma.launcher.data.Device
import app.koma.launcher.data.IconCache
import app.koma.launcher.data.Person
import app.koma.launcher.data.Note
import app.koma.launcher.data.Notes
import app.koma.launcher.data.Task
import app.koma.launcher.data.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---- search ------------------------------------------------------------------------------------

@Composable
private fun SearchPill(ink: Color, bg: Color, label: String, withButtons: Boolean, onVoice: () -> Unit, onLens: () -> Unit, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Magnifier(ink, 24.dp)
        BasicText(label, style = bodyStyle(ink, 17), modifier = Modifier.weight(1f))
        if (withButtons) {
            Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onVoice), contentAlignment = Alignment.Center) { Glyph("mic", ink, 22.dp, 2f) }
            Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onLens), contentAlignment = Alignment.Center) { Glyph("lens", ink, 22.dp, 2f) }
        }
    }
}

@Composable
fun SearchTile(
    w: Int,
    h: Int,
    bg: Color,
    ink: Color,
    icons: IconCache,
    recent: List<AppInfo>,
    onChip: (String) -> Unit,
    onApp: (AppInfo) -> Unit,
    onVoice: () -> Unit,
    onLens: () -> Unit,
) {
    when {
        w == 1 -> Box(Modifier.fillMaxSize().clip(TileShape).background(bg), contentAlignment = Alignment.Center) { Magnifier(ink, 26.dp) }
        h >= 2 -> Column(Modifier.fillMaxSize().clip(TileShape).background(bg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchPill(bg.opaque(), ink, "Search", true, onVoice, onLens, Modifier.fillMaxWidth().height(50.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Weather", "Maps", "News", "Translate").forEach { chip ->
                    Box(
                        Modifier.height(30.dp).clip(RoundedCornerShape(50)).border(1.5.dp, ink, RoundedCornerShape(50))
                            .clickable { onChip(chip) }.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { BasicText(chip, style = label(ink, 10.sp, bold = true)) }
                }
            }
            BasicText("RECENT", style = label(ink, 9.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                recent.take(6).forEach { a ->
                    Box(Modifier.clickable { onApp(a) }) { AppIcon(icons, a, 38.dp, bg = ink, ink = bg.opaque(), line = ink) }
                }
            }
        }
        else -> SearchPill(ink, bg, if (w >= 2) "Search" else "", w >= 4, onVoice, onLens, Modifier.fillMaxSize())
    }
}

// ---- people ------------------------------------------------------------------------------------

@Composable
private fun Avatar(name: String, size: Dp, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(fg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { BasicText(name.firstOrNull()?.uppercase() ?: "?", style = display(( size.value * 0.42f).sp, bg.opaque(), 0.em)) }
}

@Composable
fun FavsTile(w: Int, h: Int, bg: Color, ink: Color, granted: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val people by produceState(emptyList<Person>(), granted) {
        value = if (granted) withContext(Dispatchers.IO) { Device.starred(ctx, 8) } else emptyList()
    }
    fun call(p: Person) {
        scope.launch {
            val n = withContext(Dispatchers.IO) { Device.phoneOf(ctx, p.id) }
            if (n != null) Device.dial(ctx, n)
        }
    }
    Box(Modifier.fillMaxSize().clip(TileShape).background(bg).padding(12.dp)) {
        when {
            !granted -> Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                BasicText("FAV", style = label(ink, 10.sp))
                BasicText("Allow contacts", style = display(15.sp, ink, 0.em, 1.05f))
            }
            people.isEmpty() -> Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                BasicText("FAV", style = label(ink, 10.sp))
                BasicText("Star contacts to see them here", style = display(13.sp, ink, 0.em, 1.05f))
            }
            w == 1 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
                people.take(2).forEach { Avatar(it.name, 44.dp, bg, ink) { call(it) } }
            }
            h == 1 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val n = if (w >= 4) 6 else 3
                people.take(n).forEach { Avatar(it.name, 44.dp, bg, ink) { call(it) } }
                if (people.size > n) BasicText("+${people.size - n}", style = label(ink, 11.sp, bold = true))
            }
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText("FAVOURITES", style = label(ink, 10.sp))
                people.take(4).chunked(2).forEach { rowPeople ->
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowPeople.forEach { p ->
                            Column(Modifier.weight(1f).clickable { call(p) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Avatar(p.name, 40.dp, bg, ink) { call(p) }
                                BasicText(p.name.substringBefore(' '), style = label(ink, 9.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CallsTile(w: Int, h: Int, bg: Color, ink: Color, granted: Boolean) {
    val ctx = LocalContext.current
    val rows by produceState(emptyList<CallRow>(), granted) {
        while (true) {
            awaitVisible()
            value = if (granted) withContext(Dispatchers.IO) { Device.recentCalls(ctx, if (h >= 2 && w >= 4) 5 else 3) } else emptyList()
            delay(5 * 60_000L)
        }
    }
    Column(Modifier.fillMaxSize().clip(TileShape).background(bg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText("RECENT CALLS", style = label(ink, 10.sp))
        if (!granted) BasicText("Allow call log", style = display(15.sp, ink, 0.em, 1.05f))
        else if (rows.isEmpty()) BasicText("No calls yet", style = display(15.sp, ink, 0.em, 1.05f))
        rows.forEach { r ->
            val zone = ZoneId.systemDefault()
            val d = Instant.ofEpochMilli(r.date).atZone(zone)
            val whenText = if (d.toLocalDate() == LocalDate.now()) d.format(DateTimeFormatter.ofPattern(TimeFmt.pattern())) else d.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).uppercase()
            val kind = when (r.type) { 3 -> "MISSED"; 2 -> "OUT"; 5 -> "DECLINED"; else -> "IN" }
            Row(
                Modifier.fillMaxWidth().clickable { if (r.number.isNotEmpty()) Device.dial(ctx, r.number) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(ink), contentAlignment = Alignment.Center) {
                    BasicText(r.name.firstOrNull()?.uppercase() ?: "?", style = display(13.sp, bg.opaque(), 0.em))
                }
                Column(Modifier.weight(1f)) {
                    BasicText(r.name, style = display(13.sp, ink, 0.em, 1.05f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText("$kind $whenText", style = label(ink, 9.sp), maxLines = 1)
                }
                if (r.type == 3) BasicText("!", style = display(16.sp, ink, 0.em))
            }
        }
    }
}

// ---- app group ---------------------------------------------------------------------------------

@Composable
fun GroupTile(w: Int, h: Int, bg: Color, ink: Color, apps: List<AppInfo>, icons: IconCache, onLaunch: (AppInfo) -> Unit) {
    Box(Modifier.fillMaxSize().clip(TileShape).background(bg).padding(12.dp)) {
        if (apps.isEmpty()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                BasicText("APP GROUP", style = label(ink, 10.sp))
                BasicText("Tap to add apps", style = display(15.sp, ink, 0.em, 1.05f))
            }
        } else if (h == 1) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                apps.take(if (w >= 4) 5 else 2).forEach { a ->
                    Box(Modifier.clickable { onLaunch(a) }) { AppIcon(icons, a, 44.dp, bg = ink, ink = bg.opaque(), line = ink) }
                }
            }
        } else {
            val cols = if (w >= 4) 4 else 2
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText("APP GROUP", style = label(ink, 10.sp))
                apps.take(cols * 2).chunked(cols).forEach { rowApps ->
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rowApps.forEach { a ->
                            Column(Modifier.weight(1f).clickable { onLaunch(a) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                AppIcon(icons, a, 38.dp, bg = ink, ink = bg.opaque(), line = ink)
                                BasicText(a.label, style = label(ink, 9.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- device and toggles ------------------------------------------------------------------------

@Composable
private fun Meter(label: String, pct: Int, ink: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        BasicText("$label $pct%", style = label(ink, 9.sp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(ink.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxWidth(pct.coerceIn(0, 100) / 100f).fillMaxHeight().clip(CircleShape).background(ink))
        }
    }
}

@Composable
fun DeviceTile(w: Int, h: Int, bg: Color, ink: Color) {
    val ctx = LocalContext.current
    val info by produceState(Device.status(ctx)) {
        while (true) {
            awaitVisible()
            value = withContext(Dispatchers.IO) { Device.status(ctx) }
            delay(30_000L)
        }
    }
    Box(Modifier.fillMaxSize().clip(TileShape).background(bg).padding(14.dp)) {
        when {
            h == 1 && w >= 4 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("${info.battery}%", style = display(38.sp, ink, (-0.04).em))
                Column(Modifier.weight(1f)) { Meter("STORAGE", info.storagePct, ink) }
                Column(Modifier.weight(1f)) { Meter("MEMORY", info.memPct, ink) }
                BasicText(info.network, style = label(ink, 10.sp, bold = true))
            }
            h == 1 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    BasicText(if (info.charging) "CHARGING" else "BATTERY", style = label(ink, 9.sp))
                    BasicText(info.network, style = label(ink, 10.sp, bold = true))
                }
                BasicText("${info.battery}%", style = display(34.sp, ink, (-0.04).em))
            }
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    BasicText("DEVICE", style = label(ink, 10.sp))
                    BasicText(info.network, style = label(ink, 10.sp, bold = true))
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    BasicText("${info.battery}", style = display(54.sp, ink, (-0.05).em, 1f))
                    BasicText("%" + if (info.charging) " CHARGING" else "", style = label(ink, 10.sp), modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                }
                Meter("STORAGE", info.storagePct, ink)
                Meter("MEMORY", info.memPct, ink)
            }
        }
    }
}

@Composable
private fun rememberTorch(ctx: Context): State<Boolean> {
    val state = remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val cb = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                state.value = enabled
            }
        }
        try {
            cm.registerTorchCallback(cb, null)
        } catch (e: Exception) {
        }
        onDispose {
            try {
                cm.unregisterTorchCallback(cb)
            } catch (e: Exception) {
            }
        }
    }
    return state
}

private fun setTorch(ctx: Context, on: Boolean) {
    try {
        val cm = ctx.getSystemService(CameraManager::class.java)
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } ?: return
        cm.setTorchMode(id, on)
    } catch (e: Exception) {
    }
}

@Composable
fun ToggleTile(kind: String, bg: Color, ink: Color) {
    val ctx = LocalContext.current
    val torch by rememberTorch(ctx)
    val wifi by produceState(false, kind) { if (kind != "wifi") return@produceState; while (true) { awaitVisible(); value = Device.wifiOn(ctx); delay(4000L) } }
    val dnd by produceState(false, kind) { if (kind != "dnd") return@produceState; while (true) { awaitVisible(); value = try { Device.dndOn(ctx) } catch (e: Exception) { false }; delay(4000L) } }
    val (glyph, name, on) = when (kind) {
        "wifi" -> Triple("wifi", "WI-FI", wifi)
        "bt" -> Triple("bt", "BLUETOOTH", false)
        "dnd" -> Triple("moon", "DND", dnd)
        else -> Triple("torch", "TORCH", torch)
    }
    val fill = if (on) ink else bg
    val fg = if (on) bg.opaque() else ink
    Box(
        Modifier.fillMaxSize().clip(TileShape).background(fill).then(if (on) Modifier else Modifier.border(2.dp, ink, TileShape))
            .clickable {
                when (kind) {
                    "wifi" -> Device.openSettings(ctx, if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIFI_SETTINGS)
                    "bt" -> Device.openSettings(ctx, Settings.ACTION_BLUETOOTH_SETTINGS)
                    "dnd" -> Device.setDnd(ctx, !dnd)
                    else -> setTorch(ctx, !torch)
                }
            },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Glyph(glyph, fg, 30.dp, 2f) }
        BasicText(
            name + if (kind == "bt") "" else if (on) " ON" else " OFF",
            style = label(fg, 8.sp, bold = true),
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 8.dp),
        )
    }
}

// ---- tasks and notes ---------------------------------------------------------------------------

@Composable
fun CheckBox(done: Boolean, ink: Color, bg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(22.dp).softPress(ink, RoundedCornerShape(7.dp), onClick = onClick).background(if (done) ink else Color.Transparent)
            .border(2.dp, ink, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) { if (done) Glyph("check", bg.opaque(), 14.dp, 3f) }
}

/** A small rounded tag, used for counts in tile headers. */
@Composable
private fun TileChip(text: String, ink: Color) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(ink.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        BasicText(text, style = label(ink, 9.sp, bold = true), maxLines = 1)
    }
}

/** Tasks and reminders share this tile: a header with a count, the next item highlighted, then the rest. */
@Composable
private fun TaskListTile(w: Int, h: Int, bg: Color, ink: Color, title: String, empty: String, list: List<Task>, onToggle: (String) -> Unit) {
    val today = Tasks.today()
    val late = list.count { !it.done && it.due in 0L until today }
    val pattern = TimeFmt.pattern()
    val ordered = Tasks.sortOpen(list) + list.filter { it.done }
    // A one-row tile shows the next item; bigger ones list everything and scroll.
    val shown = if (h >= 2) ordered.take(100) else ordered.take(1)
    Column(Modifier.fillMaxSize().clip(TileShape).background(bg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(title, style = label(ink, 10.sp, bold = true))
            TileChip(if (late > 0) "$late LATE" else "${list.count { !it.done }} OPEN", ink)
        }
        if (ordered.isEmpty()) BasicText(empty, style = display(14.sp, ink, 0.em, 1.05f))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEachIndexed { i, t ->
            val next = i == 0 && !t.done
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (next) ink.copy(alpha = 0.09f) else Color.Transparent).padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CheckBox(t.done, ink, bg) { onToggle(t.id) }
                BasicText(
                    t.text.ifBlank { "Untitled" },
                    style = display(13.sp, ink.copy(alpha = if (t.done) 0.55f else 1f), 0.em).copy(textDecoration = if (t.done) TextDecoration.LineThrough else TextDecoration.None),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                val whenText = if (t.done) "" else Tasks.whenText(t, pattern, w >= 4)
                if (whenText.isNotEmpty()) BasicText(whenText.uppercase(), style = label(ink.copy(alpha = 0.75f), 9.sp, bold = true), maxLines = 1)
            }
        }
        }
    }
}

/** Everything still to do, soonest first. Timed ones show their time. */
@Composable
fun RemindersTile(w: Int, h: Int, bg: Color, ink: Color, tasks: List<Task>, onToggle: (String) -> Unit) =
    TaskListTile(w, h, bg, ink, "REMINDERS", "Nothing to do. Tap to add.", tasks, onToggle)

/** One note's heading. Its coloured line grows and the title leans in while pressed: no wash, no ripple. */
@Composable
private fun NoteLine(title: String, tone: Color, ink: Color, size: Int, time: String?, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val bar by animateDpAsState(if (down) 10.dp else 4.dp, label = "bar")
    val lean by animateDpAsState(if (down) 3.dp else 0.dp, label = "lean")
    Row(
        Modifier.fillMaxWidth().clickable(interactionSource = src, indication = null, onClick = onClick).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = bar, height = 18.dp).clip(CircleShape).background(tone))
        Spacer(Modifier.width(14.dp - bar + lean))
        BasicText(title, style = display(size.sp, ink, 0.em, 1.05f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (time != null) BasicText(time, style = label(ink.copy(alpha = 0.6f), 9.sp, bold = true), maxLines = 1)
    }
}

/** The last few notes by heading, with a + to start a new one. [onOpen] gets a note's id, or "new". */
@Composable
fun NoteTile(w: Int, h: Int, bg: Color, ink: Color, notes: List<Note>, onOpen: (String?) -> Unit) {
    val pattern = TimeFmt.pattern()
    val tiles = LocalPalette.current.tiles
    @Composable
    fun Add(size: Int) {
        Box(
            Modifier.size(size.dp).softPress(ink, RoundedCornerShape(size.dp * 0.32f)) { onOpen("new") }.background(ink),
            contentAlignment = Alignment.Center,
        ) { Glyph("plus", bg.opaque(), (size * 0.5f).dp, 2.6f) }
    }
    if (h == 1) {
        Row(Modifier.fillMaxSize().clip(TileShape).background(bg).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                BasicText("NOTES", style = label(ink.copy(alpha = 0.7f), 10.sp, bold = true))
                val first = notes.firstOrNull()
                NoteLine(first?.let { Notes.heading(it) } ?: "No notes yet", first?.let { noteTone(tiles, it) } ?: ink, ink, 16, null) { onOpen(first?.id) }
            }
            Add(36)
        }
        return
    }
    Column(Modifier.fillMaxSize().clip(TileShape).background(bg).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText("NOTES", style = label(ink, 10.sp, bold = true))
            Add(30)
        }
        if (notes.isEmpty()) BasicText("No notes yet. Tap + to write one.", style = display(14.sp, ink, 0.em, 1.05f))
        notes.take(if (h >= 3) 6 else 3).forEach { n ->
            val time = if (w >= 4) Instant.ofEpochMilli(n.updated).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern)).uppercase() else null
            NoteLine(Notes.heading(n), noteTone(tiles, n), ink, 15, time) { onOpen(n.id) }
        }
    }
}

// ---- photo and screen time ---------------------------------------------------------------------

private fun decodePhoto(ctx: Context, uri: String): ImageBitmap? = try {
    val u = Uri.parse(uri)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, opts)?.asImageBitmap() }
} catch (e: Exception) {
    null
}

@Composable
fun PhotoTile(bg: Color, ink: Color, uri: String) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, uri) {
        value = if (uri.isBlank()) null else withContext(Dispatchers.IO) { decodePhoto(ctx, uri) }
    }
    Box(Modifier.fillMaxSize().clip(TileShape).background(bg)) {
        val b = bmp
        if (b != null) Image(bitmap = b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Glyph("pics", ink, 26.dp, 2f)
            BasicText(if (uri.isBlank()) "Tap to choose a photo" else "Photo unavailable", style = display(14.sp, ink, 0.em, 1.05f))
        }
    }
}

private fun fmtMin(m: Int) = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"

private fun fmtMs(ms: Long) = fmtMin((ms / 60_000L).toInt())

/** One app's time today: its icon, name, a bar and the time. A limited app's bar is how much of its limit is used. */
@Composable
private fun UseRow(state: HomeState, pkg: String, ms: Long, top: Long, ink: Color, bg: Color, limit: app.koma.launcher.data.AppLimit?, limitUsed: Long, locked: Boolean) {
    val info = state.apps.firstOrNull { it.pkg == pkg }
    val frac = if (limit != null) (limitUsed.toFloat() / (limit.min * 60_000L)).coerceIn(0f, 1f) else (ms.toFloat() / top.coerceAtLeast(1L)).coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppIcon(state.icons, info, 24.dp, bg = ink, ink = bg, line = ink)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText((info?.label ?: pkg).uppercase(), style = label(ink, 9.sp, bold = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(ink.copy(alpha = 0.22f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceAtLeast(0.03f)).clip(CircleShape).background(ink))
            }
        }
        BasicText(
            if (locked) "LOCKED" else if (limit != null) "${(limitUsed / 60_000L).toInt()}/${limit.min}m" else fmtMs(ms),
            style = label(ink, 10.sp, bold = true), maxLines = 1,
        )
    }
}

@Composable
fun ScreenTimeTile(state: HomeState, w: Int, h: Int, bg: Color, ink: Color, granted: Boolean) {
    val ctx = LocalContext.current
    val limitsVersion = AppLimits.version
    val data by produceState(Triple(emptyList<Int>(), emptyList<Pair<String, Long>>(), emptyMap<String, Long>()), granted, limitsVersion) {
        while (true) {
            awaitVisible()
            value = if (granted) withContext(Dispatchers.IO) {
                val lim = AppLimits.all(ctx)
                Triple(
                    Device.screenMinutes(ctx), Device.todayApps(ctx),
                    lim.mapValues { (p, l) -> try { AppLimits.usedMs(ctx, p, l) } catch (e: Exception) { 0L } },
                )
            } else Triple(emptyList(), emptyList(), emptyMap())
            delay(5 * 60_000L)
        }
    }
    val (mins, apps, limitUsed) = data
    val topMs = apps.firstOrNull()?.second ?: 1L
    val empty = !granted || mins.isEmpty()
    // Apps with a limit come first, so what is being limited is always in view, then the rest by time.
    val ordered = remember(apps, limitsVersion) {
        val lim = AppLimits.all(ctx)
        apps.sortedByDescending { if (it.first in lim) 1 else 0 }
    }
    @Composable
    fun row(p: Pair<String, Long>) {
        val l = AppLimits.all(ctx)[p.first]
        UseRow(state, p.first, p.second, topMs, ink, bg, l, limitUsed[p.first] ?: p.second, l != null && AppLimits.isLocked(ctx, p.first))
    }
    if (w >= 4 && h == 1) {
        Row(
            Modifier.fillMaxSize().clip(TileShape).background(bg).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText("SCREEN TIME", style = label(ink, 10.sp, bold = true))
                if (empty) BasicText(if (granted) "No data yet" else "Allow usage access", style = display(15.sp, ink, 0.em, 1.05f), maxLines = 2)
                else {
                    BasicText(fmtMin(mins.last()), style = display(34.sp, ink, (-0.03).em, 1f), maxLines = 1)
                    BasicText("TODAY", style = label(ink.copy(alpha = 0.7f), 9.sp))
                }
            }
            if (!empty && ordered.isNotEmpty()) {
                Row(Modifier.weight(1.3f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ordered.take(3).forEach { p ->
                        val info = state.apps.firstOrNull { it.pkg == p.first }
                        val l = AppLimits.all(ctx)[p.first]
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            AppIcon(state.icons, info, 28.dp, bg = ink, ink = bg, line = ink)
                            BasicText(
                                if (l != null && AppLimits.isLocked(ctx, p.first)) "LOCKED" else fmtMs(p.second),
                                style = label(ink, 9.sp, bold = true), maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().clip(TileShape).background(bg).padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
        BasicText("SCREEN TIME", style = label(ink, 10.sp, bold = true))
        if (empty) {
            BasicText(if (granted) "No data yet" else "Allow usage access", style = display(15.sp, ink, 0.em, 1.05f))
        } else {
            BasicText(fmtMin(mins.last()), style = display(if (h >= 2) 34.sp else 26.sp, ink, (-0.03).em, 1f))
            if (h >= 2) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ordered.take(if (h >= 3) 5 else 3).forEach { row(it) }
                }
                if (h >= 3) {
                    val top = (mins.maxOrNull() ?: 1).coerceAtLeast(1)
                    Row(Modifier.fillMaxWidth().height(36.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                        mins.forEachIndexed { i, m ->
                            Box(
                                Modifier.weight(1f).fillMaxHeight((m.toFloat() / top).coerceIn(0.06f, 1f)).clip(RoundedCornerShape(3.dp))
                                    .background(ink.copy(alpha = if (i == mins.lastIndex) 1f else 0.35f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- focus -------------------------------------------------------------------------------------

@Composable
fun FocusTile(state: HomeState, w: Int, h: Int, bg: Color, ink: Color) {
    val act = state.focusActive
    val now by produceState(System.currentTimeMillis(), act?.until) {
        while (true) {
            awaitVisible()
            value = System.currentTimeMillis()
            delay(15_000L)
        }
    }
    val on = act != null
    val fill = if (on) ink else bg
    val fg = if (on) bg else ink
    val leftMs = if (act != null) (act.until - now).coerceAtLeast(0L) else 0L
    val left = (leftMs + 59_999L) / 60_000L
    val span = if (act != null) act.until - act.start else 0L
    val frac = if (act != null && act.start > 0L && span > 0L) (1f - leftMs.toFloat() / span).coerceIn(0f, 1f) else 0f
    val bags = remember(state.prefs.bags) { Bags.parse(state.prefs.bags) }
    val bag = bags.firstOrNull { it.id == state.prefs.focusBag } ?: bags.firstOrNull()
    val today = remember(act, now / 60_000L) {
        val zone = java.time.ZoneId.systemDefault()
        val d = java.time.LocalDate.now()
        FocusEngine.runs.filter { java.time.Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() == d }.sumOf { it.minutes }
    }
    val week = remember(act, now / 60_000L) {
        val zone = java.time.ZoneId.systemDefault()
        val d0 = java.time.LocalDate.now()
        (6 downTo 0).map { back ->
            val d = d0.minusDays(back.toLong())
            FocusEngine.runs.any { java.time.Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() == d }
        }
    }
    val strictOn = state.prefs.quickStrict
    fun start(m: Int) {
        state.prefs.focusMinutes = m
        state.startFocusAsk(m, bag?.apps ?: emptyList(), state.prefs.quickStrict)
    }
    fun mins(m: Int) = if (m < 60) "${m}m" else if (m % 60 == 0) "${m / 60}h" else "${m / 60}h ${m % 60}m"

    @Composable
    fun Btn(text: String, mod: Modifier = Modifier, solid: Boolean = true, onClick: () -> Unit) {
        Box(
            mod.clip(RoundedCornerShape(12.dp)).background(if (solid) fg else fg.copy(alpha = 0.16f)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { BasicText(text, style = label(if (solid) fill else fg, 11.sp, bold = true), maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp)) }
    }

    @Composable
    fun Progress(mod: Modifier) {
        Row(mod.height(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 0 until 24) Box(Modifier.weight(1f).fillMaxHeight().background(if (i < (frac * 24).toInt()) fg else fg.copy(alpha = 0.25f)))
        }
    }

    Box(Modifier.fillMaxSize().clip(TileShape).background(fill).padding(14.dp)) {
        if (w == 1) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Glyph("lock", fg, 24.dp, 2f)
                BasicText(if (on) "${left}m" else "FOCUS", style = label(fg, 10.sp, bold = true))
            }
        } else if (h == 1) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText(if (on) act.name.uppercase() else "FOCUS", style = label(fg, 10.sp, bold = true), maxLines = 1)
                    if (on) {
                        BasicText("$left MIN LEFT", style = display(20.sp, fg, (-0.03).em, 1f), maxLines = 1)
                        Progress(Modifier.fillMaxWidth().padding(top = 2.dp))
                    } else {
                        BasicText(if (today > 0) "${mins(today)} today" else "Not started", style = display(18.sp, fg, 0.em, 1f), maxLines = 1)
                    }
                }
                if (!on) {
                    Spacer(Modifier.width(10.dp))
                    if (w >= 4) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Btn("STRICT", Modifier.height(38.dp), solid = strictOn) { state.prefs.quickStrict = !strictOn }
                            listOf(15, 25, 45).forEach { m -> Btn("$m MIN", Modifier.height(38.dp), solid = false) { start(m) } }
                        }
                    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Btn("STRICT", Modifier.height(32.dp), solid = strictOn) { state.prefs.quickStrict = !strictOn }
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(fg).clickable { start(state.prefs.focusMinutes) }, contentAlignment = Alignment.Center) {
                            Glyph("play", fill, 20.dp, 2f, filled = true)
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText(if (on) act.name.uppercase() else "FOCUS", style = label(fg, 10.sp, bold = true), maxLines = 1)
                    if (on) Glyph("lock", fg, 20.dp, 2f)
                    else {
                        // Strict switch: lit means strict, dark means normal.
                        Row(
                            Modifier.clip(RoundedCornerShape(11.dp)).background(if (strictOn) fg else fg.copy(alpha = 0.16f))
                                .clickable { state.prefs.quickStrict = !strictOn }.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Glyph("lock", if (strictOn) fill else fg, 16.dp, 2.2f)
                            BasicText(if (strictOn) "STRICT" else "NORMAL", style = label(if (strictOn) fill else fg, 9.sp, bold = true), maxLines = 1)
                        }
                    }
                }
                if (on) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxHeight().aspectRatio(1f).padding(2.dp)) {
                            val sw = size.minDimension * 0.085f
                            val box = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw)
                            val tl = Offset(sw / 2, sw / 2)
                            drawArc(fg.copy(alpha = 0.22f), 0f, 360f, false, tl, box, style = Stroke(sw))
                            drawArc(fg, -90f, 360f * frac, false, tl, box, style = Stroke(sw, cap = StrokeCap.Round))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BasicText("$left", style = display(if (h >= 3) 46.sp else if (h >= 2) 30.sp else 20.sp, fg, (-0.04).em, 1f))
                            BasicText("MIN" + if (act.strict) " / STRICT" else "", style = label(fg, 9.sp, bold = true))
                        }
                    }
                    Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("+15", Modifier.weight(1f).fillMaxHeight(), solid = true) { if (FocusEngine.isSession()) state.extendFocus(15) }
                        if (!act.strict && FocusEngine.isSession()) Btn("END", Modifier.weight(1f).fillMaxHeight(), solid = false) { state.endFocus() }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (h >= 3) {
                            BasicText(if (today > 0) mins(today) else "0m", style = display(if (h >= 4) 64.sp else 46.sp, fg, (-0.04).em, 0.9f))
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                                week.forEach { d -> Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(fg.copy(alpha = if (d) 1f else 0.25f))) }
                                Spacer(Modifier.width(4.dp))
                                BasicText("FOCUSED TODAY", style = label(fg, 10.sp, bold = true))
                            }
                        } else BasicText(if (today > 0) "${mins(today)} FOCUSED TODAY" else "NOT STARTED TODAY", style = label(fg.copy(alpha = 0.8f), 10.sp, bold = true), maxLines = 1)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val fm = state.prefs.focusMinutes.coerceIn(1, 480)
                        fun nudge(dir: Int) { state.prefs.focusMinutes = stepMinutes(fm, dir) }
                        // Set any length right here: hold + or - to run through the minutes.
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StepBtn("minus", fg, fill, 38.dp) { nudge(-1) }
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                                BasicText("$fm", style = display(if (h >= 3) 44.sp else 34.sp, fg, (-0.04).em, 1f), maxLines = 1)
                                BasicText(" MIN", style = label(fg.copy(alpha = 0.7f), 10.sp, bold = true), maxLines = 1, modifier = Modifier.padding(bottom = 5.dp))
                            }
                            StepBtn("plus", fg, fill, 38.dp) { nudge(1) }
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(fg).clickable { start(fm) }, contentAlignment = Alignment.Center) {
                                Glyph("play", fill, 20.dp, 2f, filled = true)
                            }
                        }
                        if (h >= 3)                         Row(Modifier.fillMaxWidth().height(50.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(15, 25, 45).forEach { m ->
                                Column(
                                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(13.dp)).background(fg.copy(alpha = 0.14f)).clickable { start(m) },
                                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                                ) {
                                    BasicText("$m", style = display(20.sp, fg, (-0.03).em, 1f), maxLines = 1)
                                    BasicText("MIN", style = label(fg.copy(alpha = 0.7f), 8.sp, bold = true), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- android widget ----------------------------------------------------------------------------

@Composable
fun WidgetTile(state: HomeState, id: Int, bg: Color, ink: Color) {
    val ctx = LocalContext.current
    val info = remember(id) { state.widgets.info(id) }
    Box(Modifier.fillMaxSize().clip(TileShape).background(bg)) {
        if (info == null) {
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Glyph("widget", ink, 24.dp, 2f)
                BasicText("Widget unavailable", style = display(14.sp, ink, 0.em, 1.05f))
            }
        } else BoxWithConstraints(Modifier.fillMaxSize()) {
            val wDp = maxWidth.value.toInt()
            val hDp = maxHeight.value.toInt()
            AndroidView(
                factory = { c -> state.widgets.host.createView(c, id, info).apply { setPadding(0, 0, 0, 0) } },
                update = { v -> @Suppress("DEPRECATION") v.updateAppWidgetSize(null, wDp, hDp, wDp, hDp) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
