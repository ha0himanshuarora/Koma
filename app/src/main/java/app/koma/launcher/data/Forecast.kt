package app.koma.launcher.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Locale

/** One hour of the forecast. [hour] is the hour of day at the place, 0 to 23. */
class ForecastHour(val hour: Int, val temp: Int, val code: Int, val rain: Int, val day: Boolean)

/** One day of the forecast. Sunrise and sunset are "HH:mm" at the place. */
class ForecastDay(
    val date: LocalDate, val code: Int, val hi: Int, val lo: Int, val rain: Int, val mm: Double,
    val uv: Double, val wind: Int, val sunrise: String, val sunset: String,
)

/** What the Weather app shows for one place: now, the next 24 hours and the next 7 days. */
class Forecast(
    val city: String, val fetched: Long, val fahrenheit: Boolean,
    val temp: Int, val feels: Int, val code: Int, val day: Boolean,
    val humidity: Int, val wind: Int, val windDir: Int, val pressure: Int, val precip: Double, val cloud: Int,
    val aqi: Int, val hours: List<ForecastHour>, val days: List<ForecastDay>,
) {
    val unit: String get() = if (fahrenheit) "°F" else "°C"
    val windUnit: String get() = if (fahrenheit) "mph" else "km/h"
    val rainUnit: String get() = if (fahrenheit) "in" else "mm"
}

/**
 * The full forecast for the Weather app, from Open-Meteo (free, no account). Kept separate from the tile's small
 * reading so the tile stays light. Each place keeps its own saved copy, so switching places draws at once.
 */
object ForecastRepo {
    private const val MAX_AGE_MS = 20 * 60 * 1000L

    /** The place string used to save a forecast: "" is this phone's location, otherwise "lat|lon|Name". */
    private fun key(place: String) = "fc_" + if (place.isBlank()) "me" else Integer.toHexString(place.hashCode())

    private fun fahrenheit(unit: String) = when (unit) {
        "f" -> true
        "c" -> false
        else -> Locale.getDefault().country in setOf("US", "BS", "BZ", "KY", "PW", "LR")
    }

    fun cached(prefs: SharedPreferences, place: String, unit: String): Forecast? {
        val raw = prefs.getString(key(place), null) ?: return null
        return try {
            val o = JSONObject(raw)
            // A saved copy in the other unit is no use.
            if (o.optBoolean("f") != fahrenheit(unit)) return null
            parse(o.getString("body"), o.getString("city"), o.getBoolean("f"), o.getLong("fetched"), o.optInt("aqi", -1))
        } catch (e: Exception) {
            null
        }
    }

    suspend fun refresh(ctx: Context, prefs: SharedPreferences, place: String, unit: String, force: Boolean = false): Forecast? =
        withContext(Dispatchers.IO) {
            val old = cached(prefs, place, unit)
            if (!force && old != null && System.currentTimeMillis() - old.fetched < MAX_AGE_MS) return@withContext old
            val chosen = place.split("|")
            val loc = if (chosen.size >= 3) {
                (chosen[0].toDoubleOrNull() ?: return@withContext old) to (chosen[1].toDoubleOrNull() ?: return@withContext old)
            } else WeatherRepo.lastLocation(ctx, prefs) ?: return@withContext old
            try {
                val f = fahrenheit(unit)
                val url = "https://api.open-meteo.com/v1/forecast?latitude=${loc.first}&longitude=${loc.second}" +
                    "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m," +
                    "wind_direction_10m,pressure_msl,surface_pressure,precipitation,cloud_cover" +
                    "&hourly=temperature_2m,weather_code,precipitation_probability,is_day" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max," +
                    "sunrise,sunset,uv_index_max,wind_speed_10m_max" +
                    "&timezone=auto&forecast_days=7" +
                    if (f) "&temperature_unit=fahrenheit&wind_speed_unit=mph&precipitation_unit=inch" else ""
                val body = get(url)
                val zone = JSONObject(body).optString("timezone", "")
                val city = (if (chosen.size >= 3) chosen.drop(2).joinToString("|") else zone.substringAfterLast('/').replace('_', ' ')).uppercase()
                val aqi = try {
                    val a = JSONObject(get("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${loc.first}&longitude=${loc.second}&current=us_aqi&timezone=auto"))
                    a.getJSONObject("current").optInt("us_aqi", -1)
                } catch (e: Exception) {
                    -1
                }
                val now = System.currentTimeMillis()
                val fc = parse(body, city, f, now, aqi) ?: return@withContext old
                prefs.edit().putString(
                    key(place),
                    JSONObject().put("body", body).put("city", city).put("f", f).put("fetched", now).put("aqi", aqi).toString(),
                ).apply()
                fc
            } catch (e: Exception) {
                old
            }
        }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String, city: String, f: Boolean, fetched: Long, aqi: Int): Forecast? {
        return try {
            val j = JSONObject(body)
            val c = j.getJSONObject("current")
            val h = j.getJSONObject("hourly")
            val d = j.getJSONObject("daily")
            val times = h.getJSONArray("time")
            val nowHour = c.getString("time").take(13)
            var from = 0
            for (i in 0 until times.length()) if (times.getString(i).take(13) >= nowHour) { from = i; break }
            val hours = (from until minOf(times.length(), from + 24)).map { i ->
                ForecastHour(
                    hour = times.getString(i).substring(11, 13).toInt(),
                    temp = Math.round(h.getJSONArray("temperature_2m").optDouble(i, 0.0)).toInt(),
                    code = h.getJSONArray("weather_code").optInt(i),
                    rain = h.getJSONArray("precipitation_probability").optInt(i),
                    day = h.getJSONArray("is_day").optInt(i, 1) == 1,
                )
            }
            val dt = d.getJSONArray("time")
            val days = (0 until dt.length()).map { i ->
                ForecastDay(
                    date = LocalDate.parse(dt.getString(i)),
                    code = d.getJSONArray("weather_code").optInt(i),
                    hi = Math.round(d.getJSONArray("temperature_2m_max").optDouble(i, 0.0)).toInt(),
                    lo = Math.round(d.getJSONArray("temperature_2m_min").optDouble(i, 0.0)).toInt(),
                    rain = d.getJSONArray("precipitation_probability_max").optInt(i),
                    mm = d.getJSONArray("precipitation_sum").optDouble(i, 0.0),
                    uv = d.getJSONArray("uv_index_max").optDouble(i, 0.0),
                    wind = Math.round(d.getJSONArray("wind_speed_10m_max").optDouble(i, 0.0)).toInt(),
                    sunrise = d.getJSONArray("sunrise").optString(i).drop(11),
                    sunset = d.getJSONArray("sunset").optString(i).drop(11),
                )
            }
            Forecast(
                city = city, fetched = fetched, fahrenheit = f,
                temp = Math.round(c.getDouble("temperature_2m")).toInt(),
                feels = Math.round(c.optDouble("apparent_temperature", c.getDouble("temperature_2m"))).toInt(),
                code = c.getInt("weather_code"), day = c.optInt("is_day", 1) == 1,
                humidity = c.optInt("relative_humidity_2m"), wind = Math.round(c.optDouble("wind_speed_10m")).toInt(),
                windDir = c.optInt("wind_direction_10m"), pressure = Math.round(c.optDouble("pressure_msl", c.optDouble("surface_pressure"))).toInt(),
                precip = c.optDouble("precipitation", 0.0), cloud = c.optInt("cloud_cover"),
                aqi = aqi, hours = hours, days = days,
            )
        } catch (e: Exception) {
            null
        }
    }
}
