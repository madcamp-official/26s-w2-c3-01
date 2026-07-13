package com.example.myapplication.data.realtime

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.example.myapplication.data.remote.RemoteNearbySnapshot
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

            TYPE_NEARBY_SNAPSHOT -> RealtimeEvent.NearbySnapshot(
                destination = destination,
                envelope = envelope.withPayload(decode(envelope.payload, RemoteNearbySnapshot::class.java)),
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
            else -> if (destination.startsWith("/topic/sub-lounges/")) {
                RealtimeEvent.SubLoungeUpdated(destination, envelope)
            } else if (destination == RealtimeDestinations.ERRORS) {
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
    }

    private fun serverError(
        destination: String,
        envelope: RealtimeEventEnvelope<JsonElement>,
    ): RealtimeEvent.ServerError {
        val payload = if (envelope.payload.isJsonObject) {
            decode(envelope.payload, RealtimeServerErrorPayload::class.java)
        } else {
            RealtimeServerErrorPayload(message = envelope.payload.toString())
        }
        return RealtimeEvent.ServerError(destination, envelope.withPayload(payload))
    }

    private fun decodePopularTracks(payload: JsonElement): PopularTracksUpdatedPayload {
        require(payload.isJsonObject) { "POPULAR_TRACKS_UPDATED payload must be an object" }
        val tracksElement = payload.asJsonObject.get("tracks")
        if (tracksElement == null || tracksElement.isJsonNull) return PopularTracksUpdatedPayload()
        require(tracksElement.isJsonArray) { "tracks must be an array" }
        return PopularTracksUpdatedPayload(
            tracks = tracksElement.asJsonArray.map { decode(it, PopularTrackPayload::class.java) },
        )
    }

    private fun <T : Any> decode(payload: JsonElement, type: Class<T>): T {
        require(payload.isJsonObject) { "${type.simpleName} payload must be an object" }
        return requireNotNull(gson.fromJson(payload, type)) { "${type.simpleName} payload is null" }
    }

    private fun <T> RealtimeEventEnvelope<JsonElement>.withPayload(payload: T) =
        RealtimeEventEnvelope(
            eventId = eventId,
            type = type,
            version = version,
            timestamp = timestamp,
            payload = payload,
        )

    private fun JsonObject.requiredString(name: String): String {
        val element = get(name) ?: throw IllegalArgumentException("Missing $name")
        require(element.isJsonPrimitive && element.asJsonPrimitive.isString) { "$name must be a string" }
        return element.asString.also { require(it.isNotBlank()) { "$name must not be blank" } }
    }

    companion object {
        const val TYPE_CHAT_ROOM_CREATED = "CHAT_ROOM_CREATED"
        const val TYPE_CHAT_MESSAGE_CREATED = "CHAT_MESSAGE_CREATED"
        const val TYPE_CHAT_MESSAGE_READ = "CHAT_MESSAGE_READ"
        const val TYPE_CHAT_ROOM_UPDATED = "CHAT_ROOM_UPDATED"
        const val TYPE_NEARBY_REACTION_CREATED = "NEARBY_REACTION_CREATED"
        const val TYPE_NEARBY_SNAPSHOT = "NEARBY_SNAPSHOT"
        const val TYPE_NEARBY_MUSIC_UPDATED = "NEARBY_MUSIC_UPDATED"
        const val TYPE_POPULAR_TRACKS_UPDATED = "POPULAR_TRACKS_UPDATED"
        const val TYPE_NOTIFICATION_CREATED = "NOTIFICATION_CREATED"
        const val TYPE_ERROR = "ERROR"
        const val TYPE_SERVER_ERROR = "SERVER_ERROR"
    }
}
