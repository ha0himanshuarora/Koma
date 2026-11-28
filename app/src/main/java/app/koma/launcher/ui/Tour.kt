package app.koma.launcher.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import androidx.compose.runtime.snapshotFlow
import app.koma.launcher.data.Tile

// ---- where things are, so the tour can point at them -------------------------------------------------

/** On-screen rectangles of the parts of Koma the tour points at, keyed by name. Filled by [tourTarget]. */
object TourBounds {
    val map = mutableStateMapOf<String, Rect>()
}

/** Marks a piece of the interface as something the tour can highlight. Does nothing when the tour is not running. */
fun Modifier.tourTarget(id: String): Modifier = composed {
    DisposableEffect(id) { onDispose { TourBounds.map.remove(id) } }
    this.onGloballyPositioned { TourBounds.map[id] = it.boundsInWindow() }
}

// ---- the tour itself -------------------------------------------------------------------------------------

/**
 * The first-run tutorial: a few swipeable pages, then a tour of the real screens. It starts once, on the very first
 * install. An update never starts it (Koma then already has saved data), and neither does a reinstall that restores
 * data. [replay] starts it again from Settings.
 */
class Tour(context: Context, private val sp: SharedPreferences) {
    /** "" while it is not running, "intro" for the pages, "tour" for the hands-on steps. */
    var phase by mutableStateOf("")
        private set
    var step by mutableIntStateOf(0)
        private set

    /** The screen the tour last took the person to, so a step on the same screen does not navigate again. */
    var entered: String = ""

    /** True while the app long-press menu is open in the drawer (set by the drawer, read by the tutorial). */
    var menuOpen by mutableStateOf(false)

    /** The tiles as they were when the current step began, so a step can tell whether the person changed something. */
    var baseline: List<Tile>? = null

    val active: Boolean get() = phase.isNotEmpty()

    /** True on the very first hands-on step, where taps on tiles are ignored so only a long press does anything. */
    val holdOnly: Boolean get() = phase == "tour" && step == 0

    init {
        if (!sp.getBoolean("tour_done", false)) {
            when {
                // Started and not finished (the app was closed halfway): show it again from the start.
                sp.getBoolean("tour_pending", false) -> phase = "intro"
                isFreshInstall(context, sp) -> {
                    sp.edit().putBoolean("tour_pending", true).apply()
                    phase = "intro"
                }
                else -> sp.edit().putBoolean("tour_done", true).apply()
            }
        }
    }

    fun replay() {
        step = 0
        entered = ""
        phase = "intro"
    }

    fun startTour() {
        step = 0
        entered = ""
        phase = "tour"
    }

    fun next(): Boolean {
        if (step >= tourSteps.size - 1) return false
        step++
        return true
    }

    fun back() {
        if (phase == "tour" && step > 0) step-- else if (phase == "tour") phase = "intro" else finish()
    }

    fun finish() {
        phase = ""
        sp.edit().putBoolean("tour_done", true).remove("tour_pending").apply()
    }

    private fun isFreshInstall(ctx: Context, sp: SharedPreferences): Boolean {
        if (sp.contains("seed")) return false
        if (File(ctx.filesDir, "layout.json").exists() || File(ctx.filesDir, "layout_today.json").exists()) return false
        return try {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            Math.abs(pi.lastUpdateTime - pi.firstInstallTime) < 2000L
        } catch (e: Exception) {
            false
        }
    }
}

private class IntroPage(val art: String, val title: String, val lines: List<String>)

private val introPages = listOf(
    IntroPage(
        "welcome", "Meet Koma",
        listOf(
            "Your home screen is a grid of tiles. You choose what goes on it, how big each tile is and what colour it is.",
            "This takes about a minute, and then you get to try it yourself. Skip it whenever you like. It's in Settings if you want it again.",
        ),
    ),
    IntroPage(
        "tiles", "Tap a tile to use it",
        listOf(
            "Some tiles open a small app of their own when you tap them, like the clock, the weather or your notes.",
            "Others, like music and the calendar, just work right where they are.",
        ),
    ),
    IntroPage(
        "edit", "Hold to rearrange",
        listOf(
            "Hold any tile for a second and the home screen starts editing. Drag a tile to move it. Drag its edge to make it bigger.",
            "Tap a tile to pick it, then Options changes how it looks. Done keeps your changes. The X on the left throws them away.",
        ),
    ),
    IntroPage(
        "find", "Swipe to find things",
        listOf(
            "Swipe in from the left or right edge to see all your apps, A to Z. Slide your finger down the edge to jump to a letter.",
            "Swipe up from the home screen to search. It finds apps, contacts, quick sums and the web.",
        ),
    ),
    IntroPage(
        "apps", "Koma has its own apps",
        listOf(
            "Clock, Notes, Reminders, Calculator, Weather and Screen time. Each opens in its own window, so they show up separately when you switch apps.",
            "Screen time is also where you put a daily limit on an app.",
        ),
    ),
    IntroPage(
        "settings", "Make it yours",
        listOf(
            "Colours, fonts, icon shapes, gestures and plenty more are in Settings. Nothing is permanent, so change whatever you like.",
            "Now let's try it for real. Do each thing when it's asked and I'll tick it off.",
        ),
    ),
)

