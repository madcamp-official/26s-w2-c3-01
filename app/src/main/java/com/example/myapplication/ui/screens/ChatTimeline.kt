package com.example.myapplication.ui.screens

import com.example.myapplication.core.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal sealed interface ChatTimelineItem {
    data class DateDivider(val dayKey: String, val label: String) : ChatTimelineItem
    data class Message(
        val message: ChatMessage,
        val showAvatar: Boolean,
        val showTime: Boolean,
        val timeLabel: String,
    ) : ChatTimelineItem
}

internal fun buildChatTimeline(messages: List<ChatMessage>, locale: Locale = Locale.KOREAN): List<ChatTimelineItem> {
    val result = mutableListOf<ChatTimelineItem>()
    val dateFormat = SimpleDateFormat("yyyy년 M월 d일 EEEE", locale)
    val timeFormat = SimpleDateFormat("a h:mm", locale)
    var previousDay: String? = null
    messages.forEachIndexed { index, message ->
        val epoch = message.sentAtEpochMillis
        val dayKey = epoch?.let(::localDayKey)
        if (dayKey != null && dayKey != previousDay) {
            result += ChatTimelineItem.DateDivider(dayKey, dateFormat.format(Date(epoch)))
            previousDay = dayKey
        }
        result += ChatTimelineItem.Message(
            message = message,
            showAvatar = !message.isMine && !sameMinuteGroup(messages.getOrNull(index - 1), message),
            showTime = !sameMinuteGroup(message, messages.getOrNull(index + 1)),
            timeLabel = epoch?.let { timeFormat.format(Date(it)) } ?: message.sentAtLabel,
        )
    }
    return result
}

private fun sameMinuteGroup(first: ChatMessage?, second: ChatMessage?): Boolean {
    val firstEpoch = first?.sentAtEpochMillis ?: return false
    val secondEpoch = second?.sentAtEpochMillis ?: return false
    return first.isMine == second.isMine && localDayKey(firstEpoch) == localDayKey(secondEpoch) &&
        firstEpoch / 60_000L == secondEpoch / 60_000L
}

private fun localDayKey(epochMillis: Long): String = Calendar.getInstance().run {
    timeInMillis = epochMillis
    "${get(Calendar.YEAR)}-${get(Calendar.DAY_OF_YEAR)}"
}
