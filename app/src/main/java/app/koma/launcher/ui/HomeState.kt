package app.koma.launcher.ui

import app.koma.launcher.data.AppLimits
import app.koma.launcher.data.ClockModel
import app.koma.launcher.data.TimeFmt
import app.koma.launcher.data.MusicArg
import app.koma.launcher.data.ClockArg
import android.app.WallpaperManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.koma.launcher.data.AppInfo
import app.koma.launcher.data.Apps
import app.koma.launcher.data.CalendarRepo
import app.koma.launcher.data.Defaults
import app.koma.launcher.data.Device
import app.koma.launcher.data.FocusActive
import app.koma.launcher.data.FocusEngine
import app.koma.launcher.data.FocusPlan
import app.koma.launcher.data.IconCache
import app.koma.launcher.data.Kind
import app.koma.launcher.data.LayoutStore
import app.koma.launcher.data.Library
import app.koma.launcher.data.MediaWatcher
import app.koma.launcher.data.Note
import app.koma.launcher.data.Notes
import app.koma.launcher.data.Prefs
import app.koma.launcher.data.Task
import app.koma.launcher.data.ReminderCtl
import app.koma.launcher.data.Tasks
import app.koma.launcher.data.Tile
import app.koma.launcher.data.Weather
import app.koma.launcher.data.WeatherRepo
import app.koma.launcher.data.WidgetHost
import app.koma.launcher.data.allowedSizes
import app.koma.launcher.data.children
import app.koma.launcher.data.stackArg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.asImageBitmap
import java.util.UUID

enum class Screen { Home, Drawer, Search, Settings, Notes, Tasks, Focus, Clock, Calc, Apps, Weather, ScreenTime }

/** What the drawer is picking an app for. [kind] is "assign" (change a tile's app), "add" (new app tile) or "group". */
class Pick(val kind: String, val tileId: String?)

/** A widget being added. [stage] is "bind" (waiting for the system permission dialog) or "configure". */
class PendingWidget(val id: Int, val info: AppWidgetProviderInfo, val stage: String)

/** Everything the home screen shows, plus the changes the user can make. Saved after every change. */
class HomeState(private val context: Context) {
    private val sp = context.getSharedPreferences("koma", Context.MODE_PRIVATE)
    private fun layoutFile(type: String) = if (type == "today") "layout_today.json" else "layout.json"
    private var store = LayoutStore(context, layoutFile(sp.getString("home_type", "classic") ?: "classic"))
    private val library = Library(context)

    val prefs = Prefs(sp)
    val widgets = WidgetHost(context)
    val media = MediaWatcher(context)
    val icons = IconCache(context, 112)

    // Bump this when the default layout changes; it replaces the saved layout once.
    private val layoutVersion = 3
    private var seeded = sp.getInt("seed", 0) >= layoutVersion

    var tiles by mutableStateOf(
        if (!seeded) emptyList()
        else if (!store.exists() && sp.getString("home_type", "classic") == "today") Defaults.todayLayout()
        else store.load()
    )
        private set

    /**
     * The two buttons on the Today dock, each a 1x1 component saved as a tile. Empty until one is changed; until then
     * the dock shows the phone's default phone and camera apps (see [dockTile]).
     */
    var dock by mutableStateOf(loadDock())
        private set
    /** The dock button being changed (0 or 1), while its chooser is open. */
    var dockPick by mutableStateOf<Int?>(null)

    private fun loadDock(): List<Tile> = try {
        val raw = sp.getString("dock_tiles", "") ?: ""
        if (raw.isBlank()) emptyList() else LayoutStore.fromJson(org.json.JSONArray(raw)).take(2)
    } catch (e: Exception) {
        emptyList()
    }

    /** What dock button [i] holds: the saved component, else the phone's default phone (0) or camera (1). */
    fun dockTile(i: Int, roles: Map<String, String>): Tile {
        dock.getOrNull(i)?.let { return it }
        val old = if (i == 0) prefs.dock1 else prefs.dock2
        val role = if (i == 0) "PHONE" else "CAM"
        val glyph = if (i == 0) "phone" else "cam"
        return Tile("dock-$i", Kind.APP, 1, 1, arg = old.ifEmpty { roles[role] ?: "" }, label = if (old.isEmpty()) role else "", glyph = if (old.isEmpty()) glyph else "", color = i)
    }

    fun setDock(i: Int, tile: Tile) {
        if (i !in 0..1) return
        val roles = Defaults.resolveRoles(context.packageManager, apps)
        dock = (0..1).map { if (it == i) tile.copy(id = "dock-$i", w = 1, h = 1) else dockTile(it, roles) }
        sp.edit().putString("dock_tiles", LayoutStore.toJson(dock).toString()).apply()
    }

    fun setDockApp(i: Int, key: String) = setDock(i, Tile("dock-$i", Kind.APP, 1, 1, arg = key, color = i))

    /** True while the Today home type is in use. */
    val today: Boolean get() = prefs.homeType == "today"
    /** Columns across and rows on screen of whichever home type is showing. */
    val gridCols: Int get() = if (today) prefs.todayCols else prefs.gridCols
    val gridRows: Int get() = prefs.gridRows

    /**
     * Switches between the two home types. Each keeps its own tiles in its own file, so nothing in one is changed by
     * using the other. The first time Today is chosen it starts from [Defaults.todayLayout].
     */
    fun setHomeType(type: String) {
        if (type == prefs.homeType || (type != "classic" && type != "today")) return
        if (edit) finishEdit()
        save()
        prefs.homeType = type
        store = LayoutStore(context, layoutFile(type))
        val fresh = !store.exists()
        tiles = if (fresh) Defaults.todayLayout() else store.load()
        if (fresh) save()
        selectedId = null
        sheetId = null
        stackIndex.clear()
    }
    var apps by mutableStateOf<List<AppInfo>>(emptyList())
        private set

    /** Bumped when apps are installed, removed or updated so the list reloads. */
    var appsVersion by mutableIntStateOf(0)
        private set
    var iconVersion by mutableIntStateOf(0)
        private set
    var palette by mutableStateOf(Palettes.AmberSignal)
        private set
    var wallpaperSeeds by mutableStateOf(listOf(0xFFF2A541.toInt(), 0xFFE5482D.toInt(), 0xFF9DC08B.toInt()))
    /** False when the phone gave no wallpaper colours and Koma is using a picked image (or amber) instead. */
    var wallpaperKnown by mutableStateOf(true)
    /** "wallpaper", "lock", "picture", "palette" or "none": where the current colours came from. */
    var wallpaperSource by mutableStateOf("none")
        private set
    var notes by mutableStateOf(library.loadNotes().filter { it.trashed == 0L || System.currentTimeMillis() - it.trashed < Notes.BIN_MS })
        private set
    var tasks by mutableStateOf(library.loadTasks())
        private set

    /**
     * Set when a Koma app runs in a window of its own (see KomaAppActivity): going home then closes that window
     * instead of showing the home screen in it.
     */
    var standaloneExit: (() -> Unit)? = null

