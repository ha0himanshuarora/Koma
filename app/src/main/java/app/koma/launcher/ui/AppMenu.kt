package app.koma.launcher.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.koma.launcher.data.AppLimits
import app.koma.launcher.data.AppInfo
import app.koma.launcher.data.AppMenu
import app.koma.launcher.data.AppNote
import app.koma.launcher.data.Apps
import app.koma.launcher.data.Kind
import app.koma.launcher.data.KomaFocusService
import app.koma.launcher.data.KomaNotificationListener
import app.koma.launcher.data.PopupCfg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

private fun tryStartActivity(ctx: android.content.Context, i: Intent) {
    try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
    }
}

/** The app's newest notification: swipe it away to dismiss it everywhere, or reply right here when the app allows it. */
@Composable
private fun NoteCard(state: HomeState, n: AppNote, tone: Color, onOpen: () -> Unit, onDismiss: () -> Unit, onSent: () -> Unit) {
    val pal = state.palette
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val dx = remember(n.key) { androidx.compose.animation.core.Animatable(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    var replying by remember(n.key) { mutableStateOf(false) }
    var text by remember(n.key) { mutableStateOf("") }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    androidx.compose.runtime.LaunchedEffect(replying) {
        if (replying) {
            try { focus.requestFocus() } catch (e: Exception) { }
            keyboard?.show()
        }
    }
    fun send() {
        val t = text.trim()
        if (t.isEmpty()) return
        val r = n.reply ?: return
        if (KomaNotificationListener.sendReply(ctx, r, t)) {
            Toast.makeText(ctx, "Sent", Toast.LENGTH_SHORT).show()
            KomaNotificationListener.dismissAfterReply(n.key)
            onSent()
        } else Toast.makeText(ctx, "Could not send. Open the app to reply.", Toast.LENGTH_SHORT).show()
    }
    val frac = (abs(dx.value) / width).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }) {
        Row(
            Modifier.matchParentSize().padding(horizontal = 22.dp).graphicsLayer { alpha = (frac * 3f).coerceAtMost(1f) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (dx.value > 0f) Arrangement.Start else Arrangement.End,
        ) {
            BasicText("Dismiss", style = bodyStyle(pal.onGround.copy(alpha = 0.8f), 15, medium = true))
            Spacer(Modifier.width(8.dp))
            Glyph("close", pal.onGround.copy(alpha = 0.8f), 18.dp, 2.2f)
        }
        Column(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(dx.value.toInt(), 0) }
                .graphicsLayer { alpha = 1f - 0.7f * frac }
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(deepBg())
                .then(
                    if (n.clearable) Modifier.pointerInput(n.key) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (abs(dx.value) > width * 0.35f) {
                                        dx.animateTo(if (dx.value > 0f) width else -width, androidx.compose.animation.core.tween(140))
                                        onDismiss()
                                    } else dx.animateTo(0f, androidx.compose.animation.core.tween(160))
                                }
                            },
                            onDragCancel = { scope.launch { dx.animateTo(0f) } },
                            onHorizontalDrag = { c, d ->
                                c.consume()
                                scope.launch { dx.snapTo(dx.value + d) }
                            },
                        )
                    } else Modifier,
                ),
        ) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(tone), contentAlignment = Alignment.Center) {
                    BasicText(
                        (n.title.trim().firstOrNull { it.isLetterOrDigit() } ?: '•').uppercase(),
                        style = dispStyle(pal.ink, 18),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(n.title.ifBlank { "New notification" }, style = bodyStyle(pal.onGround, 15, medium = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (n.text.isNotBlank()) BasicText(n.text, style = bodyStyle(pal.onGround.copy(alpha = 0.7f), 13), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 8.dp)) {
                    val mins = ((System.currentTimeMillis() - n.time) / 60_000L).coerceAtLeast(0L)
                    BasicText(
                        if (mins < 1) "NOW" else if (mins < 60) "${mins}M" else if (mins < 1440) "${mins / 60}H" else "${mins / 1440}D",
                        style = monoStyle(pal.onGround.copy(alpha = 0.5f), 9),
                    )
                    if (n.reply != null && !replying) Pill("REPLY", tone, pal.ink, height = 28) { replying = true }
                }
            }
            n.picture?.let { pic ->
                androidx.compose.foundation.Image(
                    bitmap = pic.asImageBitmap(),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp).height(150.dp).clip(RoundedCornerShape(14.dp)),
                )
            }
            if (replying) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 30.dp, end = 14.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f).clip(CircleShape).background(surfaceColor(0.1f)).padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Field(text, { text = it }, "Reply", Modifier.fillMaxWidth().focusRequester(focus), onDone = { send() })
                    }
                    Spacer(Modifier.width(8.dp))
                    Pill("SEND", tone, pal.ink, height = 38) { send() }
                }
            }
        }
    }
}

