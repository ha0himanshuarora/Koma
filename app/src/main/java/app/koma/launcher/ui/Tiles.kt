package app.koma.launcher.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.text.drawText
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.koma.launcher.data.AppInfo
import app.koma.launcher.data.CalendarRepo
import app.koma.launcher.data.Event
import app.koma.launcher.data.IconCache
import app.koma.launcher.data.Weather
import app.koma.launcher.data.WeatherRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import app.koma.launcher.data.IconSpec

val TileShape = RoundedCornerShape(20.dp)

// ---- text styles -------------------------------------------------------------------------------

internal fun display(size: TextUnit, color: Color, tracking: TextUnit = (-0.03).em, lineHeight: Float = 0.95f) = TextStyle(
    fontFamily = Fonts.Display,
    fontWeight = FontWeight.Normal,
    fontSynthesis = FontSynthesis.None,
    fontSize = size,
    lineHeight = size * lineHeight,
    letterSpacing = tracking,
    color = color,
)

internal fun label(color: Color, size: TextUnit = 10.sp, bold: Boolean = false) = TextStyle(
    fontFamily = Fonts.Label,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
    fontSize = size,
    letterSpacing = 0.12.em,
    lineHeight = size * 1.5f,
    color = color,
)

internal fun thin(size: TextUnit, color: Color) = TextStyle(
    fontFamily = Fonts.Thin,
    fontWeight = FontWeight.ExtraLight,
    fontSynthesis = FontSynthesis.None,
    fontSize = size,
    letterSpacing = (-0.04).em,
    color = color,
)

// ---- time helpers ------------------------------------------------------------------------------

/** Current time, refreshed when the minute changes (and at once when Koma is shown again after being hidden). */
@Composable
internal fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES)) {
    while (true) {
        awaitVisible()
        val t = LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
        if (t != value) value = t
        sleepOrResume(60_000L - System.currentTimeMillis() % 60_000L)
    }
}

internal fun hourText(now: LocalDateTime, is24: Boolean): String {
    val h = if (is24) now.hour else (now.hour % 12).let { if (it == 0) 12 else it }
    return "%02d".format(h)
}

internal fun timeText(millis: Long, is24: Boolean): String {
    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    return t.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm"))
}

/** "NOW", "IN 14 MIN", "IN 3 H", "TOMORROW" or a weekday. */
internal fun untilText(event: Event, now: Long): String {
    if (now >= event.begin) return "NOW"
    val minutes = (event.begin - now) / 60_000L
    if (minutes < 60) return "IN $minutes MIN"
    if (minutes < 12 * 60) return "IN ${minutes / 60} H"
    val zone = ZoneId.systemDefault()
    val today = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone).toLocalDate()
    val day = LocalDateTime.ofInstant(Instant.ofEpochMilli(event.begin), zone).toLocalDate()
    return when {
        day == today -> "TODAY"
        day == today.plusDays(1) -> "TOMORROW"
        else -> day.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).uppercase()
    }
}

// ---- clock -------------------------------------------------------------------------------------

internal val clockStyles = listOf(
    "poster" to "POSTER", "thin" to "THIN", "analog" to "ANALOG", "mono" to "MONO", "words" to "WORDS", "flip" to "FLIP",
    "stack" to "STACK", "sub" to "BIG HOUR", "day" to "WEEKDAY", "bar" to "DAY BAR",
    "classic" to "CLASSIC", "wide" to "LINE ART",
    "ring" to "RINGS", "binary" to "BINARY", "split" to "SPLIT", "ruler" to "RULER", "arc" to "DAY ARC", "grid" to "HOURS", "seg" to "SEGMENTS", "tide" to "TIDE",
)

/** Time text with the chosen tone. Outline draws a stroke, then covers the glyph interiors with the tile colour so overlapping contours (handwritten fonts) do not show through. */
@Composable
internal fun ToneFit(
    text: String, tone: String, ink: Color, bg: Color, strokePx: Float, style: TextStyle,
    modifier: Modifier = Modifier, lines: Int = 1, align: Alignment = Alignment.CenterStart, firstGroup: Int = 0,
) {
    if (tone == "outline") {
        OutlineFit(text, style, ink, strokePx, modifier, lines, align) { _, c -> c.isDigit() }
    } else {
        FitText(toneText(text, tone, ink, strokePx, firstGroup), style, modifier, lines, align)
    }
}

/** Colours the digits of [t] by tone: solid, dim (tens digits), fade (minutes and seconds) or outline. */
internal fun toneText(t: String, tone: String, ink: Color, stroke: Float, firstGroup: Int = 0): AnnotatedString = buildAnnotatedString {
    var group = firstGroup
    var pos = 0
    t.forEach { ch ->
        if (!ch.isDigit()) {
            append(ch)
            if (ch == '\n' || ch == ':') { group++; pos = 0 }
            return@forEach
        }
        val span = when (tone) {
            "dim" -> if (pos == 0) SpanStyle(color = ink.copy(alpha = 0.4f)) else null
            "fade" -> if (group >= 1) SpanStyle(color = ink.copy(alpha = 0.45f)) else null
            "outline" -> SpanStyle(drawStyle = Stroke(width = stroke, join = StrokeJoin.Round))
            else -> null
        }
        if (span != null) withStyle(span) { append(ch) } else append(ch)
        pos++
    }
}

private val numberWords = listOf("TWELVE", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN", "ELEVEN")

/** The lines of a words clock to the nearest five minutes, for example ["TWENTY", "TO", "EIGHT"]. */
internal fun wordLines(now: LocalDateTime): List<String> {
    val r = ((now.minute + 2) / 5) * 5
    val hour = now.hour % 12
    val minuteWords = mapOf(5 to "FIVE", 10 to "TEN", 15 to "QUARTER", 20 to "TWENTY", 25 to "TWENTY-FIVE", 30 to "HALF")
    return when {
        r == 0 -> listOf(numberWords[hour], "O'CLOCK")
        r == 60 -> listOf(numberWords[(hour + 1) % 12], "O'CLOCK")
        r <= 30 -> listOf(minuteWords.getValue(r), "PAST", numberWords[hour])
        else -> listOf(minuteWords.getValue(60 - r), "TO", numberWords[(hour + 1) % 12])
    }
}

private fun zoneLabel(now: LocalDateTime): String {
    val secs = ZoneId.systemDefault().rules.getOffset(Instant.now()).totalSeconds
    val h = secs / 3600
    val m = Math.abs(secs % 3600) / 60
    val off = if (m == 0) "UTC%+d".format(h) else "UTC%+d:%02d".format(h, m)
    return off + " / " + now.format(DateTimeFormatter.ofPattern("dd.MM.yy"))
}

/** Time that ticks every second (analog and mono styles) or every minute. */
@Composable
internal fun rememberTicker(seconds: Boolean): State<LocalDateTime> = produceState(LocalDateTime.now(), seconds) {
    while (true) {
        awaitVisible()
        val t = LocalDateTime.now()
        val next = if (seconds) t.truncatedTo(java.time.temporal.ChronoUnit.SECONDS) else t.truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
        if (next != value) value = next
        if (seconds) delay(1_000L - System.currentTimeMillis() % 1_000L) else sleepOrResume(60_000L - System.currentTimeMillis() % 60_000L)
    }
}

/**
 * An analog dial: a soft face, minute ticks, bars for the hours (numerals at 12, 3, 6 and 9 and a date window once the dial
 * is big enough), rounded hands with a tail, and a seconds hand in the accent colour with an arc tracing the minute.
 */
@Composable
internal fun AnalogFace(now: LocalDateTime, ink: Color, accent: Color, modifier: Modifier, hole: Color = accent) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val font = Fonts.Clock
    val dayText = now.format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())).uppercase()
    Canvas(modifier) {
        val r = minOf(this.size.width, this.size.height) / 2f - 3f
        val c = center
        val rich = r > 100f
        fun at(deg: Double, dist: Float): Offset {
            val a = Math.toRadians(deg - 90.0)
            return c + Offset(cos(a).toFloat(), sin(a).toFloat()) * dist
        }
        drawCircle(ink.copy(alpha = 0.08f), r, c)
        drawCircle(ink.copy(alpha = 0.9f), r, c, style = Stroke(width = r * 0.03f))
        // The minute so far, drawn over the rim.
        val sec = now.second + now.nano / 1_000_000_000.0
        if (rich) drawArc(accent, -90f, (sec * 6.0).toFloat().coerceAtLeast(1f), false, Offset(c.x - r, c.y - r), androidx.compose.ui.geometry.Size(r * 2f, r * 2f), style = Stroke(r * 0.03f, cap = StrokeCap.Round))
        if (rich) for (i in 0 until 60) {
            if (i % 5 == 0) continue
            drawLine(ink.copy(alpha = 0.4f), at(i * 6.0, r * 0.89f), at(i * 6.0, r * 0.94f), strokeWidth = r * 0.012f, cap = StrokeCap.Round)
        }
        for (i in 0 until 12) {
            val quarter = i % 3 == 0
            if (rich && quarter) {
                val t = measurer.measure(if (i == 0) "12" else (i).toString(), tight(font, ink, 0f, 1f).copy(fontWeight = FontWeight.Bold, fontSize = with(density) { (r * 0.25f).toSp() }))
                val p = at(i * 30.0, r * 0.72f)
                drawText(t, topLeft = Offset(p.x - t.size.width / 2f, p.y - t.size.height / 2f))
            } else {
                drawLine(ink, at(i * 30.0, r * (if (quarter) 0.76f else 0.82f)), at(i * 30.0, r * 0.94f), strokeWidth = r * (if (quarter) 0.06f else 0.035f), cap = StrokeCap.Round)
            }
        }
        if (rich) {
            // The date, in a small window under the centre.
            val t = measurer.measure(dayText, tight(font, ink, 0.02f, 1f).copy(fontWeight = FontWeight.Bold, fontSize = with(density) { (r * 0.11f).toSp() }))
            val padX = r * 0.07f
            val padY = r * 0.04f
            val tl = Offset(c.x - t.size.width / 2f - padX, c.y + r * 0.3f)
            drawRoundRect(ink.copy(alpha = 0.14f), tl, androidx.compose.ui.geometry.Size(t.size.width + padX * 2, t.size.height + padY * 2), androidx.compose.ui.geometry.CornerRadius(r * 0.06f))
            drawText(t, topLeft = Offset(tl.x + padX, tl.y + padY))
        }
        fun hand(deg: Float, len: Float, tail: Float, width: Float, col: Color) {
            rotate(deg, c) {
                drawRoundRect(col, Offset(c.x - width / 2f, c.y - r * len), androidx.compose.ui.geometry.Size(width, r * (len + tail)), androidx.compose.ui.geometry.CornerRadius(width / 2f))
            }
        }
        val min = now.minute + sec / 60.0
        hand((((now.hour % 12) + min / 60.0) * 30.0).toFloat(), 0.5f, 0.1f, r * 0.08f, ink)
        hand((min * 6.0).toFloat(), 0.78f, 0.1f, r * 0.055f, ink)
        // Seconds: a thin hand with a counterweight and a small disc part way along.
        rotate((sec * 6.0).toFloat(), c) {
            drawRoundRect(accent, Offset(c.x - r * 0.012f, c.y - r * 0.86f), androidx.compose.ui.geometry.Size(r * 0.024f, r * 1.06f), androidx.compose.ui.geometry.CornerRadius(r * 0.012f))
            drawCircle(accent, r * 0.045f, Offset(c.x, c.y - r * 0.6f))
        }
        drawCircle(ink, r * 0.075f, c)
        drawCircle(hole, r * 0.038f, c)
    }
}

/** How far a font's colon sits below the middle of its digits, as a fraction of the font size. Measured once per font. */
private val colonLifts = HashMap<android.graphics.Typeface, Float>()

private fun colonLiftFor(tf: android.graphics.Typeface): Float = colonLifts.getOrPut(tf) {
    val p = android.graphics.Paint().apply {
        typeface = tf
        textSize = 100f
    }
    val colon = android.graphics.Rect()
    val digit = android.graphics.Rect()
    p.getTextBounds(":", 0, 1, colon)
    p.getTextBounds("0", 0, 1, digit)
    (((colon.top + colon.bottom) - (digit.top + digit.bottom)) / 2f / 100f).coerceIn(0f, 0.3f)
}

