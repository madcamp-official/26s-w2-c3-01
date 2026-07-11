package com.melodybubble.server.lounge

import com.melodybubble.server.realtime.RealtimeEventTypes
import com.melodybubble.server.realtime.RealtimePublisher
import com.melodybubble.server.safety.ActionRateLimiter
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.security.Principal
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class LoungeListeningStatus(
    val listenerAlias: String,
    val trackTitle: String?,
    val artistName: String?,
    val albumArtUrl: String?,
    val isPlaying: Boolean,
    val updatedAt: Instant,
)

data class LoungeRecommendationCard(
    val id: UUID,
    val subLoungeId: UUID,
    val clientCardId: UUID,
    val senderAlias: String,
    val trackTitle: String,
    val artistName: String,
    val message: String?,
    val reactionCount: Int,
    val reactedByMe: Boolean,
    val createdAt: Instant,
)

data class LoungePollOption(val key: String, val voteCount: Int)
data class LoungePollState(val options: List<LoungePollOption>, val myVote: String?)

data class SubLoungeSnapshot(
    val id: UUID,
    val buildingLoungeId: UUID,
    val title: String,
    val style: String?,
    val memberCount: Int,
    val joined: Boolean,
    val listeningStatuses: List<LoungeListeningStatus>,
    val cards: List<LoungeRecommendationCard>,
    val poll: LoungePollState,
    val generatedAt: Instant = Instant.now(),
)

data class UpdateLoungeListeningRequest(
    val trackTitle: String = "",
    val artistName: String = "",
    val albumArtUrl: String? = null,
    val isPlaying: Boolean = true,
)

data class CreateLoungeCardRequest(
    val clientCardId: String,
    val trackTitle: String,
    val artistName: String,
    val message: String? = null,
)

data class LoungeVoteRequest(val targetKey: String)
data class SubLoungeMemberPayload(val memberCount: Int, val memberAlias: String, val updatedAt: Instant = Instant.now())
data class SubLoungeStatePayload(val memberCount: Int, val updatedAt: Instant = Instant.now())

@Component
class SubLoungeTopicAuthorizer(private val jdbc: JdbcTemplate) {
    fun canSubscribe(userId: UUID, destination: String): Boolean {
        val match = TOPIC_PATTERN.matchEntire(destination) ?: return false
        val subLoungeId = runCatching { UUID.fromString(match.groupValues[1]) }.getOrNull() ?: return false
        return hasActiveMembership(jdbc, userId, subLoungeId)
    }

    companion object {
        private val TOPIC_PATTERN = Regex("/topic/sub-lounges/([0-9a-fA-F-]{36})")
    }
}
