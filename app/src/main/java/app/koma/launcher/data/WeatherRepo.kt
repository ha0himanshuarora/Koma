package app.koma.launcher.data

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class Weather(
    val temp: Int,
    val hi: Int,
    val lo: Int,
    val code: Int,
    val city: String,
    val fetched: Long,
    val day: Boolean = true,
)

/**
 * Current weather from Open-Meteo (free, no account). Uses the last known coarse location,
 * and keeps the last result so the tile can draw instantly on the next start.
 */
object WeatherRepo {
    private const val MAX_AGE_MS = 30 * 60 * 1000L

    fun granted(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun cached(prefs: SharedPreferences, key: String = "weather"): Weather? {
        val raw = prefs.getString(key, null) ?: return null
        return try {
            val o = JSONObject(raw)
            Weather(o.getInt("temp"), o.getInt("hi"), o.getInt("lo"), o.getInt("code"), o.getString("city"), o.getLong("fetched"), o.optInt("day", 1) == 1)
        } catch (e: Exception) {
            null
        }
    }

    /** Fetches fresh data if the cached copy is older than 30 minutes. Returns the newest data available. */
    suspend fun refresh(
        ctx: Context, prefs: SharedPreferences, unit: String = "auto", place: String = "", key: String = "weather",
    ): Weather? = withContext(Dispatchers.IO) {
        val old = cached(prefs, key)
        if (old != null && System.currentTimeMillis() - old.fetched < MAX_AGE_MS) return@withContext old
        val chosen = place.split("|")
        val loc = if (chosen.size >= 3) {
            (chosen[0].toDoubleOrNull() ?: return@withContext old) to (chosen[1].toDoubleOrNull() ?: return@withContext old)
        } else if (key != "weather") return@withContext null // the second place is always a chosen city
        else lastLocation(ctx, prefs) ?: return@withContext old
        try {
            val fahrenheit = when (unit) {
                "f" -> true
                "c" -> false
                else -> Locale.getDefault().country in setOf("US", "BS", "BZ", "KY", "PW", "LR")
            }
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${loc.first}&longitude=${loc.second}" +
                "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min" +
                "&timezone=auto&forecast_days=1" + if (fahrenheit) "&temperature_unit=fahrenheit" else ""
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val json = JSONObject(body)
            val current = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            val zone = json.optString("timezone", "")
            val w = Weather(
                temp = Math.round(current.getDouble("temperature_2m")).toInt(),
                hi = Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
                lo = Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
                code = current.getInt("weather_code"),
                city = (if (chosen.size >= 3) chosen.drop(2).joinToString("|") else zone.substringAfterLast('/').replace('_', ' ')).uppercase(),
                fetched = System.currentTimeMillis(),
                day = current.optInt("is_day", 1) == 1,
            )
            val saved = JSONObject()
                .put("temp", w.temp).put("hi", w.hi).put("lo", w.lo)
                .put("code", w.code).put("city", w.city).put("fetched", w.fetched).put("day", if (w.day) 1 else 0)
            prefs.edit().putString(key, saved.toString()).apply()
            w
        } catch (e: Exception) {
            old
        }
    }

    /** Forget the saved reading, so the next refresh fetches with the new settings. */
    fun clear(prefs: SharedPreferences) {
        prefs.edit().remove("weather").remove("weather2").apply()
    }

    /** A place found by name: label, latitude and longitude. */
    class Place(val label: String, val lat: Double, val lon: Double)

    /** Looks a city up by name with Open-Meteo's free geocoder. */
    suspend fun search(query: String): List<Place>? = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val url = "https://geocoding-api.open-meteo.com/v1/search?count=6&language=en&format=json&name=" +
                java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val arr = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val where = listOf(o.optString("admin1"), o.optString("country")).filter { it.isNotBlank() }.joinToString(", ")
                Place(o.getString("name") + if (where.isNotEmpty()) ", $where" else "", o.getDouble("latitude"), o.getDouble("longitude"))
            }
        } catch (e: Exception) {
            null // no connection, or the service did not answer
        }
    }

    internal fun lastLocation(ctx: Context, prefs: SharedPreferences): Pair<Double, Double>? {
        if (granted(ctx)) {
            try {
                val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val best: Location? = lm.getProviders(true)
                    .mapNotNull { lm.getLastKnownLocation(it) }
                    .maxByOrNull { it.time }
                if (best != null) {
                    prefs.edit().putString("lat", best.latitude.toString()).putString("lon", best.longitude.toString()).apply()
                    return best.latitude to best.longitude
                }
            } catch (e: SecurityException) {
                // fall through to the saved position
            }
        }
        val lat = prefs.getString("lat", null)?.toDoubleOrNull()
        val lon = prefs.getString("lon", null)?.toDoubleOrNull()
        return if (lat != null && lon != null) lat to lon else null
    }

    /** Short condition name and the glyph to draw for an Open-Meteo weather code. */
    fun describe(code: Int): Pair<String, String> = when (code) {
        0 -> "Clear" to "sun"
        1 -> "Mostly clear" to "sun"
        2 -> "Partly cloudy" to "cloud"
        3 -> "Overcast" to "cloud"
        45, 48 -> "Fog" to "fog"
        in 51..57 -> "Drizzle" to "rain"
        in 61..67 -> "Rain" to "rain"
        in 71..77 -> "Snow" to "snow"
        in 80..82 -> "Showers" to "rain"
        85, 86 -> "Snow showers" to "snow"
        in 95..99 -> "Storm" to "storm"
        else -> "Cloudy" to "cloud"
    }
}
