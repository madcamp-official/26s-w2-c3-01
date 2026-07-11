package com.melodybubble.server.melody

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class MelodyAliasPromptPreviewResponse(val prompt: String)

@RestController
@RequestMapping("/internal/ops/api/melody-alias", "/api/v1/melody-alias")
class MelodyAliasController(private val service: OpenAiMelodyAliasService) {
    @PostMapping("/generate")
    fun generate(@RequestBody request: MelodyAliasGenerateRequest): MelodyAliasGenerateResponse =
        service.generate(request)

    @PostMapping("/prompt-preview")
    fun promptPreview(@RequestBody request: MelodyAliasGenerateRequest): MelodyAliasPromptPreviewResponse =
        MelodyAliasPromptPreviewResponse(service.buildPromptPreview(request))

    @GetMapping("/options")
    fun options() = mapOf(
        "moods" to listOf("dreamy", "bright", "calm", "playful", "energetic", "mysterious"),
        "tones" to listOf("전자음", "피아노", "기타", "벨", "오르골", "신스패드"),
        "energies" to listOf("soft", "medium", "bouncy", "sharp", "warm"),
        "tempoRanges" to listOf("80-100 BPM", "90-120 BPM", "110-140 BPM", "130-160 BPM"),
        "vibeTags" to listOf("cozy", "light", "friendly", "clear", "cute", "clean", "sparkly", "minimal"),
    )
}
