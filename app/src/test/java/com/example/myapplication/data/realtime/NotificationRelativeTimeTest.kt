package com.example.myapplication.data.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NotificationRelativeTimeTest {
    @Test
    fun formatsRecentHoursAndDays() {
        val now = 10L * 24L * 60L * 60L * 1_000L

        assertEquals("최근", notificationRelativeTime(now - 59L * 60L * 1_000L, now))
        assertEquals("1시간 전", notificationRelativeTime(now - 60L * 60L * 1_000L, now))
        assertEquals("23시간 전", notificationRelativeTime(now - 23L * 60L * 60L * 1_000L, now))
        assertEquals("1일 전", notificationRelativeTime(now - 24L * 60L * 60L * 1_000L, now))
        assertEquals("3일 전", notificationRelativeTime(now - 72L * 60L * 60L * 1_000L, now))
    }

    @Test
    fun parsesServerTimestampsWithNanoseconds() {
        assertNotNull("2026-07-14T03:24:15.123456789Z".toServerEpochMillis())
        assertNotNull("2026-07-14T12:24:15+09:00".toServerEpochMillis())
    }

    @Test
    fun treatsLateSyncedNotificationsBeforeLastViewAsRead() {
        val lastViewedAt = 10_000L

        assertEquals(true, notificationWasAlreadyRead(9_999L, lastViewedAt))
        assertEquals(true, notificationWasAlreadyRead(10_000L, lastViewedAt))
        assertEquals(false, notificationWasAlreadyRead(10_001L, lastViewedAt))
        assertEquals(false, notificationWasAlreadyRead(1L, 0L))
    }
}
