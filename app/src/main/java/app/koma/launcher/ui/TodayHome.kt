package app.koma.launcher.ui

import android.Manifest
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.koma.launcher.data.Defaults
import app.koma.launcher.data.Kind
import app.koma.launcher.data.Tile
import app.koma.launcher.data.TimeFmt
import app.koma.launcher.data.WeatherRepo
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The Today home type. The grid in the middle is the ordinary home grid with its own tiles; what is added here is
 * the part around it: a clock and weather on top, a music strip, and a dock with search and two apps of your choice.
 */

private fun greeting(hour: Int) = when {
    hour in 5..11 -> "GOOD MORNING"
    hour in 12..16 -> "GOOD AFTERNOON"
    hour in 17..20 -> "GOOD EVENING"
    else -> "GOOD NIGHT"
}

/** True when the home screen is free for a swipe or tap of its own: no edit mode, no sheet, no focus lock. */
private fun free(state: HomeState) = !state.edit && state.sheetId == null && state.focusActive == null

/** Clock (in the clock font and tone chosen in settings), date line and weather. */
@Composable
internal fun TodayHeader(state: HomeState, ctx: Context) {
    val pal = state.palette
    val ink = pal.onGround
    val seconds = state.prefs.clockSeconds
    val now by rememberTicker(seconds)
    val is24 = TimeFmt.is24()
    val hour = if (is24) now.hour else (now.hour % 12).let { if (it == 0) 12 else it }
    val time = (if (is24) "%02d:%02d" else "%d:%02d").format(hour, now.minute) + (if (seconds) ":%02d".format(now.second) else "")
    val stroke = with(LocalDensity.current) { 3.dp.toPx() }
    val style = tight(Fonts.Clock, ink, -0.04f, 0.9f)

    val granted = state.locationGranted || state.prefs.weatherPlace.isNotEmpty()
    val source = state.prefs.weatherUnit + state.prefs.weatherPlace + state.prefs.weatherPlace2
    val weather by produceState(state.cachedWeather(), granted, source) {
        while (true) {
            awaitVisible()
            if (granted) state.loadWeather()?.let { value = it }
            delay(10 * 60_000L)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
            .pointerInput(Unit) {
                // The same double tap and hold on empty parts of the home screen as the grid has.
                detectTapGestures(
                    onDoubleTap = { if (free(state)) state.homeGestures()["double"]?.let { runGesture(state, it) } },
                    onLongPress = { if (free(state)) state.homeGestures()["long"]?.let { runGesture(state, it) } },
                )
            }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > 56.dp.toPx() && free(state)) state.homeGestures()["down"]?.let { runGesture(state, it) } },
                    onDragCancel = { total = 0f },
                ) { _, d -> total += d }
            },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                // Tapping the clock opens the Clock app, like tapping a clock tile.
                Row(
                    Modifier.clickable { if (free(state)) state.openClock(0) },
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ToneFit(time, Fonts.clockTone, ink, pal.ground, stroke, style, Modifier.weight(1f, fill = false).height(92.dp))
                    if (!is24) BasicText(if (now.hour < 12) "AM" else "PM", style = label(pal.tiles[0], 12.sp, bold = true), modifier = Modifier.padding(bottom = 8.dp))
                }
                BasicText(
                    now.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())).uppercase() + " / " + greeting(now.hour),
                    style = label(ink.copy(alpha = 0.6f), 11.sp).copy(letterSpacing = 0.14.em),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            val w = weather
            Column(
                Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.5.dp, ink.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                    .clickable { if (!granted) state.request(Manifest.permission.ACCESS_COARSE_LOCATION) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                BasicText(if (w != null) "${w.temp}°" else "--°", style = display(22.sp, ink, (-0.03).em, 1f))
                BasicText(
                    (if (w != null) WeatherRepo.describe(w.code).first else if (granted) "NO DATA" else "LOCATION").uppercase(),
                    style = label(ink.copy(alpha = 0.6f), 10.sp).copy(letterSpacing = 0.12.em),
                    maxLines = 1,
                )
            }
        }
    }
}

