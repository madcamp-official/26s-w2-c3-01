package com.example.myapplication.ui

import android.Manifest
import android.content.Intent
import android.app.SearchManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myapplication.core.model.MainTab
import com.example.myapplication.core.model.MelodyUiState
import com.example.myapplication.core.model.SharingState
import com.example.myapplication.core.model.SessionMode
import com.example.myapplication.core.model.Track
import com.example.myapplication.data.remote.LoungeMemberProfileDto
import com.example.myapplication.offlineexchange.ExchangeMusicCard
import com.example.myapplication.service.SharingForegroundService
import com.example.myapplication.service.NowPlayingNotificationListenerService
import com.example.myapplication.ui.components.MelodyBottomNavigationBar
import com.example.myapplication.ui.screens.ChatScreen
import com.example.myapplication.ui.screens.BlockedUsersScreen
import com.example.myapplication.ui.screens.BuildingLoungeMapScreen
import com.example.myapplication.ui.screens.LoungeMembersScreen
import com.example.myapplication.ui.screens.HomeScreen
import com.example.myapplication.ui.screens.InboxScreen
import com.example.myapplication.ui.screens.LoginScreen
import com.example.myapplication.ui.screens.MyScreen
import com.example.myapplication.ui.screens.NearbyScreen
import com.example.myapplication.ui.screens.NearbyMusicFilter
import com.example.myapplication.ui.screens.NotificationScreen
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
    const val OFFLINE_EXCHANGE = "offline-exchange"
    const val REPORT_USER = "report-user"
    const val BLOCKED_USERS = "blocked-users"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"
    const val FOLLOWING = "social-connections/following"
    const val FOLLOWERS = "social-connections/followers"
    const val PUBLIC_PROFILE = "profile/{profileHandle}"
    const val LOUNGE_MEMBERS = "lounge-members"
    const val EXCHANGE_PROFILE = "exchange-profile/{exchangeId}"

    fun chat(roomId: String) = "chat/$roomId"
    fun publicProfile(profileHandle: String) = "profile/$profileHandle"
    fun exchangeProfile(exchangeId: String) = "exchange-profile/$exchangeId"
}

