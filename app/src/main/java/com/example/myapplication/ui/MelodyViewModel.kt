package com.example.myapplication.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.core.model.MainTab
import com.example.myapplication.core.model.ConnectionState
import com.example.myapplication.core.model.NearbyListener
import com.example.myapplication.core.model.Track
import com.example.myapplication.core.model.ReportReason
import com.example.myapplication.core.model.ProfileArtist
import com.example.myapplication.core.model.MusicSearchResult
import com.example.myapplication.core.model.ProfilePrivacySettings
import com.example.myapplication.core.model.ProfileTrack
import com.example.myapplication.audio.MusicPreviewPlayer
import com.example.myapplication.data.DemoMelodyRepository
import com.example.myapplication.MelodyApplication
import com.example.myapplication.data.MelodyRepository
import com.example.myapplication.data.AvatarCustomization
import com.example.myapplication.data.presence.PresenceSyncCoordinator
import com.example.myapplication.data.realtime.RealtimeConnectionState
import com.example.myapplication.data.realtime.RealtimeDestinations
import com.example.myapplication.data.realtime.RealtimeEvent
import com.example.myapplication.data.remote.AuthRepository
import com.example.myapplication.data.remote.ApiClient
import com.example.myapplication.data.remote.BuildingLoungeRepository
import com.example.myapplication.data.remote.BuildingLoungeSummaryDto
import com.example.myapplication.data.remote.CreateLoungeCardRequestDto
import com.example.myapplication.data.remote.LoungeRecommendationCardDto
import com.example.myapplication.data.remote.SubLoungeSnapshotDto
import com.example.myapplication.data.remote.MusicSearchRepository
import com.example.myapplication.data.remote.LoungeMusicSearchResultDto
import com.example.myapplication.data.remote.TokenResponse
import com.example.myapplication.data.local.SecureTokenStore
import com.example.myapplication.data.local.StoredSession
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

private const val NEARBY_PREVIEW_TRACK_STABILITY_MILLIS = 1_500L

internal fun List<NearbyListener>.findPreviewSource(
    nearbyHandle: String?,
    profileHandle: String?,
): NearbyListener? {
    val normalizedProfile = profileHandle?.trim()?.takeIf(String::isNotEmpty)?.lowercase()
    return normalizedProfile?.let { profile ->
        firstOrNull { it.profileHandle?.trim()?.lowercase() == profile }
    } ?: nearbyHandle?.let { handle -> firstOrNull { it.nearbyHandle == handle } }
}

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Success(
        val expiresInSeconds: Long,
        val isNewUser: Boolean = false,
        val onboardingComplete: Boolean = false,
    ) : LoginUiState
    data class ReauthenticationRequired(val accountAlias: String?) : LoginUiState
    data class Error(val message: String) : LoginUiState
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
    val cardSubmitting: Boolean = false,
    val trackSearchResults: List<LoungeMusicSearchResultDto> = emptyList(),
    val currentDetectedTrack: Track? = null,
    val realtimeState: ConnectionState = ConnectionState.DISCONNECTED,
    val message: String? = null
)

internal class LoungeExitGraceTracker(
    private val graceMillis: Long = 60_000L,
) {
    private var outsideSinceMillis: Long? = null

    fun shouldExit(inside: Boolean, nowMillis: Long): Boolean {
        if (inside) {
            reset()
            return false
        }
        val outsideSince = outsideSinceMillis ?: nowMillis.also { outsideSinceMillis = it }
        return nowMillis - outsideSince >= graceMillis
    }

    fun reset() {
        outsideSinceMillis = null
    }
}

class MelodyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MelodyRepository = DemoMelodyRepository(application)
    private val authRepository by lazy { AuthRepository() }
    private val musicSearchRepository by lazy { MusicSearchRepository() }
    private val buildingLoungeRepository by lazy { BuildingLoungeRepository() }
    private val locationLoungeRepository by lazy { com.example.myapplication.data.remote.LocationLoungeRepository() }
    private val realtimeClient = (application as MelodyApplication).realtimeClient
    private val presenceCoordinator = PresenceSyncCoordinator.get(application)
    private val musicPreviewPlayer = MusicPreviewPlayer(application)
    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    private val _emailAvailabilityState = MutableStateFlow<EmailAvailabilityUiState>(EmailAvailabilityUiState.Idle)
    @Volatile private var accessToken: String? = null
    private var refreshToken: String? = null
    private var removeAccessTokenListener: (() -> Unit)? = null
    private var restoredSubLoungeSession = false
    private var loungeFallbackJob: Job? = null
    private var loungeSnapshotJob: Job? = null
    private var latestSubLoungeSnapshotAt: String? = null
    private var pendingAutoPlaySubLoungeId: String? = null
    private val loungeExitGraceTracker = LoungeExitGraceTracker()
    private val tokenStore = SecureTokenStore(application)
    private val sessionProfileApi by lazy { ApiClient.createProfileApi() }
    private val _buildingLoungeState = MutableStateFlow(BuildingLoungeUiState())
    private val _musicSearchState = MutableStateFlow<MusicSearchUiState>(MusicSearchUiState.Idle)
    private val _genreCatalogState = MutableStateFlow(GenreCatalogUiState())
    private var musicSearchJob: Job? = null
    private var nowPlayingPreviewJob: Job? = null
    private var previewLookupJob: Job? = null
    private var followedNearbyTransitionJob: Job? = null
    private var followedNearbyHandle: String? = null
    private var followedNearbyProfileHandle: String? = null
    private var followedNearbyTrackKey: String? = null

    val uiState = repository.state
    val loginState = _loginState.asStateFlow()
    val emailAvailabilityState = _emailAvailabilityState.asStateFlow()
    val buildingLoungeState = _buildingLoungeState.asStateFlow()
    val musicSearchState = _musicSearchState.asStateFlow()
    val genreCatalogState = _genreCatalogState.asStateFlow()
    val previewPlaybackState = musicPreviewPlayer.state

    init {
        loadGenreCatalog()
        removeAccessTokenListener = ApiClient.addAccessTokenListener { refreshedToken ->
            viewModelScope.launch {
                accessToken = refreshedToken
                repository.refreshSession(refreshedToken)
                restoreSubLoungeSession(refreshedToken)
            }
        }
        ApiClient.configureSession(tokenStore) {
            viewModelScope.launch {
                repository.logout()
                accessToken = null
                refreshToken = null
                _loginState.value = LoginUiState.ReauthenticationRequired(null)
            }
        }
        tokenStore.load()?.let { stored ->
            val storedRefresh = stored.refreshToken
            if (storedRefresh == null) {
                restoreAccessOnlySession(stored, showLoading = true)
            } else {
                _loginState.value = LoginUiState.Loading
                viewModelScope.launch {
                    authRepository.refresh(storedRefresh)
                        .onSuccess(::acceptSession)
                        .onFailure(::handleSessionRestoreFailure)
                }
            }
        }
        viewModelScope.launch {
            presenceCoordinator.detectedPlayback.collect { playback ->
                _buildingLoungeState.value = _buildingLoungeState.value.copy(
                    currentDetectedTrack = playback.track,
                )
            }
        }
        viewModelScope.launch {
            repository.state.collect { state ->
                if (followedNearbyHandle == null && followedNearbyProfileHandle == null) {
                    return@collect
                }
                val listener = state.nearbyListeners.findPreviewSource(
                    followedNearbyHandle,
                    followedNearbyProfileHandle,
                )
                listener?.let {
                    followedNearbyHandle = it.nearbyHandle
                    followedNearbyProfileHandle = it.profileHandle ?: followedNearbyProfileHandle
                }
                val track = listener?.currentTrack
                val preview = musicPreviewPlayer.state.value
                val previewActive = preview.isPlaying || preview.isPaused || preview.isLoading
                if (!previewActive) {
                    followedNearbyTransitionJob?.cancel()
                    followedNearbyTransitionJob = null
                    return@collect
                }
                val trackKey = track?.previewFollowKey()
                if (trackKey == followedNearbyTrackKey) {
                    followedNearbyTransitionJob?.cancel()
                    followedNearbyTransitionJob = null
                    return@collect
                }
                followedNearbyTransitionJob?.cancel()
                followedNearbyTransitionJob = viewModelScope.launch {
                    delay(NEARBY_PREVIEW_TRACK_STABILITY_MILLIS)
                    val latestListener = repository.state.value.nearbyListeners.findPreviewSource(
                        followedNearbyHandle,
                        followedNearbyProfileHandle,
                    )
                    latestListener?.let {
                        followedNearbyHandle = it.nearbyHandle
                        followedNearbyProfileHandle = it.profileHandle ?: followedNearbyProfileHandle
                    }
                    val latestTrack = latestListener?.currentTrack
                    followedNearbyTransitionJob = null
                    if (latestTrack == null) {
                        stopMusicPreview()
                    } else if (latestTrack.previewFollowKey() != followedNearbyTrackKey) {
                        playMusicPreview(
                            title = latestTrack.title,
                            artist = latestTrack.artist,
                            sourceNearbyHandle = latestListener.nearbyHandle,
                            sourceNearbyProfileHandle = latestListener.profileHandle,
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            realtimeClient.events.collect { event ->
                if (event is RealtimeEvent.LocationLoungeUpdated) {
                    _buildingLoungeState.value.userLocation?.let { location ->
                        refreshBuildingLounges(location.latitude, location.longitude, location.accuracyMeters)
                    }
                }
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
                    RealtimeConnectionState.Disconnected -> ConnectionState.DISCONNECTED
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
                        errorMessage = "장르 목록을 준비하지 못했어요.",
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
    }

    private fun restoreAccessOnlySession(
        stored: StoredSession,
        showLoading: Boolean,
    ) {
        if (showLoading) _loginState.value = LoginUiState.Loading
        viewModelScope.launch {
            runCatching { sessionProfileApi.me("Bearer ${stored.accessToken}") }
                .onSuccess {
                    accessToken = stored.accessToken
                    repository.authenticate(stored.accessToken)
                    restoreSubLoungeSession(stored.accessToken)
                    _loginState.value = LoginUiState.Success(
                        expiresInSeconds = 0,
                        onboardingComplete = repository.state.value.isOnboardingComplete,
                    )
                }
                .onFailure { error ->
                    if (showLoading) handleSessionRestoreFailure(error)
                }
        }
    }

    private fun handleSessionRestoreFailure(error: Throwable) {
        tokenStore.clear()
        accessToken = null
        refreshToken = null
        _loginState.value = if (error is HttpException && error.code() in setOf(401, 403)) {
            LoginUiState.ReauthenticationRequired(null)
        } else {
            LoginUiState.Error("온라인 세션을 복구하지 못했어요. 다시 로그인해 주세요.")
        }
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
        clearSession()
        viewModelScope.launch {
            if (access != null) authRepository.logout(access, refresh)
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
                followedNearbyProfileHandle = listener.profileHandle
                followedNearbyTrackKey = previewFollowKey(nowPlaying.title, nowPlaying.artist)
            }
        nowPlayingPreviewJob = viewModelScope.launch {
            musicPreviewPlayer.beginLookup(nowPlaying.title, nowPlaying.artist, nowPlaying.artworkUrl)
            runCatching { musicSearchRepository.findTrackMedia(nowPlaying.title, nowPlaying.artist) }
                .onSuccess { match ->
                    match?.previewUrl?.let { previewUrl ->
                        musicPreviewPlayer.play(
                            previewUrl,
                            nowPlaying.title,
                            nowPlaying.artist,
                            match.artworkUrl ?: nowPlaying.artworkUrl,
                        )
                    } ?: musicPreviewPlayer.stop("정확한 미리듣기를 찾지 못했어요.")
                }
                .onFailure { musicPreviewPlayer.stop("미리듣기를 찾지 못했어요.") }
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
        }
    }
    fun selectTab(tab: MainTab) = repository.selectTab(tab)
    fun selectNearby(handle: String?) {
        repository.selectNearby(handle)
    }
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
    fun clearPublicProfile() = repository.clearPublicProfile()
    fun followPublicProfile() = repository.followPublicProfile()
    fun sendChat(roomId: String, content: String) = repository.sendChat(roomId, content)
    fun markInboxRead() = repository.markInboxRead()
    fun clearNotifications() = repository.clearNotifications()
    fun deleteNotification(notificationId: String) = repository.deleteNotification(notificationId)
    fun setDiscoverable(enabled: Boolean) = repository.setDiscoverable(enabled)
    fun setAllowReactions(enabled: Boolean) = repository.setAllowReactions(enabled)
    fun setMusicVisibility(label: String) = repository.setMusicVisibility(label)
    fun updatePresenceSettings(radiusMeters: Int, discoverabilityScope: String, musicVisibility: String) =
        repository.updatePresenceSettings(radiusMeters, discoverabilityScope, musicVisibility)
    fun updateProfile(displayName: String, colorHex: Long, bio: String, genres: List<String>, moods: List<String>) =
        repository.updateProfile(displayName, colorHex, bio, genres, moods)
    fun customizeAvatar(customization: AvatarCustomization) = repository.customizeAvatar(customization)
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
        sourceNearbyProfileHandle: String? = null,
    ) {
        previewLookupJob?.cancel()
        followedNearbyTransitionJob?.cancel()
        followedNearbyTransitionJob = null
        if (sourceNearbyHandle == null) {
            clearFollowedNearbyPreview()
        } else {
            followedNearbyHandle = sourceNearbyHandle
            followedNearbyProfileHandle = sourceNearbyProfileHandle
                ?: repository.state.value.nearbyListeners
                    .firstOrNull { it.nearbyHandle == sourceNearbyHandle }
                    ?.profileHandle
            followedNearbyTrackKey = previewFollowKey(title, artist)
        }
        if (!previewUrl.isNullOrBlank()) {
            musicPreviewPlayer.play(previewUrl, title, artist, artworkUrl)
            return
        }
        musicPreviewPlayer.beginLookup(title, artist, artworkUrl)
        previewLookupJob = viewModelScope.launch {
            runCatching { musicSearchRepository.findTrackMedia(title, artist) }
                .onSuccess { match ->
                    val url = match?.previewUrl
                    if (url == null) {
                        musicPreviewPlayer.stop("정확한 미리듣기를 찾지 못했어요.")
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
        followedNearbyTransitionJob?.cancel()
        followedNearbyTransitionJob = null
        followedNearbyHandle = null
        followedNearbyProfileHandle = null
        followedNearbyTrackKey = null
    }

    private fun Track.previewFollowKey(): String = previewFollowKey(title, artist)

    private fun previewFollowKey(title: String, artist: String): String =
        "${title.trim().lowercase()}\u0000${artist.trim().lowercase()}"

    fun refreshBuildingLounges(latitude: Double, longitude: Double, accuracyMeters: Float? = null) {
        val token = accessToken
        if (token.isNullOrBlank()) {
            _buildingLoungeState.value = _buildingLoungeState.value.copy(
                userLocation = UserMapLocation(latitude, longitude, accuracyMeters),
                message = "로그인 후 위치 라운지를 확인할 수 있어요."
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
            locationLoungeRepository.snapshot(token, latitude, longitude)
                .onSuccess { lounges ->
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(
                        loading = false,
                        loadFailed = false,
                        lounges = lounges,
                        message = if (lounges.isEmpty()) "주변에 생성된 위치 라운지가 없어요." else null
                    )
                }
                .onFailure {
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(
                        loading = false,
                        loadFailed = true,
                        lounges = emptyList(),
                        message = "위치 라운지를 불러오지 못했어요."
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
        viewModelScope.launch {
            locationLoungeRepository.enter(
                token,
                loungeId,
                location.latitude,
                location.longitude,
            ).onSuccess { entry ->
                loungeExitGraceTracker.reset()
                _buildingLoungeState.value = _buildingLoungeState.value.copy(
                    enteredLoungeId = loungeId,
                    subLounges = entry.rooms,
                    message = "${entry.lounge.name}에 입장했어요."
                )
            }.onFailure {
                _buildingLoungeState.value = _buildingLoungeState.value.copy(
                    message = if (it is IllegalStateException) {
                        "현재 위치가 라운지 반경 밖이에요. 위치를 새로고침해 주세요."
                    } else {
                        "라운지 입장을 확인하지 못했어요. 잠시 후 다시 시도해 주세요."
                    }
                )
            }
        }
    }

    fun heartbeatBuildingLounge(latitude: Double, longitude: Double, accuracyMeters: Float? = null) {
        val token = accessToken ?: return
        val loungeId = _buildingLoungeState.value.enteredLoungeId ?: return
        viewModelScope.launch {
            locationLoungeRepository.isInside(token, loungeId, latitude, longitude)
                .onSuccess { inside ->
                    if (_buildingLoungeState.value.enteredLoungeId != loungeId) return@onSuccess
                    val shouldExit = loungeExitGraceTracker.shouldExit(
                        inside = inside,
                        nowMillis = android.os.SystemClock.elapsedRealtime(),
                    )
                    if (shouldExit) clearSelectedSubLounge()
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(
                        userLocation = UserMapLocation(latitude, longitude, accuracyMeters),
                        enteredLoungeId = if (shouldExit) null else loungeId,
                        subLounges = if (shouldExit) emptyList() else _buildingLoungeState.value.subLounges,
                        selectedSubLoungeId = if (shouldExit) null else _buildingLoungeState.value.selectedSubLoungeId,
                        subLoungeSnapshot = if (shouldExit) null else _buildingLoungeState.value.subLoungeSnapshot,
                        message = when {
                            shouldExit -> "라운지 반경 밖에 1분 이상 머물러 자동 퇴장했어요."
                            inside -> null
                            else -> "라운지 반경 밖이에요. 1분 안에 돌아오면 입장이 유지돼요."
                        },
                    )
                }
                .onFailure {
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(
                        userLocation = UserMapLocation(latitude, longitude, accuracyMeters),
                        message = "라운지 위치를 다시 확인하지 못했어요.",
                    )
                }
        }
    }

    fun leaveBuildingLounge() {
        if (_buildingLoungeState.value.enteredLoungeId == null) return
        viewModelScope.launch {
            loungeExitGraceTracker.reset()
            clearSelectedSubLounge()
            _buildingLoungeState.value = _buildingLoungeState.value.copy(
                enteredLoungeId = null,
                subLounges = emptyList(),
                selectedSubLoungeId = null,
                subLoungeSnapshot = null,
                message = "위치 라운지에서 나왔어요."
            )
        }
    }

    fun createBuildingSubLounge(title: String, style: String?) {
        val token = accessToken ?: return
        val loungeId = _buildingLoungeState.value.enteredLoungeId ?: return
        viewModelScope.launch {
            locationLoungeRepository.createChatRoom(token, loungeId, title)
                .onSuccess { created ->
                    val subLounges = locationLoungeRepository.chatRooms(token, loungeId).getOrDefault(emptyList())
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
            locationLoungeRepository.joinChatRoom(token, subLoungeId)
                .onSuccess { snapshot ->
                    val previous = _buildingLoungeState.value.selectedSubLoungeId
                    if (previous != null && previous != subLoungeId) {
                        realtimeClient.unsubscribeTopic(RealtimeDestinations.subLounge(previous))
                    }
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(
                        selectedSubLoungeId = subLoungeId,
                        subLoungeSnapshot = snapshot,
                        detailLoading = false,
                    )
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
        stopMusicPreview()
        viewModelScope.launch {
            locationLoungeRepository.leaveChatRoom(token, subLoungeId)
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
            locationLoungeRepository.deleteChatRoom(token, subLoungeId)
                .onSuccess {
                    clearSelectedSubLounge()
                    val rooms = locationLoungeRepository.chatRooms(token, buildingLoungeId)
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
            runCatching {
                val serverResults = buildingLoungeRepository.searchMusic(token, normalized).getOrNull()
                if (!serverResults.isNullOrEmpty()) {
                    serverResults
                } else {
                    musicSearchRepository.search(normalized).map { track ->
                        LoungeMusicSearchResultDto(
                            id = track.id.toString(),
                            title = track.title,
                            artistName = track.artist,
                            artworkUrl = track.artworkUrl,
                            storeUrl = track.appleMusicUrl,
                        )
                    }
                }
            }
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

    fun sendSearchedTrackToLounge(track: LoungeMusicSearchResultDto) {
        sendTrackToLounge(track.title, track.artistName, null)
    }

    private fun sendTrackToLounge(trackTitle: String, artistName: String, message: String?) {
        val token = accessToken ?: return
        val subLoungeId = _buildingLoungeState.value.selectedSubLoungeId ?: return
        viewModelScope.launch {
            _buildingLoungeState.value = _buildingLoungeState.value.copy(cardSubmitting = true, message = null)
            locationLoungeRepository.addCard(
                token,
                subLoungeId,
                CreateLoungeCardRequestDto(
                    clientCardId = UUID.randomUUID().toString(),
                    trackTitle = trackTitle,
                    artistName = artistName,
                    message = message,
                ),
            ).onSuccess { createdCard ->
                _buildingLoungeState.value = _buildingLoungeState.value.let { current ->
                    val snapshot = current.subLoungeSnapshot
                    current.copy(
                        cardSubmitting = false,
                        trackSearchResults = emptyList(),
                        subLoungeSnapshot = snapshot?.copy(
                            cards = (snapshot.cards.filterNot { it.id == createdCard.id } + createdCard),
                        ),
                        message = "추천 음악을 등록했어요.",
                    )
                }
                refreshSelectedSubLounge(silent = true)
            }.onFailure {
                _buildingLoungeState.value = _buildingLoungeState.value.copy(
                    cardSubmitting = false,
                    message = "추천 카드를 보내지 못했어요.",
                )
            }
        }
    }

    fun deleteLoungeCard(cardId: String) {
        val token = accessToken ?: return
        viewModelScope.launch {
            locationLoungeRepository.deleteCard(token, cardId)
                .onSuccess { refreshSelectedSubLounge(silent = true) }
                .onFailure {
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(message = "추천 카드를 삭제하지 못했어요.")
                }
        }
    }

    fun reactToLoungeCard(cardId: String, reactionType: String = "LIKE") {
        val token = accessToken ?: return
        viewModelScope.launch {
            locationLoungeRepository.reactToCard(token, cardId, reactionType)
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
        val loungeId = _buildingLoungeState.value.enteredLoungeId ?: return
        loungeSnapshotJob?.cancel()
        loungeSnapshotJob = viewModelScope.launch {
            if (silent) delay(80)
            if (!silent) {
                _buildingLoungeState.value = _buildingLoungeState.value.copy(detailLoading = true)
            }
            locationLoungeRepository.roomSnapshot(token, loungeId, subLoungeId)
                .onSuccess { snapshot ->
                    if (_buildingLoungeState.value.selectedSubLoungeId != subLoungeId) return@onSuccess
                    if (latestSubLoungeSnapshotAt?.let { snapshot.generatedAt < it } == true) return@onSuccess
                    latestSubLoungeSnapshotAt = snapshot.generatedAt
                    val shouldAutoPlay = pendingAutoPlaySubLoungeId == subLoungeId
                    if (shouldAutoPlay) pendingAutoPlaySubLoungeId = null
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(
                        detailLoading = false,
                        subLoungeSnapshot = snapshot,
                    )
                    if (shouldAutoPlay) playLatestLoungeRecommendation(snapshot)
                }
                .onFailure {
                    if (_buildingLoungeState.value.selectedSubLoungeId == subLoungeId) {
                        if (pendingAutoPlaySubLoungeId == subLoungeId) pendingAutoPlaySubLoungeId = null
                        _buildingLoungeState.value = _buildingLoungeState.value.copy(
                            detailLoading = false,
                            message = "라운지 상태를 불러오지 못했어요.",
                        )
                    }
                }
        }
    }

    private fun clearSelectedSubLounge() {
        stopMusicPreview()
        _buildingLoungeState.value.selectedSubLoungeId?.let { id ->
            realtimeClient.unsubscribeTopic(RealtimeDestinations.subLounge(id))
            presenceCoordinator.deactivateSubLounge(id)
        }
        _buildingLoungeState.value = _buildingLoungeState.value.copy(
            selectedSubLoungeId = null,
            subLoungeSnapshot = null,
            detailLoading = false,
            trackSearchLoading = false,
            cardSubmitting = false,
            trackSearchResults = emptyList(),
        )
        latestSubLoungeSnapshotAt = null
        pendingAutoPlaySubLoungeId = null
    }

    private fun playLatestLoungeRecommendation(snapshot: SubLoungeSnapshotDto) {
        snapshot.cards.latestRecommendation()?.let { card ->
            playMusicPreview(title = card.trackTitle, artist = card.artistName)
        }
    }

    fun createLocationLounge() {
        val token = accessToken ?: return
        val location = _buildingLoungeState.value.userLocation ?: return
        viewModelScope.launch {
            locationLoungeRepository.create(token)
                .onSuccess {
                    refreshBuildingLounges(location.latitude, location.longitude, location.accuracyMeters)
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(message = "현재 위치에 라운지를 만들었어요.")
                }
                .onFailure { error ->
                    _buildingLoungeState.value = _buildingLoungeState.value.copy(
                        message = locationLoungeErrorMessage(error, "라운지를 만들지 못했어요."),
                    )
                }
        }
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
                    playLatestLoungeRecommendation(snapshot)
                }
        }
    }

    fun clearFeedback() = repository.clearFeedback()

    override fun onCleared() {
        loungeFallbackJob?.cancel()
        loungeSnapshotJob?.cancel()
        removeAccessTokenListener?.invoke()
        removeAccessTokenListener = null
        musicPreviewPlayer.release()
        repository.close()
        super.onCleared()
    }
}

internal fun List<MusicSearchResult>.matchingPreviewUrl(title: String, artist: String): String? {
    val normalizedTitle = title.normalizedMusicIdentity()
    val normalizedArtist = artist.withoutParentheticalQualifier().normalizedMusicIdentity()
    val exactTitleCandidates = filter { result ->
        result.title.normalizedMusicIdentity() == normalizedTitle &&
            result.previewUrl?.startsWith("https://") == true
    }
    exactTitleCandidates.firstOrNull { result ->
        result.artist.withoutParentheticalQualifier().normalizedMusicIdentity() == normalizedArtist
    }?.previewUrl?.let { return it }

    // The Korean iTunes storefront localizes some artist names even when the
    // device reports the same artist in Latin characters (for example,
    // RESCENE -> 리센느). Only accept this fallback when the title has one
    // unambiguous result and the artist names use different writing systems.
    return exactTitleCandidates.singleOrNull()
        ?.takeIf { artist.usesDifferentWritingSystemFrom(it.artist) }
        ?.previewUrl
}

internal fun musicPreviewSearchTerm(title: String, artist: String): String =
    listOf(title.trim(), artist.withoutParentheticalQualifier())
        .filter(String::isNotBlank)
        .joinToString(" ")

internal fun String.withoutParentheticalQualifier(): String =
    replace(PARENTHETICAL_QUALIFIER, " ")
        .replace(Regex("\\s+"), " ")
        .trim()

private fun String.usesDifferentWritingSystemFrom(other: String): Boolean {
    fun String.hasLatinLetter() = any { it in 'A'..'Z' || it in 'a'..'z' }
    fun String.hasNonLatinLetter() = any { it.isLetter() && it !in 'A'..'Z' && it !in 'a'..'z' }
    return (hasLatinLetter() && other.hasNonLatinLetter()) ||
        (hasNonLatinLetter() && other.hasLatinLetter())
}

internal fun List<LoungeRecommendationCardDto>.latestRecommendation(): LoungeRecommendationCardDto? =
    maxWithOrNull(compareBy<LoungeRecommendationCardDto>({ it.createdAt }, { it.id }))

internal fun locationLoungeErrorMessage(error: Throwable, fallback: String): String = when {
    error is IOException || error.cause is IOException ->
        "네트워크에 연결할 수 없어요. 연결 상태를 확인하고 다시 시도해 주세요."
    error is HttpException && error.code() == 409 ->
        "다른 라운지 반경 안에서는 새 라운지를 만들 수 없어요."
    error is HttpException && error.code() == 401 ->
        "로그인 세션이 만료됐어요. 다시 로그인해 주세요."
    error is HttpException && error.code() == 404 ->
        "배포 서버가 새 라운지 기능을 아직 지원하지 않아요."
    error is HttpException && error.code() == 429 ->
        "요청이 너무 많아요. 잠시 후 다시 시도해 주세요."
    error is HttpException && error.code() >= 500 ->
        "서버 오류로 라운지를 처리하지 못했어요. 잠시 후 다시 시도해 주세요."
    else -> fallback
}

private fun String.normalizedMusicIdentity(): String =
    lowercase().filter(Char::isLetterOrDigit)

private val PARENTHETICAL_QUALIFIER = Regex("\\([^)]*\\)|（[^）]*）")
