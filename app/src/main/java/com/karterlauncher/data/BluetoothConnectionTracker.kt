package com.karterlauncher.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class BluetoothDashboardState(
    /** Adapter off / missing */
    val adapterDisabled: Boolean,
    /** Needs [Manifest.permission.BLUETOOTH_CONNECT] on API 31+ */
    val needsConnectPermission: Boolean,
    /** Unique friendly names (deduped across audio / phone BT profiles). */
    val connectedNames: List<String>,
) {
    val hasConnectedDevices: Boolean get() = connectedNames.isNotEmpty()
}

class BluetoothConnectionTracker(
    private val app: Application,
) {
    companion object {
        /**
         * [BluetoothProfile.A2DP_SINK] and [BluetoothProfile.HEADSET_CLIENT] are @hide in android.jar
         * but supported by [BluetoothAdapter.getProfileProxy]; on car head units the paired phone is
         * often visible only on these roles (sink/HF client), not on [BluetoothProfile.A2DP]/[HEADSET].
         */
        private const val BT_PROFILE_A2DP_SINK = 11
        private const val BT_PROFILE_HEADSET_CLIENT = 16
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var refreshJob: Job? = null

    private val _state = MutableStateFlow(
        BluetoothDashboardState(
            adapterDisabled = true,
            needsConnectPermission = false,
            connectedNames = emptyList(),
        ),
    )
    val state: StateFlow<BluetoothDashboardState> = _state.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED,
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                BluetoothDevice.ACTION_BOND_STATE_CHANGED,
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED,
                -> scheduleRefresh()
            }
        }
    }

    private val filter = IntentFilter().apply {
        addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
    }

    private var receiverRegistered = false

    fun start() {
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        scheduleRefresh()
    }

    fun refresh() {
        scheduleRefresh()
    }

    fun stop() {
        refreshJob?.cancel()
        refreshJob = null
        if (receiverRegistered) {
            runCatching { app.unregisterReceiver(receiver) }
            receiverRegistered = false
        }
        _state.value = BluetoothDashboardState(adapterDisabled = true, needsConnectPermission = false, emptyList())
    }

    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            mutex.withLock {
                computeState()
            }
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission") // Checked via permission / FEATURE_BLUETOOTH
    private suspend fun computeState() {
        val needsPermission =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothConnectPermission()

        if (!app.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            _state.value = BluetoothDashboardState(adapterDisabled = true, needsPermission, emptyList())
            return
        }

        val manager = app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (adapter == null) {
            _state.value = BluetoothDashboardState(adapterDisabled = true, needsPermission, emptyList())
            return
        }

        if (!adapter.isEnabled) {
            _state.value = BluetoothDashboardState(adapterDisabled = true, needsPermission, emptyList())
            return
        }

        if (needsPermission) {
            _state.value = BluetoothDashboardState(adapterDisabled = false, needsPermission, emptyList())
            return
        }

        val connected = gatherConnectedDevices(adapter)

        val byAddr = LinkedHashMap<String, String>()
        connected.distinctBy { it.address }.forEach { device ->
            val name = friendlyName(device) ?: return@forEach
            byAddr[device.address] = name
        }

        _state.value = BluetoothDashboardState(
            adapterDisabled = false,
            needsConnectPermission = false,
            connectedNames = byAddr.values.toList(),
        )
    }

    /**
     * Profiles that may report an active link. Order favors car head units (client/sink before AG/source).
     */
    private fun profilesToScan(): List<Int> = buildList {
        add(BT_PROFILE_HEADSET_CLIENT)
        add(BT_PROFILE_A2DP_SINK)
        add(BluetoothProfile.HEADSET)
        add(BluetoothProfile.A2DP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(BluetoothProfile.HEARING_AID)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(BluetoothProfile.LE_AUDIO)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun gatherConnectedDevices(adapter: BluetoothAdapter): List<BluetoothDevice> {
        val profiles = profilesToScan()
        val merged = ArrayList<BluetoothDevice>()
        profiles.forEachIndexed { index, profile ->
            merged += queryConnected(adapter, profile)
            if (index < profiles.lastIndex) {
                delay(100)
            }
        }
        return merged
    }

    @SuppressLint("MissingPermission")
    private suspend fun queryConnected(adapter: BluetoothAdapter, profile: Int): List<BluetoothDevice> {
        return withTimeoutOrNull(4_200L) {
            suspendCancellableCoroutine { cont ->
                val listener = object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profileId: Int, proxy: BluetoothProfile) {
                        if (profileId != profile || !cont.isActive) {
                            tryClose(adapter, profileId, proxy)
                            return
                        }
                        val devices = try {
                            proxy.connectedDevices
                        } catch (_: SecurityException) {
                            emptyList()
                        }
                        tryClose(adapter, profileId, proxy)
                        cont.resume(devices)
                    }

                    override fun onServiceDisconnected(profileId: Int) {
                        //
                    }
                }
                val ok = try {
                    adapter.getProfileProxy(app, listener, profile)
                } catch (_: SecurityException) {
                    false
                } catch (_: IllegalArgumentException) {
                    false
                }
                if (!ok) cont.resume(emptyList())
            }
        } ?: emptyList()
    }

    private fun tryClose(adapter: BluetoothAdapter, profileId: Int, proxy: BluetoothProfile?) {
        if (proxy != null) {
            runCatching { adapter.closeProfileProxy(profileId, proxy) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun friendlyName(device: BluetoothDevice): String? {
        val primary = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                device.alias?.takeIf { it.isNotBlank() } ?: device.name
            } else {
                device.name
            }
        } catch (_: SecurityException) {
            null
        }
        val n = primary?.trim()?.takeIf { it.isNotEmpty() }
        return n ?: "${device.address.takeLast(8)}"
    }
}
