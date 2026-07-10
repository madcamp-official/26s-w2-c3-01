package com.melodybubble.server.auth

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
data class GoogleLoginRequest(val idToken: String)
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

@Component
class GoogleTokenVerifier(
    @Value("\${app.google.web-client-id}") webClientId: String,
) {
    private val verifier = GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance())
        .setAudience(listOf(webClientId))
        .build()

    fun verify(rawToken: String): GoogleIdToken.Payload = runCatching {
        verifier.verify(rawToken)?.payload
    }.getOrNull() ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google ID token")
}

@Service
class AuthService(
    private val jdbc: JdbcTemplate,
    private val jwt: JwtService,
    private val googleTokens: GoogleTokenVerifier,
) {
    private val encoder = BCryptPasswordEncoder()
    fun login(request: LoginRequest): TokenResponse {
        val row = jdbc.query("select id, password_hash from users where email = ?", { rs, _ -> UUID.fromString(rs.getString("id")) to rs.getString("password_hash") }, request.email).firstOrNull()
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password")
        if (row.second == null || !encoder.matches(request.password, row.second)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password")
        return TokenResponse(jwt.issue(row.first), expiresInSeconds = jwt.ttlSeconds())
    }

    @Transactional
    fun googleLogin(request: GoogleLoginRequest): TokenResponse {
        val payload = googleTokens.verify(request.idToken)
        if (payload.emailVerified != true || payload.email.isNullOrBlank() || payload.subject.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google email is not verified")
        }
        val existingIdentity = jdbc.query(
            "select user_id from user_identities where provider='GOOGLE' and provider_subject=?",
            { rs, _ -> UUID.fromString(rs.getString(1)) },
            payload.subject,
        ).firstOrNull()
        val userId = existingIdentity ?: linkOrCreateGoogleUser(payload)
        return TokenResponse(jwt.issue(userId), expiresInSeconds = jwt.ttlSeconds())
    }

    private fun linkOrCreateGoogleUser(payload: GoogleIdToken.Payload): UUID {
        val existingUser = jdbc.query(
            "select id from users where email=?",
            { rs, _ -> UUID.fromString(rs.getString(1)) },
            payload.email,
        ).firstOrNull()
        val userId = existingUser ?: UUID.randomUUID().also { id ->
            val name = payload["name"]?.toString()?.take(40)?.ifBlank { null } ?: "Google Listener"
            jdbc.update(
                "insert into users(id,email,password_hash,display_name) values (?,?,null,?)",
                id, payload.email, name,
            )
            jdbc.update("insert into user_privacy_settings(user_id) values (?)", id)
        }
        jdbc.update(
            "insert into user_identities(provider,provider_subject,user_id,email_at_link) values ('GOOGLE',?,?,?)",
            payload.subject, userId, payload.email,
        )
        return userId
    }
}

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {
    @PostMapping("/login") fun login(@RequestBody request: LoginRequest) = authService.login(request)
    @PostMapping("/google") fun googleLogin(@RequestBody request: GoogleLoginRequest) = authService.googleLogin(request)
}
