package app.koma.launcher.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private const val AUTHOR = "ha0himanshuarora"
private const val EMAIL = "ha0himanshuarora@gmail.com"

private class Build(val name: String, val code: Long)

@Composable
private fun rememberBuild(): Build {
    val ctx = LocalContext.current
    return remember {
        try {
            val p = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            Build(p.versionName ?: "", p.longVersionCode)
        } catch (e: Exception) {
            Build("", 0L)
        }
    }
}

/** Version text for the System page. */
@Composable
internal fun versionText(): String = rememberBuild().name.let { if (it.isEmpty()) "" else "Version $it" }

@Composable
private fun Chip(text: String, ink: Color) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(ink.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 5.dp)) {
        BasicText(text, style = monoStyle(ink, 9, bold = true), maxLines = 1)
    }
}

@Composable
private fun Hero(state: HomeState, content: @Composable () -> Unit) {
    HeroBox(state.palette.tiles[0]) { content() }
}

/** One small figure: a big word over a caption. */
@Composable
private fun Fact(big: String, cap: String, modifier: Modifier) {
    val pal = LocalPalette.current
    Column(modifier.clip(Mid).background(cardBg()).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BasicText(big, style = dispStyle(pal.onGround, 30), maxLines = 1)
        BasicText(cap, style = monoStyle(pal.onGround.copy(alpha = 0.55f), 9, bold = true), maxLines = 1)
    }
}

/** The Clock's action tile: a caption and a big word at the bottom left, an icon chip at the top right. */
@Composable
private fun AboutTile(word: String, cap: String, glyph: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val fg = if (primary) pal.ink else pal.onGround
    Box(modifier.height(92.dp).clip(TileShape).background(if (primary) pal.tiles[0] else cardBg()).clickable(onClick = onClick)) {
        Box(
            Modifier.align(Alignment.TopEnd).padding(12.dp).size(30.dp).clip(CircleShape).background(if (primary) pal.ink else surfaceColor(0.14f)),
            contentAlignment = Alignment.Center,
        ) { Glyph(glyph, if (primary) pal.tiles[0] else pal.onGround, 15.dp, 2.4f) }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 12.dp)) {
            BasicText(cap, style = monoStyle(fg.copy(alpha = 0.65f), 9, bold = true), maxLines = 1)
            BasicText(word, style = dispStyle(fg, 24), maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    val pal = LocalPalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10, bold = true))
        BasicText(value, style = bodyStyle(pal.onGround, 14, medium = true), maxLines = 1)
    }
}

/** About Koma: what it is, the version, the privacy policy, who made it and how to reach them. */
@Composable
internal fun AboutPage(state: HomeState) {
    val pal = state.palette
    val t = pal.tiles
    val ctx = LocalContext.current
    val b = rememberBuild()
    Page("About", "SETTINGS / SYSTEM", onBack = { state.settingsPage = "system" }) {
        Hero(state) {
            BasicText("KOMA", style = monoStyle(pal.ink.copy(alpha = 0.7f), 10, bold = true))
            BasicText("A launcher made of tiles.", style = dispStyle(pal.ink, 32))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(if (b.name.isEmpty()) "VERSION" else "VERSION ${b.name}", pal.ink)
                if (b.code > 0) Chip("BUILD ${b.code}", pal.ink)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Fact("0", "ACCOUNTS", Modifier.weight(1f))
            Fact("0", "ADS", Modifier.weight(1f))
            Fact("0", "ANALYTICS", Modifier.weight(1f))
        }
        Group("WHAT IT IS") {
            GroupPad {
                BasicText(
                    "Your home screen is a grid of live tiles that you arrange yourself. Colours follow your wallpaper or a theme you choose, " +
                        "and a few small apps come built in: clock, notes, reminders, calculator and focus.",
                    style = bodyStyle(pal.onGround.copy(alpha = 0.85f), 14),
                )
                BasicText(
                    "It stays light on purpose. Everything you set up stays on your phone.",
                    style = bodyStyle(pal.onGround.copy(alpha = 0.85f), 14),
                )
            }
        }
        Group("PRIVACY") {
            ListRow("Privacy policy", "What Koma stores and shares", "lock", t[1 % t.size], onClick = { state.settingsPage = "privacy" })
            ListRow("Permissions", "What Koma can access", "info", t[2 % t.size], onClick = { state.settingsPage = "permissions" })
        }
        Group("AUTHOR") {
            GroupPad {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(52.dp).clip(CircleShape).background(t[3 % t.size]), contentAlignment = Alignment.Center) {
                        BasicText(AUTHOR.take(1).uppercase(), style = dispStyle(pal.ink, 26))
                    }
                    Column {
                        BasicText(AUTHOR, style = dispStyle(pal.onGround, 22), maxLines = 1)
                        BasicText("DESIGN AND CODE", style = monoStyle(pal.onGround.copy(alpha = 0.55f), 9, bold = true))
                    }
                }
            }
        }
        Group("REACH OUT") {
            GroupPad {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AboutTile("EMAIL", "WRITE TO ME", "mail", true, Modifier.weight(1.3f)) {
                        try {
                            ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$EMAIL")).putExtra(Intent.EXTRA_SUBJECT, "Koma").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) {
                        }
                    }
                    AboutTile("COPY", "EMAIL ADDRESS", "note", false, Modifier.weight(1f)) { copy(ctx, EMAIL) }
                }
                Line("EMAIL", EMAIL)
                Line("PORTFOLIO", AUTHOR)
            }
        }
        Group("CREDITS") {
            GroupPad {
                Line("BUILT WITH", "Kotlin, Jetpack Compose")
                Line("WEATHER", "Open-Meteo")
                Line("FONTS", "SIL Open Font License")
            }
        }
        Group("BUILD") {
            GroupPad {
                Line("VERSION", b.name.ifEmpty { "Unknown" })
                Line("BUILD", b.code.toString())
                Line("PACKAGE", ctx.packageName)
            }
        }
    }
}

