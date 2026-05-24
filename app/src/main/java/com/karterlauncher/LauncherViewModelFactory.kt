package com.karterlauncher

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.karterlauncher.data.StartupSnapshot
import com.karterlauncher.data.UserPreferencesRepository

class LauncherViewModelFactory(
    private val application: Application,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val startupSnapshot: StartupSnapshot,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LauncherViewModel::class.java)) {
            return LauncherViewModel(
                application,
                userPreferencesRepository,
                startupSnapshot,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
