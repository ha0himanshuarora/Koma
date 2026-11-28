package app.koma.launcher.ui

import app.koma.launcher.data.TimeFmt
import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.koma.launcher.data.Ambient
import app.koma.launcher.data.Bag
import app.koma.launcher.data.Bags
import app.koma.launcher.data.FocusEngine
import app.koma.launcher.data.KomaFocusService
import app.koma.launcher.data.FocusPlan
import app.koma.launcher.data.FocusRun
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayStyle
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt

// ---- look: pixel blocks ------------------------------------------------------------------------

internal val Big = RoundedCornerShape(36.dp)
internal val Mid = RoundedCornerShape(26.dp)

@Composable
internal fun cardBg() = surfaceColor(0.075f)

@Composable
internal fun deepBg() = surfaceColor(0.035f)

private val digitBits = mapOf(
    '0' to "111101101101111", '1' to "010010010010010", '2' to "111001111100111", '3' to "111001111001111",
    '4' to "101101111001001", '5' to "111100111001111", '6' to "111100111101111", '7' to "111001001001001",
    '8' to "111101111101111", '9' to "111101111001111",
    'D' to "110101101101110", 'O' to "111101101101111", 'N' to "101111111101101", 'E' to "111100111100111",
)

/** Digits drawn from square blocks on a 3 x 5 grid, like the Blockit clock. */
@Composable
internal fun BlockText(text: String, color: Color, modifier: Modifier = Modifier, colon: Color = color.copy(alpha = 0.5f)) {
    Canvas(modifier) {
        var cells = 0
        for (ch in text) cells += if (ch == ':') 1 else 3
        cells += (text.length - 1).coerceAtLeast(0)
        val c = minOf(size.width / cells, size.height / 5f)
        var x = (size.width - cells * c) / 2f
        val y0 = (size.height - 5 * c) / 2f
        val sq = Size(c + 0.6f, c + 0.6f)
        for (ch in text) {
            if (ch == ':') {
                drawRect(colon, Offset(x, y0 + c), sq)
                drawRect(colon, Offset(x, y0 + 3 * c), sq)
                x += 2 * c
            } else {
                val bits = digitBits[ch] ?: "000000000000000"
                for (r in 0 until 5) for (k in 0 until 3) {
                    if (bits[r * 3 + k] == '1') drawRect(color, Offset(x + k * c, y0 + r * c), sq)
                }
                x += 4 * c
            }
        }
    }
}

private fun endsAt(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(TimeFmt.pattern(), Locale.getDefault()))

internal fun hhmm(min: Int): String {
    val m = ((min % 1440) + 1440) % 1440
    return "%02d:%02d".format(m / 60, m % 60)
}

internal fun durText(min: Int) = if (min % 60 == 0) "${min / 60}h" else if (min < 60) "${min}m" else "${min / 60}h ${min % 60}m"

// ---- shared controls ---------------------------------------------------------------------------

/** A track with a knob. Drag the knob across to confirm. [reverse] puts the knob on the right, dragging left. */
@Composable
internal fun SlideTrack(label: String, glyph: String, reverse: Boolean, enabled: Boolean = true, knobBg: Color, knobInk: Color, onDone: () -> Unit) {
    val pal = LocalPalette.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var trackW by remember { mutableIntStateOf(0) }
    val offset = remember { Animatable(0f) }
    val knob = 60.dp
    val maxX = (trackW - with(density) { (knob + 12.dp).toPx() }).coerceAtLeast(1f)
    val done by rememberUpdatedState(onDone)
    // The further it is dragged, the more the knob (and a little of the track) takes on the accent colour.
    val p = if (enabled) (offset.value / maxX).coerceIn(0f, 1f) else 0f
    val trackBase = cardBg()
    val trackColor = androidx.compose.ui.graphics.lerp(trackBase, pal.tiles[0].copy(alpha = 0.32f).compositeOver(trackBase), p)
    val knobColor = androidx.compose.ui.graphics.lerp(knobBg, pal.tiles[0], p)
    val knobGlyph = androidx.compose.ui.graphics.lerp(knobInk, pal.ink, p)
    Box(
        Modifier.fillMaxWidth().height(72.dp).clip(Mid).background(trackColor).onSizeChanged { trackW = it.width },
        contentAlignment = if (reverse) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        BasicText(
            label,
            style = monoStyle(pal.onGround, 12, bold = false),
            modifier = Modifier.align(if (reverse) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 36.dp)
                .graphicsLayer { alpha = (1f - offset.value / maxX).coerceIn(0f, 1f) },
        )
        Box(
            Modifier
                .padding(6.dp)
                .offset { IntOffset(((if (reverse) -offset.value else offset.value)).roundToInt(), 0) }
                .size(knob)
                .clip(RoundedCornerShape(22.dp))
                .background(knobColor)
                .pointerInput(maxX, enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offset.value > maxX * 0.88f) {
                                done()
                                scope.launch { offset.snapTo(0f) }
                            } else scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 400f)) }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                    ) { change, drag ->
                        change.consume()
                        val d = if (reverse) -drag else drag
                        scope.launch { offset.snapTo((offset.value + d).coerceIn(0f, maxX)) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Glyph(glyph, knobGlyph, 26.dp, 2.4f, filled = glyph == "play") }
    }
}

/** A horizontal ruler for a time of day. Drag left for later, right for earlier. Wraps around midnight. */
@Composable
internal fun HRuler(value: Int, onChange: (Int) -> Unit, modifier: Modifier, step: Int = 5) {
    val pal = LocalPalette.current
    val gap = with(LocalDensity.current) { 9.dp.toPx() }
    var acc by remember { mutableFloatStateOf(0f) }
    val cur by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    val shape = RoundedCornerShape(24.dp)
    Canvas(
        modifier
            .clip(shape)
            .border(1.dp, pal.onGround.copy(alpha = 0.28f), shape)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { c, dx ->
                    c.consume()
                    acc -= dx
                    val steps = (acc / gap).toInt()
                    if (steps != 0) {
                        acc -= steps * gap
                        change((((cur + steps * step) % 1440) + 1440) % 1440)
                    }
                }
            },
    ) {
        val cx = size.width / 2
        val n = (size.width / gap).toInt() / 2 + 2
        for (k in -n..n) {
            val v = (((value + k * step) % 1440) + 1440) % 1440
            val x = cx + k * gap
            if (x < 10.dp.toPx() || x > size.width - 10.dp.toPx()) continue
            val hour = v % 60 == 0
            val half = size.height * (if (hour) 0.3f else 0.2f)
            drawLine(pal.onGround.copy(alpha = if (hour) 0.5f else 0.22f), Offset(x, size.height / 2 - half), Offset(x, size.height / 2 + half), strokeWidth = 1.5.dp.toPx())
        }
        val tick = size.height * 0.22f
        drawLine(pal.tiles[0], Offset(cx, 0f), Offset(cx, tick), strokeWidth = 3.dp.toPx())
        drawLine(pal.tiles[0], Offset(cx, size.height - tick), Offset(cx, size.height), strokeWidth = 3.dp.toPx())
    }
}


/** Minutes as text: 45 MIN, 2H, 1H 20M. */
internal fun minText(m: Int): String = when {
    m < 60 -> "$m MIN"
    m % 60 == 0 -> "${m / 60}H"
    else -> "${m / 60}H ${m % 60}M"
}

private fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

/** How far one tap moves a focus length. Up: 1 under 5 minutes, 5 under an hour, then 15. Down: 1 up to 5 minutes, 5 up to an hour, then 15. */
internal fun stepSize(cur: Int, dir: Int): Int = if (dir > 0) (if (cur < 5) 1 else if (cur < 60) 5 else 15) else (if (cur <= 5) 1 else if (cur <= 60) 5 else 15)

/** One nudge of a focus length, never below 1 minute or above 8 hours. */
internal fun stepMinutes(cur: Int, dir: Int): Int = (cur + dir * stepSize(cur, dir)).coerceIn(1, 480)

