package com.example.myapplication.data.realtime

import java.io.Closeable
import java.net.URI
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * Small STOMP 1.2 client built directly on OkHttp's WebSocket transport.
 *
 * REST remains authoritative. This client only carries realtime notifications, and emits a full
 * sync request after a previously healthy socket reconnects.
 */
class StompRealtimeClient(
    private val webSocketUrl: String,
    private val okHttpClient: OkHttpClient = OkHttpClient(),
    private val eventRouter: RealtimeEventRouter = RealtimeEventRouter(),
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val _connectionState = MutableStateFlow<RealtimeConnectionState>(
        RealtimeConnectionState.Disconnected
    )
    private val _events = MutableSharedFlow<RealtimeEvent>(
        extraBufferCapacity = EVENT_BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )
    private val _syncRequests = MutableSharedFlow<RealtimeSyncRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val seenEventIds = object : LinkedHashMap<String, Unit>(EVENT_ID_CACHE_SIZE + 1, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean =
            size > EVENT_ID_CACHE_SIZE
    }

    val connectionState: StateFlow<RealtimeConnectionState> = _connectionState.asStateFlow()
    val events: SharedFlow<RealtimeEvent> = _events.asSharedFlow()
    val syncRequests: SharedFlow<RealtimeSyncRequest> = _syncRequests.asSharedFlow()

    private var desiredAccessToken: String? = null
    private var webSocket: WebSocket? = null
    private var activeConnectionSerial = 0L
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null
    private var connectTimeoutJob: Job? = null
    private var heartbeatJob: Job? = null
    private var heartbeatWatchdogJob: Job? = null
    private var reconnectStabilityJob: Job? = null

    init {
        require(webSocketUrl.startsWith("wss://") || webSocketUrl.startsWith("ws://")) {
            "STOMP WebSocket URL must use ws:// or wss://"
        }
    }

    fun connect(accessToken: String) {
        require(accessToken.isNotBlank()) { "A non-blank access token is required" }
        var replacesSession = false
        val shouldOpen = synchronized(lock) {
            val currentState = _connectionState.value
            if (desiredAccessToken == accessToken &&
                (webSocket != null || currentState is RealtimeConnectionState.Reconnecting)
            ) {
                false
            } else {
                replacesSession = desiredAccessToken != null
                desiredAccessToken = accessToken
                activeConnectionSerial += 1
                webSocket?.close(NORMAL_CLOSE_CODE, "Session replaced")
                webSocket = null
                cancelConnectionJobsLocked()
                reconnectAttempt = 0
                seenEventIds.clear()
                true
            }
        }
        if (shouldOpen) openSocket(isReconnect = replacesSession, attempt = 0)
    }

    fun disconnect() {
        val socket = synchronized(lock) {
            desiredAccessToken = null
            activeConnectionSerial += 1
            reconnectAttempt = 0
            seenEventIds.clear()
            cancelConnectionJobsLocked()
            _connectionState.value = RealtimeConnectionState.Disconnected
            webSocket.also { webSocket = null }
        }
        socket?.send(stompFrame("DISCONNECT"))
        socket?.close(NORMAL_CLOSE_CODE, "Signed out")
    }

    override fun close() {
        disconnect()
        scope.cancel()
    }

    private fun openSocket(isReconnect: Boolean, attempt: Int) {
        val connection = synchronized(lock) {
            val token = desiredAccessToken ?: return
            activeConnectionSerial += 1
            val serial = activeConnectionSerial
            _connectionState.value = RealtimeConnectionState.Connecting(isReconnect)
            Connection(serial, token, isReconnect, attempt)
        }
        val request = Request.Builder().url(webSocketUrl).build()
        val socket = okHttpClient.newWebSocket(request, Listener(connection))
        synchronized(lock) {
            if (connection.serial == activeConnectionSerial && desiredAccessToken != null) {
                webSocket = socket
                connectTimeoutJob?.cancel()
                connectTimeoutJob = scope.launch {
                    delay(CONNECT_TIMEOUT_MILLIS)
                    val stillConnecting = synchronized(lock) {
                        connection.serial == activeConnectionSerial &&
                            _connectionState.value is RealtimeConnectionState.Connecting
                    }
                    if (stillConnecting) {
                        socket.cancel()
                        handleTerminal(connection, "STOMP CONNECTED timeout")
                    }
                }
            } else {
                socket.close(NORMAL_CLOSE_CODE, "Superseded")
            }
        }
    }

    private inner class Listener(private val connection: Connection) : WebSocketListener() {
        private val terminalHandled = AtomicBoolean(false)
        private val lastServerActivity = AtomicLong(SystemClock.elapsedRealtime())
        private val incoming = StringBuilder()

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!isActive(connection)) {
                webSocket.close(NORMAL_CLOSE_CODE, "Superseded")
                return
            }
            webSocket.send(
                stompFrame(
                    command = "CONNECT",
                    headers = linkedMapOf(
                        "accept-version" to STOMP_VERSION,
                        "host" to (runCatching { URI(webSocketUrl).host }.getOrNull() ?: "localhost"),
                        "heart-beat" to "$CLIENT_SEND_HEARTBEAT_MILLIS,$CLIENT_RECEIVE_HEARTBEAT_MILLIS",
                        "Authorization" to "Bearer ${connection.accessToken}",
                    ),
                    escapeHeaders = false,
                )
            )
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            consume(text, webSocket)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            consume(bytes.utf8(), webSocket)
        }

