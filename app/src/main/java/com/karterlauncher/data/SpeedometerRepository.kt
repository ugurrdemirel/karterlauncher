package com.karterlauncher.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.karterlauncher.model.SpeedGaugeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SpeedometerRepository(
    private val application: Application,
) {
    private val client = LocationServices.getFusedLocationProviderClient(application)
    private val _state = MutableStateFlow<SpeedGaugeState>(SpeedGaugeState.NoPermission)
    val state: StateFlow<SpeedGaugeState> = _state.asStateFlow()

    private val _lastLocation = MutableStateFlow<Location?>(null)
    /**
     * Hız göstergesi için fused location update'leri geldikçe güncellenir.
     * Hız sınırı servisi ve diğer konum tabanlı özellikler bu akışı tüketir.
     */
    val lastLocation: StateFlow<Location?> = _lastLocation.asStateFlow()

    private var callback: LocationCallback? = null

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            application,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            application,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (callback != null) return
        if (!hasLocationPermission()) {
            _state.value = SpeedGaugeState.NoPermission
            _lastLocation.value = null
            return
        }
        _state.value = SpeedGaugeState.WaitingForFix
        _lastLocation.value = null

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATES_INTERVAL_MS,
        )
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_DELTA_METERS)
            .build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                handleLocation(loc)
            }
        }
        callback = cb

        try {
            client.requestLocationUpdates(request, cb, Looper.getMainLooper())
        } catch (_: SecurityException) {
            _state.value = SpeedGaugeState.NoPermission
        } catch (_: Exception) {
            _state.value = SpeedGaugeState.WaitingForFix
        }
    }

    fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        callback?.let {
            client.removeLocationUpdates(it)
            callback = null
        }
        _lastLocation.value = null
    }

    private fun handleLocation(location: Location) {
        val previous = _lastLocation.value
        val speedMps = resolveSpeedMps(location, previous)
        _lastLocation.value = location

        val rawKmh = (speedMps * 3.6f).coerceAtLeast(0f)
        _state.value = SpeedGaugeState.Speed(rawKmh)
    }

    private fun resolveSpeedMps(current: Location, previous: Location?): Float {
        if (current.hasSpeed()) {
            val s = kotlin.math.abs(current.speed)
            if (s >= 0f && !s.isNaN()) return s
        }
        if (previous != null &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1
        ) {
            val dtNanos = kotlin.math.abs(
                locationElapsedNanos(current) - locationElapsedNanos(previous),
            ).toDouble()
            if (dtNanos < MIN_DT_NANOS_FOR_DERIVED_SPEED || dtNanos > 20_000_000_000.0) return 0f
            val secs = dtNanos / 1_000_000_000.0
            if (secs <= 1e-3) return 0f
            val dist = previous.distanceTo(current).toDouble()
            return (dist / secs).coerceAtMost(MAX_IMPLAUSIBLE_MPS.toDouble()).toFloat()
        }
        return 0f
    }

    private fun locationElapsedNanos(loc: Location): Long {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            loc.elapsedRealtimeNanos
        } else {
            @Suppress("DEPRECATION")
            loc.time * 1_000_000L
        }
    }

    companion object {
        /** Hedef güncelleme aralığı — düşük tutulursa gösterge daha canlı (pil/GPS yükü artar). */
        private const val UPDATES_INTERVAL_MS = 250L

        /** Bu süreden sık bildirim isteği (fused uyumlu alt sınır). */
        private const val MIN_UPDATE_INTERVAL_MS = 200L

        private const val MIN_DELTA_METERS = 0f
        private const val MAX_IMPLAUSIBLE_MPS = 90f // ~324 km/h

        /** Konumdan türetilen hız için minimum Δt (~120ms); çok kısa aralıkta gürültü atılır. */
        private const val MIN_DT_NANOS_FOR_DERIVED_SPEED = 120_000_000L
    }
}
