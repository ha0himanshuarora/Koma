package app.koma.launcher.ui

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import android.net.Uri
import java.io.File
import androidx.compose.runtime.setValue
import app.koma.launcher.R

/**
 * A colour scheme: a ground colour, five tile fills, and one ink colour used for all text on tiles
 * (text on a tile is always [ink], never mixed, so any scheme stays legible).
 */
@Immutable
class Palette(
    val id: String,
    val name: String,
    val ground: Color,
    val tiles: List<Color>,
    val ink: Color,
    val onGround: Color,
    /** More colours taken from the wallpaper. Tiles, folders and the colour pickers can use them too. */
    val extra: List<Color> = emptyList(),
) {
    /** Every colour a tile can take: the five tile fills, then the extra wallpaper colours. */
    val all: List<Color> get() = tiles + extra

    fun withExtra(e: List<Color>) = Palette(id, name, ground, tiles, ink, onGround, e)

    /** True for a dark scheme (a dark ground). */
    val isDark: Boolean get() = ground.luminance() < 0.5f
}

/** A named preset with a dark and a light version, so the mode setting can pick either. */
class Preset(val id: String, val name: String, val dark: Palette, val light: Palette) {
    fun pick(isDark: Boolean): Palette = if (isDark) dark else light
}

object Palettes {
    private fun p(id: String, name: String, ground: Long, tiles: List<Long>, ink: Long, onGround: Long) =
        Palette(id, name, Color(ground), tiles.map { Color(it) }, Color(ink), Color(onGround))

