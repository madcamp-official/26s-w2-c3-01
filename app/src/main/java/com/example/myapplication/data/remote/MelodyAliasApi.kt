package com.example.myapplication.data.remote

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class MelodyAliasGenerateRequest(
    val mood: String,
    val tone: String,
    val energy: String = "medium",
    val tempoRange: String,
    val pitch: String,
    val vibeTags: List<String> = emptyList(),
    val count: Int = 3
)

data class ToneJsEnvelopeDto(
    val attack: Double,
    val decay: Double,
    val sustain: Double,
    val release: Double
)

data class ToneJsEffectsDto(val reverb: Double, val delay: Double)

data class ToneJsPresetDto(
    val synth: String,
    val oscillatorType: String,
    val envelope: ToneJsEnvelopeDto,
    val effects: ToneJsEffectsDto
)

data class MelodyAliasCandidateDto(
    val id: String,
    val name: String,
    val mood: String,
    val tone: String,
    val tempo: Int,
    val energy: String,
    val notes: List<String>,
    val rhythmMs: List<Int>,
    val toneJsPreset: ToneJsPresetDto,
    val melodyId: String
)

data class MelodyAliasGenerateResponse(val candidates: List<MelodyAliasCandidateDto>)

interface MelodyAliasApi {
    @POST(MelodyApiContract.Rest.MELODY_ALIAS_GENERATE)
    suspend fun generate(
        @Header("Authorization") authorization: String,
        @Body request: MelodyAliasGenerateRequest
    ): MelodyAliasGenerateResponse
}
