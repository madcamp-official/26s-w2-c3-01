package com.melodybubble.server.auth

import com.melodybubble.server.realtime.RealtimeSessionPolicy
import com.melodybubble.server.profile.ProfileMediaStorage
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.server.ResponseStatusException

class AuthValidationTest {
    private val service = AuthService(
        mock(JdbcTemplate::class.java),
        mock(JwtService::class.java),
        mock(GoogleTokenVerifier::class.java),
        mock(RealtimeSessionPolicy::class.java),
        mock(ProfileMediaStorage::class.java),
    )

    @Test
    fun `rejects malformed signup email`() {
        assertThrows(ResponseStatusException::class.java) {
            service.signup(SignupRequest("not-an-email", "password1", "password1", "멜로디"))
        }
    }

    @Test
    fun `rejects weak signup password`() {
        assertThrows(ResponseStatusException::class.java) {
            service.signup(SignupRequest("listener@example.com", "password", "password", "멜로디"))
        }
    }

    @Test
    fun `rejects mismatched password confirmation`() {
        assertThrows(ResponseStatusException::class.java) {
            service.signup(SignupRequest("listener@example.com", "password1", "password2", "멜로디"))
        }
    }
}
