package com.karterlauncher.data

import com.karterlauncher.model.WeatherSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** [Open-Meteo](https://open-meteo.com/) — no API key; requires network. */
class WeatherRepository {
    suspend fun fetchCurrent(
        latitude: Double,
        longitude: Double,
        locationLabel: String,
    ): Result<WeatherSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val u =
                URL(
                    "https://api.open-meteo.com/v1/forecast?" +
                        "latitude=$latitude&longitude=$longitude&current_weather=true&timezone=auto",
                )
            val conn = (u.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                requestMethod = "GET"
            }
            try {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(text)
                val cw = root.getJSONObject("current_weather")
                WeatherSummary(
                    tempC = cw.getDouble("temperature").toInt(),
                    wmoCode = cw.getInt("weathercode"),
                    locationLabel = locationLabel,
                )
            } finally {
                conn.disconnect()
            }
        }
    }
}
