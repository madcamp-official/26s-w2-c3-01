package com.example.myapplication.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myapplication.BuildConfig
import com.example.myapplication.R
import com.example.myapplication.core.model.ConnectionState
import com.example.myapplication.data.remote.BuildingLoungeSummaryDto
import com.example.myapplication.data.remote.SubLoungeSummaryDto
import com.example.myapplication.ui.BuildingLoungeUiState
import com.example.myapplication.ui.theme.MossOutline
import com.example.myapplication.ui.theme.MossSurface
import com.example.myapplication.ui.theme.MossSurfaceHigh
import com.example.myapplication.ui.theme.MutedMint
import com.example.myapplication.ui.theme.PaleMint
import com.example.myapplication.ui.theme.SignalGreen
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun BuildingLoungeMapScreen(
    state: BuildingLoungeUiState,
    onLocationUpdate: (Double, Double, Float?) -> Unit,
    onHeartbeat: (Double, Double, Float?) -> Unit,
    onEnter: (String) -> Unit,
    onLeave: () -> Unit,
    onCreateSubLounge: (String, String?) -> Unit,
    onOpenSubLounge: (String) -> Unit,
    onLeaveSubLounge: () -> Unit,
    onSendTrack: (String?) -> Unit,
    onReactToCard: (String, String) -> Unit,
    onVote: (String) -> Unit,
    onRefreshSubLounge: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasLocationPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            context.hasLocationPermission()
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            context.lastKnownLocation()?.let { location ->
                onLocationUpdate(location.latitude, location.longitude, location.accuracyOrNull())
            }
        }
    }

    LaunchedEffect(hasLocationPermission, state.enteredLoungeId) {
        if (!hasLocationPermission) return@LaunchedEffect
        while (true) {
            delay(if (state.enteredLoungeId == null) 20_000 else 7_500)
            context.lastKnownLocation()?.let { location ->
                if (state.enteredLoungeId == null) {
                    onLocationUpdate(location.latitude, location.longitude, location.accuracyOrNull())
                } else {
                    onHeartbeat(location.latitude, location.longitude, location.accuracyOrNull())
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (BuildConfig.GOOGLE_MAPS_API_KEY.isBlank()) {
            MissingMapKeyPanel(modifier = Modifier.fillMaxSize())
        } else {
            BuildingGoogleMap(
                lounges = state.lounges,
                userLocation = state.userLocation?.let { LatLng(it.latitude, it.longitude) },
                hasLocationPermission = hasLocationPermission,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 18.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MossSurface.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, MossOutline)
            ) {
                IconButton(
                    onClick = {
                        if (hasLocationPermission) {
                            coroutineScope.launch {
                                context.lastKnownLocation()?.let { location ->
                                    onLocationUpdate(location.latitude, location.longitude, location.accuracyOrNull())
                                }
                            }
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Outlined.MyLocation, contentDescription = "Refresh location", tint = PaleMint)
                }
            }
        }

        LoungeBottomSheetPanel(
            state = state,
            hasLocationPermission = hasLocationPermission,
            onEnter = onEnter,
            onLeave = onLeave,
            onCreateSubLounge = onCreateSubLounge,
            onOpenSubLounge = onOpenSubLounge,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (state.selectedSubLoungeId != null) {
        SubLoungeDetailSheet(
            state = state,
            onDismiss = onLeaveSubLounge,
            onLeave = onLeaveSubLounge,
            onSendTrack = onSendTrack,
            onReactToCard = onReactToCard,
            onVote = onVote,
            onRefresh = onRefreshSubLounge,
        )
    }
}

@Composable
private fun BuildingGoogleMap(
    lounges: List<BuildingLoungeSummaryDto>,
    userLocation: LatLng?,
    hasLocationPermission: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = rememberMapViewWithLifecycle()
    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
    val latestUserLocation by rememberUpdatedState(userLocation)
    val visibleLounges = remember(lounges) { lounges.visibleMapLounges() }

    AndroidView(
        factory = {
            mapView.apply {
                getMapAsync { map ->
                    googleMap = map
                    map.setMapStyle(MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_melody))
                    map.uiSettings.isCompassEnabled = false
                    map.uiSettings.isMapToolbarEnabled = false
                    map.uiSettings.isMyLocationButtonEnabled = false
                    map.uiSettings.isScrollGesturesEnabled = false
                    map.uiSettings.isZoomGesturesEnabled = false
                    map.uiSettings.isRotateGesturesEnabled = false
                    map.uiSettings.isTiltGesturesEnabled = false
                    map.setMinZoomPreference(16.8f)
                    map.setMaxZoomPreference(17.4f)
                    @SuppressLint("MissingPermission")
                    if (hasLocationPermission && context.hasLocationPermission()) {
                        map.isMyLocationEnabled = true
                    }
                }
            }
        },
        modifier = modifier
    )

    LaunchedEffect(googleMap, visibleLounges, latestUserLocation, hasLocationPermission) {
        val map = googleMap ?: return@LaunchedEffect
        map.clear()
        @SuppressLint("MissingPermission")
        if (hasLocationPermission && context.hasLocationPermission()) {
            map.isMyLocationEnabled = true
        }
        visibleLounges.forEach { lounge ->
            val center = LatLng(lounge.latitude, lounge.longitude)
            val fill = if (lounge.inside) 0x6637D67AL.toInt() else 0x184285F4
            val stroke = if (lounge.inside) 0xFF25C76FL.toInt() else 0x664285F4
            map.addCircle(
                CircleOptions()
                    .center(center)
                    .radius(lounge.displayRadiusMeters().toDouble())
                    .strokeWidth(if (lounge.inside) 5f else 3f)
                    .strokeColor(stroke)
                    .fillColor(fill)
            )
            map.addMarker(
                MarkerOptions()
                    .position(center)
                    .title(lounge.name)
                    .snippet(if (lounge.inside) "Available now" else "${lounge.distanceMeters.roundToInt()}m away")
                    .icon(BitmapDescriptorFactory.defaultMarker(if (lounge.inside) 140f else 150f))
            )
        }
        val cameraTarget = latestUserLocation ?: visibleLounges.firstOrNull()?.let {
            LatLng(it.latitude, it.longitude)
        }
        cameraTarget?.let {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 17.1f))
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> Unit
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

@Composable
private fun LoungeBottomSheetPanel(
    state: BuildingLoungeUiState,
    hasLocationPermission: Boolean,
    onEnter: (String) -> Unit,
    onLeave: () -> Unit,
    onCreateSubLounge: (String, String?) -> Unit,
    onOpenSubLounge: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var createSheetVisible by remember { mutableStateOf(false) }
    var roomSheetVisible by remember { mutableStateOf(false) }
    val insideLounges = state.lounges.filter { it.inside }
    val entered = state.lounges.firstOrNull { it.id == state.enteredLoungeId }
    val recommended = if (insideLounges.isNotEmpty()) insideLounges else state.lounges.take(4)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        shape = RoundedCornerShape(14.dp),
        color = MossSurface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, MossOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { roomSheetVisible = true }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Radar, contentDescription = null, tint = SignalGreen)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        entered != null -> entered.name
                        insideLounges.isNotEmpty() -> "입장 가능한 실제 건물 ${insideLounges.size}곳"
                        else -> "주변 실제 건물 라운지"
                    },
                    color = PaleMint,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    when {
                        !hasLocationPermission -> "주변 건물을 찾으려면 위치 권한이 필요해요."
                        entered != null -> "사용자가 만든 하위 라운지 ${state.subLounges.size}개"
                        insideLounges.isNotEmpty() -> "눌러서 입장할 건물을 선택하세요."
                        recommended.isNotEmpty() -> "가까운 실제 건물을 확인할 수 있어요."
                        state.loadFailed -> "서버 연결을 확인하고 다시 시도해 주세요."
                        else -> "주변에 등록된 실제 건물이 없어요."
                    },
                    color = MutedMint,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = SignalGreen, strokeWidth = 3.dp)
            } else {
                Text("열기", color = SignalGreen, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (createSheetVisible) {
        CreateSubLoungeSheet(
            onDismiss = { createSheetVisible = false },
            onCreate = { title, style ->
                onCreateSubLounge(title, style)
                createSheetVisible = false
            }
        )
    }

    if (roomSheetVisible) {
        LoungeRoomsSheet(
            state = state,
            hasLocationPermission = hasLocationPermission,
            onDismiss = { roomSheetVisible = false },
            onEnter = onEnter,
            onLeave = onLeave,
            onShowCreate = {
                roomSheetVisible = false
                createSheetVisible = true
            },
            onOpenSubLounge = { id ->
                roomSheetVisible = false
                onOpenSubLounge(id)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoungeRoomsSheet(
    state: BuildingLoungeUiState,
    hasLocationPermission: Boolean,
    onDismiss: () -> Unit,
    onEnter: (String) -> Unit,
    onLeave: () -> Unit,
    onShowCreate: () -> Unit,
    onOpenSubLounge: (String) -> Unit,
) {
    val insideLounges = state.lounges.filter { it.inside }
    val entered = state.lounges.firstOrNull { it.id == state.enteredLoungeId }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MossSurface,
        contentColor = PaleMint
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Map, contentDescription = null, tint = SignalGreen)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Recommended rooms", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            !hasLocationPermission -> "Location permission is needed."
                            entered != null -> "Rooms inside ${entered.name}"
                            insideLounges.isNotEmpty() -> "Choose a lounge available at your real location."
                            else -> "Nearby candidates are listed as hints. Move closer to unlock them."
                        },
                        color = MutedMint,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            state.message?.let {
                Text(it, color = SignalGreen, style = MaterialTheme.typography.labelMedium)
            }
            if (entered == null) {
                LazyColumn(
                    modifier = Modifier.height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val visible = if (insideLounges.isEmpty()) state.lounges.take(8) else insideLounges
                    items(visible, key = { it.id }) { lounge ->
                        BuildingLoungeRow(lounge = lounge, onEnter = { onEnter(lounge.id) })
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onShowCreate,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SignalGreen)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Sub lounge")
                    }
                    OutlinedButton(onClick = onLeave, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Leave")
                    }
                }
                SubLoungeList(state.subLounges, onOpenSubLounge)
            }
        }
    }
}

@Composable
private fun BuildingLoungeRow(
    lounge: BuildingLoungeSummaryDto,
    onEnter: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = lounge.inside, onClick = onEnter),
        shape = RoundedCornerShape(12.dp),
        color = if (lounge.inside) SignalGreen.copy(alpha = 0.12f) else MossSurfaceHigh,
        border = BorderStroke(1.dp, if (lounge.inside) SignalGreen.copy(alpha = 0.45f) else MossOutline)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = if (lounge.inside) SignalGreen else MutedMint)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(lounge.name, color = PaleMint, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${lounge.distanceMeters.roundToInt()}m away / radius ${lounge.radiusMeters}m",
                    color = MutedMint,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Text(
                if (lounge.inside) "Enter" else "Outside",
                color = if (lounge.inside) SignalGreen else MutedMint,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SubLoungeList(
    subLounges: List<SubLoungeSummaryDto>,
    onOpen: (String) -> Unit,
) {
    if (subLounges.isEmpty()) {
        Text("No sub lounge yet. Create the first taste room.", color = MutedMint)
        return
    }
    LazyColumn(
        modifier = Modifier.height(180.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(subLounges, key = { it.id }) { room ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(room.id) },
                shape = RoundedCornerShape(12.dp),
                color = MossSurfaceHigh,
                border = BorderStroke(1.dp, MossOutline)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.PeopleOutline, contentDescription = null, tint = SignalGreen)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(room.title, color = PaleMint, fontWeight = FontWeight.Bold)
                        Text(room.style ?: "Free taste room", color = MutedMint, style = MaterialTheme.typography.labelMedium)
                    }
                    Text("${room.memberCount}명 · 입장", color = SignalGreen, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubLoungeDetailSheet(
    state: BuildingLoungeUiState,
    onDismiss: () -> Unit,
    onLeave: () -> Unit,
    onSendTrack: (String?) -> Unit,
    onReactToCard: (String, String) -> Unit,
    onVote: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val snapshot = state.subLoungeSnapshot
    var cardMessage by remember(state.selectedSubLoungeId) { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MossSurface,
        contentColor = PaleMint,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp,
                end = 18.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            snapshot?.title ?: "하위 라운지 연결 중",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            snapshot?.style ?: "실시간 상태를 불러오고 있어요",
                            color = MutedMint,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (state.realtimeState == ConnectionState.LIVE) {
                            SignalGreen.copy(alpha = 0.16f)
                        } else {
                            MossSurfaceHigh
                        },
                    ) {
                        Text(
                            if (state.realtimeState == ConnectionState.LIVE) "실시간" else "재연결 중",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            color = if (state.realtimeState == ConnectionState.LIVE) SignalGreen else MutedMint,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "라운지 새로고침")
                    }
                }
            }

            if (state.detailLoading && snapshot == null) {
                item {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SignalGreen)
                    }
                }
            }

            snapshot?.let { room ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricPanel("참여자", "${room.memberCount}명", Modifier.weight(1f))
                        MetricPanel("재생 중", "${room.listeningStatuses.count { it.isPlaying }}곡", Modifier.weight(1f))
                        MetricPanel("추천", "${room.cards.size}개", Modifier.weight(1f))
                    }
                }

                item { SectionLabel("지금 함께 듣는 음악") }
                if (room.listeningStatuses.isEmpty()) {
                    item { EmptyLoungePanel("아직 공유된 음악이 없어요", "음악을 재생하면 자동으로 이곳에 표시돼요.") }
                } else {
                    items(room.listeningStatuses, key = { "${it.listenerAlias}-${it.updatedAt}" }) { listening ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MossSurfaceHigh,
                            border = BorderStroke(1.dp, MossOutline),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = SignalGreen)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(listening.trackTitle ?: "재생을 멈췄어요", fontWeight = FontWeight.Bold)
                                    Text(
                                        listOfNotNull(listening.artistName, listening.listenerAlias).joinToString(" · "),
                                        color = MutedMint,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }

                item { SectionLabel("오늘의 분위기") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        room.poll.options.forEach { option ->
                            val selected = room.poll.myVote == option.key
                            OutlinedButton(
                                onClick = { onVote(option.key) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                border = BorderStroke(1.dp, if (selected) SignalGreen else MossOutline),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) SignalGreen.copy(alpha = 0.14f) else Color.Transparent,
                                ),
                            ) {
                                Text("${option.key.toMoodLabel()} ${option.voteCount}", maxLines = 1)
                            }
                        }
                    }
                }

                item { SectionLabel("추천 음악 카드") }
                item {
                    OutlinedTextField(
                        value = cardMessage,
                        onValueChange = { cardMessage = it.take(120) },
                        label = { Text("추천 한마디 (선택)") },
                        supportingText = { Text("현재 실제 재생 중인 곡이 카드로 공유돼요.") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                    )
                }
                item {
                    Button(
                        onClick = {
                            onSendTrack(cardMessage.ifBlank { null })
                            cardMessage = ""
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SignalGreen),
                    ) {
                        Icon(Icons.Outlined.MusicNote, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("지금 듣는 곡 추천하기", fontWeight = FontWeight.Bold)
                    }
                }
                if (room.cards.isEmpty()) {
                    item { EmptyLoungePanel("첫 추천을 기다리고 있어요", "좋아하는 곡으로 대화를 시작해 보세요.") }
                } else {
                    items(room.cards, key = { it.id }) { card ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MossSurfaceHigh,
                            border = BorderStroke(1.dp, MossOutline),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(card.trackTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${card.artistName} · ${card.senderAlias}", color = MutedMint)
                                card.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                OutlinedButton(
                                    onClick = { onReactToCard(card.id, "LIKE") },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    border = BorderStroke(1.dp, if (card.reactedByMe) SignalGreen else MossOutline),
                                ) {
                                    Icon(Icons.Outlined.FavoriteBorder, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (card.reactedByMe) "공감했어요 · ${card.reactionCount}" else "공감 · ${card.reactionCount}")
                                }
                            }
                        }
                    }
                }
            }

            state.message?.let { message ->
                item { Text(message, color = SignalGreen, style = MaterialTheme.typography.bodyMedium) }
            }
            item {
                OutlinedButton(
                    onClick = onLeave,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("하위 라운지 나가기")
                }
            }
        }
    }
}