/** The music strip: what is playing, a thin progress line and a play button. Tap opens the music app, hold chooses it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TodayMusic(state: HomeState, ctx: Context) {
    val tile = remember(state.prefs.todayMusic) { Tile("today-music", Kind.MUSIC, 4, 1, arg = state.prefs.todayMusic) }
    val lk = tileLook(state, tile, 4)
    val now = state.media.now
    LaunchedEffect(now?.pkg) { now?.pkg?.takeIf { it.isNotBlank() }?.let { state.prefs.lastMusic = it } }
    val playing = now?.playing == true
    val title = when {
        now != null -> now.title
        !state.media.hasAccess -> "Open music"
        else -> "Nothing playing"
    }
    val sub = when {
        now != null -> now.artist
        !state.media.hasAccess -> "Play button: allow access"
        else -> "Start a player"
    }
    val progress by produceState(0f, now) {
        while (true) {
            val n = now
            value = if (n != null && n.playing && n.duration > 0L) (n.current().toFloat() / n.duration).coerceIn(0f, 1f) else 0f
            awaitVisible()
            delay(500)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(lk.fill)
            .combinedClickable(
                onClick = { openMusic(state, ctx, tile) },
                onLongClick = { state.openDrawer(pickFor = Pick("music", tile.id)) },
            )
            .padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(
                (if (playing) "NOW PLAYING" else "PAUSED") + (if (sub.isNotBlank()) " / " + sub.uppercase() else ""),
                style = label(lk.ink.copy(alpha = 0.65f), 10.sp).copy(letterSpacing = 0.14.em),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(title, style = display(22.sp, lk.ink, (-0.02).em, 1f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(lk.ink.copy(alpha = 0.16f))) {
                Box(Modifier.fillMaxWidth(progress).height(4.dp).clip(CircleShape).background(lk.ink))
            }
        }
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(lk.ink)
                .clickable {
                    if (!state.media.hasAccess) tryStart(ctx, android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    else state.media.toggle()
                },
            contentAlignment = Alignment.Center,
        ) { Glyph(if (playing) "pause" else "play", lk.fill.opaque(), 22.dp, 2f, filled = true) }
    }
}

/** Search on the left, then two 1x1 components of your choice. Hold a button to change it; swipe up on the dock for search. */
@Composable
internal fun TodayDock(state: HomeState, ctx: Context) {
    val pal = state.palette
    val ink = pal.onGround
    val surface = surfaceColor(0.1f)
    val roles = remember(state.apps) { Defaults.resolveRoles(ctx.packageManager, state.apps) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 8.dp)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -40.dp.toPx() && free(state)) {
                            (state.homeGestures()["up"] ?: if (state.prefs.homeSearch) "search" else null)?.let { runGesture(state, it) }
                        }
                    },
                    onDragCancel = { total = 0f },
                ) { _, d -> total += d }
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(56.dp)
                .clip(CircleShape)
                .background(surface)
                .clickable { state.openSearch() }
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph("search", ink, 18.dp, 2.2f)
            BasicText(
                if (state.prefs.homeSearch) "SEARCH OR SWIPE UP" else "SEARCH",
                style = label(ink.copy(alpha = 0.6f), 11.sp, bold = true).copy(letterSpacing = 0.1.em),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DockButton(state, ctx, 0, state.dockTile(0, roles), surface)
        DockButton(state, ctx, 1, state.dockTile(1, roles), surface)
    }
}

/**
 * One dock button, holding a 1x1 component. An app shows its icon, the all apps button a grid; every other component
 * draws itself as it does at 1x1 on the grid. Tap does what the component does, hold changes it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockButton(state: HomeState, ctx: Context, slot: Int, tile: Tile, surface: Color) {
    val ink = state.palette.onGround
    val app = state.apps.firstOrNull { it.key == tile.arg }
    val lk = tileLook(state, tile, slot + 2)
    val own = tile.kind != Kind.APP && tile.kind != Kind.ALL
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (own) lk.fill else surface)
            .combinedClickable(
                onClick = { if (tile.kind == Kind.APP && app == null) state.dockPick = slot else onTileClick(state, ctx, tile) },
                onLongClick = { state.dockPick = slot },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            tile.kind == Kind.APP -> if (app != null) InkIcon(state.icons, app, ink, 28.dp) else Glyph(tile.glyph.ifEmpty { "plus" }, ink, 22.dp, 2f)
            tile.kind == Kind.ALL -> Glyph("all", ink, 24.dp, 2f)
            else -> Box(Modifier.fillMaxSize()) { TileBody(state, ctx, tile, null, lk.fill, lk.ink, lk.base) }
        }
    }
}

/** Chooser for a dock button: an app, a quick shortcut, or any component that has a 1x1 size. */
@Composable
internal fun DockSheet(state: HomeState, slot: Int) {
    val pal = state.palette
    val t = pal.tiles
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val roles = remember(state.apps) { Defaults.resolveRoles(ctx.packageManager, state.apps) }
    val cur = state.dockTile(slot, roles)
    val close = { state.dockPick = null }
    val kinds = remember {
        val skip = setOf(Kind.APP, Kind.GROUP, Kind.WIDGET, Kind.PHOTO, Kind.COUNTDOWN, Kind.STACK, Kind.SPACE)
        Kind.entries.filter { it !in skip && (1 to 1) in it.sizes }
    }
    Sheet(onDismiss = close, header = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText("DOCK BUTTON ${slot + 1}", style = dispStyle(pal.onGround, 22))
                BasicText("AN APP, OR ANY COMPONENT WITH A 1x1 SIZE", style = monoStyle(pal.onGround.copy(alpha = 0.6f), 9))
            }
            Pill("DONE", t[0], pal.ink, height = 36, onClick = close)
        }
    }) {
        Group("APP") {
            ListRow(
                "Choose an app",
                if (cur.kind == Kind.APP) state.apps.firstOrNull { it.key == cur.arg }?.label ?: "None yet" else "Replaces the current component",
                "all", t[0],
                onClick = {
                    close()
                    state.openDrawer(pickFor = Pick("dock", slot.toString()))
                },
            )
        }
        Group("QUICK SHORTCUTS") {
            Dropdown(Defaults.roleList.map { it.first to it.first }, "", "Common apps", "CHOOSE") { label ->
                val glyph = Defaults.roleList.first { it.first == label }.second
                state.setDock(slot, Tile("dock-$slot", Kind.APP, 1, 1, arg = roles[label] ?: "", label = label, glyph = glyph, color = slot))
                close()
            }
        }
        if (cur.kind == Kind.TOGGLE) Group("SWITCH") {
            ChipRow(listOf("torch" to "TORCH", "wifi" to "WI-FI", "bt" to "BLUETOOTH", "dnd" to "DND"), cur.arg.ifEmpty { "torch" }, title = "Controls") { state.setDock(slot, cur.copy(arg = it)) }
        }
        Group("COMPONENTS") {
            kinds.forEachIndexed { i, k ->
                ListRow(k.title, k.desc, kindGlyph(k), t[i % t.size], onClick = {
                    state.setDock(slot, Tile("dock-$slot", k, 1, 1, color = slot))
                    // A quick switch has its own choices, so its sheet stays open to pick one.
                    if (k != Kind.TOGGLE) close()
                })
            }
        }
    }
}
