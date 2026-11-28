package app.koma.launcher.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A value that runs from 0 to 1 over [ms] and starts again, to drive the little animations in the pictures. */
@Composable
private fun tourPulse(ms: Int = 1600): Float {
    val t = rememberInfiniteTransition(label = "tour")
    val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(ms, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
    return v
}

/** 0 to 1 and back to 0, once per cycle of [tourPulse]. */
private fun pingPong(t: Float): Float = if (t < 0.5f) t * 2f else (1f - t) * 2f

@Composable
private fun Finger(modifier: Modifier = Modifier, press: Float = 0f) {
    val pal = LocalPalette.current
    Box(modifier.size(30.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size((14 + 16 * press).dp).clip(CircleShape).background(pal.ink.copy(alpha = 0.22f * (1f - press))))
        Box(Modifier.size(14.dp).clip(CircleShape).background(pal.ink.copy(alpha = 0.9f)).border(2.dp, pal.ground, CircleShape))
    }
}

@Composable
private fun PhoneFrame(width: Dp = 120.dp, height: Dp = 214.dp, content: @Composable BoxScope.() -> Unit) {
    val pal = LocalPalette.current
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.width(width).height(height).clip(shape).background(pal.ground).border(2.dp, pal.ink.copy(alpha = 0.85f), shape), content = content)
}

@Composable
private fun MiniTile(modifier: Modifier, color: androidx.compose.ui.graphics.Color, content: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier.background(color, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center, content = content)
}

@Composable
private fun Bar(w: Dp, alpha: Float = 0.35f, on: androidx.compose.ui.graphics.Color = LocalPalette.current.onGround) {
    Box(Modifier.width(w).height(5.dp).clip(CircleShape).background(on.copy(alpha = alpha)))
}

/** The home grid, drawn small: a clock, weather, music and notes tile and a row of apps. */
@Composable
private fun MockGrid(selected: Boolean = false, grow: Float = 0f, tap: Float = -1f, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    val c = pal.tiles
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            MiniTile(Modifier.weight(2f).fillMaxHeight(), c[0]) { BasicText("9:41", style = dispStyle(pal.ink, 17)) }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                MiniTile(Modifier.fillMaxSize(), c[1 % c.size]) { Glyph("sun", pal.ink, 16.dp, 1.7f) }
                if (tap >= 0f) Finger(Modifier.align(Alignment.Center).offset(x = 6.dp, y = 8.dp), tap)
            }
        }
        Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.weight(1f + grow * 0.8f).fillMaxHeight()) {
                MiniTile(
                    Modifier.fillMaxSize().then(if (selected) Modifier.border(1.5.dp, pal.onGround, RoundedCornerShape(8.dp)) else Modifier),
                    c[2 % c.size],
                ) { Glyph("music", pal.ink, 16.dp, 1.7f) }
                if (selected) {
                    for (a in listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd)) {
                        Box(Modifier.align(a).offset(x = if (a == Alignment.TopStart || a == Alignment.BottomStart) (-3).dp else 3.dp, y = if (a == Alignment.TopStart || a == Alignment.TopEnd) (-3).dp else 3.dp).size(7.dp).clip(CircleShape).background(pal.onGround))
                    }
                    Finger(Modifier.align(Alignment.CenterEnd).offset(x = 12.dp))
                }
            }
            MiniTile(Modifier.weight(2f).fillMaxHeight(), c[3 % c.size]) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Bar(46.dp, 0.55f, pal.ink); Bar(36.dp, 0.4f, pal.ink); Bar(42.dp, 0.4f, pal.ink)
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(24.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("phone", "cam", "mail", "music").forEachIndexed { i, g ->
                MiniTile(Modifier.weight(1f).fillMaxHeight(), c[(i + 4) % c.size]) { Glyph(g, pal.ink, 11.dp, 1.6f) }
            }
        }
    }
}

@Composable
private fun MockHome(tapping: Boolean) {
    val t = tourPulse(1500)
    PhoneFrame {
        MockGrid(tap = if (tapping) t else -1f, modifier = Modifier.fillMaxSize().padding(8.dp))
    }
}

@Composable
private fun MockEdit() {
    val pal = LocalPalette.current
    val g = pingPong(tourPulse(2400))
    PhoneFrame {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().height(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(pal.onGround.copy(alpha = 0.18f)))
                Box(Modifier.weight(1f)) { Bar(40.dp, 0.5f) }
                Box(Modifier.clip(CircleShape).background(pal.tiles[0]).padding(horizontal = 6.dp, vertical = 2.dp)) { BasicText("DONE", style = monoStyle(pal.ink, 5, bold = true)) }
            }
            MockGrid(selected = true, grow = g)
            Box(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(12.dp)).background(pal.onGround.copy(alpha = 0.12f)).padding(horizontal = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.clip(CircleShape).background(pal.tiles[0]).padding(horizontal = 7.dp, vertical = 3.dp)) { BasicText("+", style = monoStyle(pal.ink, 7, bold = true)) }
                Box(Modifier.clip(CircleShape).background(pal.onGround).padding(horizontal = 6.dp, vertical = 3.dp)) { BasicText("OPTIONS", style = monoStyle(pal.ground, 5, bold = true)) }
            }
        }
    }
}

