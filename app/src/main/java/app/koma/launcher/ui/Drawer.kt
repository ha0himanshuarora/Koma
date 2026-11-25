package app.koma.launcher.ui

import app.koma.launcher.data.MusicArg
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import app.koma.launcher.data.AppNote
import app.koma.launcher.data.KomaNotificationListener
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.koma.launcher.data.Gestures
import app.koma.launcher.data.AppInfo
import app.koma.launcher.data.Engines
import app.koma.launcher.data.FocusEngine
import app.koma.launcher.data.Kind
import kotlinx.coroutines.launch
import kotlin.math.abs

/** One line of the drawer: an installed app, or the pinned Koma Settings entry. */
private class DrawerRow(val label: String, val app: AppInfo?, val builtin: String?, val pinned: Boolean = false, val header: String? = null)

private const val KOMA_HEADER = "koma"
private const val SETTINGS_ID = "settings"
private const val SETTINGS_LABEL = "Koma Settings"
private const val KAPPS_ID = "kapps"
private const val KAPPS_LABEL = "Koma apps"
private const val FOLDER_PREFIX = "folder:"
private const val NEW_FOLDER_ID = "newfolder"

private fun letterOf(r: DrawerRow): String {
    if (r.header != null) return r.header
    if (r.pinned) return "#"
    val c = r.label.firstOrNull()?.uppercaseChar() ?: return "#"
    return if (c in 'A'..'Z') c.toString() else "#"
}

/**
 * The app drawer, as in the design: a frosted panel over the live wallpaper, the search pill at the bottom of
 * the panel, and the alphabet rail down the edge (drag from either side to scrub it).
 * Koma's own built-in apps live in Koma Settings, not here. In pick mode a tap chooses an app for a tile.
 */
