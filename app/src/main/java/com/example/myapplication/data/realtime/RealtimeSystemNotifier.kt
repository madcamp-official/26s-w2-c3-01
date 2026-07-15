package com.example.myapplication.data.realtime

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.data.AvatarProfileResolver

/** Shows private system notifications only while no app Activity is visible. */
class RealtimeSystemNotifier(context: Context) {
    private val applicationContext = context.applicationContext
    private val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
    private val imageLoader = ImageLoader.Builder(applicationContext).build()
    private val conversations = linkedMapOf<String, Conversation>()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "실시간 알림", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "음악 리액션과 새 채팅 메시지를 알려드려요"
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
            )
        }
    }

    suspend fun present(event: RealtimeEvent) {
        if (!canNotify()) return
        if (event is RealtimeEvent.ChatMessageCreated) {
            presentChat(event)
            return
        }
        val content = when (event) {
            is RealtimeEvent.NearbyReactionCreated -> {
                val payload = event.envelope.payload
                val alias = payload.senderAlias?.takeIf(String::isNotBlank) ?: "주변 사용자"
                NotificationContent(event.envelope.eventId, "$alias 님의 음악 리액션", reactionLabel(payload.reactionType))
            }
            is RealtimeEvent.NotificationCreated -> NotificationContent(
                event.envelope.eventId,
                event.envelope.payload.title?.takeIf(String::isNotBlank) ?: "Sync",
                event.envelope.payload.body?.takeIf(String::isNotBlank) ?: return,
            )
            else -> return
        }
        notifyBasic(content)
    }

    fun clearChatBundles() {
        conversations.clear()
    }

    private suspend fun presentChat(event: RealtimeEvent.ChatMessageCreated) {
        val payload = event.envelope.payload
        if (payload.isMine == true) return
        val roomId = payload.roomId?.takeIf(String::isNotBlank) ?: return
        val body = payload.content?.trim()?.takeIf(String::isNotEmpty) ?: return
        val alias = payload.senderAlias?.trim()?.takeIf(String::isNotEmpty) ?: "새 메시지"
        val resolvedAvatarUrl = AvatarProfileResolver.resolve(
            remoteSeed = null,
            remoteUrl = payload.senderAvatarUrl,
            stableIdentity = payload.senderProfileHandle,
            fallbackSeed = alias,
        ).url
        val conversation = conversations.getOrPut(roomId) {
            Conversation(alias, resolvedAvatarUrl, mutableListOf())
        }
        conversation.alias = alias
        conversation.avatarUrl = resolvedAvatarUrl
        conversation.totalCount += 1
        conversation.messages += Message(body, event.envelope.timestamp.toServerEpochMillis() ?: System.currentTimeMillis())
        while (conversation.messages.size > MAX_MESSAGES) conversation.messages.removeAt(0)

        val avatar = conversation.avatarUrl?.let { loadAvatar(it) }
        val sender = Person.Builder().setName(conversation.alias).apply {
            avatar?.let { setIcon(IconCompat.createWithBitmap(it)) }
        }.build()
        val count = conversation.totalCount
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("나").build())
            .setConversationTitle(if (count > 1) "${conversation.alias} · 메시지 ${count}개" else conversation.alias)
        conversation.messages.forEach { style.addMessage(it.body, it.timestamp, sender) }
        val notificationId = roomId.hashCode() and Int.MAX_VALUE
        notificationManager.notify(
            notificationId,
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_sync)
                .setLargeIcon(avatar)
                .setColor(applicationContext.getColor(R.color.sync_violet_glow))
                .setContentTitle(if (count > 1) "${conversation.alias} · 메시지 ${count}개 (+${count - 1})" else "${conversation.alias} 님의 메시지")
                .setContentText(body.take(180))
                .setNumber(count)
                .setStyle(style)
                .setContentIntent(contentIntent(notificationId))
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .build(),
        )
    }

    private suspend fun loadAvatar(url: String): Bitmap? = runCatching {
        val result = imageLoader.execute(ImageRequest.Builder(applicationContext).data(url).size(192, 192).build())
        (result as? SuccessResult)?.image?.toBitmap()
    }.getOrNull()

    private fun notifyBasic(content: NotificationContent) {
        val id = content.id.hashCode() and Int.MAX_VALUE
        notificationManager.notify(
            id,
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_sync)
                .setColor(applicationContext.getColor(R.color.sync_violet_glow))
                .setContentTitle(content.title.take(80))
                .setContentText(content.body.take(180))
                .setStyle(NotificationCompat.BigTextStyle().bigText(content.body.take(500)))
                .setContentIntent(contentIntent(id))
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun contentIntent(requestCode: Int) = PendingIntent.getActivity(
        applicationContext,
        requestCode,
        Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun canNotify() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun reactionLabel(type: String?): String = when (type) {
        "LIKE" -> "내 곡을 좋아해요"
        "SAME_TASTE" -> "취향이 비슷해요"
        "GREAT_PICK" -> "선곡 멋져요"
        "LISTEN_TOGETHER" -> "같이 듣고 싶어요"
        else -> "새 리액션"
    }

    private data class Conversation(
        var alias: String,
        var avatarUrl: String?,
        val messages: MutableList<Message>,
        var totalCount: Int = 0,
    )
    private data class Message(val body: String, val timestamp: Long)
    private data class NotificationContent(val id: String, val title: String, val body: String)

    companion object {
        private const val CHANNEL_ID = "realtime_updates"
        private const val MAX_MESSAGES = 12
    }
}