    val amber = Preset("amber", "Amber Signal",
        p("amber", "Amber Signal", 0xFF101010, listOf(0xFFF2A541, 0xFFE5482D, 0xFF9DC08B, 0xFFA9D3E8, 0xFFF1E8D4), 0xFF101010, 0xFFF1E8D4),
        p("amber", "Amber Signal", 0xFFF4EBD8, listOf(0xFFF2A541, 0xFFE5482D, 0xFF9DC08B, 0xFFA9D3E8, 0xFFFFFAF0), 0xFF101010, 0xFF1B1712))
    val rose = Preset("rose", "Rose Dusk",
        p("rose", "Rose Dusk", 0xFF0F2247, listOf(0xFFE8195F, 0xFFF3C79A, 0xFFF8EDD3, 0xFF5B7DB8, 0xFFFF8FB1), 0xFF0F2247, 0xFFF8EDD3),
        p("rose", "Rose Dusk", 0xFFFFF1EA, listOf(0xFFE8195F, 0xFFF3C79A, 0xFFFFFAF2, 0xFF5B7DB8, 0xFFFF8FB1), 0xFF0F2247, 0xFF0F2247))
    val sunrise = Preset("sunrise", "Sunrise",
        p("sunrise", "Sunrise", 0xFF2F6392, listOf(0xFFF2C500, 0xFFA9D3E8, 0xFFF4F4F0, 0xFFFFE27A, 0xFFD7ECF7), 0xFF1F4468, 0xFFF4F4F0),
        p("sunrise", "Sunrise", 0xFFE6F1FA, listOf(0xFFF2C500, 0xFFA9D3E8, 0xFFFFFFFF, 0xFFFFE27A, 0xFFBFDDF0), 0xFF1F4468, 0xFF1F4468))
    val ember = Preset("ember", "Ember",
        p("ember", "Ember", 0xFF1A0F0A, listOf(0xFFFF6B2C, 0xFFFFB347, 0xFFF25C54, 0xFFFFD9A0, 0xFFF4E6D8), 0xFF1A0F0A, 0xFFF4E6D8),
        p("ember", "Ember", 0xFFFFF0E4, listOf(0xFFFF6B2C, 0xFFFFB347, 0xFFF25C54, 0xFFFFD9A0, 0xFFFFFAF4), 0xFF1A0F0A, 0xFF2A1A12))
    val mint = Preset("mint", "Mint Night",
        p("mint", "Mint Night", 0xFF0B1B1A, listOf(0xFF5EEAD4, 0xFFA7F3D0, 0xFF7DD3FC, 0xFFFDE68A, 0xFFE6FFF8), 0xFF0B1B1A, 0xFFE6FFF8),
        p("mint", "Mint Night", 0xFFE7F8F3, listOf(0xFF5EEAD4, 0xFFA7F3D0, 0xFF7DD3FC, 0xFFFDE68A, 0xFFFFFFFF), 0xFF0B1B1A, 0xFF0B2A28))
    val sage = Preset("sage", "Sage Cream",
        p("sage", "Sage Cream", 0xFF171D17, listOf(0xFFA9BF96, 0xFFE8C99B, 0xFFE39A7B, 0xFFCFE0D2, 0xFFFBF7EC), 0xFF1F2A1F, 0xFFE6EDE0),
        p("sage", "Sage Cream", 0xFFF3EDE0, listOf(0xFFA9BF96, 0xFFE8C99B, 0xFFE39A7B, 0xFFCFE0D2, 0xFFFBF7EC), 0xFF1F2A1F, 0xFF2B3A2B))
    val noir = Preset("noir", "Noir Red",
        p("noir", "Noir Red", 0xFF000000, listOf(0xFFE10600, 0xFFF2F2F2, 0xFFB5B5B5, 0xFFFF5A4D, 0xFFE8E8E8), 0xFF000000, 0xFFF2F2F2),
        p("noir", "Noir Red", 0xFFECECEC, listOf(0xFFE10600, 0xFFFFFFFF, 0xFFC4C4C4, 0xFFFF5A4D, 0xFFF7F7F7), 0xFF000000, 0xFF000000))
    val lavender = Preset("lavender", "Lavender Haze",
        p("lavender", "Lavender Haze", 0xFF16112B, listOf(0xFFB69CFF, 0xFFFF9EC7, 0xFF8FD3FF, 0xFFFFE08A, 0xFFEDE7FF), 0xFF16112B, 0xFFEDE7FF),
        p("lavender", "Lavender Haze", 0xFFF3EEFF, listOf(0xFFB69CFF, 0xFFFF9EC7, 0xFF8FD3FF, 0xFFFFE08A, 0xFFFFFFFF), 0xFF16112B, 0xFF16112B))
    val ocean = Preset("ocean", "Ocean Deep",
        p("ocean", "Ocean Deep", 0xFF06202E, listOf(0xFF1FB5C9, 0xFF7FE0D0, 0xFFFF8A5C, 0xFFBFE9F5, 0xFFF2FBFD), 0xFF06202E, 0xFFF2FBFD),
        p("ocean", "Ocean Deep", 0xFFE6F6F9, listOf(0xFF1FB5C9, 0xFF7FE0D0, 0xFFFF8A5C, 0xFFBFE9F5, 0xFFFFFFFF), 0xFF06202E, 0xFF06202E))
    val peach = Preset("peach", "Peach Cloud",
        p("peach", "Peach Cloud", 0xFF2A1B17, listOf(0xFFFFB5A0, 0xFFF7D08A, 0xFFB8D8BA, 0xFFA8C6EA, 0xFFFFE9DF), 0xFF3A2620, 0xFFFBE9DF),
        p("peach", "Peach Cloud", 0xFFFBF1EA, listOf(0xFFFFB5A0, 0xFFF7D08A, 0xFFB8D8BA, 0xFFA8C6EA, 0xFFFFE9DF), 0xFF3A2620, 0xFF3A2620))

    val presets = listOf(amber, rose, sunrise, ember, mint, sage, noir, lavender, ocean, peach)

    /** The dark Amber Signal, used before settings are read. */
    val AmberSignal: Palette get() = amber.dark

    fun byId(id: String, dark: Boolean = true): Palette = (presets.firstOrNull { it.id == id } ?: amber).pick(dark)
}

/**
 * Colours that follow the wallpaper (Material You style) or a chosen seed colour. The wallpaper gives up to three
 * colours; each variant turns them into the same five tile fills, a ground and one ink.
 */
object Dynamic {
    val variants = listOf("tonal" to "BALANCED", "vibrant" to "VIVID", "expressive" to "PLAYFUL", "muted" to "MUTED", "mono" to "MONOCHROME")

