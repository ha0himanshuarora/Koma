package app.koma.launcher.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.koma.launcher.data.BtDevice
import app.koma.launcher.data.Bluetooth
import app.koma.launcher.data.ClockMath
import app.koma.launcher.data.Countdown
import app.koma.launcher.data.TimerCtl
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ---- bluetooth ---------------------------------------------------------------------------------

@Composable
private fun BatteryBar(pct: Int, ink: Color, modifier: Modifier = Modifier) {
    Box(modifier.height(4.dp).clip(CircleShape).background(ink.copy(alpha = 0.22f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth((pct / 100f).coerceIn(0.04f, 1f)).clip(CircleShape).background(ink))
    }
}

/** Connected Bluetooth devices with their names and, when they report it, their battery. */
@Composable
fun BluetoothTile(w: Int, h: Int, bg: Color, ink: Color, granted: Boolean, iconStyle: String = "outline") {
    val ctx = LocalContext.current
    val tick = remember { mutableIntStateOf(0) }
    DisposableEffect(granted) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                tick.intValue = tick.intValue + 1
            }
        }
        val f = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
        }
        try {
            ContextCompat.registerReceiver(ctx, r, f, ContextCompat.RECEIVER_EXPORTED)
        } catch (e: Exception) {
        }
        onDispose { try { ctx.unregisterReceiver(r) } catch (e: Exception) { } }
    }
    val devices by produceState<List<BtDevice>?>(null, granted, tick.intValue) {
        while (true) {
            awaitVisible()
            value = if (granted) Bluetooth.connected(ctx) else emptyList()
            delay(30_000L)
        }
    }
    val list = devices.orEmpty()
    val note = when {
        !granted -> "Allow Bluetooth access"
        !Bluetooth.enabled(ctx) -> "Bluetooth is off"
        devices == null -> "Looking..."
        list.isEmpty() -> "Nothing connected"
        else -> null
    }
    fun pct(d: BtDevice) = if (d.battery >= 0) "${d.battery}%" else "ON"

    Box(Modifier.fillMaxSize().clip(TileShape).background(bg).padding(14.dp)) {
        when {
            note != null -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph("bt", ink, 18.dp, 2f)
                    Spacer(Modifier.size(8.dp))
                    BasicText("BLUETOOTH", style = label(ink, 10.sp, bold = true))
                }
                BasicText(note, style = display(if (h >= 2) 20.sp else 15.sp, ink, 0.em, 1.05f), maxLines = 2)
            }
            // One device, plenty of room: a hero card with its icon, type, name and battery.
            h >= 2 && list.size == 1 -> {
                val d = list.first()
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BtChip(d.kind, iconStyle, ink, bg, if (w >= 3) 52 else 44)
                        Spacer(Modifier.size(10.dp))
                        Column {
                            BasicText("${d.typeLabel.uppercase()} CONNECTED", style = label(ink, 10.sp, bold = true), maxLines = 1)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BasicText(d.name, style = display(if (w >= 3) 26.sp else 20.sp, ink, (-0.02).em, 1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (d.battery >= 0) {
                                BatteryBar(d.battery, ink, Modifier.weight(1f))
                                BasicText("${d.battery}%", style = label(ink, 12.sp, bold = true))
                            } else BasicText("ON", style = label(ink, 12.sp, bold = true))
                        }
                    }
                }
            }
            h >= 2 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph("bt", ink, 18.dp, 2f)
                    Spacer(Modifier.size(8.dp))
                    BasicText("CONNECTED / ${list.size}", style = label(ink, 10.sp, bold = true))
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    list.take(3).forEach { d ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BtChip(d.kind, iconStyle, ink, bg, 34)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                BasicText(d.name, style = display(15.sp, ink, 0.em, 1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (d.battery >= 0) BatteryBar(d.battery, ink, Modifier.fillMaxWidth())
                                else BasicText(d.typeLabel.uppercase(), style = label(ink.copy(alpha = 0.75f), 9.sp), maxLines = 1)
                            }
                            BasicText(pct(d), style = label(ink, 11.sp, bold = true))
                        }
                    }
                }
            }
            w >= 4 && list.size > 1 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                list.take(3).forEach { d ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BtChip(d.kind, iconStyle, ink, bg, 32)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            BasicText(d.typeLabel.uppercase(), style = label(ink, 9.sp, bold = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicText(pct(d), style = display(20.sp, ink, (-0.03).em, 1f), maxLines = 1)
                        }
                    }
                }
            }
            // One row, one device: icon, "Earbuds connected", the name and the battery, so it never reads as a lone line.
            else -> {
                val d = list.first()
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    BtChip(d.kind, iconStyle, ink, bg, if (w >= 3) 46 else 38)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        BasicText("${d.typeLabel.uppercase()} CONNECTED", style = label(ink.copy(alpha = 0.8f), 9.sp, bold = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText(d.name, style = display(if (w >= 3) 20.sp else 15.sp, ink, (-0.02).em, 1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (d.battery >= 0) BatteryBar(d.battery, ink, Modifier.fillMaxWidth(0.85f))
                    }
                    if (list.size > 1) BasicText("+${list.size - 1}", style = label(ink, 11.sp, bold = true))
                    else if (w >= 3) BasicText(pct(d), style = display(26.sp, ink, (-0.03).em, 1f), maxLines = 1)
                    else if (d.battery >= 0) BasicText(pct(d), style = label(ink, 11.sp, bold = true))
                }
            }
        }
    }
}