/** Raises each colon so its dots sit level with the middle of the digits instead of low in the line. */
private fun liftColons(text: AnnotatedString, lift: Float): AnnotatedString {
    if (lift < 0.005f || !text.text.contains(':')) return text
    return buildAnnotatedString {
        append(text)
        text.text.forEachIndexed { i, c -> if (c == ':') addStyle(SpanStyle(baselineShift = androidx.compose.ui.text.style.BaselineShift(lift)), i, i + 1) }
    }
}

/** One line of text that shrinks (to 60% at most) rather than wrap or be cut off when its box is too narrow. */
@Composable
internal fun ShrinkText(text: String, style: TextStyle, modifier: Modifier = Modifier, align: Alignment = Alignment.CenterStart) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier, contentAlignment = align) {
        val mw = constraints.maxWidth
        val fs = remember(text, style, mw) {
            var f = style.fontSize.value
            val floor = f * 0.6f
            while (f > floor && measurer.measure(text, style.copy(fontSize = f.sp), maxLines = 1, softWrap = false).size.width > mw) f -= 0.5f
            f
        }
        BasicText(text, style = style.copy(fontSize = fs.sp), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

/** Text that grows to fill its box exactly: the largest size at which it still fits in [lines] lines. */
@Composable
internal fun FitText(
    text0: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    lines: Int = 1,
    align: Alignment = Alignment.CenterStart,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val tfc = rememberTypeface(style)
    val text = remember(text0, tfc) { liftColons(text0, colonLiftFor(tfc)) }
    BoxWithConstraints(modifier, contentAlignment = align) {
        val mw = constraints.maxWidth
        val mh = constraints.maxHeight
        val fsPx = remember(text, style, mw, mh, density.density, density.fontScale) {
            var lo = 6f
            var hi = maxOf(mh.toFloat() * 1.3f, 12f)
            repeat(13) {
                val mid = (lo + hi) / 2f
                val r = measurer.measure(
                    text, style.copy(fontSize = with(density) { mid.toSp() }),
                    maxLines = lines, softWrap = false,
                )
                if (r.size.width <= mw && r.size.height <= mh) lo = mid else hi = mid
            }
            lo
        }
        BasicText(
            text,
            style = style.copy(fontSize = with(density) { fsPx.toSp() }),
            maxLines = lines,
            softWrap = false,
        )
    }
}

@Composable
internal fun FitText(text: String, style: TextStyle, modifier: Modifier = Modifier, lines: Int = 1, align: Alignment = Alignment.CenterStart) =
    FitText(AnnotatedString(text), style, modifier, lines, align)

/** A tight, centred line box so big numbers fill the space instead of floating in it. */
internal fun tight(font: FontFamily, color: Color, tracking: Float = -0.03f, lineHeight: Float = 0.9f) = TextStyle(
    fontFamily = font,
    fontWeight = FontWeight.Normal,
    fontSynthesis = FontSynthesis.None,
    letterSpacing = tracking.em,
    lineHeight = lineHeight.em,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    color = color,
)

@Composable
fun ClockTile(w: Int, h: Int, bg: Color, ink0: Color, style: String, seconds: Boolean = false, photo: String = "", photoDesign: String = "") {
    val pal = LocalPalette.current
    // "Backdrop" design: the photo fills the whole tile and the time sits on top, in black or white to suit the picture.
    val cover = style == "poster" && photoDesign == "cover" && photo.isNotBlank() && w >= 2
    val ctx = LocalContext.current
    val backdrop by produceState<ImageBitmap?>(null, photo, cover) {
        value = if (cover) withContext(Dispatchers.IO) { decodePhoto(ctx, photo) } else null
    }
    val backdropLight = remember(backdrop) { backdrop?.let { isLightImage(it) } }
    val ink = if (cover && backdropLight != null) (if (backdropLight) Color(0xFF111111) else Color(0xFFFFFFFF)) else ink0
    val live = style == "analog" || style == "mono" || seconds
    val now by rememberTicker(live)
    val is24 = app.koma.launcher.data.TimeFmt.is24()
    val hh = hourText(now, is24)
    val mm = "%02d".format(now.minute)
    val ss = "%02d".format(now.second)
    val tm = if (seconds) "$hh:$mm:$ss" else "$hh:$mm"
    val date = now.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())).uppercase()
    val accent = pal.tiles[1 % pal.tiles.size]
    val pad = if (w >= 3) 16.dp else 12.dp
    // Small text grows with the tile, so a big tile is not left with tiny captions.
    val area = w * h
    val lsz = when { area >= 12 -> 17; area >= 6 -> 13; else -> 10 }
    val lab = label(ink, lsz.sp)
    val labB = label(ink, (lsz + 2).sp, bold = true)
    val gap = if (area >= 12) 10.dp else 6.dp
    // Large tiles have room for more than the time. Each style uses these in its own way.
    val lg = area >= 6
    val wideL = w >= 3 && w >= h * 1.5f
    val dayFull = now.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())).uppercase()
    val monthFull = now.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())).uppercase()
    val dayNum = now.dayOfMonth.toString()
    val weekNo = now.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    val dayPct = (now.hour * 60 + now.minute) * 100 / 1440
    val density = LocalDensity.current
    val display = Fonts.Clock
    val tone = Fonts.clockTone
    val strokePx = with(density) { 2.dp.toPx() }
    val timeStyle = if (style == "mono") tight(Fonts.Mono, ink, -0.04f).copy(fontWeight = FontWeight.Bold) else tight(display, ink)
    Box(Modifier.fillMaxSize().clip(TileShape).background(bg)) {
    val shot = backdrop
    if (cover && shot != null) {
        Image(bitmap = shot, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        // A wash in the opposite colour to the text, so text stays readable on busy or mixed pictures.
        Box(Modifier.fillMaxSize().background(if (backdropLight == true) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)))
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(pad)) {
        val boxH = maxHeight
        when {
            // 1x1: time only.
            w == 1 && h == 1 -> ToneFit("$hh:$mm", tone, ink, bg.opaque(), strokePx, timeStyle, Modifier.fillMaxSize(), align = Alignment.BottomStart)
            // Wide strip: time (or a small dial) left, date right.
            h == 1 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (style == "analog") AnalogFace(now, ink, accent, Modifier.size(boxH), bg.opaque())
                else ToneFit(if (w >= 2) tm else "$hh:$mm", tone, ink, bg.opaque(), strokePx, timeStyle, Modifier.weight(1f).fillMaxHeight(), align = Alignment.CenterStart)
                if (w >= 3 || style == "analog") BasicText(date, style = lab)
            }
            style == "thin" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                BasicText(date, style = lab)
                ToneFit(tm, tone, ink, bg.opaque(), strokePx, timeStyle, Modifier.weight(1f).fillMaxWidth(), align = Alignment.CenterStart)
                if (lg) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // A hairline for how much of the day has gone.
                    Box(Modifier.fillMaxWidth().height(2.dp).background(ink.copy(alpha = 0.25f))) {
                        Box(Modifier.fillMaxWidth(dayPct / 100f).fillMaxHeight().background(ink))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        BasicText("WEEK $weekNo", style = lab)
                        BasicText(if (w >= 3) "$dayPct% OF THE DAY" else "$dayPct%", style = lab, maxLines = 1)
                    }
                } else if (w >= 3) BasicText(now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())).uppercase(), style = lab)
            }
            // A wide analog tile pairs the dial with the time in numbers.
            style == "analog" && wideL -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                AnalogFace(now, ink, accent, Modifier.size(boxH), bg.opaque())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    ShrinkText(zoneLabel(now), lab, Modifier.fillMaxWidth())
                    FitText("$hh:$mm", tight(display, ink, -0.03f, 0.9f), Modifier.fillMaxWidth().weight(1f).padding(vertical = 6.dp), align = Alignment.CenterStart)
                    ShrinkText(date, labB, Modifier.fillMaxWidth())
                }
            }
            style == "analog" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ShrinkText(zoneLabel(now), lab, Modifier.fillMaxWidth())
                AnalogFace(now, ink, accent, Modifier.weight(1f).fillMaxWidth(), bg.opaque())
            }
            style == "mono" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                BasicText(date, style = lab)
                FitText("$hh:$mm", timeStyle, Modifier.weight(1f).fillMaxWidth(), align = Alignment.CenterStart)
                if (lg) Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(ink.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxWidth(now.second / 60f).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(ink))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText(":%02d".format(now.second), style = label(ink, (lsz + 4).sp, bold = true))
                    ShrinkText(zoneLabel(now), lab, Modifier.weight(1f).padding(start = 8.dp), Alignment.CenterEnd)
                }
            }
            style == "words" -> {
                val lines = wordLines(now)
                val stroke = with(density) { 1.5.dp.toPx() }
                val text = lines.joinToString("\n")
                // The middle word of a three-line time is the outlined one.
                val ringFrom = if (lines.size == 3) lines[0].length + 1 else -1
                val ringTo = if (lines.size == 3) ringFrom + lines[1].length else -1
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText("IT IS", style = lab)
                    OutlineFit(text, tight(display, ink, -0.02f, 1.0f), ink, stroke, Modifier.weight(1f).fillMaxWidth(), lines.size, Alignment.CenterStart) { i, _ -> i in ringFrom until ringTo }
                    if (lg) ShrinkText("$dayFull  \u00B7  $dayNum $monthFull", lab, Modifier.fillMaxWidth())
                }
            }
            style == "flip" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Cards stay card-shaped and sit together in the middle, however wide the tile is.
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val cards = if (seconds) listOf(hh, mm, ss) else listOf(hh, mm)
                    // A 12-hour clock gets the little AM / PM tab a flip clock has beside the cards.
                    val tab = !is24 && lg
                    val cw = minOf((maxWidth - 8.dp * (cards.size - 1) - (if (tab) 42.dp else 0.dp)) / cards.size, maxHeight * 0.82f)
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                        cards.forEach { d ->
                            FlipCard(d, ink, bg.opaque(), tight(display, bg.opaque(), -0.03f, 1.0f), Modifier.width(cw).fillMaxHeight())
                        }
                        if (tab) Column(Modifier.width(34.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("AM", "PM").forEachIndexed { i, t ->
                                val on = (now.hour >= 12) == (i == 1)
                                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(10.dp)).background(if (on) ink else ink.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                                    BasicText(t, style = label(if (on) bg.opaque() else ink, (lsz - 3).sp, bold = true), maxLines = 1)
                                }
                            }
                        }
                    }
                }
                if (h >= 2) BasicText(date, style = lab)
            }
            // Rings: the hour, the minute and (with seconds) the second each fill their own ring.
            style == "ring" -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
              Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                BasicText(date, style = lab)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxHeight().aspectRatio(1f)) {
                        val sw = this.size.minDimension * 0.07f
                        fun ring(i: Int, f: Float) {
                            val inset = sw * (0.5f + i * 1.5f)
                            val sz = androidx.compose.ui.geometry.Size(this.size.width - inset * 2, this.size.height - inset * 2)
                            drawArc(ink.copy(alpha = 0.18f), 0f, 360f, false, Offset(inset, inset), sz, style = Stroke(sw))
                            drawArc(ink, -90f, 360f * f.coerceIn(0.004f, 1f), false, Offset(inset, inset), sz, style = Stroke(sw, cap = StrokeCap.Round))
                        }
                        ring(0, ((now.hour % 12) * 60 + now.minute) / 720f)
                        ring(1, (now.minute * 60 + now.second) / 3600f)
                        if (seconds) ring(2, now.second / 60f)
                    }
                    FitText(AnnotatedString("$hh\n$mm"), tight(display, ink, -0.03f, 0.9f), Modifier.fillMaxSize(if (seconds) 0.3f else 0.38f), lines = 2, align = Alignment.Center)
                }
              }
              if (wideL) Column(Modifier.weight(0.8f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                  // How far through its hour, its minute and (with seconds) its second each ring has gone.
                  val legend = listOf("HOUR" to ((now.hour % 12) * 60 + now.minute) * 100 / 720, "MINUTE" to (now.minute * 60 + now.second) * 100 / 3600) +
                      (if (seconds) listOf("SECOND" to now.second * 100 / 60) else emptyList())
                  legend.forEach { (name, pct) ->
                      Column(Modifier.padding(vertical = 5.dp)) {
                          BasicText(name, style = label(ink.copy(alpha = 0.65f), (lsz - 2).sp, bold = true))
                          BasicText("$pct%", style = labB)
                      }
                  }
              }
            }
            // Binary: each digit of the time as four dots, the way a binary clock shows it.
            style == "binary" -> {
                val digits = (hh.padStart(2, '0') + mm).map { it.digitToInt() }
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText(date, style = lab)
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // What each row is worth: add up the lit dots in a column to read its digit.
                        Column(Modifier.fillMaxHeight().width(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (b in 3 downTo 0) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                BasicText("${1 shl b}", style = label(ink.copy(alpha = 0.6f), (lsz - 2).sp, bold = true), maxLines = 1)
                            }
                        }
                        digits.forEachIndexed { idx, d ->
                            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (b in 3 downTo 0) {
                                    Box(
                                        Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(7.dp))
                                            .background(if ((d shr b) and 1 == 1) ink else ink.copy(alpha = 0.15f)),
                                    )
                                }
                            }
                            if (idx == 1) Spacer(Modifier.width(4.dp))
                        }
                    }
                    BasicText("$hh:$mm", style = labB)
                }
            }
            // Split: the hour on a solid block, the minutes on a soft one.
            style == "split" && wideL -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(ink), contentAlignment = Alignment.Center) {
                    FitText(hh, tight(display, bg.opaque(), -0.03f, 1f), Modifier.fillMaxSize().padding(12.dp), align = Alignment.Center)
                }
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(ink.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    FitText(if (seconds) "$mm:$ss" else mm, tight(display, ink, -0.03f, 1f), Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 34.dp), align = Alignment.Center)
                    BasicText(date, style = lab, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
                }
            }
            style == "split" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ink), contentAlignment = Alignment.Center) {
                    FitText(hh, tight(display, bg.opaque(), -0.03f, 1f), Modifier.fillMaxSize().padding(8.dp), align = Alignment.Center)
                }
                Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ink.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    FitText(if (seconds) "$mm:$ss" else mm, tight(display, ink, -0.03f, 1f), Modifier.fillMaxSize().padding(8.dp), align = Alignment.Center)
                }
                if (h >= 3) BasicText(date, style = lab)
            }
            // Ruler: the time, over a minute ruler that slides as the minutes pass.
            style == "ruler" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                BasicText(date, style = lab)
                ToneFit(tm, tone, ink, bg.opaque(), strokePx, timeStyle, Modifier.weight(1f).fillMaxWidth(), align = Alignment.CenterStart)
                Canvas(Modifier.fillMaxWidth().height(if (area >= 6) 44.dp else 28.dp)) {
                    val n = 31
                    val step = this.size.width / (n - 1)
                    for (i in 0 until n) {
                        val m = Math.floorMod(now.minute + i - 15, 60)
                        val mid = i == 15
                        val len = this.size.height * (if (mid) 1f else if (m % 5 == 0) 0.7f else 0.4f)
                        drawLine(
                            if (mid) ink else ink.copy(alpha = if (m % 5 == 0) 0.8f else 0.45f),
                            Offset(i * step, this.size.height), Offset(i * step, this.size.height - len),
                            strokeWidth = if (mid) 5f else 2.5f, cap = StrokeCap.Round,
                        )
                    }
                }
            }
            // Day arc: the time big, and under it the sun's path across the day, a hump from midnight to midnight with the sun on it.
            style == "arc" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText(date, style = lab)
                ToneFit(tm, tone, ink, bg.opaque(), strokePx, timeStyle, Modifier.weight(1f).fillMaxWidth(), align = Alignment.CenterStart)
                Canvas(Modifier.fillMaxWidth().height(if (area >= 12) 96.dp else if (h >= 3) 64.dp else 44.dp)) {
                    val sw = 3.dp.toPx()
                    val sunR = 5.dp.toPx()
                    val left = sunR
                    val width = this.size.width - sunR * 2f
                    val base = this.size.height - sw
                    val amp = base - sunR * 2.4f
                    val f = ((now.hour * 60 + now.minute) / 1440f).coerceIn(0f, 1f)
                    fun px(t: Float) = left + width * t
                    fun py(t: Float) = base - amp * kotlin.math.sin(Math.PI.toFloat() * t)
                    val steps = 48
                    val all = androidx.compose.ui.graphics.Path().apply {
                        moveTo(px(0f), py(0f))
                        for (i in 1..steps) lineTo(px(i / steps.toFloat()), py(i / steps.toFloat()))
                    }
                    val past = androidx.compose.ui.graphics.Path().apply {
                        moveTo(px(0f), base)
                        val n = (steps * f).toInt().coerceAtLeast(1)
                        for (i in 0..n) lineTo(px(f * i / n), py(f * i / n))
                        lineTo(px(f), base)
                        close()
                    }
                    drawPath(past, ink.copy(alpha = 0.16f))
                    drawPath(all, ink.copy(alpha = 0.3f), style = Stroke(sw, cap = StrokeCap.Round))
                    val done = androidx.compose.ui.graphics.Path().apply {
                        moveTo(px(0f), py(0f))
                        val n = (steps * f).toInt().coerceAtLeast(1)
                        for (i in 1..n) lineTo(px(f * i / n), py(f * i / n))
                    }
                    drawPath(done, ink, style = Stroke(sw, cap = StrokeCap.Round))
                    drawLine(ink.copy(alpha = 0.5f), Offset(0f, base), Offset(this.size.width, base), strokeWidth = sw * 0.5f)
                    val sun = Offset(px(f), py(f))
                    drawCircle(bg.opaque(), sunR * 1.7f, sun)
                    drawCircle(ink, sunR, sun)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("00", "06", "12", "18", "24").forEach { BasicText(it, style = label(ink.copy(alpha = 0.65f), (lsz - 1).sp, bold = true)) }
                }
            }
            // Grid: today as 24 squares, one per hour. The hours that are over are filled, the current one is half.
            style == "grid" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                BasicText(date, style = lab)
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (row in 0 until 4) {
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (col in 0 until 6) {
                                val hour = row * 6 + col
                                Box(
                                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(ink.copy(alpha = if (hour < now.hour) 1f else 0.15f)),
                                ) {
                                    if (hour == now.hour) Box(Modifier.fillMaxHeight().fillMaxWidth(now.minute / 60f).background(ink))
                                }
                            }
                        }
                    }
                }
                BasicText(tm, style = labB)
            }
            // Segments: seven-segment digits, with the unlit segments left faintly visible like an old display.
            style == "seg" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                BasicText(date, style = lab)
                val digits: List<Int?> = (hh.padStart(2, ' ') + mm).map { it.digitToIntOrNull() }
                Canvas(Modifier.weight(1f).fillMaxWidth()) {
                    val gapF = 0.2f
                    val dw = minOf(this.size.width / (4f + 0.4f + 4 * gapF), this.size.height / 1.8f)
                    val dh = dw * 1.8f
                    val t = dw * 0.2f
                    val total = dw * (4f + 0.4f + 4 * gapF)
                    var x = (this.size.width - total) / 2f
                    val y0 = (this.size.height - dh) / 2f
                    val masks = intArrayOf(0b1111110, 0b0110000, 0b1101101, 0b1111001, 0b0110011, 0b1011011, 0b1011111, 0b1110000, 0b1111111, 0b1111011)
                    val cr = androidx.compose.ui.geometry.CornerRadius(t / 2f)
                    fun seg(on: Boolean, l: Float, tp: Float, w: Float, hh2: Float) =
                        drawRoundRect(if (on) ink else ink.copy(alpha = 0.1f), Offset(l, tp), androidx.compose.ui.geometry.Size(w, hh2), cr)
                    for (k in 0 until 5) {
                        if (k == 2) {
                            val cx = x + dw * 0.2f
                            drawCircle(ink, t * 0.6f, Offset(cx, y0 + dh * 0.33f))
                            drawCircle(ink, t * 0.6f, Offset(cx, y0 + dh * 0.67f))
                            x += dw * 0.4f + dw * gapF
                            continue
                        }
                        val d = digits[if (k < 2) k else k - 1]
                        val m = if (d == null) 0 else masks[d]
                        fun on(bit: Int) = (m shr (6 - bit)) and 1 == 1
                        val half = dh / 2f
                        val vh = half - t * 1.2f
                        seg(on(0), x + t * 0.6f, y0, dw - t * 1.2f, t)
                        seg(on(1), x + dw - t, y0 + t * 0.6f, t, vh)
                        seg(on(2), x + dw - t, y0 + half + t * 0.6f, t, vh)
                        seg(on(3), x + t * 0.6f, y0 + dh - t, dw - t * 1.2f, t)
                        seg(on(4), x, y0 + half + t * 0.6f, t, vh)
                        seg(on(5), x, y0 + t * 0.6f, t, vh)
                        seg(on(6), x + t * 0.6f, y0 + half - t / 2f, dw - t * 1.2f, t)
                        x += dw + dw * gapF
                    }
                }
            }
            // Tide: the box fills up like water as the hour passes, with the time floating on it.
            style == "tide" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                BasicText(date, style = lab)
                val level = ((now.minute * 60 + now.second) / 3600f).coerceIn(0.03f, 1f)
                Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ink.copy(alpha = 0.1f))) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(level).background(ink.copy(alpha = 0.26f)))
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 0.dp).fillMaxWidth().fillMaxHeight(level)) {
                        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(3.dp).background(ink))
                    }
                    ToneFit(tm, tone, ink, bg.opaque(), strokePx, timeStyle, Modifier.fillMaxSize().padding(10.dp), align = Alignment.Center)
                }
                BasicText("${now.minute} / 60 MIN", style = label(ink, lsz.sp, bold = true))
            }
            // Classic: the time sits low, with the date line under it.
            style == "classic" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                ToneFit(tm, tone, ink, bg.opaque(), strokePx, timeStyle, Modifier.weight(1f).fillMaxWidth(), align = Alignment.BottomStart)
                if (lg) ShrinkText(now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())), label(ink, (lsz + 3).sp, bold = true), Modifier.fillMaxWidth().padding(top = 6.dp))
                else BasicText(now.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())), style = labB, modifier = Modifier.padding(top = 4.dp))
            }
            // Line art: a huge weekday over a row of small facts.
            style == "wide" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FitText(
                    AnnotatedString(now.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).uppercase()),
                    tight(Fonts.Thin, ink, 0.02f, 0.9f), Modifier.weight(1f).fillMaxWidth(), align = Alignment.Center,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BasicText(tm, style = labB)
                    BasicText(now.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())).uppercase(), style = labB)
                }
            }
            // Stack: hours over minutes, both solid. A wide tile gives the date, as a big day number, the space beside them.
            style == "stack" -> {
                val stackText: @Composable (Modifier) -> Unit = { m ->
                    ToneFit(if (seconds) "$hh\n$mm\n$ss" else "$hh\n$mm", tone, ink, bg.opaque(), strokePx, tight(display, ink, -0.04f, 0.88f), m,
                        lines = if (seconds) 3 else 2, align = Alignment.CenterStart,
                    )
                }
                if (wideL) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    stackText(Modifier.weight(1f).fillMaxHeight())
                    Column(Modifier.weight(0.9f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                        ShrinkText(dayFull, labB, Modifier.fillMaxWidth())
                        FitText(dayNum, tight(display, ink, -0.03f, 0.9f), Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp), align = Alignment.CenterStart)
                        ShrinkText(monthFull, labB, Modifier.fillMaxWidth())
                    }
                } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    stackText(Modifier.weight(1f).fillMaxWidth())
                    BasicText(date, style = lab)
                }
            }
            // Big hour: large hour with the minutes small on its baseline.
            // Big hour: one line, the hour large and the minutes small beside it on the same baseline, so the two read as one time.
            style == "sub" -> {
                val flat = if (tone == "outline") "solid" else tone
                val timeText = buildAnnotatedString {
                    append(toneText(hh, flat, ink, strokePx, 0))
                    append(" ")
                    withStyle(SpanStyle(fontSize = 0.4.em)) { append(toneText(if (seconds) "$mm:$ss" else mm, flat, ink, strokePx, 1)) }
                }
                val timeStyle2 = tight(display, ink, -0.04f, 0.86f)
                if (wideL) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    FitText(timeText, timeStyle2, Modifier.weight(1.5f).fillMaxHeight(), align = Alignment.CenterStart)
                    Column(Modifier.weight(0.8f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShrinkText(dayFull, labB, Modifier.fillMaxWidth())
                        ShrinkText("$dayNum $monthFull", lab, Modifier.fillMaxWidth())
                        ShrinkText("WEEK $weekNo", lab, Modifier.fillMaxWidth())
                    }
                } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    BasicText(date, style = lab)
                    FitText(timeText, timeStyle2, Modifier.weight(1f).fillMaxWidth(), align = Alignment.BottomStart)
                }
            }
            // Weekday: the day is the headline, the time sits underneath.
            style == "day" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FitText(
                    now.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).replaceFirstChar { it.uppercase() } + ".",
                    tight(display, ink, -0.04f, 0.9f), Modifier.weight(1f).fillMaxWidth(), align = Alignment.CenterStart,
                )
                if (lg) ShrinkText("$tm  \u00B7  $dayNum $monthFull", label(ink, (lsz + 3).sp, bold = true), Modifier.fillMaxWidth())
                else BasicText("$tm  \u00B7  " + now.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())), style = labB)
            }
            // Day bar: time with a line showing how much of the day has gone.
            style == "bar" -> {
                val frac = (now.hour * 60 + now.minute) / 1440f
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(date, style = lab)
                    ToneFit(tm, tone, ink, bg.opaque(), strokePx, timeStyle, Modifier.weight(1f).fillMaxWidth(), align = Alignment.CenterStart)
                    Box(Modifier.fillMaxWidth().height(if (area >= 6) 14.dp else 8.dp).clip(RoundedCornerShape(7.dp)).background(ink.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(7.dp)).background(ink))
                    }
                    if (lg) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("00", "06", "12", "18", "24").forEach { BasicText(it, style = label(ink.copy(alpha = 0.65f), (lsz - 1).sp, bold = true)) }
                    }
                    BasicText("DAY ${(frac * 100).toInt()}%", style = lab)
                }
            }
            // Poster: hours solid, minutes outlined, stacked and sized to fill whatever space the tile has.
            else -> {
                val big = w >= 3 && h >= 2
                val stroke = with(density) { (if (big) 3.dp else 2.dp).toPx() }
                // With a photo, the time keeps the left side and the picture sits beside it. Plain posters are unchanged.
                val pic = style == "poster" && photo.isNotBlank() && w >= 2 && !cover
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (pic && photoDesign == "left") ClockPhoto(photo, ink, photoDesign, Modifier.weight(0.8f).fillMaxHeight())
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (big && !pic) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                                BasicText(
                                    now.format(DateTimeFormatter.ofPattern("EEE dd.MM", Locale.getDefault())).uppercase() + "\nLOCAL TIME",
                                    style = label(ink, (lsz - 1).sp),
                                )
                                Glyph("arrow", ink, 32.dp, strokeWidth = 3f)
                            }
                        }
                        OutlineFit(
                            if (seconds) "$hh\n$mm\n$ss" else "$hh\n$mm", tight(display, ink, -0.01f, 0.86f), ink, stroke, Modifier.weight(1f).fillMaxWidth(),
                        ) { i, _ -> i in (hh.length + 1) until (hh.length + 1 + mm.length) }
                        if (!big || pic) BasicText(date, style = lab)
                    }
                    if (pic && photoDesign != "left") ClockPhoto(photo, ink, photoDesign, Modifier.weight(if (w >= 3) 0.8f else 0.7f).fillMaxHeight())
                }
            }
        }
    }
    }
}

