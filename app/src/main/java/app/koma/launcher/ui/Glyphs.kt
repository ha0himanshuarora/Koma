package app.koma.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/** Line glyphs on a 24 x 24 grid, taken from the design files. */
private val glyphPaths: Map<String, List<String>> = mapOf(
    "backspace" to listOf("M9 5h11a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H9l-6-7z", "M12 9l5 6M17 9l-5 6"),
    "swap" to listOf("M7 4L3 8l4 4M3 8h14M17 20l4-4-4-4M21 16H7"),
    "refresh" to listOf("M20 12a8 8 0 1 1-2.5-5.8M20 4v4h-4"),
    "n_alarm" to listOf("M6 17v-6a6 6 0 0 1 12 0v6l1.5 2h-15z", "M10 21h4"),
    "n_world" to listOf("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M3 12h18", "M12 3a14 14 0 0 1 0 18a14 14 0 0 1 0-18"),
    "n_watch" to listOf("M5 14a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "M12 14l3-3", "M10 3h4"),
    "n_timer" to listOf("M7 3h10M7 21h10", "M7 3c0 5 5 6 5 9s-5 4-5 9M17 3c0 5-5 6-5 9s5 4 5 9"),
    "n_focus" to listOf("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M9.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0"),
    "flag" to listOf("M5 21V4", "M5 5h12l-2 4 2 4H5"),
    "cam" to listOf("M4 8h3l2-3h6l2 3h3v11H4z", "M8.5 13a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0"),
    "web" to listOf(
        "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0",
        "M3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18",
    ),
    "chat" to listOf("M4 5h16v11H9l-5 4z"),
    "phone" to listOf("M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2z"),
    "pics" to listOf("M3 5h18v14H3z", "M7.5 10a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0 -3 0", "M3 17l5-4 4 3 3-2 6 4"),
    "video" to listOf("M6 6h12a3 3 0 0 1 3 3v6a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V9a3 3 0 0 1 3-3z", "M10 9.5l5 2.5-5 2.5z"),
    "mail" to listOf("M3 5h18v14H3z", "M3 7l9 6 9-6"),
    "all" to listOf("M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z"),
    "arrow" to listOf("M6 18L18 6M8 6h10v10"),
    "sun" to listOf(
        "M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0",
        "M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2",
    ),
    "cloud" to listOf("M7 15a4 4 0 0 1 0-8 5 5 0 0 1 9.5 1A3.5 3.5 0 0 1 17 15z"),
    "rain" to listOf("M7 15a4 4 0 0 1 0-8 5 5 0 0 1 9.5 1A3.5 3.5 0 0 1 17 15z", "M8 18v3M12 18v3M16 18v3"),
    "snow" to listOf("M7 15a4 4 0 0 1 0-8 5 5 0 0 1 9.5 1A3.5 3.5 0 0 1 17 15z", "M8 19h.01M12 19h.01M16 19h.01M10 21h.01M14 21h.01"),
    "storm" to listOf("M7 15a4 4 0 0 1 0-8 5 5 0 0 1 9.5 1A3.5 3.5 0 0 1 17 15z", "M12 16l-2 3h4l-2 3"),
    "fog" to listOf("M4 9h16M4 13h16M4 17h16"),
    "play" to listOf("M7 4l13 8-13 8z"),
    "bag" to listOf("M5 8h14l1 12H4z", "M9 8V6a3 3 0 0 1 6 0v2M9 13h6"),
    "bars" to listOf("M6 20v-8M12 20V5M18 20v-5"),
    "pause" to listOf("M7 4h4v16H7zM13 4h4v16h-4z"),
    "expand" to listOf("M14 4h6v6M20 4l-7 7M10 20H4v-6M4 20l7-7"),
    "prev" to listOf("M6 5v14M18 5L8 12l10 7z"),
    "next" to listOf("M18 5v14M6 5l10 7L6 19z"),
    "back" to listOf("M15 5l-7 7 7 7"),
    "chev" to listOf("M9 5l7 7-7 7"),
    "up" to listOf("M6 14l6-6 6 6"),
    "down" to listOf("M6 10l6 6 6-6"),
    "plus" to listOf("M12 5v14M5 12h14"),
    "minus" to listOf("M5 12h14"),
    "check" to listOf("M5 12l5 5 9-10"),
    "share" to listOf("M18 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6z", "M6 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z", "M18 22a3 3 0 1 0 0-6 3 3 0 0 0 0 6z", "M8.6 13.5l6.8 4M15.4 6.5l-6.8 4"),
    "close" to listOf("M6 6l12 12M18 6L6 18"),
    "trash" to listOf("M5 7h14M9 7V4h6v3M7 7l1 13h8l1-13"),
    "note" to listOf("M6 3h9l4 4v14H6z", "M14 3v5h5M9 13h7M9 17h5"),
    "tasks" to listOf("M4 6l2 2 3-3M12 7h8M4 14l2 2 3-3M12 15h8"),
    "timer" to listOf("M5 13a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "M12 9v4l2 2M10 2h4"),
    "calc" to listOf("M6 3h12a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M8 7.5h8", "M8 12h.01M12 12h.01M16 12h.01M8 16h.01M12 16h.01M16 16h.01"),
    "settings" to listOf("M4 7h10M18 7h2M4 17h2M10 17h10", "M14 5v4M6 15v4"),
    "lock" to listOf("M6 11h12v9H6z", "M8 11V8a4 4 0 0 1 8 0v3"),
    "bolt" to listOf("M13 3L5 14h6l-1 7 8-11h-6z"),
    "torch" to listOf("M9 2h6v7l-1 2v10h-4V11l-1-2z"),
    "wifi" to listOf("M2 9a15 15 0 0 1 20 0M5 13a10 10 0 0 1 14 0M8.5 16.5a5 5 0 0 1 7 0M12 20h.01"),
    "bt" to listOf("M7 7l10 10-5 4V3l5 4L7 17"),
    "moon" to listOf("M20 14a8 8 0 1 1-10-10 6 6 0 0 0 10 10z"),
    "star" to listOf("M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z"),
    "mic" to listOf("M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3z", "M6 11a6 6 0 0 0 12 0M12 17v4"),
    "lens" to listOf("M4 8V5h3M17 5h3v3M20 16v3h-3M7 19H4v-3", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0"),
    "palette" to listOf("M12 3a9 9 0 1 0 0 18c1.5 0 2-1 1.5-2.2-.6-1.4.3-2.8 1.8-2.8H18a3 3 0 0 0 3-3C21 7 17 3 12 3z", "M7.5 11h.01M10 7.5h.01M14 7.5h.01"),
    "type" to listOf("M5 6h14M12 6v13M9 19h6"),
    "users" to listOf("M9 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6z", "M3 20a6 6 0 0 1 12 0M16 5a3 3 0 0 1 0 6M18 14a6 6 0 0 1 3 6"),
    "cal" to listOf("M4 6h16v14H4z", "M4 10h16M8 3v4M16 3v4"),
    "search" to listOf("M10.5 18a7.5 7.5 0 1 0 0-15 7.5 7.5 0 0 0 0 15z", "M16 16l5 5"),
    "info" to listOf("M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z", "M12 11v6M12 7.5h.01"),
    "device" to listOf("M7 3h10v18H7z", "M11 18h2"),
    "widget" to listOf("M4 4h7v7H4z", "M13 13h7v7h-7z", "M13 4h7v7h-7z"),
    "alarm" to listOf("M5 13a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "M12 9v4l2 2", "M4 5l3-2M20 5l-3-2"),
    "globe" to listOf("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M3 12h18", "M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18"),
    "hourglass" to listOf("M7 3h10M7 21h10M8 3v3l4 6-4 6v3M16 3v3l-4 6 4 6v3"),
    "clock" to listOf("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M12 7v5l3 2"),
    "music" to listOf("M9 18V6l10-2v12", "M3.5 18a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0", "M13.5 16a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0"),
    "kanji" to listOf("M5 4h14v16H5z", "M5 12h14"),
    "focus" to listOf("M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0"),
    "sun2" to listOf("M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z"),
)

@Composable
fun Glyph(name: String, color: Color, size: Dp, strokeWidth: Float = 2f, filled: Boolean = false) {
    val paths = remember(name) {
        (glyphPaths[name] ?: emptyList()).map { PathParser().parsePathString(it).toPath() }
    }
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 24f
        val style: DrawStyle = if (filled) Fill else Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        scale(s, Offset.Zero) {
            paths.forEach { drawPath(it, color, style = style) }
        }
    }
}
