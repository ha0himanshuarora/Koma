package app.koma.launcher.ui

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koma.launcher.data.AppLimits
import app.koma.launcher.data.CalInfo
import app.koma.launcher.data.CalendarRepo
import app.koma.launcher.data.Engines
import app.koma.launcher.data.Gestures
import app.koma.launcher.data.IconPack
import app.koma.launcher.data.IconStyles
import app.koma.launcher.data.Kind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun kindGlyph(k: Kind): String = when (k) {
    Kind.CLOCK -> "clock"
    Kind.DATE -> "cal"
    Kind.KANJI -> "kanji"
    Kind.WEATHER -> "sun"
    Kind.MUSIC -> "music"
    Kind.CALENDAR -> "cal"
    Kind.SEARCH -> "search"
    Kind.FAVS -> "users"
    Kind.CALLS -> "phone"
    Kind.GROUP -> "all"
    Kind.DEVICE -> "device"
    Kind.TOGGLE -> "bolt"
    Kind.REMINDERS -> "n_alarm"
    Kind.NOTE -> "note"
    Kind.PHOTO -> "pics"
    Kind.SCREENTIME -> "device"
    Kind.FOCUS -> "lock"
    Kind.WIDGET -> "widget"
    Kind.APP -> "all"
    Kind.ALL -> "all"
    Kind.STACK -> "all"
    Kind.BLUETOOTH -> "bt"
    Kind.COUNTDOWN -> "flag"
    Kind.TIMER -> "timer"
    Kind.STOPWATCH -> "timer"
    Kind.CALC -> "calc"
    Kind.KAPPS -> "all"
    Kind.ALARMS -> "alarm"
    Kind.SPACE -> "plus"
}

@Composable
private fun Status(on: Boolean, onText: String = "ON", offText: String = "OFF") {
    val pal = LocalPalette.current
    BasicText(if (on) onText else offText, style = monoStyle(if (on) pal.onGround else pal.onGround.copy(alpha = 0.5f), 11, bold = true))
}

/** Koma Settings: one hub with a page for each area of the launcher. */
@Composable
fun SettingsScreen(state: HomeState) {
    when (state.settingsPage) {
        "colour" -> ColourPage(state)
        "type" -> TypePage(state)
        "apps", "icons", "drawer", "hidden", "folders" -> AppsPage(state)
        "calendar" -> CalendarPage(state)
        "search" -> SearchPage(state)
        "looks" -> LooksPage(state)
        "gestures" -> GesturesPage(state)
        "music" -> MusicStylePage(state)
        "weather" -> WeatherPage(state)
        "system" -> SystemPage(state)
        "limits" -> LimitsPage(state)
        "about" -> AboutPage(state)
        "privacy" -> PrivacyPage(state)
        "permissions" -> PermissionsPage(state)
        else -> Hub(state)
    }
}

@Composable
private fun Hub(state: HomeState) {
    val ctx = LocalContext.current
    val pal = state.palette
    val t = pal.tiles
    fun go(page: String) {
        state.settingsPage = page
    }
    Page("Koma", "SETTINGS", onBack = { state.goHome() }) {
        Box(Modifier.fillMaxWidth().clip(TileShape).background(t[0])) {
            // Soft shapes in the palette's other colours.
            Box(Modifier.align(Alignment.TopEnd).offset(x = 46.dp, y = (-56).dp).size(170.dp).clip(CircleShape).background(t[1 % t.size].copy(alpha = 0.55f)))
            Box(Modifier.align(Alignment.TopEnd).offset(x = (-30).dp, y = 70.dp).size(54.dp).clip(CircleShape).background(t[2 % t.size].copy(alpha = 0.6f)))
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText("Make it yours", style = dispStyle(pal.ink, 32))
                    BasicText(
                        pal.name + "  /  " + (if (state.dark) "DARK" else "LIGHT"),
                        style = monoStyle(pal.ink.copy(alpha = 0.7f), 10, bold = true),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(pal.ink.copy(alpha = 0.1f)).padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.apps.take(5).forEach { a -> AppIcon(state.icons, a, 40.dp, bg = pal.ink, ink = t[0], line = pal.ink) }
                }
                Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).border(1.5.dp, pal.ink.copy(alpha = 0.45f), CircleShape)) {
                    t.take(6).forEach { Box(Modifier.weight(1f).fillMaxHeight().background(it)) }
                }
            }
        }
        Group("HOME") {
            ListRow("Layout", "${state.prefs.gridCols} x ${state.prefs.gridRows} grid", "widget", t[2 % t.size], onClick = {
                state.goHome()
                state.startEdit()
            })
            ListRow("Weather", "", "sun", t[1 % t.size], onClick = { go("weather") })
            ListRow("Music", "", "music", t[2 % t.size], onClick = { go("music") })
            ListRow("Calendar", "", "cal", t[1 % t.size], onClick = { go("calendar") })
            ListRow("Search", (Engines.all.firstOrNull { it.first == state.prefs.engine }?.second?.first ?: "") + ", swipe up to search", "search", t[4 % t.size], onClick = { go("search") })
        }
        Group("GESTURES") {
            ListRow("Home screen", "", "all", t[3 % t.size], onClick = { go("gestures") })
        }
        Group("LOOK") {
            ListRow("Looks", state.looks.size.let { if (it == 0) "" else "$it saved" }, "palette", t[4 % t.size], onClick = { go("looks") })
            ListRow("Theme", if (state.prefs.follow) "Following your wallpaper" else pal.name, "palette", t[0 % t.size], onClick = { go("colour") })
            ListRow("Fonts", Fonts.choices.firstOrNull { it.first == state.prefs.font }?.second ?: "Koma default", "type", t[1 % t.size], onClick = { go("type") })
        }
        Group("APPS") {
            ListRow("Apps", "${state.apps.size} apps / " + (IconStyles.styles.firstOrNull { it.first == state.prefs.iconStyle }?.second ?: "Default"), "all", t[2 % t.size], onClick = { go("apps") })
        }
        Group("PHONE") {
            ListRow("System", "", "settings", t[3 % t.size], onClick = { go("system") })
        }
    }
}

// ---- app limits ----------------------------------------------------------------------------------