    val seedChoices = listOf(
        0xFFF2A541, 0xFFE5482D, 0xFFE8195F, 0xFFB45CFF, 0xFF5B7DB8, 0xFF2BB5A5,
        0xFF6BCB77, 0xFFF2C500, 0xFFFF6B2C, 0xFFA9D3E8, 0xFF9DC08B, 0xFFE8C99B,
    ).map { it.toInt() }

    /**
     * Primary, secondary and tertiary colours of the current wallpaper as ARGB, or null when the phone shares none.
     * Some phones return nothing for the system or live wallpaper, so the lock-screen colours and then the picture
     * itself are tried before giving up.
     */
    /** Where the last set of seeds came from: "wallpaper", "lock", "picture", "palette" or "none". Shown in Settings. */
    var lastSource: String = "none"
        private set

    private fun hueGap(a: Int, b: Int): Float {
        val d = Math.abs(hue(a) - hue(b))
        return minOf(d, 360f - d)
    }

    /** Adds [c] when no colour in [out] is already close to it. Stops at [max] colours. */
    private fun addDistinct(out: MutableList<Int>, c: Int, max: Int = 8) {
        if (out.size >= max) return
        if (out.none { hueGap(it, c) < 18f && Math.abs(sat(it) - sat(c)) < 0.3f }) out += c
    }

