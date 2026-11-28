package app.koma.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

private fun DrawScope.tri(color: Color, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, alpha: Float = 1f) {
    val p = Path().apply {
        moveTo(x1, y1); lineTo(x2, y2); lineTo(x3, y3); close()
    }
    drawPath(p, color, alpha = alpha)
}

/** The cassette from the design (200 x 124), fitted inside whatever space it gets. */
@Composable
fun Cassette(modifier: Modifier, body: Color, stripe: Color, label: Color, ink: Color) {
    Canvas(modifier) {
        val s = minOf(size.width / 200f, size.height / 124f)
        translate((size.width - 200f * s) / 2f, (size.height - 124f * s) / 2f) {
            scale(s, Offset.Zero) {
                drawRoundRect(body, Offset(2f, 2f), Size(196f, 120f), CornerRadius(12f, 12f))
                drawRoundRect(ink, Offset(2f, 2f), Size(196f, 120f), CornerRadius(12f, 12f), style = Stroke(4f))
                drawRoundRect(label, Offset(18f, 14f), Size(164f, 54f), CornerRadius(6f, 6f))
                drawRoundRect(ink, Offset(18f, 14f), Size(164f, 54f), CornerRadius(6f, 6f), style = Stroke(3f))
                drawRoundRect(stripe, Offset(18f, 14f), Size(164f, 16f), CornerRadius(6f, 6f))
                drawRect(stripe, Offset(18f, 22f), Size(164f, 8f))
                drawRoundRect(ink, Offset(58f, 36f), Size(84f, 26f), CornerRadius(13f, 13f))
                drawCircle(label, 9f, Offset(80f, 49f))
                drawCircle(ink, 3f, Offset(80f, 49f))
                drawCircle(label, 9f, Offset(120f, 49f))
                drawCircle(ink, 3f, Offset(120f, 49f))
                val tape = Path().apply {
                    moveTo(44f, 122f); lineTo(56f, 90f); lineTo(144f, 90f); lineTo(156f, 122f); close()
                }
                drawPath(tape, ink)
                drawCircle(body, 5f, Offset(72f, 106f))
                drawCircle(body, 5f, Offset(128f, 106f))
            }
        }
    }
}

private val catFur = Color(0xFF15110F)
private val catPink = Color(0xFFC98A8A)
private val catIris = Color(0xFFC9892E)
private val catPupil = Color(0xFF0B0806)
private val catCream = Color(0xFFE8D9B5)
private val catBlue = Color(0xFF7FB6E8)
private val catGold = Color(0xFFF2C500)

private fun DrawScope.openEyes(irisR: Float, pupilR: Float, dx: Float = 0f, dy: Float = 0f) {
    for (cx in listOf(37f, 75f)) {
        drawCircle(catIris, irisR, Offset(cx, 48f))
        drawCircle(catPupil, pupilR, Offset(cx + dx, 48f + dy))
        drawCircle(Color.White, 4f, Offset(cx - 5f + dx, 43f + dy))
        drawCircle(Color.White, 1.6f, Offset(cx + 4f + dx, 53f + dy))
    }
}

/** Heavy eyelids: a band of fur over the top of the eyes, kept inside the head so nothing spills onto the tile. */
private fun DrawScope.lids(cover: Float) {
    val head = Path().apply { addOval(Rect(8f, 22f, 104f, 110f)) }
    clipPath(head) {
        for (cx in listOf(37f, 75f)) drawRect(catFur, Offset(cx - 16f, 30f), Size(32f, cover))
    }
}

