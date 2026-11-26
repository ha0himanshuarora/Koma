package app.koma.launcher.data

/** The sizes a tile can take on a grid [cols] across: its own sizes, plus a full-width version of each 4-wide one. */
fun Kind.sizesFor(cols: Int): List<Pair<Int, Int>> =
    if (cols <= 4) sizes else sizes + sizes.filter { it.first == 4 && (cols to it.second) !in sizes }.map { cols to it.second }

/**
 * What a tile shows. [sizes] lists the (columns, rows) footprints the tile supports; the first is the default.
 * [title] and [desc] are shown in the Add component picker.
 */
enum class Kind(val title: String, val desc: String, val sizes: List<Pair<Int, Int>>) {
    CLOCK("Clock", "Six looks that adapt to the size.", listOf(3 to 3, 4 to 2, 3 to 2, 2 to 2, 2 to 1, 4 to 1, 1 to 1, 4 to 4)),
    DATE("Date", "Today's date, nice and big.", listOf(1 to 1, 2 to 1, 2 to 2)),
    KANJI("Words", "A word that means something to you.", listOf(1 to 2, 1 to 1, 2 to 1, 2 to 2, 4 to 1)),
    WEATHER("Weather", "Right where you are. Wide sizes show two places.", listOf(2 to 2, 2 to 1, 1 to 1, 4 to 2, 4 to 1)),
    MUSIC("Now playing", "Controls whatever you're listening to.", listOf(2 to 2, 4 to 2, 2 to 1, 1 to 1)),
    CALENDAR("Calendar", "What's coming up, from every calendar you use.", listOf(4 to 1, 2 to 1, 2 to 2, 4 to 2, 4 to 4, 1 to 1)),
    SEARCH("Search", "Apps and people first, then the web.", listOf(4 to 1, 2 to 1, 1 to 1, 4 to 2)),
    FAVS("People", "Call your starred contacts in one tap.", listOf(2 to 1, 1 to 2, 2 to 2, 4 to 1)),
    CALLS("Recent calls", "Who called lately.", listOf(2 to 2, 4 to 2)),
    GROUP("Apps", "A few apps together in one tile.", listOf(4 to 1, 2 to 2, 4 to 2, 2 to 1)),
    DEVICE("Phone status", "Battery, storage, memory and network.", listOf(2 to 2, 4 to 1, 2 to 1)),
    TOGGLE("Quick switch", "Torch, Wi-Fi, Bluetooth or Do Not Disturb.", listOf(1 to 1)),
    REMINDERS("Reminders", "What's coming up, with alerts at the time you set.", listOf(2 to 2, 2 to 1, 4 to 2)),
    NOTE("Notes", "Your latest note. Tap to write.", listOf(2 to 2, 2 to 1, 4 to 2)),
    PHOTO("Photo", "One picture you love.", listOf(2 to 2, 4 to 2, 2 to 1, 1 to 1)),
    SCREENTIME("Screen time", "How long you've been on your phone.", listOf(2 to 2, 2 to 1, 4 to 1)),
    FOCUS("Focus", "Block distractions for a while and see the time left.", listOf(2 to 2, 2 to 1, 4 to 1, 4 to 2, 1 to 1, 3 to 3, 3 to 2, 4 to 4)),
    STACK("Stack", "Several tiles in one spot. Swipe to flip through.", listOf(2 to 2, 4 to 2, 2 to 1, 4 to 1, 1 to 1, 4 to 3, 4 to 4, 3 to 3, 3 to 2)),
    WIDGET("App widget", "Any widget from an app on your phone.", listOf(2 to 2, 4 to 1, 2 to 1, 4 to 2, 4 to 3, 4 to 4)),
    APP("App", "One app, one tap.", listOf(1 to 1)),
    ALL("All apps", "Opens your app drawer. Swipes and more are in its options.", listOf(1 to 1)),
    BLUETOOTH("Bluetooth", "Connected devices and their battery.", listOf(2 to 1, 2 to 2, 4 to 1)),
    COUNTDOWN("Countdown", "Days until something that matters.", listOf(2 to 1, 2 to 2, 1 to 1)),
    TIMER("Timer", "A countdown that rings when it's done.", listOf(2 to 1, 2 to 2, 1 to 1, 3 to 3, 4 to 2, 3 to 2, 4 to 1, 4 to 4)),
    STOPWATCH("Stopwatch", "Tap to start, tap to stop.", listOf(2 to 1, 2 to 2, 1 to 1, 3 to 3, 4 to 2, 3 to 2, 4 to 1, 4 to 4)),
    ALARMS("Alarms", "Your next alarm and every alarm that is on.", listOf(2 to 2, 4 to 2, 2 to 1, 4 to 1, 1 to 1, 4 to 4, 3 to 3, 3 to 2)),
    CALC("Calculator", "A shortcut to the calculator, or a working keypad right on the home screen in the larger sizes.", listOf(1 to 1, 2 to 2, 4 to 4, 4 to 3, 3 to 3, 3 to 4, 2 to 4, 2 to 3, 4 to 2, 3 to 2)),
    KAPPS("Koma apps", "Clock, Focus, Notes, Tasks and Calculator in one place.", listOf(1 to 1)),
    SPACE("Gap", "Leave some room so the tiles below stay put.", listOf(1 to 1, 2 to 1, 4 to 1, 1 to 2, 2 to 2, 4 to 2, 4 to 3, 4 to 4)),
}

