package app.koma.launcher.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

private class KApp(val name: String, val sub: String, val glyph: String, val go: () -> Unit)

/** Every app that comes with Koma, in one place. Opening one from here makes Back return here. */
@Composable
fun KomaApps(state: HomeState) {
    val pal = state.palette
    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val alarmsOn = state.clock.alarms.count { it.on }
    val apps = listOf(
        KApp("Clock", if (alarmsOn == 0) "Alarms, timer, focus" else "$alarmsOn alarm${if (alarmsOn == 1) "" else "s"} on", "n_alarm") { state.openFromHub { state.openClock(0) } },
        KApp("Notes", "${state.notes.count { it.trashed == 0L && !it.archived }} notes", "note") { state.openFromHub { state.open(Screen.Notes) } },
        KApp("Reminders", "${state.tasks.count { !it.done }} open", "tasks") { state.openFromHub { state.open(Screen.Tasks) } },
        KApp("Calculator", "Convert anything", "calc") { state.openFromHub { state.open(Screen.Calc) } },
    )
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
            Group {
                apps.forEachIndexed { i, a ->
                    ListRow(a.name, glyph = a.glyph, tint = pal.tiles[(i + 1) % pal.tiles.size], onClick = { a.go() })
                }
            }
        }
    }
}
