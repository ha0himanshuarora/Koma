package app.koma.launcher.data

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import app.koma.launcher.R
import androidx.compose.ui.graphics.toArgb
import app.koma.launcher.ui.RingActivity
import app.koma.launcher.ui.savedPalette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What is ringing right now. [time] is the digits to show big, [suffix] is "AM", "PM" or a note. */
class RingInfo(val kind: String, val id: String, val title: String, val time: String, val suffix: String, val snooze: Int, val left: Int = 0)

/**
 * Rings an alarm or a timer the way a clock app does: a foreground service that plays the chosen tone on the alarm
 * volume and vibrates until it is dismissed or snoozed, or a minute passes with no answer. Its notification opens the full screen
 * alarm over the lock screen, or shows as a pop-up when the phone is in use.
 */
class RingService : Service() {
    companion object {
        val current = mutableStateOf<RingInfo?>(null)
        const val ACTION_START = "app.koma.launcher.RING_START"
        const val ACTION_DISMISS = "app.koma.launcher.RING_DISMISS"
        const val ACTION_SNOOZE = "app.koma.launcher.RING_SNOOZE"
        const val ACTION_ADD_MINUTE = "app.koma.launcher.RING_ADD_MINUTE"
        const val ACTION_RESTART = "app.koma.launcher.RING_RESTART"
        /** The full screen is showing: the notification need not pop up over it any more. */
        const val ACTION_QUIET = "app.koma.launcher.RING_QUIET"
        private const val CHANNEL_QUIET = "koma_ringing_quiet"
        private const val CHANNEL = "koma_ringing"
        private const val NOTIF = 7100
        /** With the auto options on, nobody answering for this long snoozes an alarm (or stops it when no snoozes are left) and closes a timer. Otherwise it rings until LIMIT_MS. */
        const val AUTO_MS = 60_000L
        private const val LIMIT_MS = 10 * 60_000L

        /** When the service last got going. Receivers check this to see whether ringing really began. */
        @Volatile var startedAt = 0L

        fun start(ctx: Context, info: RingInfo) {
            try {
                val i = Intent(ctx, RingService::class.java).setAction(ACTION_START)
                    .putExtra("kind", info.kind).putExtra("id", info.id).putExtra("title", info.title)
                    .putExtra("time", info.time).putExtra("suffix", info.suffix).putExtra("snooze", info.snooze).putExtra("left", info.left)
                ContextCompat.startForegroundService(ctx, i)
            } catch (e: Exception) {
                RingLog.add(ctx, "the system refused to start the ringing service (${e.javaClass.simpleName}), using the backup notification")
                ClockNotif.fallbackRing(ctx, info)
            }
        }

        fun send(ctx: Context, action: String) {
            try {
                ctx.startService(Intent(ctx, RingService::class.java).setAction(action))
            } catch (e: Exception) {
            }
        }
    }

    private var player: MediaPlayer? = null
    private var fallback: Ringtone? = null
    private var synth: AudioTrack? = null
    private var snoozing = false
    private var focus: android.media.AudioFocusRequest? = null
    private var vibrator: Vibrator? = null
    private var wake: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var powerWatcher: android.content.BroadcastReceiver? = null
    private val alarmAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> begin(intent)
            ACTION_DISMISS -> {
                RingLog.add(this, "service: dismissed")
                finish(missed = false)
            }
            ACTION_SNOOZE -> {
                RingLog.add(this, "service: snoozed")
                snooze()
            }
            ACTION_ADD_MINUTE -> addMinute()
            ACTION_RESTART -> restart()
            ACTION_QUIET -> current.value?.let { info ->
                try {
                    channel()
                    getSystemService(NotificationManager::class.java).notify(NOTIF, notif(info, fullScreen = false, quiet = true))
                } catch (e: Exception) {
                }
            }
            else -> if (current.value == null) {
                // Started with nothing to ring (the system restarted us): satisfy the foreground rule and leave.
                try {
                    startForegroundCompat(placeholder())
                } catch (e: Exception) {
                }
                finish(missed = false)
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(NOTIF, n)
    }

