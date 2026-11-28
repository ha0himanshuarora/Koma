package app.koma.launcher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.koma.launcher.data.AppLimit
import app.koma.launcher.data.AppLimits
import app.koma.launcher.data.DayUsage
import app.koma.launcher.data.FocusEngine
import app.koma.launcher.data.TimeFmt
import app.koma.launcher.data.UsageRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private class STData(val week: List<DayUsage>, val used: Map<String, Long>)

private fun dur(ms: Long): String {
    val m = (ms / 60_000L).toInt()
    return when {
        ms <= 0L -> "0m"
        m < 1 -> "<1m"
        m >= 60 -> if (m % 60 == 0) "${m / 60}h" else "${m / 60}h ${m % 60}m"
        else -> "${m}m"
    }
}

private fun mins(m: Int) = dur(m * 60_000L)

/** The name to show for an app. Koma's own apps (Notes, Clock and the rest) share one entry. */
private fun appName(state: HomeState, pkg: String): String =
    state.apps.firstOrNull { it.pkg == pkg }?.label ?: if (pkg.startsWith("app.koma.")) "Koma apps" else pkg

private fun hourName(h: Int): String =
    if (TimeFmt.is24()) "%02d".format(h) else "${if (h % 12 == 0) 12 else h % 12}${if (h < 12) "a" else "p"}"

private fun timeOf(ms: Long): String {
    val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime()
    val h = t.hour
    val mm = "%02d".format(t.minute)
    return if (TimeFmt.is24()) "%02d:%s".format(h, mm) else "${if (h % 12 == 0) 12 else h % 12}:$mm ${if (h < 12) "AM" else "PM"}"
}

/** Totals per app across several days, longest first. */
private fun sumApps(days: List<DayUsage>): List<Pair<String, Long>> {
    val m = HashMap<String, Long>()
    days.forEach { d -> d.apps.forEach { (p, ms) -> m[p] = (m[p] ?: 0L) + ms } }
    return m.toList().sortedByDescending { it.second }
}

private fun sumCategories(days: List<DayUsage>): List<Pair<String, Long>> {
    val m = HashMap<String, Long>()
    days.forEach { d -> d.categories.forEach { (c, ms) -> m[c] = (m[c] ?: 0L) + ms } }
    return m.toList().sortedByDescending { it.second }
}

/** The Screen time app: a day at a time, the week, and daily limits per app, sharing its numbers and limits with the home screen tile. */
@Composable
fun ScreenTimeApp(state: HomeState) {
    val pal = state.palette
    val ctx = LocalContext.current
    val granted = state.usageGranted
    var tab by remember { mutableIntStateOf(0) }
    var back by remember { mutableIntStateOf(0) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    val version = AppLimits.version
    val data by produceState(STData(emptyList(), emptyMap()), granted, version) {
        while (true) {
            awaitVisible()
            value = if (granted) withContext(Dispatchers.IO) {
                // An app that was removed loses its limit too, so it does not linger in the list.
                val pm = ctx.packageManager
                AppLimits.all(ctx).keys.filter { !UsageRepo.installed(pm, it) }.forEach { AppLimits.set(ctx, it, 0) }
                val used = AppLimits.all(ctx).mapValues { (p, l) -> try { AppLimits.usedMs(ctx, p, l) } catch (e: Exception) { 0L } }
                STData(UsageRepo.week(ctx), used)
            } else STData(emptyList(), emptyMap())
            delay(2 * 60_000L)
        }
    }

    AppPage(
        title = "Screen time",
        sub = when (tab) { 0 -> "Day"; 1 -> "Week"; else -> "Limits" },
        onBack = { state.exitApp() },
        nav = { PillNav(listOf("bars" to "DAY", "cal" to "WEEK", "lock" to "LIMITS"), tab) { tab = it } },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!granted && tab != 2) {
                InfoCard("USAGE ACCESS") {
                    Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        BasicText(
                            "Screen time counts the time each app is in front, using the phone's usage access. Nothing leaves your phone.",
                            style = bodyStyle(pal.onGround.copy(alpha = 0.8f), 14),
                        )
                        Pill("ALLOW USAGE ACCESS", pal.tiles[0], pal.ink) {
                            try { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                        }
                    }
                }
            } else if (data.week.isEmpty() && tab != 2) {
                InfoCard("LOADING") { BasicText("Reading today's use...", style = bodyStyle(pal.onGround.copy(alpha = 0.8f), 14), modifier = Modifier.padding(horizontal = 14.dp)) }
            } else when (tab) {
                0 -> DayTab(state, data, back, { back = it }) { sheet = it }
                1 -> WeekTab(state, data, { back = it; tab = 0 }) { sheet = it }
                else -> LimitsTab(state, data, { picking = true }) { sheet = it }
            }
        }
    }

    sheet?.let { pkg -> AppLimitSheet(state, data, pkg) { sheet = null } }
    if (picking) PickAppSheet(state, data, onDismiss = { picking = false }) { picking = false; sheet = it }
}

