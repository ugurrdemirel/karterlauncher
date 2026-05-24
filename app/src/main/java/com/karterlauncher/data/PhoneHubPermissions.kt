package com.karterlauncher.data

import android.Manifest
import android.content.Context
import androidx.core.content.ContextCompat

object PhoneHubPermissions {
    val REQUIRED: Array<String> = buildList {
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.CALL_PHONE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.toTypedArray()

    val BLUETOOTH_CALLING: Array<String> = buildList {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.toTypedArray()

    fun allGranted(context: Context): Boolean =
        REQUIRED.all { granted(context, it) }

    fun bluetoothCallingGranted(context: Context): Boolean =
        BLUETOOTH_CALLING.isEmpty() || BLUETOOTH_CALLING.all { granted(context, it) }

    /** Dial pad + Bluetooth HFP without contacts/call-log on the head unit. */
    fun canUseBluetoothDialer(context: Context): Boolean = bluetoothCallingGranted(context)

    fun canListContactsAndRecents(context: Context): Boolean =
        granted(context, Manifest.permission.READ_CONTACTS) &&
            granted(context, Manifest.permission.READ_CALL_LOG)

    fun canAccessPhoneHub(context: Context): Boolean =
        allGranted(context) || canUseBluetoothDialer(context)

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
}