/** Where each line of an outlined text sits, measured with a plain Paint so the drawing below is predictable. */
private class OutlineLook(val lines: List<String>, val paint: android.graphics.Paint, val lh: Float, val base: Float, val width: Float, val height: Float)

private fun outlineLook(text: String, style: TextStyle, tf: android.graphics.Typeface, sizePx: Float, density: Density): OutlineLook {
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    p.typeface = tf
    p.textSize = sizePx
    val ls = style.letterSpacing
    p.letterSpacing = if (ls.isEm) ls.value else if (ls.isSp) with(density) { ls.toPx() } / sizePx else 0f
    val fm = p.fontMetrics
    val lhs = style.lineHeight
    val lh = if (lhs.isEm) lhs.value * sizePx else if (lhs.isSp) with(density) { lhs.toPx() } else fm.descent - fm.ascent
    val bounds = android.graphics.Rect()
    p.getTextBounds("0H", 0, 2, bounds)
    val cap = -bounds.top.toFloat()
    // With a set line height the digits are centred in their line; otherwise they sit on the font's own baseline.
    val base = if (lhs.isEm || lhs.isSp) lh / 2f + cap / 2f else -fm.ascent
    val lines = text.split('\n')
    return OutlineLook(lines, p, lh, base, lines.maxOf { p.measureText(it) }, lh * lines.size)
}