/** A small sun with a dark outline so it reads on any tile colour. Rays go from [from] to [to] degrees; [clipBottom] cuts it at the horizon. */
private fun DrawScope.sun(cx: Float, cy: Float, r: Float, rays: Int, from: Float, to: Float, clipBottom: Float = 200f, color: Color = Color(0xFFFFE08A)) {
    clipRect(0f, 0f, 112f, clipBottom) {
        if (rays > 0) {
            for (i in 0 until rays) {
                val deg = if (rays == 1) from else from + (to - from) * i / (if (to - from >= 360f) rays else rays - 1)
                val a = Math.toRadians(deg.toDouble())
                drawLine(
                    catFur,
                    Offset(cx + ((r + 3f) * Math.cos(a)).toFloat(), cy + ((r + 3f) * Math.sin(a)).toFloat()),
                    Offset(cx + ((r + 8f) * Math.cos(a)).toFloat(), cy + ((r + 8f) * Math.sin(a)).toFloat()),
                    2.2f, StrokeCap.Round,
                )
            }
        }
        drawCircle(color, r, Offset(cx, cy))
        drawCircle(catFur, r, Offset(cx, cy), style = Stroke(2f))
    }
}

private fun DrawScope.drop(x: Float, y: Float) {
    drawCircle(catBlue, 3f, Offset(x, y + 2f))
    tri(catBlue, x - 3f, y + 1.5f, x + 3f, y + 1.5f, x, y - 5f)
}

private fun DrawScope.zed(x: Float, y: Float, s: Float) {
    val st = Stroke(2.5f, cap = StrokeCap.Round)
    val p = Path().apply { moveTo(x, y); lineTo(x + s, y); lineTo(x, y + s); lineTo(x + s, y + s) }
    drawPath(p, catCream, style = st)
}

/** The evening cat holds coffee: iced when it is warm out, hot when it is not. Null for every other cat. */
fun catCup(mood: String, temp: Int?, fahrenheit: Boolean): String? =
    if (mood != "evening" || temp == null) null else if (temp >= (if (fahrenheit) 75 else 24)) "iced" else "hot"

/** Whether "auto" means Fahrenheit here, the same rule the forecast uses. */
fun useFahrenheit(unit: String): Boolean = when (unit) {
    "f" -> true
    "c" -> false
    else -> java.util.Locale.getDefault().country in setOf("US", "BS", "BZ", "KY", "PW", "LR")
}

private val coffeeBrown = Color(0xFF5A3A22)

/** A mug with steam, or a tall glass with ice and a straw, in the bottom right corner of the cat's 112 x 76 space. */
private fun DrawScope.drawCoffee(iced: Boolean) {
    if (!iced) {
        drawArc(catCream, -90f, 180f, false, Offset(97f, 64f), Size(11f, 9f), style = Stroke(3f))
        drawRoundRect(catFur, Offset(83f, 60f), Size(20f, 18f), CornerRadius(5f, 5f), style = Stroke(2f))
        drawRoundRect(catCream, Offset(84f, 61f), Size(18f, 16f), CornerRadius(4f, 4f))
        drawRoundRect(coffeeBrown, Offset(86f, 63f), Size(14f, 4f), CornerRadius(2f, 2f))
        for (x in listOf(88f, 93f, 98f)) {
            val steam = Path().apply { moveTo(x, 57f); cubicTo(x - 3f, 53f, x + 3f, 50f, x, 45f) }
            drawPath(steam, Color.White, alpha = 0.75f, style = Stroke(1.8f, cap = StrokeCap.Round))
        }
    } else {
        drawLine(Color(0xFFD8503A), Offset(98f, 44f), Offset(95f, 72f), 2.4f, StrokeCap.Round)
        val glass = Path().apply { moveTo(86f, 56f); lineTo(106f, 56f); lineTo(103f, 78f); lineTo(89f, 78f); close() }
        drawPath(glass, Color.White, alpha = 0.35f)
        val drink = Path().apply { moveTo(87.2f, 63f); lineTo(104.8f, 63f); lineTo(103f, 78f); lineTo(89f, 78f); close() }
        drawPath(drink, coffeeBrown)
        drawPath(glass, catFur, style = Stroke(1.8f))
        drawRoundRect(Color.White, Offset(89f, 59f), Size(6f, 6f), CornerRadius(1.5f, 1.5f), alpha = 0.9f)
        drawRoundRect(Color.White, Offset(97f, 58.5f), Size(6f, 6f), CornerRadius(1.5f, 1.5f), alpha = 0.9f)
    }
}

