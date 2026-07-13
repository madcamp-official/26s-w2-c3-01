package com.melodybubble.server.nearby

import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.security.Principal
import java.util.UUID

data class PresenceSettings(
    val discoverabilityScope: String,
    val musicVisibility: String,
    val discoveryRadiusMeters: Int,
    val allowReactions: Boolean,
)

data class PresenceSettingsUpdate(
    val discoverabilityScope: String,
    val musicVisibility: String,
    val discoveryRadiusMeters: Int,
    val allowReactions: Boolean,
)

@RestController
@RequestMapping("/api/v1/me/presence-settings")
class PresenceSettingsController(
    private val jdbc: JdbcTemplate,
    private val nearby: NearbyService,
) {
    @GetMapping
    fun settings(principal: Principal) = load(UUID.fromString(principal.name))

    @PutMapping
    @Transactional
    fun update(principal: Principal, @RequestBody request: PresenceSettingsUpdate): PresenceSettings {
        val userId = UUID.fromString(principal.name)
        val previousAudience = nearby.musicAudienceSnapshot(userId)
        val discoverability = request.discoverabilityScope.trim().uppercase()
        val musicVisibility = request.musicVisibility.trim().uppercase()
        if (discoverability !in DISCOVERABILITY_SCOPES || musicVisibility !in MUSIC_VISIBILITIES) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 공개 범위입니다.")
        }
        jdbc.update(
            """
            insert into user_privacy_settings(
              user_id,discoverable,share_music,discoverability_scope,music_visibility,
              discovery_radius_meters,allow_reactions
            ) values (?,?,?,?,?,?,?)
            on conflict(user_id) do update set
              discoverable=excluded.discoverable,share_music=excluded.share_music,
              discoverability_scope=excluded.discoverability_scope,
              music_visibility=excluded.music_visibility,
              discovery_radius_meters=excluded.discovery_radius_meters,
              allow_reactions=excluded.allow_reactions,updated_at=now()
            """.trimIndent(),
            userId,
            discoverability != "HIDDEN",
            musicVisibility != "HIDDEN",
            discoverability,
            musicVisibility,
            NEARBY_RADIUS_METERS,
            request.allowReactions,
        )
        nearby.publishPrivacyAudienceChangesAfterCommit(userId, previousAudience)
        return load(userId)
    }

    private fun load(userId: UUID): PresenceSettings = jdbc.query(
        """
        select discoverability_scope,music_visibility,discovery_radius_meters,allow_reactions
        from user_privacy_settings where user_id=?
        """.trimIndent(),
        { rs, _ -> PresenceSettings(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getBoolean(4)) },
        userId,
    ).firstOrNull() ?: PresenceSettings("NEARBY", "TITLE_ARTIST", NEARBY_RADIUS_METERS, true)

    companion object {
        private val DISCOVERABILITY_SCOPES = setOf("NEARBY", "MUTUALS", "HIDDEN")
        private val MUSIC_VISIBILITIES = setOf("TITLE_ARTIST", "MUTUALS", "HIDDEN")
    }
}