    private fun pictureColours(wm: WallpaperManager?): List<Int>? = try {
        val d = wm?.peekDrawable()
        if (d == null) null else {
            val b = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, 64, 64)
            d.draw(android.graphics.Canvas(b))
            colorsFromBitmap(b, 8)
        }
    } catch (e: Throwable) {
        null
    }

    /**
     * Up to eight colours of the current wallpaper as ARGB (at least three), or null when the phone shares none.
     * The first three are the phone's primary, secondary and tertiary colours; the rest come from the picture itself
     * and the system palette. Some phones share nothing for the wallpaper, so the lock screen and then the system
     * palette are tried before giving up.
     */
    fun wallpaperSeeds(ctx: Context): List<Int>? {
        lastSource = "none"
        val out = ArrayList<Int>()
        val wm = try { WallpaperManager.getInstance(ctx) } catch (e: Exception) { null }
        if (wm != null && Build.VERSION.SDK_INT >= 27) {
            for ((which, name) in listOf(WallpaperManager.FLAG_SYSTEM to "wallpaper", WallpaperManager.FLAG_LOCK to "lock")) {
                try {
                    val c = wm.getWallpaperColors(which) ?: continue
                    val p = c.primaryColor.toArgb()
                    out += p
                    out += c.secondaryColor?.toArgb() ?: shift(p, 40f)
                    out += c.tertiaryColor?.toArgb() ?: shift(p, 120f)
                    lastSource = name
                    break
                } catch (e: Throwable) {
                }
            }
        }
        // The picture itself, where the phone lets apps read it. Gives the richest set.
        val pic = pictureColours(wm)
        if (pic != null) {
            if (out.isEmpty()) lastSource = "picture"
            pic.forEach { addDistinct(out, it) }
        }
        // Samsung One UI and some others share nothing: their system palette follows the wallpaper when it is on.
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                val ids = listOf(
                    android.R.color.system_accent1_500, android.R.color.system_accent2_500, android.R.color.system_accent3_500,
                    android.R.color.system_accent1_300, android.R.color.system_accent3_300, android.R.color.system_accent2_300,
                    android.R.color.system_accent1_700, android.R.color.system_accent3_700,
                )
                val sys = ids.map { ctx.getColor(it) }
                if (out.isEmpty()) {
                    out += sys.take(3)
                    lastSource = "palette"
                }
                sys.forEach { addDistinct(out, it) }
            } catch (e: Throwable) {
            }
        }
        if (out.isEmpty()) return null
        // Pad with neighbouring hues so there are always at least six to choose from.
        var k = 0
        while (out.size < 6 && k < 12) {
            val base = out[k % maxOf(1, minOf(out.size, 3))]
            addDistinct(out, shift(base, if (k < 3) 35f else -35f), 6)
            k++
        }
        while (out.size < 3) out += shift(out[0], if (out.size == 1) 40f else 120f)
        return out.take(8)
    }

    /** Sets the phone wallpaper from a picked picture. [target] is "home", "lock" or "both". */
    fun setWallpaper(ctx: Context, uri: android.net.Uri, target: String): Boolean = try {
        val limit = 2560
        val bmp: android.graphics.Bitmap? = if (Build.VERSION.SDK_INT >= 28) {
            android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > limit) {
                    val f = limit.toFloat() / longest
                    dec.setTargetSize((info.size.width * f).toInt().coerceAtLeast(1), (info.size.height * f).toInt().coerceAtLeast(1))
                }
                dec.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > limit) sample *= 2
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
        }
        if (bmp == null) {
            false
        } else {
            val wm = WallpaperManager.getInstance(ctx)
            val flags = when (target) {
                "home" -> WallpaperManager.FLAG_SYSTEM
                "lock" -> WallpaperManager.FLAG_LOCK
                else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }
            wm.setBitmap(bmp, null, true, flags)
            true
        }
    } catch (e: Throwable) {
        false
    }

    /** Colours taken from a picked image, for phones that do not share their wallpaper colours. */
    fun seedsFromUri(ctx: Context, uri: android.net.Uri): List<Int>? = try {
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }
        val b = ctx.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
        if (b == null) null else seedsFromBitmap(b)
    } catch (e: Exception) {
        null
    }

    fun seedsFromBitmap(src: android.graphics.Bitmap): List<Int>? = colorsFromBitmap(src, 8)

    /** The most present, clearly different hues in a picture: up to [n], and at least three. */
    fun colorsFromBitmap(src: android.graphics.Bitmap, n: Int): List<Int>? {
        val side = 48
        val b = android.graphics.Bitmap.createScaledBitmap(src, side, side, true)
        val buckets = 24
        val weight = DoubleArray(buckets)
        val sr = DoubleArray(buckets)
        val sg = DoubleArray(buckets)
        val sb = DoubleArray(buckets)
        val hsv = FloatArray(3)
        var ar = 0.0
        var ag = 0.0
        var ab = 0.0
        for (y in 0 until side) for (x in 0 until side) {
            val px = b.getPixel(x, y)
            val r = android.graphics.Color.red(px)
            val g = android.graphics.Color.green(px)
            val bl = android.graphics.Color.blue(px)
            ar += r; ag += g; ab += bl
            android.graphics.Color.colorToHSV(px, hsv)
            if (hsv[2] < 0.12f || hsv[1] < 0.12f) continue
            val w = (hsv[1] * (0.35f + hsv[2])).toDouble()
            val k = (hsv[0] / (360f / buckets)).toInt().coerceIn(0, buckets - 1)
            weight[k] += w; sr[k] += r * w; sg[k] += g * w; sb[k] += bl * w
        }
        val order = (0 until buckets).filter { weight[it] > 0.0 }.sortedByDescending { weight[it] }
        if (order.isEmpty()) {
            val cnt = (side * side).toDouble()
            return seeds(android.graphics.Color.rgb((ar / cnt).toInt(), (ag / cnt).toInt(), (ab / cnt).toInt()))
        }
        fun colour(k: Int): Int {
            val c = android.graphics.Color.rgb((sr[k] / weight[k]).toInt(), (sg[k] / weight[k]).toInt(), (sb[k] / weight[k]).toInt())
            val h = FloatArray(3)
            android.graphics.Color.colorToHSV(c, h)
            h[1] = h[1].coerceIn(0.45f, 1f)
            h[2] = h[2].coerceIn(0.6f, 1f)
            return android.graphics.Color.HSVToColor(h)
        }
        fun gap(a: Int, c: Int): Int { val d = Math.abs(a - c); return minOf(d, buckets - d) }
        val picked = ArrayList<Int>()
        for (k in order) {
            if (picked.none { gap(it, k) < 2 }) picked += k
            if (picked.size == n) break
        }
        val cols = picked.map { colour(it) }.toMutableList()
        // Fewer distinct hues than wanted: take a lighter and a deeper shade of the main ones.
        var i = 0
        while (cols.size < n && i < cols.size * 2 && i < 8) {
            val h = FloatArray(3)
            android.graphics.Color.colorToHSV(cols[i % picked.size], h)
            if (i < picked.size) { h[1] = (h[1] * 0.55f).coerceIn(0.2f, 1f); h[2] = 1f } else { h[2] = (h[2] * 0.7f).coerceIn(0.4f, 1f) }
            cols += android.graphics.Color.HSVToColor(h)
            i++
        }
        while (cols.size < 3) cols += shift(cols[0], if (cols.size == 1) 40f else 120f)
        return cols
    }

    fun seeds(seed: Int): List<Int> = listOf(seed, shift(seed, 40f), shift(seed, 120f))

    private fun shift(argb: Int, deg: Float): Int {
        val a = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, a)
        a[0] = (a[0] + deg) % 360f
        return android.graphics.Color.HSVToColor(a)
    }

    private fun hue(argb: Int): Float {
        val a = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, a)
        return a[0]
    }

    private fun sat(argb: Int): Float {
        val a = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, a)
        return a[1]
    }

    private fun hsv(h: Float, s: Float, v: Float) =
        Color(android.graphics.Color.HSVToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))

    fun build(seeds: List<Int>, variant: String, id: String, name: String, dark: Boolean = true): Palette {
        val h0 = hue(seeds[0])
        val h1 = hue(seeds[1])
        val h2 = hue(seeds[2])
        val s0 = sat(seeds[0])
        val xs = when (variant) { "vibrant" -> 0.72f; "expressive" -> 0.6f; else -> s0.coerceIn(0.28f, 0.6f) }
        val xv = if (variant == "vibrant") 0.98f else if (variant == "expressive") 0.95f else 0.9f
        val extra = when (variant) {
            "mono" -> emptyList()
            "muted" -> seeds.drop(3).take(5).map { hsv(hue(it), (s0 * 0.4f).coerceIn(0.14f, 0.26f), 0.8f) }
            else -> seeds.drop(3).take(5).map { hsv(hue(it), xs, xv) }
        }
        val base = buildBase(seeds, variant, id, name, dark, h0, h1, h2, s0).withExtra(extra)
        return if (variant == "muted") toneFor(base, dark) else base
    }

    /** Used by the Muted style: re-tones every wallpaper colour for the mode: deep and rich on dark, soft and pastel on light, with matching ink. */
    private fun toneFor(p: Palette, dark: Boolean): Palette {
        val steps = floatArrayOf(0.5f, 0.44f, 0.4f, 0.36f, 0.32f, 0.47f, 0.42f, 0.38f)
        val lights = floatArrayOf(0.97f, 0.94f, 0.91f, 0.95f, 0.98f, 0.93f, 0.96f, 0.92f)
        fun map(c: Color, i: Int): Color {
            val a = FloatArray(3)
            android.graphics.Color.colorToHSV(c.toArgb(), a)
            val k = i % steps.size
            return if (dark) hsv(a[0], (a[1] * 0.9f + 0.12f).coerceIn(0.22f, 0.6f), steps[k])
            else hsv(a[0], (a[1] * 0.7f).coerceIn(0.1f, 0.38f), lights[k])
        }
        val inkC = if (dark) hsv(0f, 0.04f, 0.96f) else hsv(0f, 0.2f, 0.12f)
        return Palette(p.id, p.name, p.ground, p.tiles.mapIndexed { i, c -> map(c, i) }, inkC, p.onGround, p.extra.mapIndexed { i, c -> map(c, i + 5) })
    }

    private fun buildBase(seeds: List<Int>, variant: String, id: String, name: String, dark: Boolean, h0: Float, h1: Float, h2: Float, s0: Float): Palette {
        return when (variant) {
            "vibrant" -> {
                val tiles = listOf(hsv(h0, 0.75f, 1f), hsv(h1, 0.8f, 0.98f), hsv(h2, 0.65f, 0.95f), hsv(h0 + 30f, 0.45f, 1f), hsv(h0, 0.12f, 0.97f))
                if (dark) Palette(id, name, hsv(h0, 0.45f, 0.09f), tiles, hsv(h0, 0.5f, 0.09f), hsv(h0, 0.1f, 0.97f))
                else Palette(id, name, hsv(h0, 0.1f, 0.98f), tiles, hsv(h0, 0.5f, 0.09f), hsv(h0, 0.5f, 0.12f))
            }
            "expressive" -> {
                val tiles = listOf(hsv(h0, 0.7f, 0.98f), hsv(h0 + 60f, 0.6f, 0.98f), hsv(h0 + 150f, 0.5f, 0.92f), hsv(h0 + 210f, 0.4f, 0.96f), hsv(h0 + 300f, 0.2f, 0.97f))
                if (dark) Palette(id, name, hsv(h0 + 200f, 0.4f, 0.1f), tiles, hsv(h0 + 200f, 0.5f, 0.1f), hsv(h0 + 300f, 0.1f, 0.97f))
                else Palette(id, name, hsv(h0 + 200f, 0.08f, 0.97f), tiles, hsv(h0 + 200f, 0.5f, 0.1f), hsv(h0 + 200f, 0.5f, 0.14f))
            }
            "muted" -> {
                // Bright wallpaper colours are pulled down to dusty, low-chroma tones, keeping only the hue.
                val m = (s0 * 0.4f).coerceIn(0.14f, 0.26f)
                val tiles = listOf(hsv(h0, m + 0.04f, 0.8f), hsv(h1, m, 0.78f), hsv(h2, m, 0.74f), hsv(h0 + 20f, m * 0.7f, 0.86f), hsv(h0, 0.06f, 0.88f))
                if (dark) Palette(id, name, hsv(h0, 0.14f, 0.1f), tiles, hsv(h0, 0.3f, 0.13f), hsv(h0, 0.05f, 0.88f))
                else Palette(id, name, hsv(h0, 0.05f, 0.94f), tiles, hsv(h0, 0.3f, 0.13f), hsv(h0, 0.25f, 0.16f))
            }
            "mono" ->
                if (dark) Palette(
                    id, name, hsv(h0, 0.06f, 0.07f),
                    listOf(hsv(h0, 0.05f, 0.98f), hsv(h0, 0.05f, 0.84f), hsv(h0, 0.05f, 0.72f), hsv(h0, 0.05f, 0.6f), hsv(h0, 0.05f, 0.92f)),
                    hsv(h0, 0.1f, 0.07f), hsv(h0, 0.03f, 0.95f),
                ) else Palette(
                    id, name, hsv(h0, 0.04f, 0.96f),
                    listOf(hsv(h0, 0.06f, 0.16f), hsv(h0, 0.06f, 0.26f), hsv(h0, 0.06f, 0.36f), hsv(h0, 0.06f, 0.46f), hsv(h0, 0.06f, 0.21f)),
                    hsv(h0, 0.03f, 0.97f), hsv(h0, 0.1f, 0.08f),
                )
            else -> {
                val s = s0.coerceIn(0.28f, 0.6f)
                val tiles = listOf(hsv(h0, s + 0.15f, 0.93f), hsv(h1, s, 0.9f), hsv(h2, s, 0.88f), hsv(h0, s * 0.6f, 0.95f), hsv(h0, 0.1f, 0.95f))
                if (dark) Palette(id, name, hsv(h0, 0.3f, 0.09f), tiles, hsv(h0, 0.35f, 0.1f), hsv(h0, 0.08f, 0.95f))
                else Palette(id, name, hsv(h0, 0.08f, 0.97f), tiles, hsv(h0, 0.35f, 0.1f), hsv(h0, 0.4f, 0.12f))
            }
        }
    }
}