    private fun channel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Ringing alarms and timers", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            },
        )
        // The same notification once the full screen is up: it stays in the shade but does not pop up over the alarm.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_QUIET, "Ringing alarms and timers (while open)", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    private fun placeholder(): Notification {
        channel()
        return Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Clock").build()
    }

    private fun begin(intent: Intent) {
        quiet()
        val info = RingInfo(
            intent.getStringExtra("kind") ?: "alarm", intent.getStringExtra("id") ?: "",
            intent.getStringExtra("title") ?: "Alarm", intent.getStringExtra("time") ?: "",
            intent.getStringExtra("suffix") ?: "", intent.getIntExtra("snooze", 0), intent.getIntExtra("left", 0),
        )
        RingLog.add(this, "service: ringing ${info.kind} \"${info.title}\"")
        channel()
        // Phone in use (screen on and unlocked): just the live notification at the top. Screen off or locked: the full screen alone.
        val inUse = try {
            getSystemService(PowerManager::class.java).isInteractive && !getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
        } catch (e: Exception) {
            false
        }
        try {
            startForegroundCompat(notif(info, fullScreen = !inUse))
            RingLog.add(this, "service: running in the foreground")
        } catch (e: Exception) {
            RingLog.add(this, "service: could not go foreground (${e.javaClass.simpleName}: ${e.message})")
        }
        current.value = info
        startedAt = System.currentTimeMillis()
        try {
            val pm = getSystemService(PowerManager::class.java)
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "koma:ring").apply { acquire(LIMIT_MS + 30_000L) }
        } catch (e: Exception) {
        }
        try {
            val interactive = getSystemService(PowerManager::class.java).isInteractive
            val locked = getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked
            RingLog.add(this, "screen: ${if (interactive) "on" else "off"}, ${if (locked) "locked" else "unlocked"}")
        } catch (e: Exception) {
        }
        play(info.kind == "timer")
        buzz()
        // Only for the full screen alarm on the lock screen. With the phone in use, the screen timing out must not close it.
        if (!inUse) watchPower()
        handler.postDelayed({
            val ok = player?.isPlaying == true || synth != null || fallback?.isPlaying == true
            RingLog.add(this, "sound: ${if (ok) "still playing" else "NOT playing"} two seconds in")
        }, 2000L)
        val sp = getSharedPreferences("koma", MODE_PRIVATE)
        val auto = if (info.kind == "timer") sp.getBoolean("auto_stop_timer", true) else sp.getBoolean("auto_snooze", true)
        handler.postDelayed({
            val cur = current.value ?: return@postDelayed
            if (auto && cur.kind == "alarm" && cur.snooze > 0) {
                RingLog.add(this, "service: no answer for a minute, snoozing")
                snooze()
            } else finish(missed = cur.kind == "alarm")
        }, if (auto) AUTO_MS else LIMIT_MS)
        // Screen off or locked: open the full screen alarm. Otherwise the notification is all that shows.
        if (!inUse) {
            try {
                startActivity(Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                RingLog.add(this, "screen: asked to open the alarm screen")
            } catch (e: Exception) {
                RingLog.add(this, "screen: could not open the alarm screen directly (${e.javaClass.simpleName}), the notification will")
            }
        }
    }

    private fun notif(info: RingInfo, fullScreen: Boolean = true, quiet: Boolean = false): Notification {
        val show = PendingIntent.getActivity(
            this, 11, Intent(this, RingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun act(action: String, code: Int) = PendingIntent.getService(
            this, 20 + code, Intent(this, RingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(this, if (quiet) CHANNEL_QUIET else CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOnlyAlertOnce(true)
            .setContentTitle(info.title)
            .setContentText((info.time + " " + info.suffix).trim())
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(show)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        if (fullScreen) b.setFullScreenIntent(show, true)
        if (info.kind == "timer") b.addAction(Notification.Action.Builder(null, "Restart", act(ACTION_RESTART, 1)).build())
        else if (info.snooze > 0) b.addAction(Notification.Action.Builder(null, "Snooze ${info.snooze} min", act(ACTION_SNOOZE, 2)).build())
        b.addAction(Notification.Action.Builder(null, if (info.kind == "timer") "End" else "Stop", act(ACTION_DISMISS, 3)).build())
        return b.build()
    }

    /** Tones to try, best first: the chosen one, then the phone's own defaults. */
    private fun candidates(timer: Boolean): List<Uri> {
        val v = RingPrefs.tone(this, timer)
        val out = mutableListOf<Uri>()
        if (v.isNotEmpty() && v != "none") try {
            out += Uri.parse(v)
        } catch (e: Exception) {
        }
        for (type in listOf(RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_NOTIFICATION)) {
            try {
                RingtoneManager.getActualDefaultRingtoneUri(this, type)?.let { out += it }
            } catch (e: Exception) {
            }
            try {
                RingtoneManager.getDefaultUri(type)?.let { out += it }
            } catch (e: Exception) {
            }
        }
        return out.distinct()
    }

    private fun play(timer: Boolean) {
        if (RingPrefs.tone(this, timer) == "none") {
            RingLog.add(this, "sound: silent by choice")
            return
        }
        try {
            val am = getSystemService(AudioManager::class.java)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val cur = am.getStreamVolume(AudioManager.STREAM_ALARM)
            RingLog.add(this, "sound: alarm volume $cur of $max")
        } catch (e: Exception) {
            RingLog.add(this, "sound: volume check failed (${e.message})")
        }
        try {
            val am = getSystemService(AudioManager::class.java)
            val req = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(alarmAttrs).setOnAudioFocusChangeListener { }.build()
            val r = am.requestAudioFocus(req)
            focus = req
            RingLog.add(this, "sound: audio focus ${if (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) "granted" else "refused ($r)"}")
        } catch (e: Exception) {
        }
        for (uri in candidates(timer)) {
            try {
                val mp = MediaPlayer()
                mp.setAudioAttributes(alarmAttrs)
                mp.setDataSource(this, uri)
                mp.isLooping = true
                mp.prepare()
                if (!timer) mp.setVolume(0.25f, 0.25f)
                mp.start()
                player = mp
                RingLog.add(this, "sound: playing $uri")
                if (!timer) {
                    // A gentle start: the sound rises to full over about twenty seconds.
                    var step = 0
                    val up = object : Runnable {
                        override fun run() {
                            step++
                            val v = (0.25f + 0.75f * step / 10f).coerceAtMost(1f)
                            player?.setVolume(v, v)
                            if (step < 10) handler.postDelayed(this, 2000L)
                        }
                    }
                    handler.postDelayed(up, 2000L)
                }
                return
            } catch (e: Exception) {
                RingLog.add(this, "sound: $uri failed (${e.javaClass.simpleName}: ${e.message})")
                try {
                    player?.release()
                } catch (e2: Exception) {
                }
                player = null
            }
        }
        // Nothing the phone offers would play: make a beep ourselves, so there is always a sound.
        try {
            val rate = 22050
            val n = rate * 2
            val pcm = ShortArray(n)
            val burst = (rate * 0.15).toInt()
            for (k in 0 until 4) {
                val start = k * (rate / 4)
                for (i in 0 until burst) pcm[start + i] = (Math.sin(2.0 * Math.PI * 1000.0 * i / rate) * 0.6 * Short.MAX_VALUE).toInt().toShort()
            }
            val tr = AudioTrack.Builder()
                .setAudioAttributes(alarmAttrs)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(n * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            tr.write(pcm, 0, n)
            tr.setLoopPoints(0, n, -1)
            tr.play()
            synth = tr
            RingLog.add(this, "sound: no tone worked, playing a built-in beep")
        } catch (e: Exception) {
            RingLog.add(this, "sound: even the built-in beep failed (${e.message})")
        }
    }

    @Suppress("DEPRECATION")
    private fun buzz() {
        if (!RingPrefs.vibrate(this)) return
        try {
            vibrator = getSystemService(Vibrator::class.java)?.also {
                it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400, 600, 400, 1200, 1000), 0), alarmAttrs)
            }
        } catch (e: Exception) {
        }
    }

    /** The power button switches the screen off while something rings: that closes the alarm or timer, with no snooze or repeat. */
    private fun watchPower() {
        unwatchPower()
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                // Ignore the screen flicker as ringing starts.
                if (current.value == null || System.currentTimeMillis() - startedAt < 1500L) return
                RingLog.add(this@RingService, "service: power button, closed")
                finish(missed = false)
            }
        }
        try {
            ContextCompat.registerReceiver(this, r, android.content.IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
            powerWatcher = r
        } catch (e: Exception) {
        }
    }

    private fun unwatchPower() {
        powerWatcher?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
            }
        }
        powerWatcher = null
    }

    private fun quiet() {
        unwatchPower()
        handler.removeCallbacksAndMessages(null)
        try {
            player?.stop()
        } catch (e: Exception) {
        }
        try {
            player?.release()
        } catch (e: Exception) {
        }
        player = null
        try {
            fallback?.stop()
        } catch (e: Exception) {
        }
        fallback = null
        try {
            synth?.stop()
            synth?.release()
        } catch (e: Exception) {
        }
        synth = null
        try {
            focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        } catch (e: Exception) {
        }
        focus = null
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
        }
        vibrator = null
        try {
            wake?.let { if (it.isHeld) it.release() }
        } catch (e: Exception) {
        }
        wake = null
    }

    private fun finish(missed: Boolean, clearTimer: Boolean = true) {
        val info = current.value
        RingLog.add(this, "service: stopped" + if (missed) " (nobody answered)" else "")
        quiet()
        current.value = null
        if (missed && info != null) ClockNotif.missed(this, info)
        if (info?.kind == "timer" && clearTimer) TimerCtl.clearState(this)
        // Snoozing leaves the count; stopping or missing the alarm clears it for next time.
        if (info?.kind == "alarm" && info.id.isNotEmpty() && !snoozing) ClockStore.setSnoozeCount(this, info.id, 0)
        if (info?.kind == "alarm" && info.id.isNotEmpty() && !snoozing) ClockNotif.clearSnoozed(this, info.id)
        snoozing = false
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
        }
        stopSelf()
    }

    private fun snooze() {
        val info = current.value ?: return
        if (info.kind == "timer") {
            restart()
            return
        }
        if (info.snooze <= 0) return
        ClockStore.setSnoozeCount(this, info.id, ClockStore.snoozeCount(this, info.id) + 1)
        val at = System.currentTimeMillis() + info.snooze * 60_000L
        AlarmCtl.at(this, info.id, at, code = 1)
        ClockNotif.snoozed(this, info, at)
        snoozing = true
        finish(missed = false)
    }

    /** A finished timer starts again for the same time. */
    private fun restart() {
        val len = TimerCtl.length(this).takeIf { it > 0L } ?: 60_000L
        val now = System.currentTimeMillis()
        TimerCtl.set(this, now + len, len, 0L)
        TimerCtl.start(this, now + len)
        finish(missed = false, clearTimer = false)
    }

    private fun addMinute() {
        val now = System.currentTimeMillis()
        TimerCtl.set(this, now + 60_000L, 60_000L, 0L)
        TimerCtl.start(this, now + 60_000L)
        finish(missed = false, clearTimer = false)
    }

    override fun onDestroy() {
        quiet()
        if (current.value != null) current.value = null
        super.onDestroy()
    }
}

