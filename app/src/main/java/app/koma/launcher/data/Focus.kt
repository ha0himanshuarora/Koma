package app.koma.launcher.data

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.graphics.toArgb
import android.view.inputmethod.InputMethodManager
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/**
 * A focus plan. [kind]: 0 one time, 1 every day, 2 chosen weekdays. [days] is a bit mask, bit 0 = Monday.
 * [startMin] is minutes after midnight (recurring plans). [onceAt] is the start time in millis (one time plans).
 * [allowed] holds package names; the phone app and system screens are always allowed.
 */
data class FocusPlan(
    val id: String,
    val name: String,
    val allowed: List<String>,
    val kind: Int,
    val days: Int,
    val startMin: Int,
    val durationMin: Int,
    val onceAt: Long,
    val enabled: Boolean,
    val strict: Boolean,
)

class FocusActive(val name: String, val until: Long, val allowed: Set<String>, val strict: Boolean, val start: Long = 0L)

/** One finished focus run, for the stats tab. */
data class FocusRun(val start: Long, val minutes: Int)

/** A named group of allowed apps, picked when starting a session or a routine. */
data class Bag(val id: String, val name: String, val apps: List<String>)

object Bags {
    private fun defaults() = listOf(Bag("b1", "Work", emptyList()), Bag("b2", "Study", emptyList()), Bag("b3", "Calm", emptyList()))

    fun parse(text: String): List<Bag> = try {
        if (text.isBlank()) defaults() else {
            val a = JSONArray(text)
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                val ap = o.optJSONArray("apps") ?: JSONArray()
                Bag(o.getString("id"), o.optString("name", "Bag"), List(minOf(ap.length(), 3)) { ap.getString(it) })
            }
        }
    } catch (e: Exception) {
        defaults()
    }

    fun write(list: List<Bag>): String {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("apps", JSONArray(it.apps))) }
        return a.toString()
    }
}

/**
 * The focus rules and the check "is this app allowed right now". Used by the launcher (hides and blocks apps)
 * and by [KomaFocusService] (sends the user home when a blocked app comes to the front). Both run in the same
 * process, so they share this object.
 */
object FocusEngine {
    @Volatile var plans: List<FocusPlan> = emptyList()
    @Volatile var sessionUntil = 0L
    @Volatile var sessionAllowed: List<String> = emptyList()
    @Volatile var sessionStrict = false
    @Volatile var sessionStart = 0L
    @Volatile var runs: List<FocusRun> = emptyList()
    @Volatile private var runStart = 0L
    @Volatile private var runUntil = 0L
    @Volatile private var loaded = false
    @Volatile private var base: Set<String> = emptySet()

    fun init(ctx: Context) {
        if (loaded) return
        loaded = true
        try {
            val text = JsonFile(ctx, "focus.json").read()
            if (text != null) {
                val o = JSONObject(text)
                val a = o.optJSONArray("plans") ?: JSONArray()
                plans = List(a.length()) { i ->
                    val p = a.getJSONObject(i)
                    val al = p.optJSONArray("allowed") ?: JSONArray()
                    FocusPlan(
                        p.getString("id"), p.optString("name", "Focus"), List(al.length()) { al.getString(it) },
                        p.optInt("kind"), p.optInt("days", 127), p.optInt("startMin", 540), p.optInt("durationMin", 60),
                        p.optLong("onceAt"), p.optBoolean("enabled", true), p.optBoolean("strict"),
                    )
                }
                sessionUntil = o.optLong("sessionUntil")
                sessionStrict = o.optBoolean("sessionStrict")
                sessionStart = o.optLong("sessionStart")
                runStart = o.optLong("runStart")
                runUntil = o.optLong("runUntil")
                val ra = o.optJSONArray("runs") ?: JSONArray()
                runs = List(ra.length()) { i -> ra.getJSONArray(i).let { FocusRun(it.getLong(0), it.getInt(1)) } }
                val sa = o.optJSONArray("sessionAllowed") ?: JSONArray()
                sessionAllowed = List(sa.length()) { sa.getString(it) }
            }
        } catch (e: Exception) {
            // Start with no plans if the file is damaged.
        }
        base = computeBase(ctx)
    }

    fun save(ctx: Context) {
        val a = JSONArray()
        plans.forEach { p ->
            a.put(
                JSONObject().put("id", p.id).put("name", p.name).put("allowed", JSONArray(p.allowed))
                    .put("kind", p.kind).put("days", p.days).put("startMin", p.startMin)
                    .put("durationMin", p.durationMin).put("onceAt", p.onceAt)
                    .put("enabled", p.enabled).put("strict", p.strict)
            )
        }
        val o = JSONObject().put("plans", a).put("sessionUntil", sessionUntil)
            .put("sessionStrict", sessionStrict).put("sessionAllowed", JSONArray(sessionAllowed))
            .put("sessionStart", sessionStart).put("runStart", runStart).put("runUntil", runUntil)
            .put("runs", JSONArray().also { r -> runs.forEach { r.put(JSONArray().put(it.start).put(it.minutes)) } })
        JsonFile(ctx, "focus.json").write(o.toString())
    }

