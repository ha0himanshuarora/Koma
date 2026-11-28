package app.koma.launcher.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.koma.launcher.KomaAppActivity

/**
 * One of Koma's own apps. Every app listed in [komaApps] is shown in the Koma apps page, and each one with
 * [searchable] set also turns up in launcher search, matched by its name or any of its [keywords]. A new Koma app
 * only has to be added to that list: search and the page both read it, so neither needs changing.
 */
internal class KomaApp(
    val id: String,
    val name: String,
    val sub: String,
    val glyph: String,
    val keywords: List<String> = emptyList(),
    val searchable: Boolean = true,
    val open: () -> Unit,
)

/**
 * Opens a Koma app in a window of its own, with its own entry in the phone's recent apps, like any other app.
 * Opening it again brings that window back instead of making another.
 */
/** The app's own alias (own name and icon in recents), or the plain activity if it has none yet. */
private fun komaAppComponent(ctx: Context, target: KomaApp): ComponentName {
    val alias = ComponentName(ctx, "app.koma.launcher.Koma${target.id.replaceFirstChar { it.uppercase() }}Alias")
    return try {
        ctx.packageManager.getActivityInfo(alias, 0)
        alias
    } catch (e: Exception) {
        ComponentName(ctx, KomaAppActivity::class.java)
    }
}

internal fun launchKomaApp(ctx: Context, target: KomaApp, extras: (Intent) -> Unit = {}): Boolean {
    return try {
        ctx.startActivity(
            Intent().setComponent(komaAppComponent(ctx, target))
                .putExtra(KomaAppActivity.EXTRA_APP, target.id)
                // The address tells the system which window this is, so each app gets one of its own.
                .setData(Uri.parse("koma://app/${target.id}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                .also(extras),
        )
        true
    } catch (e: Exception) {
        false
    }
}

/** Every app that comes with Koma. [open] shows it plainly; the Koma apps page wraps it so Back returns there. */
internal fun komaApps(state: HomeState): List<KomaApp> {
    val alarmsOn = state.clock.alarms.count { it.on }
    return listOf(
        KomaApp("clock", "Clock", if (alarmsOn == 0) "Alarms, timer, focus" else "$alarmsOn alarm${if (alarmsOn == 1) "" else "s"} on", "n_alarm", listOf("alarm", "timer", "stopwatch", "focus", "world clock")) { state.openClock(0) },
        KomaApp("notes", "Notes", "${state.notes.count { it.trashed == 0L && !it.archived }} notes", "note", listOf("note", "memo", "write")) { state.open(Screen.Notes) },
        KomaApp("reminders", "Reminders", "${state.tasks.count { !it.done }} open", "tasks", listOf("tasks", "todo", "to-do", "remind")) { state.open(Screen.Tasks) },
        KomaApp("settings", "Koma settings", "Looks, layout, gestures", "settings", listOf("settings", "preferences", "theme", "colour", "layout", "gestures", "icons", "fonts", "about", "koma")) { state.open(Screen.Settings) },
        KomaApp("weather", "Weather", "Forecast, hourly, 7 days", "sun", listOf("weather", "forecast", "temperature", "rain", "wind", "humidity", "uv", "sunrise", "sunset")) { state.open(Screen.Weather) },
        KomaApp("screentime", "Screen time", "Today, week, limits", "hourglass", listOf("screen time", "usage", "digital wellbeing", "app limits", "limits", "pickups")) { state.open(Screen.ScreenTime) },
        KomaApp("calc", "Calculator", "Convert anything", "calc", listOf("calc", "convert", "units", "currency")) { state.open(Screen.Calc) },
    )
}

/** Every app that comes with Koma, in one place. Opening one from here makes Back return here. */
@Composable
fun KomaApps(state: HomeState) {
    val pal = state.palette
    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val ctx = LocalContext.current
    val apps = komaApps(state)
    Column(Modifier.fillMaxSize().background(pal.ground).statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleBtn("back") { state.goHome() }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                BasicText("Koma apps", style = dispStyle(pal.onGround, 30), maxLines = 1)
                BasicText("BUILT IN / ${apps.size} APPS", style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10), maxLines = 1)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PageHero("BUILT IN", "Koma apps", "Made for the launcher. Nothing leaves your phone.", pal.tiles[0])
            Box(Modifier.tourTarget("kapps_list")) {
                Group {
                    apps.forEachIndexed { i, a ->
                        ListRow(a.name, glyph = a.glyph, tint = pal.tiles[(i + 1) % pal.tiles.size], onClick = { if (state.tour.active || !launchKomaApp(ctx, a)) a.open() })
                    }
                }
            }
        }
    }
}
