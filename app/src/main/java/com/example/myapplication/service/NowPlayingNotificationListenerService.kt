package com.example.myapplication.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat

/**
 * Reads current playback from active MediaSessions exposed to this enabled notification listener.
 * Transport notification text is used only when an app exposes no MediaSession.
 */
class NowPlayingNotificationListenerService : NotificationListenerService() {
    private val preferences by lazy(LazyThreadSafetyMode.NONE) {
        getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    private val mainHandler by lazy(LazyThreadSafetyMode.NONE) { Handler(Looper.getMainLooper()) }
    private val mediaSessionManager by lazy(LazyThreadSafetyMode.NONE) {
        getSystemService(MediaSessionManager::class.java)
    }
    private val sessionCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private var activeControllers: List<MediaController> = emptyList()
    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        attachControllers(controllers.orEmpty())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val component = ComponentName(this, NowPlayingNotificationListenerService::class.java)
        runCatching {
            mediaSessionManager.addOnActiveSessionsChangedListener(
                activeSessionsListener,
                component,
                mainHandler,
            )
            attachControllers(mediaSessionManager.getActiveSessions(component))
        }.onFailure {
            refreshDetectedPlayback()
        }
    }

    override fun onListenerDisconnected() {
        runCatching { mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsListener) }
        detachControllers()
        persistStopped()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(statusBarNotification: StatusBarNotification?) {
        if (statusBarNotification?.isTransportNotification() == true) refreshDetectedPlayback()
    }

    override fun onNotificationRemoved(statusBarNotification: StatusBarNotification?) {
        if (statusBarNotification?.isTransportNotification() == true) refreshDetectedPlayback()
    }

