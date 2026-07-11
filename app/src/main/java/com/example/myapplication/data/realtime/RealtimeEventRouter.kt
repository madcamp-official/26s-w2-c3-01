package com.example.myapplication.data.realtime

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Locale

/** Converts the common server envelope into exhaustively handled app events. */
class RealtimeEventRouter(
    private val gson: Gson = Gson(),
) {
    fun route(destination: String, body: String): RealtimeEvent = try {
        val envelope = parseEnvelope(body)
        when (envelope.type.uppercase(Locale.US)) {
            TYPE_CHAT_ROOM_CREATED -> RealtimeEvent.ChatRoomCreated(
                destination = destination,
                envelope = envelope.withPayload(decode(envelope.payload, ChatRoomCreatedPayload::class.java)),
            )

            TYPE_CHAT_MESSAGE_CREATED -> RealtimeEvent.ChatMessageCreated(
                destination = destination,
                envelope = envelope.withPayload(decode(envelope.payload, ChatMessageCreatedPayload::class.java)),
            )

            TYPE_CHAT_MESSAGE_READ -> RealtimeEvent.ChatMessageRead(
                destination = destination,
                envelope = envelope.withPayload(decode(envelope.payload, ChatMessageReadPayload::class.java)),
            )

            TYPE_CHAT_ROOM_UPDATED -> RealtimeEvent.ChatRoomUpdated(
                destination = destination,
                envelope = envelope.withPayload(decode(envelope.payload, ChatRoomUpdatedPayload::class.java)),
            )

            TYPE_NEARBY_REACTION_CREATED -> RealtimeEvent.NearbyReactionCreated(
                destination = destination,
                envelope = envelope.withPayload(decode(envelope.payload, NearbyReactionCreatedPayload::class.java)),
            )

            TYPE_NEARBY_MUSIC_UPDATED -> RealtimeEvent.NearbyMusicUpdated(
                destination = destination,
                envelope = envelope.withPayload(decode(envelope.payload, NearbyMusicUpdatedPayload::class.java)),
            )

            TYPE_POPULAR_TRACKS_UPDATED -> RealtimeEvent.PopularTracksUpdated(
                destination = destination,
                envelope = envelope.withPayload(decodePopularTracks(envelope.payload)),
            )

            TYPE_NOTIFICATION_CREATED -> RealtimeEvent.NotificationCreated(
                destination = destination,
                envelope = envelope.withPayload(decode(envelope.payload, RealtimeNotificationPayload::class.java)),
            )

            TYPE_ERROR, TYPE_SERVER_ERROR -> serverError(destination, envelope)
            else -> if (destination == RealtimeDestinations.ERRORS) {
                serverError(destination, envelope)
            } else {
                RealtimeEvent.Unknown(destination, envelope)
            }
        }
    } catch (error: Exception) {
        RealtimeEvent.ParsingError(
            destination = destination,
            rawBody = body,
            reason = listOfNotNull(error::class.simpleName, error.message).joinToString(": "),
        )
    }

    private fun parseEnvelope(body: String): RealtimeEventEnvelope<JsonElement> {
        val root = JsonParser.parseString(body)
        require(root.isJsonObject) { "Event envelope must be a JSON object" }
        val objectValue = root.asJsonObject
        val eventId = objectValue.requiredString("eventId")
        val type = objectValue.requiredString("type")
        val version = objectValue.get("version")?.let { versionElement ->
            require(versionElement.isJsonPrimitive && versionElement.asJsonPrimitive.isNumber) {
                "version must be a number"
            }
            versionElement.asInt
        } ?: throw IllegalArgumentException("Missing version")
        require(version >= 1) { "version must be positive" }
        val timestamp = objectValue.requiredString("timestamp")
        require(objectValue.has("payload")) { "Missing payload" }
        return RealtimeEventEnvelope(
            eventId = eventId,
            type = type,
            version = version,
            timestamp = timestamp,
            payload = objectValue.get("payload"),
        )
