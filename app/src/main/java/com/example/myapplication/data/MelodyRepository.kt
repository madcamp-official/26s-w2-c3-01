package com.example.myapplication.data

import android.content.Context
import com.example.myapplication.core.model.ChatMessage
import com.example.myapplication.core.model.ChatPreview
import com.example.myapplication.core.model.ConnectionState
import com.example.myapplication.core.model.DeliveryState
import com.example.myapplication.core.model.InboxNotification
import com.example.myapplication.core.model.MainTab
import com.example.myapplication.core.model.MelodyReducers
import com.example.myapplication.core.model.MelodyUiState
import com.example.myapplication.core.model.MelodyAliasCandidate
import com.example.myapplication.core.model.NotificationType
import com.example.myapplication.core.model.OfflineExchangeRecord
import com.example.myapplication.core.model.RelationshipStatus
import com.example.myapplication.core.model.SharingState
import com.example.myapplication.core.model.SyncState
import com.example.myapplication.core.model.Track
import com.example.myapplication.data.local.MelodyDatabase
import com.example.myapplication.data.local.MelodyAliasCandidateEntity
import com.example.myapplication.data.local.OfflineExchangeEntity
import com.example.myapplication.data.local.SyncOutboxEntity
import com.example.myapplication.data.remote.ApiEnvironment
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface MelodyRepository {
    val state: StateFlow<MelodyUiState>

    fun completeOnboarding()
    fun selectTab(tab: MainTab)
    fun selectNearby(handle: String?)
    fun selectLounge(roomId: String?)
    fun startSharing()
    fun stopSharing()
    fun setSharingPermissionRequired()
    fun follow(handle: String)
    fun react(handle: String, reactionLabel: String)
    fun block(handle: String)
    fun report(handle: String)
    fun registerMutualChat(roomId: String, peerHandle: String)
    fun replaceChats(chats: List<ChatPreview>)
    fun replaceChatMessages(roomId: String, messages: List<ChatMessage>)
    fun joinLounge(roomId: String)
    fun vote(roomId: String, optionId: String)
    fun sendMusicCard(roomId: String)
    fun reactToMusicCard(roomId: String, cardId: String)
    fun sendChat(roomId: String, content: String)
    fun selectTrack(track: Track)
    fun markInboxRead()
    fun setDiscoverable(enabled: Boolean)
    fun setAllowReactions(enabled: Boolean)
    fun setOfflineExchangeEnabled(enabled: Boolean)
    fun selectMelodyAlias(candidateId: String)
    fun selectGeneratedMelodyAlias(candidate: MelodyAliasCandidate)
    fun createDemoExchange(peerAlias: String)
    fun syncExchange(exchangeId: String)
    fun clearFeedback()
    fun close()
}

