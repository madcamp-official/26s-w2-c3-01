package com.example.myapplication.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.myapplication.core.model.PreviewPlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Plays one remote music preview while temporarily owning Android audio focus. */
class MusicPreviewPlayer(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> stop()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.pause()
            AudioManager.AUDIOFOCUS_GAIN -> player?.let { if (!it.isPlaying) it.start() }
        }
    }
    private val focusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
    } else null
    private var player: MediaPlayer? = null
    private val _state = MutableStateFlow(PreviewPlaybackState())
    val state = _state.asStateFlow()

    fun play(url: String, title: String, artist: String, artworkUrl: String? = null) {
        stop()
        _state.value = PreviewPlaybackState(title, artist, artworkUrl, isLoading = true)
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        if (!granted) {
            _state.value = PreviewPlaybackState(errorMessage = "다른 오디오를 일시 정지할 수 없어요.")
            return
        }
        player = MediaPlayer().apply {
            setAudioAttributes(attributes)
            setDataSource(url)
            setOnPreparedListener {
                it.start()
                _state.value = PreviewPlaybackState(title, artist, artworkUrl, isPlaying = true)
                handler.postDelayed(::stop, PREVIEW_LIMIT_MS)
            }
            setOnCompletionListener { stop() }
            setOnErrorListener { _, _, _ ->
                stop("미리듣기를 재생하지 못했어요.")
                true
            }
            prepareAsync()
        }
    }

    fun stop(errorMessage: String? = null) {
        handler.removeCallbacksAndMessages(null)
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(audioManager::abandonAudioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
        _state.value = PreviewPlaybackState(errorMessage = errorMessage)
    }

    fun release() = stop()

    private companion object { const val PREVIEW_LIMIT_MS = 30_000L }
}
