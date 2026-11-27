package app.koma.launcher.data

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/** One alarm. [days] is a bit mask, Monday = 1 ... Sunday = 64; 0 means it rings once. */
data class Alarm(
    val id: String,
    val hour: Int,
    val minute: Int,
    val days: Int = 0,
    val label: String = "",
    val on: Boolean = true,
    val group: String = "",
    val snooze: Int = 10,
    /** An occurrence the user skipped (epoch ms): the alarm ignores everything up to this moment. */
    val skip: Long = 0L,
)

data class AlarmGroup(val id: String, val name: String)

object ClockStore {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("koma", Context.MODE_PRIVATE)

    fun alarms(ctx: Context): List<Alarm> = try {
        val a = JSONArray(sp(ctx).getString("alarms", "[]"))
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Alarm(
                o.getString("id"), o.optInt("h"), o.optInt("m"), o.optInt("days"), o.optString("label"),
                o.optBoolean("on", true), o.optString("group"), o.optInt("snooze", 10), o.optLong("skip", 0L),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** How many times the alarm that is ringing now has been snoozed. */
    fun snoozeCount(ctx: Context, id: String): Int = sp(ctx).getInt("snz_$id", 0)

    fun setSnoozeCount(ctx: Context, id: String, n: Int) {
        sp(ctx).edit().putInt("snz_$id", n).apply()
    }

    fun saveAlarms(ctx: Context, list: List<Alarm>) {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject().put("id", it.id).put("h", it.hour).put("m", it.minute).put("days", it.days)
                    .put("label", it.label).put("on", it.on).put("group", it.group).put("snooze", it.snooze).put("skip", it.skip),
            )
        }
        sp(ctx).edit().putString("alarms", a.toString()).apply()
    }