@Composable
private fun MetricPanel(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(12.dp), color = MossSurfaceHigh) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = SignalGreen, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, color = MutedMint, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = PaleMint)
}

@Composable
private fun EmptyLoungePanel(title: String, body: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MossSurfaceHigh, border = BorderStroke(1.dp, MossOutline)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(body, color = MutedMint, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun String.toMoodLabel(): String = when (this) {
    "CHILL" -> "차분"
    "FOCUS" -> "집중"
    "ENERGY" -> "활기"
    else -> this
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateSubLoungeSheet(
    onDismiss: () -> Unit,
    onCreate: (String, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var style by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MossSurface,
        contentColor = PaleMint
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Create sub lounge", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(80) },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = style,
                onValueChange = { style = it.take(80) },
                label = { Text("Style") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = { onCreate(title, style.ifBlank { null }) },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = SignalGreen)
            ) {
                Text("Create")
            }
        }
    }
}

@Composable
private fun MissingMapKeyPanel(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF07140D))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Map, contentDescription = null, tint = SignalGreen, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("Google Maps key is missing.", color = PaleMint, style = MaterialTheme.typography.titleLarge)
            Text("Set GOOGLE_MAPS_API_KEY in local.properties.", color = MutedMint)
        }
    }
}

@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    return remember {
        MapView(context).apply {
            onCreate(Bundle())
        }
    }
}

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
private suspend fun Context.lastKnownLocation(): Location? {
    if (!hasLocationPermission()) return null
    val client = LocationServices.getFusedLocationProviderClient(this)
    return client.lastLocation.await()
}

private fun Location.accuracyOrNull(): Float? = if (hasAccuracy()) accuracy else null

private fun List<BuildingLoungeSummaryDto>.visibleMapLounges(): List<BuildingLoungeSummaryDto> {
    val selected = mutableListOf<BuildingLoungeSummaryDto>()
    filter { it.inside }
        .sortedBy { it.distanceMeters }
        .forEach { candidate ->
            if (selected.size >= 6) return@forEach
            val overlaps = selected.any { existing ->
                distanceMeters(candidate.latitude, candidate.longitude, existing.latitude, existing.longitude) <
                    (candidate.displayRadiusMeters() + existing.displayRadiusMeters()) * 0.72
            }
            if (!overlaps) selected += candidate
        }
    return selected
}

private fun BuildingLoungeSummaryDto.displayRadiusMeters(): Int = when {
    inside -> radiusMeters.coerceIn(90, 220)
    category == "SHOPPING" || category == "SHOPPING_MALL" -> radiusMeters.coerceIn(110, 180)
    else -> radiusMeters.coerceIn(70, 130)
}

private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val radius = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return radius * 2 * atan2(sqrt(a), sqrt(1 - a))
}
