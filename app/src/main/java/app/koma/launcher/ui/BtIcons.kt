package app.koma.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One device type drawn on a 24 grid. [main] is the body, [strap] extra lines, [detail] marks cut into the body.
 * [line] and [fill] are the same device built from straight, square parts for the Tessera style.
 */
class BtArt(val main: List<String>, val strap: List<String>, val detail: List<String>, val line: List<String>, val fill: List<String>)

private val art: Map<String, BtArt> = mapOf(
    "earbuds" to BtArt(listOf("M7 4.5a3.5 3.5 0 0 1 3.5 3.5c0 1.5-.8 2.6-2 3.2V19a1.5 1.5 0 0 1-3 0v-7.3C4.3 11 3.5 9.6 3.5 8A3.5 3.5 0 0 1 7 4.5z", "M17 4.5a3.5 3.5 0 0 0-3.5 3.5c0 1.5.8 2.6 2 3.2V19a1.5 1.5 0 0 0 3 0v-7.3c1.2-.7 2-2.1 2-3.7A3.5 3.5 0 0 0 17 4.5z"), listOf(), listOf(), listOf("M4.3 7.5a3.2 3.2 0 1 0 6.4 0a3.2 3.2 0 1 0 -6.4 0z", "M7.5 10.7V20", "M13.3 7.5a3.2 3.2 0 1 0 6.4 0a3.2 3.2 0 1 0 -6.4 0z", "M16.5 10.7V20"), listOf()),
    "headphones" to BtArt(listOf("M5 13h0.5a2 2 0 0 1 2 2v4a2 2 0 0 1 -2 2h-0.5a2 2 0 0 1 -2 -2v-4a2 2 0 0 1 2 -2z", "M18.5 13h0.5a2 2 0 0 1 2 2v4a2 2 0 0 1 -2 2h-0.5a2 2 0 0 1 -2 -2v-4a2 2 0 0 1 2 -2z"), listOf("M4 15v-3a8 8 0 0 1 16 0v3"), listOf(), listOf("M4.5 15V12a7.5 7.5 0 0 1 15 0v3", "M3 14h5v7h-5z", "M16 14h5v7h-5z"), listOf()),
    "speaker" to BtArt(listOf("M9.5 3h5a3.5 3.5 0 0 1 3.5 3.5v11a3.5 3.5 0 0 1 -3.5 3.5h-5a3.5 3.5 0 0 1 -3.5 -3.5v-11a3.5 3.5 0 0 1 3.5 -3.5z"), listOf(), listOf("M8.5 14.5a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0z", "M12 7h.01"), listOf("M5 3h14v18h-14z"), listOf("M9 15a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z", "M10.8 6h2.4v2.4h-2.4z")),
    "car" to BtArt(listOf("M5.2 11l1.6-4.2A1.5 1.5 0 0 1 8.2 6h7.6a1.5 1.5 0 0 1 1.4.8L18.8 11", "M5.2 11h13.6a2.2 2.2 0 0 1 2.2 2.2v2.1a2.2 2.2 0 0 1 -2.2 2.2h-13.6a2.2 2.2 0 0 1 -2.2 -2.2v-2.1a2.2 2.2 0 0 1 2.2 -2.2z"), listOf("M6.5 17.5v1.8M17.5 17.5v1.8"), listOf("M7.5 14.2h.01M16.5 14.2h.01"), listOf("M3 17v-5h18v5zM6 12l2-5h8l2 5"), listOf("M5.9 19a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0z", "M14.9 19a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0z")),
    "phone" to BtArt(listOf("M9.5 2.5h5a3 3 0 0 1 3 3v13a3 3 0 0 1 -3 3h-5a3 3 0 0 1 -3 -3v-13a3 3 0 0 1 3 -3z"), listOf(), listOf("M10.5 18.5h3"), listOf("M6 2.5h12v19h-12z"), listOf("M10.8 17.4h2.4v2.4h-2.4z")),
    "watch" to BtArt(listOf("M9.9 6.5h4.2a3.4 3.4 0 0 1 3.4 3.4v4.2a3.4 3.4 0 0 1 -3.4 3.4h-4.2a3.4 3.4 0 0 1 -3.4 -3.4v-4.2a3.4 3.4 0 0 1 3.4 -3.4z"), listOf("M9 6.5L9.8 3h4.4L15 6.5M9 17.5l.8 3.5h4.4l.8-3.5"), listOf("M12 9.8v2.4l1.5 1"), listOf("M6 6h12v12h-12z", "M9 6V3h6v3M9 18v3h6v-3", "M12 9.5V12h2.5"), listOf()),
    "laptop" to BtArt(listOf("M6.5 6h11a1.5 1.5 0 0 1 1.5 1.5v6a1.5 1.5 0 0 1 -1.5 1.5h-11a1.5 1.5 0 0 1 -1.5 -1.5v-6a1.5 1.5 0 0 1 1.5 -1.5z"), listOf("M3 19h18"), listOf(), listOf("M5 6h14v9h-14z", "M3 19h18"), listOf()),
    "tv" to BtArt(listOf("M6 6h12a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2z"), listOf("M9 21h6"), listOf(), listOf("M4 6h16v11h-16z", "M9 21h6"), listOf()),
    "keyboard" to BtArt(listOf("M5 7h14a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-6a2 2 0 0 1 2 -2z"), listOf(), listOf("M7 11h.01M11 11h.01M15 11h.01M7 14h10"), listOf("M3 7h18v10h-18z", "M7 14h10"), listOf("M6 10h2v2h-2z", "M11 10h2v2h-2z", "M16 10h2v2h-2z")),
    "mouse" to BtArt(listOf("M7 9a5 5 0 0 1 10 0v6a5 5 0 0 1-10 0z"), listOf(), listOf("M12 4.5v4"), listOf("M7 3h10v18h-10z", "M12 3v6"), listOf()),
    "bt" to BtArt(listOf(), listOf("M7 7l10 10-5 4V3l5 4L7 17"), listOf(), listOf("M7 7l10 10-5 4V3l5 4L7 17"), listOf()),
)

