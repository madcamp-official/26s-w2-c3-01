package com.melodybubble.server.melody

import com.melodybubble.server.profile.ProfileMediaStorage
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import java.util.UUID

@RestController
@RequestMapping("/api/v1/lyria")
class LyriaAliasController(
    private val service: LyriaMusicService,
    private val media: ProfileMediaStorage,
) {
    @PostMapping("/generate")
    fun generate(principal: Principal, @RequestBody request: LyriaAliasGenerateRequest): LyriaGenerateResponse {
        val generated = service.generateAlias(request)
        val candidate = media.storeCandidate(UUID.fromString(principal.name), generated.audioBase64, generated.mimeType)
        return generated.copy(candidateKey = candidate.key)
    }
}
