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
    private val topicSubscriptions = linkedMapOf<String, String>()

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
            topicSubscriptions.clear()
            cancelConnectionJobsLocked()
            _connectionState.value = RealtimeConnectionState.Disconnected
            webSocket.also { webSocket = null }
        }
        socket?.send(stompFrame("DISCONNECT"))
        socket?.close(NORMAL_CLOSE_CODE, "Signed out")
    }

    fun subscribeTopic(destination: String) {
        require(SUB_LOUNGE_TOPIC.matches(destination)) { "Unsupported realtime topic" }
        val (id, socket) = synchronized(lock) {
            topicSubscriptions.getOrPut(destination) {
                "topic-${destination.hashCode().toUInt()}"
            } to webSocket.takeIf { _connectionState.value is RealtimeConnectionState.Connected }
        }
        socket?.send(subscribeFrame(id, destination))
    }

    fun unsubscribeTopic(destination: String) {
        val (id, socket) = synchronized(lock) {
            topicSubscriptions.remove(destination) to
                webSocket.takeIf { _connectionState.value is RealtimeConnectionState.Connected }
        }
        id ?: return
        socket?.send(stompFrame("UNSUBSCRIBE", linkedMapOf("id" to id)))
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

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
            terminal("Server closing ($code): $reason")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            terminal("Socket closed ($code): $reason")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            terminal(listOfNotNull(t::class.simpleName, t.message).joinToString(": "))
        }

        private fun consume(chunk: String, webSocket: WebSocket) {
            if (!isActive(connection)) return
            lastServerActivity.set(SystemClock.elapsedRealtime())
            if (chunk.all { it == '\n' || it == '\r' }) return
            synchronized(incoming) {
                incoming.append(chunk)
                while (true) {
                    val terminator = incoming.indexOf(STOMP_NULL.toString())
                    if (terminator < 0) break
                    val rawFrame = incoming.substring(0, terminator).trimStart('\n', '\r')
                    incoming.delete(0, terminator + 1)
                    if (rawFrame.isNotBlank()) {
                        runCatching { parseFrame(rawFrame) }
                            .onSuccess { frame -> handleFrame(frame, webSocket) }
                            .onFailure { error ->
                                emitIfNew(
                                    RealtimeEvent.ParsingError(
                                        destination = RealtimeDestinations.ERRORS,
                                        rawBody = rawFrame,
                                        reason = error.message ?: "Malformed STOMP frame",
                                    )
                                )
                                webSocket.close(PROTOCOL_ERROR_CLOSE_CODE, "Malformed STOMP frame")
                                terminal("Malformed STOMP frame")
                            }
                    }
                }
                while (incoming.isNotEmpty() && (incoming[0] == '\n' || incoming[0] == '\r')) {
                    incoming.deleteCharAt(0)
                }
                if (incoming.length > MAX_INCOMING_BUFFER_CHARS) {
                    incoming.clear()
                    webSocket.cancel()
                    terminal("STOMP frame exceeded the receive limit")
                }
            }
        }

        private fun handleFrame(frame: StompFrame, webSocket: WebSocket) {
            if (!isActive(connection)) return
            when (frame.command) {
                "CONNECTED" -> handleConnected(frame, webSocket)
                "MESSAGE" -> {
                    val destination = frame.headers["destination"] ?: RealtimeDestinations.ERRORS
                    emitIfNew(eventRouter.route(destination, frame.body))
                }
                "ERROR" -> {
                    val routed = eventRouter.route(RealtimeDestinations.ERRORS, frame.body)
                    val event = if (routed is RealtimeEvent.ParsingError) {
                        routed.copy(reason = frame.headers["message"] ?: routed.reason)
                    } else routed
                    emitIfNew(event)
                    terminal(frame.headers["message"] ?: "STOMP ERROR")
                    webSocket.close(PROTOCOL_ERROR_CLOSE_CODE, "STOMP ERROR")
                }
                "RECEIPT" -> Unit
            }
        }

        private fun handleConnected(frame: StompFrame, webSocket: WebSocket) {
            if (!isActive(connection)) return
            val heartbeat = negotiatedHeartbeat(frame.headers["heart-beat"])
            val connectedAt = System.currentTimeMillis()
            synchronized(lock) {
                if (connection.serial != activeConnectionSerial) return
                connectTimeoutJob?.cancel()
                connectTimeoutJob = null
                _connectionState.value = RealtimeConnectionState.Connected(
                    sessionId = frame.headers["session"],
                    stompVersion = frame.headers["version"],
                    serverHeartbeat = heartbeat,
                    connectedAtEpochMillis = connectedAt,
                    isReconnect = connection.isReconnect,
                )
                reconnectStabilityJob?.cancel()
                reconnectStabilityJob = scope.launch {
                    delay(RECONNECT_STABILITY_RESET_MILLIS)
                    synchronized(lock) {
                        if (connection.serial == activeConnectionSerial &&
                            _connectionState.value is RealtimeConnectionState.Connected
                        ) {
                            reconnectAttempt = 0
                        }
                    }
                }
            }
            RealtimeDestinations.userQueues.forEachIndexed { index, destination ->
                webSocket.send(subscribeFrame("user-$index", destination))
            }
            synchronized(lock) { topicSubscriptions.toMap() }.forEach { (destination, id) ->
                webSocket.send(subscribeFrame(id, destination))
            }
            startHeartbeat(webSocket, connection, heartbeat, lastServerActivity)
            if (connection.isReconnect) {
                _syncRequests.tryEmit(
                    RealtimeSyncRequest.FullSync(connectedAt, connection.attempt)
                )
            }
        }

        private fun terminal(cause: String) {
            if (terminalHandled.compareAndSet(false, true)) handleTerminal(connection, cause)
        }
    }

    private fun startHeartbeat(
        socket: WebSocket,
        connection: Connection,
        heartbeat: StompHeartbeat,
        lastServerActivity: AtomicLong,
    ) {
        synchronized(lock) {
            if (connection.serial != activeConnectionSerial || desiredAccessToken == null) return
            heartbeatJob?.cancel()
            heartbeatWatchdogJob?.cancel()
            if (heartbeat.canSendEveryMillis > 0) {
                heartbeatJob = scope.launch {
                    while (isActive && isActive(connection)) {
                        delay(heartbeat.canSendEveryMillis)
                        if (isActive(connection)) socket.send("\n")
                    }
                }
            }
            if (heartbeat.wantsReceiveEveryMillis > 0) {
                heartbeatWatchdogJob = scope.launch {
                    val timeout = (heartbeat.wantsReceiveEveryMillis * HEARTBEAT_GRACE_MULTIPLIER)
                        .coerceAtLeast(MIN_HEARTBEAT_TIMEOUT_MILLIS)
                    while (isActive && isActive(connection)) {
                        delay(heartbeat.wantsReceiveEveryMillis)
                        if (SystemClock.elapsedRealtime() - lastServerActivity.get() > timeout) {
                            socket.cancel()
                            handleTerminal(connection, "Server heartbeat timeout")
                            break
                        }
                    }
                }
            }
        }
    }

    private fun handleTerminal(connection: Connection, cause: String) {
        val retry = synchronized(lock) {
            if (connection.serial != activeConnectionSerial || desiredAccessToken == null) return
            webSocket = null
            // Invalidate every callback belonging to the failed socket before scheduling another
            // one. This also prevents an onFailure following a watchdog cancel from double-retrying.
            activeConnectionSerial += 1
            connectTimeoutJob?.cancel()
            heartbeatJob?.cancel()
            heartbeatWatchdogJob?.cancel()
            reconnectStabilityJob?.cancel()
            reconnectJob?.cancel()
            reconnectAttempt += 1
            val attempt = reconnectAttempt
            val retryIn = retryDelay(attempt)
            _connectionState.value = RealtimeConnectionState.Reconnecting(attempt, retryIn, cause)
            Retry(activeConnectionSerial, attempt, retryIn)
        }
        reconnectJob = scope.launch {
            delay(retry.delayMillis)
            val shouldReconnect = synchronized(lock) {
                desiredAccessToken != null && retry.connectionSerial == activeConnectionSerial
            }
            if (shouldReconnect) openSocket(isReconnect = true, attempt = retry.attempt)
        }
    }

    private fun emitIfNew(event: RealtimeEvent) {
        val eventId = event.eventId
        val duplicate = eventId != null && synchronized(lock) {
            if (seenEventIds.containsKey(eventId)) true else {
                seenEventIds[eventId] = Unit
                false
            }
        }
        if (!duplicate && !_events.tryEmit(event)) {
            val attempt = synchronized(lock) { reconnectAttempt }
            _syncRequests.tryEmit(
                RealtimeSyncRequest.FullSync(System.currentTimeMillis(), attempt)
            )
        }
    }

    private fun isActive(connection: Connection): Boolean = synchronized(lock) {
        desiredAccessToken != null && connection.serial == activeConnectionSerial
    }

    private fun cancelConnectionJobsLocked() {
        reconnectJob?.cancel()
        connectTimeoutJob?.cancel()
        heartbeatJob?.cancel()
        heartbeatWatchdogJob?.cancel()
        reconnectStabilityJob?.cancel()
        reconnectJob = null
        connectTimeoutJob = null
        heartbeatJob = null
        heartbeatWatchdogJob = null
        reconnectStabilityJob = null
    }

    private data class Connection(
        val serial: Long,
        val accessToken: String,
        val isReconnect: Boolean,
        val attempt: Int,
    )

    private data class Retry(
        val connectionSerial: Long,
        val attempt: Int,
        val delayMillis: Long,
    )

    private data class StompFrame(
        val command: String,
        val headers: Map<String, String>,
        val body: String,
    )

    companion object {
        private const val STOMP_VERSION = "1.2"
        private const val STOMP_NULL = '\u0000'
        private const val CLIENT_SEND_HEARTBEAT_MILLIS = 10_000L
        private const val CLIENT_RECEIVE_HEARTBEAT_MILLIS = 10_000L
        private const val CONNECT_TIMEOUT_MILLIS = 15_000L
        private const val RECONNECT_STABILITY_RESET_MILLIS = 30_000L
        private const val MIN_HEARTBEAT_TIMEOUT_MILLIS = 30_000L
        private const val HEARTBEAT_GRACE_MULTIPLIER = 3L
        private const val EVENT_BUFFER_CAPACITY = 64
        private const val EVENT_ID_CACHE_SIZE = 1_024
        private const val MAX_INCOMING_BUFFER_CHARS = 1_000_000
        private const val NORMAL_CLOSE_CODE = 1000
        private const val PROTOCOL_ERROR_CLOSE_CODE = 1002
        private val SUB_LOUNGE_TOPIC = Regex("/topic/sub-lounges/[0-9a-fA-F-]{36}")

        internal fun retryDelay(attempt: Int): Long = when (attempt.coerceAtLeast(1)) {
            1 -> 1_000L
            2 -> 2_000L
            3 -> 5_000L
            4 -> 10_000L
            5 -> 20_000L
            else -> 30_000L
        }

        private fun negotiatedHeartbeat(value: String?): StompHeartbeat {
            val server = value?.split(',')?.mapNotNull(String::toLongOrNull).orEmpty()
            val serverCanSend = server.getOrElse(0) { 0L }.coerceAtLeast(0L)
            val serverWantsReceive = server.getOrElse(1) { 0L }.coerceAtLeast(0L)
            val sendEvery = if (CLIENT_SEND_HEARTBEAT_MILLIS == 0L || serverWantsReceive == 0L) {
                0L
            } else {
                maxOf(CLIENT_SEND_HEARTBEAT_MILLIS, serverWantsReceive)
            }
            val receiveEvery = if (CLIENT_RECEIVE_HEARTBEAT_MILLIS == 0L || serverCanSend == 0L) {
                0L
            } else {
                maxOf(CLIENT_RECEIVE_HEARTBEAT_MILLIS, serverCanSend)
            }
            return StompHeartbeat(sendEvery, receiveEvery)
        }

        private fun parseFrame(raw: String): StompFrame {
            val normalized = raw.replace("\r\n", "\n")
            val separator = normalized.indexOf("\n\n")
            val head = if (separator >= 0) normalized.substring(0, separator) else normalized
            val body = if (separator >= 0) normalized.substring(separator + 2) else ""
            val lines = head.split('\n')
            val command = lines.firstOrNull()?.trim().orEmpty()
            require(command.isNotBlank()) { "Missing STOMP command" }
            val headers = buildMap {
                lines.drop(1).filter(String::isNotBlank).forEach { line ->
                    val colon = line.indexOf(':')
                    require(colon > 0) { "Malformed STOMP header" }
                    put(unescapeHeader(line.substring(0, colon)), unescapeHeader(line.substring(colon + 1)))
                }
            }
            return StompFrame(command, headers, body)
        }

        private fun stompFrame(
            command: String,
            headers: Map<String, String> = emptyMap(),
            body: String = "",
            escapeHeaders: Boolean = true,
        ): String = buildString {
            append(command).append('\n')
            headers.forEach { (name, value) ->
                append(if (escapeHeaders) escapeHeader(name) else name)
                    .append(':')
                    .append(if (escapeHeaders) escapeHeader(value) else value)
                    .append('\n')
            }
            append('\n').append(body).append(STOMP_NULL)
        }

        private fun subscribeFrame(id: String, destination: String) = stompFrame(
            command = "SUBSCRIBE",
            headers = linkedMapOf(
                "id" to id,
                "destination" to destination,
                "ack" to "auto",
            ),
        )

        private fun escapeHeader(value: String): String = buildString(value.length) {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '\r' -> append("\\r")
                    '\n' -> append("\\n")
                    ':' -> append("\\c")
                    else -> append(char)
                }
            }
        }

        private fun unescapeHeader(value: String): String = buildString(value.length) {
            var index = 0
            while (index < value.length) {
                if (value[index] != '\\' || index + 1 >= value.length) {
                    append(value[index++])
                    continue
                }
                when (val escaped = value[index + 1]) {
                    '\\' -> append('\\')
                    'r' -> append('\r')
                    'n' -> append('\n')
                    'c' -> append(':')
                    else -> append(escaped)
                }
                index += 2
            }
        }
    }
}