val LocalPalette = staticCompositionLocalOf { Palettes.AmberSignal }

/** Bundled fonts (OFL licensed). The display font is a setting; the label and thin fonts are fixed. */
object Fonts {
    private val families = mapOf(
        "archivo" to FontFamily(Font(R.font.archivo_black, FontWeight.Normal)),
        "jost" to FontFamily(Font(R.font.jost_bold, FontWeight.Normal)),
        "grotesk" to FontFamily(Font(R.font.space_grotesk, FontWeight.Normal)),
        "sora" to FontFamily(Font(R.font.sora, FontWeight.Normal)),
        "mono" to FontFamily(Font(R.font.jetbrains_mono_bold, FontWeight.Normal)),
        "dmserif" to FontFamily(Font(R.font.dm_serif, FontWeight.Normal)),
        "lexend" to FontFamily(Font(R.font.f_lexend, FontWeight.Normal)),
        "fuzzy" to FontFamily(Font(R.font.f_fuzzy_bubbles, FontWeight.Normal)),
        "gaegu" to FontFamily(Font(R.font.f_gaegu_bold, FontWeight.Normal)),
        "josefin" to FontFamily(Font(R.font.f_josefin_medium, FontWeight.Normal)),
        "kalam" to FontFamily(Font(R.font.f_kalam, FontWeight.Normal)),
        "manropem" to FontFamily(Font(R.font.f_manrope_medium, FontWeight.Normal)),
        "manropes" to FontFamily(Font(R.font.f_manrope_semibold, FontWeight.Normal)),
        "merienda" to FontFamily(Font(R.font.f_merienda, FontWeight.Normal)),
        "montserrat" to FontFamily(Font(R.font.f_montserrat, FontWeight.Normal)),
        "nothing" to FontFamily(Font(R.font.f_nothing_you_could_do, FontWeight.Normal)),
        "nunito" to FontFamily(Font(R.font.f_nunito, FontWeight.Normal)),
        "pangolin" to FontFamily(Font(R.font.f_pangolin, FontWeight.Normal)),
        "righteous" to FontFamily(Font(R.font.f_righteous, FontWeight.Normal)),
        "sour" to FontFamily(Font(R.font.f_sour_gummy, FontWeight.Normal)),
        "sourm" to FontFamily(Font(R.font.f_sour_gummy_medium, FontWeight.Normal)),
        "tsukimi" to FontFamily(Font(R.font.f_tsukimi_rounded, FontWeight.Normal)),
        "tsukimis" to FontFamily(Font(R.font.f_tsukimi_rounded_semibold, FontWeight.Normal)),
        "tsukimib" to FontFamily(Font(R.font.f_tsukimi_rounded_bold, FontWeight.Normal)),
    )

