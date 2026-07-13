package com.example.myapplication.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myapplication.core.model.MainTab
import com.example.myapplication.core.model.MelodyUiState
import com.example.myapplication.core.model.SharingState
import com.example.myapplication.core.model.SessionMode
import com.example.myapplication.core.model.Track
import com.example.myapplication.offlineexchange.ExchangeMusicCard
import com.example.myapplication.service.SharingForegroundService
import com.example.myapplication.service.NowPlayingNotificationListenerService
import com.example.myapplication.ui.components.MelodyBottomNavigationBar
import com.example.myapplication.ui.screens.ChatScreen
import com.example.myapplication.ui.screens.BlockedUsersScreen
import com.example.myapplication.ui.screens.BuildingLoungeMapScreen
import com.example.myapplication.ui.screens.HomeScreen
import com.example.myapplication.ui.screens.InboxScreen
import com.example.myapplication.ui.screens.LoginScreen
import com.example.myapplication.ui.screens.MelodyAliasScreen
import com.example.myapplication.ui.screens.MyScreen
import com.example.myapplication.ui.screens.NearbyScreen
import com.example.myapplication.ui.screens.NearbyMusicFilter
import com.example.myapplication.ui.screens.OfflineExchangeScreen
import com.example.myapplication.ui.screens.OnboardingScreen
import com.example.myapplication.ui.screens.PublicProfileScreen
import com.example.myapplication.ui.screens.ReportUserScreen
import com.example.myapplication.ui.screens.SettingsScreen
import com.example.myapplication.ui.screens.SocialConnectionsScreen
import com.example.myapplication.ui.screens.UserDetailScreen
import com.example.myapplication.ui.theme.Ink

private object Route {
    const val MAIN = "main"
    const val USER_DETAIL = "user-detail"
    const val CHAT = "chat/{roomId}"
    const val MELODY_ALIAS = "melody-alias"
    const val OFFLINE_EXCHANGE = "offline-exchange"
    const val REPORT_USER = "report-user"
    const val BLOCKED_USERS = "blocked-users"
    const val SETTINGS = "settings"
    const val FOLLOWING = "social-connections/following"
    const val FOLLOWERS = "social-connections/followers"
    const val PUBLIC_PROFILE = "profile/{profileHandle}"
    const val EXCHANGE_PROFILE = "exchange-profile/{exchangeId}"

    fun chat(roomId: String) = "chat/$roomId"
    fun publicProfile(profileHandle: String) = "profile/$profileHandle"
    fun exchangeProfile(exchangeId: String) = "exchange-profile/$exchangeId"
}

