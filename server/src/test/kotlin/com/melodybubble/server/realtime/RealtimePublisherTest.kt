package com.melodybubble.server.realtime

import com.melodybubble.server.auth.JwtSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.UUID
import java.time.Instant

class RealtimePublisherTest {
    private val channel = RecordingChannel()
    private val publisher = RealtimePublisher(SimpMessagingTemplate(channel))

    @AfterEach
    fun cleanTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
        TransactionSynchronizationManager.setActualTransactionActive(false)
    }

    @Test
    fun `defers a user event until the transaction commits`() {
        TransactionSynchronizationManager.setActualTransactionActive(true)
        TransactionSynchronizationManager.initSynchronization()
        val userId = UUID.randomUUID()

        publisher.toUserAfterCommit(userId, RealtimeQueues.CHAT, "TEST_EVENT", mapOf("ok" to true))

        assertThat(channel.messages).isEmpty()
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1)

        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

        assertThat(channel.messages).hasSize(1)
        val message = channel.messages.single()
        assertThat(message.headers[SimpMessageHeaderAccessor.DESTINATION_HEADER])
            .isEqualTo("/user/$userId/queue/chat")
        assertThat(message.payload).isInstanceOf(RealtimeEnvelope::class.java)
        val envelope = message.payload as RealtimeEnvelope<*>
        assertThat(envelope.type).isEqualTo("TEST_EVENT")
        assertThat(envelope.version).isEqualTo(1)
        assertThat(envelope.payload).isEqualTo(mapOf("ok" to true))
    }

    @Test
    fun `sends immediately when no transaction is active`() {
        publisher.toUserAfterCommit(
            UUID.randomUUID(),
            RealtimeQueues.REACTIONS,
            RealtimeEventTypes.NEARBY_REACTION_CREATED,
            mapOf("reactionType" to "LIKE"),
        )

        assertThat(channel.messages).hasSize(1)
    }

    @Test
    fun `does not leak an event from a rolled back transaction`() {
        TransactionSynchronizationManager.setActualTransactionActive(true)
        TransactionSynchronizationManager.initSynchronization()

        publisher.toUserAfterCommit(
            UUID.randomUUID(),
            RealtimeQueues.NEARBY,
            RealtimeEventTypes.NEARBY_MUSIC_UPDATED,
            mapOf("isPlaying" to false),
        )
        TransactionSynchronizationManager.getSynchronizations().forEach {
            it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
        }

        assertThat(channel.messages).isEmpty()
    }

    @Test
    fun `rejects expired and explicitly revoked realtime sessions`() {
        val policy = RealtimeSessionPolicy()
        val now = Instant.now()
        val active = JwtSession(
            userId = UUID.randomUUID(),
            tokenId = UUID.randomUUID().toString(),
            issuedAt = now.minusSeconds(5),
            expiresAt = now.plusSeconds(60),
        )
        assertThat(policy.isAllowed(active, now)).isTrue()