@Composable
private fun StatGrid(stats: List<WStat>) {
    stats.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { StatCard(it, Modifier.weight(1f).fillMaxHeight()) }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun UseHero(state: HomeState, kicker: String, big: String, note: String, pills: List<String> = emptyList()) {
    val pal = state.palette
    Box(Modifier.fillMaxWidth().clip(TileShape).background(pal.tiles[2 % pal.tiles.size])) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(kicker.uppercase(), style = monoStyle(pal.ink, 11, bold = true))
            BasicText(big, style = dispStyle(pal.ink, 64, -0.04f), maxLines = 1)
            if (note.isNotEmpty()) BasicText(note, style = bodyStyle(pal.ink.copy(alpha = 0.85f), 14, medium = true))
            if (pills.isNotEmpty()) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pills.forEach { t ->
                    Box(Modifier.clip(RoundedCornerShape(50)).background(pal.ink.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 7.dp)) {
                        BasicText(t, style = monoStyle(pal.ink, 10, bold = true), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(state: HomeState, data: STData, pkg: String, ms: Long, top: Long, sub: String = "", onClick: () -> Unit) {
    val pal = state.palette
    val info = state.apps.firstOrNull { it.pkg == pkg }
    val limit = AppLimits.all(LocalContext.current)[pkg]
    val locked = limit != null && AppLimits.isLocked(LocalContext.current, pkg)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(state.icons, info, 38.dp, bg = pal.tiles[0], ink = pal.ink, line = pal.onGround)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            BasicText(appName(state, pkg), style = bodyStyle(pal.onGround, 15, medium = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(pal.onGround.copy(alpha = 0.14f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth((ms.toFloat() / top.coerceAtLeast(1L)).coerceIn(0.03f, 1f)).clip(CircleShape).background(pal.tiles[0]))
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            BasicText(dur(ms), style = bodyStyle(pal.onGround, 14, medium = true), maxLines = 1)
            val tag = when {
                locked -> "LOCKED"
                limit != null -> "LIMIT ${mins(limit.min)}"
                else -> sub
            }
            if (tag.isNotEmpty()) BasicText(tag, style = monoStyle(if (limit != null) pal.tiles[0] else pal.onGround.copy(alpha = 0.55f), 9, bold = limit != null), maxLines = 1)
        }
    }
}

/** One bar split by how much of the time each of the top apps took, with the rest as a grey end. */
@Composable
private fun ShareBar(state: HomeState, apps: List<Pair<String, Long>>, total: Long) {
    val pal = state.palette
    if (apps.size < 2 || total <= 0L) return
    val shown = apps.take(4)
    val rest = (total - shown.sumOf { it.second }).coerceAtLeast(0L)
    Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            shown.forEachIndexed { i, (_, ms) -> Box(Modifier.weight(ms.toFloat().coerceAtLeast(1f)).fillMaxHeight().background(pal.all[i % pal.all.size])) }
            if (rest > 0L) Box(Modifier.weight(rest.toFloat()).fillMaxHeight().background(pal.onGround.copy(alpha = 0.2f)))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            shown.take(3).forEachIndexed { i, (p, ms) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(pal.all[i % pal.all.size]))
                    BasicText(appName(state, p) + " " + (ms * 100 / total) + "%", style = monoStyle(pal.onGround.copy(alpha = 0.7f), 9), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Categories(state: HomeState, cats: List<Pair<String, Long>>) {
    val pal = state.palette
    val total = cats.sumOf { it.second }.coerceAtLeast(1L)
    if (cats.isEmpty()) return
    InfoCard("CATEGORIES") {
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                cats.forEachIndexed { i, (_, ms) -> Box(Modifier.weight(ms.toFloat().coerceAtLeast(1f)).fillMaxHeight().background(pal.all[i % pal.all.size])) }
            }
            cats.take(6).forEachIndexed { i, (name, ms) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(pal.all[i % pal.all.size]))
                    BasicText(name, style = bodyStyle(pal.onGround, 14), modifier = Modifier.weight(1f), maxLines = 1)
                    BasicText("${ms * 100 / total}%", style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10))
                    BasicText(dur(ms), style = bodyStyle(pal.onGround, 14, medium = true))
                }
            }
        }
    }
}

@Composable
private fun DayTab(state: HomeState, data: STData, back: Int, setBack: (Int) -> Unit, openApp: (String) -> Unit) {
    val pal = state.palette
    val idx = 6 - back
    val day = data.week.getOrNull(idx) ?: return
    val prev = data.week.getOrNull(idx - 1)
    val active = data.week.filter { it.total > 0L }
    val avg = if (active.isEmpty()) 0L else active.sumOf { it.total } / active.size
    val surf = surfaceColor(0.1f)

    // A strip of the last 7 days: pick one to see it. Each shows its own total as a small bar.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        data.week.forEachIndexed { i, d ->
            val on = i == idx
            val fg = if (on) pal.ink else pal.onGround
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (on) pal.tiles[0] else surf).clickable { setBack(6 - i) }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                BasicText(d.day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()).uppercase(), style = monoStyle(fg.copy(alpha = if (on) 1f else 0.6f), 10, bold = on))
                BasicText("${d.day.dayOfMonth}", style = monoStyle(fg.copy(alpha = if (on) 1f else 0.6f), 10, bold = on))
            }
        }
    }

    val change = if (prev != null && prev.total > 0L && day.total > 0L) ((day.total - prev.total) * 100 / prev.total).toInt() else null
    UseHero(
        state,
        when (back) {
            0 -> "Today so far"
            1 -> "Yesterday"
            else -> day.day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        },
        dur(day.total), "",
        listOfNotNull(
            when {
                change == null -> null
                change == 0 -> "SAME AS DAY BEFORE"
                else -> "${Math.abs(change)}% ${if (change < 0) "LESS" else "MORE"} THAN DAY BEFORE"
            },
            "AVERAGE ${dur(avg).uppercase()}",
        ),
    )

    val maxH = (day.hours.maxOrNull() ?: 1L).coerceAtLeast(1L)
    val peak = day.hours.indices.maxByOrNull { day.hours[it] } ?: 0
    var selH by remember(day.day) { mutableIntStateOf(-1) }
    InfoCard("BY HOUR") {
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                day.hours.forEachIndexed { h, ms ->
                    val lit = if (selH >= 0) h == selH else h == peak
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).clickable { selH = if (selH == h) -1 else h }, contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight((ms.toFloat() / maxH).coerceIn(0.04f, 1f)).clip(RoundedCornerShape(4.dp))
                                .background(
                                    when {
                                        ms <= 0L -> pal.onGround.copy(alpha = 0.1f)
                                        lit -> pal.tiles[0]
                                        else -> pal.tiles[0].copy(alpha = 0.45f)
                                    },
                                ),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(0, 6, 12, 18).forEach { BasicText(hourName(it), style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10)) }
            }
            if (selH >= 0) BasicText("${hourName(selH)} to ${hourName((selH + 1) % 24)}: ${dur(day.hours[selH])}", style = bodyStyle(pal.onGround, 13, medium = true))
            else if (day.hours[peak] > 0L) BasicText("Busiest hour ${hourName(peak)}, ${dur(day.hours[peak])}. Tap a bar to see another.", style = bodyStyle(pal.onGround.copy(alpha = 0.7f), 13))
        }
    }

    val zone = ZoneId.systemDefault()
    val runs = FocusEngine.runs.filter { Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() == day.day }
    val label = { pkg: String -> appName(state, pkg) }
    StatGrid(
        listOf(
            WStat("Pickups", "${day.pickups}", if (day.pickups > 0 && day.total > 0L) "Every ${dur(day.total / day.pickups)} on average" else ""),
            WStat("First on", if (day.firstOn > 0L) timeOf(day.firstOn) else "-", ""),
            WStat("Longest stretch", dur(day.longest), if (day.longestPkg.isNotEmpty()) label(day.longestPkg) else ""),
            WStat("Most used", day.apps.firstOrNull()?.let { label(it.first) } ?: "-", day.apps.firstOrNull()?.let { dur(it.second) } ?: ""),
            WStat("Focus", dur(runs.sumOf { it.minutes } * 60_000L), if (runs.isEmpty()) "No sessions" else "${runs.size} session${if (runs.size == 1) "" else "s"}"),
            WStat("Apps used", "${day.apps.size}", ""),
        ),
    )
    Categories(state, day.categories)

    var all by remember(day.day) { mutableStateOf(false) }
    InfoCard("APPS") {
        val top = day.apps.firstOrNull()?.second ?: 1L
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ShareBar(state, day.apps, day.total)
            if (day.apps.isEmpty()) BasicText("Nothing recorded for this day.", style = bodyStyle(pal.onGround.copy(alpha = 0.7f), 14), modifier = Modifier.padding(horizontal = 14.dp))
            (if (all) day.apps else day.apps.take(8)).forEach { (p, ms) ->
                AppRow(state, data, p, ms, top, "${ms * 100 / day.total.coerceAtLeast(1L)}%") { openApp(p) }
            }
            if (day.apps.size > 8) {
                BasicText(
                    if (all) "SHOW LESS" else "SHOW ALL ${day.apps.size}",
                    style = monoStyle(pal.tiles[0], 11, bold = true),
                    modifier = Modifier.padding(horizontal = 14.dp).clickable { all = !all },
                )
            }
        }
    }
}

