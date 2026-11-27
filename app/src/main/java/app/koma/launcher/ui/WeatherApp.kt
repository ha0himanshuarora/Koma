package app.koma.launcher.ui

import android.Manifest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.koma.launcher.data.Forecast
import app.koma.launcher.data.TimeFmt
import app.koma.launcher.data.WeatherRepo
import kotlinx.coroutines.delay
import java.time.format.TextStyle
import java.util.Locale

/** A place the Weather app can show. [key] is "" for this phone's location, otherwise "lat|lon|Name". [role] is tile, tile2 or saved. */
private class WPlace(val key: String, val name: String, val role: String)

private fun nameOf(key: String) = key.split("|").drop(2).joinToString("|")

/** The two places on the home screen tile first, then the cities added here. */
private fun wPlaces(state: HomeState): List<WPlace> {
    val p = state.prefs
    val out = ArrayList<WPlace>()
    out.add(WPlace(p.weatherPlace, if (p.weatherPlace.isEmpty()) "My location" else nameOf(p.weatherPlace), "tile"))
    if (p.weatherPlace2.isNotEmpty()) out.add(WPlace(p.weatherPlace2, nameOf(p.weatherPlace2), "tile2"))
    p.weatherSaved.split("\n").filter { it.isNotBlank() && it != p.weatherPlace && it != p.weatherPlace2 }
        .forEach { out.add(WPlace(it, nameOf(it), "saved")) }
    return out
}

private fun hourLabel(h: Int): String =
    if (TimeFmt.is24()) "%02d".format(h) else "${if (h % 12 == 0) 12 else h % 12}${if (h < 12) "a" else "p"}"

private fun minutesOf(hhmm: String): Int {
    val parts = hhmm.split(":")
    return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}

private fun clock(hhmm: String): String {
    val m = minutesOf(hhmm)
    val h = m / 60
    val mm = "%02d".format(m % 60)
    return if (TimeFmt.is24()) "%02d:%s".format(h, mm) else "${if (h % 12 == 0) 12 else h % 12}:$mm ${if (h < 12) "AM" else "PM"}"
}

private fun compass(deg: Int): String = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((deg % 360) + 360) % 360 + 22) / 45 % 8]

private fun uvWord(uv: Double) = when {
    uv < 3 -> "Low"
    uv < 6 -> "Moderate"
    uv < 8 -> "High"
    uv < 11 -> "Very high"
    else -> "Extreme"
}

private fun aqiWord(a: Int) = when {
    a <= 50 -> "Good"
    a <= 100 -> "Moderate"
    a <= 150 -> "Unhealthy for sensitive groups"
    a <= 200 -> "Unhealthy"
    a <= 300 -> "Very unhealthy"
    else -> "Hazardous"
}

internal class WStat(val label: String, val value: String, val sub: String)

@Composable
internal fun StatCard(s: WStat, modifier: Modifier) {
    val pal = LocalPalette.current
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(surfaceColor()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BasicText(s.label.uppercase(), style = monoStyle(pal.onGround.copy(alpha = 0.6f), 10), maxLines = 1)
        BasicText(s.value, style = dispStyle(pal.onGround, 26), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (s.sub.isNotEmpty()) BasicText(s.sub, style = bodyStyle(pal.onGround.copy(alpha = 0.65f), 12), maxLines = 2)
    }
}

@Composable
internal fun InfoCard(title: String, content: @Composable () -> Unit) {
    val pal = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(surfaceColor()).padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(title.uppercase(), style = monoStyle(pal.onGround.copy(alpha = 0.6f), 10), modifier = Modifier.padding(horizontal = 14.dp))
        content()
    }
}

