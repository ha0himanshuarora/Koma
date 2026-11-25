package app.koma.launcher.ui

import android.os.Build
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.exp
import kotlin.math.roundToInt

/** The letters of the alphabet rail: "#" for names that do not start with a letter, then A to Z. */
internal val railLetters: List<String> = listOf("#") + ('A'..'Z').map { it.toString() }

/** Gap between rail letters, in dp. Larger spreads the rail further up and down the screen. */
internal const val RAIL_STEP_DP = 30

/** Where the letters sit down the screen. Shared by the touch strips and the drawn rail. */
internal class RailGeo(height: Float, private val count: Int = railLetters.size, maxStep: Float = Float.MAX_VALUE) {
    private val bandTop = height * 0.075f
    private val bandBottom = height * 0.965f
    // The rail never takes more than half the screen height, so a phone with 26 letters gets tighter spacing.
    private val step = minOf(maxStep, height * 0.5f / (count - 1).coerceAtLeast(1), (bandBottom - bandTop) / (count - 1).coerceAtLeast(1))
    // Few letters sit together in the middle of the band instead of being stretched apart.
    private val total = step * (count - 1).coerceAtLeast(0)
    // Sits low on the screen, within thumb reach, like Niagara.
    private val top = (height * 0.7f - total / 2f).coerceAtMost(bandBottom - total).coerceAtLeast(bandTop)
    fun yOf(i: Int) = top + i * step
    fun indexAt(y: Float) = ((y - top) / step).roundToInt().coerceIn(0, (count - 1).coerceAtLeast(0))
}

/**
 * Touch strips down both screen edges. Dragging from either edge opens the drawer and scrubs the alphabet rail,
 * like Niagara. In the drawer the strips are wider and a tap jumps to a letter.
 *
 * The strips and their gesture handlers are kept identical between the home screen and the drawer, so the finger
 * that opens the drawer keeps scrubbing without the gesture being torn down halfway through.
 */
@Composable
fun EdgeStrips(state: HomeState) {
    val view = LocalView.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val d = LocalDensity.current
        val hPx = constraints.maxHeight.toFloat()
        val wPx = constraints.maxWidth
        val inDrawer = state.screen == Screen.Drawer
        val stripW = 44.dp
        // On the home screen only the lower part of each edge listens. It matches the stretch the system is told
        // to leave alone, so the back gesture does not steal the swipe.
        val bandPx = with(d) { 200.dp.toPx() }
        val bandTop = (hPx * 0.7f - bandPx / 2f).coerceIn(0f, (hPx - bandPx).coerceAtLeast(0f))

        // The system allows at most 200dp of excluded edge per side, so it goes where the rail sits.
        LaunchedEffect(hPx, wPx, stripW) {
            if (Build.VERSION.SDK_INT >= 29) {
                val sw = with(d) { stripW.toPx() }.toInt()
                val top = bandTop.toInt()
                val bottom = (bandTop + bandPx).toInt().coerceAtMost(hPx.toInt())
                view.systemGestureExclusionRects = listOf(
                    android.graphics.Rect(0, top, sw, bottom),
                    android.graphics.Rect(wPx - sw, top, wPx, bottom),
                )
            }
        }
        DisposableEffect(Unit) {
            onDispose { if (Build.VERSION.SDK_INT >= 29) view.systemGestureExclusionRects = emptyList() }
        }
    }
}

/**
 * The edge gesture. It sits on the root of the home screen and watches every touch before the screens under it do,
 * so one finger that starts at the edge on the home screen opens the drawer and keeps scrubbing the rail with no
 * lift in between: nothing is created or torn down while the finger is down.
 */