@Composable
private fun WeekTab(state: HomeState, data: STData, openDay: (Int) -> Unit, openApp: (String) -> Unit) {
    val pal = state.palette
    val week = data.week
    val active = week.filter { it.total > 0L }
    val total = week.sumOf { it.total }
    val avg = if (active.isEmpty()) 0L else total / active.size
    UseHero(state, "Daily average", dur(avg), "${dur(total)} in the last 7 days")

    val maxDay = (week.maxOfOrNull { it.total } ?: 1L).coerceAtLeast(1L)
    InfoCard("LAST 7 DAYS") {
      Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
        // The dashed-look line marks the daily average.
        if (avg > 0L) Box(Modifier.align(Alignment.BottomStart).padding(bottom = (24 + 110 * (avg.toFloat() / maxDay).coerceIn(0f, 1f)).dp).fillMaxWidth().height(1.dp).background(pal.onGround.copy(alpha = 0.35f)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            week.forEachIndexed { i, d ->
                val today = i == week.lastIndex
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { openDay(6 - i) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    BasicText(if (d.total > 0L) dur(d.total).replace(" ", "") else "", style = monoStyle(pal.onGround.copy(alpha = 0.7f), 9), maxLines = 1)
                    Box(Modifier.fillMaxWidth().height(110.dp), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier.fillMaxWidth().fillMaxHeight((d.total.toFloat() / maxDay).coerceIn(0.04f, 1f)).clip(RoundedCornerShape(6.dp))
                                .background(if (today) pal.tiles[0] else pal.tiles[0].copy(alpha = 0.45f)),
                        )
                    }
                    BasicText(
                        d.day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()).uppercase(),
                        style = monoStyle(if (today) pal.onGround else pal.onGround.copy(alpha = 0.55f), 11, bold = today),
                    )
                }
            }
        }
      }
    }

    val busiest = active.maxByOrNull { it.total }
    val lightest = active.filter { it.day != week.last().day }.minByOrNull { it.total }
    val dayName = { d: DayUsage -> d.day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) }
    val pickups = week.sumOf { it.pickups }
    StatGrid(
        listOf(
            WStat("Busiest day", busiest?.let { dur(it.total) } ?: "-", busiest?.let { dayName(it) } ?: ""),
            WStat("Lightest day", lightest?.let { dur(it.total) } ?: "-", lightest?.let { dayName(it) } ?: ""),
            WStat("Pickups", "$pickups", if (active.isNotEmpty()) "About ${pickups / active.size} a day" else ""),
            WStat("Apps used", "${sumApps(week).size}", "This week"),
        ),
    )
    Categories(state, sumCategories(week))

    val apps = sumApps(week)
    InfoCard("MOST USED THIS WEEK") {
        val top = apps.firstOrNull()?.second ?: 1L
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (apps.isEmpty()) BasicText("Nothing recorded yet.", style = bodyStyle(pal.onGround.copy(alpha = 0.7f), 14), modifier = Modifier.padding(horizontal = 14.dp))
            apps.take(10).forEach { (p, ms) -> AppRow(state, data, p, ms, top, "${dur(ms / 7)} a day") { openApp(p) } }
        }
    }
}

