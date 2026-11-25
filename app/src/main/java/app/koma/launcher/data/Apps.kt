package app.koma.launcher.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process

data class AppInfo(val label: String, val pkg: String, val cls: String) {
    val key: String get() = "$pkg/$cls"
}

object Apps {
    private const val SELF = "app.koma.launcher"

    /** Every launchable app, sorted by name, excluding Koma itself. */
    fun load(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != SELF }
            .map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            // Koma's own apps screen shows up as an app, so it is in the drawer, search and recents.
            .plus(AppInfo("Koma apps", SELF, "$SELF.HUB"))
            .sortedBy { it.label.lowercase() }
            // Pinned web apps and other shortcuts go last, so a lookup by package still finds the real app first.
            .plus(pinned(ctx))
    }

    /** The activity name of a pinned shortcut is "shortcut:" plus its id. */
    const val SHORTCUT = "shortcut:"

    fun isShortcut(app: AppInfo): Boolean = app.cls.startsWith(SHORTCUT)

    /**
     * Shortcuts that other apps pinned to Koma, such as a website a browser added with "Install app" or
     * "Add to Home screen". Needs Koma to be the default launcher.
     */
    fun pinned(ctx: Context): List<AppInfo> = try {
        val la = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        if (!la.hasShortcutHostPermission()) emptyList()
        else {
            val q = LauncherApps.ShortcutQuery().setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            la.getShortcuts(q, Process.myUserHandle()).orEmpty()
                .filter { it.isEnabled }
                .map { AppInfo(AppMenu.label(it).ifBlank { it.`package` }, it.`package`, SHORTCUT + it.id) }
                .sortedBy { it.label.lowercase() }
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** Unpins one shortcut from Koma. The app that owns it keeps its other pinned shortcuts. */
    fun unpin(ctx: Context, app: AppInfo) {
        try {
            val la = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val me = Process.myUserHandle()
            val id = app.cls.removePrefix(SHORTCUT)
            val q = LauncherApps.ShortcutQuery().setPackage(app.pkg).setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            val keep = la.getShortcuts(q, me).orEmpty().map { it.id }.filter { it != id }
            la.pinShortcuts(app.pkg, keep, me)
        } catch (e: Exception) {
        }
    }

    /** Starts an app from its "package/activity" key. Fails quietly if it was uninstalled. */
    fun launch(context: Context, key: String) {
        val parts = key.split("/", limit = 2)
        if (parts.size != 2) return
        if (parts[1].startsWith(SHORTCUT)) {
            try {
                (context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps)
                    .startShortcut(parts[0], parts[1].removePrefix(SHORTCUT), null, null, Process.myUserHandle())
            } catch (e: Exception) {
                // The shortcut was removed or its app was uninstalled.
            }
            return
        }
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(parts[0], parts[1]))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // App was removed since the tile was added.
        } catch (e: SecurityException) {
            // Activity is no longer exported.
        }
    }
}

/**
 * How well an app name matches what was typed, shared by the global search and the drawer's search.
 * 0: the name starts with it, 1: a later word starts with it ("Maps" in "Google Maps"), 2: it appears somewhere inside.
 * Typing more letters moves the line: "w" lists the apps starting with w first and the ones merely containing w
 * after them, and "wa" does the same again for "wa".
 */
object AppMatch {
    fun rank(label: String, query: String): Int {
        val q = query.trim()
        if (q.isEmpty()) return 0
        if (label.startsWith(q, ignoreCase = true)) return 0
        var i = label.indexOf(q, ignoreCase = true)
        if (i < 0) return -1
        while (i >= 0) {
            if (i == 0 || !label[i - 1].isLetterOrDigit()) return 1
            i = label.indexOf(q, i + 1, ignoreCase = true)
        }
        return 2
    }

    /** The items whose name matches, best matches first and alphabetical within each kind. */
    fun <T> filter(items: List<T>, query: String, label: (T) -> String): List<T> =
        items.mapNotNull { val r = rank(label(it), query); if (r < 0) null else r to it }
            .sortedWith(compareBy({ it.first }, { label(it.second).lowercase() }))
            .map { it.second }
}