// ---- countdown ---------------------------------------------------------------------------------

@Composable
fun CountdownTile(w: Int, h: Int, bg: Color, ink: Color, arg: String) {
    val now by rememberNow()
    val date = Countdown.date(arg)
    val days = if (date == null) null else ChronoUnit.DAYS.between(now.toLocalDate(), date)
    val number = days?.let { Math.abs(it).toString() } ?: "--"
    val unit = when {
        days == null -> "SET A DATE"
        days == 0L -> "TODAY"
        days > 0 -> if (days == 1L) "DAY LEFT" else "DAYS LEFT"
        else -> if (days == -1L) "DAY AGO" else "DAYS AGO"
    }
    val name = Countdown.name(arg).uppercase()
    Box(Modifier.fillMaxSize().clip(TileShape).background(bg).padding(14.dp)) {
        if (w == 1) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                BasicText(name, style = label(ink, 8.sp, bold = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(number, style = display(30.sp, ink, (-0.04).em, 1f), maxLines = 1)
            }
        } else if (h == 1) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BasicText(name, style = label(ink, 10.sp, bold = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(unit, style = label(ink.copy(alpha = 0.75f), 9.sp), maxLines = 1)
                }
                BasicText(number, style = display(38.sp, ink, (-0.04).em, 1f), maxLines = 1)
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                BasicText(name, style = label(ink, 10.sp, bold = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Column {
                    BasicText(number, style = display(64.sp, ink, (-0.05).em, 0.95f), maxLines = 1)
                    BasicText(unit, style = label(ink, 11.sp, bold = true))
                }
                BasicText(date?.format(DateTimeFormatter.ofPattern("d MMM yyyy"))?.uppercase() ?: "", style = label(ink.copy(alpha = 0.7f), 9.sp))
            }
        }
    }
}

// ---- timer -------------------------------------------------------------------------------------

private fun clockText(ms: Long): String {
    val s = ((ms + 999) / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private val timerPresets = listOf(1, 5, 10, 15, 30, 45, 60)

/** Text that shrinks to fit the width it is given, so a time is never cut off. [small] is drawn smaller after it, like AM or PM. */
@Composable
private fun TimeFit(
    text: String, color: Color, maxSp: Int, modifier: Modifier = Modifier, align: Alignment = Alignment.CenterStart,
    tracking: androidx.compose.ui.unit.TextUnit = (-0.03).em, small: String = "",
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier, contentAlignment = align) {
        val eff = text.length.coerceAtLeast(1) + if (small.isEmpty()) 0f else (small.length + 1) * 0.45f
        val sp = minOf(maxSp.toFloat(), maxWidth.value / (eff * 0.7f)).coerceAtLeast(9f)
        val shown = if (small.isEmpty()) AnnotatedString(text) else buildAnnotatedString {
            append(text)
            withStyle(SpanStyle(fontSize = (sp * 0.4f).sp)) { append(" $small") }
        }
        BasicText(shown, style = display(sp.sp, color, tracking, 1f), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
    }
}

/** A round step button that keeps stepping while it is held down. */
@Composable
internal fun StepBtn(glyph: String, fg: Color, onFg: Color, size: androidx.compose.ui.unit.Dp, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.34f)).background(fg.copy(alpha = 0.16f))
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    step()
                    val job = scope.launch {
                        delay(420L)
                        while (true) {
                            step()
                            delay(90L)
                        }
                    }
                    tryAwaitRelease()
                    job.cancel()
                })
            },
        contentAlignment = Alignment.Center,
    ) { Glyph(glyph, fg, size * 0.46f, 2.4f) }
}

