package app.koma.launcher.ui

import app.koma.launcher.data.Words
import app.koma.launcher.data.MusicArg
import app.koma.launcher.data.ClockArg
import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.koma.launcher.data.Gestures
import app.koma.launcher.data.Engines
import app.koma.launcher.data.Kind
import app.koma.launcher.data.Notes
import app.koma.launcher.data.Tile
import app.koma.launcher.data.children
import app.koma.launcher.data.allowedSizes
import androidx.compose.foundation.layout.width
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(state: HomeState, homePress: Int, homeInApp: Boolean = false) {
    val ctx = LocalContext.current
    val pal = state.palette
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // ---- system launchers ---------------------------------------------------------------------
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        state.permissionsChanged()
    }
    LaunchedEffect(state.permissionRequest) {
        val req = state.permissionRequest
        if (req != null) {
            state.permissionRequest = null
            permissionLauncher.launch(req)
        }
    }

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val id = state.photoRequest
        state.photoRequest = null
        if (uri != null && id != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
            }
            val t = state.findTile(id)
            // A clock keeps its photo beside its style; every other tile keeps just the photo.
            if (t != null && t.kind == Kind.CLOCK) state.setArg(id, ClockArg.set(t.arg, image = uri.toString()))
            else state.setArg(id, uri.toString())
        }
    }
    LaunchedEffect(state.photoRequest) {
        if (state.photoRequest != null) {
            try {
                photoLauncher.launch(arrayOf("image/*"))
            } catch (e: Exception) {
                state.photoRequest = null
            }
        }
    }

    // Android widgets: ask the system to allow binding, then run the widget's own setup screen if it has one.
    val bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val p = state.pendingWidget
        if (p == null) return@rememberLauncherForActivityResult
        if (result.resultCode != Activity.RESULT_OK) {
            state.widgets.delete(p.id)
            state.pendingWidget = null
        } else if (p.info.configure != null) {
            state.pendingWidget = PendingWidget(p.id, p.info, "configure")
        } else {
            state.addWidgetTile(p.id, p.info)
            state.afterWidgetAdd()
        }
    }
    LaunchedEffect(state.pendingWidget) {
        val p = state.pendingWidget ?: return@LaunchedEffect
        try {
            if (p.stage == "bind") {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, p.id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, p.info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, p.info.profile)
                bindLauncher.launch(intent)
            } else {
                // The result arrives in MainActivity.onActivityResult, which calls state.widgetConfigured().
                var a: Context? = ctx
                while (a is android.content.ContextWrapper && a !is Activity) a = a.baseContext
                state.widgets.configure(a as Activity, p.id)
            }
        } catch (e: Exception) {
            if (p.stage == "configure" && state.widgets.configureOptional(p.info)) {
                state.addWidgetTile(p.id, p.info)
                state.afterWidgetAdd()
            } else {
                state.widgets.delete(p.id)
                state.pendingWidget = null
                Toast.makeText(ctx, "That widget would not start its setup screen", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---- lifecycle ----------------------------------------------------------------------------
    val scroll = rememberScrollState()
    LaunchedEffect(homePress) {
        if (homePress == 0) return@LaunchedEffect
        state.goHome()
        if (state.edit) state.finishEdit()
    }
    LaunchedEffect(state.appsVersion) { state.refreshApps() }
    LaunchedEffect(Unit) {
        while (true) {
            awaitVisible()
            state.refreshFocus()
            delay(15_000L)
        }
    }
    BackHandler(enabled = state.focusActive == null) { state.back() }

    // Frosted wallpaper behind the drawer and settings, where the system supports it (Android 12+).
    val blurRadius = when (state.screen) {
        Screen.Home -> 0
        else -> if (state.prefs.ground != 0) 48 else 0
    }
    LaunchedEffect(blurRadius) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                (ctx as? Activity)?.window?.let { w ->
                    // Blur needs a window background that is not fully clear, and the blur-behind flag for the wallpaper layer.
                    w.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(if (blurRadius > 0) 0x14000000 else 0))
                    w.setBackgroundBlurRadius(blurRadius)
                    val lp = w.attributes
                    lp.blurBehindRadius = blurRadius
                    w.attributes = lp
                    if (blurRadius > 0) w.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    else w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                }
            } catch (e: Exception) {
            }
        }
    }

    // Status and navigation bar icons: dark on a light scheme, light on a dark one.
    val hostView = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(pal.isDark) {
        (ctx as? Activity)?.window?.let { w ->
            val c = androidx.core.view.WindowCompat.getInsetsController(w, hostView)
            c.isAppearanceLightStatusBars = !pal.isDark
            c.isAppearanceLightNavigationBars = !pal.isDark
        }
    }

    // The ringing screen runs on its own, so it reads the launcher's current colours from here.
    val appCtx = LocalContext.current
    androidx.compose.runtime.SideEffect {
        savePalette(appCtx, pal)
    }

    val fontScale = density.fontScale * state.prefs.textScale / 100f
    val groundColor = when {
        // The drawer shows the live wallpaper behind it whatever the ground setting is.
        state.screen == Screen.Drawer || state.screen == Screen.Search -> Color.Transparent
        // Glass tiles need the wallpaper behind them, so a solid ground becomes the clear wallpaper.
        state.prefs.ground == 0 && state.prefs.tileStyle != "solid" -> Color.Black.copy(alpha = 0.22f)
        state.prefs.ground == 0 -> pal.ground
        state.prefs.ground == 1 -> pal.ground.copy(alpha = 0.78f)
        else -> Color.Black.copy(alpha = 0.22f)
    }

    CompositionLocalProvider(
        LocalPalette provides pal,
        androidx.compose.foundation.LocalIndication provides NoIndication,
        LocalIconCfg provides state.iconConfig(),
        LocalBadge provides state.prefs.tileBadge,
        LocalDensity provides Density(density.density, fontScale),
    ) {
        // Full-screen apps and settings are opaque all the way up, including the strip beside a camera cutout, which
        // the cutout padding below would otherwise leave showing the wallpaper through a see-through ground.
        val solidScreen = when (state.screen) {
            Screen.Settings, Screen.Notes, Screen.Tasks, Screen.Focus, Screen.Clock, Screen.Calc, Screen.Apps -> true
            else -> false
        }
        Box(
            Modifier.fillMaxSize().background(groundColor).then(if (solidScreen) Modifier.background(pal.ground) else Modifier)
                .edgeScrub(state).displayCutoutPadding(),
        ) {
            val locked = state.focusActive != null
            if (locked) {
                FocusOnScreen(state)
            } else {
                if (state.screen == Screen.Home) HomeGrid(state, ctx, scroll)
                when (state.screen) {
                    Screen.Home -> Unit
                    Screen.Drawer -> Drawer(state)
                    Screen.Search -> SearchScreen(state)
                    Screen.Settings -> SettingsScreen(state)
                    Screen.Notes -> NotesScreen(state)
                    Screen.Tasks -> TasksScreen(state)
                    Screen.Focus -> FocusScreen(state)
                    Screen.Clock -> ClockApp(state)
                    Screen.Calc -> CalcApp(state)
                    Screen.Apps -> KomaApps(state)
                }
                val sheetTile = state.tiles.firstOrNull { it.id == state.sheetId }
                if (sheetTile != null && state.screen == Screen.Home) TileSheet(state, sheetTile)
                if (state.showPicker) PickerSheet(state)
                state.dockPick?.let { if (state.screen == Screen.Home) DockSheet(state, it) }
                if (state.showGrid && state.edit) GridSheet(state)
                if (state.showWidgets) WidgetChooser(state)
                val free = !state.edit && state.sheetId == null && !state.showPicker && !state.showWidgets
                if ((state.screen == Screen.Home && free) || (state.screen == Screen.Drawer && state.openFolderId == null)) EdgeStrips(state)
            }
            state.focusDone?.let { FocusDoneScreen(state, it) }
            state.strictAsk?.let { go -> StrictConfirm(state, onConfirm = { state.strictAsk = null; go() }, onCancel = { state.strictAsk = null }) }
        }
    }
}

