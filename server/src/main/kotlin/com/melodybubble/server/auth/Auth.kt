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
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.Principal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

data class LoginRequest(val email: String, val password: String)
data class SignupRequest(val email: String, val password: String, val displayName: String)
data class GoogleLoginRequest(val idToken: String)
data class RefreshRequest(val refreshToken: String)
data class LogoutRequest(val refreshToken: String?)
data class OnboardingRequest(
    val acceptedTerms: Boolean,
    val termsVersion: String,
    val genres: List<String>,
    val moods: List<String>,
)
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresInSeconds: Long,
    val isNewUser: Boolean = false,
    val onboardingComplete: Boolean = false,
)

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
    init {
        require(webClientId.isNotBlank()) { "GOOGLE_WEB_CLIENT_ID must be configured" }
    }
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
    private val refreshDays = 30L

    private fun tokenHash(token: String): String = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun issueSession(userId: UUID, isNewUser: Boolean = false): TokenResponse {
        val refreshToken = "${UUID.randomUUID()}.${UUID.randomUUID()}"
        jdbc.update(
            "insert into refresh_tokens(id,user_id,token_hash,expires_at) values (?,?,?,?)",
            UUID.randomUUID(), userId, tokenHash(refreshToken),
            java.sql.Timestamp.from(Instant.now().plus(refreshDays, ChronoUnit.DAYS)),
        )
        val completed = jdbc.queryForObject(
            "select onboarding_completed from users where id=?", Boolean::class.java, userId,
        ) ?: false
        return TokenResponse(jwt.issue(userId), refreshToken, expiresInSeconds = jwt.ttlSeconds(),
            isNewUser = isNewUser, onboardingComplete = completed)
    }
    fun login(request: LoginRequest): TokenResponse {
        val row = jdbc.query("select id, password_hash from users where email = ?", { rs, _ -> UUID.fromString(rs.getString("id")) to rs.getString("password_hash") }, request.email).firstOrNull()
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password")
        if (row.second == null || !encoder.matches(request.password, row.second)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password")
        return issueSession(row.first)
    }

    @Transactional
    fun signup(request: SignupRequest): TokenResponse {
        val email = request.email.trim().lowercase()
        val displayName = request.displayName.trim()
        if (email.isBlank() || request.password.length < 6 || displayName.length !in 2..40) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Email, password (6+), and display name are required")
        }
        val userId = UUID.randomUUID()
        val inserted = jdbc.update(
            "insert into users(id,email,password_hash,display_name) values (?,?,?,?) on conflict(email) do nothing",
            userId, email, encoder.encode(request.password), displayName,
        )
        if (inserted != 1) throw ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered")
        jdbc.update("insert into user_privacy_settings(user_id) values (?)", userId)
        return issueSession(userId, isNewUser = true)
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
        val (userId, isNewUser) = existingIdentity?.let { it to false }
            ?: linkOrCreateGoogleUser(payload)
        return issueSession(userId, isNewUser)
    }

    @Transactional
    fun refresh(request: RefreshRequest): TokenResponse {
        val hash = tokenHash(request.refreshToken)
        val userId = jdbc.query(
            "select user_id from refresh_tokens where token_hash=? and revoked_at is null and expires_at>now()",
            { rs, _ -> UUID.fromString(rs.getString(1)) }, hash,
        ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid")
        jdbc.update("update refresh_tokens set revoked_at=now() where token_hash=?", hash)
        return issueSession(userId)
    }

    @Transactional
    fun logout(userId: UUID, request: LogoutRequest) {
        if (request.refreshToken.isNullOrBlank()) {
            jdbc.update("update refresh_tokens set revoked_at=now() where user_id=? and revoked_at is null", userId)
        } else {
            jdbc.update("update refresh_tokens set revoked_at=now() where user_id=? and token_hash=?", userId, tokenHash(request.refreshToken))
        }
    }

    @Transactional
    fun completeOnboarding(userId: UUID, request: OnboardingRequest) {
        if (!request.acceptedTerms || request.termsVersion.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Terms must be accepted")
        }
        val genres = request.genres.map(String::trim).filter(String::isNotBlank).distinct().take(8)
        val moods = request.moods.map(String::trim).filter(String::isNotBlank).distinct().take(8)
        if (genres.isEmpty() || moods.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one genre and mood are required")
        }
        jdbc.update("""update users set onboarding_completed=true,terms_accepted_at=now(),terms_version=?,
            preferred_genres=?,mood_tags=?,updated_at=now() where id=?""",
            request.termsVersion.take(32), genres.joinToString(","), moods.joinToString(","), userId)
    }

    @Transactional
    fun deleteAccount(userId: UUID) {
        jdbc.update("delete from users where id=?", userId)
    }

    private fun linkOrCreateGoogleUser(payload: GoogleIdToken.Payload): Pair<UUID, Boolean> {
        val existingUser = jdbc.query(
            "select id from users where email=?",
            { rs, _ -> UUID.fromString(rs.getString(1)) },
            payload.email,
        ).firstOrNull()
        val candidateId = UUID.randomUUID()
        val created = if (existingUser == null) {
            val name = payload["name"]?.toString()?.take(40)?.ifBlank { null } ?: "Google Listener"
            jdbc.update(
                "insert into users(id,email,password_hash,display_name) values (?,?,null,?) on conflict (email) do nothing",
                candidateId, payload.email, name,
            ) == 1
        } else false
        val userId = if (created) candidateId else existingUser ?: jdbc.query(
            "select id from users where email=?",
            { rs, _ -> UUID.fromString(rs.getString(1)) },
            payload.email,
        ).first()
        jdbc.update(
            "insert into user_privacy_settings(user_id) values (?) on conflict (user_id) do nothing",
            userId,
        )
        jdbc.update(
            "insert into user_identities(provider,provider_subject,user_id,email_at_link) values ('GOOGLE',?,?,?) on conflict do nothing",
            payload.subject, userId, payload.email,
        )
        val linkedUserId = jdbc.query(
            "select user_id from user_identities where provider='GOOGLE' and provider_subject=?",
            { rs, _ -> UUID.fromString(rs.getString(1)) },
            payload.subject,
        ).first()
        return linkedUserId to created
    }
}

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {
    @PostMapping("/login") fun login(@RequestBody request: LoginRequest) = authService.login(request)
    @PostMapping("/signup") fun signup(@RequestBody request: SignupRequest) = authService.signup(request)
    @PostMapping("/google") fun googleLogin(@RequestBody request: GoogleLoginRequest) = authService.googleLogin(request)
    @PostMapping("/refresh") fun refresh(@RequestBody request: RefreshRequest) = authService.refresh(request)
    @PostMapping("/logout") fun logout(principal: Principal, @RequestBody request: LogoutRequest) =
        authService.logout(UUID.fromString(principal.name), request)
    @PutMapping("/onboarding") fun onboarding(principal: Principal, @RequestBody request: OnboardingRequest) =
        authService.completeOnboarding(UUID.fromString(principal.name), request)
    @DeleteMapping("/account") fun deleteAccount(principal: Principal) =
        authService.deleteAccount(UUID.fromString(principal.name))
}