/** The minus / plus button. Its label is the size of the step it takes now (see [stepSize]); holding it repeats. */
@Composable
private fun RowScope.HoldStep(dir: Int, minutes: Int, setMinutes: (Int) -> Unit) {
    val pal = LocalPalette.current
    val cur by rememberUpdatedState(minutes)
    val set by rememberUpdatedState(setMinutes)
    val scope = rememberCoroutineScope()
    Box(
        Modifier.width(62.dp).fillMaxHeight().pointerInput(Unit) {
            detectTapGestures(onPress = {
                set(stepMinutes(cur, dir))
                val job = scope.launch {
                    delay(400L)
                    while (true) {
                        set(stepMinutes(cur, dir))
                        delay(110L)
                    }
                }
                tryAwaitRelease()
                job.cancel()
            })
        },
        contentAlignment = Alignment.Center,
    ) { BasicText((if (dir > 0) "+" else "-") + stepSize(minutes, dir), style = dispStyle(pal.onGround, 26)) }
}

/** A horizontal ruler for a length of time. Drag left for longer, right for shorter. Stops at both ends. */
@Composable
internal fun DurationRuler(value: Int, onChange: (Int) -> Unit, modifier: Modifier, lo: Int = 1, hi: Int = 480, step: Int = 1) {
    val pal = LocalPalette.current
    val gap = with(LocalDensity.current) { 10.dp.toPx() }
    var acc by remember { mutableFloatStateOf(0f) }
    val cur by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    Canvas(
        modifier.pointerInput(Unit) {
            detectHorizontalDragGestures { c, dx ->
                c.consume()
                acc -= dx
                val steps = (acc / gap).toInt()
                if (steps != 0) {
                    acc -= steps * gap
                    change((cur + steps * step).coerceIn(lo, hi))
                }
            }
        },
    ) {
        val cx = size.width / 2
        val n = (size.width / gap).toInt() / 2 + 2
        for (k in -n..n) {
            val v = value + k * step
            if (v < lo || v > hi) continue
            val x = cx + k * gap
            if (x < 6.dp.toPx() || x > size.width - 6.dp.toPx()) continue
            val hour = v % 60 == 0
            val half = size.height * (if (hour) 0.34f else if (v % 10 == 0) 0.24f else 0.15f)
            drawLine(pal.onGround.copy(alpha = if (hour) 0.55f else 0.25f), Offset(x, size.height / 2 - half), Offset(x, size.height / 2 + half), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        }
        drawLine(pal.tiles[0], Offset(cx, 4.dp.toPx()), Offset(cx, size.height - 4.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
}

@Composable
internal fun ToggleRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val pal = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = bodyStyle(pal.onGround, 17, medium = true))
            if (sub.isNotEmpty() && sub.length <= 32) BasicText(sub, style = bodyStyle(pal.onGround.copy(alpha = 0.6f), 13), modifier = Modifier.padding(top = 2.dp))
        }
        KToggle(on, onChange)
    }
}

@Composable
internal fun HairLine() {
    val pal = LocalPalette.current
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(1.dp).background(pal.onGround.copy(alpha = 0.1f)))
}

@Composable
private fun Header(state: HomeState) {
    val pal = state.palette
    val today = LocalDate.now()
    val todays = FocusEngine.runs.filter { dateOf(it.start) == today }
    val min = todays.sumOf { it.minutes }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(surfaceColor(0.1f)).clickable { state.exitApp() },
            contentAlignment = Alignment.Center,
        ) { Glyph("back", pal.onGround, 22.dp, 2.4f) }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            BasicText("Focus", style = dispStyle(pal.onGround, 30))
            BasicText(
                "TODAY ${minText(min)} / ${todays.size} SESSION${if (todays.size == 1) "" else "S"}",
                style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10),
            )
        }
    }
}

@Composable
private fun NavPill(state: HomeState, tab: Int, onTab: (Int) -> Unit) {
    val pal = state.palette
    Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.clip(CircleShape).background(surfaceColor(0.1f)).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("timer" to "TIMER", "bag" to "BAGS", "all" to "ROUTINES", "bars" to "STATS").forEachIndexed { i, (g, name) ->
                val on = i == tab
                Row(
                    Modifier.clip(CircleShape).background(if (on) pal.tiles[0] else Color.Transparent).clickable { onTab(i) }
                        .padding(horizontal = if (on) 16.dp else 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Glyph(g, if (on) pal.ink else pal.tiles[0], 24.dp, 2.2f)
                    if (on) BasicText(name, style = monoStyle(pal.ink, 11, bold = true), maxLines = 1)
                }
            }
        }
    }
}

// ---- the Focus app -----------------------------------------------------------------------------

/** True while a Focus bag is being edited full-screen, so the Clock app can hide its own header and tabs. */
internal val focusEditing = androidx.compose.runtime.mutableStateOf(false)

@Composable
private fun SubTabs(tab: Int, onTab: (Int) -> Unit) {
    val pal = LocalPalette.current
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(deepBg()).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("SESSION", "BAGS", "ROUTINES", "STATS").forEachIndexed { i, name ->
            val on = i == tab
            Box(
                Modifier.weight(1f).height(42.dp).clip(CircleShape).background(if (on) surfaceColor(0.12f) else Color.Transparent).clickable { onTab(i) },
                contentAlignment = Alignment.Center,
            ) { BasicText(name, style = monoStyle(if (on) pal.onGround else pal.onGround.copy(alpha = 0.5f), 10, bold = on), maxLines = 1) }
        }
    }
}

@Composable
fun FocusScreen(state: HomeState, embedded: Boolean = false) {
    val pal = state.palette
    var tab by remember { mutableIntStateOf(0) }
    var bags by remember { mutableStateOf(Bags.parse(state.prefs.bags)) }
    var bagIdx by remember { mutableIntStateOf(bags.indexOfFirst { it.id == state.prefs.focusBag }.coerceAtLeast(0)) }
    var minutes by remember { mutableIntStateOf(state.prefs.focusMinutes.coerceIn(1, 480)) }
    var editingBag by remember { mutableStateOf<Bag?>(null) }
    // The tile on the home screen changes these too, so follow the saved values instead of keeping the first read.
    LaunchedEffect(state.prefs.focusMinutes) {
        val v = state.prefs.focusMinutes.coerceIn(1, 480)
        if (v != minutes) minutes = v
    }
    LaunchedEffect(state.prefs.bags) {
        val parsed = Bags.parse(state.prefs.bags)
        if (parsed != bags) bags = parsed
    }
    LaunchedEffect(state.prefs.focusBag, state.prefs.bags) {
        val i = bags.indexOfFirst { it.id == state.prefs.focusBag }
        if (i >= 0 && i != bagIdx) bagIdx = i
    }

    fun saveBags(list: List<Bag>) {
        bags = list
        state.prefs.bags = Bags.write(list)
    }
    fun pickBag(i: Int) {
        bagIdx = i
        bags.getOrNull(i)?.let { state.prefs.focusBag = it.id }
    }
    fun setMin(m: Int) {
        minutes = m.coerceIn(1, 480)
        state.prefs.focusMinutes = minutes
    }

    val bag = editingBag
    if (embedded) {
        androidx.compose.runtime.DisposableEffect(editingBag != null) {
            focusEditing.value = editingBag != null
            onDispose { focusEditing.value = false }
        }
    }
    if (bag != null) {
        val isNew = bags.none { it.id == bag.id }
        BagEdit(
            state, bag, isNew = isNew, canDelete = !isNew && bags.size > 1,
            onSave = { b ->
                val list = if (isNew) bags + b else bags.map { if (it.id == b.id) b else it }
                saveBags(list)
                if (isNew) pickBag(list.size - 1)
                editingBag = null
            },
            onDelete = {
                val list = bags.filterNot { it.id == bag.id }
                if (list.isNotEmpty()) {
                    saveBags(list)
                    pickBag(bagIdx.coerceIn(0, list.size - 1))
                }
                editingBag = null
            },
            onClose = { editingBag = null },
        )
        return
    }

    val idx = bagIdx.coerceIn(0, (bags.size - 1).coerceAtLeast(0))
    Column(
        if (embedded) Modifier.fillMaxSize()
        else Modifier.fillMaxSize().background(pal.ground).statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp),
    ) {
        if (embedded) SubTabs(tab) { tab = it } else Header(state)
        Spacer(Modifier.height(if (embedded) 10.dp else 0.dp))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (tab == 0 || tab == 1) GuardNotice(state)
            when (tab) {
                0 -> TimerTab(state, bags, idx, { pickBag(it) }, minutes) { setMin(it) }
                1 -> BagsTab(
                    state, bags, idx, { pickBag(it) }, { editingBag = it },
                    { editingBag = Bag(UUID.randomUUID().toString(), "New bag", emptyList()) },
                )
                2 -> RoutinesTab(state, bags)
                else -> StatsTab(state)
            }
            Spacer(Modifier.height(4.dp))
        }
        if (!embedded) NavPill(state, tab) { tab = it }
    }
}

