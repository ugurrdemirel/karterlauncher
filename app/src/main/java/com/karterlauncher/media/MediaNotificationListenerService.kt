package com.karterlauncher.media

import android.content.ComponentName
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.karterlauncher.data.ActiveMediaRepository
import com.karterlauncher.util.isNotificationListenerEnabled

/**
 * Bridges [android.media.session.MediaSessionManager]: active media sessions are only exposed to
 * apps whose notification listener is enabled for the package.
 */
class MediaNotificationListenerService : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())
    private val componentName by lazy {
        ComponentName(this, MediaNotificationListenerService::class.java)
    }
    private val debouncedRefresh = Runnable {
        ActiveMediaRepository.getInstance(application).refreshSessions()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val repo = ActiveMediaRepository.getInstance(application)
        repo.refreshSessions()
        repo.attachActiveSessionsListener()
    }

    override fun onListenerDisconnected() {
        handler.removeCallbacks(debouncedRefresh)
        val repo = ActiveMediaRepository.getInstance(application)
        repo.detachActiveSessionsListener()
        repo.clearAllSessions()
        super.onListenerDisconnected()
        // System sometimes drops the bind during package updates; ask to reconnect if still allowed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            isNotificationListenerEnabled(application)
        ) {
            try {
                requestRebind(componentName)
            } catch (e: Exception) {
                Log.w(TAG, "requestRebind failed", e)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        scheduleRefresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        scheduleRefresh()
    }

    private fun scheduleRefresh() {
        handler.removeCallbacks(debouncedRefresh)
        handler.postDelayed(debouncedRefresh, DEBOUNCE_MS)
    }

    companion object {
        private const val TAG = "MediaNotifListener"
        private const val DEBOUNCE_MS = 400L
    }
}