    /** Apps that must never be blocked: Koma, the phone app, system screens, keyboards, permission dialogs. */
    private fun computeBase(ctx: Context): Set<String> {
        val s = HashSet<String>()
        s += ctx.packageName
        s += listOf(
            "android", "com.android.systemui", "com.android.incallui", "com.android.phone", "com.android.server.telecom",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller", "com.samsung.android.incallui",
        )
        try {
            ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { s += it }
        } catch (e: Exception) {
        }
        try {
            ctx.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.forEach { s += it.packageName }
        } catch (e: Exception) {
        }
        val dial = ctx.packageManager.resolveActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")), 0)?.activityInfo?.packageName
        if (dial != null) s += dial
        return s
    }

    fun refreshBase(ctx: Context) {
        base = computeBase(ctx)
    }

    /** Koma, system screens, keyboards and the phone app: never timed or blocked. */
    fun isBase(pkg: String) = pkg in base

    fun active(now: Long = System.currentTimeMillis()): FocusActive? {
        if (sessionUntil > now) return FocusActive("Focus", sessionUntil, sessionAllowed.toSet(), sessionStrict, sessionStart)
        for (p in plans) {
            if (!p.enabled) continue
            val until = runningUntil(p, now) ?: continue
            return FocusActive(p.name, until, p.allowed.toSet(), p.strict, until - p.durationMin * 60_000L)
        }
        return null
    }

    /** End time if the plan is running at [now], else null. A recurring plan may have started yesterday. */
    private fun runningUntil(p: FocusPlan, now: Long): Long? {
        val dur = p.durationMin * 60_000L
        if (p.kind == 0) return if (now >= p.onceAt && now < p.onceAt + dur) p.onceAt + dur else null
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        for (back in 0..1) {
            val day = today.minusDays(back.toLong())
            val bit = 1 shl (day.dayOfWeek.value - 1)
            if (p.kind == 2 && (p.days and bit) == 0) continue
            val start = day.atTime(p.startMin / 60, p.startMin % 60).atZone(zone).toInstant().toEpochMilli()
            if (now >= start && now < start + dur) return start + dur
        }
        return null
    }

    fun allows(pkg: String, now: Long = System.currentTimeMillis()): Boolean {
        val a = active(now) ?: return true
        return pkg in a.allowed || pkg in base
    }

    fun isSession(now: Long = System.currentTimeMillis()) = sessionUntil > now

    /** Records finished runs for the stats tab and clears an expired quick session. Call whenever state is refreshed. */
    fun tick(ctx: Context, now: Long = System.currentTimeMillis()) {
        val a = active(now)
        var dirty = false
        if (runUntil != 0L && (a == null || a.until != runUntil)) {
            finish(now)
            dirty = true
        }
        if (a != null && runUntil == 0L) {
            runStart = if (a.start > 0L) a.start else now
            runUntil = a.until
            dirty = true
        }
        if (sessionUntil != 0L && sessionUntil <= now) {
            sessionUntil = 0L
            dirty = true
        }
        if (dirty) save(ctx)
    }

    /** Forgets every finished run, so the stats start over. */
    fun clearRuns(ctx: Context) {
        runs = emptyList()
        save(ctx)
    }

    private fun finish(now: Long) {
        val end = minOf(now, runUntil)
        val m = ((end - runStart) / 60_000L).toInt()
        if (m >= 1) runs = (runs + FocusRun(runStart, m)).takeLast(800)
        runStart = 0L
        runUntil = 0L
    }

    fun extend(ctx: Context, minutes: Int) {
        if (!isSession()) return
        sessionUntil += minutes * 60_000L
        if (runUntil != 0L) runUntil = sessionUntil
        save(ctx)
    }

    fun startSession(ctx: Context, minutes: Int, allowed: List<String>, strict: Boolean) {
        sessionStart = System.currentTimeMillis()
        sessionUntil = sessionStart + minutes * 60_000L
        sessionAllowed = allowed
        sessionStrict = strict
        save(ctx)
    }

    fun endSession(ctx: Context) {
        if (runUntil != 0L) finish(System.currentTimeMillis())
        sessionUntil = 0L
        save(ctx)
    }
}

