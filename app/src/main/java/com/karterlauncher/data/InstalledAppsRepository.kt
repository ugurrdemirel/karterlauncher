package com.karterlauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.karterlauncher.model.LaunchableApp

class InstalledAppsRepository(
    private val context: Context,
) {
    private val packageManager: PackageManager = context.packageManager

    fun getLaunchableApps(): List<LaunchableApp> {
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                launchIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launchIntent, PackageManager.MATCH_ALL)
        }

        val self = context.packageName
        return infos
            .asSequence()
            .mapNotNull { resolve -> resolve.toLaunchableAppOrNull() }
            .filter { it.packageName != self }
            .sortedBy { it.label.lowercase() }
            .distinctBy { it.packageName }
            .toList()
    }

    private fun android.content.pm.ResolveInfo.toLaunchableAppOrNull(): LaunchableApp? {
        val info = activityInfo ?: return null
        val label = info.loadLabel(packageManager).toString()
        if (label.isBlank()) return null
        val cn = android.content.ComponentName(info.packageName, info.name)
        return LaunchableApp(
            label = label,
            packageName = info.packageName,
            componentName = cn,
        )
    }
}