/**
 * One step of the hands-on tutorial. [screen] is where it starts. With [done] the person does the thing themselves and
 * the tutorial moves on when [done] turns true; without it the step is a note with a NEXT button. [assist] does the
 * action for them when they press SKIP.
 */
class TourStep(
    val screen: String,
    val target: String?,
    val title: String,
    val text: String,
    val done: ((HomeState) -> Boolean)? = null,
    val assist: ((HomeState) -> Unit)? = null,
    /** Sets the screen up when the step starts, so going back to it always finds what it needs (a tile selected, a sheet open). */
    val prep: ((HomeState) -> Unit)? = null,
    /** Puts the card at the top, for steps where the gesture starts at the bottom of the screen. */
    val cardTop: Boolean = false,
)

private fun HomeState.pickFirst() {
    if (selectedId == null) selectedId = tiles.firstOrNull()?.id
}

private fun HomeState.calm() {
    sheetId = null
    showPicker = false
}

val tourSteps = listOf(
    TourStep(
        "home", "tour_tile", "Hold this tile",
        "Press and hold the highlighted tile until the edit bar shows up at the bottom. Just hold it, a quick tap does nothing right now.",
        done = { it.edit }, assist = { it.startEdit() },
    ),
    TourStep(
        "edit", null, "Pick one",
        "Tap a tile to select it.",
        done = { it.selectedId != null }, assist = { s -> s.selectedId = s.tiles.firstOrNull()?.id },
        prep = { it.calm(); it.selectedId = null },
    ),
    TourStep(
        "edit", "edit_options", "Open Options",
        "Tap OPTIONS in the bar at the bottom.",
        done = { it.sheetId != null }, assist = { s -> s.sheetId = s.selectedId },
        prep = { it.calm(); it.pickFirst() },
    ),
    TourStep(
        "edit", null, "Change the colour",
        "Every tile has a Colour setting, whatever it shows. Tap a different colour.",
        done = { it.tiles != it.tour.baseline },
        prep = { it.pickFirst(); it.showPicker = false; it.sheetId = it.selectedId },
    ),
    TourStep(
        "edit", null, "Close Options",
        "Nice. Press back to close Options.",
        done = { it.sheetId == null }, assist = { s -> s.sheetId = null },
        prep = { it.pickFirst(); it.showPicker = false; it.sheetId = it.selectedId },
    ),
    TourStep(
        "edit", "edit_add", "Add something",
        "Tap the + to see what you can put on your home screen.",
        done = { it.showPicker }, assist = { s -> s.showPicker = true },
        prep = { it.calm() },
    ),
    TourStep(
        "edit", null, "Add a component",
        "Tap any component in the list and it goes onto your home screen.",
        done = { it.tiles.size > (it.tour.baseline?.size ?: Int.MAX_VALUE) },
        assist = { s -> s.showPicker = false },
        prep = { it.sheetId = null; it.showPicker = true },
    ),
    TourStep(
        "edit", null, "Move or resize",
        "Drag a tile somewhere else, or drag its edge or corner to change its size. Drop one on another of the same size and they stack, and you can swipe the stack.",
        done = { it.tiles != it.tour.baseline },
        prep = { it.calm() },
    ),
    TourStep(
        "edit", "edit_cancel", "Undo it all",
        "Everything you did here was practice. Tap the X at the top left and it all goes back to how it was.",
        done = { !it.edit }, assist = { it.cancelEdit() },
    ),
    TourStep(
        "home", null, "Open all your apps",
        "Swipe in from the left or right edge of the screen.",
        done = { it.screen == Screen.Drawer }, assist = { it.openDrawer() },
    ),
    TourStep(
        "drawer", null, "Hold an app",
        "Press and hold any app in the list for a moment.",
        done = { it.tour.menuOpen },
    ),
    TourStep(
        "drawer", null, "Its menu",
        "From here you can open its shortcuts, set a daily limit or hide it. Press back to close the menu.",
        done = { !it.tour.menuOpen },
    ),
    TourStep(
        "home", null, "Search",
        "Swipe up from the home screen.",
        done = { it.screen == Screen.Search }, assist = { it.openSearch() }, cardTop = true,
    ),
    TourStep(
        "search", null, "What you can type",
        "A name, to find an app or a contact. A sum like 12 x 8. A conversion like 5 km in miles. Or anything at all, to search the web. You pick the search engine in Settings.",
    ),
    TourStep(
        "apps", "kapps_list", "Koma apps",
        "These are the apps that come with Koma.",
    ),
    TourStep(
        "apps", "kapps_list", "Open Weather",
        "Tap Weather in the list.",
        done = { it.screen == Screen.Weather }, assist = { it.open(Screen.Weather) },
    ),
    TourStep(
        "weather", null, "Go back",
        "Normally an app like this opens in its own window, and you'd see it in your recent apps. Press back to come back here.",
        done = { it.screen != Screen.Weather }, assist = { it.open(Screen.Apps) },
    ),
    TourStep(
        "settings", "settings_home", "Settings: Home",
        "The home screen type, layout, saved looks, colours that follow your wallpaper and what the gestures do.",
    ),
    TourStep(
        "settings", "settings_cards", "Settings: Cards",
        "Set up Weather, Music, Calendar, fonts and search. Icon styles and hiding apps are under Apps. Permissions and Focus Lock are under System.",
    ),
    TourStep(
        "home", null, "Let's get started",
        "Set Koma as your default launcher so the home button brings you here. You can run this tutorial again any time from Settings, then Tutorial.",
    ),
)

