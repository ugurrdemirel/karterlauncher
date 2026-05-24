package com.karterlauncher.ui.phone

import android.content.Context
import com.karterlauncher.R
import com.karterlauncher.data.BluetoothCallRouter

internal fun outgoingCallErrorMessage(
    context: Context,
    reason: BluetoothCallRouter.OutgoingCallFailure,
): String = when (reason) {
    BluetoothCallRouter.OutgoingCallFailure.NO_PHONE_LINKED ->
        context.getString(R.string.phone_hub_error_no_bt_phone)
    BluetoothCallRouter.OutgoingCallFailure.BLUETOOTH_UNAVAILABLE ->
        context.getString(R.string.phone_hub_error_bt_unavailable)
    BluetoothCallRouter.OutgoingCallFailure.BLUETOOTH_PERMISSION_REQUIRED ->
        context.getString(R.string.phone_hub_error_bt_permission)
    BluetoothCallRouter.OutgoingCallFailure.DIAL_FAILED ->
        context.getString(R.string.phone_hub_error_dial_failed)
    BluetoothCallRouter.OutgoingCallFailure.NO_CALL_HANDLER ->
        context.getString(R.string.phone_hub_error_no_handler)
}
