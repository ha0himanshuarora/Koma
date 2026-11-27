package app.koma.launcher

import android.app.ActivityManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.koma.launcher.ui.HomeScreen
import app.koma.launcher.ui.HomeState
import app.koma.launcher.ui.Visibility
import app.koma.launcher.ui.komaApps

/**
 * A Koma app (Clock, Notes, Reminders, Calculator) in a window of its own, with its own entry in the phone's recent
 * apps. Which one is in the [EXTRA_APP] extra, named as in the list of Koma apps. Going home or back from the app's
 * first page closes the window and returns to wherever you were.
 *
 * The window keeps its own copy of the app's data, read again each time it comes back on screen, because notes,
 * reminders and alarms are saved in files that every window shares.
 */
class KomaAppActivity : ComponentActivity() {
    private lateinit var state: HomeState
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        state = HomeState(applicationContext)
        val target = komaApps(state).firstOrNull { it.id == intent.getStringExtra(EXTRA_APP) }
        if (target == null) {
            finish()
            return
        }
        state.standaloneExit = { finish() }
        if (Build.VERSION.SDK_INT >= 28) setTaskDescription(ActivityManager.TaskDescription.Builder().setLabel(target.name).build())
        else @Suppress("DEPRECATION") setTaskDescription(ActivityManager.TaskDescription(target.name))
        target.open()
        applyExtras(intent)
        setContent { HomeScreen(state, 0, false) }
    }

    /** A tile can ask for a particular clock tab, note or timer length when it opens the window. */
    private fun applyExtras(i: Intent) {
        if (i.hasExtra(EXTRA_TAB)) state.clockTab = i.getIntExtra(EXTRA_TAB, 0)
        if (i.hasExtra(EXTRA_TIMER)) state.timerSec = i.getIntExtra(EXTRA_TIMER, 0)
        i.getStringExtra(EXTRA_NOTE)?.let { state.noteOpenId = it }
        i.getStringExtra(EXTRA_PAGE)?.let { state.settingsPage = it }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra(EXTRA_TAB) || intent.hasExtra(EXTRA_NOTE) || intent.hasExtra(EXTRA_PAGE)) {
            komaApps(state).firstOrNull { it.id == intent.getStringExtra(EXTRA_APP) }?.open?.invoke()
            applyExtras(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        Visibility.enter()
        if (started) state.reloadData()
        started = true
        state.permissionsChanged()
    }

    override fun onStop() {
        Visibility.leave()
        super.onStop()
    }

    companion object {
        const val EXTRA_APP = "koma_app"
        const val EXTRA_TAB = "koma_tab"
        const val EXTRA_NOTE = "koma_note"
        const val EXTRA_TIMER = "koma_timer"
        const val EXTRA_PAGE = "koma_page"
    }
}
