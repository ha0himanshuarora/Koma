package app.koma.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.koma.launcher.data.AppInfo
import app.koma.launcher.data.ContactHit
import app.koma.launcher.data.ContactSearch
import app.koma.launcher.data.Engines
import app.koma.launcher.data.FocusEngine
import app.koma.launcher.data.Quick
import app.koma.launcher.data.AppMatch
import app.koma.launcher.data.QuickAnswer
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One line of app search: an installed app, or one of Koma's own apps (never both). */
private class SearchApp(val label: String, val app: AppInfo?, val koma: KomaApp?) {
    /** The text to rank for [q]: the name, or the keyword that matched when the name does not. */
    fun matchText(q: String): String {
        if (koma == null || AppMatch.rank(label, q) >= 0) return label
        return koma.keywords.firstOrNull { AppMatch.rank(it, q) >= 0 } ?: label
    }
}

/**
 * Quick search, opened by pressing Home again on the home screen. Everything sits at the bottom, next to the
 * thumb and the keyboard: the field at the very bottom, matching apps above it (best match closest), and the
 * web search row above the apps. Only apps that match are listed.
 */
@Composable
fun SearchScreen(state: HomeState) {
    val pal = state.palette
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focusReq = remember { FocusRequester() }
    val q = query.trim()
    val engineName = Engines.all.firstOrNull { it.first == state.prefs.engine }?.second?.first ?: "the web"

    // Listed top to bottom, so the best match is the last one, right above the field.
    val apps: List<SearchApp> = remember(state.apps, q) {
        if (q.isEmpty()) {
            state.recentApps().filter { FocusEngine.allows(it.pkg) }.take(5).reversed().map { SearchApp(it.label, it, null) }
        } else {
            // Koma's own apps that are flagged searchable sit among the installed ones and rank the same way.
            val usable = state.apps.filter { FocusEngine.allows(it.pkg) }.map { SearchApp(it.label, it, null) } +
                komaApps(state).filter { it.searchable }.map { SearchApp(it.name, null, it) }
            app.koma.launcher.data.AppMatch.filter(usable, q) { it.matchText(q) }
                .take(5)
                .reversed()
        }
    }

    val quick = remember(q) { Quick.answer(q) }
    val contacts = rememberContacts(state, q)
    val suggestions = rememberWebSuggestions(q)

    fun openApp(a: SearchApp) {
        if (a.app != null) state.launchThenHome(a.app.key) else a.koma?.let {
            // Search closes behind the new window, so coming back lands on the home screen.
            if (launchKomaApp(ctx, it)) state.goHome() else it.open()
        }
    }

    fun web() {
        if (q.isEmpty()) return
        Engines.search(ctx, state.prefs, q)
        state.goHome()
    }

    LaunchedEffect(Unit) {
        try {
            focusReq.requestFocus()
        } catch (e: Exception) {
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).pointerInput(Unit) { detectTapGestures { state.goHome() } },
    ) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().statusBarsPadding().imePadding().navigationBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (q.isNotEmpty() || apps.isNotEmpty() || quick != null || contacts.isNotEmpty()) {
                // Tall results scroll inside the panel, starting from the bottom so the apps next to the field stay in view.
                Column(
                    Modifier.weight(1f, fill = false).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(pal.ground.copy(alpha = 0.96f))
                        .border(1.dp, pal.onGround.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                        .pointerInput(Unit) { detectTapGestures { } }
                        .verticalScroll(rememberScrollState(), reverseScrolling = true)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    if (quick != null) QuickRow(state, quick)
                    // Top to bottom: plain web search, people, then apps next to the field.
                    if (q.isNotEmpty()) WebRow(state, q, noApp = apps.isEmpty())
                    ContactRows(state, q, contacts, onOpen = { state.goHome() })
                    if (q.isEmpty() && apps.isNotEmpty()) {
                        BasicText("RECENT", style = monoStyle(pal.onGround.copy(alpha = 0.6f), 9), modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 4.dp))
                    }
                    apps.forEachIndexed { idx, a ->
                        val best = q.isNotEmpty() && idx == apps.lastIndex
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(if (best) pal.onGround.copy(alpha = 0.08f) else Color.Transparent)
                                .clickable { openApp(a) }.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (a.app != null) AppIcon(state.icons, a.app, 38.dp, bg = pal.tiles[abs(a.app.key.hashCode()) % pal.tiles.size], ink = pal.ink, line = pal.onGround)
                            else GlyphIcon(a.koma?.glyph ?: "all", 38.dp, pal.tiles[abs(a.label.hashCode()) % pal.tiles.size], pal.ink, pal.onGround)
                            Spacer(Modifier.width(14.dp))
                            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                                BasicText(a.label, style = bodyStyle(pal.onGround, 17, medium = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (a.koma != null) BasicText("KOMA APP", style = monoStyle(pal.onGround.copy(alpha = 0.55f), 8))
                                else if (a.app != null && a.app.key in state.hiddenKeys) BasicText("hidden", style = monoStyle(pal.onGround.copy(alpha = 0.55f), 8))
                            }
                            if (best) Glyph("arrow", pal.onGround.copy(alpha = 0.6f), 18.dp, 2f)
                        }
                    }
                }
            }
            // Web suggestions are one slim strip above the field, so they never push the apps away.
            if (suggestions.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { SuggestionChip(state, it) }
                }
            }
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(50)).background(pal.ground.copy(alpha = 0.96f))
                    .border(1.dp, pal.onGround.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(start = 18.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Magnifier(pal.onGround.copy(alpha = 0.7f), 20.dp)
                Spacer(Modifier.width(10.dp))
                @Suppress("DEPRECATION")
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = bodyStyle(pal.onGround, 17, medium = true),
                    cursorBrush = SolidColor(pal.onGround),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (q.isNotEmpty() && apps.isNotEmpty()) openApp(apps.last())
                        else if (quick != null) runQuick(ctx, quick)
                        else web()
                    }),
                    modifier = Modifier.weight(1f).focusRequester(focusReq),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) BasicText("Search apps, people, the web", style = bodyStyle(pal.onGround.copy(alpha = 0.45f), 17, medium = true))
                            inner()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(pal.onGround.copy(alpha = 0.12f)).clickable { query = "" },
                        contentAlignment = Alignment.Center,
                    ) { Glyph("close", pal.onGround, 14.dp, 2f) }
                }
            }
        }
    }
}