/** The quiet notifications of the clock: running timer, stopwatch, upcoming alarm, snoozed and missed alarms. */
object ClockNotif {
    private const val CH = "koma_clock"
    const val TIMER_ID = 7101
    const val STOPWATCH_ID = 7102
    const val UPCOMING_ID = 7201
    const val FOCUS_ID = 7103
    @Volatile private var lastFocus = 0L
    private const val PRE_CODE = 7200
    private const val REMINDER_CH = "koma_reminders"
    /** The "turn off your upcoming alarm?" notice appears this long before the alarm. */
    private const val UPCOMING_LEAD = 30 * 60_000L

    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    @Volatile private var cx: Context? = null

    private fun channel(ctx: Context) {
        cx = ctx.applicationContext
        nm(ctx).createNotificationChannel(NotificationChannel(CH, "Clock", NotificationManager.IMPORTANCE_LOW))
    }

    private fun open(ctx: Context): PendingIntent? = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let {
        PendingIntent.getActivity(ctx, 7004, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun act(ctx: Context, action: String, code: Int, id: String = ""): PendingIntent = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, AlarmReceiver::class.java).setAction(action).putExtra("id", id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun snoozeId(id: String) = 7300 + Math.abs(id.hashCode() % 500)

    fun reminderId(id: String) = 7900 + Math.abs(id.hashCode() % 500)

    /** A reminder going off: a heads-up notice with Done and Snooze. */
    fun reminder(ctx: Context, t: Task, whenText: String) {
        try {
            nm(ctx).createNotificationChannel(NotificationChannel(REMINDER_CH, "Reminders", NotificationManager.IMPORTANCE_HIGH))
            val nid = reminderId(t.id)
            val n = Notification.Builder(ctx, REMINDER_CH)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(t.text.ifBlank { "Reminder" })
                .setContentText(whenText)
                .setContentIntent(open(ctx))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .addAction(Notification.Action.Builder(null, "Done", act(ctx, ReminderCtl.ACTION_DONE, nid, t.id)).build())
                .addAction(Notification.Action.Builder(null, "Snooze 10 min", act(ctx, ReminderCtl.ACTION_SNOOZE, nid, t.id)).build())
                .build()
            nm(ctx).notify(nid, n)
        } catch (e: Exception) {
        }
    }

    fun reminderGone(ctx: Context, id: String) {
        try {
            nm(ctx).cancel(reminderId(id))
        } catch (e: Exception) {
        }
    }

    private fun fmt(ctx: Context, ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(if (TimeFmt.is24Saved(ctx)) "HH:mm" else "h:mm a", Locale.getDefault()))

    /** "7:00" and "AM" for the big display. */
    fun parts(ctx: Context, ms: Long): Pair<String, String> {
        val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        return ClockMath.parts(t.hour, t.minute, TimeFmt.is24Saved(ctx))
    }

    private fun base(ctx: Context, title: String, text: String) = Notification.Builder(ctx, CH)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(open(ctx))
        .setOnlyAlertOnce(true)

    private val handler = android.os.Handler(Looper.getMainLooper())
    private val ticks = HashMap<Int, Runnable>()

    /** Cuts a time down to what fits in the status bar capsule: "1h 23m", "45 min", or "9:57" in the last ten minutes. */
    private fun chipText(ms: Long, up: Boolean = false): String {
        val s = (if (up) ms / 1000L else (ms + 999L) / 1000L).coerceAtLeast(0L)
        val h = s / 3600L
        val m = (s % 3600L) / 60L
        return if (s >= 600L) (if (h > 0L) "${h}h ${m}m" else "$m min") else "%d:%02d".format(s / 60L, s % 60L)
    }

    private var screenWatcher: android.content.BroadcastReceiver? = null

    /** When the screen comes on, every running tick posts its notification again straight away. */
    private fun watchScreen(ctx: Context) {
        if (screenWatcher != null) return
        val r = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                ticks.values.toList().forEach { t ->
                    handler.removeCallbacks(t)
                    handler.post(t)
                }
            }
        }
        try {
            androidx.core.content.ContextCompat.registerReceiver(
                ctx.applicationContext, r, android.content.IntentFilter(Intent.ACTION_SCREEN_ON),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            screenWatcher = r
        } catch (e: Exception) {
        }
    }

    private fun tickDelay(ms: Long): Long = if (ms > 600_000L) 30_000L else 1_000L

    fun stopTick(id: Int) {
        ticks.remove(id)?.let { handler.removeCallbacks(it) }
    }

    /**
     * The capsule's time is plain text, so it is posted again as it changes while the notification is up. [step] posts it
     * and returns the wait before the next post, or 0 to stop. Does nothing once the notification has been dismissed.
     */
    private fun tickFor(ctx: Context, id: Int, step: () -> Long) {
        stopTick(id)
        watchScreen(ctx)
        val r = object : Runnable {
            override fun run() {
                // Nobody can see the capsule with the screen off, so wake once a minute instead of every second.
                // Switching the screen on runs every pending tick at once (see watchScreen).
                val awake = try { ctx.getSystemService(android.os.PowerManager::class.java).isInteractive } catch (e: Exception) { true }
                if (!awake) {
                    handler.postDelayed(this, 60_000L)
                    return
                }
                val alive = try { nm(ctx).activeNotifications.any { it.id == id } } catch (e: Exception) { false }
                val d = if (alive) try { step() } catch (e: Exception) { 0L } else 0L
                if (d > 0L) handler.postDelayed(this, d) else ticks.remove(id)
            }
        }
        ticks[id] = r
        handler.postDelayed(r, 1_000L)
    }

    /**
     * Asks the system to show this as a live update: Android 16's promoted ongoing notifications (status bar chip and
     * lock screen) and Samsung's Now Bar. Phones that do not know these keys ignore them.
     */
    private fun live(b: Notification.Builder, primary: String, secondary: String, chip: String = primary): Notification.Builder {
        val e = android.os.Bundle()
        if (Build.VERSION.SDK_INT >= 36) e.putBoolean("android.requestPromotedOngoing", true)
        e.putInt("android.ongoingActivityNoti.style", 1)
        e.putString("android.ongoingActivityNoti.primaryInfo", primary)
        e.putString("android.ongoingActivityNoti.secondaryInfo", secondary)
        e.putString("android.ongoingActivityNoti.nowbarPrimaryInfo", primary)
        e.putString("android.ongoingActivityNoti.nowbarSecondaryInfo", secondary)
        // The status bar capsule: a short word, an icon and a colour.
        val saved: Int? = try { savedPalette(cx!!)?.tiles?.firstOrNull()?.toArgb() } catch (x: Exception) { null }
        val color = saved ?: 0xFFFF6A2B.toInt()
        e.putString("android.ongoingActivityNoti.chipExpandedText", chip)
        e.putInt("android.ongoingActivityNoti.chipBgColor", color)
        e.putInt("android.ongoingActivityNoti.actionType", 1)
        e.putInt("android.ongoingActivityNoti.actionPrimarySet", 0)
        try {
            e.putParcelable("android.ongoingActivityNoti.chipIcon", android.graphics.drawable.Icon.createWithResource(cx!!, android.R.drawable.ic_lock_idle_alarm))
        } catch (x: Exception) {
        }
        e.putString("android.substName", "Koma")
        if (Build.VERSION.SDK_INT >= 36) {
            // Android 16 and later: ask for a promoted (live update) notification. Done by name so it builds on older SDKs.
            try {
                Notification.Builder::class.java.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType).invoke(b, true)
            } catch (x: Exception) {
            }
            return b.addExtras(e)
        }
        return b.setColor(color).addExtras(e)
    }