// ---- the editable grid -----------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeGrid(state: HomeState, ctx: Context, scroll: ScrollState) {
    val pal = state.palette
    val scope = rememberCoroutineScope()
    val gapPx = with(LocalDensity.current) { 6.dp.toPx() }
    val edit = state.edit
    val appByKey = remember(state.apps) { state.apps.associateBy { it.key } }

    var gridW by remember { mutableIntStateOf(0) }
    var viewH by remember { mutableIntStateOf(0) }
    // The whole home area (below the status bar, above the navigation bar). The Today home sizes its cells from this,
    // as if the grid filled it, so a tile has exactly the size it has on the classic home even though the grid is shorter.
    var colH by remember { mutableIntStateOf(0) }
    val topSpacerPx = with(LocalDensity.current) { 10.dp.toPx() }
    // The viewport height outside edit mode, so tiles keep their height when the edit bar appears.
    var baseH by remember { mutableIntStateOf(0) }
    // Swiping up on the home screen opens search. When the home scrolls, the grid scrolls first; search opens from the
    // drag left over once it is at the bottom (a bar grows to show it, and a quick flick needs less).
    val swipeThreshold = with(LocalDensity.current) { 56.dp.toPx() }
    val quickThreshold = with(LocalDensity.current) { 20.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    var dragId by remember { mutableStateOf<String?>(null) }
    // A tile held over the middle of a same-size tile: after a short hold it is marked, and dropping it stacks the two.
    var overId by remember { mutableStateOf<String?>(null) }
    var stackHover by remember { mutableStateOf<String?>(null) }
    // Same-size tile under the finger: reordering with it waits until the drop, so it does not slide away before a stack can form.
    var swapId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(overId) {
        stackHover = null
        if (overId != null) {
            kotlinx.coroutines.delay(380)
            stackHover = overId
        }
    }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }

    // Sideways there is twice the width and about half the height: twice the columns, fewer rows, and every tile
    // keeps its own size, so tiles flow side by side and keep roughly the same shape.
    val land = androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    val cols = if (land) state.gridCols * 2 else state.gridCols
    val rowsOnScreen = if (land) maxOf(3, (state.prefs.gridRows * 0.55f).roundToInt()) else state.prefs.gridRows
    // The Today home type puts its clock, music strip and dock around the grid; sideways there is no room, so it is just the grid.
    val chrome = state.today && !land
    fun cellSize() = (gridW - gapPx * (cols - 1)) / cols
    // Tile height: this many rows fit on one screen. Falls back to square cells until the screen is measured.
    fun cellHeight(): Float {
        // Sideways, tiles stay square whatever the screen shape; the page just scrolls.
        if (land) return cellSize()
        // Today: the same rows-on-screen of the same full height as the classic home, so the same size is the same size.
        if (chrome && colH > 0) return (colH - topSpacerPx - gapPx * (rowsOnScreen - 1)) / rowsOnScreen
        val h = if (baseH > 0) baseH else viewH
        return if (h > 0) (h - gapPx * (rowsOnScreen - 1)) / rowsOnScreen else cellSize()
    }

    fun originOf(index: Int, cells: List<Cell>): Offset {
        val c = cellSize()
        return Offset(cells[index].col * (c + gapPx), cells[index].row * (cellHeight() + gapPx))
    }

    fun hit(x: Float, y: Float): Int {
        val tiles = state.tiles
        val cells = pack(tiles, cols)
        val c = cellSize()
        val ch = cellHeight()
        for (i in tiles.indices) {
            val t = tiles[i]
            val w = minOf(t.w, cols)
            val left = cells[i].col * (c + gapPx)
            val top = cells[i].row * (ch + gapPx)
            val right = left + w * c + (w - 1) * gapPx
            val bottom = top + t.h * ch + (t.h - 1) * gapPx
            if (x in left..right && y in top..bottom) return i
        }
        return -1
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().onSizeChanged { colH = it.height }) {
        if (edit) EditTopBar(state) else Spacer(Modifier.height(10.dp))
        if (chrome) TodayHeader(state, ctx)

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { viewH = it.height; if (!edit) baseH = it.height }
                .pointerInput(state, chrome) {
                    // Watches every touch without taking it. Once the list is at its bottom (or does not scroll at all),
                    // a drag up from there opens search, whatever the list itself did with the touch.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        // Search only opens from a swipe that starts at the bottom edge of the screen. Anywhere else
                        // an upward swipe belongs to scrolling or to cycling a stack.
                        val fromBottom = !chrome && down.position.y > size.height - 130.dp.toPx()
                        val fromTop = !chrome && down.position.y < 130.dp.toPx()
                        var topY = Float.NaN
                        var downTravel = 0f
                        val vt = VelocityTracker()
                        vt.addPosition(down.uptimeMillis, down.position)
                        var bottomY = Float.NaN
                        var travel = 0f
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) {
                                val vy = vt.calculateVelocity().y
                                val fire = fromBottom && (travel > swipeThreshold || (travel > quickThreshold && vy < -1800f))
                                val fireDown = fromTop && (downTravel > swipeThreshold || (downTravel > quickThreshold && vy > 1800f))
                                val g = state.homeGestures()
                                val free = !state.edit && state.sheetId == null && state.focusActive == null && dragId == null
                                if (fire && free) (g["up"] ?: if (state.prefs.homeSearch) "search" else null)?.let { runGesture(state, it) }
                                else if (fireDown && free) g["down"]?.let { runGesture(state, it) }
                                break
                            }
                            vt.addPosition(ch.uptimeMillis, ch.position)
                            if (scroll.value >= scroll.maxValue) {
                                if (bottomY.isNaN() || ch.position.y > bottomY) bottomY = ch.position.y
                                travel = (bottomY - ch.position.y).coerceAtLeast(0f)
                            } else {
                                bottomY = Float.NaN
                                travel = 0f
                            }
                            if (scroll.value <= 0) {
                                if (topY.isNaN() || ch.position.y < topY) topY = ch.position.y
                                downTravel = (ch.position.y - topY).coerceAtLeast(0f)
                            } else {
                                topY = Float.NaN
                                downTravel = 0f
                            }
                            pull = if (fromBottom) travel else 0f
                        }
                        pull = 0f
                    }
                }
                .drawWithContent {
                    drawContent()
                    val p = (pull / swipeThreshold).coerceIn(0f, 1f)
                    if (p > 0f && (state.prefs.homeSearch || state.homeGestures().containsKey("up")) && !state.edit) {
                        val w = 36.dp.toPx() + 84.dp.toPx() * p
                        drawRoundRect(
                            color = pal.onGround.copy(alpha = 0.3f + 0.6f * p),
                            topLeft = Offset((size.width - w) / 2f, size.height - 14.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(w, 5.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                        )
                    }
                }
                .pointerInput(edit) {
                    // Double tap and long press on an empty part of the home screen. Taps on tiles are already used up.
                    if (!edit) {
                        detectTapGestures(
                            onDoubleTap = { state.homeGestures()["double"]?.let { runGesture(state, it) } },
                            onLongPress = { state.homeGestures()["long"]?.let { runGesture(state, it) } },
                        )
                    }
                }
                .verticalScroll(scroll, enabled = dragId == null)
                .padding(horizontal = 6.dp)
        ) {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().onSizeChanged { gridW = it.width }) {
                    val cells = remember(state.tiles, cols) { pack(state.tiles, cols) }
                    TileGrid(
                        state.tiles,
                        columns = cols,
                        cellHeightPx = cellHeight().roundToInt(),
                        tileModifier = { t ->
                            if (t.id == dragId) {
                                Modifier.zIndex(5f).graphicsLayer {
                                    val i = state.tiles.indexOfFirst { it.id == t.id }
                                    if (i >= 0 && i < cells.size && gridW > 0) {
                                        val o = originOf(i, pack(state.tiles, cols))
                                        translationX = dragPos.x - grab.x - o.x
                                        translationY = dragPos.y - grab.y - o.y
                                        scaleX = 1.05f
                                        scaleY = 1.05f
                                        alpha = if (stackHover != null) 0.6f else 0.92f
                                    }
                                }
                            } else if (t.id == stackHover) {
                                Modifier.zIndex(4f).graphicsLayer {
                                    scaleX = 1.06f
                                    scaleY = 1.06f
                                }.border(3.dp, pal.onGround, TileShape)
                            } else Modifier
                        },
                    ) { tile ->
                        TileFrame(state, ctx, tile, appByKey[tile.arg], edit, selected = tile.id == state.selectedId)
                    }

                    // In edit mode a transparent layer sits on top: tap selects a tile (tap again for options),
                    // hold and drag moves it, X removes it, and the corner handle resizes the selected one.
                    if (edit && gridW > 0) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .pointerInput(Unit) {
                                    // A quick swipe along a stack flips to its next or previous component; holding still picks the tile up.
                                    // Watched in the Initial pass, before the tap, hold and scroll handlers, so the swipe is ours once it starts.
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                        val t = state.tiles.getOrNull(hit(down.position.x, down.position.y)) ?: return@awaitEachGesture
                                        if (t.kind != Kind.STACK || dragId != null) return@awaitEachGesture
                                        val horizontal = t.label == "h"
                                        val quick = viewConfiguration.longPressTimeoutMillis
                                        val slop = viewConfiguration.touchSlop
                                        var total = Offset.Zero
                                        var moved = 0f
                                        var ours = false
                                        while (true) {
                                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                                            val c = ev.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                                            if (!c.pressed) break
                                            val d = c.positionChange()
                                            total += d
                                            if (!ours) {
                                                if (c.uptimeMillis - down.uptimeMillis >= quick) return@awaitEachGesture
                                                if (total.getDistance() < slop) continue
                                                val along = if (horizontal) abs(total.x) > abs(total.y) else abs(total.y) > abs(total.x)
                                                if (!along) return@awaitEachGesture
                                                ours = true
                                            }
                                            moved += if (horizontal) d.x else d.y
                                            c.consume()
                                        }
                                        if (ours && abs(moved) > 24.dp.toPx()) StackNav.flip[t.id]?.invoke(if (moved < 0) 1 else -1)
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(onTap = { p ->
                                        val i = hit(p.x, p.y)
                                        if (i < 0) {
                                            state.selectedId = null
                                        } else {
                                            val id = state.tiles[i].id
                                            if (state.selectedId == id) state.sheetId = id else state.selectedId = id
                                        }
                                    })
                                }
                                .pointerInput(Unit) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { p ->
                                            val i = hit(p.x, p.y)
                                            if (i >= 0) {
                                                val o = originOf(i, pack(state.tiles, cols))
                                                dragId = state.tiles[i].id
                                                dragPos = p
                                                grab = p - o
                                                state.selectedId = state.tiles[i].id
                                            }
                                        },
                                        onDrag = { change, amount ->
                                            val id = dragId
                                            if (id != null) {
                                                change.consume()
                                                dragPos += amount
                                                val vy = dragPos.y - scroll.value
                                                val dy = when {
                                                    vy < 90f -> -22f
                                                    vy > viewH - 90f -> 22f
                                                    else -> 0f
                                                }
                                                if (dy != 0f) {
                                                    scope.launch {
                                                        val before = scroll.value
                                                        scroll.scrollBy(dy)
                                                        dragPos += Offset(0f, (scroll.value - before).toFloat())
                                                    }
                                                }
                                                val from = state.tiles.indexOfFirst { it.id == id }
                                                val to = hit(dragPos.x, dragPos.y)
                                                val target = state.tiles.getOrNull(to)
                                                val mover = state.tiles.getOrNull(from)
                                                var centred = false
                                                val stackable = target != null && mover != null && to != from && state.canStack(mover, target, cols)
                                                swapId = if (stackable) target.id else null
                                                if (stackable) {
                                                    val o = originOf(to, pack(state.tiles, cols))
                                                    val c = cellSize()
                                                    val tw = minOf(target.w, cols) * c + (minOf(target.w, cols) - 1) * gapPx
                                                    val th = target.h * cellHeight() + (target.h - 1) * gapPx
                                                    centred = dragPos.x in (o.x + tw * 0.25f)..(o.x + tw * 0.75f) &&
                                                        dragPos.y in (o.y + th * 0.25f)..(o.y + th * 0.75f)
                                                }
                                                overId = if (centred) target?.id else null
                                                if (!stackable && from >= 0 && to >= 0 && to != from) state.move(from, to)
                                            }
                                        },
                                        onDragEnd = {
                                            val d = dragId
                                            val t = stackHover
                                            val sw = swapId
                                            if (d != null && t != null) state.stackOnto(d, t)
                                            else if (d != null) {
                                                if (sw != null) {
                                                    val a = state.tiles.indexOfFirst { it.id == d }
                                                    val b = state.tiles.indexOfFirst { it.id == sw }
                                                    if (a >= 0 && b >= 0) state.move(a, b)
                                                }
                                                state.commitLayout()
                                            }
                                            swapId = null
                                            overId = null
                                            stackHover = null
                                            dragId = null
                                        },
                                        onDragCancel = {
                                            swapId = null
                                            if (dragId != null) state.commitLayout()
                                            overId = null
                                            stackHover = null
                                            dragId = null
                                        },
                                    )
                                }
                        ) {
                            val cellsNow = pack(state.tiles, cols)
                            state.tiles.forEachIndexed { i, t ->
                                if (t.id != dragId) {
                                    val o = originOf(i, cellsNow)
                                    Box(
                                        Modifier
                                            .offset { IntOffset((o.x + 6.dp.toPx()).roundToInt(), (o.y + 6.dp.toPx()).roundToInt()) }
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(pal.onGround)
                                            .clickable { state.remove(t) },
                                        contentAlignment = Alignment.Center,
                                    ) { Glyph("close", pal.ground, 12.dp, 3f) }
                                }
                            }

                            val selIndex = state.tiles.indexOfFirst { it.id == state.selectedId && it.allowedSizes(cols).size > 1 && it.id != dragId }
                            if (selIndex >= 0) {
                                val t = state.tiles[selIndex]
                                val o = originOf(selIndex, cellsNow)
                                val c = cellSize()
                                val wPx = minOf(t.w, cols) * c + (minOf(t.w, cols) - 1) * gapPx
                                val hPx = t.h * cellHeight() + (t.h - 1) * gapPx
                                val sizes = t.allowedSizes(cols)
                                val canW = sizes.map { it.first }.distinct().size > 1
                                val canH = sizes.map { it.second }.distinct().size > 1
                                // Handles sit on every edge and the bottom-right corner; drag any of them to resize that way.
                                // sx/sy say which way a drag grows the tile: -1 for left/top, 1 for right/bottom, 0 for none.
                                @Composable
                                fun Handle(cx: Float, cy: Float, sx: Int, sy: Int, hitW: Float, hitH: Float) {
                                    val dens = LocalDensity.current
                                    Box(
                                        Modifier
                                            .offset { IntOffset((cx - hitW / 2).roundToInt(), (cy - hitH / 2).roundToInt()) }
                                            .size(with(dens) { hitW.toDp() }, with(dens) { hitH.toDp() })
                                            .pointerInput(t.id, sx, sy) {
                                                var accX = 0f
                                                var accY = 0f
                                                var w0 = 1
                                                var h0 = 1
                                                detectDragGestures(
                                                    onDragStart = {
                                                        accX = 0f
                                                        accY = 0f
                                                        val cur = state.tiles.firstOrNull { it.id == t.id }
                                                        if (cur != null) {
                                                            w0 = cur.w
                                                            h0 = cur.h
                                                        }
                                                    },
                                                    onDrag = { change, amount ->
                                                        change.consume()
                                                        accX += amount.x
                                                        accY += amount.y
                                                        val dw = w0 + sx * accX / (cellSize() + gapPx)
                                                        val dh = h0 + sy * accY / (cellHeight() + gapPx)
                                                        val cur = state.tiles.firstOrNull { it.id == t.id }
                                                        if (cur != null) {
                                                            val best = cur.allowedSizes(cols).minByOrNull { sz ->
                                                                val ex = if (sx != 0) sz.first - dw else (sz.first - w0).toFloat() * 4f
                                                                val ey = if (sy != 0) sz.second - dh else (sz.second - h0).toFloat() * 4f
                                                                ex * ex + ey * ey
                                                            }
                                                            if (best != null && (best.first != cur.w || best.second != cur.h)) {
                                                                state.resize(cur.id, best.first, best.second)
                                                            }
                                                        }
                                                    },
                                                )
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        // A quiet grip: a short bar lying along the edge, a small dot at the corner.
                                        val grip = Modifier.clip(CircleShape).background(pal.onGround.copy(alpha = 0.5f))
                                        Box(
                                            when {
                                                sx != 0 && sy != 0 -> grip.size(10.dp)
                                                sx != 0 -> grip.size(width = 4.dp, height = 30.dp)
                                                else -> grip.size(width = 30.dp, height = 4.dp)
                                            },
                                        )
                                    }
                                }
                                val inset = gapPx
                                val midX = o.x + wPx / 2
                                val midY = o.y + hPx / 2
                                val edge = 44.dp.toPx()
                                val corner = 56.dp.toPx()
                                // Edge zones run along most of the side, so a drag anywhere along it grabs the handle.
                                val alongV = maxOf(hPx * 0.6f, 64.dp.toPx())
                                val alongH = maxOf(wPx * 0.6f, 64.dp.toPx())
                                if (canW) {
                                    Handle(o.x + wPx - inset - edge / 4, midY, 1, 0, edge, alongV)
                                    Handle(o.x + inset + edge / 4, midY, -1, 0, edge, alongV)
                                }
                                if (canH) {
                                    Handle(midX, o.y + hPx - inset - edge / 4, 0, 1, alongH, edge)
                                    Handle(midX, o.y + inset + edge / 4, 0, -1, alongH, edge)
                                }
                                // Corners are drawn last, so they win where they overlap an edge.
                                if (canW && canH) {
                                    val k = corner / 2 - 4.dp.toPx()
                                    Handle(o.x + wPx - inset - k, o.y + hPx - inset - k, 1, 1, corner, corner)
                                    Handle(o.x + inset + k, o.y + hPx - inset - k, -1, 1, corner, corner)
                                    Handle(o.x + wPx - inset - k, o.y + inset + k, 1, -1, corner, corner)
                                    Handle(o.x + inset + k, o.y + inset + k, -1, -1, corner, corner)
                                }
                            }
                        }
                    }
                }

                if (edit) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 12.dp)
                            .height(64.dp)
                            .drawBehind {
                                drawRoundRect(
                                    color = pal.onGround.copy(alpha = 0.45f),
                                    cornerRadius = CornerRadius(20.dp.toPx()),
                                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
                                )
                            }
                            .clip(TileShape)
                            .clickable {
                                state.stackTarget = null
                                state.showPicker = true
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Glyph("plus", pal.onGround, 20.dp, 2.4f)
                            BasicText("ADD COMPONENT", style = monoStyle(pal.onGround, 11, bold = true))
                        }
                    }
                }
            }
        }

        if (chrome && !edit) {
            TodayMusic(state, ctx)
            TodayDock(state, ctx)
        }
        if (edit) EditBar(state)
    }
}

