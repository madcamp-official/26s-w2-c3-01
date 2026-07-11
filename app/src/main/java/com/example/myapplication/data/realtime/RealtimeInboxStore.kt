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
