package com.karterlauncher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karterlauncher.MainActivity
import com.karterlauncher.LauncherViewModel
import com.karterlauncher.R
import com.karterlauncher.model.DockLocation
import com.karterlauncher.model.LaunchableApp
import com.karterlauncher.data.PhoneHubPermissions
import com.karterlauncher.ui.components.AppIcon
import com.karterlauncher.ui.theme.LauncherMotion
import com.karterlauncher.ui.theme.rememberSoftPressInteraction
import com.karterlauncher.ui.theme.softPressScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val SidebarWidth = 112.dp
private val SidebarOuterPad = 10.dp
private val MainPanelCorner = 32.dp
/** Car / at-a-glance: dock rail hit targets and glyph size */
private val RailIconOuterSize = 72.dp
private val RailIconInnerSize = 40.dp
private val CompactLayoutBreakpoint = 600.dp
private val CompactPanelCorner = 24.dp
/** App drawer: minimum column width → larger tiles on wide head units */
private val AppGridMinTileWidth = 152.dp
private val AppTileIconSize = 92.dp
private val AppTileMinHeight = 176.dp

@Composable
fun LauncherScreen(
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier,
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val dockMaps by viewModel.userPreferences.dockMapsPackageFlow.collectAsStateWithLifecycle(null)
    val dockMusic by viewModel.userPreferences.dockMusicPackageFlow.collectAsStateWithLifecycle(null)
    val dockPhone by viewModel.userPreferences.dockPhonePackageFlow.collectAsStateWithLifecycle(null)
    val dockLocation by viewModel.userPreferences.dockLocationFlow.collectAsStateWithLifecycle(
        initialValue = viewModel.startupSnapshot.dockLocation,
    )
    val passengerDualDock by viewModel.userPreferences.passengerDualDockEnabledFlow.collectAsStateWithLifecycle(
        initialValue = viewModel.startupSnapshot.passengerDualDockEnabled,
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.refreshApps()
                    viewModel.refreshNowPlaying()
                    viewModel.refreshBluetooth()
                    viewModel.startSpeedometer()
                }
                Lifecycle.Event.ON_PAUSE -> viewModel.stopSpeedometer()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopSpeedometer()
        }
    }

    var showAppList by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(showAppList) {
        if (showAppList) {
            viewModel.refreshApps()
        }
    }

    BackHandler(showAppList) {
        showAppList = false
    }

    val context = LocalContext.current
    val activity = context as? MainActivity
    SideEffect {
        activity?.setAppDrawerController(
            isOpen = showAppList,
            onClose = { showAppList = false },
        )
    }
    DisposableEffect(activity) {
        onDispose {
            activity?.setAppDrawerController(isOpen = false, onClose = null)
        }
    }
    val mapsDesc = remember(dockMaps) { dockContentDescription(context, dockMaps, R.string.dock_maps) }
    val musicDesc = remember(dockMusic) { dockContentDescription(context, dockMusic, R.string.dock_music) }
    val phoneDesc = remember(dockPhone) { dockContentDescription(context, dockPhone, R.string.dock_phone) }
    val phoneAvailable = remember(dockPhone) {
        dockPhone != null || PhoneHubPermissions.hasSystemDialer(context) || PhoneHubPermissions.hasSystemContactsApp(context)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val compact = maxWidth < CompactLayoutBreakpoint
        if (compact) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(
                            start = SidebarOuterPad,
                            top = SidebarOuterPad,
                            end = SidebarOuterPad,
                            bottom = 4.dp,
                        ),
                    shape = RoundedCornerShape(CompactPanelCorner),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    MainPanelContent(
                        showAppList = showAppList,
                        apps = apps,
                        viewModel = viewModel,
                        horizontalPadding = 8.dp,
                    )
                }
                CompactBottomDock(
                    dockMapsPackage = dockMaps,
                    dockMusicPackage = dockMusic,
                    dockPhonePackage = dockPhone,
                    phoneAvailable = phoneAvailable,
                    mapsContentDescription = mapsDesc,
                    musicContentDescription = musicDesc,
                    phoneContentDescription = phoneDesc,
                    onMaps = { viewModel.openMapsFromDock() },
                    onMusic = { viewModel.openMusicFromDock() },
                    onPhone = { viewModel.openPhoneFromDock() },
                    onSettings = { viewModel.openAppSettings() },
                    appListActive = showAppList,
                    onToggleAppList = { showAppList = !showAppList },
                )
            }
        } else {
            val showLeftDock = passengerDualDock || dockLocation == DockLocation.Left
            val showRightDock = passengerDualDock || dockLocation == DockLocation.Right
            val railModifier = Modifier
                .fillMaxHeight()
                .padding(
                    top = SidebarOuterPad,
                    bottom = SidebarOuterPad,
                )
            val dockProps = DockRailProps(
                dockMapsPackage = dockMaps,
                dockMusicPackage = dockMusic,
                dockPhonePackage = dockPhone,
                phoneAvailable = phoneAvailable,
                mapsContentDescription = mapsDesc,
                musicContentDescription = musicDesc,
                phoneContentDescription = phoneDesc,
                onMaps = { viewModel.openMapsFromDock() },
                onMusic = { viewModel.openMusicFromDock() },
                onPhone = { viewModel.openPhoneFromDock() },
                onSettings = { viewModel.openAppSettings() },
                appListActive = showAppList,
                onToggleAppList = { showAppList = !showAppList },
            )

            Row(modifier = Modifier.fillMaxSize()) {
                if (showLeftDock) {
                    PinnedAppRail(
                        modifier = railModifier.padding(start = SidebarOuterPad),
                        dockSide = DockLocation.Left,
                        props = dockProps,
                    )
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(
                            start = if (showLeftDock) 6.dp else SidebarOuterPad,
                            top = SidebarOuterPad,
                            end = if (showRightDock) 6.dp else SidebarOuterPad,
                            bottom = SidebarOuterPad,
                        ),
                    shape = RoundedCornerShape(MainPanelCorner),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    MainPanelContent(
                        showAppList = showAppList,
                        apps = apps,
                        viewModel = viewModel,
                        horizontalPadding = 8.dp,
                    )
                }

                if (showRightDock) {
                    PinnedAppRail(
                        modifier = railModifier.padding(end = SidebarOuterPad),
                        dockSide = DockLocation.Right,
                        props = dockProps,
                    )
                }
            }
        }
    }
}

