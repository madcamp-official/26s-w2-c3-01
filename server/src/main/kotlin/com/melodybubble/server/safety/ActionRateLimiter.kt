package com.melodybubble.server.safety

import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class ActionRateLimiter(private val jdbc: JdbcTemplate) {
    fun enforce(userId: UUID, action: String, limit: Int, window: Duration) {
        val now = Instant.now()
        val cutoff = now.minus(window)
        val count = jdbc.query(
            """
            INSERT INTO action_rate_limits(user_id, action, window_started_at, request_count)
            VALUES (?, ?, ?, 1)
            ON CONFLICT(user_id, action) DO UPDATE SET
              window_started_at = CASE
                WHEN action_rate_limits.window_started_at < ? THEN excluded.window_started_at
                ELSE action_rate_limits.window_started_at
              END,
              request_count = CASE
                WHEN action_rate_limits.window_started_at < ? THEN 1
                ELSE action_rate_limits.request_count + 1
              END
            RETURNING request_count
            """.trimIndent(),
            { rs, _ -> rs.getInt(1) },
            userId,
            action.take(32),
            Timestamp.from(now),
            Timestamp.from(cutoff),
            Timestamp.from(cutoff),
        ).single()
        if (count > limit) {
            throw ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
            )
        }
    }
}
