package app.koma.launcher.data

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat

/**
 * Empty on purpose. Android only lets an app read media sessions if it has an enabled
 * notification listener, so this service exists to carry that permission.
 */
class KomaNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    companion object {
        @Volatile
        var instance: KomaNotificationListener? = null

        /** Real messages and alerts only: not ongoing ones, group summaries, or a music player's now-playing card. */
        private fun shown(sbn: android.service.notification.StatusBarNotification): Boolean {
            val n = sbn.notification
            if (sbn.isOngoing || (n.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0) return false
            if (n.category == android.app.Notification.CATEGORY_TRANSPORT || n.category == android.app.Notification.CATEGORY_PROGRESS) return false
            val template = n.extras.getString(android.app.Notification.EXTRA_TEMPLATE).orEmpty()
            if (template.contains("MediaStyle") || n.extras.containsKey("android.mediaSession")) return false
            return true
        }

        /** The big picture of a notification, when it has one. */
        private fun pictureOf(x: android.os.Bundle): android.graphics.Bitmap? = try {
            @Suppress("DEPRECATION")
            val bmp = x.getParcelable<android.graphics.Bitmap>(android.app.Notification.EXTRA_PICTURE)
            if (bmp != null) bmp
            else if (android.os.Build.VERSION.SDK_INT >= 31) {
                @Suppress("DEPRECATION")
                val ic = x.getParcelable<android.graphics.drawable.Icon>(android.app.Notification.EXTRA_PICTURE_ICON)
                val d = ic?.loadDrawable(instance)
                if (d == null) null else {
                    val w = d.intrinsicWidth.coerceIn(1, 1200)
                    val h = d.intrinsicHeight.coerceIn(1, 1200)
                    val b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                    d.setBounds(0, 0, w, h)
                    d.draw(android.graphics.Canvas(b))
                    b
                }
            } else null
        } catch (e: Throwable) {
            null
        }

        private fun toNote(sbn: android.service.notification.StatusBarNotification, withPicture: Boolean = false): AppNote? {
            val n = sbn.notification
            val x = n.extras
            val title = (x.getCharSequence(android.app.Notification.EXTRA_CONVERSATION_TITLE) ?: x.getCharSequence(android.app.Notification.EXTRA_TITLE))?.toString().orEmpty()
            var text = x.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
            @Suppress("DEPRECATION")
            val msgs = x.getParcelableArray(android.app.Notification.EXTRA_MESSAGES)
            val last = msgs?.lastOrNull() as? android.os.Bundle
            val body = last?.getCharSequence("text")?.toString()
            if (!body.isNullOrBlank()) {
                val who = last.getCharSequence("sender")?.toString()
                text = if (!who.isNullOrBlank() && who != title) "$who: $body" else body
            }
            if (title.isBlank() && text.isBlank()) return null
            val reply = n.actions.orEmpty().firstNotNullOfOrNull { act ->
                val ins = act.remoteInputs
                if (act.actionIntent != null && ins != null && ins.any { it.allowFreeFormInput }) ReplyAction(act, ins) else null
            }
            val picture = if (withPicture) pictureOf(x) else null
            return AppNote(title, text, n.contentIntent, sbn.key, reply, sbn.isClearable, sbn.postTime, (n.flags and android.app.Notification.FLAG_AUTO_CANCEL) != 0, picture)
        }

        /** The newest notification of one app that is not in [skip], or null. Null when notification access is off. */
        fun latest(pkg: String, skip: Set<String> = emptySet()): AppNote? = try {
            instance?.activeNotifications.orEmpty()
                .filter { it.packageName == pkg && it.key !in skip && shown(it) }
                .sortedByDescending { it.postTime }
                .firstNotNullOfOrNull { toNote(it, withPicture = true) }
        } catch (e: Exception) {
            null
        }

        /** The newest notification of every app that has one, by package. */
        fun allLatest(): Map<String, AppNote> = try {
            val out = HashMap<String, AppNote>()
            instance?.activeNotifications.orEmpty()
                .filter { shown(it) }
                .sortedByDescending { it.postTime }
                .forEach { sbn -> if (sbn.packageName !in out) toNote(sbn)?.let { out[sbn.packageName] = it } }
            out
        } catch (e: Exception) {
            emptyMap()
        }

        /**
         * Opens where the notification points. Android 14 and later block an app from opening another app's screen through
         * its pending intent unless the sender opts in, so this does. Clears the notification the way the shade does.
         */
        fun open(ctx: Context, n: AppNote): Boolean {
            val pi = n.intent ?: return false
            return try {
                val opts = android.app.ActivityOptions.makeBasic()
                if (android.os.Build.VERSION.SDK_INT >= 36) {
                    opts.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
                } else if (android.os.Build.VERSION.SDK_INT >= 34) {
                    @Suppress("DEPRECATION")
                    opts.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                }
                pi.send(ctx, 0, null, null, null, null, opts.toBundle())
                if (n.autoCancel) dismiss(n.key)
                true
            } catch (e: Exception) {
                false
            }
        }

        /** Clears one notification from the system tray too. */
        fun dismiss(key: String) {
            try {
                instance?.cancelNotification(key)
            } catch (e: Exception) {
            }
        }

        /**
         * After a reply the app re-posts the same conversation notification showing the sent message, a moment later and
         * sometimes more than once. Clear it now and again as those echoes arrive, so neither the chat nor the reply lingers.
         */
        fun dismissAfterReply(key: String) {
            val h = android.os.Handler(android.os.Looper.getMainLooper())
            dismiss(key)
            listOf(400L, 1000L, 2000L, 4000L).forEach { d -> h.postDelayed({ dismiss(key) }, d) }
        }

        /** Sends [text] through the notification's own reply button, so the app never opens. */
        fun sendReply(ctx: Context, r: ReplyAction, text: String): Boolean = try {
            val i = android.content.Intent()
            val b = android.os.Bundle()
            r.inputs.forEach { if (it.allowFreeFormInput) b.putCharSequence(it.resultKey, text) }
            android.app.RemoteInput.addResultsToIntent(r.inputs, i, b)
            if (android.os.Build.VERSION.SDK_INT >= 28) android.app.RemoteInput.setResultsSource(i, android.app.RemoteInput.SOURCE_FREE_FORM_INPUT)
            r.action.actionIntent.send(ctx, 0, i)
            true
        } catch (e: Exception) {
            false
        }
    }
}

