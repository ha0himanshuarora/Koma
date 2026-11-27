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
    /** Set by a window of its own (Koma settings) that cannot start editing the home screen itself; the home screen starts it on return. */
    @Volatile var pendingEdit = false

    val shown = MutableStateFlow(true)
    private var visible = 0

    /** An activity of Koma came on screen. Counted, because the next one starts before the last one stops. */
    fun enter() {
        visible++
        shown.value = true
    }

    /** An activity of Koma left the screen. Koma counts as hidden only once none is showing. */
    fun leave() {
        visible = (visible - 1).coerceAtLeast(0)
        shown.value = visible > 0
    }
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
