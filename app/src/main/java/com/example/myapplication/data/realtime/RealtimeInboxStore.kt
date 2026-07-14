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
import java.text.SimpleDateFormat
import java.util.Locale

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
        senderProfileHandle: String? = null,
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
                actorProfileHandle = senderProfileHandle.safeText(80),
                preview = listOfNotNull(reaction, track?.let { "‘$it’" }).joinToString(" · "),
                createdAtEpochMillis = createdAtEpochMillis,
            )
        )
    }

    private fun record(item: StoredItem) {
        synchronized(lock) {
            if (activeOwner == null) return
            if (item.id in dismissedIds()) return
            val current = readItems()
            if (current.any { it.id == item.id }) return
            val lastReadAt = preferences.getLong(KEY_LAST_READ_AT, 0L)
            val storedItem = item.copy(
                isRead = item.isRead || notificationWasAlreadyRead(
                    createdAtEpochMillis = item.createdAtEpochMillis,
                    lastReadAtEpochMillis = lastReadAt,
                ),
            )
            writeItems((listOf(storedItem) + current).take(MAX_ITEMS))
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
            actorProfileHandle = item.actorProfileHandle,
            preview = item.preview,
            relativeTime = notificationRelativeTime(item.createdAtEpochMillis),
            isRead = item.isRead,
        )
    }

    fun markAllRead() = synchronized(lock) {
        if (activeOwner != null) {
            val items = readItems()
            val lastReadAt = maxOf(
                System.currentTimeMillis(),
                items.maxOfOrNull(StoredItem::createdAtEpochMillis) ?: 0L,
            )
            preferences.edit().putLong(KEY_LAST_READ_AT, lastReadAt).apply()
            writeItems(items.map { it.copy(isRead = true) })
            _notifications.value = readItems().toNotifications()
        }
    }

    fun deleteAll(notificationIds: Collection<String> = emptyList()) = synchronized(lock) {
        if (activeOwner != null) {
            addDismissedIds(readItems().map(StoredItem::id) + notificationIds)
            writeItems(emptyList())
            _notifications.value = emptyList()
        }
    }

    fun delete(notificationId: String) = synchronized(lock) {
        if (activeOwner != null) {
            addDismissedIds(listOf(notificationId))
            val remaining = readItems().filterNot { it.id == notificationId }
            writeItems(remaining)
            _notifications.value = remaining.toNotifications()
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

    private fun dismissedIds(): Set<String> =
        preferences.getStringSet(KEY_DISMISSED_IDS, emptySet())?.toSet().orEmpty()

    private fun addDismissedIds(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val updated = (dismissedIds() + ids.filter(String::isNotBlank))
            .toList()
            .takeLast(MAX_DISMISSED_IDS)
            .toSet()
        preferences.edit().putStringSet(KEY_DISMISSED_IDS, updated).apply()
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
                actorProfileHandle = payload.senderProfileHandle.safeText(80),
                preview = listOfNotNull(reaction, track?.let { "‘$it’" }).joinToString(" · "),
                createdAtEpochMillis = payload.createdAt.toServerEpochMillis()
                    ?: envelope.timestamp.toServerEpochMillis()
                    ?: System.currentTimeMillis(),
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
                createdAtEpochMillis = payload.createdAt.toServerEpochMillis()
                    ?: envelope.timestamp.toServerEpochMillis()
                    ?: System.currentTimeMillis(),
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
        val actorProfileHandle: String? = null,
        val preview: String,
        val createdAtEpochMillis: Long,
        val isRead: Boolean = false,
    )

    companion object {
        private const val PREFERENCES_NAME = "realtime_inbox"
        private const val KEY_OWNER = "owner"
        private const val KEY_ITEMS = "items"
        private const val KEY_LAST_READ_AT = "last_read_at"
        private const val KEY_DISMISSED_IDS = "dismissed_ids"
        private const val MAX_ITEMS = 100
        private const val MAX_DISMISSED_IDS = 500
        private val CONTROL_CHARACTERS = Regex("[\\p{Cc}\\p{Cf}]+")
    }
}

internal fun notificationWasAlreadyRead(
    createdAtEpochMillis: Long,
    lastReadAtEpochMillis: Long,
): Boolean = lastReadAtEpochMillis > 0L && createdAtEpochMillis <= lastReadAtEpochMillis

internal fun notificationRelativeTime(
    createdAtEpochMillis: Long,
    nowEpochMillis: Long = System.currentTimeMillis(),
): String {
    val ageMillis = (nowEpochMillis - createdAtEpochMillis).coerceAtLeast(0L)
    val hours = ageMillis / 3_600_000L
    if (hours < 1L) return "최근"
    val days = hours / 24L
    return if (days < 1L) "${hours}시간 전" else "${days}일 전"
}

internal fun String?.toServerEpochMillis(): Long? {
    val source = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val normalized = source.replace(
        Regex("(\\.\\d{3})\\d+(?=Z$|[+-]\\d{2}:?\\d{2}$)"),
        "$1",
    )
    return listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
    ).firstNotNullOfOrNull { pattern ->
        runCatching {
            SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(normalized)?.time
        }.getOrNull()
    }
}
