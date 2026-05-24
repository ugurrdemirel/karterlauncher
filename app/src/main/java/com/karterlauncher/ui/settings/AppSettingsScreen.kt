package com.karterlauncher.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karterlauncher.LauncherViewModel
import com.karterlauncher.HiddenAppsActivity
import com.karterlauncher.PrivacyActivity
import com.karterlauncher.R
import com.karterlauncher.model.LaunchableApp
import com.karterlauncher.model.DockLocation
import com.karterlauncher.model.ThemeMode
import com.karterlauncher.ui.components.AppIcon
import com.karterlauncher.ui.onboarding.isOurAppDefaultHome
import com.karterlauncher.ui.onboarding.launchDefaultHomePicker
import com.karterlauncher.util.isNotificationListenerEnabled
import kotlinx.coroutines.launch

private enum class DockPickSlot { Maps, Music, Phone }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    viewModel: LauncherViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val prefs = viewModel.userPreferences
    val lifecycleOwner = LocalLifecycleOwner.current

    val themeMode by prefs.themeModeFlow.collectAsStateWithLifecycle(ThemeMode.System)
    val dockMaps by prefs.dockMapsPackageFlow.collectAsStateWithLifecycle(null)
    val dockMusic by prefs.dockMusicPackageFlow.collectAsStateWithLifecycle(null)
    val dockPhone by prefs.dockPhonePackageFlow.collectAsStateWithLifecycle(null)
    val dockLocation by prefs.dockLocationFlow.collectAsStateWithLifecycle(DockLocation.Left)
    val passengerDualDock by prefs.passengerDualDockEnabledFlow.collectAsStateWithLifecycle(false)
    val seatbeltReminderEnabled by prefs.seatbeltReminderEnabledFlow.collectAsStateWithLifecycle(
        initialValue = false,
    )
    val internetTtsEnabled by prefs.internetTtsEnabledFlow.collectAsStateWithLifecycle(
        initialValue = false,
    )
    val hiddenPackages by prefs.hiddenAppPackagesFlow.collectAsStateWithLifecycle(
        initialValue = emptySet(),
    )
    val speedHeatColorsEnabled by prefs.speedHeatColorsEnabledFlow.collectAsStateWithLifecycle(
        initialValue = true,
    )
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val hiddenCount = hiddenPackages.size

    var isDefaultHome by remember { mutableStateOf(context.isOurAppDefaultHome()) }
    var locationTick by remember { mutableIntStateOf(0) }
    var notificationAccessTick by remember { mutableIntStateOf(0) }
    val notificationListenerEnabled = remember(notificationAccessTick) {
        isNotificationListenerEnabled(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefaultHome = context.isOurAppDefaultHome()
                locationTick++
                notificationAccessTick++
                viewModel.refreshApps()
                viewModel.refreshNowPlaying()
                viewModel.refreshBluetooth()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { locationTick++ }

    var pickingSlot by remember { mutableStateOf<DockPickSlot?>(null) }
    var showReplayOnboardingDialog by remember { mutableStateOf(false) }

    val locationGranted = remember(locationTick) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.app_settings_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                SettingsSectionTitle(stringResource(R.string.app_settings_section_theme))
                Spacer(Modifier.height(8.dp))
                SettingsGroup {
                    ThemeSegmentedRow(
                        themeMode = themeMode,
                        onSelect = { mode -> scope.launch { prefs.setThemeMode(mode) } },
                    )
                }
            }

            item {
                SettingsSectionTitle(stringResource(R.string.app_settings_section_location))
                Spacer(Modifier.height(8.dp))
                SettingsGroup {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.onboarding_step_location_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = if (locationGranted) {
                                    stringResource(R.string.onboarding_step_location_granted)
                                } else {
                                    stringResource(R.string.onboarding_step_location_denied)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.onboarding_step_location_cta)) },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.clickable {
                            locationLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.app_settings_app_permissions)) },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.clickable {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            )
                            context.startActivity(intent)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            item {
                SettingsSectionTitle(stringResource(R.string.app_settings_section_driving))
                Text(
                    text = stringResource(R.string.app_settings_driving_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 8.dp),
                )
                SettingsGroup {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.app_settings_seatbelt_tts_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.app_settings_seatbelt_tts_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Campaign,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = seatbeltReminderEnabled,
                                onCheckedChange = { on ->
                                    scope.launch { prefs.setSeatbeltReminderEnabled(on) }
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.app_settings_internet_tts_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.app_settings_internet_tts_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Campaign,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = internetTtsEnabled,
                                onCheckedChange = { on ->
                                    scope.launch { prefs.setInternetTtsEnabled(on) }
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.app_settings_speed_heat_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.app_settings_speed_heat_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = speedHeatColorsEnabled,
                                onCheckedChange = { on ->
                                    scope.launch { prefs.setSpeedHeatColorsEnabled(on) }
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            item {
                SettingsSectionTitle(stringResource(R.string.app_settings_section_home))
                if (isDefaultHome) {
                    Text(
                        text = stringResource(R.string.onboarding_step_home_change_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 8.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                SettingsGroup {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.onboarding_step_home_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = if (isDefaultHome) {
                                    stringResource(R.string.onboarding_step_home_status_ok)
                                } else {
                                    stringResource(R.string.onboarding_step_home_status_pending)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Home,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(
                                    if (isDefaultHome) {
                                        R.string.onboarding_step_home_cta_change
                                    } else {
                                        R.string.onboarding_step_home_cta
                                    },
                                ),
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Home,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.clickable {
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
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            item {
                SettingsSectionTitle(stringResource(R.string.app_settings_section_media))
                Text(
                    text = stringResource(R.string.app_settings_media_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 8.dp),
                )
                SettingsGroup {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.music_widget_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = if (notificationListenerEnabled) {
                                    stringResource(R.string.app_settings_media_status_on)
                                } else {
                                    stringResource(R.string.app_settings_media_status_off)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.app_settings_media_open_listener))
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.clickable {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            item {
                SettingsSectionTitle(stringResource(R.string.app_settings_section_apps))
                Spacer(Modifier.height(8.dp))
                SettingsGroup {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.app_settings_manage_hidden_apps),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = if (hiddenCount > 0) {
                                    stringResource(
                                        R.string.app_settings_manage_hidden_apps_summary_count,
                                        hiddenCount,
                                    )
                                } else {
                                    stringResource(R.string.app_settings_manage_hidden_apps_summary)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.VisibilityOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(context, HiddenAppsActivity::class.java))
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            item {
                SettingsSectionTitle(stringResource(R.string.app_settings_section_dock))
                Text(
                    text = stringResource(R.string.app_settings_dock_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 8.dp),
                )
                SettingsGroup {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.app_settings_dock_location_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.app_settings_dock_location_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    DockLocationSegmentedRow(
                        dockLocation = dockLocation,
                        enabled = !passengerDualDock,
                        onSelect = { location -> scope.launch { prefs.setDockLocation(location) } },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.app_settings_passenger_dual_dock_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.app_settings_passenger_dual_dock_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = passengerDualDock,
                                onCheckedChange = { on ->
                                    scope.launch { prefs.setPassengerDualDockEnabled(on) }
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    DockShortcutListItem(
                        title = stringResource(R.string.dock_maps),
                        packageName = dockMaps,
                        onChoose = { pickingSlot = DockPickSlot.Maps },
                        onClear = { scope.launch { prefs.setDockMapsPackage(null) } },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    DockShortcutListItem(
                        title = stringResource(R.string.dock_music),
                        packageName = dockMusic,
                        onChoose = { pickingSlot = DockPickSlot.Music },
                        onClear = { scope.launch { prefs.setDockMusicPackage(null) } },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    DockShortcutListItem(
                        title = stringResource(R.string.dock_phone),
                        packageName = dockPhone,
                        onChoose = { pickingSlot = DockPickSlot.Phone },
                        onClear = { scope.launch { prefs.setDockPhonePackage(null) } },
                    )
                }
            }

            item {
                SettingsSectionTitle(stringResource(R.string.app_settings_section_privacy))
                Spacer(Modifier.height(8.dp))
                SettingsGroup {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.app_settings_open_privacy)) },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(context, PrivacyActivity::class.java))
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            item {
                SettingsSectionTitle(stringResource(R.string.app_settings_replay_onboarding_title))
                Spacer(Modifier.height(8.dp))
                SettingsGroup {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.app_settings_replay_onboarding_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(R.string.app_settings_replay_onboarding_summary),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Replay,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.clickable { showReplayOnboardingDialog = true },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showReplayOnboardingDialog) {
        AlertDialog(
            onDismissRequest = { showReplayOnboardingDialog = false },
            title = { Text(stringResource(R.string.app_settings_replay_onboarding_confirm)) },
            text = { Text(stringResource(R.string.app_settings_replay_onboarding_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReplayOnboardingDialog = false
                        scope.launch {
                            prefs.resetOnboarding()
                            activity.finish()
                        }
                    },
                ) {
                    Text(stringResource(R.string.app_settings_replay_onboarding_run))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReplayOnboardingDialog = false }) {
                    Text(stringResource(R.string.app_settings_cancel))
                }
            },
        )
    }

    if (pickingSlot != null) {
        AppPickerDialog(
            apps = apps,
            onDismiss = { pickingSlot = null },
            onPick = { app ->
                scope.launch {
                    when (pickingSlot) {
                        DockPickSlot.Maps -> prefs.setDockMapsPackage(app.packageName)
                        DockPickSlot.Music -> prefs.setDockMusicPackage(app.packageName)
                        DockPickSlot.Phone -> prefs.setDockPhonePackage(app.packageName)
                        null -> Unit
                    }
                    pickingSlot = null
                }
            },
        )
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DockLocationSegmentedRow(
    dockLocation: DockLocation,
    enabled: Boolean,
    onSelect: (DockLocation) -> Unit,
) {
    val options = listOf(DockLocation.Left, DockLocation.Right)
    val selectedIndex = options.indexOf(dockLocation).coerceAtLeast(0)
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        options.forEachIndexed { index, location ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                onClick = { onSelect(location) },
                selected = index == selectedIndex,
                enabled = enabled,
            ) {
                Text(
                    text = when (location) {
                        DockLocation.Left -> stringResource(R.string.app_settings_dock_location_left)
                        DockLocation.Right -> stringResource(R.string.app_settings_dock_location_right)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ThemeSegmentedRow(
    themeMode: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val options = listOf(ThemeMode.Light, ThemeMode.Dark, ThemeMode.System)
    val selectedIndex = options.indexOf(themeMode).coerceAtLeast(0)
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        options.forEachIndexed { index, mode ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                onClick = { onSelect(mode) },
                selected = index == selectedIndex,
            ) {
                Text(
                    text = when (mode) {
                        ThemeMode.Light -> stringResource(R.string.onboarding_theme_light)
                        ThemeMode.Dark -> stringResource(R.string.onboarding_theme_dark)
                        ThemeMode.System -> stringResource(R.string.onboarding_theme_system)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun DockShortcutListItem(
    title: String,
    packageName: String?,
    onChoose: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val label = remember(packageName) {
        if (packageName.isNullOrBlank()) null
        else runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0),
            ).toString()
        }.getOrNull()
    }
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Text(
                text = label ?: stringResource(R.string.app_settings_dock_default),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            if (packageName.isNullOrBlank()) {
                Icon(
                    Icons.Outlined.Widgets,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                AppIcon(packageName, Modifier.size(40.dp))
            }
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilledTonalIconButton(onClick = onChoose) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.app_settings_dock_choose_app),
                    )
                }
                if (packageName != null) {
                    IconButton(onClick = onClear) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.app_settings_dock_reset),
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun AppPickerDialog(
    apps: List<LaunchableApp>,
    onDismiss: () -> Unit,
    onPick: (LaunchableApp) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_settings_pick_app_title)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(apps, key = { it.componentName.flattenToString() }) { app ->
                    ListItem(
                        headlineContent = {
                            Text(
                                app.label,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingContent = { AppIcon(app.packageName, Modifier.size(40.dp)) },
                        modifier = Modifier.clickable { onPick(app) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.app_settings_cancel))
            }
        },
    )
}
