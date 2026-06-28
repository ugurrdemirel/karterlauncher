package com.karterlauncher.model

/** Yolun hız sınırı bilgisi — OpenStreetMap (Overpass) üzerinden çekilir. */
sealed interface SpeedLimitState {
    /** Servis başlatılmadı veya konum izni yok. */
    data object Idle : SpeedLimitState

    /** İlk sorgu veya yol değişimi sonrası Overpass'e gidiliyor. */
    data object Loading : SpeedLimitState

    /**
     * [kmh] = sürüş yönündeki en olası yolun hız sınırı (km/h).
     * [roadName] = OSM `name` etiketi (yoksa null).
     * [roadKind] = OSM `highway` etiketi (örn. `residential`, `motorway`).
     */
    data class Known(
        val kmh: Int,
        val roadName: String?,
        val roadKind: String?,
    ) : SpeedLimitState

    /**
     * Konumun yakınında yol var ama OSM'de `maxspeed` etiketi yok.
     * UI sadece "Limit bilinmiyor" gösterir; uyarı tetiklenmez.
     */
    data class Unknown(
        val roadName: String?,
        val roadKind: String?,
    ) : SpeedLimitState

    /** Ağ hatası veya parse hatası — UI mevcut cache'i göstermeye devam edebilir. */
    data class Error(val message: String? = null) : SpeedLimitState
}
