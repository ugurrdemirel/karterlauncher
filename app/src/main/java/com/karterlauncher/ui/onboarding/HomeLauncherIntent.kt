package com.karterlauncher.ui.onboarding

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.getSystemService
import com.karterlauncher.R

/**
 * Opens system UI to pick or change the default home app.
 * Tries several intents because head units often lack RoleManager UI or HOME_SETTINGS.
 *
 * @return true if an activity was started, false if nothing on this device could be opened
 */
fun launchDefaultHomePicker(activity: Activity): Boolean {
    val packageManager = activity.packageManager
    val alreadyDefault = activity.isOurAppDefaultHome()

    fun isResolvable(intent: Intent): Boolean =
        packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null

    fun tryLaunch(intent: Intent): Boolean {
        if (!isResolvable(intent)) return false
        return runCatching {
            activity.startActivity(
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }

    val settingsIntents = buildList {
        add(Intent(Settings.ACTION_HOME_SETTINGS))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
        add(Intent(Settings.ACTION_APPLICATION_SETTINGS))
        add(Intent(Settings.ACTION_SETTINGS))
    }

    for (intent in settingsIntents) {
        if (tryLaunch(intent)) return true
    }

    if (!alreadyDefault) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService<RoleManager>()
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                val roleIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                if (tryLaunch(roleIntent)) return true
            }
        }
        val homePicker = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        if (tryLaunch(homePicker)) return true
        val chooser = Intent.createChooser(
            homePicker,
            activity.getString(R.string.onboarding_step_home_chooser_title),
        )
        if (tryLaunch(chooser)) return true
    }

    return false
}