private fun buzz(ctx: android.content.Context) {
    try {
        val v = if (android.os.Build.VERSION.SDK_INT >= 31) ctx.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        v?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 220, 140, 220, 140, 440), -1))
    } catch (e: Exception) {
    }
}

/** The quiet, outlined version of the running page: pure black ground, dim blocks, thin outlines. */
@Composable
private fun DimFocus(left: Long, modifier: Modifier) {
    val line = Color.White.copy(alpha = 0.22f)
    val digit = Color.White.copy(alpha = 0.38f)
    val total = left / 1000L
    Column(
        modifier.background(Color.Black).statusBarsPadding().navigationBarsPadding().padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.fillMaxWidth().height(190.dp).clip(Big).border(1.5.dp, line, Big)) {
            BlockText("%02d:%02d".format(total / 3600, (total % 3600) / 60), digit, Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 48.dp), colon = digit.copy(alpha = 0.6f))
        }
        Box(Modifier.size(200.dp).clip(Big).border(1.5.dp, line, Big), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BlockText("%02d".format(total % 60), digit, Modifier.width(120.dp).height(84.dp))
                BasicText("SEC", style = bodyStyle(digit, 16).copy(letterSpacing = androidx.compose.ui.unit.TextUnit(4f, androidx.compose.ui.unit.TextUnitType.Sp)), modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/** Shown when a session runs its full time: pure black, a big DONE, and the numbers. */
@Composable
internal fun FocusDoneScreen(state: HomeState, done: HomeState.FocusDone) {
    val pal = state.palette
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()
    val runs = FocusEngine.runs
    val today = LocalDate.now()
    val todayMin = runs.filter { Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() == today }.sumOf { it.minutes }.coerceAtLeast(done.minutes)
    val days = runs.map { Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() }.toSet() + today
    var streakN = 0
    var d = today
    while (d in days) { streakN++; d = d.minusDays(1) }
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val interceptBack = remember { MutableInteractionSource() }
    Column(
        Modifier.fillMaxSize().background(Color.Black).clickable(interactionSource = interceptBack, indication = null) { }
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) { BasicText("koma", style = dispStyle(pal.onGround, 32)) }
        Box(Modifier.fillMaxWidth().height(250.dp).clip(Big).background(cardBg())) {
            BlockText("DONE", pal.onGround, Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 58.dp))
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(done.minutes to "MIN · SESSION", todayMin to "MIN · TODAY").forEach { (n, l) ->
                Column(Modifier.weight(1f).fillMaxHeight().clip(Big).background(cardBg()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    BasicText("$n", style = dispStyle(pal.onGround, 56))
                    BasicText(l, style = monoStyle(pal.onGround.copy(alpha = 0.55f), 12), modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(112.dp).clip(Big).background(cardBg()).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText("$streakN", style = dispStyle(pal.tiles[0], 52))
                BasicText("DAY STREAK", style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10))
            }
            Spacer(Modifier.width(18.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                "MTWTFSS".forEachIndexed { i, c ->
                    val day = monday.plusDays(i.toLong())
                    val lit = day in days
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp)).background(if (lit) pal.onGround else pal.onGround.copy(alpha = 0.1f)))
                        BasicText("$c", style = monoStyle(pal.onGround.copy(alpha = if (day == today) 1f else 0.5f), 11, bold = day == today))
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(72.dp).clip(CircleShape).background(pal.onGround).clickable { state.focusDone = null }, contentAlignment = Alignment.Center) {
            Glyph("check", pal.ground, 28.dp, 2.6f)
        }
        Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(cardBg()).clickable {
                    try {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, "I stayed focused for ${done.minutes} min with Koma. ${streakN} day streak.")
                        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                    }
                },
                contentAlignment = Alignment.Center,
            ) { Glyph("share", pal.onGround, 24.dp, 2.2f) }
        }
    }
}

/** Asked before a strict session starts: there is no way out until the timer ends. */
@Composable
internal fun StrictConfirm(state: HomeState, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val pal = state.palette
    Sheet(onDismiss = onCancel) {
        BasicText("Start a strict session?", style = dispStyle(pal.onGround, 24))
        BasicText(
            "Once it starts you can't leave focus or open settings until the timer ends. Only your allowed apps and the phone stay available.",
            style = bodyStyle(pal.onGround.copy(alpha = 0.75f), 15),
        )
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("CANCEL", pal.onGround.copy(alpha = 0.14f), pal.onGround, Modifier.weight(1f), height = 48) { onCancel() }
            Pill("START STRICT", pal.tiles[0], pal.ink, Modifier.weight(1f), height = 48) { onConfirm() }
        }
    }
}

/**
 * Koma can only keep other apps out while its Accessibility guard is switched on. Without it a link from another
 * app opens whatever it likes, so say so, and offer the switch.
 */
@Composable
private fun GuardNotice(state: HomeState) {
    val pal = state.palette
    val on by androidx.compose.runtime.produceState(KomaFocusService.isRunning()) {
        while (true) {
            awaitVisible()
            value = KomaFocusService.isRunning()
            kotlinx.coroutines.delay(1500L)
        }
    }
    if (on) return
    Row(
        Modifier.fillMaxWidth().clip(Mid).background(pal.tiles[0]).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText("Blocking is off", style = bodyStyle(pal.ink, 15, medium = true))
            BasicText("Other apps can still open during focus until Koma is switched on in Accessibility.", style = bodyStyle(pal.ink.copy(alpha = 0.75f), 12))
        }
        Pill("TURN ON", pal.ink, pal.tiles[0], height = 36) {
            try {
                state.appCtx.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
            }
        }
    }
}

// ---- timer -------------------------------------------------------------------------------------

@Composable
internal fun PresetCard(big: String, unit: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Column(
        modifier.clip(Mid).background(if (selected) pal.onGround else cardBg()).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        BasicText(big, style = dispStyle(if (selected) pal.ground else pal.onGround, 36), maxLines = 1)
        if (unit.isNotEmpty()) BasicText(unit, style = monoStyle(if (selected) pal.ground.copy(alpha = 0.7f) else pal.onGround.copy(alpha = 0.55f), 10))
    }
}

