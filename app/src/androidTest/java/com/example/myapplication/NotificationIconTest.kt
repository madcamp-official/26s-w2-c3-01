package com.example.myapplication

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.realtime.RealtimeDestinations
import com.example.myapplication.data.realtime.RealtimeEvent
import com.example.myapplication.data.realtime.RealtimeEventEnvelope
import com.example.myapplication.data.realtime.RealtimeNotificationPayload
import com.example.myapplication.data.realtime.RealtimeSystemNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationIconTest {
    @Test
    fun realtimeNotificationUsesSharedPaddedSyncIcon() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = context.getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        manager.cancelAll()

        try {
            RealtimeSystemNotifier(context).present(
                RealtimeEvent.NotificationCreated(
                    destination = RealtimeDestinations.NOTIFICATIONS,
                    envelope = RealtimeEventEnvelope(
                        eventId = TEST_EVENT_ID,
                        type = "notification.created",
                        version = 1,
                        timestamp = "2026-07-14T00:00:00Z",
                        payload = RealtimeNotificationPayload(
                            notificationId = TEST_EVENT_ID,
                            title = "Sync",
                            body = "notification icon verification",
                        ),
                    ),
                ),
            )

            val posted = waitForNotification(manager)
            assertNotNull("Realtime notification was not posted", posted)
            assertEquals(R.drawable.ic_notification_sync, posted?.notification?.smallIcon?.resId)
            assertEquals(context.getColor(R.color.sync_violet_glow), posted?.notification?.color)
        } finally {
            manager.cancelAll()
        }
    }

    private fun waitForNotification(manager: NotificationManager) = run {
        repeat(20) {
            manager.activeNotifications.firstOrNull()?.let { return@run it }
            Thread.sleep(50)
        }
        null
    }

    private companion object {
        const val TEST_EVENT_ID = "notification-icon-test"
    }
}
