package com.karterlauncher

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.karterlauncher.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karterlauncher.ui.LauncherScreen
import com.karterlauncher.ui.onboarding.OnboardingScreen
import com.karterlauncher.ui.onboarding.isOurAppDefaultHome
import com.karterlauncher.ui.theme.LauncherTheme
import com.karterlauncher.ui.theme.applyLauncherWindowTheme
import com.karterlauncher.ui.theme.prefersDarkTheme
import com.karterlauncher.ui.theme.LauncherMotion
import com.karterlauncher.ui.theme.prepareLauncherTheme

class MainActivity : ComponentActivity() {
    private var appDrawerOpen = false
    private var closeAppDrawer: (() -> Unit)? = null

    private val homeBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            // Back is disabled while we are the default HOME launcher.
        }
    }

    fun setAppDrawerController(isOpen: Boolean, onClose: (() -> Unit)?) {
        appDrawerOpen = isOpen
        closeAppDrawer = onClose
        updateHomeBackCallback()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val (prefs, startup) = prepareLauncherTheme()
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, homeBackCallback)
        enableEdgeToEdge()
        setContent {
            val onboardingComplete by prefs.onboardingCompleteFlow.collectAsStateWithLifecycle(
                initialValue = startup.onboardingComplete,
            )
            val themeMode by prefs.themeModeFlow.collectAsStateWithLifecycle(
                initialValue = startup.themeMode,
            )
            LaunchedEffect(themeMode) {
                applyLauncherWindowTheme(themeMode)
            }
            LauncherTheme(darkTheme = themeMode.prefersDarkTheme()) {
                AnimatedContent(
                    targetState = onboardingComplete,
                    transitionSpec = { LauncherMotion.rootRevealTransition() },
                    label = "launcherRoot",
                ) { complete ->
                    if (!complete) {
                        OnboardingScreen(userPreferencesRepository = prefs)
                    } else {
                        val app = LocalContext.current.applicationContext as Application
                        val factory = remember(prefs, startup) {
                            LauncherViewModelFactory(app, prefs, startup)
                        }
                        val viewModel: LauncherViewModel = viewModel(factory = factory)
                        val activity = LocalContext.current as Activity
                        val snackbarHostState = remember { SnackbarHostState() }
                        LaunchedEffect(viewModel) {
                            viewModel.launchRequests.collect { request ->
                                try {
                                    activity.startActivity(request.intent)
                                } catch (_: Exception) {
                                    val label = request.failureLabel
                                        ?: activity.getString(R.string.app_name)
                                    snackbarHostState.showSnackbar(
                                        activity.getString(R.string.launch_error_failed, label),
                                    )
                                }
                            }
                        }
                        LaunchedEffect(viewModel) {
                            viewModel.snackbarMessages.collect { message ->
                                snackbarHostState.showSnackbar(message)
                            }
                        }
                        Scaffold(
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                        ) { innerPadding ->
                            LauncherScreen(
                                viewModel = viewModel,
                                modifier = Modifier.padding(innerPadding),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_HOME)
        ) {
            closeAppDrawer?.invoke()
        }
    }

    override fun onResume() {
        super.onResume()
        updateHomeBackCallback()
    }

    private fun updateHomeBackCallback() {
        // Swallow back on the home screen when we are HOME and the app drawer is closed.
        homeBackCallback.isEnabled = isOurAppDefaultHome() && !appDrawerOpen
    }
}
