package com.example.myapplication.audio

import android.animation.ValueAnimator
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.animation.LinearInterpolator
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
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> pause()
            AudioManager.AUDIOFOCUS_GAIN -> resume()
        }
    }
    private val focusRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
    } else null
    private var player: MediaPlayer? = null
    private var volumeAnimator: ValueAnimator? = null
    private var remainingPreviewMillis = PREVIEW_LIMIT_MS
    private var playbackStartedAtMillis = 0L
    private val _state = MutableStateFlow(PreviewPlaybackState())
    val state = _state.asStateFlow()

    fun beginLookup(title: String, artist: String, artworkUrl: String? = null) {
        stop()
        _state.value = PreviewPlaybackState(title, artist, artworkUrl, isLoading = true)
    }

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
                // A newly-created player can briefly reach the speaker before some vendor audio
                // effects have applied the media-stream gain. Start silent and fade up so that
                // initialization cannot produce a short full-scale burst.
                it.setVolume(0f, 0f)
                it.start()
                fadeIn(it)
                _state.value = PreviewPlaybackState(title, artist, artworkUrl, isPlaying = true)
                remainingPreviewMillis = PREVIEW_LIMIT_MS
                schedulePreviewEnd()
            }
            setOnCompletionListener { stop() }
            setOnErrorListener { _, _, _ ->
                stop("미리듣기를 재생하지 못했어요.")
                true
            }
            prepareAsync()
        }
    }

    fun togglePauseResume() {
        if (_state.value.isPlaying) pause() else if (_state.value.isPaused) resume()
    }

    private fun pause() {
        val activePlayer = player ?: return
        if (!activePlayer.isPlaying) return
        volumeAnimator?.cancel()
        volumeAnimator = null
        activePlayer.pause()
        remainingPreviewMillis = (remainingPreviewMillis -
            (SystemClock.elapsedRealtime() - playbackStartedAtMillis)).coerceAtLeast(0L)
        handler.removeCallbacksAndMessages(null)
        _state.value = _state.value.copy(isPlaying = false, isPaused = true)
    }

    private fun resume() {
        val activePlayer = player ?: return
        if (!_state.value.isPaused || remainingPreviewMillis <= 0L) return
        activePlayer.setVolume(1f, 1f)
        activePlayer.start()
        _state.value = _state.value.copy(isPlaying = true, isPaused = false)
        schedulePreviewEnd()
    }

    private fun schedulePreviewEnd() {
        playbackStartedAtMillis = SystemClock.elapsedRealtime()
        handler.postDelayed(::stop, remainingPreviewMillis)
    }

    private fun fadeIn(activePlayer: MediaPlayer) {
        volumeAnimator?.cancel()
        volumeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = FADE_IN_DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                if (player === activePlayer) {
                    val volume = animator.animatedValue as Float
                    runCatching { activePlayer.setVolume(volume, volume) }
                }
            }
            start()
        }
    }

    fun stop(errorMessage: String? = null) {
        handler.removeCallbacksAndMessages(null)
        volumeAnimator?.cancel()
        volumeAnimator = null
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        remainingPreviewMillis = PREVIEW_LIMIT_MS
        playbackStartedAtMillis = 0L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(audioManager::abandonAudioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
        _state.value = PreviewPlaybackState(errorMessage = errorMessage)
    }

    fun release() = stop()

    private companion object {
        const val PREVIEW_LIMIT_MS = 30_000L
        const val FADE_IN_DURATION_MS = 400L
    }
}