    /**
     * Notes, reminders and alarms live in files that every window of Koma shares, but each window keeps its own copy in
     * memory. This reads them again, so a window that was behind another one shows what was changed there.
     */
    fun reloadData() {
        // Koma settings runs in a window of its own, so settings, layout and dock are read again too.
        app.koma.launcher.data.Pref.reloadAll()
        store = LayoutStore(context, layoutFile(prefs.homeType))
        if (!edit && store.exists()) tiles = store.load()
        dock = loadDock()
        notes = library.loadNotes().filter { it.trashed == 0L || System.currentTimeMillis() - it.trashed < Notes.BIN_MS }
        tasks = library.loadTasks()
        clock.refresh()
        if (Visibility.pendingEdit) {
            Visibility.pendingEdit = false
            if (standaloneExit == null) startEdit()
        }
    }

    init {
        // Done on a reminder's notification changes the file; reload so the lists follow. Arm the next reminder too.
        ReminderCtl.onChanged = { tasks = library.loadTasks() }
        ReminderCtl.schedule(context)
    }
    val clock = ClockModel(context)

    /** Which tab of the Clock app is showing: 0 alarms, 1 world, 2 stopwatch, 3 timer. */
    var clockTab by mutableStateOf(0)
    /** The page open over the Clock app: null, "settings", "widget" or "font". */
    var clockPage by mutableStateOf<String?>(null)

    /** Seconds shown on the timer page before it starts. 0 = use the length of the last session. */
    var timerSec by mutableStateOf(0)

    /** True while a built-in app was opened from the Koma apps screen, so back returns there. */
    var fromHub by mutableStateOf(false)

    /** The back arrow inside a built-in app: back to the Koma apps screen if it was opened from there, else home. */
    fun exitApp() {
        if (fromHub) { fromHub = false; screen = Screen.Apps } else goHome()
    }

    fun openFromHub(go: () -> Unit) {
        go()
        fromHub = true
    }

    fun openClock(tab: Int = clockTab) {
        clockTab = tab
        clockPage = null
        open(Screen.Clock)
    }
    var focusActive by mutableStateOf<FocusActive?>(null)
        private set

    /** A session that just ran its full time, shown on the black "done" page until dismissed. */
    class FocusDone(val name: String, val minutes: Int, val end: Long)
    var focusDone by mutableStateOf<FocusDone?>(null)

    /** Set while a strict session waits for the person to confirm the warning. */
    var strictAsk by mutableStateOf<(() -> Unit)?>(null)
    private var endedByHand = false
    var focusPlans by mutableStateOf<List<FocusPlan>>(emptyList())
        private set

    // Navigation and edit state.
    var screen by mutableStateOf(Screen.Home)
    var settingsPage by mutableStateOf("")
    var edit by mutableStateOf(false)
        private set
    var selectedId by mutableStateOf<String?>(null)
    var sheetId by mutableStateOf<String?>(null)

    // The alphabet rail, driven by a drag from either screen edge. [railPing] changes on every move.
    var railSide by mutableIntStateOf(1)
    /** Finger position down the rail as a fraction (0..1) of the screen height, so it holds when sizes differ. */
    var railY by mutableFloatStateOf(0.5f)
    /** How many letters the drawer rail currently shows (0 = none, so edge drags should not be captured). */
    var railCount by mutableIntStateOf(0)
    var railDragging by mutableStateOf(false)
    var railPing by mutableIntStateOf(0)

    private var editSnapshot: List<Tile>? = null
    private val removedWidgets = HashSet<Int>()
    var showPicker by mutableStateOf(false)
    var showGrid by mutableStateOf(false)
    /** When set, the next component added goes into this tile's stack instead of onto the grid. */
    var stackTarget by mutableStateOf<String?>(null)

    // ---- folders in the app drawer ----
    var folders by mutableStateOf(app.koma.launcher.data.Folders.parse(prefs.foldersJson))
    var openFolderId by mutableStateOf<String?>(null)
    /** When set, the next widget added goes into this folder instead of onto the home grid. */
    var folderTarget by mutableStateOf<String?>(null)
    private var addedToFolder = false

    private fun saveFolders() {
        prefs.foldersJson = app.koma.launcher.data.Folders.encode(folders)
    }

    private fun editFolder(id: String, change: (app.koma.launcher.data.Folder) -> app.koma.launcher.data.Folder) {
        folders = folders.map { if (it.id == id) change(it) else it }
        saveFolders()
    }

    fun newFolder(): app.koma.launcher.data.Folder {
        val f = app.koma.launcher.data.Folder(UUID.randomUUID().toString(), "Folder ${folders.size + 1}", emptyList())
        folders = folders + f
        saveFolders()
        return f
    }

    fun renameFolder(id: String, name: String) = editFolder(id) { it.copy(name = name) }

    fun setFolderHide(id: String, hide: Boolean) = editFolder(id) { it.copy(hideApps = hide) }

    fun deleteFolder(id: String) {
        folders.firstOrNull { it.id == id }?.items?.filter { it.widget }?.forEach { it.arg.toIntOrNull()?.let { w -> widgets.delete(w) } }
        folders = folders.filterNot { it.id == id }
        if (openFolderId == id) openFolderId = null
        saveFolders()
    }

    fun toggleFolderApp(id: String, key: String) = editFolder(id) { f ->
        if (f.items.any { !it.widget && it.arg == key }) f.copy(items = f.items.filterNot { !it.widget && it.arg == key })
        else f.copy(items = f.items + app.koma.launcher.data.FolderItem(false, key))
    }

    fun addFolderWidget(id: String, widgetId: Int) = editFolder(id) { it.copy(items = it.items + app.koma.launcher.data.FolderItem(true, widgetId.toString())) }

    fun removeFolderItem(id: String, item: app.koma.launcher.data.FolderItem) {
        if (item.widget) item.arg.toIntOrNull()?.let { widgets.delete(it) }
        editFolder(id) { f -> f.copy(items = f.items.filterNot { it === item }) }
    }

    /** After a widget was added: go home, unless it went into a folder and the person is still in the drawer. */
    fun afterWidgetAdd() {
        if (addedToFolder) addedToFolder = false else if (screen != Screen.Home) goHome()
    }
    /** Which page each stack was showing, so a tap opens the right component and the page survives recomposition. */
    val stackIndex = androidx.compose.runtime.mutableStateMapOf<String, Int>()
    var showWidgets by mutableStateOf(false)
    var pick by mutableStateOf<Pick?>(null)
    var pendingWidget by mutableStateOf<PendingWidget?>(null)
    var photoRequest by mutableStateOf<String?>(null)
    var searchFocus by mutableStateOf(false)

    /** Set by any screen to ask for runtime permissions; the home screen shows the system dialog. */
    var permissionRequest by mutableStateOf<Array<String>?>(null)

    fun request(vararg permissions: String) {
        permissionRequest = arrayOf(*permissions)
    }

    private var permissionTick by mutableIntStateOf(0)

