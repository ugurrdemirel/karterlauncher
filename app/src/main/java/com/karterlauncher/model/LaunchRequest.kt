package com.karterlauncher.model

import android.content.Intent

/** Ana ekrandan başlatılacak aktivite; hata mesajı için etiket isteğe bağlı. */
data class LaunchRequest(
    val intent: Intent,
    val failureLabel: String? = null,
)
