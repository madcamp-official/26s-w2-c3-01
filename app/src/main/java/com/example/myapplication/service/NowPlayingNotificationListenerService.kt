package com.example.myapplication.service

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Optional, user-enabled fallback for media apps that do not expose a direct integration.
 *
 * Only CATEGORY_TRANSPORT notifications are inspected. The media app package, notification key
 * and every other notification field are deliberately neither persisted nor logged.
 */
class NowPlayingNotificationListenerService : NotificationListenerService() {

    private val preferences by lazy(LazyThreadSafetyMode.NONE) {
        getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshFromActiveTransportNotifications()
    }

    override fun onListenerDisconnected() {
        clearFallback()
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(statusBarNotification: StatusBarNotification?) {
        val candidate = statusBarNotification ?: return
        if (!candidate.isTransportNotification()) return
        candidate.fallbackText()?.let(::persist)
    }

    override fun onNotificationRemoved(statusBarNotification: StatusBarNotification?) {
        val removed = statusBarNotification ?: return
        if (!removed.isTransportNotification()) return
        refreshFromActiveTransportNotifications(excludedKey = removed.key)
    }

    private fun refreshFromActiveTransportNotifications(excludedKey: String? = null) {
        val newestRemaining = try {
            activeNotifications
                .asSequence()
                .filter { it.key != excludedKey && it.isTransportNotification() }
                .mapNotNull { notification ->
                    notification.fallbackText()?.let { text ->
                        TimestampedFallback(notification.postTime, text)
                    }
                }
                .maxByOrNull { it.postTime }
        } catch (_: RuntimeException) {
            null
        }

        if (newestRemaining == null) {
            clearFallback()
        } else {
            persist(newestRemaining.fallback)
        }
    }

    private fun StatusBarNotification.isTransportNotification(): Boolean =
        notification.category == Notification.CATEGORY_TRANSPORT

    private fun StatusBarNotification.fallbackText(): FallbackText? {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE).toSafeText(MAX_TITLE_LENGTH)
        val text = (
            extras.getCharSequence(Notification.EXTRA_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ).toSafeText(MAX_TEXT_LENGTH)
        return if (title == null || text == null) null else FallbackText(title, text)
    }

    private fun persist(fallback: FallbackText) {
        preferences.edit()
            .putString(KEY_TITLE, fallback.title)
            .putString(KEY_TEXT, fallback.text)
            .putLong(KEY_UPDATED_AT_EPOCH_MS, System.currentTimeMillis())
            .putBoolean(KEY_ACTIVE, true)
            .apply()
    }

    private fun CharSequence?.toSafeText(maxLength: Int): String? =
        this?.toString()
            ?.replace(CONTROL_CHARACTERS, " ")
            ?.trim()
            ?.take(maxLength)
            ?.takeIf(String::isNotEmpty)

    private fun clearFallback() {
        preferences.edit()
            .remove(KEY_TITLE)
            .remove(KEY_TEXT)
            .remove(KEY_UPDATED_AT_EPOCH_MS)
            .putBoolean(KEY_ACTIVE, false)
            .apply()
    }

    private data class FallbackText(val title: String, val text: String)

    private data class TimestampedFallback(val postTime: Long, val fallback: FallbackText)

    companion object {
        const val PREFERENCES_NAME = "melody_bubble_now_playing_fallback"
        const val KEY_TITLE = "title"
        const val KEY_TEXT = "text"
        const val KEY_UPDATED_AT_EPOCH_MS = "updated_at_epoch_ms"
        const val KEY_ACTIVE = "active"

        private const val MAX_TITLE_LENGTH = 256
        private const val MAX_TEXT_LENGTH = 512
        private val CONTROL_CHARACTERS = Regex("[\\p{Cc}\\p{Cf}]+")
    }
}