    private fun attachControllers(controllers: List<MediaController>) {
        detachControllers()
        activeControllers = controllers
        controllers.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = refreshDetectedPlayback()
                override fun onMetadataChanged(metadata: MediaMetadata?) = refreshDetectedPlayback()
                override fun onSessionDestroyed() = refreshActiveSessions()
            }
            runCatching { controller.registerCallback(callback, mainHandler) }
                .onSuccess { sessionCallbacks[controller] = callback }
        }
        refreshDetectedPlayback()
    }

    private fun detachControllers() {
        sessionCallbacks.forEach { (controller, callback) ->
            runCatching { controller.unregisterCallback(callback) }
        }
        sessionCallbacks.clear()
        activeControllers = emptyList()
    }

    private fun refreshActiveSessions() {
        val component = ComponentName(this, NowPlayingNotificationListenerService::class.java)
        runCatching { mediaSessionManager.getActiveSessions(component) }
            .onSuccess(::attachControllers)
            .onFailure { refreshDetectedPlayback() }
    }

    private fun refreshDetectedPlayback() {
        val playing = activeControllers
            .asSequence()
            .mapNotNull { controller ->
                val state = runCatching { controller.playbackState }.getOrNull() ?: return@mapNotNull null
                if (state.state !in ACTIVE_PLAYBACK_STATES) return@mapNotNull null
                controller.mediaText()?.let { DetectedPlayback(state.lastPositionUpdateTime, it) }
            }
            .maxByOrNull(DetectedPlayback::positionUpdatedAt)

        when {
            playing != null -> persist(playing.text, SOURCE_MEDIA_SESSION)
            activeControllers.isNotEmpty() -> persistStopped()
            else -> refreshFromActiveTransportNotifications()
        }
    }

    private fun refreshFromActiveTransportNotifications() {
        val newest = runCatching {
            activeNotifications
                .asSequence()
                .filter { it.isTransportNotification() }
                .mapNotNull { notification ->
                    notification.fallbackText()?.let { text ->
                        TimestampedFallback(notification.postTime, text)
                    }
                }
                .maxByOrNull(TimestampedFallback::postTime)
        }.getOrNull()
        if (newest == null) persistStopped() else persist(newest.text, SOURCE_NOTIFICATION_FALLBACK)
    }

    private fun MediaController.mediaText(): NowPlayingText? {
        val metadata = runCatching { metadata }.getOrNull() ?: return null
        val title = metadata.getText(MediaMetadata.METADATA_KEY_TITLE).toSafeText(MAX_TITLE_LENGTH)
            ?: metadata.description?.title.toSafeText(MAX_TITLE_LENGTH)
        val artist = (
            metadata.getText(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: metadata.description?.subtitle
            ).toSafeText(MAX_TEXT_LENGTH)
        return if (title == null || artist == null) null else NowPlayingText(title, artist)
    }

    private fun StatusBarNotification.isTransportNotification(): Boolean =
        notification.category == Notification.CATEGORY_TRANSPORT

    private fun StatusBarNotification.fallbackText(): NowPlayingText? {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE).toSafeText(MAX_TITLE_LENGTH)
        val artist = (
            extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ).toSafeText(MAX_TEXT_LENGTH)
        return if (title == null || artist == null) null else NowPlayingText(title, artist)
    }

    private fun persist(text: NowPlayingText, source: String) {
        preferences.edit()
            .putString(KEY_TITLE, text.title)
            .putString(KEY_TEXT, text.artist)
            .putString(KEY_SOURCE, source)
            .putLong(KEY_UPDATED_AT_EPOCH_MS, System.currentTimeMillis())
            .putBoolean(KEY_ACTIVE, true)
            .apply()
        publish(text.title, text.artist, source, isPlaying = true)
    }

    private fun persistStopped() {
        preferences.edit()
            .remove(KEY_TITLE)
            .remove(KEY_TEXT)
            .remove(KEY_SOURCE)
            .remove(KEY_UPDATED_AT_EPOCH_MS)
            .putBoolean(KEY_ACTIVE, false)
            .apply()
        publish(null, null, SOURCE_MEDIA_SESSION, isPlaying = false)
    }

    private fun publish(title: String?, artist: String?, source: String, isPlaying: Boolean) {
        sendBroadcast(
            Intent(ACTION_NOW_PLAYING_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_ARTIST, artist)
                .putExtra(EXTRA_SOURCE, source)
                .putExtra(EXTRA_IS_PLAYING, isPlaying)
        )
    }

    private fun CharSequence?.toSafeText(maxLength: Int): String? =
        this?.toString()
            ?.replace(CONTROL_CHARACTERS, " ")
            ?.trim()
            ?.take(maxLength)
            ?.takeIf(String::isNotEmpty)

    private data class NowPlayingText(val title: String, val artist: String)
    private data class DetectedPlayback(val positionUpdatedAt: Long, val text: NowPlayingText)
    private data class TimestampedFallback(val postTime: Long, val text: NowPlayingText)

    companion object {
        fun isEnabled(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        const val PREFERENCES_NAME = "melody_bubble_now_playing_fallback"
        const val KEY_TITLE = "title"
        const val KEY_TEXT = "text"
        const val KEY_SOURCE = "source"
        const val KEY_UPDATED_AT_EPOCH_MS = "updated_at_epoch_ms"
        const val KEY_ACTIVE = "active"

        const val ACTION_NOW_PLAYING_CHANGED =
            "com.example.myapplication.service.action.NOW_PLAYING_CHANGED"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ARTIST = "artist"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_IS_PLAYING = "is_playing"

        const val SOURCE_MEDIA_SESSION = "MEDIA_SESSION"
        const val SOURCE_NOTIFICATION_FALLBACK = "MEDIA_NOTIFICATION"

        private const val MAX_TITLE_LENGTH = 256
        private const val MAX_TEXT_LENGTH = 512
        private val ACTIVE_PLAYBACK_STATES = setOf(
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING,
        )
        private val CONTROL_CHARACTERS = Regex("[\\p{Cc}\\p{Cf}]+")
    }
}
