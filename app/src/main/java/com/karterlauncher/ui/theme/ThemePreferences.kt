package com.karterlauncher.ui.theme

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.karterlauncher.data.StartupSnapshot
import com.karterlauncher.data.UserPreferencesRepository
import com.karterlauncher.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@Composable
fun ThemeMode.prefersDarkTheme(): Boolean = prefersDarkTheme(isSystemInDarkTheme())

fun ThemeMode.prefersDarkTheme(systemInDarkTheme: Boolean): Boolean = when (this) {
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
    ThemeMode.System -> systemInDarkTheme
}

fun Context.isSystemInDarkTheme(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

fun launcherBackgroundColor(darkTheme: Boolean): Int =
    if (darkTheme) 0xFF000000.toInt() else 0xFFE8E8ED.toInt()

fun Activity.applyLauncherWindowTheme(themeMode: ThemeMode) {
    val dark = themeMode.prefersDarkTheme(isSystemInDarkTheme())
    @Suppress("DEPRECATION")
    window.setBackgroundDrawable(ColorDrawable(launcherBackgroundColor(dark)))
}

/** Reads persisted theme before the first frame and applies the matching window background. */
fun ComponentActivity.prepareLauncherTheme(): Pair<UserPreferencesRepository, StartupSnapshot> {
    val prefs = UserPreferencesRepository(applicationContext)
    val startup = runBlocking {
        withContext(Dispatchers.IO) { prefs.readStartupSnapshot() }
    }
    applyLauncherWindowTheme(startup.themeMode)
    return prefs to startup
}
