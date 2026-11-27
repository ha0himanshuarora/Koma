package app.koma.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import app.koma.launcher.ui.HomeScreen
import app.koma.launcher.ui.HomeState

class MainActivity : ComponentActivity() {
    private var homePress by mutableIntStateOf(0)
    private var homeInApp by androidx.compose.runtime.mutableStateOf(false)
    private lateinit var state: HomeState
    private var started = false
    private var askDefault by androidx.compose.runtime.mutableStateOf(false)

    // True while the system "set default" dialog is up, so coming back from it does not ask again at once.
    private var awaitingRole = false

    private val roleRequest = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {
        awaitingRole = false
        askDefault = false
    }

    private fun isDefaultHome(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val rm = getSystemService(android.app.role.RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) return rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo?.packageName == packageName
    }

    private fun requestDefaultHome() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val rm = getSystemService(android.app.role.RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                    awaitingRole = true
                    roleRequest.launch(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME))
                    return
                }
            }
            startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            try { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) } catch (_: Exception) {}
        }
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            state.appsChanged()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.koma.launcher.data.KomaFocusService.ensureDeclared(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        state = HomeState(applicationContext)
        setContent {
            HomeScreen(state, homePress, homeInApp)
            if (askDefault) app.koma.launcher.ui.DefaultLauncherPrompt(state, onSet = { askDefault = false; requestDefaultHome() }, onDismiss = { askDefault = false })
        }
    }

    override fun onStart() {
        super.onStart()
        app.koma.launcher.ui.Visibility.enter()
        // Another window of Koma may have changed notes, reminders or alarms while this one was behind it.
        if (started) state.reloadData()
        started = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        // Permissions and notification access may have changed while we were away (system settings).
        state.permissionsChanged()
        // So may the installed apps: an uninstall done from the system screen is not heard while we are stopped.
        state.syncApps()
        // Ask every time Koma opens until it is the default launcher.
        if (!awaitingRole) askDefault = !isDefaultHome()
        state.media.start()
        state.widgets.start()
        state.watchWallpaper(true)
    }

    override fun onStop() {
        if (state.homeAfterLaunch) {
            state.homeAfterLaunch = false
            state.goHome()
        }
        app.koma.launcher.ui.Visibility.leave()
        state.watchWallpaper(false)
        state.widgets.stop()
        state.media.stop()
        unregisterReceiver(packageReceiver)
        super.onStop()
    }

    // The phone switched between light and dark (the manifest handles uiMode changes without recreating us).
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        state.systemChanged(newConfig.uiMode)
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == app.koma.launcher.data.WidgetHost.REQ_CONFIGURE) {
            state.widgetConfigured(resultCode == RESULT_OK)
        }
    }

    // The home button re-delivers the HOME intent to this singleTask activity.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            // True when Koma was already on screen (not brought forward from another app).
            homeInApp = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
            homePress++
        }
    }
}
