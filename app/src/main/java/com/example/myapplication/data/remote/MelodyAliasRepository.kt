package com.example.myapplication.data.remote

import com.example.myapplication.core.model.MelodyAliasCandidate

class MelodyAliasRepository(
    private val api: MelodyAliasApi = ApiClient.createMelodyAliasApi()
) {
    suspend fun generate(
        accessToken: String,
        request: MelodyAliasGenerateRequest
    ): Result<List<MelodyAliasCandidate>> = runCatching {
        api.generate("Bearer $accessToken", request).candidates.take(3).map { candidate ->
            MelodyAliasCandidate(
                id = candidate.id,
                name = candidate.name,
                mood = candidate.mood,
                tone = candidate.tone,
                tempo = candidate.tempo,
                energy = candidate.energy,
                notes = candidate.notes,
                rhythm = candidate.rhythmMs,
                toneJsPreset = candidate.toneJsPreset.toString(),
                melodyId = candidate.melodyId
            )
        }
    }
}
