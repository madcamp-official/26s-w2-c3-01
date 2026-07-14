package com.melodybubble.server.profile

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AvatarUrlFactoryTest {
    private val factory = AvatarUrlFactory()

    @Test
    fun `creates stable lorelei neutral avatar url`() {
        assertEquals(
            "https://api.dicebear.com/10.x/lorelei-neutral/svg?seed=user-seed",
            factory.create("user-seed"),
        )
    }

    @Test
    fun `encodes seed before adding it to url`() {
        assertEquals(
            "https://api.dicebear.com/10.x/lorelei-neutral/svg?seed=user+seed%2F1",
            factory.create("user seed/1"),
        )
    }

    @Test
    fun `creates avatar url with selected face components`() {
        val customization = AvatarCustomization(
            eyebrowsVariant = "variant13",
            eyesVariant = "variant24",
            noseVariant = "variant06",
            mouthVariant = "sad09",
            glassesVariant = "variant05",
            freckles = true,
        ).validated()

        assertEquals(
            "https://api.dicebear.com/10.x/lorelei-neutral/svg?seed=user-seed" +
                "&eyebrowsVariant=variant13&eyesVariant=variant24&noseVariant=variant06" +
                "&mouthVariant=sad09&glassesVariant=variant05&glassesProbability=100&frecklesProbability=100",
            factory.create("user-seed", customization),
        )
    }

    @Test
    fun `rejects unsupported face component`() {
        assertThrows(IllegalArgumentException::class.java) {
            AvatarCustomization(
                eyebrowsVariant = "variant99",
                eyesVariant = "variant01",
                noseVariant = "variant01",
                mouthVariant = "happy01",
            ).validated()
        }
    }
}