@Composable
private fun MainPanelContent(
    showAppList: Boolean,
    apps: List<LaunchableApp>,
    viewModel: LauncherViewModel,
    horizontalPadding: Dp,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding),
    ) {
        TopStatusBar(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
        )
        AnimatedContent(
            targetState = showAppList,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            transitionSpec = { LauncherMotion.panelContentTransform() },
            label = "mainPanel",
        ) { showingApps ->
            if (showingApps) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = stringResource(R.string.apps_section),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        val minTile = AppGridMinTileWidth
                        val columns = max(2, (maxWidth / minTile).toInt())
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 16.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            items(
                                items = apps,
                                key = { it.componentName.flattenToString() },
                            ) { app ->
                                AppTile(
                                    app = app,
                                    onClick = { viewModel.openApp(app) },
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = tween(220),
                                        fadeOutSpec = tween(160),
                                    ),
                                )
                            }
                        }
                    }
                }
            } else {
                val dashboardScroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(dashboardScroll)
                        .padding(bottom = 12.dp),
                ) {
                    HomeDashboard(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactBottomDock(
    dockMapsPackage: String?,
    dockMusicPackage: String?,
    dockPhonePackage: String?,
    phoneAvailable: Boolean,
    mapsContentDescription: String,
    musicContentDescription: String,
    phoneContentDescription: String,
    onMaps: () -> Unit,
    onMusic: () -> Unit,
    onPhone: () -> Unit,
    onSettings: () -> Unit,
    appListActive: Boolean,
    onToggleAppList: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockRailSlot(
                customPackage = dockMapsPackage,
                defaultIcon = Icons.Filled.Map,
                contentDescription = mapsContentDescription,
                onClick = onMaps,
            )
            DockRailSlot(
                customPackage = dockMusicPackage,
                defaultIcon = Icons.Filled.MusicNote,
                contentDescription = musicContentDescription,
                onClick = onMusic,
            )
            if (phoneAvailable) {
                DockRailSlot(
                    customPackage = dockPhonePackage,
                    defaultIcon = Icons.Filled.Call,
                    contentDescription = phoneContentDescription,
                    onClick = onPhone,
                )
            }
            PinnedRailIcon(
                icon = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.dock_settings),
                onClick = onSettings,
            )
            PinnedRailIcon(
                icon = Icons.Filled.Apps,
                contentDescription = stringResource(R.string.rail_all_apps),
                onClick = onToggleAppList,
                selected = appListActive,
            )
        }
    }
}

