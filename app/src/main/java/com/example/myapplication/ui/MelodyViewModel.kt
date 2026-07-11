package com.example.myapplication.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.core.model.MainTab
import com.example.myapplication.core.model.MelodyAliasCandidate
import com.example.myapplication.core.model.Track
import com.example.myapplication.audio.MelodyAliasPreviewPlayer
import com.example.myapplication.data.DemoMelodyRepository
import com.example.myapplication.data.MelodyRepository
import com.example.myapplication.data.remote.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Success(val expiresInSeconds: Long, val isNewUser: Boolean = false) : LoginUiState
    data class Error(val message: String) : LoginUiState
}

class MelodyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MelodyRepository = DemoMelodyRepository(application)
    private val authRepository by lazy { AuthRepository() }
    private val melodyAliasPreviewPlayer = MelodyAliasPreviewPlayer()
    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    private var accessToken: String? = null

    val uiState = repository.state
    val loginState = _loginState.asStateFlow()

    fun login(email: String, password: String) {
        if (_loginState.value == LoginUiState.Loading) return
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            authRepository.login(email, password)
                .onSuccess { response ->
                    accessToken = response.accessToken
                    _loginState.value = LoginUiState.Success(response.expiresInSeconds, response.isNewUser)
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
                    _loginState.value = LoginUiState.Success(response.expiresInSeconds, response.isNewUser)
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
    fun previewMelodyAlias(candidate: MelodyAliasCandidate) = melodyAliasPreviewPlayer.play(candidate)
    fun previewMelodyTone(tone: String) = melodyAliasPreviewPlayer.playToneSample(tone)
    fun createDemoExchange(peerAlias: String) = repository.createDemoExchange(peerAlias)
    fun syncExchange(exchangeId: String) = repository.syncExchange(exchangeId)
    fun clearFeedback() = repository.clearFeedback()

    override fun onCleared() {
        melodyAliasPreviewPlayer.release()
        repository.close()
        super.onCleared()
    }
}
