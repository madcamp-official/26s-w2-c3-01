package com.example.myapplication.ui

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.core.model.MainTab
import com.example.myapplication.core.model.ConnectionState
import com.example.myapplication.core.model.MelodyAliasCandidate
import com.example.myapplication.core.model.Track
import com.example.myapplication.core.model.ReportReason
import com.example.myapplication.core.model.SessionMode
import com.example.myapplication.core.model.ProfileArtist
import com.example.myapplication.core.model.MusicSearchResult
import com.example.myapplication.core.model.ProfilePrivacySettings
import com.example.myapplication.core.model.ProfileTrack
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
import com.example.myapplication.data.remote.MusicSearchRepository
import com.example.myapplication.data.remote.LyriaAliasGenerateRequest
import com.example.myapplication.data.remote.LyriaMusicRepository
import com.example.myapplication.data.remote.LyriaMusicResponse
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

sealed interface MusicSearchUiState {
    data object Idle : MusicSearchUiState
    data class Loading(val query: String) : MusicSearchUiState
    data class Success(
        val query: String,
        val results: List<MusicSearchResult>,
    ) : MusicSearchUiState
    data class Error(val query: String, val message: String) : MusicSearchUiState
}

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
    val realtimeState: ConnectionState = ConnectionState.OFFLINE,
    val message: String? = null
)

class MelodyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MelodyRepository = DemoMelodyRepository(application)
    private val authRepository by lazy { AuthRepository() }
    private val melodyAliasPreviewPlayer = MelodyAliasPreviewPlayer()
    private val melodyAliasRepository by lazy { MelodyAliasRepository() }
    private val musicSearchRepository by lazy { MusicSearchRepository() }
    private val lyriaRepository by lazy { LyriaMusicRepository() }
    private val buildingLoungeRepository by lazy { BuildingLoungeRepository() }
    private val realtimeClient = (application as MelodyApplication).realtimeClient
    private val presenceCoordinator = PresenceSyncCoordinator.get(application)
    private val lyriaClipPlayer = LyriaClipPlayer(application)
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
    private val _melodyAliasGenerationState = MutableStateFlow<MelodyAliasGenerationState>(MelodyAliasGenerationState.Idle)
    private val _lyriaGenerationState = MutableStateFlow<LyriaGenerationState>(LyriaGenerationState.Idle)
    private val _buildingLoungeState = MutableStateFlow(BuildingLoungeUiState())
    private val _musicSearchState = MutableStateFlow<MusicSearchUiState>(MusicSearchUiState.Idle)
    private var musicSearchJob: Job? = null

    val uiState = repository.state
    val loginState = _loginState.asStateFlow()
    val sessionState = _sessionState.asStateFlow()
    val emailAvailabilityState = _emailAvailabilityState.asStateFlow()
    val melodyAliasGenerationState = _melodyAliasGenerationState.asStateFlow()
    val lyriaGenerationState = _lyriaGenerationState.asStateFlow()
    val buildingLoungeState = _buildingLoungeState.asStateFlow()
    val musicSearchState = _musicSearchState.asStateFlow()
    val exchangeState = nearbyExchangeManager.state

    init {
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
                    avatarDataUrl = state.profile.avatarDataUrl,
                    colorHex = state.profile.colorHex,
                    melodyAlias = state.profile.melodyNotes.joinToString(" · "),
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
            realtimeClient.events.collect { event ->
                if (event is RealtimeEvent.SubLoungeUpdated &&
                    event.destination == _buildingLoungeState.value.selectedSubLoungeId
                        ?.let(RealtimeDestinations::subLounge)
                ) {
                    val snapshot = _buildingLoungeState.value.subLoungeSnapshot
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
                    avatarDataUrl = state.profile.avatarDataUrl,
                    colorHex = state.profile.colorHex,
                    melodyAlias = state.profile.melodyNotes.joinToString(" · "),
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
        melodyAlias = profile.melodyNotes.joinToString(" · "),
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
            profile.accountAlias, profile.colorHex, profile.bio, profile.avatarDataUrl,
            genres, moods,
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
    fun selectNearby(handle: String?) = repository.selectNearby(handle)
    fun enterBubbleMode() = repository.enterBubbleMode()
    fun exitBubbleMode() = repository.exitBubbleMode()
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
    fun loadSocialConnections() = repository.loadSocialConnections()
    fun unfollowRelationship(relationshipId: String) = repository.unfollowRelationship(relationshipId)
    fun loadPublicProfile(profileHandle: String) = repository.loadPublicProfile(profileHandle)
    fun loadExchangeProfile(exchangeId: String) = repository.loadExchangeProfile(exchangeId)
    fun clearPublicProfile() = repository.clearPublicProfile()
    fun followPublicProfile() = repository.followPublicProfile()
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
    fun updateProfileCuration(signatureTracks: List<ProfileTrack>, favoriteArtists: List<ProfileArtist>) =
        repository.updateProfileCuration(signatureTracks, favoriteArtists)
    fun updateProfilePrivacy(settings: ProfilePrivacySettings) =
        repository.updateProfilePrivacy(settings)
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
    fun saveLyriaAsProfileMusic() {
        val song = (_lyriaGenerationState.value as? LyriaGenerationState.Success)?.song ?: return
        val candidateKey = song.candidateKey ?: run {
            _lyriaGenerationState.value = LyriaGenerationState.Error("저장할 음악 정보를 찾지 못했어요.")
            return
        }
        repository.setProfileMusic(candidateKey, song.description)
    }

    fun playProfileMusic() {
        val url = repository.state.value.profile.profileMusicUrl ?: return
        playProfileMusicUrl(url)
    }

    fun playProfileMusicUrl(url: String) {
        lyriaClipPlayer.loadUrl(url)
        lyriaClipPlayer.playFull()
    }

    fun deleteProfileMusic() = repository.deleteProfileMusic()
    fun resetLyriaSong() {
        lyriaClipPlayer.stop()
        _lyriaGenerationState.value = LyriaGenerationState.Idle
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
        viewModelScope.launch {
            _buildingLoungeState.value = _buildingLoungeState.value.copy(
                loading = true,
                loadFailed = false,
                userLocation = UserMapLocation(latitude, longitude, accuracyMeters),
                message = null
            )
            buildingLoungeRepository.nearby(token, latitude, longitude)
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
    fun syncExchange(exchangeId: String) = repository.syncExchange(exchangeId)
    fun deleteExchange(exchangeId: String) = repository.deleteExchange(exchangeId)
    fun clearFeedback() = repository.clearFeedback()

    override fun onCleared() {
        loungeFallbackJob?.cancel()
        loungeSnapshotJob?.cancel()
        removeAccessTokenListener?.invoke()
        removeAccessTokenListener = null
        melodyAliasPreviewPlayer.release()
        lyriaClipPlayer.release()
        nearbyExchangeManager.stop()
        runCatching { connectivityManager.unregisterNetworkCallback(connectivityCallback) }
        repository.close()
        super.onCleared()
    }
}