@Composable
private fun TimerTab(state: HomeState, bags: List<Bag>, bagIdx: Int, onBag: (Int) -> Unit, minutes: Int, setMinutes: (Int) -> Unit) {
    val pal = state.palette
    val bag = bags.getOrNull(bagIdx)
    val now by rememberNow()
    val strict = state.prefs.quickStrict
    val ends = now.plusMinutes(minutes.toLong()).format(DateTimeFormatter.ofPattern(TimeFmt.pattern(), Locale.getDefault())).uppercase()

    Box(Modifier.fillMaxWidth().height(190.dp).clip(Big).background(cardBg())) {
        BlockText(hhmm(minutes), pal.onGround, Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 52.dp))
        BasicText("SESSION", style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10), modifier = Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 20.dp))
        BasicText("ENDS $ends", style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10), modifier = Modifier.align(Alignment.TopEnd).padding(end = 24.dp, top = 20.dp))
        BasicText(minText(minutes), style = monoStyle(pal.onGround.copy(alpha = 0.55f), 11), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp))
    }

    val presets = listOf(Triple(15, "15", "MIN"), Triple(25, "25", "MIN"), Triple(45, "45", "MIN"), Triple(60, "1", "HOUR"), Triple(90, "90", "MIN"), Triple(180, "3", "HOURS"))
    presets.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { (m, big, unit) -> PresetCard(big, unit, minutes == m, Modifier.weight(1f).fillMaxHeight()) { setMinutes(m) } }
        }
    }

    Row(Modifier.fillMaxWidth().height(84.dp).clip(Big).background(cardBg()), verticalAlignment = Alignment.CenterVertically) {
        HoldStep(-1, minutes, setMinutes)
        DurationRuler(minutes, setMinutes, Modifier.weight(1f).fillMaxHeight().padding(vertical = 12.dp))
        HoldStep(1, minutes, setMinutes)
    }

    Column(Modifier.fillMaxWidth().clip(Big).background(cardBg()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText("ALLOWED APPS", style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            bags.forEachIndexed { i, b ->
                val on = i == bagIdx
                Box(
                    Modifier.clip(CircleShape).background(if (on) pal.tiles[0] else deepBg()).clickable { onBag(i) }.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    BasicText("${b.name}  ${b.apps.size}", style = bodyStyle(if (on) pal.ink else pal.onGround.copy(alpha = 0.7f), 15, medium = on), maxLines = 1)
                }
            }
        }
    }

    Column(Modifier.fillMaxWidth().clip(Big).background(cardBg())) {
        ToggleRow("Strict mode", "", strict) { state.prefs.quickStrict = it }
    }

    SlideTrack("SLIDE TO START", "play", reverse = false, knobBg = pal.onGround, knobInk = pal.ground) {
        state.startFocusAsk(minutes, bag?.apps ?: emptyList(), state.prefs.quickStrict) { state.goHome() }
    }
}

// ---- bags --------------------------------------------------------------------------------------

/** One pixel-art bag, as drawn in the design: a tall middle block, two side blocks, three squares underneath. */
@Composable
private fun BagArt(color: Color, hole: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = 160f
        val h = 180f
        val s = minOf(size.width / w, size.height / h)
        val ox = (size.width - w * s) / 2f
        val oy = (size.height - h * s) / 2f
        fun r(x: Float, y: Float, rw: Float, rh: Float, col: Color) = drawRect(col, Offset(ox + x * s, oy + y * s), Size(rw * s, rh * s))
        r(0f, 33f, 45f, 97f, color)
        r(53f, 0f, 55f, 130f, color)
        r(116f, 33f, 44f, 97f, color)
        r(63f, 12f, 33f, 9f, hole)
        r(20f, 147f, 32f, 32f, color)
        r(64f, 147f, 32f, 32f, color)
        r(108f, 147f, 32f, 32f, color)
    }
}

@Composable
private fun BagsTab(state: HomeState, bags: List<Bag>, sel: Int, onSel: (Int) -> Unit, onEdit: (Bag) -> Unit, onNew: () -> Unit) {
    val pal = state.palette
    val bag = bags.getOrNull(sel)
    Row(
        Modifier.fillMaxWidth().height(160.dp).clip(Big).background(cardBg()).padding(horizontal = 22.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BagArt(pal.tiles[0], cardBg(), Modifier.width(96.dp).fillMaxHeight())
        Column(Modifier.weight(1f).padding(start = 22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText("USED FOR THE TIMER", style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10))
            BasicText(bag?.name ?: "No bag", style = dispStyle(pal.onGround, 30), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText("${bag?.apps?.size ?: 0} apps allowed", style = bodyStyle(pal.onGround.copy(alpha = 0.6f), 15))
        }
    }
    BasicText("YOUR BAGS", style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10), modifier = Modifier.padding(start = 6.dp, top = 6.dp))
    bags.forEachIndexed { i, b ->
        val on = i == sel
        val apps = state.apps.filter { it.pkg in b.apps }
        Row(
            Modifier.fillMaxWidth().clip(Mid).background(if (on) surfaceColor(0.12f) else cardBg())
                .then(if (on) Modifier.border(2.dp, pal.tiles[0], Mid) else Modifier)
                .clickable { onSel(i) }.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(if (on) pal.tiles[0] else Color.Transparent)
                    .then(if (on) Modifier else Modifier.border(2.dp, pal.onGround.copy(alpha = 0.4f), CircleShape)),
                contentAlignment = Alignment.Center,
            ) { if (on) Glyph("check", pal.ink, 14.dp, 3.2f) }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(b.name, style = bodyStyle(pal.onGround, 18, medium = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (apps.isEmpty()) {
                    BasicText("No apps yet", style = bodyStyle(pal.onGround.copy(alpha = 0.5f), 13))
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        apps.take(6).forEachIndexed { k, a -> AppIcon(state.icons, a, 28.dp, bg = pal.tiles[k % pal.tiles.size], ink = pal.ink, line = pal.onGround) }
                        if (apps.size > 6) BasicText("+${apps.size - 6}", style = monoStyle(pal.onGround.copy(alpha = 0.7f), 11, bold = true))
                    }
                }
            }
            Pill("EDIT", pal.onGround.copy(alpha = 0.14f), pal.onGround, height = 36) { onEdit(b) }
        }
    }
    Pill("+ NEW BAG", pal.tiles[0], pal.ink, Modifier.fillMaxWidth(), height = 52, onClick = onNew)
}

@Composable
private fun BagEdit(state: HomeState, bag: Bag, isNew: Boolean, canDelete: Boolean, onSave: (Bag) -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    val pal = state.palette
    var name by remember(bag.id) { mutableStateOf(bag.name) }
    var sel by remember(bag.id) { mutableStateOf(bag.apps.toSet()) }
    Page(
        if (isNew) "New bag" else "Bag", "FOCUS / BAGS", onBack = onClose, scroll = false,
        actions = {
            if (canDelete) {
                Pill("DELETE", pal.onGround.copy(alpha = 0.14f), pal.onGround) { onDelete() }
                Spacer(Modifier.width(6.dp))
            }
            Pill("SAVE", pal.tiles[0], pal.ink) { onSave(bag.copy(name = name.ifBlank { "Bag" }, apps = sel.toList())) }
        },
    ) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(surfaceColor()).padding(16.dp)) {
            Field(name, { name = it.take(16) }, "Bag name", Modifier.fillMaxWidth())
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(surfaceColor()).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph("phone", pal.tiles[0], 20.dp, 2.2f)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                BasicText("Phone", style = bodyStyle(pal.onGround, 16, medium = true))
                BasicText("Always allowed, in every bag", style = bodyStyle(pal.onGround.copy(alpha = 0.6f), 12))
            }
            BasicText("${sel.size} / 3", style = monoStyle(pal.onGround, 13, bold = true))
        }
        AppChecklist(state, sel, { sel = it }, Modifier.weight(1f), max = 3)
    }
}

// ---- routines ----------------------------------------------------------------------------------

/** "TODAY 21:00", "TUE 21:00": when the routine next starts, or null if it is off. */
private fun nextRun(p: FocusPlan): String? {
    if (!p.enabled) return null
    val now = LocalDateTime.now()
    val locale = Locale.getDefault()
    for (d in 0..7) {
        val day = LocalDate.now().plusDays(d.toLong())
        val bit = 1 shl (day.dayOfWeek.value - 1)
        if (p.kind == 2 && (p.days and bit) == 0) continue
        if (day.atTime(p.startMin / 60, p.startMin % 60).isAfter(now)) {
            val label = when (d) { 0 -> "TODAY"; 1 -> "TOMORROW"; else -> day.dayOfWeek.getDisplayName(DayStyle.SHORT, locale).uppercase(locale) }
            return "$label ${hhmm(p.startMin)}"
        }
    }
    return null
}

