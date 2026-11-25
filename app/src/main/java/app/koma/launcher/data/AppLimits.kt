package app.koma.launcher.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/**
 * A daily time limit on one app. [lockedDay] is the day (epoch day) the limit was used up, or 0. [since] is when the
 * allowance last started counting: the start of today, or the moment the app was turned back on.
 */
class AppLimit(val min: Int, val lockedDay: Long = 0L, val since: Long = 0L)

/**
 * Daily limits per app. While a limited app is in front, [KomaFocusService] draws a thin bar that fills as the limit
 * is used. When it is full the app is locked until it is turned back on or the limit is removed, or until tomorrow.
 */
object AppLimits {
    val presets = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120)

    @Volatile private var map: Map<String, AppLimit> = emptyMap()
    @Volatile private var loaded = false

    /** Read in a composable so it redraws when a limit changes. */
    var version by mutableIntStateOf(0)
        private set

    private fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        try {
            val text = JsonFile(ctx, "limits.json").read() ?: return
            val o = JSONObject(text)
            map = o.keys().asSequence().associateWith { k ->
                val l = o.getJSONObject(k)
                AppLimit(l.optInt("min"), l.optLong("locked"), l.optLong("since"))
            }
        } catch (e: Exception) {
        }
    }

    private fun save(ctx: Context) {
        try {
            val o = JSONObject()
            map.forEach { (k, l) -> o.put(k, JSONObject().put("min", l.min).put("locked", l.lockedDay).put("since", l.since)) }
            JsonFile(ctx, "limits.json").write(o.toString())
        } catch (e: Exception) {
        }
        version++
    }

    fun all(ctx: Context): Map<String, AppLimit> {
        load(ctx)
        return map
    }

    fun get(ctx: Context, pkg: String): AppLimit? {
        load(ctx)
        return map[pkg]
    }

    /** Sets the daily limit in minutes; 0 removes it and its lock. */
    fun set(ctx: Context, pkg: String, min: Int) {
        load(ctx)
        map = if (min <= 0) map - pkg else map + (pkg to AppLimit(min, 0L, map[pkg]?.since ?: 0L))
        save(ctx)
    }

    private fun today() = LocalDate.now().toEpochDay()

    fun isLocked(ctx: Context, pkg: String): Boolean = get(ctx, pkg)?.lockedDay == today()

    fun lock(ctx: Context, pkg: String) {
        val l = get(ctx, pkg) ?: return
        map = map + (pkg to AppLimit(l.min, today(), l.since))
        save(ctx)
    }

    /** Turns the app back on with a fresh allowance counted from now. */
    fun unlock(ctx: Context, pkg: String) {
        val l = get(ctx, pkg) ?: return
        map = map + (pkg to AppLimit(l.min, 0L, System.currentTimeMillis()))
        save(ctx)
    }

    /** Where today's counting starts for this limit. */
    fun windowStart(l: AppLimit): Long =
        maxOf(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), l.since)

    /** Milliseconds of this app used against its limit. */
    fun usedMs(ctx: Context, pkg: String, l: AppLimit): Long =
        Device.appUsage(ctx, windowStart(l), System.currentTimeMillis())[pkg] ?: 0L
}
