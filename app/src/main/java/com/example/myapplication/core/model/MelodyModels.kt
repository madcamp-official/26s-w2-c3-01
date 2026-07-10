package com.example.myapplication.core.model

enum class MainTab(val label: String) {
    HOME("홈"),
    NEARBY("근처"),
    LOUNGE("라운지"),
    INBOX("인박스"),
    MY("마이")
}

enum class ConnectionState {
    OFFLINE,
    CONNECTING,
    LIVE,
    RECONNECTING
}

enum class SharingState {
    STOPPED,
    STARTING,
    ACTIVE,
    PERMISSION_REQUIRED
}

enum class Proximity(val label: String) {
    VERY_CLOSE("아주 가까움"),
    CLOSE("가까움"),
    AROUND("주변")
}

enum class RelationshipStatus {
    NONE,
    FOLLOWING,
    FOLLOWS_ME,
    MUTUAL,
    BLOCKED
}

enum class LoungeStatus {
    LIVE,
    SCHEDULED,
    CLOSED
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

enum class SyncState {
    PENDING,
    SYNCED,
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
    val platform: String = "MANUAL",
    val externalUrl: String? = null,
    val genreTags: List<String> = emptyList(),
    val moodTags: List<String> = emptyList()
)

data class NearbyListener(
    val nearbyHandle: String,
    val displayAlias: String,
    val colorHex: Long,
    val displayPosition: DisplayPosition,
    val matchScore: Int,
    val proximity: Proximity,
    val isPlaying: Boolean,
    val currentTrack: Track?,
    val commonGenres: List<String>,
    val relationship: RelationshipStatus = RelationshipStatus.NONE,
    val isNew: Boolean = false
)

data class PopularTrack(
    val track: Track,
    val listenerCount: Int,
    val reactionCount: Int
)

data class MusicCard(
    val id: String,
    val senderAlias: String,
    val track: Track,
    val reactionCount: Int,
    val hasReacted: Boolean = false
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

data class Lounge(
    val id: String,
    val name: String,
    val description: String,
    val status: LoungeStatus,
    val memberCount: Int,
    val vibeTags: List<String>,
    val cards: List<MusicCard> = emptyList(),
    val poll: LoungePoll? = null,
    val isJoined: Boolean = false
)

data class InboxNotification(
    val id: String,
    val type: NotificationType,
    val actorAlias: String?,
    val actorColorHex: Long?,
    val preview: String,
    val relativeTime: String,
    val isRead: Boolean = false
)

data class ChatPreview(
    val roomId: String,
    val peerHandle: String,
    val peerAlias: String,
    val peerColorHex: Long,
    val lastMessage: String,
    val relativeTime: String,
    val unreadCount: Int,
    val relationship: RelationshipStatus
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
    val offlineExchangeEnabled: Boolean
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

data class OfflineExchangeRecord(
    val id: String,
    val localSessionId: String,
    val peerDisplayAlias: String,
    val trackTitle: String,
    val trackArtist: String,
    val melodyAlias: String,
    val exchangedAt: Long,
    val syncState: SyncState
)

data class MelodyUiState(
    val isOnboardingComplete: Boolean = false,
    val selectedTab: MainTab = MainTab.HOME,
    val connectionState: ConnectionState = ConnectionState.OFFLINE,
    val sharingState: SharingState = SharingState.STOPPED,
    val scopeLabel: String = "캠퍼스 주변",
    val snapshotSequence: Long = 1,
    val currentTrack: Track,
    val nearbyListeners: List<NearbyListener>,
    val popularTracks: List<PopularTrack>,
    val lounges: List<Lounge>,
    val notifications: List<InboxNotification>,
    val chats: List<ChatPreview>,
    val chatMessages: Map<String, List<ChatMessage>>,
    val profile: ProfileSettings,
    val melodyAliasCandidates: List<MelodyAliasCandidate> = emptyList(),
    val offlineExchanges: List<OfflineExchangeRecord> = emptyList(),
    val selectedNearbyHandle: String? = null,
    val selectedLoungeId: String? = null,
    val feedbackMessage: String? = null,
    val dataSourceLabel: String = "DEMO LIVE"
) {
    val selectedNearby: NearbyListener?
        get() = nearbyListeners.firstOrNull { it.nearbyHandle == selectedNearbyHandle }

    val selectedLounge: Lounge?
        get() = lounges.firstOrNull { it.id == selectedLoungeId }

    val unreadNotificationCount: Int
        get() = notifications.count { !it.isRead } + chats.sumOf { it.unreadCount }
}
