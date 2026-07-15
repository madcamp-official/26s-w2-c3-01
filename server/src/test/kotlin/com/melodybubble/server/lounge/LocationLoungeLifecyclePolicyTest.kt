package com.melodybubble.server.lounge

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class LocationLoungeLifecyclePolicyTest {
    @Test
    fun `outside presence is retained for one continuous minute`() {
        val outsideSince = Instant.parse("2026-07-14T00:00:00Z")

        assertThat(LocationLoungePolicy.shouldRetainPresence(false, null, outsideSince)).isTrue()
        assertThat(
            LocationLoungePolicy.shouldRetainPresence(false, outsideSince, outsideSince.plusSeconds(59)),
        ).isTrue()
        assertThat(
            LocationLoungePolicy.shouldRetainPresence(false, outsideSince, outsideSince.plusSeconds(60)),
        ).isFalse()
        assertThat(
            LocationLoungePolicy.shouldRetainPresence(true, outsideSince, outsideSince.plusSeconds(600)),
        ).isTrue()
    }

    @Test
    fun `creation is allowed outside every lounge`() {
        assertThat(LocationLoungePolicy.canCreateLounge(0)).isTrue()
    }

    @Test
    fun `creation is rejected inside one or multiple lounges`() {
        assertThat(LocationLoungePolicy.canCreateLounge(1)).isFalse()
        assertThat(LocationLoungePolicy.canCreateLounge(3)).isFalse()
    }

    @Test
    fun `fresh location enters and leaving radius removes user`() {
        assertThat(LocationLoungePolicy.isIncluded(4.999, 5, locationFresh = true)).isTrue()
        assertThat(LocationLoungePolicy.isIncluded(5.001, 5, locationFresh = true)).isFalse()
    }

    @Test
    fun `stale location is excluded even when coordinates are inside`() {
        assertThat(LocationLoungePolicy.isIncluded(0.0, 20, locationFresh = false)).isFalse()
    }

    @Test
    fun `one user may be included by more than one overlapping lounge`() {
        val included = listOf(4.0 to 5, 9.0 to 10).count { (distance, radius) ->
            LocationLoungePolicy.isIncluded(distance, radius, locationFresh = true)
        }
        assertThat(included).isEqualTo(2)
    }

    @Test
    fun `normal chat room creation stops at five and resumes below five`() {
        assertThat(LocationLoungePolicy.canCreateChatRoom(4, LocationLoungeStatus.ACTIVE)).isTrue()
        assertThat(LocationLoungePolicy.canCreateChatRoom(5, LocationLoungeStatus.ACTIVE)).isFalse()
        assertThat(LocationLoungePolicy.canCreateChatRoom(7, LocationLoungeStatus.ACTIVE)).isFalse()
    }

    @Test
    fun `merging and deleting lounges reject new chat rooms`() {
        assertThat(LocationLoungePolicy.canCreateChatRoom(0, LocationLoungeStatus.MERGING)).isFalse()
        assertThat(LocationLoungePolicy.canCreateChatRoom(0, LocationLoungeStatus.DELETING)).isFalse()
        assertThat(LocationLoungePolicy.canCreateChatRoom(0, LocationLoungeStatus.DELETED)).isFalse()
    }

    @Test
    fun `only room owner can delete room`() {
        val owner = UUID.randomUUID()
        assertThat(LocationLoungePolicy.canDeleteChatRoom(owner, owner)).isTrue()
        assertThat(LocationLoungePolicy.canDeleteChatRoom(owner, UUID.randomUUID())).isFalse()
    }

    @Test
    fun `grace period protects underpopulated lounge for first three minutes`() {
        val created = Instant.parse("2026-07-14T00:00:00Z")
        assertThat(
            LocationLoungePolicy.shouldAutoDelete(1, created, created.plusSeconds(179), LocationLoungeStatus.ACTIVE),
        ).isFalse()
    }

    @Test
    fun `underpopulated lounge deletes at and after grace boundary`() {
        val created = Instant.parse("2026-07-14T00:00:00Z")
        assertThat(
            LocationLoungePolicy.shouldAutoDelete(2, created, created.plusSeconds(180), LocationLoungeStatus.ACTIVE),
        ).isTrue()
        assertThat(
            LocationLoungePolicy.shouldAutoDelete(0, created, created.plusSeconds(600), LocationLoungeStatus.ACTIVE),
        ).isTrue()
    }

    @Test
    fun `merge and delete states cannot be independently auto deleted`() {
        val created = Instant.EPOCH
        assertThat(
            LocationLoungePolicy.shouldAutoDelete(0, created, created.plusSeconds(600), LocationLoungeStatus.MERGING),
        ).isFalse()
        assertThat(
            LocationLoungePolicy.shouldAutoDelete(0, created, created.plusSeconds(600), LocationLoungeStatus.DELETING),
        ).isFalse()
    }
}
