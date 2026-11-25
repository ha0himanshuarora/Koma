package app.koma.launcher.data

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build

/** Hosts standard Android widgets so they sit in the tile grid like any other tile. */
class WidgetHost(private val ctx: Context) {
    val host = AppWidgetHost(ctx, 1024)
    val manager: AppWidgetManager = AppWidgetManager.getInstance(ctx)

    fun start() {
        try {
            host.startListening()
        } catch (e: Exception) {
        }
    }

    fun stop() {
        try {
            host.stopListening()
        } catch (e: Exception) {
        }
    }

    /** Every widget that can sit on a home screen, from all apps (and the work profile, if there is one). */
    fun providers(): List<AppWidgetProviderInfo> =
        manager.installedProviders
            .filter { (it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0 }
            .sortedBy { it.loadLabel(ctx.packageManager)?.lowercase().orEmpty() }

    fun allocate(): Int = host.allocateAppWidgetId()

    fun bindIfAllowed(id: Int, info: AppWidgetProviderInfo): Boolean =
        try {
            manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)
        } catch (e: Exception) {
            false
        }

    /**
     * Runs the widget's own setup screen through the system. Starting it ourselves fails for the many widgets whose
     * setup screen is not exported; the system launches it on the widget's behalf.
     */
    fun configure(activity: Activity, id: Int) {
        host.startAppWidgetConfigureActivityForResult(activity, id, 0, REQ_CONFIGURE, null)
    }

    /** True when the widget works without its setup screen (Android 12+ widgets can say so). */
    fun configureOptional(info: AppWidgetProviderInfo): Boolean =
        Build.VERSION.SDK_INT >= 31 &&
            (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0

    companion object {
        const val REQ_CONFIGURE = 4021
    }

    fun delete(id: Int) {
        try {
            host.deleteAppWidgetId(id)
        } catch (e: Exception) {
        }
    }

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    /** Grid footprint for a widget, from the size it asks for. Cells are about 80dp wide. */
    fun cells(info: AppWidgetProviderInfo): Pair<Int, Int> {
        val d = ctx.resources.displayMetrics.density
        val w = Math.ceil((info.minWidth / d / 76.0)).toInt().coerceIn(1, 4)
        val h = Math.ceil((info.minHeight / d / 76.0)).toInt().coerceIn(1, 6)
        return w to h
    }
}
