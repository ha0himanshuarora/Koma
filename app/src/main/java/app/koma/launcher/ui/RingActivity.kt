package app.koma.launcher.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.koma.launcher.data.RingInfo
import app.koma.launcher.data.RingService
import app.koma.launcher.data.TimeFmt
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * The full screen alarm. It opens over the lock screen and wakes the display; when the phone is in use the system
 * shows the same alarm as a pop-up notification instead.
 */
class RingActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        // The alarm is on screen, so the notification should not pop up over it.
        RingService.send(this, RingService.ACTION_QUIET)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        Fonts.init(this)
        Fonts.setDisplay(getSharedPreferences("koma", MODE_PRIVATE).getString("font", "default") ?: "default")
        setContent {
            CompositionLocalProvider(LocalPalette provides (savedPalette(this) ?: Palettes.AmberSignal), androidx.compose.foundation.LocalIndication provides NoIndication) {
                RingScreen(
                    onAction = { action -> startService(Intent(this, RingService::class.java).setAction(action)) },
                    onClose = { finish() },
                )
            }
        }
    }
}

/** Everything a ring style needs to draw itself. */
private class RingUi(
    val timer: Boolean,
    val info: RingInfo,
    val autoOn: Boolean,
    val frac: Float,
    val autoText: String,
    val clock: String,
    val date: String,
    val now: Long,
    val onAction: (String) -> Unit,
) {
    val title: String get() = if (timer) "Time's up" else info.title.ifBlank { "Alarm" }
    val kindName: String get() = if (timer) "TIMER" else "ALARM"
    val glyph: String get() = if (timer) "hourglass" else "alarm"
    val hasAction: Boolean get() = timer || info.snooze > 0
    val actionTitle: String get() = if (timer) "Restart" else "Snooze"
    val actionSub: String get() = if (timer) "SAME TIME AGAIN" else "${info.snooze} MIN  ·  ${info.left} LEFT"
    val stopTitle: String get() = if (timer) "End" else "Stop"
    fun act() = onAction(if (timer) RingService.ACTION_RESTART else RingService.ACTION_SNOOZE)
    fun stop() = onAction(RingService.ACTION_DISMISS)
}

@Composable
private fun RingScreen(onAction: (String) -> Unit, onClose: () -> Unit) {
    val pal = LocalPalette.current
    val ctx = LocalContext.current
    val info = RingService.current.value
    LaunchedEffect(info == null) { if (info == null) onClose() }
    if (info == null) {
        Box(Modifier.fillMaxSize().background(pal.ground))
        return
    }
    val timer = info.kind == "timer"
    val is24 = TimeFmt.is24Saved(ctx)
    val since = remember(info) { System.currentTimeMillis() }
    val sp = ctx.getSharedPreferences("koma", android.content.Context.MODE_PRIVATE)
    val style = sp.getString("ring_style", "classic") ?: "classic"
    val now by produceState(System.currentTimeMillis(), info) {
        while (true) {
            value = System.currentTimeMillis()
            // Only the classic look animates every frame; the others just count seconds.
            delay(if (style == "classic") 33L else 200L)
        }
    }
    val autoOn = sp.getBoolean(if (timer) "auto_stop_timer" else "auto_snooze", true)
    val elapsed = (now - since).coerceAtLeast(0L)
    val remain = (RingService.AUTO_MS - elapsed).coerceAtLeast(0L)
    val leftSecs = (remain + 999L) / 1000L
    val auto = when {
        timer -> "CLOSES IN"
        info.snooze > 0 -> "SNOOZES IN"
        else -> "STOPS IN"
    }
    val ui = RingUi(
        timer = timer, info = info, autoOn = autoOn,
        frac = remain / RingService.AUTO_MS.toFloat(),
        autoText = "$auto  0:%02d".format(leftSecs),
        clock = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a")).uppercase(),
        date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.getDefault())).uppercase(),
        now = now, onAction = onAction,
    )
    when (style) {
        "quiet" -> QuietRing(ui)
        "tiles" -> TilesRing(ui)
        "split" -> SplitRing(ui)
        "orbit" -> OrbitRing(ui)
        else -> ClassicRing(ui)
    }
}

// ---- shared bits ---------------------------------------------------------------------------------