/** Which cat to show: the weather first, then the time of day. [hour] is 0 to 23. */
fun catMood(glyph: String, day: Boolean, hour: Int = java.time.LocalTime.now().hour): String = when {
    glyph == "rain" || glyph == "snow" || glyph == "storm" || glyph == "fog" -> glyph
    hour in 4..6 -> "dawn"
    hour in 7..9 -> "morning"
    hour in 17..19 -> "evening"
    hour >= 23 || hour < 4 -> "midnight"
    !day -> "night"
    glyph == "sun" -> "sun"
    else -> "cloud"
}

/**
 * The cat peeking from the corner of the weather tile (112 x 76 in the design). Its face follows the weather:
 * sunglasses in the sun, droopy eyes and raindrops in rain, a woolly hat in snow, wide eyes and lightning in a
 * storm, sleepy in fog, and asleep at night. Dry weather follows the time of day: dawn, morning, evening and
 * midnight each have a look. [mood] is one of sun, cloud, rain, snow, storm, fog, night, dawn, morning, evening, midnight.
 */
@Composable
fun Cat(modifier: Modifier, mood: String = "cloud", cup: String? = null) {
    Canvas(modifier) {
        val s = minOf(size.width / 112f, size.height / 76f)
        translate(size.width - 112f * s, size.height - 76f * s) {
            scale(s, Offset.Zero) {
                tri(catFur, 12f, 50f, 20f, 4f, 50f, 28f)
                tri(catFur, 100f, 50f, 92f, 4f, 62f, 28f)
                tri(catPink, 20f, 40f, 24f, 16f, 40f, 29f, 0.7f)
                tri(catPink, 92f, 40f, 88f, 16f, 72f, 29f, 0.7f)
                drawOval(catFur, Offset(8f, 22f), Size(96f, 88f))
                when (mood) {
                    "sun" -> {
                        for (cx in listOf(37f, 75f)) {
                            drawCircle(Color(0xFF2B2622), 16f, Offset(cx, 48f))
                            drawCircle(catGold, 16f, Offset(cx, 48f), style = Stroke(3f))
                            drawArc(Color.White, 200f, 50f, false, Offset(cx - 10f, 38f), Size(20f, 20f), alpha = 0.5f, style = Stroke(2.5f, cap = StrokeCap.Round))
                        }
                        drawLine(catGold, Offset(52f, 46f), Offset(60f, 46f), 3f)
                        drawArc(catPink, 20f, 140f, false, Offset(46f, 64f), Size(20f, 10f), style = Stroke(2f, cap = StrokeCap.Round))
                    }
                    "rain" -> {
                        openEyes(14f, 8f, 0f, 2f)
                        for (cx in listOf(37f, 75f)) drawRect(catFur, Offset(cx - 15f, 32f), Size(30f, 16f))
                        drop(56f, 10f); drop(30f, 6f); drop(84f, 12f); drop(104f, 26f); drop(6f, 28f)
                    }
                    "snow" -> {
                        openEyes(14f, 9f)
                        drawRoundRect(Color(0xFF7FB6E8), Offset(26f, 10f), Size(60f, 22f), CornerRadius(10f, 10f))
                        drawRoundRect(Color.White, Offset(24f, 25f), Size(64f, 8f), CornerRadius(4f, 4f))
                        drawCircle(Color.White, 6f, Offset(56f, 8f))
                        for ((x, y) in listOf(8f to 10f, 100f to 8f, 14f to 60f, 98f to 56f, 4f to 36f)) drawCircle(Color.White, 2f, Offset(x, y), alpha = 0.9f)
                    }
                    "storm" -> {
                        openEyes(16f, 3.5f)
                        val bolt = Path().apply {
                            moveTo(100f, 0f); lineTo(88f, 18f); lineTo(97f, 18f); lineTo(90f, 34f)
                            lineTo(108f, 12f); lineTo(99f, 12f); lineTo(106f, 0f); close()
                        }
                        drawPath(bolt, catGold)
                    }
                    "fog" -> {
                        openEyes(14f, 8f)
                        for (cx in listOf(37f, 75f)) drawRect(catFur, Offset(cx - 15f, 32f), Size(30f, 10f))
                        drawRoundRect(Color.White, Offset(0f, 36f), Size(112f, 7f), CornerRadius(4f, 4f), alpha = 0.28f)
                        drawRoundRect(Color.White, Offset(0f, 54f), Size(112f, 7f), CornerRadius(4f, 4f), alpha = 0.22f)
                    }
                    "night" -> {
                        for (cx in listOf(37f, 75f)) drawArc(catCream, 0f, 180f, false, Offset(cx - 11f, 44f), Size(22f, 12f), style = Stroke(3.5f, cap = StrokeCap.Round))
                        zed(92f, 14f, 7f); zed(100f, 2f, 5f)
                    }
                    "dawn" -> {
                        // Half awake and yawning, with the sun just coming up over the corner.
                        openEyes(14f, 8f, 0f, 3f)
                        lids(18f)
                        drawOval(Color(0xFF6B2E2E), Offset(50f, 70f), Size(12f, 8f))
                        sun(98f, 16f, 9f, rays = 5, from = -180f, to = 0f, clipBottom = 16f)
                        drawLine(catFur, Offset(84f, 16.5f), Offset(112f, 16.5f), 2f, StrokeCap.Round)
                    }
                    "morning" -> {
                        // Happy closed eyes, rosy cheeks and a bright sun.
                        for (cx in listOf(37f, 75f)) {
                            drawArc(catCream, 190f, 160f, false, Offset(cx - 11f, 41f), Size(22f, 16f), style = Stroke(3.5f, cap = StrokeCap.Round))
                            drawCircle(catPink, 6f, Offset(cx + (if (cx < 56f) -9f else 9f), 62f), alpha = 0.6f)
                        }
                        drawArc(catPink, 20f, 140f, false, Offset(46f, 64f), Size(20f, 10f), style = Stroke(2f, cap = StrokeCap.Round))
                        sun(98f, 13f, 7f, rays = 8, from = 0f, to = 360f)
                    }
                    "evening" -> {
                        // Calm and relaxed, eyes drifting to the cup, with the sun going down in the corner.
                        openEyes(14f, 8f, 5f, 3f)
                        lids(12f)
                        drawArc(catPink, 20f, 140f, false, Offset(46f, 64f), Size(20f, 10f), style = Stroke(2f, cap = StrokeCap.Round))
                        sun(24f, 16f, 8f, rays = 0, from = 0f, to = 0f, clipBottom = 16f, color = Color(0xFFFFB067))
                        drawLine(catFur, Offset(8f, 16.5f), Offset(40f, 16.5f), 2f, StrokeCap.Round)
                        drawCircle(Color.White, 1.6f, Offset(60f, 6f), alpha = 0.8f)
                        drawCircle(Color.White, 1.2f, Offset(72f, 12f), alpha = 0.6f)
                    }
                    "midnight" -> {
                        // Wide awake in the small hours: big dark eyes, a crescent moon and stars.
                        openEyes(16f, 12f)
                        val moon = Path.combine(
                            PathOperation.Difference,
                            Path().apply { addOval(Rect(88f, 3f, 106f, 21f)) },
                            Path().apply { addOval(Rect(94f, 0f, 112f, 18f)) },
                        )
                        drawPath(moon, Color(0xFFFFF1C2))
                        for ((x, y) in listOf(10f to 8f, 24f to 3f, 76f to 6f, 5f to 30f, 106f to 38f)) drawCircle(Color.White, 1.9f, Offset(x, y), alpha = 0.9f)
                    }
                    else -> openEyes(14f, 9f, 3f, -3f)
                }
                tri(catPink, 52f, 65f, 60f, 65f, 56f, 69f)
                if (mood == "evening" && cup != null) drawCoffee(cup == "iced")
            }
        }
    }
}