internal fun Modifier.edgeScrub(state: HomeState): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val screen = state.screen
        val onHome = screen == Screen.Home && !state.edit && state.sheetId == null && !state.showPicker && !state.showWidgets && state.focusActive == null
        val inDrawer = screen == Screen.Drawer && state.openFolderId == null && state.focusActive == null
        if (!onHome && !inDrawer) return@awaitEachGesture
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        val railSide = if (state.prefs.leftHanded) -1 else 1
        val x = down.position.x
        val edge = { s: Int -> (if (!inDrawer) 26.dp else if (s == railSide) 44.dp else 28.dp).toPx() }
        val side = if (x <= edge(-1)) -1 else if (x >= w - edge(1)) 1 else 0
        if (side == 0) return@awaitEachGesture
        if (inDrawer && state.railCount <= 0) return@awaitEachGesture
        if (onHome) {
            val bandPx = 200.dp.toPx()
            val bandTop = (h * 0.7f - bandPx / 2f).coerceIn(0f, (h - bandPx).coerceAtLeast(0f))
            if (down.position.y < (bandTop - 24.dp.toPx()).coerceAtLeast(0f)) return@awaitEachGesture
        }
        val slop = viewConfiguration.touchSlop * 0.5f
        var moved = Offset.Zero
        var started = false
        fun upd(y: Float) {
            state.railSide = side
            state.railY = (y / h).coerceIn(0f, 1f)
            state.railPing++
        }
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val c = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed) {
                    // A tap on the rail's own edge jumps to that letter.
                    if (!started && inDrawer && side == railSide && moved.getDistance() <= slop) {
                        c.consume()
                        state.railDragging = false
                        upd(c.position.y)
                    }
                    break
                }
                if (!started) {
                    moved += c.positionChange()
                    if (moved.getDistance() > slop) {
                        started = true
                        state.railDragging = true
                        upd(c.position.y)
                        if (state.screen == Screen.Home) state.openDrawer()
                    }
                }
                if (started) {
                    c.consume()
                    upd(c.position.y)
                }
            }
        } finally {
            if (started) state.railDragging = false
        }
    }
}

/**
 * The drawn rail. It always sits on the rail side (right, or left in left-handed mode), whichever edge the
 * finger came from. While a finger scrubs it, the letters bulge inward toward the finger. Everything that moves
 * with the finger is read inside layout and draw lambdas, so a drag redraws the rail without recomposing it.
 */
@Composable
fun CurvedRail(state: HomeState, activeLetter: String, letters: List<String> = railLetters, clearance: Dp? = null) {
    val pal = state.palette
    val left = state.prefs.leftHanded
    val bulgeAmt by animateFloatAsState(if (state.railDragging) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 700f), label = "bulge")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val d = LocalDensity.current
        val hPx = constraints.maxHeight.toFloat()
        val wPx = constraints.maxWidth.toFloat()
        val geo = remember(hPx, letters.size) { RailGeo(hPx, letters.size, with(d) { RAIL_STEP_DP.dp.toPx() }) }
        val sigma = with(d) { 90.dp.toPx() }
        // The bulge may not reach into the app panel: it is limited to the free space beside it.
        val shift = minOf(
            with(d) { 120.dp.toPx() },
            clearance?.let { with(d) { (it - 10.dp - 24.dp * 1.3f - 6.dp).toPx() }.coerceAtLeast(0f) } ?: Float.MAX_VALUE,
        )
        val edge = with(d) { 10.dp.toPx() }
        val cell = with(d) { 24.dp.toPx() }
        // Only changes when the highlighted letter changes, so letters recompose rarely.
        val activeIdx by remember(geo, letters, activeLetter, hPx) {
            derivedStateOf {
                if (state.railDragging) geo.indexAt(state.railY * hPx) else letters.indexOf(activeLetter).coerceAtLeast(0)
            }
        }
        letters.forEachIndexed { i, l ->
            val y = geo.yOf(i)
            val on = i == activeIdx
            Box(
                Modifier
                    .offset {
                        val dist = (y - state.railY * hPx) / sigma
                        val fromEdge = edge + shift * exp(-dist * dist) * bulgeAmt
                        val x = if (!left) wPx - fromEdge - cell else fromEdge
                        IntOffset(x.roundToInt(), (y - cell / 2).roundToInt())
                    }
                    .size(24.dp)
                    .graphicsLayer {
                        val dist = (y - state.railY * hPx) / sigma
                        val s = 1f + 0.6f * exp(-dist * dist) * bulgeAmt
                        scaleX = s
                        scaleY = s
                    },
                contentAlignment = Alignment.Center,
            ) {
                // The rail sits on the wallpaper (dimmed or clear), not on the panel, so it stays light whatever the theme. Only a solid drawer background uses the theme's own ink.
                val ink = if (state.prefs.drawerBg == 2) pal.onGround else Color.White
                val hi = if (state.prefs.drawerBg == 2) lerp(pal.onGround, pal.tiles[0], 0.8f) else lerp(Color.White, pal.tiles[0], 0.7f)
                BasicText(
                    l,
                    style = monoStyle(if (on) hi else ink.copy(alpha = 0.9f), 11, bold = true).copy(
                        shadow = if (state.prefs.drawerBg == 2) null else Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 4f),
                    ),
                )
            }
        }
    }
}