    // Reading permissionTick makes these re-evaluate when permissions change.
    val calendarGranted: Boolean get() = permissionTick >= 0 && CalendarRepo.granted(context)
    val locationGranted: Boolean get() = permissionTick >= 0 && WeatherRepo.granted(context)
    val contactsGranted: Boolean get() = permissionTick >= 0 && Device.contactsGranted(context)
    val callsGranted: Boolean get() = permissionTick >= 0 && Device.callsGranted(context)
    val usageGranted: Boolean get() = permissionTick >= 0 && Device.usageGranted(context)
    val btGranted: Boolean get() = permissionTick >= 0 && app.koma.launcher.data.Bluetooth.granted(context)

    /** Changes when permissions or system settings may have changed; read it to refresh status displays. */
    val tick: Int get() = permissionTick

    val hiddenCalendars: Set<Long>
        get() = prefs.hiddenCalendars.split(",").mapNotNull { it.toLongOrNull() }.toSet()

    /** True while the phone itself is in dark mode. Kept up to date by the activity. */
    var systemDark by mutableStateOf(readSystemDark())

    private fun readSystemDark(): Boolean =
        (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    init {
        Fonts.init(context)
        Fonts.setDisplay(prefs.font)
        Fonts.setClock(prefs.clockFont)
        Fonts.clockTone = prefs.clockTone
        TimeFmt.init(context, prefs.clockHour)
        FocusEngine.init(context)
        focusPlans = FocusEngine.plans
        icons.usePack(prefs.iconPack)
        applyTheme()
        refreshFocus()
    }

    // ---- theme and icons ------------------------------------------------------------------------

    /** Whether the colours should be the dark version, from the mode setting (and the phone, when set to follow it). */
    val dark: Boolean get() = when (prefs.themeMode) {
        "light" -> false
        "system" -> systemDark
        else -> true
    }

    /** The phone switched between light and dark. */
    fun systemChanged(uiMode: Int = context.resources.configuration.uiMode) {
        val now = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (now != systemDark) {
            systemDark = now
            if (prefs.themeMode == "system") applyTheme()
        }
    }

    fun applyTheme() {
        val img = prefs.imageSeeds.split(",").mapNotNull { it.toIntOrNull() }.takeIf { it.size >= 3 }
        if (prefs.useImageColours && img != null) {
            wallpaperKnown = true
            wallpaperSource = "image"
            wallpaperSeeds = img
        } else {
            val ws = Dynamic.wallpaperSeeds(context)
            wallpaperKnown = ws != null
            wallpaperSource = Dynamic.lastSource
            wallpaperSeeds = ws ?: img ?: listOf(0xFFF2A541.toInt(), 0xFFE5482D.toInt(), 0xFF9DC08B.toInt())
        }
        palette = when {
            prefs.follow -> {
                val m = prefs.mainSeed
                val lead = if (m in wallpaperSeeds.indices) listOf(wallpaperSeeds[m]) + wallpaperSeeds.filterIndexed { i, _ -> i != m } else wallpaperSeeds
                Dynamic.build(lead, prefs.variant, "dynamic", "Wallpaper", dark)
            }
            prefs.paletteId == "custom" -> Dynamic.build(Dynamic.seeds(prefs.customSeed), prefs.variant, "custom", "Custom", dark)
            else -> Palettes.byId(prefs.paletteId, dark)
        }
    }

    /** Changes colour settings and rebuilds the palette. */
    fun theme(change: Prefs.() -> Unit) {
        prefs.change()
        applyTheme()
    }

    /** Takes the three colours from a picked image. Returns false when the image could not be read. */
    fun refreshColours() = applyTheme()

    fun useWallpaperColours() {
        prefs.useImageColours = false
        applyTheme()
    }

    fun pickImageColours(uri: android.net.Uri): Boolean {
        val seeds = Dynamic.seedsFromUri(context, uri) ?: return false
        applyPickedColours(seeds)
        return true
    }

    /** Makes these three colours the wallpaper colours Koma follows. */
    fun applyPickedColours(seeds: List<Int>) {
        prefs.imageSeeds = seeds.joinToString(",")
        prefs.useImageColours = true
        prefs.follow = true
        applyTheme()
    }

    fun setFont(id: String) {
        prefs.font = id
        Fonts.setDisplay(id)
    }

    /** Imports a font file and selects it. Returns false when the file is not a usable font. */
    fun importFont(uri: android.net.Uri): Boolean {
        val id = Fonts.import(context, uri) ?: return false
        setFont(id)
        return true
    }

    fun setClockFont(id: String) {
        prefs.clockFont = id
        Fonts.setClock(id)
    }

    /** The music style: used by every music tile, and by new ones until one is given a style in its own options. */
    fun setMusicStyle(id: String) {
        prefs.musicStyle = id
        // Music tiles that picked their own style follow this choice too.
        allOf(Kind.MUSIC).filter { MusicArg.style(it.arg).isNotEmpty() }.forEach { setArg(it.id, MusicArg.set(it.arg, style = "")) }
    }

    /** Every tile and stacked component of one kind. */
    private fun allOf(kind: Kind): List<Tile> = tiles.flatMap { t -> if (t.kind == Kind.STACK) t.children() else listOf(t) }.filter { it.kind == kind }

    /** The clock style: used by every clock, and by new clocks until one is given a style in its own options. */
    fun setClockStyle(id: String) {
        prefs.clockStyle = id
        // Clocks that picked their own style follow this choice too. Photos stay where they are.
        allOf(Kind.CLOCK).filter { ClockArg.style(it.arg).isNotEmpty() }.forEach { setArg(it.id, ClockArg.set(it.arg, style = "")) }
    }

    fun setClockHour(v: String) {
        prefs.clockHour = v
        TimeFmt.mode = v
    }

    fun setClockTone(t: String) {
        prefs.clockTone = t
        Fonts.clockTone = t
    }

    fun removeFont(id: String) {
        if (prefs.clockFont == id) setClockFont("same")
        Fonts.remove(id)
        if (prefs.font == id) setFont("archivo")
    }

    /** Changes icon settings and redraws every icon. */
    fun iconSettings(change: Prefs.() -> Unit) {
        prefs.change()
        icons.usePack(prefs.iconPack)
        icons.clear()
        iconVersion++
    }

    fun iconConfig() = IconCfg(
        prefs.iconStyle, prefs.iconShape, prefs.forceIcons,
        "${prefs.iconStyle}|${prefs.iconShape}|${prefs.forceIcons}|${prefs.iconPack}|$iconVersion|$appsVersion",
    )

    private var colorsListener: Any? = null

    /** Recolours when the wallpaper changes while Koma is visible. */
    fun watchWallpaper(on: Boolean) {
        if (Build.VERSION.SDK_INT < 27) return
        try {
            val wm = WallpaperManager.getInstance(context)
            if (on) {
                val l = WallpaperManager.OnColorsChangedListener { _, _ -> applyTheme() }
                colorsListener = l
                wm.addOnColorsChangedListener(l, Handler(Looper.getMainLooper()))
            } else {
                (colorsListener as? WallpaperManager.OnColorsChangedListener)?.let { wm.removeOnColorsChangedListener(it) }
                colorsListener = null
            }
        } catch (e: Exception) {
        }
    }

    // ---- lifecycle ------------------------------------------------------------------------------

    fun permissionsChanged() {
        permissionTick++
        applyTheme()
        refreshFocus()
    }

    fun appsChanged() {
        icons.clear()
        appsVersion++
    }

    /**
     * Koma only hears installs and uninstalls while it is showing, so after an app is removed from the system's
     * uninstall screen the list is stale. This checks again on return and reloads only when something changed.
     */
    fun syncApps() {
        Thread {
            val loaded = Apps.load(context)
            if (loaded != apps) android.os.Handler(android.os.Looper.getMainLooper()).post { appsChanged() }
        }.start()
    }

    suspend fun refreshApps() {
        val loaded = withContext(Dispatchers.Default) { Apps.load(context) }
        apps = loaded
        if (!seeded) {
            val roles = withContext(Dispatchers.Default) { Defaults.resolveRoles(context.packageManager, loaded) }
            tiles = Defaults.layout(roles)
            save()
            sp.edit().putInt("seed", layoutVersion).apply()
            seeded = true
        }
    }

    fun cachedWeather(): Weather? = WeatherRepo.cached(sp)

    /** Changing any weather setting that alters the data drops the saved reading so the tile reloads. */
    fun setWeatherSource(unit: String = prefs.weatherUnit, place: String = prefs.weatherPlace, place2: String = prefs.weatherPlace2) {
        prefs.weatherUnit = unit
        prefs.weatherPlace = place
        prefs.weatherPlace2 = place2
        WeatherRepo.clear(sp)
    }

    fun cachedWeather2(): Weather? = WeatherRepo.cached(sp, "weather2")

    suspend fun loadWeather2(): Weather? =
        if (prefs.weatherPlace2.isEmpty()) null else WeatherRepo.refresh(context, sp, prefs.weatherUnit, prefs.weatherPlace2, "weather2")

    suspend fun loadWeather(): Weather? = WeatherRepo.refresh(context, sp, prefs.weatherUnit, prefs.weatherPlace)

    /** The Weather app's full forecast for one place ("" is this phone's location). */
    fun cachedForecast(place: String): app.koma.launcher.data.Forecast? =
        app.koma.launcher.data.ForecastRepo.cached(sp, place, prefs.weatherUnit)

    suspend fun loadForecast(place: String, force: Boolean = false): app.koma.launcher.data.Forecast? =
        app.koma.launcher.data.ForecastRepo.refresh(context, sp, place, prefs.weatherUnit, force)

    // ---- launching ------------------------------------------------------------------------------

    /** Set when an app was opened from a screen other than Home: Koma goes back to Home once the app covers it. */
    var homeAfterLaunch = false

    /** Opens an app. False when Focus Lock blocked it. */
    fun launch(key: String): Boolean {
        val pkg = key.substringBefore('/')
        if (!FocusEngine.allows(pkg)) {
            Toast.makeText(context, "Blocked by Focus Lock", Toast.LENGTH_SHORT).show()
            return false
        }
        if (AppLimits.isLocked(context, pkg)) {
            Toast.makeText(context, "Daily limit reached. Turn it back on in Settings, App limits.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (pkg == context.packageName) open(Screen.Apps)
        else if (pkg.contains("calculator", true)) open(Screen.Calc)
        else Apps.launch(context, key)
        prefs.recent = (listOf(key) + prefs.recent.split("|").filter { it.isNotEmpty() && it != key }).take(8).joinToString("|")
        return true
    }

    /**
     * Opens an app from a screen that sits over Home (a folder, search, a menu) without Home flashing in between:
     * the screen stays up until the app covers Koma, then MainActivity sends Koma back to Home.
     * Koma's own screens open inside Koma, so there is nothing to wait for.
     */
    fun launchThenHome(key: String): Boolean {
        if (!launch(key)) return false
        val pkg = key.substringBefore('/')
        if (pkg == context.packageName || pkg.contains("calculator", true)) openFolderId = null
        else homeAfterLaunch = true
        return true
    }

    fun recentApps(): List<AppInfo> {
        val byKey = apps.associateBy { it.key }
        val hide = hiddenKeys
        return prefs.recent.split("|").mapNotNull { byKey[it] }.filter { it.key !in hide }
    }

    /** Apps hidden from the drawer and recents (search still finds them). */
    val hiddenKeys: Set<String>
        get() = prefs.hiddenApps.split("|").filter { it.isNotEmpty() }.toSet()

    fun popupCfg(pkg: String) = app.koma.launcher.data.Popups.get(prefs.popups, pkg)

    fun setPopup(pkg: String, cfg: app.koma.launcher.data.PopupCfg) {
        prefs.popups = app.koma.launcher.data.Popups.set(prefs.popups, pkg, cfg)
    }

    fun toggleHidden(key: String) {
        val cur = hiddenKeys.toMutableSet()
        if (!cur.remove(key)) cur.add(key)
        prefs.hiddenApps = cur.joinToString("|")
    }

    fun appsOf(arg: String): List<AppInfo> {
        val byKey = apps.associateBy { it.key }
        return arg.split("|").mapNotNull { byKey[it] }
    }

    // ---- navigation -----------------------------------------------------------------------------

    /** Clock, Notes, Reminders and Calculator open in a window of their own (own entry in recents) from the home screen. */
    private fun openInOwnWindow(s: Screen, page: String = ""): Boolean {
        if (standaloneExit != null) return false
        val id = when (s) {
            Screen.Notes -> "notes"
            Screen.Tasks -> "reminders"
            Screen.Calc -> "calc"
            Screen.Settings -> "settings"
            Screen.Weather -> "weather"
            Screen.ScreenTime -> "screentime"
            Screen.Clock, Screen.Focus -> "clock"
            else -> return false
        }
        val target = komaApps(this).firstOrNull { it.id == id } ?: return false
        val tab = if (s == Screen.Focus) 4 else clockTab
        val note = noteOpenId
        val timer = timerSec
        val ok = launchKomaApp(context, target) {
            if (id == "clock") it.putExtra(app.koma.launcher.KomaAppActivity.EXTRA_TAB, tab)
            if (id == "settings" && page.isNotEmpty()) it.putExtra(app.koma.launcher.KomaAppActivity.EXTRA_PAGE, page)
            if (id == "notes" && note != null) it.putExtra(app.koma.launcher.KomaAppActivity.EXTRA_NOTE, note)
            if (id == "clock" && timer > 0) it.putExtra(app.koma.launcher.KomaAppActivity.EXTRA_TIMER, timer)
        }
        if (ok) noteOpenId = null
        return ok
    }

    fun open(s: Screen, page: String = "") {
        if (s == Screen.Settings && focusActive?.strict == true) {
            Toast.makeText(context, "Settings are locked during Focus", Toast.LENGTH_SHORT).show()
            return
        }
        if (openInOwnWindow(s, page)) return
        // Focus now lives inside the Clock app, as its last tab.
        if (s == Screen.Focus) clockTab = 4
        screen = if (s == Screen.Focus) Screen.Clock else s
        settingsPage = page
        if (s != Screen.Drawer) pick = null
    }

    fun openDrawer(search: Boolean = false, pickFor: Pick? = null) {
        pick = pickFor
        searchFocus = search
        screen = Screen.Drawer
    }

    fun openSearch() {
        pick = null
        screen = Screen.Search
    }

    fun goHome() {
        standaloneExit?.let { it(); return }
        fromHub = false
        screen = Screen.Home
        closeTaskEditor()
        noteOpenId = null
        settingsPage = ""
        pick = null
        showPicker = false
        showWidgets = false
        openFolderId = null
        folderTarget = null
        selectedId = null
        sheetId = null
        railDragging = false
    }

    /** Handles the back button. Returns false when there is nothing to go back from. */
    fun back(): Boolean = when {
        showWidgets -> { showWidgets = false; folderTarget = null; true }
        pick?.kind == "folder" -> { pick = null; true }
        openFolderId != null -> { openFolderId = null; true }
        showPicker -> { showPicker = false; true }
        sheetId != null -> { sheetId = null; true }
        taskEditId != null -> { closeTaskEditor(); true }
        selectedId != null -> { selectedId = null; true }
        screen == Screen.Settings && (settingsPage == "privacy" || settingsPage == "permissions") -> { settingsPage = "about"; true }
        screen == Screen.Settings && settingsPage in setOf("colour", "looks", "gestures") -> { settingsPage = "home"; true }
        screen == Screen.Settings && settingsPage == "about" -> { settingsPage = "system"; true }
        screen == Screen.Settings && settingsPage.isNotEmpty() -> { settingsPage = ""; true }
        standaloneExit != null && screen != Screen.Home -> { standaloneExit?.invoke(); true }
        fromHub && screen != Screen.Apps -> { fromHub = false; pick = null; screen = Screen.Apps; true }
        screen != Screen.Home -> { pick = null; screen = Screen.Home; settingsPage = ""; true }
        edit -> { finishEdit(); true }
        else -> false
    }

    // ---- edit mode ------------------------------------------------------------------------------

    fun startEdit() {
        if (standaloneExit != null) { // a window of its own cannot edit the home screen: the home screen does it when you return
            Visibility.pendingEdit = true
            return
        }
        if (edit) return
        editSnapshot = tiles
        removedWidgets.clear()
        stackTarget = null
        edit = true
    }

    /** Keeps the changes. */
    fun finishEdit() {
        showGrid = false
        removedWidgets.forEach { widgets.delete(it) }
        removedWidgets.clear()
        editSnapshot = null
        edit = false
        selectedId = null
        sheetId = null
    }

    /** Throws the changes away and restores the layout from when edit mode started. */
    fun cancelEdit() {
        showGrid = false
        editSnapshot?.let {
            tiles = it
            save()
        }
        removedWidgets.clear()
        editSnapshot = null
        edit = false
        selectedId = null
        sheetId = null
    }

    // ---- tiles ----------------------------------------------------------------------------------

    fun add(kind: Kind, arg: String = "", size: Pair<Int, Int>? = null): Tile {
        val (w, h) = size ?: stackTarget?.let { id -> tiles.firstOrNull { it.id == id }?.let { it.w to it.h } } ?: kind.sizes.first()
        val a = if (arg.isEmpty() && kind == Kind.COUNTDOWN) app.koma.launcher.data.Countdown.make(java.time.LocalDate.now().plusDays(30), "Event") else arg
        val tile = Tile(UUID.randomUUID().toString(), kind, w, h, arg = a, color = tiles.size % palette.tiles.size)
        insertTile(tile)
        return tile
    }

    private fun insertTile(tile: Tile) {
        val target = stackTarget
        stackTarget = null
        if (target != null && tiles.any { it.id == target }) {
            stackInto(target, tile)
            return
        }
        rawInsert(tile)
    }

    private fun rawInsert(tile: Tile) {
        val allAt = tiles.indexOfFirst { it.kind == Kind.ALL }
        tiles = if (allAt >= 0) tiles.toMutableList().also { it.add(allAt, tile) } else tiles + tile
        save()
    }

    /** Adds a quick shortcut (CAM, WEB, ...) pointing at the phone's default app for that role. */
    fun addRole(label: String, glyph: String): Tile {
        val key = Defaults.resolveRoles(context.packageManager, apps)[label] ?: ""
        val tile = Tile(UUID.randomUUID().toString(), Kind.APP, 1, 1, arg = key, label = label, glyph = glyph, color = tiles.size % palette.tiles.size)
        insertTile(tile)
        return tile
    }

    private fun releaseWidgets(tile: Tile) {
        val owned = if (tile.kind == Kind.STACK) tile.children() else listOf(tile)
        owned.filter { it.kind == Kind.WIDGET }.mapNotNull { it.arg.toIntOrNull() }
            .forEach { if (editSnapshot != null) removedWidgets.add(it) else widgets.delete(it) }
    }

    /** Puts [child] into the stack made from tile [targetId]; a plain tile becomes a stack of itself plus the child. */
    fun stackInto(targetId: String, child: Tile) {
        val t = tiles.firstOrNull { it.id == targetId } ?: return rawInsert(child)
        val kids = if (t.kind == Kind.STACK) t.children() else listOf(t)
        // A plain tile that becomes a stack gets a new id: the tile itself lives on as the first component, under its old one.
        val merged = if (t.kind == Kind.STACK) t.copy(arg = stackArg(kids + child))
        else t.copy(id = UUID.randomUUID().toString(), kind = Kind.STACK, arg = stackArg(kids + child), label = "", glyph = "", color = -1)
        tiles = tiles.map { if (it.id == targetId) merged else it }
        stackIndex[merged.id] = kids.size
        selectedId = merged.id
        save()
    }

    /** "" slides vertically, "h" horizontally. Kept in the stack tile's label, which stacks do not otherwise use. */
    fun setStackDirection(id: String, dir: String) {
        tiles = tiles.map { if (it.id == id && it.kind == Kind.STACK) it.copy(label = dir) else it }
        save()
    }

    fun stackApp(targetId: String, key: String) {
        stackInto(targetId, Tile(UUID.randomUUID().toString(), Kind.APP, 1, 1, arg = key, color = tiles.size % palette.tiles.size))
    }

    /** Two tiles can stack when they are the same size and both can live at that size. */
    fun canStack(a: Tile, b: Tile, cols: Int): Boolean {
        if (a.id == b.id || a.kind == Kind.ALL || b.kind == Kind.ALL || a.kind == Kind.SPACE || b.kind == Kind.SPACE) return false
        if (a.w != b.w || a.h != b.h) return false
        return (a.w to a.h) in a.allowedSizes(cols) && (a.w to a.h) in b.allowedSizes(cols)
    }

    /** Drops tile [dragId] onto tile [targetId]: it joins that tile's stack. Its widgets move along, they are not deleted. */
    fun stackOnto(dragId: String, targetId: String) {
        val d = tiles.firstOrNull { it.id == dragId } ?: return
        val t = tiles.firstOrNull { it.id == targetId } ?: return
        if (!canStack(d, t, gridCols)) return
        val incoming = if (d.kind == Kind.STACK) d.children() else listOf(d)
        tiles = tiles.filterNot { it.id == dragId }
        val kids = if (t.kind == Kind.STACK) t.children() else listOf(t)
        val merged = if (t.kind == Kind.STACK) t.copy(arg = stackArg(kids + incoming))
        else t.copy(id = UUID.randomUUID().toString(), kind = Kind.STACK, arg = stackArg(kids + incoming), label = "", glyph = "", color = -1)
        tiles = tiles.map { if (it.id == targetId) merged else it }
        stackIndex[merged.id] = kids.size
        stackIndex.remove(dragId)
        selectedId = merged.id
        save()
    }

    /** Takes one component out of a stack (releasing its widget); a stack left with one component becomes that tile again. */
    fun removeFromStack(stackId: String, childId: String) {
        val t = tiles.firstOrNull { it.id == stackId } ?: return
        val kids = t.children()
        kids.firstOrNull { it.id == childId }?.let { releaseWidgets(it) }
        val left = kids.filterNot { it.id == childId }
        when {
            left.isEmpty() -> remove(t)
            left.size == 1 -> {
                tiles = tiles.map { if (it.id == stackId) left[0].copy(w = t.w, h = t.h) else it }
                stackIndex.remove(stackId)
                if (selectedId == stackId) selectedId = left[0].id
                if (sheetId == stackId) sheetId = null
                save()
            }
            else -> {
                tiles = tiles.map { if (it.id == stackId) t.copy(arg = stackArg(left)) else it }
                stackIndex[stackId] = (stackIndex[stackId] ?: 0).coerceIn(0, left.size - 1)
                save()
            }
        }
    }

    /** Takes one component out of a stack and puts it on the home screen as its own tile, right after the stack. Nothing is deleted. */
    fun extractFromStack(stackId: String, childId: String) {
        val t = tiles.firstOrNull { it.id == stackId && it.kind == Kind.STACK } ?: return
        val kids = t.children()
        val c = kids.firstOrNull { it.id == childId } ?: return
        val left = kids.filterNot { it.id == childId }
        val out = c.copy(w = t.w, h = t.h)
        val at = tiles.indexOf(t)
        tiles = tiles.toMutableList().also { list ->
            when {
                left.isEmpty() -> list[at] = out
                left.size == 1 -> { list[at] = left[0].copy(w = t.w, h = t.h); list.add(at + 1, out) }
                else -> { list[at] = t.copy(arg = stackArg(left)); list.add(at + 1, out) }
            }
        }
        if (left.size <= 1) stackIndex.remove(stackId) else stackIndex[stackId] = (stackIndex[stackId] ?: 0).coerceIn(0, left.size - 1)
        sheetId = null
        selectedId = out.id
        save()
    }

    fun moveInStack(stackId: String, childId: String, by: Int) {
        val t = tiles.firstOrNull { it.id == stackId } ?: return
        val kids = t.children().toMutableList()
        val from = kids.indexOfFirst { it.id == childId }
        val to = from + by
        if (from < 0 || to !in kids.indices) return
        kids.add(to, kids.removeAt(from))
        tiles = tiles.map { if (it.id == stackId) t.copy(arg = stackArg(kids)) else it }
        save()
    }

    /** Breaks a stack back into separate tiles, in place. */
    fun unstack(stackId: String) {
        val t = tiles.firstOrNull { it.id == stackId } ?: return
        val at = tiles.indexOf(t)
        val kids = t.children().map { it.copy(w = t.w, h = t.h) }
        tiles = tiles.toMutableList().also { it.removeAt(at); it.addAll(at, kids) }
        stackIndex.remove(stackId)
        selectedId = null
        sheetId = null
        save()
    }

    fun remove(tile: Tile) {
        releaseWidgets(tile)
        // With "keep gaps" on, a removed component leaves an empty space of its size, so nothing below moves up.
        tiles = if (prefs.keepGaps && tile.kind != Kind.SPACE) {
            tiles.map { if (it.id == tile.id) Tile(UUID.randomUUID().toString(), Kind.SPACE, tile.w, tile.h) else it }.dropLastWhile { it.kind == Kind.SPACE }
        } else {
            tiles.filterNot { it.id == tile.id }
        }
        if (selectedId == tile.id) selectedId = null
        if (sheetId == tile.id) sheetId = null
        save()
    }

    fun resize(id: String, w: Int, h: Int) {
        tiles = tiles.map { if (it.id == id) it.copy(w = w, h = h) else it }
        save()
    }

    fun recolor(id: String, color: Int) {
        tiles = tiles.map {
            when {
                it.id != id -> it
                it.kind == Kind.STACK -> it.copy(color = color, arg = stackArg(it.children().map { c -> c.copy(color = color) }))
                else -> it.copy(color = color)
            }
        }
        save()
    }

    /** A tile by id, or a component inside a stack. */
    fun findTile(id: String?): Tile? {
        if (id == null) return null
        // Components first: a stack shares its id with its first component, and callers want that component's own settings.
        for (t in tiles) if (t.kind == Kind.STACK) t.children().firstOrNull { it.id == id }?.let { return it }
        return tiles.firstOrNull { it.id == id }
    }

    /** Colours one component of a stack. */
    fun recolorChild(stackId: String, childId: String, color: Int) {
        tiles = tiles.map { t ->
            if (t.id == stackId && t.kind == Kind.STACK) t.copy(arg = stackArg(t.children().map { c -> if (c.id == childId) c.copy(color = color) else c })) else t
        }
        save()
    }

    fun setArg(id: String, arg: String) {
        tiles = tiles.map { t ->
            when {
                // A component inside a stack: change it within the stack's saved list. This comes first because a tile
                // that became a stack keeps its id, so its first component and the stack share one; matching the
                // stack itself here would overwrite the whole list with the new value and empty the stack.
                t.kind == Kind.STACK && t.children().any { it.id == id } ->
                    t.copy(arg = stackArg(t.children().map { c -> if (c.id == id) c.copy(arg = arg) else c }))
                // A stack's own data is only ever written by the stack functions, never by a component's setting.
                t.id == id -> if (t.kind == Kind.STACK) t else t.copy(arg = arg)
                else -> t
            }
        }
        save()
    }

    /** Points a shortcut tile at a different app. */
    fun assign(tileId: String, appKey: String) = setArg(tileId, appKey)

    /** Moves a tile to another position in the list without saving (used while dragging). */
    fun move(from: Int, to: Int) {
        if (from == to || from !in tiles.indices || to !in tiles.indices) return
        tiles = tiles.toMutableList().also { it.add(to, it.removeAt(from)) }
    }

    fun moveBy(id: String, delta: Int) {
        val i = tiles.indexOfFirst { it.id == id }
        if (i < 0) return
        move(i, (i + delta).coerceIn(0, tiles.lastIndex))
        save()
    }

    fun commitLayout() = save()

    suspend fun resetLayout() {
        val roles = withContext(Dispatchers.Default) { Defaults.resolveRoles(context.packageManager, apps) }
        tiles.filter { it.kind == Kind.WIDGET || it.kind == Kind.STACK }.forEach { releaseWidgets(it) }
        tiles = if (today) Defaults.todayLayout() else Defaults.layout(roles)
        save()
    }

    // ---- widgets --------------------------------------------------------------------------------

    /** The widget's own setup screen finished. */
    fun widgetConfigured(ok: Boolean) {
        val p = pendingWidget ?: return
        if (ok) {
            addWidgetTile(p.id, p.info)
            afterWidgetAdd()
        } else {
            widgets.delete(p.id)
            pendingWidget = null
        }
    }

    fun addWidgetTile(id: Int, info: AppWidgetProviderInfo) {
        val target = folderTarget
        folderTarget = null
        if (target != null && openFolderId == target) {
            addFolderWidget(target, id)
            addedToFolder = true
            pendingWidget = null
            return
        }
        add(Kind.WIDGET, id.toString(), widgets.cells(info))
        pendingWidget = null
    }

    // ---- calendars ------------------------------------------------------------------------------

    /** The wallpaper colour chosen for each calendar account (an index into the palette). */
    val calendarColors: Map<String, Int>
        get() = prefs.calColors.split("|").mapNotNull { p ->
            val i = p.lastIndexOf('=')
            val idx = if (i > 0) p.substring(i + 1).toIntOrNull() else null
            idx?.let { p.substring(0, i) to it }
        }.toMap()

    /** Colours an account's calendars; [index] -1 goes back to the colour the palette picks for it. */
    fun setCalendarColor(account: String, index: Int) {
        val m = calendarColors.toMutableMap()
        if (index < 0) m.remove(account) else m[account] = index
        prefs.calColors = m.entries.joinToString("|") { "${it.key}=${it.value}" }
    }

    fun toggleCalendar(id: Long) {
        val hidden = hiddenCalendars.toMutableSet()
        if (!hidden.add(id)) hidden.remove(id)
        prefs.hiddenCalendars = hidden.joinToString(",")
    }

    // ---- notes and tasks ------------------------------------------------------------------------

    /** Which note the Notes screen should open straight away: a note id, or "new". Set by the home tile. */
    var noteOpenId by mutableStateOf<String?>(null)

    private fun sortedNotes(list: List<Note>) = list.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updated })

    fun saveNote(n: Note) {
        val stamped = n.copy(updated = System.currentTimeMillis(), created = if (n.created == 0L) System.currentTimeMillis() else n.created)
        val list = if (notes.any { it.id == n.id }) notes.map { if (it.id == n.id) stamped else it } else notes + stamped
        notes = sortedNotes(list)
        library.saveNotes(notes)
    }

    private fun changeNote(id: String, f: (Note) -> Note) {
        notes = sortedNotes(notes.map { if (it.id == id) f(it) else it })
        library.saveNotes(notes)
    }

    fun trashNote(id: String) = changeNote(id) { it.copy(trashed = System.currentTimeMillis(), pinned = false) }
    fun restoreNote(id: String) = changeNote(id) { it.copy(trashed = 0L) }

    /** Gone for good. */
    fun deleteNote(id: String) {
        notes = notes.filterNot { it.id == id }
        library.saveNotes(notes)
    }

    fun emptyBin() {
        notes = notes.filter { it.trashed == 0L }
        library.saveNotes(notes)
    }

    /** The task being edited in the Tasks screen. */
    var taskEditId by mutableStateOf<String?>(null)

    /** Keeps the task list on disk and the reminder alarm pointing at the next one due. */
    private fun commitTasks(list: List<Task>) {
        tasks = list
        library.saveTasks(list)
        ReminderCtl.schedule(context)
    }

    /** Closes the task editor. A task still without a title is dropped if it was just made, and called Untitled otherwise. */
    fun closeTaskEditor() {
        val id = taskEditId ?: return
        val isNew = taskNewId == id
        taskEditId = null
        taskNewId = null
        val t = tasks.firstOrNull { it.id == id } ?: return
        if (t.text.isBlank()) { if (isNew) deleteTask(id) else updateTask(t.copy(text = "Untitled")) }
    }

    /** The task just made from the + button, still being filled in. Closing its editor with no title throws it away. */
    var taskNewId by mutableStateOf<String?>(null)

    /** A task, or a reminder when [at] (minute of the day) and [remind] are set. Returns its id, or null if there was no title. */
    fun addTask(text: String, due: Long = -1, list: String = "", priority: Int = 0, at: Int = -1, remind: Int = -1, blank: Boolean = false): String? {
        if (text.isBlank() && !blank) return null
        val id = UUID.randomUUID().toString()
        commitTasks(tasks + Task(id, text.trim(), due = due, at = at, remind = remind, priority = priority, list = list, created = System.currentTimeMillis()))
        return id
    }

    /** Ticks a task. A repeating one moves to its next date instead of finishing. */
    fun toggleTask(id: String) = commitTasks(Tasks.toggled(tasks, id))

    fun updateTask(t: Task) = commitTasks(tasks.map { if (it.id == t.id) t else it })

    fun deleteTask(id: String) {
        if (taskEditId == id) taskEditId = null
        commitTasks(tasks.filterNot { it.id == id })
    }

    fun clearDoneTasks() = commitTasks(tasks.filterNot { it.done })

    // ---- focus ----------------------------------------------------------------------------------

    fun refreshFocus() {
        val prev = focusActive
        FocusEngine.tick(context)
        focusActive = FocusEngine.active()
        val fa = focusActive
        if (prev != null && fa == null && !endedByHand && System.currentTimeMillis() >= prev.until - 2_000L) {
            val mins = if (prev.start > 0L) ((prev.until - prev.start) / 60_000L).toInt() else (FocusEngine.runs.lastOrNull()?.minutes ?: 1)
            focusDone = FocusDone(prev.name, mins.coerceAtLeast(1), prev.until)
        }
        if (fa == null) endedByHand = false
        app.koma.launcher.data.ClockNotif.focus(context, fa?.name, fa?.start ?: 0L, fa?.until ?: 0L, fa?.strict == true)
    }

    /** Starts a session; a strict one asks first, since there is no way out of it. */
    fun startFocusAsk(minutes: Int, allowed: List<String>, strict: Boolean, then: () -> Unit = {}) {
        if (!strict) {
            startFocus(minutes, allowed, false)
            then()
        } else {
            strictAsk = {
                startFocus(minutes, allowed, true)
                then()
            }
        }
    }

    fun extendFocus(minutes: Int) {
        FocusEngine.extend(context, minutes)
        refreshFocus()
    }

    fun startFocus(minutes: Int, allowed: List<String>, strict: Boolean) {
        FocusEngine.startSession(context, minutes, allowed, strict)
        refreshFocus()
    }

    fun endFocus() {
        endedByHand = true
        FocusEngine.endSession(context)
        refreshFocus()
    }

    fun savePlan(p: FocusPlan) {
        FocusEngine.plans = if (FocusEngine.plans.any { it.id == p.id }) FocusEngine.plans.map { if (it.id == p.id) p else it } else FocusEngine.plans + p
        FocusEngine.save(context)
        focusPlans = FocusEngine.plans
        refreshFocus()
    }

    private fun save() = store.save(tiles)

    val appCtx: Context get() = context

    /** Gestures on empty parts of the home screen. The old "double tap to lock" setting counts as a double tap that locks. */
    fun homeGestures(): Map<String, String> {
        val m = app.koma.launcher.data.Gestures.parse(prefs.homeGestures).toMutableMap()
        if (prefs.doubleTapLock && "double" !in m) m["double"] = "lock"
        return m
    }

    fun setHomeGesture(key: String, action: String) {
        val cur = homeGestures()
        prefs.doubleTapLock = false
        var raw = ""
        cur.forEach { (k, v) -> if (k != key) raw = app.koma.launcher.data.Gestures.set(raw, k, v) }
        prefs.homeGestures = app.koma.launcher.data.Gestures.set(raw, key, action)
    }

    // ---- looks: saved setups of layout, theme, icons, fonts and wallpaper ----------------------------

    private val lookStore = app.koma.launcher.data.LookStore(context)
    var looks by mutableStateOf(lookStore.list())

    private fun saveWallpaper(f: java.io.File): Boolean = try {
        val d = WallpaperManager.getInstance(context).peekDrawable()
        if (d == null) false else {
            val dm = context.resources.displayMetrics
            val w = minOf(dm.widthPixels, 1080)
            val h = (w * dm.heightPixels.toFloat() / dm.widthPixels).toInt()
            val iw = d.intrinsicWidth.coerceAtLeast(1)
            val ih = d.intrinsicHeight.coerceAtLeast(1)
            val sc = maxOf(w.toFloat() / iw, h.toFloat() / ih)
            val dw = (iw * sc).toInt().coerceAtLeast(w)
            val dh = (ih * sc).toInt().coerceAtLeast(h)
            val b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val l = (w - dw) / 2
            val t = (h - dh) / 2
            d.setBounds(l, t, l + dw, t + dh)
            d.draw(android.graphics.Canvas(b))
            f.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
            true
        }
    } catch (e: Throwable) {
        false
    }

    private fun pendingWall() = java.io.File(context.filesDir, "pending_wall.jpg")
    private fun currentWall() = java.io.File(context.filesDir, "current_wall.jpg")
    var hasPendingWall by mutableStateOf(pendingWall().exists())

    private fun copyScaled(uri: android.net.Uri, to: java.io.File): Boolean = try {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        var s = 1
        while (bounds.outWidth / (s * 2) >= 1440) s *= 2
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = s }
        val b = context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
        if (b == null) false else {
            to.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
            true
        }
    } catch (e: Throwable) {
        false
    }

