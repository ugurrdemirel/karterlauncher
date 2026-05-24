package com.karterlauncher

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karterlauncher.ui.settings.HiddenAppsScreen
import com.karterlauncher.ui.theme.LauncherTheme
import com.karterlauncher.ui.theme.applyLauncherWindowTheme
import com.karterlauncher.ui.theme.prefersDarkTheme
import com.karterlauncher.ui.theme.prepareLauncherTheme

class HiddenAppsActivity : ComponentActivity() {
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
                val app = LocalContext.current.applicationContext as Application
                val factory = remember(prefs, startup) {
                    LauncherViewModelFactory(app, prefs, startup)
                }
                val viewModel: LauncherViewModel = viewModel(factory = factory)
                HiddenAppsScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                )
            }
        }
    }
}
