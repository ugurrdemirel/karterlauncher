package com.karterlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karterlauncher.ui.phone.PhoneHubScreen
import com.karterlauncher.ui.theme.LauncherTheme
import com.karterlauncher.ui.theme.applyLauncherWindowTheme
import com.karterlauncher.ui.theme.prefersDarkTheme
import com.karterlauncher.ui.theme.prepareLauncherTheme

class PhoneHubActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val (prefs, startup) = prepareLauncherTheme()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by prefs.themeModeFlow.collectAsStateWithLifecycle(
                initialValue = startup.themeMode,
            )
            LaunchedEffect(themeMode) {
                applyLauncherWindowTheme(themeMode)
            }
            LauncherTheme(darkTheme = themeMode.prefersDarkTheme()) {
                PhoneHubScreen(onFinish = { finish() })
            }
        }
    }
}