/** A superellipse: rounder than a rounded square, squarer than a circle. Starts at the top and runs clockwise. */
private fun squirclePath(cx: Float, cy: Float, r: Float, n: Float = 4.6f): Path {
    val p = Path()
    val steps = 96
    val e = 2f / n
    for (i in 0..steps) {
        val t = -Math.PI / 2 + 2 * Math.PI * i / steps
        val c = cos(t).toFloat()
        val s = sin(t).toFloat()
        val x = cx + r * sign(c) * abs(c).pow(e)
        val y = cy + r * sign(s) * abs(s).pow(e)
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

private val SquircleShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(squirclePath(size.width / 2f, size.height / 2f, size.width / 2f))
}

/** The time as one line of display text, sized down as it gets longer so it never wraps. */
@Composable
private fun BigTime(text: String, color: Color, big: Int, mid: Int, small: Int, align: TextAlign = TextAlign.Center, modifier: Modifier = Modifier) {
    val sp = when {
        text.length <= 4 -> big
        text.length <= 5 -> mid
        else -> small
    }
    BasicText(text, modifier = modifier, style = dispStyle(color, sp, -0.05f).copy(textAlign = align), maxLines = 1, softWrap = false)
}

@Composable
private fun StateTitle(ui: RingUi, color: Color, size: Int = 30, align: TextAlign = TextAlign.Center) {
    BasicText(ui.title, style = dispStyle(color, size).copy(textAlign = align), maxLines = 2)
}

@Composable
private fun ProgressBar(frac: Float, track: Color, fill: Color, modifier: Modifier = Modifier) {
    Box(modifier.height(4.dp).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0.001f, 1f)).height(4.dp).clip(CircleShape).background(fill))
    }
}

/** A big button in the page's own colours: a word, and optionally a small line under it. */
@Composable
private fun ActionCard(title: String, sub: String, fill: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(Mid).background(fill).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        BasicText(title, style = dispStyle(fg, 36), maxLines = 1)
        if (sub.isNotEmpty()) BasicText(sub, style = monoStyle(fg.copy(alpha = 0.65f), 10))
    }
}

/** The pill at the top: what is ringing. */
@Composable
private fun KindPill(ui: RingUi, hero: Color, ink: Color, text: Color) {
    Row(
        Modifier.clip(CircleShape).background(cardBg()).padding(start = 5.dp, end = 16.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(hero), contentAlignment = Alignment.Center) {
            Glyph(ui.glyph, ink, 18.dp, 2.2f)
        }
        BasicText(ui.kindName, style = monoStyle(text, 12, bold = true))
    }
}

// ---- Classic: rings round a squircle -------------------------------------------------------------

@Composable
private fun ClassicRing(ui: RingUi) {
    val pal = LocalPalette.current
    val info = ui.info
    val now = ui.now
    val dim = pal.onGround.copy(alpha = 0.6f)
    val hero = pal.tiles[0]
    val second = pal.tiles.getOrElse(1) { pal.onGround }
    val phase = (now % 2400L) / 2400f
    Column(
        Modifier.fillMaxSize().background(pal.ground).statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            KindPill(ui, hero, pal.ink, pal.onGround)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                BasicText(ui.clock, style = monoStyle(pal.onGround, 15, bold = true))
                if (!ui.timer) BasicText(ui.date, style = monoStyle(dim, 10, bold = true))
            }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            // Squircles spreading out from the icon; the outline is the minute left before it answers by itself.
            Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = center
                    val max = size.minDimension / 2f
                    for (k in 0 until 3) {
                        val f = (phase + k / 3f) % 1f
                        drawPath(squirclePath(c.x, c.y, max * (0.45f + 0.55f * f)), hero.copy(alpha = (1f - f) * 0.35f))
                    }
                    if (ui.autoOn) {
                        val r = max * 0.56f
                        val sw = 5.dp.toPx()
                        val outline = squirclePath(c.x, c.y, r)
                        drawPath(outline, hero.copy(alpha = 0.16f), style = Stroke(sw))
                        val pm = PathMeasure()
                        pm.setPath(outline, false)
                        val seg = Path()
                        pm.getSegment(0f, pm.length * ui.frac, seg, true)
                        drawPath(seg, hero, style = Stroke(sw, cap = StrokeCap.Round))
                    }
                }
                val shake = Math.sin((now / 55.0)).toFloat() * (if ((now / 1000L) % 2L == 0L) 9f else 0f)
                Box(
                    Modifier.size(96.dp).graphicsLayer { rotationZ = shake }.clip(SquircleShape).background(hero),
                    contentAlignment = Alignment.Center,
                ) { Glyph(ui.glyph, pal.ink, 46.dp, 2.4f) }
            }
            Spacer(Modifier.height(20.dp))
            BlockText(info.time, pal.onGround, Modifier.fillMaxWidth(if (info.time.length > 5) 0.9f else 0.78f).height(84.dp))
            if (info.suffix.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                BasicText(info.suffix, style = monoStyle(dim, 12, bold = true))
            }
            Spacer(Modifier.height(18.dp))
            StateTitle(ui, pal.onGround, 34)
            if (ui.autoOn) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.clip(CircleShape).background(cardBg()).padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(hero))
                    BasicText(ui.autoText, style = monoStyle(dim, 11, bold = true))
                }
            }
        }
        if (ui.hasAction) {
            ActionCard(ui.actionTitle.uppercase(), ui.actionSub, second, pal.ink, Modifier.fillMaxWidth().height(78.dp)) { ui.act() }
        } else {
            Box(Modifier.fillMaxWidth().clip(Mid).background(cardBg()).padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                BasicText("NO SNOOZES LEFT", style = monoStyle(dim, 11, bold = true))
            }
        }
        SlideTrack(if (ui.timer) "SLIDE TO END" else "SLIDE TO STOP", "check", reverse = false, knobBg = pal.onGround, knobInk = pal.ground) { ui.stop() }
    }
}

