package com.karterlauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.karterlauncher.model.DockLocation
import com.karterlauncher.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class StartupSnapshot(
    val themeMode: ThemeMode,
    val onboardingComplete: Boolean,
    val dockLocation: DockLocation,
    val passengerDualDockEnabled: Boolean,
)

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences",
)

class UserPreferencesRepository(
    context: Context,
) {
    private val dataStore = context.applicationContext.userPreferencesDataStore

    val onboardingCompleteFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_COMPLETE] == true
    }

    val themeModeFlow: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.fromStorage(prefs[KEY_THEME_MODE])
    }

    suspend fun setOnboardingComplete() {
        dataStore.edit { it[KEY_ONBOARDING_COMPLETE] = true }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.storageKey }
    }

    suspend fun readStartupSnapshot(): StartupSnapshot {
        val prefs = dataStore.data.first()
        return StartupSnapshot(
            themeMode = ThemeMode.fromStorage(prefs[KEY_THEME_MODE]),
            onboardingComplete = prefs[KEY_ONBOARDING_COMPLETE] == true,
            dockLocation = DockLocation.fromStorage(prefs[KEY_DOCK_LOCATION]),
            passengerDualDockEnabled = prefs[KEY_PASSENGER_DUAL_DOCK_ENABLED] ?: false,
        )
    }

    val dockMapsPackageFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_DOCK_MAPS]?.takeIf { it.isNotBlank() }
    }

    val dockMusicPackageFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_DOCK_MUSIC]?.takeIf { it.isNotBlank() }
    }

    val dockPhonePackageFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_DOCK_PHONE]?.takeIf { it.isNotBlank() }
    }

    /** Varsayılan: sol. */
    val dockLocationFlow: Flow<DockLocation> = dataStore.data.map { prefs ->
        DockLocation.fromStorage(prefs[KEY_DOCK_LOCATION])
    }

    /** Varsayılan: kapalı. Yolcu koltuğu için her iki yanda da dock gösterir. */
    val passengerDualDockEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_PASSENGER_DUAL_DOCK_ENABLED] ?: false
    }

    /** Varsayılan: kapalı (false). */
    val seatbeltReminderEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SEATBELT_REMINDER_ENABLED] ?: false
    }

    /** Varsayılan: açık (true). Konum + internet izni varsa Overpass'ten hız sınırı çekilir. */
    val speedLimitEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SPEED_LIMIT_ENABLED] ?: true
    }

    /** Varsayılan: kapalı (false). Hız sınırı +10 km/h üzerinde TTS uyarısı. */
    val speedLimitWarningEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SPEED_LIMIT_WARNING_ENABLED] ?: false
    }

    /** Varsayılan: açık (true). TTS'ten önce iki tonlu "ding-dong" çalar. */
    val speedLimitChimeEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SPEED_LIMIT_CHIME_ENABLED] ?: true
    }

    /** Varsayılan: kapalı (false). */
    val internetTtsEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_INTERNET_TTS_ENABLED] ?: false
    }

    val hiddenAppPackagesFlow: Flow<Set<String>> = dataStore.data.map { prefs ->
        prefs[KEY_HIDDEN_APPS] ?: emptySet()
    }

    /** Varsayılan: açık (true). */
    val speedHeatColorsEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SPEED_HEAT_COLORS_ENABLED] ?: true
    }

    suspend fun setSeatbeltReminderEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SEATBELT_REMINDER_ENABLED] = enabled }
    }

    suspend fun setSpeedLimitEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SPEED_LIMIT_ENABLED] = enabled }
    }

    suspend fun setSpeedLimitWarningEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SPEED_LIMIT_WARNING_ENABLED] = enabled }
    }

    suspend fun setSpeedLimitChimeEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SPEED_LIMIT_CHIME_ENABLED] = enabled }
    }

    suspend fun setInternetTtsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_INTERNET_TTS_ENABLED] = enabled }
    }

    suspend fun setAppHidden(packageName: String, hidden: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[KEY_HIDDEN_APPS]?.toMutableSet() ?: mutableSetOf()
            if (hidden) current.add(packageName) else current.remove(packageName)
            if (current.isEmpty()) prefs.remove(KEY_HIDDEN_APPS) else prefs[KEY_HIDDEN_APPS] = current
        }
    }

    suspend fun resetOnboarding() {
        dataStore.edit { it[KEY_ONBOARDING_COMPLETE] = false }
    }

    suspend fun setSpeedHeatColorsEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SPEED_HEAT_COLORS_ENABLED] = enabled }
    }

    suspend fun getLastWeatherFetchEpochMs(): Long? = dataStore.data.first()[KEY_LAST_WEATHER_FETCH]

    suspend fun setLastWeatherFetchEpochMs(epochMs: Long) {
        dataStore.edit { it[KEY_LAST_WEATHER_FETCH] = epochMs }
    }

    suspend fun setDockMapsPackage(packageName: String?) {
        dataStore.edit {
            if (packageName.isNullOrBlank()) it.remove(KEY_DOCK_MAPS)
            else it[KEY_DOCK_MAPS] = packageName
        }
    }

    suspend fun setDockMusicPackage(packageName: String?) {
        dataStore.edit {
            if (packageName.isNullOrBlank()) it.remove(KEY_DOCK_MUSIC)
            else it[KEY_DOCK_MUSIC] = packageName
        }
    }

    suspend fun setDockPhonePackage(packageName: String?) {
        dataStore.edit {
            if (packageName.isNullOrBlank()) it.remove(KEY_DOCK_PHONE)
            else it[KEY_DOCK_PHONE] = packageName
        }
    }

    suspend fun setDockLocation(location: DockLocation) {
        dataStore.edit { it[KEY_DOCK_LOCATION] = location.storageKey }
    }

    suspend fun setPassengerDualDockEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_PASSENGER_DUAL_DOCK_ENABLED] = enabled }
    }

    companion object {
        private val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DOCK_MAPS = stringPreferencesKey("dock_maps_package")
        private val KEY_DOCK_MUSIC = stringPreferencesKey("dock_music_package")
        private val KEY_DOCK_PHONE = stringPreferencesKey("dock_phone_package")
        private val KEY_DOCK_LOCATION = stringPreferencesKey("dock_location")
        private val KEY_PASSENGER_DUAL_DOCK_ENABLED = booleanPreferencesKey("passenger_dual_dock_enabled")
        private val KEY_LAST_WEATHER_FETCH = longPreferencesKey("last_weather_fetch_epoch_ms")
        private val KEY_SEATBELT_REMINDER_ENABLED = booleanPreferencesKey("seatbelt_reminder_enabled")
        private val KEY_SPEED_HEAT_COLORS_ENABLED = booleanPreferencesKey("speed_heat_colors_enabled")
        private val KEY_INTERNET_TTS_ENABLED = booleanPreferencesKey("internet_tts_enabled")
        private val KEY_HIDDEN_APPS = stringSetPreferencesKey("hidden_app_packages")
        private val KEY_SPEED_LIMIT_ENABLED = booleanPreferencesKey("speed_limit_enabled")
        private val KEY_SPEED_LIMIT_WARNING_ENABLED = booleanPreferencesKey("speed_limit_warning_enabled")
        private val KEY_SPEED_LIMIT_CHIME_ENABLED = booleanPreferencesKey("speed_limit_chime_enabled")
    }
}