@Composable
private fun RoutinesTab(state: HomeState, bags: List<Bag>) {
    val pal = state.palette
    var sel by remember { mutableIntStateOf(0) }
    val plans = state.focusPlans
    val blanks = remember { List(3) { FocusPlan("routine-$it", "Routine ${it + 1}", emptyList(), 1, 127, 21 * 60, 60, 0L, false, false) } }
    fun planAt(i: Int) = plans.getOrNull(i) ?: blanks[i]
    fun edit(i: Int, f: (FocusPlan) -> FocusPlan) {
        for (k in plans.size until i) state.savePlan(blanks[k])
        val base = state.focusPlans.getOrNull(i)
        val start = base ?: blanks[i].copy(allowed = bags.firstOrNull()?.apps ?: emptyList())
        state.savePlan(f(start))
    }
    val p = planAt(sel)
    val days = if (p.kind == 2) p.days else 127
    val end = (p.startMin + p.durationMin) % 1440
    val digit = if (p.enabled) pal.onGround else pal.onGround.copy(alpha = 0.4f)

    Column(Modifier.fillMaxWidth().height(173.dp).clip(Big).background(cardBg()).padding(horizontal = 20.dp, vertical = 22.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            BlockText(hhmm(p.startMin), digit, Modifier.weight(1f).fillMaxHeight())
            BlockText(hhmm(end), digit, Modifier.weight(1f).fillMaxHeight())
        }
        Spacer(Modifier.height(14.dp))
        // 48 squares, one per half hour. The routine's span is lit.
        Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 0 until 48) {
                val m = i * 30
                val lit = if (p.durationMin >= 1440) true else {
                    val rel = ((m - p.startMin) % 1440 + 1440) % 1440
                    rel < p.durationMin
                }
                Box(Modifier.weight(1f).fillMaxHeight().background(if (lit && p.enabled) pal.tiles[0] else if (lit) pal.tiles[0].copy(alpha = 0.4f) else pal.onGround.copy(alpha = 0.18f)))
            }
        }
    }
    Row(Modifier.fillMaxWidth().height(108.dp).clip(Big).background(deepBg()).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 0 until 3) {
            val pi = planAt(i)
            val on = i == sel
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(26.dp)).background(if (on) surfaceColor(0.1f) else Color.Transparent).clickable { sel = i },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                BasicText("ROUTINE ${i + 1}", style = monoStyle(pal.onGround.copy(alpha = if (on) 0.9f else 0.5f), 10))
                BasicText(hhmm(pi.startMin), style = bodyStyle(pal.onGround.copy(alpha = if (on) 1f else 0.5f), 26), modifier = Modifier.padding(vertical = 6.dp))
                Box(Modifier.width(42.dp).height(3.dp).clip(CircleShape).background(if (pi.enabled) pal.tiles[0] else pal.onGround.copy(alpha = 0.25f)))
            }
        }
    }
    Column(Modifier.fillMaxWidth().clip(Big).background(surfaceColor(0.05f)).padding(vertical = 6.dp)) {
        ToggleRow("Enabled", nextRun(p)?.let { "Next: $it" } ?: if (p.enabled) "No run in the next week" else "Off", p.enabled) { v -> edit(sel) { it.copy(enabled = v) } }
        HairLine()
        ToggleRow("Strict", "", p.strict) { v -> edit(sel) { it.copy(strict = v) } }
        HairLine()
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { d, l ->
                    val on = days and (1 shl d) != 0
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(if (on) pal.tiles[0] else deepBg()).clickable {
                            val next = days xor (1 shl d)
                            if (next != 0) edit(sel) { it.copy(days = next, kind = if (next == 127) 1 else 2) }
                        },
                        contentAlignment = Alignment.Center,
                    ) { BasicText(l, style = bodyStyle(if (on) pal.ink else pal.onGround.copy(alpha = 0.6f), 17, medium = true)) }
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(if (days == 127) "Daily" else "Chosen days", style = bodyStyle(pal.onGround.copy(alpha = 0.55f), 16))
                    BasicText("·", style = bodyStyle(pal.onGround.copy(alpha = 0.55f), 16))
                    BasicText(durText(p.durationMin), style = bodyStyle(pal.tiles[0], 16))
                }
            }
        }
        HairLine()
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText("START  ${hhmm(p.startMin)}", style = monoStyle(pal.onGround, 12, bold = true))
                HRuler(p.startMin, { v -> edit(sel) { it.copy(startMin = v) } }, Modifier.fillMaxWidth().height(64.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText("END  ${hhmm(end)}", style = monoStyle(pal.onGround, 12, bold = true))
                HRuler(end, { v ->
                    edit(sel) {
                        val d = ((v - it.startMin) % 1440 + 1440) % 1440
                        it.copy(durationMin = if (d < 15) 15 else d)
                    }
                }, Modifier.fillMaxWidth().height(64.dp))
            }
        }
        HairLine()
        // Which bag of apps this routine leaves open.
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText("ALLOWED APPS", style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bags.forEach { b ->
                    val on = b.apps.isNotEmpty() && b.apps.toSet() == p.allowed.toSet()
                    Box(
                        Modifier.clip(CircleShape).background(if (on) pal.tiles[0] else deepBg()).clickable { edit(sel) { it.copy(allowed = b.apps) } }
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                    ) { BasicText(b.name, style = bodyStyle(if (on) pal.ink else pal.onGround.copy(alpha = 0.7f), 15, medium = on), maxLines = 1) }
                }
            }
        }
    }
}

// ---- stats -------------------------------------------------------------------------------------

/** One value per slot of the chosen period. now is the slot that is current (or the count if the period is past, -1 if future). */
private class Buckets(val values: List<Float>, val now: Int)

private fun weekStart(ref: LocalDate): LocalDate = ref.minusDays((ref.dayOfWeek.value - 1).toLong())

private fun buckets(runs: List<FocusRun>, mode: Int, ref: LocalDate): Buckets {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    return when (mode) {
        0 -> {
            val start = ref.atStartOfDay(zone).toInstant().toEpochMilli()
            val vals = MutableList(24) { 0f }
            runs.forEach { r ->
                val re = r.start + r.minutes * 60_000L
                for (h in 0 until 24) {
                    val a = start + h * 3_600_000L
                    val ov = minOf(re, a + 3_600_000L) - maxOf(r.start, a)
                    if (ov > 0) vals[h] += ov / 60_000f
                }
            }
            Buckets(vals, if (ref == today) java.time.LocalTime.now().hour else if (ref.isBefore(today)) 24 else -1)
        }
        1 -> {
            val monday = weekStart(ref)
            val vals = MutableList(7) { 0f }
            runs.forEach { r ->
                val i = ChronoUnit.DAYS.between(monday, dateOf(r.start)).toInt()
                if (i in 0..6) vals[i] += r.minutes
            }
            val thisMonday = weekStart(today)
            Buckets(vals, if (monday == thisMonday) (today.dayOfWeek.value - 1) else if (monday.isBefore(thisMonday)) 7 else -1)
        }
        2 -> {
            val ym = YearMonth.from(ref)
            val vals = MutableList(ym.lengthOfMonth()) { 0f }
            runs.forEach { r ->
                val d = dateOf(r.start)
                if (YearMonth.from(d) == ym) vals[d.dayOfMonth - 1] += r.minutes
            }
            val cur = YearMonth.from(today)
            Buckets(vals, if (ym == cur) today.dayOfMonth - 1 else if (ym.isBefore(cur)) vals.size else -1)
        }
        else -> {
            val vals = MutableList(12) { 0f }
            runs.forEach { r ->
                val d = dateOf(r.start)
                if (d.year == ref.year) vals[d.monthValue - 1] += r.minutes
            }
            Buckets(vals, if (ref.year == today.year) today.monthValue - 1 else if (ref.year < today.year) 12 else -1)
        }
    }
}

private fun periodStart(mode: Int, ref: LocalDate): LocalDate = when (mode) {
    0 -> ref
    1 -> weekStart(ref)
    2 -> ref.withDayOfMonth(1)
    else -> ref.withDayOfYear(1)
}

private fun periodEnd(mode: Int, start: LocalDate): LocalDate = when (mode) {
    0 -> start.plusDays(1)
    1 -> start.plusDays(7)
    2 -> start.plusMonths(1)
    else -> start.plusYears(1)
}

/** Long label of one slot, and a short one for the middle of the dial. */
private fun slotLabel(mode: Int, ref: LocalDate, i: Int, locale: Locale, short: Boolean): String = when (mode) {
    0 -> "%02d:00".format(i)
    1 -> weekStart(ref).plusDays(i.toLong()).format(DateTimeFormatter.ofPattern(if (short) "EEE" else "EEEE d MMM", locale)).uppercase(locale)
    2 -> YearMonth.from(ref).atDay(i + 1).format(DateTimeFormatter.ofPattern(if (short) "d MMM" else "EEE d MMM", locale)).uppercase(locale)
    else -> YearMonth.of(ref.year, i + 1).format(DateTimeFormatter.ofPattern(if (short) "MMM" else "MMMM", locale)).uppercase(locale)
}

