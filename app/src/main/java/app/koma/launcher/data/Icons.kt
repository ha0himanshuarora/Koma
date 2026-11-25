package app.koma.launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import kotlin.math.cos
import kotlin.math.sin

/** An icon bitmap. When [mask] is true it is a silhouette to be tinted; otherwise it is the app's own artwork. */
class Icon(val bitmap: ImageBitmap, val mask: Boolean)

/** How an icon should look: style and shape ids, plus the colours of the place it sits on. */
class IconSpec(val style: String, val shape: String, val bg: Int, val ink: Int, val line: Int, val force: Boolean)

object IconStyles {
    val styles = listOf(
        "koma" to "KOMA", "original" to "ORIGINAL", "material" to "MATERIAL", "sticker" to "STICKER", "dots" to "DOTS",
        "outline" to "OUTLINE", "watercolor" to "WATERCOLOR", "marble" to "MARBLE", "bright" to "BRIGHT",
        "stellar" to "STELLAR", "chroma" to "OUTLINE CHROMA",
    )
    /** How the icon on a shortcut tile (CAM, WEB, ...) is held. */
    val badges = listOf(
        "circle" to "CIRCLE", "squircle" to "SQUIRCLE", "teardrop" to "TEARDROP", "ring" to "RING", "plain" to "PLAIN",
    )
    /** How the device icon on the Bluetooth tile is drawn. */
    val btIcons = listOf("outline" to "OUTLINE", "solid" to "SOLID", "duotone" to "DUOTONE", "tessera" to "TESSERA")
    val shapes = listOf("circle" to "CIRCLE", "squircle" to "SQUIRCLE", "rounded" to "ROUNDED", "teardrop" to "TEARDROP", "square" to "SQUARE")
}

/** A third-party icon pack (Nova, ADW and similar): maps app components to drawables in the pack. */
class IconPack private constructor(private val res: Resources, val pkg: String) {
    private val map = HashMap<String, String>()

    private fun parse() {
        val id = res.getIdentifier("appfilter", "xml", pkg)
        val parser: XmlPullParser = if (id != 0) {
            res.getXml(id)
        } else {
            val stream = res.assets.open("appfilter.xml")
            XmlPullParserFactory.newInstance().newPullParser().also { it.setInput(stream, null) }
        }
        var ev = parser.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                val comp = parser.getAttributeValue(null, "component")
                val dr = parser.getAttributeValue(null, "drawable")
                if (comp != null && dr != null && comp.startsWith("ComponentInfo{") && comp.endsWith("}")) {
                    map[comp.removePrefix("ComponentInfo{").removeSuffix("}")] = dr
                }
            }
            ev = parser.next()
        }
    }

    fun drawable(app: AppInfo): Drawable? {
        val name = map["${app.pkg}/${app.cls}"] ?: return null
        val id = res.getIdentifier(name, "drawable", pkg)
        if (id == 0) return null
        return try {
            res.getDrawable(id, null)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        fun load(ctx: Context, pkg: String): IconPack? = try {
            IconPack(ctx.packageManager.getResourcesForApplication(pkg), pkg).also { it.parse() }
        } catch (e: Exception) {
            null
        }

        /** Installed icon packs as (package, label). */
        fun installed(ctx: Context): List<Pair<String, String>> {
            val pm = ctx.packageManager
            val actions = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme")
            val seen = LinkedHashMap<String, String>()
            for (a in actions) {
                for (r in pm.queryIntentActivities(Intent(a), 0)) {
                    seen[r.activityInfo.packageName] = r.loadLabel(pm).toString()
                }
            }
            return seen.map { it.key to it.value }.sortedBy { it.second.lowercase() }
        }
    }
}

/**
 * Renders app icons at a fixed pixel size and keeps them in memory. Besides the app's own icon it can restyle
 * every icon (shape, colour, outline and so on), even when the app ignores adaptive icons: legacy icons with a
 * transparent background are cut out, and legacy icons with a solid background get a generated letter mark.
 */
class IconCache(private val ctx: Context, private val sizePx: Int) {
    private val pm = ctx.packageManager
    private val cache = LruCache<String, ImageBitmap>(300)
    private val silhouettes = LruCache<String, Icon>(160)
    private var pack: IconPack? = null
    private var packId = ""

