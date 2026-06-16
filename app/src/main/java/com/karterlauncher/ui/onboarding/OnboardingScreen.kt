package com.karterlauncher.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.karterlauncher.R
import com.karterlauncher.data.LocationRepository
import com.karterlauncher.data.UserPreferencesRepository
import com.karterlauncher.util.isNotificationListenerEnabled
import com.karterlauncher.model.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    userPreferencesRepository: UserPreferencesRepository,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val pagerState = rememberPagerState(pageCount = { 5 })
    val configuration = LocalConfiguration.current
    val narrowControls = configuration.screenWidthDp < 360

    var isDefaultHome by remember { mutableStateOf(context.isOurAppDefaultHome()) }
    BackHandler(pagerState.currentPage == 0 && isDefaultHome) {
        // Consume back on the first step while we are HOME (MainActivity must not finish).
    }
    BackHandler(pagerState.currentPage > 0) {
        scope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage - 1)
        }
    }
    var locationTick by remember { mutableIntStateOf(0) }
    val locationRepository = remember { LocationRepository(context) }
    val locationGranted = remember(locationTick) {
        locationRepository.hasLocationPermission()
    }
    var notificationListenerEnabled by remember {
        mutableStateOf(isNotificationListenerEnabled(context))
    }
    var bluetoothPermTick by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefaultHome = context.isOurAppDefaultHome()
                locationTick++
                bluetoothPermTick++
                notificationListenerEnabled = isNotificationListenerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { locationTick++ }

    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { bluetoothPermTick++ }

    var selectedTheme by remember { mutableStateOf(ThemeMode.System) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(5) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (pagerState.currentPage == index) 10.dp else 8.dp)
                            .background(
                                color = if (pagerState.currentPage == index) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                },
                                shape = CircleShape,
                            ),
                    )
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) { page ->
            val pageScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(pageScroll)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 8.dp),
            ) {
                when (page) {
                    0 -> OnboardingHomeStep(
                        isDefaultHome = isDefaultHome,
                        onOpenHomeSettings = {
                            if (!launchDefaultHomePicker(activity)) {
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.onboarding_step_home_open_failed,
                                        context.getString(R.string.app_name),
                                    ),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                    )
                    1 -> OnboardingLocationStep(
                        locationGranted = locationGranted,
                        onRequestLocation = {
                            locationLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        },
                    )
                    2 -> OnboardingNotificationStep(
                        listenerEnabled = notificationListenerEnabled,
                        onOpenNotificationSettings = {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                            )
                        },
                    )
                    3 -> {
                        val bluetoothGranted = remember(bluetoothPermTick) {
                            onboardingBluetoothGranted(context)
                        }
                        OnboardingBluetoothStep(
                            bluetoothGranted = bluetoothGranted,
                            needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                            onRequestBluetooth = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                }
                            },
                        )
                    }
                    4 -> OnboardingThemeStep(
                        selected = selectedTheme,
                        onSelected = { selectedTheme = it },
                    )
                    else -> Unit
                }
            }
        }

        Surface(
            tonalElevation = 2.dp,
            shadowElevation = 0.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (narrowControls) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (pagerState.currentPage > 0) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.onboarding_back))
                            }
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        if (pagerState.currentPage < 4) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                },
                                enabled = when (pagerState.currentPage) {
                                    0 -> isDefaultHome
                                    else -> true
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.onboarding_next))
                            }
                        } else {
                            Button(
                                onClick = {
                                    scope.launch {
                                        userPreferencesRepository.setThemeMode(selectedTheme)
                                        userPreferencesRepository.setOnboardingComplete()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.onboarding_finish))
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (pagerState.currentPage > 0) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.onboarding_back))
                            }
                        } else {
                            Spacer(Modifier.size(1.dp))
                        }

                        if (pagerState.currentPage < 4) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                },
                                enabled = when (pagerState.currentPage) {
                                    0 -> isDefaultHome
                                    else -> true
                                },
                            ) {
                                Text(stringResource(R.string.onboarding_next))
                            }
                        } else {
                            Button(
                                onClick = {
                                    scope.launch {
                                        userPreferencesRepository.setThemeMode(selectedTheme)
                                        userPreferencesRepository.setOnboardingComplete()
                                    }
                                },
                            ) {
                                Text(stringResource(R.string.onboarding_finish))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun onboardingBluetoothGranted(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_CONNECT,
    ) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun OnboardingHomeStep(
    isDefaultHome: Boolean,
    onOpenHomeSettings: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Home,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(R.string.onboarding_step_home_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.onboarding_step_home_body, stringResource(R.string.app_name)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onOpenHomeSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (isDefaultHome) {
                        R.string.onboarding_step_home_cta_change
                    } else {
                        R.string.onboarding_step_home_cta
                    },
                ),
            )
        }
        if (isDefaultHome) {
            Text(
                text = stringResource(R.string.onboarding_step_home_change_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (isDefaultHome) {
                stringResource(R.string.onboarding_step_home_status_ok)
            } else {
                stringResource(R.string.onboarding_step_home_status_pending)
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (isDefaultHome) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun OnboardingLocationStep(
    locationGranted: Boolean,
    onRequestLocation: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(R.string.onboarding_step_location_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.onboarding_step_location_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onRequestLocation,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_step_location_cta))
        }
        Text(
            text = if (locationGranted) {
                stringResource(R.string.onboarding_step_location_granted)
            } else {
                stringResource(R.string.onboarding_step_location_denied)
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (locationGranted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun OnboardingNotificationStep(
    listenerEnabled: Boolean,
    onOpenNotificationSettings: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Notifications,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(R.string.onboarding_step_notification_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(
                R.string.onboarding_step_notification_body,
                stringResource(R.string.app_name),
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onOpenNotificationSettings,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_step_notification_cta))
        }
        Text(
            text = if (listenerEnabled) {
                stringResource(R.string.onboarding_step_notification_status_ok)
            } else {
                stringResource(
                    R.string.onboarding_step_notification_status_pending,
                    stringResource(R.string.app_name),
                )
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (listenerEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun OnboardingBluetoothStep(
    bluetoothGranted: Boolean,
    needsRuntimePermission: Boolean,
    onRequestBluetooth: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Bluetooth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(R.string.onboarding_step_bluetooth_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.onboarding_step_bluetooth_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (needsRuntimePermission) {
            OutlinedButton(
                onClick = onRequestBluetooth,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboarding_step_bluetooth_cta))
            }
        } else {
            Text(
                text = stringResource(R.string.onboarding_step_bluetooth_skip_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (bluetoothGranted) {
                stringResource(R.string.onboarding_step_bluetooth_granted)
            } else {
                stringResource(R.string.onboarding_step_bluetooth_denied)
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (bluetoothGranted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun OnboardingThemeStep(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
) {
    val chipScroll = rememberScrollState()
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Palette,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(R.string.onboarding_step_theme_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.onboarding_step_theme_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(chipScroll),
        ) {
            FilterChip(
                selected = selected == ThemeMode.Light,
                onClick = { onSelected(ThemeMode.Light) },
                label = { Text(stringResource(R.string.onboarding_theme_light)) },
                modifier = Modifier.widthIn(min = 88.dp),
            )
            FilterChip(
                selected = selected == ThemeMode.Dark,
                onClick = { onSelected(ThemeMode.Dark) },
                label = { Text(stringResource(R.string.onboarding_theme_dark)) },
                modifier = Modifier.widthIn(min = 88.dp),
            )
            FilterChip(
                selected = selected == ThemeMode.System,
                onClick = { onSelected(ThemeMode.System) },
                label = { Text(stringResource(R.string.onboarding_theme_system)) },
                modifier = Modifier.widthIn(min = 120.dp),
            )
        }
    }
}
