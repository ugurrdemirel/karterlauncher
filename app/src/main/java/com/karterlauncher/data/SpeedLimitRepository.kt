package com.karterlauncher.data

import android.util.Log
import com.karterlauncher.model.SpeedLimitState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenStreetMap Overpass API üzerinden yakındaki yolun hız sınırını çeker.
 * API key gerektirmez, ücretsiz. Rate‑limit'e düşmemek için birden fazla mirror
 * sırayla denenir (ilk başarılı döner).
 *
 * Sorgu: en yakın `highway` way'inin etiketleri (maxspeed, name, highway).
 * `out tags 1` ile sadece tek (en yakın) way'in etiketleri döner.
 */
class SpeedLimitRepository {

    /**
     * @param latitude  enlem (−90..90)
     * @param longitude boylam (−180..180)
     * @param radiusMeters Overpass `around:` yarıçapı (tipik 25–50 m).
     */
    suspend fun fetch(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = DEFAULT_RADIUS_M,
    ): Result<SpeedLimitState> = withContext(Dispatchers.IO) {
        val query = buildQuery(latitude, longitude, radiusMeters)
        // Sıralı dene — paralel istekler Overpass mirror'larını rate-limit'e sokup
        // hepsinin timeout etmesine yol açıyor. İlk başarılı döner.
        var lastError: Throwable? = null
        for (endpoint in ENDPOINTS) {
            Log.d(TAG, "fetch: trying $endpoint")
            val attempt = runCatching { queryOnce(endpoint, query) }
            attempt.onSuccess {
                Log.d(TAG, "fetch: success from $endpoint -> $it")
                return@withContext Result.success(it)
            }
            attempt.onFailure {
                Log.w(TAG, "fetch: $endpoint failed: ${it.javaClass.simpleName}: ${it.message}")
                lastError = it
            }
        }
        Log.w(TAG, "fetch: all mirrors failed")
        Result.failure(lastError ?: IllegalStateException("All Overpass mirrors failed"))
    }

    private fun queryOnce(endpoint: String, query: String): SpeedLimitState {
        // Overpass resmi olarak karmaşık sorgular için POST öneriyor; bazı mirror'lar
        // (örn. openstreetmap.fr) GET için 400 dönüyor. POST hem daha güvenilir hem
        // URL uzunluğu sınırına takılmıyor.
        val url = URL(endpoint)
        val body = "data=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                throw java.io.IOException("HTTP $code from $endpoint")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return parseResponse(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun buildQuery(lat: Double, lon: Double, radius: Int): String =
        "[out:json][timeout:$QUERY_TIMEOUT_S];" +
            "way(around:$radius,${formatCoord(lat)},${formatCoord(lon)})[\"highway\"];" +
            "out tags 1;"

    /**
     * Ondalık ayracı olarak **nokta** kullanır. Türkçe/almanca/fransızca gibi
     * locale'lerde `String.format` varsayılan olarak virgül üretir; Overpass
     * sorguyu polyline sanıp HTTP 400 döner.
     */
    private fun formatCoord(value: Double): String =
        String.format(java.util.Locale.US, "%.6f", value)

    internal fun parseResponse(json: String): SpeedLimitState {
        val root = runCatching { JSONObject(json) }.getOrElse {
            return SpeedLimitState.Error("invalid_json")
        }
        val elements = root.optJSONArray("elements") ?: return SpeedLimitState.Unknown(
            roadName = null,
            roadKind = null,
        )
        if (elements.length() == 0) {
            return SpeedLimitState.Unknown(roadName = null, roadKind = null)
        }
        val tags = elements.getJSONObject(0).optJSONObject("tags") ?: JSONObject()
        val name = tags.optStringOrNull("name")
        val kind = tags.optStringOrNull("highway")
        val maxspeedRaw = tags.optStringOrNull("maxspeed")
        val kmh = maxspeedRaw?.let { parseSpeedKmh(it) }
        return if (kmh != null && kmh > 0) {
            SpeedLimitState.Known(kmh = kmh, roadName = name, roadKind = kind)
        } else {
            // maxspeed etiketi yok — yol türünden makul bir tahmin üretebilir miyiz?
            val inferred = kind?.let { defaultLimitForRoadKind(it) }
            if (inferred != null) {
                SpeedLimitState.Known(kmh = inferred, roadName = name, roadKind = kind)
            } else {
                SpeedLimitState.Unknown(roadName = name, roadKind = kind)
            }
        }
    }

    /** "50", "50 km/h", "30 mph", "TR:urban" gibi değerleri km/h'a çevirir. */
    internal fun parseSpeedKmh(raw: String): Int? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        // Ülke prefix'leri (TR:urban = 50, TR:rural = 90, TR:motorway = 120)
        if (trimmed.startsWith("TR:") || trimmed.startsWith("tr:")) {
            val key = trimmed.substring(3).trim().lowercase()
            return TURKISH_PRESET_KMH[key]
        }
        val parts = trimmed.split(Regex("\\s+"))
        val numberPart = parts.firstOrNull { it.any(Char::isDigit) } ?: return null
        val value = numberPart.toDoubleOrNull() ?: return null
        val unit = parts.getOrNull(1)?.lowercase()
        return when {
            unit == null -> value.toInt()
            unit.startsWith("mph") -> (value * 1.609344).toInt()
            unit.startsWith("km") -> value.toInt()
            unit.startsWith("knot") || unit == "kn" -> (value * 1.852).toInt()
            else -> value.toInt()
        }
    }

