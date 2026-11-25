package app.koma.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Shown at the bottom of the screen (thumb reach) every time Koma opens while it is not the default home app.
 * "Not now" hides it until the next time Koma is opened.
 */
@Composable
fun DefaultLauncherPrompt(state: HomeState, onSet: () -> Unit, onDismiss: () -> Unit) {
    val pal = state.palette
    BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp)
                .clip(RoundedCornerShape(28.dp)).background(pal.ground)
                // Swallow taps on the card so they do not dismiss it.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText("MAKE KOMA YOUR HOME SCREEN?", style = monoStyle(pal.onGround.copy(alpha = 0.6f), 11))
            BasicText("Set Koma as default", style = dispStyle(pal.onGround, 26))
            BasicText(
                "Koma is not your default launcher yet. Set it as default so the home button and gestures open it.",
                style = bodyStyle(pal.onGround.copy(alpha = 0.75f), 15),
            )
            Pill("SET AS DEFAULT", pal.tiles[0], pal.ink, Modifier.fillMaxWidth(), height = 52) { onSet() }
            Pill("NOT NOW", pal.onGround.copy(alpha = 0.12f), pal.onGround, Modifier.fillMaxWidth(), height = 46) { onDismiss() }
        }
    }
}
