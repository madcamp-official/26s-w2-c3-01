package com.example.myapplication.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.core.model.MainTab
import com.example.myapplication.core.model.MelodyAliasCandidate
import com.example.myapplication.core.model.Track
import com.example.myapplication.audio.MelodyAliasPreviewPlayer
import com.example.myapplication.audio.LyriaClipPlayer
import com.example.myapplication.data.DemoMelodyRepository
import com.example.myapplication.data.MelodyRepository
import com.example.myapplication.data.remote.AuthRepository
import com.example.myapplication.data.remote.MelodyAliasGenerateRequest
import com.example.myapplication.data.remote.MelodyAliasRepository
import com.example.myapplication.data.remote.LyriaAliasGenerateRequest
import com.example.myapplication.data.remote.LyriaMusicRepository
import com.example.myapplication.data.remote.LyriaMusicResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Success(val expiresInSeconds: Long) : LoginUiState
    data class Error(val message: String) : LoginUiState
}

sealed interface MelodyAliasGenerationState {
    data object Idle : MelodyAliasGenerationState
    data object Loading : MelodyAliasGenerationState
    data class Success(val candidates: List<MelodyAliasCandidate>) : MelodyAliasGenerationState
    data class Error(val message: String) : MelodyAliasGenerationState
}

sealed interface LyriaGenerationState {
    data object Idle : LyriaGenerationState
    data object Loading : LyriaGenerationState
    data class Success(val song: LyriaMusicResponse) : LyriaGenerationState
    data class Error(val message: String) : LyriaGenerationState
}

class MelodyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MelodyRepository = DemoMelodyRepository(application)
    private val authRepository by lazy { AuthRepository() }
    private val melodyAliasPreviewPlayer = MelodyAliasPreviewPlayer()
    private val melodyAliasRepository by lazy { MelodyAliasRepository() }
    private val lyriaRepository by lazy { LyriaMusicRepository() }
    private val lyriaClipPlayer = LyriaClipPlayer(application)
    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    private var accessToken: String? = null
    private val _melodyAliasGenerationState = MutableStateFlow<MelodyAliasGenerationState>(MelodyAliasGenerationState.Idle)
    private val _lyriaGenerationState = MutableStateFlow<LyriaGenerationState>(LyriaGenerationState.Idle)

    val uiState = repository.state
    val loginState = _loginState.asStateFlow()
    val melodyAliasGenerationState = _melodyAliasGenerationState.asStateFlow()
    val lyriaGenerationState = _lyriaGenerationState.asStateFlow()

    fun login(email: String, password: String) {
        if (_loginState.value == LoginUiState.Loading) return
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            authRepository.login(email, password)
                .onSuccess { response ->
                    accessToken = response.accessToken
                    _loginState.value = LoginUiState.Success(response.expiresInSeconds)
                }
                .onFailure {
                    _loginState.value = LoginUiState.Error(
                        "로그인하지 못했습니다. 서버와 계정 정보를 확인해주세요."
                    )
                }
        }
    }

    fun loginWithGoogle(idToken: String) {
        if (_loginState.value == LoginUiState.Loading) return
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            authRepository.googleLogin(idToken)
                .onSuccess { response ->
                    accessToken = response.accessToken
                    _loginState.value = LoginUiState.Success(response.expiresInSeconds)
                }
                .onFailure {
                    _loginState.value = LoginUiState.Error(
                        "Google 로그인에 실패했습니다. 잠시 후 다시 시도해주세요."
                    )
                }
        }
    }

    fun completeOnboarding() = repository.completeOnboarding()
    fun selectTab(tab: MainTab) = repository.selectTab(tab)
    fun selectNearby(handle: String?) = repository.selectNearby(handle)
    fun selectLounge(roomId: String?) = repository.selectLounge(roomId)
    fun startSharing() = repository.startSharing()
    fun stopSharing() = repository.stopSharing()
    fun sharingPermissionRequired() = repository.setSharingPermissionRequired()
    fun follow(handle: String) = repository.follow(handle)
    fun react(handle: String, reactionLabel: String) = repository.react(handle, reactionLabel)
    fun block(handle: String) = repository.block(handle)
    fun report(handle: String) = repository.report(handle)
    fun joinLounge(roomId: String) = repository.joinLounge(roomId)
    fun vote(roomId: String, optionId: String) = repository.vote(roomId, optionId)
    fun sendMusicCard(roomId: String) = repository.sendMusicCard(roomId)
    fun reactToMusicCard(roomId: String, cardId: String) =
        repository.reactToMusicCard(roomId, cardId)
    fun sendChat(roomId: String, content: String) = repository.sendChat(roomId, content)
    fun selectTrack(track: Track) = repository.selectTrack(track)
    fun markInboxRead() = repository.markInboxRead()
    fun setDiscoverable(enabled: Boolean) = repository.setDiscoverable(enabled)
    fun setAllowReactions(enabled: Boolean) = repository.setAllowReactions(enabled)
    fun setOfflineExchangeEnabled(enabled: Boolean) = repository.setOfflineExchangeEnabled(enabled)
    fun selectMelodyAlias(candidateId: String) = repository.selectMelodyAlias(candidateId)
    fun selectGeneratedMelodyAlias(candidate: MelodyAliasCandidate) = repository.selectGeneratedMelodyAlias(candidate)

    fun generateMelodyAliases(mood: String, tone: String, pitch: String, tempoRange: String) {
        if (_melodyAliasGenerationState.value == MelodyAliasGenerationState.Loading) return
        val token = accessToken
        if (token.isNullOrBlank()) {
            _melodyAliasGenerationState.value = MelodyAliasGenerationState.Error("로그인 후 AI 멜로디를 생성할 수 있어요.")
            return
        }
        viewModelScope.launch {
            _melodyAliasGenerationState.value = MelodyAliasGenerationState.Loading
            melodyAliasRepository.generate(
                token,
                MelodyAliasGenerateRequest(
                    mood = mood,
                    tone = tone,
                    pitch = pitch,
                    tempoRange = tempoRange,
                    count = 3
                )
            ).onSuccess { candidates ->
                _melodyAliasGenerationState.value = MelodyAliasGenerationState.Success(candidates)
            }.onFailure {
                _melodyAliasGenerationState.value = MelodyAliasGenerationState.Error(
                    "멜로디를 만들지 못했어요. 서버 연결과 OpenAI 설정을 확인한 뒤 다시 시도해 주세요."
                )
            }
        }
    }

    fun resetMelodyAliasGeneration() {
        _melodyAliasGenerationState.value = MelodyAliasGenerationState.Idle
    }

    fun generateLyriaSong(
        moods: Map<String, Int>,
        genre: String,
        instruments: List<String>,
        pitch: Int,
        speed: Int
    ) {
        if (_lyriaGenerationState.value == LyriaGenerationState.Loading) return
        val token = accessToken
        if (token.isNullOrBlank()) {
            _lyriaGenerationState.value = LyriaGenerationState.Error("로그인 후 음악을 만들 수 있어요.")
            return
        }
        viewModelScope.launch {
            _lyriaGenerationState.value = LyriaGenerationState.Loading
            lyriaRepository.generate(token, LyriaAliasGenerateRequest(moods, genre, instruments, pitch, speed))
                .onSuccess { song ->
                    lyriaClipPlayer.load(song.audioBase64)
                    _lyriaGenerationState.value = LyriaGenerationState.Success(song)
                }
                .onFailure {
                    _lyriaGenerationState.value = LyriaGenerationState.Error("30초 음악을 만들지 못했어요. 잠시 후 다시 시도해 주세요.")
                }
        }
    }

    fun playLyriaSong() = lyriaClipPlayer.playFull()
    fun playLyriaSelection(startSeconds: Float) = lyriaClipPlayer.playSelection(startSeconds)
    fun resetLyriaSong() {
        lyriaClipPlayer.stop()
        _lyriaGenerationState.value = LyriaGenerationState.Idle
    }
    fun previewMelodyAlias(candidate: MelodyAliasCandidate) = melodyAliasPreviewPlayer.play(candidate)
    fun previewMelodyTone(tone: String) = melodyAliasPreviewPlayer.playToneSample(tone)
    fun createDemoExchange(peerAlias: String) = repository.createDemoExchange(peerAlias)
    fun syncExchange(exchangeId: String) = repository.syncExchange(exchangeId)
    fun clearFeedback() = repository.clearFeedback()

    override fun onCleared() {
        melodyAliasPreviewPlayer.release()
        lyriaClipPlayer.release()
        repository.close()
        super.onCleared()
    }
}