@Composable
private fun MockDrawer(width: Dp = 120.dp, height: Dp = 214.dp) {
    val pal = LocalPalette.current
    val t = tourPulse(2200)
    val pos = pingPong(t)
    PhoneFrame(width, height) {
        Column(Modifier.fillMaxSize().padding(start = 10.dp, end = 18.dp, top = 12.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(pal.tiles[0]))
                Bar(38.dp, 0.6f)
            }
            repeat(7) { i ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    MiniTile(Modifier.size(17.dp), pal.tiles[i % pal.tiles.size])
                    Bar((26 + (i * 11) % 30).dp, 0.4f)
                }
            }
        }
        // The A to Z rail, with the letter under the finger lit.
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 5.dp).fillMaxHeight(0.7f), verticalArrangement = Arrangement.SpaceBetween) {
            repeat(10) { i ->
                val lit = (pos * 9f).toInt() == i
                Box(Modifier.size(if (lit) 6.dp else 4.dp).clip(CircleShape).background(if (lit) pal.tiles[0] else pal.onGround.copy(alpha = 0.4f)))
            }
        }
        Finger(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (30 + 110 * pos).dp))
    }
}

@Composable
private fun MockSearch(width: Dp = 120.dp, height: Dp = 214.dp) {
    val pal = LocalPalette.current
    val t = tourPulse(2000)
    PhoneFrame(width, height) {
        MockGrid(modifier = Modifier.fillMaxSize().padding(8.dp).alpha(0.3f))
        Column(Modifier.align(Alignment.BottomCenter).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(pal.tiles[1 % pal.tiles.size]).padding(7.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Glyph("calc", pal.ink, 12.dp, 1.6f)
                BasicText("12 x 8 = 96", style = monoStyle(pal.ink, 6, bold = true))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(15.dp).clip(CircleShape).background(pal.tiles[2 % pal.tiles.size]))
                Bar(44.dp, 0.5f)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MiniTile(Modifier.size(15.dp), pal.tiles[3 % pal.tiles.size])
                Bar(34.dp, 0.5f)
            }
            Row(
                Modifier.fillMaxWidth().height(24.dp).clip(CircleShape).background(pal.tiles[4 % pal.tiles.size]).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Glyph("search", pal.ink, 11.dp, 1.8f)
                Bar(30.dp, 0.5f, pal.ink)
            }
        }
        Finger(Modifier.align(Alignment.BottomCenter).offset(y = (-10 - 64 * t).dp).alpha(1f - t * 0.6f))
    }
}

@Composable
private fun MockApps() {
    val pal = LocalPalette.current
    val c = pal.tiles
    val t = pingPong(tourPulse(2600))
    PhoneFrame(width = 132.dp) {
        Box(Modifier.fillMaxSize().padding(10.dp)) {
            listOf(Triple("hourglass", 2, "1h 20m"), Triple("sun", 1, "21°"), Triple("clock", 0, "9:41")).forEachIndexed { idx, (g, ci, big) ->
                val back = 2 - idx
                Column(
                    Modifier.offset(x = (back * 5).dp, y = (back * 22 + t * 3 * back).dp).fillMaxWidth(0.88f).height(104.dp)
                        .clip(RoundedCornerShape(12.dp)).background(c[ci % c.size]).border(1.5.dp, pal.ground, RoundedCornerShape(12.dp)).padding(9.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Glyph(g, pal.ink, 12.dp, 1.7f)
                        Bar(30.dp, 0.55f, pal.ink)
                    }
                    BasicText(big, style = dispStyle(pal.ink, 24))
                    Bar(54.dp, 0.4f, pal.ink)
                }
            }
        }
    }
}

@Composable
private fun MockSettings() {
    val pal = LocalPalette.current
    val c = pal.tiles
    PhoneFrame {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(10.dp)).background(c[0]).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("Koma", style = dispStyle(pal.ink, 14))
            }
            listOf("widget", "palette", "type", "all", "settings").forEachIndexed { i, g ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(pal.onGround.copy(alpha = 0.09f)).padding(5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    MiniTile(Modifier.size(17.dp), c[(i + 1) % c.size]) { Glyph(g, pal.ink, 10.dp, 1.6f) }
                    Bar((30 + (i * 9) % 26).dp, 0.45f)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                c.take(5).forEach { Box(Modifier.size(15.dp).clip(CircleShape).background(it)) }
            }
        }
    }
}

/** The picture for an intro page, drawn from the live theme so it matches the person's own colours. */
@Composable
fun TourArt(kind: String) {
    when (kind) {
        "welcome" -> MockHome(tapping = false)
        "tiles" -> MockHome(tapping = true)
        "edit" -> MockEdit()
        "find" -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MockDrawer(104.dp, 196.dp)
            MockSearch(104.dp, 196.dp)
        }
        "apps" -> MockApps()
        "settings" -> MockSettings()
    }
}