/**
 * Draws the text into a bitmap: the characters in [mask] as a ring (stroke, then the inside cleared) and the rest solid.
 * Clearing the inside of a stroked glyph leaves one clean ring even where the font builds a digit from overlapping
 * strokes, and it leaves transparency, so a photo behind the text shows through.
 */
private fun renderOutline(look: OutlineLook, mask: BooleanArray, ink: Color, stroke: Float, pad: Int): android.graphics.Bitmap {
    val bmp = android.graphics.Bitmap.createBitmap(
        kotlin.math.ceil(look.width).toInt() + 2 * pad, kotlin.math.ceil(look.height).toInt() + 2 * pad, android.graphics.Bitmap.Config.ARGB_8888,
    )
    val c = android.graphics.Canvas(bmp)
    val col = ink.toArgb()
    val ring = android.graphics.Paint(look.paint).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = stroke * 2f
        strokeJoin = android.graphics.Paint.Join.ROUND
        color = col
    }
    val clear = android.graphics.Paint(look.paint).apply {
        style = android.graphics.Paint.Style.FILL
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
    }
    val fill = android.graphics.Paint(look.paint).apply {
        style = android.graphics.Paint.Style.FILL
        color = col
    }
    class Ch(val s: String, val x: Float, val y: Float, val ring: Boolean)
    val chars = ArrayList<Ch>()
    var idx = 0
    look.lines.forEachIndexed { li, line ->
        val y = pad + li * look.lh + look.base
        line.forEachIndexed { ci, ch ->
            if (ch != ' ') {
                val lift = if (ch == ':') look.paint.textSize * colonLiftFor(look.paint.typeface ?: android.graphics.Typeface.DEFAULT) else 0f
                chars += Ch(ch.toString(), pad + look.paint.measureText(line, 0, ci), y - lift, mask.getOrElse(idx + ci) { false })
            }
        }
        idx += line.length + 1
    }
    chars.filter { it.ring }.forEach { c.drawText(it.s, it.x, it.y, ring) }
    chars.forEach { c.drawText(it.s, it.x, it.y, clear) }
    chars.filter { !it.ring }.forEach { c.drawText(it.s, it.x, it.y, fill) }
    return bmp
}

@Composable
private fun rememberTypeface(style: TextStyle): android.graphics.Typeface {
    val resolver = androidx.compose.ui.platform.LocalFontFamilyResolver.current
    return remember(style.fontFamily, style.fontWeight) {
        resolver.resolve(
            style.fontFamily ?: FontFamily.Default, style.fontWeight ?: FontWeight.Normal,
            androidx.compose.ui.text.font.FontStyle.Normal, FontSynthesis.None,
        ).value as? android.graphics.Typeface ?: android.graphics.Typeface.DEFAULT
    }
}

@Composable
private fun OutlineAt(
    text: String, style: TextStyle, tf: android.graphics.Typeface, sizePx: Float, ink: Color, stroke: Float,
    modifier: Modifier, outlined: (Int, Char) -> Boolean,
) {
    val density = LocalDensity.current
    val look = remember(text, style.letterSpacing, style.lineHeight, tf, sizePx, density.density) { outlineLook(text, style, tf, sizePx, density) }
    val mask = BooleanArray(text.length) { outlined(it, text[it]) }
    val key = String(CharArray(mask.size) { if (mask[it]) '1' else '0' })
    val pad = kotlin.math.ceil(stroke).toInt() + 1
    val img = remember(look, key, ink, stroke) { renderOutline(look, mask, ink, stroke, pad).asImageBitmap() }
    Box(modifier.size(with(density) { look.width.toDp() }, with(density) { look.height.toDp() })) {
        Image(img, null, Modifier.requiredSize(with(density) { img.width.toDp() }, with(density) { img.height.toDp() }), contentScale = ContentScale.FillBounds)
    }
}

/** Text where the characters picked by [outlined] are a clean outline [stroke] px wide and the rest are solid. */
@Composable
internal fun OutlineText(
    text: String, style: TextStyle, ink: Color, stroke: Float, modifier: Modifier = Modifier,
    outlined: (Int, Char) -> Boolean = { _, _ -> true },
) {
    val density = LocalDensity.current
    OutlineAt(text, style, rememberTypeface(style), with(density) { style.fontSize.toPx() }, ink, stroke, modifier, outlined)
}

/** [OutlineText] sized to fill its box, like [FitText]. [lines] is kept for symmetry; the lines come from the text. */
@Composable
internal fun OutlineFit(
    text: String, style: TextStyle, ink: Color, stroke: Float, modifier: Modifier = Modifier, lines: Int = 1,
    align: Alignment = Alignment.CenterStart, outlined: (Int, Char) -> Boolean = { _, _ -> true },
) {
    val density = LocalDensity.current
    val tf = rememberTypeface(style)
    BoxWithConstraints(modifier, contentAlignment = align) {
        val mw = constraints.maxWidth
        val mh = constraints.maxHeight
        val fsPx = remember(text, style, tf, mw, mh, density.density, stroke) {
            var lo = 6f
            var hi = maxOf(mh.toFloat() * 1.3f, 12f)
            repeat(13) {
                val mid = (lo + hi) / 2f
                val l = outlineLook(text, style, tf, mid, density)
                if (l.width <= mw - 2 * stroke && l.height <= mh - 2 * stroke) lo = mid else hi = mid
            }
            lo
        }
        OutlineAt(text, style, tf, fsPx, ink, stroke, Modifier, outlined)
    }
}

/** True when a picture is mostly light, so dark text reads better on it. */
private fun isLightImage(img: ImageBitmap): Boolean {
    val small = android.graphics.Bitmap.createScaledBitmap(img.asAndroidBitmap(), 16, 16, true)
    var sum = 0.0
    for (y in 0 until 16) for (x in 0 until 16) sum += Color(small.getPixel(x, y)).luminance()
    return sum / 256 > 0.5
}

/** How the picture beside a poster clock is cut and where it sits. */
internal val clockPhotoDesigns = listOf("card" to "CARD", "left" to "CARD LEFT", "arch" to "ARCH", "cover" to "BACKDROP")

/** The picture beside a poster clock, cropped to fill its shape. */
@Composable
private fun ClockPhoto(uri: String, ink: Color, design: String, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { decodePhoto(ctx, uri) }
    }
    val b = bmp
    val shape = when (design) {
        "arch" -> RoundedCornerShape(topStart = CornerSize(50), topEnd = CornerSize(50), bottomEnd = CornerSize(14.dp), bottomStart = CornerSize(14.dp))
        else -> RoundedCornerShape(18.dp)
    }
    val box = Modifier.fillMaxSize()
    Box(modifier, contentAlignment = Alignment.Center) {
        if (b != null) Image(bitmap = b, contentDescription = null, contentScale = ContentScale.Crop, modifier = box.clip(shape))
        else Box(box.clip(shape).background(ink.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { Glyph("pics", ink, 24.dp, 2f) }
    }
}

// ---- date --------------------------------------------------------------------------------------

@Composable
fun DateTile(w: Int, h: Int, bg: Color, ink: Color) {
    val now by rememberNow()
    val weekday = now.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())).uppercase()
    val month = now.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())).uppercase()
    val day = now.dayOfMonth.toString()
    Box(Modifier.fillMaxSize().clip(TileShape).background(bg).padding(12.dp)) {
        when {
            w >= 2 && h >= 2 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                BasicText(weekday, style = label(ink))
                BasicText(day, style = display(84.sp, ink, (-0.04).em))
                BasicText(month, style = label(ink))
            }
            w >= 2 -> Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    BasicText(weekday.take(3), style = label(ink))
                    BasicText(month.take(3), style = label(ink))
                }
                BasicText(day, style = display(48.sp, ink, (-0.04).em))
            }
            // 1x1, as in the design: big day number on top, "SUN / NOV" below.
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                BasicText(day, style = display(42.sp, ink, (-0.05).em, 0.9f))
                BasicText("${weekday.take(3)} / ${month.take(3)}", style = label(ink, 11.sp, bold = true))
            }
        }
    }
}

