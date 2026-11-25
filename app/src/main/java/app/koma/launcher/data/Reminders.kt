package app.koma.launcher.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Sets an exact alarm that also fires in Doze, falling back to an inexact one if exact alarms are refused. */
internal fun armExact(ctx: Context, at: Long, pi: PendingIntent) {
    val am = ctx.getSystemService(AlarmManager::class.java)
    try {
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    } catch (e: SecurityException) {
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }
}

/**
 * Reminders are tasks with a date, a time and a "remind me" setting. One alarm is kept for the soonest of them; when it
 * goes off the due ones are posted as notifications (Done, Snooze) and the alarm moves on to the next.
 */
object ReminderCtl {
    const val ACTION_FIRE = "app.koma.launcher.REMINDER_FIRE"
    const val ACTION_DONE = "app.koma.launcher.REMINDER_DONE"
    const val ACTION_SNOOZE = "app.koma.launcher.REMINDER_SNOOZE"
    const val ACTION_AGAIN = "app.koma.launcher.REMINDER_AGAIN"
    private const val FIRE_CODE = 7400
    private const val SNOOZE_MS = 10 * 60_000L
    /** How far back a missed reminder (phone off, Doze) is still worth showing. */
    private const val LATE_MS = 10 * 60_000L

    /** Set by the open launcher so its lists follow changes made from a notification. */
    @Volatile var onChanged: (() -> Unit)? = null

    private fun pi(ctx: Context, action: String, id: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, AlarmReceiver::class.java).setAction(action).putExtra("id", id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun sp(ctx: Context) = ctx.getSharedPreferences("koma", Context.MODE_PRIVATE)

    private fun pattern(ctx: Context) = if (TimeFmt.is24Saved(ctx)) "HH:mm" else "h:mm a"

    /** Arms the alarm for the next reminder, or clears it when there is none. Call after any task changes. */
    fun schedule(ctx: Context) {
        try {
            val now = System.currentTimeMillis()
            val next = Library(ctx).loadTasks().filter { !it.done }.mapNotNull { Tasks.trigger(it) }.filter { it > now }.minOrNull()
            val p = pi(ctx, ACTION_FIRE, "", FIRE_CODE)
            ctx.getSystemService(AlarmManager::class.java).cancel(p)
            if (next != null) armExact(ctx, next, p)
        } catch (e: Exception) {
        }
    }

    /** Posts every reminder that has come due since the last one, then arms the next. */
    fun fire(ctx: Context) {
        val now = System.currentTimeMillis()
        val from = maxOf(sp(ctx).getLong("rem_last", now - 60_000L), now - LATE_MS)
        Library(ctx).loadTasks().filter { !it.done }.forEach { t ->
            val at = Tasks.trigger(t) ?: return@forEach
            if (at > from && at <= now + 2000L) ClockNotif.reminder(ctx, t, Tasks.whenText(t, pattern(ctx), true))
        }
        sp(ctx).edit().putLong("rem_last", now + 2000L).apply()
        schedule(ctx)
    }

    /** Done from the notification: ticks the task (a repeating one moves on) and tells the open launcher. */
    fun done(ctx: Context, id: String) {
        val lib = Library(ctx)
        lib.saveTasks(Tasks.toggled(lib.loadTasks(), id))
        ClockNotif.reminderGone(ctx, id)
        schedule(ctx)
        onChanged?.invoke()
    }

    /** Hides the notification and brings it back in ten minutes. */
    fun snooze(ctx: Context, id: String) {
        ClockNotif.reminderGone(ctx, id)
        armExact(ctx, System.currentTimeMillis() + SNOOZE_MS, pi(ctx, ACTION_AGAIN, id, ClockNotif.reminderId(id)))
    }

    /** The snooze is over: show it again if the task is still open. */
    fun again(ctx: Context, id: String) {
        val t = Library(ctx).loadTasks().firstOrNull { it.id == id && !it.done } ?: return
        ClockNotif.reminder(ctx, t, Tasks.whenText(t, pattern(ctx), true))
    }
}
