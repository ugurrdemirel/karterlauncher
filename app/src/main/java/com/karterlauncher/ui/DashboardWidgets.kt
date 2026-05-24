package com.karterlauncher.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.os.Build
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.content.pm.PackageManager
import android.media.session.MediaSession.QueueItem
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.karterlauncher.LauncherViewModel
import com.karterlauncher.R
import com.karterlauncher.data.BluetoothDashboardState
import com.karterlauncher.data.NowPlayingUi
import com.karterlauncher.model.SpeedGaugeState
import com.karterlauncher.model.WeatherSummary
import com.karterlauncher.model.WeatherUiState
import com.karterlauncher.ui.theme.LauncherMotion
import kotlin.math.round

/** Düşük hızda 0, [SPEED_HEAT_END_KMH] civarında 1 — renk kırmızıya kayar. */
private fun speedHeatFraction(kmh: Float): Float =
    if (kmh <= SPEED_HEAT_START_KMH) {
        0f
    } else {
        ((kmh - SPEED_HEAT_START_KMH) / (SPEED_HEAT_END_KMH - SPEED_HEAT_START_KMH)).coerceIn(0f, 1f)
    }

private const val SPEED_HEAT_START_KMH = 15f
private const val SPEED_HEAT_END_KMH = 120f

@Suppress("ObsoleteSdkInt") // RECEIVER_* requires API check
private fun registerVolumeBroadcastReceiver(context: Context, receiver: BroadcastReceiver, filter: IntentFilter) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        context.registerReceiver(receiver, filter)
    }
}