private val rowFadeIn = androidx.compose.animation.core.tween<Float>(180)
private val rowFadeOut = androidx.compose.animation.core.tween<Float>(90)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Drawer(state: HomeState) {
    val pal = state.palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val pick = state.pick
    var query by remember { mutableStateOf("") }
    var popupApp by remember { mutableStateOf<AppInfo?>(null) }
    var popupY by remember { mutableFloatStateOf(0f) }
    var sheetApp by remember { mutableStateOf<AppInfo?>(null) }
    var editApp by remember { mutableStateOf<AppInfo?>(null) }
    val focusReq = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val active = state.focusActive

    val sortMode = state.prefs.drawerSort
    val headersOn = state.prefs.drawerHeaders
    val hiddenSet = state.hiddenKeys
    val rows = remember(state.apps, query, pick, active, sortMode, headersOn, hiddenSet, state.folders, state.prefs.drawerKoma) {
        val q = query.trim()
        val list = ArrayList<DrawerRow>()
        // Hidden apps stay out of the list but turn up when you search for them.
        // Apps in a folder set to "hide from app drawer" are left out, but still turn up in search and when picking apps.
        val inHiddenFolder = if (q.isEmpty() && pick == null) {
            state.folders.filter { it.hideApps }.flatMap { f -> f.items.filter { !it.widget }.map { it.arg } }.toSet()
        } else emptySet()
        val base = state.apps.filter { (active == null || FocusEngine.allows(it.pkg)) && (q.isNotEmpty() || it.key !in hiddenSet) && it.key !in inHiddenFolder && (it.pkg != ctx.packageName || (q.isNotEmpty() && pick == null)) }
        val ordered = when (sortMode) {
            "za" -> base.sortedByDescending { it.label.lowercase() }
            "recent" -> {
                val order = state.prefs.recent.split("|").filter { it.isNotEmpty() }
                base.sortedWith(compareBy({ order.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.label.lowercase() }))
            }
            "newest" -> base.sortedByDescending {
                try { ctx.packageManager.getPackageInfo(it.pkg, 0).firstInstallTime } catch (e: Exception) { 0L }
            }
            else -> base.sortedBy { it.label.lowercase() }
        }
        val alphaSort = sortMode == "alpha" || sortMode == "za"
        var lastLetter = ""
        ordered.forEach { a ->
            val row = DrawerRow(a.label, a, null)
            if (headersOn && alphaSort && q.isEmpty() && pick == null) {
                val l = letterOf(row)
                if (l != lastLetter) {
                    list += DrawerRow(l, null, null, header = l)
                    lastLetter = l
                }
            }
            list += row
        }
        // While searching, the best match goes last so it sits at the bottom, next to the thumb.
        val filtered = if (q.isEmpty()) list else app.koma.launcher.data.AppMatch.filter(list, q) { it.label }.reversed().toMutableList()
        val pinned = ArrayList<DrawerRow>()
        val koma = ArrayList<DrawerRow>()
        if (pick == null) {
            if (q.isEmpty() || SETTINGS_LABEL.contains(q, ignoreCase = true)) koma += DrawerRow(SETTINGS_LABEL, null, SETTINGS_ID, pinned = true)
            if (q.isEmpty()) koma += DrawerRow(KAPPS_LABEL, null, KAPPS_ID, pinned = true)
            state.folders.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
                .forEach { pinned += DrawerRow(it.name.ifBlank { "Folder" }, null, FOLDER_PREFIX + it.id, pinned = true) }
        }
        // The Koma entries get their own header, a mark in place of a letter, so they read as the launcher's own.
        if (q.isEmpty() && pick == null && koma.isNotEmpty()) koma.add(0, DrawerRow("", null, null, pinned = true, header = KOMA_HEADER))
        if (q.isEmpty() && state.prefs.drawerKoma == "bottom") pinned + filtered + koma else koma + pinned + filtered
    }

    LaunchedEffect(Unit) {
        if (state.searchFocus) {
            try {
                focusReq.requestFocus()
            } catch (e: Exception) {
            }
        }
    }

    // Each app's newest notification, shown under its name. Refreshed while the drawer is open.
    val notes by androidx.compose.runtime.produceState(emptyMap<String, AppNote>()) {
        while (true) {
            value = KomaNotificationListener.allLatest()
            awaitVisible()
            kotlinx.coroutines.delay(3000L)
        }
    }
    fun choose(row: DrawerRow) {
        val app = row.app
        when {
            row.builtin == SETTINGS_ID -> state.open(Screen.Settings)
            row.builtin == KAPPS_ID -> state.open(Screen.Apps)
            row.builtin == NEW_FOLDER_ID -> state.openFolderId = state.newFolder().id
            row.builtin?.startsWith(FOLDER_PREFIX) == true -> state.openFolderId = row.builtin.removePrefix(FOLDER_PREFIX)
            app == null -> Unit
            pick == null -> {
                // Open the app first and only go Home once it covers Koma, so Home never flashes in between.
                if (!state.launchThenHome(app.key)) state.screen = Screen.Home
            }
            pick.kind == "group" && pick.tileId != null -> {
                val tile = state.findTile(pick.tileId)
                val keys = (tile?.arg ?: "").split("|").filter { it.isNotEmpty() }.toMutableList()
                if (!keys.remove(app.key)) keys.add(app.key)
                state.setArg(pick.tileId, keys.joinToString("|"))
            }
            pick.kind == "folder" && pick.tileId != null -> state.toggleFolderApp(pick.tileId, app.key)
            pick.kind == "clock" -> {
                state.prefs.clockApp = app.key
                state.pick = null
                state.screen = Screen.Home
            }
            pick.kind == "music" && pick.tileId != null -> {
                state.setArg(pick.tileId, MusicArg.make(app.key, MusicArg.style(state.findTile(pick.tileId)?.arg ?: "")))
                state.pick = null
                state.screen = Screen.Home
            }
            pick.kind.startsWith("hgesture-") -> {
                state.setHomeGesture(pick.kind.removePrefix("hgesture-"), "app:" + app.key)
                state.pick = null
                state.settingsPage = "gestures"
                state.screen = Screen.Settings
            }
            pick.kind.startsWith("gesture-") && pick.tileId != null -> {
                val t = state.findTile(pick.tileId)
                state.setArg(pick.tileId, Gestures.set(t?.arg ?: "", pick.kind.removePrefix("gesture-"), "app:" + app.key))
                state.pick = null
                state.screen = Screen.Home
            }
            pick.kind == "stack" && pick.tileId != null -> {
                state.stackApp(pick.tileId, app.key)
                state.pick = null
                state.screen = Screen.Home
            }
            pick.kind == "assign" && pick.tileId != null -> {
                state.assign(pick.tileId, app.key)
                state.pick = null
                state.screen = Screen.Home
            }
            else -> {
                state.add(Kind.APP, app.key)
                state.pick = null
                state.screen = Screen.Home
            }
        }
    }

    val groupKeys = when (pick?.kind) {
        "group" -> (state.findTile(pick.tileId)?.arg ?: "").split("|").toSet()
        "folder" -> state.folders.firstOrNull { it.id == pick.tileId }?.items?.filter { !it.widget }?.map { it.arg }?.toSet() ?: emptySet()
        else -> emptySet()
    }

    // While a finger scrubs the rail only that letter's apps are shown; on release the full list returns.
    var scrub by remember { mutableStateOf<String?>(null) }
    // Where the scrubbed letter's apps sit: 0 = top of the panel, 1 = bottom, following the finger along the rail.
    val scrubFrac = remember { androidx.compose.animation.core.Animatable(0f) }
    var listH by remember { mutableStateOf(0) }
    val shown = remember(rows, scrub) {
        val sc = scrub
        if (sc == null) rows else rows.filter { !it.pinned && letterOf(it) == sc }
    }

    val range by remember(shown) {
        derivedStateOf {
            val info = listState.layoutInfo.visibleItemsInfo
            val real = info.filter { it.index < shown.size && !shown[it.index].pinned }
            if (real.isEmpty()) "" else "${letterOf(shown[real.first().index])} - ${letterOf(shown[real.last().index])} / ${shown.count { !it.pinned }}"
        }
    }
    val currentLetter by remember(shown) {
        derivedStateOf {
            // The probe slides from the first visible row at the top to the last at the bottom, so the end of the
            // list reaches its last letters (Z) instead of staying on the letter that is at the top of the screen.
            val info = listState.layoutInfo.visibleItemsInfo
            val total = listState.layoutInfo.totalItemsCount
            val maxFirst = (total - info.size).coerceAtLeast(1)
            val progress = (listState.firstVisibleItemIndex.toFloat() / maxFirst).coerceIn(0f, 1f)
            val first = if (info.isEmpty()) listState.firstVisibleItemIndex else info[(progress * (info.size - 1) + 0.5f).toInt().coerceIn(0, info.lastIndex)].index
            val i = (first until shown.size).firstOrNull { !shown[it].pinned } ?: 0
            if (shown.isEmpty()) "" else letterOf(shown[i.coerceIn(0, shown.lastIndex)])
        }
    }

    val lh = state.prefs.leftHanded
    val letters = remember(rows) {
        val present = rows.filter { !it.pinned }.map { letterOf(it) }.toSet()
        if (sortMode == "alpha" || sortMode == "za") railLetters.filter { it in present } else emptyList()
    }
    val panelShape = if (lh) RoundedCornerShape(topStart = 20.dp, topEnd = 32.dp, bottomEnd = 32.dp, bottomStart = 32.dp)
    else RoundedCornerShape(topStart = 32.dp, topEnd = 20.dp, bottomEnd = 32.dp, bottomStart = 32.dp)
    val enter = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 380f)) }

    // The wallpaper stays visible behind the drawer: only a light scrim, no home screen underneath.
    val scrim = when (state.prefs.drawerBg) {
        1 -> Color.Transparent
        2 -> pal.ground
        else -> Color.Black.copy(alpha = 0.3f)
    }
    val panelOn = state.prefs.drawerPanel
    val shadow = if (panelOn) null else Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), 10f)
    Box(Modifier.fillMaxSize().background(scrim).imePadding()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val sideways = maxWidth > maxHeight
            val panelWidth = if (sideways) minOf(maxWidth * 0.55f, 460.dp) else maxWidth * 0.74f
            val tallFrac = if (sideways) 1f else when (state.prefs.drawerHeight) { 0 -> 0.45f; 1 -> 0.65f; else -> 1f }
            val hPx = with(density) { maxHeight.toPx() }

            // Scrubbing the rail scrolls the list to that letter. Read through a snapshot flow so a drag does not
            // recompose the whole drawer, and only scroll when the letter actually changes.
            val lettersNow by rememberUpdatedState(letters)
            val rowsNow by rememberUpdatedState(rows)
            val view = androidx.compose.ui.platform.LocalView.current
            val maxStep = with(density) { RAIL_STEP_DP.dp.toPx() }
            LaunchedEffect(hPx) {
                var handled = state.railPing
                var last = ""
                var wasDragging = false
                snapshotFlow { Triple(state.railPing, state.railY, state.railDragging) }.collectLatest { (ping, y, dragging) ->
                    if (!wasDragging && dragging) last = ""
                    wasDragging = dragging
                    if (!dragging && scrub != null) {
                        // Finger lifted: bring the full list back, positioned at the letter that was shown.
                        val t = scrub
                        scrub = null
                        scrubFrac.snapTo(0f)
                        androidx.compose.runtime.withFrameNanos { }
                        val at = rowsNow.indexOfFirst { !it.pinned && letterOf(it) == t }
                        if (at >= 0) listState.scrollToItem(at)
                        return@collectLatest
                    }
                    if (ping == handled) return@collectLatest
                    handled = ping
                    val ls = lettersNow
                    if (ls.isEmpty()) return@collectLatest
                    val target = ls[RailGeo(hPx, ls.size, maxStep).indexAt(y * hPx)]
                    if (dragging) {
                        val geo = RailGeo(hPx, ls.size, maxStep)
                        val span = (geo.yOf(ls.lastIndex) - geo.yOf(0)).coerceAtLeast(1f)
                        val frac = ((y * hPx - geo.yOf(0)) / span).coerceIn(0f, 1f)
                        if (last.isEmpty()) scrubFrac.snapTo(frac)
                        else scope.launch { scrubFrac.animateTo(frac, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 500f)) }
                        if (target == last) return@collectLatest
                        last = target
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        scrub = target
                        return@collectLatest
                    }
                    last = ""
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    val rs = rowsNow
                    val at = rs.indexOfFirst { !it.pinned && letterOf(it) == target }
                    val to = if (at >= 0) at else rs.lastIndex
                    if (to >= 0) listState.animateScrollToItem(to)
                }
            }
            SideEffect { state.railCount = letters.size }
            DisposableEffect(Unit) { onDispose { state.railCount = 0 } }

            Column(
                Modifier
                    .align(if (lh) Alignment.BottomEnd else Alignment.BottomStart)
                    .graphicsLayer {
                        alpha = enter.value
                        translationX = (1f - enter.value) * 48.dp.toPx() * (if (lh) 1f else -1f)
                    }
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(
                        start = if (panelOn) (if (lh) 0.dp else 10.dp) else (if (lh) 56.dp else 12.dp),
                        end = if (panelOn) (if (lh) 10.dp else 0.dp) else (if (lh) 12.dp else 56.dp),
                        top = 14.dp, bottom = 14.dp,
                    )
                    .then(if (panelOn) Modifier.width(panelWidth) else Modifier.fillMaxWidth())
                    .then(if (tallFrac >= 1f) Modifier.fillMaxHeight() else Modifier.heightIn(max = maxHeight * tallFrac))
                    .then(
                        if (panelOn) Modifier.clip(panelShape).background(pal.ground.copy(alpha = 0.58f)).border(1.dp, pal.onGround.copy(alpha = 0.16f), panelShape)
                        else Modifier
                    )
                    .padding(start = 18.dp, end = 12.dp, top = 20.dp, bottom = 14.dp),
            ) {
                if (panelOn || pick != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    BasicText(
                        when {
                            pick?.kind == "group" -> "ADD APPS"
                            pick?.kind == "folder" -> "ADD TO FOLDER"
                            pick != null -> "CHOOSE APP"
                            else -> "APPS"
                        },
                        style = dispStyle(pal.onGround, 22),
                    )
                    if (pick != null) {
                        // Several apps can be chosen for a group or folder, so that ends with DONE. The rest choose one app
                        // by tapping it, so this just backs out.
                        val multi = pick.kind == "group" || pick.kind == "folder"
                        Pill(if (multi) "DONE" else "CANCEL", if (multi) pal.tiles[0] else pal.onGround.copy(alpha = 0.16f), if (multi) pal.ink else pal.onGround, height = 32) {
                            state.pick = null
                            if (pick.kind != "folder") state.screen = Screen.Home
                        }
                    } else BasicText(if (active != null) "FOCUS ON" else range, style = monoStyle(pal.onGround.copy(alpha = 0.75f), 10))
                }
                Spacer(Modifier.height(10.dp))
                val searching = query.isNotBlank() && pick == null
                val quick = remember(query) { app.koma.launcher.data.Quick.answer(query) }
                val noApp = rows.none { !it.pinned }
                val contacts = rememberContacts(state, query.trim())
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f, fill = tallFrac >= 1f)
                        .fillMaxWidth()
                        .onSizeChanged { listH = it.height }
                        .graphicsLayer {
                            // While scrubbing, the letter's apps slide down or up the panel with the finger.
                            if (scrub != null) {
                                val rowsPx = shown.fold(0) { acc, r -> acc + (if (r.header != null) 48 else 50) } * density.density
                                translationY = scrubFrac.value * (listH - rowsPx).coerceAtLeast(0f)
                            }
                        },
                    verticalArrangement = if (searching) androidx.compose.foundation.layout.Arrangement.Bottom else androidx.compose.foundation.layout.Arrangement.Top,
                ) {
                    if (searching && quick != null) item(key = "quick") { QuickRow(state, quick) }
                    if (searching && noApp) item(key = "web") { WebRow(state, query.trim()) }
                    if (searching) item(key = "contacts") { Column { ContactRows(state, query.trim(), contacts, onOpen = { state.goHome() }) } }
                    itemsIndexed(shown, key = { _, r -> r.header?.let { "h$it" } ?: ((r.app?.key ?: r.builtin) + r.pinned) }) { _, row ->
                        if (row.header == KOMA_HEADER) {
                            Row(
                                Modifier.animateItem(fadeInSpec = rowFadeIn, placementSpec = null, fadeOutSpec = rowFadeOut).fillMaxWidth().height(48.dp).padding(start = 6.dp, top = 14.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                            ) {
                                Glyph("all", pal.onGround, 22.dp, 2.2f)
                                BasicText("KOMA", style = monoStyle(pal.onGround.copy(alpha = 0.7f), 11, bold = true).copy(shadow = shadow))
                                Box(Modifier.weight(1f).height(1.dp).background(pal.onGround.copy(alpha = 0.2f)))
                            }
                            return@itemsIndexed
                        }
                        if (row.header != null) {
                            BasicText(
                                row.header,
                                style = dispStyle(pal.onGround, 24).copy(shadow = shadow),
                                modifier = Modifier.animateItem(fadeInSpec = rowFadeIn, placementSpec = null, fadeOutSpec = rowFadeOut).fillMaxWidth().height(48.dp).padding(start = 6.dp, top = 14.dp),
                            )
                            return@itemsIndexed
                        }
                        val src = remember { MutableInteractionSource() }
                        val pressed by src.collectIsPressedAsState()
                        val rowTop = remember { floatArrayOf(0f) }
                        Row(
                            Modifier
                                .animateItem(fadeInSpec = rowFadeIn, placementSpec = null, fadeOutSpec = rowFadeOut)
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .onGloballyPositioned { rowTop[0] = it.positionInRoot().y }
                                .clip(RoundedCornerShape(16.dp))
                                .graphicsLayer { val k = if (pressed) 0.98f else 1f; scaleX = k; scaleY = k }
                                .combinedClickable(
                                    interactionSource = src,
                                    indication = null,
                                    onClick = { choose(row) },
                                    onLongClick = {
                                        val a = row.app
                                        if (a != null && pick == null) sheetApp = a
                                        else a?.let {
                                            try {
                                                ctx.startActivity(
                                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${it.pkg}"))
                                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                )
                                            } catch (e: Exception) {
                                            }
                                        }
                                    },
                                )
                                .pointerInput(row.app?.key, pick) {
                                    val a = row.app
                                    if (a != null && pick == null) {
                                        var dx = 0f
                                        detectHorizontalDragGestures(
                                            onDragStart = { dx = 0f },
                                            onDragEnd = {
                                                if ((if (state.prefs.leftHanded) -dx else dx) > 48.dp.toPx()) {
                                                    popupY = rowTop[0] + size.height
                                                    popupApp = a
                                                }
                                            },
                                            onDragCancel = {},
                                        ) { _, d -> dx += d }
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = if (row.app != null && pick == null && notes[row.app.pkg] != null) 6.dp else 0.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val tint = pal.all[if (row.app != null) abs(row.app.key.hashCode()) % pal.all.size else 0]
                            if (row.app != null) {
                                AppIcon(state.icons, row.app, 40.dp, bg = tint, ink = pal.ink, line = pal.onGround)
                            } else {
                                GlyphIcon(
                                    when {
                                        row.builtin?.startsWith(FOLDER_PREFIX) == true -> "all"
                                        row.builtin == NEW_FOLDER_ID -> "widget"
                                        row.builtin == KAPPS_ID -> "all"
                                        else -> "settings"
                                    },
                                    40.dp, tint, pal.ink, pal.onGround,
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                BasicText(
                                    row.label,
                                    style = bodyStyle(pal.onGround, 18, medium = true).copy(shadow = shadow),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (row.builtin?.startsWith(FOLDER_PREFIX) == true) {
                                    val f = state.folders.firstOrNull { it.id == row.builtin.removePrefix(FOLDER_PREFIX) }
                                    val apps = f?.items?.count { !it.widget } ?: 0
                                    val widgets = f?.items?.count { it.widget } ?: 0
                                    BasicText(
                                        "$apps apps" + if (widgets > 0) ", $widgets widgets" else "",
                                        style = monoStyle(pal.onGround.copy(alpha = 0.6f), 8).copy(shadow = shadow),
                                    )
                                }
                                if (row.builtin == NEW_FOLDER_ID) {
                                    BasicText("group apps and widgets", style = monoStyle(pal.onGround.copy(alpha = 0.6f), 8).copy(shadow = shadow))
                                }
                                val inline = if (row.app != null && pick == null) notes[row.app.pkg] else null
                                if (inline != null) {
                                    val mins = ((System.currentTimeMillis() - inline.time) / 60_000L).coerceAtLeast(0L)
                                    val ago = if (mins < 1) "now" else if (mins < 60) "${mins}m" else if (mins < 1440) "${mins / 60}h" else "${mins / 1440}d"
                                    Column(
                                        Modifier.padding(top = 2.dp).clickable(
                                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                                        ) {
                                            if (!KomaNotificationListener.open(ctx, inline)) state.launch(row.app!!.key)
                                            state.homeAfterLaunch = true
                                        },
                                    ) {
                                        Row {
                                            BasicText(
                                                inline.title.ifBlank { "New notification" },
                                                style = bodyStyle(pal.onGround.copy(alpha = 0.85f), 13, medium = true).copy(shadow = shadow),
                                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                                            )
                                            BasicText(
                                                " · $ago",
                                                style = bodyStyle(pal.onGround.copy(alpha = 0.6f), 13).copy(shadow = shadow),
                                                maxLines = 1, softWrap = false,
                                            )
                                        }
                                        if (inline.text.isNotBlank()) BasicText(
                                            inline.text,
                                            style = bodyStyle(pal.onGround.copy(alpha = 0.65f), 12).copy(shadow = shadow),
                                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (row.app != null && row.app.key in hiddenSet) {
                                    BasicText("hidden", style = monoStyle(pal.onGround.copy(alpha = 0.55f), 8).copy(shadow = shadow))
                                }
                            }
                            if (row.app != null && row.app.key in groupKeys) Glyph("check", pal.tiles[0], 22.dp, 3f)
                        }
                    }
                }
                if (state.prefs.drawerSearch) Spacer(Modifier.height(10.dp))
                // Search pill, inside the panel under the list.
                if (state.prefs.drawerSearch) Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(pal.tiles[4 % pal.tiles.size])
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Magnifier(pal.ink, 20.dp)
                    Spacer(Modifier.width(10.dp))
                    @Suppress("DEPRECATION")
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = bodyStyle(pal.ink, 16, medium = true),
                        cursorBrush = SolidColor(pal.ink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            val best = if (query.isNotBlank()) rows.lastOrNull { !it.pinned } else rows.firstOrNull { !it.pinned }
                            if (best != null) choose(best)
                            else if (query.isNotBlank()) Engines.search(ctx, state.prefs, query.trim())
                        }),
                        modifier = Modifier.weight(1f).focusRequester(focusReq),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) BasicText("Search apps", style = bodyStyle(pal.ink.copy(alpha = 0.6f), 16, medium = true))
                                inner()
                            }
                        },
                    )
                }
            }

            if (letters.isNotEmpty()) CurvedRail(state, currentLetter, letters, clearance = if (panelOn) maxWidth - panelWidth - 10.dp else null)
        }
        if (state.openFolderId != null && pick == null) FolderCard(
            state,
            onMenu = { sheetApp = it },
            onPopup = { a, y ->
                popupY = y
                popupApp = a
            },
        )
        popupApp?.let { a -> AppPopup(state, a, popupY, onClose = { popupApp = null }, onEdit = { editApp = a; popupApp = null }) }
        sheetApp?.let { a -> AppSheet(state, a, onClose = { sheetApp = null }, onEdit = { editApp = a; sheetApp = null }) }
        editApp?.let { a -> PopupEditor(state, a) { editApp = null } }
        BackHandler(enabled = popupApp != null || sheetApp != null || editApp != null) {
            when {
                editApp != null -> editApp = null
                sheetApp != null -> sheetApp = null
                else -> popupApp = null
            }
        }
    }
}