private fun streak(runs: List<FocusRun>): Int {
    val days = runs.map { dateOf(it.start) }.toSet()
    var d = LocalDate.now()
    if (d !in days) d = d.minusDays(1)
    var n = 0
    while (d in days) {
        n++
        d = d.minusDays(1)
    }
    return n
}

/**
 * The dial: a ring with 36 ticks and a dot. Drag the dot round the ring (or tap anywhere on it) to pick a slot of the
 * period. The ticks up to the dot light up, and the middle shows the chosen slot.
 */
@Composable
private fun StatsDial(values: List<Float>, sel: Int, small: String, big: String, onSel: (Int) -> Unit, modifier: Modifier) {
    val pal = LocalPalette.current
    val n = values.size
    var box by remember { mutableStateOf(IntSize.Zero) }
    val pick = { p: Offset ->
        val dx = p.x - box.width / 2f
        val dy = p.y - box.height / 2f
        if (dx != 0f || dy != 0f) {
            var a = atan2(dy.toDouble(), dx.toDouble()) + PI / 2
            if (a < 0) a += 2 * PI
            onSel(((a / (2 * PI)) * n).toInt().coerceIn(0, n - 1))
        }
    }
    val frac = (sel + 0.5f) / n
    Box(
        modifier
            .onSizeChanged { box = it }
            .pointerInput(n) { detectTapGestures { pick(it) } }
            .pointerInput(n) { detectDragGestures(onDragStart = { pick(it) }) { change, _ -> change.consume(); pick(change.position) } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(16.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            val rad = minOf(size.width, size.height) * 0.42f
            val mx = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
            val seg = 360f / n
            val gap = minOf(5f, seg * 0.3f)
            val sw = 14.dp.toPx()
            val tl = Offset(c.x - rad, c.y - rad)
            val sz = Size(rad * 2, rad * 2)
            // The ring: one arc per slot. Brighter means more time, and the chosen one is thicker.
            for (t in 0 until n) {
                val v = values[t]
                val chosen = t == sel
                val col = if (chosen) pal.tiles[0] else if (v > 0f) pal.onGround.copy(alpha = 0.3f + 0.55f * v / mx) else pal.onGround.copy(alpha = 0.1f)
                drawArc(
                    col, -90f + t * seg + gap / 2f, seg - gap, false, tl, sz,
                    style = Stroke(width = if (chosen) sw * 1.5f else sw),
                )
            }
            // The inner ring: how the chosen slot compares with the busiest one.
            val ir = rad * 0.7f
            val itl = Offset(c.x - ir, c.y - ir)
            val isz = Size(ir * 2, ir * 2)
            drawArc(pal.onGround.copy(alpha = 0.1f), 0f, 360f, false, itl, isz, style = Stroke(width = 4.dp.toPx()))
            val frac = (values.getOrElse(sel) { 0f } / mx).coerceIn(0f, 1f)
            if (frac > 0f) drawArc(pal.tiles[0], -90f, 360f * frac, false, itl, isz, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(small, style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10), maxLines = 1)
            BasicText(big, style = dispStyle(pal.onGround, 24), maxLines = 1)
        }
    }
}

/** The chart above the dial; what it draws depends on the period so it never looks empty. */
@Composable
private fun StatsChart(mode: Int, ref: LocalDate, b: Buckets, daily: Map<LocalDate, Float>, sel: Int, onPick: (Int) -> Unit, modifier: Modifier) {
    val pal = LocalPalette.current
    val n = b.values.size
    val accent = pal.tiles[0]
    val ink = pal.onGround
    val first = when (mode) { 2 -> ref.withDayOfMonth(1); 3 -> ref.withDayOfYear(1); else -> ref }
    val lead = if (mode >= 2) first.dayOfWeek.value - 1 else 0
    val days = if (mode == 3) ref.lengthOfYear() else n
    val cols = if (mode == 3) (lead + days + 6) / 7 else 7
    val rows = if (mode == 3) 7 else ((lead + days + 6) / 7).coerceAtLeast(1)
    val max = (b.values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val yearMax = if (mode == 3) (0 until days).maxOf { daily[first.plusDays(it.toLong())] ?: 0f }.coerceAtLeast(1f) else 1f
    fun pick(x: Float, y: Float, w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        when (mode) {
            0, 1 -> onPick((x / w * n).toInt().coerceIn(0, n - 1))
            2 -> {
                val c = (x / w * 7).toInt().coerceIn(0, 6)
                val r = (y / h * rows).toInt().coerceIn(0, rows - 1)
                onPick((r * 7 + c - lead).coerceIn(0, n - 1))
            }
            else -> {
                val c = (x / w * cols).toInt().coerceIn(0, cols - 1)
                val r = (y / h * 7).toInt().coerceIn(0, 6)
                onPick(first.plusDays((c * 7 + r - lead).coerceIn(0, days - 1).toLong()).monthValue - 1)
            }
        }
    }
    if (mode == 2) {
        MonthCal(ref, b, sel, onPick, modifier)
        return
    }
    if (mode == 3) {
        YearGrid(ref, daily, sel, onPick, modifier)
        return
    }
    Column(modifier) {
        Canvas(
            Modifier.fillMaxWidth().weight(1f)
                .pointerInput(mode, n) { detectTapGestures { pick(it.x, it.y, size.width, size.height) } }
                .pointerInput(mode, n) { detectDragGestures { change, _ -> change.consume(); pick(change.position.x, change.position.y, size.width, size.height) } },
        ) {
            val slot = size.width / n
            val gap = if (mode == 1) 8.dp.toPx() else 2.dp.toPx()
            val bw = slot - gap
            val rr = androidx.compose.ui.geometry.CornerRadius(minOf(bw / 2f, 10.dp.toPx()))
            b.values.forEachIndexed { i, v ->
                val x = i * slot + gap / 2f
                drawRoundRect(if (i == sel) accent.copy(alpha = 0.22f) else ink.copy(alpha = 0.07f), Offset(x, 0f), Size(bw, size.height), rr)
                if (v > 0f) {
                    val h = (size.height * (0.14f + 0.86f * v / max)).coerceAtMost(size.height)
                    drawRoundRect(if (i == sel) accent else ink.copy(alpha = if (i <= b.now) 0.85f else 0.5f), Offset(x, size.height - h), Size(bw, h), rr)
                }
            }
        }
        if (mode == 1) {
            val loc = Locale.getDefault()
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                for (i in 0 until 7) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        BasicText(
                            weekStart(ref).plusDays(i.toLong()).dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, loc).uppercase(loc),
                            style = monoStyle(if (i == sel) accent else ink.copy(alpha = 0.5f), 10, bold = i == sel),
                        )
                    }
                }
            }
        }
    }
}

