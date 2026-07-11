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
