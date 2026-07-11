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
