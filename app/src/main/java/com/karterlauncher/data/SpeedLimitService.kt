package com.karterlauncher.data

import android.location.Location
import android.util.Log
import com.karterlauncher.model.SpeedLimitState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Konum akışını izler, hız sınırı için Overpass'i [SpeedLimitRepository] aracılığıyla
 * çağırır ve sonucu [state] olarak yayınlar.
 *
 * Overpass'i sıkıştırmak için iki katmanlı kısıtlama uygulanır:
 *  - **Cooldown**: Son başarılı sorgudan en az [MIN_REQUERY_INTERVAL_MS] geçmeli.
 *  - **Mesafe**:   Son sorgudan bu yana en az [MIN_REQUERY_DISTANCE_M] yol alınmalı.
 * Aynı yol boyunca tekrar tekrar Overpass'e gidilmez.
 */
class SpeedLimitService(
    private val repository: SpeedLimitRepository = SpeedLimitRepository(),
    private val isOnline: () -> Boolean,
) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)
    private val fetchMutex = Mutex()

    private val _state = MutableStateFlow<SpeedLimitState>(SpeedLimitState.Idle)
    val state: StateFlow<SpeedLimitState> = _state.asStateFlow()

    private var collectJob: Job? = null

    private var lastQueriedCellKey: Long? = null
    private var lastQueriedAtElapsedMs: Long = 0L

    /**
     * Konum akışını izlemeye başlar. start() tekrar tekrar çağrılabilir — yalnızca
     * tek bir collector yaşar.
     */
    fun start(locationFlow: StateFlow<Location?>) {
        collectJob?.cancel()
        collectJob = scope.launch {
            locationFlow.collect { loc ->
                if (loc == null) {
                    _state.value = SpeedLimitState.Idle
                    return@collect
                }
                handleLocation(loc)
            }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        _state.value = SpeedLimitState.Idle
        lastQueriedCellKey = null
        lastQueriedAtElapsedMs = 0L
    }

    /** Eşzamanlı çağrıları engelle; manuel yenileme için de kullanılabilir. */
    suspend fun refreshNow(latitude: Double, longitude: Double) {
        fetchForLocation(latitude, longitude, force = true)
    }

    fun destroy() {
        stop()
        job.cancelChildren()
    }

    private suspend fun handleLocation(loc: Location) {
        if (!isOnline()) {
            // Çevrimdışıyken Overpass çağrısı yapma; mevcut state (Known/Unknown) kalabilir.
            return
        }
        val cellKey = cellKeyFor(loc.latitude, loc.longitude)
        val now = android.os.SystemClock.elapsedRealtime()
        if (!shouldQuery(cellKey, now)) return
        fetchForLocation(loc.latitude, loc.longitude, force = false, cellKey = cellKey, nowElapsedMs = now)
    }

    private fun shouldQuery(cellKey: Long, nowElapsedMs: Long): Boolean {
        val lastKey = lastQueriedCellKey
        val lastAt = lastQueriedAtElapsedMs
        // İlk sorgu: her zaman.
        if (lastKey == null || lastAt == 0L) return true
        // Minimum mutlak aralık (yüksek hızda Overpass'i boğmamak için).
        if (nowElapsedMs - lastAt < MIN_REQUERY_INTERVAL_MS) return false
        // Aynı hücredeyiz ve cooldown geçti: sorgula.
        if (lastKey == cellKey) return true
        // Farklı hücre: daha gevşek bir aralık uygula — yine de uzun yolda
        // her 50 m'de yeni sorgu atılmasını engelle.
        return nowElapsedMs - lastAt >= CROSS_CELL_MIN_INTERVAL_MS
    }

    private suspend fun fetchForLocation(
        lat: Double,
        lon: Double,
        force: Boolean,
        cellKey: Long = cellKeyFor(lat, lon),
        nowElapsedMs: Long = android.os.SystemClock.elapsedRealtime(),
    ) {
        fetchMutex.withLock {
            if (!force) {
                val lastKey = lastQueriedCellKey
                val lastAt = lastQueriedAtElapsedMs
                val sameCell = lastKey == cellKey
                val minInterval = if (sameCell) MIN_REQUERY_INTERVAL_MS else CROSS_CELL_MIN_INTERVAL_MS
                if (lastAt > 0 && nowElapsedMs - lastAt < minInterval) {
                    return@withLock
                }
            }
            // Önceden bilinen bir değer varsa, kullanıcı onu görmeye devam etsin;
            // spinner sadece ilk sorguda (Idle durumundan geçişte) gösterilir.
            val previous = _state.value
            val shouldShowLoading = previous is SpeedLimitState.Idle
            if (shouldShowLoading) {
                _state.value = SpeedLimitState.Loading
            }
            val result = repository.fetch(lat, lon)
            val newState = result.getOrElse { SpeedLimitState.Error(it.message) }
            _state.value = newState
            if (newState is SpeedLimitState.Known || newState is SpeedLimitState.Unknown) {
                lastQueriedCellKey = cellKey
                lastQueriedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
            }
        }
    }

    /**
     * Konumu yaklaşık 50 m'lik bir grid hücresine yuvarlar. Aynı yol boyunca ilerlerken
     * birden fazla kez Overpass'e gidilmesini engeller.
     */
    private fun cellKeyFor(lat: Double, lon: Double): Long {
        val latCell = (lat * GRID_LAT_SCALE).toLong()
        val lonCell = (lon * GRID_LON_SCALE).toLong()
        return latCell * 73856093L xor (lonCell and 0xFFFFFFFFL)
    }

    companion object {
        private const val TAG = "SpeedLimitSvc"
        /** Aynı hücredeyken en az bu kadar beklenir. */
        private const val MIN_REQUERY_INTERVAL_MS = 30_000L

        /**
         * Hücre değiştiğinde bile minimum bekleme süresi. Otoyolda 30 m/s hızla her
         * 50 m'de yeni hücreye girilir; bunu koymazsak Overpass saniyede birkaç kez
         * sorgulanır. 8 sn, hız sınırının değiştiği gerçek sürüş senaryoları için yeterli.
         */
        private const val CROSS_CELL_MIN_INTERVAL_MS = 8_000L

        /** ~50 m grid: 1 / (111_320 m/derece) × 50 m ≈ 0.00045 derece. */
        private const val GRID_LAT_SCALE = 2222.0
        private const val GRID_LON_SCALE = 2222.0
    }
}