/**
 * Second layer of Focus Lock. Needs the user to switch it on in Accessibility settings. When an app that is not
 * allowed comes to the front during a session, it sends the user to the home screen (which is Koma).
 */
class KomaFocusService : AccessibilityService() {
    companion object {
        @Volatile
        var instance: KomaFocusService? = null

        fun isRunning() = instance != null

        /** An earlier build could hide the service from the system. Puts it back so it shows in Accessibility settings. */
        fun ensureDeclared(ctx: android.content.Context) {
            try {
                val c = android.content.ComponentName(ctx, KomaFocusService::class.java)
                if (ctx.packageManager.getComponentEnabledSetting(c) == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    ctx.packageManager.setComponentEnabledSetting(c, android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, android.content.pm.PackageManager.DONT_KILL_APP)
                }
            } catch (e: Exception) {
            }
        }

        /** Runs a system action such as GLOBAL_ACTION_NOTIFICATIONS. False when the service is not switched on. */
        fun perform(action: Int): Boolean = instance?.performGlobalAction(action) == true
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var wm: android.view.WindowManager? = null
    private var bar: LimitBar? = null
    private var timed = ""
    private var lastReal = ""
    private var sessionStart = 0L
    private var baseMs = 0L
    private var limitMs = 0L

    private val tick = object : Runnable {
        override fun run() {
            if (timed.isEmpty()) return
            val used = baseMs + (android.os.SystemClock.elapsedRealtime() - sessionStart)
            val frac = (used.toFloat() / limitMs).coerceIn(0f, 1f)
            bar?.set(frac)
            if (used >= limitMs) {
                val pkg = timed
                AppLimits.lock(this@KomaFocusService, pkg)
                stopTimer()
                lockOut(pkg)
            } else handler.postDelayed(this, 1000L)
        }
    }

    private val screen = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == Intent.ACTION_SCREEN_OFF) stopTimer()
            else if (lastReal.isNotEmpty()) handleLimits(lastReal)
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        stopTimer()
        try { unregisterReceiver(screen) } catch (e: Exception) { }
        return super.onUnbind(intent)
    }

    override fun onServiceConnected() {
        instance = this
        FocusEngine.init(this)
        FocusEngine.refreshBase(this)
        try {
            val f = android.content.IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_USER_PRESENT) }
            androidx.core.content.ContextCompat.registerReceiver(this, screen, f, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
        }
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (!FocusEngine.allows(pkg)) {
            stopTimer()
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }
        handleLimits(pkg, event.className?.toString().orEmpty())
    }

    /** Starts, keeps or stops the limit bar for the app that just came to the front. */
    private fun handleLimits(pkg: String, cls: String = "Activity") {
        if (pkg == packageName) {
            // The bar is a window of Koma's own, and adding it raises an event too. Only a real screen of Koma means "home".
            if (!cls.endsWith("Activity")) return
            lastReal = ""
            stopTimer()
            return
        }
        if (FocusEngine.isBase(pkg)) return
        lastReal = pkg
        val lim = AppLimits.get(this, pkg)
        if (lim == null) {
            stopTimer()
            return
        }
        if (AppLimits.isLocked(this, pkg)) {
            stopTimer()
            lockOut(pkg)
            return
        }
        if (timed == pkg) return
        stopTimer()
        val used = try { AppLimits.usedMs(this, pkg, lim) } catch (e: Exception) { 0L }
        val limit = lim.min * 60_000L
        if (used >= limit) {
            AppLimits.lock(this, pkg)
            lockOut(pkg)
            return
        }
        timed = pkg
        baseMs = used
        limitMs = limit
        sessionStart = android.os.SystemClock.elapsedRealtime()
        showBar((used.toFloat() / limit).coerceIn(0f, 1f))
        handler.postDelayed(tick, 1000L)
    }

    private fun lockOut(pkg: String) {
        performGlobalAction(GLOBAL_ACTION_HOME)
        val name = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            "This app"
        }
        android.widget.Toast.makeText(this, "$name: daily limit reached. Turn it back on in Koma, under App limits.", android.widget.Toast.LENGTH_LONG).show()
    }

    private fun stopTimer() {
        handler.removeCallbacks(tick)
        timed = ""
        try {
            bar?.let { wm?.removeView(it) }
        } catch (e: Exception) {
        }
        bar = null
    }

    /** The limit indicator follows the theme: its first tile colour. */
    private fun markColour(): Int =
        app.koma.launcher.ui.savedPalette(this)?.tiles?.firstOrNull()?.toArgb() ?: android.graphics.Color.rgb(0xF2, 0xB5, 0x3C)

    private fun showBar(frac: Float) {
        try {
            val sp = getSharedPreferences("koma", Context.MODE_PRIVATE)
            val ring = sp.getString("limit_style", "line") == "ring"
            val left = sp.getString("limit_side", "right") == "left"
            val w = wm ?: (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).also { wm = it }
            val d = resources.displayMetrics.density
            val statusId = resources.getIdentifier("status_bar_height", "dimen", "android")
            val statusH = if (statusId > 0) resources.getDimensionPixelSize(statusId) else (24 * d).toInt()
            val stroke = 2.5f * d

            // The camera hole, when the phone reports one: the ring is drawn around it and sized to it.
            var cam: android.graphics.Rect? = null
            if (ring && android.os.Build.VERSION.SDK_INT >= 30) {
                try {
                    val r = w.currentWindowMetrics.windowInsets.displayCutout?.boundingRectTop
                    if (r != null && !r.isEmpty && r.width() < resources.displayMetrics.widthPixels / 3) cam = r
                } catch (e: Exception) {
                }
            }
            // Ring radius: around the camera with a small gap, or a small ring in the middle of the status bar.
            val radius = if (cam != null) maxOf(cam.width(), cam.height()) / 2f + 3 * d else 7 * d
            val box = ((radius + stroke + 3 * d) * 2).toInt()

            val view = LimitBar(this, markColour(), ring, radius)
            view.set(frac)
            val lp = android.view.WindowManager.LayoutParams(
                if (ring) box else android.view.WindowManager.LayoutParams.MATCH_PARENT,
                if (ring) box else (4 * d).toInt(),
                android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                if (ring) {
                    if (cam != null) {
                        x = cam.centerX() - box / 2
                        y = cam.centerY() - box / 2
                    } else {
                        // No camera hole: beside the middle of the status bar, where no app content is.
                        val cx = resources.displayMetrics.widthPixels / 2
                        val gap = (10 * d).toInt()
                        x = if (left) cx - gap - box else cx + gap
                        y = ((statusH - box) / 2).coerceAtLeast(0)
                    }
                } else {
                    x = 0
                    y = statusH - (2 * d).toInt()
                }
                if (android.os.Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                if (android.os.Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
            }
            w.addView(view, lp)
            bar = view
        } catch (e: Exception) {
            RingLog.add(this, "limit bar could not be drawn: ${e.javaClass.simpleName} ${e.message}")
        }
    }

    /**
     * The limit indicator: a slim rounded line under the status bar, or a small ring in a top corner. Either has a faint
     * track and a soft dark edge so it reads on light and dark apps, the used part in the chosen colour, easing to red
     * as the limit comes close.
     */
    private class LimitBar(ctx: Context, private val color: Int, private val ring: Boolean, private val radius: Float = 0f) : android.view.View(ctx) {
        private var frac = 0f
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private val rect = android.graphics.RectF()
        private val d = ctx.resources.displayMetrics.density

        fun set(f: Float) {
            frac = f
            invalidate()
        }

        private fun used(): Int {
            val warn = ((frac - 0.85f) / 0.15f).coerceIn(0f, 1f)
            return androidx.core.graphics.ColorUtils.blendARGB(color, android.graphics.Color.rgb(0xE5, 0x53, 0x4B), warn)
        }

        override fun onDraw(c: android.graphics.Canvas) {
            if (ring) drawRing(c) else drawLine(c)
        }

        private fun drawLine(c: android.graphics.Canvas) {
            // Edge to edge and thin: a faint track of the theme colour with the used part solid on top.
            val thick = 2 * d
            val top = (height - thick) / 2f
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = android.graphics.Color.argb(50, 0, 0, 0)
            c.drawRect(0f, top - d / 2f, width.toFloat(), top + thick + d / 2f, paint)
            paint.color = androidx.core.graphics.ColorUtils.setAlphaComponent(color, 100)
            c.drawRect(0f, top, width.toFloat(), top + thick, paint)
            paint.color = used()
            c.drawRect(0f, top, width * frac, top + thick, paint)
        }

        private fun drawRing(c: android.graphics.Canvas) {
            val stroke = 2.5f * d
            val size = radius * 2
            val cx = width / 2f
            val cy = height / 2f
            rect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeCap = android.graphics.Paint.Cap.ROUND
            paint.strokeWidth = stroke + 2 * d
            paint.color = android.graphics.Color.argb(70, 0, 0, 0)
            c.drawArc(rect, 0f, 360f, false, paint)
            paint.strokeWidth = stroke
            paint.color = androidx.core.graphics.ColorUtils.setAlphaComponent(color, 90)
            c.drawArc(rect, 0f, 360f, false, paint)
            paint.color = used()
            c.drawArc(rect, -90f, 360f * frac.coerceAtLeast(0.02f), false, paint)
        }
    }

    override fun onInterrupt() {}
}