private fun copy(ctx: Context, text: String) {
    try {
        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("email", text))
        // Android 13 and later show their own "copied" confirmation, so Koma adds one only on older versions.
        if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
    }
}

private class Perm(val name: String, val why: String, val optional: Boolean = false)

private val perms = listOf(
    Perm("Contacts", "Starred contacts and contact search. Names and numbers are read on the device and never sent anywhere."),
    Perm("Call log", "The Recent calls tile."),
    Perm("Calendar", "The Calendar tile."),
    Perm("Approximate location", "Picks the place for the Weather tile. Only rounded coordinates are sent."),
    Perm("Internet", "Weather, currency rates, and opening web searches in your browser."),
    Perm("Usage access", "Screen time and app limits. You grant it in system settings.", true),
    Perm("Notification access", "Lets Now playing read and control the playing media. Notification content is not read or stored.", true),
    Perm("Accessibility service", "Focus Lock sends you home when a blocked app opens, and runs the system gestures you pick. It does not read the screen or record input.", true),
    Perm("Alarms and notifications", "Alarms, timers and reminders, including over the lock screen."),
    Perm("Bluetooth and Wi-Fi state", "The Device and Bluetooth tiles."),
)

/** What each permission is for, and which ones are optional. */
@Composable
internal fun PermissionsPage(state: HomeState) {
    val pal = state.palette
    Page("Permissions", "SETTINGS / ABOUT", onBack = { state.settingsPage = "about" }) {
        BasicText(
            "Nothing is asked for until a feature needs it. Refuse or revoke any of these and the matching tile simply stays empty.",
            style = bodyStyle(pal.onGround.copy(alpha = 0.8f), 14),
        )
        Group("ASKED WHEN NEEDED") {
            perms.filter { !it.optional }.forEach { PermRow(it) }
        }
        Group("OPTIONAL, GRANTED IN SYSTEM SETTINGS") {
            perms.filter { it.optional }.forEach { PermRow(it) }
        }
    }
}

@Composable
private fun PermRow(p: Perm) {
    val pal = LocalPalette.current
    GroupPad {
        BasicText(p.name, style = bodyStyle(pal.onGround, 15, medium = true))
        BasicText(p.why, style = bodyStyle(pal.onGround.copy(alpha = 0.7f), 13))
    }
}

private val policy = listOf(
    "ON YOUR DEVICE" to "Your layout, settings, notes, tasks, Focus history and imported fonts live in the app's private storage. Nothing is uploaded, and backups are turned off.",
    "SENT OFF THE DEVICE" to "The Weather tile sends your approximate coordinates to a public weather service (Open-Meteo) to get a forecast, and the Calculator asks a public service for currency rates. " +
        "No identifier or account is attached. Web searches open in your own browser with the engine you chose, and that engine's policy applies from there.",
    "PERMISSIONS" to "Each permission is used only for the feature that asks for it, and you can refuse or revoke any of them. The Permissions page lists what each one is for.",
    "CHILDREN" to "Koma is not directed at children and collects nothing from anyone.",
    "CHANGES AND CONTACT" to "If this policy changes, the date below changes. Questions: $EMAIL",
)

/** The privacy policy, readable inside the app. */
@Composable
internal fun PrivacyPage(state: HomeState) {
    val pal = state.palette
    val t = pal.tiles
    Page("Privacy policy", "SETTINGS / ABOUT", onBack = { state.settingsPage = "about" }) {
        Hero(state) {
            BasicText("YOUR DATA", style = monoStyle(pal.ink.copy(alpha = 0.7f), 10, bold = true))
            BasicText("Stays on your phone.", style = dispStyle(pal.ink, 30))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("NO ACCOUNT", pal.ink)
                Chip("NO ADS", pal.ink)
                Chip("NO ANALYTICS", pal.ink)
            }
        }
        policy.forEachIndexed { i, (head, body) ->
            Group("${(i + 1).toString().padStart(2, '0')} / $head") {
                GroupPad { BasicText(body, style = bodyStyle(pal.onGround.copy(alpha = 0.85f), 14)) }
            }
        }
        ListRow("Permissions", "What each one is for", "info", t[2 % t.size], onClick = { state.settingsPage = "permissions" })
        BasicText("LAST UPDATED 25 NOVEMBER 2026", style = monoStyle(pal.onGround.copy(alpha = 0.45f), 10, bold = true))
    }
}
