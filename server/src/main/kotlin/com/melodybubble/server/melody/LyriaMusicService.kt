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
    val candidateKey: String? = null,
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
        return generate(LyriaGenerateRequest(buildAliasPrompt(request)))
    }

    internal fun buildAliasPrompt(request: LyriaAliasGenerateRequest): String {
        val rankedMoods = request.moods.entries
            .map { it.key.trim() to it.value.coerceIn(0, 100) }
            .filter { it.first.isNotBlank() && it.second > 0 }
            .sortedByDescending { it.second }
        val primaryMood = rankedMoods.getOrNull(0) ?: ("Calm" to 50)
        val secondaryMood = rankedMoods.getOrNull(1)
        val accents = rankedMoods.drop(2)
        val emphasized = request.instruments.map(String::trim).filter(String::isNotBlank)
            .joinToString(", ").ifBlank { "piano" }
        val genre = genreDirection(request.genre)
        val pitch = pitchDirection(request.pitch.coerceIn(0, 100))
        val speed = speedDirection(request.speed.coerceIn(0, 100))
        val secondaryLine = secondaryMood?.let {
            "Secondary mood: ${it.first} (${it.second}/100 intensity). It should support, not override, the primary mood."
        } ?: "Secondary mood: none. Keep the emotional direction focused."
        val accentLine = accents.takeIf { it.isNotEmpty() }?.joinToString(", ") { "${it.first} ${it.second}/100" }
            ?.let { "Subtle mood accents: $it. Treat these only as light color; do not average every mood equally." }
            ?: "Subtle mood accents: none."

        return """
            Create one polished 30-second instrumental identity song that expresses "this is my vibe."

            EMOTIONAL PRIORITY
            Primary mood: ${primaryMood.first} (${primaryMood.second}/100 intensity). This must be the clearest emotional impression.
            $secondaryLine
            $accentLine

            GENRE AND ARRANGEMENT
            $genre
            Make the genre recognizable through rhythm, harmony, sound palette, and arrangement, not merely as a vague influence.
            Lead instruments: $emphasized. Keep them clearly audible and central to the motif.

            PITCH PROFILE
            $pitch

            TEMPO AND ENERGY
            $speed

            Supporting instruments are allowed, but they must not obscure the selected genre, primary mood, or lead instruments.
            Build a memorable motif, a clear development, and a satisfying ending within 30 seconds. Keep the mix musical and comfortable to hear, never harsh or alarm-like.
            Instrumental only. No vocals or spoken words. Do not imitate any existing artist or copyrighted song.
        """.trimIndent()
    }

    private fun genreDirection(genre: String): String = when (genre.trim().lowercase()) {
        "팝", "pop" -> "Genre: modern pop. Use a clear hook, clean production, and an accessible verse-to-chorus-like arc."
        "힙합", "hip-hop", "hip hop" -> "Genre: hip-hop. Use a defined drum pocket, syncopated groove, and strong low-end pulse."
        "r&b", "알앤비" -> "Genre: contemporary R&B. Use a laid-back pocket, soulful extended harmony, and smooth layered textures."
        "밴드사운드", "band" -> "Genre: live band sound. Center the arrangement on drums, bass, and expressive guitar or keys with human dynamics."
        "전자음악", "electronic" -> "Genre: electronic music. Use synthesized timbres, a precise electronic groove, and deliberate textural movement."
        "어쿠스틱", "acoustic" -> "Genre: acoustic. Use natural instruments, intimate dynamics, and organic room-like texture."
        "재즈", "jazz" -> "Genre: jazz. Use swing or a nuanced jazz pocket, extended chords, and conversational instrumental phrasing."
        "락", "rock" -> "Genre: rock. Use assertive live drums, electric-guitar-driven momentum, and a strong dynamic rise."
        "클래식", "classical" -> "Genre: classical. Use acoustic orchestral or chamber writing, thematic development, and expressive dynamics."
        else -> "Genre: ${genre.trim().ifBlank { "modern pop" }}. Make its defining rhythmic, harmonic, and timbral traits unmistakable."
    }

    private fun pitchDirection(pitch: Int): String = when (pitch) {
        in 0..20 -> "Very low and grounded: keep the motif in a low register, use weighty bass and dark voicings, and avoid bright high-register leads."
        in 21..40 -> "Low-mid and warm: favor lower melodic phrases, full bass, and close warm voicings with limited high-frequency sparkle."
        in 41..60 -> "Balanced mid register: keep the lead centered and natural, with an even low-to-high frequency balance and no extreme register bias."
        in 61..80 -> "High and airy: place the lead in the upper-mid register, use open voicings and light bass, and add controlled high-frequency shimmer."
        else -> "Very high and luminous: feature an upper-register motif, sparkling textures, open voicings, and restrained low end; avoid heavy low-register riffs."
    }

    private fun speedDirection(speed: Int): String = when (speed) {
        in 0..20 -> "Very slow and spacious, approximately 55-70 BPM, with long phrases, sparse attacks, and low energy."
        in 21..40 -> "Relaxed, approximately 70-90 BPM, with an unhurried groove and gentle dynamic motion."
        in 41..60 -> "Moderate, approximately 90-115 BPM, with steady forward motion and balanced energy."
        in 61..80 -> "Fast and energetic, approximately 115-140 BPM, with active rhythm and a clear dynamic lift."
        else -> "Very fast and intense, approximately 140-165 BPM, with urgent rhythmic motion while remaining clean and musical."
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
