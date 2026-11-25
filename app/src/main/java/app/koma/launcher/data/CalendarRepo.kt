package app.koma.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

data class Event(val title: String, val begin: Long, val end: Long, val allDay: Boolean, val cal: Long = 0, val account: String = "")

/** One calendar on the phone (a Google, Samsung, Outlook or holiday calendar). */
data class CalInfo(val id: Long, val name: String, val account: String)

/** Reads events from the phone's calendar store, which includes every synced account. */
object CalendarRepo {
    fun granted(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun calendars(ctx: Context): List<CalInfo> {
        if (!granted(ctx)) return emptyList()
        val out = ArrayList<CalInfo>()
        try {
            ctx.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf("_id", "calendar_displayName", "account_name"),
                null, null, "account_name ASC, calendar_displayName ASC",
            )?.use { c ->
                while (c.moveToNext()) out.add(CalInfo(c.getLong(0), c.getString(1) ?: "Calendar", c.getString(2) ?: ""))
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return out
    }

    /** Upcoming events, skipping calendars listed in [hidden]. */
    fun upcoming(ctx: Context, hidden: Set<Long> = emptySet(), days: Int = 7, limit: Int = 8): List<Event> {
        if (!granted(ctx)) return emptyList()
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - 86_400_000L)
            ContentUris.appendId(it, now + days * 86_400_000L)
        }.build()
        val projection = arrayOf("title", "begin", "end", "allDay", "calendar_id")
        val accounts = calendars(ctx).associate { it.id to it.account }
        val out = ArrayList<Event>()
        try {
            ctx.contentResolver.query(uri, projection, null, null, "begin ASC")?.use { c ->
                while (c.moveToNext()) {
                    val cal = c.getLong(4)
                    if (cal in hidden) continue
                    val allDay = c.getInt(3) == 1
                    var begin = c.getLong(1)
                    var end = c.getLong(2)
                    if (allDay) {
                        // All-day events are stored as UTC midnights; show them on the local day.
                        val zone = ZoneId.systemDefault()
                        begin = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
                        end = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
                    }
                    if (end <= now) continue
                    out.add(Event(c.getString(0) ?: "(No title)", begin, end, allDay, cal, accounts[cal] ?: ""))
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        // The same event often sits in two calendars (a holiday calendar and a synced one). Show it once.
        return out.sortedBy { it.begin }.distinctBy { Triple(it.title.trim().lowercase(), it.begin, it.allDay) }.take(limit)
    }
}
