package com.example.myapplication.data

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import com.example.myapplication.MelodyApplication
import com.example.myapplication.core.model.ChatMessage
import com.example.myapplication.core.model.ChatPreview
import com.example.myapplication.core.model.BlockedUser
import com.example.myapplication.core.model.ConnectionState
import com.example.myapplication.core.model.DeliveryState
import com.example.myapplication.core.model.InboxNotification
import com.example.myapplication.core.model.MainTab
import com.example.myapplication.core.model.MAX_NEARBY_RADIUS_METERS
import com.example.myapplication.core.model.MelodyReducers
import com.example.myapplication.core.model.MelodyUiState
import com.example.myapplication.core.model.NearbyLoadState
import com.example.myapplication.core.model.NearbyProximityConfidence
import com.example.myapplication.core.model.NearbyMeasurementDiagnostics
import com.example.myapplication.core.model.NearbyMeasurementMethod
import com.example.myapplication.core.model.NearbyProximityStabilizer
import com.example.myapplication.core.model.NotificationType
import com.example.myapplication.core.model.PopularTrack
import com.example.myapplication.core.model.ProfileMelodyAlias
import com.example.myapplication.core.model.ProfileArtist
import com.example.myapplication.core.model.ProfileNowPlaying
import com.example.myapplication.core.model.ProfilePrivacySettings
import com.example.myapplication.core.model.ProfileStats
import com.example.myapplication.core.model.ProfileTrack
import com.example.myapplication.core.model.PublicProfile
import com.example.myapplication.core.model.CommonTasteMetric
import com.example.myapplication.core.model.CommonTasteSummary
import com.example.myapplication.core.model.RelationshipStatus
import com.example.myapplication.core.model.ReportReason
import com.example.myapplication.core.model.SharingState
import com.example.myapplication.core.model.SharedFollowerPreview
import com.example.myapplication.core.model.SocialConnection
import com.example.myapplication.core.model.TasteFingerprint
import com.example.myapplication.core.model.TasteMetric
import com.example.myapplication.core.model.Track
import com.example.myapplication.core.model.abstractDisplayPosition
import com.example.myapplication.core.model.randomDisplayPosition
import com.example.myapplication.data.presence.PresenceSyncCoordinator
import com.example.myapplication.data.realtime.RealtimeConnectionState
import com.example.myapplication.data.realtime.RealtimeEvent
import com.example.myapplication.data.realtime.RealtimeSyncRequest
import com.example.myapplication.data.realtime.StompRealtimeClient
import com.example.myapplication.data.realtime.notificationRelativeTime
import com.example.myapplication.data.realtime.toServerEpochMillis
import com.example.myapplication.data.remote.ApiEnvironment
import com.example.myapplication.data.remote.ApiClient
import com.example.myapplication.data.remote.LocationUpdateRequest
import com.example.myapplication.data.remote.DirectProximityBatchRequest
import com.example.myapplication.data.remote.DirectProximityUpdateRequest
import com.example.myapplication.data.remote.NearbyBeaconRequest
import com.example.myapplication.data.remote.MusicSearchRepository
import com.example.myapplication.data.remote.NearbyReactionRequest
import com.example.myapplication.data.remote.ResolveNearbyBeaconsRequest
import com.example.myapplication.data.remote.PresenceSettingsUpdateRequest
import com.example.myapplication.data.remote.RemoteChatSummary
import com.example.myapplication.data.remote.RemoteCommonTasteSummary
import com.example.myapplication.data.remote.RemoteFollowResponse
import com.example.myapplication.data.remote.RemoteNearbyBubble
import com.example.myapplication.data.remote.RemotePopularTrack
import com.example.myapplication.data.remote.ReportSubmitRequest
import com.example.myapplication.data.remote.SendChatMessageRequest
import com.example.myapplication.data.remote.ProfileUpdateRequest
import com.example.myapplication.data.remote.AvatarCustomizationRequest
import com.example.myapplication.data.remote.ProfileCurationUpdateRequest
import com.example.myapplication.data.remote.ProfilePrivacyUpdateRequest
import com.example.myapplication.data.remote.RemoteProfile
import com.example.myapplication.data.remote.RemoteProfileMelodyAlias
import com.example.myapplication.data.remote.RemoteProfileStats
import com.example.myapplication.data.remote.RemoteProfileTrack
import com.example.myapplication.data.remote.RemoteProfileArtist
import com.example.myapplication.data.remote.RemotePublicProfile
import com.example.myapplication.data.remote.RemoteTasteFingerprint
import com.example.myapplication.data.remote.TasteFeedbackRequest
import com.example.myapplication.data.remote.RemoteSocialConnection
import com.example.myapplication.data.remote.jwtSubject
import com.example.myapplication.nearby.PassiveNearbyDiscoveryManager
import com.example.myapplication.nearby.PeerProximityMeasurement
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.gson.Gson
import com.example.myapplication.service.SharingForegroundService
import com.example.myapplication.service.NearbyLocationPolicy
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException

private const val CURRENT_LOCATION_TIMEOUT_MILLIS = 5_000L
private const val PRESENCE_LOCATION_CACHE_MAX_AGE_MILLIS = 60_000L
private const val STATIONARY_LOCATION_FALLBACK_MAX_AGE_MILLIS = 2 * 60_000L
private const val ACTIVE_PRESENCE_REFRESH_INTERVAL_MILLIS = 1_000L
private const val DIRECT_PROXIMITY_REPORT_INTERVAL_MILLIS = 1_000L
private const val DIRECT_BEACON_RESOLVE_INTERVAL_MILLIS = 1_100L
private const val DIRECT_BEACON_RESOLVE_INITIAL_RETRY_MILLIS = 500L
private const val DIRECT_BEACON_RESOLVE_MAX_RETRY_MILLIS = 5_000L
private const val MAX_DIRECT_PROXIMITY_BATCH_SIZE = 40
private const val NEARBY_BUBBLE_MISSING_RETENTION_MILLIS = 15_000L
private const val POPULAR_TRACK_REFRESH_INTERVAL_MILLIS = 10_000L
private const val CHAT_FALLBACK_SYNC_INTERVAL_MILLIS = 10_000L
private const val KEY_PROFILE_CURATION_DIRTY = "profile-curation-dirty"
private const val KEY_PROFILE_PRIVACY_DIRTY = "profile-privacy-dirty"
private const val KEY_PROFILE_AVATAR_DIRTY = "profile-avatar-dirty"
private const val KEY_HIDDEN_CHAT_ROOM_IDS = "hidden-chat-room-ids"

internal fun NearbyLoadState.keepSettledDuringRefresh(
    fallback: NearbyLoadState,
): NearbyLoadState = when (this) {
    NearbyLoadState.READY,
    NearbyLoadState.EMPTY,
    NearbyLoadState.ERROR,
    -> this
    NearbyLoadState.IDLE,
    NearbyLoadState.LOADING,
    -> fallback
}

internal fun chatSentAtLabel(
    sentAt: String?,
    nowEpochMillis: Long = System.currentTimeMillis(),
): String {
    val sentAtEpochMillis = sentAt.toServerEpochMillis() ?: return ""
    val sent = Calendar.getInstance().apply { timeInMillis = sentAtEpochMillis }
    val now = Calendar.getInstance().apply { timeInMillis = nowEpochMillis }
    val sameDay = sent.get(Calendar.ERA) == now.get(Calendar.ERA) &&
        sent.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        sent.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "a h:mm" else "M월 d일 a h:mm"
    return SimpleDateFormat(pattern, Locale.KOREAN).format(sent.time)
}

private fun String?.isDeezerArtistImage(): Boolean =
    this?.startsWith("https://cdn-images.dzcdn.net/images/artist/") == true

private fun List<ProfileArtist>.hasSameArtistSelection(other: List<ProfileArtist>): Boolean =
    map { it.providerArtistId to it.name.trim().lowercase() } ==
        other.map { it.providerArtistId to it.name.trim().lowercase() }

internal fun reactionTypeForLabel(label: String): String? = when (label.trim()) {
    "이 곡 좋아요", "LIKE" -> "LIKE"
    "취향이 닮았어요", "SAME_TASTE" -> "SAME_TASTE"
    "선곡 멋져요", "GREAT_PICK" -> "GREAT_PICK"
    "같이 듣고 싶어요", "LISTEN_TOGETHER" -> "LISTEN_TOGETHER"
    else -> null
}

internal fun List<PopularTrack>.preservingResolvedArtwork(
    previous: List<PopularTrack>,
): List<PopularTrack> {
    val previousArtwork = previous.associate { it.track.id to it.track.artworkUrl }
    return map { incoming ->
        val retained = incoming.track.artworkUrl ?: previousArtwork[incoming.track.id]
        if (retained == incoming.track.artworkUrl) incoming
        else incoming.copy(track = incoming.track.copy(artworkUrl = retained))
    }
}

internal data class DirectNearbyCandidate(
    val beaconId: String,
    val user: com.example.myapplication.core.model.NearbyListener,
    val measurement: PeerProximityMeasurement?,
)

internal fun com.example.myapplication.core.model.NearbyListener.stableNearbyIdentity(): String =
    profileHandle?.trim()?.takeIf(String::isNotEmpty)?.lowercase()?.let { "profile:$it" }
        ?: "session:$nearbyHandle"

internal fun deduplicateNearbyListeners(
    listeners: List<com.example.myapplication.core.model.NearbyListener>,
    preferredHandles: Set<String> = emptySet(),
): List<com.example.myapplication.core.model.NearbyListener> = listeners
    .groupBy { it.stableNearbyIdentity() }
    .values
    .map { sameUser ->
        sameUser.maxWithOrNull(
            compareBy<com.example.myapplication.core.model.NearbyListener> {
                it.nearbyHandle in preferredHandles
            }.thenBy { !it.nearbyHandle.startsWith("d_") }
                .thenBy { it.canReact }
                .thenBy { it.isPlaying },
        ) ?: error("nearby identity group cannot be empty")
    }

internal fun preferDirectNearbyUsers(
    candidates: List<DirectNearbyCandidate>,
    currentByHandle: Map<String, com.example.myapplication.core.model.NearbyListener>,
): Map<String, com.example.myapplication.core.model.NearbyListener> {
    val currentByIdentity = currentByHandle.values.associateBy { it.stableNearbyIdentity() }
    return candidates
    .groupBy { it.user.stableNearbyIdentity() }
    .values
    .map { sameUserCandidates ->
        val preferred = sameUserCandidates.maxWithOrNull(
            compareBy<DirectNearbyCandidate> { it.measurement != null }
                .thenBy { it.measurement?.observedAtEpochMillis ?: Long.MIN_VALUE }
                .thenBy { it.beaconId },
        ) ?: error("direct candidate group cannot be empty")
        val existing = currentByHandle[preferred.user.nearbyHandle]
            ?: currentByIdentity[preferred.user.stableNearbyIdentity()]
        // A beacon resolution is authoritative only for direct proximity. Its embedded profile
        // snapshot can be older than a realtime music event, so never let it roll playback back.
        val proximityUser = existing?.let { current ->
            preferred.user.copy(
                relationship = current.relationship,
                isPlaying = current.isPlaying,
                currentTrack = current.currentTrack,
            )
        } ?: preferred.user
        if (proximityUser.proximityConfidence == NearbyProximityConfidence.LOW &&
            existing != null && proximityUser.proximity != existing.proximity
        ) {
            proximityUser.copy(
                proximity = existing.proximity,
                displayPosition = existing.displayPosition,
            )
        } else if (existing != null && proximityUser.proximity == existing.proximity) {
            proximityUser.copy(displayPosition = existing.displayPosition)
        } else {
            proximityUser.copy(
                displayPosition = randomDisplayPosition(proximityUser.proximity),
            )
        }
    }
    .associateBy { it.nearbyHandle }
}

internal fun shouldApplyDirectResolveResult(
    requestGeneration: Long,
    currentGeneration: Long,
    requestedBeaconIds: Set<String>,
    desiredBeaconIds: Set<String>,
    tokenIsCurrent: Boolean,
    sharingIsActive: Boolean,
): Boolean = requestGeneration == currentGeneration &&
    requestedBeaconIds == desiredBeaconIds &&
    tokenIsCurrent &&
    sharingIsActive

interface MelodyRepository {
    val state: StateFlow<MelodyUiState>

    fun completeOnboarding()
    fun selectTab(tab: MainTab)
    fun selectNearby(handle: String?)
    fun openChat(roomId: String)
    fun closeChat(roomId: String)
    fun leaveChat(roomId: String)
    fun startSharing()
    fun stopSharing()
    fun setSharingPermissionRequired()
    fun setSharingStartFailed()
    fun retrySharing()
    fun follow(handle: String)
    fun react(handle: String, reactionLabel: String)
    fun block(handle: String)
    fun report(handle: String, reason: ReportReason = ReportReason.OTHER, description: String? = null)
    fun loadBlockedUsers()
    fun unblock(blockId: String)
    fun loadSocialConnections()
    fun unfollowRelationship(relationshipId: String)
    fun loadPublicProfile(profileHandle: String)
    fun recordTasteFeedback(profileHandle: String, action: String)
    fun clearPublicProfile()
    fun followPublicProfile()
    fun sendChat(roomId: String, content: String)
    fun markInboxRead()
    fun clearNotifications()
    fun deleteNotification(notificationId: String)
    fun setDiscoverable(enabled: Boolean)
    fun setAllowReactions(enabled: Boolean)
    fun setMusicVisibility(label: String)
    fun updatePresenceSettings(radiusMeters: Int, discoverabilityScope: String, musicVisibility: String)
    fun updateProfile(displayName: String, colorHex: Long, bio: String, genres: List<String>)
    fun customizeAvatar(customization: AvatarCustomization)
    fun updateProfileCuration(signatureTracks: List<ProfileTrack>, favoriteArtists: List<ProfileArtist>)
    fun updateProfilePrivacy(settings: ProfilePrivacySettings)
    fun clearFeedback()
    fun authenticate(accessToken: String)
    fun refreshSession(accessToken: String)
    fun logout()
    fun close()
}

