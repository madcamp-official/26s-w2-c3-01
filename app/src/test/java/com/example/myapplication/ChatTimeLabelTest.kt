package com.example.myapplication

import com.example.myapplication.data.chatSentAtLabel
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTimeLabelTest {
    @Test
    fun sameDayMessageUsesClockTimeAndOlderMessageIncludesDate() {
        val previousTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val now = parser.parse("2026-07-14T10:00:00Z")!!.time

        try {
            assertEquals("오후 6:30", chatSentAtLabel("2026-07-14T09:30:00Z", now))
            assertEquals("7월 13일 오후 6:30", chatSentAtLabel("2026-07-13T09:30:00Z", now))
        } finally {
            TimeZone.setDefault(previousTimeZone)
        }
    }
}