/** Whether the app is already on the screen a step starts from. */
private fun isAt(state: HomeState, screen: String): Boolean = when (screen) {
    "home" -> state.screen == Screen.Home && !state.edit
    "edit" -> state.edit && state.screen == Screen.Home
    "drawer" -> state.screen == Screen.Drawer
    "search" -> state.screen == Screen.Search
    "apps" -> state.screen == Screen.Apps
    "weather" -> state.screen == Screen.Weather
    "settings" -> state.screen == Screen.Settings
    else -> true
}

/** Takes the person to the screen a step starts from. */
private fun goTo(state: HomeState, screen: String) {
    if (state.edit && screen != "edit") state.cancelEdit()
    when (screen) {
        "home" -> state.goHome()
        "edit" -> { state.goHome(); state.startEdit() }
        "drawer" -> state.openDrawer()
        "search" -> state.openSearch()
        "apps" -> state.open(Screen.Apps)
        "weather" -> state.open(Screen.Weather)
        "settings" -> { state.goHome(); state.open(Screen.Settings) }
    }
}

/** Leaves the tutorial: back to a plain home screen, and never shown again unless replayed. */
private fun endTour(state: HomeState) {
    if (state.edit) state.cancelEdit()
    state.goHome()
    state.tour.finish()
}

@Composable
fun TourOverlay(state: HomeState) {
    when (state.tour.phase) {
        "intro" -> TourIntro(state)
        "tour" -> TourSteps(state)
    }
}

@Composable
private fun TourIntro(state: HomeState) {
    val pal = state.palette
    val tour = state.tour
    val pager = rememberPagerState { introPages.size }
    val scope = rememberCoroutineScope()
    BackHandler {
        if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } else endTour(state)
    }
    val last = pager.currentPage == introPages.size - 1
    Box(Modifier.fillMaxSize().background(pal.ground).pointerInput(Unit) { detectTapGestures { } }) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (!last) Pill("SKIP", pal.onGround.copy(alpha = 0.12f), pal.onGround, height = 34) { endTour(state) }
            }
            // The pager spans the full width, each page pads itself, so a page never gets cut off mid swipe.
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
                val p = introPages[i]
                val off = ((pager.currentPage - i) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                Column(
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = 1f - off * 0.7f
                        val sc = 1f - off * 0.06f
                        scaleX = sc
                        scaleY = sc
                    }.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(250.dp).clip(TileShape).background(pal.tiles[i % pal.tiles.size]),
                        contentAlignment = Alignment.Center,
                    ) { TourArt(p.art) }
                    BasicText(p.title, style = dispStyle(pal.onGround, 32))
                    p.lines.forEach { line ->
                        BasicText(line, style = bodyStyle(pal.onGround.copy(alpha = 0.85f), 16))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center) {
                repeat(introPages.size) { i ->
                    Box(
                        Modifier.padding(horizontal = 4.dp).size(if (i == pager.currentPage) 22.dp else 8.dp, 8.dp).clip(CircleShape)
                            .background(if (i == pager.currentPage) pal.tiles[0] else pal.onGround.copy(alpha = 0.25f)),
                    )
                }
            }
            Pill(if (last) "LET'S TRY IT" else "NEXT", pal.tiles[0], pal.ink, Modifier.padding(horizontal = 20.dp).fillMaxWidth(), height = 52) {
                if (last) tour.startTour() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            }
        }
    }
}