/** A month as a calendar: each day is a cell with its number, filled in by how long you focused. */
@Composable
private fun MonthCal(ref: LocalDate, b: Buckets, sel: Int, onPick: (Int) -> Unit, modifier: Modifier) {
    val pal = LocalPalette.current
    val n = b.values.size
    val lead = ref.withDayOfMonth(1).dayOfWeek.value - 1
    val rows = ((lead + n + 6) / 7).coerceAtLeast(1)
    val max = (b.values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val loc = Locale.getDefault()
    val shape = RoundedCornerShape(12.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until 7) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    BasicText(weekStart(ref).plusDays(i.toLong()).dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, loc).uppercase(loc), style = monoStyle(pal.onGround.copy(alpha = 0.4f), 9))
                }
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c - lead
                    if (idx !in 0 until n) { Spacer(Modifier.weight(1f)); continue }
                    val v = b.values[idx]
                    val strong = v > 0f && v / max > 0.5f
                    val bg = if (v > 0f) pal.tiles[0].copy(alpha = 0.25f + 0.75f * v / max) else pal.onGround.copy(alpha = if (b.now in 0 until idx) 0.04f else 0.08f)
                    Box(
                        Modifier.weight(1f).fillMaxHeight().clip(shape).background(bg)
                            .then(if (idx == sel) Modifier.border(2.dp, pal.onGround, shape) else Modifier)
                            .clickable { onPick(idx) },
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText("${idx + 1}", style = monoStyle(if (strong) pal.ink else pal.onGround.copy(alpha = if (v > 0f) 0.9f else 0.5f), 10, bold = idx == sel), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** A year as twelve rows. Each row is a strip with one cell per day, so a whole year shows at once. */
@Composable
private fun YearGrid(ref: LocalDate, daily: Map<LocalDate, Float>, sel: Int, onPick: (Int) -> Unit, modifier: Modifier) {
    val pal = LocalPalette.current
    val loc = Locale.getDefault()
    val yearMax = (1..ref.lengthOfYear()).maxOf { daily[ref.withDayOfYear(it)] ?: 0f }.coerceAtLeast(1f)
    val shape = RoundedCornerShape(14.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (mi in 0 until 12) {
            val ym = YearMonth.of(ref.year, mi + 1)
            val len = ym.lengthOfMonth()
            val on = mi == sel
            val total = (1..len).sumOf { (daily[ym.atDay(it)] ?: 0f).toDouble() }.toInt()
            Row(
                Modifier.fillMaxWidth().weight(1f).clip(shape)
                    .background(if (on) pal.tiles[0].copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onPick(mi) }.padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    ym.format(DateTimeFormatter.ofPattern("MMM", loc)).uppercase(loc),
                    style = monoStyle(if (on) pal.tiles[0] else pal.onGround.copy(alpha = 0.6f), 10, bold = on), modifier = Modifier.width(38.dp),
                )
                Canvas(Modifier.weight(1f).fillMaxHeight()) {
                    val cw = size.width / 31f
                    val g = 1.5.dp.toPx()
                    val rr = androidx.compose.ui.geometry.CornerRadius(minOf(cw, size.height) / 3f)
                    for (d in 0 until len) {
                        val v = daily[ym.atDay(d + 1)] ?: 0f
                        val col = if (v > 0f) pal.tiles[0].copy(alpha = 0.3f + 0.7f * v / yearMax) else pal.onGround.copy(alpha = if (on) 0.16f else 0.09f)
                        drawRoundRect(col, Offset(d * cw + g / 2f, g / 2f), Size(cw - g, size.height - g), rr)
                    }
                }
                BasicText(
                    if (total == 0) "-" else minText(total),
                    style = monoStyle(if (on) pal.onGround else pal.onGround.copy(alpha = 0.55f), 10, bold = on), modifier = Modifier.width(54.dp).padding(start = 8.dp), maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MiniStat(big: String, small: String, modifier: Modifier) {
    val pal = LocalPalette.current
    Column(modifier.clip(Big).background(cardBg()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        BasicText(big, style = dispStyle(pal.onGround, 26), maxLines = 1)
        BasicText(small, style = monoStyle(pal.onGround.copy(alpha = 0.55f), 9), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun StatsTab(state: HomeState) {
    val pal = state.palette
    var mode by remember { mutableIntStateOf(2) }
    var offset by remember { mutableIntStateOf(0) }
    var picked by remember(mode, offset) { mutableIntStateOf(-1) }
    var cleared by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val ref = when (mode) { 0 -> today.plusDays(offset.toLong()); 1 -> today.plusWeeks(offset.toLong()); 2 -> today.plusMonths(offset.toLong()); else -> today.plusYears(offset.toLong()) }
    val runs = if (cleared >= 0) FocusEngine.runs else emptyList()
    val b = buckets(runs, mode, ref)
    val n = b.values.size
    val total = b.values.sum().roundToInt()
    val max = (b.values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val locale = Locale.getDefault()
    val defaultIdx = when {
        b.now in 0 until n -> b.now
        b.now >= n -> b.values.indices.maxByOrNull { b.values[it] } ?: 0
        else -> 0
    }
    val sel = if (picked in 0 until n) picked else defaultIdx
    val selVal = b.values[sel].roundToInt()

    // The chart: tap or drag along it to choose a slot. Day and week are wide bars, month is a calendar, year is a heat map.
    val daily = remember(runs.size, runs.lastOrNull()?.start) { runs.groupBy { dateOf(it.start) }.mapValues { e -> e.value.sumOf { it.minutes }.toFloat() } }
    StatsChart(mode, ref, b, daily, sel, { picked = it }, Modifier.fillMaxWidth().height(when (mode) { 3 -> 330.dp; 2 -> 262.dp; else -> 173.dp }).clip(Big).background(cardBg()).padding(horizontal = 16.dp, vertical = 16.dp))
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BasicText("${slotLabel(mode, ref, sel, locale, short = false)}   ·   ${if (selVal == 0) "NONE" else minText(selVal)}", style = bodyStyle(pal.onGround, 17), maxLines = 1)
        BasicText("PERIOD TOTAL ${minText(total)}", style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10))
    }
    val navTitle = when (mode) {
        0 -> ref.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale)).uppercase(locale)
        1 -> {
            val s = weekStart(ref)
            s.format(DateTimeFormatter.ofPattern("d MMM", locale)).uppercase(locale) + " - " + s.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM", locale)).uppercase(locale)
        }
        2 -> ref.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)).uppercase(locale)
        else -> ref.year.toString()
    }
    val navShape = RoundedCornerShape(40.dp)
    Row(Modifier.fillMaxWidth().height(70.dp).clip(navShape).border(1.dp, pal.onGround.copy(alpha = 0.28f), navShape)) {
        Box(Modifier.weight(1f).fillMaxHeight().clickable { offset-- }, contentAlignment = Alignment.Center) { Glyph("back", pal.onGround, 26.dp, 2.4f) }
        Box(Modifier.width(1.dp).fillMaxHeight().background(pal.onGround.copy(alpha = 0.28f)))
        Box(Modifier.weight(2.5f).fillMaxHeight().clickable { offset = 0 }, contentAlignment = Alignment.Center) { BasicText(navTitle, style = bodyStyle(pal.onGround, 18, medium = true), maxLines = 1) }
        Box(Modifier.width(1.dp).fillMaxHeight().background(pal.onGround.copy(alpha = 0.28f)))
        Box(Modifier.weight(1f).fillMaxHeight().clickable { if (offset < 0) offset++ }, contentAlignment = Alignment.Center) {
            Glyph("chev", pal.onGround.copy(alpha = if (offset < 0) 1f else 0.3f), 26.dp, 2.4f)
        }
    }
    Row(Modifier.fillMaxWidth().height(252.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.width(90.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Day", "Week", "Month", "Year").forEachIndexed { i, l ->
                val on = i == mode
                Box(
                    Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(if (on) pal.onGround else cardBg()).clickable { mode = i; offset = 0 },
                    contentAlignment = Alignment.Center,
                ) { BasicText(l, style = bodyStyle(if (on) pal.ground else pal.onGround.copy(alpha = 0.85f), 18, medium = true)) }
            }
        }
        StatsDial(
            b.values, sel, slotLabel(mode, ref, sel, locale, short = true),
            if (selVal == 0) "0" else minText(selVal), { picked = it },
            Modifier.weight(1f).fillMaxHeight().clip(Big).background(surfaceColor(0.05f)),
        )
    }

    val start = periodStart(mode, ref)
    val end = periodEnd(mode, start)
    val inPeriod = runs.filter { dateOf(it.start) >= start && dateOf(it.start) < end }
    val active = b.values.count { it > 0f }
    Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MiniStat("${streak(runs)}", "DAY STREAK", Modifier.weight(1f).fillMaxHeight())
        MiniStat("${inPeriod.size}", "SESSIONS", Modifier.weight(1f).fillMaxHeight())
        MiniStat(if (active == 0) "-" else minText(total / active), "AVG / ACTIVE", Modifier.weight(1f).fillMaxHeight())
    }

    val recent = runs.takeLast(6).reversed()
    if (recent.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().clip(Big).background(surfaceColor(0.05f)).padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText("RECENT SESSIONS", style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10))
            recent.forEach { r ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        Instant.ofEpochMilli(r.start).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE d MMM  " + TimeFmt.pattern(), locale)).uppercase(locale),
                        style = monoStyle(pal.onGround.copy(alpha = 0.8f), 11), modifier = Modifier.weight(1f),
                    )
                    BasicText(minText(r.minutes), style = bodyStyle(pal.onGround, 16, medium = true))
                }
            }
        }
    }
    if (runs.isNotEmpty()) {
        Box(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp))
                .background(if (confirmClear) pal.tiles[0] else surfaceColor(0.08f))
                .clickable {
                    if (confirmClear) {
                        FocusEngine.clearRuns(state.appCtx)
                        cleared++
                        confirmClear = false
                    } else confirmClear = true
                },
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                if (confirmClear) "TAP AGAIN TO CLEAR ALL STATS" else "CLEAR PAST STATS",
                style = monoStyle(if (confirmClear) pal.ink else pal.onGround.copy(alpha = 0.8f), 11, bold = true),
            )
        }
    }
}

