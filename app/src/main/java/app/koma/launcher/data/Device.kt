package app.koma.launcher.data

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId

data class Person(val id: Long, val name: String, val lookup: String)

data class CallRow(val name: String, val number: String, val type: Int, val date: Long)

data class DeviceInfo(val battery: Int, val charging: Boolean, val storagePct: Int, val memPct: Int, val network: String)

/** Contacts, call log, device status and usage time. Each one needs its own permission and fails quietly without it. */
object Device {
    private fun has(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun contactsGranted(ctx: Context) = has(ctx, Manifest.permission.READ_CONTACTS)
    fun callsGranted(ctx: Context) = has(ctx, Manifest.permission.READ_CALL_LOG)

    fun starred(ctx: Context, limit: Int = 6): List<Person> {
        if (!contactsGranted(ctx)) return emptyList()
        val out = ArrayList<Person>()
        try {
            ctx.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.LOOKUP_KEY),
                "${ContactsContract.Contacts.STARRED}=1", null, "${ContactsContract.Contacts.DISPLAY_NAME} ASC",
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) out.add(Person(c.getLong(0), c.getString(1) ?: "?", c.getString(2) ?: ""))
            }
        } catch (e: SecurityException) {
        }
        return out
    }

    fun phoneOf(ctx: Context, contactId: Long): String? {
        if (!contactsGranted(ctx)) return null
        return try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?", arrayOf(contactId.toString()), null,
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: SecurityException) {
            null
        }
    }

    fun dial(ctx: Context, number: String) {
        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun recentCalls(ctx: Context, limit: Int = 4): List<CallRow> {
        if (!callsGranted(ctx)) return emptyList()
        val out = ArrayList<CallRow>()
        try {
            ctx.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.CACHED_NAME, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE),
                null, null, "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val number = c.getString(1) ?: ""
                    out.add(CallRow(c.getString(0) ?: number.ifEmpty { "Unknown" }, number, c.getInt(2), c.getLong(3)))
                }
            }
        } catch (e: SecurityException) {
        }
        return out
    }

    fun status(ctx: Context): DeviceInfo {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra("level", 0) ?: 0
        val scale = i?.getIntExtra("scale", 100) ?: 100
        val st = i?.getIntExtra("status", 0) ?: 0
        val battery = if (scale > 0) level * 100 / scale else 0
        val charging = st == 2 || st == 5
        val fs = StatFs(Environment.getDataDirectory().path)
        val storage = if (fs.totalBytes > 0) (100 - fs.availableBytes * 100 / fs.totalBytes).toInt() else 0
        val mi = ActivityManager.MemoryInfo()
        ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        val mem = if (mi.totalMem > 0) (100 - mi.availMem * 100 / mi.totalMem).toInt() else 0
        var net = "OFFLINE"
        try {
            val cm = ctx.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            net = when {
                caps == null -> "OFFLINE"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WI-FI"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "MOBILE"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
                else -> "ONLINE"
            }
        } catch (e: Exception) {
        }
        return DeviceInfo(battery, charging, storage, mem, net)
    }

    // ---- toggles --------------------------------------------------------------------------------

    fun wifiOn(ctx: Context): Boolean = try {
        (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
    } catch (e: Exception) {
        false
    }

    fun dndOn(ctx: Context) =
        ctx.getSystemService(NotificationManager::class.java).currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE

    fun setDnd(ctx: Context, on: Boolean) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_NONE else NotificationManager.INTERRUPTION_FILTER_ALL)
        } else {
            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openSettings(ctx: Context, action: String) {
        try {
            ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
        }
    }

    // ---- usage time -----------------------------------------------------------------------------

    @Suppress("DEPRECATION")
    fun usageGranted(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Milliseconds each app was in front between [start] and [end], from usage events, with nothing counted while the screen is off. */
    fun appUsage(ctx: Context, start: Long, end: Long): Map<String, Long> {
        val out = HashMap<String, Long>()
        try {
            UsageEngine.run(ctx, start, end, live = end >= System.currentTimeMillis() - 5_000L, kind = { _, _ -> UsageEngine.COUNT }) { pkg, s, e ->
                out[pkg] = (out[pkg] ?: 0L) + (e - s)
            }
        } catch (ex: Exception) {
        }
        return out
    }

    /** Today's foreground time per app (launchable apps only, not the home screen), longest first. Apps that were removed are left out. */
    fun todayApps(ctx: Context): List<Pair<String, Long>> {
        if (!usageGranted(ctx)) return emptyList()
        return UsageRepo.day(ctx, LocalDate.now()).apps
    }

    /**
     * Foreground minutes for today and the 6 days before it, oldest first. Counted from the usage events the way the
     * phone's own screen time is, by the same code the Screen time app uses, so the tile and the app always agree.
     */
    fun screenMinutes(ctx: Context): List<Int> {
        if (!usageGranted(ctx)) return emptyList()
        return UsageRepo.week(ctx).map { (it.total / 60_000L).toInt() }
    }
}

/** Web search engines for the search tile and drawer. */
object Engines {
    val all = listOf(
        "google" to ("Google" to "https://www.google.com/search?q=%s"),
        "ddg" to ("DuckDuckGo" to "https://duckduckgo.com/?q=%s"),
        "brave" to ("Brave" to "https://search.brave.com/search?q=%s"),
        "bing" to ("Bing" to "https://www.bing.com/search?q=%s"),
        "custom" to ("Custom" to ""),
    )

    fun url(prefs: Prefs, query: String): String {
        var template = if (prefs.engine == "custom") prefs.customEngine.trim() else all.firstOrNull { it.first == prefs.engine }?.second?.second ?: all[0].second.second
        // A custom address that is empty or has no %s would search for nothing, so Google answers instead.
        if (template.isEmpty() || !template.contains("%s")) template = all[0].second.second
        return template.replace("%s", Uri.encode(query))
    }

    fun search(ctx: Context, prefs: Prefs, query: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url(prefs, query))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
        }
    }
}
