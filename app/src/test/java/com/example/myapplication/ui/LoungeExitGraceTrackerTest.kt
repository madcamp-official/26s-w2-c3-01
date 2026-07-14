package com.example.myapplication.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoungeExitGraceTrackerTest {
    @Test
    fun continuousOutsidePeriodMustReachOneMinuteBeforeExit() {
        val tracker = LoungeExitGraceTracker()

        assertFalse(tracker.shouldExit(inside = false, nowMillis = 1_000L))
        assertFalse(tracker.shouldExit(inside = false, nowMillis = 60_999L))
        assertTrue(tracker.shouldExit(inside = false, nowMillis = 61_000L))
    }

    @Test
    fun returningInsideResetsTheOutsideTimer() {
        val tracker = LoungeExitGraceTracker()

        assertFalse(tracker.shouldExit(inside = false, nowMillis = 1_000L))
        assertFalse(tracker.shouldExit(inside = true, nowMillis = 40_000L))
        assertFalse(tracker.shouldExit(inside = false, nowMillis = 70_000L))
        assertFalse(tracker.shouldExit(inside = false, nowMillis = 129_999L))
        assertTrue(tracker.shouldExit(inside = false, nowMillis = 130_000L))
    }
}