private fun appMinText(m: Int) = when {
    m < 1 -> "under a minute"
    m >= 60 -> "${m / 60}h ${m % 60}m"
    else -> "${m}m"
}

/**
 * The pop-up that opens when you swipe an app row toward the rail (right, or left in left-handed mode): the app's
 * latest notifications, its shortcuts, and any other apps added to it. Long-press it, or use EDIT, to change it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppPopup(state: HomeState, app: AppInfo, anchorY: Float, onClose: () -> Unit, onEdit: () -> Unit) {
    val pal = state.palette
    val ctx = LocalContext.current
    val left = state.prefs.leftHanded
    val cfg = state.popupCfg(app.pkg)
    val shortcuts by produceState(emptyList<android.content.pm.ShortcutInfo>(), app.pkg) {
        value = withContext(Dispatchers.IO) { AppMenu.shortcuts(ctx, app.pkg) }
    }
    val gone = remember(app.pkg) { androidx.compose.runtime.mutableStateListOf<String>() }
    val note = remember(app.pkg, gone.size) { KomaNotificationListener.latest(app.pkg, gone.toSet()) }
    val extra = cfg.apps.mapNotNull { k -> state.apps.firstOrNull { it.key == k } }
    val shown = if (cfg.shortcuts) shortcuts else emptyList()
    val originY = remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { visible = true }
    val p by androidx.compose.animation.core.animateFloatAsState(if (visible) 1f else 0f, androidx.compose.animation.core.tween(180), label = "popup")
    fun tone(i: Int) = pal.tiles[i % pal.tiles.size]
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f * p)).imePadding()
            .onGloballyPositioned { originY.floatValue = it.positionInRoot().y }
            .pointerInput(Unit) { detectTapGestures { onClose() } },
    ) {
        Box(
            Modifier
                .layout { m, c ->
                    val pl = m.measure(c.copy(minWidth = 0, minHeight = 0))
                    val margin = 24.dp.roundToPx()
                    val y = (anchorY - originY.floatValue).toInt().coerceIn(margin, (c.maxHeight - pl.height - margin).coerceAtLeast(margin))
                    layout(pl.width, c.maxHeight) { pl.place(0, y) }
                }
                .graphicsLayer {
                    alpha = p
                    translationX = (1f - p) * (if (left) 36.dp.toPx() else -36.dp.toPx())
                    scaleX = 0.96f + 0.04f * p
                    scaleY = 0.96f + 0.04f * p
                }
                .padding(start = if (left) 56.dp else 12.dp, end = if (left) 12.dp else 56.dp)
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        ) {
            val inner = RoundedCornerShape(20.dp)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(cardBg()).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().clip(inner).clickable { state.launchThenHome(app.key); onClose() }.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(state.icons, app, 48.dp, bg = pal.all[abs(app.key.hashCode()) % pal.all.size], ink = pal.ink, line = pal.onGround)
                    Spacer(Modifier.width(14.dp))
                    BasicText(app.label, style = dispStyle(pal.onGround, 24), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box(Modifier.size(38.dp).clip(CircleShape).background(tone(0)), contentAlignment = Alignment.Center) {
                        Glyph("arrow", pal.ink, 18.dp, 2.2f)
                    }
                }
                note?.let { n ->
                    NoteCard(
                        state, n, tone(1),
                        onOpen = {
                            if (!KomaNotificationListener.open(ctx, n)) state.launch(app.key)
                            state.homeAfterLaunch = true
                            onClose()
                        },
                        onDismiss = {
                            KomaNotificationListener.dismiss(n.key)
                            gone.add(n.key)
                        },
                        onSent = { onClose() },
                    )
                }
                shown.chunked(2).forEachIndexed { row, pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEachIndexed { j, s ->
                            Row(
                                Modifier.weight(1f).clip(inner).background(deepBg()).clickable { AppMenu.start(ctx, s); onClose() }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(26.dp).clip(CircleShape).background(tone(row * 2 + j + 2)), contentAlignment = Alignment.Center) {
                                    Glyph("arrow", pal.ink, 13.dp, 2.2f)
                                }
                                Spacer(Modifier.width(10.dp))
                                BasicText(AppMenu.label(s), style = bodyStyle(pal.onGround, 14, medium = true), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (extra.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().clip(inner).background(deepBg()).padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        extra.take(5).forEach { a ->
                            Column(
                                Modifier.clip(RoundedCornerShape(14.dp)).clickable { state.launchThenHome(a.key); onClose() }.padding(2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AppIcon(state.icons, a, 44.dp, bg = pal.all[abs(a.key.hashCode()) % pal.all.size], ink = pal.ink, line = pal.onGround)
                                BasicText(a.label.uppercase(), style = monoStyle(pal.onGround.copy(alpha = 0.7f), 8), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(48.dp))
                            }
                        }
                    }
                }
                if (note == null && shown.isEmpty() && extra.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().clip(inner).background(deepBg()).padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        BasicText("NOTHING HERE YET", style = monoStyle(pal.onGround.copy(alpha = 0.6f), 9))
                        BasicText("Notifications, shortcuts and apps you add will show here.", style = bodyStyle(pal.onGround.copy(alpha = 0.7f), 14))
                    }
                }
                Pill("EDIT POP-UP", pal.onGround.copy(alpha = 0.1f), pal.onGround, Modifier.fillMaxWidth(), height = 36, onClick = onEdit)
            }
        }
    }
}

/** The menu that opens when you long-press an app in the drawer. */
@Composable
fun AppSheet(state: HomeState, app: AppInfo, onClose: () -> Unit, onEdit: () -> Unit) {
    val pal = state.palette
    val ctx = LocalContext.current
    val usage by produceState(-1 to -1, app.pkg, state.usageGranted) {
        value = withContext(Dispatchers.IO) { AppMenu.minutes(ctx, app.pkg) }
    }
    val hidden = app.key in state.hiddenKeys
    val onHome = state.tiles.any { it.kind == Kind.APP && it.arg == app.key }
    fun t(i: Int) = pal.tiles[i % pal.tiles.size]
    Sheet(onDismiss = onClose, header = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(state.icons, app, 44.dp, bg = pal.all[abs(app.key.hashCode()) % pal.all.size], ink = pal.ink, line = pal.onGround)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                BasicText(app.label, style = dispStyle(pal.onGround, 26), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.usageGranted && usage.first >= 0) BasicText(
                    "TODAY ${appMinText(usage.first).uppercase()}  /  7 DAYS ${appMinText(usage.second).uppercase()}",
                    style = monoStyle(pal.onGround.copy(alpha = 0.6f), 10), maxLines = 1,
                )
            }
        }
    }) {
        // The three things done most, as big buttons.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            @Composable
            fun Quick(i: Int, glyph: String, name: String, onClick: () -> Unit) {
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(t(i)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Glyph(glyph, pal.ink, 24.dp, 2f)
                    BasicText(name, style = monoStyle(pal.ink, 10, bold = true), maxLines = 1)
                }
            }
            Quick(0, if (onHome) "check" else "plus", if (onHome) "ON HOME" else "ADD TO HOME") {
                if (!onHome) {
                    state.add(Kind.APP, app.key)
                    Toast.makeText(ctx, "Added to home", Toast.LENGTH_SHORT).show()
                }
                onClose()
            }
            Quick(1, "info", "APP INFO") {
                tryStartActivity(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
                onClose()
            }
            Quick(2, "lock", if (hidden) "HIDDEN" else "HIDE") { state.toggleHidden(app.key) }
        }
        if (!state.usageGranted) Group {
            ListRow("Screen time", "Allow usage access to see it", "timer", t(2), onClick = {
                tryStartActivity(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                onClose()
            })
        }
        val limitRead = AppLimits.version
        val limit = AppLimits.get(ctx, app.pkg)
        Group(
            "DAILY LIMIT",
            hint = when {
                limit == null -> "A thin bar along the top shows the time used while this app is open. When it fills, the app locks."
                !state.usageGranted -> "Allow usage access in Settings, System, or the limit cannot count."
                !KomaFocusService.isRunning() -> "Switch Koma on in Accessibility settings, or the bar and lock cannot work."
                AppLimits.isLocked(ctx, app.pkg) -> "Locked until tomorrow. Turn it back on in Settings, App limits."
                else -> null
            },
        ) {
            Dropdown(
                listOf("0" to "NO LIMIT") + AppLimits.presets.map { it.toString() to (if (it >= 60 && it % 60 == 0) "${it / 60} HR" else "$it MIN") },
                (limit?.min ?: 0).toString(), "Per day",
            ) { v -> AppLimits.set(ctx, app.pkg, v.toInt()) }
        }
        Group("FOLDERS") {
            GroupPad {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.folders.forEach { f ->
                        val inIt = f.items.any { !it.widget && it.arg == app.key }
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(if (inIt) t(4) else pal.onGround.copy(alpha = 0.1f))
                                .clickable { state.toggleFolderApp(f.id, app.key) }.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (inIt) Glyph("check", pal.ink, 14.dp, 3f)
                            BasicText(f.name.ifBlank { "Folder" }, style = monoStyle(if (inIt) pal.ink else pal.onGround, 11, bold = true), maxLines = 1)
                        }
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(t(0)).clickable { val f = state.newFolder(); state.toggleFolderApp(f.id, app.key) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Glyph("plus", pal.ink, 14.dp, 3f)
                        BasicText("NEW", style = monoStyle(pal.ink, 11, bold = true))
                    }
                }
            }
        }
        Group("MORE") {
            if (app.cls.startsWith(Apps.SHORTCUT)) ListRow("Remove shortcut", "Unpins it from Koma. The app stays installed.", "trash", t(3), onClick = {
                Apps.unpin(ctx, app)
                state.appsChanged()
                onClose()
            })
            ListRow("Pop-up", "Notifications, shortcuts and apps for ${app.label}", "expand", t(1), onClick = onEdit)
            ListRow("Koma settings", "", "settings", t(2), onClick = {
                onClose()
                state.open(Screen.Settings)
            })
        }
    }
}

