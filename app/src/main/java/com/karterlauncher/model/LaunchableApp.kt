package com.karterlauncher.model

import android.content.ComponentName

data class LaunchableApp(
    val label: String,
    val packageName: String,
    val componentName: ComponentName,
)