    fun usePack(id: String) {
        if (id == packId) return
        packId = id
        pack = if (id.isEmpty()) null else IconPack.load(ctx, id)
        clear()
    }

    private class Src(val full: Bitmap, val color: Bitmap?, val mask: Bitmap?, val dominant: Int, val packed: Boolean, val scale: Float, val derived: Boolean = false)

    private fun layer(d: Drawable): Bitmap {
        // Adaptive layers are 108dp with the visible 72dp in the middle: scale so the middle fills the bitmap.
        val b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val off = sizePx / 4
        d.setBounds(-off, -off, sizePx + off, sizePx + off)
        d.draw(Canvas(b))
        return b
    }

    private fun opaqueRatio(b: Bitmap): Float {
        val s = Bitmap.createScaledBitmap(b, 16, 16, true)
        var n = 0
        for (y in 0 until 16) for (x in 0 until 16) if (Color.alpha(s.getPixel(x, y)) > 128) n++
        return n / 256f
    }

    private fun cornersClear(b: Bitmap): Boolean {
        val w = b.width - 2
        val h = b.height - 2
        return Color.alpha(b.getPixel(1, 1)) < 20 && Color.alpha(b.getPixel(w, 1)) < 20 &&
            Color.alpha(b.getPixel(1, h)) < 20 && Color.alpha(b.getPixel(w, h)) < 20
    }

    private fun dominant(b: Bitmap): Int {
        val s = Bitmap.createScaledBitmap(b, 8, 8, true)
        var r = 0L
        var g = 0L
        var bl = 0L
        var n = 0
        for (y in 0 until 8) for (x in 0 until 8) {
            val p = s.getPixel(x, y)
            if (Color.alpha(p) > 80) {
                r += Color.red(p)
                g += Color.green(p)
                bl += Color.blue(p)
                n++
            }
        }
        if (n == 0) return 0xFF7A7A7A.toInt()
        val hsv = FloatArray(3)
        Color.colorToHSV(Color.rgb((r / n).toInt(), (g / n).toInt(), (bl / n).toInt()), hsv)
        if (hsv[1] >= 0.12f) hsv[1] = hsv[1].coerceIn(0.45f, 1f)
        hsv[2] = hsv[2].coerceIn(0.55f, 1f)
        return Color.HSVToColor(hsv)
    }

    /**
     * For icons with no usable cut-out (a full-bleed adaptive icon, a legacy square icon), work one out from the
     * finished picture: the background colour is read from a ring just inside the edge, and every pixel that is
     * clearly different from it becomes the silhouette. Returns the mask and a colour copy, or null when the icon
     * is one solid block (nothing to separate) or nearly empty.
     */
    private fun derive(full: Bitmap): Pair<Bitmap, Bitmap>? {
        val w = full.width
        val h = full.height
        val px = IntArray(w * h)
        full.getPixels(px, 0, w, 0, 0, w, h)
        val inset = (w * 0.1f).toInt().coerceAtLeast(2)
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (i in 0 until 48) {
            val a = i * Math.PI * 2 / 48
            val x = (w / 2 + cos(a) * (w / 2 - inset)).toInt().coerceIn(0, w - 1)
            val y = (h / 2 + sin(a) * (h / 2 - inset)).toInt().coerceIn(0, h - 1)
            val c = px[y * w + x]
            if (Color.alpha(c) > 200) {
                r += Color.red(c)
                g += Color.green(c)
                b += Color.blue(c)
                n++
            }
        }
        if (n < 12) return null
        val br = (r / n).toInt()
        val bg = (g / n).toInt()
        val bb = (b / n).toInt()
        val edge = (w * 0.07f).toInt()
        val maskPx = IntArray(w * h)
        val colorPx = IntArray(w * h)
        var opaque = 0
        for (y in 0 until h) for (x in 0 until w) {
            val c = px[y * w + x]
            if (x < edge || y < edge || x >= w - edge || y >= h - edge || Color.alpha(c) < 200) continue
            val dr = Color.red(c) - br
            val dg = Color.green(c) - bg
            val db = Color.blue(c) - bb
            val dist = kotlin.math.sqrt((dr * dr + dg * dg + db * db).toFloat())
            val a = ((dist - 28f) / 50f).coerceIn(0f, 1f)
            if (a <= 0f) continue
            val al = (a * 255).toInt()
            maskPx[y * w + x] = Color.argb(al, 0, 0, 0)
            colorPx[y * w + x] = Color.argb(al, Color.red(c), Color.green(c), Color.blue(c))
            if (al > 128) opaque++
        }
        val ratio = opaque / (w * h).toFloat()
        if (ratio < 0.03f || ratio > 0.75f) return null
        val m = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        m.setPixels(maskPx, 0, w, 0, 0, w, h)
        val c = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        c.setPixels(colorPx, 0, w, 0, 0, w, h)
        return m to c
    }

