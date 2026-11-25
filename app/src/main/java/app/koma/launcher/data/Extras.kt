package app.koma.launcher.data

import java.time.LocalDate

/** The saved text of a Countdown tile: "yyyy-MM-dd|name". */
object Countdown {
    fun make(date: LocalDate, name: String) = "$date|${name.replace('|', ' ')}"
    fun date(arg: String): LocalDate? = try { LocalDate.parse(arg.substringBefore('|')) } catch (e: Exception) { null }
    fun rawName(arg: String) = arg.substringAfter('|', "")
    fun name(arg: String) = rawName(arg).ifBlank { "Event" }
}

/** What a Words tile shows and how it looks. Saved as JSON in the tile; the older plain-text forms still load. */
data class WordsCfg(
    /** "text" (your own words), "day" (today's weekday) or "greet" (a new hello every hour). */
    val mode: String = "text",
    val text: String = "今日",
    /** "bold", "light", "serif" or "display". */
    val style: String = "bold",
    /** "auto", "stack" (one letter per line) or "across". */
    val layout: String = "auto",
    /** An index into the palette, or -1 to pick whatever reads best. */
    val tone: Int = -1,
    val caps: Boolean = false,
    val faded: Boolean = false,
)

object Words {
    val ideas = listOf("今日", "夢", "静", "愛", "風", "禅", "Breathe", "Hello", "Focus", "Be kind", "Onward")

    fun parse(arg: String): WordsCfg {
        if (arg.startsWith("{")) {
            try {
                val o = org.json.JSONObject(arg)
                return WordsCfg(
                    o.optString("mode", "text"), o.optString("text", "今日"), o.optString("style", "bold"), o.optString("layout", "auto"),
                    o.optInt("tone", -1), o.optBoolean("caps", false), o.optBoolean("faded", false),
                )
            } catch (e: Exception) {
            }
        }
        return when (arg) {
            "" -> WordsCfg()
            "day" -> WordsCfg(mode = "day")
            "rotate" -> WordsCfg(mode = "greet")
            else -> WordsCfg(text = arg)
        }
    }

    fun encode(c: WordsCfg): String = org.json.JSONObject()
        .put("mode", c.mode).put("text", c.text).put("style", c.style).put("layout", c.layout)
        .put("tone", c.tone).put("caps", c.caps).put("faded", c.faded).toString()
}