// ---- kanji -------------------------------------------------------------------------------------

private val greetings = listOf("今日", "你好", "안녕", "नमस्ते", "Ciao", "Hola", "مرحبا", "Привет", "Bonjour", "Hallo")
private val weekdayKanji = listOf("月", "火", "水", "木", "金", "土", "日")

/** The soft accent colour when it stands out from the tile, otherwise the plain ink, so the word is always readable. */
internal fun kanjiInk(fill: Color, soft: Color, ink: Color): Color {
    val a = soft.luminance() + 0.05f
    val b = fill.luminance() + 0.05f
    val ratio = if (a > b) a / b else b / a
    return if (ratio >= 4f) soft else ink
}

private fun isCjk(c: Char) = c.code in 0x3040..0x30FF || c.code in 0x3400..0x9FFF || c.code in 0xAC00..0xD7AF

/** A big word in the tile, centred. Your own words, today's weekday, or a greeting that changes every hour. */
@Composable
fun KanjiTile(bg: Color, fg: Color, cfg: app.koma.launcher.data.WordsCfg) {
    val now by rememberNow()
    var text = when (cfg.mode) {
        "day" -> weekdayKanji[now.dayOfWeek.value - 1] + "曜日"
        "greet" -> greetings[(now.dayOfYear * 24 + now.hour) % greetings.size]
        else -> cfg.text.ifBlank { "今日" }
    }
    if (cfg.caps) text = text.uppercase()
    val stacked = when (cfg.layout) {
        "stack" -> text.length <= 8
        "across" -> false
        else -> text.length <= 4 && text.all { isCjk(it) }
    }
    val shown = if (stacked) text.toList().joinToString("\n") else text
    val style = TextStyle(
        fontFamily = when (cfg.style) {
            "light" -> Fonts.Thin
            "serif" -> FontFamily.Serif
            "display" -> Fonts.Display
            else -> FontFamily.SansSerif
        },
        fontWeight = when (cfg.style) {
            "bold" -> FontWeight.Black
            "serif" -> FontWeight.Bold
            else -> FontWeight.Normal
        },
        color = fg.copy(alpha = if (cfg.faded) 0.4f else 1f),
        textAlign = TextAlign.Center,
        lineHeight = 1.0.em,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    )
    Box(Modifier.fillMaxSize().clip(TileShape).background(bg).padding(10.dp), contentAlignment = Alignment.Center) {
        FitText(AnnotatedString(shown), style, Modifier.fillMaxSize(), lines = if (stacked) text.length else 1, align = Alignment.Center)
    }
}

// ---- search ------------------------------------------------------------------------------------

@Composable
fun Magnifier(color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = Stroke(width = s * 0.11f, cap = StrokeCap.Round)
        drawCircle(color, radius = s * 0.31f, center = Offset(s * 0.43f, s * 0.43f), style = stroke)
        drawLine(color, Offset(s * 0.66f, s * 0.66f), Offset(s * 0.92f, s * 0.92f), strokeWidth = s * 0.11f, cap = StrokeCap.Round)
    }
}

// ---- apps and shortcuts ------------------------------------------------------------------------

/** Icon settings shared by every icon on screen. [sig] changes whenever a setting does, so icons redraw. */
class IconCfg(val style: String, val shape: String, val force: Boolean, val sig: String)

/** The badge shape for shortcut tiles, from settings. */
val LocalBadge = staticCompositionLocalOf { "circle" }

val LocalIconCfg = staticCompositionLocalOf { IconCfg("koma", "squircle", true, "") }

/**
 * An app icon in the chosen style. [bg] and [ink] colour the "koma" and "material" styles; [line] colours
 * the styles drawn straight onto what is behind them (outline).
 */
@Composable
fun AppIcon(icons: IconCache, app: AppInfo?, size: Dp, bg: Color = Color.Gray, ink: Color = Color.Black, line: Color = Color.White) {
    val cfg = LocalIconCfg.current
    val keyStr = "${cfg.sig}|${app?.key}|${bg.toArgb()}|${ink.toArgb()}|${line.toArgb()}"
    val bmp by produceState<ImageBitmap?>(null, keyStr) {
        value = app?.let {
            withContext(Dispatchers.Default) {
                icons.styled(it, IconSpec(cfg.style, cfg.shape, bg.toArgb(), ink.toArgb(), line.toArgb(), cfg.force))
            }
        }
    }
    val b = bmp
    if (b != null) Image(bitmap = b, contentDescription = null, modifier = Modifier.size(size))
    else Spacer(Modifier.size(size))
}

internal fun iconShapeOf(shape: String): Shape = when (shape) {
    "circle" -> CircleShape
    "rounded" -> RoundedCornerShape(22)
    "teardrop" -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 50, bottomStartPercent = 12)
    "square" -> RoundedCornerShape(10)
    else -> RoundedCornerShape(34)
}

/**
 * One of Koma's own icons (settings, built-in apps) drawn in the same style and shape as the installed apps,
 * so the drawer reads as one set.
 */
@Composable
fun GlyphIcon(glyph: String, size: Dp, bg: Color, ink: Color, line: Color) {
    val cfg = LocalIconCfg.current
    val shape = iconShapeOf(cfg.shape)
    val inner = size * 0.56f
    when (cfg.style) {
        "outline", "sticker", "dots", "watercolor" -> Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Glyph(glyph, if (cfg.style == "outline") line else bg.opaque(), size * 0.64f, 2.2f)
        }
        "material" -> Box(Modifier.size(size).clip(shape).background(lerp(bg, Color.White, 0.72f)), contentAlignment = Alignment.Center) {
            Glyph(glyph, ink, inner, 2f)
        }
        "marble" -> Box(
            Modifier.size(size).clip(shape).background(Brush.linearGradient(listOf(lerp(bg, Color.White, 0.3f), lerp(bg, Color.Black, 0.35f)))),
            contentAlignment = Alignment.Center,
        ) { Glyph(glyph, Color.White, inner, 2f) }
        "stellar" -> Box(Modifier.size(size).clip(shape).background(Color(0xFF0A0B16)), contentAlignment = Alignment.Center) {
            Glyph(glyph, Color.White, inner, 2f)
        }
        "chroma" -> Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Glyph(glyph, bg.opaque(), size * 0.64f, 2.2f)
        }
        else -> Box(Modifier.size(size).clip(shape).background(bg), contentAlignment = Alignment.Center) {
            Glyph(glyph, ink, inner, 2f)
        }
    }
}

/** An app icon cut out and forced into one colour so it matches the tile. */
@Composable
internal fun InkIcon(icons: IconCache, app: AppInfo?, ink: Color, size: Dp) {
    val sig = LocalIconCfg.current.sig
    val icon by produceState<app.koma.launcher.data.Icon?>(null, app?.key, sig) {
        value = app?.let { withContext(Dispatchers.Default) { icons.silhouette(it) } }
    }
    val i = icon
    if (i != null) {
        Image(
            bitmap = i.bitmap,
            contentDescription = null,
            modifier = Modifier.size(size),
            colorFilter = if (i.mask) ColorFilter.tint(ink) else null,
        )
    } else Spacer(Modifier.size(size))
}

/**
 * A square shortcut tile. Role shortcuts (CAM, WEB, ...) draw a line glyph and a small label in the
 * corner, like the design. Other apps follow the icon style in settings: forced single colour, or a styled icon.
 */
@Composable
fun AppTile(
    app: AppInfo?,
    name: String,
    glyph: String,
    icons: IconCache,
    bg: Color,
    ink: Color,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().clip(TileShape).background(bg)) {
        // A solid medallion in the corner holds the icon, cut out in the tile's colour; it scales with the tile.
        val side = minOf(maxWidth, maxHeight)
        val medal = (side * 0.44f).coerceIn(30.dp, 60.dp)
        val badge = LocalBadge.current
        Box(Modifier.padding(start = 10.dp, top = 10.dp).size(medal), contentAlignment = Alignment.Center) {
            run {
                val solid = badge != "ring" && badge != "plain"
                val tint = if (solid) bg else ink
                val frame = when (badge) {
                    "plain" -> Modifier
                    "ring" -> Modifier.clip(iconShapeOf("circle")).border(2.dp, ink, iconShapeOf("circle"))
                    else -> Modifier.clip(iconShapeOf(badge)).background(ink)
                }
                Box(Modifier.fillMaxSize().then(frame), contentAlignment = Alignment.Center) {
                    val inner = if (badge == "plain") medal * 0.9f else medal * 0.56f
                    if (glyph.isNotEmpty()) Glyph(glyph, tint, inner, 2.3f)
                    else InkIcon(icons, app, tint, if (badge == "plain") medal * 0.9f else medal * 0.62f)
                }
            }
        }
        BasicText(
            (name.ifEmpty { app?.label ?: "" }).uppercase(),
            style = label(ink, 10.sp).copy(letterSpacing = 0.08.em),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 8.dp, end = 8.dp),
        )
    }
}

/** The "ALL" tile that opens the drawer: dark with an outline. */
@Composable
fun AllAppsTile(ground: Color, fg: Color) {
    Box(
        Modifier.fillMaxSize().clip(TileShape).background(ground).border(2.dp, fg, TileShape),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Glyph("all", fg, 30.dp, 2.2f) }
        BasicText(
            "ALL",
            style = label(fg, 10.sp, bold = true).copy(letterSpacing = 0.08.em),
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 6.dp),
        )
    }
}

// ---- weather -----------------------------------------------------------------------------------

