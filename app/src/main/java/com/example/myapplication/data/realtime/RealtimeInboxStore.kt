package com.example.myapplication.data.realtime

import android.content.Context
import android.util.Base64
import com.example.myapplication.core.model.InboxNotification
import com.example.myapplication.core.model.NotificationType
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Private, account-scoped persistence for realtime inbox items that arrive without an Activity. */
class RealtimeInboxStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val gson = Gson()
    private val lock = Any()
    private var activeOwner: String? = null
    private val _notifications = MutableStateFlow<List<InboxNotification>>(emptyList())
    val notifications: StateFlow<List<InboxNotification>> = _notifications.asStateFlow()

    fun activate(accessToken: String) {
        val owner = jwtSubject(accessToken) ?: run {
            clear()
            return
        }
        synchronized(lock) {
            val storedOwner = preferences.getString(KEY_OWNER, null)
            if (storedOwner != owner) preferences.edit().clear().apply()
            preferences.edit().putString(KEY_OWNER, owner).apply()
            activeOwner = owner
            _notifications.value = readItems().toNotifications()
        }
    }

    fun record(event: RealtimeEvent) {
        val item = event.toStoredItem() ?: return
        record(item)
    }

    fun recordReaction(
        reactionId: String,
        senderAlias: String,
        reactionType: String,
        trackTitle: String?,
        createdAtEpochMillis: Long = System.currentTimeMillis(),
    ) {
        val reaction = reactionLabel(reactionType)
        val track = trackTitle.safeText(160)
        record(
            StoredItem(
                id = reactionId,
                type = NotificationType.REACTION.name,
                actorAlias = senderAlias.safeText(40) ?: "주변 사용자",
                preview = listOfNotNull(reaction, track?.let { "‘$it’" }).joinToString(" · "),
                createdAtEpochMillis = createdAtEpochMillis,
            )
        )
    }

    private fun record(item: StoredItem) {
        synchronized(lock) {
            if (activeOwner == null) return
            val current = readItems()
            if (current.any { it.id == item.id }) return
            writeItems((listOf(item) + current).take(MAX_ITEMS))
            _notifications.value = readItems().toNotifications()
        }
    }

    fun load(): List<InboxNotification> = synchronized(lock) {
        if (activeOwner == null) emptyList() else _notifications.value
    }

    private fun List<StoredItem>.toNotifications(): List<InboxNotification> = map { item ->
        InboxNotification(
            id = item.id,
            type = runCatching { NotificationType.valueOf(item.type) }
                .getOrDefault(NotificationType.SYSTEM),
            actorAlias = item.actorAlias,
            actorColorHex = null,
            preview = item.preview,
            relativeTime = "최근",
            isRead = item.isRead,
        )
    }

    fun markAllRead() = synchronized(lock) {
        if (activeOwner != null) {
            writeItems(readItems().map { it.copy(isRead = true) })
            _notifications.value = readItems().toNotifications()
        }
    }

    fun clear() = synchronized(lock) {
        activeOwner = null
        preferences.edit().clear().apply()
        _notifications.value = emptyList()
    }

    private fun readItems(): List<StoredItem> {
        val json = preferences.getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            gson.fromJson(json, Array<StoredItem>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun writeItems(items: List<StoredItem>) {
        preferences.edit().putString(KEY_ITEMS, gson.toJson(items)).apply()
    }

    private fun RealtimeEvent.toStoredItem(): StoredItem? = when (this) {
        is RealtimeEvent.NearbyReactionCreated -> {
            val payload = envelope.payload
            val alias = payload.senderAlias.safeText(40) ?: "주변 사용자"
            val reaction = reactionLabel(payload.reactionType)
            val track = payload.trackTitle.safeText(160)
            StoredItem(
                id = payload.reactionId?.takeIf(String::isNotBlank) ?: envelope.eventId,
                type = NotificationType.REACTION.name,
                actorAlias = alias,
                preview = listOfNotNull(reaction, track?.let { "‘$it’" }).joinToString(" · "),
                createdAtEpochMillis = System.currentTimeMillis(),
            )
        }
        is RealtimeEvent.NotificationCreated -> {
            val payload = envelope.payload
            val preview = payload.body.safeText(300) ?: payload.title.safeText(160) ?: return null
            StoredItem(
                id = payload.notificationId?.takeIf(String::isNotBlank) ?: envelope.eventId,
                type = notificationType(payload.type).name,
                actorAlias = null,
                preview = preview,
                createdAtEpochMillis = System.currentTimeMillis(),
            )
        }
        else -> null
    }

    private fun notificationType(type: String?): NotificationType = when (type) {
        "FOLLOW", "FOLLOWED" -> NotificationType.FOLLOW
        "MUTUAL_FOLLOW" -> NotificationType.MUTUAL_FOLLOW
        "REACTION" -> NotificationType.REACTION
        else -> NotificationType.SYSTEM
    }

    private fun reactionLabel(type: String?): String = when (type) {
        "LIKE" -> "이 곡 좋아요"
        "SAME_TASTE" -> "취향이 닮았어요"
        "GREAT_PICK" -> "선곡 멋져요"
        "LISTEN_TOGETHER" -> "같이 듣고 싶어요"
        else -> "새 리액션"
    }

    private fun jwtSubject(token: String): String? = runCatching {
        val payload = token.split('.').getOrNull(1) ?: return@runCatching null
        val decoded = String(
            Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
            Charsets.UTF_8,
        )
        JsonParser.parseString(decoded).asJsonObject.get("sub")?.asString
            ?.takeIf(String::isNotBlank)
    }.getOrNull()

    private fun String?.safeText(maxLength: Int): String? = this
        ?.replace(CONTROL_CHARACTERS, " ")
        ?.trim()
        ?.take(maxLength)
        ?.takeIf(String::isNotEmpty)

    private data class StoredItem(
        val id: String,
        val type: String,
        val actorAlias: String?,
        val preview: String,
        val createdAtEpochMillis: Long,
        val isRead: Boolean = false,
    )

    companion object {
        private const val PREFERENCES_NAME = "realtime_inbox"
