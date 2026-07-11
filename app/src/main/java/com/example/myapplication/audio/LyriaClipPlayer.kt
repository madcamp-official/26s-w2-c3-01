package com.example.myapplication.audio

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Base64
import java.io.File

class LyriaClipPlayer(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var audioFile: File? = null

    fun load(audioBase64: String) {
        stop()
        audioFile?.delete()
        audioFile = File(context.cacheDir, "lyria-alias-preview.mp3").apply {
            writeBytes(Base64.decode(audioBase64, Base64.DEFAULT))
        }
    }

    fun playFull() = play(0, 30_000)

    fun playSelection(startSeconds: Float) = play((startSeconds * 1_000).toInt(), 5_000)

    private fun play(startMs: Int, durationMs: Int) {
        val file = audioFile ?: return
        stop()
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            seekTo(startMs)
            start()
        }
        handler.postDelayed({ stop() }, durationMs.toLong())
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    fun release() {
        stop()
        audioFile?.delete()
    }
}