@Composable
fun WeatherTile(
    w: Int, h: Int, bg: Color, ink: Color, granted: Boolean, load: suspend () -> Weather?, cached: Weather?,
    showHiLo: Boolean = true, showCat: Boolean = true, showCity: Boolean = false, source: String = "", fahrenheit: Boolean = false,
    load2: suspend () -> Weather? = { null }, cached2: Weather? = null, hasSecond: Boolean = false,
) {
    if (w >= 4) {
        WeatherPair(h, bg, ink, granted, load, cached, load2, cached2, hasSecond, showHiLo, showCat, source, fahrenheit)
        return
    }
    val weather by produceState(cached, granted, source) {
        while (true) {
            awaitVisible()
            if (granted) load()?.let { value = it }
            delay(10 * 60_000L)
        }
    }
    val data = weather
    val (cond, glyph) = if (data != null) WeatherRepo.describe(data.code) else (if (granted) "No data" else "Tap for location") to "cloud"
    val temp = if (data != null) "${data.temp}°" else "--°"
    val mood = if (data == null) "cloud" else catMood(glyph, data.day)
    Box(Modifier.fillMaxSize().clip(TileShape).background(bg)) {
        when {
            w >= 2 && h >= 2 -> androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                // The cat shrinks on narrow tiles so it never covers the High / Low text.
                val catW = (maxWidth - 32.dp - 66.dp).coerceIn(56.dp, 124.dp)
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Glyph(glyph, ink, 28.dp, 2f)
                        BasicText(cond.uppercase(), style = label(ink, 11.sp).copy(letterSpacing = 0.1.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    BasicText(temp, style = display(if (showCity) 56.sp else 68.sp, ink, (-0.06).em, 1f), modifier = Modifier.padding(top = if (showCity) 2.dp else 8.dp))
                    if (showCity && data != null) BasicText(data.city, style = label(ink, 10.sp).copy(letterSpacing = 0.1.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.weight(1f))
                    if (showHiLo && data != null) BasicText(if (showCity) "H ${data.hi}  L ${data.lo}" else "H ${data.hi}\nL ${data.lo}", style = label(ink, 11.sp).copy(letterSpacing = 0.1.em))
                }
                if (showCat) Cat(mood = mood, cup = catCup(mood, data?.temp, fahrenheit), modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp).size(width = catW, height = catW * 84 / 124))
            }
            w >= 2 -> Row(
                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Glyph(glyph, ink, 22.dp, 2f)
                        BasicText(cond.uppercase(), style = label(ink, 10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (data != null && showCity) BasicText(data.city, style = label(ink, 10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    else if (data != null && showHiLo) BasicText("H ${data.hi}  L ${data.lo}", style = label(ink, 10.sp), maxLines = 1)
                }
                BasicText(temp, style = display(40.sp, ink, (-0.05).em))
            }
            else -> Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Glyph(glyph, ink, 24.dp, 2f)
                BasicText(temp, style = display(30.sp, ink, (-0.05).em))
            }
        }
    }
}

// ---- calendar ----------------------------------------------------------------------------------

/** The colour of an account's calendars: the wallpaper colour chosen in settings, else one the palette gives the account. */
internal fun calendarTone(chosen: Int?, account: String, all: List<Color>): Color = all[(chosen ?: Math.abs(account.hashCode())) % all.size]

@Composable
private fun MonthGrid(now: LocalDateTime, eventDays: Map<LocalDate, Color>, bg: Color, ink: Color) {
    val first = now.toLocalDate().withDayOfMonth(1)
    val offset = first.dayOfWeek.value - 1
    val rows = (offset + first.lengthOfMonth() + 6) / 7
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            "MTWTFSS".forEachIndexed { i, ch ->
                Box(Modifier.weight(1f).padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
                    BasicText(ch.toString(), style = label(ink.copy(alpha = if (i >= 5) 0.5f else 0.85f), 9.sp, bold = true))
                }
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (c in 0 until 7) {
                    val day = r * 7 + c - offset + 1
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (day in 1..first.lengthOfMonth()) {
                            val date = first.withDayOfMonth(day)
                            val today = date == now.toLocalDate()
                            val past = date < now.toLocalDate()
                            val weekend = c >= 5
                            val dot = eventDays[date]
                            val hasEv = dot != null
                            Box(
                                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (today) ink else if (dot != null) dot.copy(alpha = 0.22f) else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                BasicText(
                                    day.toString(),
                                    style = label(if (today) bg.opaque() else ink.copy(alpha = if (past) 0.4f else if (weekend) 0.65f else 1f), 11.sp, bold = today || hasEv),
                                )
                            }
                            if (dot != null && !today) {
                                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp).size(width = 10.dp, height = 3.dp).clip(CircleShape).background(dot))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarTile(w: Int, h: Int, bg: Color, ink: Color, granted: Boolean, hidden: Set<Long>, colors: Map<String, Int>, refreshMin: Int = 60) {
    val ctx = LocalContext.current
    val every by androidx.compose.runtime.rememberUpdatedState(refreshMin)
    val all = LocalPalette.current.all
    fun tone(e: Event) = calendarTone(colors[e.account], e.account, all)
    val is24 = app.koma.launcher.data.TimeFmt.is24()
    val month = w >= 4 && h >= 4
    val snapshot by produceState(0L to emptyList<Event>(), granted, hidden, month) {
        var events = emptyList<Event>()
        var fetched = 0L
        while (true) {
            awaitVisible()
            // The calendar is read as often as the setting says; the "now" marker moves every minute without touching it.
            if (System.currentTimeMillis() - fetched >= every * 60_000L) {
                events = if (granted) {
                    withContext(Dispatchers.IO) { CalendarRepo.upcoming(ctx, hidden, days = if (month) 42 else 7, limit = if (month) 200 else 40) }
                } else emptyList()
                fetched = System.currentTimeMillis()
            }
            value = System.currentTimeMillis() to events
            sleepOrResume(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val (nowMs, upcoming) = snapshot
    // Only today: what is still on, or yet to start, before midnight. The month view keeps every day for its dots.
    val events = remember(upcoming, nowMs) {
        val endOfDay = java.time.LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        upcoming.filter { it.begin < endOfDay }
    }
    val next = events.firstOrNull()
    val title = when {
        !granted -> "Allow calendar"
        next == null -> "Nothing today"
        else -> next.title
    }
    val whenText = if (next != null) untilText(next, nowMs) else ""
    val clockText = when {
        next == null -> ""
        next.allDay -> "ALL DAY"
        else -> timeText(next.begin, is24)
    }
    val now by rememberNow()
    Box(Modifier.fillMaxSize().clip(TileShape).background(bg)) {
        when {
            // Month view with a dot under days that have events, and the next event below.
            month -> Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val z = ZoneId.systemDefault()
                val todayCount = events.size
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        BasicText(now.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())), style = display(26.sp, ink, (-0.03).em, 1f), maxLines = 1)
                        BasicText(now.format(DateTimeFormatter.ofPattern("yyyy", Locale.getDefault())), style = label(ink.copy(alpha = 0.6f), 10.sp, bold = true))
                    }
                    Box(Modifier.clip(RoundedCornerShape(9.dp)).background(ink.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 5.dp)) {
                        BasicText(
                            if (!granted) "ALLOW CALENDAR" else if (todayCount == 0) "FREE TODAY" else "$todayCount TODAY",
                            style = label(ink, 9.sp, bold = true), maxLines = 1,
                        )
                    }
                }
                Box(Modifier.weight(1f)) {
                    // Each day with something on gets the colour of its first event.
                    val days = remember(upcoming, colors) {
                        upcoming.flatMap { e ->
                            val a = Instant.ofEpochMilli(e.begin).atZone(z).toLocalDate()
                            val b = Instant.ofEpochMilli(maxOf(e.begin, e.end - 1)).atZone(z).toLocalDate()
                            generateSequence(a) { d -> if (d < b) d.plusDays(1) else null }.map { it to tone(e) }.toList()
                        }.distinctBy { it.first }.toMap()
                    }
                    MonthGrid(now, days, bg, ink)
                }
                // The next few things coming up, across days, each tagged with its day.
                Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.18f)))
                val coming = upcoming.take(60)
                if (coming.isEmpty()) {
                    BasicText(title, style = label(ink.copy(alpha = 0.8f), 10.sp, bold = true), maxLines = 1)
                } else Column(
                    Modifier.fillMaxWidth().then(Modifier.heightIn(max = if (h >= 5) 56.dp else 40.dp)).verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { coming.forEach { e ->
                    val d = Instant.ofEpochMilli(e.begin).atZone(z).toLocalDate()
                    val tag = if (d == now.toLocalDate()) "TODAY" else d.format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())).uppercase()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(width = 4.dp, height = 16.dp).clip(RoundedCornerShape(2.dp)).background(tone(e)))
                        Box(Modifier.width(54.dp)) { BasicText(tag, style = label(ink.copy(alpha = 0.7f), 9.sp, bold = true), maxLines = 1) }
                        BasicText(e.title, style = display(13.sp, ink, 0.em, 1f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        BasicText(if (e.allDay) "ALL DAY" else timeText(e.begin, is24), style = label(ink.copy(alpha = 0.7f), 9.sp, bold = true), maxLines = 1)
                    }
                } }
            }
            // 4x2: today on the left, agenda on the right.
            w >= 4 && h >= 2 -> Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.width(84.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    BasicText(now.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).uppercase(), style = label(ink, 11.sp, bold = true))
                    BasicText(now.dayOfMonth.toString(), style = display(64.sp, ink, (-0.05).em, 1f))
                    BasicText(now.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())).uppercase(), style = label(ink, 11.sp))
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(if (events.isEmpty()) "TODAY" else "${events.size} TODAY", style = label(ink, 10.sp))
                    if (!granted || events.isEmpty()) {
                        BasicText(title, style = display(17.sp, ink, (-0.01).em, 1.05f))
                    } else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        events.forEach { e -> AgendaRow(e, nowMs, is24, ink, tone(e)) }
                    }
                }
            }
            // 4x1 strip from the design.
            w >= 4 && h == 1 -> Row(
                Modifier.fillMaxSize().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    BasicText(if (whenText.isEmpty()) "NEXT UP" else "NEXT UP / $whenText", style = label(ink, 10.sp), maxLines = 1)
                    BasicText(
                        title,
                        style = display(25.sp, ink, (-0.02).em, 1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (clockText.isNotEmpty()) BasicText(clockText, style = thin(if (clockText.length > 5) 28.sp else 54.sp, ink), maxLines = 1)
            }
            // Agenda list.
            w >= 2 && h >= 2 -> Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicText(if (whenText.isEmpty()) "TODAY" else "TODAY / ${events.size}", style = label(ink, 10.sp))
                if (!granted || events.isEmpty()) {
                    BasicText(title, style = display(16.sp, ink, (-0.01).em, 1.05f))
                } else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    events.forEach { e -> AgendaRow(e, nowMs, is24, ink, tone(e)) }
                }
            }
            // 2x1: next event.
            w >= 2 -> Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                BasicText(if (whenText.isEmpty()) "NEXT UP" else whenText, style = label(ink, 10.sp), maxLines = 1)
                BasicText(title, style = display(17.sp, ink, (-0.01).em, 1.05f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            // 1x1: today's date.
            else -> Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                BasicText(now.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).uppercase(), style = label(ink, 10.sp, bold = true))
                BasicText(now.dayOfMonth.toString(), style = display(36.sp, ink, (-0.05).em))
            }
        }
    }
}

@Composable
private fun AgendaRow(e: Event, nowMs: Long, is24: Boolean, ink: Color, tone: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(4.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(tone))
        Column {
            BasicText(e.title, style = display(13.sp, ink, 0.em, 1.05f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(
                (if (e.allDay) "ALL DAY" else timeText(e.begin, is24)) + " / " + untilText(e, nowMs),
                style = label(ink, 9.sp),
                maxLines = 1,
            )
        }
    }
}

// ---- music -------------------------------------------------------------------------------------

@Composable
private fun RoundButton(size: Dp, bg: Color, fg: Color, glyph: String, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(glyph, fg, size * 0.44f, filled = true) }
}