/** The Weather app: now, the next 24 hours, 7 days, details, places, and the settings of the home screen weather tile. */
@Composable
fun WeatherApp(state: HomeState) {
    val pal = state.palette
    val prefs = state.prefs
    var sel by remember { mutableIntStateOf(prefs.weatherSel) }
    val places = wPlaces(state)
    val cur = places[sel.coerceIn(0, places.size - 1)]
    var adding by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var doneTick by remember { mutableIntStateOf(0) }
    var fc by remember(cur.key, prefs.weatherUnit) { mutableStateOf(state.cachedForecast(cur.key)) }
    val needsLocation = cur.key.isEmpty() && !state.locationGranted

    LaunchedEffect(cur.key, prefs.weatherUnit, state.locationGranted, refreshTick) {
        if (needsLocation) return@LaunchedEffect
        while (true) {
            awaitVisible()
            val force = refreshTick != doneTick
            doneTick = refreshTick
            loading = true
            state.loadForecast(cur.key, force)?.let { fc = it }
            loading = false
            delay(15 * 60_000L)
        }
    }

    fun saveCity(key: String) {
        val list = prefs.weatherSaved.split("\n").filter { it.isNotBlank() && it != key }
        prefs.weatherSaved = (list + key).joinToString("\n")
    }
    fun forgetCity(key: String) {
        prefs.weatherSaved = prefs.weatherSaved.split("\n").filter { it.isNotBlank() && it != key }.joinToString("\n")
    }
    fun pick(i: Int) {
        sel = i
        prefs.weatherSel = i
    }

    AppPage(
        title = "Weather",
        sub = cur.name,
        onBack = { state.exitApp() },
        actions = {
            CircleBtn("refresh") { refreshTick++ }
            CircleBtn("plus", accent = true) { adding = true }
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val chipOff = surfaceColor(0.1f)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                places.forEachIndexed { i, p ->
                    val on = p === cur
                    Box(
                        Modifier.height(38.dp).clip(RoundedCornerShape(50)).background(if (on) pal.tiles[0] else chipOff)
                            .clickable { pick(i) }.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) { BasicText(p.name.uppercase(), style = monoStyle(if (on) pal.ink else pal.onGround, 11, bold = true), maxLines = 1) }
                }
            }

            val f = fc
            if (needsLocation) {
                InfoCard("LOCATION") {
                    Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        BasicText("Allow location to see the weather where you are, or add a city instead. Only rounded coordinates are sent.", style = bodyStyle(pal.onGround.copy(alpha = 0.8f), 14))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("ALLOW LOCATION", pal.tiles[0], pal.ink) { state.request(Manifest.permission.ACCESS_COARSE_LOCATION) }
                            Pill("ADD A CITY", pal.onGround.copy(alpha = 0.14f), pal.onGround) { adding = true }
                        }
                    }
                }
            } else if (f == null) {
                InfoCard(if (loading) "LOADING" else "NO FORECAST") {
                    Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        BasicText(
                            if (loading) "Getting the forecast..." else "The forecast could not be loaded. Check your internet connection" +
                                if (cur.key.isEmpty()) " and that this phone has a recent location." else ".",
                            style = bodyStyle(pal.onGround.copy(alpha = 0.8f), 14),
                        )
                        if (!loading) Pill("TRY AGAIN", pal.tiles[0], pal.ink) { refreshTick++ }
                    }
                }
            } else {
                Forecasted(state, f)
            }

            Group("TEMPERATURE") {
                ChipRow(listOf("auto" to "AUTO", "c" to "CELSIUS", "f" to "FAHRENHEIT"), prefs.weatherUnit, title = "Unit") { state.setWeatherSource(unit = it) }
            }
            Group("THIS PLACE", hint = "The tile on the home screen shows its first place, and the wide sizes a second one.") {
                when (cur.role) {
                    "saved" -> {
                        ListRow("Show on tile", "", "sun", pal.tiles[0], onClick = {
                            val old = prefs.weatherPlace
                            if (old.isNotEmpty()) saveCity(old)
                            forgetCity(cur.key)
                            state.setWeatherSource(place = cur.key)
                            pick(0)
                        })
                        ListRow("Show beside it on tile", "", "cloud", pal.tiles[1 % pal.tiles.size], onClick = {
                            val old = prefs.weatherPlace2
                            if (old.isNotEmpty()) saveCity(old)
                            forgetCity(cur.key)
                            state.setWeatherSource(place2 = cur.key)
                            pick(wPlaces(state).indexOfFirst { it.key == cur.key }.coerceAtLeast(0))
                        })
                        ListRow("Remove", "", "trash", pal.tiles[2 % pal.tiles.size], onClick = { forgetCity(cur.key); pick(0) })
                    }
                    "tile2" -> ListRow("Stop showing on tile", "", "close", pal.tiles[2 % pal.tiles.size], onClick = {
                        saveCity(cur.key)
                        state.setWeatherSource(place2 = "")
                    })
                    else -> if (cur.key.isNotEmpty()) ListRow("Use my location on tile", "", "device", pal.tiles[0], onClick = {
                        saveCity(cur.key)
                        state.setWeatherSource(place = "")
                        pick(0)
                    }) else BasicText(
                        "This phone's location is shown on the tile.",
                        style = bodyStyle(pal.onGround.copy(alpha = 0.7f), 13), modifier = Modifier.padding(14.dp),
                    )
                }
                ListRow("Add a city", "", "plus", pal.tiles[3 % pal.tiles.size], onClick = { adding = true })
            }
            Group("TILE") {
                SwitchRow("High and low", "Today's range", prefs.weatherHiLo) { prefs.weatherHiLo = it }
                SwitchRow("City name", "Under the temperature", prefs.weatherCity) { prefs.weatherCity = it }
                SwitchRow("Cat", "On the large tile and here", prefs.weatherCat) { prefs.weatherCat = it }
            }
            if (f != null) {
                val t = java.time.Instant.ofEpochMilli(f.fetched).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                BasicText(
                    "UPDATED ${clock("%02d:%02d".format(t.hour, t.minute))}  ·  OPEN-METEO",
                    style = monoStyle(pal.onGround.copy(alpha = 0.45f), 10), modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
                )
            }
        }
    }

    if (adding) AddCitySheet(onDismiss = { adding = false }) { place ->
        val key = "${place.lat}|${place.lon}|${place.label.substringBefore(',')}"
        val existing = wPlaces(state).indexOfFirst { it.key == key }
        if (existing < 0) {
            saveCity(key)
            pick(wPlaces(state).indexOfFirst { it.key == key }.coerceAtLeast(0))
        } else pick(existing)
        adding = false
    }
}

