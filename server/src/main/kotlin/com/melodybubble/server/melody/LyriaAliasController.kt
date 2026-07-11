package com.melodybubble.server.melody

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/lyria")
class LyriaAliasController(private val service: LyriaMusicService) {
    @PostMapping("/generate")
    fun generate(@RequestBody request: LyriaAliasGenerateRequest): LyriaGenerateResponse =
        service.generateAlias(request)
}
