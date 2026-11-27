package app.koma.launcher.data

import org.json.JSONObject

/**
 * Gestures on the all-apps tile. The tile's arg holds a small JSON object: gesture name -> action.
 * An action is one of [actions], or "app:<app key>" to open an app.
 */
object Gestures {
    val names = listOf(
        "up" to "Swipe up", "down" to "Swipe down", "left" to "Swipe left", "right" to "Swipe right",
        "double" to "Double tap", "long" to "Long press",
    )
    val actions = listOf(
        "" to "NOTHING", "search" to "SEARCH", "notes" to "NOTES", "tasks" to "REMINDERS", "focus" to "FOCUS", "calc" to "CALCULATOR", "weather" to "WEATHER", "screentime" to "SCREEN TIME", "kapps" to "KOMA APPS",
        "settings" to "KOMA SETTINGS", "edit" to "EDIT LAYOUT", "drawer" to "ALL APPS",
        // These need Koma switched on in Accessibility settings.
        "lock" to "LOCK SCREEN", "shade" to "NOTIFICATIONS", "quick" to "QUICK SETTINGS",
        "recents" to "RECENT APPS", "shot" to "SCREENSHOT", "power" to "POWER MENU",
    )
    /** Gestures on empty parts of the home screen. */
    val homeNames = listOf(
        "double" to "Double tap", "long" to "Long press", "up" to "Swipe up from the bottom edge", "down" to "Swipe down from the top edge",
    )

    fun parse(arg: String): Map<String, String> = try {
        if (arg.isBlank()) emptyMap() else {
            val o = JSONObject(arg)
            o.keys().asSequence().associateWith { o.optString(it) }.filterValues { it.isNotEmpty() }
        }
    } catch (e: Exception) {
        emptyMap()
    }

    fun set(arg: String, name: String, action: String): String {
        val m = parse(arg).toMutableMap()
        if (action.isEmpty()) m.remove(name) else m[name] = action
        return if (m.isEmpty()) "" else JSONObject(m as Map<*, *>).toString()
    }
}

/**
 * A clock tile keeps "<style>|<photo address>|<photo design>" in its arg. Any part can be empty, and a plain style id
 * is still valid. [set] changes one part and keeps the others.
 */
object ClockArg {
    private fun part(arg: String, i: Int) = arg.split('|').getOrElse(i) { "" }
    fun style(arg: String) = part(arg, 0)
    fun image(arg: String) = part(arg, 1)
    fun design(arg: String) = part(arg, 2)
    fun set(arg: String, style: String? = null, image: String? = null, design: String? = null): String {
        val s = style ?: style(arg)
        val i = image ?: image(arg)
        val d = design ?: design(arg)
        return when {
            d.isNotEmpty() -> "$s|$i|$d"
            i.isNotEmpty() -> "$s|$i"
            else -> s
        }
    }
}

/** A music tile keeps "<app key>|<style>" in its arg. Either part can be empty. */
object MusicArg {
    private fun part(arg: String, i: Int) = arg.split('|').getOrElse(i) { "" }
    fun app(arg: String) = part(arg, 0)
    fun style(arg: String) = part(arg, 1)
    fun design(arg: String) = part(arg, 2)
    /** Stored as "app|style|design". Changes one part and keeps the others. */
    fun set(arg: String, app: String? = null, style: String? = null, design: String? = null): String {
        val a = app ?: app(arg)
        val s = style ?: style(arg)
        val d = design ?: design(arg)
        return when {
            d.isNotEmpty() -> "$a|$s|$d"
            s.isNotEmpty() -> "$a|$s"
            else -> a
        }
    }
}
