package com.karterlauncher.util

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.karterlauncher.media.MediaNotificationListenerService

/** Whether [MediaNotificationListenerService] is enabled in system notification access settings. */
fun isNotificationListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners",
    ) ?: return false
    val component = ComponentName(context, MediaNotificationListenerService::class.java)
    return flat.contains(component.flattenToString())
}