/** One tile on the home screen: its fill, its content, and what a tap does. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileFrame(
    state: HomeState,
    ctx: Context,
    tile: Tile,
    app: app.koma.launcher.data.AppInfo?,
    edit: Boolean,
    selected: Boolean,
) {
    val pal = state.palette
    if (tile.kind == Kind.SPACE) {
        SpaceFrame(state, tile, edit, selected)
        return
    }
    val look = state.prefs.tileStyle
    val glass = look == "glass"
    val frosted = look != "solid"
    val lk = tileLook(state, tile)

    val click: () -> Unit = { onTileClick(state, ctx, tile) }
    val none = remember { MutableInteractionSource() }
    // The tile follows the finger while it is swiped (all-apps gestures) and springs back on release.
    val swipeX = remember { Animatable(0f) }
    val swipeY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val s = if (edit) 0.96f else 1f
                scaleX = s
                scaleY = s
                translationX = swipeX.value
                translationY = swipeY.value
            }
            .then(if (frosted) Modifier.border(1.dp, if (glass) pal.onGround.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.3f), TileShape) else Modifier)
            .then(if (selected) Modifier.border(3.dp, pal.onGround, TileShape) else Modifier)
            .then(
                if (edit) Modifier
                else {
                    val gest = if (tile.kind == Kind.ALL) remember(tile.arg) { Gestures.parse(tile.arg) } else emptyMap()
                    val swipes = gest.filterKeys { it == "up" || it == "down" || it == "left" || it == "right" }
                    Modifier
                        .then(
                            if (swipes.isNotEmpty()) Modifier.pointerInput(swipes) {
                                swipeGestures(
                                    swipes,
                                    onMove = { dx, dy ->
                                        // Rubber band: follows the finger, but gets heavier the further it goes.
                                        val limit = 60.dp.toPx()
                                        scope.launch {
                                            swipeX.snapTo(limit * kotlin.math.tanh(dx / limit))
                                            swipeY.snapTo(limit * kotlin.math.tanh(dy / limit))
                                        }
                                    },
                                    onEnd = { action ->
                                        scope.launch {
                                            // A committed swipe throws the tile a little further, then everything settles back.
                                            if (action != null) {
                                                val kx = swipeX.value
                                                val ky = swipeY.value
                                                launch { swipeX.animateTo(kx * 1.5f, tween(90)) }
                                                launch { swipeY.animateTo(ky * 1.5f, tween(90)) }.join()
                                                runGesture(state, action)
                                            }
                                            launch { swipeX.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)) }
                                            launch { swipeY.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)) }
                                        }
                                    },
                                )
                            } else Modifier,
                        )
                        .combinedClickable(
                            interactionSource = none,
                            indication = null,
                            onClick = click,
                            onLongClick = { gest["long"]?.let { runGesture(state, it) } ?: state.startEdit() },
                            onDoubleClick = gest["double"]?.let { a -> { runGesture(state, a) } },
                        )
                }
            )
    ) {
        if (tile.kind == Kind.STACK) StackTile(state, ctx, tile, edit)
        else TileBody(state, ctx, tile, app, lk.fill, lk.ink, lk.base)
    }
}

/** Runs an all-apps tile gesture's action. */
internal fun runGesture(state: HomeState, action: String) {
    val system = when (action) {
        "lock" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
        "shade" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
        "quick" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
        "recents" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
        "shot" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
        "power" -> android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
        else -> null
    }
    if (system != null) {
        val ok = android.os.Build.VERSION.SDK_INT >= 28 && app.koma.launcher.data.KomaFocusService.perform(system)
        if (!ok) {
            val c = state.appCtx
            android.widget.Toast.makeText(c, "Switch on Koma in Accessibility settings to use this gesture", android.widget.Toast.LENGTH_LONG).show()
            tryStart(c, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        return
    }
    when {
        action == "search" -> state.openSearch()
        action == "notes" -> state.open(Screen.Notes)
        action == "tasks" -> state.open(Screen.Tasks)
        action == "focus" -> state.open(Screen.Focus)
        action == "clock" -> state.openClock(0)
        action == "calc" -> state.open(Screen.Calc)
        action == "kapps" -> state.open(Screen.Apps)
        action == "settings" -> state.open(Screen.Settings)
        action == "edit" -> state.startEdit()
        action == "drawer" -> state.openDrawer()
        action.startsWith("app:") -> state.launch(action.removePrefix("app:"))
    }
}

/**
 * Swipes on a tile. A swipe is only taken (so the home list does not scroll) when its direction has an action;
 * other directions are left to the list.
 */
private suspend fun PointerInputScope.swipeGestures(
    actions: Map<String, String>,
    onMove: (Float, Float) -> Unit,
    onEnd: (String?) -> Unit,
) {
    val min = 40.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var dir: String? = null
        val slop = awaitTouchSlopOrCancellation(down.id) { change, over ->
            val d = if (abs(over.x) > abs(over.y)) (if (over.x > 0) "right" else "left") else (if (over.y > 0) "down" else "up")
            if (actions[d] != null) {
                dir = d
                change.consume()
            }
        } ?: return@awaitEachGesture
        val d = dir ?: return@awaitEachGesture
        val vertical = d == "up" || d == "down"
        var along = 0f
        val finished = drag(slop.id) {
            it.consume()
            along = if (vertical) it.position.y - down.position.y else it.position.x - down.position.x
            // Only the swiped axis moves, and only towards the swipe's direction (a little give the other way).
            val sign = if (d == "down" || d == "right") 1f else -1f
            val v = if (along * sign >= 0f) along else along * 0.2f
            if (vertical) onMove(0f, v) else onMove(v, 0f)
        }
        val committed = finished && abs(along) >= min && (if (d == "down" || d == "right") along > 0 else along < 0)
        onEnd(if (committed) actions[d] else null)
    }
}

/** An empty space: invisible on the home screen, a dashed outline while editing. */
@Composable
private fun SpaceFrame(state: HomeState, tile: Tile, edit: Boolean, selected: Boolean) {
    val pal = state.palette
    Box(
        Modifier.fillMaxSize().then(
            if (!edit) Modifier else Modifier.drawBehind {
                drawRoundRect(
                    color = pal.onGround.copy(alpha = if (selected) 0.8f else 0.28f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx()),
                )
            }
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (edit) BasicText("EMPTY / ${tile.w}x${tile.h}", style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10, bold = true))
    }
}

internal class TileLook(val base: Color, val fill: Color, val ink: Color)

/** The automatic colour slot of component [i] in a stack: neighbours get different colours instead of one shared one. */
internal fun stackSlot(stack: Tile, i: Int) = abs(stack.id.hashCode()) % 97 + i

/** Colours for a tile under the current tile style (solid, glass or tinted glass). */
internal fun tileLook(state: HomeState, tile: Tile, slot: Int = -1): TileLook {
    val pal = state.palette
    val look = state.prefs.tileStyle
    val base = if (tile.color >= 0) pal.all[tile.color % pal.all.size]
    else if (slot >= 0) pal.tiles[slot % pal.tiles.size]
    else pal.tiles[abs(tile.kind.ordinal * 7 + tile.arg.hashCode()) % pal.tiles.size]
    // Glass: the same frosted look as the drawer panel. Tint: the tile's own colour, frosted and see-through.
    val fill = when (look) {
        "glass" -> pal.ground.copy(alpha = 0.58f)
        "tint" -> base.copy(alpha = 0.68f)
        else -> base
    }
    return TileLook(base, fill, if (look == "glass") pal.onGround else pal.ink)
}

/** What a tile draws inside its frame. Also used for each page of a stack. */
@Composable
internal fun TileBody(
    state: HomeState,
    ctx: Context,
    tile: Tile,
    app: app.koma.launcher.data.AppInfo?,
    fill: Color,
    ink: Color,
    base: Color,
) {
    val pal = state.palette
    val glass = state.prefs.tileStyle == "glass"
    when (tile.kind) {
        Kind.CLOCK -> ClockTile(tile.w, tile.h, fill, ink, ClockArg.style(tile.arg).ifEmpty { state.prefs.clockStyle }, state.prefs.clockSeconds, ClockArg.image(tile.arg), ClockArg.design(tile.arg))
        Kind.DATE -> DateTile(tile.w, tile.h, fill, ink)
        Kind.KANJI -> {
            val words = Words.parse(tile.arg)
            val fg = if (words.tone >= 0) pal.all[words.tone % pal.all.size] else kanjiInk(fill, if (glass) ink else pal.tiles[4 % pal.tiles.size], ink)
            KanjiTile(fill, fg, words)
        }
        Kind.WEATHER -> WeatherTile(
            tile.w, tile.h, fill, ink,
            granted = state.locationGranted || state.prefs.weatherPlace.isNotEmpty(),
            load = { state.loadWeather() },
            cached = state.cachedWeather(),
            showHiLo = state.prefs.weatherHiLo,
            showCat = state.prefs.weatherCat,
            showCity = state.prefs.weatherCity,
            source = state.prefs.weatherUnit + state.prefs.weatherPlace + state.prefs.weatherPlace2,
            load2 = { state.loadWeather2() },
            cached2 = state.cachedWeather2(),
            hasSecond = state.prefs.weatherPlace2.isNotEmpty(),
        )
        Kind.MUSIC -> {
            val now = state.media.now
            LaunchedEffect(now?.pkg) { now?.pkg?.takeIf { it.isNotBlank() }?.let { state.prefs.lastMusic = it } }
            MusicTile(
                w = tile.w, h = tile.h, bg = fill, ink = ink,
                title = when {
                    now != null -> now.title
                    !state.media.hasAccess -> "Open music"
                    else -> "Nothing playing"
                },
                artist = when {
                    now != null -> now.artist
                    !state.media.hasAccess -> "Play button: allow access"
                    else -> "Start a player"
                },
                playing = now?.playing == true,
                onToggle = {
                    if (!state.media.hasAccess) tryStart(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    else state.media.toggle()
                },
                onNext = { state.media.next() },
                onPrevious = { state.media.previous() },
                now = now,
                onSeek = { state.media.seekTo(it) },
                style = MusicArg.style(tile.arg).ifEmpty { state.prefs.musicStyle },
                design = MusicArg.design(tile.arg),
            )
        }
        Kind.CALENDAR -> CalendarTile(tile.w, tile.h, fill, ink, state.calendarGranted, state.hiddenCalendars, state.calendarColors, state.prefs.calRefreshMin)
        Kind.SEARCH -> SearchTile(
            w = tile.w, h = tile.h, bg = fill, ink = ink,
            icons = state.icons,
            recent = state.recentApps(),
            onChip = { Engines.search(ctx, state.prefs, it) },
            onApp = { state.launch(it.key) },
            onVoice = { startVoice(ctx) },
            onLens = { startLens(ctx) },
        )
        Kind.FAVS -> FavsTile(tile.w, tile.h, fill, ink, state.contactsGranted)
        Kind.CALLS -> CallsTile(tile.w, tile.h, fill, ink, state.callsGranted)
        Kind.GROUP -> GroupTile(tile.w, tile.h, fill, ink, state.appsOf(tile.arg), state.icons) { state.launch(it.key) }
        Kind.DEVICE -> DeviceTile(tile.w, tile.h, fill, ink)
        Kind.TOGGLE -> ToggleTile(tile.arg.ifEmpty { "torch" }, fill, ink)
        Kind.REMINDERS -> RemindersTile(tile.w, tile.h, fill, ink, state.tasks) { state.toggleTask(it) }
        Kind.NOTE -> NoteTile(tile.w, tile.h, fill, ink, Notes.live(state.notes).sortedByDescending { it.updated }) { id ->
            state.noteOpenId = id
            state.open(Screen.Notes)
        }
        Kind.PHOTO -> PhotoTile(fill, ink, tile.arg)
        Kind.SCREENTIME -> ScreenTimeTile(state, tile.w, tile.h, fill, ink, state.usageGranted)
        Kind.BLUETOOTH -> BluetoothTile(tile.w, tile.h, fill, ink, state.btGranted, state.prefs.btIcon)
        Kind.COUNTDOWN -> CountdownTile(tile.w, tile.h, fill, ink, tile.arg)
        Kind.TIMER -> TimerTile(state, tile.id, tile.w, tile.h, fill, ink, tile.arg.toIntOrNull() ?: 10, state.prefs.timerEnd, state.prefs.timerLen, state.prefs.timerLeft)
        Kind.STOPWATCH -> StopwatchTile(tile.w, tile.h, fill, ink)
        Kind.CALC -> if (tile.w == 1 && tile.h == 1) AppTile(null, "Calc", "calc", state.icons, fill, ink) else CalcTile(tile.w, tile.h, fill, ink) { state.open(Screen.Calc) }
        Kind.KAPPS -> AppTile(null, "Koma", "all", state.icons, fill, ink)
        Kind.ALARMS -> {
            val tctx = LocalContext.current
            AlarmsTile(tile.w, tile.h, fill, ink, state.clock.alarms) { id, on ->
                state.clock.setOn(id, on)
                if (on) needNotifications(state, tctx)
            }
        }
        Kind.FOCUS -> FocusTile(state, tile.w, tile.h, fill, ink)
        Kind.WIDGET -> {
            val id = tile.arg.toIntOrNull()
            if (id != null) WidgetTile(state, id, fill, ink)
        }
        Kind.APP -> AppTile(app, tile.label, tile.glyph, state.icons, fill, ink)
        Kind.ALL -> AllAppsTile(pal.ground, pal.onGround)
        Kind.STACK -> Unit
        Kind.SPACE -> Unit
    }
}

/**
 * Several components in one tile. A pager over a practically endless page range gives the circular loop:
 * page % n picks the component, so swiping past the last one lands on the first. Pages scale and fade as they
 * travel, and snap with a soft spring. Each stack slides vertically or, when its label is "h", horizontally.
 */
@Composable
private fun StackTile(state: HomeState, ctx: Context, tile: Tile, edit: Boolean) {
    val kids = remember(tile.arg, tile.w, tile.h) { tile.children() }
    val n = kids.size
    if (n == 0) return
    val horizontal = tile.label == "h"
    androidx.compose.runtime.key(tile.id, n, horizontal) {
        val first = (1 shl 30) - (1 shl 30) % n + (state.stackIndex[tile.id] ?: 0).coerceIn(0, n - 1)
        val ps = androidx.compose.foundation.pager.rememberPagerState(initialPage = first) { Int.MAX_VALUE }
        LaunchedEffect(ps) {
            androidx.compose.runtime.snapshotFlow { ps.settledPage % n }.collect { state.stackIndex[tile.id] = it }
        }
        val fling = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
            state = ps,
            snapAnimationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = 360f),
        )
        val content: @Composable androidx.compose.foundation.pager.PagerScope.(Int) -> Unit = { page ->
            val child = kids[page % n]
            val lk = tileLook(state, child, stackSlot(tile, page % n))
            val app = remember(state.apps, child.arg) { state.apps.firstOrNull { it.key == child.arg } }
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    // Distance from the centred page: 0 centred, 1 one page away.
                    val d = ((page - ps.currentPage) - ps.currentPageOffsetFraction).coerceIn(-1f, 1f)
                    val a = kotlin.math.abs(d)
                    val sc = 1f - 0.14f * a
                    scaleX = sc
                    scaleY = sc
                    alpha = 1f - 0.75f * a
                }
            ) { TileBody(state, ctx, child, app, lk.fill, lk.ink, lk.base) }
        }
        // One swipe moves exactly one component: the drag is clamped to a page either side of where it began, and on
        // release the stack settles on the next, the previous, or back where it was.
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val job = remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }
        androidx.compose.runtime.DisposableEffect(tile.id, ps) {
            StackNav.flip[tile.id] = { dir ->
                job[0]?.cancel()
                job[0] = scope.launch {
                    ps.animateScrollToPage(ps.currentPage + dir, animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 380f))
                }
            }
            onDispose { StackNav.flip.remove(tile.id) }
        }
        val acc = remember { FloatArray(1) }
        val start = remember { IntArray(1) }
        val begin = {
            job[0]?.cancel()
            start[0] = ps.currentPage
            acc[0] = ps.currentPageOffsetFraction * ps.layoutInfo.pageSize
        }
        val move = { d: Float ->
            val size = ps.layoutInfo.pageSize.toFloat()
            val next = (acc[0] - d).coerceIn(-size, size)
            ps.dispatchRawDelta(next - acc[0])
            acc[0] = next
        }
        val end = {
            val size = ps.layoutInfo.pageSize.toFloat()
            val target = start[0] + when {
                acc[0] > size * 0.2f -> 1
                acc[0] < -size * 0.2f -> -1
                else -> 0
            }
            job[0] = scope.launch {
                ps.animateScrollToPage(target, animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 380f))
            }
            Unit
        }
        // A component with its own list (Reminders, Calendar...) eats the swipe along the stack's direction. When that list
        // is at its end, or can't scroll, the leftover moves the stack instead, so the stack can still be swiped.
        val handOff = remember(ps, horizontal) {
            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                var active = false
                private fun axis(o: androidx.compose.ui.geometry.Offset) = if (horizontal) o.x else o.y
                override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                    if (!active || source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) return androidx.compose.ui.geometry.Offset.Zero
                    move(axis(available))
                    return if (horizontal) androidx.compose.ui.geometry.Offset(available.x, 0f) else androidx.compose.ui.geometry.Offset(0f, available.y)
                }
                override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                    val d = axis(available)
                    if (source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput || d == 0f) return androidx.compose.ui.geometry.Offset.Zero
                    if (!active) {
                        active = true
                        begin()
                    }
                    move(d)
                    return if (horizontal) androidx.compose.ui.geometry.Offset(available.x, 0f) else androidx.compose.ui.geometry.Offset(0f, available.y)
                }
                override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                    if (!active) return androidx.compose.ui.unit.Velocity.Zero
                    active = false
                    end()
                    return available
                }
            }
        }
        val dragger = Modifier.pointerInput(horizontal, edit) {
            if (edit) {
                // While editing, a quick swipe flips through the stack; holding first still picks the tile up to move it.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val quick = viewConfiguration.longPressTimeoutMillis
                    var swipe = false
                    val reached = { c: androidx.compose.ui.input.pointer.PointerInputChange ->
                        if (c.uptimeMillis - down.uptimeMillis < quick) {
                            c.consume()
                            swipe = true
                        }
                    }
                    val slop = if (horizontal) awaitHorizontalTouchSlopOrCancellation(down.id) { c, _ -> reached(c) }
                    else awaitVerticalTouchSlopOrCancellation(down.id) { c, _ -> reached(c) }
                    if (slop != null && swipe) {
                        begin()
                        if (horizontal) horizontalDrag(slop.id) { c ->
                            c.consume()
                            move(c.positionChange().x)
                        } else verticalDrag(slop.id) { c ->
                            c.consume()
                            move(c.positionChange().y)
                        }
                        end()
                    }
                }
                return@pointerInput
            }
            if (horizontal) detectHorizontalDragGestures(
                onDragStart = { begin() }, onDragEnd = { end() }, onDragCancel = { end() },
            ) { change, d ->
                change.consume()
                move(d)
            } else detectVerticalDragGestures(
                onDragStart = { begin() }, onDragEnd = { end() }, onDragCancel = { end() },
            ) { change, d ->
                change.consume()
                move(d)
            }
        }
        Box(Modifier.fillMaxSize().nestedScroll(handOff).then(dragger)) {
            if (horizontal) {
                androidx.compose.foundation.pager.HorizontalPager(
                    state = ps,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = false,
                    beyondViewportPageCount = 1,
                    flingBehavior = fling,
                    pageContent = content,
                )
            } else {
                androidx.compose.foundation.pager.VerticalPager(
                    state = ps,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = false,
                    beyondViewportPageCount = 1,
                    flingBehavior = fling,
                    pageContent = content,
                )
            }
            if (n > 1) StackDots(
                state, kids, ps, n, horizontal,
                Modifier.align(if (horizontal) Alignment.BottomCenter else Alignment.CenterEnd),
            )
        }
    }
}

