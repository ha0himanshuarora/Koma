package app.koma.launcher.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.time.LocalDate
import java.time.ZoneId

/**
 * One day of phone use. Counted the way [Device.screenMinutes] counts it: only apps you can launch, not home screens,
 * not Koma, and nothing while the screen is off. [hours] holds the milliseconds in each hour of the day.
 */
class DayUsage(
    val day: LocalDate,
    val total: Long,
    val apps: List<Pair<String, Long>>,
    val hours: LongArray,
    val pickups: Int,
    val firstOn: Long,
    val longest: Long,
    val longestPkg: String,
    val categories: List<Pair<String, Long>>,
)

/** Detailed usage for the Screen time app. Past days never change, so they are kept after the first read. */
object UsageRepo {
    private val past = HashMap<LocalDate, DayUsage>()

    private fun categoryName(c: Int): String = when (c) {
        0 -> "Games"
        1 -> "Music and audio"
        2 -> "Video"
        3 -> "Photos"
        4 -> "Social"
        5 -> "News"
        6 -> "Maps"
        7 -> "Productivity"
        else -> "Other"
    }

    /** Whether an app is still on the phone. A removed app must not keep showing up, or keep its time. */
    fun installed(pm: PackageManager, pkg: String): Boolean = try {
        pm.getApplicationInfo(pkg, 0)
        true
    } catch (ex: Exception) {
        false
    }

    fun day(ctx: Context, d: LocalDate): DayUsage {
        val today = LocalDate.now()
        val pm = ctx.packageManager
        if (d != today) past[d]?.let { cached -> if (cached.apps.all { installed(pm, it.first) }) return cached else past.remove(d) }
        val zone = ZoneId.systemDefault()
        val startZ = d.atStartOfDay(zone)
        val start = startZ.toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val end = minOf(d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), now)
        val bounds = LongArray(25) { startZ.plusHours(it.toLong()).toInstant().toEpochMilli() }
        val perApp = HashMap<String, Long>()
        val hours = LongArray(24)
        var pickups = 0
        var firstOn = 0L
        var longest = 0L
        var longestPkg = ""

        if (end > start) {
            try {
                UsageEngine.run(
                    ctx, start, end, live = end == now, kind = UsageEngine.launcherKinds(ctx),
                    screenOn = { t -> pickups++; if (firstOn == 0L) firstOn = t },
                ) { pkg, s, e ->
                    // A removed app is dropped here, so its time disappears with it.
                    if (!installed(pm, pkg)) return@run
                    perApp[pkg] = (perApp[pkg] ?: 0L) + (e - s)
                    if (e - s > longest) { longest = e - s; longestPkg = pkg }
                    for (h in 0 until 24) {
                        val x = maxOf(s, bounds[h])
                        val y = minOf(e, bounds[h + 1])
                        if (y > x) hours[h] += y - x
                    }
                }
            } catch (ex: Exception) {
            }
        }
        val apps = perApp.filter { it.value >= 30_000L }.toList().sortedByDescending { it.second }
        val cats = HashMap<String, Long>()
        apps.forEach { (p, ms) ->
            val c = try { pm.getApplicationInfo(p, 0).category } catch (ex: Exception) { -1 }
            val n = categoryName(c)
            cats[n] = (cats[n] ?: 0L) + ms
        }
        val out = DayUsage(d, perApp.values.sum(), apps, hours, pickups, firstOn, longest, longestPkg, cats.toList().sortedByDescending { it.second })
        if (d != today) past[d] = out
        return out
    }

    /** Today and the six days before it, oldest first. */
    fun week(ctx: Context): List<DayUsage> {
        val today = LocalDate.now()
        return (6 downTo 0).map { day(ctx, today.minusDays(it.toLong())) }
    }
}


/**
 * Walks the phone's usage events the way the system's own screen time does. An app is in front from the moment one of its
 * screens resumes until its last screen pauses; one app is in front at a time; nothing counts while the screen is off.
 * A pause followed straight away by a resume in the same app (one screen opening the next) is one unbroken stretch.
 * The walk starts a little before [start] so an app already open at midnight is counted from midnight.
 */
internal object UsageEngine {
    private const val LOOKBACK = 2 * 60 * 60 * 1000L
    private const val JOIN_MS = 1000L

    /** What an app coming to the front means: nothing (a system dialog), time to count, or the home screen, which ends the stretch. */
    const val IGNORE = 0
    const val COUNT = 1
    const val END = 2

    /** For screen time: apps you can launch count, home screens end the stretch, Koma's own apps count but its home screen does not. */
    fun launcherKinds(ctx: Context): (String, String) -> Int {
        val pm = ctx.packageManager
        val homes = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { it.activityInfo.packageName }.toSet()
        val cache = HashMap<String, Int>()
        return { pkg, cls ->
            if (pkg == ctx.packageName) {
                if (cls.endsWith("MainActivity")) END else COUNT
            } else cache.getOrPut(pkg) {
                when {
                    pkg in homes -> END
                    pm.getLaunchIntentForPackage(pkg) != null -> COUNT
                    else -> IGNORE
                }
            }
        }
    }

    fun run(
        ctx: Context, start: Long, end: Long, live: Boolean, kind: (String, String) -> Int,
        screenOn: (Long) -> Unit = {}, span: (String, Long, Long) -> Unit,
    ) {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val ev = usm.queryEvents(maxOf(0L, start - LOOKBACK), end)
        val e = UsageEvents.Event()
        val screens = HashMap<String, MutableSet<String>>()
        val since = HashMap<String, Long>()
        var pendPkg: String? = null
        var pendAt = 0L

        fun close(pkg: String, at: Long) {
            val s = since.remove(pkg)
            screens.remove(pkg)
            if (s != null) {
                val a = maxOf(s, start)
                val b = minOf(at, end)
                if (b > a) span(pkg, a, b)
            }
        }
        fun closeAll(at: Long) { screens.keys.toList().forEach { close(it, at) } }

        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            val t = e.timeStamp
            val pkg = e.packageName
            val type = e.eventType
            val pp = pendPkg
            if (pp != null) {
                pendPkg = null
                if (!(type == 1 && pkg == pp && t - pendAt <= JOIN_MS)) close(pp, pendAt)
            }
            when (type) {
                15 -> if (t >= start) screenOn(t)
                16, 26 -> closeAll(t)
                1 -> if (pkg != null) {
                    val cls = e.className ?: ""
                    when (kind(pkg, cls)) {
                        END -> closeAll(t)
                        COUNT -> {
                            if (pkg !in screens) {
                                closeAll(t)
                                screens[pkg] = HashSet()
                                since[pkg] = t
                            }
                            screens[pkg]?.add(cls)
                        }
                    }
                }
                2, 23 -> if (pkg != null) {
                    val set = screens[pkg]
                    if (set != null) {
                        set.remove(e.className ?: "")
                        if (set.isEmpty()) {
                            if (type == 2) { pendPkg = pkg; pendAt = t } else close(pkg, t)
                        }
                    }
                }
            }
        }
        pendPkg?.let { close(it, pendAt) }
        if (live) screens.keys.toList().forEach { close(it, end) }
    }
}