@Composable
private fun RoundBtn(glyph: String, fg: Color, onFg: Color, size: androidx.compose.ui.unit.Dp, filled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.34f)).background(fg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Glyph(glyph, onFg, size * 0.46f, 2f, filled = filled)
    }
}

@Composable
private fun PillBtn(text: String, fg: Color, onFg: Color, solid: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (solid) fg else fg.copy(alpha = 0.16f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { BasicText(text, style = label(if (solid) onFg else fg, 10.sp, bold = true), maxLines = 1, modifier = Modifier.padding(horizontal = 5.dp), softWrap = false) }
}

/** A countdown that rings when it ends. Start, pause, reset and pick the length right on the tile. */
@Composable
fun TimerTile(state: HomeState, tileId: String, w: Int, h: Int, bg: Color, ink: Color, minutes: Int, end: Long, len: Long = 0L, paused: Long = 0L) {
    val ctx = LocalContext.current
    val p = state.prefs
    // The last session's length is remembered, so the tile shows it and runs it again.
    val total = if (len > 0L) len else minutes * 60_000L
    val mins = ((total + 30_000L) / 60_000L).toInt().coerceAtLeast(1)
    val now by produceState(System.currentTimeMillis(), end) {
        while (end > 0L) {
            awaitVisible()
            value = System.currentTimeMillis()
            delay(250L)
        }
    }
    val running = end > now
    val done = end > 0L && !running
    val idle = !running && !done && paused <= 0L
    val left = if (running) end - now else if (paused > 0L) paused else total
    val frac = if (running && total > 0L) (1f - (end - now).toFloat() / total).coerceIn(0f, 1f) else if (paused > 0L && total > 0L) (1f - paused.toFloat() / total).coerceIn(0f, 1f) else if (done) 1f else 0f
    val fill = if (!idle) ink else bg
    val fg = if (!idle) bg else ink
    val title = if (done) "TIME'S UP" else if (paused > 0L) "PAUSED" else if (running) "TIMER" else "TIMER"
    val time = if (done) "0:00" else clockText(left)

    fun pickMin(m: Int) {
        state.setArg(tileId, m.toString())
        p.timerLen = m * 60_000L
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
    fun primary() {
        when {
            done -> begin(if (len > 0L) len else minutes * 60_000L)
            running -> {
                p.timerLeft = (end - System.currentTimeMillis()).coerceAtLeast(1000L)
                p.timerEnd = 0L
                TimerCtl.cancel(ctx)
            }
            paused > 0L -> {
                val t = System.currentTimeMillis()
                p.timerEnd = t + paused
                TimerCtl.start(ctx, t + paused)
                p.timerLeft = 0L
            }
            else -> begin(total)
        }
    }
    val playGlyph = if (running) "pause" else "play"

    @Composable
    fun Ring(m: Modifier) {
        androidx.compose.foundation.Canvas(m) {
            val sw = size.minDimension * 0.085f
            val box = androidx.compose.ui.geometry.Size(size.width - sw, size.height - sw)
            val topLeft = androidx.compose.ui.geometry.Offset(sw / 2, sw / 2)
            drawArc(fg.copy(alpha = 0.22f), 0f, 360f, false, topLeft, box, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
            drawArc(fg, -90f, 360f * frac, false, topLeft, box, style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
    }

    @Composable
    fun Chips(count: Int, modifier: Modifier) {
        val base = when (count) {
            3 -> listOf(5, 10, 15)
            4 -> listOf(5, 10, 15, 30)
            5 -> listOf(5, 10, 15, 30, 60)
            else -> listOf(1, 5, 10, 15, 30, 60)
        }
        val shown = if (mins in base) base else (base.drop(1) + mins).sorted()
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            shown.forEach { m ->
                PillBtn("${m}m", fg, fill, m == mins, Modifier.weight(1f).height(30.dp)) { pickMin(m) }
            }
        }
    }

    @Composable
    fun Controls(chips: Int, modifier: Modifier) {
        if (idle) {
            Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (chips <= 0) {
                    PillBtn("${mins}m", fg, fill, false, Modifier.weight(1f).height(34.dp)) {
                        pickMin((timerPresets.firstOrNull { it > mins } ?: timerPresets.first()))
                    }
                    PillBtn("START", fg, fill, true, Modifier.weight(1.4f).height(34.dp)) { primary() }
                } else {
                    Chips(chips, Modifier.weight(1f))
                    RoundBtn("play", fg, fill, 34.dp) { primary() }
                }
            }
        } else {
            Row(modifier.height(34.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PillBtn(if (done) "END" else "RESET", fg, fill, false, Modifier.weight(1f).fillMaxHeight()) { stop() }
                PillBtn(if (done) "RESTART" else if (running) "PAUSE" else "RESUME", fg, fill, true, Modifier.weight(1.3f).fillMaxHeight()) { primary() }
            }
        }
    }

    @Composable
    fun Segments(modifier: Modifier) {
        Row(modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 0 until 30) Box(Modifier.weight(1f).fillMaxHeight().background(if (i < (frac * 30).toInt()) fg else fg.copy(alpha = 0.25f)))
        }
    }

    Box(Modifier.fillMaxSize().clip(TileShape).background(fill).padding(14.dp)) {
        if (w == 1) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Glyph("timer", fg, 18.dp, 2f)
                    RoundBtn(playGlyph, fg, fill, 26.dp) { primary() }
                }
                TimeFit(time, fg, 22, Modifier.fillMaxWidth())
            }
        } else if (h == 1) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    BasicText(title, style = label(fg, 10.sp, bold = true), maxLines = 1)
                    TimeFit(time, fg, if (w >= 4) 34 else 28, Modifier.fillMaxWidth())
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(fg.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(frac).clip(CircleShape).background(fg))
                    }
                }
                if (idle) {
                    PillBtn("${mins}m", fg, fill, false, Modifier.height(32.dp)) {
                        pickMin((timerPresets.firstOrNull { it > mins } ?: timerPresets.first()))
                    }
                } else RoundBtn("close", fg, fill, 30.dp, filled = false) { stop() }
                RoundBtn(playGlyph, fg, fill, 40.dp) { primary() }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText(title, style = label(fg, 10.sp, bold = true), maxLines = 1, softWrap = false)
                    if (running) {
                        val at = java.time.Instant.ofEpochMilli(end).atZone(java.time.ZoneId.systemDefault())
                        BasicText(
                            "RINGS " + at.format(DateTimeFormatter.ofPattern(if (app.koma.launcher.data.TimeFmt.is24()) "HH:mm" else "h:mm a")).uppercase(),
                            style = label(fg.copy(alpha = 0.75f), 10.sp, bold = true), maxLines = 1, softWrap = false,
                        )
                    }
                }
                fun nudge(dir: Int) {
                    // The same steps as the Focus tile: up is 1 under 5 minutes, 5 under an hour, then 15. Down is 1 up to 5, 5 up to an hour, then 15.
                    pickMin((mins + dir * stepSize(mins, dir)).coerceIn(1, 5940))
                }
                if (idle) {
                    // Set any length right here: hold + or - to run through the minutes.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StepBtn("minus", fg, fill, 36.dp) { nudge(-1) }
                        TimeFit(time, fg, if (h >= 4) 96 else if (h >= 3) 64 else if (w >= 3) 48 else 40, Modifier.weight(1f), align = Alignment.Center)
                        StepBtn("plus", fg, fill, 36.dp) { nudge(1) }
                    }
                } else TimeFit(time, fg, if (h >= 4) 104 else if (h >= 3) 72 else if (w >= 3) 54 else 44, Modifier.fillMaxWidth())
                Segments(Modifier.fillMaxWidth())
                Controls(if (w >= 4) 5 else if (w == 3) 4 else 0, Modifier.fillMaxWidth())
            }
        }
    }
}