/** Media (music) volume: hardware keys sync via undocumented broadcast honored on typical builds. */
@Composable
private fun MediaVolumeSliderRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxSteps = remember(audioManager) {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    val volumeIndexState = remember(audioManager, maxSteps) {
        mutableIntStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxSteps),
        )
    }

    DisposableEffect(audioManager, context, lifecycleOwner, maxSteps) {
        fun syncVolume() {
            volumeIndexState.intValue =
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxSteps)
        }
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                syncVolume()
            }
        }
        registerVolumeBroadcastReceiver(context, receiver, filter)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                syncVolume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                context.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            }
        }
    }

    fun applyVolume(step: Int) {
        val v = step.coerceIn(0, maxSteps)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
        volumeIndexState.intValue = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maxSteps)
    }

    val title = stringResource(R.string.volume_widget_title)
    val descDecrease = stringResource(R.string.volume_widget_decrease)
    val descIncrease = stringResource(R.string.volume_widget_increase)
    val sliderDesc = stringResource(R.string.volume_widget_slider_accessibility)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilledTonalIconButton(
                    onClick = { applyVolume(volumeIndexState.intValue - 1) },
                    modifier = Modifier
                        .size(52.dp)
                        .semantics { contentDescription = descDecrease },
                ) {
                    Icon(
                        Icons.Filled.VolumeDown,
                        contentDescription = null,
                    )
                }
                Slider(
                    value = volumeIndexState.intValue.toFloat() / maxSteps.toFloat(),
                    onValueChange = { f ->
                        val idx =
                            round(f.coerceIn(0f, 1f).toDouble() * maxSteps)
                                .toInt()
                                .coerceIn(0, maxSteps)
                        if (idx != volumeIndexState.intValue) {
                            applyVolume(idx)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = sliderDesc
                        },
                )
                FilledTonalIconButton(
                    onClick = { applyVolume(volumeIndexState.intValue + 1) },
                    modifier = Modifier
                        .size(52.dp)
                        .semantics { contentDescription = descIncrease },
                ) {
                    Icon(
                        Icons.Filled.VolumeUp,
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

@Composable
fun HomeDashboard(
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val bluetoothState by viewModel.bluetoothState.collectAsStateWithLifecycle()
    val speedState by viewModel.speedState.collectAsStateWithLifecycle()
    val prefsVm = viewModel.userPreferences
    val speedHeatColorsEnabled by prefsVm.speedHeatColorsEnabledFlow.collectAsStateWithLifecycle(
        initialValue = true,
    )
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) {
            viewModel.onLocationPermissionGranted()
        }
    }
    val bluetoothConnectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.refreshBluetooth()
        }
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refreshBluetooth()
    }

    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp)
                .height(280.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MusicNowPlayingCard(
                nowPlaying = nowPlaying,
                onPrevious = { viewModel.mediaPrevious() },
                onPlayPause = { viewModel.mediaPlayPause() },
                onNext = { viewModel.mediaNext() },
                onOpenMusicApp = { viewModel.openMusicApp() },
                onSkipToQueueItem = { viewModel.skipToQueueItem(it) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            SpeedGaugeCard(
                state = speedState,
                onRequestPermission = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
                heatColorsEnabled = speedHeatColorsEnabled,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }

        MediaVolumeSliderRow(modifier = Modifier.fillMaxWidth())

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WeatherWidgetCard(
                state = weatherState,
                onRetry = { viewModel.refreshWeather() },
                onRequestLocation = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            BluetoothWidgetCard(
                state = bluetoothState,
                onRequestConnectPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        bluetoothConnectLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                },
                onRequestEnableBluetooth = {
                    val nm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                    val adapter = nm?.adapter
                    if (adapter != null && !adapter.isEnabled) {
                        try {
                            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        } catch (_: Exception) {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        }
                    }
                },
                onOpenBluetoothSettings = {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun SpeedGaugeCard(
    state: SpeedGaugeState,
    onRequestPermission: () -> Unit,
    heatColorsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val waitingLabel = stringResource(R.string.speed_waiting_fix)
    val noPermissionLabel = stringResource(R.string.speed_no_permission_hint)
    val widgetTitleLabel = stringResource(R.string.speed_widget_title)
    val kmhUnitLabel = stringResource(R.string.speed_unit_kmh)
    val semanticsLabel = when (state) {
        SpeedGaugeState.NoPermission -> noPermissionLabel
        SpeedGaugeState.WaitingForFix -> waitingLabel
        is SpeedGaugeState.Speed -> {
            val k = state.kmh.toInt().coerceAtLeast(0)
            "$widgetTitleLabel: $k $kmhUnitLabel"
        }
    }

    val surfaceBase: Color
    val contentMain: Color
    val badgeIconTint: Color
    val badgeIconBg: Color
    val decorBrush: Brush
    val needleColor: Color
    val meterFill: Brush
    val meterEnabled: Boolean
    val meterSpeed: Float
    val meterTrackColor: Color
    val meterTickColor: Color

    val needsPermissionTap = state is SpeedGaugeState.NoPermission

    when (state) {
        SpeedGaugeState.NoPermission -> {
            surfaceBase = scheme.surfaceVariant
            contentMain = scheme.onSurfaceVariant
            badgeIconTint = scheme.error
            badgeIconBg = scheme.error.copy(alpha = 0.14f)
            decorBrush = Brush.verticalGradient(
                colors = listOf(
                    scheme.error.copy(alpha = 0.06f),
                    Color.Transparent,
                    scheme.surfaceVariant.copy(alpha = 0.45f),
                ),
            )
            needleColor = contentMain.copy(alpha = 0.55f)
            meterFill = Brush.horizontalGradient(
                listOf(contentMain.copy(0.5f), contentMain.copy(0.35f)),
            )
            meterEnabled = false
            meterSpeed = 0f
            meterTrackColor = contentMain.copy(alpha = 0.18f)
            meterTickColor = contentMain.copy(alpha = 0.55f)
        }

        SpeedGaugeState.WaitingForFix -> {
            surfaceBase = scheme.surfaceContainerHigh
            contentMain = scheme.onSurface
            badgeIconTint = scheme.primary
            badgeIconBg = scheme.primary.copy(alpha = 0.12f)
            decorBrush = Brush.verticalGradient(
                colors = listOf(
                    scheme.primary.copy(alpha = 0.07f),
                    Color.Transparent,
                    scheme.surfaceContainerHigh.copy(alpha = 0f),
                ),
            )
            needleColor = scheme.primary
            meterFill = Brush.horizontalGradient(listOf(scheme.primary, scheme.tertiary))
            meterEnabled = false
            meterSpeed = 0f
            meterTrackColor = contentMain.copy(alpha = 0.3f)
            meterTickColor = contentMain.copy(alpha = 0.55f)
        }

        is SpeedGaugeState.Speed -> {
            val moving = state.kmh >= 3f
            when {
                moving && heatColorsEnabled -> {
                    val heat = speedHeatFraction(state.kmh)
                    badgeIconTint = lerp(scheme.primary, scheme.error, heat)
                    badgeIconBg = badgeIconTint.copy(alpha = 0.14f)
                    surfaceBase = lerp(scheme.primaryContainer, scheme.errorContainer, heat)
                    contentMain = lerp(scheme.onPrimaryContainer, scheme.onErrorContainer, heat)
                    needleColor = lerp(scheme.tertiary, scheme.error, heat)
                    meterEnabled = true
                    meterSpeed = state.kmh
                    meterFill = Brush.horizontalGradient(
                        colors = listOf(
                            lerp(scheme.tertiary, scheme.error, heat),
                            lerp(scheme.primary, scheme.error, heat * 0.92f),
                            lerp(badgeIconTint, scheme.error.copy(alpha = 0.95f), heat * 0.65f),
                        ),
                    )
                    decorBrush = Brush.verticalGradient(
                        colors = listOf(
                            lerp(
                                scheme.primary.copy(alpha = 0.2f),
                                scheme.error.copy(alpha = 0.26f),
                                heat,
                            ),
                            Color.Transparent,
                            lerp(
                                scheme.primary.copy(alpha = 0.06f),
                                scheme.error.copy(alpha = 0.12f),
                                heat,
                            ),
                        ),
                    )
                    meterTrackColor = lerp(
                        scheme.onPrimaryContainer.copy(alpha = 0.22f),
                        scheme.error.copy(alpha = 0.34f),
                        heat * 0.92f,
                    )
                    meterTickColor = lerp(
                        scheme.onPrimaryContainer.copy(alpha = 0.55f),
                        scheme.error.copy(alpha = 0.72f),
                        heat * 0.85f,
                    )
                }
                moving -> {
                    badgeIconTint = scheme.primary
                    badgeIconBg = badgeIconTint.copy(alpha = 0.14f)
                    surfaceBase = scheme.primaryContainer
                    contentMain = scheme.onPrimaryContainer
                    needleColor = scheme.error
                    meterEnabled = true
                    meterSpeed = state.kmh
                    meterFill = Brush.horizontalGradient(
                        colors = listOf(
                            scheme.tertiary,
                            scheme.primary,
                            badgeIconTint.copy(alpha = 0.92f),
                        ),
                    )
                    decorBrush = Brush.verticalGradient(
                        colors = listOf(
                            scheme.primary.copy(alpha = 0.2f),
                            Color.Transparent,
                            scheme.primary.copy(alpha = 0.06f),
                        ),
                    )
                    meterTrackColor = scheme.onPrimaryContainer.copy(alpha = 0.22f)
                    meterTickColor = scheme.onPrimaryContainer.copy(alpha = 0.55f)
                }
                else -> {
                    badgeIconTint = scheme.secondary
                    badgeIconBg = badgeIconTint.copy(alpha = 0.14f)
                    surfaceBase = scheme.surfaceVariant
                    contentMain = scheme.onSurface
                    needleColor = scheme.onSurfaceVariant
                    meterEnabled = true
                    meterSpeed = state.kmh
                    meterFill = Brush.horizontalGradient(
                        colors = listOf(
                            scheme.secondary.copy(alpha = 0.75f),
                            scheme.primary.copy(alpha = 0.85f),
                        ),
                    )
                    decorBrush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent,
                            scheme.surfaceVariant.copy(alpha = 0.85f),
                        ),
                    )
                    meterTrackColor = contentMain.copy(alpha = 0.22f)
                    meterTickColor = contentMain.copy(alpha = 0.55f)
                }
            }
        }
    }

    val animatedSurface by animateColorAsState(
        targetValue = surfaceBase,
        animationSpec = tween(LauncherMotion.ColorFadeMs),
        label = "speedCardSurface",
    )
    val animatedContent by animateColorAsState(
        targetValue = contentMain,
        animationSpec = tween(LauncherMotion.ColorFadeMs),
        label = "speedCardContent",
    )

    Card(
        modifier = modifier
            .semantics { contentDescription = semanticsLabel }
            .then(
                if (needsPermissionTap) {
                    Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) {
                        onRequestPermission()
                    }
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = animatedSurface,
            contentColor = animatedContent,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(decorBrush),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.speed_widget_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = contentMain,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.speed_unit_kmh),
                            style = MaterialTheme.typography.labelMedium,
                            color = contentMain.copy(alpha = 0.65f),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(badgeIconBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            targetState = state::class,
                            transitionSpec = { LauncherMotion.tabContentTransform() },
                            label = "speedBadge",
                        ) { stateKind ->
                            when (stateKind) {
                                SpeedGaugeState.NoPermission::class -> Icon(
                                    imageVector = Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = badgeIconTint,
                                    modifier = Modifier.size(28.dp),
                                )
                                SpeedGaugeState.WaitingForFix::class -> CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp,
                                    color = badgeIconTint,
                                )
                                else -> Icon(
                                    imageVector = Icons.Filled.Speed,
                                    contentDescription = null,
                                    tint = badgeIconTint,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                ) {
                    SpeedSemiGaugeMeter(
                        speedKmh = meterSpeed,
                        enabled = meterEnabled,
                        trackColor = meterTrackColor,
                        fillBrush = meterFill,
                        needleBaseColor = needleColor,
                        tickColor = meterTickColor,
                        modifier = Modifier.fillMaxSize(),
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(bottom = 52.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AnimatedContent(
                            targetState = when (state) {
                                SpeedGaugeState.NoPermission -> -1
                                SpeedGaugeState.WaitingForFix -> -2
                                is SpeedGaugeState.Speed -> state.kmh.toInt().coerceAtLeast(0)
                            },
                            transitionSpec = { LauncherMotion.tabContentTransform() },
                            label = "speedReadout",
                        ) { readout ->
                            when (readout) {
                                -1 -> {
                                    Text(
                                        text = "—",
                                        style = MaterialTheme.typography.displayMedium,
                                        color = contentMain.copy(alpha = 0.22f),
                                    )
                                }
                                -2 -> Unit
                                else -> {
                                    Row(
                                        verticalAlignment = Alignment.Bottom,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            text = readout.toString(),
                                            style = MaterialTheme.typography.displayLarge.copy(
                                                fontWeight = FontWeight.SemiBold,
                                            ),
                                            color = contentMain.copy(alpha = 0.92f),
                                            maxLines = 1,
                                        )
                                        Text(
                                            text = kmhUnitLabel,
                                            modifier = Modifier.padding(bottom = 6.dp),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = contentMain.copy(alpha = 0.55f),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (state is SpeedGaugeState.NoPermission || state is SpeedGaugeState.WaitingForFix) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            when (state) {
                                SpeedGaugeState.NoPermission ->
                                    Text(
                                        text = noPermissionLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = contentMain.copy(alpha = 0.78f),
                                        textAlign = TextAlign.Center,
                                    )
                                SpeedGaugeState.WaitingForFix ->
                                    Text(
                                        text = waitingLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = contentMain.copy(alpha = 0.78f),
                                        textAlign = TextAlign.Center,
                                    )
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BluetoothWidgetCard(
    state: BluetoothDashboardState,
    onRequestConnectPermission: () -> Unit,
    onRequestEnableBluetooth: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor: androidx.compose.ui.graphics.Color
    val contentColor: androidx.compose.ui.graphics.Color
    val headlineColor: androidx.compose.ui.graphics.Color
    val iconVector: ImageVector
    val iconTint: androidx.compose.ui.graphics.Color
    val subtitle: String

    when {
        state.needsConnectPermission -> {
            containerColor = scheme.surfaceVariant
            contentColor = scheme.onSurfaceVariant
            headlineColor = scheme.onSurfaceVariant
            iconVector = Icons.Filled.Bluetooth
            iconTint = scheme.primary
            subtitle = stringResource(R.string.bt_widget_permission)
        }
        state.adapterDisabled -> {
            containerColor = scheme.errorContainer
            contentColor = scheme.onErrorContainer
            headlineColor = scheme.onErrorContainer
            iconVector = Icons.Outlined.BluetoothDisabled
            iconTint = scheme.error
            subtitle = stringResource(R.string.bt_widget_off) + "\n" + stringResource(R.string.bt_widget_tap_enable)
        }
        state.connectedNames.size == 1 -> {
            containerColor = scheme.primaryContainer
            contentColor = scheme.onPrimaryContainer
            headlineColor = scheme.onPrimaryContainer
            iconVector = Icons.Filled.Bluetooth
            iconTint = scheme.primary
            subtitle = stringResource(R.string.bt_widget_connected_one, state.connectedNames.first())
        }
        state.connectedNames.size > 1 -> {
            containerColor = scheme.primaryContainer
            contentColor = scheme.onPrimaryContainer
            headlineColor = scheme.onPrimaryContainer
            iconVector = Icons.Filled.Bluetooth
            iconTint = scheme.primary
            subtitle = stringResource(R.string.bt_widget_connected_many, state.connectedNames.size)
        }
        else -> {
            containerColor = scheme.surface
            contentColor = scheme.onSurfaceVariant
            headlineColor = scheme.onSurface
            iconVector = Icons.Filled.Bluetooth
            iconTint = scheme.onSurfaceVariant
            subtitle = stringResource(R.string.bt_widget_on_no_device)
        }
    }

    val onCardClick: () -> Unit = {
        when {
            state.needsConnectPermission -> onRequestConnectPermission()
            state.adapterDisabled -> onRequestEnableBluetooth()
            else -> onOpenBluetoothSettings()
        }
    }

    Card(
        modifier = modifier.clickable(onClick = onCardClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.bt_widget_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = headlineColor,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun WeatherWidgetCard(
    state: WeatherUiState,
    onRetry: () -> Unit,
    onRequestLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.weather_widget_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                targetState = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                transitionSpec = { LauncherMotion.tabContentTransform() },
                label = "weatherBody",
            ) { weatherState ->
            when (weatherState) {
                WeatherUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                            )
                            Text(
                                text = stringResource(R.string.weather_loading),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                WeatherUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true)
                            .clickable(onClick = onRetry),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.weather_error),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = stringResource(R.string.weather_tap_retry),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                WeatherUiState.NeedLocation -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true)
                            .clickable(onClick = onRequestLocation),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.weather_need_location_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = stringResource(R.string.weather_need_location_body),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.weather_need_location_action),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                is WeatherUiState.Ready -> {
                    WeatherReadyContent(
                        summary = weatherState.summary,
                        stale = weatherState.stale,
                        onRefresh = if (weatherState.stale) onRetry else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun WeatherReadyContent(
    summary: WeatherSummary,
    stale: Boolean,
    onRefresh: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val clickableModifier =
        if (stale && onRefresh != null) {
            modifier.clickable(onClick = onRefresh)
        } else {
            modifier
        }
    Row(
        modifier = clickableModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.weather_temp_c, summary.tempC),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = wmoDescription(summary.wmoCode),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary.locationLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (stale) {
                Text(
                    text = stringResource(R.string.weather_stale_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                )
            }
        }
        Icon(
            imageVector = wmoIcon(summary.wmoCode),
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun wmoDescription(code: Int): String = stringResource(
    when (code) {
        0 -> R.string.weather_clear
        in 1..3 -> R.string.weather_cloudy
        in 45..48 -> R.string.weather_fog
        in 51..57 -> R.string.weather_drizzle
        in 61..67 -> R.string.weather_rain
        in 71..77 -> R.string.weather_snow
        in 80..82 -> R.string.weather_showers
        in 85..86 -> R.string.weather_snow_showers
        in 95..99 -> R.string.weather_thunder
        else -> R.string.weather_unknown
    },
)

private fun wmoIcon(code: Int): ImageVector = when (code) {
    0 -> Icons.Filled.WbSunny
    in 1..3 -> Icons.Filled.Cloud
    in 45..48 -> Icons.Filled.Cloud
    in 51..57 -> Icons.Filled.WaterDrop
    in 61..67 -> Icons.Filled.WaterDrop
    in 71..77 -> Icons.Filled.AcUnit
    in 80..82 -> Icons.Filled.Grain
    in 85..86 -> Icons.Filled.AcUnit
    in 95..99 -> Icons.Filled.Thunderstorm
    else -> Icons.Filled.Cloud
}

@Composable
private fun MusicNowPlayingCard(
    nowPlaying: NowPlayingUi,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenMusicApp: () -> Unit,
    onSkipToQueueItem: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val placeholderPainter = remember(surfaceVariant) { ColorPainter(surfaceVariant) }

    val titleText = when {
        nowPlaying.hasActiveSession && !nowPlaying.title.isNullOrBlank() -> nowPlaying.title
        nowPlaying.hasActiveSession -> stringResource(R.string.music_widget_unknown_track)
        else -> stringResource(R.string.music_widget_title)
    }
    val subtitleText = when {
        !nowPlaying.notificationAccessEnabled ->
            stringResource(R.string.music_widget_enable_listener)
        nowPlaying.hasActiveSession -> {
            nowPlaying.subtitle?.takeIf { it.isNotBlank() }
                ?: nowPlaying.artist?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.music_widget_playing)
        }
        else -> stringResource(R.string.music_widget_nothing_playing)
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (nowPlaying.artworkModel != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(nowPlaying.artworkModel)
                                .crossfade(true)
                                .size(Size(256, 256))
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            placeholder = placeholderPainter,
                            error = placeholderPainter,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        IconButton(
                            onClick = onOpenMusicApp,
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = stringResource(R.string.dock_music),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (!nowPlaying.notificationAccessEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = !nowPlaying.notificationAccessEnabled) {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                    )
                }
            }
            val queueScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(queueScroll),
            ) {
                if (nowPlaying.upNext.isNotEmpty() &&
                    nowPlaying.hasActiveSession &&
                    nowPlaying.notificationAccessEnabled
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.music_widget_up_next),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(6.dp))
                        val playQueuedDesc = stringResource(R.string.music_widget_play_queued_track)
                        nowPlaying.upNext.take(3).forEach { track ->
                            val canPlay = track.queueId != QueueItem.UNKNOWN_ID.toLong()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .semantics {
                                        contentDescription = if (canPlay) {
                                            "$playQueuedDesc. ${track.title}"
                                        } else {
                                            track.title
                                        }
                                    }
                                    .clickable(
                                        enabled = canPlay,
                                        onClick = { onSkipToQueueItem(track.queueId) },
                                    )
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = "\u2022",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (canPlay) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                    if (track.subtitle != null) {
                                        Text(
                                            text = track.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(58.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = stringResource(R.string.music_widget_prev),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(42.dp),
                    )
                }
                Spacer(Modifier.size(12.dp))
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(76.dp),
                ) {
                    Icon(
                        imageVector = if (nowPlaying.isPlaying) {
                            Icons.Filled.Pause
                        } else {
                            Icons.Filled.PlayArrow
                        },
                        contentDescription = stringResource(R.string.music_widget_play_pause),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(54.dp),
                    )
                }
                Spacer(Modifier.size(12.dp))
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(58.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = stringResource(R.string.music_widget_next),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(42.dp),
                    )
                }
            }
        }
    }
}