// ---- while focus is on -------------------------------------------------------------------------

/** Shown instead of the home screen while a focus session is running. */
@Composable
fun FocusOnScreen(state: HomeState) {
    val pal = state.palette
    val ctx = LocalContext.current
    val active = state.focusActive ?: return
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            awaitVisible()
            value = System.currentTimeMillis()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    LaunchedEffect(now / 1000L) {
        if (now >= active.until) state.refreshFocus()
    }
    val left = (active.until - now).coerceAtLeast(0L)
    // A few buzzes just before it ends.
    var buzzed by remember(active.until) { mutableStateOf(false) }
    LaunchedEffect(left / 1000L) {
        if (!buzzed && left in 1L..4000L) {
            buzzed = true
            buzz(ctx)
        }
    }
    // After a quiet half minute the page turns black and outlined, so an OLED screen sits almost dark. A touch wakes it.
    var lastTouch by remember { mutableStateOf(System.currentTimeMillis()) }
    val dim = now - lastTouch > 30_000L && left > 4000L
    if (dim) {
        DimFocus(left, Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope { while (true) { awaitPointerEvent(); lastTouch = System.currentTimeMillis() } }
        })
        return
    }
    val totalSec = left / 1000L
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val sec = totalSec % 60
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { Ambient.stop() } }
    var extended by remember { mutableIntStateOf(0) }
    val allowedApps = state.apps.filter { it.pkg in active.allowed }
    val canExtend = !active.strict && FocusEngine.isSession(now)

    Column(
        Modifier.fillMaxSize().background(pal.ground).statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp)
            .pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial); lastTouch = System.currentTimeMillis() } } },
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) { BasicText("koma", style = dispStyle(pal.onGround, 32)) }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GuardNotice(state)
            Box(Modifier.fillMaxWidth().height(215.dp).clip(Big).background(cardBg())) {
                BlockText("%02d:%02d".format(h, m), pal.onGround, Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 52.dp))
                BasicText(active.name.uppercase(), style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10), modifier = Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 20.dp))
                val span = active.until - active.start
                val frac = if (active.start > 0L && span > 0L) (1f - left.toFloat() / span).coerceIn(0f, 1f) else 0f
                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp).height(8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (i in 0 until 40) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(if (i < (frac * 40).toInt()) pal.tiles[0] else pal.onGround.copy(alpha = 0.18f)))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(245.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1.45f).fillMaxHeight().clip(Big).background(cardBg()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    BlockText("%02d".format(sec), pal.onGround, Modifier.fillMaxWidth().height(110.dp).padding(horizontal = 40.dp), colon = pal.onGround)
                    BasicText("SEC", style = bodyStyle(pal.onGround.copy(alpha = 0.55f), 20).copy(letterSpacing = androidx.compose.ui.unit.TextUnit(4f, androidx.compose.ui.unit.TextUnitType.Sp)), modifier = Modifier.padding(top = 10.dp))
                }
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(Big).background(cardBg()).then(if (canExtend) Modifier.clickable { state.extendFocus(15); extended++ } else Modifier),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    BasicText("+15", style = dispStyle(if (canExtend) pal.onGround else pal.onGround.copy(alpha = 0.35f), 46))
                    BasicText("min", style = bodyStyle(pal.onGround.copy(alpha = 0.55f), 20))
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(4) { i -> Box(Modifier.size(14.dp).background(if (i < extended) pal.tiles[0] else pal.onGround.copy(alpha = 0.18f))) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(124.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1.45f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().weight(1f).clip(CircleShape).background(cardBg()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        BasicText("ENDS", style = monoStyle(pal.onGround.copy(alpha = 0.55f), 13))
                        Spacer(Modifier.width(14.dp))
                        BasicText(endsAt(active.until), style = bodyStyle(pal.onGround, 24, medium = true), maxLines = 1)
                    }
                    Row(Modifier.fillMaxWidth().weight(1f).clip(CircleShape).background(deepBg()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(pal.onGround.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) { Glyph("lock", pal.ground, 22.dp, 2.2f) }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            BasicText(if (active.strict) "Strict" else "Normal", style = bodyStyle(pal.onGround.copy(alpha = 0.9f), 20), maxLines = 1)
                            BasicText(if (active.strict) "No exit" else "Can exit", style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10), maxLines = 1)
                        }
                    }
                }
                AmbientCard(Modifier.weight(1f).fillMaxHeight())
            }
            if (active.strict) {
                SlideTrack("STRICT / LOCKED UNTIL ${endsAt(active.until)}", "lock", reverse = true, enabled = false, knobBg = pal.onGround.copy(alpha = 0.4f), knobInk = pal.ground) { }
            } else {
                SlideTrack("SLIDE TO EXIT", "arrow", reverse = true, knobBg = pal.onGround.copy(alpha = 0.55f), knobInk = pal.ground) { state.endFocus() }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            // Centred while the apps fit; scrolls once there are too many.
            Box(Modifier.weight(1f).height(76.dp).clip(CircleShape).background(cardBg()), contentAlignment = Alignment.Center) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    allowedApps.forEach { a ->
                        Box(Modifier.clickable { state.launch(a.key) }) { AppIcon(state.icons, a, 50.dp, bg = pal.tiles[0], ink = pal.ink, line = pal.onGround) }
                    }
                    if (allowedApps.isEmpty()) BasicText("No apps in this bag", style = bodyStyle(pal.onGround.copy(alpha = 0.5f), 14))
                }
            }
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(surfaceColor(0.12f)).clickable {
                    try { ctx.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                },
                contentAlignment = Alignment.Center,
            ) { Glyph("phone", pal.onGround, 30.dp, 2.2f, filled = true) }
        }
    }
}

/** Background sounds: swipe sideways to choose, tap to play, tap again to stop. */
@Composable
private fun AmbientCard(modifier: Modifier) {
    val pal = LocalPalette.current
    var idx by remember { mutableIntStateOf(0) }
    val on = Ambient.playing >= 0
    val fg = if (on) pal.ink else pal.onGround
    val count = Ambient.names.size
    Column(
        modifier
            .clip(Big)
            .background(if (on) pal.tiles[0] else cardBg())
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (kotlin.math.abs(total) > 40f) {
                            idx = (idx + (if (total < 0) 1 else count - 1)) % count
                            if (Ambient.playing >= 0) Ambient.play(idx)
                        }
                    },
                    onDragCancel = { },
                ) { change, d ->
                    change.consume()
                    total += d
                }
            }
            .clickable { if (Ambient.playing >= 0) Ambient.stop() else Ambient.play(idx) },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(4) { Box(Modifier.size(7.dp).background(fg.copy(alpha = if (on) 0.9f else 0.35f))) }
        }
        BasicText(Ambient.names[idx], style = bodyStyle(fg.copy(alpha = if (on) 1f else 0.6f), 20, medium = true).copy(letterSpacing = androidx.compose.ui.unit.TextUnit(4f, androidx.compose.ui.unit.TextUnitType.Sp)), modifier = Modifier.padding(vertical = 14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(count) { i -> Box(Modifier.size(8.dp).background(fg.copy(alpha = if (i == idx) 1f else 0.3f))) }
        }
    }
}
