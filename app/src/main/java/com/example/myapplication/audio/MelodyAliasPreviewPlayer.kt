package com.example.myapplication.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.myapplication.core.model.MelodyAliasCandidate
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

class MelodyAliasPreviewPlayer {
    private var activeTrack: AudioTrack? = null

    fun play(candidate: MelodyAliasCandidate) {
        playNotes(candidate.notes, candidate.rhythm, candidate.tone)
    }

    fun playToneSample(tone: String) {
        val notes = when (tone) {
            "피아노" -> listOf("C5", "E5", "G5")
            "기타" -> listOf("D5", "F5", "A5")
            "벨" -> listOf("G5", "C6", "E6")
            "오르골" -> listOf("E6", "G6", "C7")
            "신스패드" -> listOf("A5", "D6", "F6")
            else -> listOf("C6", "E6", "G6")
        }
        playNotes(notes, listOf(120, 120, 260), tone)
    }

    fun stop() {
        activeTrack?.let {
            runCatching { it.stop() }
            it.release()
        }
        activeTrack = null
    }

    fun release() {
        stop()
    }

    private fun playNotes(notes: List<String>, rhythmMs: List<Int>, tone: String) {
        stop()
        val samples = synthesize(notes, rhythmMs, tone)
        thread(name = "melody-alias-preview") {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            activeTrack = track
            track.write(samples, 0, samples.size)
            track.setVolume(1.0f)
            track.play()
        }
    }

    private fun synthesize(notes: List<String>, rhythmMs: List<Int>, tone: String): ShortArray {
        val silenceSamples = (SAMPLE_RATE * 0.025).toInt()
        val totalSamples = notes.indices.sumOf { index ->
            val durationMs = rhythmMs.getOrElse(index) { 140 }
            (SAMPLE_RATE * durationMs / 1000) + silenceSamples
        }
        val output = ShortArray(totalSamples)
        var cursor = 0
        notes.forEachIndexed { index, note ->
            val durationMs = rhythmMs.getOrElse(index) { 140 }
            val noteSamples = SAMPLE_RATE * durationMs / 1000
            val frequency = noteFrequency(note)
            repeat(noteSamples) { sampleIndex ->
                val t = sampleIndex.toDouble() / SAMPLE_RATE
                val phase = 2.0 * PI * frequency * t
                val progress = sampleIndex.toDouble() / noteSamples.coerceAtLeast(1)
                val envelope = envelope(progress, tone)
        val wave = waveform(phase, tone)
                output[cursor + sampleIndex] = (wave * envelope * 0.64 * Short.MAX_VALUE)
                    .toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
            cursor += noteSamples + silenceSamples
        }
        return output
    }

    private fun waveform(phase: Double, tone: String): Double = when (tone) {
        "피아노" -> sin(phase) * 0.72 + sin(phase * 2.0) * 0.18 + sin(phase * 3.0) * 0.08
        "기타" -> triangle(phase) * 0.76 + sin(phase * 2.0) * 0.12
        "벨" -> sin(phase) * 0.64 + sin(phase * 2.7) * 0.24 + sin(phase * 4.2) * 0.12
        "오르골" -> sin(phase) * 0.56 + sin(phase * 3.0) * 0.26 + sin(phase * 5.0) * 0.1
        "신스패드" -> sin(phase) * 0.48 + triangle(phase) * 0.28
        else -> sin(phase) * 0.56 + square(phase) * 0.24
    }

    private fun envelope(progress: Double, tone: String): Double {
        val attack = if (tone == "피아노" || tone == "기타") 0.06 else 0.025
        val attackGain = (progress / attack).coerceIn(0.0, 1.0)
        val decay = when (tone) {
            "벨" -> (1.0 - progress).coerceAtLeast(0.0).pow(1.8)
            "기타" -> (1.0 - progress).coerceAtLeast(0.0).pow(1.4)
            else -> (1.0 - progress * 0.72).coerceAtLeast(0.0)
        }
        return attackGain * decay
    }

    private fun noteFrequency(note: String): Double {
        val match = NOTE_PATTERN.matchEntire(note) ?: return 440.0
        val name = match.groupValues[1]
        val octave = match.groupValues[2].toIntOrNull() ?: 4
        val semitone = NOTE_INDEX[name] ?: 9
        val midi = (octave + 1) * 12 + semitone
        return 440.0 * 2.0.pow((midi - 69) / 12.0)
    }

    private fun square(phase: Double): Double = if (sin(phase) >= 0.0) 1.0 else -1.0

    private fun triangle(phase: Double): Double {
        val normalized = phase / (2.0 * PI)
        return 2.0 * abs(2.0 * (normalized - kotlin.math.floor(normalized + 0.5))) - 1.0
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        val NOTE_PATTERN = Regex("""([A-G]#?)(\d)""")
        val NOTE_INDEX = mapOf(
            "C" to 0,
            "C#" to 1,
            "D" to 2,
            "D#" to 3,
            "E" to 4,
            "F" to 5,
            "F#" to 6,
            "G" to 7,
            "G#" to 8,
            "A" to 9,
            "A#" to 10,
            "B" to 11
        )
    }
}
