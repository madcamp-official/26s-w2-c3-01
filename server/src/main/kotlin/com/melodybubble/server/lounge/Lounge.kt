package com.melodybubble.server.lounge

import com.melodybubble.server.realtime.RealtimeEnvelope
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal
import java.util.UUID

data class LoungeSummary(val id: UUID, val title: String, val description: String, val theme: String, val activeMembers: Int)
data class LoungeVote(val requestId: String, val roomId: UUID, val voteType: String, val targetKey: String)
data class VoteState(val voteType: String, val targetKey: String, val count: Int)

@RestController
@RequestMapping("/api/v1/rooms")
class LoungeController(private val jdbc: JdbcTemplate) {
    @GetMapping fun rooms(): List<LoungeSummary> = jdbc.query("""select l.id,l.title,l.description,l.theme,count(distinct ps.user_id) as active_members
        from lounges l left join presence_sessions ps on ps.expires_at>now() group by l.id order by l.created_at desc""", { rs, _ ->
        LoungeSummary(UUID.fromString(rs.getString("id")), rs.getString("title"), rs.getString("description"), rs.getString("theme"), rs.getInt("active_members"))
    })
    @GetMapping("/{roomId}") fun room(@PathVariable roomId: UUID) = mapOf("room" to rooms().firstOrNull { it.id == roomId }, "votes" to votes(roomId))
    internal fun votes(roomId: UUID): List<VoteState> = jdbc.query("select vote_type,target_key,count(*) count from lounge_votes where lounge_id=? group by vote_type,target_key", { rs, _ -> VoteState(rs.getString(1), rs.getString(2), rs.getInt(3)) }, roomId)
}

@Controller
class LoungeMessageController(private val jdbc: JdbcTemplate, private val messaging: SimpMessagingTemplate, private val loungeController: LoungeController) {
    @org.springframework.messaging.handler.annotation.MessageMapping("room/vote")
    fun vote(vote: LoungeVote, principal: Principal) {
        jdbc.update("""insert into lounge_votes(lounge_id,voter_id,vote_type,target_key) values (?,?,?,?)
            on conflict(lounge_id,voter_id,vote_type) do update set target_key=excluded.target_key,updated_at=now()""", vote.roomId, UUID.fromString(principal.name), vote.voteType, vote.targetKey)
        messaging.convertAndSend(
            "/topic/room/${vote.roomId}/votes",
            RealtimeEnvelope(type = "VOTE_STATE", payload = loungeController.votes(vote.roomId)),
        )
        messaging.convertAndSendToUser(
            principal.name,
            "/queue/ack",
            RealtimeEnvelope(type = "ACK", payload = mapOf("requestId" to vote.requestId)),
        )
    }
}
