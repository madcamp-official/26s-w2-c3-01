package com.example.myapplication.core.model

enum class MainTab(val label: String) {
    HOME("홈"),
    NEARBY("근처"),
    LOUNGE("라운지"),
    INBOX("채팅"),
    MY("마이")
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    LIVE,
    RECONNECTING
}

enum class SharingState {
    STOPPED,
    STARTING,
    ACTIVE,
    PERMISSION_REQUIRED,
    FAILED
}

enum class NearbyLoadState {
    IDLE,
    LOADING,
    READY,
    EMPTY,
    ERROR
}

enum class NearbyMeasurementMethod(val label: String) {
    UWB("UWB"),
    WIFI_RTT("Wi-Fi RTT"),
    BLUETOOTH("Bluetooth"),
    GPS("GPS"),
    FUSED("Fused"),
    UNKNOWN("위치"),
}

enum class NearbyProximityConfidence {
    HIGH,
    LOW,
    UNKNOWN,
}

data class NearbyMeasurementDiagnostics(
    val method: NearbyMeasurementMethod,
    val accuracyMeters: Float?,
    val observedAtEpochMillis: Long,
    val uploadLatencyMillis: Long?,
)

enum class Proximity(
    val label: String,
    val outerRadiusFraction: Float,
) {
    WITHIN_5M("5m 안쪽", 0.143f),
    WITHIN_10M("10m 안쪽", 0.286f),
    WITHIN_15M("15m 안쪽", 0.429f);

    companion object {
        /** Accepts one release of the former broad-distance contract during rollout. */
        fun fromWire(value: String?): Proximity = when (value?.trim()?.uppercase()) {
            "WITHIN_5M", "VERY_CLOSE" -> WITHIN_5M
            "WITHIN_10M", "CLOSE" -> WITHIN_10M
            "WITHIN_15M", "AROUND" -> WITHIN_15M
            else -> WITHIN_15M
        }
    }
}

enum class RelationshipStatus {
    NONE,
    FOLLOWING,
    FOLLOWS_ME,
    MUTUAL,
    BLOCKED
}

enum class NotificationType {
    REACTION,
    FOLLOW,
    MUTUAL_FOLLOW,
    SYSTEM
}

enum class DeliveryState {
    PENDING,
    SENT,
    READ,
    FAILED
}

data class DisplayPosition(
    val x: Float,
    val y: Float
)

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val platform: String = "MANUAL",
    val externalUrl: String? = null,
    val genreTags: List<String> = emptyList(),
    val moodTags: List<String> = emptyList()
)

data class NearbyListener(
    val nearbyHandle: String,
    val profileHandle: String? = null,
    val displayAlias: String,
    val colorHex: Long,
    val displayPosition: DisplayPosition,
    val matchScore: Int,
    val proximity: Proximity,
    val proximityConfidence: NearbyProximityConfidence = NearbyProximityConfidence.UNKNOWN,
    val isPlaying: Boolean,
    val currentTrack: Track?,
    val commonGenres: List<String>,
    val relationship: RelationshipStatus = RelationshipStatus.NONE,
    val canReact: Boolean = true,
    val isNew: Boolean = false,
    val avatarUrl: String? = null,
    val isDirectlyDetected: Boolean = false,
)

data class PopularTrack(
    val track: Track,
    val listenerCount: Int,
    val reactionCount: Int
)

data class PollOption(
    val id: String,
    val label: String,
    val voteCount: Int
)

data class LoungePoll(
    val id: String,
    val question: String,
    val options: List<PollOption>,
    val myChoice: String? = null,
    val isOpen: Boolean = true
) {
    val totalVotes: Int get() = options.sumOf { it.voteCount }
}

data class InboxNotification(
    val id: String,
    val type: NotificationType,
    val actorAlias: String?,
    val actorColorHex: Long?,
    val actorProfileHandle: String? = null,
    val preview: String,
    val relativeTime: String,
    val isRead: Boolean = false,
)

