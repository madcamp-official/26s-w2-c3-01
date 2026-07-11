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

@Service
class OpenAiMelodyAliasService(
    private val objectMapper: ObjectMapper,
    @Value("\${app.openai.api-key:}") private val apiKey: String,
    @Value("\${app.openai.model:gpt-5.4}") private val model: String,
) {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    fun generate(request: MelodyAliasGenerateRequest): MelodyAliasGenerateResponse {
        require(apiKey.isNotBlank()) { "OPENAI_API_KEY is not configured." }

        val count = request.count.coerceIn(1, 5)
        val body = objectMapper.writeValueAsString(
            mapOf(
                "model" to model,
                "input" to listOf(
                    mapOf(
                        "role" to "developer",
                        "content" to listOf(
                            mapOf(
                                "type" to "input_text",
                                "text" to developerPrompt,
                            ),
                        ),
                    ),
                    mapOf(
                        "role" to "user",
                        "content" to listOf(
                            mapOf(
                                "type" to "input_text",
                                "text" to userPrompt(request.copy(count = count)),
                            ),
                        ),
                    ),
                ),
                "text" to mapOf(
                    "format" to mapOf(
                        "type" to "json_schema",
                        "name" to "melody_alias_candidates",
                        "strict" to true,
                        "schema" to melodyAliasSchema,
                    ),
                ),
                "max_output_tokens" to 2000,
            ),
        )

        val httpRequest = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
            .timeout(Duration.ofSeconds(45))
            .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("OpenAI request failed: HTTP ${response.statusCode()} ${response.body()}")
        }

        val outputText = extractOutputText(objectMapper.readTree(response.body()))
        val generated = objectMapper.readValue(outputText, MelodyAliasGenerateResponse::class.java)
        return generated.copy(candidates = generated.candidates.map(::normalizeCandidate))
    }

    fun buildPromptPreview(request: MelodyAliasGenerateRequest): String =
        "$developerPrompt\n\n${userPrompt(request.copy(count = request.count.coerceIn(1, 5)))}"

    private fun extractOutputText(root: JsonNode): String {
        root.path("output_text").takeIf { it.isTextual && it.asText().isNotBlank() }?.let {
            return it.asText()
        }
        root.path("output").forEach { output ->
            output.path("content").forEach { content ->
                val text = content.path("text")
                if (text.isTextual && text.asText().isNotBlank()) return text.asText()
            }
        }
        throw IllegalStateException("OpenAI response did not contain output text.")
    }

    private fun normalizeCandidate(candidate: MelodyAliasCandidateResponse): MelodyAliasCandidateResponse {
        val notes = candidate.notes.take(5)
        val rhythm = candidate.rhythmMs.take(notes.size)
        require(notes.size in 2..5) { "Generated melody must contain 2 to 5 notes." }
        require(rhythm.size == notes.size) { "Generated rhythm must match note count." }
        return candidate.copy(
            id = candidate.id.lowercase().replace(Regex("[^a-z0-9-]+"), "-").trim('-'),
            tempo = candidate.tempo.coerceIn(80, 160),
            notes = notes,
            rhythmMs = rhythm.map { it.coerceIn(60, 800) },
        )
    }

    private fun userPrompt(request: MelodyAliasGenerateRequest): String = """
        User selected options:
        - Mood: ${request.mood}
        - Tone: ${request.tone}
        - Energy: ${request.energy}
        - Pitch range: ${request.pitch}
        - Tempo range: ${request.tempoRange}
        - Preferred vibe tags: ${request.vibeTags.joinToString(", ").ifBlank { "none" }}
        - Candidate count: ${request.count}

        Generate exactly ${request.count} melody alias candidates.
    """.trimIndent()

    private companion object {
        private val developerPrompt = """
            You are a melody alias generator for Melody Bubble, a music-sharing app.
            Generate short alarm-like identity sounds that can be played with Tone.js.

            Rules:
            - Return JSON only.
            - Each melody must be suitable for an app notification or identity sound.
            - Each melody must use 2 to 5 notes only.
            - Use Tone.js-compatible note names, such as C5, D#5, F6, A6.
            - rhythmMs values are note durations in milliseconds.
            - Keep tempo from 80 to 160 BPM unless the user's tempo range is narrower.
            - Do not reference real songs, artists, brands, or copyrighted melodies.
            - Names must be short, friendly, and usable as a melody alias.
            - melodyId must summarize the notes and end with -AI.
        """.trimIndent()

        private val melodyAliasSchema = mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "required" to listOf("candidates"),
            "properties" to mapOf(
                "candidates" to mapOf(
                    "type" to "array",
                    "minItems" to 1,
                    "maxItems" to 5,
                    "items" to mapOf(
                        "type" to "object",
                        "additionalProperties" to false,
                        "required" to listOf(
                            "id",
                            "name",
                            "mood",
                            "tone",
                            "tempo",
                            "energy",
                            "notes",
                            "rhythmMs",
                            "toneJsPreset",
                            "playbackHint",
                            "melodyId",
                        ),
                        "properties" to mapOf(
                            "id" to mapOf("type" to "string"),
                            "name" to mapOf("type" to "string"),
                            "mood" to mapOf("type" to "string"),
                            "tone" to mapOf("type" to "string"),
                            "tempo" to mapOf("type" to "integer", "minimum" to 80, "maximum" to 160),
                            "energy" to mapOf("type" to "string"),
                            "notes" to mapOf(
                                "type" to "array",
                                "minItems" to 2,
                                "maxItems" to 5,
                                "items" to mapOf("type" to "string"),
                            ),
                            "rhythmMs" to mapOf(
                                "type" to "array",
                                "minItems" to 2,
                                "maxItems" to 5,
                                "items" to mapOf("type" to "integer", "minimum" to 60, "maximum" to 800),
                            ),
                            "toneJsPreset" to mapOf(
                                "type" to "object",
                                "additionalProperties" to false,
                                "required" to listOf("synth", "oscillatorType", "envelope", "effects"),
                                "properties" to mapOf(
                                    "synth" to mapOf("type" to "string", "enum" to listOf("Synth", "AMSynth", "FMSynth")),
                                    "oscillatorType" to mapOf(
                                        "type" to "string",
                                        "enum" to listOf("sine", "triangle", "square", "sawtooth"),
                                    ),
                                    "envelope" to mapOf(
                                        "type" to "object",
                                        "additionalProperties" to false,
                                        "required" to listOf("attack", "decay", "sustain", "release"),
                                        "properties" to mapOf(
                                            "attack" to mapOf("type" to "number", "minimum" to 0.001, "maximum" to 0.2),
                                            "decay" to mapOf("type" to "number", "minimum" to 0.01, "maximum" to 0.6),
                                            "sustain" to mapOf("type" to "number", "minimum" to 0.0, "maximum" to 0.8),
                                            "release" to mapOf("type" to "number", "minimum" to 0.02, "maximum" to 1.2),
                                        ),
                                    ),
                                    "effects" to mapOf(
                                        "type" to "object",
                                        "additionalProperties" to false,
                                        "required" to listOf("reverb", "delay"),
                                        "properties" to mapOf(
                                            "reverb" to mapOf("type" to "number", "minimum" to 0.0, "maximum" to 0.8),
                                            "delay" to mapOf("type" to "number", "minimum" to 0.0, "maximum" to 0.5),
                                        ),
                                    ),
                                ),
                            ),
                            "playbackHint" to mapOf("type" to "string"),
                            "melodyId" to mapOf("type" to "string"),
                        ),
                    ),
                ),
            ),
        )
    }
}