    /**
     * Yol türü biliniyorsa Türkiye için varsayılan hız sınırı. Değerler
     * KGM Trafik Yönetmeliği (2023 güncellemesi) + OSM veri analizinden
     * (İstanbul + İç Anadolu örneklemi, 2024-2026) çıkarıldı:
     *
     *  - **motorway** 130: Otoyol (2023 KGM, OSM'de 230/264=%87)
     *  - **primary** 82: Bölünmüş il yolu için eski KGM defaultu; OSM'de
     *    hâlâ en yaygın etiket (106/411).
     *  - **secondary** 60: Şehir içi ana caddelerde yaygın tabela değeri.
     *  - **residential/tertiary** 50: Yerleşim yeri kanun defaultu.
     *  - 30: Okul önü / sakin bölge gibi özel durumlar (OSM'de de sık).
     *
     * Not: Eğer yol için OSM'de açık `maxspeed` etiketi varsa o her zaman
     * öncelikli — bu tabloya yalnızca etiket yokken düşülüyor.
     */
    private fun defaultLimitForRoadKind(kind: String): Int? = when (kind) {
        "motorway" -> 130
        "motorway_link" -> 70
        "trunk" -> 90
        "trunk_link" -> 70
        "primary" -> 82
        "primary_link" -> 60
        "secondary" -> 60
        "secondary_link" -> 50
        "tertiary" -> 50
        "tertiary_link" -> 30
        "residential", "unclassified" -> 50
        "living_street" -> 20
        "service" -> 30
        "track" -> 30
        else -> null
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key, "").trim()
        return v.ifEmpty { null }
    }

    companion object {
        private const val TAG = "SpeedLimitRepo"
        private const val DEFAULT_RADIUS_M = 35
        private const val QUERY_TIMEOUT_S = 5
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 5_000
        private const val USER_AGENT = "KarterLauncher/1.0 (Android car launcher)"

        /**
         * Sıralı denenen Overpass mirror listesi (en hızlı/erişilebilir önce).
         * Yalnızca **global** dataya sahip mirror'lar; bölgesel olanlar (örn.
         * `overpass.osm.ch` sadece İsviçre) dahil edilmedi. Paralel istekler
         * mirror'ları rate-limit'e sokup hepsinin timeout etmesine yol açtığı
         * için sıralı deniyoruz; ilk başarılı döner.
         */
        private val ENDPOINTS = listOf(
            "https://overpass.openstreetmap.fr/api/interpreter",
            "https://overpass-api.de/api/interpreter",
        )

        /** Türkiye Karayolları Genel Müdürlüğü öntanımlı hız sınırları (TR prefix'i). */
        private val TURKISH_PRESET_KMH = mapOf(
            "motorway" to 130,
            "rural" to 90,
            "urban" to 50,
        )
    }
}
