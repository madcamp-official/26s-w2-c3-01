package com.melodybubble.server.melody

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/ops/api/lyria")
class LyriaMusicController(private val service: LyriaMusicService) {
    @PostMapping("/generate")
    fun generate(@RequestBody request: LyriaGenerateRequest): LyriaGenerateResponse =
        service.generate(request)
}