private fun MelodyUiState.loungeProfileHandlesByAlias(): Map<String, String> = buildMap {
    profile.profileHandle.takeIf(String::isNotBlank)?.let { handle ->
        put(profile.accountAlias, handle)
        put(profile.nearbyDisplayAlias, handle)
    }
    nearbyListeners.forEach { listener ->
        listener.profileHandle?.takeIf(String::isNotBlank)?.let { put(listener.displayAlias, it) }
    }
    (following + followers).forEach { connection ->
        connection.profileHandle?.takeIf(String::isNotBlank)?.let { put(connection.displayAlias, it) }
    }
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
    val genreCatalogState by viewModel.genreCatalogState.collectAsState()
    val previewPlaybackState by viewModel.previewPlaybackState.collectAsState()
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
        val nearbyPermissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (hasLocationPermission && !needsNotificationPermission && nearbyPermissions.isEmpty()) {
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
            addAll(nearbyPermissions)
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
            genreCatalogState = genreCatalogState,
            onRetryGenreCatalog = viewModel::loadGenreCatalog,
            onSearchMusic = viewModel::searchMusic,
            onClearMusicSearch = viewModel::clearMusicSearch,
            onPreviewMusic = { viewModel.playMusicPreview(it.title, it.artist, it.previewUrl, it.artworkUrl) },
            onComplete = viewModel::completeOnboarding,
            modifier = modifier.safeDrawingPadding()
        )
        return
    }

    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val isChatScreen = currentBackStackEntry?.destination?.route == Route.CHAT
    val previewBarVisible = previewPlaybackState.isPlaying ||
        previewPlaybackState.isPaused || previewPlaybackState.isLoading
    val globalPreviewBarVisible = previewBarVisible && !isChatScreen
    val previewContentInset by animateDpAsState(
        targetValue = if (globalPreviewBarVisible) 62.dp else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "preview content inset",
    )
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Ink,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!isChatScreen) {
                MelodyBottomNavigationBar(
                    selectedTab = state.selectedTab,
                    unreadCount = state.unreadChatCount,
                    onTabSelected = { tab ->
                        viewModel.selectTab(tab)
                        navController.popBackStack(Route.MAIN, inclusive = false)
                    },
                )
            }
        },
    ) { appPadding ->
      Box(modifier = Modifier.fillMaxSize().padding(appPadding)) {
        NavHost(
            navController = navController,
            startDestination = Route.MAIN,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = previewContentInset)
        ) {
            composable(Route.MAIN) {
                MainShell(
                    state = state,
                    musicSearchState = musicSearchState,
                    genreCatalogState = genreCatalogState,
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
                    onOpenNotifications = { navController.navigate(Route.NOTIFICATIONS) },
                    onOpenSettings = { navController.navigate(Route.SETTINGS) },
                    onOpenFollowing = { navController.navigate(Route.FOLLOWING) },
                    onOpenFollowers = { navController.navigate(Route.FOLLOWERS) },
                    onOpenOfflineExchange = { navController.navigate(Route.OFFLINE_EXCHANGE) },
                    onOpenTrack = ::openTrackOnDevice,
                    onOpenProfile = { navController.navigate(Route.publicProfile(it)) },
                    onOpenLoungeMembers = { navController.navigate(Route.LOUNGE_MEMBERS) },
                )
            }
            composable(Route.USER_DETAIL) {
                val listener = state.selectedNearby
                if (listener == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
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
                        modifier = Modifier.statusBarsPadding(),
                    )
                }
            }
            composable(Route.BLOCKED_USERS) {
                LaunchedEffect(Unit) { viewModel.loadBlockedUsers() }
                BlockedUsersScreen(
                    users = state.blockedUsers,
                    onBack = { navController.popBackStack() },
                    onUnblock = viewModel::unblock,
                    modifier = Modifier.statusBarsPadding(),
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
                    modifier = Modifier.statusBarsPadding(),
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
                    modifier = Modifier.statusBarsPadding(),
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
                    modifier = Modifier.statusBarsPadding(),
                )
            }
            composable(Route.LOUNGE_MEMBERS) {
                LoungeMembersScreen(
                    snapshot = buildingLoungeState.subLoungeSnapshot,
                    selfMember = state.profile.profileHandle.takeIf(String::isNotBlank)?.let { handle ->
                        LoungeMemberProfileDto(handle, state.profile.accountAlias, "#6750A4")
                    },
                    profileHandlesByAlias = state.loungeProfileHandlesByAlias(),
                    onBack = { navController.popBackStack() },
                    onOpenProfile = { navController.navigate(Route.publicProfile(it)) },
                    modifier = Modifier.statusBarsPadding(),
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
                        onMessage = {
                            val roomId = state.chats.firstOrNull { it.peerHandle == profileHandle }?.roomId
                            if (roomId != null) {
                                navController.navigate(Route.chat(roomId))
                            } else {
                                viewModel.selectTab(MainTab.INBOX)
                                navController.popBackStack(Route.MAIN, inclusive = false)
                            }
                        },
                        onPlayNowPlaying = { title, artist, artworkUrl ->
                            val nearbyHandle = state.nearbyListeners
                                .firstOrNull { it.profileHandle == profileHandle }
                                ?.nearbyHandle
                            viewModel.playMusicPreview(
                                title,
                                artist,
                                artworkUrl = artworkUrl,
                                sourceNearbyHandle = nearbyHandle,
                            )
                        },
                        onPlayTrackPreview = { track ->
                            viewModel.playMusicPreview(track.title, track.artist, artworkUrl = track.artworkUrl)
                        },
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
                        modifier = Modifier.statusBarsPadding(),
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
                        onMessage = {
                            val profileHandle = state.selectedPublicProfile?.profileHandle
                            val roomId = state.chats.firstOrNull { it.peerHandle == profileHandle }?.roomId
                            if (roomId != null) {
                                navController.navigate(Route.chat(roomId))
                            } else {
                                viewModel.selectTab(MainTab.INBOX)
                                navController.popBackStack(Route.MAIN, inclusive = false)
                            }
                        },
                        onPlayNowPlaying = { title, artist, artworkUrl ->
                            val nearbyHandle = state.selectedPublicProfile?.profileHandle?.let { handle ->
                                state.nearbyListeners.firstOrNull { it.profileHandle == handle }?.nearbyHandle
                            }
                            viewModel.playMusicPreview(
                                title,
                                artist,
                                artworkUrl = artworkUrl,
                                sourceNearbyHandle = nearbyHandle,
                            )
                        },
                        onPlayTrackPreview = { track ->
                            viewModel.playMusicPreview(track.title, track.artist, artworkUrl = track.artworkUrl)
                        },
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
                        modifier = Modifier.statusBarsPadding(),
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
                    ChatScreen(
                        chat = chat,
                        messages = state.chatMessages[roomId].orEmpty(),
                        currentTrack = peerTrack,
                        previewPlaybackState = previewPlaybackState,
                        onTogglePreview = viewModel::toggleMusicPreviewPause,
                        onStopPreview = viewModel::stopMusicPreview,
                        onBack = { navController.popBackStack() },
                        onSend = { viewModel.sendChat(roomId, it) },
                        modifier = Modifier.statusBarsPadding()
                    )
                }
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
                        melodyAlias = "",
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
                    modifier = Modifier.statusBarsPadding()
                )
            }
            composable(Route.NOTIFICATIONS) {
                NotificationScreen(
                    notifications = state.notifications,
                    onViewed = viewModel::markInboxRead,
                    onBack = { navController.popBackStack() },
                    onClearAll = viewModel::clearNotifications,
                    onDelete = viewModel::deleteNotification,
                    onOpenProfile = { profileHandle ->
                        navController.navigate(Route.publicProfile(profileHandle))
                    },
                    modifier = Modifier.statusBarsPadding(),
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        AnimatedVisibility(
            visible = globalPreviewBarVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            com.example.myapplication.ui.components.PreviewNowPlayingBar(
                state = previewPlaybackState,
                onTogglePause = viewModel::toggleMusicPreviewPause,
                onStop = viewModel::stopMusicPreview,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
      }
    }
}

@Composable
private fun MainShell(
    state: MelodyUiState,
    musicSearchState: MusicSearchUiState,
    genreCatalogState: GenreCatalogUiState,
    buildingLoungeState: BuildingLoungeUiState,
    viewModel: MelodyViewModel,
    onStartSharing: () -> Unit,
    onStopSharing: () -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenFollowers: () -> Unit,
    onOpenOfflineExchange: () -> Unit,
    onOpenTrack: (Track) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenLoungeMembers: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var similarityThreshold by rememberSaveable { mutableFloatStateOf(60f) }
    var nearbyMusicFilter by rememberSaveable { mutableStateOf(NearbyMusicFilter.ALL) }

    DisposableEffect(lifecycleOwner, state.selectedTab, state.sharingState) {
        fun updateLocationProfile(started: Boolean) {
            val nearbyVisible = state.selectedTab == MainTab.HOME || state.selectedTab == MainTab.NEARBY
            SharingForegroundService.setInteractive(
                context,
                state.sharingState in setOf(SharingState.STARTING, SharingState.ACTIVE) &&
                    started && nearbyVisible,
            )
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> updateLocationProfile(started = true)
                Lifecycle.Event.ON_STOP -> updateLocationProfile(started = false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        updateLocationProfile(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            SharingForegroundService.setInteractive(context, interactive = false)
        }
    }

    val contentModifier = Modifier.fillMaxSize()
    when (state.selectedTab) {
            MainTab.HOME -> HomeScreen(
                state = state,
                modifier = contentModifier,
                onStartSharing = onStartSharing,
                onStopSharing = onStopSharing,
                onOpenNearby = { viewModel.selectTab(MainTab.NEARBY) },
                onOpenNotifications = onOpenNotifications,
                onSelectListener = { onOpenUser(it.nearbyHandle) },
                onOpenTrack = onOpenTrack,
                onPlayPreview = { viewModel.playMusicPreview(it.title, it.artist) },
            )
            MainTab.NEARBY -> if (state.sessionMode == SessionMode.OFFLINE) {
                OfflineServerFeatureScreen("온라인 주변 사용자", onOpenOfflineExchange, contentModifier)
            } else NearbyScreen(
                state = state,
                modifier = contentModifier,
                similarityThreshold = similarityThreshold.toInt(),
                musicFilter = nearbyMusicFilter,
                onSimilarityThresholdChange = { similarityThreshold = it.toFloat() },
                onRetry = viewModel::retrySharing,
                onMusicFilterChange = { nearbyMusicFilter = it },
                onSelectListener = {
                    viewModel.selectNearby(it.nearbyHandle)
                    it.currentTrack?.let { track ->
                        viewModel.playMusicPreview(
                            track.title,
                            track.artist,
                            sourceNearbyHandle = it.nearbyHandle,
                        )
                    }
                },
                onOpenListenerDetail = { onOpenUser(it.nearbyHandle) },
                onOpenTrack = onOpenTrack,
                onReact = { listener, label -> viewModel.react(listener.nearbyHandle, label) },
                onFollow = { viewModel.follow(it.nearbyHandle) },
                onPlayPreview = { listener, track ->
                    viewModel.playMusicPreview(
                        track.title,
                        track.artist,
                        sourceNearbyHandle = listener.nearbyHandle,
                    )
                },
                onSearchInMusicApp = { track ->
                    val query = "${track.title} ${track.artist}"
                    val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_SEARCH)
                        .putExtra(SearchManager.QUERY, query)
                        .putExtra(MediaStore.EXTRA_MEDIA_TITLE, track.title)
                        .putExtra(MediaStore.EXTRA_MEDIA_ARTIST, track.artist)
                    runCatching { context.startActivity(intent) }.onFailure {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://music.apple.com/kr/search?term=${Uri.encode(query)}")))
                    }
                },
            )
            MainTab.LOUNGE -> if (state.sessionMode == SessionMode.OFFLINE) {
                OfflineServerFeatureScreen("음악 라운지", onOpenOfflineExchange, contentModifier)
            } else BuildingLoungeMapScreen(
                state = buildingLoungeState,
                onLocationUpdate = viewModel::refreshBuildingLounges,
                onLocationUnavailable = viewModel::setBuildingLoungeLocationUnavailable,
                onHeartbeat = viewModel::heartbeatBuildingLounge,
                onEnter = viewModel::enterBuildingLounge,
                onLeave = viewModel::leaveBuildingLounge,
                onCreateSubLounge = viewModel::createBuildingSubLounge,
                onOpenSubLounge = viewModel::openSubLounge,
                onLeaveSubLounge = viewModel::leaveSubLounge,
                onDeleteSubLounge = viewModel::deleteSubLounge,
                onSearchTracks = viewModel::searchLoungeTracks,
                onSendSearchedTrack = viewModel::sendSearchedTrackToLounge,
                onDeleteCard = viewModel::deleteLoungeCard,
                onReactToCard = viewModel::reactToLoungeCard,
                onVote = viewModel::voteInSubLounge,
                onRefreshSubLounge = viewModel::refreshSubLounge,
                onOpenProfile = onOpenProfile,
                onOpenMembers = onOpenLoungeMembers,
                profileHandlesByAlias = state.loungeProfileHandlesByAlias(),
                modifier = contentModifier
            )
            MainTab.INBOX -> if (state.sessionMode == SessionMode.OFFLINE) {
                OfflineServerFeatureScreen("채팅", onOpenOfflineExchange, contentModifier)
            } else InboxScreen(
                chats = state.chats,
                onOpenChat = onOpenChat,
                onLeaveChat = viewModel::leaveChat,
                modifier = contentModifier.statusBarsPadding()
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
                onRandomizeAvatar = viewModel::randomizeAvatar,
                onProfileCurationUpdate = viewModel::updateProfileCuration,
                musicSearchState = musicSearchState,
                genreCatalogState = genreCatalogState,
                onRetryGenreCatalog = viewModel::loadGenreCatalog,
                onSearchMusic = viewModel::searchMusic,
                onClearMusicSearch = viewModel::clearMusicSearch,
                onPreviewMusic = { viewModel.playMusicPreview(it.title, it.artist, it.previewUrl, it.artworkUrl) },
                modifier = contentModifier.statusBarsPadding()
            )
    }
}

@Composable
private fun OfflineServerFeatureScreen(
    featureName: String,
    onOpenOfflineExchange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().statusBarsPadding(), contentAlignment = Alignment.Center) {
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