/**
 * One tile on the home grid. Position is not stored: tiles are packed in list order,
 * which keeps the saved layout tiny and always valid.
 *
 * [arg] depends on the kind: "package/activity" for APP, a style id for CLOCK, app keys joined by "|" for GROUP,
 * an image address for PHOTO, a switch name for TOGGLE, an AppWidget id for WIDGET.
 * [label] and [glyph] are set for role shortcuts (CAM, WEB, CHAT, PHONE ...), which draw a line glyph.
 * [color] is an index into the palette's tile colours, or -1 to choose automatically.
 */
data class Tile(
    val id: String,
    val kind: Kind,
    val w: Int,
    val h: Int,
    val arg: String = "",
    val label: String = "",
    val glyph: String = "",
    val color: Int = -1,
) {
}

/** The components inside a STACK tile. Each takes the stack's footprint; the list is kept as JSON in [Tile.arg]. */
fun Tile.children(): List<Tile> {
    if (kind != Kind.STACK || arg.isBlank()) return emptyList()
    return try {
        val a = org.json.JSONArray(arg)
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val k = Kind.entries.firstOrNull { it.name == o.optString("k").replace("TASKS", "REMINDERS") } ?: return@mapNotNull null
            if (k == Kind.STACK) null
            else Tile(o.optString("id"), k, w, h, o.optString("a"), o.optString("l"), o.optString("g"), o.optInt("c", -1))
        }
    } catch (e: Exception) {
        emptyList()
    }
}

fun stackArg(kids: List<Tile>): String {
    val a = org.json.JSONArray()
    kids.forEach {
        a.put(
            org.json.JSONObject().put("id", it.id).put("k", it.kind.name).put("a", it.arg)
                .put("l", it.label).put("g", it.glyph).put("c", it.color)
        )
    }
    return a.toString()
}

/** The sizes this tile may take. A stack only allows sizes every component in it supports. */
fun Tile.allowedSizes(cols: Int): List<Pair<Int, Int>> {
    if (kind != Kind.STACK) return kind.sizesFor(cols)
    val kids = children()
    if (kids.isEmpty()) return kind.sizesFor(cols)
    val common = kids.map { it.kind.sizesFor(cols) }.reduce { a, b -> a.filter { it in b } }
    return common.ifEmpty { listOf(w to h) }
}
