package com.example.myapplication.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvatarProfileResolverTest {
    @Test
    fun missingRemoteAvatarFallsBackToStableIdentity() {
        val avatar = AvatarProfileResolver.resolve(
            remoteSeed = null,
            remoteUrl = null,
            stableIdentity = "listener one",
            fallbackSeed = "cached-seed",
        )

        assertEquals("listener one", avatar.seed)
        assertEquals(
            "https://api.dicebear.com/10.x/lorelei-neutral/svg?seed=listener%20one",
            avatar.url,
        )
    }

    @Test
    fun completeRemoteAvatarIsPreserved() {
        val avatar = AvatarProfileResolver.resolve(
            remoteSeed = "server-seed",
            remoteUrl = "https://example.com/avatar.svg",
            stableIdentity = "listener-one",
            fallbackSeed = "cached-seed",
        )

        assertEquals("server-seed", avatar.seed)
        assertEquals("https://example.com/avatar.svg", avatar.url)
    }

    @Test
    fun legacyDiceBearAvatarIsRestyledWithTheResolvedSeed() {
        val avatar = AvatarProfileResolver.resolve(
            remoteSeed = "server-seed",
            remoteUrl = "https://api.dicebear.com/10.x/thumbs/svg?seed=old-seed",
            stableIdentity = "listener-one",
            fallbackSeed = "cached-seed",
        )

        assertEquals("server-seed", avatar.seed)
        assertEquals(
            "https://api.dicebear.com/10.x/lorelei-neutral/svg?seed=server-seed",
            avatar.url,
        )
    }

    @Test
    fun diceBearUrlSeedIsPreservedWhenTheCallerOnlyHasTheUrl() {
        val avatar = AvatarProfileResolver.resolve(
            remoteSeed = null,
            remoteUrl = "https://api.dicebear.com/10.x/lorelei-neutral/svg?seed=fresh%20avatar",
            stableIdentity = null,
            fallbackSeed = "profile-name",
        )

        assertEquals("fresh avatar", avatar.seed)
        assertEquals(
            "https://api.dicebear.com/10.x/lorelei-neutral/svg?seed=fresh%20avatar",
            avatar.url,
        )
    }

    @Test
    fun customizedLoreleiNeutralAvatarIsPreserved() {
        val url = "https://api.dicebear.com/10.x/lorelei-neutral/svg?seed=server-seed" +
            "&eyebrowsVariant=variant13&eyesVariant=variant24&noseVariant=variant06" +
            "&mouthVariant=sad09&glassesVariant=variant05&glassesProbability=100&frecklesProbability=100"

        val avatar = AvatarProfileResolver.resolve(
            remoteSeed = "server-seed",
            remoteUrl = url,
            stableIdentity = null,
            fallbackSeed = "cached-seed",
        )

        assertEquals(url, avatar.url)
        assertEquals(
            AvatarCustomization("variant13", "variant24", "variant06", "sad09", "variant05", true),
            AvatarProfileResolver.customizationFrom(avatar.url),
        )
    }

    @Test
    fun seedOnlyUrlDoesNotPretendTheCurrentAvatarUsesDefaultParts() {
        assertNull(
            AvatarProfileResolver.explicitCustomizationFrom(
                "https://api.dicebear.com/10.x/lorelei-neutral/svg?seed=existing-avatar",
            ),
        )
    }

    @Test
    fun renderedSvgRestoresTheExactVisibleAvatarParts() {
        val svg = """
            <svg>
              <defs>
                <g id="eyebrows-variant13-a1"><path /></g>
                <g id="eyes-variant24-a1"><path /></g>
                <g id="freckles-variant01-a1"><path /></g>
                <g id="glasses-variant05-a1"><path /></g>
                <g id="mouth-sad09-a1"><path /></g>
                <g id="nose-variant06-a1"><path /></g>
              </defs>
            </svg>
        """.trimIndent()

        assertEquals(
            AvatarCustomization("variant13", "variant24", "variant06", "sad09", "variant05", true),
            AvatarProfileResolver.customizationFromSvg(svg),
        )
    }

    @Test
    fun explicitZeroProbabilityDoesNotEnableGlasses() {
        val url = "https://api.dicebear.com/10.x/lorelei-neutral/svg?seed=server-seed" +
            "&eyebrowsVariant=variant13&eyesVariant=variant24&noseVariant=variant06" +
            "&mouthVariant=sad09&glassesVariant=variant05&glassesProbability=0&frecklesProbability=0"

        assertEquals(
            AvatarCustomization("variant13", "variant24", "variant06", "sad09", null, false),
            AvatarProfileResolver.explicitCustomizationFrom(url),
        )
    }

    @Test
    fun customizedUrlDisablesOptionalComponentsWhenNotSelected() {
        assertEquals(
            "https://api.dicebear.com/10.x/lorelei-neutral/svg?seed=user%20seed" +
                "&eyebrowsVariant=variant01&eyesVariant=variant01&noseVariant=variant01" +
                "&mouthVariant=happy01&glassesProbability=0&frecklesProbability=0",
            AvatarProfileResolver.customizedUrl("user seed", AvatarCustomization()),
        )
    }

    @Test
    fun blankRemoteValuesUseCachedSeed() {
        val avatar = AvatarProfileResolver.resolve(
            remoteSeed = " ",
            remoteUrl = "",
            stableIdentity = null,
            fallbackSeed = "cached-seed",
        )

        assertEquals("cached-seed", avatar.seed)
        assertEquals(
            "https://api.dicebear.com/10.x/lorelei-neutral/svg?seed=cached-seed",
            avatar.url,
        )
    }
}