// ---- stopwatch ---------------------------------------------------------------------------------

/** One stopwatch for the whole launcher; every Stopwatch tile shows and controls it. */
object Stopwatch {
    val running = mutableStateOf(false)
    private val acc = mutableStateOf(0L)
    private var startedAt = 0L

    fun elapsed(): Long = acc.value + if (running.value) SystemClock.elapsedRealtime() - startedAt else 0L

    fun toggle() {
        if (running.value) {
            acc.value = elapsed()
            running.value = false
        } else {
            startedAt = SystemClock.elapsedRealtime()
            running.value = true
        }
    }

    /** Cumulative times at which each lap button was pressed. */
    val laps = androidx.compose.runtime.mutableStateListOf<Long>()

    fun lap() {
        if (running.value) laps.add(elapsed())
    }

    fun reset() {
        acc.value = 0L
        running.value = false
        laps.clear()
    }

    fun format(ms: Long, tenths: Boolean): String {
        val t = ms / 1000
        val h = t / 3600
        val m = (t % 3600) / 60
        val s = t % 60
        return when {
            h > 0 -> "%d:%02d:%02d".format(h, m, s)
            tenths -> "%d:%02d.%d".format(m, s, (ms / 100) % 10)
            else -> "%d:%02d".format(m, s)
        }
    }
}