    /** A progress bar in the live update's own style on Android 16 and later, the plain bar before that. */
    private fun withProgress(b: Notification.Builder, pct: Int): Notification.Builder {
        if (Build.VERSION.SDK_INT < 36) return b.setProgress(100, pct, false)
        try {
            val cls = Class.forName("android.app.Notification\$ProgressStyle")
            val style = cls.getDeclaredConstructor().newInstance() as Notification.Style
            cls.getMethod("setProgress", Int::class.javaPrimitiveType).invoke(style, pct)
            b.setStyle(style)
        } catch (x: Exception) {
            b.setProgress(100, pct, false)
        }
        return b
    }

    /**
     * The live clock in a notification. Promoted notifications cannot use custom views, so Android 16 gets the plain
     * chronometer; older phones get the big clock layout.
     */
    private fun clockStyle(ctx: Context, b: Notification.Builder, whenMs: Long, down: Boolean, sub: String): Notification.Builder {
        if (Build.VERSION.SDK_INT >= 36 || Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
            return b.setShowWhen(true).setWhen(whenMs).setUsesChronometer(true).setChronometerCountDown(down)
        }
        val rv = clockView(ctx, whenMs, down, sub)
        return b.setStyle(Notification.DecoratedCustomViewStyle()).setCustomContentView(rv).setCustomBigContentView(rv).setShowWhen(false)
    }

