package com.melodybubble.server.config

import com.melodybubble.server.auth.JwtService
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
class SecurityConfig(private val jwtService: JwtService) {
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
            it.requestMatchers("/actuator/health", "/api/v1/auth/**", "/ws/**").permitAll()
                .requestMatchers("/internal/ops/**").hasRole("OPS")
                .anyRequest().authenticated()
        }
        .httpBasic(Customizer.withDefaults())
        .addFilterBefore(BearerFilter(jwtService), UsernamePasswordAuthenticationFilter::class.java)
        .build()
}

private class BearerFilter(private val jwtService: JwtService) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val token = request.getHeader(HttpHeaders.AUTHORIZATION)?.removePrefix("Bearer ")
        if (!token.isNullOrBlank()) {
            jwtService.parse(token)?.let { userId ->
                SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(userId.toString(), null, emptyList())
            }
        }
        chain.doFilter(request, response)
    }
}