private fun dockContentDescription(
    context: android.content.Context,
    packageName: String?,
    defaultLabelRes: Int,
): String {
    val base = context.getString(defaultLabelRes)
    if (packageName.isNullOrBlank()) return base
    val label = runCatching {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(packageName, 0),
        ).toString()
    }.getOrNull()
    return if (label != null) "$label. $base" else base
}

private data class DockRailProps(
    val dockMapsPackage: String?,
    val dockMusicPackage: String?,
    val dockPhonePackage: String?,
    val phoneAvailable: Boolean,
    val mapsContentDescription: String,
    val musicContentDescription: String,
    val phoneContentDescription: String,
    val onMaps: () -> Unit,
    val onMusic: () -> Unit,
    val onPhone: () -> Unit,
    val onSettings: () -> Unit,
    val appListActive: Boolean,
    val onToggleAppList: () -> Unit,
)

@Composable
private fun PinnedAppRail(
    props: DockRailProps,
    dockSide: DockLocation,
    modifier: Modifier = Modifier,
) {
    val shape =
        when (dockSide) {
            DockLocation.Left -> RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
            DockLocation.Right -> RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
        }
    Surface(
        modifier = modifier.width(SidebarWidth),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 0.dp,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 16.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                DockRailSlot(
                    customPackage = props.dockMapsPackage,
                    defaultIcon = Icons.Filled.Map,
                    contentDescription = props.mapsContentDescription,
                    onClick = props.onMaps,
                )
                DockRailSlot(
                    customPackage = props.dockMusicPackage,
                    defaultIcon = Icons.Filled.MusicNote,
                    contentDescription = props.musicContentDescription,
                    onClick = props.onMusic,
                )
                if (props.phoneAvailable) {
                    DockRailSlot(
                        customPackage = props.dockPhonePackage,
                        defaultIcon = Icons.Filled.Call,
                        contentDescription = props.phoneContentDescription,
                        onClick = props.onPhone,
                    )
                }
                PinnedRailIcon(
                    icon = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.dock_settings),
                    onClick = props.onSettings,
                )
            }
            Spacer(Modifier.weight(1f))
            PinnedRailIcon(
                icon = Icons.Filled.Apps,
                contentDescription = stringResource(R.string.rail_all_apps),
                onClick = props.onToggleAppList,
                selected = props.appListActive,
            )
        }
    }
}

@Composable
private fun DockRailSlot(
    customPackage: String?,
    defaultIcon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val interaction = rememberSoftPressInteraction()
    val bg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val slotModifier =
        if (customPackage != null) {
            Modifier.semantics { this.contentDescription = contentDescription }
        } else {
            Modifier
        }
    Box(
        modifier = Modifier
            .size(RailIconOuterSize)
            .then(slotModifier)
            .softPressScale(interaction)
            .clip(CircleShape)
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (customPackage != null) {
            AppIcon(
                packageName = customPackage,
                modifier = Modifier
                    .size(RailIconOuterSize - 14.dp)
                    .clip(CircleShape),
            )
        } else {
            Icon(
                imageVector = defaultIcon,
                contentDescription = contentDescription,
                modifier = Modifier.size(RailIconInnerSize),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PinnedRailIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    val interaction = rememberSoftPressInteraction()
    val targetBg =
        if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        }
    val bg by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(LauncherMotion.ColorFadeMs),
        label = "railIconBg",
    )
    Box(
        modifier = Modifier
            .size(RailIconOuterSize)
            .softPressScale(interaction)
            .clip(CircleShape)
            .background(bg)
            .clickable(
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
            modifier = Modifier.size(RailIconInnerSize),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TopStatusBar(modifier: Modifier = Modifier) {
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var tick by remember { mutableIntStateOf(0) }
    val hasInternet = rememberHasValidatedInternet()
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(30_000L)
            tick++
        }
    }
    val timeText = remember(tick, formatter) {
        LocalTime.now().format(formatter)
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = timeText,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        AnimatedVisibility(
            visible = !hasInternet,
            enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.9f, animationSpec = tween(220)),
            exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.92f, animationSpec = tween(160)),
        ) {
            Row {
                Spacer(Modifier.width(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ) {
                    Text(
                        text = stringResource(R.string.status_offline_badge),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppTile(
    app: LaunchableApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = rememberSoftPressInteraction()
    Card(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AppTileMinHeight)
            .softPressScale(interaction, pressedScale = 0.97f),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            AppIcon(
                packageName = app.packageName,
                modifier = Modifier.size(AppTileIconSize),
            )
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
