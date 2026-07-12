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
    private var audioUrl: String? = null

    fun load(audioBase64: String) {
        stop()
        audioUrl = null
        audioFile?.delete()
        audioFile = File(context.cacheDir, "lyria-alias-preview.mp3").apply {
            writeBytes(Base64.decode(audioBase64, Base64.DEFAULT))
        }
    }

    fun loadUrl(url: String) {
        stop()
        audioFile?.delete()
        audioFile = null
        audioUrl = url
    }

    fun playFull() = play(0, 30_000)

    fun playSelection(startSeconds: Float) = play((startSeconds * 1_000).toInt(), 5_000)

    private fun play(startMs: Int, durationMs: Int) {
        val source = audioFile?.absolutePath ?: audioUrl ?: return
        stop()
        player = MediaPlayer().apply {
            setDataSource(source)
            setOnPreparedListener {
                it.seekTo(startMs)
                it.start()
                handler.postDelayed({ stop() }, durationMs.toLong())
            }
            setOnErrorListener { _, _, _ -> stop(); true }
            prepareAsync()
        }
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
