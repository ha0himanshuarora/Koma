package app.koma.launcher.data

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import kotlin.reflect.KProperty

/** A setting that is both Compose state (so the UI follows it) and saved to SharedPreferences. */
class Pref<T : Any>(private val sp: SharedPreferences, private val key: String, default: T) {
    @Suppress("UNCHECKED_CAST")
    private val state = mutableStateOf(
        when (default) {
            is Boolean -> sp.getBoolean(key, default)
            is Int -> sp.getInt(key, default)
            is Long -> sp.getLong(key, default)
            is String -> sp.getString(key, default) ?: default
            else -> default
        } as T
    )

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        state.value = value
        val e = sp.edit()
        when (value) {
            is Boolean -> e.putBoolean(key, value)
            is Int -> e.putInt(key, value)
            is Long -> e.putLong(key, value)
            is String -> e.putString(key, value)
            else -> Unit
        }
        e.apply()
    }
}

/** Every user setting in one place. */
class Prefs(sp: SharedPreferences) {
    init {
        live = this
    }

    companion object {
        /** The settings the open launcher uses, so the timer's notification buttons can change them. */
        @Volatile var live: Prefs? = null
    }

    /** Removing a component leaves an empty space of its size instead of pulling the ones below up. */
    var keepGaps by Pref(sp, "keep_gaps", true)
    /** Double tap on an empty part of the home screen locks the phone (needs Koma's accessibility service). */
    var doubleTapLock by Pref(sp, "double_tap_lock", false)
    var musicStyle by Pref(sp, "music_style", "tape")
    /** When the running timer ends (epoch ms), or 0 when no timer is set. */
    var timerEnd by Pref(sp, "timer_end", 0L)
    var timerLen by Pref(sp, "timer_len", 0L)
    var timerLeft by Pref(sp, "timer_left", 0L)
    /** Per-app pop-up settings for the app drawer, as JSON. See [Popups]. */
    var popups by Pref(sp, "app_popups", "{}")
    /** Gestures on empty parts of the home screen: name -> action, as JSON (see Gestures). */
    var homeGestures by Pref(sp, "home_gestures", "")

    /** Everything that makes up the look of the launcher, for saving as a preset. */
    fun lookSnapshot(): org.json.JSONObject = org.json.JSONObject()
        .put("palette_id", paletteId).put("theme_mode", themeMode).put("follow", follow).put("variant", variant)
        .put("custom_seed", customSeed).put("ground", ground).put("tile_style", tileStyle).put("font", font)
        .put("music_style", musicStyle).put("clock_style", clockStyle).put("clock_seconds", clockSeconds).put("clock_font", clockFont)
        .put("clock_tone", clockTone).put("text_scale", textScale).put("icon_style", iconStyle)
        .put("icon_shape", iconShape).put("tile_badge", tileBadge).put("bt_icon", btIcon).put("icon_pack", iconPack).put("force_icons", forceIcons)
        .put("drawer_bg", drawerBg).put("drawer_height", drawerHeight).put("drawer_panel", drawerPanel)
        .put("drawer_koma", drawerKoma).put("drawer_headers", drawerHeaders).put("use_image_colours", useImageColours)
        .put("image_seeds", imageSeeds).put("main_seed", mainSeed).put("grid_cols", gridCols)
        .put("grid_rows", gridRows).put("folders", foldersJson)

    fun restoreLook(o: org.json.JSONObject) {
        fun s(k: String, f: (String) -> Unit) { if (o.has(k)) f(o.getString(k)) }
        fun i(k: String, f: (Int) -> Unit) { if (o.has(k)) f(o.getInt(k)) }
        fun b(k: String, f: (Boolean) -> Unit) { if (o.has(k)) f(o.getBoolean(k)) }
        s("palette_id") { paletteId = it }; s("theme_mode") { themeMode = it }; b("follow") { follow = it }
        s("variant") { variant = it }; i("custom_seed") { customSeed = it }; i("ground") { ground = it }
        s("tile_style") { tileStyle = it }; s("font") { font = it }; s("clock_style") { clockStyle = it }; s("music_style") { musicStyle = it }
        b("clock_seconds") { clockSeconds = it }; s("clock_font") { clockFont = it }; s("clock_tone") { clockTone = it }
        i("text_scale") { textScale = it }; s("icon_style") { iconStyle = it }; s("icon_shape") { iconShape = it }; s("tile_badge") { tileBadge = it }; s("bt_icon") { btIcon = it }
        s("icon_pack") { iconPack = it }; b("force_icons") { forceIcons = it }; i("drawer_bg") { drawerBg = it }
        i("drawer_height") { drawerHeight = it }; b("drawer_panel") { drawerPanel = it }
        s("drawer_koma") { drawerKoma = it }; b("drawer_headers") { drawerHeaders = it }; b("use_image_colours") { useImageColours = it }
        s("image_seeds") { imageSeeds = it }; i("main_seed") { mainSeed = it }; i("grid_cols") { gridCols = it }
        i("grid_rows") { gridRows = it }; s("folders") { foldersJson = it }
    }

    // Colour
    var paletteId by Pref(sp, "palette_id", "amber")
    // Light or dark: "system" follows the phone, otherwise "light" or "dark".
    var themeMode by Pref(sp, "theme_mode", "dark")
    // Home grid: columns across, and how many rows fit on one screen (tile height follows).
    var gridCols by Pref(sp, "grid_cols", 4)
    var gridRows by Pref(sp, "grid_rows", 8)
    var follow by Pref(sp, "follow", false)
    var variant by Pref(sp, "variant", "tonal")
    var customSeed by Pref(sp, "custom_seed", 0xFFF2A541.toInt())
    /** 0 solid, 1 dimmed wallpaper, 2 clear wallpaper. */
    var ground by Pref(sp, "ground", 0)
    // Tile look: "solid", "glass" (frosted like the drawer panel) or "tint" (frosted, in the palette's colours).
    var tileStyle by Pref(sp, "tile_style", "solid")

