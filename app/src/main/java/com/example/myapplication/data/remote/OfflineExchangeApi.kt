package com.example.myapplication.data.remote

import com.example.myapplication.offlineexchange.OfflineCredential
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

data class OfflineCredentialRequest(val devicePublicKey: String)

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

data class OfflineExchangeSyncResult(
    val exchangeId: String,
    val state: String,
)

interface OfflineExchangeApi {
    @POST(MelodyApiContract.Rest.OFFLINE_CREDENTIALS)
    suspend fun issueCredential(
        @Header("Authorization") authorization: String,
        @Body request: OfflineCredentialRequest,
    ): OfflineCredential

    @POST(MelodyApiContract.Rest.OFFLINE_EXCHANGE_BATCH)
    suspend fun upload(
        @Header("Authorization") authorization: String,
        @Body request: OfflineExchangeBatchRequest,
    ): List<OfflineExchangeSyncResult>

    @GET(MelodyApiContract.Rest.OFFLINE_EXCHANGE_HISTORY)
    suspend fun history(
        @Header("Authorization") authorization: String,
    ): List<RemoteOfflineExchange>

    @DELETE(MelodyApiContract.Rest.OFFLINE_EXCHANGE_DELETE)
    suspend fun delete(
        @Header("Authorization") authorization: String,
        @Path("exchangeId") exchangeId: String,
    )
}
