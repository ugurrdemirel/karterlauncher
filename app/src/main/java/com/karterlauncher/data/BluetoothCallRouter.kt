package com.karterlauncher.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Places outgoing calls on car head units by routing through the paired phone over
 * Bluetooth Hands-Free Client (HFP HF role). Falls back to system dial/call intents when
 * HFP is unavailable.
 */
class BluetoothCallRouter(
    private val context: Context,
) {
    companion object {
        /** [BluetoothProfile.HEADSET_CLIENT] — @hide but used on automotive head units. */
        private const val BT_PROFILE_HEADSET_CLIENT = 16
    }

    data class BluetoothPhoneLink(
        val deviceName: String,
        val canPlaceCalls: Boolean,
    )

    sealed class OutgoingCallResult {
        data object RoutedBluetooth : OutgoingCallResult()
        data object RoutedSystemDialer : OutgoingCallResult()
        data class Failed(val reason: OutgoingCallFailure) : OutgoingCallResult()
    }

    enum class OutgoingCallFailure {
        NO_PHONE_LINKED,
        BLUETOOTH_UNAVAILABLE,
        BLUETOOTH_PERMISSION_REQUIRED,
        DIAL_FAILED,
        NO_CALL_HANDLER,
    }

    suspend fun queryPhoneLink(): BluetoothPhoneLink? = withContext(Dispatchers.IO) {
        when (bluetoothBlocker()) {
            BluetoothBlocker.PERMISSION -> return@withContext null
            BluetoothBlocker.UNAVAILABLE -> return@withContext null
            BluetoothBlocker.OK -> Unit
        }
        val device = findHeadsetClientPhone() ?: return@withContext null
        BluetoothPhoneLink(
            deviceName = friendlyName(device) ?: device.address,
            canPlaceCalls = true,
        )
    }

    suspend fun placeCall(rawNumber: String): OutgoingCallResult = withContext(Dispatchers.IO) {
        val number = normalizeDialNumber(rawNumber)
            ?: return@withContext OutgoingCallResult.Failed(OutgoingCallFailure.DIAL_FAILED)

        when (bluetoothBlocker()) {
            BluetoothBlocker.PERMISSION ->
                return@withContext OutgoingCallResult.Failed(OutgoingCallFailure.BLUETOOTH_PERMISSION_REQUIRED)
            BluetoothBlocker.UNAVAILABLE -> {
                // No BT stack — try intents only.
            }
            BluetoothBlocker.OK -> {
                if (dialViaHeadsetClient(number)) {
                    return@withContext OutgoingCallResult.RoutedBluetooth
                }
                val linkMissing = findHeadsetClientPhone() == null
                if (linkMissing && !hasIntentCallHandler()) {
                    return@withContext OutgoingCallResult.Failed(OutgoingCallFailure.NO_PHONE_LINKED)
                }
            }
        }

        placeCallViaIntent(number)
    }

    private enum class BluetoothBlocker {
        OK,
        PERMISSION,
        UNAVAILABLE,
    }

    private fun bluetoothBlocker(): BluetoothBlocker {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            return BluetoothBlocker.UNAVAILABLE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return BluetoothBlocker.PERMISSION
        }
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: return BluetoothBlocker.UNAVAILABLE
        if (!adapter.isEnabled) return BluetoothBlocker.UNAVAILABLE
        return BluetoothBlocker.OK
    }

    @SuppressLint("MissingPermission")
    private suspend fun findHeadsetClientPhone(): BluetoothDevice? {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: return null
        val devices = queryConnected(adapter, BT_PROFILE_HEADSET_CLIENT)
        return devices.firstOrNull()
    }

    @SuppressLint("MissingPermission")
    private suspend fun dialViaHeadsetClient(number: String): Boolean {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: return false
        val device = findHeadsetClientPhone() ?: return false
        return withHeadsetClientProxy(adapter) { proxy ->
            invokeHeadsetClientDial(proxy, device, number)
        }
    }

    private fun invokeHeadsetClientDial(proxy: BluetoothProfile, device: BluetoothDevice, number: String): Boolean {
        return try {
            val result = proxy.javaClass
                .getMethod("dial", BluetoothDevice::class.java, String::class.java)
                .invoke(proxy, device, number)
            result != null
        } catch (_: ReflectiveOperationException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun withHeadsetClientProxy(
        adapter: BluetoothAdapter,
        block: suspend (BluetoothProfile) -> Boolean,
    ): Boolean {
        return withTimeoutOrNull(5_000L) {
            suspendCancellableCoroutine { cont ->
                val listener = object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profileId: Int, proxy: BluetoothProfile) {
                        if (profileId != BT_PROFILE_HEADSET_CLIENT || !cont.isActive) {
                            closeProxy(adapter, profileId, proxy)
                            return
                        }
                        cont.resume(proxy)
                    }

                    override fun onServiceDisconnected(profileId: Int) {
                        //
                    }
                }
                val ok = try {
                    adapter.getProfileProxy(context.applicationContext, listener, BT_PROFILE_HEADSET_CLIENT)
                } catch (_: SecurityException) {
                    false
                } catch (_: IllegalArgumentException) {
                    false
                }
                if (!ok) cont.resume(null)
            }
        }?.let { proxy ->
            try {
                block(proxy)
            } finally {
                closeProxy(adapter, BT_PROFILE_HEADSET_CLIENT, proxy)
            }
        } ?: false
    }

    private fun placeCallViaIntent(number: String): OutgoingCallResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${Uri.encode(number)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(callIntent)
                return OutgoingCallResult.RoutedSystemDialer
            } catch (_: SecurityException) {
                // fall through to DIAL
            } catch (_: ActivityNotFoundException) {
                // fall through to DIAL
            }
        }

        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${Uri.encode(number)}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return try {
            context.startActivity(dialIntent)
            OutgoingCallResult.RoutedSystemDialer
        } catch (_: ActivityNotFoundException) {
            OutgoingCallResult.Failed(OutgoingCallFailure.NO_CALL_HANDLER)
        }
    }

    private fun hasIntentCallHandler(): Boolean {
        val pm = context.packageManager
        val dial = Intent(Intent.ACTION_DIAL)
        val call = Intent(Intent.ACTION_CALL).apply { data = Uri.parse("tel:0") }
        return pm.resolveActivity(dial, PackageManager.MATCH_DEFAULT_ONLY) != null ||
            pm.resolveActivity(call, PackageManager.MATCH_DEFAULT_ONLY) != null
    }

    @SuppressLint("MissingPermission")
    private suspend fun queryConnected(adapter: BluetoothAdapter, profile: Int): List<BluetoothDevice> {
        return withTimeoutOrNull(4_200L) {
            suspendCancellableCoroutine { cont ->
                val listener = object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profileId: Int, proxy: BluetoothProfile) {
                        if (profileId != profile || !cont.isActive) {
                            closeProxy(adapter, profileId, proxy)
                            return
                        }
                        val devices = try {
                            proxy.connectedDevices
                        } catch (_: SecurityException) {
                            emptyList()
                        }
                        closeProxy(adapter, profileId, proxy)
                        cont.resume(devices)
                    }

                    override fun onServiceDisconnected(profileId: Int) {
                        //
                    }
                }
                val ok = try {
                    adapter.getProfileProxy(context.applicationContext, listener, profile)
                } catch (_: SecurityException) {
                    false
                } catch (_: IllegalArgumentException) {
                    false
                }
                if (!ok) cont.resume(emptyList())
            }
        } ?: emptyList()
    }

    private fun closeProxy(adapter: BluetoothAdapter, profileId: Int, proxy: BluetoothProfile?) {
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
        return primary?.trim()?.takeIf { it.isNotEmpty() }
    }
}

/** Keeps only characters valid for HFP dial strings. */
internal fun normalizeDialNumber(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val sb = StringBuilder()
    trimmed.forEachIndexed { index, ch ->
        when {
            ch.isDigit() -> sb.append(ch)
            ch == '+' && index == 0 && sb.isEmpty() -> sb.append(ch)
            ch == '*' || ch == '#' -> sb.append(ch)
            ch == ' ' || ch == '-' || ch == '(' || ch == ')' -> Unit
            else -> return null
        }
    }
    return sb.toString().takeIf { it.isNotEmpty() }
}