    /** A big live clock with one line under it, like a system clock app's notification. */
    private fun clockView(ctx: Context, whenMs: Long, down: Boolean, sub: String): android.widget.RemoteViews {
        val rv = android.widget.RemoteViews(ctx.packageName, R.layout.notif_clock)
        val base = android.os.SystemClock.elapsedRealtime() + (whenMs - System.currentTimeMillis())
        rv.setChronometerCountDown(R.id.clock_chrono, down)
        rv.setChronometer(R.id.clock_chrono, base, null, true)
        rv.setTextViewText(R.id.clock_sub, sub)
        return rv
    }

    /** A running countdown, live in the shade. */
    fun timer(ctx: Context, end: Long) {
        postTimer(ctx, end)
        tickFor(ctx, TIMER_ID) {
            val left = end - System.currentTimeMillis()
            if (left <= 0L) 0L else { postTimer(ctx, end); tickDelay(left) }
        }
    }

    private fun postTimer(ctx: Context, end: Long) {
        try {
            channel(ctx)
            val n = live(clockStyle(ctx, base(ctx, "Timer", "Rings at ${fmt(ctx, end)}"), end, true, "Timer / rings at ${fmt(ctx, end)}"), "Timer", "Rings at ${fmt(ctx, end)}", chipText(end - System.currentTimeMillis()))
                .setOngoing(true)
                .addAction(Notification.Action.Builder(null, "+1 min", act(ctx, Ring.ACTION_TIMER_ADD, 7110)).build())
                .addAction(Notification.Action.Builder(null, "Cancel", act(ctx, Ring.ACTION_TIMER_CANCEL, 7111)).build())
                .addAction(Notification.Action.Builder(null, "Pause", act(ctx, Ring.ACTION_TIMER_PAUSE, 7112)).build())
                .build()
            nm(ctx).notify(TIMER_ID, n)
        } catch (e: Exception) {
        }
    }