@Composable
fun MelodyBubbleApp(
    viewModel: MelodyViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val loginState by viewModel.loginState.collectAsState()
    val emailAvailabilityState by viewModel.emailAvailabilityState.collectAsState()
    val musicSearchState by viewModel.musicSearchState.collectAsState()
    val buildingLoungeState by viewModel.buildingLoungeState.collectAsState()
    val exchangeState by viewModel.exchangeState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionPreferences = remember {
        context.getSharedPreferences("melody-bubble-permission-prompts", android.content.Context.MODE_PRIVATE)
    }

    fun requestNowPlayingAccessIfNeeded() {
        if (!NowPlayingNotificationListenerService.isEnabled(context)) {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    fun openTrackOnDevice(track: Track) {
        val externalUrl = track.externalUrl?.takeIf { it.startsWith("https://") } ?: return
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(externalUrl)))
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val hasLocation = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            SharingForegroundService.hasLocationPermission(context)
        if (hasLocation && SharingForegroundService.start(context)) {
            viewModel.startSharing()
            requestNowPlayingAccessIfNeeded()
        } else if (hasLocation) {
            viewModel.sharingStartFailed()
        } else {
            viewModel.sharingPermissionRequired()
        }
    }

    val realtimeNotificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permissionPreferences.edit().putBoolean("realtime-notifications-requested", true).apply()
    }

    val offlineExchangePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it }) viewModel.startOfflineExchange()
        else viewModel.offlineExchangePermissionDenied()
    }

    fun requestOfflineExchangeStart() {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) viewModel.startOfflineExchange()
        else offlineExchangePermissionLauncher.launch(missing.toTypedArray())
    }

    LaunchedEffect(loginState, state.isOnboardingComplete) {
        val shouldRequest = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            loginState is LoginUiState.Success &&
            state.isOnboardingComplete &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            !permissionPreferences.getBoolean("realtime-notifications-requested", false)
        if (shouldRequest) realtimeNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun requestSharingStart() {
        val hasLocationPermission = SharingForegroundService.hasLocationPermission(context)
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED

        if (hasLocationPermission && !needsNotificationPermission) {
            if (SharingForegroundService.start(context)) {
                viewModel.startSharing()
                requestNowPlayingAccessIfNeeded()
            } else {
                viewModel.sharingStartFailed()
            }
            return
        }
        val permissions = buildList {
            if (!hasLocationPermission) {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (needsNotificationPermission) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    LaunchedEffect(state.feedbackMessage) {
        val message = state.feedbackMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearFeedback()
    }

    if (loginState !is LoginUiState.Success) {
        LoginScreen(
            state = loginState,
            emailAvailabilityState = emailAvailabilityState,
            onLogin = viewModel::login,
            onSignup = viewModel::signup,
            onCheckEmail = viewModel::checkEmailAvailability,
            onEmailChanged = viewModel::resetEmailAvailability,
            onGoogleLogin = viewModel::loginWithGoogle,
            onStartOffline = viewModel::startOfflineMode,
            onRetryOnline = viewModel::retryOnlineSession,
            modifier = modifier.safeDrawingPadding()
        )
        return
    }

    if (!state.isOnboardingComplete) {
        OnboardingScreen(
            musicSearchState = musicSearchState,
            onSearchMusic = viewModel::searchMusic,
            onClearMusicSearch = viewModel::clearMusicSearch,
            onComplete = viewModel::completeOnboarding,
            modifier = modifier.safeDrawingPadding()
        )
        return
    }

    val navController = rememberNavController()
    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Route.MAIN,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Route.MAIN) {
                MainShell(
                    state = state,
                    musicSearchState = musicSearchState,
                    buildingLoungeState = buildingLoungeState,
                    viewModel = viewModel,
                    onStartSharing = ::requestSharingStart,
                    onStopSharing = {
                        SharingForegroundService.stop(context)
                        viewModel.stopSharing()
                    },
                    onOpenUser = { handle ->
                        viewModel.selectNearby(handle)
                        navController.navigate(Route.USER_DETAIL)
                    },
                    onOpenChat = { navController.navigate(Route.chat(it)) },
                    onOpenMelodyAlias = { navController.navigate(Route.MELODY_ALIAS) },
                    onOpenSettings = { navController.navigate(Route.SETTINGS) },
                    onOpenFollowing = { navController.navigate(Route.FOLLOWING) },
                    onOpenFollowers = { navController.navigate(Route.FOLLOWERS) },
                    onOpenOfflineExchange = { navController.navigate(Route.OFFLINE_EXCHANGE) },
                    onOpenTrack = ::openTrackOnDevice,
                )
            }
            composable(Route.USER_DETAIL) {
                val listener = state.selectedNearby
                if (listener == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    DisposableEffect(listener.nearbyHandle) {
                        onDispose { viewModel.stopMelodyAudio() }
                    }
                    var reactionSheetVisible by rememberSaveable { mutableStateOf(false) }
                    UserDetailScreen(
                        listener = listener,
                        reactionSheetVisible = reactionSheetVisible,
                        onBack = { navController.popBackStack() },
                        onShowReactionSheet = { reactionSheetVisible = true },
                        onDismissReactionSheet = { reactionSheetVisible = false },
                        onReact = { selected, label -> viewModel.react(selected.nearbyHandle, label) },
                        onFollow = { viewModel.follow(it.nearbyHandle) },
                        onOpenProfile = { selected ->
                            selected.profileHandle?.let { navController.navigate(Route.publicProfile(it)) }
                        },
                        onOpenChat = { selected ->
                            state.chats.firstOrNull { it.peerHandle == selected.nearbyHandle }?.let { chat ->
                                navController.navigate(Route.chat(chat.roomId))
                            }
                        },
                        onBlock = {
                            viewModel.block(it.nearbyHandle)
                            navController.popBackStack()
                        },
                        onReport = { navController.navigate(Route.REPORT_USER) }
                    )
                }
            }
            composable(Route.REPORT_USER) {
                val listener = state.selectedNearby
                if (listener == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    ReportUserScreen(
                        listener = listener,
                        onBack = { navController.popBackStack() },
                        onSubmit = { reason, description ->
                            viewModel.report(listener.nearbyHandle, reason, description)
                            navController.popBackStack(Route.MAIN, inclusive = false)
                        },
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
            }
            composable(Route.BLOCKED_USERS) {
                LaunchedEffect(Unit) { viewModel.loadBlockedUsers() }
                BlockedUsersScreen(
                    users = state.blockedUsers,
                    onBack = { navController.popBackStack() },
                    onUnblock = viewModel::unblock,
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
            composable(Route.SETTINGS) {
                SettingsScreen(
                    profile = state.profile,
                    offlineExchangeCount = state.offlineExchanges.size,
                    onBack = { navController.popBackStack() },
                    onDiscoverableChange = viewModel::setDiscoverable,
                    onAllowReactionsChange = viewModel::setAllowReactions,
                    onOfflineExchangeChange = viewModel::setOfflineExchangeEnabled,
                    onMusicVisibilityChange = viewModel::setMusicVisibility,
                    onProfilePrivacyChange = viewModel::updateProfilePrivacy,
                    onOpenNotificationAccess = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onOpenOfflineExchange = { navController.navigate(Route.OFFLINE_EXCHANGE) },
                    onOpenBlockedUsers = { navController.navigate(Route.BLOCKED_USERS) },
                    onLogout = viewModel::logout,
                    onDeleteAccount = viewModel::deleteAccount,
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
            composable(Route.FOLLOWING) {
                LaunchedEffect(Unit) { viewModel.loadSocialConnections() }
                SocialConnectionsScreen(
                    following = state.following,
                    followers = state.followers,
                    loading = state.socialConnectionsLoading,
                    initialFollowing = true,
                    onBack = { navController.popBackStack() },
                    onUnfollow = viewModel::unfollowRelationship,
                    onOpenProfile = { navController.navigate(Route.publicProfile(it)) },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
            composable(Route.FOLLOWERS) {
                LaunchedEffect(Unit) { viewModel.loadSocialConnections() }
                SocialConnectionsScreen(
                    following = state.following,
                    followers = state.followers,
                    loading = state.socialConnectionsLoading,
                    initialFollowing = false,
                    onBack = { navController.popBackStack() },
                    onUnfollow = viewModel::unfollowRelationship,
                    onOpenProfile = { navController.navigate(Route.publicProfile(it)) },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
            composable(
                route = Route.PUBLIC_PROFILE,
                arguments = listOf(navArgument("profileHandle") { type = NavType.StringType }),
            ) { entry ->
                val profileHandle = entry.arguments?.getString("profileHandle")
                if (profileHandle == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    LaunchedEffect(profileHandle) { viewModel.loadPublicProfile(profileHandle) }
                    LaunchedEffect(
                        profileHandle,
                        state.selectedPublicProfile?.profileHandle,
                        state.selectedPublicProfile?.nowPlaying?.title,
                        state.selectedPublicProfile?.nowPlaying?.artist,
                    ) {
                        state.selectedPublicProfile
                            ?.takeIf { it.profileHandle == profileHandle }
                            ?.let(viewModel::autoPlayPublicProfileNowPlaying)
                    }
                    DisposableEffect(profileHandle) {
                        onDispose {
                            viewModel.stopMelodyAudio()
                            viewModel.clearPublicProfile()
                        }
                    }
                    PublicProfileScreen(
                        profile = state.selectedPublicProfile,
                        loading = state.publicProfileLoading,
                        errorMessage = state.publicProfileError,
                        onBack = { navController.popBackStack() },
                        onRetry = { viewModel.loadPublicProfile(profileHandle) },
                        onFollow = viewModel::followPublicProfile,
                        onPlayProfileMusic = viewModel::playProfileMusicUrl,
                        onShare = {
                            val name = state.selectedPublicProfile?.displayName ?: profileHandle
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND)
                                        .setType("text/plain")
                                        .putExtra(Intent.EXTRA_TEXT, "$name 님의 Melody Bubble 음악 프로필 · @$profileHandle"),
                                    "음악 프로필 공유",
                                )
                            )
                        },
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
            }
            composable(
                route = Route.EXCHANGE_PROFILE,
                arguments = listOf(navArgument("exchangeId") { type = NavType.StringType }),
            ) { entry ->
                val exchangeId = entry.arguments?.getString("exchangeId")
                if (exchangeId == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    LaunchedEffect(exchangeId) { viewModel.loadExchangeProfile(exchangeId) }
                    LaunchedEffect(
                        exchangeId,
                        state.selectedPublicProfile?.profileHandle,
                        state.selectedPublicProfile?.nowPlaying?.title,
                        state.selectedPublicProfile?.nowPlaying?.artist,
                    ) {
                        state.selectedPublicProfile?.let(viewModel::autoPlayPublicProfileNowPlaying)
                    }
                    DisposableEffect(exchangeId) {
                        onDispose {
                            viewModel.stopMelodyAudio()
                            viewModel.clearPublicProfile()
                        }
                    }
                    PublicProfileScreen(
                        profile = state.selectedPublicProfile,
                        loading = state.publicProfileLoading,
                        errorMessage = state.publicProfileError,
                        onBack = { navController.popBackStack() },
                        onRetry = { viewModel.loadExchangeProfile(exchangeId) },
                        onFollow = viewModel::followPublicProfile,
                        onPlayProfileMusic = viewModel::playProfileMusicUrl,
                        onShare = {
                            state.selectedPublicProfile?.let { selected ->
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND)
                                            .setType("text/plain")
                                            .putExtra(Intent.EXTRA_TEXT, "${selected.displayName} 님의 Melody Bubble 음악 프로필 · @${selected.profileHandle}"),
                                        "음악 프로필 공유",
                                    )
                                )
                            }
                        },
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
            }
            composable(
                route = Route.CHAT,
                arguments = listOf(navArgument("roomId") { type = NavType.StringType })
            ) { entry ->
                val roomId = entry.arguments?.getString("roomId")
                val chat = state.chats.firstOrNull { it.roomId == roomId }
                if (roomId == null || chat == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    DisposableEffect(roomId) {
                        viewModel.openChat(roomId)
                        onDispose { viewModel.closeChat(roomId) }
                    }
                    val peerTrack = state.nearbyListeners
                        .firstOrNull { it.nearbyHandle == chat.peerHandle }
                        ?.currentTrack
                        ?: state.currentTrack
                    ChatScreen(
                        chat = chat,
                        messages = state.chatMessages[roomId].orEmpty(),
                        currentTrack = peerTrack,
                        onBack = { navController.popBackStack() },
                        onSend = { viewModel.sendChat(roomId, it) },
                        modifier = Modifier.safeDrawingPadding()
                    )
                }
            }
            composable(Route.MELODY_ALIAS) {
                val lyriaState by viewModel.lyriaGenerationState.collectAsState()
                DisposableEffect(Unit) {
                    onDispose { viewModel.stopMelodyAudio() }
                }
                MelodyAliasScreen(
                    onBack = { navController.popBackStack() },
                    generationState = lyriaState,
                    onGenerate = viewModel::generateLyriaSong,
                    onPlayFull = viewModel::playLyriaSong,
                    onPlaySelection = viewModel::playLyriaSelection,
                    onSaveProfile = viewModel::saveLyriaAsProfileMusic,
                    onReset = viewModel::resetLyriaSong,
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            composable(Route.OFFLINE_EXCHANGE) {
                DisposableEffect(Unit) {
                    viewModel.enterBubbleMode()
                    onDispose { viewModel.exitBubbleMode() }
                }
                OfflineExchangeScreen(
                    records = state.offlineExchanges,
                    myCard = ExchangeMusicCard(
                        displayAlias = state.profile.accountAlias,
                        trackTitle = state.currentTrack.title,
                        trackArtist = state.currentTrack.artist,
                        melodyAlias = state.profile.melodyNotes.joinToString(" · "),
                        genreTags = state.currentTrack.genreTags.ifEmpty { state.profile.genres },
                        moodTags = state.currentTrack.moodTags.ifEmpty { state.profile.moods },
                    ),
                    exchangeState = exchangeState,
                    onBack = { navController.popBackStack() },
                    onStart = ::requestOfflineExchangeStart,
                    onConnect = viewModel::connectOfflineEndpoint,
                    onApprove = viewModel::approveOfflineConnection,
                    onReject = viewModel::rejectOfflineConnection,
                    onStop = viewModel::stopOfflineExchange,
                    onClearResult = viewModel::clearOfflineExchangeResult,
                    onSync = viewModel::syncExchange,
                    onDelete = viewModel::deleteExchange,
                    onOpenProfile = { navController.navigate(Route.exchangeProfile(it)) },
                    modifier = Modifier.safeDrawingPadding()
                )
            }
        }

        if (state.sessionMode == SessionMode.OFFLINE) {
            Surface(
                color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .fillMaxWidth()
                    .clickable { navController.navigate(Route.OFFLINE_EXCHANGE) },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("오프라인 모드", modifier = Modifier.weight(1f))
                    Text("주변 기기 찾기")
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 88.dp)
        )
    }
}

@Composable
private fun MainShell(
    state: MelodyUiState,
    musicSearchState: MusicSearchUiState,
    buildingLoungeState: BuildingLoungeUiState,
    viewModel: MelodyViewModel,
    onStartSharing: () -> Unit,
    onStopSharing: () -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenMelodyAlias: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenFollowers: () -> Unit,
    onOpenOfflineExchange: () -> Unit,
    onOpenTrack: (Track) -> Unit,
) {
    var similarityThreshold by rememberSaveable { mutableFloatStateOf(60f) }
    var nearbyMusicFilter by rememberSaveable { mutableStateOf(NearbyMusicFilter.ALL) }
    var nearbyRadius by rememberSaveable { mutableFloatStateOf(state.discoveryRadiusMeters.toFloat()) }
    LaunchedEffect(state.discoveryRadiusMeters) {
        nearbyRadius = state.discoveryRadiusMeters.toFloat()
    }

    Scaffold(
        containerColor = Ink,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            MelodyBottomNavigationBar(
                selectedTab = state.selectedTab,
                unreadCount = state.unreadNotificationCount,
                onTabSelected = viewModel::selectTab
            )
        }
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(bottom = innerPadding.calculateBottomPadding())
        when (state.selectedTab) {
            MainTab.HOME -> HomeScreen(
                state = state,
                modifier = contentModifier,
                onStartSharing = onStartSharing,
                onStopSharing = onStopSharing,
                onOpenNearby = { viewModel.selectTab(MainTab.NEARBY) },
                onSelectListener = { onOpenUser(it.nearbyHandle) },
                onSelectTrack = viewModel::selectTrack,
                onOpenTrack = onOpenTrack
            )
            MainTab.NEARBY -> if (state.sessionMode == SessionMode.OFFLINE) {
                OfflineServerFeatureScreen("온라인 주변 사용자", onOpenOfflineExchange, contentModifier)
            } else NearbyScreen(
                state = state,
                modifier = contentModifier,
                similarityThreshold = similarityThreshold.toInt(),
                radiusMeters = nearbyRadius.toInt(),
                musicFilter = nearbyMusicFilter,
                onSimilarityThresholdChange = { similarityThreshold = it.toFloat() },
                onRadiusChange = { nearbyRadius = it.toFloat() },
                onRadiusChangeFinished = {
                    viewModel.updatePresenceSettings(
                        nearbyRadius.toInt(),
                        state.discoverabilityScope,
                        state.musicVisibility,
                    )
                },
                onRetry = viewModel::retrySharing,
                onMusicFilterChange = { nearbyMusicFilter = it },
                onSelectListener = { viewModel.selectNearby(it.nearbyHandle) },
                onOpenListenerDetail = { onOpenUser(it.nearbyHandle) },
                onOpenTrack = onOpenTrack,
                onReact = { listener, label -> viewModel.react(listener.nearbyHandle, label) },
                onFollow = { viewModel.follow(it.nearbyHandle) }
            )
            MainTab.LOUNGE -> if (state.sessionMode == SessionMode.OFFLINE) {
                OfflineServerFeatureScreen("음악 라운지", onOpenOfflineExchange, contentModifier)
            } else BuildingLoungeMapScreen(
                state = buildingLoungeState,
                onLocationUpdate = viewModel::refreshBuildingLounges,
                onHeartbeat = viewModel::heartbeatBuildingLounge,
                onEnter = viewModel::enterBuildingLounge,
                onLeave = viewModel::leaveBuildingLounge,
                onCreateSubLounge = viewModel::createBuildingSubLounge,
                onOpenSubLounge = viewModel::openSubLounge,
                onLeaveSubLounge = viewModel::leaveSubLounge,
                onSendTrack = viewModel::sendDetectedTrackToLounge,
                onReactToCard = viewModel::reactToLoungeCard,
                onVote = viewModel::voteInSubLounge,
                onRefreshSubLounge = viewModel::refreshSubLounge,
                modifier = contentModifier
            )
            MainTab.INBOX -> if (state.sessionMode == SessionMode.OFFLINE) {
                OfflineServerFeatureScreen("인박스와 채팅", onOpenOfflineExchange, contentModifier)
            } else InboxScreen(
                notifications = state.notifications,
                chats = state.chats,
                onOpenChat = onOpenChat,
                onMarkRead = viewModel::markInboxRead,
                modifier = contentModifier.safeDrawingPadding()
            )
            MainTab.MY -> MyScreen(
                profile = state.profile,
                profileSaving = state.profileSaving,
                feedbackMessage = state.feedbackMessage,
                followingCount = maxOf(state.profile.stats.followingCount, state.following.size),
                followerCount = maxOf(state.profile.stats.followerCount, state.followers.size),
                verifiedOfflineExchangeCount = state.verifiedOfflineExchangeCount,
                offlineExchangeGenres = state.offlineExchangeGenres,
                offlineExchangeMoods = state.offlineExchangeMoods,
                nowPlayingTrack = state.detectedTrack,
                nowPlayingActive = state.detectedTrackPlaying,
                onLoadConnections = viewModel::loadSocialConnections,
                onOpenFollowing = onOpenFollowing,
                onOpenFollowers = onOpenFollowers,
                onOpenSettings = onOpenSettings,
                onOpenBubbleMode = onOpenOfflineExchange,
                onProfileUpdate = viewModel::updateProfile,
                onProfileCurationUpdate = viewModel::updateProfileCuration,
                musicSearchState = musicSearchState,
                onSearchMusic = viewModel::searchMusic,
                onClearMusicSearch = viewModel::clearMusicSearch,
                onPlayProfileMusic = viewModel::playProfileMusic,
                onDeleteProfileMusic = viewModel::deleteProfileMusic,
                onOpenMelodyAlias = onOpenMelodyAlias,
                modifier = contentModifier.safeDrawingPadding()
            )
        }
    }
}

@Composable
private fun OfflineServerFeatureScreen(
    featureName: String,
    onOpenOfflineExchange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("$featureName 기능은 인터넷 연결 후 이용할 수 있어요", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Text("오프라인에서는 가까운 기기와 음악 카드를 직접 교환할 수 있어요.")
            Spacer(Modifier.height(20.dp))
            Button(onClick = onOpenOfflineExchange) { Text("주변 기기 찾기") }
        }
    }
}
