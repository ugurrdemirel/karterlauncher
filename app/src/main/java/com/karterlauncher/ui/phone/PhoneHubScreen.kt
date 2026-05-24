package com.karterlauncher.ui.phone

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.PhoneForwarded
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.karterlauncher.R
import com.karterlauncher.data.BluetoothCallRouter
import com.karterlauncher.data.ContactPhoneRow
import com.karterlauncher.data.PhoneContentLoader
import com.karterlauncher.data.PhoneHubPermissions
import com.karterlauncher.data.RecentCallRow
import com.karterlauncher.ui.theme.LauncherMotion
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneHubScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val loader = remember { PhoneContentLoader(context.applicationContext) }
    val callRouter = remember { BluetoothCallRouter(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var gateTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) gateTick++
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { gateTick++ }

    var selectedTab by remember { mutableIntStateOf(0) }
    var recentCalls by remember { mutableStateOf<List<RecentCallRow>>(emptyList()) }
    var contacts by remember { mutableStateOf<List<ContactPhoneRow>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var dialNumber by remember { mutableStateOf("") }
    var btPhoneLink by remember { mutableStateOf<BluetoothCallRouter.BluetoothPhoneLink?>(null) }

    val hubAccessible = remember(gateTick) { PhoneHubPermissions.canAccessPhoneHub(context) }
    val listDataOk = remember(gateTick) { PhoneHubPermissions.canListContactsAndRecents(context) }
    val btOnlyMode = hubAccessible && !listDataOk

    LaunchedEffect(btOnlyMode) {
        if (btOnlyMode) selectedTab = 2
    }

    LaunchedEffect(hubAccessible, listDataOk, gateTick) {
        if (!hubAccessible) return@LaunchedEffect
        btPhoneLink = callRouter.queryPhoneLink()
        if (!listDataOk) return@LaunchedEffect
        recentCalls = loader.loadRecentCalls()
        contacts = loader.loadContactsWithPhones()
    }

    fun placeCallFromHub(number: String) {
        scope.launch {
            when (val result = callRouter.placeCall(number)) {
                is BluetoothCallRouter.OutgoingCallResult.Failed -> {
                    snackbarHostState.showSnackbar(outgoingCallErrorMessage(context, result.reason))
                }
                BluetoothCallRouter.OutgoingCallResult.RoutedBluetooth,
                BluetoothCallRouter.OutgoingCallResult.RoutedSystemDialer,
                -> Unit
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.phone_hub_title))
                        btPhoneLink?.let { link ->
                            Text(
                                text = stringResource(
                                    R.string.phone_hub_bt_linked,
                                    link.deviceName,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onFinish) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.phone_hub_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (hubAccessible) {
                NavigationBar {
                    if (!btOnlyMode) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = {
                                Icon(Icons.AutoMirrored.Filled.PhoneForwarded, contentDescription = null)
                            },
                            label = { Text(stringResource(R.string.phone_hub_tab_recents)) },
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                            label = { Text(stringResource(R.string.phone_hub_tab_contacts)) },
                        )
                    }
                    NavigationBarItem(
                        selected = selectedTab == 2 || btOnlyMode,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Filled.Dialpad, contentDescription = null) },
                        label = { Text(stringResource(R.string.phone_hub_tab_dialpad)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        if (!hubAccessible) {
            PhonePermissionGate(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                onRequestPermissions = {
                    permissionLauncher.launch(PhoneHubPermissions.REQUIRED)
                },
                onOpenAppSettings = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    runCatching { context.startActivity(intent) }
                },
            )
        } else {
            val tabKey = when {
                btOnlyMode || selectedTab == 2 -> 2
                selectedTab == 0 -> 0
                else -> 1
            }
            AnimatedContent(
                targetState = tabKey,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { LauncherMotion.tabContentTransform() },
                label = "phoneHubTab",
            ) { tab ->
            when (tab) {
                2 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    if (btOnlyMode) {
                        Text(
                            text = stringResource(R.string.phone_hub_bt_only_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    PhoneDialPadScreen(
                        number = dialNumber,
                        onNumberChange = { dialNumber = it },
                        onPlaceCall = { placeCallFromHub(it) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                0 -> RecentCallsList(
                    calls = recentCalls,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    onPlaceCall = { placeCallFromHub(it) },
                    onOpenDialer = { openDialer(context, it) },
                )

                else -> {
                    val filtered = remember(contacts, searchQuery) {
                        val q = searchQuery.trim()
                        if (q.isEmpty()) contacts
                        else contacts.filter {
                            it.displayName.contains(q, ignoreCase = true) ||
                                it.number.contains(q, ignoreCase = true)
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            placeholder = { Text(stringResource(R.string.phone_hub_search_hint)) },
                            leadingIcon = {
                                Icon(Icons.Filled.Search, contentDescription = null)
                            },
                            singleLine = true,
                        )
                        ContactsList(
                            contacts = filtered,
                            emptyHintRes = if (searchQuery.isBlank()) {
                                R.string.phone_hub_contacts_empty
                            } else {
                                R.string.phone_hub_search_empty
                            },
                            modifier = Modifier.weight(1f),
                            onPlaceCall = { placeCallFromHub(it) },
                            onOpenDialer = { openDialer(context, it) },
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun PhonePermissionGate(
    onRequestPermissions: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.phone_hub_permission_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.phone_hub_permission_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRequestPermissions, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.phone_hub_permission_allow))
        }
        OutlinedButton(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.phone_hub_permission_app_settings))
        }
        Text(
            text = stringResource(R.string.phone_hub_permission_fallback_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecentCallsList(
    calls: List<RecentCallRow>,
    onPlaceCall: (String) -> Unit,
    onOpenDialer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (calls.isEmpty()) {
        EmptyHint(
            text = stringResource(R.string.phone_hub_recents_empty),
            modifier = modifier.padding(24.dp),
        )
        return
    }
    val formatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        items(calls, key = { "${it.dateMillis}_${it.number}_${it.type}" }) { row ->
            val title = row.cachedName?.takeIf { it.isNotBlank() } ?: row.number
            val typeLabel =
                callTypeIconLabel(row.type)
            val subtitle =
                "${row.number} • ${formatter.format(Instant.ofEpochMilli(row.dateMillis))} • $typeLabel"

            ListItem(
                headlineContent = {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                },
                trailingContent = {
                    CallAndDialTrailing(
                        number = row.number,
                        onPlaceCall = onPlaceCall,
                        onOpenDialer = onOpenDialer,
                    )
                },
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun callTypeIconLabel(type: Int): String {
    return when (type) {
        CallLog.Calls.INCOMING_TYPE -> stringResource(R.string.phone_hub_call_incoming)
        CallLog.Calls.OUTGOING_TYPE -> stringResource(R.string.phone_hub_call_outgoing)
        CallLog.Calls.MISSED_TYPE -> stringResource(R.string.phone_hub_call_missed)
        CallLog.Calls.REJECTED_TYPE -> stringResource(R.string.phone_hub_call_rejected)
        CallLog.Calls.VOICEMAIL_TYPE -> stringResource(R.string.phone_hub_call_voicemail)
        else -> stringResource(R.string.phone_hub_call_other)
    }
}

@Composable
private fun ContactsList(
    contacts: List<ContactPhoneRow>,
    emptyHintRes: Int,
    onPlaceCall: (String) -> Unit,
    onOpenDialer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (contacts.isEmpty()) {
        EmptyHint(
            text = stringResource(emptyHintRes),
            modifier = modifier.padding(24.dp),
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        items(contacts, key = { "${it.displayName}_${it.number}" }) { row ->
            ListItem(
                headlineContent = {
                    Text(row.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(row.number, maxLines = 1)
                },
                trailingContent = {
                    CallAndDialTrailing(
                        number = row.number,
                        onPlaceCall = onPlaceCall,
                        onOpenDialer = onOpenDialer,
                    )
                },
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun CallAndDialTrailing(
    number: String,
    onPlaceCall: (String) -> Unit,
    onOpenDialer: (String) -> Unit,
) {
    Row {
        IconButton(onClick = { onPlaceCall(number) }) {
            Icon(
                Icons.Filled.Call,
                contentDescription = stringResource(R.string.phone_hub_a11y_place_call),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = { onOpenDialer(number) }) {
            Icon(
                Icons.Filled.Dialpad,
                contentDescription = stringResource(R.string.phone_hub_a11y_open_dialer),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun openDialer(context: android.content.Context, rawNumber: String) {
    val intent = Intent(Intent.ACTION_DIAL).apply {
        data = Uri.parse("tel:${Uri.encode(rawNumber.trim())}")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // head unit edge case — sessiz düşür
    }
}
