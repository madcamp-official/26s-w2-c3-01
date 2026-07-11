package com.melodybubble.server.config

import com.melodybubble.server.auth.JwtService
import com.melodybubble.server.realtime.RealtimeSessionPolicy
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.Customizer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.filter.OncePerRequestFilter

@Configuration
class SecurityConfig(
    private val jwtService: JwtService,
    private val realtimeSessions: RealtimeSessionPolicy,
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun opsUsers(
        @Value("\${app.ops.username}") username: String,
        @Value("\${app.ops.password}") password: String,
        encoder: PasswordEncoder,
    ): UserDetailsService {
        require(username.isNotBlank() && password.length >= 16) {
            "OPS_USERNAME and OPS_PASSWORD (at least 16 characters) are required"
        }
        return InMemoryUserDetailsManager(
            User.withUsername(username).password(encoder.encode(password)).roles("OPS").build()
        )
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests {
            it.requestMatchers(
                "/actuator/health",
                "/error",
                "/api/v1/auth/login",
                "/api/v1/auth/signup",
                "/api/v1/auth/google",
                "/api/v1/auth/refresh",
                "/ws/**",
            ).permitAll()
                .requestMatchers("/internal/ops/**").hasRole("OPS")
                .anyRequest().authenticated()
        }
        .httpBasic(Customizer.withDefaults())
        .addFilterBefore(BearerFilter(jwtService, realtimeSessions), UsernamePasswordAuthenticationFilter::class.java)
        .build()
}

private class BearerFilter(
    private val jwtService: JwtService,
    private val realtimeSessions: RealtimeSessionPolicy,
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val token = request.getHeader(HttpHeaders.AUTHORIZATION)?.removePrefix("Bearer ")
        if (!token.isNullOrBlank()) {
            jwtService.parseSession(token)?.takeIf(realtimeSessions::isAllowed)?.let { session ->
                SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(session.userId.toString(), null, emptyList())
            }
        }
        chain.doFilter(request, response)
    }
}