/** A watch face: 60 ticks that light up with the seconds. */
@Composable
private fun Dial(ms: Long, fg: Color, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        val sec = ((ms % 60000L) / 1000L).toInt()
        for (i in 0 until 60) {
            val a = Math.toRadians((i * 6 - 90).toDouble())
            val major = i % 5 == 0
            val len = r * (if (major) 0.17f else 0.09f)
            val lit = ms > 0L && i <= sec
            val col = if (lit) fg else fg.copy(alpha = if (major) 0.5f else 0.22f)
            val cs = Math.cos(a).toFloat()
            val sn = Math.sin(a).toFloat()
            drawLine(
                col,
                androidx.compose.ui.geometry.Offset(c.x + (r - len) * cs, c.y + (r - len) * sn),
                androidx.compose.ui.geometry.Offset(c.x + r * cs, c.y + r * sn),
                strokeWidth = size.minDimension * (if (major) 0.026f else 0.016f),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
    }
}

@Composable
fun StopwatchTile(w: Int, h: Int, bg: Color, ink: Color) {
    val on = Stopwatch.running.value
    val frozen = if (on) 0L else Stopwatch.elapsed()
    val ms by produceState(Stopwatch.elapsed(), on, frozen) {
        value = Stopwatch.elapsed()
        while (Stopwatch.running.value) {
            awaitVisible()
            value = Stopwatch.elapsed()
            delay(100L)
        }
    }
    val fill = if (on) ink else bg
    val fg = if (on) bg else ink
    val showReset = !on && ms > 0L
    val laps = Stopwatch.laps.toList()
    val name = if (on) "RUNNING" else if (showReset) "PAUSED" else "STOPWATCH"
    val shortName = if (on) "RUNNING" else if (showReset) "PAUSED" else "READY"
    val playGlyph = if (on) "pause" else "play"

    @Composable
    fun Secondary() {
        if (on) PillBtn("LAP", fg, fill, false, Modifier.height(32.dp).width(58.dp)) { Stopwatch.lap() }
        else if (showReset) PillBtn("RESET", fg, fill, false, Modifier.height(32.dp).width(64.dp)) { Stopwatch.reset() }
    }

    @Composable
    fun LapRows(n: Int) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (laps.isEmpty()) BasicText("NO LAPS YET", style = label(fg.copy(alpha = 0.6f), 9.sp), maxLines = 1, softWrap = false)
            laps.indices.reversed().take(n).forEach { i ->
                val split = laps[i] - (if (i == 0) 0L else laps[i - 1])
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText("LAP %02d".format(i + 1), style = label(fg.copy(alpha = 0.7f), 10.sp, bold = true))
                    BasicText("+" + Stopwatch.format(split, true), style = label(fg, 11.sp, bold = true))
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().clip(TileShape).background(fill).padding(14.dp)) {
        if (w == 1) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Glyph("timer", fg, 18.dp, 2f)
                    RoundBtn(playGlyph, fg, fill, 26.dp) { Stopwatch.toggle() }
                }
                TimeFit(Stopwatch.format(ms, false), fg, 22, Modifier.fillMaxWidth())
            }
        } else if (h == 1) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    BasicText(name, style = label(fg, 10.sp, bold = true), maxLines = 1, softWrap = false)
                    TimeFit(Stopwatch.format(ms, true), fg, if (w >= 4) 36 else 30, Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().height(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (i in 0 until 30) Box(Modifier.weight(1f).fillMaxHeight().background(if (ms > 0L && i <= ((ms / 2000L) % 30).toInt()) fg else fg.copy(alpha = 0.25f)))
                    }
                }
                if (w >= 3) Secondary()
                RoundBtn(playGlyph, fg, fill, 40.dp) { Stopwatch.toggle() }
            }
        } else if (w >= 3 && h == 2) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.fillMaxHeight().aspectRatio(1f), contentAlignment = Alignment.Center) {
                    Dial(ms, fg, Modifier.fillMaxSize().padding(2.dp))
                    TimeFit(Stopwatch.format(ms, false), fg, 30, Modifier.fillMaxHeight(0.52f).aspectRatio(1f), Alignment.Center)
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        BasicText(shortName, style = label(fg, 10.sp, bold = true), maxLines = 1, softWrap = false)
                        if (laps.isNotEmpty()) BasicText("${laps.size} LAP${if (laps.size == 1) "" else "S"}", style = label(fg.copy(alpha = 0.75f), 10.sp, bold = true), maxLines = 1, softWrap = false)
                    }
                    LapRows(2)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundBtn(playGlyph, fg, fill, 40.dp) { Stopwatch.toggle() }
                        Secondary()
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText(name, style = label(fg, 10.sp, bold = true), maxLines = 1, softWrap = false)
                    if (laps.isNotEmpty()) BasicText("${laps.size} LAP${if (laps.size == 1) "" else "S"}", style = label(fg.copy(alpha = 0.75f), 10.sp, bold = true))
                }
                Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                    Dial(ms, fg, Modifier.fillMaxHeight().aspectRatio(1f))
                    TimeFit(Stopwatch.format(ms, true), fg, 34, Modifier.fillMaxHeight(0.52f).aspectRatio(1f), Alignment.Center)
                }
                if (h >= 4) LapRows(2)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    RoundBtn(playGlyph, fg, fill, 42.dp) { Stopwatch.toggle() }
                    Secondary()
                }
            }
        }
    }
}