@Composable
fun MusicTile(
    w: Int,
    h: Int,
    bg: Color,
    ink: Color,
    title: String,
    artist: String,
    playing: Boolean,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    now: app.koma.launcher.data.NowPlaying? = null,
    onSeek: (Long) -> Unit = {},
    style: String = "tape",
    design: String = "",
) {
    val pal = LocalPalette.current
    val cassetteBody = pal.tiles[0]
    val cassetteStripe = pal.tiles[1 % pal.tiles.size]
    val cassetteLabel = pal.tiles[4 % pal.tiles.size]
    Box(Modifier.fillMaxSize().clip(TileShape).background(bg)) {
        when {
            w >= 2 && style != "tape" -> MusicStyled(style, design, w, bg, ink, title, artist, playing, now, onToggle, onNext, onPrevious, onSeek)
            w >= 4 && h >= 2 -> Row(Modifier.fillMaxSize().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Cassette(Modifier.fillMaxHeight().weight(1f), cassetteBody, cassetteStripe, cassetteLabel, ink)
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    StatusChip(if (playing) "NOW PLAYING" else "PAUSED", playing, ink)
                    Column {
                        BasicText(title.uppercase(), style = display(20.sp, ink, (-0.01).em, 1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        BasicText(artist.uppercase(), style = label(ink, 10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                    SongProgress(now, ink, true, onSeek)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        MusicBtn(36.dp, ink, bg.opaque(), "prev", onPrevious)
                        MusicBtn(46.dp, ink, bg.opaque(), if (playing) "pause" else "play", onToggle)
                        MusicBtn(36.dp, ink, bg.opaque(), "next", onNext)
                    }
                }
            }
            w >= 2 && h >= 2 -> Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(if (playing) "NOW PLAYING" else "PAUSED", playing, ink)
                }
                Cassette(Modifier.fillMaxWidth().height(60.dp), cassetteBody, cassetteStripe, cassetteLabel, ink)
                Column {
                    BasicText(title.uppercase(), style = display(16.sp, ink, (-0.01).em, 1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(artist.uppercase(), style = label(ink, 10.sp).copy(letterSpacing = 0.08.em), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                }
                SongProgress(now, ink, false, onSeek)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    MusicBtn(34.dp, ink, bg.opaque(), "prev", onPrevious)
                    MusicBtn(44.dp, ink, bg.opaque(), if (playing) "pause" else "play", onToggle)
                    MusicBtn(34.dp, ink, bg.opaque(), "next", onNext)
                }
            }
            w >= 2 -> Row(
                Modifier.fillMaxSize().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // A 4x1 strip has room for the cassette on the left and all three controls on the right.
                if (w >= 4) Cassette(Modifier.height(46.dp).width(72.dp), cassetteBody, cassetteStripe, cassetteLabel, ink)
                Column(Modifier.weight(1f).padding(horizontal = if (w >= 4) 12.dp else 0.dp).padding(end = if (w >= 4) 0.dp else 8.dp)) {
                    BasicText(if (playing) "NOW PLAYING" else "PAUSED", style = label(ink, 9.sp))
                    BasicText(title.uppercase(), style = display(15.sp, ink, (-0.01).em, 1f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                    BasicText(artist.uppercase(), style = label(ink, 9.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    SongProgress(now, ink, false, onSeek)
                }
                if (w >= 4) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MusicBtn(32.dp, ink, bg.opaque(), "prev", onPrevious)
                    MusicBtn(40.dp, ink, bg.opaque(), if (playing) "pause" else "play", onToggle)
                    MusicBtn(32.dp, ink, bg.opaque(), "next", onNext)
                } else MusicBtn(40.dp, ink, bg.opaque(), if (playing) "pause" else "play", onToggle)
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MusicBtn(44.dp, ink, bg.opaque(), if (playing) "pause" else "play", onToggle)
            }
        }
    }
}

/** A control for the music tiles: a rounded square. Play and pause are solid and wider, skip is a soft tint. */
@Composable
private fun MusicBtn(size: Dp, fg: Color, onFg: Color, glyph: String, onClick: () -> Unit) {
    val main = glyph == "play" || glyph == "pause"
    Box(
        Modifier.width(if (main) size * 1.4f else size).height(size).clip(RoundedCornerShape(size * 0.34f))
            .background(if (main) fg else fg.copy(alpha = 0.14f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(glyph, if (main) onFg else fg, size * 0.44f, filled = true) }
}

/** The small "now playing / paused" tag: a square dot that is solid while music plays. */
@Composable
private fun StatusChip(text: String, playing: Boolean, ink: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(ink.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(2.dp)).background(if (playing) ink else ink.copy(alpha = 0.35f)))
        BasicText(text, style = label(ink, 9.sp, bold = true), maxLines = 1)
    }
}

internal val musicStyles = listOf("tape" to "TAPE", "vinyl" to "VINYL", "bars" to "BARS", "poster" to "POSTER", "ring" to "RING")

/** A record that turns while the music plays, with a tonearm that swings onto it. It keeps its angle when paused. */
@Composable
private fun Record(playing: Boolean, ink: Color, labelColor: Color, modifier: Modifier) {
    val angle = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            var last = androidx.compose.runtime.withFrameNanos { it }
            while (true) {
                // No frames are requested while Koma is hidden, and the first one after it returns does not jump.
                awaitVisible()
                val n = androidx.compose.runtime.withFrameNanos { it }
                angle.floatValue = (angle.floatValue + (n - last).coerceAtMost(100_000_000L) / 1_000_000_000f * 80f) % 360f
                last = n
            }
        }
    }
    val arm = androidx.compose.animation.core.animateFloatAsState(
        if (playing) 1f else 0f, androidx.compose.animation.core.tween(700), label = "arm",
    ).value
    Box(modifier.aspectRatio(1f)) {
        Canvas(Modifier.fillMaxSize().rotate(angle.floatValue)) {
            val r = size.minDimension / 2f
            drawCircle(Color(0xFF111111), r)
            // grooves: many fine rings, a darker band between tracks
            var k = 0.97f
            var i = 0
            while (k > 0.46f) {
                drawCircle(Color.White.copy(alpha = if (i % 9 == 0) 0.02f else 0.07f), r * k, style = Stroke(if (i % 9 == 0) 3f else 1f))
                k -= 0.028f
                i++
            }
            // light catching the grooves
            drawArc(
                Color.White.copy(alpha = 0.16f), 205f, 32f, false,
                topLeft = Offset(r * 0.1f, r * 0.1f), size = androidx.compose.ui.geometry.Size(size.width - r * 0.2f, size.height - r * 0.2f),
                style = Stroke(r * 0.16f),
            )
            drawArc(
                Color.White.copy(alpha = 0.1f), 25f, 32f, false,
                topLeft = Offset(r * 0.1f, r * 0.1f), size = androidx.compose.ui.geometry.Size(size.width - r * 0.2f, size.height - r * 0.2f),
                style = Stroke(r * 0.16f),
            )
            drawCircle(labelColor, r * 0.38f)
            drawCircle(Color.Black.copy(alpha = 0.12f), r * 0.38f, style = Stroke(r * 0.03f))
            drawCircle(Color.Black.copy(alpha = 0.18f), r * 0.2f, style = Stroke(r * 0.012f))
            drawCircle(Color(0xFF111111), r * 0.065f)
        }
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val pivot = Offset(w * 0.94f, w * 0.08f)
            val rest = Offset(w * 0.97f, w * 0.52f)
            val play = Offset(w * 0.66f, w * 0.34f)
            val tip = Offset(rest.x + (play.x - rest.x) * arm, rest.y + (play.y - rest.y) * arm)
            val sw = w * 0.035f
            drawLine(Color.Black.copy(alpha = 0.3f), pivot + Offset(2f, 3f), tip + Offset(2f, 3f), strokeWidth = sw * 1.4f, cap = StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.92f), pivot, tip, strokeWidth = sw, cap = StrokeCap.Round)
            drawCircle(Color.White, w * 0.06f, pivot)
            drawCircle(Color.Black.copy(alpha = 0.35f), w * 0.025f, pivot)
            drawCircle(Color.White, w * 0.03f, tip)
        }
    }
}

private fun n(i: Int) = (i * 7) % 5

/**
 * A waveform that doubles as the progress bar: the bars already played are solid, the rest are faded,
 * and the bars move a little while the music plays. Tap or drag to jump.
 */
@Composable
private fun WaveProgress(
    now: app.koma.launcher.data.NowPlaying?, playing: Boolean, ink: Color, seed: String, count: Int,
    modifier: Modifier, onSeek: (Long) -> Unit,
) {
    val pos by produceState(now?.current() ?: 0L, now, playing) {
        value = now?.current() ?: 0L
        while (playing) {
            awaitVisible()
            delay(400)
            value = now?.current() ?: 0L
        }
    }
    val base = remember(seed, count) {
        val r = java.util.Random(seed.hashCode().toLong())
        val raw = FloatArray(count) { 0.25f + 0.75f * r.nextFloat() }
        FloatArray(count) { i -> (raw[i] * 0.6f + raw[(i + 1) % count] * 0.2f + raw[(i + count - 1) % count] * 0.2f).coerceIn(0.2f, 1f) }
    }
    val shown by Visibility.shown.collectAsState()
    val phase = if (playing && shown) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "wave").animateFloat(
            0f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.LinearEasing)),
            label = "phase",
        ).value
    } else 0f
    val seekable = playing && now != null && now.duration > 0L
    val drag = remember { mutableStateOf<Float?>(null) }
    val frac = drag.value ?: if (seekable) (pos.toFloat() / now.duration).coerceIn(0f, 1f) else 0f
    Canvas(
        modifier
            .pointerInput(seekable, now?.duration) {
                if (seekable) detectTapGestures { o -> onSeek((o.x / size.width * now.duration).toLong()) }
            }
            .pointerInput(seekable, now?.duration) {
                if (seekable) detectHorizontalDragGestures(
                    onDragStart = { o -> drag.value = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { drag.value?.let { onSeek((it * now.duration).toLong()) }; drag.value = null },
                    onDragCancel = { drag.value = null },
                ) { change, _ -> drag.value = (change.position.x / size.width).coerceIn(0f, 1f) }
            },
    ) {
        val gap = 3.dp.toPx()
        val bw = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
        val mid = size.height / 2f
        for (i in 0 until count) {
            val wob = if (playing) 0.78f + 0.22f * kotlin.math.sin((phase * 2f + i * 0.17f) * 6.2832f) else 1f
            val hh = (size.height * base[i] * wob).coerceAtLeast(bw)
            val played = (i + 0.5f) / count <= frac
            drawRoundRect(
                if (played) ink else ink.copy(alpha = 0.28f),
                Offset(i * (bw + gap), mid - hh / 2f),
                androidx.compose.ui.geometry.Size(bw, hh),
                androidx.compose.ui.geometry.CornerRadius(bw / 2f),
            )
        }
    }
}

/** A ring that fills as the song goes, with the play button in the middle. */
@Composable
private fun ProgressRing(now: app.koma.launcher.data.NowPlaying?, playing: Boolean, ink: Color, bg: Color, size: Dp, onToggle: () -> Unit) {
    val pos by produceState(now?.current() ?: 0L, now, playing) {
        value = now?.current() ?: 0L
        while (playing) {
            awaitVisible()
            delay(500)
            value = now?.current() ?: 0L
        }
    }
    val frac = if (playing && now != null && now.duration > 0L) (pos.toFloat() / now.duration).coerceIn(0f, 1f) else 0f
    val strokeDp = (size.value * 0.075f).coerceIn(5f, 14f).dp
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = strokeDp.toPx()
            val box = androidx.compose.ui.geometry.Size(this.size.width - sw, this.size.height - sw)
            drawArc(ink.copy(alpha = 0.22f), 0f, 360f, false, Offset(sw / 2, sw / 2), box, style = Stroke(sw))
            drawArc(ink, -90f, 360f * frac, false, Offset(sw / 2, sw / 2), box, style = Stroke(sw, cap = StrokeCap.Round))
        }
        RoundButton(size * 0.62f, ink, bg.opaque(), if (playing) "pause" else "play", onToggle)
    }
}

/**
 * The alternative looks for a music tile of 2x1 or more. Every layout is worked out from the room the tile really has:
 * a short strip puts the picture, the names and a button in one row, a wide tile puts the picture beside the details,
 * and a tall or square one stacks them. Sizes grow with the tile, so a large tile fills out instead of floating.
 * The tape look is in [MusicTile] itself.
 */
