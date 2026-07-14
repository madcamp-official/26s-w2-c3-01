package com.example.myapplication.data

import org.junit.Assert.assertEquals
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
