package com.karterlauncher

import android.app.Application
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.karterlauncher.data.ActiveMediaRepository
import com.karterlauncher.data.BluetoothConnectionTracker
import com.karterlauncher.data.BluetoothDashboardState
import com.karterlauncher.data.InstalledAppsRepository
import com.karterlauncher.data.LocationRepository
import com.karterlauncher.data.NowPlayingUi
import com.karterlauncher.data.ScreenInteractiveMonitor
import com.karterlauncher.data.SeatbeltReminderController
import com.karterlauncher.data.SpeedometerRepository
import com.karterlauncher.data.StartupSnapshot
import com.karterlauncher.data.UserPreferencesRepository
import com.karterlauncher.data.InternetConnectedTtsAnnouncer
import com.karterlauncher.data.ValidatedInternetMonitor
import com.karterlauncher.data.WeatherRepository
import com.karterlauncher.model.LaunchRequest
import com.karterlauncher.model.LaunchableApp
import com.karterlauncher.model.SpeedGaugeState
import com.karterlauncher.model.WeatherUiState
import com.karterlauncher.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LauncherViewModel(
    application: Application,
    private val userPreferencesRepository: UserPreferencesRepository,
    val startupSnapshot: StartupSnapshot,
) : AndroidViewModel(application) {
    private val repository = InstalledAppsRepository(application)
    private val weatherRepository = WeatherRepository()
    private val locationRepository = LocationRepository(application)
    private val activeMediaRepository = ActiveMediaRepository.getInstance(application)
    private val bluetoothTracker = BluetoothConnectionTracker(application)
    private val speedometerRepository = SpeedometerRepository(application)
    private val seatbeltReminderController = SeatbeltReminderController(application)
    private val internetMonitor = ValidatedInternetMonitor(application)
    private val internetConnectedTtsAnnouncer = InternetConnectedTtsAnnouncer(application)
    private val screenInteractiveMonitor = ScreenInteractiveMonitor(application)
    private val weatherRefreshMutex = Mutex()

    private val _apps = MutableStateFlow<List<LaunchableApp>>(emptyList())
    val apps: StateFlow<List<LaunchableApp>> = _apps.asStateFlow()

    private val _launchChannel = Channel<LaunchRequest>(Channel.BUFFERED)
    val launchRequests = _launchChannel.receiveAsFlow()

    private val _snackbarMessages = Channel<String>(Channel.BUFFERED)
    val snackbarMessages = _snackbarMessages.receiveAsFlow()

    private val _weatherState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val weatherState = _weatherState.asStateFlow()

    val nowPlaying: StateFlow<NowPlayingUi> = activeMediaRepository.nowPlaying

    val bluetoothState: StateFlow<BluetoothDashboardState> = bluetoothTracker.state

    val speedState: StateFlow<SpeedGaugeState> = speedometerRepository.state

    val userPreferences: UserPreferencesRepository get() = userPreferencesRepository

    init {
        refreshNowPlaying()
        bluetoothTracker.start()
        internetMonitor.start()
        screenInteractiveMonitor.start()
        viewModelScope.launch {
            combine(
                internetMonitor.connected,
                screenInteractiveMonitor.interactive,
            ) { online, interactive -> online to interactive }
                .distinctUntilChanged()
                .collectLatest { (online, interactive) ->
                    if (online && interactive) {
                        runWeatherAutoRefreshLoopWhileAwakeAndOnline()
                    }
                }
        }
        viewModelScope.launch {
            var previousOnline = internetMonitor.connected.value
            internetMonitor.connected.collect { online ->
                if (online && !previousOnline) {
                    maybeRefreshWeatherIfStaleSinceLastFetch()
                    if (userPreferencesRepository.internetTtsEnabledFlow.first()) {
                        internetConnectedTtsAnnouncer.speakConnectivityRestored()
                    }
                }
                previousOnline = online
            }
        }
        viewModelScope.launch {
            var previousAwake = screenInteractiveMonitor.interactive.value
            screenInteractiveMonitor.interactive.collect { awake ->
                if (awake && !previousAwake) {
                    maybeRefreshWeatherIfStaleSinceLastFetch()
                }
                previousAwake = awake
            }
        }
        viewModelScope.launch {
            if (locationRepository.hasLocationPermission()) {
                ensureWeatherLoaded()
            }
        }
    }

    override fun onCleared() {
        bluetoothTracker.stop()
        speedometerRepository.stop()
        seatbeltReminderController.destroy()
        internetMonitor.stop()
        internetConnectedTtsAnnouncer.shutdown()
        screenInteractiveMonitor.stop()
        super.onCleared()
    }

    /** GPS hız güncellemeleri — [Lifecycle.Event.ON_RESUME] sırasında çağırın. */
    fun startSpeedometer() {
        speedometerRepository.start()
        seatbeltReminderController.startObserving(
            speedometerRepository.state,
            userPreferencesRepository.seatbeltReminderEnabledFlow,
        )
    }

    /** [Lifecycle.Event.ON_PAUSE] veya gerektiğinde pili korumak için. */
    fun stopSpeedometer() {
        seatbeltReminderController.stopObserving()
        speedometerRepository.stop()
    }

    fun refreshNowPlaying() {
        activeMediaRepository.refreshSessions()
        activeMediaRepository.attachActiveSessionsListener()
    }

    fun refreshBluetooth() {
        bluetoothTracker.refresh()
    }

    fun openAppSettings() {
        viewModelScope.launch {
            launchIntent(
                Intent(getApplication(), SettingsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                failureLabel = getApplication<Application>().getString(R.string.dock_settings),
            )
        }
    }

    fun refreshApps() {
        viewModelScope.launch {
            val hidden = userPreferencesRepository.hiddenAppPackagesFlow.first()
            _apps.value = repository.getLaunchableApps().filter { it.packageName !in hidden }
        }
    }

    fun setAppHidden(packageName: String, hidden: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAppHidden(packageName, hidden)
            refreshApps()
        }
    }

    fun refreshWeather() {
        viewModelScope.launch {
            refreshWeatherInternal(showLoading = _weatherState.value !is WeatherUiState.Ready)
        }
    }

    fun onLocationPermissionGranted() {
        startSpeedometer()
        viewModelScope.launch { ensureWeatherLoaded() }
    }

    private suspend fun ensureWeatherLoaded() {
        if (_weatherState.value is WeatherUiState.Ready) {
            maybeRefreshWeatherIfStaleSinceLastFetch()
            return
        }
        maybeRefreshWeatherIfStaleSinceLastFetch()
        if (_weatherState.value !is WeatherUiState.Ready) {
            refreshWeatherInternal(showLoading = true)
        }
    }

    private suspend fun runWeatherAutoRefreshLoopWhileAwakeAndOnline() {
        while (true) {
            if (!internetMonitor.connected.value || !screenInteractiveMonitor.interactive.value) {
                return
            }
            val last = userPreferencesRepository.getLastWeatherFetchEpochMs()
            val now = System.currentTimeMillis()
            val stale =
                last == null || now - last >= WEATHER_AUTO_REFRESH_PERIOD_MS
            if (stale && locationRepository.hasLocationPermission()) {
                refreshWeatherInternal(showLoading = false)
            }
            val lastAfter = userPreferencesRepository.getLastWeatherFetchEpochMs()
            val delayMs = when {
                lastAfter == null -> WEATHER_RETRY_WHEN_NEVER_FETCHED_MS
                else -> {
                    val nextDue = lastAfter + WEATHER_AUTO_REFRESH_PERIOD_MS
                    (nextDue - System.currentTimeMillis()).coerceAtLeast(WEATHER_MIN_DELAY_MS)
                }
            }
            delay(delayMs)
        }
    }

    /**
     * Son başarılı hava çekiminden ~1 saat geçmişse sessiz yenile.
     * Ağ doğrulanmamışken API çağrısı yapılmaz (ekran yeniden açıldığında / bağlantı dönüşünde).
     */
    private suspend fun maybeRefreshWeatherIfStaleSinceLastFetch() {
        if (!internetMonitor.connected.value) return
        val last = userPreferencesRepository.getLastWeatherFetchEpochMs()
        val now = System.currentTimeMillis()
        if (last != null && now - last < WEATHER_AUTO_REFRESH_PERIOD_MS) return
        refreshWeatherInternal(showLoading = false)
    }

    private suspend fun refreshWeatherInternal(showLoading: Boolean) {
        weatherRefreshMutex.withLock {
            if (!locationRepository.hasLocationPermission()) {
                if (showLoading) {
                    _weatherState.value = WeatherUiState.NeedLocation
                }
                return@withLock
            }
            val loc = locationRepository.getDeviceLocation()
            if (loc == null) {
                if (showLoading) {
                    _weatherState.value = WeatherUiState.NeedLocation
                }
                return@withLock
            }
            if (showLoading) {
                _weatherState.value = WeatherUiState.Loading
            }
            val label = locationRepository.reverseGeocodeLabel(loc.latitude, loc.longitude)
                ?: getApplication<Application>().getString(R.string.weather_location_current)
            weatherRepository.fetchCurrent(loc.latitude, loc.longitude, label).fold(
                onSuccess = { summary ->
                    _weatherState.value = WeatherUiState.Ready(summary, stale = false)
                    userPreferencesRepository.setLastWeatherFetchEpochMs(System.currentTimeMillis())
                },
                onFailure = {
                    val prev = _weatherState.value
                    _weatherState.value = when {
                        prev is WeatherUiState.Ready -> prev.copy(stale = true)
                        showLoading -> WeatherUiState.Error
                        else -> prev
                    }
                },
            )
        }
    }

    fun openApp(app: LaunchableApp) {
        viewModelScope.launch {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = app.componentName
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            launchIntent(intent, failureLabel = app.label)
        }
    }

    fun openMapsFromDock() {
        viewModelScope.launch {
            val custom = userPreferencesRepository.dockMapsPackageFlow.first()
            if (custom.isNullOrBlank()) {
                openMapsDefault()
            } else {
                launchPackageOrFallback(custom) { openMapsDefault() }
            }
        }
    }

    fun openMusicFromDock() {
        viewModelScope.launch {
            val custom = userPreferencesRepository.dockMusicPackageFlow.first()
            if (custom.isNullOrBlank()) {
                openMusicDefault()
            } else {
                launchPackageOrFallback(custom) { openMusicDefault() }
            }
        }
    }

    fun openPhoneFromDock() {
        viewModelScope.launch {
            val custom = userPreferencesRepository.dockPhonePackageFlow.first()
            if (custom.isNullOrBlank()) {
                openBuiltinPhoneHub()
            } else {
                launchPackageOrFallback(custom) { openDialerDefault() }
            }
        }
    }

    private suspend fun launchPackageOrFallback(
        packageName: String,
        fallback: suspend () -> Unit,
    ) {
        val pm = getApplication<Application>().packageManager
        val launch = pm.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        if (launch != null) {
            launchIntent(launch, failureLabel = packageName)
        } else {
            fallback()
        }
    }

    private suspend fun openMapsDefault() {
        val pm = getApplication<Application>().packageManager
        val mapsPkg = "com.google.android.apps.maps"
        val launch = pm.getLaunchIntentForPackage(mapsPkg)
        val intent = if (launch != null) {
            launch.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        } else {
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
        launchIntent(
            intent,
            failureLabel = getApplication<Application>().getString(R.string.dock_maps),
        )
    }

    private suspend fun openMusicDefault() {
        val intent = Intent.makeMainSelectorActivity(
            Intent.ACTION_MAIN,
            Intent.CATEGORY_APP_MUSIC,
        )
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        launchIntent(
            intent,
            failureLabel = getApplication<Application>().getString(R.string.dock_music),
        )
    }

    private suspend fun openBuiltinPhoneHub() {
        val intent = Intent(getApplication(), PhoneHubActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        launchIntent(
            intent,
            failureLabel = getApplication<Application>().getString(R.string.dock_phone),
        )
    }

    private suspend fun openDialerDefault() {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        launchIntent(
            intent,
            failureLabel = getApplication<Application>().getString(R.string.dock_phone),
        )
    }

    private suspend fun launchIntent(intent: Intent, failureLabel: String? = null) {
        val pm = getApplication<Application>().packageManager
        if (intent.resolveActivity(pm) == null) {
            val label = failureLabel ?: getApplication<Application>().getString(R.string.app_name)
            _snackbarMessages.send(
                getApplication<Application>().getString(R.string.launch_error_not_found, label),
            )
            return
        }
        _launchChannel.send(LaunchRequest(intent, failureLabel))
    }

    /** Harita widget / dış bağlantılar için (dock kısayolundan bağımsız). */
    fun openMapsExternal() {
        viewModelScope.launch { openMapsDefault() }
    }

    fun openMusicApp() {
        viewModelScope.launch { openMusicDefault() }
    }

    /** Head-unit friendly: steers the active global media session when supported by the ROM. */
    fun mediaPlayPause() {
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun mediaNext() {
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun mediaPrevious() {
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    fun skipToQueueItem(queueId: Long) {
        activeMediaRepository.skipToQueueItem(queueId)
    }

    private fun dispatchMediaKey(keyCode: Int) {
        val audioManager =
            getApplication<Application>().getSystemService(Application.AUDIO_SERVICE) as AudioManager
        val uptime = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(uptime, uptime, KeyEvent.ACTION_DOWN, keyCode, 0),
        )
        audioManager.dispatchMediaKeyEvent(
            KeyEvent(uptime, uptime, KeyEvent.ACTION_UP, keyCode, 0),
        )
    }

    private companion object {
        private const val WEATHER_AUTO_REFRESH_PERIOD_MS = 60L * 60L * 1000L
        private const val WEATHER_MIN_DELAY_MS = 60_000L
        private const val WEATHER_RETRY_WHEN_NEVER_FETCHED_MS = 10L * 60L * 1000L
    }
}
