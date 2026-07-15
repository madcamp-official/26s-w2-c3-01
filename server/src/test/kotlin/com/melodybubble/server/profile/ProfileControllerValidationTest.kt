package com.melodybubble.server.profile

import com.melodybubble.server.nearby.NearbyService
import com.melodybubble.server.safety.ActionRateLimiter
import com.melodybubble.server.taste.TasteMatchService
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.jdbc.core.JdbcTemplate
import java.security.Principal
import java.util.UUID

class ProfileControllerValidationTest {
    private val controller = ProfileController(
        mock(JdbcTemplate::class.java),
        mock(NearbyService::class.java),
        mock(ActionRateLimiter::class.java),
        mock(ProfileQueryService::class.java),
        mock(AvatarUrlFactory::class.java),
        mock(TasteMatchService::class.java),
    )
    private val principal = Principal { UUID.randomUUID().toString() }

    @Test
    fun `rejects more than three signature tracks`() {
        val tracks = (1..4).map { rank ->
            ProfileTrack(rank = rank, title = "track-$rank", artist = "artist-$rank")
        }

        assertThrows(IllegalArgumentException::class.java) {
            controller.curation(principal, ProfileCurationUpdate(signatureTracks = tracks))
        }
    }

    @Test
    fun `rejects unknown profile privacy scope`() {
        assertThrows(IllegalArgumentException::class.java) {
            controller.profilePrivacy(
                principal,
                ProfilePrivacyUpdate(currentMusicVisibility = "FRIENDS_OF_FRIENDS"),
            )
        }
    }
}