private fun artFor(kind: String): BtArt = art[when (kind) {
    "headset" -> "headphones"
    "computer" -> "laptop"
    else -> kind
}] ?: art.getValue("bt")

/** How a connected device is drawn: outline, solid, duotone (soft fill and line) or tessera (square-cut blocks). */
@Composable
fun BtDeviceIcon(kind: String, style: String, ink: Color, cut: Color, size: Dp) {
    val a = remember(kind) { artFor(kind) }
    fun parse(l: List<String>): List<Path> = l.map { PathParser().parsePathString(it).toPath() }
    val main = remember(kind) { parse(a.main) }
    val strap = remember(kind) { parse(a.strap) }
    val detail = remember(kind) { parse(a.detail) }
    val line = remember(kind) { parse(a.line) }
    val fill = remember(kind) { parse(a.fill) }
    Canvas(Modifier.size(size)) {
        scale(this.size.minDimension / 24f, Offset.Zero) {
            when (style) {
                "tessera" -> {
                    val st = Stroke(width = 2.6f, cap = StrokeCap.Butt, join = StrokeJoin.Miter)
                    line.forEach { drawPath(it, ink, style = st) }
                    fill.forEach { drawPath(it, ink, style = Fill) }
                }
                else -> {
                    val round = { w: Float -> Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round) }
                    when (style) {
                        "solid" -> {
                            main.forEach { drawPath(it, ink, style = Fill); drawPath(it, ink, style = round(1.6f)) }
                            strap.forEach { drawPath(it, ink, style = round(1.8f)) }
                            detail.forEach { drawPath(it, cut, style = round(1.8f)) }
                        }
                        "duotone" -> {
                            main.forEach { drawPath(it, ink.copy(alpha = 0.24f), style = Fill); drawPath(it, ink, style = round(1.8f)) }
                            strap.forEach { drawPath(it, ink, style = round(1.8f)) }
                            detail.forEach { drawPath(it, ink, style = round(1.8f)) }
                        }
                        else -> (main + strap + detail).forEach { drawPath(it, ink, style = round(1.8f)) }
                    }
                }
            }
        }
    }
}

/** The device icon on its round chip; Tessera sits on a squircle instead. [base] is the colour behind the chip. */
@Composable
fun BtChip(kind: String, style: String, ink: Color, base: Color, size: Int) {
    val back = lerp(base, ink, 0.16f)
    val shape = if (style == "tessera") RoundedCornerShape(34) else CircleShape
    Box(Modifier.size(size.dp).clip(shape).background(back), contentAlignment = Alignment.Center) {
        BtDeviceIcon(kind, style, ink, back, (size * 0.56f).dp)
    }
}
