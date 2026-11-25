package app.koma.launcher.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/** What one app's drawer pop-up shows: its own shortcuts, and other apps the person added. */
class PopupCfg(val shortcuts: Boolean = true, val apps: List<String> = emptyList())

/** The saved pop-up settings of every app, as {"package": {"sc": true, "apps": ["key"]}}. */
object Popups {
    fun get(raw: String, pkg: String): PopupCfg = try {
        val o = JSONObject(raw).optJSONObject(pkg)
        if (o == null) PopupCfg() else PopupCfg(
            o.optBoolean("sc", true),
            o.optJSONArray("apps")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
        )
    } catch (e: Exception) {
        PopupCfg()
    }

    fun set(raw: String, pkg: String, cfg: PopupCfg): String {
        val o = try { JSONObject(raw) } catch (e: Exception) { JSONObject() }
        o.put(pkg, JSONObject().put("sc", cfg.shortcuts).put("apps", JSONArray(cfg.apps)))
        return o.toString()
    }
}

/** Shortcuts and usage numbers for the drawer pop-up and its long-press menu. Everything fails quietly. */
object AppMenu {
    fun shortcuts(ctx: Context, pkg: String): List<ShortcutInfo> = try {
        val la = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        if (!la.hasShortcutHostPermission()) emptyList()
        else {
            val q = LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
            la.getShortcuts(q, Process.myUserHandle()).orEmpty().filter { it.isEnabled }.sortedBy { it.rank }.take(4)
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun label(s: ShortcutInfo): String = (s.shortLabel ?: s.longLabel)?.toString().orEmpty()

    fun start(ctx: Context, s: ShortcutInfo) {
        try {
            (ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps).startShortcut(s, null, null)
        } catch (e: Exception) {
        }
    }

    /** Foreground minutes of one app: today, and the last 7 days together. (-1, -1) without usage access. */
    fun minutes(ctx: Context, pkg: String): Pair<Int, Int> {
        if (!Device.usageGranted(ctx)) return -1 to -1
        return try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val zone = ZoneId.systemDefault()
            val start = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
            val now = System.currentTimeMillis()
            val today = Device.appUsage(ctx, start, now)[pkg] ?: 0L
            val week = Device.appUsage(ctx, now - 7L * 24 * 3600 * 1000, now)[pkg] ?: 0L
            (today / 60_000L).toInt() to (week / 60_000L).toInt()
        } catch (e: Exception) {
            -1 to -1
        }
    }
}
