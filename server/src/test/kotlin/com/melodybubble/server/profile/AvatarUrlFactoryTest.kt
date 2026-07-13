package com.melodybubble.server.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AvatarUrlFactoryTest {
    private val factory = AvatarUrlFactory()

    @Test
    fun `creates stable thumbs avatar url`() {
        assertEquals(
            "https://api.dicebear.com/10.x/thumbs/svg?seed=user-seed",
            factory.create("user-seed"),
        )
    }

    @Test
    fun `encodes seed before adding it to url`() {
        assertEquals(
            "https://api.dicebear.com/10.x/thumbs/svg?seed=user+seed%2F1",
            factory.create("user seed/1"),
        )
    }
}