/** Lets the edit-mode overlay flip a stack: the overlay sits above every tile, so a stack never sees the touch itself. */
private object StackNav {
    val flip = HashMap<String, (Int) -> Unit>()
}

/** Dots along the edge: a column on the right for vertical stacks, a row at the bottom for horizontal ones. */
@Composable
private fun StackDots(
    state: HomeState,
    kids: List<Tile>,
    ps: androidx.compose.foundation.pager.PagerState,
    n: Int,
    horizontal: Boolean,
    modifier: Modifier,
) {
    val current = ps.currentPage % n
    val ink = tileLook(state, kids[current]).ink
    @Composable
    fun dot(i: Int) {
        val on = i == current
        val len by androidx.compose.animation.core.animateDpAsState(if (on) 12.dp else 4.dp, label = "dotLen")
        val al by androidx.compose.animation.core.animateFloatAsState(if (on) 0.9f else 0.35f, label = "dotA")
        Box(
            (if (horizontal) Modifier.width(len).height(3.dp) else Modifier.width(3.dp).height(len))
                .clip(CircleShape).background(ink.copy(alpha = al))
        )
    }
    if (horizontal) {
        Row(modifier.padding(bottom = 5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(n) { dot(it) }
        }
    } else {
        Column(modifier.padding(end = 5.dp), verticalArrangement = Arrangement.spacedBy(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            repeat(n) { dot(it) }
        }
    }
}

internal fun onTileClick(state: HomeState, ctx: Context, tile: Tile) {
    when (tile.kind) {
        Kind.APP -> if (tile.arg.isBlank()) state.openDrawer(pickFor = Pick("assign", tile.id)) else state.launch(tile.arg)
        Kind.GROUP -> if (tile.arg.isBlank()) state.openDrawer(pickFor = Pick("group", tile.id))
        Kind.PHOTO -> if (tile.arg.isBlank()) state.photoRequest = tile.id
        Kind.ALL -> state.openDrawer()
        Kind.SEARCH -> state.openSearch()
        Kind.CALENDAR -> if (!state.calendarGranted) state.request(Manifest.permission.READ_CALENDAR) else openCalendar(ctx)
        Kind.WEATHER -> if (!state.locationGranted && state.prefs.weatherPlace.isEmpty()) state.request(Manifest.permission.ACCESS_COARSE_LOCATION)
        Kind.MUSIC -> openMusic(state, ctx, tile)
        Kind.KANJI -> Unit
        Kind.FAVS -> if (!state.contactsGranted) state.request(Manifest.permission.READ_CONTACTS)
        Kind.CALLS -> if (!state.callsGranted) state.request(Manifest.permission.READ_CALL_LOG)
        Kind.SCREENTIME -> if (!state.usageGranted) tryStart(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        Kind.CLOCK -> state.openClock(0)
        Kind.BLUETOOTH -> if (!state.btGranted) state.request(Manifest.permission.BLUETOOTH_CONNECT) else tryStart(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        Kind.TIMER -> {
            if (state.prefs.timerEnd == 0L && state.prefs.timerLeft == 0L) state.timerSec = if (state.prefs.timerLen > 0L) 0 else (tile.arg.toIntOrNull() ?: 10) * 60
            state.openClock(3)
        }
        Kind.STOPWATCH -> state.openClock(2)
        Kind.CALC -> state.open(Screen.Calc)
        Kind.KAPPS -> state.open(Screen.Apps)
        Kind.ALARMS -> state.openClock(0)
        Kind.NOTE -> state.open(Screen.Notes)
        Kind.REMINDERS -> state.open(Screen.Tasks)
        Kind.FOCUS -> state.open(Screen.Focus)
        Kind.STACK -> {
            val kids = tile.children()
            val c = kids.getOrNull((state.stackIndex[tile.id] ?: 0).coerceIn(0, (kids.size - 1).coerceAtLeast(0)))
            val needsPick = c != null && c.arg.isBlank() && (c.kind == Kind.APP || c.kind == Kind.GROUP || c.kind == Kind.PHOTO)
            if (c != null && !needsPick) onTileClick(state, ctx, c)
        }
        else -> Unit
    }
}

internal fun tryStart(ctx: Context, intent: Intent): Boolean = try {
    ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: Exception) {
    false
}

/**
 * Opens a music app: the one chosen for this tile, else the player playing now, else the last one seen playing,
 * else the phone's default music app. If none of those exist the drawer opens so one can be chosen once.
 */
internal fun openMusic(state: HomeState, ctx: Context, tile: Tile) {
    val chosen = MusicArg.app(tile.arg)
    if (chosen.isNotBlank() && state.apps.any { it.key == chosen }) {
        state.launch(chosen)
        return
    }
    for (pkg in listOf(state.media.now?.pkg.orEmpty(), state.prefs.lastMusic)) {
        val launch = if (pkg.isNotBlank()) ctx.packageManager.getLaunchIntentForPackage(pkg) else null
        if (launch != null && tryStart(ctx, launch)) return
    }
    val def = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
    if (ctx.packageManager.resolveActivity(def, 0) != null && tryStart(ctx, def)) return
    Toast.makeText(ctx, "Choose your music app", Toast.LENGTH_SHORT).show()
    state.openDrawer(pickFor = Pick("music", tile.id))
}

/** The phone's default calendar app. */
private fun openCalendar(ctx: Context) {
    if (tryStart(ctx, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))) return
    tryStart(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}")))
}

private fun startVoice(ctx: Context) {
    tryStart(ctx, Intent(RecognizerIntent.ACTION_WEB_SEARCH))
}

private fun startLens(ctx: Context) {
    val lens = ctx.packageManager.getLaunchIntentForPackage("com.google.ar.lens")
    if (lens != null) tryStart(ctx, lens)
    else Toast.makeText(ctx, "Google Lens is not installed", Toast.LENGTH_SHORT).show()
}

// ---- chrome ---------------------------------------------------------------------------------------

