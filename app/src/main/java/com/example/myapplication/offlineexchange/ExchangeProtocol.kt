package com.example.myapplication.offlineexchange

import com.google.gson.Gson

object ExchangeProtocol {
    const val VERSION = 1
    const val TYPE_HELLO = "HELLO"
    const val TYPE_ACK = "ACK"
    private val gson = Gson()

    internal fun encode(message: ExchangeWireMessage): ByteArray = gson.toJson(message).toByteArray(Charsets.UTF_8)

    internal fun decode(bytes: ByteArray): ExchangeWireMessage =
        gson.fromJson(String(bytes, Charsets.UTF_8), ExchangeWireMessage::class.java)

    fun cardJson(card: ExchangeMusicCard): String = gson.toJson(card)

    fun cardFromJson(json: String): ExchangeMusicCard = gson.fromJson(json, ExchangeMusicCard::class.java)

    fun credentialCanonical(credential: OfflineCredential): String = canonical(listOf(
        credential.credentialId,
        credential.publicSubject,
        credential.devicePublicKey,
        credential.displayAlias,
        credential.issuedAt.toString(),
        credential.expiresAt.toString(),
    ))

    internal fun helloCanonical(nonce: String, credential: OfflineCredential, card: ExchangeMusicCard): String =
        canonical(listOf(TYPE_HELLO, nonce, credential.credentialId, cardCanonical(card)))

    internal fun ackCanonical(exchangeId: String, payloadHash: String): String =
        canonical(listOf(TYPE_ACK, exchangeId, payloadHash))

    fun recordCanonical(
        exchangeId: String,
        credentialId: String,
        peerCredentialId: String,
        payloadHash: String,
        protocolVersion: Int,
        deviceOccurredAt: Long,
    ): String = canonical(listOf(
        exchangeId,
        credentialId,
        peerCredentialId,
        payloadHash,
        protocolVersion.toString(),
        deviceOccurredAt.toString(),
    ))

    internal fun payloadCanonical(
        firstCredentialId: String,
        firstCard: ExchangeMusicCard,
        secondCredentialId: String,
        secondCard: ExchangeMusicCard,
    ): String = canonical(listOf(
        firstCredentialId to cardCanonical(firstCard),
        secondCredentialId to cardCanonical(secondCard),
    ).sortedBy { it.first }.map { canonical(listOf(it.first, it.second)) })

    private fun cardCanonical(card: ExchangeMusicCard): String = canonical(listOf(
        card.displayAlias,
        card.trackTitle,
        card.trackArtist,
        card.melodyAlias,
        card.genreTags.sorted().joinToString(","),
        card.moodTags.sorted().joinToString(","),
    ))

    private fun canonical(parts: List<String>): String = parts.joinToString("") { part ->
        "${part.toByteArray(Charsets.UTF_8).size}:$part"
    }
}
