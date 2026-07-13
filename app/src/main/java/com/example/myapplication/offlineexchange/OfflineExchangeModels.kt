package com.example.myapplication.offlineexchange

data class OfflineCredential(
    val credentialId: String,
    val publicSubject: String,
    val devicePublicKey: String,
    val displayAlias: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val serverSignature: String,
    val serverPublicKey: String,
)
data class ExchangeMusicCard(
    val displayAlias: String,
    val trackTitle: String,
    val trackArtist: String,
    val melodyAlias: String,
    val genreTags: List<String> = emptyList(),
    val moodTags: List<String> = emptyList(),
)

data class OfflineExchangeIdentity(
    val ownerUserId: String,
    val endpointName: String,
    val credential: OfflineCredential,
    val card: ExchangeMusicCard,
)

data class OfflineExchangeResult(
    val exchangeId: String,
    val peerCredentialId: String,
    val peerCard: ExchangeMusicCard,
    val sentCard: ExchangeMusicCard,
    val exchangedAt: Long,
    val payloadHash: String,
    val protocolVersion: Int,
    val recordSignature: String,
)

sealed interface ExchangeConnectionState {
    data object Idle : ExchangeConnectionState
    data object Discovering : ExchangeConnectionState
    data class EndpointFound(val endpointId: String, val endpointName: String) : ExchangeConnectionState
    data class AwaitingApproval(
        val endpointId: String,
        val endpointName: String,
        val authenticationDigits: String,
    ) : ExchangeConnectionState
    data class Connecting(val endpointName: String) : ExchangeConnectionState
    data class Exchanging(val endpointName: String) : ExchangeConnectionState
    data class Completed(val result: OfflineExchangeResult) : ExchangeConnectionState
    data class Error(val message: String) : ExchangeConnectionState
}

internal data class ExchangeWireMessage(
    val type: String,
    val nonce: String? = null,
    val credential: OfflineCredential? = null,
    val card: ExchangeMusicCard? = null,
    val exchangeId: String? = null,
    val payloadHash: String? = null,
    val signature: String,
)