internal fun runQuick(ctx: android.content.Context, a: QuickAnswer) {
    try {
        when (a.kind) {
            "url" -> ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(a.value)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            "phone" -> ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + a.value)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            else -> {
                val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("Koma", a.value))
                // Android 13 and later show their own "copied" confirmation, so Koma adds one only on older versions.
                if (android.os.Build.VERSION.SDK_INT < 33) android.widget.Toast.makeText(ctx, "Copied ${a.value}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
    }
}

/** An instant answer: a sum, a conversion, a web address or a phone number. */
@Composable
internal fun QuickRow(state: HomeState, a: QuickAnswer) {
    val pal = state.palette
    val ctx = LocalContext.current
    val glyph = when (a.kind) { "url" -> "web"; "phone" -> "phone"; else -> "calc" }
    Row(
        Modifier.fillMaxWidth().clickable { runQuick(ctx, a) }.padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(glyph, 40.dp, pal.tiles[2 % pal.tiles.size], pal.ink, pal.onGround)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            BasicText(a.sub, style = monoStyle(pal.onGround.copy(alpha = 0.6f), 9), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(a.title, style = bodyStyle(pal.onGround, 22, medium = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Search the web for [q]. */
@Composable
internal fun WebRow(state: HomeState, q: String, noApp: Boolean = false) {
    val pal = state.palette
    val ctx = LocalContext.current
    val engineName = Engines.all.firstOrNull { it.first == state.prefs.engine }?.second?.first ?: "the web"
    Row(
        Modifier.fillMaxWidth().clickable { Engines.search(ctx, state.prefs, q); state.goHome() }.padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon("search", 40.dp, pal.tiles[1 % pal.tiles.size], pal.ink, pal.onGround)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            BasicText(if (noApp) "NO APP FOUND / SEARCH ${engineName.uppercase()}" else "SEARCH ${engineName.uppercase()}", style = monoStyle(pal.onGround.copy(alpha = 0.6f), 9), maxLines = 1)
            BasicText("“$q”", style = bodyStyle(pal.onGround, 18, medium = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun rememberContacts(state: HomeState, q: String): List<ContactHit> {
    val ctx = LocalContext.current
    val granted = state.contactsGranted
    val hits by produceState(emptyList<ContactHit>(), q, granted) {
        value = if (!granted || q.isBlank()) emptyList() else withContext(Dispatchers.IO) { ContactSearch.find(ctx, q.trim(), 3) }
    }
    return hits
}

/** People that match the search. Tapping one opens their contact card. */
@Composable
internal fun ContactRows(state: HomeState, q: String, hits: List<ContactHit>, onOpen: () -> Unit) {
    val pal = state.palette
    val ctx = LocalContext.current
    if (q.isNotBlank() && !state.contactsGranted) {
        Row(
            Modifier.fillMaxWidth().clickable { state.request(android.Manifest.permission.READ_CONTACTS) }.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon("users", 40.dp, pal.tiles[3 % pal.tiles.size], pal.ink, pal.onGround)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                BasicText("CONTACTS", style = monoStyle(pal.onGround.copy(alpha = 0.6f), 9))
                BasicText("Allow access to search people", style = bodyStyle(pal.onGround, 16, medium = true), maxLines = 1)
            }
        }
        return
    }
    hits.forEachIndexed { i, h ->
        Row(
            Modifier.fillMaxWidth().clickable {
                try {
                    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, h.uri).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                }
                onOpen()
            }.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val initials = h.name.split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }
            Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(pal.tiles[(i + 2) % pal.tiles.size]), contentAlignment = Alignment.Center) {
                BasicText(initials, style = monoStyle(pal.ink, 13, bold = true))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                BasicText("CONTACT", style = monoStyle(pal.onGround.copy(alpha = 0.6f), 9))
                BasicText(h.name, style = bodyStyle(pal.onGround, 18, medium = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Autocomplete suggestions from the web for [q]. Silent when offline. */
@Composable
internal fun rememberWebSuggestions(q: String): List<String> {
    val hits by produceState(emptyList<String>(), q) {
        value = emptyList()
        if (q.length < 2) return@produceState
        kotlinx.coroutines.delay(250)
        value = withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://suggestqueries.google.com/complete/search?client=firefox&q=" + java.net.URLEncoder.encode(q, "UTF-8"))
                val c = url.openConnection() as java.net.HttpURLConnection
                c.connectTimeout = 3000
                c.readTimeout = 3000
                try {
                    val arr = org.json.JSONArray(c.inputStream.bufferedReader().use { it.readText() }).getJSONArray(1)
                    (0 until arr.length()).map { arr.getString(it) }.filter { !it.equals(q, ignoreCase = true) }.take(6)
                } finally {
                    c.disconnect()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    return hits
}

/** One web suggestion as a pill. Tapping it searches the web for it. */
@Composable
internal fun SuggestionChip(state: HomeState, text: String) {
    val pal = state.palette
    val ctx = LocalContext.current
    Row(
        Modifier.clip(CircleShape).background(pal.ground.copy(alpha = 0.96f))
            .border(1.dp, pal.onGround.copy(alpha = 0.14f), CircleShape)
            .clickable { Engines.search(ctx, state.prefs, text); state.goHome() }
            .padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Magnifier(pal.onGround.copy(alpha = 0.55f), 14.dp)
        Spacer(Modifier.width(8.dp))
        BasicText(text, style = bodyStyle(pal.onGround, 15, medium = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