// ---- Quiet: the time, one line, one slide --------------------------------------------------------

@Composable
private fun QuietRing(ui: RingUi) {
    val pal = LocalPalette.current
    val dim = pal.onGround.copy(alpha = 0.6f)
    val hero = pal.tiles[0]
    val second = pal.tiles.getOrElse(1) { pal.onGround }
    Column(
        Modifier.fillMaxSize().background(pal.ground)
            // A soft glow of the theme colours behind the time: faint, so the time stays the focus.
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(hero.copy(alpha = 0.22f), second.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width * 0.3f, size.height * 0.34f),
                        radius = size.width * 1.05f,
                    ),
                )
            }
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 26.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(hero))
            BasicText(if (ui.timer) "TIMER  /  ${ui.clock}" else "ALARM  /  ${ui.date}", style = monoStyle(dim, 12, bold = true))
        }
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigTime(ui.info.time, pal.onGround, 140, 112, 78, TextAlign.Start)
                if (ui.info.suffix.isNotBlank() && ui.info.suffix.length <= 3) {
                    BasicText(ui.info.suffix, modifier = Modifier.padding(top = 14.dp), style = monoStyle(dim, 12, bold = true))
                }
            }
            if (ui.info.suffix.isNotBlank() && ui.info.suffix.length > 3) {
                Spacer(Modifier.height(6.dp))
                BasicText(ui.info.suffix, style = monoStyle(dim, 12, bold = true))
            }
            Spacer(Modifier.height(22.dp))
            StateTitle(ui, pal.onGround, 28, TextAlign.Start)
            if (ui.autoOn) {
                Spacer(Modifier.height(30.dp))
                ProgressBar(ui.frac, cardBg(), hero, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                BasicText(ui.autoText, style = monoStyle(dim, 11, bold = true))
            }
        }
        if (ui.hasAction) {
            // Two halves: the word on a solid block, the detail as a big number beside it.
            val shape = RoundedCornerShape(22.dp)
            Row(Modifier.fillMaxWidth().height(88.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(
                    Modifier.weight(1.25f).fillMaxSize().clip(shape).background(second).clickable { ui.act() }.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    BasicText(if (ui.timer) "AGAIN" else "NOT YET", style = monoStyle(pal.ink.copy(alpha = 0.65f), 10, bold = true))
                    BasicText(ui.actionTitle, style = dispStyle(pal.ink, 28), maxLines = 1)
                }
                Column(
                    Modifier.weight(1f).fillMaxSize().clip(shape).background(cardBg()).clickable { ui.act() }.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (ui.timer) {
                        BasicText("SAME TIME", style = monoStyle(dim, 10, bold = true), maxLines = 1)
                        BasicText(ui.info.time, style = dispStyle(pal.onGround, if (ui.info.time.length <= 5) 28 else 22, -0.04f), maxLines = 1, softWrap = false)
                    } else {
                        BasicText("${ui.info.left} LEFT", style = monoStyle(dim, 10, bold = true), maxLines = 1)
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BasicText("${ui.info.snooze}", style = dispStyle(pal.onGround, 34, -0.04f), maxLines = 1)
                            BasicText("MIN", style = monoStyle(dim, 12, bold = true), modifier = Modifier.padding(bottom = 5.dp))
                        }
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                BasicText("NO SNOOZES LEFT", style = monoStyle(dim, 11, bold = true))
            }
        }
        Spacer(Modifier.height(12.dp))
        SlideTrack(if (ui.timer) "SLIDE TO END" else "SLIDE TO STOP", "check", reverse = false, knobBg = pal.onGround, knobInk = pal.ground) { ui.stop() }
    }
}

