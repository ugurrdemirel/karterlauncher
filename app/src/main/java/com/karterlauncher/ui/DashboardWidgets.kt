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
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
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
import com.karterlauncher.model.SpeedLimitState
import com.karterlauncher.model.WeatherSummary
import com.karterlauncher.model.WeatherUiState
import com.karterlauncher.ui.theme.LauncherMotion
import com.karterlauncher.ui.theme.rememberSoftPressInteraction
import com.karterlauncher.ui.theme.softPressScale
import com.karterlauncher.util.formatTrackDuration
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
    onOpenMusicPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val positionMs by viewModel.positionMs.collectAsStateWithLifecycle()
    val bluetoothState by viewModel.bluetoothState.collectAsStateWithLifecycle()
    val speedState by viewModel.speedState.collectAsStateWithLifecycle()
    val speedLimitState by viewModel.speedLimitState.collectAsStateWithLifecycle()
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
                positionMs = positionMs,
                onPrevious = { viewModel.mediaPrevious() },
                onPlayPause = { viewModel.mediaPlayPause() },
                onNext = { viewModel.mediaNext() },
                onOpenMusicPlayer = onOpenMusicPlayer,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            SpeedGaugeCard(
                state = speedState,
                speedLimitState = speedLimitState,
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
    speedLimitState: SpeedLimitState,
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

                    SpeedLimitChip(
                        limitState = speedLimitState,
                        currentSpeedKmh = (state as? SpeedGaugeState.Speed)?.kmh,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedLimitChip(
    limitState: SpeedLimitState,
    currentSpeedKmh: Float?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val exceeded: Boolean = limitState is SpeedLimitState.Known &&
        currentSpeedKmh != null &&
        currentSpeedKmh >= limitState.kmh

    val containerColor: Color
    val contentColor: Color
    val valueText: String
    val trailing: @Composable (() -> Unit)?

    when (limitState) {
        SpeedLimitState.Idle, SpeedLimitState.Loading -> {
            containerColor = scheme.surfaceContainerHigh.copy(alpha = 0.78f)
            contentColor = scheme.onSurfaceVariant
            valueText = "—"
            trailing = {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
        }
        is SpeedLimitState.Known -> {
            containerColor = if (exceeded) scheme.errorContainer else scheme.primaryContainer
            contentColor = if (exceeded) scheme.onErrorContainer else scheme.onPrimaryContainer
            valueText = limitState.kmh.toString()
            trailing = null
        }
        is SpeedLimitState.Unknown -> {
            containerColor = scheme.surfaceContainerHigh.copy(alpha = 0.78f)
            contentColor = scheme.onSurfaceVariant
            valueText = "—"
            trailing = null
        }
        is SpeedLimitState.Error -> {
            containerColor = scheme.errorContainer.copy(alpha = 0.7f)
            contentColor = scheme.onErrorContainer
            valueText = "—"
            trailing = {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = contentColor,
                )
            }
        }
    }

    val limitTitle = stringResource(R.string.speed_limit_widget_title)
    val kmhUnit = stringResource(R.string.speed_unit_kmh)
    val a11y = when (limitState) {
        is SpeedLimitState.Known -> "$limitTitle: ${limitState.kmh} $kmhUnit"
        else -> limitTitle
    }

    Surface(
        modifier = modifier.semantics { contentDescription = a11y },
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Speed,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor,
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = contentColor,
                maxLines = 1,
            )
            if (trailing != null) {
                trailing()
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
    positionMs: Long,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenMusicPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val placeholderPainter = remember(surfaceVariant) { ColorPainter(surfaceVariant) }
    val scheme = MaterialTheme.colorScheme

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

    val upNextTrack = nowPlaying.upNext.firstOrNull()
    val hasUpNext = upNextTrack != null &&
        nowPlaying.hasActiveSession &&
        nowPlaying.notificationAccessEnabled

    Card(
        onClick = onOpenMusicPlayer,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = scheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (nowPlaying.artworkModel != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(nowPlaying.artworkModel)
                                .crossfade(true)
                                .size(Size(384, 384))
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
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (!nowPlaying.notificationAccessEnabled) {
                            scheme.primary
                        } else {
                            scheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = !nowPlaying.notificationAccessEnabled) {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                    )
                }
            }

            if (nowPlaying.hasActiveSession && nowPlaying.notificationAccessEnabled) {
                if (hasUpNext) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.music_widget_up_next),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.primary,
                        )
                        Text(
                            text = upNextTrack?.title.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                val showProgress = nowPlaying.durationMs > 0L
                if (showProgress) {
                    val progress = (positionMs.toFloat() / nowPlaying.durationMs.toFloat())
                        .coerceIn(0f, 1f)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = scheme.primary,
                            trackColor = scheme.surfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = formatTrackDuration(positionMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                            Text(
                                text = formatTrackDuration(nowPlaying.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundMusicButton(
                    icon = Icons.Filled.SkipPrevious,
                    contentDescription = stringResource(R.string.music_widget_prev),
                    size = 52.dp,
                    iconSize = 36.dp,
                    onClick = onPrevious,
                    container = scheme.surfaceVariant,
                    content = scheme.onSurface,
                    enabled = nowPlaying.hasActiveSession && nowPlaying.notificationAccessEnabled,
                )
                RoundMusicButton(
                    icon = if (nowPlaying.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.music_widget_play_pause),
                    size = 72.dp,
                    iconSize = 48.dp,
                    onClick = onPlayPause,
                    container = scheme.primary,
                    content = scheme.onPrimary,
                    enabled = nowPlaying.notificationAccessEnabled,
                )
                RoundMusicButton(
                    icon = Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.music_widget_next),
                    size = 52.dp,
                    iconSize = 36.dp,
                    onClick = onNext,
                    container = scheme.surfaceVariant,
                    content = scheme.onSurface,
                    enabled = nowPlaying.hasActiveSession && nowPlaying.notificationAccessEnabled,
                )
            }
        }
    }
}

@Composable
private fun RoundMusicButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
) {
    val interaction = rememberSoftPressInteraction()
    Box(
        modifier = Modifier
            .size(size)
            .softPressScale(interaction)
            .clip(CircleShape)
            .background(if (enabled) container else container.copy(alpha = 0.45f))
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) content else content.copy(alpha = 0.6f),
            modifier = Modifier.size(iconSize),
        )
    }
}

