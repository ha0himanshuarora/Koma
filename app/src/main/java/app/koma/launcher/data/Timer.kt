package app.koma.launcher.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Rings when the timer's time is up, even if Koma is closed or the phone is asleep. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val len = TimerCtl.length(ctx)
        ClockNotif.timerGone(ctx)
        RingLog.add(ctx, "timer fired")
        ClockNotif.ring(ctx, TimerCtl.ringInfo(len), goAsync())
    }
}

object TimerCtl {
    /** What the ring screen shows for a timer that was [len] long: its length as the big number, and a caption. */
    fun ringInfo(len: Long): RingInfo {
        val secs = (len / 1000L).coerceAtLeast(0L)
        val h = secs / 3600L
        val time = if (h > 0) "%d:%02d:%02d".format(h, (secs / 60) % 60, secs % 60) else "%02d:%02d".format(secs / 60, secs % 60)
        val mins = (len / 60000L).toInt()
        val sub = when {
            len <= 0L -> ""
            mins >= 60 && mins % 60 == 0 -> "${mins / 60} HOUR TIMER"
            mins >= 1 && len % 60000L == 0L -> "$mins MIN TIMER"
            else -> "${len / 1000L} SEC TIMER"
        }
        return RingInfo("timer", "timer", "Time's up", time, sub, 0)
    }

    private fun sp(ctx: Context) = ctx.getSharedPreferences("koma", Context.MODE_PRIVATE)

    private fun fire(ctx: Context) =
        PendingIntent.getBroadcast(ctx, 7001, Intent(ctx, TimerReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun length(ctx: Context): Long = sp(ctx).getLong("timer_len", 0L)

    /** Writes the timer's state: through the open launcher if there is one, so its screens follow. */
    fun set(ctx: Context, end: Long, len: Long, left: Long) {
        val live = Prefs.live
        if (live != null) {
            live.timerEnd = end
            live.timerLen = len
            live.timerLeft = left
        } else {
            sp(ctx).edit().putLong("timer_end", end).putLong("timer_len", len).putLong("timer_left", left).apply()
        }
    }

    /** Sets the alarm for [endMs]. Uses the alarm-clock slot, so it is exact and needs no special permission. */
    fun start(ctx: Context, endMs: Long) {
        RingLog.add(ctx, "timer set for ${java.time.Instant.ofEpochMilli(endMs).atZone(java.time.ZoneId.systemDefault()).toLocalTime().withNano(0)}")
        try {
            val show = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
                PendingIntent.getActivity(ctx, 7002, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            } ?: fire(ctx)
            ctx.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(endMs, show), fire(ctx))
        } catch (e: Exception) {
        }
        ClockNotif.timer(ctx, endMs)
    }

    /** Stops the countdown (a pause, or a cancel). Also silences the ringing timer if it is going off. */
    fun cancel(ctx: Context) {
        try {
            ctx.getSystemService(AlarmManager::class.java).cancel(fire(ctx))
        } catch (e: Exception) {
        }
        ClockNotif.timerGone(ctx)
        if (RingService.current.value?.kind == "timer") RingService.send(ctx, RingService.ACTION_DISMISS)
    }

    /** Adds a minute to the running timer, from its notification. */
    fun addMinute(ctx: Context) {
        val s = sp(ctx)
        val end = s.getLong("timer_end", 0L)
        val held = s.getLong("timer_left", 0L)
        if (end <= 0L && held > 0L) {
            // paused: the minute goes on to what is left
            set(ctx, 0L, s.getLong("timer_len", 0L) + 60_000L, held + 60_000L)
            ClockNotif.timerPaused(ctx, held + 60_000L)
            return
        }
        if (end <= System.currentTimeMillis()) return
        set(ctx, end + 60_000L, s.getLong("timer_len", 0L) + 60_000L, 0L)
        start(ctx, end + 60_000L)
    }

    /** Holds the timer with what is left, from its notification. */
    fun pause(ctx: Context) {
        val s = sp(ctx)
        val end = s.getLong("timer_end", 0L)
        val left = end - System.currentTimeMillis()
        if (end <= 0L || left <= 0L) return
        val len = s.getLong("timer_len", 0L)
        cancel(ctx)
        set(ctx, 0L, len, left.coerceAtLeast(1000L))
        ClockNotif.timerPaused(ctx, left.coerceAtLeast(1000L))
    }

    /** Carries on from where it was held, from its notification. */
    fun resume(ctx: Context) {
        val s = sp(ctx)
        val left = s.getLong("timer_left", 0L)
        if (left <= 0L) return
        val end = System.currentTimeMillis() + left
        set(ctx, end, s.getLong("timer_len", 0L), 0L)
        start(ctx, end)
    }

    /** Throws the timer away, from its notification. */
    fun cancelAndClear(ctx: Context) {
        cancel(ctx)
        set(ctx, 0L, length(ctx), 0L)
    }

    /** After the ringing timer is dismissed: back to ready. */
    fun clearState(ctx: Context) = set(ctx, 0L, length(ctx), 0L)
}
