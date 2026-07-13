package com.melodybubble.server.offlineexchange

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.NamedParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.ByteBuffer
import java.security.Principal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.sql.Timestamp
import java.util.Base64
import java.util.UUID

data class OfflineCredentialRequest(val devicePublicKey: String)

data class OfflineCredentialResponse(
    val credentialId: String,
    val publicSubject: String,
    val devicePublicKey: String,
    val displayAlias: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val serverSignature: String,
    val serverPublicKey: String,
)

data class OfflineExchangeUpload(
    val exchangeId: String,
    val credentialId: String,
    val peerCredentialId: String,
    val sentCardJson: String,
    val receivedCardJson: String,
    val deviceOccurredAt: Long,
    val payloadHash: String,
    val protocolVersion: Int,
    val recordSignature: String,
)

data class OfflineExchangeBatchRequest(val items: List<OfflineExchangeUpload>)
data class OfflineExchangeSyncResult(val exchangeId: String, val state: String)
data class OfflineExchangeError(val message: String)

data class RemoteOfflineExchange(
    val exchangeId: String,
    val peerDisplayAlias: String,
    val sentCardJson: String,
    val receivedCardJson: String,
    val deviceOccurredAt: Long,
    val payloadHash: String,
    val protocolVersion: Int,
    val verificationState: String,
)

private data class ExchangeCard(
    val displayAlias: String = "",
    val trackTitle: String = "",
    val trackArtist: String = "",
    val melodyAlias: String = "",
    val genreTags: List<String> = emptyList(),
    val moodTags: List<String> = emptyList(),
)

private data class StoredCredential(
    val id: UUID,
    val userId: UUID,
    val devicePublicKey: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant?,
)

private data class StoredExchangeFingerprint(
    val credentialId: UUID,
    val peerCredentialId: UUID,
    val deviceOccurredAt: Instant,
    val payloadHash: String,
    val protocolVersion: Int,
)

@Service
class OfflineCredentialSigner(@Value("\${app.jwt.secret}") secret: String) {
    private val subjectSecret = secret.toByteArray(StandardCharsets.UTF_8)
    private val keyPair: KeyPair = KeyPairGenerator.getInstance("Ed25519").run {
        val random = SecureRandom.getInstance("SHA1PRNG").apply {
            setSeed(MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(StandardCharsets.UTF_8)))
        }
        initialize(NamedParameterSpec.ED25519, random)
        generateKeyPair()
    }

    val publicKey: String = Base64.getEncoder().encodeToString(keyPair.public.encoded)

    fun sign(value: String): String = Signature.getInstance("Ed25519").run {
        initSign(keyPair.private)
        update(value.toByteArray(StandardCharsets.UTF_8))
        Base64.getEncoder().encodeToString(sign())
    }

    fun publicSubject(userId: UUID): UUID {
        val digest = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(subjectSecret, "HmacSHA256"))
            doFinal(userId.toString().toByteArray(StandardCharsets.UTF_8))
        }.copyOfRange(0, 16)
        digest[6] = ((digest[6].toInt() and 0x0f) or 0x50).toByte()
        digest[8] = ((digest[8].toInt() and 0x3f) or 0x80).toByte()
        val buffer = ByteBuffer.wrap(digest)
        return UUID(buffer.long, buffer.long)
    }
}