class DemoMelodyRepository(
    context: Context,
    environment: ApiEnvironment = ApiEnvironment()
) : MelodyRepository {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(
        "melody-bubble-session",
        Context.MODE_PRIVATE
    )
    private val gson = Gson()
    @Volatile private var activeOwnerId: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val presenceSyncCoordinator = PresenceSyncCoordinator.get(applicationContext)
    private val applicationRealtimeClient =
        (applicationContext as? MelodyApplication)?.realtimeClient
    private val realtimeInboxStore =
        (applicationContext as? MelodyApplication)?.realtimeInboxStore
    private val realtimeClient = applicationRealtimeClient
        ?: StompRealtimeClient(environment.stompWsUrl)
    private val ownsRealtimeClient = applicationRealtimeClient == null
    private val initialDetectedPlayback = presenceSyncCoordinator.detectedPlayback.value
    private val _state = MutableStateFlow(
        DemoCatalog.initialState(
            isOnboardingComplete = preferences.getBoolean("onboarding-complete", false)
        ).copy(
            profile = restoreProfile(DemoCatalog.initialState(false).profile),
            currentTrack = initialDetectedPlayback.track
                ?: DemoCatalog.initialState(false).currentTrack,
            currentTrackPlaying = initialDetectedPlayback.isPlaying,
            detectedTrack = initialDetectedPlayback.track,
            detectedTrackPlaying = initialDetectedPlayback.isPlaying,
            dataSourceLabel = if (environment.isConfigured) {
                "DEMO FALLBACK"
            } else {
                "DEMO LIVE"
            }
        )
    )
    override val state: StateFlow<MelodyUiState> = _state.asStateFlow()

    private var sharingJob: Job? = null
    private val presenceSyncMutex = Mutex()
    private var lastPresenceSyncStartedAtElapsedMillis = 0L
    private val locationSyncSignal = Channel<Unit>(Channel.CONFLATED)
    private val directMeasurementReportSignal = Channel<Unit>(Channel.CONFLATED)
    private val directStateLock = Any()
    private val directResolveStartMutex = Mutex()
    private val directReportStartMutex = Mutex()
    private var lastDirectResolveStartedAtElapsedMillis = 0L
    private var lastDirectReportStartedAtElapsedMillis = 0L
    @Volatile private var latestLocationFix: LocationFix? = null
    private var hybridDiscoveryJob: Job? = null
    private var directResolveJob: Job? = null
    private val directResolveGeneration = AtomicLong(0L)
    @Volatile private var desiredDirectBeaconIds = emptySet<String>()
    private var directDiscoveryActive = false
    private var chatSyncJob: Job? = null
    private val chatRealtimeVersion = AtomicLong(0)
    private val nearbyRealtimeVersion = AtomicLong(0)
    private val socialConnectionsRequestVersion = AtomicLong(0)
    private val publicProfileRequestVersion = AtomicLong(0)
    private val locationSequence = AtomicLong(System.currentTimeMillis() * 1_000L)
    private val directProximitySequence = AtomicLong(System.currentTimeMillis() * 1_000L)
    private val popularRealtimeVersion = AtomicLong(0)
    private val lastPopularRefreshStartedAt = AtomicLong(0L)
    private val chatReadLock = Any()
    private val hiddenChatRoomLock = Any()
    private val chatReadJobs = mutableMapOf<String, Job>()
    private val chatReadDirty = mutableSetOf<String>()
    @Volatile private var activeChatRoomId: String? = null
    private val clientSessionId = preferences.getString("client-session-id", null)
        ?: UUID.randomUUID().toString().also { preferences.edit().putString("client-session-id", it).apply() }
    private val nearbyApi = ApiClient.createNearbyApi(environment)
    private val passiveNearbyDiscovery = PassiveNearbyDiscoveryManager(applicationContext)
    @Volatile private var directNearbyByHandle = emptyMap<String, com.example.myapplication.core.model.NearbyListener>()
    @Volatile private var directUsersByBeacon = emptyMap<String, com.example.myapplication.core.model.NearbyListener>()
    @Volatile private var directMeasurementsByBeacon = emptyMap<String, PeerProximityMeasurement>()
    @Volatile private var authoritativeNearbyHandles = emptySet<String>()
    private val profileByNearbyHandle = ConcurrentHashMap<String, String>()
    private val reportedDirectMeasurementAt = ConcurrentHashMap<String, Long>()
    private val lastMusicEventAtByHandle = ConcurrentHashMap<String, Long>()
    private val explicitlyRemovedNearbyHandles = ConcurrentHashMap.newKeySet<String>()
    private var passiveDiscoveryStarted = false
    // Server distance bands are already privacy-coarsened. Apply a crossed band immediately so
    // another user's movement is visible on the next snapshot instead of one cycle later.
    private val nearbyProximityStabilizer = NearbyProximityStabilizer(
        confirmationsRequired = 1,
        missingRetentionMillis = NEARBY_BUBBLE_MISSING_RETENTION_MILLIS,
        nowMillis = SystemClock::elapsedRealtime,
    )
    private val profileApi = ApiClient.createProfileApi(environment)
    private val tasteApi = ApiClient.createTasteApi(environment)
    private val socialApi = ApiClient.createSocialApi(environment)
    private val musicSearchRepository = MusicSearchRepository()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
    @Volatile private var accessToken: String? = null
    private val latestLocationFixLock = Any()
    private var locationReceiverRegistered = false
    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != SharingForegroundService.ACTION_LOCATION_FIX) return
            val latitude = intent.getDoubleExtra(SharingForegroundService.EXTRA_LATITUDE, Double.NaN)
            val longitude = intent.getDoubleExtra(SharingForegroundService.EXTRA_LONGITUDE, Double.NaN)
            val accuracy = intent.getFloatExtra(SharingForegroundService.EXTRA_ACCURACY_METERS, Float.NaN)
                .takeIf(Float::isFinite)
            val observedAt = intent.getLongExtra(
                SharingForegroundService.EXTRA_LOCATION_TIME_EPOCH_MS,
                System.currentTimeMillis(),
            )
            val source = intent.getStringExtra(SharingForegroundService.EXTRA_LOCATION_SOURCE).orEmpty()
            if (!latitude.isFinite() || !longitude.isFinite()) return
            if (_state.value.sharingState == SharingState.STARTING ||
                _state.value.sharingState == SharingState.ACTIVE
            ) {
                rememberLatestLocationFix(
                    LocationFix(latitude, longitude, accuracy, source, observedAt),
                )
                locationSyncSignal.trySend(Unit)
            }
        }
    }
    private val sharingStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != SharingForegroundService.ACTION_SHARING_STATE_CHANGED) return
            if (!intent.getBooleanExtra(SharingForegroundService.EXTRA_SHARING_ACTIVE, false)) {
                stopSharing()
            }
        }
    }
    init {
        ContextCompat.registerReceiver(
            applicationContext,
            locationReceiver,
            IntentFilter(SharingForegroundService.ACTION_LOCATION_FIX),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        locationReceiverRegistered = true
        ContextCompat.registerReceiver(
            applicationContext,
            sharingStateReceiver,
            IntentFilter(SharingForegroundService.ACTION_SHARING_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch {
            realtimeClient.events.collect(::handleRealtimeEvent)
        }
        scope.launch {
            realtimeClient.connectionState.collect(::handleRealtimeConnectionState)
        }
        scope.launch {
            realtimeClient.syncRequests.collect { request ->
                if (request is RealtimeSyncRequest.FullSync) performFullRealtimeSync()
            }
        }
        scope.launch {
            combine(
                passiveNearbyDiscovery.beaconIds,
                passiveNearbyDiscovery.proximityMeasurements.map { it.keys }.distinctUntilChanged(),
            ) { connectionIds, rangingIds -> connectionIds + rangingIds }
                .distinctUntilChanged()
                .collect(::resolveDirectBeacons)
        }
        scope.launch {
            passiveNearbyDiscovery.proximityMeasurements.collect { measurements ->
                val shouldReport = synchronized(directStateLock) {
                    if (!directDiscoveryActive) {
                        directMeasurementsByBeacon = emptyMap()
                        false
                    } else {
                        directMeasurementsByBeacon = measurements.filterKeys {
                            it in desiredDirectBeaconIds
                        }
                        applyDirectProximityMeasurementsLocked()
                        directMeasurementsByBeacon.isNotEmpty()
                    }
                }
                if (shouldReport) directMeasurementReportSignal.trySend(Unit)
            }
        }
        scope.launch {
            for (ignored in directMeasurementReportSignal) {
                while (isActive &&
                    _state.value.sharingState in setOf(SharingState.STARTING, SharingState.ACTIVE) &&
                    directMeasurementsByBeacon.isNotEmpty()
                ) {
                    awaitDirectReportSlot()
                    val reported = reportDirectProximityMeasurements(directMeasurementsByBeacon)
                    if (reported) break
                }
            }
        }
        scope.launch {
            for (ignored in locationSyncSignal) {
                if (_state.value.sharingState == SharingState.STARTING ||
                    _state.value.sharingState == SharingState.ACTIVE
                ) {
                    syncLatestLocationPresence()
                }
            }
        }
        realtimeInboxStore?.let { store ->
            scope.launch {
                store.notifications.collect { durable ->
                    if (accessToken == null) return@collect
                    _state.update { current ->
                        val durableIds = durable.map(InboxNotification::id).toSet()
                        current.copy(
                            notifications = durable + current.notifications
                                .filterNot { it.id in durableIds }
                        )
                    }
                }
            }
        }
        scope.launch {
            presenceSyncCoordinator.detectedPlayback.collect { playback ->
                _state.update {
                    it.copy(
                        currentTrack = playback.track ?: it.currentTrack,
                        currentTrackPlaying = playback.isPlaying,
                        detectedTrack = playback.track,
                        detectedTrackPlaying = playback.isPlaying,
                    )
                }
            }
        }
    }

    override fun completeOnboarding() {
        preferences.edit().putBoolean("onboarding-complete", true).apply()
        _state.update { it.copy(isOnboardingComplete = true) }
    }

    override fun selectTab(tab: MainTab) {
        _state.update { it.copy(selectedTab = tab) }
    }

    override fun selectNearby(handle: String?) {
        _state.update { it.copy(selectedNearbyHandle = handle) }
    }

    override fun openChat(roomId: String) {
        setChatRoomHidden(roomId, hidden = false)
        activeChatRoomId = roomId
        markChatRoomRead(roomId)
    }

    override fun closeChat(roomId: String) {
        if (activeChatRoomId == roomId) activeChatRoomId = null
    }

    override fun leaveChat(roomId: String) {
        if (activeChatRoomId == roomId) activeChatRoomId = null
        setChatRoomHidden(roomId, hidden = true)
        markChatRoomRead(roomId)
    }

    private fun setChatRoomHidden(roomId: String, hidden: Boolean) {
        persistChatRoomHidden(roomId, hidden)
        _state.update { current ->
            current.copy(
                chats = current.chats.map { chat ->
                    if (chat.roomId == roomId) chat.copy(isHidden = hidden) else chat
                },
            )
        }
    }

    private fun persistChatRoomHidden(roomId: String, hidden: Boolean) {
        synchronized(hiddenChatRoomLock) {
            val updated = hiddenChatRoomIds().toMutableSet().apply {
                if (hidden) add(roomId) else remove(roomId)
            }
            persistHiddenChatRoomIds(updated)
        }
    }

    private fun hiddenChatRoomIds(): Set<String> = synchronized(hiddenChatRoomLock) {
        preferences.getStringSet(profileScopedKey(KEY_HIDDEN_CHAT_ROOM_IDS), emptySet())
            ?.toSet()
            .orEmpty()
    }

    private fun persistHiddenChatRoomIds(roomIds: Set<String>) {
        synchronized(hiddenChatRoomLock) {
            preferences.edit()
                .putStringSet(profileScopedKey(KEY_HIDDEN_CHAT_ROOM_IDS), roomIds.toSet())
                .apply()
        }
    }

    override fun startSharing() {
        if (_state.value.sharingState == SharingState.ACTIVE || sharingJob?.isActive == true) return
        if (accessToken.isNullOrBlank()) {
            setSharingStartFailed()
            return
        }
        synchronized(directStateLock) {
            directDiscoveryActive = true
        }
        sharingJob = scope.launch {
            _state.update {
                it.copy(
                    sharingState = SharingState.STARTING,
                    connectionState = ConnectionState.CONNECTING,
                    nearbyLoadState = NearbyLoadState.LOADING,
                    nearbyErrorMessage = null,
                    feedbackMessage = "안전한 주변 공유를 준비하고 있어요"
                )
            }
            startHybridDiscovery(accessToken.orEmpty())
            val synced = syncPresence()
            _state.update {
                it.copy(
                    sharingState = SharingState.ACTIVE,
                    connectionState = if (
                        realtimeClient.connectionState.value is RealtimeConnectionState.Connected
                    ) ConnectionState.LIVE else ConnectionState.CONNECTING,
                    feedbackMessage = if (synced) {
                        "실제 주변 기기와 음악을 공유 중이에요"
                    } else {
                        "Nearby 탐색 중이에요. 위치가 확인되면 거리 정보도 갱신돼요"
                    }
                )
            }
            var refreshDelayMillis = 1_000L
            while (isActive && _state.value.sharingState == SharingState.ACTIVE) {
                delay(refreshDelayMillis)
                val recovered = syncPresence(requirePrecise = true)
                if (recovered && hybridDiscoveryJob?.isActive != true) {
                    startHybridDiscovery(accessToken.orEmpty())
                }
                refreshDelayMillis = ACTIVE_PRESENCE_REFRESH_INTERVAL_MILLIS
            }
        }
    }

    override fun stopSharing() {
        val shouldNotifyServer = sharingJob != null ||
            _state.value.sharingState == SharingState.STARTING ||
            _state.value.sharingState == SharingState.ACTIVE
        sharingJob?.cancel()
        sharingJob = null
        hybridDiscoveryJob?.cancel()
        hybridDiscoveryJob = null
        val pendingDirectResolve = synchronized(directStateLock) {
            directDiscoveryActive = false
            directResolveGeneration.incrementAndGet()
            desiredDirectBeaconIds = emptySet()
            val pending = directResolveJob
            directResolveJob = null
            directNearbyByHandle = emptyMap()
            directUsersByBeacon = emptyMap()
            directMeasurementsByBeacon = emptyMap()
            reportedDirectMeasurementAt.clear()
            pending
        }
        pendingDirectResolve?.cancel()
        passiveNearbyDiscovery.stop()
        passiveDiscoveryStarted = false
        synchronized(latestLocationFixLock) {
            latestLocationFix = null
        }
        nearbyProximityStabilizer.clear()
        explicitlyRemovedNearbyHandles.clear()
        lastMusicEventAtByHandle.clear()
        authoritativeNearbyHandles = emptySet()
        profileByNearbyHandle.clear()
        _state.update {
            it.copy(
                sharingState = SharingState.STOPPED,
                connectionState = ConnectionState.DISCONNECTED,
                nearbyLoadState = NearbyLoadState.IDLE,
                nearbyErrorMessage = null,
                nearbyListeners = emptyList(),
                feedbackMessage = "주변 공유를 중지했어요"
            )
        }
        if (shouldNotifyServer) {
            accessToken?.let { token ->
                scope.launch { runCatching { nearbyApi.stopPresence("Bearer $token", clientSessionId) } }
            }
        }
    }

    override fun setSharingPermissionRequired() {
        _state.update {
            it.copy(
                sharingState = SharingState.PERMISSION_REQUIRED,
                connectionState = ConnectionState.DISCONNECTED,
                nearbyLoadState = NearbyLoadState.IDLE,
                feedbackMessage = "주변 공유를 시작하려면 위치 권한이 필요해요"
            )
        }
    }

    override fun setSharingStartFailed() {
        _state.update {
            it.copy(
                sharingState = SharingState.FAILED,
                connectionState = ConnectionState.DISCONNECTED,
                nearbyLoadState = NearbyLoadState.ERROR,
                nearbyErrorMessage = "공유 서비스를 시작하지 못했어요. 권한과 시스템 설정을 확인한 뒤 다시 시도해 주세요.",
                feedbackMessage = "주변 공유를 시작하지 못했어요",
            )
        }
    }

    override fun retrySharing() {
        _state.update {
            it.copy(
                nearbyLoadState = NearbyLoadState.LOADING,
                nearbyErrorMessage = null,
                connectionState = ConnectionState.CONNECTING,
            )
        }
        if (_state.value.sharingState == SharingState.ACTIVE) {
            scope.launch { syncPresence(requirePrecise = true) }
        } else {
            startSharing()
        }
    }

    override fun follow(handle: String) {
        val token = accessToken ?: return
        val relationship = _state.value.nearbyListeners
            .firstOrNull { it.nearbyHandle == handle }?.relationship ?: return
        scope.launch {
            runCatching {
                if (relationship == RelationshipStatus.FOLLOWING ||
                    relationship == RelationshipStatus.MUTUAL
                ) {
                    socialApi.unfollow("Bearer $token", handle)
                } else {
                    socialApi.follow("Bearer $token", handle)
                }
            }.onSuccess { if (isCurrentSession(token)) applyFollowResponse(handle, it) }
                .onFailure { error ->
                    if (isCurrentSession(token)) showRequestError(error, "팔로우 상태를 변경하지 못했어요")
                }
        }
    }

    override fun react(handle: String, reactionLabel: String) {
        val selected = _state.value.nearbyListeners.firstOrNull { it.nearbyHandle == handle }
            ?: return
        val target = canonicalNearbyTarget(selected)
        val reactionType = reactionTypeForLabel(reactionLabel)
        if (reactionType == null) {
            _state.update { it.copy(feedbackMessage = "지원하지 않는 리액션이에요") }
            return
        }
        val token = accessToken ?: return
        scope.launch {
            runCatching {
                nearbyApi.sendReaction(
                    authorization = "Bearer $token",
                    nearbyHandle = target.nearbyHandle,
                    request = NearbyReactionRequest(
                        clientReactionId = UUID.randomUUID().toString(),
                        reactionType = reactionType,
                        trackTitle = target.currentTrack?.title,
                        trackArtist = target.currentTrack?.artist,
                    ),
                )
            }.onSuccess {
                if (!isCurrentSession(token)) return@onSuccess
                _state.update { state ->
                    state.copy(
                        feedbackMessage = "${target.displayAlias} 님에게 ‘$reactionLabel’ 리액션을 보냈어요"
                    )
                }
                refreshPopularTracks(token)
            }.onFailure { error ->
                if (isCurrentSession(token)) showRequestError(error, "리액션을 보내지 못했어요")
            }
        }
    }

    private fun canonicalNearbyTarget(
        selected: com.example.myapplication.core.model.NearbyListener,
    ): com.example.myapplication.core.model.NearbyListener {
        val identity = selected.stableNearbyIdentity()
        return deduplicateNearbyListeners(
            listeners = _state.value.nearbyListeners.filter {
                it.stableNearbyIdentity() == identity
            },
            preferredHandles = authoritativeNearbyHandles,
        ).firstOrNull() ?: selected
    }

    override fun block(handle: String) {
        val alias = _state.value.nearbyListeners.firstOrNull {
            it.nearbyHandle == handle
        }?.displayAlias ?: return
        val token = accessToken ?: return
        scope.launch {
            runCatching { socialApi.block("Bearer $token", handle) }
                .onSuccess { blocked ->
                    if (!isCurrentSession(token)) return@onSuccess
                    _state.update { current ->
                        current.copy(
                            nearbyListeners = current.nearbyListeners.filterNot { it.nearbyHandle == handle },
                            chats = current.chats.filterNot { it.peerHandle == handle },
                            blockedUsers = listOf(blocked.toDomain()) + current.blockedUsers
                                .filterNot { it.blockId == blocked.blockId },
                            selectedNearbyHandle = null,
                            feedbackMessage = "$alias 님을 차단했어요. 서로의 주변 목록에 표시되지 않아요",
                        )
                    }
                }
                .onFailure { error ->
                    if (isCurrentSession(token)) showRequestError(error, "사용자를 차단하지 못했어요")
                }
        }
    }

    override fun report(handle: String, reason: ReportReason, description: String?) {
        val alias = _state.value.nearbyListeners.firstOrNull {
            it.nearbyHandle == handle
        }?.displayAlias ?: return
        val token = accessToken ?: return
        scope.launch {
            runCatching {
                socialApi.report(
                    "Bearer $token",
                    handle,
                    ReportSubmitRequest(UUID.randomUUID().toString(), reason.name, description),
                )
            }.onSuccess {
                if (!isCurrentSession(token)) return@onSuccess
                _state.update { state -> state.copy(feedbackMessage = "$alias 님에 대한 신고를 제출했어요") }
            }.onFailure { error ->
                if (isCurrentSession(token)) showRequestError(error, "신고를 제출하지 못했어요")
            }
        }
    }

    override fun loadBlockedUsers() {
        val token = accessToken ?: return
        scope.launch {
            runCatching { socialApi.blocks("Bearer $token") }
                .onSuccess { users ->
                    if (isCurrentSession(token)) {
                        _state.update { it.copy(blockedUsers = users.map { user -> user.toDomain() }) }
                    }
                }
                .onFailure { error ->
                    if (isCurrentSession(token)) showRequestError(error, "차단 목록을 불러오지 못했어요")
                }
        }
    }

    override fun unblock(blockId: String) {
        val token = accessToken ?: return
        scope.launch {
            runCatching { socialApi.unblock("Bearer $token", blockId) }
                .onSuccess {
                    if (!isCurrentSession(token)) return@onSuccess
                    _state.update { state ->
                        state.copy(
                            blockedUsers = state.blockedUsers.filterNot { it.blockId == blockId },
                            feedbackMessage = "차단을 해제했어요",
                        )
                    }
                }.onFailure { error ->
                    if (isCurrentSession(token)) showRequestError(error, "차단을 해제하지 못했어요")
                }
        }
    }

    override fun loadSocialConnections() {
        val token = accessToken ?: return
        val requestVersion = socialConnectionsRequestVersion.incrementAndGet()
        _state.update { it.copy(socialConnectionsLoading = true) }
        scope.launch {
            runCatching {
                val authorization = "Bearer $token"
                socialApi.following(authorization) to socialApi.followers(authorization)
            }.onSuccess { (following, followers) ->
                if (!isCurrentSession(token) ||
                    requestVersion != socialConnectionsRequestVersion.get()
                ) return@onSuccess
                _state.update {
                    it.copy(
                        following = following.map { it.toDomain() },
                        followers = followers.map { it.toDomain() },
                        socialConnectionsLoading = false,
                        socialConnectionsLoaded = true,
                    )
                }
            }.onFailure { error ->
                if (!isCurrentSession(token) ||
                    requestVersion != socialConnectionsRequestVersion.get()
                ) return@onFailure
                _state.update { it.copy(socialConnectionsLoading = false) }
                showRequestError(error, "팔로우 목록을 불러오지 못했어요")
            }
        }
    }

    override fun unfollowRelationship(relationshipId: String) {
        val token = accessToken ?: return
        val wasMutual = _state.value.following
            .firstOrNull { it.relationshipId == relationshipId }?.mutual == true
        scope.launch {
            runCatching { socialApi.unfollowRelationship("Bearer $token", relationshipId) }
                .onSuccess {
                    if (!isCurrentSession(token)) return@onSuccess
                    _state.update { state ->
                        state.copy(
                            following = state.following.filterNot { it.relationshipId == relationshipId },
                            followers = state.followers.map { connection ->
                                if (connection.relationshipId == relationshipId) {
                                    connection.copy(relationshipId = null, mutual = false)
                                } else connection
                            },
                            feedbackMessage = if (wasMutual) "맞팔을 취소했어요" else "팔로우를 취소했어요",
                        )
                    }
                    loadChatRooms()
                }.onFailure { error ->
                    if (isCurrentSession(token)) showRequestError(error, "팔로우를 취소하지 못했어요")
                }
        }
    }

    override fun loadPublicProfile(profileHandle: String) {
        val token = accessToken ?: return
        if (profileHandle.isBlank()) return
        val requestVersion = publicProfileRequestVersion.incrementAndGet()
        _state.update {
            it.copy(
                selectedPublicProfile = it.selectedPublicProfile
                    ?.takeIf { profile -> profile.profileHandle == profileHandle },
                publicProfileLoading = true,
                publicProfileError = null,
            )
        }
        scope.launch {
            runCatching { profileApi.publicProfile("Bearer $token", profileHandle) }
                .onSuccess { remote ->
                    if (!isCurrentSession(token) ||
                        requestVersion != publicProfileRequestVersion.get()
                    ) return@onSuccess
                    val profile = remote.toDomain()
                    _state.update {
                        it.copy(
                            selectedPublicProfile = profile,
                            publicProfileLoading = false,
                            publicProfileError = null,
                        )
                    }
                    resolvePublicProfileArtwork(profile)
                    recordTasteFeedback(profile.profileHandle, "PROFILE_OPEN")
                }
                .onFailure { error ->
                    if (!isCurrentSession(token) ||
                        requestVersion != publicProfileRequestVersion.get()
                    ) return@onFailure
                    _state.update {
                        it.copy(
                            selectedPublicProfile = null,
                            publicProfileLoading = false,
                            publicProfileError = if (error is HttpException && error.code() == 404) {
                                "프로필을 찾을 수 없어요."
                            } else {
                                "프로필을 불러오지 못했어요."
                            },
                        )
                    }
                }
        }
    }

    override fun clearPublicProfile() {
        publicProfileRequestVersion.incrementAndGet()
        _state.update {
            it.copy(selectedPublicProfile = null, publicProfileLoading = false, publicProfileError = null)
        }
    }

    override fun recordTasteFeedback(profileHandle: String, action: String) {
        val token = accessToken ?: return
        if (profileHandle.isBlank() || profileHandle == _state.value.profile.profileHandle) return
        scope.launch {
            runCatching {
                tasteApi.recordFeedback(
                    "Bearer $token",
                    TasteFeedbackRequest(
                        clientFeedbackId = UUID.randomUUID().toString(),
                        targetProfileHandle = profileHandle,
                        action = action,
                    ),
                )
            }
        }
    }

    override fun followPublicProfile() {
        val token = accessToken ?: return
        val profile = _state.value.selectedPublicProfile ?: return
        if (profile.profileHandle == _state.value.profile.profileHandle) return
        scope.launch {
            runCatching {
                if (profile.following) {
                    socialApi.unfollowProfile("Bearer $token", profile.profileHandle)
                } else {
                    socialApi.followProfile("Bearer $token", profile.profileHandle)
                }
            }.onSuccess { response ->
                if (!isCurrentSession(token)) return@onSuccess
                val relationship = runCatching { RelationshipStatus.valueOf(response.relationship) }
                    .getOrDefault(RelationshipStatus.NONE)
                publicProfileRequestVersion.incrementAndGet()
                _state.update { current ->
                    val selected = current.selectedPublicProfile
                    if (selected?.profileHandle != profile.profileHandle) current else current.copy(
                        selectedPublicProfile = selected.copy(
                            relationship = relationship,
                            following = response.following,
                            mutual = response.mutual,
                        )
                    )
                }
                loadPublicProfile(profile.profileHandle)
                loadSocialConnections()
                loadChatRooms()
            }.onFailure { error ->
                if (isCurrentSession(token)) showRequestError(error, "팔로우 상태를 변경하지 못했어요")
            }
        }
    }

    override fun sendChat(roomId: String, content: String) {
        val chat = _state.value.chats.firstOrNull { it.roomId == roomId }
        val validationError = MelodyReducers.chatValidationError(
            content = content,
            relationship = chat?.relationship ?: RelationshipStatus.NONE
        )
        if (validationError != null) {
            _state.update { it.copy(feedbackMessage = validationError) }
            return
        }
        val trimmed = content.trim()

        val clientId = UUID.randomUUID().toString()
        val pending = ChatMessage(
            messageId = "pending-$clientId",
            clientMessageId = clientId,
            roomId = roomId,
            isMine = true,
            content = trimmed.take(1_000),
            sentAtLabel = "지금",
            deliveryState = DeliveryState.PENDING,
            sentAtEpochMillis = System.currentTimeMillis(),
        )
        _state.update { current ->
            current.copy(
                chatMessages = current.chatMessages + (
                    roomId to (current.chatMessages[roomId].orEmpty() + pending)
                ),
                chats = current.chats.map {
                    if (it.roomId == roomId) {
                        it.copy(
                            lastMessage = pending.content,
                            relativeTime = "방금",
                            unreadCount = 0,
                            hasMessages = true,
                            isHidden = false,
                        )
                    } else {
                        it
                    }
                }
            )
        }
        scope.launch {
            val token = accessToken
            val result = if (token == null) {
                Result.failure(IllegalStateException("로그인이 필요합니다"))
            } else {
                runCatching {
                    socialApi.sendChatMessage(
                        "Bearer $token",
                        roomId,
                        SendChatMessageRequest(clientId, trimmed),
                    )
                }
            }
            if (token == null || !isCurrentSession(token)) return@launch
            result.onSuccess { remote ->
                _state.update { current ->
                    current.copy(
                        chatMessages = current.chatMessages + (
                            roomId to current.chatMessages[roomId].orEmpty().map { message ->
                                if (message.clientMessageId == clientId) {
                                    message.copy(
                                        messageId = remote.messageId,
                                        sentAtLabel = chatSentAtLabel(remote.sentAt),
                                        deliveryState = if (message.deliveryState == DeliveryState.READ) {
                                            DeliveryState.READ
                                        } else DeliveryState.SENT,
                                    )
                                } else message
                            }
                        )
                    )
                }
            }.onFailure { error ->
                var markedFailed = false
                _state.update { current ->
                    current.copy(
                        chatMessages = current.chatMessages + (
                            roomId to current.chatMessages[roomId].orEmpty().map { message ->
                                if (message.clientMessageId == clientId &&
                                    message.deliveryState == DeliveryState.PENDING &&
                                    message.messageId.startsWith("pending-")
                                ) {
                                    markedFailed = true
                                    message.copy(deliveryState = DeliveryState.FAILED)
                                } else message
                            }
                        )
                    )
                }
                if (markedFailed) showRequestError(error, "메시지를 보내지 못했어요")
            }
        }
    }

    override fun markInboxRead() {
        realtimeInboxStore?.markAllRead()
        _state.update { current ->
            current.copy(
                notifications = current.notifications.map { it.copy(isRead = true) },
            )
        }
    }

    override fun clearNotifications() {
        realtimeInboxStore?.deleteAll(_state.value.notifications.map(InboxNotification::id))
        _state.update { it.copy(notifications = emptyList()) }
        accessToken?.let { token ->
            scope.launch {
                runCatching { nearbyApi.dismissAllReceivedReactions("Bearer $token") }
            }
        }
    }

    override fun deleteNotification(notificationId: String) {
        realtimeInboxStore?.delete(notificationId)
        _state.update { current ->
            current.copy(notifications = current.notifications.filterNot { it.id == notificationId })
        }
        accessToken?.let { token ->
            scope.launch {
                runCatching { nearbyApi.dismissReceivedReaction("Bearer $token", notificationId) }
            }
        }
    }

    private fun markChatRoomRead(roomId: String) {
        _state.update { current ->
            current.copy(
                chats = current.chats.map { chat ->
                    if (chat.roomId == roomId) chat.copy(unreadCount = 0) else chat
                }
            )
        }
        val token = accessToken ?: return
        synchronized(chatReadLock) {
            chatReadDirty += roomId
            if (chatReadJobs[roomId]?.isActive == true) return
            chatReadJobs[roomId] = scope.launch { syncChatRoomRead(roomId, token) }
        }
    }

    private suspend fun syncChatRoomRead(roomId: String, token: String) {
        var retryDelayMillis = 1_000L
        try {
            while (isCurrentSession(token)) {
                synchronized(chatReadLock) { chatReadDirty -= roomId }
                val result = runCatching { socialApi.markChatRead("Bearer $token", roomId) }
                if (!isCurrentSession(token)) return
                if (result.isSuccess) {
                    _state.update { current ->
                        current.copy(
                            chats = current.chats.map { chat ->
                                if (chat.roomId == roomId) chat.copy(unreadCount = 0) else chat
                            }
                        )
                    }
                    retryDelayMillis = 1_000L
                    if (synchronized(chatReadLock) { roomId !in chatReadDirty }) return
                    continue
                }
                val error = result.exceptionOrNull()
                if (error is HttpException && error.code() in 400..499 &&
                    error.code() !in setOf(408, 429)
                ) {
                    loadChatRooms()
                    return
                }
                synchronized(chatReadLock) { chatReadDirty += roomId }
                delay(retryDelayMillis)
                retryDelayMillis = (retryDelayMillis * 2).coerceAtMost(30_000L)
            }
        } finally {
            val restart = synchronized(chatReadLock) {
                chatReadJobs.remove(roomId)
                roomId in chatReadDirty && isCurrentSession(token)
            }
            if (restart) markChatRoomRead(roomId)
        }
    }

    override fun setDiscoverable(enabled: Boolean) {
        _state.update { it.copy(profile = it.profile.copy(discoverable = enabled)) }
        persistProfile(_state.value.profile)
        updatePresenceSettings(
            _state.value.discoveryRadiusMeters,
            if (enabled) "NEARBY" else "HIDDEN",
            _state.value.musicVisibility,
        )
    }

    override fun setAllowReactions(enabled: Boolean) {
        _state.update { it.copy(profile = it.profile.copy(allowReactions = enabled)) }
        persistProfile(_state.value.profile)
        syncPresenceSettings()
    }

    override fun setMusicVisibility(label: String) {
        _state.update { it.copy(profile = it.profile.copy(musicVisibilityLabel = label)) }
        persistProfile(_state.value.profile)
        val visibility = when (label) {
            "맞팔에게만 공개" -> "MUTUALS"
            "비공개" -> "HIDDEN"
            else -> "TITLE_ARTIST"
        }
        updatePresenceSettings(
            _state.value.discoveryRadiusMeters,
            _state.value.discoverabilityScope,
            visibility,
        )
    }

    override fun updatePresenceSettings(
        radiusMeters: Int,
        discoverabilityScope: String,
        musicVisibility: String,
    ) {
        _state.update {
            it.copy(
                discoveryRadiusMeters = radiusMeters.coerceIn(
                    MAX_NEARBY_RADIUS_METERS,
                    MAX_NEARBY_RADIUS_METERS,
                ),
                discoverabilityScope = discoverabilityScope,
                musicVisibility = musicVisibility,
                profile = it.profile.copy(
                    discoverable = discoverabilityScope != "HIDDEN",
                    musicVisibilityLabel = when (musicVisibility) {
                        "MUTUALS" -> "맞팔에게만 공개"
                        "HIDDEN" -> "비공개"
                        else -> "제목과 아티스트 공개"
                    },
                ),
            )
        }
        syncPresenceSettings()
    }

    override fun updateProfile(displayName: String, colorHex: Long, bio: String, genres: List<String>) {
        val token = accessToken ?: return
        val previousProfile = _state.value.profile
        _state.update { current -> current.copy(profileSaving = true, profile = current.profile.copy(
            accountAlias = displayName.trim(), nearbyDisplayAlias = displayName.trim(), colorHex = colorHex,
            bio = bio.trim(), genres = genres,
        )) }
        persistProfile(_state.value.profile)
        scope.launch {
            runCatching {
                profileApi.update("Bearer $token", ProfileUpdateRequest(
                    displayName, "#%06X".format(colorHex and 0xFFFFFF), bio,
                    genres,
                ))
            }.onSuccess {
                if (isCurrentSession(token)) applyRemoteProfile(it, "프로필을 변경했어요")
            }.onFailure {
                if (isCurrentSession(token)) {
                    _state.update { state -> state.copy(
                        profile = previousProfile,
                        profileSaving = false,
                        feedbackMessage = "프로필을 저장하지 못해 이전 값으로 되돌렸어요",
                    ) }
                    persistProfile(previousProfile)
                }
            }
        }
    }

    override fun customizeAvatar(customization: AvatarCustomization) {
        val token = accessToken
        val previousProfile = _state.value.profile
        val optimisticAvatar = AvatarProfileResolver.resolve(
            remoteSeed = previousProfile.avatarSeed,
            remoteUrl = AvatarProfileResolver.customizedUrl(previousProfile.avatarSeed, customization),
            stableIdentity = null,
            fallbackSeed = previousProfile.avatarSeed,
        )
        _state.update { current ->
            current.copy(
                profileSaving = true,
                profile = current.profile.copy(
                    avatarSeed = optimisticAvatar.seed,
                    avatarUrl = optimisticAvatar.url,
                ),
            )
        }
        persistProfile(_state.value.profile)
        preferences.edit().putBoolean(profileScopedKey(KEY_PROFILE_AVATAR_DIRTY), true).apply()
        if (token == null) {
            _state.update {
                it.copy(profileSaving = false, feedbackMessage = "새 아바타를 기기에 적용했어요.")
            }
            return
        }
        scope.launch {
            runCatching {
                profileApi.customizeAvatar(
                    "Bearer $token",
                    AvatarCustomizationRequest(
                        eyebrowsVariant = customization.eyebrowsVariant,
                        eyesVariant = customization.eyesVariant,
                        noseVariant = customization.noseVariant,
                        mouthVariant = customization.mouthVariant,
                        glassesVariant = customization.glassesVariant,
                        freckles = customization.freckles,
                    ),
                )
            }
                .onSuccess {
                    if (isCurrentSession(token)) {
                        preferences.edit().putBoolean(profileScopedKey(KEY_PROFILE_AVATAR_DIRTY), false).apply()
                        applyRemoteProfile(it, "새 아바타를 적용했어요")
                    }
                }
                .onFailure {
                    if (isCurrentSession(token)) {
                        _state.update { state ->
                            state.copy(
                                profileSaving = false,
                                feedbackMessage = "새 아바타를 기기에 적용했어요. 서버 연결 후 다시 동기화할게요.",
                            )
                        }
                        persistProfile(_state.value.profile)
                    }
                }
        }
    }

    override fun updateProfileCuration(
        signatureTracks: List<ProfileTrack>,
        favoriteArtists: List<ProfileArtist>,
    ) {
        val tracks = signatureTracks.take(3).mapIndexed { index, track -> track.copy(rank = index + 1) }
        val artists = favoriteArtists.take(3).mapIndexed { index, artist -> artist.copy(rank = index + 1) }
        _state.update { current ->
            current.copy(
                profileSaving = true,
                profile = current.profile.copy(signatureTracks = tracks, favoriteArtists = artists),
            )
        }
        preferences.edit().putBoolean(profileScopedKey(KEY_PROFILE_CURATION_DIRTY), true).apply()
        persistProfile(_state.value.profile)
        val token = accessToken
        if (token == null) {
            _state.update {
                it.copy(profileSaving = false, feedbackMessage = "음악 프로필을 기기에 저장했어요. 온라인 연결 후 반영돼요")
            }
            return
        }
        scope.launch { syncPendingCuration(token, successFeedback = "음악 정체성을 저장했어요") }
    }

    override fun updateProfilePrivacy(settings: ProfilePrivacySettings) {
        presenceSyncCoordinator.setListeningInsightsEnabled(settings.listeningInsightsEnabled)
        _state.update { current ->
            val legacyVisibility = when (settings.currentMusicVisibility) {
                "MUTUALS" -> "MUTUALS"
                "PRIVATE" -> "HIDDEN"
                else -> "TITLE_ARTIST"
            }
            current.copy(
                profileSaving = true,
                musicVisibility = legacyVisibility,
                profile = current.profile.copy(
                    privacy = settings,
                    musicVisibilityLabel = when (legacyVisibility) {
                        "MUTUALS" -> "맞팔에게만 공개"
                        "HIDDEN" -> "비공개"
                        else -> "제목과 아티스트 공개"
                    },
                ),
            )
        }
        preferences.edit().putBoolean(profileScopedKey(KEY_PROFILE_PRIVACY_DIRTY), true).apply()
        persistProfile(_state.value.profile)
        val token = accessToken
        if (token == null) {
            _state.update {
                it.copy(profileSaving = false, feedbackMessage = "공개 범위를 기기에 저장했어요. 온라인 연결 후 반영돼요")
            }
            return
        }
        scope.launch { syncPendingProfilePrivacy(token, successFeedback = "프로필 공개 범위를 저장했어요") }
    }

    private suspend fun syncPendingCuration(token: String, successFeedback: String? = null) {
        if (!preferences.getBoolean(profileScopedKey(KEY_PROFILE_CURATION_DIRTY), false) || !isCurrentSession(token)) return
        val profile = restoreProfile(_state.value.profile)
        runCatching {
            profileApi.updateCuration(
                "Bearer $token",
                ProfileCurationUpdateRequest(
                    signatureTracks = profile.signatureTracks.map { it.toRemote() },
                    favoriteArtists = profile.favoriteArtists.map { it.toRemote() },
                    profileRevision = profile.profileRevision,
                ),
            )
        }.onSuccess { remote ->
            if (!isCurrentSession(token)) return@onSuccess
            preferences.edit().putBoolean(profileScopedKey(KEY_PROFILE_CURATION_DIRTY), false).apply()
            applyRemoteProfile(remote, successFeedback)
        }.onFailure { error ->
            if (!isCurrentSession(token)) return@onFailure
            _state.update {
                it.copy(
                    profileSaving = false,
                    feedbackMessage = if (error is HttpException && error.code() == 409) {
                        "다른 기기에서 프로필이 바뀌었어요. 다시 저장해 주세요"
                    } else {
                        "음악 프로필을 기기에 보관했어요. 연결되면 다시 시도해요"
                    },
                )
            }
        }
    }

    private suspend fun syncPendingProfilePrivacy(token: String, successFeedback: String? = null) {
        if (!preferences.getBoolean(profileScopedKey(KEY_PROFILE_PRIVACY_DIRTY), false) || !isCurrentSession(token)) return
        val settings = restoreProfile(_state.value.profile).privacy
        runCatching {
            profileApi.updateProfilePrivacy(
                "Bearer $token",
                ProfilePrivacyUpdateRequest(
                    currentMusicVisibility = settings.currentMusicVisibility,
                    listeningInsightsEnabled = settings.listeningInsightsEnabled,
                    listeningInsightsVisibility = settings.listeningInsightsVisibility,
                ),
            )
        }.onSuccess { remote ->
            if (!isCurrentSession(token)) return@onSuccess
            preferences.edit().putBoolean(profileScopedKey(KEY_PROFILE_PRIVACY_DIRTY), false).apply()
            applyRemoteProfile(remote, successFeedback)
        }.onFailure {
            if (!isCurrentSession(token)) return@onFailure
            _state.update {
                it.copy(profileSaving = false, feedbackMessage = "공개 범위를 기기에 보관했어요. 연결되면 다시 시도해요")
            }
        }
    }

    override fun clearFeedback() {
        _state.update { it.copy(feedbackMessage = null) }
    }

    override fun authenticate(accessToken: String) {
        this.accessToken = accessToken
        val ownerUserId = jwtSubject(accessToken)
        setActiveOwner(ownerUserId)
        realtimeInboxStore?.activate(accessToken)
        presenceSyncCoordinator.onSessionAvailable(accessToken)
        val detectedPlayback = presenceSyncCoordinator.detectedPlayback.value
        preferences.edit().putBoolean("onboarding-complete", false).apply()
        _state.update {
            it.copy(
                isOnboardingComplete = false,
                profile = DemoCatalog.initialState(false).profile,
                nearbyListeners = emptyList(),
                popularTracks = emptyList(),
                notifications = realtimeInboxStore?.load().orEmpty(),
                chats = emptyList(),
                chatMessages = emptyMap(),
                currentTrack = detectedPlayback.track ?: it.currentTrack,
                currentTrackPlaying = detectedPlayback.isPlaying,
                following = emptyList(),
                followers = emptyList(),
                socialConnectionsLoading = false,
                socialConnectionsLoaded = false,
                selectedPublicProfile = null,
                publicProfileLoading = false,
                publicProfileError = null,
                detectedTrack = detectedPlayback.track,
                detectedTrackPlaying = detectedPlayback.isPlaying,
                selectedNearbyHandle = null,
                dataSourceLabel = "SERVER LIVE",
                activeAccountId = ownerUserId,
            )
        }
        scope.launch {
            runCatching { profileApi.me("Bearer $accessToken") }
                .onSuccess { remote ->
                    if (!isCurrentSession(accessToken)) return@onSuccess
                    applyRemoteProfile(remote)
                    syncPendingCuration(accessToken)
                    syncPendingProfilePrivacy(accessToken)
                }
        }
        scope.launch {
            runCatching { nearbyApi.presenceSettings("Bearer $accessToken") }
                .onSuccess { remote ->
                    if (!isCurrentSession(accessToken)) return@onSuccess
                    _state.update {
                        it.copy(
                            discoveryRadiusMeters = MAX_NEARBY_RADIUS_METERS,
                            discoverabilityScope = remote.discoverabilityScope,
                            musicVisibility = remote.musicVisibility,
                            profile = it.profile.copy(
                                discoverable = remote.discoverabilityScope != "HIDDEN",
                                allowReactions = remote.allowReactions,
                                musicVisibilityLabel = when (remote.musicVisibility) {
                                    "MUTUALS" -> "맞팔에게만 공개"
                                    "HIDDEN" -> "비공개"
                                    else -> "제목과 아티스트 공개"
                                },
                            ),
                        )
                    }
                }
        }
        refreshPopularTracks(accessToken)
        syncReceivedReactions(accessToken)
        loadBlockedUsers()
        loadSocialConnections()
        realtimeClient.connect(accessToken)
        handleRealtimeConnectionState(realtimeClient.connectionState.value)
        startChatSync(accessToken)
        if (SharingForegroundService.isSharingActive(applicationContext)) {
            startSharing()
        }
    }

    override fun refreshSession(accessToken: String) {
        if (this.accessToken == null) return
        this.accessToken = accessToken
        realtimeInboxStore?.activate(accessToken)
        presenceSyncCoordinator.onSessionAvailable(accessToken)
        realtimeClient.connect(accessToken)
        startChatSync(accessToken)
    }

    override fun logout() {
        stopSharing()
        chatSyncJob?.cancel()
        chatSyncJob = null
        synchronized(chatReadLock) {
            chatReadJobs.values.forEach(Job::cancel)
            chatReadJobs.clear()
            chatReadDirty.clear()
        }
        activeChatRoomId = null
        presenceSyncCoordinator.onSessionCleared()
        accessToken = null
        realtimeClient.disconnect()
        realtimeInboxStore?.clear()
        preferences.edit().clear().apply()
        _state.update { it.copy(
            isOnboardingComplete = false,
            selectedTab = MainTab.HOME,
            profile = DemoCatalog.initialState(false).profile,
            following = emptyList(),
            followers = emptyList(),
            socialConnectionsLoading = false,
            socialConnectionsLoaded = false,
            selectedPublicProfile = null,
            publicProfileLoading = false,
            publicProfileError = null,
            feedbackMessage = null,
            dataSourceLabel = "SIGNED OUT",
            activeAccountId = null,
        ) }
        setActiveOwner(null)
    }

    private fun syncPresenceSettings() {
        val token = accessToken ?: return
        val state = _state.value
        scope.launch {
            runCatching {
                nearbyApi.updatePresenceSettings(
                    "Bearer $token",
                    PresenceSettingsUpdateRequest(
                        state.discoverabilityScope,
                        state.musicVisibility,
                        MAX_NEARBY_RADIUS_METERS,
                        state.profile.allowReactions,
                    ),
                )
            }.onSuccess { remote ->
                if (!isCurrentSession(token)) return@onSuccess
                _state.update {
                    it.copy(
                        discoveryRadiusMeters = MAX_NEARBY_RADIUS_METERS,
                        discoverabilityScope = remote.discoverabilityScope,
                        musicVisibility = remote.musicVisibility,
                        feedbackMessage = "주변 공개 범위를 저장했어요",
                    )
                }
            }.onFailure { error ->
                if (isCurrentSession(token)) showRequestError(error, "공개 범위를 저장하지 못했어요")
            }
        }
    }

    private fun applyRemoteProfile(remote: RemoteProfile, feedback: String? = null) {
        _state.update { current ->
            val cachedProfile = restoreProfile(current.profile)
            val keepPendingCuration = preferences.getBoolean(profileScopedKey(KEY_PROFILE_CURATION_DIRTY), false)
            val keepPendingPrivacy = preferences.getBoolean(profileScopedKey(KEY_PROFILE_PRIVACY_DIRTY), false)
            val keepPendingAvatar = preferences.getBoolean(profileScopedKey(KEY_PROFILE_AVATAR_DIRTY), false)
            val remoteAlias = remote.melodyAlias
            val stats = remote.stats?.toDomain() ?: current.profile.stats
            val fingerprint = remote.tasteFingerprint?.toDomain() ?: current.profile.tasteFingerprint
            val resolvedAvatar = AvatarProfileResolver.resolve(
                remoteSeed = remote.avatarSeed,
                remoteUrl = remote.avatarUrl,
                stableIdentity = remote.profileHandle ?: remote.displayName,
                fallbackSeed = cachedProfile.avatarSeed,
            )
            current.copy(
                profileSaving = false,
                profile = current.profile.copy(
                    accountAlias = remote.displayName,
                    nearbyDisplayAlias = remote.displayName,
                    colorHex = remote.profileColor.removePrefix("#").toLongOrNull(16) ?: current.profile.colorHex,
                    bio = remote.bio.orEmpty(),
                    avatarSeed = if (keepPendingAvatar) cachedProfile.avatarSeed else resolvedAvatar.seed,
                    avatarUrl = if (keepPendingAvatar) cachedProfile.avatarUrl else resolvedAvatar.url,
                    genres = remote.genres.orEmpty(),
                    profileHandle = remote.profileHandle.orEmpty().ifBlank { current.profile.profileHandle },
                    stats = stats,
                    tasteFingerprint = fingerprint,
                    profileRevision = if (keepPendingCuration) cachedProfile.profileRevision else remote.profileRevision,
                    signatureTracks = if (keepPendingCuration) {
                        cachedProfile.signatureTracks
                    } else remote.signatureTracks.orEmpty().map { it.toDomain() },
                    favoriteArtists = if (keepPendingCuration) {
                        cachedProfile.favoriteArtists
                    } else remote.favoriteArtists.orEmpty().map { it.toDomain() },
                    privacy = if (keepPendingPrivacy) {
                        cachedProfile.privacy
                    } else remote.privacy?.let {
                        ProfilePrivacySettings(
                            currentMusicVisibility = it.currentMusicVisibility,
                            listeningInsightsEnabled = it.listeningInsightsEnabled,
                            listeningInsightsVisibility = it.listeningInsightsVisibility,
                        )
                    } ?: current.profile.privacy,
                    melodyNotes = remoteAlias?.notes ?: current.profile.melodyNotes,
                    melodyAliasId = remoteAlias?.id ?: current.profile.melodyAliasId,
                    melodyAliasTone = remoteAlias?.tone ?: current.profile.melodyAliasTone,
                    melodyAliasTempo = remoteAlias?.tempo ?: current.profile.melodyAliasTempo,
                    discoverable = current.discoverabilityScope != "HIDDEN",
                    musicVisibilityLabel = when (current.musicVisibility) {
                        "MUTUALS" -> "맞팔에게만 공개"
                        "HIDDEN" -> "비공개"
                        else -> "제목과 아티스트 공개"
                    },
                ),
                feedbackMessage = feedback ?: current.feedbackMessage,
            )
        }
        persistProfile(_state.value.profile)
        presenceSyncCoordinator.setListeningInsightsEnabled(
            _state.value.profile.privacy.listeningInsightsEnabled,
        )
        resolveOwnProfileArtistArtwork()
    }

    private fun persistProfile(profile: com.example.myapplication.core.model.ProfileSettings) {
        preferences.edit()
            .putString("profile-account-alias", profile.accountAlias)
            .putString("profile-nearby-alias", profile.nearbyDisplayAlias)
            .putLong("profile-color", profile.colorHex)
            .putString("profile-bio", profile.bio)
            .putString("profile-avatar-seed", profile.avatarSeed)
            .putString("profile-avatar", profile.avatarUrl)
            .putString("profile-genres", profile.genres.joinToString("\u001F"))
            .remove("profile-moods")
            .putString("profile-handle", profile.profileHandle)
            .putString("profile-melody-notes", profile.melodyNotes.joinToString("\u001F"))
            .putString("profile-melody-id", profile.melodyAliasId)
            .putString("profile-melody-tone", profile.melodyAliasTone)
            .remove("profile-melody-mood")
            .putInt("profile-melody-tempo", profile.melodyAliasTempo)
            .putString("profile-music-visibility", profile.musicVisibilityLabel)
            .putBoolean("profile-discoverable", profile.discoverable)
            .putBoolean("profile-allow-reactions", profile.allowReactions)
            .putLong(profileScopedKey("profile-revision"), profile.profileRevision)
            .putString(profileScopedKey("profile-signature-tracks"), gson.toJson(profile.signatureTracks))
            .putString(profileScopedKey("profile-favorite-artists"), gson.toJson(profile.favoriteArtists))
            .putString(profileScopedKey("profile-section-privacy"), gson.toJson(profile.privacy))
            .apply()
    }

    private fun restoreProfile(fallback: com.example.myapplication.core.model.ProfileSettings): com.example.myapplication.core.model.ProfileSettings {
        if (!preferences.contains("profile-account-alias")) return fallback
        return fallback.copy(
            accountAlias = preferences.getString("profile-account-alias", fallback.accountAlias) ?: fallback.accountAlias,
            nearbyDisplayAlias = preferences.getString("profile-nearby-alias", fallback.nearbyDisplayAlias) ?: fallback.nearbyDisplayAlias,
            colorHex = preferences.getLong("profile-color", fallback.colorHex),
            bio = preferences.getString("profile-bio", fallback.bio) ?: fallback.bio,
            avatarSeed = preferences.getString("profile-avatar-seed", fallback.avatarSeed) ?: fallback.avatarSeed,
            avatarUrl = preferences.getString("profile-avatar", fallback.avatarUrl),
            genres = preferences.getString("profile-genres", null)?.split('\u001F')?.filter(String::isNotBlank) ?: fallback.genres,
            profileHandle = preferences.getString("profile-handle", fallback.profileHandle) ?: fallback.profileHandle,
            melodyNotes = preferences.getString("profile-melody-notes", null)?.split('\u001F')?.filter(String::isNotBlank) ?: fallback.melodyNotes,
            melodyAliasId = preferences.getString("profile-melody-id", fallback.melodyAliasId) ?: fallback.melodyAliasId,
            melodyAliasTone = preferences.getString("profile-melody-tone", fallback.melodyAliasTone) ?: fallback.melodyAliasTone,
            melodyAliasTempo = preferences.getInt("profile-melody-tempo", fallback.melodyAliasTempo),
            musicVisibilityLabel = preferences.getString("profile-music-visibility", fallback.musicVisibilityLabel) ?: fallback.musicVisibilityLabel,
            discoverable = preferences.getBoolean("profile-discoverable", fallback.discoverable),
            allowReactions = preferences.getBoolean("profile-allow-reactions", fallback.allowReactions),
            profileRevision = preferences.getLong(profileScopedKey("profile-revision"), fallback.profileRevision),
            signatureTracks = preferences.getString(profileScopedKey("profile-signature-tracks"), null)?.let { json ->
                runCatching { gson.fromJson(json, Array<ProfileTrack>::class.java).toList() }.getOrNull()
            } ?: fallback.signatureTracks,
            favoriteArtists = preferences.getString(profileScopedKey("profile-favorite-artists"), null)?.let { json ->
                runCatching { gson.fromJson(json, Array<ProfileArtist>::class.java).toList() }.getOrNull()
            } ?: fallback.favoriteArtists,
            privacy = preferences.getString(profileScopedKey("profile-section-privacy"), null)?.let { json ->
                runCatching { gson.fromJson(json, ProfilePrivacySettings::class.java) }.getOrNull()
            } ?: fallback.privacy,
        )
    }

    private fun startHybridDiscovery(token: String) {
        if (token.isBlank() || hybridDiscoveryJob?.isActive == true) return
        hybridDiscoveryJob = scope.launch {
            while (isActive && isCurrentSession(token) &&
                _state.value.sharingState in setOf(SharingState.STARTING, SharingState.ACTIVE)
            ) {
                val beacon = runCatching {
                    nearbyApi.issueBeacon("Bearer $token", NearbyBeaconRequest(clientSessionId))
                }.getOrNull()
                if (beacon != null) {
                    if (!passiveDiscoveryStarted) {
                        passiveNearbyDiscovery.start(beacon.beaconId)
                        passiveDiscoveryStarted = true
                    } else {
                        passiveNearbyDiscovery.rotate(beacon.beaconId)
                    }
                }
                delay(30_000L)
            }
        }
    }

    private fun resolveDirectBeacons(beaconIds: Set<String>) {
        val incomingBeaconIds = beaconIds.toSortedSet()
        val (generation, pendingResolve, requestedBeaconIds) = synchronized(directStateLock) {
            val currentBeaconIds = if (directDiscoveryActive) incomingBeaconIds else emptySet()
            val nextGeneration = directResolveGeneration.incrementAndGet()
            desiredDirectBeaconIds = currentBeaconIds
            val pending = directResolveJob
            directResolveJob = null
            if (currentBeaconIds.isEmpty()) {
                directUsersByBeacon = emptyMap()
                directNearbyByHandle = emptyMap()
                directMeasurementsByBeacon = emptyMap()
                reportedDirectMeasurementAt.clear()
            }
            Triple(nextGeneration, pending, currentBeaconIds)
        }
        pendingResolve?.cancel()
        if (requestedBeaconIds.isEmpty()) {
            return
        }
        val token = accessToken ?: return
        lateinit var resolveJob: Job
        resolveJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                var retryDelayMillis = DIRECT_BEACON_RESOLVE_INITIAL_RETRY_MILLIS
                while (isActive && isCurrentDirectResolve(generation, requestedBeaconIds, token)) {
                    awaitDirectResolveSlot()
                    if (!isCurrentDirectResolve(generation, requestedBeaconIds, token)) return@launch
                    val resolved = try {
                        nearbyApi.resolveBeacons(
                            "Bearer $token",
                            ResolveNearbyBeaconsRequest(requestedBeaconIds.toList()),
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w("MelodyNearby", "Direct beacon resolve failed; retrying", error)
                        null
                    }
                    if (resolved == null) {
                        delay(retryDelayMillis)
                        retryDelayMillis = (retryDelayMillis * 2L)
                            .coerceAtMost(DIRECT_BEACON_RESOLVE_MAX_RETRY_MILLIS)
                        continue
                    }
                    val usersByBeacon = resolved
                        .filter { it.beaconId in requestedBeaconIds }
                        .associate { item -> item.beaconId to item.user.toDomain() }
                    val applied = synchronized(directStateLock) {
                        if (!isCurrentDirectResolveLocked(generation, requestedBeaconIds, token)) {
                            false
                        } else {
                            directUsersByBeacon = usersByBeacon
                            applyDirectProximityMeasurementsLocked()
                            true
                        }
                    }
                    if (!applied) return@launch
                    return@launch
                }
            } finally {
                synchronized(directStateLock) {
                    if (directResolveJob === resolveJob) directResolveJob = null
                }
            }
        }
        val shouldStart = synchronized(directStateLock) {
            if (isCurrentDirectResolveLocked(generation, requestedBeaconIds, token)) {
                directResolveJob = resolveJob
                true
            } else {
                false
            }
        }
        if (shouldStart) resolveJob.start() else resolveJob.cancel()
    }

    private fun isCurrentDirectResolve(
        generation: Long,
        beaconIds: Set<String>,
        token: String,
    ): Boolean = synchronized(directStateLock) {
        isCurrentDirectResolveLocked(generation, beaconIds, token)
    }

    private fun isCurrentDirectResolveLocked(
        generation: Long,
        beaconIds: Set<String>,
        token: String,
    ): Boolean = shouldApplyDirectResolveResult(
        requestGeneration = generation,
        currentGeneration = directResolveGeneration.get(),
        requestedBeaconIds = beaconIds,
        desiredBeaconIds = desiredDirectBeaconIds,
        tokenIsCurrent = isCurrentSession(token),
        sharingIsActive = directDiscoveryActive && _state.value.sharingState in
            setOf(SharingState.STARTING, SharingState.ACTIVE),
    )

    private suspend fun awaitDirectResolveSlot() {
        directResolveStartMutex.withLock {
            val elapsed = SystemClock.elapsedRealtime()
            val waitMillis = DIRECT_BEACON_RESOLVE_INTERVAL_MILLIS -
                (elapsed - lastDirectResolveStartedAtElapsedMillis)
            if (waitMillis > 0L) delay(waitMillis)
            lastDirectResolveStartedAtElapsedMillis = SystemClock.elapsedRealtime()
        }
    }

    private fun applyDirectProximityMeasurementsLocked() {
        val candidates = directUsersByBeacon.map { (beaconId, user) ->
            val measurement = directMeasurementsByBeacon[beaconId]
            val measuredUser = if (measurement == null) {
                user.copy(isDirectlyDetected = true)
            } else {
                user.copy(
                    proximity = measurement.proximity,
                    proximityConfidence = measurement.confidence,
                    displayPosition = abstractDisplayPosition(user.nearbyHandle, measurement.proximity),
                    isDirectlyDetected = true,
                )
            }
            DirectNearbyCandidate(beaconId, measuredUser, measurement)
        }
        val currentByHandle = _state.value.nearbyListeners.associateBy { it.nearbyHandle }
        directNearbyByHandle = preferDirectNearbyUsers(candidates, currentByHandle)
        val direct = directNearbyByHandle
        val newest = directMeasurementsByBeacon.values.maxByOrNull { it.observedAtEpochMillis }
        _state.update { current ->
            val directByIdentity = direct.values.associateBy { it.stableNearbyIdentity() }
            val existingIdentities = current.nearbyListeners
                .mapTo(mutableSetOf()) { it.stableNearbyIdentity() }
            val listeners = current.nearbyListeners.map { existing ->
                val measured = direct[existing.nearbyHandle]
                    ?: directByIdentity[existing.stableNearbyIdentity()]
                    ?: return@map existing
                // Re-read playback from the atomic state update. A realtime event may have arrived
                // after direct candidates were calculated but before this reducer executes.
                existing.copy(
                    proximity = measured.proximity,
                    proximityConfidence = measured.proximityConfidence,
                    displayPosition = measured.displayPosition,
                    isDirectlyDetected = true,
                )
            } +
                direct.values.filterNot { it.stableNearbyIdentity() in existingIdentities }
            val deduplicatedListeners = deduplicateNearbyListeners(
                listeners,
                authoritativeNearbyHandles,
            )
            current.copy(
                nearbyListeners = deduplicatedListeners,
                nearbyLoadState = if (deduplicatedListeners.isEmpty()) {
                    current.nearbyLoadState
                } else {
                    NearbyLoadState.READY
                },
                nearbyMeasurementDiagnostics = newest?.let {
                    NearbyMeasurementDiagnostics(it.method, null, it.observedAtEpochMillis, null)
                } ?: current.nearbyMeasurementDiagnostics,
                snapshotSequence = current.snapshotSequence + 1,
            )
        }
    }

    private suspend fun reportDirectProximityMeasurements(
        measurements: Map<String, PeerProximityMeasurement>,
    ): Boolean {
        val token = accessToken ?: return true
        if (!isCurrentSession(token) ||
            _state.value.sharingState !in setOf(SharingState.STARTING, SharingState.ACTIVE)
        ) return true
        reportedDirectMeasurementAt.keys.retainAll(measurements.keys)
        val pendingMeasurements = measurements.values
            .filter { measurement ->
                measurement.observedAtEpochMillis >
                    (reportedDirectMeasurementAt[measurement.beaconId] ?: Long.MIN_VALUE)
            }
            .sortedWith(
                compareBy<PeerProximityMeasurement>(PeerProximityMeasurement::observedAtEpochMillis)
                    .thenBy(PeerProximityMeasurement::beaconId),
            )
        if (pendingMeasurements.isEmpty()) return true
        val requests = pendingMeasurements.take(MAX_DIRECT_PROXIMITY_BATCH_SIZE).map { measurement ->
            DirectProximityUpdateRequest(
                beaconId = measurement.beaconId,
                proximity = measurement.proximity.name,
                confidence = measurement.confidence.name,
                method = measurement.method.name,
                sequence = directProximitySequence.incrementAndGet(),
                observedAtEpochMillis = measurement.observedAtEpochMillis,
            )
        }
        val succeeded = try {
            nearbyApi.reportDirectProximityBatch(
                "Bearer $token",
                DirectProximityBatchRequest(requests),
            )
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w("MelodyNearby", "Direct proximity batch failed; retrying", error)
            false
        }
        if (!succeeded) return false
        requests.forEach { request ->
            reportedDirectMeasurementAt[request.beaconId] = request.observedAtEpochMillis
        }
        return pendingMeasurements.size <= requests.size
    }

    private suspend fun awaitDirectReportSlot() {
        directReportStartMutex.withLock {
            val elapsed = SystemClock.elapsedRealtime()
            val waitMillis = DIRECT_PROXIMITY_REPORT_INTERVAL_MILLIS -
                (elapsed - lastDirectReportStartedAtElapsedMillis)
            if (waitMillis > 0L) delay(waitMillis)
            lastDirectReportStartedAtElapsedMillis = SystemClock.elapsedRealtime()
        }
    }

    private fun mergeDirectNearby(
        locationUsers: List<com.example.myapplication.core.model.NearbyListener>,
    ): List<com.example.myapplication.core.model.NearbyListener> {
        val activeLocationUsers = deduplicateNearbyListeners(
            locationUsers.filterNot { it.nearbyHandle in explicitlyRemovedNearbyHandles },
        )
        val locationHandles = activeLocationUsers.mapTo(mutableSetOf()) { it.nearbyHandle }
        authoritativeNearbyHandles = locationHandles
        rememberNearbyAliases(activeLocationUsers)
        rememberNearbyAliases(directNearbyByHandle.values)
        val locationIdentities = activeLocationUsers
            .mapTo(mutableSetOf()) { it.stableNearbyIdentity() }
        val currentByProfile = _state.value.nearbyListeners
            .mapNotNull { listener ->
                listener.profileHandle?.trim()?.lowercase()?.let { it to listener }
            }
            .toMap()
        val directOnly = directNearbyByHandle.values.filterNot {
            it.nearbyHandle in locationHandles ||
                it.stableNearbyIdentity() in locationIdentities ||
                it.nearbyHandle in explicitlyRemovedNearbyHandles
        }.map { direct ->
            val current = direct.profileHandle?.trim()?.lowercase()
                ?.let(currentByProfile::get) ?: return@map direct
            direct.copy(
                relationship = current.relationship,
                isPlaying = current.isPlaying,
                currentTrack = current.currentTrack,
            )
        }
        return deduplicateNearbyListeners(activeLocationUsers + directOnly, locationHandles)
    }

    private fun rememberNearbyAliases(
        listeners: Collection<com.example.myapplication.core.model.NearbyListener>,
    ) {
        listeners.forEach { listener ->
            listener.profileHandle?.trim()?.takeIf(String::isNotEmpty)?.lowercase()?.let { profile ->
                profileByNearbyHandle[listener.nearbyHandle] = profile
            }
        }
    }

    private fun preserveNewerRealtimeMusic(
        incoming: List<com.example.myapplication.core.model.NearbyListener>,
        snapshotGeneratedAt: String?,
    ): List<com.example.myapplication.core.model.NearbyListener> {
        val snapshotEpochMillis = snapshotGeneratedAt.toServerEpochMillis() ?: return incoming
        val currentByHandle = _state.value.nearbyListeners.associateBy { it.nearbyHandle }
        val currentByIdentity = _state.value.nearbyListeners.associateBy { it.stableNearbyIdentity() }
        return incoming.map { listener ->
            val profile = listener.profileHandle?.trim()?.lowercase()
            val musicEventAt = sequenceOf(lastMusicEventAtByHandle[listener.nearbyHandle])
                .plus(
                    profileByNearbyHandle.entries.asSequence()
                        .filter { (_, aliasProfile) -> aliasProfile == profile }
                        .mapNotNull { (aliasHandle, _) -> lastMusicEventAtByHandle[aliasHandle] },
                )
                .filterNotNull()
                .maxOrNull() ?: return@map listener
            val current = currentByHandle[listener.nearbyHandle]
                ?: currentByIdentity[listener.stableNearbyIdentity()]
                ?: return@map listener
            if (musicEventAt > snapshotEpochMillis) {
                listener.copy(
                    isPlaying = current.isPlaying,
                    currentTrack = current.currentTrack,
                )
            } else {
                listener
            }
        }
    }

    private suspend fun syncPresence(
        fix: LocationFix? = null,
        requirePrecise: Boolean = false,
    ): Boolean = presenceSyncMutex.withLock {
        awaitPresenceSyncSlot()
        syncPresenceLocked(fix, requirePrecise)
    }

    private suspend fun syncLatestLocationPresence(): Boolean =
        presenceSyncMutex.withLock {
            val fix = reusableLatestLocationFix() ?: return@withLock false
            awaitPresenceSyncSlot()
            syncPresenceLocked(fix, requirePrecise = true)
        }

    private fun reusableLatestLocationFix(): LocationFix? = synchronized(latestLocationFixLock) {
        latestLocationFix?.takeIf {
            NearbyLocationPolicy.isReusableForPresenceKeepAlive(it.accuracyMeters)
        }
    }

    private fun rememberLatestLocationFix(candidate: LocationFix) {
        synchronized(latestLocationFixLock) {
            val current = latestLocationFix
            if (current == null || candidate.observedAtEpochMillis >= current.observedAtEpochMillis) {
                latestLocationFix = candidate
            }
        }
    }

    private suspend fun awaitPresenceSyncSlot() {
        val elapsed = SystemClock.elapsedRealtime()
        val waitMillis = ACTIVE_PRESENCE_REFRESH_INTERVAL_MILLIS -
            (elapsed - lastPresenceSyncStartedAtElapsedMillis)
        if (waitMillis > 0L) delay(waitMillis)
        lastPresenceSyncStartedAtElapsedMillis = SystemClock.elapsedRealtime()
    }

    private suspend fun syncPresenceLocked(
        fix: LocationFix? = null,
        requirePrecise: Boolean = false,
    ): Boolean {
        val token = accessToken ?: return false
        val nearbyVersionAtRequest = nearbyRealtimeVersion.get()
        val freshLocation = fix ?: currentLocation(
            allowInitialCoarseLocation = !requirePrecise,
        )?.let {
            LocationFix(
                it.latitude,
                it.longitude,
                it.accuracy.takeIf(Float::isFinite),
                it.provider.orEmpty(),
                it.time,
            )
        }
        val location = freshLocation ?: reusableLatestLocationFix() ?: run {
            _state.update { current ->
                val loadState = current.nearbyLoadState.keepSettledDuringRefresh(
                    NearbyLoadState.LOADING,
                )
                current.copy(
                    nearbyLoadState = loadState,
                    nearbyErrorMessage = if (loadState == NearbyLoadState.LOADING) {
                        "Nearby로 주변을 찾는 중이에요. 위치가 확인되면 거리 정보도 갱신돼요."
                    } else {
                        current.nearbyErrorMessage
                    },
                )
            }
            return false
        }
        return runCatching {
            val snapshot = nearbyApi.updateLocation(
                "Bearer $token",
                LocationUpdateRequest(
                    UUID.randomUUID().toString(),
                    clientSessionId,
                    locationSequence.updateAndGet { previous ->
                        maxOf(previous + 1L, location.observedAtEpochMillis * 1_000L)
                    },
                    location.observedAtEpochMillis,
                    location.source.uppercase(),
                    location.latitude,
                    location.longitude,
                    location.accuracyMeters,
                )
            )
            if (!isCurrentSession(token)) return@runCatching false
            if (NearbyLocationPolicy.isReusableForPresenceKeepAlive(location.accuracyMeters)) {
                rememberLatestLocationFix(location)
            }
            val incomingListeners = preserveNewerRealtimeMusic(
                mergeDirectNearby(snapshot.items.map { it.toDomain() }),
                snapshot.generatedAt,
            )
            val stabilizedListeners = deduplicateNearbyListeners(
                nearbyProximityStabilizer.stabilize(
                    _state.value.nearbyListeners,
                    incomingListeners,
                ),
                incomingListeners.mapTo(mutableSetOf()) { it.nearbyHandle },
            )
            _state.update { current ->
                val listeners = if (nearbyRealtimeVersion.get() == nearbyVersionAtRequest) {
                    stabilizedListeners
                } else {
                    val currentByHandle = current.nearbyListeners.associateBy { it.nearbyHandle }
                    stabilizedListeners.map { remote ->
                        currentByHandle[remote.nearbyHandle]?.let { latest ->
                            remote.copy(
                                isPlaying = latest.isPlaying,
                                currentTrack = latest.currentTrack,
                            )
                        } ?: remote
                    }
                }
                val uniqueListeners = deduplicateNearbyListeners(
                    listeners,
                    authoritativeNearbyHandles,
                )
                current.copy(
                    nearbyListeners = uniqueListeners,
                    connectionState = if (
                        realtimeClient.connectionState.value is RealtimeConnectionState.Connected
                    ) ConnectionState.LIVE else ConnectionState.RECONNECTING,
                    discoveryRadiusMeters = MAX_NEARBY_RADIUS_METERS,
                    nearbyLoadState = if (uniqueListeners.isEmpty()) NearbyLoadState.EMPTY else NearbyLoadState.READY,
                    nearbyErrorMessage = null,
                    dataSourceLabel = "SERVER LIVE",
                    nearbyMeasurementDiagnostics = NearbyMeasurementDiagnostics(
                        method = location.source.toMeasurementMethod(),
                        accuracyMeters = location.accuracyMeters,
                        observedAtEpochMillis = location.observedAtEpochMillis,
                        uploadLatencyMillis = (System.currentTimeMillis() - location.observedAtEpochMillis)
                            .coerceAtLeast(0L),
                    ),
                )
            }
            Log.d(
                "MelodyLocation",
                "presence uploaded accuracy=${location.accuracyMeters?.toInt()}m users=${snapshot.items.size}",
            )
            refreshPopularTracks(token)
            true
        }.getOrElse { error ->
            Log.e("MelodyNearby", "Presence snapshot failed", error)
            if (!isCurrentSession(token)) return false
            _state.update { current ->
                val loadState = current.nearbyLoadState.keepSettledDuringRefresh(
                    NearbyLoadState.ERROR,
                )
                current.copy(
                    connectionState = ConnectionState.RECONNECTING,
                    nearbyLoadState = loadState,
                    nearbyErrorMessage = if (loadState == NearbyLoadState.ERROR) {
                        requestErrorMessage(error, "주변 서버에 연결하지 못했어요.")
                    } else {
                        current.nearbyErrorMessage
                    },
                )
            }
            false
        }
    }

    private fun refreshPopularTracks(token: String? = accessToken) {
        if (token.isNullOrBlank()) return
        val now = System.currentTimeMillis()
        while (true) {
            val previous = lastPopularRefreshStartedAt.get()
            if (now - previous < POPULAR_TRACK_REFRESH_INTERVAL_MILLIS) return
            if (lastPopularRefreshStartedAt.compareAndSet(previous, now)) break
        }
        val versionAtRequest = popularRealtimeVersion.get()
        scope.launch {
            runCatching { nearbyApi.popularTracks("Bearer $token") }
                .onSuccess { tracks ->
                    if (!isCurrentSession(token)) return@onSuccess
                    if (popularRealtimeVersion.get() != versionAtRequest) return@onSuccess
                    val incoming = tracks.map { it.toDomain() }
                    _state.update { current ->
                        current.copy(
                            popularTracks = incoming.preservingResolvedArtwork(current.popularTracks),
                        )
                    }
                    resolveMissingPopularArtwork()
                }
            // Popular-track aggregation is supplementary. A failed refresh must not downgrade an
            // otherwise healthy nearby-presence connection.
        }
    }

    private suspend fun currentLocation(
        allowInitialCoarseLocation: Boolean,
    ): android.location.Location? {
        val fine = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return null

        val cached = runCatching { fusedLocationClient.lastLocation.await() }.getOrNull()
        if (allowInitialCoarseLocation && cached != null && cached.hasAccuracy() &&
            cached.accuracy <= NearbyLocationPolicy.INITIAL_MAX_ACCURACY_METERS &&
            System.currentTimeMillis() - cached.time in 0L..PRESENCE_LOCATION_CACHE_MAX_AGE_MILLIS
        ) {
            return cached
        }

        val cancellation = CancellationTokenSource()
        return try {
            withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MILLIS) {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellation.token,
                ).await()
            }?.takeIf { location ->
                val accuracy = location.accuracy.takeIf { location.hasAccuracy() }
                if (allowInitialCoarseLocation) {
                    NearbyLocationPolicy.isUsableForInitialDiscovery(
                        observedAtMillis = location.time,
                        accuracyMeters = accuracy,
                        nowMillis = System.currentTimeMillis(),
                    )
                } else {
                    NearbyLocationPolicy.isUsable(
                        observedAtMillis = location.time,
                        accuracyMeters = accuracy,
                        nowMillis = System.currentTimeMillis(),
                    )
                }
            } ?: cached?.takeIf { location ->
                val accuracy = location.accuracy.takeIf { location.hasAccuracy() }
                val age = System.currentTimeMillis() - location.time
                if (allowInitialCoarseLocation) {
                    accuracy != null && accuracy <= NearbyLocationPolicy.INITIAL_MAX_ACCURACY_METERS &&
                        age in 0L..STATIONARY_LOCATION_FALLBACK_MAX_AGE_MILLIS
                } else {
                    NearbyLocationPolicy.isUsable(location.time, accuracy, System.currentTimeMillis())
                }
            }
        } catch (_: SecurityException) {
            null
        } finally {
            cancellation.cancel()
        }
    }

    private fun RemoteNearbyBubble.toDomain(): com.example.myapplication.core.model.NearbyListener {
        val resolvedAvatar = AvatarProfileResolver.resolve(
            remoteSeed = avatarSeed,
            remoteUrl = avatarUrl,
            stableIdentity = profileHandle ?: nearbyHandle,
            fallbackSeed = displayAlias,
        )
        return com.example.myapplication.core.model.NearbyListener(
            nearbyHandle = nearbyHandle,
            profileHandle = profileHandle,
            displayAlias = displayAlias,
            colorHex = profileColor.removePrefix("#").toLongOrNull(16) ?: 0x6750A4,
            displayPosition = com.example.myapplication.core.model.DisplayPosition(displayPosition.x, displayPosition.y),
            matchScore = matchScore,
            proximity = com.example.myapplication.core.model.Proximity.fromWire(proximity),
            proximityConfidence = runCatching { NearbyProximityConfidence.valueOf(distanceConfidence) }
                .getOrDefault(NearbyProximityConfidence.UNKNOWN),
            isPlaying = track != null,
            currentTrack = track?.let {
                Track(
                    id = "remote-$nearbyHandle",
                    title = it.title,
                    artist = it.artist,
                    artworkUrl = it.albumArtUrl,
                    platform = "REMOTE",
                )
            },
            commonGenres = emptyList(),
            relationship = relationship?.let {
                runCatching { RelationshipStatus.valueOf(it) }.getOrDefault(RelationshipStatus.NONE)
            } ?: RelationshipStatus.NONE,
            canReact = canReact ?: true,
            avatarUrl = resolvedAvatar.url,
            tasteMatch = tasteMatch?.toDomain(),
        )
    }

    private fun applyFollowResponse(handle: String, response: RemoteFollowResponse) {
        val relationship = runCatching { RelationshipStatus.valueOf(response.relationship) }
            .getOrDefault(RelationshipStatus.NONE)
        response.roomId?.takeIf { response.mutual }?.let { roomId ->
            persistChatRoomHidden(roomId, hidden = false)
        }
        synchronized(directStateLock) {
            directNearbyByHandle = directNearbyByHandle.mapValues { (_, listener) ->
                if (listener.nearbyHandle == handle) listener.copy(relationship = relationship) else listener
            }
            directUsersByBeacon = directUsersByBeacon.mapValues { (_, listener) ->
                if (listener.nearbyHandle == handle) listener.copy(relationship = relationship) else listener
            }
        }
        _state.update { current ->
            val peer = current.nearbyListeners.firstOrNull { it.nearbyHandle == handle }
            val listeners = current.nearbyListeners.map {
                if (it.nearbyHandle == handle) it.copy(relationship = relationship) else it
            }
            val newChat = response.roomId?.takeIf { response.mutual }?.let { roomId ->
                ChatPreview(
                    roomId = roomId,
                    peerHandle = handle,
                    peerAlias = response.peerAlias,
                    peerColorHex = response.peerColor.removePrefix("#").toLongOrNull(16)
                        ?: peer?.colorHex ?: 0x6750A4,
                    peerAvatarUrl = peer?.avatarUrl,
                    lastMessage = "서로 팔로우했어요",
                    relativeTime = "방금",
                    unreadCount = 0,
                    relationship = RelationshipStatus.MUTUAL,
                    hasMessages = false,
                )
            }
            val chatsWithoutPeer = current.chats.filterNot { it.peerHandle == handle }
            val notification = if (response.mutual && peer != null) {
                InboxNotification(
                    id = "mutual-${UUID.randomUUID()}",
                    type = NotificationType.MUTUAL_FOLLOW,
                    actorAlias = peer.displayAlias,
                    actorColorHex = peer.colorHex,
                    actorProfileHandle = peer.profileHandle,
                    actorAvatarUrl = peer.avatarUrl,
                    preview = "서로 팔로우했어요. 이제 대화할 수 있어요",
                    relativeTime = "방금",
                )
            } else null
            current.copy(
                nearbyListeners = listeners,
                chats = listOfNotNull(newChat) + chatsWithoutPeer,
                notifications = listOfNotNull(notification) + current.notifications,
                feedbackMessage = when {
                    response.mutual -> "맞팔이 성립됐어요. 인박스에서 대화해 보세요"
                    response.following -> "팔로우했어요"
                    else -> "팔로우를 취소했어요"
                },
            )
        }
        if (response.mutual) loadChatRooms()
        loadSocialConnections()
    }

    private fun handleRealtimeConnectionState(realtimeState: RealtimeConnectionState) {
        if (accessToken == null) return
        if (_state.value.sharingState !in setOf(SharingState.STARTING, SharingState.ACTIVE)) return
        val connectionState = when (realtimeState) {
            RealtimeConnectionState.Disconnected -> ConnectionState.DISCONNECTED
            is RealtimeConnectionState.Connecting -> ConnectionState.CONNECTING
            is RealtimeConnectionState.Connected -> ConnectionState.LIVE
            is RealtimeConnectionState.Reconnecting -> ConnectionState.RECONNECTING
        }
        _state.update { current -> current.copy(connectionState = connectionState) }
    }

    private fun handleRealtimeEvent(event: RealtimeEvent) {
        if (accessToken == null) return
        if (event is RealtimeEvent.ChatRoomCreated ||
            event is RealtimeEvent.ChatMessageCreated ||
            event is RealtimeEvent.ChatMessageRead ||
            event is RealtimeEvent.ChatRoomUpdated
        ) {
            chatRealtimeVersion.incrementAndGet()
        }
        if (event is RealtimeEvent.NearbyMusicUpdated || event is RealtimeEvent.NearbySnapshot) {
            nearbyRealtimeVersion.incrementAndGet()
        }
        if (event is RealtimeEvent.PopularTracksUpdated) popularRealtimeVersion.incrementAndGet()
        when (event) {
            is RealtimeEvent.ChatRoomCreated -> loadChatRooms()
            is RealtimeEvent.ChatMessageCreated -> applyChatMessageCreated(event)
            is RealtimeEvent.ChatMessageRead -> applyChatMessageRead(event)
            is RealtimeEvent.ChatRoomUpdated -> applyChatRoomUpdated(event)
            is RealtimeEvent.NearbyReactionCreated -> applyNearbyReaction(event)
            is RealtimeEvent.NearbyMusicUpdated -> applyNearbyMusicUpdated(event)
            is RealtimeEvent.NearbySnapshot -> applyNearbySnapshot(event)
            is RealtimeEvent.PopularTracksUpdated -> applyPopularTracksUpdated(event)
            is RealtimeEvent.NotificationCreated -> applyNotificationCreated(event)
            is RealtimeEvent.SubLoungeUpdated -> Unit
            is RealtimeEvent.LocationLoungeUpdated -> Unit
            is RealtimeEvent.ServerError -> {
                event.envelope.payload.message?.takeIf(String::isNotBlank)?.let { message ->
                    _state.update { it.copy(feedbackMessage = message) }
                }
            }
            is RealtimeEvent.ParsingError,
            is RealtimeEvent.Unknown -> Unit
        }
    }

    private fun applyChatMessageCreated(event: RealtimeEvent.ChatMessageCreated) {
        val payload = event.envelope.payload
        val messageId = payload.messageId?.takeIf(String::isNotBlank) ?: return
        val clientMessageId = payload.clientMessageId?.takeIf(String::isNotBlank) ?: messageId
        val roomId = payload.roomId?.takeIf(String::isNotBlank) ?: return
        val content = payload.content?.trim()?.takeIf(String::isNotEmpty) ?: return
        var shouldRefreshRooms = false
        var shouldMarkRead = false
        _state.update { current ->
            val currentMessages = current.chatMessages[roomId].orEmpty()
            val pending = currentMessages.firstOrNull { it.clientMessageId == clientMessageId }
            val existing = currentMessages.firstOrNull { it.messageId == messageId }
            val isMine = payload.isMine ?: pending?.isMine ?: false
            val delivered = ChatMessage(
                messageId = messageId,
                clientMessageId = clientMessageId,
                roomId = roomId,
                isMine = isMine,
                content = content.take(1_000),
                sentAtLabel = chatSentAtLabel(payload.sentAt ?: event.envelope.timestamp),
                deliveryState = existing?.deliveryState?.takeIf { it == DeliveryState.READ }
                    ?: DeliveryState.SENT,
                sentAtEpochMillis = (payload.sentAt ?: event.envelope.timestamp).toServerEpochMillis(),
            )
            val mergedMessages = when {
                existing != null -> currentMessages.map { if (it.messageId == messageId) delivered else it }
                pending != null -> currentMessages.map {
                    if (it.clientMessageId == clientMessageId) delivered else it
                }
                else -> currentMessages + delivered
            }.distinctBy(ChatMessage::messageId)
            val active = activeChatRoomId == roomId
            shouldMarkRead = active && !isMine
            shouldRefreshRooms = current.chats.none { it.roomId == roomId }
            current.copy(
                chatMessages = current.chatMessages + (roomId to mergedMessages),
                chats = current.chats.map { chat ->
                    if (chat.roomId != roomId) chat else chat.copy(
                        lastMessage = delivered.content,
                        relativeTime = "방금",
                        unreadCount = if (isMine || active) 0 else chat.unreadCount + 1,
                        hasMessages = true,
                        isHidden = false,
                    )
                },
            )
        }
        if (payload.isMine != true) persistChatRoomHidden(roomId, hidden = false)
        if (shouldRefreshRooms) loadChatRooms()
        if (shouldMarkRead) markChatRoomRead(roomId)
    }

    private fun applyChatMessageRead(event: RealtimeEvent.ChatMessageRead) {
        val payload = event.envelope.payload
        val roomId = payload.roomId?.takeIf(String::isNotBlank) ?: return
        _state.update { current ->
            val currentMessages = current.chatMessages[roomId].orEmpty()
            val lastReadIndex = payload.lastReadMessageId?.let { lastReadMessageId ->
                currentMessages.indexOfFirst { it.messageId == lastReadMessageId }
            } ?: -1
            val messages = currentMessages.mapIndexed { index, message ->
                if (payload.isMine == false && lastReadIndex >= 0 && index <= lastReadIndex &&
                    message.isMine && message.deliveryState != DeliveryState.FAILED
                ) {
                    message.copy(deliveryState = DeliveryState.READ)
                } else message
            }
            current.copy(
                chatMessages = current.chatMessages + (roomId to messages),
                chats = current.chats.map { chat ->
                    if (chat.roomId == roomId && payload.isMine == true) {
                        chat.copy(unreadCount = 0)
                    } else chat
                },
            )
        }
    }

    private fun applyChatRoomUpdated(event: RealtimeEvent.ChatRoomUpdated) {
        val payload = event.envelope.payload
        val roomId = payload.roomId?.takeIf(String::isNotBlank) ?: return
        var found = false
        _state.update { current ->
            current.copy(
                chats = current.chats.map { chat ->
                    if (chat.roomId != roomId) return@map chat
                    found = true
                    chat.copy(
                        lastMessage = payload.lastMessageContent ?: chat.lastMessage,
                        relativeTime = if (payload.lastMessageAt == null) chat.relativeTime else "방금",
                        unreadCount = if (activeChatRoomId == roomId) 0
                        else payload.unreadCount?.coerceAtLeast(0) ?: chat.unreadCount,
                        hasMessages = chat.hasMessages || payload.lastMessageContent != null,
                        isHidden = if ((payload.unreadCount ?: 0) > 0) false else chat.isHidden,
                    )
                }
            )
        }
        if ((payload.unreadCount ?: 0) > 0) persistChatRoomHidden(roomId, hidden = false)
        if (!found) loadChatRooms()
    }

    private fun applyNearbyReaction(event: RealtimeEvent.NearbyReactionCreated) {
        val payload = event.envelope.payload
        val alias = payload.senderAlias?.takeIf(String::isNotBlank) ?: "주변 사용자"
        val actorColor = payload.senderNearbyHandle?.let { handle ->
            _state.value.nearbyListeners.firstOrNull { it.nearbyHandle == handle }?.colorHex
        }
        val actorAvatarUrl = _state.value.nearbyListeners.firstOrNull { listener ->
            listener.nearbyHandle == payload.senderNearbyHandle ||
                listener.profileHandle == payload.senderProfileHandle
        }?.avatarUrl ?: payload.senderAvatarUrl
        val reaction = reactionLabelForType(payload.reactionType)
        val preview = payload.trackTitle?.takeIf(String::isNotBlank)?.let { title ->
            "$reaction · ‘$title’"
        } ?: reaction
        val notification = InboxNotification(
            id = payload.reactionId?.takeIf(String::isNotBlank) ?: event.envelope.eventId,
            type = NotificationType.REACTION,
            actorAlias = alias,
            actorColorHex = actorColor,
            actorProfileHandle = payload.senderProfileHandle?.takeIf(String::isNotBlank),
            actorAvatarUrl = actorAvatarUrl,
            preview = preview,
            relativeTime = notificationRelativeTime(
                payload.createdAt.toServerEpochMillis()
                    ?: event.envelope.timestamp.toServerEpochMillis()
                    ?: System.currentTimeMillis(),
            ),
        )
        _state.update { current ->
            current.copy(
                notifications = listOf(notification) + current.notifications
                    .filterNot { it.id == notification.id },
                feedbackMessage = "$alias 님이 ‘$reaction’ 리액션을 보냈어요",
            )
        }
    }

    private fun applyNearbyMusicUpdated(event: RealtimeEvent.NearbyMusicUpdated) {
        val payload = event.envelope.payload
        val handle = payload.nearbyHandle?.takeIf(String::isNotBlank) ?: return
        val profile = payload.profileHandle?.trim()?.takeIf(String::isNotEmpty)?.lowercase()
            ?: profileByNearbyHandle[handle]
            ?: _state.value.nearbyListeners.firstOrNull { it.nearbyHandle == handle }
                ?.profileHandle?.trim()?.takeIf(String::isNotEmpty)?.lowercase()
        if (profile != null) profileByNearbyHandle[handle] = profile
        val eventAt = event.envelope.timestamp.toServerEpochMillis() ?: System.currentTimeMillis()
        val aliasHandles = buildSet {
            add(handle)
            if (profile != null) {
                profileByNearbyHandle.forEach { (aliasHandle, aliasProfile) ->
                    if (aliasProfile == profile) add(aliasHandle)
                }
                _state.value.nearbyListeners.forEach { listener ->
                    if (listener.profileHandle?.trim()?.lowercase() == profile) {
                        add(listener.nearbyHandle)
                    }
                }
            }
        }
        aliasHandles.forEach { aliasHandle -> lastMusicEventAtByHandle[aliasHandle] = eventAt }
        val title = payload.track?.title?.trim()?.takeIf(String::isNotEmpty)
        val artist = payload.track?.artist?.trim()?.takeIf(String::isNotEmpty)
        val playingTrack = if (payload.isPlaying == true && title != null && artist != null) {
            Track(
                id = "remote-${profile ?: handle}-${title.hashCode()}-${artist.hashCode()}",
                title = title,
                artist = artist,
                artworkUrl = payload.track?.albumArtUrl,
                platform = "REMOTE",
            )
        } else null
        synchronized(directStateLock) {
            directNearbyByHandle = directNearbyByHandle.mapValues { (_, listener) ->
                if (listener.nearbyHandle in aliasHandles ||
                    profile != null && listener.profileHandle?.trim()?.lowercase() == profile
                ) {
                    listener.copy(isPlaying = playingTrack != null, currentTrack = playingTrack)
                } else listener
            }
            directUsersByBeacon = directUsersByBeacon.mapValues { (_, listener) ->
                if (listener.nearbyHandle in aliasHandles ||
                    profile != null && listener.profileHandle?.trim()?.lowercase() == profile
                ) {
                    listener.copy(isPlaying = playingTrack != null, currentTrack = playingTrack)
                } else listener
            }
        }
        var found = false
        _state.update { current ->
            current.copy(
                nearbyListeners = deduplicateNearbyListeners(
                    current.nearbyListeners.map { listener ->
                        val matches = listener.nearbyHandle in aliasHandles ||
                            profile != null && listener.profileHandle?.trim()?.lowercase() == profile
                        if (!matches) return@map listener
                        found = true
                        listener.copy(isPlaying = playingTrack != null, currentTrack = playingTrack)
                    },
                    authoritativeNearbyHandles,
                ),
                snapshotSequence = current.snapshotSequence + 1,
            )
        }
        if (!found) refreshNearbySnapshot()
    }

    private fun applyNearbySnapshot(event: RealtimeEvent.NearbySnapshot) {
        val snapshot = event.envelope.payload
        val removedHandles = snapshot.removedNearbyHandles.orEmpty().toSet()
        var currentListeners = _state.value.nearbyListeners
        if (removedHandles.isNotEmpty()) {
            explicitlyRemovedNearbyHandles.addAll(removedHandles)
            currentListeners = nearbyProximityStabilizer.remove(currentListeners, removedHandles)
            synchronized(directStateLock) {
                directNearbyByHandle = directNearbyByHandle.filterKeys { it !in removedHandles }
                directUsersByBeacon = directUsersByBeacon.filterValues {
                    it.nearbyHandle !in removedHandles
                }
            }
        }
        val incomingListeners = preserveNewerRealtimeMusic(
            mergeDirectNearby(snapshot.items.map { it.toDomain() }),
            snapshot.generatedAt,
        )
        val listeners = deduplicateNearbyListeners(
            nearbyProximityStabilizer.stabilize(currentListeners, incomingListeners),
            incomingListeners.mapTo(mutableSetOf()) { it.nearbyHandle },
        )
        _state.update { current ->
            current.copy(
                nearbyListeners = listeners,
                discoveryRadiusMeters = MAX_NEARBY_RADIUS_METERS,
                nearbyLoadState = if (listeners.isEmpty()) NearbyLoadState.EMPTY else NearbyLoadState.READY,
                nearbyErrorMessage = null,
                snapshotSequence = current.snapshotSequence + 1,
            )
        }
    }

    private fun applyPopularTracksUpdated(event: RealtimeEvent.PopularTracksUpdated) {
        val tracks = event.envelope.payload.tracks.mapNotNull { remote ->
            val title = remote.title?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val artist = remote.artist?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            PopularTrack(
                track = Track(
                    id = "popular-${title.hashCode()}-${artist.hashCode()}",
                    title = title,
                    artist = artist,
                    artworkUrl = remote.artworkUrl,
                    platform = "SERVER_AGGREGATE",
                ),
                listenerCount = remote.listenerCount?.coerceAtLeast(0) ?: 0,
                reactionCount = remote.reactionCount?.coerceAtLeast(0) ?: 0,
            )
        }
        _state.update { current ->
            current.copy(
                popularTracks = tracks.preservingResolvedArtwork(current.popularTracks),
            )
        }
        resolveMissingPopularArtwork()
    }

    private fun resolveMissingPopularArtwork() {
        _state.value.popularTracks
            .filter { it.track.artworkUrl.isNullOrBlank() }
            .forEach { popularTrack ->
                scope.launch {
                    val resolvedUrl = runCatching {
                        musicSearchRepository.trackArtwork(
                            popularTrack.track.title,
                            popularTrack.track.artist,
                        )
                    }.getOrNull() ?: return@launch
                    _state.update { current ->
                        current.copy(
                            popularTracks = current.popularTracks.map { currentTrack ->
                                if (currentTrack.track.id == popularTrack.track.id &&
                                    currentTrack.track.artworkUrl.isNullOrBlank()
                                ) {
                                    currentTrack.copy(track = currentTrack.track.copy(artworkUrl = resolvedUrl))
                                } else currentTrack
                            },
                        )
                    }
                }
            }
    }

    private fun resolvePublicProfileArtwork(profile: PublicProfile) {
        profile.nowPlaying?.takeIf { it.artworkUrl.isNullOrBlank() }?.let { nowPlaying ->
            scope.launch {
                val resolvedUrl = runCatching {
                    musicSearchRepository.trackArtwork(nowPlaying.title, nowPlaying.artist)
                }.getOrNull() ?: return@launch
                _state.update { current ->
                    val selected = current.selectedPublicProfile
                    if (selected?.profileHandle != profile.profileHandle ||
                        selected.nowPlaying?.title != nowPlaying.title ||
                        selected.nowPlaying.artist != nowPlaying.artist
                    ) current else current.copy(
                        selectedPublicProfile = selected.copy(
                            nowPlaying = selected.nowPlaying.copy(artworkUrl = resolvedUrl),
                        ),
                    )
                }
            }
        }

        val sourceArtists = profile.favoriteArtists
        if (sourceArtists.isEmpty()) return
        scope.launch {
            val resolvedArtists = resolveArtistArtwork(sourceArtists)
            if (resolvedArtists == sourceArtists) return@launch
            _state.update { current ->
                val selected = current.selectedPublicProfile
                if (selected?.profileHandle != profile.profileHandle ||
                    !selected.favoriteArtists.hasSameArtistSelection(sourceArtists)
                ) current else current.copy(
                    selectedPublicProfile = selected.copy(favoriteArtists = resolvedArtists),
                )
            }
        }
    }

    private fun resolveOwnProfileArtistArtwork() {
        val accountId = _state.value.activeAccountId
        val sourceArtists = _state.value.profile.favoriteArtists
        if (sourceArtists.isEmpty()) return
        scope.launch {
            val resolvedArtists = resolveArtistArtwork(sourceArtists)
            if (resolvedArtists == sourceArtists) return@launch
            _state.update { current ->
                if (current.activeAccountId != accountId ||
                    !current.profile.favoriteArtists.hasSameArtistSelection(sourceArtists)
                ) current else current.copy(
                    profile = current.profile.copy(favoriteArtists = resolvedArtists),
                )
            }
            val current = _state.value
            if (current.activeAccountId == accountId &&
                current.profile.favoriteArtists.hasSameArtistSelection(resolvedArtists)
            ) {
                persistProfile(current.profile)
            }
        }
    }

    private suspend fun resolveArtistArtwork(artists: List<ProfileArtist>): List<ProfileArtist> =
        coroutineScope {
            artists.map { artist ->
                async {
                    if (artist.imageUrl.isDeezerArtistImage()) return@async artist
                    val deezerImage = runCatching {
                        musicSearchRepository.artistImage(artist.name)
                    }.getOrNull()
                    deezerImage?.let { artist.copy(imageUrl = it) } ?: artist
                }
            }.awaitAll()
        }

    private fun applyNotificationCreated(event: RealtimeEvent.NotificationCreated) {
        val payload = event.envelope.payload
        val preview = payload.body?.takeIf(String::isNotBlank)
            ?: payload.title?.takeIf(String::isNotBlank)
            ?: return
        val notification = InboxNotification(
            id = payload.notificationId?.takeIf(String::isNotBlank) ?: event.envelope.eventId,
            type = NotificationType.SYSTEM,
            actorAlias = null,
            actorColorHex = null,
            preview = preview,
            relativeTime = "방금",
        )
        _state.update { current ->
            current.copy(
                notifications = listOf(notification) + current.notifications
                    .filterNot { it.id == notification.id }
            )
        }
    }

    private fun performFullRealtimeSync() {
        loadChatRooms()
        refreshPopularTracks()
        syncReceivedReactions()
        refreshNearbySnapshot()
        loadBlockedUsers()
        activeChatRoomId?.let(::markChatRoomRead)
    }

    private fun refreshNearbySnapshot() {
        val token = accessToken ?: return
        val versionAtRequest = nearbyRealtimeVersion.get()
        scope.launch {
            runCatching { nearbyApi.snapshot("Bearer $token") }
                .onSuccess { snapshot ->
                    if (!isCurrentSession(token)) return@onSuccess
                    val incomingListeners = preserveNewerRealtimeMusic(
                        mergeDirectNearby(snapshot.items.map { it.toDomain() }),
                        snapshot.generatedAt,
                    )
                    val stabilizedListeners = deduplicateNearbyListeners(
                        nearbyProximityStabilizer.stabilize(
                            _state.value.nearbyListeners,
                            incomingListeners,
                        ),
                        incomingListeners.mapTo(mutableSetOf()) { it.nearbyHandle },
                    )
                    _state.update { current ->
                        val listeners = if (nearbyRealtimeVersion.get() == versionAtRequest) {
                            stabilizedListeners
                        } else {
                            val currentByHandle = current.nearbyListeners.associateBy { it.nearbyHandle }
                            stabilizedListeners.map { remote ->
                                currentByHandle[remote.nearbyHandle]?.let { latest ->
                                    remote.copy(
                                        isPlaying = latest.isPlaying,
                                        currentTrack = latest.currentTrack,
                                    )
                                } ?: remote
                            }
                        }
                        val uniqueListeners = deduplicateNearbyListeners(
                            listeners,
                            authoritativeNearbyHandles,
                        )
                        current.copy(
                            nearbyListeners = uniqueListeners,
                            discoveryRadiusMeters = MAX_NEARBY_RADIUS_METERS,
                            nearbyLoadState = if (uniqueListeners.isEmpty()) {
                                NearbyLoadState.EMPTY
                            } else NearbyLoadState.READY,
                            nearbyErrorMessage = null,
                            snapshotSequence = current.snapshotSequence + 1,
                        )
                    }
                }
        }
    }

    private fun syncReceivedReactions(token: String? = accessToken) {
        if (token.isNullOrBlank()) return
        scope.launch {
            runCatching { nearbyApi.receivedReactions("Bearer $token") }
                .onSuccess { reactions ->
                    if (!isCurrentSession(token)) return@onSuccess
                    reactions.asReversed().forEach { reaction ->
                        realtimeInboxStore?.recordReaction(
                            reactionId = reaction.reactionId,
                            senderAlias = reaction.senderAlias,
                            senderProfileHandle = reaction.senderProfileHandle,
                            senderAvatarUrl = reaction.senderAvatarUrl,
                            reactionType = reaction.reactionType,
                            trackTitle = reaction.trackTitle,
                            createdAtEpochMillis = reaction.createdAt.toServerEpochMillis()
                                ?: System.currentTimeMillis(),
                        )
                    }
                    val remoteNotifications = reactions.map { reaction ->
                        val label = reactionLabelForType(reaction.reactionType)
                        InboxNotification(
                            id = reaction.reactionId,
                            type = NotificationType.REACTION,
                            actorAlias = reaction.senderAlias,
                            actorColorHex = null,
                            actorProfileHandle = reaction.senderProfileHandle,
                            actorAvatarUrl = reaction.senderAvatarUrl,
                            preview = reaction.trackTitle?.takeIf(String::isNotBlank)?.let {
                                "$label · ‘$it’"
                            } ?: label,
                            relativeTime = notificationRelativeTime(
                                reaction.createdAt.toServerEpochMillis()
                                    ?: System.currentTimeMillis(),
                            ),
                        )
                    }
                    val durable = realtimeInboxStore?.load() ?: remoteNotifications
                    _state.update { current ->
                        val durableIds = durable.map(InboxNotification::id).toSet()
                        current.copy(
                            notifications = durable + current.notifications
                                .filterNot { it.id in durableIds }
                        )
                    }
                }
        }
    }

    private fun startChatSync(token: String) {
        chatSyncJob?.cancel()
        loadChatRooms()
        chatSyncJob = scope.launch {
            while (isActive && accessToken == token) {
                delay(CHAT_FALLBACK_SYNC_INTERVAL_MILLIS)
                if (realtimeClient.connectionState.value !is RealtimeConnectionState.Connected) {
                    loadChatRooms()
                }
            }
        }
    }

    private fun loadChatRooms() {
        val token = accessToken ?: return
        val realtimeVersionAtRequest = chatRealtimeVersion.get()
        scope.launch {
            runCatching { socialApi.chatRooms("Bearer $token") }
                .onSuccess { rooms ->
                    if (!isCurrentSession(token)) return@onSuccess
                    val storedHiddenIds = hiddenChatRoomIds()
                    val newlyActiveRoomIds = rooms
                        .filter { it.unreadCount > 0 }
                        .map(RemoteChatSummary::roomId)
                        .toSet()
                    val activeHiddenIds = storedHiddenIds - newlyActiveRoomIds
                    if (activeHiddenIds != storedHiddenIds) persistHiddenChatRoomIds(activeHiddenIds)
                    val remoteRooms = rooms.map { room ->
                        room.toDomain(isHidden = room.roomId in activeHiddenIds)
                    }
                    _state.update { current ->
                        val realtimeAdvanced = chatRealtimeVersion.get() != realtimeVersionAtRequest
                        if (!realtimeAdvanced) {
                            current.copy(chats = remoteRooms)
                        } else {
                            val currentById = current.chats.associateBy(ChatPreview::roomId)
                            val remoteIds = remoteRooms.map(ChatPreview::roomId).toSet()
                            current.copy(
                                chats = remoteRooms.map { remote ->
                                    currentById[remote.roomId] ?: remote
                                } + current.chats.filter { it.roomId !in remoteIds }
                            )
                        }
                    }
                    rooms.forEach { room ->
                        scope.launch {
                            runCatching { socialApi.chatMessages("Bearer $token", room.roomId) }
                                .onSuccess { messages ->
                                    if (!isCurrentSession(token)) return@onSuccess
                                    _state.update { current ->
                                        val existing = current.chatMessages[room.roomId].orEmpty()
                                        val remoteMessages = messages.map { message ->
                                            val previous = existing.firstOrNull {
                                                it.messageId == message.messageId
                                            }
                                            ChatMessage(
                                                message.messageId,
                                                message.clientMessageId ?: message.messageId,
                                                message.roomId,
                                                message.isMine,
                                                message.content,
                                                chatSentAtLabel(message.sentAt),
                                                if (message.isMine && message.readByPeer == true) {
                                                    DeliveryState.READ
                                                } else previous?.deliveryState?.takeIf {
                                                    it == DeliveryState.READ
                                                } ?: DeliveryState.SENT,
                                                message.sentAt.toServerEpochMillis(),
                                            )
                                        }
                                        val remoteClientIds = remoteMessages
                                            .map(ChatMessage::clientMessageId)
                                            .toSet()
                                        val remoteMessageIds = remoteMessages
                                            .map(ChatMessage::messageId)
                                            .toSet()
                                        val localOnly = existing.filter { message ->
                                            message.messageId !in remoteMessageIds &&
                                                message.clientMessageId !in remoteClientIds
                                        }
                                        current.copy(
                                            chatMessages = current.chatMessages + (
                                                room.roomId to (remoteMessages + localOnly)
                                            )
                                        )
                                    }
                                }
                        }
                    }
                }.onFailure { error ->
                    if (isCurrentSession(token)) showRequestError(error, "대화방을 불러오지 못했어요")
                }
        }
    }

    private fun RemoteChatSummary.toDomain(isHidden: Boolean = false) = ChatPreview(
        roomId = roomId,
        peerHandle = peerHandle ?: "chat-$roomId",
        peerAlias = peerAlias,
        peerColorHex = peerColor.removePrefix("#").toLongOrNull(16) ?: 0x6750A4,
        peerAvatarUrl = peerAvatarUrl,
        lastMessage = lastMessage.orEmpty(),
        relativeTime = if (lastMessageAt == null) "새 대화" else "최근",
        unreadCount = unreadCount,
        relationship = RelationshipStatus.MUTUAL,
        hasMessages = lastMessage != null,
        isHidden = isHidden,
    )

    private fun com.example.myapplication.data.remote.RemoteBlockedUser.toDomain() = BlockedUser(
        blockId = blockId,
        displayAlias = displayAlias,
        colorHex = profileColor.removePrefix("#").toLongOrNull(16) ?: 0x6750A4,
        blockedAt = blockedAt,
    )

    private fun RemoteSocialConnection.toDomain(): SocialConnection {
        val resolvedAvatar = AvatarProfileResolver.resolve(
            remoteSeed = null,
            remoteUrl = avatarUrl,
            stableIdentity = profileHandle,
            fallbackSeed = displayAlias,
        )
        return SocialConnection(
            relationshipId = relationshipId,
            profileHandle = profileHandle,
            displayAlias = displayAlias,
            colorHex = profileColor.removePrefix("#").toLongOrNull(16) ?: 0x6750A4,
            avatarUrl = resolvedAvatar.url,
            bio = bio,
            mutual = mutual,
            followedAt = followedAt,
        )
    }

    private fun RemoteProfileStats.toDomain() = ProfileStats(
        followingCount = followingCount,
        followerCount = followerCount,
    )

    private fun RemoteTasteFingerprint.toDomain() = TasteFingerprint(
        genres = genres.orEmpty().map { TasteMetric(it.label, it.count, it.ratio) },
    )

    private fun RemoteCommonTasteSummary.toDomain() = CommonTasteSummary(
        score = score,
        confidence = confidence,
        metrics = metrics.orEmpty().map {
            CommonTasteMetric(it.label, it.type, it.score, it.evidenceCount)
        },
        algorithmVersion = algorithmVersion,
        sampleSize = sampleSize,
        calculatedAt = calculatedAt,
    )

    private fun RemoteProfileMelodyAlias.toDomain() = ProfileMelodyAlias(id, notes, tone, tempo)

    private fun RemoteProfileTrack.toDomain() = ProfileTrack(
        rank = rank,
        provider = provider,
        providerTrackId = providerTrackId,
        title = title,
        artist = artist,
        album = album,
        artworkUrl = artworkUrl,
        genreTags = genreTags.orEmpty(),
    )

    private fun ProfileTrack.toRemote() = RemoteProfileTrack(
        rank = rank,
        provider = provider,
        providerTrackId = providerTrackId,
        title = title,
        artist = artist,
        album = album,
        artworkUrl = artworkUrl,
        genreTags = genreTags,
    )

    private fun RemoteProfileArtist.toDomain() = ProfileArtist(
        rank = rank,
        provider = provider,
        providerArtistId = providerArtistId,
        name = name,
        imageUrl = imageUrl,
        genreTags = genreTags.orEmpty(),
    )

    private fun ProfileArtist.toRemote() = RemoteProfileArtist(
        rank = rank,
        provider = provider,
        providerArtistId = providerArtistId,
        name = name,
        imageUrl = imageUrl,
        genreTags = genreTags,
    )

    private fun RemotePublicProfile.toDomain(): PublicProfile {
        val resolvedAvatar = AvatarProfileResolver.resolve(
            remoteSeed = avatarSeed,
            remoteUrl = avatarUrl,
            stableIdentity = profileHandle,
            fallbackSeed = displayName,
        )
        return PublicProfile(
            profileHandle = profileHandle,
            isSelf = relationship == "SELF",
            displayName = displayName,
            colorHex = profileColor.removePrefix("#").toLongOrNull(16) ?: 0x6750A4,
            bio = bio.orEmpty(),
            avatarSeed = resolvedAvatar.seed,
            avatarUrl = resolvedAvatar.url,
            genres = genres.orEmpty(),
            melodyAlias = melodyAlias?.toDomain(),
            stats = stats.toDomain(),
            tasteFingerprint = tasteFingerprint?.toDomain() ?: TasteFingerprint(),
            relationship = runCatching { RelationshipStatus.valueOf(relationship) }.getOrDefault(RelationshipStatus.NONE),
            following = following,
            mutual = mutual,
            sharedFollowers = sharedFollowers.orEmpty().map {
                SharedFollowerPreview(
                    profileHandle = it.profileHandle,
                    displayName = it.displayName,
                    avatarUrl = it.avatarUrl,
                )
            },
            sharedFollowerCount = sharedFollowerCount,
            signatureTracks = signatureTracks.orEmpty().map { it.toDomain() },
            favoriteArtists = favoriteArtists.orEmpty().map { it.toDomain() },
            nowPlaying = nowPlaying?.let {
                ProfileNowPlaying(
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    artworkUrl = it.artworkUrl,
                    isPlaying = it.isPlaying,
                    durationMs = it.durationMs,
                    positionMs = it.positionMs,
                    positionObservedAt = it.positionObservedAt,
                    observedAt = it.observedAt,
                    expiresAt = it.expiresAt,
                )
            },
            commonTaste = commonTaste?.let { taste ->
                taste.toDomain()
            },
            sectionStates = sectionStates.orEmpty(),
        )
    }

    private fun RemotePopularTrack.toDomain() = PopularTrack(
        track = Track(
            id = "popular-${title.hashCode()}-${artist.hashCode()}",
            title = title,
            artist = artist,
            artworkUrl = artworkUrl,
            platform = "SERVER_AGGREGATE",
        ),
        listenerCount = listenerCount.coerceAtLeast(0),
        reactionCount = reactionCount.coerceAtLeast(0),
    )

    private fun reactionLabelForType(type: String?): String = when (type) {
        "LIKE" -> "이 곡 좋아요"
        "SAME_TASTE" -> "취향이 닮았어요"
        "GREAT_PICK" -> "선곡 멋져요"
        "LISTEN_TOGETHER" -> "같이 듣고 싶어요"
        else -> "새 리액션"
    }

    private fun isCurrentSession(token: String): Boolean = accessToken == token

    private fun showRequestError(error: Throwable, fallback: String) {
        _state.update { it.copy(feedbackMessage = requestErrorMessage(error, fallback)) }
    }

    private fun requestErrorMessage(error: Throwable, fallback: String): String = when {
        error is HttpException && error.code() == 429 -> "요청이 너무 많아요. 잠시 후 다시 시도해 주세요."
        error is HttpException && error.code() == 403 -> "현재 관계나 공개 범위에서는 요청할 수 없어요."
        error is HttpException && error.code() == 404 -> "사용자가 더 이상 주변에 없어요."
        else -> fallback
    }

    override fun close() {
        passiveNearbyDiscovery.stop()
        locationSyncSignal.close()
        if (locationReceiverRegistered) {
            runCatching { applicationContext.unregisterReceiver(locationReceiver) }
            locationReceiverRegistered = false
        }
        if (ownsRealtimeClient) realtimeClient.close()
        scope.coroutineContext[Job]?.cancel()
    }

    private fun setActiveOwner(ownerUserId: String?) {
        activeOwnerId = ownerUserId
    }

    private fun profileScopedKey(base: String): String =
        activeOwnerId?.let { owner -> "$base::$owner" } ?: base

    private data class LocationFix(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float?,
        val source: String,
        val observedAtEpochMillis: Long,
    )
}

internal fun String.toMeasurementMethod(): NearbyMeasurementMethod = when (lowercase()) {
    "gps" -> NearbyMeasurementMethod.GPS
    "fused" -> NearbyMeasurementMethod.FUSED
    else -> NearbyMeasurementMethod.UNKNOWN
}