    private val builtIn = listOf(
        "default" to "Koma default",
        "archivo" to "Archivo Black", "jost" to "Jost", "grotesk" to "Space Grotesk",
        "sora" to "Sora", "mono" to "JetBrains Mono", "dmserif" to "DM Serif",
        "lexend" to "Lexend",
        "fuzzy" to "Fuzzy Bubbles",
        "gaegu" to "Gaegu Bold",
        "josefin" to "Josefin Sans Medium",
        "kalam" to "Kalam",
        "manropem" to "Manrope Medium",
        "manropes" to "Manrope Semi Bold",
        "merienda" to "Merienda",
        "montserrat" to "Montserrat",
        "nothing" to "Nothing You Could Do",
        "nunito" to "Nunito",
        "pangolin" to "Pangolin",
        "righteous" to "Righteous",
        "sour" to "Sour Gummy",
        "sourm" to "Sour Gummy Medium",
        "tsukimi" to "Tsukimi Rounded",
        "tsukimis" to "Tsukimi Rounded Semi Bold",
        "tsukimib" to "Tsukimi Rounded Bold",
    )

    // Fonts the user imported (.ttf / .otf), copied into the app's private storage.
    private val custom = mutableStateListOf<String>()
    private val customCache = HashMap<String, FontFamily>()
    private var dir: File? = null