@Composable
private fun LimitsPage(state: HomeState) {
    val ctx = LocalContext.current
    val pal = state.palette
    val t = pal.tiles
    val version = AppLimits.version
    val all = remember(version) { AppLimits.all(ctx).toList() }
    val used by androidx.compose.runtime.produceState(emptyMap<String, Long>(), version) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            all.associate { (p, l) -> p to try { AppLimits.usedMs(ctx, p, l) } catch (e: Exception) { 0L } }
        }
    }
    Page("App limits", "SETTINGS", onBack = { state.settingsPage = "" }) {
        PageHero("APP LIMITS", if (all.isEmpty()) "None set" else "${all.size} with a limit", "An app locks when its daily time is used up.", state.palette.tiles[2 % state.palette.tiles.size])
        if (all.isEmpty()) {
            BasicText(
                "No limits yet. Long-press an app in the drawer and set a daily limit. A thin bar along the top of the screen then fills as you use it, and the app locks when it is full.",
                style = bodyStyle(pal.onGround.copy(alpha = 0.7f), 14),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        } else {
            Group("LIMITED APPS", hint = "A locked app stays locked until you turn it back on here, remove its limit, or tomorrow.") {
                all.forEachIndexed { i, (pkg, l) ->
                    val label = state.apps.firstOrNull { it.pkg == pkg }?.label ?: pkg
                    val locked = AppLimits.isLocked(ctx, pkg)
                    val min = ((used[pkg] ?: 0L) / 60_000L).toInt()
                    ListRow(
                        label,
                        if (locked) "Locked. ${l.min} min limit used up" else "$min of ${l.min} min today",
                        "timer", t[i % t.size],
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (locked) Pill("TURN ON", t[i % t.size], pal.ink, height = 28) { AppLimits.unlock(ctx, pkg) }
                                Pill("REMOVE", pal.onGround.copy(alpha = 0.14f), pal.onGround, height = 28) { AppLimits.set(ctx, pkg, 0) }
                            }
                        },
                    )
                }
            }
        }
        Group("INDICATOR", hint = "Shown while a limited app is open, in your theme colour. A ring fills around the front camera, sized to it, and a line fills along the top. Ring side is only used on phones with no camera hole.") {
            Dropdown(listOf("line" to "LINE", "ring" to "RING"), state.prefs.limitStyle, "Style") { state.prefs.limitStyle = it }
            if (state.prefs.limitStyle == "ring") Dropdown(listOf("right" to "RIGHT OF CAMERA", "left" to "LEFT OF CAMERA"), state.prefs.limitSide, "Ring side") { state.prefs.limitSide = it }
        }
        Group("NEEDS") {
            ListRow("Usage access", "Counts the time each app is open", "device", t[2 % t.size], onClick = {
                try { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
            }, trailing = { Status(state.usageGranted) })
            ListRow("Accessibility guard", "Draws the bar and locks the app", "lock", t[3 % t.size], onClick = {
                try { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
            }, trailing = { Status(app.koma.launcher.data.KomaFocusService.isRunning()) })
        }
    }
}

// ---- colour ------------------------------------------------------------------------------------