    /** The timer held part way: what is left, with a way to carry on. */
    fun timerPaused(ctx: Context, left: Long) {
        try {
            channel(ctx)
            stopTick(TIMER_ID)
            val n = base(ctx, "Timer paused", "${chipText(left)} left")
                .setOngoing(true).setShowWhen(false)
                .addAction(Notification.Action.Builder(null, "+1 min", act(ctx, Ring.ACTION_TIMER_ADD, 7110)).build())
                .addAction(Notification.Action.Builder(null, "Cancel", act(ctx, Ring.ACTION_TIMER_CANCEL, 7111)).build())
                .addAction(Notification.Action.Builder(null, "Resume", act(ctx, Ring.ACTION_TIMER_RESUME, 7113)).build())
                .build()
            nm(ctx).notify(TIMER_ID, n)
        } catch (e: Exception) {
        }
    }

    /** Focus running: a live countdown to when it ends. Called whenever the focus state is refreshed. */
    fun focus(ctx: Context, name: String?, start: Long, until: Long, strict: Boolean) {
        try {
            if (name == null) {
                if (lastFocus != 0L) nm(ctx).cancel(FOCUS_ID)
                lastFocus = 0L
                stopTick(FOCUS_ID)
                return
            }
            if (lastFocus == until && ticks.containsKey(FOCUS_ID)) return
            lastFocus = until
            postFocus(ctx, name, start, until, strict)
            tickFor(ctx, FOCUS_ID) {
                val left = until - System.currentTimeMillis()
                if (left <= 0L) 0L else { postFocus(ctx, name, start, until, strict); tickDelay(left) }
            }
        } catch (e: Exception) {
        }
    }

