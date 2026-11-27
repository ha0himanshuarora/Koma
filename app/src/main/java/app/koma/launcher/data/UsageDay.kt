package app.koma.launcher.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
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

    fun day(ctx: Context, d: LocalDate): DayUsage {
        val today = LocalDate.now()
        if (d != today) past[d]?.let { return it }
        val zone = ZoneId.systemDefault()
        val pm = ctx.packageManager
        val homes = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { it.activityInfo.packageName }.toSet()
        val counts = HashMap<String, Boolean>()
        fun counted(pkg: String): Boolean = counts.getOrPut(pkg) {
            pkg != ctx.packageName && pkg !in homes && pm.getLaunchIntentForPackage(pkg) != null
        }
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

        fun span(pkg: String, s: Long, e: Long) {
            if (e <= s) return
            perApp[pkg] = (perApp[pkg] ?: 0L) + (e - s)
            if (e - s > longest) { longest = e - s; longestPkg = pkg }
            for (h in 0 until 24) {
                val a = maxOf(s, bounds[h])
                val b = minOf(e, bounds[h + 1])
                if (b > a) hours[h] += b - a
            }
        }

        if (end > start) {
            try {
                val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val ev = usm.queryEvents(start, end)
                val e = UsageEvents.Event()
                val open = HashMap<String, Long>()
                while (ev.hasNextEvent()) {
                    ev.getNextEvent(e)
                    when (e.eventType) {
                        15 -> { // the screen turned on
                            pickups++
                            if (firstOn == 0L) firstOn = e.timeStamp
                        }
                        16, 26 -> { // screen off, shutdown
                            open.forEach { (p, s) -> span(p, s, e.timeStamp) }
                            open.clear()
                        }
                        1 -> { // an app came to the front: only one is in front at a time, so whatever was open ends here
                            val p = e.packageName
                            if (p != null && (open.size != 1 || p !in open)) {
                                open.forEach { (q, s) -> span(q, s, e.timeStamp) }
                                open.clear()
                                if (counted(p)) open[p] = e.timeStamp
                            }
                        }
                        2 -> { val p = e.packageName; if (p != null) open.remove(p)?.let { span(p, it, e.timeStamp) } }
                    }
                }
                if (end == now) open.forEach { (p, s) -> span(p, s, end) }
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
