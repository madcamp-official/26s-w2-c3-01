package com.example.myapplication.ui.screens

/**
 * Keeps message entry animations tied to genuinely new, tail-appended messages.
 *
 * The tracker deliberately lives outside Compose state so a delivery-state update or a lazy-list
 * item being disposed and composed again cannot replay an entry animation. Initial history is
 * registered as already seen, and a delayed first history snapshot can also be accepted as a
 * baseline when the room metadata says that history exists.
 */
internal class ChatMessageEntryAnimationTracker(
    initialMessageIds: List<String>,
    awaitInitialHistory: Boolean,
) {
    private var previousMessageIds = initialMessageIds.distinct()
    private val seenMessageIds = previousMessageIds.toMutableSet()
    private val pendingEntryAnimations = linkedSetOf<String>()
    private var awaitingInitialHistory = awaitInitialHistory
    private var animateNextAppend = false

    /** Ensures an optimistic outgoing message still animates if history has not hydrated yet. */
    fun expectNextAppend() {
        animateNextAppend = true
    }

    /** Observes the latest ordered snapshot without replaying refreshed or batched history. */
    fun observe(messageIds: List<String>) {
        val currentMessageIds = messageIds.distinct()
        val currentMessageIdSet = currentMessageIds.toSet()
        pendingEntryAnimations.retainAll(currentMessageIdSet)

        val unseenMessageIds = currentMessageIds.filterNot(seenMessageIds::contains)
        if (unseenMessageIds.isEmpty()) {
            previousMessageIds = currentMessageIds
            return
        }
        seenMessageIds += unseenMessageIds

        if (awaitingInitialHistory && !animateNextAppend) {
            awaitingInitialHistory = false
            previousMessageIds = currentMessageIds
            return
        }

        val isSingleTailAppend = unseenMessageIds.size == 1 &&
            currentMessageIds.lastOrNull() == unseenMessageIds.single() &&
            currentMessageIds.size == previousMessageIds.size + 1 &&
            currentMessageIds.take(previousMessageIds.size) == previousMessageIds

        when {
            animateNextAppend -> pendingEntryAnimations += unseenMessageIds.last()
            isSingleTailAppend -> pendingEntryAnimations += unseenMessageIds.single()
        }

        animateNextAppend = false
        awaitingInitialHistory = false
        previousMessageIds = currentMessageIds
    }

    /** Returns true exactly once, when the corresponding lazy-list item is first composed. */
    fun claimEntryAnimation(messageId: String): Boolean =
        pendingEntryAnimations.remove(messageId)
}