class DemoMelodyRepository(
    context: Context,
    private val database: MelodyDatabase = MelodyDatabase.getInstance(context),
    environment: ApiEnvironment = ApiEnvironment()
) : MelodyRepository {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(
        "melody-bubble-session",
        Context.MODE_PRIVATE
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(
        DemoCatalog.initialState(
            isOnboardingComplete = preferences.getBoolean("onboarding-complete", false)
        ).copy(
            dataSourceLabel = if (environment.isConfigured) {
                "DEMO FALLBACK"
            } else {
                "DEMO LIVE"
            }
        )
    )
    override val state: StateFlow<MelodyUiState> = _state.asStateFlow()

    private var sharingJob: Job? = null
    private var liveTick = 0

    init {
        scope.launch {
            database.offlineExchangeDao().deleteExpired(System.currentTimeMillis())
            database.offlineExchangeDao().observeAll().collect { entities ->
                _state.update { current ->
                    current.copy(offlineExchanges = entities.map { it.toDomain() })
                }
            }
        }

        scope.launch {
            database.melodyAliasCandidateDao().observeAll().collect { entities ->
                _state.update { current ->
                    current.copy(melodyAliasCandidates = entities.map { it.toDomain() })
                }
            }
        }

        scope.launch {
            while (isActive) {
                delay(3_500)
                if (_state.value.sharingState == SharingState.ACTIVE) {
                    applyLiveTick()
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

    override fun selectLounge(roomId: String?) {
        _state.update { it.copy(selectedLoungeId = roomId) }
    }

    override fun startSharing() {
        if (_state.value.sharingState == SharingState.ACTIVE || sharingJob?.isActive == true) return
        sharingJob = scope.launch {
            _state.update {
                it.copy(
                    sharingState = SharingState.STARTING,
                    connectionState = ConnectionState.CONNECTING,
                    feedbackMessage = "안전한 주변 공유를 준비하고 있어요"
                )
            }
            delay(700)
            _state.update {
                it.copy(
                    sharingState = SharingState.ACTIVE,
                    connectionState = ConnectionState.LIVE,
                    feedbackMessage = "캠퍼스 주변에 음악을 공유 중이에요"
                )
            }
        }
    }

    override fun stopSharing() {
        sharingJob?.cancel()
        sharingJob = null
        _state.update {
            it.copy(
                sharingState = SharingState.STOPPED,
                connectionState = ConnectionState.OFFLINE,
                feedbackMessage = "주변 공유를 중지했어요"
            )
        }
    }

    override fun setSharingPermissionRequired() {
        _state.update {
            it.copy(
                sharingState = SharingState.PERMISSION_REQUIRED,
                connectionState = ConnectionState.OFFLINE,
                feedbackMessage = "주변 공유를 시작하려면 위치 권한이 필요해요"
            )
        }
    }

    override fun follow(handle: String) {
        var becameMutual = false
        _state.update { current ->
            val updatedListeners = current.nearbyListeners.map { listener ->
                if (listener.nearbyHandle != handle) return@map listener
                val nextRelationship = when (listener.relationship) {
                    RelationshipStatus.NONE -> RelationshipStatus.FOLLOWING
                    RelationshipStatus.FOLLOWS_ME -> {
                        becameMutual = true
                        RelationshipStatus.MUTUAL
                    }
                    RelationshipStatus.FOLLOWING -> RelationshipStatus.NONE
                    RelationshipStatus.MUTUAL -> RelationshipStatus.MUTUAL
                    RelationshipStatus.BLOCKED -> RelationshipStatus.BLOCKED
                }
                listener.copy(relationship = nextRelationship)
            }
            val peer = updatedListeners.firstOrNull { it.nearbyHandle == handle }
            val newNotification = if (becameMutual && peer != null) {
                InboxNotification(
                    id = "mutual-${UUID.randomUUID()}",
                    type = NotificationType.MUTUAL_FOLLOW,
                    actorAlias = peer.displayAlias,
                    actorColorHex = peer.colorHex,
                    preview = "서로 팔로우했어요. 이제 대화할 수 있어요",
                    relativeTime = "방금"
                )
            } else {
                null
            }
            val hasChat = current.chats.any { it.peerHandle == handle }
            val newChat = if (becameMutual && peer != null && !hasChat) {
                ChatPreview(
                    roomId = "chat-${handle.substringAfter("nearby-").substringBefore("-")}",
                    peerHandle = handle,
                    peerAlias = peer.displayAlias,
                    peerColorHex = peer.colorHex,
                    lastMessage = "서로 팔로우했어요",
                    relativeTime = "방금",
                    unreadCount = 0,
                    relationship = RelationshipStatus.MUTUAL
                )
            } else {
                null
            }
            current.copy(
                nearbyListeners = updatedListeners,
                notifications = listOfNotNull(newNotification) + current.notifications,
                chats = listOfNotNull(newChat) + current.chats,
                feedbackMessage = when {
                    becameMutual -> "맞팔이 성립됐어요. 인박스에서 대화해 보세요"
                    peer?.relationship == RelationshipStatus.FOLLOWING -> "팔로우했어요"
                    peer?.relationship == RelationshipStatus.NONE -> "팔로우를 취소했어요"
                    else -> current.feedbackMessage
                }
            )
        }
    }

    override fun react(handle: String, reactionLabel: String) {
        val alias = _state.value.nearbyListeners
            .firstOrNull { it.nearbyHandle == handle }
            ?.displayAlias
            ?: return
        _state.update {
            it.copy(feedbackMessage = "$alias 님에게 ‘$reactionLabel’ 리액션을 보냈어요")
        }
    }

    override fun block(handle: String) {
        val alias = _state.value.nearbyListeners.firstOrNull {
            it.nearbyHandle == handle
        }?.displayAlias ?: return
        _state.update { current ->
            current.copy(
                nearbyListeners = current.nearbyListeners.filterNot { it.nearbyHandle == handle },
                selectedNearbyHandle = null,
                feedbackMessage = "$alias 님을 차단했어요. 서로의 주변 목록에 표시되지 않아요"
            )
        }
    }

    override fun report(handle: String) {
        val alias = _state.value.nearbyListeners.firstOrNull {
            it.nearbyHandle == handle
        }?.displayAlias ?: return
        _state.update { it.copy(feedbackMessage = "$alias 님의 신고 초안을 접수했어요") }
    }

    override fun registerMutualChat(roomId: String, peerHandle: String) {
        _state.update { current ->
            val peer = current.nearbyListeners.firstOrNull { it.nearbyHandle == peerHandle }
            val existing = current.chats.any { it.roomId == roomId || it.peerHandle == peerHandle }
            if (peer == null || existing) return@update current
            current.copy(
                chats = listOf(
                    ChatPreview(
                        roomId = roomId,
                        peerHandle = peerHandle,
                        peerAlias = peer.displayAlias,
                        peerColorHex = peer.colorHex,
                        lastMessage = "서로 팔로우했어요",
                        relativeTime = "방금",
                        unreadCount = 0,
                        relationship = RelationshipStatus.MUTUAL
                    )
                ) + current.chats
            )
        }
    }

    override fun replaceChats(chats: List<ChatPreview>) {
        _state.update { current ->
            current.copy(
                chats = chats,
                chatMessages = current.chatMessages.filterKeys { roomId ->
                    chats.any { it.roomId == roomId }
                }
            )
        }
    }

    override fun replaceChatMessages(roomId: String, messages: List<ChatMessage>) {
        _state.update { current ->
            current.copy(chatMessages = current.chatMessages + (roomId to messages))
        }
    }

    override fun joinLounge(roomId: String) {
        _state.update { current ->
            val lounges = current.lounges.map { lounge ->
                if (lounge.id != roomId) return@map lounge
                val joined = !lounge.isJoined
                lounge.copy(
                    isJoined = joined,
                    memberCount = (lounge.memberCount + if (joined) 1 else -1).coerceAtLeast(0)
                )
            }
            val selected = lounges.firstOrNull { it.id == roomId }
            current.copy(
                lounges = lounges,
                feedbackMessage = if (selected?.isJoined == true) {
                    "${selected.name}에 입장했어요"
                } else {
                    "라운지에서 나왔어요"
                }
            )
        }
    }

    override fun vote(roomId: String, optionId: String) {
        _state.update { current ->
            val lounges = current.lounges.map { lounge ->
                if (lounge.id != roomId || lounge.poll == null || !lounge.poll.isOpen) {
                    return@map lounge
                }
                lounge.copy(poll = MelodyReducers.applyVote(lounge.poll, optionId))
            }
            current.copy(
                lounges = lounges,
                feedbackMessage = "투표가 실시간으로 반영됐어요"
            )
        }
    }

    override fun sendMusicCard(roomId: String) {
        _state.update { current ->
            val currentTrack = current.currentTrack
            val lounges = current.lounges.map { lounge ->
                if (lounge.id != roomId || !lounge.isJoined) return@map lounge
                val alreadySent = lounge.cards.any {
                    it.senderAlias == current.profile.nearbyDisplayAlias &&
                        it.track.id == currentTrack.id
                }
                if (alreadySent) return@map lounge
                lounge.copy(
                    cards = listOf(
                        com.example.myapplication.core.model.MusicCard(
                            id = "local-card-${UUID.randomUUID()}",
                            senderAlias = current.profile.nearbyDisplayAlias,
                            track = currentTrack,
                            reactionCount = 0
                        )
                    ) + lounge.cards
                )
            }
            current.copy(
                lounges = lounges,
                feedbackMessage = "${currentTrack.title} 음악 카드를 라운지에 보냈어요"
            )
        }
    }

    override fun reactToMusicCard(roomId: String, cardId: String) {
        _state.update { current ->
            var reactedTrackTitle: String? = null
            val lounges = current.lounges.map { lounge ->
                if (lounge.id != roomId) return@map lounge
                lounge.copy(
                    cards = lounge.cards.map { card ->
                        if (card.id != cardId) return@map card
                        reactedTrackTitle = card.track.title
                        val nextReacted = !card.hasReacted
                        card.copy(
                            hasReacted = nextReacted,
                            reactionCount = (card.reactionCount + if (nextReacted) 1 else -1)
                                .coerceAtLeast(0)
                        )
                    }
                )
            }
            current.copy(
                lounges = lounges,
                feedbackMessage = reactedTrackTitle?.let { "$it 카드 공감이 반영됐어요" }
                    ?: current.feedbackMessage
            )
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
            deliveryState = DeliveryState.PENDING
        )
        _state.update { current ->
            current.copy(
                chatMessages = current.chatMessages + (
                    roomId to (current.chatMessages[roomId].orEmpty() + pending)
                ),
                chats = current.chats.map {
                    if (it.roomId == roomId) {
                        it.copy(lastMessage = pending.content, relativeTime = "방금", unreadCount = 0)
                    } else {
                        it
                    }
                }
            )
        }
        scope.launch {
            delay(450)
            _state.update { current ->
                current.copy(
                    chatMessages = current.chatMessages + (
                        roomId to current.chatMessages[roomId].orEmpty().map { message ->
                            if (message.clientMessageId == clientId) {
                                message.copy(
                                    messageId = "server-${UUID.randomUUID()}",
                                    deliveryState = DeliveryState.SENT
                                )
                            } else {
                                message
                            }
                        }
                    )
                )
            }
        }
    }

    override fun selectTrack(track: Track) {
        _state.update {
            it.copy(
                currentTrack = track,
                feedbackMessage = "${track.title}을(를) 현재 음악으로 선택했어요"
            )
        }
    }

    override fun markInboxRead() {
        _state.update { current ->
            current.copy(
                notifications = current.notifications.map { it.copy(isRead = true) },
                chats = current.chats.map { it.copy(unreadCount = 0) }
            )
        }
    }

    override fun setDiscoverable(enabled: Boolean) {
        _state.update { it.copy(profile = it.profile.copy(discoverable = enabled)) }
    }

    override fun setAllowReactions(enabled: Boolean) {
        _state.update { it.copy(profile = it.profile.copy(allowReactions = enabled)) }
    }

    override fun setOfflineExchangeEnabled(enabled: Boolean) {
        _state.update { it.copy(profile = it.profile.copy(offlineExchangeEnabled = enabled)) }
    }

    override fun selectMelodyAlias(candidateId: String) {
        val candidate = _state.value.melodyAliasCandidates.firstOrNull { it.id == candidateId }
            ?: return
        _state.update { current ->
            current.copy(
                profile = current.profile.copy(
                    melodyNotes = candidate.notes,
                    melodyAliasId = candidate.id,
                    melodyAliasTone = candidate.tone,
                    melodyAliasMood = candidate.mood,
                    melodyAliasTempo = candidate.tempo
                ),
                feedbackMessage = "${candidate.name}을(를) 멜로디 별칭으로 설정했어요"
            )
        }
    }

    override fun selectGeneratedMelodyAlias(candidate: MelodyAliasCandidate) {
        _state.update { current ->
            current.copy(
                profile = current.profile.copy(
                    melodyNotes = candidate.notes,
                    melodyAliasId = candidate.id,
                    melodyAliasTone = candidate.tone,
                    melodyAliasMood = candidate.mood,
                    melodyAliasTempo = candidate.tempo
                ),
                feedbackMessage = "${candidate.name}을(를) 멜로디 별칭으로 설정했어요"
            )
        }
    }

    override fun createDemoExchange(peerAlias: String) {
        if (!_state.value.profile.offlineExchangeEnabled) {
            _state.update { it.copy(feedbackMessage = "마이 화면에서 오프라인 교환을 켜 주세요") }
            return
        }
        scope.launch(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val sessionId = UUID.randomUUID().toString()
            val requestId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            database.offlineExchangeDao().insert(
                OfflineExchangeEntity(
                    id = id,
                    localSessionId = sessionId,
                    peerDisplayAlias = peerAlias,
                    trackTitle = _state.value.currentTrack.title,
                    trackArtist = _state.value.currentTrack.artist,
                    melodyAlias = _state.value.profile.melodyNotes.joinToString(" · "),
                    exchangedAt = now,
                    syncState = SyncState.PENDING.name,
                    expiresAt = now + 7 * 24 * 60 * 60 * 1_000L
                )
            )
            database.syncOutboxDao().insert(
                SyncOutboxEntity(
                    id = "outbox-$id",
                    kind = "OFFLINE_EXCHANGE",
                    requestId = requestId,
                    payloadJson = "{\"exchangeId\":\"$id\"}",
                    createdAt = now
                )
            )
            _state.update { it.copy(feedbackMessage = "$peerAlias 님의 음악 카드를 저장했어요") }
        }
    }

    override fun syncExchange(exchangeId: String) {
        scope.launch(Dispatchers.IO) {
            val record = _state.value.offlineExchanges.firstOrNull { it.id == exchangeId } ?: return@launch
            delay(500)
            database.offlineExchangeDao().update(
                OfflineExchangeEntity(
                    id = record.id,
                    localSessionId = record.localSessionId,
                    peerDisplayAlias = record.peerDisplayAlias,
                    trackTitle = record.trackTitle,
                    trackArtist = record.trackArtist,
                    melodyAlias = record.melodyAlias,
                    exchangedAt = record.exchangedAt,
                    syncState = SyncState.SYNCED.name,
                    expiresAt = record.exchangedAt + 7 * 24 * 60 * 60 * 1_000L
                )
            )
            database.syncOutboxDao().delete("outbox-${record.id}")
            _state.update { it.copy(feedbackMessage = "교환 기록 동기화를 확인했어요") }
        }
    }

    override fun clearFeedback() {
        _state.update { it.copy(feedbackMessage = null) }
    }

    override fun close() {
        scope.coroutineContext[Job]?.cancel()
    }

    private fun applyLiveTick() {
        liveTick += 1
        _state.update { current ->
            val movedListeners = current.nearbyListeners.mapIndexed { index, listener ->
                val delta = if ((liveTick + index) % 2 == 0) 0.008f else -0.008f
                listener.copy(
                    displayPosition = listener.displayPosition.copy(
                        x = (listener.displayPosition.x + delta).coerceIn(0.14f, 0.86f)
                    ),
                    isNew = false
                )
            }
            val lounges = current.lounges.map { lounge ->
                if (lounge.id == "campus-lounge") {
                    lounge.copy(memberCount = lounge.memberCount + if (liveTick % 2 == 0) 1 else -1)
                } else {
                    lounge
                }
            }
            current.copy(
                snapshotSequence = current.snapshotSequence + 1,
                nearbyListeners = movedListeners,
                lounges = lounges
            )
        }
    }

    private fun OfflineExchangeEntity.toDomain() = OfflineExchangeRecord(
        id = id,
        localSessionId = localSessionId,
        peerDisplayAlias = peerDisplayAlias,
        trackTitle = trackTitle,
        trackArtist = trackArtist,
        melodyAlias = melodyAlias,
        exchangedAt = exchangedAt,
        syncState = runCatching { SyncState.valueOf(syncState) }.getOrDefault(SyncState.PENDING)
    )

    private fun MelodyAliasCandidateEntity.toDomain() = MelodyAliasCandidate(
        id = id,
        name = name,
        mood = mood,
        tone = tone,
        tempo = tempo,
        energy = energy,
        notes = notesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        rhythm = rhythmCsv.split(",").mapNotNull { it.trim().toIntOrNull() },
        toneJsPreset = toneJsPreset,
        melodyId = melodyId
    )
}