    /** The picture of a pinned shortcut, such as a website a browser added. */
    private fun shortcutIcon(app: AppInfo): Drawable? = try {
        val la = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val q = LauncherApps.ShortcutQuery()
            .setPackage(app.pkg)
            .setShortcutIds(listOf(app.cls.removePrefix(Apps.SHORTCUT)))
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        la.getShortcuts(q, Process.myUserHandle())?.firstOrNull()?.let { la.getShortcutIconDrawable(it, 0) }
    } catch (e: Exception) {
        null
    }

    private fun source(app: AppInfo): Src? {
        val shortcut = Apps.isShortcut(app)
        val packed = if (shortcut) null else pack?.drawable(app)
        if (packed != null) {
            val b = packed.toBitmap(sizePx, sizePx)
            return Src(b, null, null, dominant(b), true, 0.78f)
        }
        val d = if (shortcut) {
            shortcutIcon(app) ?: return null
        } else try {
            pm.getActivityIcon(ComponentName(app.pkg, app.cls))
        } catch (e: PackageManager.NameNotFoundException) {
            try { pm.getApplicationIcon(app.pkg) } catch (e2: PackageManager.NameNotFoundException) { return null }
        }
        val full = d.toBitmap(sizePx, sizePx)
        var color: Bitmap? = null
        var mask: Bitmap? = null
        var scale = 0.7f
        if (d is AdaptiveIconDrawable) {
            scale = 0.8f
            val fg = d.foreground?.let { layer(it) }
            val mono = if (Build.VERSION.SDK_INT >= 33) d.monochrome?.let { layer(it) } else null
            // A real glyph covers only part of the icon. A layer that is mostly solid is a plate (a coloured
            // square or circle with the logo drawn on it), and outlining that would just draw a square.
            fun glyphy(b: Bitmap) = opaqueRatio(b) in 0.015f..0.5f
            when {
                mono != null && glyphy(mono) -> { color = fg ?: mono; mask = mono }
                fg != null && glyphy(fg) -> { color = fg; mask = fg }
                fg != null -> derive(fg)?.let { color = it.second; mask = it.first; scale = 0.7f }
            }
        } else if (cornersClear(full) && opaqueRatio(full) < 0.62f) {
            color = full
            mask = full
        }
        var derived = false
        if (mask == null) {
            val dv = derive(full)
            if (dv != null) {
                derived = true
                mask = dv.first
                color = dv.second
                scale = 0.7f
            }
        }
        return Src(full, color, mask, dominant(color ?: full), false, scale, derived)
    }

    /** The app's own icon (or the chosen icon pack's). */
    fun original(app: AppInfo): ImageBitmap? {
        val key = "o|${app.key}|$packId"
        cache.get(key)?.let { return it }
        val b = source(app)?.full?.asImageBitmap() ?: return null
        cache.put(key, b)
        return b
    }

    /** A cut-out for tinting on a tile. Icons that cannot be cut out come back as they are. */
    fun silhouette(app: AppInfo): Icon? {
        val key = "${app.key}|$packId"
        silhouettes.get(key)?.let { return it }
        val s = source(app) ?: return null
        val icon = if (s.mask != null && !s.packed) Icon(s.mask.asImageBitmap(), true) else Icon(s.full.asImageBitmap(), false)
        silhouettes.put(key, icon)
        return icon
    }

    /** The icon drawn in the chosen style and shape. */
    fun styled(app: AppInfo, spec: IconSpec): ImageBitmap? {
        if (spec.style == "original") return original(app)
        val key = "${app.key}|${spec.style}|${spec.shape}|${spec.bg}|${spec.ink}|${spec.line}|${spec.force}|$packId"
        cache.get(key)?.let { return it }
        val src = source(app) ?: return null
        val bmp = if (src.packed) src.full else paint(app, src, spec)
        val img = bmp.asImageBitmap()
        cache.put(key, img)
        return img
    }