    /** A picture chosen on the Looks page, kept until the next look is saved. */
    suspend fun stagePickedWall(uri: android.net.Uri): Boolean {
        val ok = withContext(Dispatchers.IO) { copyScaled(uri, pendingWall()) }
        hasPendingWall = ok
        return ok
    }

    /** Remembers a wallpaper set through Koma, because the phone may not let apps read its wallpaper back. */
    suspend fun keepWallpaper(uri: android.net.Uri) {
        withContext(Dispatchers.IO) { copyScaled(uri, currentWall()) }
    }

    /** Saves the current look under [name]. Returns false when the wallpaper was asked for but could not be read. */
    suspend fun saveLook(name: String, withWallpaper: Boolean): Boolean {
        val id = UUID.randomUUID().toString()
        val o = prefs.lookSnapshot()
            .put("id", id).put("name", name.trim().ifBlank { "Look ${looks.size + 1}" })
            .put("time", System.currentTimeMillis())
            .put("tiles", LayoutStore.toJson(tiles))
        var wallOk = true
        withContext(Dispatchers.IO) {
            if (withWallpaper) {
                val target = lookStore.wallFile(id)
                val pending = pendingWall()
                val current = currentWall()
                wallOk = when {
                    // A picture chosen on the Looks page wins, then the phone's own wallpaper if it shares it,
                    // then the last wallpaper that was set through Koma.
                    pending.exists() -> { pending.copyTo(target, true); true }
                    saveWallpaper(target) -> true
                    current.exists() -> { current.copyTo(target, true); true }
                    else -> false
                }
                pending.delete()
            }
            lookStore.write(id, o)
        }
        hasPendingWall = false
        looks = lookStore.list()
        return wallOk
    }