@Composable
private fun MiniToggle(on: Boolean, fg: Color, bg: Color, onChange: (Boolean) -> Unit) {
    Box(Modifier.clickable { onChange(!on) }.padding(horizontal = 4.dp, vertical = 7.dp)) {
        Box(Modifier.width(36.dp).height(20.dp).clip(RoundedCornerShape(7.dp)).background(if (on) fg else fg.copy(alpha = 0.22f)).padding(3.dp)) {
            Box(Modifier.size(14.dp).align(if (on) Alignment.CenterEnd else Alignment.CenterStart).clip(RoundedCornerShape(4.dp)).background(if (on) bg else fg.copy(alpha = 0.8f)))
        }
    }
}

/** Every alarm in a list, next one first, each with a small switch. Tap anywhere else to open the Clock app. */
@Composable
fun AlarmsTile(w: Int, h: Int, bg: Color, ink: Color, alarms: List<app.koma.launcher.data.Alarm>, onToggle: (String, Boolean) -> Unit) {
    val now by produceState(java.time.ZonedDateTime.now(), alarms) {
        while (true) {
            awaitVisible()
            value = java.time.ZonedDateTime.now()
            delay(20_000L)
        }
    }
    val is24 = app.koma.launcher.data.TimeFmt.is24()
    val all = alarms
        .map { a -> Triple(a, app.koma.launcher.data.ClockMath.next(a, now), a.on) }
        .sortedWith(compareBy({ !it.third }, { it.second?.toEpochSecond() ?: Long.MAX_VALUE }))
    val active = all.filter { it.third && it.second != null }
    val next = active.firstOrNull()
    fun clock(a: app.koma.launcher.data.Alarm) = ClockMath.parts(a.hour, a.minute, is24).first
    fun ampm(a: app.koma.launcher.data.Alarm) = ClockMath.parts(a.hour, a.minute, is24).second
    fun inText(t: java.time.ZonedDateTime) = "IN " + ClockMath.until(now, t).uppercase()
    fun days(a: app.koma.launcher.data.Alarm) = ClockMath.daysText(a.days, ClockMath.dayLetters, "DAILY")
    Box(Modifier.fillMaxSize().clip(TileShape).background(bg).padding(14.dp)) {
        if (w == 1) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Glyph("alarm", ink, 18.dp, 2f)
                Column {
                    if (next != null) TimeFit(clock(next.first), ink, 22, Modifier.fillMaxWidth())
                    else BasicText("OFF", style = display(20.sp, ink, (-0.03).em, 1f), maxLines = 1)
                    if (next != null && ampm(next.first).isNotEmpty()) BasicText(ampm(next.first), style = label(ink.copy(alpha = 0.7f), 9.sp, bold = true))
                }
            }
        } else if (h == 1) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(if (next != null) "NEXT ALARM / " + inText(next.second!!) else "ALARMS", style = label(ink, 10.sp, bold = true), maxLines = 1)
                    if (next != null) TimeFit((clock(next.first) + " " + ampm(next.first)).trim(), ink, 30, Modifier.fillMaxWidth())
                    else BasicText("None on", style = display(26.sp, ink, (-0.03).em, 1f), maxLines = 1)
                }
                if (next != null) MiniToggle(true, ink, bg) { onToggle(next.first.id, it) }
                else Glyph("alarm", ink, 22.dp, 2f)
            }
        } else {
            val hero = next ?: all.firstOrNull()
            val others = all.filter { it !== hero }
            val rowsFit = if (h == 2) 0 else if (h == 3) 2 else 4
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText("ALARMS", style = label(ink, 10.sp, bold = true))
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(ink.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        BasicText(if (next != null) inText(next.second!!) else "NONE ON", style = label(ink, 9.sp, bold = true), maxLines = 1)
                    }
                }
                if (hero == null) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) {
                        BasicText("No alarms. Tap to add one.", style = display(20.sp, ink, (-0.03).em, 1f), maxLines = 2)
                    }
                } else {
                    val ha = hero.first
                    val hon = hero.third
                    // The next alarm gets the room: a big time, what it's called, and a switch.
                    Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f).alpha(if (hon) 1f else 0.45f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            TimeFit(clock(ha), ink, if (h >= 3) 64 else 52, Modifier.fillMaxWidth(), small = ampm(ha))
                            BasicText(
                                (if (ha.label.isNotBlank()) ha.label.uppercase() + " / " else "") + days(ha),
                                style = label(ink.copy(alpha = 0.75f), 10.sp, bold = true), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        MiniToggle(hon, ink, bg) { onToggle(ha.id, it) }
                    }
                    if (h >= 3 && hon && hero.second != null) {
                        // How close it is: fills over the last 12 hours before it rings.
                        val mins = java.time.Duration.between(now, hero.second!!).toMinutes().coerceAtLeast(0)
                        val frac = (1f - mins / 720f).coerceIn(0f, 1f)
                        Row(Modifier.fillMaxWidth().height(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (i in 0 until 24) Box(Modifier.weight(1f).fillMaxHeight().background(if (i < (frac * 24).toInt()) ink else ink.copy(alpha = 0.2f)))
                        }
                    }
                    // Which days it rings: a week strip, today ringed.
                    val ringDays = if (ha.days == 0) (hero.second?.let { 1 shl (it.dayOfWeek.value - 1) } ?: 0) else ha.days
                    val todayBit = now.dayOfWeek.value - 1
                    Row(Modifier.fillMaxWidth().height(24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, d ->
                                val lit = ringDays and (1 shl i) != 0
                                Box(
                                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                                        .background(if (lit) ink else ink.copy(alpha = 0.13f))
                                        .then(if (i == todayBit) Modifier.border(1.5.dp, ink, RoundedCornerShape(8.dp)) else Modifier),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    BasicText(d, style = label(if (lit) bg else ink.copy(alpha = 0.55f), 9.sp, bold = true), maxLines = 1)
                                }
                            }
                        }
                        if (h == 2 && w >= 3 && others.isNotEmpty()) {
                            others.take(if (w >= 4) 2 else 1).forEach { (a, _, on) ->
                                Box(
                                    Modifier.fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(ink.copy(alpha = if (on) 0.18f else 0.08f))
                                        .clickable { onToggle(a.id, !on) }.padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    BasicText((clock(a) + " " + ampm(a)).trim(), style = label(ink.copy(alpha = if (on) 1f else 0.5f), 9.sp, bold = true), maxLines = 1)
                                }
                            }
                            if (others.size > (if (w >= 4) 2 else 1)) BasicText("+${others.size - (if (w >= 4) 2 else 1)}", style = label(ink.copy(alpha = 0.6f), 9.sp, bold = true))
                        }
                    }
                    if (rowsFit > 0) {
                        others.take(rowsFit).forEach { (a, _, on) ->
                            Row(Modifier.fillMaxWidth().weight(0.6f), verticalAlignment = Alignment.CenterVertically) {
                                Row(Modifier.weight(1f).alpha(if (on) 1f else 0.45f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    BasicText(clock(a), style = display(20.sp, ink, (-0.03).em, 1f), maxLines = 1)
                                    if (ampm(a).isNotEmpty()) BasicText(ampm(a), style = label(ink, 9.sp, bold = true))
                                    BasicText(days(a), style = label(ink.copy(alpha = 0.7f), 9.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                MiniToggle(on, ink, bg) { onToggle(a.id, it) }
                            }
                        }
                        if (others.size > rowsFit) BasicText("+${others.size - rowsFit} MORE", style = label(ink.copy(alpha = 0.6f), 9.sp, bold = true))
                    }
                }
            }
        }
    }
}