@Composable
private fun LimitsTab(state: HomeState, data: STData, add: () -> Unit, openApp: (String) -> Unit) {
    val pal = state.palette
    val ctx = LocalContext.current
    val limits = AppLimits.all(ctx).toList().filter { (p, _) -> UsageRepo.installed(ctx.packageManager, p) }.sortedBy { (p, _) -> state.apps.firstOrNull { it.pkg == p }?.label ?: p }
    val locked = limits.count { (p, _) -> AppLimits.isLocked(ctx, p) }
    PageHero(
        "APP LIMITS", if (limits.isEmpty()) "None set" else "${limits.size} with a limit",
        if (locked > 0) "$locked locked right now." else "An app locks when its daily time is used up.",
        pal.tiles[2 % pal.tiles.size],
    )
    Group(if (limits.isEmpty()) null else "LIMITED APPS", hint = if (limits.isEmpty()) "No limits yet. Add one here, or long-press an app in the drawer. A thin bar along the top of the screen fills as you use it, and the app locks when it is full." else "A locked app stays locked until you turn it back on, remove its limit, or tomorrow.") {
        limits.forEach { (pkg, l) ->
            val info = state.apps.firstOrNull { it.pkg == pkg }
            val used = data.used[pkg] ?: 0L
            val isLocked = AppLimits.isLocked(ctx, pkg)
            ListRow(
                info?.label ?: pkg,
                if (isLocked) "Locked, ${mins(l.min)} used up" else "${dur(used)} of ${mins(l.min)} today",
                onClick = { openApp(pkg) },
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isLocked) Pill("TURN ON", pal.tiles[0], pal.ink, height = 28) { AppLimits.unlock(ctx, pkg) }
                        Pill("REMOVE", pal.onGround.copy(alpha = 0.14f), pal.onGround, height = 28) { AppLimits.set(ctx, pkg, 0) }
                    }
                },
            )
        }
        ListRow("Add a limit", "", "plus", pal.tiles[0], onClick = add)
    }
    Group("INDICATOR", hint = "Shown while a limited app is open, in your theme colour. A ring fills around the front camera, sized to it, and a line fills along the top.") {
        Dropdown(listOf("line" to "LINE", "ring" to "RING"), state.prefs.limitStyle, "Style") { state.prefs.limitStyle = it }
    }
    Group {
        ListRow("Usage access", "Counts the time each app is open", "device", pal.tiles[2 % pal.tiles.size], onClick = {
            try { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
        }, trailing = { BasicText(if (state.usageGranted) "ON" else "OFF", style = monoStyle(if (state.usageGranted) pal.tiles[0] else pal.onGround.copy(alpha = 0.5f), 11, bold = true)) })
    }
}