    fun clear() {
        cache.evictAll()
        silhouettes.evictAll()
    }

    // ---- painting ------------------------------------------------------------------------------

    private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)

    private fun tone(c: Int, s: Float, v: Float, hueShift: Float = 0f): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[0] = (hsv[0] + hueShift + 360f) % 360f
        hsv[1] = s
        hsv[2] = v
        return Color.HSVToColor(hsv)
    }

    private fun luminance(c: Int) = (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f

    private fun tint(b: Bitmap, color: Int): Bitmap {
        val o = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        p.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        Canvas(o).drawBitmap(b, 0f, 0f, p)
        return o
    }

    private fun drawIn(c: Canvas, b: Bitmap, size: Float, scale: Float, paint: Paint? = null, dx: Float = 0f, dy: Float = 0f) {
        val s = size * scale
        val l = (size - s) / 2f
        c.drawBitmap(b, null, RectF(l + dx, l + dy, l + s + dx, l + s + dy), paint ?: Paint(Paint.FILTER_BITMAP_FLAG))
    }

    /** Only the outline of a silhouette: the shape grown by [width], with the shape itself cut out. */
    private fun ring(mask: Bitmap, color: Int, size: Float, scale: Float, width: Float): Bitmap {
        val o = Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(o)
        val t = tint(mask, color)
        for (i in 0 until 12) {
            val a = i * Math.PI * 2 / 12
            drawIn(c, t, size, scale, null, (cos(a) * width).toFloat(), (sin(a) * width).toFloat())
        }
        val clear = Paint(Paint.FILTER_BITMAP_FLAG)
        clear.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        drawIn(c, mask, size, scale, clear)
        return o
    }

    private fun shapePath(shape: String, f: Float): Path {
        val p = Path()
        val r = RectF(0f, 0f, f, f)
        when (shape) {
            "circle" -> p.addCircle(f / 2, f / 2, f / 2, Path.Direction.CW)
            "square" -> p.addRoundRect(r, f * 0.06f, f * 0.06f, Path.Direction.CW)
            "rounded" -> p.addRoundRect(r, f * 0.2f, f * 0.2f, Path.Direction.CW)
            "teardrop" -> p.addRoundRect(
                r, floatArrayOf(f / 2, f / 2, f / 2, f / 2, f * 0.1f, f * 0.1f, f / 2, f / 2), Path.Direction.CW,
            )
            else -> p.addRoundRect(r, f * 0.34f, f * 0.34f, Path.Direction.CW)
        }
        return p
    }

    private fun paint(app: AppInfo, src: Src, spec: IconSpec): Bitmap {
        val f = sizePx.toFloat()
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val shape = shapePath(spec.shape, f)
        val dom = src.dominant
        val useCut = !src.derived || spec.force
        val mask = if (useCut) src.mask else null
        val color = if (useCut) src.color else null
        val gs = src.scale
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        fun fillShape(col: Int) {
            fill.shader = null
            fill.color = col
            c.drawPath(shape, fill)
        }

        fun letter(col: Int) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = col
            p.textSize = f * 0.5f
            p.typeface = Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            val ch = app.label.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
            c.drawText(ch, f / 2, f / 2 - (p.descent() + p.ascent()) / 2, p)
        }

        // A shape with a one-colour glyph. Used by several styles.
        fun onShape(bg: Int, glyph: Int) {
            fillShape(bg)
            when {
                mask != null -> drawIn(c, tint(mask, glyph), f, gs)
                spec.force -> letter(glyph)
                else -> {
                    c.save()
                    c.clipPath(shape)
                    c.drawBitmap(src.full, 0f, 0f, null)
                    c.restore()
                }
            }
        }

        fun material() = onShape(tone(dom, 0.22f, 0.96f), tone(dom, 0.85f, 0.38f))

        when (spec.style) {
            "material" -> material()
            "bright" -> {
                val bg = tone(dom, 0.95f, 1f)
                onShape(bg, if (luminance(bg) > 0.72f) 0xFF101010.toInt() else Color.WHITE)
            }
            "sticker" -> {
                if (color == null) {
                    material()
                } else {
                    drawIn(c, tint(color, 0x55000000), f, gs * 0.92f, null, 0f, f * 0.03f)
                    val white = tint(color, Color.WHITE)
                    val w = f * 0.035f
                    for (i in 0 until 12) {
                        val a = i * Math.PI * 2 / 12
                        drawIn(c, white, f, gs * 0.92f, null, (cos(a) * w).toFloat(), (sin(a) * w).toFloat())
                    }
                    drawIn(c, color, f, gs * 0.92f)
                }
            }
            "dots" -> {
                if (color == null) {
                    material()
                } else {
                    val n = 14
                    val tmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                    drawIn(Canvas(tmp), color, f, gs)
                    val sm = Bitmap.createScaledBitmap(tmp, n, n, true)
                    val cell = f / n
                    val dot = Paint(Paint.ANTI_ALIAS_FLAG)
                    for (y in 0 until n) for (x in 0 until n) {
                        val p = sm.getPixel(x, y)
                        val a = Color.alpha(p)
                        if (a > 40) {
                            dot.color = withAlpha(p, 255)
                            c.drawCircle((x + 0.5f) * cell, (y + 0.5f) * cell, cell * 0.5f * (a / 255f) * 0.95f, dot)
                        }
                    }
                }
            }
            "outline" -> {
                if (mask == null) {
                    val st = Paint(Paint.ANTI_ALIAS_FLAG)
                    st.style = Paint.Style.STROKE
                    st.strokeWidth = f * 0.035f
                    st.color = spec.line
                    c.drawPath(shape, st)
                    letter(spec.line)
                } else {
                    c.drawBitmap(ring(mask, spec.line, f, gs, f * 0.028f), 0f, 0f, null)
                }
            }
            "watercolor" -> {
                val bp = Paint(Paint.ANTI_ALIAS_FLAG)
                bp.maskFilter = BlurMaskFilter(f * 0.07f, BlurMaskFilter.Blur.NORMAL)
                bp.color = withAlpha(dom, 150)
                c.drawCircle(f * 0.46f, f * 0.54f, f * 0.34f, bp)
                bp.color = withAlpha(tone(dom, 0.6f, 1f, 25f), 120)
                c.drawCircle(f * 0.58f, f * 0.44f, f * 0.26f, bp)
                if (color != null) drawIn(c, color, f, gs * 0.85f) else letter(tone(dom, 0.9f, 0.4f))
            }
            "marble" -> {
                fill.shader = LinearGradient(0f, 0f, f, f, tone(dom, 0.5f, 1f), tone(dom, 0.9f, 0.55f, 40f), Shader.TileMode.CLAMP)
                c.drawPath(shape, fill)
                fill.shader = RadialGradient(f * 0.3f, f * 0.25f, f * 0.7f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
                c.drawPath(shape, fill)
                fill.shader = null
                if (mask != null) {
                    drawIn(c, tint(mask, 0x55000000), f, gs, null, 0f, f * 0.02f)
                    drawIn(c, tint(mask, Color.WHITE), f, gs)
                } else letter(Color.WHITE)
            }
            "stellar" -> {
                fillShape(0xFF0A0B16.toInt())
                if (mask != null) {
                    val glow = Paint(Paint.FILTER_BITMAP_FLAG)
                    glow.maskFilter = BlurMaskFilter(f * 0.06f, BlurMaskFilter.Blur.NORMAL)
                    val g = tint(mask, dom)
                    drawIn(c, g, f, gs, glow)
                    drawIn(c, g, f, gs, glow)
                    drawIn(c, tint(mask, Color.WHITE), f, gs * 0.96f)
                } else letter(Color.WHITE)
                val star = Paint(Paint.ANTI_ALIAS_FLAG)
                star.color = 0xCCFFFFFF.toInt()
                c.drawCircle(f * 0.2f, f * 0.22f, f * 0.02f, star)
                c.drawCircle(f * 0.82f, f * 0.3f, f * 0.015f, star)
                c.drawCircle(f * 0.72f, f * 0.82f, f * 0.02f, star)
            }
            "chroma" -> {
                fillShape(0xFF101114.toInt())
                val col = tone(dom, 0.85f, 1f)
                if (mask != null) {
                    drawIn(c, tint(mask, withAlpha(col, 60)), f, gs * 0.9f)
                    c.drawBitmap(ring(mask, col, f, gs * 0.9f, f * 0.03f), 0f, 0f, null)
                } else letter(col)
            }
            else -> onShape(spec.bg, spec.ink)
        }
        return out
    }
}
