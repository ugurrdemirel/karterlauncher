package com.karterlauncher.model

sealed interface WeatherUiState {
    data object Loading : WeatherUiState
    data class Ready(
        val summary: WeatherSummary,
        /** Son arka plan yenilemesi başarısız; gösterilen veri eski olabilir. */
        val stale: Boolean = false,
    ) : WeatherUiState
    /** No permission, GPS off, or no fix — no default city fallback. */
    data object NeedLocation : WeatherUiState
    data object Error : WeatherUiState
}
