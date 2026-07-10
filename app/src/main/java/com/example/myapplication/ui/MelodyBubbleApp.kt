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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
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
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myapplication.core.model.MainTab
import com.example.myapplication.core.model.MelodyUiState
import com.example.myapplication.core.model.SharingState
import com.example.myapplication.data.DemoCatalog
import com.example.myapplication.service.SharingForegroundService
import com.example.myapplication.ui.components.MelodyBottomNavigationBar
import com.example.myapplication.ui.screens.ChatScreen
import com.example.myapplication.ui.screens.HomeScreen
import com.example.myapplication.ui.screens.InboxScreen
import com.example.myapplication.ui.screens.LoginScreen
import com.example.myapplication.ui.screens.LoungeDetailScreen
import com.example.myapplication.ui.screens.LoungeListScreen
import com.example.myapplication.ui.screens.MusicSelectScreen
import com.example.myapplication.ui.screens.MyScreen
import com.example.myapplication.ui.screens.NearbyScreen
import com.example.myapplication.ui.screens.NearbyMusicFilter
import com.example.myapplication.ui.screens.OfflineExchangeScreen
import com.example.myapplication.ui.screens.OnboardingScreen
import com.example.myapplication.ui.screens.UserDetailScreen
import com.example.myapplication.ui.theme.Ink

private object Route {
    const val MAIN = "main"
    const val USER_DETAIL = "user-detail"
    const val LOUNGE_DETAIL = "lounge-detail"
    const val CHAT = "chat/{roomId}"
    const val MUSIC_SELECT = "music-select"
    const val OFFLINE_EXCHANGE = "offline-exchange"

    fun chat(roomId: String) = "chat/$roomId"
}

@Composable
fun MelodyBubbleApp(
    viewModel: MelodyViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val loginState by viewModel.loginState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val hasLocation = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            SharingForegroundService.hasLocationPermission(context)
        if (hasLocation && SharingForegroundService.start(context)) {
            viewModel.startSharing()
        } else {
            viewModel.sharingPermissionRequired()
        }
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
            } else {
                viewModel.sharingPermissionRequired()
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
            onLogin = viewModel::login,
            onGoogleLogin = viewModel::loginWithGoogle,
            modifier = modifier.safeDrawingPadding()
        )
        return
    }

    if (!state.isOnboardingComplete) {
        OnboardingScreen(
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
                    onOpenLounge = { roomId ->
                        viewModel.selectLounge(roomId)
                        navController.navigate(Route.LOUNGE_DETAIL)
                    },
                    onOpenChat = { navController.navigate(Route.chat(it)) },
                    onOpenMusicSelect = { navController.navigate(Route.MUSIC_SELECT) },
                    onOpenNotificationAccess = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onOpenOfflineExchange = { navController.navigate(Route.OFFLINE_EXCHANGE) }
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
                        onOpenTrack = { track ->
                            val externalUrl = track.externalUrl
                                ?.takeIf { it.startsWith("https://") }
                            if (externalUrl != null) {
                                val opened = runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(externalUrl))
                                    )
                                }.isSuccess
                                if (!opened) viewModel.selectTrack(track)
                            } else {
                                viewModel.selectTrack(track)
                            }
                        },
                        onShowReactionSheet = { reactionSheetVisible = true },
                        onDismissReactionSheet = { reactionSheetVisible = false },
                        onReact = { selected, label -> viewModel.react(selected.nearbyHandle, label) },
                        onFollow = { viewModel.follow(it.nearbyHandle) },
                        onBlock = {
                            viewModel.block(it.nearbyHandle)
                            navController.popBackStack()
                        },
                        onReport = { viewModel.report(it.nearbyHandle) }
                    )
                }
            }
            composable(Route.LOUNGE_DETAIL) {
                val lounge = state.selectedLounge
                if (lounge == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    LoungeDetailScreen(
                        lounge = lounge,
                        onBack = { navController.popBackStack() },
                        onJoin = { viewModel.joinLounge(lounge.id) },
                        onLeave = {
                            viewModel.joinLounge(lounge.id)
                            navController.popBackStack()
                        },
                        onVote = { viewModel.vote(lounge.id, it) },
                        onReactToCard = { viewModel.reactToMusicCard(lounge.id, it) },
                        onSendCurrentTrack = { viewModel.sendMusicCard(lounge.id) },
                        modifier = Modifier.safeDrawingPadding()
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
            composable(Route.MUSIC_SELECT) {
                MusicSelectScreen(
                    currentTrack = state.currentTrack,
                    options = listOf(
                        DemoCatalog.blueNight,
                        DemoCatalog.lateDrive,
                        DemoCatalog.summerEnd,
                        DemoCatalog.indieRain
                    ),
                    onBack = { navController.popBackStack() },
                    onSelect = viewModel::selectTrack,
                    modifier = Modifier.safeDrawingPadding()
                )
            }
            composable(Route.OFFLINE_EXCHANGE) {
                OfflineExchangeScreen(
                    records = state.offlineExchanges,
                    onBack = { navController.popBackStack() },
                    onCreate = viewModel::createDemoExchange,
                    onSync = viewModel::syncExchange,
                    modifier = Modifier.safeDrawingPadding()
                )
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
    viewModel: MelodyViewModel,
    onStartSharing: () -> Unit,
    onStopSharing: () -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenLounge: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenMusicSelect: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenOfflineExchange: () -> Unit
) {
    var similarityThreshold by rememberSaveable { mutableFloatStateOf(60f) }
    var nearbyMusicFilter by rememberSaveable { mutableStateOf(NearbyMusicFilter.ALL) }

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
                onSelectTrack = viewModel::selectTrack
            )
            MainTab.NEARBY -> NearbyScreen(
                state = state,
                modifier = contentModifier,
                similarityThreshold = similarityThreshold.toInt(),
                musicFilter = nearbyMusicFilter,
                onSimilarityThresholdChange = { similarityThreshold = it.toFloat() },
                onMusicFilterChange = { nearbyMusicFilter = it },
                onSelectListener = { viewModel.selectNearby(it.nearbyHandle) },
                onOpenListenerDetail = { onOpenUser(it.nearbyHandle) },
                onReact = { listener, label -> viewModel.react(listener.nearbyHandle, label) },
                onFollow = { viewModel.follow(it.nearbyHandle) }
            )
            MainTab.LOUNGE -> LoungeListScreen(
                lounges = state.lounges,
                onOpen = onOpenLounge,
                onJoinAndOpen = { roomId ->
                    viewModel.joinLounge(roomId)
                    onOpenLounge(roomId)
                },
                modifier = contentModifier.safeDrawingPadding()
            )
            MainTab.INBOX -> InboxScreen(
                notifications = state.notifications,
                chats = state.chats,
                onOpenChat = onOpenChat,
                onMarkRead = viewModel::markInboxRead,
                modifier = contentModifier.safeDrawingPadding()
            )
            MainTab.MY -> MyScreen(
                profile = state.profile,
                offlineExchangeCount = state.offlineExchanges.size,
                onDiscoverableChange = viewModel::setDiscoverable,
                onAllowReactionsChange = viewModel::setAllowReactions,
                onOfflineExchangeChange = viewModel::setOfflineExchangeEnabled,
                onOpenMusicSelect = onOpenMusicSelect,
                onOpenNotificationAccess = onOpenNotificationAccess,
                onOpenOfflineExchange = onOpenOfflineExchange,
                modifier = contentModifier.safeDrawingPadding()
            )
        }
    }
}
