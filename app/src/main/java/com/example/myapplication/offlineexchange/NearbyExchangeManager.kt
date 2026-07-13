package com.example.myapplication.offlineexchange

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NearbyExchangeManager(
    context: Context,
    private val crypto: ExchangeCrypto = ExchangeCrypto(),
    private val onCompleted: (OfflineExchangeResult) -> Unit,
) {
    private val client = Nearby.getConnectionsClient(context.applicationContext)
    private val serviceId = "com.example.myapplication.OFFLINE_MUSIC_EXCHANGE_V1"
    private val _state = MutableStateFlow<ExchangeConnectionState>(ExchangeConnectionState.Idle)
    val state: StateFlow<ExchangeConnectionState> = _state.asStateFlow()

    private var identity: OfflineExchangeIdentity? = null
    private var activeEndpointId: String? = null
    private var activeEndpointName: String = "주변 사용자"
    private var localNonce: String = UUID.randomUUID().toString()
    private var remoteHello: ExchangeWireMessage? = null
    private var exchangeId: String? = null
    private var payloadHash: String? = null
    private val completionGate = ExchangeCompletionGate()
    private val completed = AtomicBoolean(false)

    fun start(identity: OfflineExchangeIdentity) {
        stop(resetState = false)
        if (identity.credential.expiresAt <= System.currentTimeMillis()) {
            _state.value = ExchangeConnectionState.Error("오프라인 교환 인증이 만료됐어요. 인터넷 연결 후 다시 인증해 주세요.")
            return
        }
        if (!crypto.verifyServer(identity.credential, identity.credential.serverPublicKey)) {
            _state.value = ExchangeConnectionState.Error("저장된 오프라인 인증서를 확인할 수 없어요.")
            return
        }
        this.identity = identity
        resetHandshake()
        _state.value = ExchangeConnectionState.Discovering
        runCatching {
            client.startAdvertising(
                identity.endpointName.take(40),
                serviceId,
                connectionLifecycleCallback,
                AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build(),
            ).addOnFailureListener { error("기기 검색을 시작하지 못했어요") }
            client.startDiscovery(
                serviceId,
                endpointDiscoveryCallback,
                DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build(),
            ).addOnFailureListener { error("주변 기기를 찾지 못했어요") }
        }.onFailure { error("근거리 권한과 Bluetooth/Wi-Fi 상태를 확인해 주세요") }
    }

    fun requestConnection(endpointId: String) {
        val local = identity ?: return error("오프라인 계정 정보가 없어요")
        activeEndpointId = endpointId
        val endpointName = (state.value as? ExchangeConnectionState.EndpointFound)?.endpointName
            ?: "주변 사용자"
        activeEndpointName = endpointName
        _state.value = ExchangeConnectionState.Connecting(endpointName)
        runCatching {
            client.requestConnection(local.endpointName.take(40), endpointId, connectionLifecycleCallback)
                .addOnFailureListener { error("상대 기기에 연결하지 못했어요") }
        }.onFailure { error("상대 기기에 연결하지 못했어요") }
    }

    fun approveConnection() {
        val endpointId = activeEndpointId ?: return
        _state.value = ExchangeConnectionState.Connecting(activeEndpointName)
        runCatching {
            client.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { error("연결을 승인하지 못했어요") }
        }.onFailure { error("연결을 승인하지 못했어요") }
    }

    fun rejectConnection() {
        activeEndpointId?.let(client::rejectConnection)
        stop()
    }

    fun stop(resetState: Boolean = true) {
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        runCatching { client.stopAllEndpoints() }
        identity = null
        activeEndpointId = null
        resetHandshake()
        if (resetState) _state.value = ExchangeConnectionState.Idle
    }

    fun clearResult() {
        stop()
    }

    fun permissionDenied() {
        _state.value = ExchangeConnectionState.Error("근거리 기기 검색 권한이 필요해요. 설정에서 권한을 허용해 주세요.")
    }

    fun unavailable(message: String) {
        _state.value = ExchangeConnectionState.Error(message)
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (activeEndpointId != null) return
            _state.value = ExchangeConnectionState.EndpointFound(endpointId, info.endpointName)
        }

        override fun onEndpointLost(endpointId: String) {
            val found = _state.value as? ExchangeConnectionState.EndpointFound
            if (found?.endpointId == endpointId) _state.value = ExchangeConnectionState.Discovering
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val currentEndpoint = activeEndpointId
            if (currentEndpoint != null && currentEndpoint != endpointId) {
                client.rejectConnection(endpointId)
                return
            }
            activeEndpointId = endpointId
            activeEndpointName = info.endpointName
            client.stopDiscovery()
            _state.value = ExchangeConnectionState.AwaitingApproval(
                endpointId,
                info.endpointName,
                info.authenticationDigits,
            )
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (!resolution.status.isSuccess) {
                error("상대 기기와 연결을 완료하지 못했어요")
                return
            }
            activeEndpointId = endpointId
            _state.value = ExchangeConnectionState.Exchanging(activeEndpointName)
            sendHello(endpointId)
        }

        override fun onDisconnected(endpointId: String) {
            completionGate.disconnect()
            if (!completed.get()) error("카드 교환이 끝나기 전에 연결이 끊겼어요")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return error("지원하지 않는 교환 데이터예요")
            val message = runCatching { ExchangeProtocol.decode(bytes) }
                .getOrElse { return error("교환 데이터를 읽지 못했어요") }
            when (message.type) {
                ExchangeProtocol.TYPE_HELLO -> receiveHello(endpointId, message)
                ExchangeProtocol.TYPE_ACK -> receiveAck(message)
                else -> error("지원하지 않는 교환 프로토콜이에요")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.FAILURE ||
                update.status == PayloadTransferUpdate.Status.CANCELED
            ) error("음악 카드 전송이 중단됐어요")
        }
    }

    private fun sendHello(endpointId: String) {
        val local = identity ?: return error("오프라인 계정 정보가 없어요")
        val canonical = ExchangeProtocol.helloCanonical(localNonce, local.credential, local.card)
        val hello = ExchangeWireMessage(
            type = ExchangeProtocol.TYPE_HELLO,
            nonce = localNonce,
            credential = local.credential,
            card = local.card,
            signature = crypto.sign(canonical),
        )
        send(endpointId, hello)
    }

    private fun receiveHello(endpointId: String, message: ExchangeWireMessage) {
        val local = identity ?: return error("오프라인 계정 정보가 없어요")
        val nonce = message.nonce ?: return error("상대 기기의 교환 nonce가 없어요")
        val credential = message.credential ?: return error("상대 기기의 인증서가 없어요")
        val card = message.card ?: return error("상대 기기의 음악 카드가 없어요")
        if (credential.publicSubject == local.credential.publicSubject) return error("같은 계정끼리는 교환할 수 없어요")
        if (credential.expiresAt <= System.currentTimeMillis()) return error("상대 기기의 인증서가 만료됐어요")
        if (!crypto.verifyServer(credential, local.credential.serverPublicKey)) {
            return error("상대 기기의 서버 인증서를 확인할 수 없어요")
        }
        if (!crypto.verifyDevice(
                credential.devicePublicKey,
                ExchangeProtocol.helloCanonical(nonce, credential, card),
                message.signature,
            )) return error("상대 기기의 카드 서명이 올바르지 않아요")

        remoteHello = message
        val exchangeSeed = listOf(
            local.credential.credentialId to localNonce,
            credential.credentialId to nonce,
        ).sortedBy { it.first }.joinToString("|") { "${it.first}:${it.second}" }
        val derivedExchangeId = crypto.deterministicExchangeId(exchangeSeed)
        val derivedPayloadHash = crypto.hash(
            ExchangeProtocol.payloadCanonical(
                local.credential.credentialId,
                local.card,
                credential.credentialId,
                card,
            )
        )
        exchangeId = derivedExchangeId
        payloadHash = derivedPayloadHash
        val ack = ExchangeWireMessage(
            type = ExchangeProtocol.TYPE_ACK,
            exchangeId = derivedExchangeId,
            payloadHash = derivedPayloadHash,
            signature = crypto.sign(ExchangeProtocol.ackCanonical(derivedExchangeId, derivedPayloadHash)),
        )
        completionGate.markLocalAck()
        send(endpointId, ack)
        finishIfReady()
    }

    private fun receiveAck(message: ExchangeWireMessage) {
        val hello = remoteHello ?: return error("상대 기기의 카드보다 확인 응답이 먼저 도착했어요")
        val credential = hello.credential ?: return error("상대 인증 정보가 없어요")
        val expectedExchangeId = exchangeId ?: return error("교환 ID가 준비되지 않았어요")
        val expectedHash = payloadHash ?: return error("교환 데이터 검증값이 없어요")
        if (message.exchangeId != expectedExchangeId || message.payloadHash != expectedHash) {
            return error("양쪽 기기의 교환 데이터가 일치하지 않아요")
        }
        if (!crypto.verifyDevice(
                credential.devicePublicKey,
                ExchangeProtocol.ackCanonical(expectedExchangeId, expectedHash),
                message.signature,
            )) return error("상대 기기의 완료 서명이 올바르지 않아요")
        completionGate.markRemoteAck()
        finishIfReady()
    }

    private fun finishIfReady() {
        if (!completionGate.canCommit || !completed.compareAndSet(false, true)) return
        val local = identity ?: return
        val hello = remoteHello ?: return
        val peerCredential = hello.credential ?: return
        val peerCard = hello.card ?: return
        val id = exchangeId ?: return
        val hash = payloadHash ?: return
        val occurredAt = System.currentTimeMillis()
        val result = OfflineExchangeResult(
            exchangeId = id,
            peerCredentialId = peerCredential.credentialId,
            peerCard = peerCard,
            sentCard = local.card,
            exchangedAt = occurredAt,
            payloadHash = hash,
            protocolVersion = ExchangeProtocol.VERSION,
            recordSignature = crypto.sign(
                ExchangeProtocol.recordCanonical(
                    id,
                    local.credential.credentialId,
                    peerCredential.credentialId,
                    hash,
                    ExchangeProtocol.VERSION,
                    occurredAt,
                )
            ),
        )
        _state.value = ExchangeConnectionState.Completed(result)
        onCompleted(result)
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        activeEndpointId?.let { runCatching { client.disconnectFromEndpoint(it) } }
    }

    private fun send(endpointId: String, message: ExchangeWireMessage) {
        client.sendPayload(endpointId, Payload.fromBytes(ExchangeProtocol.encode(message)))
            .addOnFailureListener { error("교환 데이터를 전송하지 못했어요") }
    }

    private fun resetHandshake() {
        localNonce = UUID.randomUUID().toString()
        remoteHello = null
        exchangeId = null
        payloadHash = null
        completionGate.reset()
        completed.set(false)
    }

    private fun error(message: String) {
        _state.value = ExchangeConnectionState.Error(message)
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        activeEndpointId?.let { runCatching { client.disconnectFromEndpoint(it) } }
    }
}
