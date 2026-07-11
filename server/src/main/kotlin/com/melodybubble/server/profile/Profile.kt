package com.melodybubble.server.profile

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import java.util.UUID

data class ProfileResponse(
    val displayName: String,
    val profileColor: String,
    val bio: String,
    val avatarDataUrl: String?,
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

@RestController
@RequestMapping("/api/v1/me")
class ProfileController(private val jdbc: JdbcTemplate) {
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

    @PutMapping("/privacy") fun privacy(principal: Principal, @RequestBody request: PrivacyUpdate): ProfileResponse {
        jdbc.update("""insert into user_privacy_settings(user_id,discoverable,share_music) values (?,?,?)
            on conflict(user_id) do update set discoverable=excluded.discoverable,share_music=excluded.share_music,updated_at=now()""",
            UUID.fromString(principal.name), request.discoverable, request.shareMusic)
        return load(UUID.fromString(principal.name))
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
