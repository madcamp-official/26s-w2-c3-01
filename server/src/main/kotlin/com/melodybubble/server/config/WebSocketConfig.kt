package com.melodybubble.server.config

import com.melodybubble.server.auth.JwtService
import com.melodybubble.server.auth.JwtSession
import com.melodybubble.server.realtime.RealtimeSessionPolicy
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessagingException
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.ConcurrentHashMap

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val jwtService: JwtService,
    private val realtimeSessions: RealtimeSessionPolicy,
    @param:Value("\${app.realtime.broker-relay.enabled:false}") private val brokerRelayEnabled: Boolean,
    @param:Value("\${app.realtime.broker-relay.host:localhost}") private val brokerRelayHost: String,
    @param:Value("\${app.realtime.broker-relay.port:61613}") private val brokerRelayPort: Int,
    @param:Value("\${app.realtime.broker-relay.login:guest}") private val brokerRelayLogin: String,
    @param:Value("\${app.realtime.broker-relay.passcode:guest}") private val brokerRelayPasscode: String,
    @param:Value("\${app.realtime.broker-relay.virtual-host:/}") private val brokerRelayVirtualHost: String,
) : WebSocketMessageBrokerConfigurer {
    private val authenticatedSessions = ConcurrentHashMap<String, JwtSession>()
    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        if (brokerRelayEnabled) {
            registry.enableStompBrokerRelay("/topic", "/queue")
                .setRelayHost(brokerRelayHost)
                .setRelayPort(brokerRelayPort)
                .setClientLogin(brokerRelayLogin)
                .setClientPasscode(brokerRelayPasscode)
                .setSystemLogin(brokerRelayLogin)
                .setSystemPasscode(brokerRelayPasscode)
                .setSystemHeartbeatSendInterval(10_000)
                .setSystemHeartbeatReceiveInterval(10_000)
                .setVirtualHost(brokerRelayVirtualHost)
                // Resolve /user destinations across application instances, not just the local registry.
                .setUserDestinationBroadcast("/topic/unresolved-user")
                .setUserRegistryBroadcast("/topic/simp-user-registry")
        } else {
            registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(longArrayOf(10_000, 10_000))
                .setTaskScheduler(stompHeartbeatScheduler())
        }
        registry.setApplicationDestinationPrefixes("/app")
        registry.setUserDestinationPrefix("/user")
    }

    @Bean
    fun stompHeartbeatScheduler() = ThreadPoolTaskScheduler().apply {
        poolSize = 1
        setThreadNamePrefix("stomp-heartbeat-")
        setRemoveOnCancelPolicy(true)
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*")
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(object : ChannelInterceptor {
            override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
                val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
                    ?: return message
                if (StompCommand.CONNECT == accessor.command) {
                    val authorization = accessor.getFirstNativeHeader("Authorization")
                        ?: accessor.getFirstNativeHeader("authorization")
                    val token = authorization?.trim()?.split(Regex("\\s+"), limit = 2)
                        ?.takeIf { it.size == 2 && it[0].equals("Bearer", ignoreCase = true) }
                        ?.get(1)
                    val jwtSession = token?.let(jwtService::parseSession)
                        ?.takeIf(realtimeSessions::isAllowed)
                        ?: throw MessagingException("Missing or invalid STOMP Authorization header")
                    accessor.user = UsernamePasswordAuthenticationToken(
                        jwtSession.userId.toString(),
                        null,
                        emptyList(),
                    )
                    accessor.sessionId?.let { authenticatedSessions[it] = jwtSession }
                }
                val command = accessor.command
                if (command == StompCommand.DISCONNECT) {
                    accessor.sessionId?.let(authenticatedSessions::remove)
                }
                if (command == StompCommand.SEND || command == StompCommand.SUBSCRIBE) {
                    val session = accessor.sessionId?.let(authenticatedSessions::get)
                    if (accessor.user == null || session == null || !realtimeSessions.isAllowed(session)) {
                        throw MessagingException("Authenticated, unexpired STOMP session required")
                    }
                }
                if (command == StompCommand.SUBSCRIBE && accessor.destination?.let(::isInternalBrokerTopic) == true) {
                    throw MessagingException("Subscription to internal broker destinations is forbidden")
                }
                return message
            }
        })
    }
}