    // Type and clock
    var font by Pref(sp, "font", "default")
    var clockStyle by Pref(sp, "clock_style", "poster")
    var clockSeconds by Pref(sp, "clock_seconds", false)
    /** "system", "12" or "24". */
    var clockHour by Pref(sp, "clock_hour", "system")
    var alarmTone by Pref(sp, "alarm_tone", "")
    var timerTone by Pref(sp, "timer_tone", "")
    var ringVibrate by Pref(sp, "ring_vibrate", true)
    var defaultSnooze by Pref(sp, "default_snooze", 10)
    /** How many times one alarm may be snoozed before only stop is left. 0 turns snooze off. */
    var snoozeMax by Pref(sp, "snooze_max", 3)
    /** An unanswered alarm snoozes by itself after a minute, and stops once no snoozes are left. */
    var autoSnooze by Pref(sp, "auto_snooze", true)
    /** An unanswered timer closes by itself after a minute. */
    var autoStopTimer by Pref(sp, "auto_stop_timer", true)
    /** Look of the full screen alarm and timer: classic, quiet, tiles, split or orbit. */
    var ringStyle by Pref(sp, "ring_style", "classic")
    var notesGrid by Pref(sp, "notes_grid", true)
    // The app the clock tile opens ("package/activity"); empty = find the phone's clock app.
    var clockApp by Pref(sp, "clock_app", "")
    // Weather tile: "auto" | "c" | "f"; a chosen place as "lat|lon|Name" (empty = this phone's location).
    var weatherUnit by Pref(sp, "weather_unit", "auto")
    var weatherPlace by Pref(sp, "weather_place", "")
    var weatherPlace2 by Pref(sp, "weather_place2", "")
    var foldersJson by Pref(sp, "folders", "")
    var weatherHiLo by Pref(sp, "weather_hilo", true)
    var weatherCat by Pref(sp, "weather_cat", true)
    var weatherCity by Pref(sp, "weather_city", false)
    var textScale by Pref(sp, "text_scale", 100)

    // Icons
    var iconStyle by Pref(sp, "icon_style", "koma")
    var iconShape by Pref(sp, "icon_shape", "squircle")
    var iconPack by Pref(sp, "icon_pack", "")
    var tileBadge by Pref(sp, "tile_badge", "circle")
    /** How the app limit shows while a limited app is open: "line" along the top or a small "ring" in a corner. */
    var limitStyle by Pref(sp, "limit_style", "line")
    var limitSide by Pref(sp, "limit_side", "right")
    var btIcon by Pref(sp, "bt_icon", "outline")
    var forceIcons by Pref(sp, "force_icons", true)

    // Search
    var engine by Pref(sp, "engine", "google")
    var customEngine by Pref(sp, "custom_engine", "")

    // Misc
    /** 0 dimmed wallpaper, 1 clear wallpaper, 2 solid colour. */
    var drawerBg by Pref(sp, "drawer_bg", 0)
    /** 0 compact, 1 tall, 2 full height (default). */
    var drawerHeight by Pref(sp, "drawer_height", 2)
    var drawerPanel by Pref(sp, "drawer_panel", true)
    var drawerSearch by Pref(sp, "drawer_search", true)
    var drawerHeaders by Pref(sp, "drawer_headers", true)
    /** "alpha", "za", "recent" or "newest". */
    var drawerSort by Pref(sp, "drawer_sort", "alpha")
    /** Where Koma Settings and Koma apps sit in the drawer: "top" or "bottom". */
    var drawerKoma by Pref(sp, "drawer_koma", "top")
    /** "same" follows the launcher font. */
    var clockFont by Pref(sp, "clock_font", "same")
    /** "solid", "dim", "fade" or "outline". */
    var clockTone by Pref(sp, "clock_tone", "solid")
    /** Colours taken from a picked image: "a,b,c" as ARGB ints. Used when the phone shares no wallpaper colours. */
    /** True once the user picks an image for colours: it then wins over what the phone shares. */
    var useImageColours by Pref(sp, "use_image_colours", false)
    var imageSeeds by Pref(sp, "image_seeds", "")
    /** Which wallpaper colour leads the theme: an index into the extracted colours, -1 for the first. */
    var mainSeed by Pref(sp, "main_seed", -1)
    /** Package of the last music player seen playing, so the Music tile can open it later. */
    var lastMusic by Pref(sp, "last_music", "")
    /** App keys hidden from the drawer and recents. They still show up in search. Separated by "|". */
    var hiddenApps by Pref(sp, "hidden_apps", "")
    var homeSearch by Pref(sp, "home_search", true)
    var leftHanded by Pref(sp, "left_handed", false)
    var bags by Pref(sp, "bags", "")
    var hiddenCalendars by Pref(sp, "hidden_cals", "")
    /** Chosen colour per calendar account as "account=index|account=index", the index being a palette colour. */
    var calColors by Pref(sp, "cal_colors", "")
    /** How often the Calendar tile reads the phone's calendars, in minutes. */
    var calRefreshMin by Pref(sp, "cal_refresh_min", 60)
    var recent by Pref(sp, "recent", "")
    var quickStrict by Pref(sp, "quick_strict", false)
    var focusMinutes by Pref(sp, "focus_minutes", 25)
    var focusBag by Pref(sp, "focus_bag", "")
}