@Composable
private fun MusicStyled(
    style: String, design: String, w: Int, bg: Color, ink0: Color, title: String, artist: String, playing: Boolean,
    now: app.koma.launcher.data.NowPlaying?, onToggle: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, onSeek: (Long) -> Unit,
) {
    val pal = LocalPalette.current
    // Backdrop design: the cover art fills the tile and the text turns black or white to suit it.
    val photoArt = now?.art
    val backdrop = style == "poster" && design == "cover" && photoArt != null
    val light = remember(photoArt) { photoArt?.let { isLightImage(it.asImageBitmap()) } }
    val ink = if (backdrop && light != null) (if (light) Color(0xFF111111) else Color(0xFFFFFFFF)) else ink0
    // Solid so the play button's glyph keeps its contrast; the scrim below evens out busy pictures.
    val ob = if (backdrop) (if (light == true) Color.White else Color(0xFF111111)) else bg.opaque()
    val status = if (playing) "NOW PLAYING" else "PAUSED"
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val bw = maxWidth
        val bh = maxHeight
        val short = bh < 120.dp
        val landscape = !short && bw > bh * 1.45f
        // How much bigger than a 2x2 this tile is; text and buttons follow it.
        val u = (minOf(bw, bh) / 150.dp).coerceIn(0.9f, 2.2f)
        val k = u.coerceIn(1f, 1.6f)
        val pad = if (short) 10.dp else 12.dp * u.coerceAtMost(1.5f)
        val inner = bh - pad * 2
        @Composable
        fun controls(big: Boolean, fg: Color = ink, bgc: Color = ob) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp * k), verticalAlignment = Alignment.CenterVertically) {
                MusicBtn((if (big) 36.dp else 32.dp) * k, fg, bgc, "prev", onPrevious)
                MusicBtn((if (big) 46.dp else 40.dp) * k, fg, bgc, if (playing) "pause" else "play", onToggle)
                MusicBtn((if (big) 36.dp else 32.dp) * k, fg, bgc, "next", onNext)
            }
        }
        @Composable
        fun names(titleSize: Int, lines: Int) {
            Column {
                BasicText(title.uppercase(), style = display((titleSize * u).sp, ink, (-0.01).em, 1f), maxLines = lines, overflow = TextOverflow.Ellipsis)
                BasicText(artist.uppercase(), style = label(ink, (10 * k).sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp * k))
            }
        }
        // The buttons at the end of a short strip: play, and previous and next too when the strip is wide enough.
        @Composable
        fun stripButtons() {
            if (bw >= 300.dp) controls(false) else MusicBtn(40.dp, ink, ob, if (playing) "pause" else "play", onToggle)
        }
        if (photoArt != null && backdrop) {
            Image(photoArt.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            // A wash in the opposite colour to the text, so text stays readable on busy or mixed pictures.
            Box(Modifier.fillMaxSize().background(if (light == true) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)))
        }
        when (style) {
            "vinyl" -> when {
                short -> Row(Modifier.fillMaxSize().padding(pad), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Record(playing, ink, pal.tiles[0], Modifier.height(inner))
                    Box(Modifier.weight(1f)) { names(13, 1) }
                    stripButtons()
                }
                landscape -> Row(Modifier.fillMaxSize().padding(pad), horizontalArrangement = Arrangement.spacedBy(14.dp * k)) {
                    Record(playing, ink, pal.tiles[0], Modifier.fillMaxHeight())
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                        StatusChip(status, playing, ink)
                        names(20, 2)
                        SongProgress(now, ink, true, onSeek)
                        controls(true)
                    }
                }
                else -> Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
                    Record(playing, ink, pal.tiles[0], Modifier.height(minOf(bh * 0.42f, bw - pad * 2)))
                    Column(Modifier.fillMaxWidth()) {
                        names(15, 2)
                        SongProgress(now, ink, bh > 220.dp, onSeek)
                    }
                    controls(bh > 220.dp)
                }
            }
            "bars" -> when {
                short -> Row(Modifier.fillMaxSize().padding(pad), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        names(13, 1)
                        WaveProgress(now, playing, ink, title + artist, if (bw >= 300.dp) 40 else 20, Modifier.fillMaxWidth().height(22.dp), onSeek)
                    }
                    stripButtons()
                }
                landscape -> Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            StatusChip(status, playing, ink)
                            Spacer(Modifier.height(6.dp))
                            names(24, 1)
                        }
                        if (playing && now != null && now.duration > 0L) BasicText(clock(now.duration), style = label(ink, (10 * k).sp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        controls(true)
                        WaveProgress(now, playing, ink, title + artist, 36, Modifier.weight(1f).height(52.dp * k), onSeek)
                    }
                }
                else -> Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.SpaceBetween) {
                    StatusChip(status, playing, ink)
                    names(17, 2)
                    WaveProgress(now, playing, ink, title + artist, if (bw >= 260.dp) 36 else 22, Modifier.fillMaxWidth().height(maxOf(34.dp, bh * 0.2f)), onSeek)
                    controls(bh > 220.dp)
                }
            }
            "poster" -> {
                val art = now?.art
                val shape = if (design == "arch") RoundedCornerShape(topStart = CornerSize(50), topEnd = CornerSize(50), bottomEnd = CornerSize(14.dp), bottomStart = CornerSize(14.dp)) else RoundedCornerShape(14.dp)
                val left = design == "left"
                // The backdrop design draws the art behind everything, so there is no separate cover.
                val cover: @Composable (Modifier) -> Unit = { m ->
                    if (backdrop) Unit
                    else if (art != null) Image(art.asImageBitmap(), null, m.aspectRatio(1f).clip(shape), contentScale = ContentScale.Crop)
                    else Box(m.aspectRatio(1f).clip(shape).background(ink.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { Glyph("music", ink, 26.dp * k, 2f) }
                }
                when {
                    short -> Row(Modifier.fillMaxSize().padding(pad), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (left) cover(Modifier.size(inner))
                        Box(Modifier.weight(1f)) { names(13, 1) }
                        if (!left) cover(Modifier.size(inner))
                        stripButtons()
                    }
                    landscape -> Row(Modifier.fillMaxSize().padding(pad), horizontalArrangement = Arrangement.spacedBy(14.dp * k)) {
                        if (left) cover(Modifier.fillMaxHeight())
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                            StatusChip(status, playing, ink)
                            names(22, 2)
                            SongProgress(now, ink, true, onSeek)
                            controls(true)
                        }
                        if (!left) cover(Modifier.fillMaxHeight())
                    }
                    else -> {
                        // Square or tall: the picture grows with the tile, up to a bit under half its height.
                        val coverSize = minOf(bh * 0.4f, bw * 0.45f).coerceAtLeast(58.dp)
                        Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.SpaceBetween) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp * k)) {
                                if (left) cover(Modifier.size(coverSize))
                                Column(Modifier.weight(1f)) {
                                    StatusChip(status, playing, ink)
                                    Spacer(Modifier.height(4.dp * k))
                                    names(15, 3)
                                }
                                if (!left) cover(Modifier.size(coverSize))
                            }
                            SongProgress(now, ink, bh > 220.dp, onSeek)
                            controls(bh > 220.dp)
                        }
                    }
                }
            }
            else -> when {
                short -> Row(Modifier.fillMaxSize().padding(pad), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(now, playing, ink, bg, inner, onToggle)
                    Box(Modifier.weight(1f)) { names(13, 1) }
                    MusicBtn(34.dp, ink, ob, "next", onNext)
                }
                landscape -> Row(Modifier.fillMaxSize().padding(pad), horizontalArrangement = Arrangement.spacedBy(16.dp * k), verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(now, playing, ink, bg, minOf(inner, 220.dp), onToggle)
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                        StatusChip(status, playing, ink)
                        names(20, 2)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MusicBtn(36.dp * k, ink, ob, "prev", onPrevious)
                            MusicBtn(36.dp * k, ink, ob, "next", onNext)
                        }
                    }
                }
                else -> Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.SpaceBetween) {
                    names(15, 2)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        MusicBtn(30.dp * k, ink, ob, "prev", onPrevious)
                        ProgressRing(now, playing, ink, bg, minOf(bh * 0.4f, bw * 0.5f).coerceAtLeast(78.dp), onToggle)
                        MusicBtn(30.dp * k, ink, ob, "next", onNext)
                    }
                }
            }
        }
    }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** How far into the song it is. Only drawn while a song plays; tap the bar to jump. */
@Composable
private fun SongProgress(now: app.koma.launcher.data.NowPlaying?, ink: Color, times: Boolean, onSeek: (Long) -> Unit) {
    if (now == null || !now.playing || now.duration <= 0L) return
    val pos by produceState(now.current(), now) {
        while (true) {
            value = now.current()
            awaitVisible()
            delay(500)
        }
    }
    val drag = remember { mutableStateOf<Float?>(null) }
    val frac = drag.value ?: (pos.toFloat() / now.duration).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Canvas(
            Modifier.fillMaxWidth().height(22.dp)
                .pointerInput(now.duration) {
                    detectTapGestures { o -> onSeek((o.x / size.width * now.duration).toLong()) }
                }
                .pointerInput(now.duration) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> drag.value = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = { drag.value?.let { onSeek((it * now.duration).toLong()) }; drag.value = null },
                        onDragCancel = { drag.value = null },
                    ) { change, _ -> drag.value = (change.position.x / size.width).coerceIn(0f, 1f) }
                },
        ) {
            val bar = 6.dp.toPx()
            val y = size.height / 2f
            val rad = androidx.compose.ui.geometry.CornerRadius(bar / 2f)
            drawRoundRect(ink.copy(alpha = 0.25f), Offset(0f, y - bar / 2f), androidx.compose.ui.geometry.Size(size.width, bar), rad)
            drawRoundRect(ink, Offset(0f, y - bar / 2f), androidx.compose.ui.geometry.Size(size.width * frac, bar), rad)
            val tw = 8.dp.toPx()
            val th = 18.dp.toPx()
            drawRoundRect(ink, Offset((size.width * frac - tw / 2f).coerceIn(0f, size.width - tw), y - th / 2f), androidx.compose.ui.geometry.Size(tw, th), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
        }
        if (times) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(clock(pos), style = label(ink, 9.sp))
                BasicText(clock(now.duration), style = label(ink, 9.sp))
            }
        }
    }
}

/** The wide weather tile: two places side by side. The second place is chosen in the tile's options. */
@Composable
private fun WeatherPair(
    h: Int, bg: Color, ink: Color, granted: Boolean,
    load: suspend () -> Weather?, cached: Weather?,
    load2: suspend () -> Weather?, cached2: Weather?, hasSecond: Boolean,
    showHiLo: Boolean, showCat: Boolean, source: String, fahrenheit: Boolean,
) {
    val first by produceState(cached, granted, source) {
        while (true) {
            awaitVisible()
            if (granted) load()?.let { value = it }
            delay(10 * 60_000L)
        }
    }
    val second by produceState(cached2, hasSecond, source) {
        if (!hasSecond) value = null
        while (hasSecond) {
            awaitVisible()
            load2()?.let { value = it }
            delay(10 * 60_000L)
        }
    }
    Row(Modifier.fillMaxSize().clip(TileShape).background(bg)) {
        WeatherHalf(Modifier.weight(1f), h, ink, first, if (granted) "No data" else "Tap for location", showHiLo, showCat, fahrenheit)
        Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 14.dp).background(ink.copy(alpha = 0.18f)))
        WeatherHalf(Modifier.weight(1f), h, ink, second, if (hasSecond) "No data" else "Add a second place in options", showHiLo, showCat, fahrenheit)
    }
}

@Composable
private fun WeatherHalf(modifier: Modifier, h: Int, ink: Color, data: Weather?, empty: String, showHiLo: Boolean, showCat: Boolean, fahrenheit: Boolean) {
    val (cond, glyph) = if (data != null) WeatherRepo.describe(data.code) else empty to "cloud"
    val temp = if (data != null) "${data.temp}°" else "--°"
    val mood = if (data == null) "cloud" else catMood(glyph, data.day)
    Box(modifier.fillMaxHeight()) {
        if (h >= 2) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Glyph(glyph, ink, 22.dp, 2f)
                    BasicText(cond.uppercase(), style = label(ink, 10.sp).copy(letterSpacing = 0.08.em), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                BasicText(temp, style = display(52.sp, ink, (-0.06).em, 1f), modifier = Modifier.padding(top = 6.dp))
                if (data != null) BasicText(data.city, style = label(ink, 10.sp).copy(letterSpacing = 0.08.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                if (showHiLo && data != null) BasicText("H ${data.hi}  L ${data.lo}", style = label(ink, 10.sp))
            }
            if (showCat) Cat(mood = mood, cup = catCup(mood, data?.temp, fahrenheit), modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp).size(width = 84.dp, height = 57.dp))
        } else {
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Glyph(glyph, ink, 20.dp, 2f)
                    BasicText(if (data != null) data.city else cond.uppercase(), style = label(ink, 9.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                BasicText(temp, style = display(34.sp, ink, (-0.05).em))
            }
        }
    }
}

/**
 * One card of the flip clock. When [value] changes the top half of the old number folds down and the bottom half of the
 * new one folds into place, the way a split-flap clock turns.
 */
@Composable
private fun FlipCard(value: String, fill: Color, face: Color, style: TextStyle, modifier: Modifier) {
    var shown by remember { mutableStateOf(value) }
    var prev by remember { mutableStateOf(value) }
    val turn = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(value) {
        if (value != shown) {
            prev = shown
            shown = value
            turn.snapTo(0f)
            turn.animateTo(1f, androidx.compose.animation.core.tween(560, easing = androidx.compose.animation.core.LinearEasing))
        }
    }
    val dens = LocalDensity.current
    @Composable
    fun Half(text: String, top: Boolean, mod: Modifier = Modifier) {
        Box(
            mod
                .fillMaxSize()
                .drawWithContent {
                    val h = size.height / 2f
                    clipRect(top = if (top) 0f else h, bottom = if (top) h else size.height) { this@drawWithContent.drawContent() }
                }
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            FitText(text, style, Modifier.fillMaxSize().padding(10.dp), align = Alignment.Center)
            Box(Modifier.fillMaxWidth().height(2.dp).background(face.copy(alpha = 0.35f)))
        }
    }
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(fill)) {
        val p = turn.value
        // Still halves: the new top is revealed as the old one folds away, the old bottom stays until the new one lands.
        Half(shown, top = true)
        Half(if (p < 1f) prev else shown, top = false)
        if (p < 0.5f) {
            Half(
                prev, top = true,
                mod = Modifier.graphicsLayer {
                    cameraDistance = 14f * dens.density
                    rotationX = -180f * p
                },
            )
        } else if (p < 1f) {
            Half(
                shown, top = false,
                mod = Modifier.graphicsLayer {
                    cameraDistance = 14f * dens.density
                    rotationX = 180f * (1f - p)
                },
            )
        }
    }
}
