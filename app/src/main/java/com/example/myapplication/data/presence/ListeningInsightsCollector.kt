package com.example.myapplication.data.presence

import android.content.Context
import com.example.myapplication.data.remote.ApiClient
import com.example.myapplication.data.remote.ListenEventBatchRequest
import com.example.myapplication.data.remote.ListenEventRequest
import com.example.myapplication.data.remote.TasteApi
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

internal class ListeningInsightsCollector(
    context: Context,
    private val api: TasteApi = ApiClient.createTasteApi(),
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val gson = Gson()
    // Consent is re-established from the authenticated user's remote profile on every process
    // start. Never inherit a global device preference into a different account session.
    private var enabled = false
    private var active: ActiveListenSession? = null
    private val pending = readPending().toMutableList()

    fun setEnabled(value: Boolean) = synchronized(this) {
        enabled = value
        preferences.edit().putBoolean(KEY_ENABLED, value).apply()
        if (!value) {
            active = null
            pending.clear()
            persistPending()
        }
    }

    fun onPlayback(playback: DetectedPlaybackState) = synchronized(this) {
        if (!enabled) return
        val track = playback.track?.takeIf {
            playback.isPlaying && playback.verifiedInCurrentProcess
        }
        val now = playback.observedAtEpochMs.coerceAtMost(System.currentTimeMillis() + 5_000L)
        val current = active
        if (current != null) {
            current.accrue(now)
            val sameTrack = track != null && current.identity == trackIdentity(track.title, track.artist)
            if (sameTrack) {
                current.durationMs = playback.durationMs ?: current.durationMs
                current.album = track.album ?: current.album
                current.sourcePackage = playback.sourcePackage ?: current.sourcePackage
                return
            }
            finalizeActive(now)
        }
        if (track != null) {
            active = ActiveListenSession(
                identity = trackIdentity(track.title, track.artist),
                title = track.title,
                artist = track.artist,
                album = track.album,
                sourceType = track.platform.toRemoteSourceType(),
                sourcePackage = playback.sourcePackage,
                startedAtEpochMs = now,
                lastObservedAtEpochMs = now,
                durationMs = playback.durationMs,
            )
        }
    }

    suspend fun checkpointAndUpload(authorization: String) {
        val batch = synchronized(this) {
            if (!enabled) return
            val now = System.currentTimeMillis()
            val current = active
            if (current != null) {
                current.accrue(now)
                if (current.playedMs >= CHECKPOINT_MILLIS) {
                    finalizeActive(now)
                    active = current.copy(
                        startedAtEpochMs = now,
                        lastObservedAtEpochMs = now,
                        playedMs = 0L,
                    )
                }
            }
            pending.take(MAX_BATCH_SIZE)
        }
        if (batch.isEmpty()) return
        val response = api.uploadListenEvents(
            authorization,
            ListenEventBatchRequest(batch),
        )
        synchronized(this) {
            if (!response.collectionEnabled) {
                setEnabled(false)
                return
            }
            val uploadedIds = batch.mapTo(hashSetOf(), ListenEventRequest::clientEventId)
            pending.removeAll { it.clientEventId in uploadedIds }
            persistPending()
        }
    }

    private fun finalizeActive(endedAtEpochMs: Long) {
        val session = active ?: return
        active = null
        if (session.playedMs < MIN_RECORDED_LISTEN_MILLIS) return
        pending += ListenEventRequest(
            clientEventId = UUID.randomUUID().toString(),
            title = session.title,
            artist = session.artist,
            album = session.album,
            sourceType = session.sourceType,
            sourcePackage = session.sourcePackage,
            startedAt = session.startedAtEpochMs.toIso8601Utc(),
            endedAt = endedAtEpochMs.toIso8601Utc(),
            playedMs = session.playedMs.coerceAtMost(MAX_MEDIA_MILLIS),
            durationMs = session.durationMs,
            completionRatio = session.durationMs?.takeIf { it > 0L }?.let {
                (session.playedMs.toDouble() / it.toDouble()).coerceIn(0.0, 1.0)
            },
        )
        while (pending.size > MAX_PENDING_EVENTS) pending.removeAt(0)
        persistPending()
    }

    private fun readPending(): List<ListenEventRequest> = runCatching {
        preferences.getString(KEY_PENDING, null)
            ?.let { gson.fromJson(it, PersistedQueue::class.java) }
            ?.events
            .orEmpty()
    }.getOrDefault(emptyList())

    private fun persistPending() {
        preferences.edit().putString(KEY_PENDING, gson.toJson(PersistedQueue(pending))).apply()
    }

    private data class PersistedQueue(val events: List<ListenEventRequest> = emptyList())

    private data class ActiveListenSession(
        val identity: String,
        val title: String,
        val artist: String,
        var album: String?,
        val sourceType: String,
        var sourcePackage: String?,
        var startedAtEpochMs: Long,
        var lastObservedAtEpochMs: Long,
        var playedMs: Long = 0L,
        var durationMs: Long?,
    ) {
        fun accrue(observedAtEpochMs: Long) {
            val delta = (observedAtEpochMs - lastObservedAtEpochMs).coerceIn(0L, MAX_OBSERVATION_GAP_MILLIS)
            playedMs = (playedMs + delta).coerceAtMost(MAX_MEDIA_MILLIS)
            lastObservedAtEpochMs = maxOf(lastObservedAtEpochMs, observedAtEpochMs)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "sync-listening-insights"
        const val KEY_ENABLED = "enabled"
        const val KEY_PENDING = "pending-events"
        const val MIN_RECORDED_LISTEN_MILLIS = 10_000L
        const val CHECKPOINT_MILLIS = 60_000L
        const val MAX_OBSERVATION_GAP_MILLIS = 90_000L
        const val MAX_MEDIA_MILLIS = 86_400_000L
        const val MAX_BATCH_SIZE = 50
        const val MAX_PENDING_EVENTS = 200
    }
}

private fun trackIdentity(title: String, artist: String): String =
    "${title.trim().lowercase()}\u0000${artist.trim().lowercase()}"

private fun String?.toRemoteSourceType(): String = when (this) {
    "MEDIA_SESSION", "ANDROID_MEDIA_SESSION" -> "ANDROID_MEDIA_SESSION"
    "MEDIA_NOTIFICATION", "ANDROID_MEDIA_NOTIFICATION" -> "ANDROID_MEDIA_NOTIFICATION"
    else -> this?.take(32)?.ifBlank { null } ?: "ANDROID_MEDIA_SESSION"
}

private fun Long.toIso8601Utc(): String = SimpleDateFormat(
    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
    Locale.US,
).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(this))
