package com.example.myapplication.data.presence

import android.content.Context
import com.example.myapplication.core.model.Track
import com.example.myapplication.data.local.SecureTokenStore
import com.example.myapplication.data.remote.ApiClient
import com.example.myapplication.data.remote.MusicUpdateRequest
import com.example.myapplication.data.remote.NearbyApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException

data class DetectedPlaybackState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val verifiedInCurrentProcess: Boolean = true,
)

/**
 * Process-wide owner of Android now-playing state and its REST synchronization.
 *
 * Notification listener callbacks can arrive without an Activity. The latest state is therefore
 * persisted first and is retried when either the network or an authenticated session returns.
 */
class PresenceSyncCoordinator private constructor(
    context: Context,
    private val nearbyApi: NearbyApi = ApiClient.createNearbyApi(),
) {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val tokenStore = SecureTokenStore(applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _detectedPlayback = MutableStateFlow(readPersistedPlayback())
    private val syncSignals = Channel<Unit>(Channel.CONFLATED)

    val detectedPlayback: StateFlow<DetectedPlaybackState> = _detectedPlayback.asStateFlow()

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var lastSyncedFingerprint: String? = null

    init {
        scope.launch { syncWorker() }
    }

    fun restoreSession() {
        scope.launch {
            tokenStore.load()?.accessToken?.let(::onSessionAvailable)
        }
    }

    fun onSessionAvailable(token: String) {
        if (token.isBlank()) return
        val changed = accessToken != token
        accessToken = token
        if (changed) lastSyncedFingerprint = null
        scheduleLatestSync(force = changed)
    }

    fun onSessionCleared() {
        accessToken = null
        lastSyncedFingerprint = null
        syncSignals.trySend(Unit)
    }

    fun onPlaybackDetected(title: String, artist: String, source: String?) {
        val safeTitle = title.toSafeMetadata(MAX_TITLE_LENGTH)
        val safeArtist = artist.toSafeMetadata(MAX_ARTIST_LENGTH)
        if (safeTitle == null || safeArtist == null) {
            onPlaybackStopped()
            return
        }
        val safeSource = source.toSafeMetadata(MAX_SOURCE_LENGTH) ?: DEFAULT_SOURCE_TYPE
        updatePlayback(
            DetectedPlaybackState(
                track = Track(
                    id = "detected-${safeTitle.hashCode()}-${safeArtist.hashCode()}",
                    title = safeTitle,
                    artist = safeArtist,
                    platform = safeSource,
                ),
                isPlaying = true,
            )
        )
    }

    fun onPlaybackStopped() {
        updatePlayback(DetectedPlaybackState())
    }

    private fun updatePlayback(playback: DetectedPlaybackState) {
        persist(playback)
        _detectedPlayback.value = playback
        scheduleLatestSync()
    }

    private fun scheduleLatestSync(force: Boolean = false) {
        accessToken ?: return
        val fingerprint = _detectedPlayback.value.fingerprint()
        if (!force && fingerprint == lastSyncedFingerprint) return
        syncSignals.trySend(Unit)
    }

    private suspend fun syncWorker() {
        while (scope.isActive) {
            syncSignals.receive()
            syncLatestSerially()
        }
    }

    private suspend fun syncLatestSerially() {
        var retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS
        while (scope.isActive) {
            val token = accessToken ?: return
            val playback = _detectedPlayback.value
            val fingerprint = playback.fingerprint()
            val result = runCatching {
                nearbyApi.updateMusic(
                    authorization = "Bearer $token",
                    request = playback.toMusicUpdateRequest(),
                )
            }

            if (accessToken != token) continue

            if (result.isSuccess) {
                lastSyncedFingerprint = fingerprint
                if (_detectedPlayback.value.fingerprint() != fingerprint) {
                    retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS
                    continue
                }
                if (!playback.toMusicUpdateRequest().isPlaying) return
                withTimeoutOrNull(ACTIVE_PLAYBACK_REFRESH_MILLIS) {
                    syncSignals.receive()
                }
                retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS
                continue
            }

            val httpError = result.exceptionOrNull() as? HttpException
            if (httpError != null && httpError.code() in 400..499 &&
                httpError.code() !in setOf(408, 429)
            ) {
                return
            }

            withTimeoutOrNull(retryDelayMillis) { syncSignals.receive() }
            retryDelayMillis = (retryDelayMillis * 2).coerceAtMost(MAX_RETRY_DELAY_MILLIS)
        }
    }

    private fun readPersistedPlayback(): DetectedPlaybackState {
        if (!preferences.getBoolean(KEY_ACTIVE, false)) return DetectedPlaybackState()
        val updatedAt = preferences.getLong(KEY_UPDATED_AT_EPOCH_MS, 0L)
        val ageMillis = System.currentTimeMillis() - updatedAt
        if (updatedAt <= 0L || ageMillis !in 0..PERSISTED_PLAYBACK_MAX_AGE_MILLIS) {
            return DetectedPlaybackState()
        }
        val title = preferences.getString(KEY_TITLE, null).toSafeMetadata(MAX_TITLE_LENGTH)
            ?: return DetectedPlaybackState()
        val artist = preferences.getString(KEY_ARTIST, null).toSafeMetadata(MAX_ARTIST_LENGTH)
            ?: return DetectedPlaybackState()
        val source = preferences.getString(KEY_SOURCE, null).toSafeMetadata(MAX_SOURCE_LENGTH)
            ?: DEFAULT_SOURCE_TYPE
        // Persisted metadata is only a UI hint. It must be re-verified by the notification
        // listener in this process before it can be sent as actively playing.
        preferences.edit().putBoolean(KEY_ACTIVE, false).apply()
        return DetectedPlaybackState(
            Track(
                id = "detected-${title.hashCode()}-${artist.hashCode()}",
                title = title,
                artist = artist,
                platform = source,
            ),
            isPlaying = false,
            verifiedInCurrentProcess = false,
        )
    }

    private fun persist(playback: DetectedPlaybackState) {
        val track = playback.track
        preferences.edit().apply {
            if (playback.isPlaying && track != null) {
                putString(KEY_TITLE, track.title)
                putString(KEY_ARTIST, track.artist)
                putString(KEY_SOURCE, track.platform)
                putLong(KEY_UPDATED_AT_EPOCH_MS, System.currentTimeMillis())
                putBoolean(KEY_ACTIVE, true)
            } else {
                remove(KEY_TITLE)
                remove(KEY_ARTIST)
                remove(KEY_SOURCE)
                remove(KEY_UPDATED_AT_EPOCH_MS)
                putBoolean(KEY_ACTIVE, false)
            }
        }.apply()
    }

    private fun DetectedPlaybackState.fingerprint(): String =
        "${track?.title.orEmpty()}\u001F${track?.artist.orEmpty()}\u001F${track?.platform.orEmpty()}\u001F$isPlaying\u001F$verifiedInCurrentProcess"

    companion object {
        const val PREFERENCES_NAME = "melody_bubble_now_playing_fallback"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "text"
        const val KEY_SOURCE = "source"
        const val KEY_UPDATED_AT_EPOCH_MS = "updated_at_epoch_ms"
        const val KEY_ACTIVE = "active"

        private const val DEFAULT_SOURCE_TYPE = "ANDROID_MEDIA_SESSION"
        private const val MAX_TITLE_LENGTH = 160
        private const val MAX_ARTIST_LENGTH = 160
        private const val MAX_SOURCE_LENGTH = 32
        private const val INITIAL_RETRY_DELAY_MILLIS = 1_000L
        private const val MAX_RETRY_DELAY_MILLIS = 30_000L
        private const val ACTIVE_PLAYBACK_REFRESH_MILLIS = 60_000L
        private const val PERSISTED_PLAYBACK_MAX_AGE_MILLIS = 90_000L
        @Volatile
        private var instance: PresenceSyncCoordinator? = null

        fun get(context: Context): PresenceSyncCoordinator =
            instance ?: synchronized(this) {
                instance ?: PresenceSyncCoordinator(context).also { instance = it }
            }
    }
}

internal fun DetectedPlaybackState.toMusicUpdateRequest(): MusicUpdateRequest {
    val detected = track?.takeIf { isPlaying && verifiedInCurrentProcess }
    return MusicUpdateRequest(
        title = detected?.title.orEmpty(),
        artist = detected?.artist.orEmpty(),
        sourceType = detected?.platform.toRemoteSourceType(),
        isPlaying = detected != null,
    )
}

private fun String?.toRemoteSourceType(): String = when (this) {
    "MEDIA_SESSION", "ANDROID_MEDIA_SESSION" -> "ANDROID_MEDIA_SESSION"
    "MEDIA_NOTIFICATION", "ANDROID_MEDIA_NOTIFICATION" -> "ANDROID_MEDIA_NOTIFICATION"
    else -> this?.take(32)?.ifBlank { null } ?: "ANDROID_MEDIA_SESSION"
}

private fun String?.toSafeMetadata(maxLength: Int): String? =
    this
        ?.replace(METADATA_CONTROL_CHARACTERS, " ")
        ?.trim()
        ?.take(maxLength)
        ?.takeIf(String::isNotEmpty)

private val METADATA_CONTROL_CHARACTERS = Regex("[\\p{Cc}\\p{Cf}]+")