// ---- Tiles: Koma's own tile look -----------------------------------------------------------------

@Composable
private fun TilesRing(ui: RingUi) {
    val pal = LocalPalette.current
    val dim = pal.onGround.copy(alpha = 0.6f)
    val hero = pal.tiles[0]
    val second = pal.tiles.getOrElse(1) { pal.onGround }
    val big = RoundedCornerShape(26.dp)
    Column(
        Modifier.fillMaxSize().background(pal.ground).statusBarsPadding().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(ui.kindName, style = monoStyle(pal.onGround, 12, bold = true))
            Spacer(Modifier.weight(1f))
            BasicText(if (ui.timer) ui.clock else ui.date, style = monoStyle(dim, 11, bold = true))
        }
        Box(Modifier.fillMaxWidth().weight(1f).clip(big).background(hero)) {
            Box(Modifier.padding(22.dp)) { Glyph(ui.glyph, pal.ink, 38.dp, 2.2f) }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 22.dp, end = 22.dp, bottom = 26.dp)) {
                BigTime(ui.info.time, pal.ink, 124, 100, 70, TextAlign.Start)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    BasicText(ui.info.suffix, style = monoStyle(pal.ink.copy(alpha = 0.75f), 12, bold = true))
                    Spacer(Modifier.weight(1f))
                    if (ui.autoOn) BasicText(ui.autoText, style = monoStyle(pal.ink.copy(alpha = 0.75f), 11, bold = true))
                }
            }
            if (ui.autoOn) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(ui.frac.coerceIn(0.001f, 1f)).height(6.dp).background(pal.ink))
        }
        Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier.weight(1f).fillMaxSize().clip(big).background(pal.onGround).padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                BasicText(if (ui.timer) "STATE" else "LABEL", style = monoStyle(pal.ground.copy(alpha = 0.65f), 11, bold = true))
                StateTitle(ui, pal.ground, 24, TextAlign.Start)
            }
            if (ui.hasAction) {
                Column(
                    Modifier.weight(1f).fillMaxSize().clip(big).background(second).clickable { ui.act() }.padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    BasicText(ui.actionSub, style = monoStyle(pal.ink.copy(alpha = 0.65f), 11, bold = true), maxLines = 2)
                    BasicText(ui.actionTitle, style = dispStyle(pal.ink, 26), maxLines = 1)
                }
            } else {
                Box(Modifier.weight(1f).fillMaxSize().clip(big).background(cardBg()).padding(16.dp), contentAlignment = Alignment.Center) {
                    BasicText("NO SNOOZES LEFT", style = monoStyle(dim, 11, bold = true), maxLines = 2)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(88.dp).clip(big).border(2.dp, pal.onGround, big).clickable { ui.stop() }.padding(horizontal = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(ui.stopTitle, style = dispStyle(pal.onGround, 30), maxLines = 1)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(56.dp).clip(CircleShape).background(pal.onGround), contentAlignment = Alignment.Center) {
                Glyph("check", pal.ground, 26.dp, 2.4f)
            }
        }
    }
}

// ---- Split: two huge blocks ----------------------------------------------------------------------