@Service
class OfflineExchangeService(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    private val signer: OfflineCredentialSigner,
) {
    fun issueCredential(userId: UUID, request: OfflineCredentialRequest): OfflineCredentialResponse {
        val decodedKey = runCatching { Base64.getDecoder().decode(request.devicePublicKey) }
            .getOrElse { throw IllegalArgumentException("Invalid device public key") }
        require(decodedKey.size in 64..512) { "Invalid device public key" }
        runCatching {
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(decodedKey))
        }.getOrElse { throw IllegalArgumentException("Unsupported device public key") }

        val alias = jdbc.queryForObject("select display_name from users where id=?", String::class.java, userId)
            ?: throw IllegalArgumentException("Unknown user")
        val id = UUID.randomUUID()
        val subject = signer.publicSubject(userId)
        val issuedAt = Instant.now()
        val expiresAt = issuedAt.plus(30, ChronoUnit.DAYS)
        val unsigned = canonical(listOf(
            id.toString(), subject.toString(), request.devicePublicKey, alias,
            issuedAt.toEpochMilli().toString(), expiresAt.toEpochMilli().toString(),
        ))
        val signature = signer.sign(unsigned)
        jdbc.update(
            """insert into offline_credentials
                (id,user_id,public_subject,device_public_key,display_alias,issued_at,expires_at,server_signature)
                values (?,?,?,?,?,?,?,?)""",
            id, userId, subject, request.devicePublicKey, alias,
            Timestamp.from(issuedAt), Timestamp.from(expiresAt), signature,
        )
        return OfflineCredentialResponse(
            id.toString(), subject.toString(), request.devicePublicKey, alias,
            issuedAt.toEpochMilli(), expiresAt.toEpochMilli(), signature, signer.publicKey,
        )
    }

    @Transactional
    fun upload(userId: UUID, request: OfflineExchangeBatchRequest): List<OfflineExchangeSyncResult> {
        require(request.items.size <= 50) { "At most 50 exchanges can be uploaded at once" }
        return request.items.map { item ->
            val exchangeId = UUID.fromString(item.exchangeId)
            val credentialId = UUID.fromString(item.credentialId)
            val peerCredentialId = UUID.fromString(item.peerCredentialId)
            val credential = loadCredential(credentialId)
            require(credential.userId == userId) { "Credential does not belong to this account" }
            val occurredAt = Instant.ofEpochMilli(item.deviceOccurredAt)
            require(!occurredAt.isBefore(credential.issuedAt.minusSeconds(300))) { "Exchange predates credential" }
            require(!occurredAt.isAfter(credential.expiresAt.plusSeconds(300))) { "Credential was expired" }
            require(credential.revokedAt == null || occurredAt.isBefore(credential.revokedAt)) {
                "Credential was revoked before the exchange"
            }
            val peerCredential = loadCredential(peerCredentialId)
            require(peerCredential.userId != userId) { "An account cannot exchange with itself" }
            require(!occurredAt.isBefore(peerCredential.issuedAt.minusSeconds(300))) { "Peer credential was not issued" }
            require(!occurredAt.isAfter(peerCredential.expiresAt.plusSeconds(300))) { "Peer credential was expired" }
            require(peerCredential.revokedAt == null || occurredAt.isBefore(peerCredential.revokedAt)) {
                "Peer credential was revoked before the exchange"
            }

            val sentCard = parseCard(item.sentCardJson)
            val receivedCard = parseCard(item.receivedCardJson)
            val expectedHash = sha256(payloadCanonical(
                credentialId.toString(), sentCard, peerCredentialId.toString(), receivedCard,
            ))
            require(expectedHash == item.payloadHash) { "Exchange payload hash does not match" }
            require(item.protocolVersion == 1) { "Unsupported exchange protocol" }
            require(verifyDeviceSignature(
                credential.devicePublicKey,
                recordCanonical(item),
                item.recordSignature,
            )) { "Invalid exchange signature" }

            val inserted = jdbc.update(
                """insert into offline_exchange_events
                    (id,exchange_id,participant_user_id,credential_id,peer_credential_id,sent_card_json,
                     received_card_json,device_occurred_at,payload_hash,protocol_version,record_signature)
                    values (?,?,?,?,?,cast(? as jsonb),cast(? as jsonb),?,?,?,?)
                    on conflict(exchange_id,participant_user_id) do nothing""",
                UUID.randomUUID(), exchangeId, userId, credentialId, peerCredentialId,
                item.sentCardJson, item.receivedCardJson, Timestamp.from(occurredAt), item.payloadHash,
                item.protocolVersion, item.recordSignature,
            )
            if (inserted == 0) {
                val existing = loadExchangeFingerprint(exchangeId, userId)
                require(
                    existing.credentialId == credentialId &&
                        existing.peerCredentialId == peerCredentialId &&
                        existing.deviceOccurredAt == occurredAt &&
                        existing.payloadHash == item.payloadHash &&
                        existing.protocolVersion == item.protocolVersion
                ) { "Exchange id was already used with a different payload" }
            }
            verifyPair(exchangeId)
            OfflineExchangeSyncResult(item.exchangeId, verificationState(exchangeId, userId))
        }
    }

    fun history(userId: UUID): List<RemoteOfflineExchange> = jdbc.query(
        """select e.exchange_id,c.display_alias,e.sent_card_json::text,e.received_card_json::text,
                  e.device_occurred_at,e.payload_hash,e.protocol_version,e.verification_state
           from offline_exchange_events e
           join offline_credentials c on c.id=e.peer_credential_id
           where e.participant_user_id=? order by e.device_occurred_at desc""",
        { rs, _ -> RemoteOfflineExchange(
            rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
            rs.getTimestamp(5).toInstant().toEpochMilli(), rs.getString(6), rs.getInt(7), rs.getString(8),
        ) },
        userId,
    )

    fun delete(userId: UUID, exchangeId: UUID) {
        jdbc.update(
            "delete from offline_exchange_events where participant_user_id=? and exchange_id=?",
            userId, exchangeId,
        )
    }

    private fun loadCredential(id: UUID): StoredCredential = jdbc.query(
        """select id,user_id,device_public_key,issued_at,expires_at,revoked_at
           from offline_credentials where id=?""",
        { rs, _ -> StoredCredential(
            UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), rs.getString(3),
            rs.getTimestamp(4).toInstant(), rs.getTimestamp(5).toInstant(), rs.getTimestamp(6)?.toInstant(),
        ) }, id,
    ).firstOrNull() ?: throw IllegalArgumentException("Unknown offline credential")

    private fun loadExchangeFingerprint(exchangeId: UUID, userId: UUID): StoredExchangeFingerprint = jdbc.query(
        """select credential_id,peer_credential_id,device_occurred_at,payload_hash,protocol_version
           from offline_exchange_events where exchange_id=? and participant_user_id=?""",
        { rs, _ -> StoredExchangeFingerprint(
            UUID.fromString(rs.getString(1)),
            UUID.fromString(rs.getString(2)),
            rs.getTimestamp(3).toInstant(),
            rs.getString(4),
            rs.getInt(5),
        ) },
        exchangeId,
        userId,
    ).firstOrNull() ?: throw IllegalStateException("Conflicting exchange row disappeared")

    private fun parseCard(json: String): ExchangeCard {
        require(json.length <= 16_000) { "Music card is too large" }
        return mapper.readValue(json, ExchangeCard::class.java).also {
            require(it.displayAlias.length <= 40 && it.trackTitle.length <= 160 && it.trackArtist.length <= 160)
        }
    }

    private fun payloadCanonical(
        firstCredentialId: String,
        firstCard: ExchangeCard,
        secondCredentialId: String,
        secondCard: ExchangeCard,
    ): String = canonical(listOf(
        firstCredentialId to cardCanonical(firstCard),
        secondCredentialId to cardCanonical(secondCard),
    ).sortedBy { it.first }.map { canonical(listOf(it.first, it.second)) })

    private fun cardCanonical(card: ExchangeCard): String = canonical(listOf(
        card.displayAlias, card.trackTitle, card.trackArtist, card.melodyAlias,
        card.genreTags.sorted().joinToString(","), card.moodTags.sorted().joinToString(","),
    ))

    private fun recordCanonical(item: OfflineExchangeUpload): String = canonical(listOf(
        item.exchangeId, item.credentialId, item.peerCredentialId, item.payloadHash,
        item.protocolVersion.toString(), item.deviceOccurredAt.toString(),
    ))

    private fun canonical(parts: List<String>): String = parts.joinToString("") { part ->
        "${part.toByteArray(StandardCharsets.UTF_8).size}:$part"
    }

    private fun verifyDeviceSignature(publicKeyBase64: String, value: String, signatureBase64: String): Boolean = runCatching {
        val publicKey = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64))
        )
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(value.toByteArray(StandardCharsets.UTF_8))
            verify(Base64.getDecoder().decode(signatureBase64))
        }
    }.getOrDefault(false)

    private fun verifyPair(exchangeId: UUID) {
        val pairMatches = jdbc.queryForObject(
            """select count(*)=2 and count(distinct payload_hash)=1
               from offline_exchange_events where exchange_id=?""",
            Boolean::class.java,
            exchangeId,
        ) ?: false
        if (pairMatches) {
            val reversed = jdbc.queryForObject(
                """select count(*)=2 from offline_exchange_events a
                   join offline_exchange_events b on b.exchange_id=a.exchange_id
                    and b.credential_id=a.peer_credential_id and b.peer_credential_id=a.credential_id
                   where a.exchange_id=?""",
                Boolean::class.java,
                exchangeId,
            ) ?: false
            if (reversed) jdbc.update(
                "update offline_exchange_events set verification_state='VERIFIED',verified_at=now() where exchange_id=?",
                exchangeId,
            )
        }
    }

    private fun verificationState(exchangeId: UUID, userId: UUID): String = jdbc.queryForObject(
        "select verification_state from offline_exchange_events where exchange_id=? and participant_user_id=?",
        String::class.java, exchangeId, userId,
    ) ?: "UNCONFIRMED"

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

@RestController
class OfflineExchangeController(private val service: OfflineExchangeService) {
    @ExceptionHandler(IllegalArgumentException::class)
    fun invalidRequest(error: IllegalArgumentException): ResponseEntity<OfflineExchangeError> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            OfflineExchangeError(error.message?.take(160) ?: "Invalid offline exchange request")
        )

    @PostMapping("/api/v1/offline-credentials")
    fun issue(
        principal: Principal,
        @RequestBody request: OfflineCredentialRequest,
    ): OfflineCredentialResponse = service.issueCredential(UUID.fromString(principal.name), request)

    @PostMapping("/api/v1/offline-exchanges/batch")
    fun upload(
        principal: Principal,
        @RequestBody request: OfflineExchangeBatchRequest,
    ): List<OfflineExchangeSyncResult> = service.upload(UUID.fromString(principal.name), request)

    @GetMapping("/api/v1/offline-exchanges")
    fun history(principal: Principal): List<RemoteOfflineExchange> =
        service.history(UUID.fromString(principal.name))

    @DeleteMapping("/api/v1/offline-exchanges/{exchangeId}")
    fun delete(principal: Principal, @PathVariable exchangeId: UUID) =
        service.delete(UUID.fromString(principal.name), exchangeId)
}
