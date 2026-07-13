package com.example.myapplication

import com.example.myapplication.core.model.SharingState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharingServiceReconciliationTest {
    @Test
    fun waitsForOnlineSessionBeforeResumingPresence() {
        assertFalse(shouldResumeSharing(true, false, SharingState.STOPPED))
        assertTrue(shouldResumeSharing(true, true, SharingState.STOPPED))
    }

    @Test
    fun doesNotStartDuplicatePresenceJob() {
        assertFalse(shouldResumeSharing(true, true, SharingState.STARTING))
        assertFalse(shouldResumeSharing(true, true, SharingState.ACTIVE))
    }

    @Test
    fun doesNotStartWhenForegroundServiceIsStopped() {
        assertFalse(shouldResumeSharing(false, true, SharingState.STOPPED))
    }
}
