package com.karterlauncher.data

import android.app.Application
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSession.QueueItem
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.karterlauncher.media.MediaNotificationListenerService
import com.karterlauncher.util.isNotificationListenerEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MediaQueueItemUi(
    val title: String,
    val subtitle: String?,
    /** [MediaSession.QueueItem.getQueueId]; use with [MediaController.TransportControls.skipToQueueItem]. */
    val queueId: Long,
    /** Per-row artwork, taken from [MediaDescription.getIconBitmap]/[MediaDescription.getIconUri]. */
    val artwork: Any? = null,
)

data class NowPlayingUi(
    val title: String? = null,
    val artist: String? = null,
    /** Secondary line: artist · album or artist */
    val subtitle: String? = null,
    /** [Bitmap] or [Uri] for Coil */
    val artworkModel: Any? = null,
    val isPlaying: Boolean = false,
    val hasActiveSession: Boolean = false,
    val notificationAccessEnabled: Boolean = false,
    /** From [MediaController.getQueue] when the player exposes a queue (often empty for some apps). */
    val upNext: List<MediaQueueItemUi> = emptyList(),
    /** Track duration in ms from [MediaMetadata.METADATA_KEY_DURATION], 0 if unknown. */
    val durationMs: Long = 0L,
)

class ActiveMediaRepository private constructor(
    private val application: Application,
) {
    private val _nowPlaying = MutableStateFlow(
        NowPlayingUi(notificationAccessEnabled = isNotificationListenerEnabled(application)),
    )
    val nowPlaying: StateFlow<NowPlayingUi> = _nowPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val pollingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionJob: Job? = null
    private var lastPublishedSnapshot: Int = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaSessionManager: MediaSessionManager? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            mainHandler.post {
                bindControllers(controllers ?: emptyList())
            }
        }

    private val boundCallbacks = mutableMapOf<MediaSession.Token, MediaController.Callback>()
    private var activeControllers: List<MediaController> = emptyList()
    private var sessionsListenerRegistered = false

    fun refreshSessions() {
        mainHandler.post { refreshSessionsOnMainThread() }
    }

    /**
     * Jump playback to a track from the active session queue (if the media app supports it).
     */
    fun skipToQueueItem(queueId: Long) {
        if (queueId == QueueItem.UNKNOWN_ID.toLong()) return
        mainHandler.post {
            if (!isNotificationListenerEnabled(application)) return@post
            val controller = pickController(activeControllers) ?: return@post
            runCatching { controller.transportControls.skipToQueueItem(queueId) }
        }
    }

    private fun refreshSessionsOnMainThread() {
        val enabled = isNotificationListenerEnabled(application)
        if (!enabled) {
            clearControllersInternal()
            _nowPlaying.value = NowPlayingUi(notificationAccessEnabled = false)
            _positionMs.value = 0L
            return
        }
        try {
            val msm = mediaSessionManager
                ?: (application.getSystemService(Application.MEDIA_SESSION_SERVICE) as MediaSessionManager)
                    .also { mediaSessionManager = it }
            val component = ComponentName(application, MediaNotificationListenerService::class.java)
            val controllers = msm.getActiveSessions(component)
            bindControllers(controllers)
        } catch (_: SecurityException) {
            clearControllersInternal()
            _nowPlaying.value = NowPlayingUi(notificationAccessEnabled = false)
        }
    }

    fun attachActiveSessionsListener() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (!isNotificationListenerEnabled(application)) return
        if (sessionsListenerRegistered) return
        val msm = mediaSessionManager ?: run {
            runCatching {
                application.getSystemService(Application.MEDIA_SESSION_SERVICE) as MediaSessionManager
            }.getOrNull()?.also { mediaSessionManager = it }
        } ?: return
        try {
            val component = ComponentName(application, MediaNotificationListenerService::class.java)
            msm.addOnActiveSessionsChangedListener(sessionsListener, component, mainHandler)
            sessionsListenerRegistered = true
        } catch (_: SecurityException) {
            sessionsListenerRegistered = false
        }
    }

    fun detachActiveSessionsListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sessionsListenerRegistered) {
            runCatching {
                mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
            }
        }
        sessionsListenerRegistered = false
    }

    fun clearAllSessions() {
        mainHandler.post {
            clearControllersInternal()
            _nowPlaying.value = NowPlayingUi(
                notificationAccessEnabled = isNotificationListenerEnabled(application),
            )
            _positionMs.value = 0L
        }
    }

    private fun clearControllersInternal() {
        activeControllers.forEach { unregisterController(it) }
        activeControllers = emptyList()
    }

    private fun bindControllers(newControllers: List<MediaController>) {
        val old = activeControllers
        val newTokens = newControllers.map { it.sessionToken }.toSet()
        old.forEach { c ->
            if (c.sessionToken !in newTokens) unregisterController(c)
        }
        val oldTokens = old.map { it.sessionToken }.toSet()
        newControllers.forEach { c ->
            if (c.sessionToken !in oldTokens) registerController(c)
        }
        activeControllers = newControllers
        publishFrom(pickController(newControllers))
    }

    private fun registerController(controller: MediaController) {
        if (boundCallbacks.containsKey(controller.sessionToken)) return
        val cb = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                mainHandler.post { publishFrom(pickController(activeControllers)) }
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                mainHandler.post { publishFrom(pickController(activeControllers)) }
            }

            override fun onQueueChanged(queue: MutableList<QueueItem>?) {
                mainHandler.post { publishFrom(pickController(activeControllers)) }
            }
        }
        boundCallbacks[controller.sessionToken] = cb
        controller.registerCallback(cb)
    }

    private fun unregisterController(controller: MediaController) {
        boundCallbacks.remove(controller.sessionToken)?.let { cb ->
            controller.unregisterCallback(cb)
        }
    }

    private fun pickController(controllers: List<MediaController>): MediaController? {
        if (controllers.isEmpty()) return null
        fun MediaController.score(): Int {
            val state = playbackState?.state ?: PlaybackState.STATE_NONE
            val meta = metadata
            val hasTitle = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) != null ||
                meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) != null
            val stateScore = when (state) {
                PlaybackState.STATE_PLAYING -> 300
                PlaybackState.STATE_BUFFERING -> 200
                PlaybackState.STATE_PAUSED -> 100
                else -> 0
            }
            val metaScore = if (hasTitle) 50 else 0
            return stateScore + metaScore
        }
        return controllers.maxByOrNull { it.score() }
    }

    private fun publishFrom(controller: MediaController?) {
        val access = isNotificationListenerEnabled(application)
        if (controller == null) {
            _nowPlaying.value = NowPlayingUi(
                notificationAccessEnabled = access,
                hasActiveSession = false,
            )
            _positionMs.value = 0L
            return
        }
        val meta = controller.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val album = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val subtitle = listOfNotNull(artist?.takeIf { it.isNotBlank() }, album?.takeIf { it.isNotBlank() })
            .joinToString(" · ")
            .ifBlank { null }
            ?: artist?.takeIf { it.isNotBlank() }
        val artwork = meta.toArtworkModel()
        val state = controller.playbackState?.state ?: PlaybackState.STATE_NONE
        val isPlaying = state == PlaybackState.STATE_PLAYING ||
            state == PlaybackState.STATE_BUFFERING
        val upNext = buildUpNext(controller)
        val duration = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        _nowPlaying.value = NowPlayingUi(
            title = title,
            artist = artist,
            subtitle = subtitle,
            artworkModel = artwork,
            isPlaying = isPlaying,
            hasActiveSession = true,
            notificationAccessEnabled = access,
            upNext = upNext,
            durationMs = duration,
        )
        _positionMs.value = computePositionMs(controller)
    }

    fun startPositionPolling() {
        if (positionJob?.isActive == true) return
        positionJob = pollingScope.launch {
            while (isActive) {
                val controller = pickController(activeControllers)
                if (controller != null) {
                    _positionMs.value = computePositionMs(controller)
                    val snapshot = controllerSnapshotKey(controller)
                    if (snapshot != lastPublishedSnapshot) {
                        lastPublishedSnapshot = snapshot
                        publishFrom(controller)
                    }
                } else if (lastPublishedSnapshot != 0) {
                    lastPublishedSnapshot = 0
                    _nowPlaying.value = NowPlayingUi(
                        notificationAccessEnabled = isNotificationListenerEnabled(application),
                        hasActiveSession = false,
                    )
                    _positionMs.value = 0L
                }
                delay(POSITION_POLL_INTERVAL_MS)
            }
        }
    }

    private fun controllerSnapshotKey(controller: MediaController): Int {
        val state = controller.playbackState?.state ?: PlaybackState.STATE_NONE
        val meta = controller.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val duration = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val queue = controller.queue
        val queueSize = queue?.size ?: 0
        val queueFirstId = queue?.firstOrNull()?.queueId ?: 0L
        var key = state
        key = 31 * key + (title?.hashCode() ?: 0)
        key = 31 * key + (artist?.hashCode() ?: 0)
        key = 31 * key + duration.hashCode()
        key = 31 * key + queueSize
        key = 31 * key + queueFirstId.hashCode()
        return key
    }

    fun stopPositionPolling() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun computePositionMs(controller: MediaController): Long {
        val state = controller.playbackState ?: return 0L
        val pos = state.position
        if (pos < 0L) return 0L
        val isPlaying = state.state == PlaybackState.STATE_PLAYING ||
            state.state == PlaybackState.STATE_BUFFERING
        val updateTime = state.lastPositionUpdateTime
        if (!isPlaying || updateTime <= 0L) return pos
        val elapsed = SystemClock.elapsedRealtime() - updateTime
        if (elapsed <= 0L) return pos
        return (pos + elapsed).coerceAtLeast(0L)
    }

    private fun buildUpNext(controller: MediaController, maxItems: Int = 50): List<MediaQueueItemUi> {
        val raw = controller.queue ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        val activeId = controller.playbackState?.activeQueueItemId ?: QueueItem.UNKNOWN_ID
        val idx = raw.indexOfFirst { it.queueId == activeId }
        val startIndex = when {
            idx >= 0 && idx < raw.lastIndex -> idx + 1
            idx >= 0 -> return emptyList()
            else -> guessQueueStartIndex(controller, raw)
        }
        return raw.asSequence()
            .drop(startIndex)
            .take(maxItems)
            .mapNotNull { item ->
                val d = item.description ?: return@mapNotNull null
                val t = d.title?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val sub = d.subtitle?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                val art = d.iconBitmap ?: run {
                    val uri = d.iconUri
                    if (uri != null) uri else null
                }
                MediaQueueItemUi(title = t, subtitle = sub, queueId = item.queueId, artwork = art)
            }
            .toList()
    }

    private fun guessQueueStartIndex(controller: MediaController, queue: List<QueueItem>): Int {
        val currentTitle = controller.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: controller.metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (currentTitle.isNullOrBlank() || queue.size <= 1) return 0
        val firstTitle = queue.first().description?.title?.toString()
        return if (currentTitle == firstTitle) 1 else 0
    }

    private fun MediaMetadata?.toArtworkModel(): Any? {
        if (this == null) return null
        val bmp = getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (bmp != null) return bmp
        val uriStr = getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        if (!uriStr.isNullOrBlank()) {
            return runCatching { Uri.parse(uriStr) }.getOrNull()
        }
        return null
    }

    companion object {
        private const val POSITION_POLL_INTERVAL_MS = 500L

        @Volatile
        private var instance: ActiveMediaRepository? = null

        fun getInstance(app: Application): ActiveMediaRepository {
            return instance ?: synchronized(this) {
                instance ?: ActiveMediaRepository(app).also { instance = it }
            }
        }
    }
}
