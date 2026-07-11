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
