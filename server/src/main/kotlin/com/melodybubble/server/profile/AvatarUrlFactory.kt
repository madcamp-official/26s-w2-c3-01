package com.melodybubble.server.profile

import org.springframework.stereotype.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Component
class AvatarUrlFactory {
    fun create(seed: String): String =
        "$DICEBEAR_BASE_URL?seed=${URLEncoder.encode(seed, StandardCharsets.UTF_8)}"

    private companion object {
        const val DICEBEAR_BASE_URL = "https://api.dicebear.com/10.x/thumbs/svg"
    }
}