    /** Built-in fonts, then imported ones. Reads state, so lists redraw after an import. */
    val choices: List<Pair<String, String>>
        get() = builtIn + custom.map { "custom:$it" to it.substringBeforeLast('.') }

    fun isCustom(id: String) = id.startsWith("custom:")

    fun init(ctx: Context) {
        val d = File(ctx.filesDir, "fonts").also { it.mkdirs() }
        dir = d
        custom.clear()
        custom.addAll(d.listFiles()?.filter { it.isFile }?.map { it.name }?.sorted().orEmpty())
    }

    /** Copies a picked font file in. Returns its id, or null if it is not a usable font. */
    fun import(ctx: Context, uri: Uri): String? {
        val d = dir ?: return null
        return try {
            val raw = ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "font.ttf"
            val ext = if (raw.endsWith(".otf", true)) "otf" else "ttf"
            val base = raw.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9 _-]"), "").trim().take(24).ifEmpty { "Font" }
            val name = "$base.$ext"
            val tmp = File(d, "$name.tmp")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > 8L * 1024 * 1024) { tmp.delete(); return null }
                        out.write(buf, 0, n)
                    }
                }
            } ?: return null
            // Typeface.createFromFile throws on garbage; that is our validity check.
            android.graphics.Typeface.createFromFile(tmp)
            val dest = File(d, name)
            customCache.remove("custom:$name")
            tmp.renameTo(dest)
            if (name !in custom) { custom.add(name); custom.sort() }
            "custom:$name"
        } catch (e: Exception) {
            null
        }
    }

    fun remove(id: String) {
        val name = id.removePrefix("custom:")
        File(dir ?: return, name).delete()
        custom.remove(name)
        customCache.remove(id)
    }

    private var displayId by mutableStateOf("default")
    private var clockId by mutableStateOf("same")
    var clockTone by mutableStateOf("solid")

    fun setClock(id: String) {
        clockId = if (id == "same" || families.containsKey(id) || (isCustom(id) && id.removePrefix("custom:") in custom)) id else "same"
    }

    /** Font for clock tiles: its own choice, or the launcher font. */
    val Clock: FontFamily get() = if (clockId == "same") Display else family(clockId)

    fun setDisplay(id: String) {
        displayId = if (id == "default" || families.containsKey(id) || (isCustom(id) && id.removePrefix("custom:") in custom)) id else "default"
    }

    fun family(id: String): FontFamily {
        if (id == "default") return families.getValue("archivo")
        families[id]?.let { return it }
        if (isCustom(id)) {
            customCache[id]?.let { return it }
            val f = File(dir ?: return families.getValue("archivo"), id.removePrefix("custom:"))
            if (f.exists()) {
                val fam = try { FontFamily(Font(f, FontWeight.Normal)) } catch (e: Exception) { null }
                if (fam != null) { customCache[id] = fam; return fam }
            }
        }
        return families.getValue("archivo")
    }

    /** Reads state, so text using it redraws when the font setting changes. */
    val Display: FontFamily get() = family(displayId)

    /** True while the original Koma look (Archivo titles, mono labels, system body) is selected. */
    val isDefault: Boolean get() = displayId == "default"

    /** Small uppercase labels: the chosen font everywhere, or JetBrains Mono for the default look. */
    val Label: FontFamily get() = if (isDefault) Mono else Display

    /** Running text: the chosen font everywhere, or the system font for the default look. */
    val Body: FontFamily get() = if (isDefault) FontFamily.SansSerif else Display

    val Thin = FontFamily(Font(R.font.jost_extralight, FontWeight.ExtraLight))
    val Mono = FontFamily(
        Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
        Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
    )
}


private fun hex(c: Color) = c.toArgb()

/** Keeps the launcher's resolved colours so full-screen pages outside the launcher (the alarm) can match it. */
fun savePalette(ctx: android.content.Context, p: Palette) {
    try {
        val s = (listOf(p.ground, p.ink, p.onGround) + p.tiles).joinToString(",") { hex(it).toString() }
        val sp = ctx.getSharedPreferences("koma", android.content.Context.MODE_PRIVATE)
        if (sp.getString("ring_pal", "") != s) sp.edit().putString("ring_pal", s).apply()
    } catch (e: Exception) {
    }
}

/** The colours saved by [savePalette], or null before the launcher has run. */
fun savedPalette(ctx: android.content.Context): Palette? = try {
    val v = ctx.getSharedPreferences("koma", android.content.Context.MODE_PRIVATE).getString("ring_pal", "") ?: ""
    val n = v.split(",").filter { it.isNotBlank() }.map { Color(it.toInt()) }
    if (n.size < 4) null else Palette("saved", "Saved", n[0], n.drop(3), n[1], n[2])
} catch (e: Exception) {
    null
}