    private fun postFocus(ctx: Context, name: String, start: Long, until: Long, strict: Boolean) {
        try {
            val now = System.currentTimeMillis()
            val mins = ((until - now + 59_999L) / 60_000L).coerceAtLeast(0L)
            channel(ctx)
            val title = "Session ends at ${fmt(ctx, until)}"
            val left = if (mins <= 1L) "Less than a minute remaining" else if (mins >= 60L) "${mins / 60} h ${mins % 60} min remaining" else "$mins min remaining"
            val text = (if (strict) "Strict. " else "") + left
            val span = (until - start).coerceAtLeast(1L)
            val pct = if (start > 0L) (((now - start) * 100L) / span).toInt().coerceIn(0, 100) else 0
            val b = base(ctx, title, text)
                .setOngoing(true).setShowWhen(true).setWhen(until).setUsesChronometer(true).setChronometerCountDown(true)
            nm(ctx).notify(FOCUS_ID, live(withProgress(b, pct), title, text, chipText(until - now)).build())
        } catch (e: Exception) {
        }
    }

    fun timerGone(ctx: Context) {
        stopTick(TIMER_ID)
        try {
            nm(ctx).cancel(TIMER_ID)
        } catch (e: Exception) {
        }
    }

    /** The stopwatch, counting up in the shade while it runs. */
    fun stopwatch(ctx: Context, on: Boolean, elapsed: Long) {
        try {
            if (!on) {
                stopTick(STOPWATCH_ID)
                nm(ctx).cancel(STOPWATCH_ID)
                return
            }
            channel(ctx)
            val began = System.currentTimeMillis() - elapsed
            fun post() {
                val n = live(clockStyle(ctx, base(ctx, "Stopwatch", "Running"), began, false, "Stopwatch / running"), "Stopwatch", "Running", chipText(System.currentTimeMillis() - began, up = true))
                    .setOngoing(true)
                    .build()
                nm(ctx).notify(STOPWATCH_ID, n)
            }
            post()
            tickFor(ctx, STOPWATCH_ID) { post(); tickDelay(System.currentTimeMillis() - began) }
        } catch (e: Exception) {
        }
    }

    /** Removes the "snoozed" notification of an alarm and stops its ticker. */
    fun clearSnoozed(ctx: Context, id: String) {
        try {
            stopTick(snoozeId(id))
            nm(ctx).cancel(snoozeId(id))
        } catch (e: Exception) {
        }
    }

    fun snoozed(ctx: Context, info: RingInfo, at: Long) {
        try {
            channel(ctx)
            fun post() {
                val n = live(
                    clockStyle(ctx, base(ctx, info.title.ifBlank { "Alarm" }, "Snoozed. Rings again at ${fmt(ctx, at)}"), at, true, "${info.title.ifBlank { "Alarm" }} / snoozed until ${fmt(ctx, at)}"),
                    info.title.ifBlank { "Alarm" }, "Snoozed until ${fmt(ctx, at)}", chipText(at - System.currentTimeMillis()),
                ).setOngoing(true)
                    .addAction(Notification.Action.Builder(null, "Cancel alarm", act(ctx, Ring.ACTION_UNSNOOZE, snoozeId(info.id), info.id)).build())
                    .build()
                nm(ctx).notify(snoozeId(info.id), n)
            }
            post()
            tickFor(ctx, snoozeId(info.id)) {
                val left = at - System.currentTimeMillis()
                if (left <= 0L) { clearSnoozed(ctx, info.id); 0L } else { post(); tickDelay(left) }
            }
        } catch (e: Exception) {
        }
    }

