package app.koma.launcher

import android.app.Activity
import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.Toast
import app.koma.launcher.data.AppMenu

/**
 * Answers "pin this shortcut to the home screen" from other apps. A browser's "Install app" for a website arrives
 * here. The shortcut is accepted straight away and then shows up in Koma's app drawer.
 */
class PinShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val la = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
            val request = la.getPinItemRequest(intent)
            if (request != null && request.isValid && request.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
                val label = request.shortcutInfo?.let { AppMenu.label(it) }.orEmpty().ifBlank { "Shortcut" }
                if (request.accept()) Toast.makeText(this, "$label added to Koma apps", Toast.LENGTH_SHORT).show()
                else Toast.makeText(this, "Could not add $label", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Set Koma as your default launcher to add shortcuts", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
