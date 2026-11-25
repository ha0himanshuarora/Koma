package app.koma.launcher.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Whether Koma is on screen. Compose keeps running coroutines while the activity is stopped, so every
 * repeating refresh (weather, calendar, calls, clocks...) calls [awaitVisible] first and sleeps until Koma
 * is shown again. Nothing wakes the phone while another app is in front or the screen is off.
 */
object Visibility {
    val shown = MutableStateFlow(true)
}

suspend fun awaitVisible() {
    if (!Visibility.shown.value) Visibility.shown.first { it }
}

/**
 * Sleeps [ms], or less if Koma is hidden and then shown again meanwhile, so a clock that slept through the screen being
 * off catches up the moment it is looked at. Unlike polling, this wakes the phone once per [ms] and no more.
 */
suspend fun sleepOrResume(ms: Long) {
    withTimeoutOrNull(ms.coerceAtLeast(1L)) {
        Visibility.shown.first { !it }
        Visibility.shown.first { it }
    }
}
