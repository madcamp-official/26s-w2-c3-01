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
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.example.myapplication.data.presence.PresenceSyncCoordinator
import com.example.myapplication.music.MusicPlaybackAppPreference

/**
 * Reads current playback from active MediaSessions exposed to this enabled notification listener.
 * Transport notification text is used only when an app exposes no MediaSession.
 */
class NowPlayingNotificationListenerService : NotificationListenerService() {
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
        val observed = activeControllers
            .asSequence()
            .mapNotNull { controller ->
                val state = runCatching { controller.playbackState }.getOrNull() ?: return@mapNotNull null
                controller.mediaText(state)?.let {
                    ObservedPlayback(
                        positionUpdatedAt = state.lastPositionUpdateTime,
                        text = it,
                        isPlaying = state.state in ACTIVE_PLAYBACK_STATES,
                        packageName = controller.packageName,
                    )
                }
            }
            .maxByOrNull(ObservedPlayback::positionUpdatedAt)

        when {
            observed != null -> publish(
                observed.text,
                SOURCE_MEDIA_SESSION,
                observed.isPlaying,
                observed.packageName,
            )
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
                        TimestampedFallback(notification.postTime, text, notification.packageName)
                    }
                }
                .maxByOrNull(TimestampedFallback::postTime)
        }.getOrNull()
        if (newest == null) {
            persistStopped()
        } else {
            publish(
                newest.text,
                SOURCE_NOTIFICATION_FALLBACK,
                isPlaying = true,
                playbackPackageName = newest.packageName,
            )
        }
    }

    private fun MediaController.mediaText(playbackState: PlaybackState): NowPlayingText? {
        val metadata = runCatching { metadata }.getOrNull() ?: return null
        val title = metadata.getText(MediaMetadata.METADATA_KEY_TITLE).toSafeText(MAX_TITLE_LENGTH)
            ?: metadata.description?.title.toSafeText(MAX_TITLE_LENGTH)
        val artist = (
            metadata.getText(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: metadata.description?.subtitle
            ).toSafeText(MAX_TEXT_LENGTH)
        val album = metadata.getText(MediaMetadata.METADATA_KEY_ALBUM).toSafeText(MAX_TEXT_LENGTH)
        val artworkUrl = sequenceOf(
            metadata.getString(MediaMetadata.METADATA_KEY_ART_URI),
            metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),
            metadata.description?.iconUri?.toString(),
        ).filterNotNull().firstOrNull { it.startsWith("https://") }?.take(MAX_URL_LENGTH)
        val durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0L }
        val positionObservedAt = System.currentTimeMillis() -
            (SystemClock.elapsedRealtime() - playbackState.lastPositionUpdateTime).coerceAtLeast(0L)
        return if (title == null || artist == null) null else NowPlayingText(
            title = title,
            artist = artist,
            album = album,
            artworkUrl = artworkUrl,
            durationMs = durationMs,
            positionMs = playbackState.position.takeIf { it >= 0L },
            positionObservedAtEpochMs = positionObservedAt,
        )
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

    private fun persistStopped() {
        publish(null, SOURCE_MEDIA_SESSION, isPlaying = false)
    }

    private fun publish(
        text: NowPlayingText?,
        source: String,
        isPlaying: Boolean,
        playbackPackageName: String? = null,
    ) {
        if (text != null && playbackPackageName != null) {
            MusicPlaybackAppPreference.remember(this, playbackPackageName)
        }
        val coordinator = PresenceSyncCoordinator.get(this)
        if (text != null) {
            coordinator.onPlaybackDetected(
                title = text.title,
                artist = text.artist,
                source = source,
                album = text.album,
                artworkUrl = text.artworkUrl,
                durationMs = text.durationMs,
                positionMs = text.positionMs,
                positionObservedAtEpochMs = text.positionObservedAtEpochMs,
                isPlaying = isPlaying,
            )
        } else {
            coordinator.onPlaybackStopped()
        }

        // Kept as a package-scoped compatibility signal for older app components. Presence REST
        // synchronization is owned by the coordinator above and never depends on this broadcast.
        sendBroadcast(
            Intent(ACTION_NOW_PLAYING_CHANGED)
                .setPackage(packageName)
                .putExtra(EXTRA_TITLE, text?.title)
                .putExtra(EXTRA_ARTIST, text?.artist)
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

    private data class NowPlayingText(
        val title: String,
        val artist: String,
        val album: String? = null,
        val artworkUrl: String? = null,
        val durationMs: Long? = null,
        val positionMs: Long? = null,
        val positionObservedAtEpochMs: Long? = null,
    )
    private data class ObservedPlayback(
        val positionUpdatedAt: Long,
        val text: NowPlayingText,
        val isPlaying: Boolean,
        val packageName: String,
    )
    private data class TimestampedFallback(
        val postTime: Long,
        val text: NowPlayingText,
        val packageName: String,
    )

    companion object {
        fun isEnabled(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        const val PREFERENCES_NAME = PresenceSyncCoordinator.PREFERENCES_NAME
        const val KEY_TITLE = PresenceSyncCoordinator.KEY_TITLE
        const val KEY_TEXT = PresenceSyncCoordinator.KEY_ARTIST
        const val KEY_SOURCE = PresenceSyncCoordinator.KEY_SOURCE
        const val KEY_UPDATED_AT_EPOCH_MS = PresenceSyncCoordinator.KEY_UPDATED_AT_EPOCH_MS
        const val KEY_ACTIVE = PresenceSyncCoordinator.KEY_ACTIVE

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
        private const val MAX_URL_LENGTH = 2_000
        private val ACTIVE_PLAYBACK_STATES = setOf(
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING,
        )
        private val CONTROL_CHARACTERS = Regex("[\\p{Cc}\\p{Cf}]+")
    }
}