@Composable
private fun AppLimitSheet(state: HomeState, data: STData, pkg: String, onDismiss: () -> Unit) {
    val pal = state.palette
    val ctx = LocalContext.current
    val info = state.apps.firstOrNull { it.pkg == pkg }
    val limit: AppLimit? = AppLimits.all(ctx)[pkg]
    val todayMs = data.week.lastOrNull()?.apps?.firstOrNull { it.first == pkg }?.second ?: 0L
    val weekMs = sumApps(data.week).firstOrNull { it.first == pkg }?.second ?: 0L
    Sheet(
        onDismiss = onDismiss,
        header = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(state.icons, info, 46.dp, bg = pal.tiles[0], ink = pal.ink, line = pal.onGround)
                Column {
                    BasicText(info?.label ?: pkg, style = bodyStyle(pal.onGround, 18, medium = true), maxLines = 1)
                    BasicText("Today ${dur(todayMs)}  ·  ${dur(weekMs / 7)} a day this week", style = bodyStyle(pal.onGround.copy(alpha = 0.65f), 13), maxLines = 1)
                }
            }
        },
    ) {
        BasicText("DAILY LIMIT", style = monoStyle(pal.onGround.copy(alpha = 0.6f), 10))
        AppLimits.presets.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { m ->
                    val on = limit?.min == m
                    Pill(mins(m), if (on) pal.tiles[0] else pal.onGround.copy(alpha = 0.14f), if (on) pal.ink else pal.onGround, Modifier.weight(1f)) { AppLimits.set(ctx, pkg, m) }
                }
            }
        }
        if (limit != null) ListRow("Remove limit", "", "trash", pal.tiles[2 % pal.tiles.size], onClick = { AppLimits.set(ctx, pkg, 0) })
        if (info != null) ListRow("Open app", "", "arrow", pal.tiles[1 % pal.tiles.size], onClick = { if (state.launch(info.key)) onDismiss() })
    }
}

