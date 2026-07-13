package com.melodybubble.server.music

import com.fasterxml.jackson.databind.ObjectMapper
import com.melodybubble.server.safety.ActionRateLimiter
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException
import java.security.Principal
import java.time.Duration
import java.util.UUID

data class MusicSearchResult(
    val id: String,
    val title: String,
    val artistName: String,
    val artworkUrl: String?,
    val storeUrl: String?,
)

@Service
class MusicSearchService(
    restClientBuilder: RestClient.Builder,
    private val objectMapper: ObjectMapper,
    private val rateLimiter: ActionRateLimiter,
) {
    private val client = restClientBuilder.baseUrl("https://itunes.apple.com").build()

    fun search(userId: UUID, rawQuery: String): List<MusicSearchResult> {
        val query = rawQuery.trim().take(80)
        if (query.length < 2) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "검색어를 2자 이상 입력해 주세요.")
        rateLimiter.enforce(userId, "MUSIC_SEARCH", 20, Duration.ofMinutes(1))
        val body = runCatching {
            client.get().uri { builder ->
                builder.path("/search")
                    .queryParam("term", query)
                    .queryParam("country", "KR")
                    .queryParam("media", "music")
                    .queryParam("entity", "song")
                    .queryParam("limit", 12)
                    .build()
            }.retrieve().body(String::class.java).orEmpty()
        }.getOrElse {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "음악 검색 서비스에 연결하지 못했습니다.")
        }
        return objectMapper.readTree(body).path("results").mapNotNull { item ->
            val id = item.path("trackId").asText().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = item.path("trackName").asText().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val artist = item.path("artistName").asText().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            MusicSearchResult(
                id = id,
                title = title.take(160),
                artistName = artist.take(160),
                artworkUrl = item.path("artworkUrl100").asText(null),
                storeUrl = item.path("trackViewUrl").asText(null),
            )
        }.distinctBy { it.id }
    }
}

@RestController
@RequestMapping("/api/v1/music")
class MusicSearchController(private val service: MusicSearchService) {
    @GetMapping("/search")
    fun search(principal: Principal, @RequestParam query: String) =
        service.search(UUID.fromString(principal.name), query)
}