    fun groups(ctx: Context): List<AlarmGroup> = try {
        val a = JSONArray(sp(ctx).getString("alarm_groups", "[]"))
        List(a.length()) { i -> a.getJSONObject(i).let { AlarmGroup(it.getString("id"), it.optString("name")) } }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveGroups(ctx: Context, list: List<AlarmGroup>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name)) }
        sp(ctx).edit().putString("alarm_groups", a.toString()).apply()
    }

    fun zones(ctx: Context): List<String> = try {
        val a = JSONArray(sp(ctx).getString("world_zones", "[]"))
        List(a.length()) { a.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveZones(ctx: Context, list: List<String>) {
        sp(ctx).edit().putString("world_zones", JSONArray(list).toString()).apply()
    }
}

object ClockMath {
    val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")

    /** "6:30" and "AM" (or "06:30" and "" on a 24 hour clock). */
    fun parts(h: Int, m: Int, is24: Boolean): Pair<String, String> =
        if (is24) "%02d:%02d".format(h, m) to "" else "%d:%02d".format(if (h % 12 == 0) 12 else h % 12, m) to if (h < 12) "AM" else "PM"

    /** "6:30 AM", or "06:30" on a 24 hour clock. */
    fun text(h: Int, m: Int, is24: Boolean): String = parts(h, m, is24).let { (t, s) -> if (s.isEmpty()) t else "$t $s" }

    /** How long until [to]: "2d 3h", "1h 5m" or "4m", rounding up to the minute. */
    fun until(from: ZonedDateTime, to: ZonedDateTime): String {
        val mins = java.time.Duration.between(from, to).toMinutes().coerceAtLeast(0) + 1
        val d = mins / 1440
        val h = (mins % 1440) / 60
        val m = mins % 60
        return when {
            d > 0 -> "${d}d ${h}h"
            h > 0 -> "${h}h ${m}m"
            else -> "${m}m"
        }
    }

    /** "ONCE", "WEEKDAYS", "WEEKENDS", [daily], or the days that are on, named by [names]. */
    fun daysText(days: Int, names: List<String>, daily: String = "EVERY DAY"): String = when (days) {
        0 -> "ONCE"
        127 -> daily
        31 -> "WEEKDAYS"
        96 -> "WEEKENDS"
        else -> names.filterIndexed { i, _ -> days and (1 shl i) != 0 }.joinToString(" ")
    }

    /** The next moment [a] rings after [from], or null when it never will. */
    fun next(a: Alarm, start: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        val from = if (a.skip > start.toInstant().toEpochMilli()) java.time.Instant.ofEpochMilli(a.skip).atZone(start.zone).plusMinutes(1) else start
        for (i in 0..8) {
            val d = from.toLocalDate().plusDays(i.toLong())
            val at = d.atTime(a.hour, a.minute).atZone(from.zone)
            val dayOk = a.days == 0 || a.days and (1 shl (d.dayOfWeek.value - 1)) != 0
            if (dayOk && at.isAfter(from)) return at
        }
        return null
    }

    fun cityName(zone: String) = zone.substringAfterLast('/').replace('_', ' ')
}

/** Everything the Clock app keeps: alarms, alarm groups and world clock cities. Saves on every change. */
class ClockModel(private val ctx: Context) {
    init {
        live = this
    }

    companion object {
        /** The model the open launcher uses, so receivers can tell it that an alarm changed. */
        @Volatile var live: ClockModel? = null
    }

    var alarms by mutableStateOf(ClockStore.alarms(ctx))
        private set
    var groups by mutableStateOf(ClockStore.groups(ctx))
        private set
    var zones by mutableStateOf(ClockStore.zones(ctx))
        private set

    /** Re-reads the alarms, groups and world clocks: a one-time alarm switches itself off while Koma is closed, and another window may have changed them. */
    fun refresh() {
        alarms = ClockStore.alarms(ctx)
        groups = ClockStore.groups(ctx)
        zones = ClockStore.zones(ctx)
    }

    private fun commit(list: List<Alarm>) {
        alarms = list
        ClockStore.saveAlarms(ctx, list)
        list.forEach { if (it.on) AlarmCtl.schedule(ctx, it) else AlarmCtl.cancel(ctx, it.id) }
        ClockNotif.refreshUpcoming(ctx)
    }

    fun newId() = UUID.randomUUID().toString()

    fun save(a: Alarm) {
        val exists = alarms.any { it.id == a.id }
        commit(if (exists) alarms.map { if (it.id == a.id) a else it } else alarms + a)
    }

    fun delete(id: String) {
        AlarmCtl.cancel(ctx, id)
        alarms = alarms.filterNot { it.id == id }
        ClockStore.saveAlarms(ctx, alarms)
        ClockNotif.refreshUpcoming(ctx)
    }

    fun deleteMany(ids: Set<String>) {
        ids.forEach { AlarmCtl.cancel(ctx, it) }
        alarms = alarms.filterNot { it.id in ids }
        ClockStore.saveAlarms(ctx, alarms)
        ClockNotif.refreshUpcoming(ctx)
    }

    fun setOn(id: String, on: Boolean) = commit(alarms.map { if (it.id == id) it.copy(on = on) else it })

    fun setAll(ids: Set<String>, on: Boolean) = commit(alarms.map { if (it.id in ids) it.copy(on = on) else it })

    fun addGroup(name: String): String {
        val g = AlarmGroup(newId(), name.trim().ifEmpty { "Group" })
        groups = groups + g
        ClockStore.saveGroups(ctx, groups)
        return g.id
    }

    fun renameGroup(id: String, name: String) {
        groups = groups.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }) else it }
        ClockStore.saveGroups(ctx, groups)
    }

    /** Removes the group; its alarms stay, just ungrouped. */
    fun deleteGroup(id: String) {
        groups = groups.filterNot { it.id == id }
        ClockStore.saveGroups(ctx, groups)
        commit(alarms.map { if (it.group == id) it.copy(group = "") else it })
    }

    fun addZone(id: String) {
        if (id in zones) return
        zones = zones + id
        ClockStore.saveZones(ctx, zones)
    }

    fun removeZone(id: String) {
        zones = zones.filterNot { it == id }
        ClockStore.saveZones(ctx, zones)
    }

    /** The alarm that rings soonest, with when. */
    fun upcoming(): Pair<Alarm, ZonedDateTime>? =
        alarms.filter { it.on }.mapNotNull { a -> ClockMath.next(a)?.let { a to it } }.minByOrNull { it.second }
}

// ---- ringing -----------------------------------------------------------------------------------

/** 12 or 24 hour time for the whole launcher. "system" follows the phone. */
object TimeFmt {
    var mode by mutableStateOf("system")
    private var appCtx: Context? = null

