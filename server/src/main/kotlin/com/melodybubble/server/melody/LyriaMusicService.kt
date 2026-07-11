package com.melodybubble.server.melody

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class LyriaGenerateRequest(val prompt: String)

data class LyriaAliasGenerateRequest(
    val moods: Map<String, Int>,
    val genre: String,
    val instruments: List<String>,
    val pitch: Int,
    val speed: Int,
)

data class LyriaGenerateResponse(
    val audioBase64: String,
    val mimeType: String,
    val description: String?,
    val model: String,
    val durationSeconds: Int = 30,
)

@Service
class LyriaMusicService(
    private val objectMapper: ObjectMapper,
    @Value("\${app.gemini.api-key:}") private val apiKey: String,
    @Value("\${app.gemini.model:lyria-3-clip-preview}") private val model: String,
) {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    fun generate(request: LyriaGenerateRequest): LyriaGenerateResponse {
        require(apiKey.isNotBlank()) { "GEMINI_API_KEY is not configured." }
        val prompt = request.prompt.trim()
        require(prompt.isNotBlank()) { "Prompt is required." }
        require(prompt.length <= 20_000) { "Prompt must be 20,000 characters or fewer." }

        val body = objectMapper.writeValueAsString(
            mapOf(
                "contents" to listOf(
                    mapOf("parts" to listOf(mapOf("text" to prompt))),
                ),
            ),
        )
        val httpRequest = HttpRequest.newBuilder(
            URI.create("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"),
        )
            .timeout(Duration.ofMinutes(3))
            .header("x-goog-api-key", apiKey)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Lyria request failed: HTTP ${response.statusCode()} ${response.body()}")
        }
        return parseResponse(objectMapper.readTree(response.body()))
    }

    fun generateAlias(request: LyriaAliasGenerateRequest): LyriaGenerateResponse {
        val emphasized = request.instruments.joinToString(", ").ifBlank { "Piano" }
        val moods = request.moods.entries.joinToString(", ") { "${it.key} ${it.value}%" }
        val prompt = """
            Create one polished 30-second instrumental identity song that expresses "this is my vibe."

            Mood blend: $moods.
            Genre: ${request.genre}.
            Emphasized instruments: $emphasized.
            Pitch character: ${request.pitch}/100, where 0 is low and grounded and 100 is high and airy.
            Speed and energy: ${request.speed}/100, where 0 is slow and spacious and 100 is fast and energetic.

            The emphasized instruments should be more noticeable, but the arrangement does not have to contain only those instruments. Add any supporting instruments needed to make the music coherent, rich, and pleasant.
            Build a memorable motif, a clear development, and a satisfying ending within 30 seconds. Keep the mix musical and comfortable to hear, never harsh or alarm-like.
            Instrumental only. No vocals or spoken words. Do not imitate any existing artist or copyrighted song.
        """.trimIndent()
        return generate(LyriaGenerateRequest(prompt))
    }

    private fun parseResponse(root: JsonNode): LyriaGenerateResponse {
        val parts = root.path("candidates").path(0).path("content").path("parts")
        var audioData: String? = null
        var mimeType = "audio/mpeg"
        val text = mutableListOf<String>()
        parts.forEach { part ->
            part.path("text").takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() }?.let(text::add)
            val inlineData = part.path("inlineData").takeIf { !it.isMissingNode }
                ?: part.path("inline_data")
            inlineData.path("data").takeIf { it.isTextual && it.asText().isNotBlank() }?.let {
                audioData = it.asText()
                mimeType = inlineData.path("mimeType").asText(
                    inlineData.path("mime_type").asText("audio/mpeg"),
                )
            }
        }
        return LyriaGenerateResponse(
            audioBase64 = requireNotNull(audioData) { "Lyria response did not contain audio data." },
            mimeType = mimeType,
            description = text.joinToString("\n").ifBlank { null },
            model = model,
        )
    }
}
