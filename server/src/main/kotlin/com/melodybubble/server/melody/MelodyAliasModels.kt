package com.melodybubble.server.melody

data class MelodyAliasGenerateRequest(
    val mood: String,
    val tone: String,
    val energy: String,
    val tempoRange: String,
    val pitch: String = "medium",
    val vibeTags: List<String> = emptyList(),
    val count: Int = 3,
    val promptOverride: String? = null,
)

data class ToneJsEnvelope(
    val attack: Double,
    val decay: Double,
    val sustain: Double,
    val release: Double,
)

data class ToneJsEffects(
    val reverb: Double,
    val delay: Double,
)

data class ToneJsPreset(
    val synth: String,
    val oscillatorType: String,
    val envelope: ToneJsEnvelope,
    val effects: ToneJsEffects,
)

data class MelodyAliasCandidateResponse(
    val id: String,
    val name: String,
    val mood: String,
    val tone: String,
    val tempo: Int,
    val energy: String,
    val notes: List<String>,
    val rhythmMs: List<Int>,
    val toneJsPreset: ToneJsPreset,
    val playbackHint: String,
    val melodyId: String,
)

data class MelodyAliasGenerateResponse(
    val candidates: List<MelodyAliasCandidateResponse>,
)