    /** Bumps when the phone's 12/24 hour setting changes, so everything that shows a time redraws. */
    private var tick by mutableStateOf(0)

    fun init(ctx: Context, saved: String) {
        val c = ctx.applicationContext
        if (appCtx == null) {
            appCtx = c
            try {
                c.contentResolver.registerContentObserver(
                    android.provider.Settings.System.CONTENT_URI, true,
                    object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) { tick++ }
                    },
                )
            } catch (e: Exception) {
            }
        }
        mode = saved
    }

    /** Read fresh each time, so a change in the phone's settings is followed without restarting the launcher. */
    fun is24(): Boolean {
        tick
        return mode == "24" || (mode != "12" && (appCtx?.let { DateFormat.is24HourFormat(it) } ?: false))
    }

    /** For receivers, which can run before the launcher has started. */
    fun is24Saved(ctx: Context): Boolean {
        val m = ctx.getSharedPreferences("koma", Context.MODE_PRIVATE).getString("clock_hour", "system")
        return m == "24" || (m != "12" && DateFormat.is24HourFormat(ctx))
    }

    fun pattern(): String = if (is24()) "HH:mm" else "h:mm a"
}

/** Ringtone choices. "" is the phone's alarm tone, "none" is silent, anything else is a tone's address. */
object RingPrefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("koma", Context.MODE_PRIVATE)
    fun tone(ctx: Context, timer: Boolean): String = sp(ctx).getString(if (timer) "timer_tone" else "alarm_tone", "") ?: ""
    fun vibrate(ctx: Context): Boolean = sp(ctx).getBoolean("ring_vibrate", true)
    fun uri(v: String): Uri? = when (v) {
        "none" -> null
        "" -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        else -> Uri.parse(v)
    }
    fun name(ctx: Context, v: String): String = when (v) {
        "" -> "Phone's alarm tone"
        "none" -> "Silent"
        else -> try {
            RingtoneManager.getRingtone(ctx, Uri.parse(v))?.getTitle(ctx) ?: "Custom"
        } catch (e: Exception) {
            "Custom"
        }
    }
}

object Ring {
    const val ACTION_RING = "app.koma.launcher.ALARM_RING"
    const val ACTION_PRE = "app.koma.launcher.ALARM_PRE"
    const val ACTION_SKIP = "app.koma.launcher.ALARM_SKIP"
    const val ACTION_UNSNOOZE = "app.koma.launcher.ALARM_UNSNOOZE"
    const val ACTION_TIMER_ADD = "app.koma.launcher.TIMER_ADD"
    const val ACTION_TIMER_CANCEL = "app.koma.launcher.TIMER_CANCEL"
    const val ACTION_TIMER_PAUSE = "app.koma.launcher.TIMER_PAUSE"
    const val ACTION_TIMER_RESUME = "app.koma.launcher.TIMER_RESUME"
}