@Composable
private fun TourSteps(state: HomeState) {
    val pal = state.palette
    val tour = state.tour
    val s = tourSteps[tour.step.coerceIn(0, tourSteps.size - 1)]
    val interactive = s.done != null
    val lastStep = tour.step >= tourSteps.size - 1
    // On a note step back means the step before. On a hands-on step back belongs to the app, because the person needs it.
    BackHandler(enabled = !interactive) { tour.back() }

    LaunchedEffect(tour.step, tour.phase) {
        if (tour.phase != "tour") return@LaunchedEffect
        if (!isAt(state, s.screen)) goTo(state, s.screen)
        s.prep?.invoke(state)
        tour.baseline = state.tiles
        val d = s.done ?: return@LaunchedEffect
        // Move on only when the thing turns from not-done to done while this step is showing.
        var sawFalse = false
        snapshotFlow { d(state) }.collect { v ->
            if (!v) {
                sawFalse = true
            } else if (sawFalse) {
                delay(450)
                tour.next()
            }
        }
    }

    var origin by remember { mutableStateOf(Offset.Zero) }
    val rect = s.target?.let { TourBounds.map[it] }
    val ring = pal.tiles[0]
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        // A note step dims the screen and blocks it. A hands-on step only outlines the thing to use and lets touches through.
        val blocker = if (!interactive) Modifier.pointerInput(Unit) { detectTapGestures { } } else Modifier
        if (rect != null || !interactive) {
            Canvas(Modifier.fillMaxSize().then(blocker).graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
                drawRect(Color.Black.copy(alpha = if (interactive) 0.5f else 0.74f))
                if (rect != null) {
                    val pad = 8.dp.toPx()
                    val tl = Offset(rect.left - origin.x - pad, rect.top - origin.y - pad)
                    val sz = Size(rect.width + pad * 2, rect.height + pad * 2)
                    drawRoundRect(Color.Transparent, tl, sz, CornerRadius(20.dp.toPx()), blendMode = BlendMode.Clear)
                    drawRoundRect(ring, tl, sz, CornerRadius(20.dp.toPx()), style = Stroke(2.dp.toPx()))
                }
            }
        }
        // The card goes on the half of the screen the highlighted part is not on. With nothing highlighted it sits at the
        // bottom, except while search or the edit bar need the bottom.
        val top = when {
            rect != null -> (rect.center.y - origin.y) > heightPx / 2f
            else -> s.cardTop || s.screen == "search" || s.screen == "edit" || s.screen == "drawer"
        }
        Column(
            Modifier.align(if (top) Alignment.TopCenter else Alignment.BottomCenter)
                .statusBarsPadding().navigationBarsPadding().padding(16.dp)
                .clip(RoundedCornerShape(26.dp)).background(pal.ground)
                .border(1.dp, pal.onGround.copy(alpha = 0.14f), RoundedCornerShape(26.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicText(
                (if (interactive) "TRY IT" else "NOTE") + "   " + (tour.step + 1) + " / " + tourSteps.size,
                style = monoStyle(if (interactive) pal.tiles[0] else pal.onGround.copy(alpha = 0.55f), 10, bold = interactive),
            )
            BasicText(s.title, style = dispStyle(pal.onGround, 24))
            BasicText(s.text, style = bodyStyle(pal.onGround.copy(alpha = 0.85f), 15))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!lastStep) Pill("END TOUR", pal.onGround.copy(alpha = 0.12f), pal.onGround, height = 38) { endTour(state) }
                Spacer(Modifier.weight(1f))
                if (tour.step > 0) {
                    Pill("BACK", pal.onGround.copy(alpha = 0.12f), pal.onGround, height = 38) { tour.back() }
                    Spacer(Modifier.width(8.dp))
                }
                if (interactive) {
                    Pill("SKIP", pal.onGround.copy(alpha = 0.12f), pal.onGround, height = 38) {
                        s.assist?.invoke(state)
                        tour.next()
                    }
                } else {
                    Pill(if (lastStep) "DONE" else "NEXT", pal.tiles[0], pal.ink, height = 38) { if (lastStep) endTour(state) else tour.next() }
                }
            }
        }
    }
}
