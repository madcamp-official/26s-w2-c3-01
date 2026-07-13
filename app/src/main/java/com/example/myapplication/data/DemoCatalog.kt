package com.example.myapplication.data

import com.example.myapplication.core.model.ChatMessage
import com.example.myapplication.core.model.ChatPreview
import com.example.myapplication.core.model.DeliveryState
import com.example.myapplication.core.model.InboxNotification
import com.example.myapplication.core.model.MelodyUiState
import com.example.myapplication.core.model.NearbyListener
import com.example.myapplication.core.model.NotificationType
import com.example.myapplication.core.model.PopularTrack
import com.example.myapplication.core.model.ProfileSettings
import com.example.myapplication.core.model.Proximity
import com.example.myapplication.core.model.RelationshipStatus
import com.example.myapplication.core.model.Track
import com.example.myapplication.core.model.abstractDisplayPosition

object DemoCatalog {
    val blueNight = Track(
        id = "track-blue-night",
        title = "Blue Night",
        artist = "Wave to Earth",
        album = "0.1 flaws and all.",
        externalUrl = "https://www.youtube.com/results?search_query=Blue+Night+Wave+to+Earth",
        genreTags = listOf("Indie", "R&B"),
        moodTags = listOf("Calm", "Night")
    )

    val lateDrive = Track(
        id = "track-late-drive",
        title = "Late Drive",
        artist = "Seoul Moon",
        genreTags = listOf("City Pop"),
        moodTags = listOf("Night")
    )

    val summerEnd = Track(
        id = "track-summer-end",
        title = "Summer End",
        artist = "Loco",
        genreTags = listOf("R&B"),
        moodTags = listOf("Warm")
    )

    val indieRain = Track(
        id = "track-indie-rain",
        title = "Indie Rain",
        artist = "Aurora",
        genreTags = listOf("Indie"),
        moodTags = listOf("Rainy", "Calm")
    )

    fun initialState(isOnboardingComplete: Boolean): MelodyUiState {
        val nearby = listOf(
            NearbyListener(
                nearbyHandle = "nearby-mint-session",
                displayAlias = "Mint",
                colorHex = 0xFF25C76FL,
                displayPosition = abstractDisplayPosition("nearby-mint-session", Proximity.WITHIN_5M),
                matchScore = 93,
                proximity = Proximity.WITHIN_5M,
                isPlaying = true,
                currentTrack = blueNight,
                commonGenres = listOf("Indie", "R&B"),
                relationship = RelationshipStatus.FOLLOWS_ME,
                isNew = true
            ),
            NearbyListener(
                nearbyHandle = "nearby-wave-session",
                displayAlias = "Wave",
                colorHex = 0xFF53D889L,
                displayPosition = abstractDisplayPosition("nearby-wave-session", Proximity.WITHIN_10M),
                matchScore = 81,
                proximity = Proximity.WITHIN_10M,
                isPlaying = true,
                currentTrack = summerEnd,
                commonGenres = listOf("R&B", "Pop")
            ),
            NearbyListener(
                nearbyHandle = "nearby-nova-session",
                displayAlias = "Nova",
                colorHex = 0xFF299F61L,
                displayPosition = abstractDisplayPosition("nearby-nova-session", Proximity.WITHIN_15M),
                matchScore = 70,
                proximity = Proximity.WITHIN_15M,
                isPlaying = false,
                currentTrack = indieRain,
                commonGenres = listOf("Indie")
            ),
            NearbyListener(
                nearbyHandle = "nearby-echo-session",
                displayAlias = "Echo",
                colorHex = 0xFF79A85BL,
                displayPosition = abstractDisplayPosition("nearby-echo-session", Proximity.WITHIN_10M),
                matchScore = 76,
                proximity = Proximity.WITHIN_10M,
                isPlaying = true,
                currentTrack = lateDrive,
                commonGenres = listOf("City Pop", "R&B")
            )
        )

        val notifications = listOf(
            InboxNotification(
                id = "notification-reaction",
                type = NotificationType.REACTION,
                actorAlias = "Mint",
                actorColorHex = 0xFF25C76FL,
                preview = "내 음악 카드에 공감했어요",
                relativeTime = "3분 전"
            ),
            InboxNotification(
                id = "notification-follow",
                type = NotificationType.FOLLOW,
                actorAlias = "Wave",
                actorColorHex = 0xFF53D889L,
                preview = "나를 팔로우했어요",
                relativeTime = "12분 전"
            ),
            InboxNotification(
                id = "notification-system",
                type = NotificationType.SYSTEM,
                actorAlias = null,
                actorColorHex = null,
                preview = "공유 범위는 캠퍼스 주변으로 고정돼요",
                relativeTime = "오늘",
                isRead = true
            )
        )

        val chats = listOf(
            ChatPreview(
                roomId = "chat-mint",
                peerHandle = "nearby-mint-session",
                peerAlias = "Mint",
                peerColorHex = 0xFF25C76FL,
                lastMessage = "이 노래 저도 좋아해요",
                relativeTime = "방금",
                unreadCount = 1,
                relationship = RelationshipStatus.MUTUAL
            )
        )

        val messages = mapOf(
            "chat-mint" to listOf(
                ChatMessage(
                    messageId = "message-1",
                    clientMessageId = "remote-1",
                    roomId = "chat-mint",
                    isMine = false,
                    content = "오 진짜요?",
                    sentAtLabel = "오후 3:28",
                    deliveryState = DeliveryState.READ
                ),
                ChatMessage(
                    messageId = "message-2",
                    clientMessageId = "local-1",
                    roomId = "chat-mint",
                    isMine = true,
                    content = "비슷한 플레이리스트 추천해줄까요?",
                    sentAtLabel = "오후 3:29",
                    deliveryState = DeliveryState.READ
                ),
                ChatMessage(
                    messageId = "message-3",
                    clientMessageId = "remote-2",
                    roomId = "chat-mint",
                    isMine = false,
                    content = "이 노래 저도 좋아해요",
                    sentAtLabel = "오후 3:30",
                    deliveryState = DeliveryState.READ
                )
            )
        )

        return MelodyUiState(
            isOnboardingComplete = isOnboardingComplete,
            currentTrack = blueNight,
            nearbyListeners = nearby,
            popularTracks = listOf(
                PopularTrack(blueNight, listenerCount = 12, reactionCount = 7),
                PopularTrack(lateDrive, listenerCount = 9, reactionCount = 5),
                PopularTrack(indieRain, listenerCount = 7, reactionCount = 4)
            ),
            notifications = notifications,
            chats = chats,
            chatMessages = messages,
            profile = ProfileSettings(
                accountAlias = "JH Melody",
                nearbyDisplayAlias = "Lime",
                colorHex = 0xFF25C76FL,
                bio = "좋은 음악을 발견하고 나누는 걸 좋아해요.",
                avatarDataUrl = null,
                genres = listOf("Indie", "R&B"),
                moods = listOf("Calm", "Night"),
                melodyNotes = listOf("C4", "E4", "G4"),
                melodyAliasId = "mint-ring",
                melodyAliasTone = "전자음",
                melodyAliasMood = "밝음",
                melodyAliasTempo = 132,
                musicVisibilityLabel = "제목·아티스트 공개",
                discoverable = true,
                allowReactions = true,
                offlineExchangeEnabled = true
            )
        )
    }
}
