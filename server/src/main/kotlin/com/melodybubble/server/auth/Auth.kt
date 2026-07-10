package com.melodybubble.server.auth

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

data class LoginRequest(val email: String, val password: String)
data class TokenResponse(val accessToken: String, val tokenType: String = "Bearer", val expiresInSeconds: Long)

@Component
class JwtService(
    @Value("\${app.jwt.secret}") private val rawSecret: String,
    @Value("\${app.jwt.access-token-minutes}") private val accessMinutes: Long,
) {
    private fun key(): SecretKey {
        if (rawSecret.toByteArray(StandardCharsets.UTF_8).size < 32) {
            throw IllegalStateException("JWT_SECRET must be at least 32 bytes")
        }
        return Keys.hmacShaKeyFor(rawSecret.toByteArray(StandardCharsets.UTF_8))
    }
    fun issue(userId: UUID): String = Jwts.builder().subject(userId.toString())
        .issuedAt(Date()).expiration(Date.from(Instant.now().plus(accessMinutes, ChronoUnit.MINUTES)))
        .signWith(key()).compact()
    fun parse(token: String): UUID? = runCatching {
        UUID.fromString(Jwts.parser().verifyWith(key()).build().parseSignedClaims(token).payload.subject)
    }.getOrNull()
    fun ttlSeconds() = accessMinutes * 60
}

@Service
class AuthService(private val jdbc: JdbcTemplate, private val jwt: JwtService) {
    private val encoder = BCryptPasswordEncoder()
    fun login(request: LoginRequest): TokenResponse {
        val row = jdbc.query("select id, password_hash from users where email = ?", { rs, _ -> UUID.fromString(rs.getString("id")) to rs.getString("password_hash") }, request.email).firstOrNull()
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password")
        if (!encoder.matches(request.password, row.second)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password")
        return TokenResponse(jwt.issue(row.first), expiresInSeconds = jwt.ttlSeconds())
    }
}

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {
    @PostMapping("/login") fun login(@RequestBody request: LoginRequest) = authService.login(request)
}