@Composable
private fun SplitRing(ui: RingUi) {
    val pal = LocalPalette.current
    val dim = pal.onGround.copy(alpha = 0.6f)
    val hero = pal.tiles[0]
    val second = pal.tiles.getOrElse(1) { pal.onGround }
    Column(Modifier.fillMaxSize().background(pal.ground)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(ui.kindName, style = monoStyle(pal.onGround, 12, bold = true))
            Spacer(Modifier.weight(1f))
            BasicText(if (ui.timer) ui.clock else ui.date, style = monoStyle(dim, 11, bold = true))
        }
        Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            BigTime(ui.info.time, pal.onGround, 140, 116, 80)
            if (ui.info.suffix.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                BasicText(ui.info.suffix, style = monoStyle(hero, 13, bold = true))
            }
            Spacer(Modifier.height(20.dp))
            StateTitle(ui, pal.onGround, 26)
            if (ui.autoOn) {
                Spacer(Modifier.height(8.dp))
                BasicText(ui.autoText, style = monoStyle(dim, 11, bold = true))
            }
        }
        if (ui.hasAction) {
            Column(
                Modifier.fillMaxWidth().height(150.dp).background(second).clickable { ui.act() }.padding(start = 28.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                BasicText(ui.actionSub, style = monoStyle(pal.ink.copy(alpha = 0.65f), 11, bold = true))
                BasicText(ui.actionTitle, style = dispStyle(pal.ink, 52), maxLines = 1)
            }
        }
        Column(
            Modifier.fillMaxWidth().background(hero).clickable { ui.stop() }.navigationBarsPadding().height(if (ui.hasAction) 150.dp else 240.dp).padding(start = 28.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            BasicText(if (ui.hasAction) "TAP TO SILENCE" else "NO SNOOZES LEFT", style = monoStyle(pal.ink.copy(alpha = 0.65f), 11, bold = true))
            BasicText(ui.stopTitle, style = dispStyle(pal.ink, 52), maxLines = 1)
        }
    }
}

// ---- Orbit: 60 ticks for the minute --------------------------------------------------------------

@Composable
private fun OrbitRing(ui: RingUi) {
    val pal = LocalPalette.current
    val dim = pal.onGround.copy(alpha = 0.6f)
    val hero = pal.tiles[0]
    val second = pal.tiles.getOrElse(1) { pal.onGround }
    val lit = if (ui.autoOn) ceil(ui.frac * 60f).toInt().coerceIn(0, 60) else 60
    val off = pal.onGround.copy(alpha = 0.16f)
    Column(
        Modifier.fillMaxSize().background(pal.ground).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(ui.kindName, style = monoStyle(hero, 12, bold = true))
            Spacer(Modifier.weight(1f))
            BasicText(if (ui.timer) ui.clock else ui.date, style = monoStyle(dim, 11, bold = true))
        }
        Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(310.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val c = center
                    val outer = size.minDimension / 2f
                    for (i in 0 until 60) {
                        val a = Math.toRadians(i * 6.0 - 90.0)
                        val len = (if (i % 5 == 0) 20.dp else 13.dp).toPx()
                        val dx = cos(a).toFloat()
                        val dy = sin(a).toFloat()
                        drawLine(
                            if (i < lit) hero else off,
                            Offset(c.x + dx * (outer - len), c.y + dy * (outer - len)),
                            Offset(c.x + dx * outer, c.y + dy * outer),
                            strokeWidth = 3.5.dp.toPx(), cap = StrokeCap.Round,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val t = ui.info.time
                    BasicText(t, style = dispStyle(pal.onGround, if (t.length <= 4) 76 else if (t.length <= 5) 62 else 44, -0.05f), maxLines = 1, softWrap = false)
                    if (ui.info.suffix.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        BasicText(ui.info.suffix, style = monoStyle(hero, 12, bold = true))
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            StateTitle(ui, pal.onGround, 28)
            if (ui.autoOn) {
                Spacer(Modifier.height(8.dp))
                BasicText(ui.autoText, style = monoStyle(dim, 11, bold = true))
            }
        }
        Row(Modifier.fillMaxWidth().height(68.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui.hasAction) {
                Column(
                    Modifier.weight(1f).fillMaxSize().clip(CircleShape).background(second).clickable { ui.act() },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    BasicText(ui.actionTitle, style = dispStyle(pal.ink, 20), maxLines = 1)
                    BasicText(if (ui.timer) ui.info.time else "${ui.info.snooze} MIN  ·  ${ui.info.left} LEFT", style = monoStyle(pal.ink.copy(alpha = 0.65f), 10, bold = true), maxLines = 1)
                }
            }
            Box(
                Modifier.weight(1f).fillMaxSize().clip(CircleShape).border(2.dp, pal.onGround, CircleShape).clickable { ui.stop() },
                contentAlignment = Alignment.Center,
            ) { BasicText(ui.stopTitle, style = dispStyle(pal.onGround, 20), maxLines = 1) }
        }
    }
}