    /** Switches to a saved look in one go. */
    suspend fun applyLook(id: String) {
        val o = withContext(Dispatchers.IO) { lookStore.read(id) } ?: return
        val wall = lookStore.wallFile(id)
        if (wall.exists()) {
            withContext(Dispatchers.IO) {
                try {
                    android.graphics.BitmapFactory.decodeFile(wall.path)?.let { WallpaperManager.getInstance(context).setBitmap(it) }
                } catch (e: Throwable) {
                }
            }
        }
        tiles.forEach { releaseWidgets(it) }
        val saved = LayoutStore.fromJson(o.optJSONArray("tiles") ?: org.json.JSONArray())
        // Android widgets belong to this phone's widget host, so a saved one becomes an empty space of the same size.
        tiles = saved.map { if (it.kind == Kind.WIDGET) Tile(UUID.randomUUID().toString(), Kind.SPACE, it.w, it.h) else it }
        save()
        iconSettings { restoreLook(o) }
        folders = app.koma.launcher.data.Folders.parse(prefs.foldersJson)
        applyTheme()
    }

    /** What a look's preview card draws: its tiles, its colours and (when saved) its wallpaper, shrunk. */
    suspend fun loadLookPreview(id: String): LookPreview? = withContext(Dispatchers.IO) {
        try {
            val o = lookStore.read(id) ?: return@withContext null
            val tl = LayoutStore.fromJson(o.optJSONArray("tiles") ?: org.json.JSONArray())
            val mode = o.optString("theme_mode", "dark")
            val d = mode == "dark" || (mode == "system" && systemDark)
            val variant = o.optString("variant", "tonal")
            val pal = when {
                o.optBoolean("follow", false) -> {
                    val seeds = o.optString("image_seeds").split(",").mapNotNull { it.toIntOrNull() }.takeIf { it.size >= 3 } ?: wallpaperSeeds
                    Dynamic.build(seeds, variant, "dynamic", "Wallpaper", d)
                }
                o.optString("palette_id") == "custom" -> Dynamic.build(Dynamic.seeds(o.optInt("custom_seed", 0xFFF2A541.toInt())), variant, "custom", "Custom", d)
                else -> Palettes.byId(o.optString("palette_id", "amber"), d)
            }
            val f = lookStore.wallFile(id)
            val bmp = if (f.exists()) {
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                android.graphics.BitmapFactory.decodeFile(f.path, opts)?.asImageBitmap()
            } else null
            LookPreview(tl, pal, bmp, o.optInt("grid_cols", 4), o.optInt("grid_rows", 8), o.optInt("tile_style_glass", 0) == 1)
        } catch (e: Throwable) {
            null
        }
    }

    fun deleteLook(id: String) {
        lookStore.delete(id)
        looks = lookStore.list()
    }
}

/** A look reduced to what a small preview card needs. */
class LookPreview(val tiles: List<Tile>, val pal: Palette, val wall: androidx.compose.ui.graphics.ImageBitmap?, val cols: Int, val rows: Int, val glass: Boolean)
