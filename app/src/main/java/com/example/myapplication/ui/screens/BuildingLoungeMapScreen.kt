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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.example.myapplication.data.remote.LoungeMusicSearchResultDto
import com.example.myapplication.data.remote.LoungeMemberProfileDto
import com.example.myapplication.data.remote.SubLoungeSummaryDto
import com.example.myapplication.data.remote.SubLoungeSnapshotDto
import com.example.myapplication.ui.BuildingLoungeUiState
import com.example.myapplication.core.model.PreviewPlaybackState
import com.example.myapplication.ui.components.PreviewEqualizerBars
import com.example.myapplication.ui.theme.MossOutline
import com.example.myapplication.ui.theme.MossSurface
import com.example.myapplication.ui.theme.MossSurfaceHigh
import com.example.myapplication.ui.theme.MutedMint
import com.example.myapplication.ui.theme.PaleMint
import com.example.myapplication.ui.theme.SignalGreen
import com.example.myapplication.ui.theme.CurrentSyncPalette
import com.example.myapplication.ui.theme.SyncPalette
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.Locale

private val HIDDEN_OSM_ADDRESSES = setOf("주소 정보 없음", "OpenStreetMap building footprint")

@Composable
fun BuildingLoungeMapScreen(
    state: BuildingLoungeUiState,
    previewPlaybackState: PreviewPlaybackState,
    onLocationUpdate: (Double, Double, Float?) -> Unit,
    onLocationUnavailable: () -> Unit,
    onHeartbeat: (Double, Double, Float?) -> Unit,
    onCreateLounge: () -> Unit,
    onEnter: (String) -> Unit,
    onLeave: () -> Unit,
    onCreateSubLounge: (String, String?) -> Unit,
    onOpenSubLounge: (String) -> Unit,
    onLeaveSubLounge: () -> Unit,
    onDeleteSubLounge: () -> Unit,
    onSearchTracks: (String) -> Unit,
    onSendSearchedTrack: (LoungeMusicSearchResultDto) -> Unit,
    onDeleteCard: (String) -> Unit,
    onReactToCard: (String, String) -> Unit,
    onVote: (String) -> Unit,
    onRefreshSubLounge: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenMembers: () -> Unit,
    profileHandlesByAlias: Map<String, String>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasLocationPermission = context.hasLocationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            context.currentLocation()?.let { location ->
                onLocationUpdate(location.latitude, location.longitude, location.accuracyOrNull())
            } ?: onLocationUnavailable()
        }
    }

    LaunchedEffect(hasLocationPermission, state.enteredLoungeId) {
        if (!hasLocationPermission) return@LaunchedEffect
        while (true) {
            delay(if (state.enteredLoungeId == null) 20_000 else 7_500)
            context.currentLocation()?.let { location ->
                if (state.enteredLoungeId == null) {
                    onLocationUpdate(location.latitude, location.longitude, location.accuracyOrNull())
                } else {
                    onHeartbeat(location.latitude, location.longitude, location.accuracyOrNull())
                }
            } ?: onLocationUnavailable()
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
                                context.currentLocation()?.let { location ->
                                    onLocationUpdate(location.latitude, location.longitude, location.accuracyOrNull())
                                } ?: onLocationUnavailable()
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
            onCreateLounge = onCreateLounge,
            onCreateSubLounge = onCreateSubLounge,
            onOpenSubLounge = onOpenSubLounge,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (state.selectedSubLoungeId != null) {
        SubLoungeDetailSheet(
            state = state,
            previewPlaybackState = previewPlaybackState,
            onDismiss = onLeaveSubLounge,
            onLeave = onLeaveSubLounge,
            onDeleteSubLounge = onDeleteSubLounge,
            onSearchTracks = onSearchTracks,
            onSendSearchedTrack = onSendSearchedTrack,
            onDeleteCard = onDeleteCard,
            onReactToCard = onReactToCard,
            onVote = onVote,
            onRefresh = onRefreshSubLounge,
            onOpenProfile = onOpenProfile,
            onOpenMembers = onOpenMembers,
            profileHandlesByAlias = profileHandlesByAlias,
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
    val palette = CurrentSyncPalette
    val mapStyleOptions = remember(palette) { MapStyleOptions(syncMapStyleJson(palette)) }
    val markerHue = remember(palette.primary) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(palette.primary.toArgb(), it) }[0]
    }

    AndroidView(
        factory = {
            mapView.apply {
                getMapAsync { map ->
                    googleMap = map
                    map.setMapStyle(mapStyleOptions)
                    map.uiSettings.isCompassEnabled = false
                    map.uiSettings.isMapToolbarEnabled = false
                    map.uiSettings.isMyLocationButtonEnabled = false
                    map.uiSettings.isScrollGesturesEnabled = true
                    map.uiSettings.isZoomGesturesEnabled = true
                    map.uiSettings.isRotateGesturesEnabled = false
                    map.uiSettings.isTiltGesturesEnabled = false
                    map.setMinZoomPreference(10f)
                    map.setMaxZoomPreference(19f)
                    @SuppressLint("MissingPermission")
                    if (hasLocationPermission && context.hasLocationPermission()) {
                        map.isMyLocationEnabled = true
                    }
                }
            }
        },
        modifier = modifier
    )

    LaunchedEffect(googleMap, visibleLounges, latestUserLocation, hasLocationPermission, palette) {
        val map = googleMap ?: return@LaunchedEffect
        map.setMapStyle(mapStyleOptions)
        map.clear()
        @SuppressLint("MissingPermission")
        if (hasLocationPermission && context.hasLocationPermission()) {
            map.isMyLocationEnabled = true
        }
        visibleLounges.forEach { lounge ->
            val center = LatLng(lounge.latitude, lounge.longitude)
            val fill = if (lounge.inside) {
                palette.primary.copy(alpha = 0.4f).toArgb()
            } else {
                palette.primarySoft.copy(alpha = 0.12f).toArgb()
            }
            val stroke = if (lounge.inside) {
                palette.primary.toArgb()
            } else {
                palette.primarySoft.copy(alpha = 0.4f).toArgb()
            }
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
                    .icon(BitmapDescriptorFactory.defaultMarker(if (lounge.inside) markerHue else (markerHue + 12f) % 360f))
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

private fun Color.mapHex(): String = String.format(Locale.US, "#%06X", toArgb() and 0xFFFFFF)

private fun syncMapStyleJson(palette: SyncPalette): String = """
[
  {"elementType":"geometry","stylers":[{"color":"${palette.background.mapHex()}"}]},
  {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"${palette.textMuted.mapHex()}"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"${palette.background.mapHex()}"}]},
  {"featureType":"administrative","elementType":"geometry.stroke","stylers":[{"color":"${palette.border.mapHex()}"}]},
  {"featureType":"administrative.locality","elementType":"labels.text.fill","stylers":[{"color":"${palette.text.mapHex()}"}]},
  {"featureType":"landscape.man_made","elementType":"geometry.fill","stylers":[{"color":"${palette.surface.mapHex()}"}]},
  {"featureType":"landscape.natural","elementType":"geometry","stylers":[{"color":"${palette.background.mapHex()}"}]},
  {"featureType":"poi","elementType":"geometry","stylers":[{"color":"${palette.surfaceRaised.mapHex()}"}]},
  {"featureType":"poi.business","stylers":[{"visibility":"off"}]},
  {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"${palette.surface.mapHex()}"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"${palette.surfaceRaised.mapHex()}"}]},
  {"featureType":"road","elementType":"geometry.stroke","stylers":[{"color":"${palette.background.mapHex()}"}]},
  {"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"${palette.textMuted.mapHex()}"}]},
  {"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"${palette.border.mapHex()}"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"${palette.primary.mapHex()}"}]},
  {"featureType":"transit","stylers":[{"visibility":"simplified"}]},
  {"featureType":"transit.station","elementType":"labels.text.fill","stylers":[{"color":"${palette.primarySoft.mapHex()}"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"${palette.surface.mapHex()}"}]},
  {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"${palette.textMuted.mapHex()}"}]}
]
""".trimIndent()

@Composable
private fun LoungeBottomSheetPanel(
    state: BuildingLoungeUiState,
    hasLocationPermission: Boolean,
    onEnter: (String) -> Unit,
    onLeave: () -> Unit,
    onCreateLounge: () -> Unit,
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
                        insideLounges.isNotEmpty() -> "현재 포함된 위치 라운지 ${insideLounges.size}곳"
                        else -> "주변 위치 라운지"
                    },
                    color = PaleMint,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    state.message ?: when {
                        !hasLocationPermission -> "주변 건물을 찾으려면 위치 권한이 필요해요."
                        entered != null -> "사용자가 만든 하위 라운지 ${state.subLounges.size}개"
                        insideLounges.isNotEmpty() -> "눌러서 입장할 건물을 선택하세요."
                        recommended.isNotEmpty() -> "가까운 위치 라운지를 확인할 수 있어요."
                        state.loadFailed -> "서버 연결을 확인하고 다시 시도해 주세요."
                        else -> "주변에 생성된 위치 라운지가 없어요."
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
            onCreateLounge = onCreateLounge,
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
    onCreateLounge: () -> Unit,
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
                    Text("위치 라운지", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            !hasLocationPermission -> "위치 권한을 허용해 주세요."
                            entered != null -> "${entered.name} 안에서 사용자가 만든 방"
                            insideLounges.isNotEmpty() -> "현재 위치에서 입장 가능한 라운지를 선택하세요."
                            else -> "어떤 라운지 반경에도 속하지 않을 때 새 라운지를 만들 수 있어요."
                        },
                        color = MutedMint,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            state.message?.let {
                Text(it, color = SignalGreen, style = MaterialTheme.typography.labelMedium)
            }
            state.userLocation?.accuracyMeters?.takeIf { it > 100f }?.let { accuracy ->
                Text(
                    "위치 정확도가 약 ${accuracy.roundToInt()}m예요. 건물 입장이 정확하지 않을 수 있어요.",
                    color = MutedMint,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (entered == null && insideLounges.isEmpty() && hasLocationPermission) {
                Button(
                    onClick = onCreateLounge,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SignalGreen),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("현재 위치에 라운지 만들기")
                }
            }
            if (entered == null) {
                when {
                    state.loading -> Box(
                        Modifier.fillMaxWidth().height(180.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = SignalGreen) }
                    state.lounges.isEmpty() -> EmptyLoungePanel(
                        if (state.loadFailed) "라운지 정보를 불러오지 못했어요" else "아직 열린 라운지가 없어요",
                        if (state.loadFailed) "잠시 후 위치 새로고침을 다시 시도해 주세요." else "현재 위치에 첫 라운지를 만들어 보세요.",
                    )
                    else -> LazyColumn(
                        modifier = Modifier.height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val visible = if (insideLounges.isEmpty()) state.lounges.take(8) else insideLounges
                        items(visible, key = { it.id }) { lounge ->
                            BuildingLoungeRow(lounge = lounge, onEnter = { onEnter(lounge.id) })
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onShowCreate,
                        enabled = state.subLounges.size < 5,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SignalGreen)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.subLounges.size < 5) "하위 라운지 만들기" else "하위 라운지 5/5")
                    }
                    OutlinedButton(onClick = onLeave, modifier = Modifier.weight(1f)) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("라운지 나가기")
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
                lounge.address?.takeIf { it.isNotBlank() && it !in HIDDEN_OSM_ADDRESSES }?.let { address ->
                    Text(address, color = MutedMint, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                Text(
                    "현재 거리 ${lounge.distanceMeters.roundToInt()}m · 입장 반경 ${lounge.radiusMeters}m",
                    color = MutedMint,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Text(
                if (lounge.inside) "입장" else "반경 밖",
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
        EmptyLoungePanel(
            "아직 하위 라운지가 없어요",
            "위의 ‘하위 라운지 만들기’를 눌러 첫 번째 방을 만들어 보세요.",
        )
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
                        Text(room.style ?: "자유 음악방", color = MutedMint, style = MaterialTheme.typography.labelMedium)
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
    previewPlaybackState: PreviewPlaybackState,
    onDismiss: () -> Unit,
    onLeave: () -> Unit,
    onDeleteSubLounge: () -> Unit,
    onSearchTracks: (String) -> Unit,
    onSendSearchedTrack: (LoungeMusicSearchResultDto) -> Unit,
    onDeleteCard: (String) -> Unit,
    onReactToCard: (String, String) -> Unit,
    onVote: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenMembers: () -> Unit,
    profileHandlesByAlias: Map<String, String>,
) {
    val snapshot = state.subLoungeSnapshot
    var trackQuery by remember(state.selectedSubLoungeId) { mutableStateOf("") }
    var selectedTrack by remember(state.selectedSubLoungeId) { mutableStateOf<LoungeMusicSearchResultDto?>(null) }
    var submissionStarted by remember(state.selectedSubLoungeId) { mutableStateOf(false) }
    var confirmDeleteVisible by remember(state.selectedSubLoungeId) { mutableStateOf(false) }
    LaunchedEffect(state.cardSubmitting) {
        if (state.cardSubmitting) {
            submissionStarted = true
        } else if (submissionStarted) {
            selectedTrack = null
            trackQuery = ""
            submissionStarted = false
        }
    }
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
                val latestCardId = room.cards.maxWithOrNull(
                    compareBy({ it.createdAt }, { it.id }),
                )?.id
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricPanel("참여자", "${room.memberCount}명", Modifier.weight(1f), onClick = onOpenMembers)
                        MetricPanel("재생 중", "${room.listeningStatuses.count { it.isPlaying }}곡", Modifier.weight(1f))
                        MetricPanel("추천", "${room.cards.size}개", Modifier.weight(1f))
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
                state.currentDetectedTrack?.let { nowPlaying ->
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedTrack = LoungeMusicSearchResultDto(
                                    id = "now-playing:${nowPlaying.title}:${nowPlaying.artist}",
                                    title = nowPlaying.title,
                                    artistName = nowPlaying.artist,
                                    artworkUrl = nowPlaying.artworkUrl,
                                    storeUrl = nowPlaying.externalUrl,
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = SignalGreen.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, SignalGreen.copy(alpha = 0.45f)),
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = SignalGreen)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("현재 듣고 있는 노래", color = SignalGreen, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    Text(nowPlaying.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(nowPlaying.artist, color = MutedMint, style = MaterialTheme.typography.bodySmall)
                                }
                                Text("선택", color = SignalGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } ?: item {
                    Text(
                        "현재 재생 중인 곡이 감지되면 바로 선택할 수 있어요.",
                        color = MutedMint,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                item {
                    OutlinedTextField(
                        value = trackQuery,
                        onValueChange = { trackQuery = it.take(80) },
                        label = { Text("추천할 노래 검색") },
                        supportingText = { Text("곡명이나 아티스트를 검색해 추천할 수 있어요.") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            if (state.trackSearchLoading) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = SignalGreen)
                            } else {
                                IconButton(
                                    onClick = { onSearchTracks(trackQuery) },
                                    enabled = trackQuery.trim().length >= 2,
                                ) {
                                    Icon(Icons.Outlined.Search, contentDescription = "노래 검색")
                                }
                            }
                        },
                    )
                }
                if (selectedTrack == null) {
                    items(state.trackSearchResults, key = { "search-${it.id}" }) { track ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedTrack = track
                                trackQuery = ""
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = MossSurfaceHigh,
                            border = BorderStroke(1.dp, MossOutline),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = SignalGreen)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(track.artistName, color = MutedMint, style = MaterialTheme.typography.bodySmall)
                                }
                                Text("선택", color = SignalGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                selectedTrack?.let { track ->
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MossSurfaceHigh,
                            border = BorderStroke(1.dp, SignalGreen.copy(alpha = 0.55f)),
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text("추천 카드 작성", color = SignalGreen, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(12.dp), color = SignalGreen.copy(alpha = 0.14f)) {
                                        Icon(
                                            Icons.Outlined.MusicNote,
                                            contentDescription = null,
                                            tint = SignalGreen,
                                            modifier = Modifier.padding(14.dp).size(28.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                                        Text(track.artistName, color = MutedMint, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    TextButton(onClick = {
                                        selectedTrack = null
                                    }) { Text("변경") }
                                }
                                Button(
                                    onClick = {
                                        onSendSearchedTrack(track)
                                    },
                                    enabled = !state.cardSubmitting,
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SignalGreen),
                                ) {
                                    if (state.cardSubmitting) {
                                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text("보내는 중…", fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(Icons.Outlined.MusicNote, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("추천 보내기", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                if (room.cards.isEmpty()) {
                    item { EmptyLoungePanel("첫 추천을 기다리고 있어요", "좋아하는 곡으로 대화를 시작해 보세요.") }
                } else {
                    items(room.cards, key = { it.id }) { card ->
                        val previewActive = card.id == latestCardId &&
                            previewPlaybackState.matches(card.trackTitle, card.artistName) &&
                            (previewPlaybackState.isLoading || previewPlaybackState.isPlaying || previewPlaybackState.isPaused)
                        val senderHandle = card.senderProfileHandle
                            ?: room.members.orEmpty().firstOrNull { it.displayName == card.senderAlias }?.profileHandle
                            ?: profileHandlesByAlias[card.senderAlias]
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MossSurfaceHigh,
                            border = BorderStroke(1.dp, MossOutline),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        card.trackTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (previewActive) {
                                        PreviewEqualizerBars(
                                            active = previewPlaybackState.isPlaying,
                                            modifier = Modifier.semantics {
                                                contentDescription = if (previewPlaybackState.isPlaying) {
                                                    "추천곡 재생 중"
                                                } else {
                                                    "추천곡 재생 준비 또는 일시정지"
                                                }
                                            },
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    if (card.canDelete) {
                                        IconButton(onClick = { onDeleteCard(card.id) }) {
                                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "추천 삭제")
                                        }
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = senderHandle != null) { senderHandle?.let(onOpenProfile) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.PeopleOutline, contentDescription = null, tint = SignalGreen, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(card.artistName, color = MutedMint, style = MaterialTheme.typography.bodySmall)
                                        Text(card.senderAlias, fontWeight = FontWeight.Bold)
                                    }
                                    if (senderHandle != null) {
                                        Text("프로필 보기", color = SignalGreen, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
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
            if (snapshot?.canDelete == true) {
                item {
                    OutlinedButton(
                        onClick = { confirmDeleteVisible = true },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("하위 라운지 삭제", color = MaterialTheme.colorScheme.error)
                    }
                }
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

    if (confirmDeleteVisible) {
        AlertDialog(
            onDismissRequest = { confirmDeleteVisible = false },
            title = { Text("하위 라운지를 삭제할까요?") },
            text = { Text("추천 음악과 참여 정보가 함께 삭제되며 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteVisible = false
                    onDeleteSubLounge()
                }) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteVisible = false }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun MetricPanel(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.clickable(enabled = onClick != null) { onClick?.invoke() },
        shape = RoundedCornerShape(12.dp),
        color = MossSurfaceHigh,
    ) {
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
            Text("하위 라운지 만들기", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(80) },
                label = { Text("방 이름") },
                supportingText = { Text("2~80자 · 같은 건물에서 중복 이름은 사용할 수 없어요.") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = style,
                onValueChange = { style = it.take(80) },
                label = { Text("음악 스타일 (선택)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = { onCreate(title, style.ifBlank { null }) },
                enabled = title.trim().length >= 2,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = SignalGreen)
            ) {
                Text("만들고 입장하기")
            }
        }
    }
}

@Composable
private fun MissingMapKeyPanel(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(CurrentSyncPalette.background)
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

private fun PreviewPlaybackState.matches(title: String, artist: String): Boolean =
    this.title.trim().equals(title.trim(), ignoreCase = true) &&
        this.artist.trim().equals(artist.trim(), ignoreCase = true)

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
private suspend fun Context.currentLocation(): Location? {
    if (!hasLocationPermission()) return null
    val client = LocationServices.getFusedLocationProviderClient(this)
    val current = withTimeoutOrNull(10_000) {
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
    }

    return current ?: runCatching { client.lastLocation.await() }.getOrNull()
}

private fun Location.accuracyOrNull(): Float? = if (hasAccuracy()) accuracy else null

private fun List<BuildingLoungeSummaryDto>.visibleMapLounges(): List<BuildingLoungeSummaryDto> {
    // Dynamic Wi-Fi lounges are already merged by the server. Draw the actual circles so
    // users can see where a nearby lounge begins before they enter it.
    return sortedBy { it.distanceMeters }.take(6)
}

@Composable
fun LoungeMembersScreen(
    snapshot: SubLoungeSnapshotDto?,
    selfMember: LoungeMemberProfileDto?,
    profileHandlesByAlias: Map<String, String>,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val members = remember(snapshot, selfMember, profileHandlesByAlias) {
        buildList {
            selfMember?.let(::add)
            addAll(snapshot?.members.orEmpty())
            snapshot?.cards.orEmpty().forEach { card ->
                (card.senderProfileHandle ?: profileHandlesByAlias[card.senderAlias])?.let { handle ->
                    add(LoungeMemberProfileDto(handle, card.senderAlias, "#6750A4"))
                }
            }
            snapshot?.listeningStatuses.orEmpty().forEach { listening ->
                (listening.listenerProfileHandle ?: profileHandlesByAlias[listening.listenerAlias])?.let { handle ->
                    add(LoungeMemberProfileDto(handle, listening.listenerAlias, "#40897A"))
                }
            }
        }.distinctBy(LoungeMemberProfileDto::profileHandle)
    }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
            }
            Column(Modifier.weight(1f)) {
                Text("라운지 참여자", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${snapshot?.memberCount ?: members.size}명 참여 중", color = MutedMint)
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (members.isEmpty()) {
                item {
                    EmptyLoungePanel("참여자 프로필을 불러오는 중이에요", "잠시 후 라운지를 새로고침해 주세요.")
                }
            }
            items(members, key = LoungeMemberProfileDto::profileHandle) { member ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenProfile(member.profileHandle) },
                    shape = RoundedCornerShape(16.dp),
                    color = MossSurfaceHigh,
                    border = BorderStroke(1.dp, MossOutline),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(46.dp),
                            shape = CircleShape,
                            color = runCatching { Color(android.graphics.Color.parseColor(member.profileColor)) }
                                .getOrDefault(SignalGreen.copy(alpha = 0.35f)),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(member.displayName.take(1).uppercase(), fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(member.displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        }
                        Text("프로필 보기", color = SignalGreen, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

private fun BuildingLoungeSummaryDto.displayRadiusMeters(): Int = radiusMeters.coerceIn(5, 2_000)