@Composable
private fun ColourPage(state: HomeState) {
    val pal = state.palette
    val prefs = state.prefs
    val ctx = LocalContext.current
    val imagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri: android.net.Uri? ->
        if (uri != null && !state.pickImageColours(uri)) {
            android.widget.Toast.makeText(ctx, "Could not read that image", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    var wpTarget by remember { mutableStateOf("both") }
    val scope = rememberCoroutineScope()
    val wallpaperPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri: android.net.Uri? ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { Dynamic.setWallpaper(ctx, uri, wpTarget) }
            val seeds = if (ok) withContext(Dispatchers.IO) { Dynamic.seedsFromUri(ctx, uri) } else null
            if (ok) state.keepWallpaper(uri)
            if (ok && seeds != null) state.applyPickedColours(seeds)
            android.widget.Toast.makeText(ctx, if (ok) "Wallpaper set, colours updated" else "Could not set the wallpaper", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val sourceText = when {
        !prefs.follow && prefs.paletteId == "custom" -> "Built from a colour you picked"
        !prefs.follow -> "A ready-made palette"
        else -> when (state.wallpaperSource) {
            "wallpaper" -> "Following your wallpaper"
            "lock" -> "From your lock screen wallpaper"
            "picture" -> "Read from your wallpaper picture"
            "palette" -> "From your phone's colour palette"
            "image" -> "From the picture you picked"
            else -> "Default colours"
        }
    }
    val t = pal.tiles
    Page("Theme", "SETTINGS / LOOK", onBack = { state.settingsPage = "" }) {
        PageHero("USING NOW", pal.name, sourceText, pal.tiles[0], listOf(if (state.dark) "Dark" else "Light"))

        Group {
            SwitchRow("Follow wallpaper", "Colours come from your wallpaper", prefs.follow) { on -> state.theme { follow = on } }
        }

        if (prefs.follow) {
            Group(
                "FROM YOUR WALLPAPER",
                hint = if (state.wallpaperSource == "palette") "Your phone does not share its wallpaper with apps, so Koma is using its colour palette. Set a wallpaper here for exact colours." else "Tap a colour to lead the theme.",
            ) {
                GroupPad {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val lead = if (prefs.mainSeed in state.wallpaperSeeds.indices) prefs.mainSeed else 0
                        state.wallpaperSeeds.forEachIndexed { i, c ->
                            Box(
                                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(c))
                                    .then(if (i == lead) Modifier.border(3.dp, pal.onGround, RoundedCornerShape(14.dp)) else Modifier)
                                    .clickable { state.theme { mainSeed = i; follow = true } },
                            )
                        }
                    }
                }
                Dropdown(Dynamic.variants, prefs.variant, "Colour style") { v -> state.theme { variant = v } }
            }
            Group("WALLPAPER") {
                Dropdown(listOf("both" to "HOME + LOCK", "home" to "HOME", "lock" to "LOCK"), wpTarget, "Set wallpaper on") { wpTarget = it }
                ListRow("Set from gallery", "", "pics", t[0 % t.size], onClick = { wallpaperPicker.launch(arrayOf("image/*")) })
                ListRow("Colours from a picture", "", "palette", t[1 % t.size], onClick = { imagePicker.launch(arrayOf("image/*")) })
                ListRow("Refresh colours", "", "refresh", t[2 % t.size], onClick = { state.refreshColours() })
                if (prefs.useImageColours) ListRow("Use phone wallpaper colours", "Instead of the picture", "sun", t[3 % t.size], onClick = { state.useWallpaperColours() })
            }
        } else {
            Group("PRESETS") {
                GroupPad {
                    Palettes.presets.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { preset ->
                                val p = preset.pick(state.dark)
                                val on = prefs.paletteId == p.id
                                val shape = RoundedCornerShape(20.dp)
                                Column(
                                    Modifier.weight(1f).clip(shape).background(p.ground)
                                        .border(if (on) 3.dp else 1.dp, if (on) pal.tiles[0] else pal.onGround.copy(alpha = 0.14f), shape)
                                        .clickable { state.theme { follow = false; paletteId = p.id } }
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        p.tiles.forEach { Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(8.dp)).background(it)) }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        BasicText(p.name, style = bodyStyle(p.onGround, 14, medium = true), maxLines = 1, modifier = Modifier.weight(1f))
                                        if (on) Glyph("check", pal.tiles[0], 18.dp, 3f)
                                    }
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Group("ONE COLOUR", hint = "Builds a whole theme from a single colour.") {
                GroupPad {
                    // Six to a row, each in an equal slot, so the circles sit evenly across the card.
                    Dynamic.seedChoices.chunked(6).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { seed ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Box(
                                        Modifier.size(44.dp).clip(CircleShape).background(Color(seed)).clickable {
                                            state.theme { follow = false; paletteId = "custom"; customSeed = seed }
                                        },
                                        contentAlignment = Alignment.Center,
                                    ) { if (prefs.paletteId == "custom" && prefs.customSeed == seed) Glyph("check", Color.Black, 22.dp, 3f) }
                                }
                            }
                            repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                if (prefs.paletteId == "custom") Dropdown(Dynamic.variants, prefs.variant, "Colour style") { v -> state.theme { variant = v } }
            }
        }

        Group(
            "APPEARANCE",
            hint = if (prefs.themeMode == "system") "Following your phone: currently ${if (state.dark) "dark" else "light"}." else null,
        ) {
            Dropdown(listOf("system" to "FOLLOW PHONE", "light" to "LIGHT", "dark" to "DARK"), prefs.themeMode, "Mode") { m -> state.theme { themeMode = m } }
            Dropdown(listOf("solid" to "SOLID", "glass" to "GLASS", "tint" to "TINTED GLASS"), prefs.tileStyle, "Tile style") { prefs.tileStyle = it }
            Dropdown(listOf("0" to "SOLID", "1" to "DIM WALLPAPER", "2" to "CLEAR WALLPAPER"), prefs.ground.toString(), "Home background") { prefs.ground = it.toInt() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowOf(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}

// ---- fonts and clock ---------------------------------------------------------------------------

@Composable
private fun TypePage(state: HomeState) {
    val pal = state.palette
    val prefs = state.prefs
    Page("Fonts", "SETTINGS / LOOK", onBack = { state.settingsPage = "" }) {
        PageHero("USING NOW", Fonts.choices.firstOrNull { it.first == prefs.font }?.second ?: "Koma default", "Applies everywhere in Koma.", pal.tiles[2 % pal.tiles.size])
        FontPicker(state, prefs.font, emptyList()) { state.setFont(it) }
        Group("TEXT SIZE") {
            ListRow("Text size", "${prefs.textScale}%", trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("A-", pal.onGround.copy(alpha = 0.14f), pal.onGround, height = 34) { prefs.textScale = (prefs.textScale - 5).coerceAtLeast(80) }
                    Pill("A+", pal.tiles[0], pal.ink, height = 34) { prefs.textScale = (prefs.textScale + 5).coerceAtMost(130) }
                }
            })
        }
    }
}

/** Searchable font list: a letter badge, the name set in its own face, and a radio. */
@Composable
private fun FontPicker(state: HomeState, selected: String, top: List<Pair<String, String>>, onPick: (String) -> Unit) {
    val pal = state.palette
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val importer = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri: android.net.Uri? ->
        if (uri != null && !state.importFont(uri)) {
            android.widget.Toast.makeText(ctx, "That file is not a usable font", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(pal.onGround.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph("search", pal.onGround.copy(alpha = 0.7f), 20.dp, 2f)
        Spacer(Modifier.width(10.dp))
        Field(q, { q = it }, "Search fonts", Modifier.weight(1f))
    }
    val all = (top + Fonts.choices).filter { q.isBlank() || it.second.contains(q.trim(), ignoreCase = true) }
    val builtIn = all.filter { !Fonts.isCustom(it.first) }
    val mine = all.filter { Fonts.isCustom(it.first) }
    @Composable
    fun FontRow(index: Int, id: String, name: String) {
        val on = selected == id
        val fam = when (id) {
            "same" -> Fonts.Display
            else -> Fonts.family(id)
        }
        Row(
            Modifier.fillMaxWidth().card()
                .clickable { onPick(id) }.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(pal.tiles[index % pal.tiles.size]), contentAlignment = Alignment.Center) {
                BasicText("Aa", style = TextStyle(fontFamily = fam, fontSize = 16.sp, color = pal.ink))
            }
            Spacer(Modifier.width(14.dp))
            BasicText(name, style = TextStyle(fontFamily = fam, fontSize = 18.sp, color = pal.onGround), modifier = Modifier.weight(1f))
            if (Fonts.isCustom(id)) {
                SmallIcon("close") { state.removeFont(id) }
                Spacer(Modifier.width(4.dp))
            }
            KCheck(on)
        }
    }
    if (builtIn.isNotEmpty()) {
        Group("KOMA'S OWN APPS") { builtIn.forEachIndexed { i, (id, name) -> FontRow(i, id, name) } }
    }
    if (mine.isNotEmpty() || q.isBlank()) {
        Group("YOUR FONTS") {
            mine.forEachIndexed { i, (id, name) -> FontRow(i + 3, id, name) }
            if (q.isBlank()) GroupPad {
                Pill("ADD YOUR OWN FONT (.TTF / .OTF)", pal.onGround.copy(alpha = 0.14f), pal.onGround, Modifier.fillMaxWidth(), height = 44) {
                    importer.launch(arrayOf("font/ttf", "font/otf", "font/sfnt", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream"))
                }
            }
        }
    }
    if (all.isEmpty()) BasicText("No font matches.", style = bodyStyle(pal.onGround.copy(alpha = 0.7f), 14))
}

private val clockTones = listOf("solid" to "SOLID", "dim" to "DIM DIGITS", "fade" to "FADE MINUTES", "outline" to "OUTLINE")

@Composable
internal fun ClockStyleSettings(state: HomeState, onBack: () -> Unit, onFont: () -> Unit) {
    val pal = state.palette
    val prefs = state.prefs
    val fontName = if (prefs.clockFont == "same") "Same as launcher" else Fonts.choices.firstOrNull { it.first == prefs.clockFont }?.second ?: "Same as launcher"
    Page("Clock look", "CLOCK / WIDGET", onBack = onBack) {
        Group {
            SwitchRow("Show seconds", "", prefs.clockSeconds) { prefs.clockSeconds = it }
            ListRow("Clock font", fontName, "type", pal.tiles[1 % pal.tiles.size], onClick = onFont)
            Dropdown(clockTones, prefs.clockTone, "Digit tone") { state.setClockTone(it) }
        }
                Group("CLOCK STYLE", hint = "Tap a style to use it on every clock. For a photo or a style on just one clock, open its options while editing.") {
          GroupPad {
        clockStyles.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEachIndexed { col, (id, name) ->
                    val on = prefs.clockStyle == id
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            Modifier.fillMaxWidth().height(150.dp).clip(TileShape).clickable { state.setClockStyle(id) },
                        ) {
                            Box(Modifier.fillMaxSize().alpha(if (on) 1f else 0.55f)) {
                                ClockTile(2, 2, pal.tiles[(row * 2 + col) % pal.tiles.size], pal.ink, id, prefs.clockSeconds)
                            }
                            if (on) Box(
                                Modifier.align(Alignment.TopEnd).padding(10.dp).size(28.dp).clip(RoundedCornerShape(9.dp)).background(pal.onGround),
                                contentAlignment = Alignment.Center,
                            ) { Glyph("check", pal.ground, 15.dp, 3f) }
                        }
                        Box(
                            Modifier.padding(start = 2.dp).clip(RoundedCornerShape(8.dp)).background(if (on) pal.onGround else Color.Transparent)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) { BasicText(name, style = monoStyle(if (on) pal.ground else pal.onGround.copy(alpha = 0.6f), 10, bold = on)) }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
          }
        }
    }
}

@Composable
internal fun ClockFontSettings(state: HomeState, onBack: () -> Unit) {
    Page("Clock font", "CLOCK / WIDGET", onBack = onBack) {
        FontPicker(state, state.prefs.clockFont, listOf("same" to "Same as launcher")) { state.setClockFont(it) }
    }
}

@Composable
private fun WeatherPage(state: HomeState) {
    val pal = state.palette
    Page("Weather", "SETTINGS / HOME", onBack = { state.settingsPage = "" }) {
        PageHero("WEATHER TILE", "Local forecast", "From Open-Meteo. Only rounded coordinates are sent.", state.palette.tiles[1 % state.palette.tiles.size])
        WeatherOptions(state)
        Group("THE CAT") {
            GroupPad {
                listOf(
                    listOf("sun" to "SUNNY", "cloud" to "CLOUDY", "rain" to "RAIN"),
                    listOf("snow" to "SNOW", "storm" to "STORM", "fog" to "FOG"),
                    listOf("night" to "NIGHT"),
                ).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (mood, name) ->
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.fillMaxWidth().height(70.dp).clip(RoundedCornerShape(16.dp)).background(pal.tiles[0])) {
                                    Cat(mood = mood, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp).size(width = 90.dp, height = 61.dp))
                                }
                                BasicText(name, style = monoStyle(pal.onGround.copy(alpha = 0.7f), 9))
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicStylePage(state: HomeState) {
    val pal = state.palette
    val prefs = state.prefs
    val sample = app.koma.launcher.data.NowPlaying("Vaaroon forever", "Anand Bhaskar", true, "", position = 70_000L, duration = 200_000L)
    Page("Music", "SETTINGS / LOOK", onBack = { state.settingsPage = "" }) {
        PageHero("MUSIC STYLE", musicStyles.firstOrNull { it.first == prefs.musicStyle }?.second ?: "", "Tap a style to use it on every music tile.")
        Group("MUSIC STYLE") {
            GroupPad {
                musicStyles.chunked(2).forEachIndexed { row, pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEachIndexed { col, (id, name) ->
                            val on = prefs.musicStyle == id
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.fillMaxWidth().height(190.dp).clip(TileShape).clickable { state.setMusicStyle(id) }) {
                                    Box(Modifier.fillMaxSize().alpha(if (on) 1f else 0.55f)) {
                                        MusicTile(2, 2, pal.tiles[(row * 2 + col + 1) % pal.tiles.size], pal.ink, sample.title, sample.artist, true, {}, {}, {}, sample, {}, id)
                                    }
                                    if (on) Box(
                                        Modifier.align(Alignment.TopEnd).padding(10.dp).size(28.dp).clip(RoundedCornerShape(9.dp)).background(pal.onGround),
                                        contentAlignment = Alignment.Center,
                                    ) { Glyph("check", pal.ground, 15.dp, 3f) }
                                }
                                Box(
                                    Modifier.padding(start = 2.dp).clip(RoundedCornerShape(8.dp)).background(if (on) pal.onGround else Color.Transparent)
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) { BasicText(name, style = monoStyle(if (on) pal.ground else pal.onGround.copy(alpha = 0.6f), 10, bold = on)) }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun GesturesPage(state: HomeState) {
    val gest = state.homeGestures()
    val needs = gest.values.any { it in setOf("lock", "shade", "quick", "recents", "shot", "power") }
    Page("Gestures", "SETTINGS", onBack = { state.settingsPage = "" }) {
        PageHero("HOME SCREEN", "${Gestures.homeNames.count { (k, _) -> (gest[k] ?: "").isNotEmpty() }} of ${Gestures.homeNames.size} set", "Swipes and taps on empty space.", state.palette.tiles[3 % state.palette.tiles.size])
        Group("HOME SCREEN", hint = if (needs) "System actions need Koma switched on in Accessibility settings." else null) {
            Gestures.homeNames.forEach { (key, title) ->
                val cur = gest[key] ?: ""
                val appLabel = if (cur.startsWith("app:")) state.apps.firstOrNull { it.key == cur.removePrefix("app:") }?.label ?: "App" else null
                val options = Gestures.actions + listOfNotNull(appLabel?.let { cur to "APP: ${it.uppercase()}" }) + ("app" to "OPEN AN APP...")
                Dropdown(options, cur, title, if (key == "up" && state.prefs.homeSearch) "SEARCH" else "NOTHING") { v ->
                    if (v == "app") {
                        state.openDrawer(pickFor = Pick("hgesture-$key", null))
                    } else state.setHomeGesture(key, v)
                }
            }
        }
        if (needs) Group {
            ListRow("Accessibility settings", if (app.koma.launcher.data.KomaFocusService.isRunning()) "Koma is on" else "Koma is off", onClick = {
                state.appCtx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
        }
    }
}

@Composable
private fun LooksPage(state: HomeState) {
    val pal = state.palette
    val t = pal.tiles
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var withWall by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri: android.net.Uri? ->
        if (uri != null) scope.launch {
            if (!state.stagePickedWall(uri)) android.widget.Toast.makeText(ctx, "Could not read that picture", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    Page("Looks", "SETTINGS / LOOK", onBack = { state.settingsPage = "" }) {
        PageHero("YOUR LOOKS", if (state.looks.isEmpty()) "None saved yet" else "${state.looks.size} saved", "Tap a look to put it on.", t[4 % t.size])
        Group("YOUR LOOKS") {
            if (state.looks.isEmpty()) GroupPad { BasicText("Nothing saved yet.", style = bodyStyle(pal.onGround.copy(alpha = 0.7f), 14)) }
            GroupPad {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.looks.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { l ->
                                LookCard(state, l, busy, Modifier.weight(1f), onApply = {
                                    if (!busy) {
                                        busy = true
                                        scope.launch {
                                            state.applyLook(l.id)
                                            busy = false
                                            state.goHome()
                                        }
                                    }
                                }, onDelete = { state.deleteLook(l.id) })
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Group("SAVE THIS LOOK", hint = "Layout, theme, fonts, icons and drawer. Android widgets are not kept.") {
            GroupPad {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(pal.onGround.copy(alpha = 0.08f)).padding(start = 16.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Field(name, { name = it }, "Name, for example Night desk", Modifier.weight(1f))
                    Pill(if (busy) "SAVING" else "SAVE", t[0], pal.ink, height = 38) {
                        if (!busy) {
                            busy = true
                            scope.launch {
                                val ok = state.saveLook(name, withWall)
                                if (!ok) android.widget.Toast.makeText(ctx, "Saved without the wallpaper. Choose a wallpaper picture below and save again.", android.widget.Toast.LENGTH_LONG).show()
                                name = ""
                                busy = false
                            }
                        }
                    }
                }
            }
            SwitchRow("Include wallpaper", "", withWall) { withWall = it }
            if (withWall) {
                ListRow(
                    "Wallpaper picture",
                    if (state.hasPendingWall) "Picture chosen. Tap to pick another" else "Choose the picture to save",
                    "pics", t[1 % t.size], onClick = { picker.launch(arrayOf("image/*")) },
                )
            }
        }
    }
}

/** A saved look as a small phone: its wallpaper, its tiles in its colours, and its name underneath. Tap to apply. */
@Composable
private fun LookCard(state: HomeState, l: app.koma.launcher.data.Look, busy: Boolean, modifier: Modifier, onApply: () -> Unit, onDelete: () -> Unit) {
    val pal = state.palette
    val pv = androidx.compose.runtime.produceState<LookPreview?>(null, l.id) { value = state.loadLookPreview(l.id) }.value
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(surfaceColor()).clickable(enabled = !busy, onClick = onApply).padding(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(16.dp)).background(pv?.pal?.ground ?: pal.ground)) {
            if (pv != null) {
                if (pv.wall != null) {
                    androidx.compose.foundation.Image(pv.wall, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
                }
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                    val cols = pv.cols
                    val gap = 3.dp.toPx()
                    val cell = (size.width - gap * (cols - 1)) / cols
                    val ch = (size.height - gap * (pv.rows - 1)) / pv.rows
                    val cells = pack(pv.tiles, cols)
                    pv.tiles.forEachIndexed { i, t ->
                        if (t.kind == Kind.SPACE) return@forEachIndexed
                        val w = minOf(t.w, cols)
                        val x = cells[i].col * (cell + gap)
                        val y = cells[i].row * (ch + gap)
                        if (y > size.height) return@forEachIndexed
                        val base = if (t.color >= 0) pv.pal.all[t.color % pv.pal.all.size]
                        else pv.pal.tiles[kotlin.math.abs(t.kind.ordinal * 7 + t.arg.hashCode()) % pv.pal.tiles.size]
                        val col = if (t.kind == Kind.ALL) pv.pal.onGround.copy(alpha = 0.5f) else base
                        drawRoundRect(
                            color = col,
                            topLeft = androidx.compose.ui.geometry.Offset(x, y),
                            size = androidx.compose.ui.geometry.Size(w * cell + (w - 1) * gap, minOf(t.h * ch + (t.h - 1) * gap, size.height - y)),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(l.name, style = bodyStyle(pal.onGround, 15, medium = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(
                    (if (busy) "APPLYING" else if (l.wall) "WITH WALLPAPER" else "NO WALLPAPER"),
                    style = monoStyle(pal.onGround.copy(alpha = 0.6f), 9), maxLines = 1,
                )
            }
            Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
                Glyph("close", pal.onGround.copy(alpha = 0.6f), 16.dp, 2.2f)
            }
        }
    }
}

@Composable
fun SmallIcon(glyph: String, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Glyph(glyph, pal.onGround.copy(alpha = 0.8f), 20.dp, 2f)
    }
}

// ---- icons -------------------------------------------------------------------------------------

// ---- apps (drawer, icons, folders, hidden) -----------------------------------------------------

@Composable
private fun AppsPage(state: HomeState) {
    val pal = state.palette
    val t = pal.tiles
    val prefs = state.prefs
    val cfg = state.iconConfig()
    val samples = state.apps.take(6)
    var tab by remember {
        mutableStateOf(
            when (state.settingsPage) { "icons" -> 1; "folders" -> 2; "hidden" -> 3; else -> 0 },
        )
    }
    // The icon style that was tapped, shown in a preview that asks Apply or Cancel.
    var pending by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        AppPage(
            "Apps", "${state.apps.size} APPS / ${state.hiddenKeys.size} HIDDEN / ${state.folders.size} FOLDER${if (state.folders.size == 1) "" else "S"}",
            onBack = { state.settingsPage = "" },
            nav = {
                PillNav(listOf("all" to "DRAWER", "palette" to "ICONS", "widget" to "FOLDERS", "search" to "HIDDEN"), tab) { tab = it }
            },
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val folderCount = state.folders.size
                PageHero(
                    "YOUR APPS", "${state.apps.size} apps",
                    "${state.hiddenKeys.size} hidden, $folderCount folder${if (folderCount == 1) "" else "s"}",
                    t[2 % t.size],
                )
                when (tab) {
                    0 -> DrawerBody(state)
                    1 -> IconsBody(state) { pending = it }
                    2 -> FoldersBody(state)
                    else -> HiddenBody(state)
                }
            }
        }
        val choice = pending
        if (choice != null) {
            val label = IconStyles.styles.firstOrNull { it.first == choice }?.second ?: ""
            Sheet(onDismiss = { pending = null }) {
                BasicText(label.uppercase(), style = dispStyle(pal.onGround, 22))
                BasicText("PREVIEW", style = monoStyle(pal.onGround.copy(alpha = 0.6f), 9))
                CompositionLocalProvider(LocalIconCfg provides IconCfg(choice, prefs.iconShape, prefs.forceIcons, cfg.sig + choice)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        samples.forEach { a -> AppIcon(state.icons, a, 48.dp, bg = pal.tiles[kotlin.math.abs(a.key.hashCode()) % pal.tiles.size], ink = pal.ink, line = pal.onGround) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("CANCEL", pal.onGround.copy(alpha = 0.14f), pal.onGround, Modifier.weight(1f), height = 46) { pending = null }
                    Pill("APPLY", pal.tiles[0], pal.ink, Modifier.weight(1f), height = 46) {
                        state.iconSettings { iconStyle = choice }
                        pending = null
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.DrawerBody(state: HomeState) {
    val pal = state.palette
    val t = pal.tiles
    val prefs = state.prefs
    val ctx = LocalContext.current
    val byName = prefs.drawerSort == "alpha" || prefs.drawerSort == "za"
    Group("DRAWER") {
        Dropdown(
            listOf("alpha" to "A TO Z", "za" to "Z TO A", "recent" to "RECENTLY USED", "newest" to "NEWEST FIRST"),
            prefs.drawerSort, "Sort",
        ) { prefs.drawerSort = it }
        Dropdown(listOf("0" to "COMPACT", "1" to "TALL", "2" to "FULL"), prefs.drawerHeight.toString(), "Height") { prefs.drawerHeight = it.toInt() }
        Dropdown(listOf("0" to "DIMMED WALLPAPER", "1" to "CLEAR WALLPAPER", "2" to "NO WALLPAPER"), prefs.drawerBg.toString(), "Background") { prefs.drawerBg = it.toInt() }
        Dropdown(listOf("top" to "TOP", "bottom" to "BOTTOM"), prefs.drawerKoma, "Koma apps") { prefs.drawerKoma = it }
    }
    Group("OPTIONS") {
        SwitchRow("Frosted panel", "", prefs.drawerPanel) { prefs.drawerPanel = it }
        SwitchRow("Search bar", "", prefs.drawerSearch) { prefs.drawerSearch = it }
        SwitchRow("Section letters", if (byName) "" else "Sorted by name only", prefs.drawerHeaders && byName) { prefs.drawerHeaders = it }
        SwitchRow("Left-handed", "", prefs.leftHanded) { prefs.leftHanded = it }
    }
    Group("LIMITS") {
        ListRow("App limits", AppLimits.all(ctx).size.let { if (it == 0) "Daily time per app" else "$it set" }, "timer", t[3 % t.size], onClick = { state.settingsPage = "limits" })
    }
}

@Composable
private fun ColumnScope.IconsBody(state: HomeState, onPick: (String) -> Unit) {
    val pal = state.palette
    val prefs = state.prefs
    val ctx = LocalContext.current
    val packs = remember { IconPack.installed(ctx) }
    val sample = state.apps.firstOrNull { it.pkg.contains("chrome") } ?: state.apps.firstOrNull()
    val cfg = state.iconConfig()
    BasicText("STYLE", style = monoStyle(pal.onGround.copy(alpha = 0.6f), 10))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconStyles.styles.forEach { (id, name) ->
            val on = prefs.iconStyle == id
            Column(
                Modifier.width(92.dp).clip(RoundedCornerShape(20.dp))
                    .background(if (on) pal.tiles[0] else pal.onGround.copy(alpha = 0.1f))
                    .clickable { onPick(id) }.padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompositionLocalProvider(LocalIconCfg provides IconCfg(id, prefs.iconShape, prefs.forceIcons, cfg.sig + id)) {
                    if (on) AppIcon(state.icons, sample, 46.dp, bg = pal.ink, ink = pal.tiles[0], line = pal.ink)
                    else AppIcon(state.icons, sample, 46.dp, bg = pal.tiles[0], ink = pal.ink, line = pal.onGround)
                }
                BasicText(
                    name, style = bodyStyle(if (on) pal.ink else pal.onGround, 12, medium = true),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    Group("SHAPE AND PACK", hint = if (packs.isEmpty()) "No icon packs found. Install a Nova, ADW or Lawnchair compatible pack to use it here." else null) {
        if (prefs.iconStyle in IconStyles.shaped) Dropdown(IconStyles.shapes, prefs.iconShape, "Shape") { s -> state.iconSettings { iconShape = s } }
        Dropdown(listOf("" to "NONE") + packs.map { it.first to it.second.uppercase() }, prefs.iconPack, "Icon pack") { id -> state.iconSettings { iconPack = id } }
        SwitchRow(
            "Force icons on every app", "Restyle apps that ignore adaptive icons",
            prefs.forceIcons,
        ) { on -> state.iconSettings { forceIcons = on } }
    }
    Group("OTHER ICONS", hint = "Shortcut tile badges (CAM, WEB, PHONE) and the Bluetooth device icons.") {
        Dropdown(IconStyles.badges, prefs.tileBadge, "Shortcut badge") { s -> prefs.tileBadge = s }
        Dropdown(IconStyles.btIcons, prefs.btIcon, "Bluetooth icons") { s -> prefs.btIcon = s }
    }
}

@Composable
private fun ColumnScope.FoldersBody(state: HomeState) {
    val pal = state.palette
    val t = pal.tiles
    fun show(id: String) {
        state.openFolderId = id
        state.openDrawer()
    }
    Group {
        if (state.folders.isEmpty()) GroupPad { BasicText("No folders yet.", style = bodyStyle(pal.onGround.copy(alpha = 0.7f), 14)) }
        state.folders.forEachIndexed { i, f ->
            val apps = f.items.count { !it.widget }
            val widgets = f.items.count { it.widget }
            ListRow(
                f.name.ifBlank { "Folder" }, "$apps apps" + if (widgets > 0) ", $widgets widgets" else "",
                "all", t[i % t.size], onClick = { show(f.id) },
            )
        }
        ListRow("New folder", "", "widget", t[0 % t.size], onClick = { show(state.newFolder().id) })
    }
}

@Composable
private fun ColumnScope.HiddenBody(state: HomeState) {
    val pal = state.palette
    var q by remember { mutableStateOf("") }
    val hidden = state.hiddenKeys
    BasicText(
        "Hidden apps stay out of the drawer. Search still finds them.",
        style = bodyStyle(pal.onGround.copy(alpha = 0.7f), 13),
    )
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(pal.onGround.copy(alpha = 0.12f)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph("search", pal.onGround.copy(alpha = 0.7f), 20.dp, 2f)
        Spacer(Modifier.width(10.dp))
        Field(q, { q = it }, "Search apps", Modifier.weight(1f))
    }
    val list = state.apps.filter { q.isBlank() || it.label.contains(q.trim(), ignoreCase = true) }.sortedBy { it.label.lowercase() }
    Group {
        list.forEach { a ->
            val on = a.key in hidden
            Row(
                Modifier.fillMaxWidth().clickable { state.toggleHidden(a.key) }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(state.icons, a, 36.dp, bg = pal.tiles[kotlin.math.abs(a.key.hashCode()) % pal.tiles.size], ink = pal.ink, line = pal.onGround)
                Spacer(Modifier.width(14.dp))
                BasicText(a.label, style = bodyStyle(pal.onGround, 16, medium = true), maxLines = 1, modifier = Modifier.weight(1f))
                KToggle(on) { state.toggleHidden(a.key) }
            }
        }
    }
    if (list.isEmpty()) BasicText("No app matches.", style = bodyStyle(pal.onGround.copy(alpha = 0.7f), 14))
}

// ---- calendar ----------------------------------------------------------------------------------

@Composable
private fun CalendarPage(state: HomeState) {
    val pal = state.palette
    val ctx = LocalContext.current
    val granted = state.calendarGranted
    val cals by produceState(emptyList<CalInfo>(), state.tick, granted) {
        value = if (granted) withContext(Dispatchers.IO) { CalendarRepo.calendars(ctx) } else emptyList()
    }
    Page("Calendar", "SETTINGS / HOME", onBack = { state.settingsPage = "" }) {
        if (!granted) {
            BasicText("Koma reads the calendars already on your phone: Google, Samsung, Outlook, holidays and anything else synced.", style = bodyStyle(pal.onGround.copy(alpha = 0.7f), 14))
            Pill("ALLOW CALENDAR ACCESS", pal.tiles[0], pal.ink, Modifier.fillMaxWidth(), height = 48) { state.request(Manifest.permission.READ_CALENDAR) }
        } else {
            val hidden = state.hiddenCalendars
            val colors = state.calendarColors
            PageHero("ON THE CALENDAR TILE", "${cals.count { it.id !in hidden }} of ${cals.size} on", "${cals.map { it.account }.distinct().size} accounts", pal.tiles[1 % pal.tiles.size])
            Group("REFRESH") {
                Dropdown(
                    listOf("1" to "EVERY MINUTE", "5" to "EVERY 5 MINUTES", "30" to "EVERY 30 MINUTES", "60" to "EVERY HOUR", "1440" to "ONCE A DAY"),
                    state.prefs.calRefreshMin.toString(), "Read the calendar",
                ) { state.prefs.calRefreshMin = it.toInt() }
            }
            cals.groupBy { it.account }.forEach { (account, list) ->
                Group(account.uppercase()) {
                    // One colour for the whole account: it marks all of its events on the Calendar tile.
                    ColourRow(colors[account] ?: -1) { state.setCalendarColor(account, it) }
                    list.forEach { c ->
                        ListRow(c.name, "", trailing = { KToggle(c.id !in hidden) { state.toggleCalendar(c.id) } }, onClick = { state.toggleCalendar(c.id) })
                    }
                }
            }
            if (cals.isEmpty()) BasicText("No calendars on this phone yet. Add an account in Android settings, then it appears here.", style = bodyStyle(pal.onGround.copy(alpha = 0.7f), 14))
            BasicText("Pick a colour for each account. It marks that account's events on the Calendar tile; A lets the palette choose.", style = bodyStyle(pal.onGround.copy(alpha = 0.55f), 12))
            BasicText("Holidays show up when your account's Holidays calendar is switched on in the Calendar app.", style = bodyStyle(pal.onGround.copy(alpha = 0.55f), 12))
        }
    }
}

// ---- app drawer --------------------------------------------------------------------------------

// ---- search ------------------------------------------------------------------------------------

@Composable
private fun SearchPage(state: HomeState) {
    val pal = state.palette
    val prefs = state.prefs
    val ctx = LocalContext.current
    Page("Search", "SETTINGS / HOME", onBack = { state.settingsPage = "" }) {
        PageHero("SEARCH ENGINE", Engines.all.firstOrNull { it.first == state.prefs.engine }?.second?.first ?: "Web", "Apps and people first, then the web.", state.palette.tiles[4 % state.palette.tiles.size])
        SearchPreview(Engines.all.firstOrNull { it.first == prefs.engine }?.second?.first ?: "the web")
        Group("OPENING SEARCH") {
            SwitchRow("Swipe up opens search", "From the bottom edge of the home screen", prefs.homeSearch) { prefs.homeSearch = it }
        }
        Group("WEB ENGINE") {
            Engines.all.forEach { (id, v) ->
                val on = prefs.engine == id
                val host = if (id == "custom") "Your own address" else (android.net.Uri.parse(v.second).host ?: "")
                ListRow(
                    v.first, host, onClick = { prefs.engine = id },
                    trailing = { Box(Modifier.size(22.dp)) { if (on) Glyph("check", pal.tiles[0], 22.dp, 3f) } },
                )
            }
        }
        if (prefs.engine == "custom") CustomEngine(prefs)
    }
}

private val enginePresets = listOf(
    "Ecosia" to "https://www.ecosia.org/search?q=%s",
    "Startpage" to "https://www.startpage.com/do/search?q=%s",
    "Qwant" to "https://www.qwant.com/?q=%s",
    "Wikipedia" to "https://en.wikipedia.org/w/index.php?search=%s",
    "YouTube" to "https://www.youtube.com/results?search_query=%s",
)

/** The address of your own search engine, with a check that it will work and a preview of the link it makes. */
@Composable
private fun CustomEngine(prefs: app.koma.launcher.data.Prefs) {
    val pal = LocalPalette.current
    val text = prefs.customEngine.trim()
    val problem = when {
        text.isEmpty() -> "ADD AN ADDRESS. UNTIL THEN GOOGLE ANSWERS."
        !(text.startsWith("https://") || text.startsWith("http://")) -> "THE ADDRESS MUST START WITH HTTPS://"
        !text.contains("%s") -> "ADD %S WHERE THE SEARCH WORDS GO"
        else -> null
    }
    Group("YOUR ADDRESS", hint = "Write the address of a search results page and put %s where the words you search for should go.") {
        GroupPad {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(pal.onGround.copy(alpha = 0.08f)).padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Field(prefs.customEngine, { prefs.customEngine = it }, "https://example.com/search?q=%s", Modifier.weight(1f), style = bodyStyle(pal.onGround, 15, medium = true))
                if (prefs.customEngine.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(28.dp).clip(CircleShape).clickable { prefs.customEngine = "" }, contentAlignment = Alignment.Center) {
                        Glyph("close", pal.onGround.copy(alpha = 0.6f), 16.dp, 2.4f)
                    }
                }
            }
            BasicText(
                problem ?: "READY",
                style = monoStyle(if (problem == null) pal.tiles[0] else pal.onGround.copy(alpha = 0.6f), 10, bold = true),
            )
            if (problem == null) BasicText(
                "weather today  >  " + text.replace("%s", android.net.Uri.encode("weather today")),
                style = bodyStyle(pal.onGround.copy(alpha = 0.7f), 12), maxLines = 3,
            )
        }
        GroupPad {
            BasicText("START FROM", style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10, bold = true))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                enginePresets.forEach { (n, u) ->
                    Box(
                        Modifier.clip(CircleShape).border(1.5.dp, pal.onGround.copy(alpha = if (text == u) 0.8f else 0.2f), CircleShape)
                            .clickable { prefs.customEngine = u }.padding(horizontal = 14.dp, vertical = 9.dp),
                    ) { BasicText(n.uppercase(), style = monoStyle(pal.onGround, 10, bold = true), maxLines = 1) }
                }
            }
        }
    }
}

/** What the real search looks like when you open it: suggestion pills over the search bar. Just a picture; nothing in it is tappable. */
@Composable
private fun SearchPreview(engine: String) {
    val pal = LocalPalette.current
    val face = pal.ground.copy(alpha = 0.96f)
    val edge = pal.onGround.copy(alpha = 0.14f)
    Column(Modifier.fillMaxWidth().clip(TileShape).background(Color.Black.copy(alpha = 0.35f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BasicText("PREVIEW / SEARCHES WITH ${engine.uppercase()}", style = monoStyle(pal.onGround.copy(alpha = 0.6f), 10, bold = true), maxLines = 1)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("weather today", "news", "translate", "maps near me").forEach { text ->
                Row(
                    Modifier.clip(CircleShape).background(face).border(1.dp, edge, CircleShape).padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Magnifier(pal.onGround.copy(alpha = 0.55f), 14.dp)
                    Spacer(Modifier.width(8.dp))
                    BasicText(text, style = bodyStyle(pal.onGround, 15, medium = true), maxLines = 1)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50)).background(face).border(1.dp, edge, RoundedCornerShape(50)).padding(start = 18.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Magnifier(pal.onGround.copy(alpha = 0.7f), 20.dp)
            Spacer(Modifier.width(10.dp))
            BasicText("Search", style = bodyStyle(pal.onGround.copy(alpha = 0.5f), 17, medium = true))
        }
    }
}

// ---- system ------------------------------------------------------------------------------------

@Composable
private fun SystemPage(state: HomeState) {
    val pal = state.palette
    val t = pal.tiles
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    fun open(action: String) {
        try {
            ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
        }
    }
    Page("System", "SETTINGS / PHONE", onBack = { state.settingsPage = "" }) {
        PageHero("THIS PHONE", "Access and defaults", "The permissions Koma uses, and the default launcher.", state.palette.tiles[3 % state.palette.tiles.size])
        Group {
            ListRow("Default launcher", "", "all", t[0 % t.size], onClick = { open(Settings.ACTION_HOME_SETTINGS) })
        }
        Group("ACCESS") {
            ListRow("Notification access", "", "music", t[1 % t.size], onClick = { open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) }, trailing = { Status(state.media.hasAccess) })
            ListRow("Usage access", "", "device", t[2 % t.size], onClick = { open(Settings.ACTION_USAGE_ACCESS_SETTINGS) }, trailing = { Status(state.usageGranted) })
            ListRow("Accessibility guard", "", "lock", t[3 % t.size], onClick = { open(Settings.ACTION_ACCESSIBILITY_SETTINGS) })
            ListRow("Calendar", "", "cal", t[4 % t.size], onClick = { state.request(Manifest.permission.READ_CALENDAR) }, trailing = { Status(state.calendarGranted, "ALLOWED", "ALLOW") })
            ListRow("Location", "", "sun", t[0 % t.size], onClick = { state.request(Manifest.permission.ACCESS_COARSE_LOCATION) }, trailing = { Status(state.locationGranted, "ALLOWED", "ALLOW") })
            ListRow("Contacts", "", "users", t[1 % t.size], onClick = { state.request(Manifest.permission.READ_CONTACTS) }, trailing = { Status(state.contactsGranted, "ALLOWED", "ALLOW") })
            ListRow("Call log", "", "phone", t[2 % t.size], onClick = { state.request(Manifest.permission.READ_CALL_LOG) }, trailing = { Status(state.callsGranted, "ALLOWED", "ALLOW") })
        }
        Group("KOMA") {
            ListRow("About", versionText(), "info", t[4 % t.size], onClick = { state.settingsPage = "about" })
        }
        Group("RESET") {
            ListRow("Reset home layout", "Notes and tasks stay", "trash", t[3 % t.size], onClick = { scope.launch { state.resetLayout() } })
        }
        BasicText("Everything stays on your phone.", style = monoStyle(pal.onGround.copy(alpha = 0.5f), 10))
    }
}
