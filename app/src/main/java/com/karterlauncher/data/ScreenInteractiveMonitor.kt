package com.karterlauncher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether the device is interactive (screen on / user awake). Uses [PowerManager.isInteractive]
 * plus [Intent.ACTION_SCREEN_ON] / [Intent.ACTION_SCREEN_OFF] for updates.
 */
class ScreenInteractiveMonitor(context: Context) {
    private val app = context.applicationContext
    private val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val _interactive = MutableStateFlow(power.isInteractive)
    val interactive: StateFlow<Boolean> = _interactive.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> _interactive.value = true
                Intent.ACTION_SCREEN_OFF -> _interactive.value = false
            }
        }
    }

    private var registered = false

    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            app.registerReceiver(receiver, filter)
        }
        registered = true
        _interactive.value = power.isInteractive
    }

    fun stop() {
        if (!registered) return
        runCatching { app.unregisterReceiver(receiver) }
        registered = false
    }
}
