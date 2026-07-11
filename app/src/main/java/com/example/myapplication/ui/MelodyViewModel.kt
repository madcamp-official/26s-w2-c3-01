package com.example.myapplication.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.core.model.MainTab
import com.example.myapplication.core.model.ConnectionState
import com.example.myapplication.core.model.MelodyAliasCandidate
import com.example.myapplication.core.model.Track
import com.example.myapplication.core.model.ReportReason
import com.example.myapplication.audio.MelodyAliasPreviewPlayer
import com.example.myapplication.audio.LyriaClipPlayer
import com.example.myapplication.data.DemoMelodyRepository
import com.example.myapplication.MelodyApplication
import com.example.myapplication.data.MelodyRepository
import com.example.myapplication.data.presence.PresenceSyncCoordinator
import com.example.myapplication.data.realtime.RealtimeConnectionState
import com.example.myapplication.data.realtime.RealtimeDestinations
import com.example.myapplication.data.realtime.RealtimeEvent
import com.example.myapplication.data.remote.AuthRepository
import com.example.myapplication.data.remote.ApiClient
import com.example.myapplication.data.remote.BuildingLoungeRepository
import com.example.myapplication.data.remote.BuildingLoungeSummaryDto
import com.example.myapplication.data.remote.CreateLoungeCardRequestDto
import com.example.myapplication.data.remote.SubLoungeSnapshotDto
import com.example.myapplication.data.remote.MelodyAliasGenerateRequest
import com.example.myapplication.data.remote.MelodyAliasRepository
import com.example.myapplication.data.remote.LyriaAliasGenerateRequest
import com.example.myapplication.data.remote.LyriaMusicRepository
import com.example.myapplication.data.remote.LyriaMusicResponse
import com.example.myapplication.data.remote.TokenResponse
import com.example.myapplication.data.local.SecureTokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Success(
        val expiresInSeconds: Long,
        val isNewUser: Boolean = false,
        val onboardingComplete: Boolean = false,
    ) : LoginUiState
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

data class UserMapLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null
)

data class BuildingLoungeUiState(
    val loading: Boolean = false,
    val userLocation: UserMapLocation? = null,
    val lounges: List<BuildingLoungeSummaryDto> = emptyList(),
    val enteredLoungeId: String? = null,
    val subLounges: List<com.example.myapplication.data.remote.SubLoungeSummaryDto> = emptyList(),
    val selectedSubLoungeId: String? = null,
    val subLoungeSnapshot: SubLoungeSnapshotDto? = null,
    val detailLoading: Boolean = false,
    val realtimeState: ConnectionState = ConnectionState.OFFLINE,
    val message: String? = null
)

class MelodyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MelodyRepository = DemoMelodyRepository(application)
    private val authRepository by lazy { AuthRepository() }
    private val melodyAliasPreviewPlayer = MelodyAliasPreviewPlayer()
    private val melodyAliasRepository by lazy { MelodyAliasRepository() }
    private val lyriaRepository by lazy { LyriaMusicRepository() }
    private val buildingLoungeRepository by lazy { BuildingLoungeRepository() }
    private val realtimeClient = (application as MelodyApplication).realtimeClient
    private val presenceCoordinator = PresenceSyncCoordinator.get(application)
    private val lyriaClipPlayer = LyriaClipPlayer(application)
    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    @Volatile private var accessToken: String? = null
    private var refreshToken: String? = null
    private var removeAccessTokenListener: (() -> Unit)? = null
    private var restoredSubLoungeSession = false
    private var loungeFallbackJob: Job? = null
    private var loungeSnapshotJob: Job? = null
    private var latestSubLoungeSnapshotAt: String? = null
    private val tokenStore = SecureTokenStore(application)
    private val _melodyAliasGenerationState = MutableStateFlow<MelodyAliasGenerationState>(MelodyAliasGenerationState.Idle)
    private val _lyriaGenerationState = MutableStateFlow<LyriaGenerationState>(LyriaGenerationState.Idle)
    private val _buildingLoungeState = MutableStateFlow(BuildingLoungeUiState())

    val uiState = repository.state
    val loginState = _loginState.asStateFlow()
    val melodyAliasGenerationState = _melodyAliasGenerationState.asStateFlow()
    val lyriaGenerationState = _lyriaGenerationState.asStateFlow()
    val buildingLoungeState = _buildingLoungeState.asStateFlow()

    init {
        removeAccessTokenListener = ApiClient.addAccessTokenListener { refreshedToken ->
            viewModelScope.launch {
                accessToken = refreshedToken
                repository.refreshSession(refreshedToken)
                restoreSubLoungeSession(refreshedToken)
            }
        }
        ApiClient.configureSession(tokenStore) {
            viewModelScope.launch { clearSession() }
        }
        tokenStore.load()?.let { stored ->
            val storedRefresh = stored.refreshToken
            if (storedRefresh == null) {
                accessToken = stored.accessToken
                repository.authenticate(stored.accessToken)
                restoreSubLoungeSession(stored.accessToken)
                _loginState.value = LoginUiState.Success(
                    expiresInSeconds = 0,
                    onboardingComplete = repository.state.value.isOnboardingComplete,
                )
            } else {
                _loginState.value = LoginUiState.Loading
                viewModelScope.launch {
                    authRepository.refresh(storedRefresh)
                        .onSuccess(::acceptSession)
                        .onFailure {
                            clearSession()
                        }
                }
            }
        }
        viewModelScope.launch {
            realtimeClient.events.collect { event ->
                if (event is RealtimeEvent.SubLoungeUpdated &&
                    event.destination == _buildingLoungeState.value.selectedSubLoungeId
                        ?.let(RealtimeDestinations::subLounge)
                ) {
                    refreshSelectedSubLounge(silent = true)
                }
            }
        }
        viewModelScope.launch {
            realtimeClient.connectionState.collect { state ->
                val connection = when (state) {
                    RealtimeConnectionState.Disconnected -> ConnectionState.OFFLINE
                    is RealtimeConnectionState.Connecting -> ConnectionState.CONNECTING
                    is RealtimeConnectionState.Connected -> ConnectionState.LIVE
                    is RealtimeConnectionState.Reconnecting -> ConnectionState.RECONNECTING
                }
                _buildingLoungeState.value = _buildingLoungeState.value.copy(realtimeState = connection)
                loungeFallbackJob?.cancel()
                loungeFallbackJob = null
                if (state is RealtimeConnectionState.Connected) {
                    refreshSelectedSubLounge(silent = true)
                } else {
                    loungeFallbackJob = viewModelScope.launch {
                        while (isActive) {
                            delay(10_000)
                            refreshSelectedSubLounge(silent = true)
                        }
                    }
                }
            }
        }
    }

    private fun acceptSession(response: TokenResponse) {
        accessToken = response.accessToken
        refreshToken = response.refreshToken
        tokenStore.save(response.accessToken, response.refreshToken)
        repository.authenticate(response.accessToken)
        restoreSubLoungeSession(response.accessToken)
        if (response.onboardingComplete) repository.completeOnboarding()
        _loginState.value = LoginUiState.Success(
            response.expiresInSeconds,
            response.isNewUser,
            response.onboardingComplete,
        )
    }

    fun login(email: String, password: String) {
        if (_loginState.value == LoginUiState.Loading) return
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            authRepository.login(email, password)
                .onSuccess(::acceptSession)
                .onFailure {
                    _loginState.value = LoginUiState.Error(
                        "로그인하지 못했습니다. 서버와 계정 정보를 확인해주세요."
                    )
                }
        }
    }

    fun signup(email: String, password: String, displayName: String) {
        if (_loginState.value == LoginUiState.Loading) return
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            authRepository.signup(email, password, displayName)
                .onSuccess(::acceptSession)
                .onFailure {
                    _loginState.value = LoginUiState.Error("회원가입하지 못했습니다. 이미 사용 중인 아이디인지 확인해주세요.")
                }
        }
    }

    fun logout() {
        val stored = tokenStore.load()
        val access = stored?.accessToken ?: accessToken
        val refresh = stored?.refreshToken ?: refreshToken
        viewModelScope.launch {
            if (access != null) authRepository.logout(access, refresh)
            clearSession()
        }
    }

    private fun clearSession() {
        clearSelectedSubLounge()
        repository.logout()
        accessToken = null
        restoredSubLoungeSession = false
        latestSubLoungeSnapshotAt = null
        refreshToken = null
        tokenStore.clear()
        _loginState.value = LoginUiState.Idle
    }

    fun deleteAccount() {
        val access = tokenStore.load()?.accessToken ?: accessToken ?: return
        viewModelScope.launch {
            authRepository.deleteAccount(access)
                .onSuccess { clearSession() }
                .onFailure { repository.clearFeedback() }
        }
    }

    fun loginWithGoogle(idToken: String) {
        if (_loginState.value == LoginUiState.Loading) return
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            authRepository.googleLogin(idToken)
                .onSuccess(::acceptSession)
                .onFailure {
                    _loginState.value = LoginUiState.Error(
                        "Google 로그인에 실패했습니다. 잠시 후 다시 시도해주세요."
                    )
                }
        }
    }

    fun completeOnboarding(genres: List<String>, moods: List<String>) {
        repository.completeOnboarding()
        val current = _loginState.value as? LoginUiState.Success
        if (current != null) _loginState.value = current.copy(onboardingComplete = true)
        val profile = repository.state.value.profile
        repository.updateProfile(
            profile.accountAlias, profile.colorHex, profile.bio, profile.avatarDataUrl,
            genres, moods,
        )
        val access = accessToken ?: return
        viewModelScope.launch {
            authRepository.completeOnboarding(access, genres, moods)
        }
    }
    fun selectTab(tab: MainTab) = repository.selectTab(tab)
    fun selectNearby(handle: String?) = repository.selectNearby(handle)
    fun selectLounge(roomId: String?) = repository.selectLounge(roomId)
    fun openChat(roomId: String) = repository.openChat(roomId)
    fun closeChat(roomId: String) = repository.closeChat(roomId)
    fun startSharing() = repository.startSharing()
    fun stopSharing() = repository.stopSharing()
    fun sharingPermissionRequired() = repository.setSharingPermissionRequired()
    fun sharingStartFailed() = repository.setSharingStartFailed()
    fun retrySharing() = repository.retrySharing()
    fun follow(handle: String) = repository.follow(handle)
    fun react(handle: String, reactionLabel: String) = repository.react(handle, reactionLabel)
    fun block(handle: String) = repository.block(handle)
    fun report(handle: String, reason: ReportReason = ReportReason.OTHER, description: String? = null) =
        repository.report(handle, reason, description)
    fun loadBlockedUsers() = repository.loadBlockedUsers()
    fun unblock(blockId: String) = repository.unblock(blockId)
    fun joinLounge(roomId: String) = repository.joinLounge(roomId)
    fun vote(roomId: String, optionId: String) = repository.vote(roomId, optionId)
    fun sendMusicCard(roomId: String) = repository.sendMusicCard(roomId)
    fun reactToMusicCard(roomId: String, cardId: String) =
        repository.reactToMusicCard(roomId, cardId)
    fun sendChat(roomId: String, content: String) = repository.sendChat(roomId, content)
    fun selectTrack(track: Track) = repository.selectTrack(track)
    fun setCurrentMusicPlaying(isPlaying: Boolean) = repository.setCurrentMusicPlaying(isPlaying)
    fun markInboxRead() = repository.markInboxRead()
    fun setDiscoverable(enabled: Boolean) = repository.setDiscoverable(enabled)
    fun setAllowReactions(enabled: Boolean) = repository.setAllowReactions(enabled)
    fun setOfflineExchangeEnabled(enabled: Boolean) = repository.setOfflineExchangeEnabled(enabled)
    fun setMusicVisibility(label: String) = repository.setMusicVisibility(label)
    fun updatePresenceSettings(radiusMeters: Int, discoverabilityScope: String, musicVisibility: String) =
        repository.updatePresenceSettings(radiusMeters, discoverabilityScope, musicVisibility)
    fun updateProfile(displayName: String, colorHex: Long, bio: String, avatarDataUrl: String?, genres: List<String>, moods: List<String>) =
        repository.updateProfile(displayName, colorHex, bio, avatarDataUrl, genres, moods)
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

    fun refreshBuildingLounges(latitude: Double, longitude: Double, accuracyMeters: Float? = null) {
        val token = accessToken
        if (token.isNullOrBlank()) {
            _buildingLoungeState.value = _buildingLoungeState.value.copy(
                userLocation = UserMapLocation(latitude, longitude, accuracyMeters),
                message = "Login is required to load building lounges."
            )
            return
        }
        viewModelScope.launch {
            _buildingLoungeState.value = _buildingLoungeState.value.copy(
                loading = true,
                userLocation = UserMapLocation(latitude, longitude, accuracyMeters),
                message = null
            )
            buildingLoungeRepository.nearby(token, latitude, longitude)
                .onSuccess { lounges ->
                    val hasOldOffsetFixtures = lounges.any {
                        it.name == "Test Mall Lounge" ||
                            it.name == "Test Cafe Lounge" ||
                            it.name == "Test Music Hall Lounge"
                    }
                    if (lounges.isEmpty() || hasOldOffsetFixtures) {
                        buildingLoungeRepository.createTestFixtures(token, latitude, longitude)
                            .onSuccess { seededLounges ->
                                _buildingLoungeState.value = _buildingLoungeState.value.copy(
                                    loading = false,
                                    lounges = seededLounges,
                                    message = "Real building test lounges are ready."
                                )
                            }
                            .onFailure {
                                _buildingLoungeState.value = _buildingLoungeState.value.copy(
                                    loading = false,
                                    lounges = emptyList(),
                                    message = "Could not create test places."
                                )
                            }
                        return@launch
                    }
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(
                        loading = false,
                        lounges = lounges,
                        message = if (lounges.none { it.inside }) "No active lounge in this area yet." else null
                    )
                }
                .onFailure {
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(
                        loading = false,
                        message = "Could not load building lounges."
                    )
                }
        }
    }

    fun enterBuildingLounge(loungeId: String) {
        val token = accessToken ?: return
        val location = _buildingLoungeState.value.userLocation ?: return
        viewModelScope.launch {
            buildingLoungeRepository.enter(
                token,
                loungeId,
                location.latitude,
                location.longitude,
                location.accuracyMeters
            ).onSuccess {
                val subLounges = buildingLoungeRepository.subLounges(token, loungeId).getOrDefault(emptyList())
                _buildingLoungeState.value = _buildingLoungeState.value.copy(
                    enteredLoungeId = loungeId,
                    subLounges = subLounges,
                    message = "Entered ${it.lounge.name}."
                )
            }.onFailure {
                _buildingLoungeState.value = _buildingLoungeState.value.copy(
                    message = "Move inside the lounge circle to enter."
                )
            }
        }
    }

    fun heartbeatBuildingLounge(latitude: Double, longitude: Double, accuracyMeters: Float? = null) {
        val token = accessToken ?: return
        val loungeId = _buildingLoungeState.value.enteredLoungeId ?: return
        viewModelScope.launch {
            buildingLoungeRepository.heartbeat(token, loungeId, latitude, longitude, accuracyMeters)
                .onSuccess { response ->
                    if (response.forcedExit) clearSelectedSubLounge()
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(
                        userLocation = UserMapLocation(latitude, longitude, accuracyMeters),
                        enteredLoungeId = if (response.forcedExit) null else loungeId,
                        subLounges = if (response.forcedExit) emptyList() else _buildingLoungeState.value.subLounges,
                        selectedSubLoungeId = if (response.forcedExit) null else _buildingLoungeState.value.selectedSubLoungeId,
                        subLoungeSnapshot = if (response.forcedExit) null else _buildingLoungeState.value.subLoungeSnapshot,
                        message = if (response.forcedExit) "You left the building lounge." else null
                    )
                }
        }
    }

    fun leaveBuildingLounge() {
        val token = accessToken ?: return
        val loungeId = _buildingLoungeState.value.enteredLoungeId ?: return
        viewModelScope.launch {
            clearSelectedSubLounge()
            buildingLoungeRepository.leave(token, loungeId)
            _buildingLoungeState.value = _buildingLoungeState.value.copy(
                enteredLoungeId = null,
                subLounges = emptyList(),
                selectedSubLoungeId = null,
                subLoungeSnapshot = null,
                message = "Left the lounge."
            )
        }
    }

    fun createBuildingSubLounge(title: String, style: String?) {
        val token = accessToken ?: return
        val loungeId = _buildingLoungeState.value.enteredLoungeId ?: return
        viewModelScope.launch {
            buildingLoungeRepository.createSubLounge(token, loungeId, title, style)
                .onSuccess { created ->
                    val subLounges = buildingLoungeRepository.subLounges(token, loungeId).getOrDefault(emptyList())
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(
                        subLounges = subLounges,
                        message = "Created ${created.title}."
                    )
                    openSubLounge(created.id)
                }
                .onFailure {
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(message = "Could not create sub lounge.")
                }
        }
    }

    fun openSubLounge(subLoungeId: String) {
        val token = accessToken ?: return
        viewModelScope.launch {
            _buildingLoungeState.value = _buildingLoungeState.value.copy(detailLoading = true, message = null)
            buildingLoungeRepository.joinSubLounge(token, subLoungeId)
                .onSuccess {
                    val previous = _buildingLoungeState.value.selectedSubLoungeId
                    if (previous != null && previous != subLoungeId) {
                        realtimeClient.unsubscribeTopic(RealtimeDestinations.subLounge(previous))
                    }
                    realtimeClient.subscribeTopic(RealtimeDestinations.subLounge(subLoungeId))
                    presenceCoordinator.activateSubLounge(subLoungeId)
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(
                        selectedSubLoungeId = subLoungeId,
                        detailLoading = true,
                    )
                    refreshSelectedSubLounge(silent = false)
                }
                .onFailure {
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(
                        detailLoading = false,
                        message = "하위 라운지에 입장하지 못했어요.",
                    )
                }
        }
    }

    fun leaveSubLounge() {
        val token = accessToken ?: return
        val subLoungeId = _buildingLoungeState.value.selectedSubLoungeId ?: return
        viewModelScope.launch {
            presenceCoordinator.deactivateSubLounge(subLoungeId)
            buildingLoungeRepository.leaveSubLounge(token, subLoungeId)
            clearSelectedSubLounge()
            _buildingLoungeState.value = _buildingLoungeState.value.copy(message = "하위 라운지에서 나왔어요.")
        }
    }

    fun sendDetectedTrackToLounge(message: String?) {
        val token = accessToken ?: return
        val subLoungeId = _buildingLoungeState.value.selectedSubLoungeId ?: return
        val playback = presenceCoordinator.detectedPlayback.value
        val track = playback.track?.takeIf { playback.isPlaying && playback.verifiedInCurrentProcess }
        if (track == null) {
            _buildingLoungeState.value = _buildingLoungeState.value.copy(message = "재생 중인 음악을 찾지 못했어요.")
            return
        }
        viewModelScope.launch {
            buildingLoungeRepository.addCard(
                token,
                subLoungeId,
                CreateLoungeCardRequestDto(
                    clientCardId = UUID.randomUUID().toString(),
                    trackTitle = track.title,
                    artistName = track.artist,
                    message = message,
                ),
            ).onSuccess { refreshSelectedSubLounge(silent = true) }
                .onFailure {
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(message = "추천 카드를 보내지 못했어요.")
                }
        }
    }

    fun reactToLoungeCard(cardId: String, reactionType: String = "LIKE") {
        val token = accessToken ?: return
        viewModelScope.launch {
            buildingLoungeRepository.reactToCard(token, cardId, reactionType)
                .onSuccess { refreshSelectedSubLounge(silent = true) }
                .onFailure {
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(message = "리액션을 반영하지 못했어요.")
                }
        }
    }

    fun voteInSubLounge(targetKey: String) {
        val token = accessToken ?: return
        val subLoungeId = _buildingLoungeState.value.selectedSubLoungeId ?: return
        viewModelScope.launch {
            buildingLoungeRepository.vote(token, subLoungeId, targetKey)
                .onSuccess { refreshSelectedSubLounge(silent = true) }
                .onFailure {
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(message = "투표를 반영하지 못했어요.")
                }
        }
    }

    fun refreshSubLounge() = refreshSelectedSubLounge(silent = false)

    private fun refreshSelectedSubLounge(silent: Boolean) {
        val token = accessToken ?: return
        val subLoungeId = _buildingLoungeState.value.selectedSubLoungeId ?: return
        loungeSnapshotJob?.cancel()
        loungeSnapshotJob = viewModelScope.launch {
            if (silent) delay(80)
            if (!silent) {
                _buildingLoungeState.value = _buildingLoungeState.value.copy(detailLoading = true)
            }
            buildingLoungeRepository.snapshot(token, subLoungeId)
                .onSuccess { snapshot ->
                    if (_buildingLoungeState.value.selectedSubLoungeId != subLoungeId) return@onSuccess
                    if (latestSubLoungeSnapshotAt?.let { snapshot.generatedAt < it } == true) return@onSuccess
                    latestSubLoungeSnapshotAt = snapshot.generatedAt
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(
                        detailLoading = false,
                        subLoungeSnapshot = snapshot,
                    )
                }
                .onFailure {
                    if (_buildingLoungeState.value.selectedSubLoungeId == subLoungeId) {
                        _buildingLoungeState.value = _buildingLoungeState.value.copy(
                            detailLoading = false,
                            message = "라운지 상태를 불러오지 못했어요.",
                        )
                    }
                }
        }
    }

    private fun clearSelectedSubLounge() {
        _buildingLoungeState.value.selectedSubLoungeId?.let { id ->
            realtimeClient.unsubscribeTopic(RealtimeDestinations.subLounge(id))
            presenceCoordinator.deactivateSubLounge(id)
        }
        _buildingLoungeState.value = _buildingLoungeState.value.copy(
            selectedSubLoungeId = null,
            subLoungeSnapshot = null,
            detailLoading = false,
        )
        latestSubLoungeSnapshotAt = null
    }

    private fun restoreSubLoungeSession(token: String) {
        if (restoredSubLoungeSession) return
        restoredSubLoungeSession = true
        viewModelScope.launch {
            buildingLoungeRepository.activeSubLounge(token)
                .onSuccess { snapshot ->
                    snapshot ?: return@onSuccess
                    latestSubLoungeSnapshotAt = snapshot.generatedAt
                    realtimeClient.subscribeTopic(RealtimeDestinations.subLounge(snapshot.id))
                    presenceCoordinator.activateSubLounge(snapshot.id)
                    val rooms = buildingLoungeRepository.subLounges(token, snapshot.buildingLoungeId)
                        .getOrDefault(emptyList())
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(
                        enteredLoungeId = snapshot.buildingLoungeId,
                        subLounges = rooms,
                        selectedSubLoungeId = snapshot.id,
                        subLoungeSnapshot = snapshot,
                    )
                }
        }
    }

    fun previewMelodyAlias(candidate: MelodyAliasCandidate) = melodyAliasPreviewPlayer.play(candidate)
    fun previewMelodyTone(tone: String) = melodyAliasPreviewPlayer.playToneSample(tone)
    fun createDemoExchange(peerAlias: String) = repository.createDemoExchange(peerAlias)
    fun syncExchange(exchangeId: String) = repository.syncExchange(exchangeId)
    fun clearFeedback() = repository.clearFeedback()

    override fun onCleared() {
        loungeFallbackJob?.cancel()
        loungeSnapshotJob?.cancel()
        removeAccessTokenListener?.invoke()
        removeAccessTokenListener = null
        melodyAliasPreviewPlayer.release()
        lyriaClipPlayer.release()
        repository.close()
        super.onCleared()
    }
}
