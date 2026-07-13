package com.example.myapplication.ui

import com.example.myapplication.data.realtime.RealtimeEvent
import com.example.myapplication.data.remote.LoungeListeningStatusDto
import com.example.myapplication.data.remote.LoungePollStateDto
import com.example.myapplication.data.remote.LoungeRecommendationCardDto
import com.example.myapplication.data.remote.SubLoungeSnapshotDto
import com.google.gson.Gson

internal object SubLoungeEventReducer {
    private val gson = Gson()

    fun reduce(snapshot: SubLoungeSnapshotDto, event: RealtimeEvent.SubLoungeUpdated): SubLoungeSnapshotDto? =
        runCatching {
            val payload = event.envelope.payload
            when (event.type) {
                "SUB_LOUNGE_STATE_UPDATED", "SUB_LOUNGE_MEMBER_JOINED", "SUB_LOUNGE_MEMBER_LEFT" ->
                    snapshot.copy(
                        memberCount = payload.asJsonObject.get("memberCount").asInt,
                        generatedAt = event.envelope.timestamp,
                    )
                "LISTENING_STATUS_UPDATED" -> {
                    val listening = gson.fromJson(payload, LoungeListeningStatusDto::class.java)
                    val others = snapshot.listeningStatuses.filterNot {
                        it.listenerAlias == listening.listenerAlias
                    }
                    snapshot.copy(
                        listeningStatuses = if (listening.isPlaying) listOf(listening) + others else others,
                        generatedAt = event.envelope.timestamp,
                    )
                }
                "RECOMMENDATION_CARD_CREATED" -> {
                    val card = gson.fromJson(payload, LoungeRecommendationCardDto::class.java)
                    snapshot.copy(
                        cards = listOf(card) + snapshot.cards.filterNot { it.id == card.id },
                        generatedAt = event.envelope.timestamp,
                    )
                }
                "RECOMMENDATION_CARD_REACTED" -> {
                    val card = gson.fromJson(payload, LoungeRecommendationCardDto::class.java)
                    snapshot.copy(
                        cards = snapshot.cards.map {
                            if (it.id == card.id) it.copy(reactionCount = card.reactionCount) else it
                        },
                        generatedAt = event.envelope.timestamp,
                    )
                }
                "RECOMMENDATION_CARD_DELETED" -> snapshot.copy(
                    cards = snapshot.cards.filterNot {
                        it.id == payload.asJsonObject.get("cardId").asString
                    },
                    generatedAt = event.envelope.timestamp,
                )
                "LOUNGE_POLL_UPDATED" -> {
                    val poll = gson.fromJson(payload, LoungePollStateDto::class.java)
                    snapshot.copy(
                        poll = poll.copy(myVote = snapshot.poll.myVote),
                        generatedAt = event.envelope.timestamp,
                    )
                }
                else -> null
            }
        }.getOrNull()
}
