package com.karterlauncher.ui.onboarding

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.getSystemService

fun Context.isOurAppDefaultHome(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = getSystemService<RoleManager>() ?: return false
        return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
    }
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val info = packageManager.resolveActivity(
        intent,
        PackageManager.MATCH_DEFAULT_ONLY,
    )
    return info?.activityInfo?.packageName == packageName
}
