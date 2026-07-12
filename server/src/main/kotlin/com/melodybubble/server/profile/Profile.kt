package com.melodybubble.server.profile

import com.melodybubble.server.nearby.NearbyService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import java.util.UUID
import java.time.Instant

data class ProfileResponse(
    val displayName: String,
    val profileColor: String,
    val bio: String,
    val avatarDataUrl: String?,
    val profileMusicUrl: String?,
    val profileMusicDescription: String?,
    val profileMusicUpdatedAt: Instant?,
    val genres: List<String>,
    val moods: List<String>,
    val discoverable: Boolean,
    val shareMusic: Boolean,
)
data class ProfileUpdate(
    val displayName: String,
    val profileColor: String,
    val bio: String = "",
    val avatarDataUrl: String? = null,
    val genres: List<String> = emptyList(),
    val moods: List<String> = emptyList(),
)
data class PrivacyUpdate(val discoverable: Boolean, val shareMusic: Boolean)
data class ProfileMusicUpdate(val candidateKey: String, val description: String? = null)

@RestController
@RequestMapping("/api/v1/me")
class ProfileController(
    private val jdbc: JdbcTemplate,
    private val nearby: NearbyService,
    private val media: ProfileMediaStorage,
) {
    @GetMapping fun me(principal: Principal): ProfileResponse = load(UUID.fromString(principal.name))

    @PatchMapping fun update(principal: Principal, @RequestBody request: ProfileUpdate): ProfileResponse {
        val name = request.displayName.trim().take(40)
        val color = request.profileColor.takeIf { it.matches(Regex("#[0-9A-Fa-f]{6}")) } ?: "#6750A4"
        require(name.length >= 2) { "Display name must contain at least 2 characters" }
        val avatar = request.avatarDataUrl?.takeIf { it.startsWith("data:image/") && it.length <= 700_000 }
        jdbc.update("""update users set display_name=?,profile_color=?,bio=?,avatar_data_url=?,
            preferred_genres=?,mood_tags=?,updated_at=now() where id=?""", name, color,
            request.bio.trim().take(160), avatar, request.genres.take(8).joinToString(","),
            request.moods.take(8).joinToString(","), UUID.fromString(principal.name))
        return load(UUID.fromString(principal.name))
    }

    @PutMapping("/privacy")
    @Transactional
    fun privacy(principal: Principal, @RequestBody request: PrivacyUpdate): ProfileResponse {
        val userId = UUID.fromString(principal.name)
        val previousAudience = nearby.musicAudienceSnapshot(userId)
        jdbc.update("""insert into user_privacy_settings(user_id,discoverable,share_music) values (?,?,?)
            on conflict(user_id) do update set discoverable=excluded.discoverable,share_music=excluded.share_music,updated_at=now()""",
            userId, request.discoverable, request.shareMusic)
        jdbc.update(
            """
            update user_privacy_settings set
              discoverability_scope=case
                when ? then case when discoverability_scope='HIDDEN' then 'NEARBY' else discoverability_scope end
                else 'HIDDEN' end,
              music_visibility=case
                when ? then case when music_visibility='HIDDEN' then 'TITLE_ARTIST' else music_visibility end
                else 'HIDDEN' end,
              updated_at=now()
            where user_id=?
            """.trimIndent(),
            request.discoverable,
            request.shareMusic,
            userId,
        )
        nearby.publishPrivacyAudienceChangesAfterCommit(userId, previousAudience)
        return load(userId)
    }

    private fun load(userId: UUID): ProfileResponse = jdbc.query("""select u.display_name,u.profile_color,u.bio,
        u.avatar_data_url,u.preferred_genres,u.mood_tags,
        coalesce(p.discoverable,true) discoverable,coalesce(p.share_music,true) share_music
        from users u left join user_privacy_settings p on p.user_id=u.id where u.id=?""", { rs, _ ->
        ProfileResponse(
            rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
            rs.getString(5).orEmpty().split(',').filter(String::isNotBlank),
            rs.getString(6).orEmpty().split(',').filter(String::isNotBlank),
            rs.getBoolean(7), rs.getBoolean(8),
        )
    }, userId).first()
}
