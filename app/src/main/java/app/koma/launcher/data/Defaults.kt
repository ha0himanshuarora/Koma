package app.koma.launcher.data

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/** First-run layout, matching the Main screen in the design. */
object Defaults {

    /** Finds the app the system uses for each shortcut role, as a "package/activity" key. */
    fun resolveRoles(pm: PackageManager, apps: List<AppInfo>): Map<String, String> {
        fun defaultPackage(intent: Intent): String? {
            val pkg = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            return if (pkg == null || pkg == "android") null else pkg
        }

        val packages = mapOf(
            "CAM" to defaultPackage(Intent("android.media.action.STILL_IMAGE_CAMERA")),
            "WEB" to defaultPackage(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)),
            "CHAT" to defaultPackage(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            "PHONE" to defaultPackage(Intent(Intent.ACTION_DIAL)),
            "PICS" to defaultPackage(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)),
            "MAIL" to defaultPackage(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)),
            "VIDEO" to apps.firstOrNull { it.pkg == "com.google.android.youtube" }?.pkg,
        )
        val result = HashMap<String, String>()
        for ((role, pkg) in packages) {
            if (pkg == null) continue
            apps.firstOrNull { it.pkg == pkg }?.let { result[role] = it.key }
        }
        return result
    }

    /** The quick shortcuts offered in Add component: label and line glyph. */
    val roleList = listOf("CAM" to "cam", "WEB" to "web", "CHAT" to "chat", "PHONE" to "phone", "PICS" to "pics", "VIDEO" to "video", "MAIL" to "mail")

    private fun role(label: String, glyph: String, color: Int, roles: Map<String, String>) =
        Tile("role-$glyph", Kind.APP, 1, 1, arg = roles[label] ?: "", label = label, glyph = glyph, color = color)

    /**
     * First layout of the Today home type: the three cards (next event tall on the left, timer and focus on its right)
     * and a screen time row below. The clock, music strip and dock are part of the screen itself, not tiles.
     */
    fun todayLayout(): List<Tile> = listOf(
        Tile("today-next", Kind.CALENDAR, 2, 2, color = 0),
        Tile("today-timer", Kind.TIMER, 2, 1, color = 2),
        Tile("today-focus", Kind.FOCUS, 2, 1, color = 1),
        Tile("today-screen", Kind.SCREENTIME, 4, 1, color = 4),
    )

    // Colour indexes follow the Amber Signal order: 0 amber, 1 vermilion, 2 sage, 3 sky, 4 bone.
    fun layout(roles: Map<String, String>): List<Tile> = listOf(
        Tile("clock", Kind.CLOCK, 3, 3, color = 0),
        Tile("kanji", Kind.KANJI, 1, 2, color = 1),
        Tile("date", Kind.DATE, 1, 1, color = 4),
        Tile("weather", Kind.WEATHER, 2, 2, color = 2),
        Tile("music", Kind.MUSIC, 2, 2, color = 4),
        Tile("next", Kind.CALENDAR, 4, 1, color = 3),
        role("CAM", "cam", 4, roles),
        role("WEB", "web", 2, roles),
        role("CHAT", "chat", 3, roles),
        role("PHONE", "phone", 1, roles),
        role("PICS", "pics", 4, roles),
        role("VIDEO", "video", 3, roles),
        role("MAIL", "mail", 0, roles),
        Tile("all", Kind.ALL, 1, 1),
    )
}