data class ChatPreview(
    val roomId: String,
    val peerHandle: String,
    val peerAlias: String,
    val peerColorHex: Long,
    val peerAvatarUrl: String? = null,
    val lastMessage: String,
    val relativeTime: String,
    val unreadCount: Int,
    val relationship: RelationshipStatus,
    val hasMessages: Boolean = true,
    val isHidden: Boolean = false,
)

data class ChatMessage(
    val messageId: String,
    val clientMessageId: String,
    val roomId: String,
    val isMine: Boolean,
    val content: String,
    val sentAtLabel: String,
    val deliveryState: DeliveryState
)

data class ProfileSettings(
    val accountAlias: String,
    val nearbyDisplayAlias: String,
    val colorHex: Long,
    val bio: String,
    val avatarSeed: String,
    val avatarUrl: String?,
    val genres: List<String>,
    val moods: List<String>,
    val melodyNotes: List<String>,
    val melodyAliasId: String,
    val melodyAliasTone: String,
    val melodyAliasMood: String,
    val melodyAliasTempo: Int,
    val musicVisibilityLabel: String,
    val discoverable: Boolean,
    val allowReactions: Boolean,
    val profileHandle: String = "",
    val stats: ProfileStats = ProfileStats(),
    val tasteFingerprint: TasteFingerprint = TasteFingerprint(),
    val profileRevision: Long = 0,
    val signatureTracks: List<ProfileTrack> = emptyList(),
    val favoriteArtists: List<ProfileArtist> = emptyList(),
    val privacy: ProfilePrivacySettings = ProfilePrivacySettings(),
)

data class ProfileTrack(
    val rank: Int,
    val provider: String = "MANUAL",
    val providerTrackId: String? = null,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val genreTags: List<String> = emptyList(),
    val moodTags: List<String> = emptyList(),
)

data class ProfileArtist(
    val rank: Int,
    val provider: String = "MANUAL",
    val providerArtistId: String? = null,
    val name: String,
    val imageUrl: String? = null,
    val genreTags: List<String> = emptyList(),
)

data class MusicSearchResult(
    val id: Long,
    val artistId: Long?,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val releaseDate: String?,
    val durationSeconds: Int,
    val artworkUrl: String?,
    val previewUrl: String?,
    val appleMusicUrl: String?,
    val artistImageUrl: String? = null,
)

data class PreviewPlaybackState(
    val title: String = "",
    val artist: String = "",
    val artworkUrl: String? = null,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val errorMessage: String? = null,
)

data class ProfilePrivacySettings(
    val currentMusicVisibility: String = "EVERYONE",
    val listeningInsightsEnabled: Boolean = false,
    val listeningInsightsVisibility: String = "PRIVATE",
)

data class ProfileNowPlaying(
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val isPlaying: Boolean,
    val durationMs: Long? = null,
    val positionMs: Long? = null,
    val positionObservedAt: String? = null,
    val observedAt: String,
    val expiresAt: String,
)

data class CommonTasteMetric(
    val label: String,
    val type: String,
    val score: Int,
    val evidenceCount: Int,
)

data class CommonTasteSummary(
    val score: Int,
    val metrics: List<CommonTasteMetric>,
    val algorithmVersion: String,
    val sampleSize: Int,
    val calculatedAt: String,
)

data class TasteMetric(
    val label: String,
    val count: Int,
    val ratio: Double,
)

data class TasteFingerprint(
    val genres: List<TasteMetric> = emptyList(),
    val moods: List<TasteMetric> = emptyList(),
)

data class ProfileStats(
    val followingCount: Int = 0,
    val followerCount: Int = 0,
)

data class ProfileMelodyAlias(
    val id: String,
    val notes: List<String>,
    val tone: String,
    val mood: String,
    val tempo: Int,
)

data class SharedFollowerPreview(
    val profileHandle: String,
    val displayName: String,
    val avatarUrl: String?,
)

