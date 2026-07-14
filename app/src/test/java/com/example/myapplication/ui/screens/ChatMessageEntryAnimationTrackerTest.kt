package com.example.myapplication.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageEntryAnimationTrackerTest {
    @Test
    fun initialHistoryNeverClaimsAnEntryAnimation() {
        val tracker = tracker(initialMessageIds = listOf("history-1", "history-2"))

        tracker.observe(listOf("history-1", "history-2"))

        assertFalse(tracker.claimEntryAnimation("history-1"))
        assertFalse(tracker.claimEntryAnimation("history-2"))
    }

    @Test
    fun newlyAppendedOutgoingAndIncomingMessagesAnimateExactlyOnce() {
        val tracker = tracker(initialMessageIds = listOf("history"))

        tracker.expectNextAppend()
        tracker.observe(listOf("history", "outgoing"))
        assertTrue(tracker.claimEntryAnimation("outgoing"))
        assertFalse(tracker.claimEntryAnimation("outgoing"))

        tracker.observe(listOf("history", "outgoing", "incoming"))
        assertTrue(tracker.claimEntryAnimation("incoming"))
        assertFalse(tracker.claimEntryAnimation("incoming"))
    }

    @Test
    fun delayedHistoryHydrationBecomesBaselineWithoutAnimating() {
        val tracker = tracker(
            initialMessageIds = emptyList(),
            awaitInitialHistory = true,
        )

        tracker.observe(listOf("history-1", "history-2"))

        assertFalse(tracker.claimEntryAnimation("history-1"))
        assertFalse(tracker.claimEntryAnimation("history-2"))

        tracker.observe(listOf("history-1", "history-2", "incoming"))
        assertTrue(tracker.claimEntryAnimation("incoming"))
    }

    @Test
    fun optimisticOutgoingMessageCanAnimateBeforeHistoryHydrates() {
        val tracker = tracker(
            initialMessageIds = emptyList(),
            awaitInitialHistory = true,
        )

        tracker.expectNextAppend()
        tracker.observe(listOf("outgoing"))

        assertTrue(tracker.claimEntryAnimation("outgoing"))
    }

    @Test
    fun deliveryUpdatesAndReinsertedMessagesDoNotReplayAnimation() {
        val tracker = tracker(initialMessageIds = listOf("history"))

        tracker.observe(listOf("history", "message"))
        assertTrue(tracker.claimEntryAnimation("message"))

        tracker.observe(listOf("history", "message"))
        assertFalse(tracker.claimEntryAnimation("message"))

        tracker.observe(listOf("history"))
        tracker.observe(listOf("history", "message"))
        assertFalse(tracker.claimEntryAnimation("message"))
    }

    @Test
    fun refreshedMessageBatchDoesNotAnimateAsLiveMessages() {
        val tracker = tracker(initialMessageIds = listOf("history-1"))

        tracker.observe(listOf("history-1", "history-2", "history-3"))

        assertFalse(tracker.claimEntryAnimation("history-2"))
        assertFalse(tracker.claimEntryAnimation("history-3"))
    }

    private fun tracker(
        initialMessageIds: List<String>,
        awaitInitialHistory: Boolean = false,
    ) = ChatMessageEntryAnimationTracker(
        initialMessageIds = initialMessageIds,
        awaitInitialHistory = awaitInitialHistory,
    )
}