class ReplyAction(val action: android.app.Notification.Action, val inputs: Array<android.app.RemoteInput>)

class AppNote(
    val title: String, val text: String, val intent: android.app.PendingIntent?,
    val key: String, val reply: ReplyAction?, val clearable: Boolean, val time: Long = 0L, val autoCancel: Boolean = false,
    val picture: android.graphics.Bitmap? = null,
)

data class NowPlaying(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val pkg: String = "",
    val position: Long = 0L,
    val duration: Long = 0L,
    /** SystemClock.elapsedRealtime() when [position] was taken. */
    val stamp: Long = 0L,
    val speed: Float = 1f,
    val art: android.graphics.Bitmap? = null,
) {
    /** Where the song is now, moving on from the last reported position while it plays. */
    fun current(): Long {
        val p = if (playing && stamp > 0L) position + ((android.os.SystemClock.elapsedRealtime() - stamp) * speed).toLong() else position
        return if (duration > 0L) p.coerceIn(0L, duration) else p.coerceAtLeast(0L)
    }
}

/** Follows whichever media session is active (Spotify, YouTube Music, podcasts, local players). */
class MediaWatcher(private val ctx: Context) {
    var now by mutableStateOf<NowPlaying?>(null)
        private set
    var hasAccess by mutableStateOf(false)
        private set

    private var controller: MediaController? = null
    // The last song is remembered so the tile can show it, and start it again, after the phone restarts.
    private val sp = ctx.getSharedPreferences("koma_media", Context.MODE_PRIVATE)
    private val component = ComponentName(ctx, KomaNotificationListener::class.java)
    private val manager = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() = pick(null)
    }

    fun start() {
        hasAccess = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        if (!hasAccess) {
            now = null
            return
        }
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component)
            pick(manager.getActiveSessions(component))
        } catch (e: SecurityException) {
            hasAccess = false
        }
    }

    fun stop() {
        try {
            manager.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (e: Exception) {
            // listener was never added
        }
        controller?.unregisterCallback(callback)
        controller = null
    }

    private fun pick(list: List<MediaController>?) {
        val best = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list?.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            best?.registerCallback(callback)
        }
        publish()
    }

    private var lastRawArt: android.graphics.Bitmap? = null
    private var lastArt: android.graphics.Bitmap? = null

    private fun publish() {
        val c = controller
        val m = c?.metadata
        if (c == null || m == null) {
            val t = sp.getString("title", "").orEmpty()
            now = if (t.isBlank()) null else NowPlaying(t, sp.getString("artist", "").orEmpty(), false, sp.getString("pkg", "").orEmpty())
        } else {
            val st = c.playbackState
            val raw = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            if (raw !== lastRawArt) {
                lastRawArt = raw
                lastArt = raw?.let { b ->
                    val k = 640f / maxOf(b.width, b.height)
                    if (k < 1f) android.graphics.Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true) else b
                }
            }
            val n = NowPlaying(
                title = m.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
                artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
                playing = st?.state == PlaybackState.STATE_PLAYING,
                pkg = c.packageName ?: "",
                position = st?.position ?: 0L,
                duration = m.getLong(MediaMetadata.METADATA_KEY_DURATION),
                stamp = st?.lastPositionUpdateTime ?: 0L,
                speed = st?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
                art = lastArt,
            )
            now = n
            if (n.title.isNotBlank()) sp.edit().putString("title", n.title).putString("artist", n.artist).putString("pkg", n.pkg).apply()
        }
    }

    fun toggle() {
        val c = controller
        if (c != null) {
            val controls = c.transportControls
            if (now?.playing == true) controls.pause() else controls.play()
        } else {
            resume()
        }
    }

    /** Nothing is running (the phone just started): press play on the last player, and open it if that does not wake it. */
    private fun resume() {
        val pkg = sp.getString("pkg", "").orEmpty()
        try {
            if (pkg.isNotBlank()) {
                for (action in listOf(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.ACTION_UP)) {
                    ctx.sendBroadcast(
                        android.content.Intent(android.content.Intent.ACTION_MEDIA_BUTTON).setPackage(pkg)
                            .putExtra(android.content.Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(action, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
                    )
                }
            } else {
                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
                am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
            }
        } catch (e: Exception) {
        }
        if (pkg.isNotBlank()) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (controller == null) {
                    ctx.packageManager.getLaunchIntentForPackage(pkg)?.let {
                        try { ctx.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
                    }
                }
            }, 2500)
        }
    }

    fun seekTo(ms: Long) = controller?.transportControls?.seekTo(ms)

    fun next() = controller?.transportControls?.skipToNext()
    fun previous() = controller?.transportControls?.skipToPrevious()
}