@Composable
private fun Forecasted(state: HomeState, f: Forecast) {
    val pal = state.palette
    val today = f.days.firstOrNull()
    val (cond, glyph) = WeatherRepo.describe(f.code)
    val mood = when {
        glyph == "rain" || glyph == "snow" || glyph == "storm" || glyph == "fog" -> glyph
        !f.day -> "night"
        glyph == "sun" -> "sun"
        else -> "cloud"
    }
    val heroCol = pal.tiles[(if (!f.day) 3 else when (glyph) { "sun" -> 0; "rain", "storm" -> 2; "snow" -> 4; else -> 1 }) % pal.tiles.size]
    val ink = pal.ink
    val rainAt = f.hours.firstOrNull { it.rain >= 50 }
    val outlook = when {
        rainAt != null && rainAt === f.hours.first() -> "Rain is likely now."
        rainAt != null -> "Rain is likely around ${hourLabel(rainAt.hour)}."
        else -> "No rain expected in the next 24 hours."
    }

    // Now: the temperature with a big glyph and the cat beside it, a row of small facts, then a one-line outlook.
    Box(Modifier.fillMaxWidth().clip(TileShape).background(heroCol)) {
        val chip = ink.copy(alpha = 0.14f)
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    BasicText(cond.uppercase(), style = monoStyle(ink, 12, bold = true), maxLines = 1)
                    BasicText("${f.temp}°", style = dispStyle(ink, 92, -0.05f), maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                }
                if (state.prefs.weatherCat) Cat(mood = mood, modifier = Modifier.padding(top = 30.dp).size(width = 112.dp, height = 76.dp))
                else Glyph(glyph, ink, 56.dp, 1.8f)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOfNotNull(
                    "FEELS ${f.feels}°",
                    today?.let { "H ${it.hi}°" },
                    today?.let { "L ${it.lo}°" },
                ).forEach { t ->
                    Box(Modifier.clip(RoundedCornerShape(50)).background(chip).padding(horizontal = 12.dp, vertical = 7.dp)) {
                        BasicText(t, style = monoStyle(ink, 11, bold = true), maxLines = 1)
                    }
                }
            }
            BasicText(outlook, style = bodyStyle(ink.copy(alpha = 0.85f), 14, medium = true))
        }
    }

    // Next 24 hours: the temperature as a filled curve, with the hours under it.
    InfoCard("NEXT 24 HOURS") {
        val temps = f.hours.map { it.temp }
        val lo = (temps.minOrNull() ?: 0)
        val hi = (temps.maxOrNull() ?: 0).coerceAtLeast(lo + 1)
        val line = pal.tiles[0]
        val anyRain = f.hours.any { it.rain >= 10 }
        Column(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp)) {
            Canvas(Modifier.width(56.dp * f.hours.size).height(70.dp)) {
                val step = size.width / f.hours.size
                val top = 14.dp.toPx()
                val bottom = size.height - 6.dp.toPx()
                fun yOf(t: Int) = top + (bottom - top) * (1f - (t - lo).toFloat() / (hi - lo))
                val path = Path()
                f.hours.forEachIndexed { i, h ->
                    val x = step * i + step / 2f
                    if (i == 0) path.moveTo(x, yOf(h.temp)) else path.lineTo(x, yOf(h.temp))
                }
                val fillPath = Path().apply {
                    addPath(path)
                    lineTo(step * (f.hours.size - 1) + step / 2f, size.height)
                    lineTo(step / 2f, size.height)
                    close()
                }
                drawPath(fillPath, line.copy(alpha = 0.16f))
                drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                f.hours.forEachIndexed { i, h ->
                    drawCircle(line, if (i == 0) 5.dp.toPx() else 3.dp.toPx(), Offset(step * i + step / 2f, yOf(h.temp)))
                }
            }
            Row {
                f.hours.forEachIndexed { i, h ->
                    Column(
                        Modifier.width(56.dp).padding(top = 6.dp, bottom = 4.dp)
                            .clip(RoundedCornerShape(14.dp)).background(if (i == 0) pal.onGround.copy(alpha = 0.08f) else Color.Transparent)
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BasicText("${h.temp}°", style = bodyStyle(pal.onGround, 15, medium = true), maxLines = 1)
                        val g = WeatherRepo.describe(h.code).second
                        Glyph(if (!h.day && g == "sun") "moon" else g, pal.onGround, 22.dp, 1.8f)
                        if (anyRain) BasicText(if (h.rain >= 10) "${h.rain}%" else " ", style = monoStyle(pal.tiles[0], 10, bold = true), maxLines = 1)
                        BasicText(if (i == 0) "NOW" else hourLabel(h.hour), style = monoStyle(pal.onGround.copy(alpha = 0.6f), 10), maxLines = 1)
                    }
                }
            }
        }
    }

    // Next 7 days, each with a bar from its low to its high on the week's own scale.
    InfoCard("7 DAYS") {
        val wLo = f.days.minOfOrNull { it.lo } ?: 0
        val wHi = (f.days.maxOfOrNull { it.hi } ?: 1).coerceAtLeast(wLo + 1)
        val span = (wHi - wLo).toFloat()
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            f.days.forEachIndexed { i, d ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(
                        if (i == 0) "Today" else d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = bodyStyle(pal.onGround, 15, medium = true), modifier = Modifier.width(52.dp), maxLines = 1,
                    )
                    Glyph(WeatherRepo.describe(d.code).second, pal.onGround, 22.dp, 1.8f)
                    BasicText(if (d.rain >= 10) "${d.rain}%" else "", style = monoStyle(pal.tiles[0], 10, bold = true), modifier = Modifier.width(34.dp), maxLines = 1)
                    BasicText("${d.lo}°", style = bodyStyle(pal.onGround.copy(alpha = 0.6f), 14), modifier = Modifier.width(32.dp), maxLines = 1)
                    Row(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(pal.onGround.copy(alpha = 0.14f))) {
                        Spacer(Modifier.weight(((d.lo - wLo) / span).coerceAtLeast(0.001f)))
                        Box(Modifier.weight(((d.hi - d.lo) / span).coerceAtLeast(0.02f)).fillMaxHeight().clip(CircleShape).background(pal.tiles[0]))
                        Spacer(Modifier.weight(((wHi - d.hi) / span).coerceAtLeast(0.001f)))
                    }
                    BasicText("${d.hi}°", style = bodyStyle(pal.onGround, 14, medium = true), modifier = Modifier.width(32.dp), maxLines = 1)
                }
            }
        }
    }

    // Details
    val stats = ArrayList<WStat>()
    stats.add(WStat("Feels like", "${f.feels}°", when { f.feels > f.temp + 1 -> "Warmer than the air"; f.feels < f.temp - 1 -> "Cooler than the air"; else -> "About the same as the air" }))
    stats.add(WStat("Humidity", "${f.humidity}%", if (f.humidity < 30) "Dry" else if (f.humidity <= 60) "Comfortable" else "Humid"))
    stats.add(WStat("Wind", "${f.wind} ${f.windUnit}", "From ${compass(f.windDir)}" + (today?.let { ", up to ${it.wind} today" } ?: "")))
    stats.add(WStat("Pressure", "${f.pressure} hPa", if (f.pressure < 1000) "Low" else if (f.pressure > 1020) "High" else "Normal"))
    if (today != null) stats.add(WStat("UV index", "${Math.round(today.uv)}", uvWord(today.uv) + " today"))
    stats.add(WStat("Rain", "${"%.1f".format(f.precip)} ${f.rainUnit}", today?.let { "Today ${"%.1f".format(it.mm)} ${f.rainUnit}, ${it.rain}% chance" } ?: ""))
    stats.add(WStat("Cloud cover", "${f.cloud}%", if (f.cloud < 20) "Clear" else if (f.cloud < 50) "Partly cloudy" else if (f.cloud < 85) "Mostly cloudy" else "Overcast"))
    if (f.aqi >= 0) stats.add(WStat("Air quality", "${f.aqi}", aqiWord(f.aqi)))
    stats.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { StatCard(it, Modifier.weight(1f).fillMaxHeight()) }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }

    // Sun: an arc from sunrise to sunset with the sun where the day has got to.
    if (today != null && today.sunrise.isNotEmpty() && today.sunset.isNotEmpty()) {
        val rise = minutesOf(today.sunrise)
        val set = minutesOf(today.sunset)
        val nowMin = (f.hours.firstOrNull()?.hour ?: 12) * 60 + 30
        val progress = ((nowMin - rise).toFloat() / (set - rise).coerceAtLeast(1)).coerceIn(0f, 1f)
        val daylight = set - rise
        InfoCard("SUN") {
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val arc = pal.onGround.copy(alpha = 0.25f)
                val dot = pal.tiles[0]
                Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                    val r = size.width / 2f - 12.dp.toPx()
                    val cx = size.width / 2f
                    val base = size.height - 6.dp.toPx()
                    val path = Path()
                    for (i in 0..40) {
                        val a = Math.PI * (1.0 - i / 40.0)
                        val x = cx + (r * Math.cos(a)).toFloat()
                        val y = base - (r * 0.55f * Math.sin(a)).toFloat()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, arc, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                    val a = Math.PI * (1.0 - progress)
                    drawCircle(dot, 7.dp.toPx(), Offset(cx + (r * Math.cos(a)).toFloat(), base - (r * 0.55f * Math.sin(a)).toFloat()))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        BasicText("SUNRISE", style = monoStyle(pal.onGround.copy(alpha = 0.6f), 10))
                        BasicText(clock(today.sunrise), style = bodyStyle(pal.onGround, 16, medium = true))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText("DAYLIGHT", style = monoStyle(pal.onGround.copy(alpha = 0.6f), 10))
                        BasicText("${daylight / 60}h ${daylight % 60}m", style = bodyStyle(pal.onGround, 16, medium = true))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        BasicText("SUNSET", style = monoStyle(pal.onGround.copy(alpha = 0.6f), 10))
                        BasicText(clock(today.sunset), style = bodyStyle(pal.onGround, 16, medium = true))
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCitySheet(onDismiss: () -> Unit, onPick: (WeatherRepo.Place) -> Unit) {
    val pal = LocalPalette.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherRepo.Place>>(emptyList()) }
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) { results = emptyList(); note = null; return@LaunchedEffect }
        if (q.length < 2) { note = "Type at least 2 letters."; return@LaunchedEffect }
        delay(450)
        note = "Searching..."
        val found = WeatherRepo.search(q)
        results = found ?: emptyList()
        note = when {
            found == null -> "Can't reach the city search. Check your internet connection."
            found.isEmpty() -> "No city found for \"$q\"."
            else -> null
        }
    }
    Sheet(
        onDismiss = onDismiss,
        header = {
            BasicText("ADD A CITY", style = monoStyle(pal.onGround.copy(alpha = 0.6f), 10))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(pal.onGround.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph("search", pal.onGround.copy(alpha = 0.6f), 18.dp, 2f)
                Spacer(Modifier.width(10.dp))
                Field(query, { query = it }, "Search a city, e.g. Paris", Modifier.weight(1f))
            }
        },
    ) {
        note?.let { BasicText(it, style = bodyStyle(pal.onGround.copy(alpha = 0.7f), 13)) }
        results.forEach { r -> ListRow(r.label, "", "sun", pal.tiles[0], onClick = { onPick(r) }) }
    }
}