    fun missed(ctx: Context, info: RingInfo) {
        try {
            channel(ctx)
            val n = base(ctx, "Missed ${if (info.kind == "timer") "timer" else "alarm"}", (info.title + "  " + info.time + " " + info.suffix).trim())
                .setAutoCancel(true).build()
            nm(ctx).notify(7400 + Math.abs(info.id.hashCode() % 500), n)
        } catch (e: Exception) {
        }
    }

    /** Last resort when the ringing service could not start: a loud notification that opens the alarm screen. */
    fun fallbackRing(ctx: Context, info: RingInfo) {
        try {
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            nm(ctx).createNotificationChannel(
                NotificationChannel("koma_ring_fallback", "Alarm backup", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), attrs)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 1200)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                },
            )
            val show = PendingIntent.getActivity(
                ctx, 12, ctx.packageManager.getLaunchIntentForPackage(ctx.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = Notification.Builder(ctx, "koma_ring_fallback")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(info.title)
                .setContentText((info.time + " " + info.suffix).trim())
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(show)
                .setFullScreenIntent(show, true)
                .build()
            n.flags = n.flags or Notification.FLAG_INSISTENT
            nm(ctx).notify(7500, n)
        } catch (e: Exception) {
            RingLog.add(ctx, "backup notification failed (${e.message})")
        }
    }

    /** Starts ringing, and if the service has not really started a few seconds later, rings by notification. */
    fun ring(ctx: Context, info: RingInfo, pending: android.content.BroadcastReceiver.PendingResult?) {
        val t0 = System.currentTimeMillis()
        RingService.start(ctx, info)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (RingService.startedAt < t0) {
                RingLog.add(ctx, "the ringing service did not start, using the backup notification")
                fallbackRing(ctx, info)
            }
            pending?.finish()
        }, 3500L)
    }

    private fun nextAlarm(ctx: Context): Pair<Alarm, Long>? =
        ClockStore.alarms(ctx).filter { it.on }.mapNotNull { a -> ClockMath.next(a)?.let { a to it.toInstant().toEpochMilli() } }.minByOrNull { it.second }

    /** Keeps "alarm coming up" honest: shows it within the hour before, and arms the moment it should appear. */
    fun refreshUpcoming(ctx: Context) {
        try {
            val am = ctx.getSystemService(AlarmManager::class.java)
            val pre = PendingIntent.getBroadcast(
                ctx, PRE_CODE, Intent(ctx, AlarmReceiver::class.java).setAction(Ring.ACTION_PRE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            am.cancel(pre)
            nm(ctx).cancel(UPCOMING_ID)
            val next = nextAlarm(ctx) ?: return
            val showAt = next.second - UPCOMING_LEAD
            if (showAt <= System.currentTimeMillis()) postUpcoming(ctx)
            else armExact(ctx, showAt, pre)
        } catch (e: Exception) {
        }
    }

    fun postUpcoming(ctx: Context) {
        try {
            val (a, at) = nextAlarm(ctx) ?: return
            if (at - System.currentTimeMillis() > UPCOMING_LEAD) return
            channel(ctx)
            val sub = fmt(ctx, at) + (if (a.label.isBlank()) "" else " / " + a.label)
            fun post() {
                val b = base(ctx, "Turn off your upcoming alarm?", sub)
                if (Build.VERSION.SDK_INT >= 36) b.setOngoing(true).setShowWhen(true).setWhen(at).setUsesChronometer(true).setChronometerCountDown(true)
                else b.setShowWhen(true).setWhen(System.currentTimeMillis())
                val n = live(b, a.label.ifBlank { "Alarm" }, "Rings at ${fmt(ctx, at)}", chipText(at - System.currentTimeMillis()))
                    .addAction(Notification.Action.Builder(null, "Turn off", act(ctx, Ring.ACTION_SKIP, 7210, a.id)).build())
                    .build()
                nm(ctx).notify(UPCOMING_ID, n)
            }
            post()
            tickFor(ctx, UPCOMING_ID) {
                val left = at - System.currentTimeMillis()
                if (left <= 0L) 0L else { post(); tickDelay(left) }
            }
        } catch (e: Exception) {
        }
    }
}

/** Debug output only. Nothing is stored or shown in the app. */
object RingLog {
    fun add(@Suppress("UNUSED_PARAMETER") ctx: Context, msg: String) {
        Log.d("KomaRing", msg)
    }
}
