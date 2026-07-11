package com.melodybubble.server.ops

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.view.RedirectView
import java.lang.management.ManagementFactory
import java.time.Instant

data class OpsCount(val key: String, val label: String, val value: Long)
data class OpsRuntime(val uptimeSeconds: Long, val heapUsedMb: Long, val heapMaxMb: Long, val processors: Int)
data class OpsSummary(
    val status: String,
    val generatedAt: Instant,
    val database: String,
    val flywayVersion: String?,
    val counts: List<OpsCount>,
    val runtime: OpsRuntime,
)

@Controller
@RequestMapping("/internal/ops")
class OpsController(private val jdbc: JdbcTemplate) {
    @GetMapping("", "/")
    fun index() = RedirectView("/internal/ops/index.html")

    @GetMapping("/melody-prompt", "/melody-prompt/")
    fun melodyPrompt() = "forward:/internal/ops/melody-prompt/index.html"

    @GetMapping("/api/summary")
    @ResponseBody
    fun summary(): OpsSummary {
        val counts = listOf(
            count("users", "Users", "users"),
            count("presence", "Active presence", "presence_sessions", "expires_at > now()"),
            count("locations", "Live locations", "current_locations", "expires_at > now()"),
            count("music", "Music statuses", "music_statuses", "expires_at > now()"),
            count("chatRooms", "Chat rooms", "chat_rooms"),
            count("messages", "Messages", "chat_messages"),
            count("nearbyReactions", "Nearby reactions", "nearby_reactions"),
            count("lounges", "Active lounges", "lounges", "active = true"),
            count("cards", "Live cards", "lounge_cards", "expires_at > now()"),
            count("votes", "Votes", "lounge_votes"),
        )
        val runtime = Runtime.getRuntime()
        return OpsSummary(
            status = "UP",
            generatedAt = Instant.now(),
            database = jdbc.queryForObject("select current_database()", String::class.java) ?: "unknown",
            flywayVersion = jdbc.queryForObject("select max(version) from flyway_schema_history where success = true", String::class.java),
            counts = counts,
            runtime = OpsRuntime(
                uptimeSeconds = ManagementFactory.getRuntimeMXBean().uptime / 1_000,
                heapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576,
                heapMaxMb = runtime.maxMemory() / 1_048_576,
                processors = runtime.availableProcessors(),
            ),
        )
    }

    private fun count(key: String, label: String, table: String, where: String? = null): OpsCount {
        val predicate = where?.let { " where $it" }.orEmpty()
        return OpsCount(key, label, jdbc.queryForObject("select count(*) from $table$predicate", Long::class.java) ?: 0)
    }
}