data class PublicProfile(
    val profileHandle: String,
    val isSelf: Boolean = false,
    val displayName: String,
    val colorHex: Long,
    val bio: String,
    val avatarSeed: String,
    val avatarUrl: String?,
    val genres: List<String>,
    val moods: List<String>,
    val melodyAlias: ProfileMelodyAlias?,
    val stats: ProfileStats,
    val tasteFingerprint: TasteFingerprint,
    val relationship: RelationshipStatus,
    val following: Boolean,
    val mutual: Boolean,
    val sharedFollowers: List<SharedFollowerPreview> = emptyList(),
    val sharedFollowerCount: Int = 0,
    val signatureTracks: List<ProfileTrack> = emptyList(),
    val favoriteArtists: List<ProfileArtist> = emptyList(),
    val nowPlaying: ProfileNowPlaying? = null,
    val commonTaste: CommonTasteSummary? = null,
    val sectionStates: Map<String, String> = emptyMap(),
)

data class MelodyAliasCandidate(
    val id: String,
    val name: String,
    val mood: String,
    val tone: String,
    val tempo: Int,
    val energy: String,
    val notes: List<String>,
    val rhythm: List<Int>,
    val toneJsPreset: String,
    val melodyId: String
)

data class BlockedUser(
    val blockId: String,
    val displayAlias: String,
    val colorHex: Long,
    val blockedAt: String
)

data class SocialConnection(
    val relationshipId: String?,
    val profileHandle: String?,
    val displayAlias: String,
    val colorHex: Long,
    val avatarUrl: String?,
    val bio: String,
    val mutual: Boolean,
    val followedAt: String,
)

enum class ReportReason(val label: String) {
    SPAM("스팸·광고"),
    HARASSMENT("괴롭힘·위협"),
    HATE("혐오 표현"),
    SEXUAL_CONTENT("부적절한 성적 콘텐츠"),
    IMPERSONATION("사칭"),
    OTHER("기타")
}

data class MelodyUiState(
    val isOnboardingComplete: Boolean = false,
    val selectedTab: MainTab = MainTab.HOME,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val sharingState: SharingState = SharingState.STOPPED,
    val scopeLabel: String = "캠퍼스 주변",
    val snapshotSequence: Long = 1,
    val currentTrack: Track,
    val currentTrackPlaying: Boolean = true,
    val detectedTrack: Track? = null,
    val detectedTrackPlaying: Boolean = false,
    val nearbyListeners: List<NearbyListener>,
    val popularTracks: List<PopularTrack>,
    val notifications: List<InboxNotification>,
    val chats: List<ChatPreview>,
    val chatMessages: Map<String, List<ChatMessage>>,
    val profile: ProfileSettings,
    val melodyAliasCandidates: List<MelodyAliasCandidate> = emptyList(),
    val blockedUsers: List<BlockedUser> = emptyList(),
    val following: List<SocialConnection> = emptyList(),
    val followers: List<SocialConnection> = emptyList(),
    val socialConnectionsLoading: Boolean = false,
    val socialConnectionsLoaded: Boolean = false,
    val selectedPublicProfile: PublicProfile? = null,
    val publicProfileLoading: Boolean = false,
    val publicProfileError: String? = null,
    val discoveryRadiusMeters: Int = 15,
    val discoverabilityScope: String = "NEARBY",
    val musicVisibility: String = "TITLE_ARTIST",
    val nearbyLoadState: NearbyLoadState = NearbyLoadState.IDLE,
    val nearbyErrorMessage: String? = null,
    val nearbyMeasurementDiagnostics: NearbyMeasurementDiagnostics? = null,
    val selectedNearbyHandle: String? = null,
    val profileSaving: Boolean = false,
    val feedbackMessage: String? = null,
    val dataSourceLabel: String = "DEMO LIVE",
    val activeAccountId: String? = null,
) {
    val selectedNearby: NearbyListener?
        get() = nearbyListeners.firstOrNull { it.nearbyHandle == selectedNearbyHandle }

    val unreadNotificationCount: Int
        get() = notifications.count { !it.isRead }

    val unreadChatCount: Int
        get() = chats.filterNot(ChatPreview::isHidden).sumOf { it.unreadCount }
}