@Composable
private fun PickAppSheet(state: HomeState, data: STData, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val pal = state.palette
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    val limited = AppLimits.all(ctx).keys
    val list = remember(query, data) {
        val q = query.trim()
        if (q.isEmpty()) sumApps(data.week).map { it.first }.filter { it !in limited }.take(25)
        else state.apps.filter { it.label.contains(q, ignoreCase = true) && it.pkg !in limited }.map { it.pkg }.take(25)
    }
    Sheet(
        onDismiss = onDismiss,
        header = {
            BasicText("ADD A LIMIT", style = monoStyle(pal.onGround.copy(alpha = 0.6f), 10))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(pal.onGround.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph("search", pal.onGround.copy(alpha = 0.6f), 18.dp, 2f)
                Spacer(Modifier.width(10.dp))
                Field(query, { query = it }, "Search apps", Modifier.weight(1f))
            }
        },
    ) {
        if (query.isBlank() && list.isNotEmpty()) BasicText("MOST USED", style = monoStyle(pal.onGround.copy(alpha = 0.6f), 10))
        list.forEach { p ->
            val info = state.apps.firstOrNull { it.pkg == p }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPick(p) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppIcon(state.icons, info, 38.dp, bg = pal.tiles[0], ink = pal.ink, line = pal.onGround)
                BasicText(info?.label ?: p, style = bodyStyle(pal.onGround, 15, medium = true), maxLines = 1)
            }
        }
        if (list.isEmpty()) BasicText("No apps found.", style = bodyStyle(pal.onGround.copy(alpha = 0.7f), 14))
    }
}