@Composable
private fun AppCheckRow(state: HomeState, a: AppInfo, on: Boolean, onClick: () -> Unit) {
    val pal = state.palette
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(state.icons, a, 40.dp, bg = pal.all[abs(a.key.hashCode()) % pal.all.size], ink = pal.ink, line = pal.onGround)
        Spacer(Modifier.width(14.dp))
        BasicText(a.label, style = bodyStyle(pal.onGround, 16, medium = true), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        KCheck(on)
    }
}

/** The page that picks what an app's pop-up shows: its shortcuts, and extra apps. */
@Composable
fun PopupEditor(state: HomeState, app: AppInfo, onDone: () -> Unit) {
    val pal = state.palette
    val ctx = LocalContext.current
    val cfg = state.popupCfg(app.pkg)
    val shortcuts by produceState(emptyList<android.content.pm.ShortcutInfo>(), app.pkg) {
        value = withContext(Dispatchers.IO) { AppMenu.shortcuts(ctx, app.pkg) }
    }
    val chosen = cfg.apps.mapNotNull { k -> state.apps.firstOrNull { it.key == k } }
    val recent = state.recentApps().map { it.key }
    val others = state.apps.filter { it.pkg != app.pkg && it.key !in cfg.apps }
        .sortedWith(compareBy({ recent.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }, { it.label.lowercase() }))
        .take(40)
    fun toggleApp(key: String) {
        state.setPopup(app.pkg, PopupCfg(cfg.shortcuts, if (key in cfg.apps) cfg.apps - key else cfg.apps + key))
    }
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } }) {
        BigPage(
            title = "Pop-up",
            onBack = onDone,
            trailing = { Pill("DONE", pal.tiles[4 % pal.tiles.size], pal.ink, onClick = onDone) },
        ) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BasicText(
                    "What shows when you swipe ${app.label} to the right.",
                    style = bodyStyle(pal.onGround.copy(alpha = 0.7f), 14), modifier = Modifier.padding(horizontal = 4.dp),
                )
                Group("SHOWN") {
                    if (shortcuts.isNotEmpty()) {
                        SwitchRow("${app.label} shortcuts", shortcuts.joinToString(", ") { AppMenu.label(it) }, cfg.shortcuts) {
                            state.setPopup(app.pkg, PopupCfg(it, cfg.apps))
                        }
                    }
                    chosen.forEach { a -> AppCheckRow(state, a, true) { toggleApp(a.key) } }
                    if (shortcuts.isEmpty() && chosen.isEmpty()) {
                        BasicText("Nothing added yet. Pick apps below.", style = bodyStyle(pal.onGround.copy(alpha = 0.6f), 14), modifier = Modifier.padding(14.dp))
                    }
                }
                Group("ADD APPS") {
                    others.forEach { a -> AppCheckRow(state, a, false) { toggleApp(a.key) } }
                }
            }
        }
    }
}
