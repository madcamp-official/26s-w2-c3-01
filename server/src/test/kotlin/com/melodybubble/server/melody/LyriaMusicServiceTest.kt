package com.melodybubble.server.melody

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LyriaMusicServiceTest {
    private val service = LyriaMusicService(ObjectMapper(), "test-key", "test-model")

    @Test
    fun `builds a prioritized and musically concrete alias prompt`() {
        val prompt = service.buildAliasPrompt(
            LyriaAliasGenerateRequest(
                moods = mapOf("Calm" to 60, "Bright" to 45, "Dreamy" to 70, "Dark" to 20),
                genre = "R&B",
                instruments = listOf("Piano", "Synth"),
                pitch = 80,
                speed = 35,
            ),
        )

        assertThat(prompt).contains("Primary mood: Dreamy (70/100 intensity)")
        assertThat(prompt).contains("Secondary mood: Calm (60/100 intensity)")
        assertThat(prompt).contains("Subtle mood accents: Bright 45/100, Dark 20/100")
        assertThat(prompt).contains("Genre: contemporary R&B")
        assertThat(prompt).contains("Lead instruments: Piano, Synth")
        assertThat(prompt).contains("place the lead in the upper-mid register")
        assertThat(prompt).contains("approximately 70-90 BPM")
        assertThat(prompt).doesNotContain("Mood blend:")
    }

    @Test
    fun `maps Korean genre and clamps out-of-range controls`() {
        val prompt = service.buildAliasPrompt(
            LyriaAliasGenerateRequest(
                moods = mapOf("Bright" to 150),
                genre = "전자음악",
                instruments = emptyList(),
                pitch = -10,
                speed = 200,
            ),
        )

        assertThat(prompt).contains("Primary mood: Bright (100/100 intensity)")
        assertThat(prompt).contains("Genre: electronic music")
        assertThat(prompt).contains("Lead instruments: piano")
        assertThat(prompt).contains("Very low and grounded")
        assertThat(prompt).contains("Very fast and intense")
    }
}
