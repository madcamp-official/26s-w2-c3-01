package com.example.myapplication.data.realtime

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.myapplication.MainActivity

/** Shows private system notifications only while no app Activity is visible. */
class RealtimeSystemNotifier(context: Context) {
    private val applicationContext = context.applicationContext
    private val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Realtime updates",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Music reactions and other realtime Melody Bubble updates"
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
            )
        }
    }

    fun present(event: RealtimeEvent) {
        val content = when (event) {
            is RealtimeEvent.NearbyReactionCreated -> {
                val payload = event.envelope.payload
                val alias = payload.senderAlias?.takeIf(String::isNotBlank) ?: "주변 사용자"
                val reaction = reactionLabel(payload.reactionType)
                val track = payload.trackTitle?.takeIf(String::isNotBlank)
                NotificationContent(
                    id = event.envelope.eventId,
                    title = "$alias 님의 음악 리액션",
                    body = listOfNotNull(reaction, track?.let { "‘$it’" }).joinToString(" · "),
                )
            }
            is RealtimeEvent.NotificationCreated -> NotificationContent(
                id = event.envelope.eventId,
                title = event.envelope.payload.title?.takeIf(String::isNotBlank) ?: "Melody Bubble",
                body = event.envelope.payload.body?.takeIf(String::isNotBlank) ?: return,
            )
            else -> return
        }
        notify(content)
    }

    private fun notify(content: NotificationContent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val requestCode = content.id.hashCode() and Int.MAX_VALUE
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            requestCode,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(applicationContext, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(applicationContext)
        }
        notificationManager.notify(
            requestCode,
            builder
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(content.title.take(80))
                .setContentText(content.body.take(180))
                .setStyle(Notification.BigTextStyle().bigText(content.body.take(500)))
                .setContentIntent(pendingIntent)
                .setCategory(Notification.CATEGORY_SOCIAL)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun reactionLabel(type: String?): String = when (type) {
        "LIKE" -> "이 곡 좋아요"
        "SAME_TASTE" -> "취향이 닮았어요"
        "GREAT_PICK" -> "선곡 멋져요"
        "LISTEN_TOGETHER" -> "같이 듣고 싶어요"
        else -> "새 리액션"
    }

    private data class NotificationContent(val id: String, val title: String, val body: String)

    companion object {
        private const val CHANNEL_ID = "realtime_updates"
    }
}
