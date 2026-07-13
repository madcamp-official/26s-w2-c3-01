package com.example.myapplication.ui

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.core.model.MainTab
import com.example.myapplication.core.model.ConnectionState
import com.example.myapplication.core.model.Track
import com.example.myapplication.core.model.ReportReason
import com.example.myapplication.core.model.SessionMode
import com.example.myapplication.core.model.ProfileArtist
import com.example.myapplication.core.model.MusicSearchResult
import com.example.myapplication.core.model.ProfilePrivacySettings
import com.example.myapplication.core.model.ProfileTrack
import com.example.myapplication.audio.MusicPreviewPlayer
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
import com.example.myapplication.data.remote.MusicSearchRepository
import com.example.myapplication.data.remote.LoungeMusicSearchResultDto
import com.example.myapplication.data.remote.TokenResponse
import com.example.myapplication.data.local.SecureTokenStore
import com.example.myapplication.data.local.StoredSession
import com.example.myapplication.data.local.CachedAccount
import com.example.myapplication.data.local.OfflineAccountStore
import com.example.myapplication.data.remote.OfflineCredentialRequest
import com.example.myapplication.data.remote.jwtSubject
import com.example.myapplication.offlineexchange.ExchangeCrypto
import com.example.myapplication.offlineexchange.ExchangeMusicCard
import com.example.myapplication.offlineexchange.NearbyExchangeManager
import com.example.myapplication.offlineexchange.OfflineExchangeIdentity
import com.example.myapplication.data.network.WifiFingerprintProvider
import com.example.myapplication.data.network.WifiIdentityResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import retrofit2.HttpException
import java.io.IOException

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Success(
        val expiresInSeconds: Long,
        val isNewUser: Boolean = false,
        val onboardingComplete: Boolean = false,
        val mode: SessionMode = SessionMode.ONLINE,
    ) : LoginUiState
    data class OfflineAvailable(
        val account: CachedAccount,
        val message: String = "인터넷에 연결할 수 없어 저장된 계정으로 오프라인 모드를 사용할 수 있어요.",
    ) : LoginUiState
    data class ReauthenticationRequired(val accountAlias: String?) : LoginUiState
    data class Error(val message: String) : LoginUiState
}

sealed interface SessionState {
    data object SignedOut : SessionState
    data object Restoring : SessionState
    data class Online(val account: CachedAccount) : SessionState
    data class Offline(val account: CachedAccount) : SessionState
    data class ReauthenticationRequired(val account: CachedAccount?) : SessionState
}

sealed interface EmailAvailabilityUiState {
    data object Idle : EmailAvailabilityUiState
    data object Loading : EmailAvailabilityUiState
    data class Available(val email: String) : EmailAvailabilityUiState
    data class Unavailable(val email: String) : EmailAvailabilityUiState
    data class Error(val message: String) : EmailAvailabilityUiState
}

sealed interface MusicSearchUiState {
    data object Idle : MusicSearchUiState
    data class Loading(val query: String) : MusicSearchUiState
    data class Success(
        val query: String,
        val results: List<MusicSearchResult>,
    ) : MusicSearchUiState
    data class Error(val query: String, val message: String) : MusicSearchUiState
}

data class GenreCatalogUiState(
    val genres: List<String> = emptyList(),
    val loading: Boolean = false,
    val errorMessage: String? = null,
)

data class UserMapLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null
)

data class BuildingLoungeUiState(
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
    val userLocation: UserMapLocation? = null,
    val lounges: List<BuildingLoungeSummaryDto> = emptyList(),
    val enteredLoungeId: String? = null,
    val subLounges: List<com.example.myapplication.data.remote.SubLoungeSummaryDto> = emptyList(),
    val selectedSubLoungeId: String? = null,
    val subLoungeSnapshot: SubLoungeSnapshotDto? = null,
    val detailLoading: Boolean = false,
    val trackSearchLoading: Boolean = false,
    val trackSearchResults: List<LoungeMusicSearchResultDto> = emptyList(),
    val realtimeState: ConnectionState = ConnectionState.OFFLINE,
    val message: String? = null
)

class MelodyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MelodyRepository = DemoMelodyRepository(application)
    private val authRepository by lazy { AuthRepository() }
    private val musicSearchRepository by lazy { MusicSearchRepository() }
    private val buildingLoungeRepository by lazy { BuildingLoungeRepository() }
    private val realtimeClient = (application as MelodyApplication).realtimeClient
    private val presenceCoordinator = PresenceSyncCoordinator.get(application)
    private val musicPreviewPlayer = MusicPreviewPlayer(application)
    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    private val _sessionState = MutableStateFlow<SessionState>(SessionState.SignedOut)
    private val _emailAvailabilityState = MutableStateFlow<EmailAvailabilityUiState>(EmailAvailabilityUiState.Idle)
    @Volatile private var accessToken: String? = null
    private var refreshToken: String? = null
    private var removeAccessTokenListener: (() -> Unit)? = null
    private var restoredSubLoungeSession = false
    private var loungeFallbackJob: Job? = null
    private var loungeSnapshotJob: Job? = null
    private var latestSubLoungeSnapshotAt: String? = null
    @Volatile private var offlineCredentialPreparing = false
    private val tokenStore = SecureTokenStore(application)
    private val offlineAccountStore = OfflineAccountStore(application)
    private val exchangeCrypto = ExchangeCrypto()
    private val offlineExchangeApi by lazy { ApiClient.createOfflineExchangeApi() }
    private val sessionProfileApi by lazy { ApiClient.createProfileApi() }
    private val connectivityManager = application.getSystemService(ConnectivityManager::class.java)
    private val connectivityCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            viewModelScope.launch { restoreOnlineAfterOfflineMode() }
        }
    }
    private val nearbyExchangeManager = NearbyExchangeManager(application, exchangeCrypto) { result ->
        offlineAccountStore.load()?.offlineCredential?.let { credential ->
            repository.saveOfflineExchange(result, credential.credentialId)
        }
    }
    private val _buildingLoungeState = MutableStateFlow(BuildingLoungeUiState())
    private val _musicSearchState = MutableStateFlow<MusicSearchUiState>(MusicSearchUiState.Idle)
    private val _genreCatalogState = MutableStateFlow(GenreCatalogUiState())
    private var musicSearchJob: Job? = null
    private var nowPlayingPreviewJob: Job? = null
    private var previewLookupJob: Job? = null
    private var followedNearbyHandle: String? = null
    private var followedNearbyTrackKey: String? = null

    val uiState = repository.state
    val loginState = _loginState.asStateFlow()
    val sessionState = _sessionState.asStateFlow()
    val emailAvailabilityState = _emailAvailabilityState.asStateFlow()
    val buildingLoungeState = _buildingLoungeState.asStateFlow()
    val musicSearchState = _musicSearchState.asStateFlow()
    val genreCatalogState = _genreCatalogState.asStateFlow()
    val previewPlaybackState = musicPreviewPlayer.state
    val exchangeState = nearbyExchangeManager.state

    init {
        loadGenreCatalog()
        connectivityManager.registerDefaultNetworkCallback(connectivityCallback)
        removeAccessTokenListener = ApiClient.addAccessTokenListener { refreshedToken ->
            viewModelScope.launch {
                accessToken = refreshedToken
                val currentLogin = _loginState.value as? LoginUiState.Success
                if (currentLogin?.mode == SessionMode.OFFLINE) {
                    repository.authenticate(refreshedToken)
                    repository.completeOnboarding()
                    val cached = offlineAccountStore.load()
                    _loginState.value = LoginUiState.Success(
                        expiresInSeconds = 0,
                        onboardingComplete = true,
                        mode = SessionMode.ONLINE,
                    )
                    if (cached != null) _sessionState.value = SessionState.Online(cached)
                } else {
                    repository.refreshSession(refreshedToken)
                }
                restoreSubLoungeSession(refreshedToken)
            }
        }
        ApiClient.configureSession(tokenStore) {
            viewModelScope.launch {
                val cached = offlineAccountStore.load()
                repository.logout()
                accessToken = null
                refreshToken = null
                _loginState.value = LoginUiState.ReauthenticationRequired(cached?.displayAlias)
                _sessionState.value = SessionState.ReauthenticationRequired(cached)
            }
        }
        tokenStore.load()?.let { stored ->
            _sessionState.value = SessionState.Restoring
            val cachedAccount = offlineAccountStore.load()
            val storedRefresh = stored.refreshToken
            if (storedRefresh == null) {
                restoreAccessOnlySession(stored, cachedAccount, showLoading = true)
            } else {
                _loginState.value = LoginUiState.Loading
                viewModelScope.launch {
                    authRepository.refresh(storedRefresh)
                        .onSuccess(::acceptSession)
                        .onFailure { error ->
                            handleSessionRestoreFailure(error, cachedAccount, storedRefresh)
                        }
                }
            }
        }
        if (tokenStore.load() == null) {
            offlineAccountStore.load()?.let { cached ->
                _loginState.value = LoginUiState.ReauthenticationRequired(cached.displayAlias)
                _sessionState.value = SessionState.ReauthenticationRequired(cached)
            }
        }
        viewModelScope.launch {
            repository.state.collect { state ->
                val accountId = state.activeAccountId ?: return@collect
                val existing = offlineAccountStore.load()?.takeIf { it.accountId == accountId }
                    ?: return@collect
                offlineAccountStore.save(existing.copy(
                    displayAlias = state.profile.accountAlias,
                    avatarUrl = state.profile.avatarUrl,
                    colorHex = state.profile.colorHex,
                    melodyAlias = "",
                    musicCard = state.toExchangeMusicCard(),
                ))
                val credential = existing.offlineCredential
                val shouldRenew = credential == null ||
                    credential.displayAlias != state.profile.accountAlias ||
                    credential.expiresAt - System.currentTimeMillis() < 24 * 60 * 60 * 1_000L
                if (shouldRenew) accessToken?.let(::prepareOfflineCredential)
            }
        }
        viewModelScope.launch {
            repository.state.collect { state ->
                val handle = followedNearbyHandle ?: return@collect
                val track = state.nearbyListeners
                    .firstOrNull { it.nearbyHandle == handle }
                    ?.currentTrack
                val preview = musicPreviewPlayer.state.value
                val previewActive = preview.isPlaying || preview.isPaused || preview.isLoading
                if (!previewActive) return@collect
                if (track == null) {
                    stopMusicPreview()
                    return@collect
                }
                val trackKey = track.previewFollowKey()
                if (trackKey != followedNearbyTrackKey) {
                    playMusicPreview(
                        title = track.title,
                        artist = track.artist,
                        sourceNearbyHandle = handle,
                    )
                }
            }
        }
        viewModelScope.launch {
            realtimeClient.events.collect { event ->
                if (event is RealtimeEvent.SubLoungeUpdated &&
                    event.destination == _buildingLoungeState.value.selectedSubLoungeId
                        ?.let(RealtimeDestinations::subLounge)
                ) {
                    val snapshot = _buildingLoungeState.value.subLoungeSnapshot
                    if (event.type == "SUB_LOUNGE_DELETED") {
                        val buildingLoungeId = snapshot?.buildingLoungeId
                        clearSelectedSubLounge()
                        if (buildingLoungeId != null) {
                            val token = accessToken ?: return@collect
                            val rooms = buildingLoungeRepository.subLounges(
                                token,
                                buildingLoungeId,
                            ).getOrDefault(emptyList())
                            _buildingLoungeState.value = _buildingLoungeState.value.copy(
                                subLounges = rooms,
                                message = "하위 라운지가 삭제됐어요.",
                            )
                        }
                        return@collect
                    }
                    val reduced = snapshot?.let { SubLoungeEventReducer.reduce(it, event) }
                    if (reduced != null) {
                        latestSubLoungeSnapshotAt = event.envelope.timestamp
                        _buildingLoungeState.value = _buildingLoungeState.value.copy(
                            subLoungeSnapshot = reduced,
                        )
                    } else {
                        refreshSelectedSubLounge(silent = true)
                    }
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

    fun loadGenreCatalog() {
        if (_genreCatalogState.value.loading) return
        viewModelScope.launch {
            _genreCatalogState.value = _genreCatalogState.value.copy(loading = true, errorMessage = null)
            runCatching { musicSearchRepository.genres() }
                .onSuccess { genres ->
                    _genreCatalogState.value = if (genres.isEmpty()) {
                        GenreCatalogUiState(errorMessage = "장르 목록이 비어 있어요. 다시 불러와 주세요.")
                    } else {
                        GenreCatalogUiState(genres = genres)
                    }
                }
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    _genreCatalogState.value = GenreCatalogUiState(
                        genres = _genreCatalogState.value.genres,
                        errorMessage = "Apple Music 장르 목록을 불러오지 못했어요.",
                    )
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
        offlineAccountStore.load()?.takeIf { it.accountId == jwtSubject(response.accessToken) }?.let {
            _sessionState.value = SessionState.Online(it)
        } ?: run { _sessionState.value = SessionState.Restoring }
        if (response.onboardingComplete) prepareOfflineCredential(response.accessToken)
    }

    private fun restoreAccessOnlySession(
        stored: StoredSession,
        cachedAccount: CachedAccount?,
        showLoading: Boolean,
    ) {
        if (showLoading) _loginState.value = LoginUiState.Loading
        viewModelScope.launch {
            runCatching { sessionProfileApi.me("Bearer ${stored.accessToken}") }
                .onSuccess {
                    accessToken = stored.accessToken
                    repository.authenticate(stored.accessToken)
                    if (cachedAccount != null) repository.completeOnboarding()
                    restoreSubLoungeSession(stored.accessToken)
                    _loginState.value = LoginUiState.Success(
                        expiresInSeconds = 0,
                        onboardingComplete = cachedAccount != null || repository.state.value.isOnboardingComplete,
                    )
                    if (cachedAccount != null) _sessionState.value = SessionState.Online(cachedAccount)
                    else _sessionState.value = SessionState.Restoring
                    if (cachedAccount != null) prepareOfflineCredential(stored.accessToken)
                }
                .onFailure { error ->
                    if (showLoading) handleSessionRestoreFailure(error, cachedAccount, null)
                }
        }
    }

    private fun handleSessionRestoreFailure(
        error: Throwable,
        cachedAccount: CachedAccount?,
        storedRefresh: String?,
    ) {
        when (decideSessionRestore(error, cachedAccount != null)) {
            SessionRestoreDecision.OFFER_OFFLINE -> {
                if (cachedAccount == null) {
                    clearSession(clearOfflineAccount = false)
                    return
                }
                accessToken = null
                refreshToken = storedRefresh
                _loginState.value = LoginUiState.OfflineAvailable(cachedAccount)
                _sessionState.value = SessionState.Restoring
            }
            SessionRestoreDecision.REQUIRE_REAUTHENTICATION -> {
                tokenStore.clear()
                _loginState.value = LoginUiState.ReauthenticationRequired(cachedAccount?.displayAlias)
                _sessionState.value = SessionState.ReauthenticationRequired(cachedAccount)
            }
            SessionRestoreDecision.SIGN_OUT -> clearSession(clearOfflineAccount = false)
        }
    }

    fun startOfflineMode() {
        val account = offlineAccountStore.load() ?: return
        repository.authenticateOffline(account)
        _sessionState.value = SessionState.Offline(account)
        _loginState.value = LoginUiState.Success(
            expiresInSeconds = 0,
            onboardingComplete = true,
            mode = SessionMode.OFFLINE,
        )
    }

    fun retryOnlineSession() {
        val stored = tokenStore.load()
        val refresh = stored?.refreshToken ?: refreshToken
        if (refresh == null) {
            if (stored != null) restoreAccessOnlySession(stored, offlineAccountStore.load(), showLoading = true)
            else _loginState.value = LoginUiState.Idle
            return
        }
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            authRepository.refresh(refresh)
                .onSuccess(::acceptSession)
                .onFailure { error ->
                    val cached = offlineAccountStore.load()
                    _loginState.value = if (isNetworkFailure(error) && cached != null) {
                        LoginUiState.OfflineAvailable(cached)
                    } else LoginUiState.Error("온라인 세션을 복구하지 못했어요.")
                }
        }
    }

    private suspend fun restoreOnlineAfterOfflineMode() {
        val current = _loginState.value as? LoginUiState.Success ?: return
        if (current.mode != SessionMode.OFFLINE) return
        val stored = tokenStore.load() ?: return
        val refresh = stored.refreshToken ?: refreshToken
        if (refresh != null) authRepository.refresh(refresh).onSuccess(::acceptSession)
        else restoreAccessOnlySession(stored, offlineAccountStore.load(), showLoading = false)
        com.example.myapplication.offlineexchange.OfflineExchangeSyncScheduler.enqueue(getApplication())
    }

    fun login(email: String, password: String) {
        if (_loginState.value == LoginUiState.Loading) return
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            authRepository.login(email, password)
                .onSuccess(::acceptSession)
                .onFailure { error ->
                    _loginState.value = LoginUiState.Error(
                        when {
                            error is HttpException && error.code() == 401 ->
                                "이메일 또는 비밀번호가 일치하지 않아요."
                            isNetworkFailure(error) ->
                                "서버에 연결하지 못했어요. 인터넷 연결을 확인해 주세요."
                            else -> "로그인하지 못했습니다. 잠시 후 다시 시도해 주세요."
                        }
                    )
                }
        }
    }

    fun checkEmailAvailability(email: String) {
        if (_emailAvailabilityState.value == EmailAvailabilityUiState.Loading) return
        viewModelScope.launch {
            _emailAvailabilityState.value = EmailAvailabilityUiState.Loading
            authRepository.emailAvailability(email)
                .onSuccess { response ->
                    _emailAvailabilityState.value = if (response.available) {
                        EmailAvailabilityUiState.Available(response.email)
                    } else {
                        EmailAvailabilityUiState.Unavailable(response.email)
                    }
                }
                .onFailure {
                    _emailAvailabilityState.value = EmailAvailabilityUiState.Error(
                        "중복 확인에 실패했습니다. 잠시 후 다시 시도해 주세요."
                    )
                }
        }
    }

    fun resetEmailAvailability() {
        _emailAvailabilityState.value = EmailAvailabilityUiState.Idle
    }

    fun signup(email: String, password: String, passwordConfirmation: String, displayName: String) {
        if (_loginState.value == LoginUiState.Loading) return
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            authRepository.signup(email, password, passwordConfirmation, displayName)
                .onSuccess(::acceptSession)
                .onFailure { error ->
                    _loginState.value = LoginUiState.Error(
                        if (error is HttpException && error.code() == 409) "이미 가입된 이메일입니다."
                        else "회원가입하지 못했습니다. 입력 내용을 다시 확인해 주세요."
                    )
                }
        }
    }

    fun logout() {
        val stored = tokenStore.load()
        val access = stored?.accessToken ?: accessToken
        val refresh = stored?.refreshToken ?: refreshToken
        clearSession(clearOfflineAccount = true)
        viewModelScope.launch {
            if (access != null) authRepository.logout(access, refresh)
        }
    }

    private fun clearSession(clearOfflineAccount: Boolean = true) {
        clearSelectedSubLounge()
        repository.logout()
        accessToken = null
        restoredSubLoungeSession = false
        latestSubLoungeSnapshotAt = null
        refreshToken = null
        tokenStore.clear()
        if (clearOfflineAccount) {
            com.example.myapplication.offlineexchange.OfflineExchangeSyncScheduler.cancel(getApplication())
            nearbyExchangeManager.stop()
            offlineAccountStore.clear()
            exchangeCrypto.deleteDeviceKey()
        }
        _sessionState.value = SessionState.SignedOut
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

    fun startOfflineExchange() {
        val account = offlineAccountStore.load()
        val credential = account?.offlineCredential
        if (!repository.state.value.profile.offlineExchangeEnabled) {
            nearbyExchangeManager.unavailable("설정에서 오프라인 음악 카드 교환을 먼저 켜 주세요.")
            return
        }
        if (account == null || credential == null) {
            nearbyExchangeManager.unavailable("인터넷 연결 상태에서 오프라인 교환 인증을 먼저 준비해 주세요.")
            return
        }
        val state = repository.state.value
        nearbyExchangeManager.start(
            OfflineExchangeIdentity(
                ownerUserId = account.accountId,
                endpointName = state.profile.accountAlias,
                credential = credential,
                card = state.toExchangeMusicCard(),
            )
        )
    }

    fun connectOfflineEndpoint(endpointId: String) = nearbyExchangeManager.requestConnection(endpointId)
    fun approveOfflineConnection() = nearbyExchangeManager.approveConnection()
    fun rejectOfflineConnection() = nearbyExchangeManager.rejectConnection()
    fun stopOfflineExchange() = nearbyExchangeManager.stop()
    fun clearOfflineExchangeResult() = nearbyExchangeManager.clearResult()
    fun offlineExchangePermissionDenied() = nearbyExchangeManager.permissionDenied()

    private fun prepareOfflineCredential(token: String) {
        val accountId = jwtSubject(token) ?: return
        if (offlineCredentialPreparing) return
        offlineCredentialPreparing = true
        viewModelScope.launch {
            val result = runCatching {
                offlineExchangeApi.issueCredential(
                    "Bearer $token",
                    OfflineCredentialRequest(exchangeCrypto.publicKeyBase64()),
                )
            }.onSuccess { credential ->
                val state = repository.state.value
                val cached = CachedAccount(
                    accountId = accountId,
                    displayAlias = credential.displayAlias,
                    avatarUrl = state.profile.avatarUrl,
                    colorHex = state.profile.colorHex,
                    melodyAlias = "",
                    musicCard = state.toExchangeMusicCard(),
                    lastAuthenticatedAt = System.currentTimeMillis(),
                    offlineCredential = credential,
                )
                offlineAccountStore.save(cached)
                _sessionState.value = SessionState.Online(cached)
                com.example.myapplication.offlineexchange.OfflineExchangeSyncScheduler.enqueue(getApplication())
            }
            offlineCredentialPreparing = false
            val error = result.exceptionOrNull()
            val retryable = error != null && (
                isNetworkFailure(error) ||
                    error is HttpException && (error.code() >= 500 || error.code() == 429)
                )
            if (retryable && accessToken == token) {
                delay(30_000)
                prepareOfflineCredential(token)
            }
        }
    }

    private fun com.example.myapplication.core.model.MelodyUiState.toExchangeMusicCard() = ExchangeMusicCard(
        displayAlias = profile.accountAlias,
        trackTitle = currentTrack.title,
        trackArtist = currentTrack.artist,
        melodyAlias = "",
        genreTags = currentTrack.genreTags.ifEmpty { profile.genres },
        moodTags = currentTrack.moodTags.ifEmpty { profile.moods },
    )

    private fun isNetworkFailure(error: Throwable): Boolean = error is IOException ||
        error.cause is IOException

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

    fun searchMusic(keyword: String) {
        val query = keyword.trim()
        if (query.isBlank()) {
            clearMusicSearch()
            return
        }
        musicSearchJob?.cancel()
        musicSearchJob = viewModelScope.launch {
            _musicSearchState.value = MusicSearchUiState.Loading(query)
            runCatching { musicSearchRepository.search(query) }
                .onSuccess { results ->
                    _musicSearchState.value = if (results.isEmpty()) {
                        MusicSearchUiState.Error(query, "검색 결과가 없어요. 다른 이름으로 찾아보세요.")
                    } else {
                        MusicSearchUiState.Success(query, results)
                    }
                }
                .onFailure { error ->
                    if (error !is CancellationException) {
                        _musicSearchState.value = MusicSearchUiState.Error(
                            query,
                            "음악을 검색하지 못했어요. 연결을 확인하고 다시 시도해 주세요.",
                        )
                    }
                }
        }
    }

    fun clearMusicSearch() {
        musicSearchJob?.cancel()
        musicSearchJob = null
        _musicSearchState.value = MusicSearchUiState.Idle
    }

    fun autoPlayPublicProfileNowPlaying(profile: com.example.myapplication.core.model.PublicProfile?) {
        val currentPreview = musicPreviewPlayer.state.value
        if (currentPreview.isPlaying || currentPreview.isPaused || currentPreview.isLoading) return
        clearFollowedNearbyPreview()
        nowPlayingPreviewJob?.cancel()
        musicPreviewPlayer.stop()
        val nowPlaying = profile?.nowPlaying
            ?.takeIf { !profile.isSelf && it.isPlaying }
            ?: return
        repository.state.value.nearbyListeners
            .firstOrNull { it.profileHandle == profile.profileHandle }
            ?.let { listener ->
                followedNearbyHandle = listener.nearbyHandle
                followedNearbyTrackKey = previewFollowKey(nowPlaying.title, nowPlaying.artist)
            }
        nowPlayingPreviewJob = viewModelScope.launch {
            runCatching {
                musicSearchRepository.search("${nowPlaying.title} ${nowPlaying.artist}")
            }.onSuccess { results ->
                results.firstOrNull { it.previewUrl == results.matchingPreviewUrl(nowPlaying.title, nowPlaying.artist) }
                    ?.let { musicPreviewPlayer.play(it.previewUrl!!, nowPlaying.title, nowPlaying.artist, it.artworkUrl) }
            }.onFailure { error ->
                if (error is CancellationException) throw error
            }
        }
    }

    fun completeOnboarding(
        genres: List<String>,
        moods: List<String>,
        favoriteArtists: List<ProfileArtist>,
        signatureTracks: List<ProfileTrack>,
    ) {
        repository.completeOnboarding()
        val current = _loginState.value as? LoginUiState.Success
        if (current != null) _loginState.value = current.copy(onboardingComplete = true)
        val profile = repository.state.value.profile
        repository.updateProfile(
            profile.accountAlias, profile.colorHex, profile.bio, genres, moods,
        )
        repository.updateProfileCuration(signatureTracks, favoriteArtists)
        clearMusicSearch()
        val access = accessToken ?: return
        viewModelScope.launch {
            authRepository.completeOnboarding(access, genres, moods)
                .onSuccess { prepareOfflineCredential(access) }
        }
    }
    fun selectTab(tab: MainTab) = repository.selectTab(tab)
    fun selectNearby(handle: String?) {
        repository.selectNearby(handle)
    }
    fun enterBubbleMode() = repository.enterBubbleMode()
    fun exitBubbleMode() = repository.exitBubbleMode()
    fun openChat(roomId: String) = repository.openChat(roomId)
    fun closeChat(roomId: String) = repository.closeChat(roomId)
    fun leaveChat(roomId: String) = repository.leaveChat(roomId)
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
    fun loadSocialConnections() = repository.loadSocialConnections()
    fun unfollowRelationship(relationshipId: String) = repository.unfollowRelationship(relationshipId)
    fun loadPublicProfile(profileHandle: String) = repository.loadPublicProfile(profileHandle)
    fun loadExchangeProfile(exchangeId: String) = repository.loadExchangeProfile(exchangeId)
    fun clearPublicProfile() = repository.clearPublicProfile()
    fun followPublicProfile() = repository.followPublicProfile()
    fun sendChat(roomId: String, content: String) = repository.sendChat(roomId, content)
    fun markInboxRead() = repository.markInboxRead()
    fun clearNotifications() = repository.clearNotifications()
    fun deleteNotification(notificationId: String) = repository.deleteNotification(notificationId)
    fun setDiscoverable(enabled: Boolean) = repository.setDiscoverable(enabled)
    fun setAllowReactions(enabled: Boolean) = repository.setAllowReactions(enabled)
    fun setOfflineExchangeEnabled(enabled: Boolean) = repository.setOfflineExchangeEnabled(enabled)
    fun setMusicVisibility(label: String) = repository.setMusicVisibility(label)
    fun updatePresenceSettings(radiusMeters: Int, discoverabilityScope: String, musicVisibility: String) =
        repository.updatePresenceSettings(radiusMeters, discoverabilityScope, musicVisibility)
    fun updateProfile(displayName: String, colorHex: Long, bio: String, genres: List<String>, moods: List<String>) =
        repository.updateProfile(displayName, colorHex, bio, genres, moods)
    fun randomizeAvatar() = repository.randomizeAvatar()
    fun updateProfileCuration(signatureTracks: List<ProfileTrack>, favoriteArtists: List<ProfileArtist>) =
        repository.updateProfileCuration(signatureTracks, favoriteArtists)
    fun updateProfilePrivacy(settings: ProfilePrivacySettings) =
        repository.updateProfilePrivacy(settings)
    fun playMusicPreview(
        title: String,
        artist: String,
        previewUrl: String? = null,
        artworkUrl: String? = null,
        sourceNearbyHandle: String? = null,
    ) {
        previewLookupJob?.cancel()
        if (sourceNearbyHandle == null) {
            clearFollowedNearbyPreview()
        } else {
            followedNearbyHandle = sourceNearbyHandle
            followedNearbyTrackKey = previewFollowKey(title, artist)
        }
        if (!previewUrl.isNullOrBlank()) {
            musicPreviewPlayer.play(previewUrl, title, artist, artworkUrl)
            return
        }
        previewLookupJob = viewModelScope.launch {
            runCatching { musicSearchRepository.search("$title $artist") }
                .onSuccess { results ->
                    val normalizedTitle = title.trim().lowercase()
                    val match = results.firstOrNull {
                        it.title.trim().lowercase() == normalizedTitle &&
                            it.artist.contains(artist, ignoreCase = true)
                    } ?: results.firstOrNull { it.previewUrl != null }
                    val url = match?.previewUrl
                    if (url == null) {
                        musicPreviewPlayer.stop("이 곡은 30초 미리듣기를 제공하지 않아요.")
                    } else {
                        musicPreviewPlayer.play(url, title, artist, match.artworkUrl ?: artworkUrl)
                    }
                }
                .onFailure { musicPreviewPlayer.stop("미리듣기를 찾지 못했어요.") }
        }
    }

    fun stopMusicPreview() {
        previewLookupJob?.cancel()
        previewLookupJob = null
        clearFollowedNearbyPreview()
        musicPreviewPlayer.stop()
    }
    fun toggleMusicPreviewPause() = musicPreviewPlayer.togglePauseResume()

    private fun clearFollowedNearbyPreview() {
        followedNearbyHandle = null
        followedNearbyTrackKey = null
    }

    private fun Track.previewFollowKey(): String = previewFollowKey(title, artist)

    private fun previewFollowKey(title: String, artist: String): String =
        "${title.trim().lowercase()}\u0000${artist.trim().lowercase()}"

    fun stopProfileAudio() {
        stopNowPlayingPreview()
    }

    private fun stopNowPlayingPreview() {
        nowPlayingPreviewJob?.cancel()
        nowPlayingPreviewJob = null
        musicPreviewPlayer.stop()
    }

    fun refreshBuildingLounges(latitude: Double, longitude: Double, accuracyMeters: Float? = null) {
        val token = accessToken
        if (token.isNullOrBlank()) {
            _buildingLoungeState.value = _buildingLoungeState.value.copy(
                userLocation = UserMapLocation(latitude, longitude, accuracyMeters),
                message = "로그인 후 실제 건물 라운지를 확인할 수 있어요."
            )
            return
        }
        val wifi = when (val result = WifiFingerprintProvider.currentResult(getApplication())) {
            is WifiIdentityResult.Available -> result.identity
            WifiIdentityResult.NotConnected -> {
                _buildingLoungeState.value = _buildingLoungeState.value.copy(
                    userLocation = UserMapLocation(latitude, longitude, accuracyMeters),
                    lounges = emptyList(),
                    message = "Wi-Fi에 연결한 뒤 라운지를 다시 찾아주세요."
                )
                return
            }
            WifiIdentityResult.SsidUnavailable -> {
                _buildingLoungeState.value = _buildingLoungeState.value.copy(
                    userLocation = UserMapLocation(latitude, longitude, accuracyMeters),
                    lounges = emptyList(),
                    message = "Wi-Fi 이름(SSID)을 확인하지 못했어요. Wi-Fi 연결을 껐다 켠 뒤 다시 시도해 주세요."
                )
                return
            }
        }
        viewModelScope.launch {
            _buildingLoungeState.value = _buildingLoungeState.value.copy(
                loading = true,
                loadFailed = false,
                userLocation = UserMapLocation(latitude, longitude, accuracyMeters),
                message = null
            )
            buildingLoungeRepository.nearby(token, latitude, longitude, wifi.fingerprint, wifi.displayName)
                .onSuccess { lounges ->
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(
                        loading = false,
                        loadFailed = false,
                        lounges = lounges,
                        message = if (lounges.isEmpty()) "주변에 이용 가능한 실제 건물 라운지가 없어요." else null
                    )
                }
                .onFailure {
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(
                        loading = false,
                        loadFailed = true,
                        lounges = emptyList(),
                        message = "주변 건물 라운지를 불러오지 못했어요."
                    )
                }
        }
    }

    fun setBuildingLoungeLocationUnavailable() {
        _buildingLoungeState.value = _buildingLoungeState.value.copy(
            loading = false,
            loadFailed = false,
            lounges = emptyList(),
            message = "현재 위치를 가져오지 못했어요. 위치 서비스를 확인하고 다시 시도해 주세요."
        )
    }

    fun enterBuildingLounge(loungeId: String) {
        val token = accessToken ?: return
        val location = _buildingLoungeState.value.userLocation ?: return
        val wifi = WifiFingerprintProvider.current(getApplication()) ?: run {
            _buildingLoungeState.value = _buildingLoungeState.value.copy(message = "Wi-Fi 연결을 확인해 주세요.")
            return
        }
        viewModelScope.launch {
            buildingLoungeRepository.enter(
                token,
                loungeId,
                location.latitude,
                location.longitude,
                location.accuracyMeters,
                wifi.fingerprint
            ).onSuccess {
                val subLounges = buildingLoungeRepository.subLounges(token, loungeId).getOrDefault(emptyList())
                _buildingLoungeState.value = _buildingLoungeState.value.copy(
                    enteredLoungeId = loungeId,
                    subLounges = subLounges,
                    message = "${it.lounge.name}에 입장했어요."
                )
            }.onFailure {
                _buildingLoungeState.value = _buildingLoungeState.value.copy(
                    message = "건물 입장 반경 안으로 이동해 주세요."
                )
            }
        }
    }

    fun heartbeatBuildingLounge(latitude: Double, longitude: Double, accuracyMeters: Float? = null) {
        val token = accessToken ?: return
        val loungeId = _buildingLoungeState.value.enteredLoungeId ?: return
        val wifi = WifiFingerprintProvider.current(getApplication()) ?: run {
            leaveBuildingLounge()
            return
        }
        viewModelScope.launch {
            buildingLoungeRepository.heartbeat(token, loungeId, latitude, longitude, accuracyMeters, wifi.fingerprint)
                .onSuccess { response ->
                    if (response.forcedExit) clearSelectedSubLounge()
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(
                        userLocation = UserMapLocation(latitude, longitude, accuracyMeters),
                        enteredLoungeId = if (response.forcedExit) null else loungeId,
                        subLounges = if (response.forcedExit) emptyList() else _buildingLoungeState.value.subLounges,
                        selectedSubLoungeId = if (response.forcedExit) null else _buildingLoungeState.value.selectedSubLoungeId,
                        subLoungeSnapshot = if (response.forcedExit) null else _buildingLoungeState.value.subLoungeSnapshot,
                        message = if (response.forcedExit) "건물 반경을 벗어나 자동 퇴장했어요." else null
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
                message = "건물 라운지에서 나왔어요."
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
                        message = "${created.title}을 만들고 입장했어요."
                    )
                    openSubLounge(created.id)
                }
                .onFailure {
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(message = "하위 라운지를 만들지 못했어요.")
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
        sendTrackToLounge(track.title, track.artist, message)
    }

    fun deleteSubLounge() {
        val token = accessToken ?: return
        val subLoungeId = _buildingLoungeState.value.selectedSubLoungeId ?: return
        val buildingLoungeId = _buildingLoungeState.value.subLoungeSnapshot?.buildingLoungeId ?: return
        viewModelScope.launch {
            buildingLoungeRepository.deleteSubLounge(token, subLoungeId)
                .onSuccess {
                    clearSelectedSubLounge()
                    val rooms = buildingLoungeRepository.subLounges(token, buildingLoungeId)
                        .getOrDefault(emptyList())
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(
                        subLounges = rooms,
                        message = "하위 라운지를 삭제했어요.",
                    )
                }
                .onFailure {
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(
                        message = "하위 라운지를 삭제하지 못했어요.",
                    )
                }
        }
    }

    fun searchLoungeTracks(query: String) {
        val token = accessToken ?: return
        val normalized = query.trim()
        if (normalized.length < 2) {
            _buildingLoungeState.value = _buildingLoungeState.value.copy(
                trackSearchResults = emptyList(),
                message = "검색어를 2자 이상 입력해 주세요.",
            )
            return
        }
        viewModelScope.launch {
            _buildingLoungeState.value = _buildingLoungeState.value.copy(trackSearchLoading = true, message = null)
            buildingLoungeRepository.searchMusic(token, normalized)
                .onSuccess { results ->
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(
                        trackSearchLoading = false,
                        trackSearchResults = results,
                        message = if (results.isEmpty()) "검색 결과가 없어요." else null,
                    )
                }
                .onFailure {
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(
                        trackSearchLoading = false,
                        trackSearchResults = emptyList(),
                        message = "노래를 검색하지 못했어요.",
                    )
                }
        }
    }

    fun sendSearchedTrackToLounge(track: LoungeMusicSearchResultDto, message: String?) {
        sendTrackToLounge(track.title, track.artistName, message)
    }

    private fun sendTrackToLounge(trackTitle: String, artistName: String, message: String?) {
        val token = accessToken ?: return
        val subLoungeId = _buildingLoungeState.value.selectedSubLoungeId ?: return
        viewModelScope.launch {
            buildingLoungeRepository.addCard(
                token,
                subLoungeId,
                CreateLoungeCardRequestDto(
                    clientCardId = UUID.randomUUID().toString(),
                    trackTitle = trackTitle,
                    artistName = artistName,
                    message = message,
                ),
            ).onSuccess {
                _buildingLoungeState.value = _buildingLoungeState.value.copy(trackSearchResults = emptyList())
                refreshSelectedSubLounge(silent = true)
            }.onFailure {
                _buildingLoungeState.value = _buildingLoungeState.value.copy(message = "추천 카드를 보내지 못했어요.")
            }
        }
    }

    fun deleteLoungeCard(cardId: String) {
        val token = accessToken ?: return
        viewModelScope.launch {
            buildingLoungeRepository.deleteCard(token, cardId)
                .onSuccess { refreshSelectedSubLounge(silent = true) }
                .onFailure {
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(message = "추천 카드를 삭제하지 못했어요.")
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
            trackSearchLoading = false,
            trackSearchResults = emptyList(),
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

    fun syncExchange(exchangeId: String) = repository.syncExchange(exchangeId)
    fun deleteExchange(exchangeId: String) = repository.deleteExchange(exchangeId)
    fun clearFeedback() = repository.clearFeedback()

    override fun onCleared() {
        loungeFallbackJob?.cancel()
        loungeSnapshotJob?.cancel()
        removeAccessTokenListener?.invoke()
        removeAccessTokenListener = null
        musicPreviewPlayer.release()
        nearbyExchangeManager.stop()
        runCatching { connectivityManager.unregisterNetworkCallback(connectivityCallback) }
        repository.close()
        super.onCleared()
    }
}

internal fun List<MusicSearchResult>.matchingPreviewUrl(title: String, artist: String): String? {
    val normalizedTitle = title.normalizedMusicIdentity()
    val normalizedArtist = artist.normalizedMusicIdentity()
    return firstOrNull { result ->
        result.title.normalizedMusicIdentity() == normalizedTitle &&
            result.artist.normalizedMusicIdentity() == normalizedArtist &&
            result.previewUrl?.startsWith("https://") == true
    }?.previewUrl
}

private fun String.normalizedMusicIdentity(): String =
    lowercase().filter(Char::isLetterOrDigit)