/** Handles the alarm clock going off, and the buttons on the clock notifications. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: ""
        when (intent.action) {
            Ring.ACTION_RING -> {
                RingLog.add(ctx, "alarm clock fired for $id")
                try {
                    ctx.getSystemService(android.os.PowerManager::class.java)
                        .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "koma:alarm").acquire(15_000L)
                } catch (e: Exception) {
                }
                val at = System.currentTimeMillis()
                val stamp = ClockNotif.parts(ctx, at)
                val snoozeMin = ctx.getSharedPreferences("koma", Context.MODE_PRIVATE).getInt("default_snooze", 10)
                val snoozeMax = ctx.getSharedPreferences("koma", Context.MODE_PRIVATE).getInt("snooze_max", 3)
                // The snooze is over: its "snoozed" notification must go, whether or not this ring is answered.
                ClockNotif.clearSnoozed(ctx, id)
                if (intent.getIntExtra("code", 0) == 0) ClockStore.setSnoozeCount(ctx, id, 0)
                val used = ClockStore.snoozeCount(ctx, id)
                val left = (snoozeMax - used).coerceAtLeast(0)
                if (id == "__test") {
                    ClockNotif.ring(ctx, RingInfo("alarm", id, "Test alarm", stamp.first, stamp.second, if (left > 0) snoozeMin else 0, left), goAsync())
                    return
                }
                val a = ClockStore.alarms(ctx).firstOrNull { it.id == id }
                if (a == null) {
                    RingLog.add(ctx, "that alarm no longer exists")
                    return
                }
                ClockNotif.ring(ctx, RingInfo("alarm", id, a.label.ifBlank { "Alarm" }, stamp.first, stamp.second, if (left > 0) snoozeMin else 0, left), goAsync())
                ctx.getSystemService(NotificationManager::class.java).cancel(ClockNotif.UPCOMING_ID)
                if (a.days == 0) {
                    ClockStore.saveAlarms(ctx, ClockStore.alarms(ctx).map { if (it.id == id) it.copy(on = false) else it })
                } else {
                    AlarmCtl.schedule(ctx, a)
                }
                ClockModel.live?.refresh()
                ClockNotif.refreshUpcoming(ctx)
            }
            Ring.ACTION_PRE -> ClockNotif.postUpcoming(ctx)
            Ring.ACTION_SKIP -> {
                // "Turn off" really turns the alarm off, so the switch in the app shows it too.
                if (ClockStore.alarms(ctx).none { it.id == id }) return
                val list = ClockStore.alarms(ctx).map { if (it.id != id) it else it.copy(on = false, skip = 0L) }
                ClockStore.saveAlarms(ctx, list)
                AlarmCtl.cancel(ctx, id)
                ClockModel.live?.refresh()
                ClockNotif.refreshUpcoming(ctx)
            }
            Ring.ACTION_UNSNOOZE -> {
                ClockStore.setSnoozeCount(ctx, id, 0)
                AlarmCtl.cancelSnooze(ctx, id)
                ClockNotif.clearSnoozed(ctx, id)
            }
            Ring.ACTION_TIMER_ADD -> TimerCtl.addMinute(ctx)
            Ring.ACTION_TIMER_CANCEL -> TimerCtl.cancelAndClear(ctx)
            Ring.ACTION_TIMER_PAUSE -> TimerCtl.pause(ctx)
            Ring.ACTION_TIMER_RESUME -> TimerCtl.resume(ctx)
            ReminderCtl.ACTION_FIRE -> ReminderCtl.fire(ctx)
            ReminderCtl.ACTION_DONE -> ReminderCtl.done(ctx, id)
            ReminderCtl.ACTION_SNOOZE -> ReminderCtl.snooze(ctx, id)
            ReminderCtl.ACTION_AGAIN -> ReminderCtl.again(ctx, id)
            Intent.ACTION_BOOT_COMPLETED, "android.intent.action.MY_PACKAGE_REPLACED" -> {
                AlarmCtl.scheduleAll(ctx)
                ClockNotif.refreshUpcoming(ctx)
                ReminderCtl.schedule(ctx)
            }
        }
    }
}

object AlarmCtl {
    private fun pi(ctx: Context, id: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx, id.hashCode() + code, Intent(ctx, AlarmReceiver::class.java).setAction(Ring.ACTION_RING).putExtra("id", id).putExtra("code", code),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Rings at [ms]. Uses the alarm-clock slot, which is exact and needs no special permission. */
    fun at(ctx: Context, id: String, ms: Long, code: Int = 0) {
        RingLog.add(ctx, "alarm set for ${java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalTime().withNano(0)}" + if (id == "__test") " (test)" else "")
        try {
            val show = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
                PendingIntent.getActivity(ctx, 7003, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            } ?: pi(ctx, id, code)
            ctx.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(ms, show), pi(ctx, id, code))
        } catch (e: Exception) {
        }
    }

    fun schedule(ctx: Context, a: Alarm) {
        val next = ClockMath.next(a) ?: return
        at(ctx, a.id, next.toInstant().toEpochMilli())
    }

    fun cancel(ctx: Context, id: String) {
        try {
            val am = ctx.getSystemService(AlarmManager::class.java)
            am.cancel(pi(ctx, id, 0))
            am.cancel(pi(ctx, id, 1))
        } catch (e: Exception) {
        }
        ClockNotif.clearSnoozed(ctx, id)
    }

    fun cancelSnooze(ctx: Context, id: String) {
        try {
            ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx, id, 1))
        } catch (e: Exception) {
        }
    }

    fun scheduleAll(ctx: Context) = ClockStore.alarms(ctx).filter { it.on }.forEach { schedule(ctx, it) }
}
