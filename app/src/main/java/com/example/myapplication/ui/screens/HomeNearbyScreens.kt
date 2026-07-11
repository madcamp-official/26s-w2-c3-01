package com.example.myapplication.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.ReportGmailerrorred
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.core.model.ConnectionState
import com.example.myapplication.core.model.MelodyUiState
import com.example.myapplication.core.model.NearbyListener
import com.example.myapplication.core.model.PopularTrack
import com.example.myapplication.core.model.RelationshipStatus
import com.example.myapplication.core.model.SharingState
import com.example.myapplication.core.model.Track
import com.example.myapplication.ui.components.MelodyBubbleColors
import com.example.myapplication.ui.components.MelodyCard
import com.example.myapplication.ui.components.SectionTitle

private val ScreenHorizontalPadding = 20.dp

enum class NearbyMusicFilter(val label: String) {
    ALL("전체"),
    SAME_MUSIC("같은 앨범/가수/노래")
}

private enum class MusicMatchLevel {
    NONE,
    ALBUM_OR_ARTIST,
    SONG
}

@Composable
private fun screenTopInsets(): WindowInsets = WindowInsets.safeDrawing.only(
    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
)

/**
 * Home overview. Navigation is deliberately owned by the parent Scaffold so all main tabs share it.
 */
@Composable
fun HomeScreen(
    state: MelodyUiState,
    modifier: Modifier = Modifier,
    onStartSharing: () -> Unit = {},
    onStopSharing: () -> Unit = {},
    onOpenNearby: () -> Unit = {},
    onSelectListener: (NearbyListener) -> Unit = {},
    onSelectTrack: (Track) -> Unit = {}
) {
    val isSharingActive = state.sharingState == SharingState.ACTIVE
    val visibleListeners = if (isSharingActive) state.nearbyListeners else emptyList()
    val nearestMusic = visibleListeners.firstOrNull { it.isPlaying && it.currentTrack != null }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MelodyBubbleColors.Background)
            .windowInsetsPadding(screenTopInsets()),
        contentPadding = PaddingValues(
            start = ScreenHorizontalPadding,
            top = 18.dp,
            end = ScreenHorizontalPadding,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HomeHeader(
                listenerCount = visibleListeners.size,
                dataSourceLabel = state.dataSourceLabel
            )
        }
        item {
            CompactRadar(
                listeners = visibleListeners,
                nearestMusic = nearestMusic,
                onOpenNearby = onOpenNearby
            )
        }
        item {
            MyCurrentTrackCard(
                track = state.currentTrack,
                sharingEnabled = isSharingActive,
                onClick = { onSelectTrack(state.currentTrack) },
                onSharingToggle = {
                    if (isSharingActive) onStopSharing() else onStartSharing()
                }
            )
        }
        item {
            SectionTitle(
                title = "주변 인기 음악",
                subtitle = "개인을 특정할 수 없는 집계 데이터"
            )
        }
        item {
            PopularTrackCarousel(
                tracks = state.popularTracks,
                onSelectTrack = onSelectTrack
            )
        }
    }
}

/**
 * Nearby discovery. [displayPosition] is a server-provided drawing coordinate only; it is never
 * converted to metres, bearings, or a physical map.
 */
@Composable
fun NearbyScreen(
    state: MelodyUiState,
    modifier: Modifier = Modifier,
    similarityThreshold: Int = 60,
    musicFilter: NearbyMusicFilter = NearbyMusicFilter.ALL,
    onSimilarityThresholdChange: (Int) -> Unit = {},
    onMusicFilterChange: (NearbyMusicFilter) -> Unit = {},
    onSelectListener: (NearbyListener) -> Unit = {},
    onOpenListenerDetail: (NearbyListener) -> Unit = {},
    onReact: (NearbyListener, String) -> Unit = { _, _ -> },
    onFollow: (NearbyListener) -> Unit = {}
) {
    val isSharingActive = state.sharingState == SharingState.ACTIVE
    val visibleListeners = if (isSharingActive) state.nearbyListeners else emptyList()
    val currentTrack = state.currentTrack
    val filteredListeners = visibleListeners.filter {
        it.matchesMusicFilter(currentTrack, musicFilter)
    }
    val selected = filteredListeners.firstOrNull {
        it.nearbyHandle == state.selectedNearbyHandle
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MelodyBubbleColors.Background)
            .windowInsetsPadding(screenTopInsets()),
        contentPadding = PaddingValues(
            start = ScreenHorizontalPadding,
            top = 18.dp,
            end = ScreenHorizontalPadding,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionTitle(
                title = "근처",
                subtitle = when {
                    state.connectionState == ConnectionState.LIVE -> "공유 중 · 실시간 연결"
                    else -> "위치 공유가 꺼져 있어 주변 버블을 볼 수 없어요"
                }
            )
        }
        item {
            NearbyMusicFilterRow(
                selectedFilter = musicFilter,
                onFilterChange = onMusicFilterChange
            )
        }
        item {
            AbstractNearbyMap(
                listeners = filteredListeners,
                selectedHandle = selected?.nearbyHandle,
                currentTrack = currentTrack,
                onSelectListener = onSelectListener
            )
        }
        item {
            Text(
                text = "버블 배치는 탐색 신호를 위한 추상 표현이며 실제 거리·방향·이동 경로와 무관해요.",
                modifier = Modifier.fillMaxWidth(),
                color = MelodyBubbleColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
        item {
            SectionTitle(
                title = "선택한 리스너",
                subtitle = when {
                    !isSharingActive -> "위치 공유를 켜야 주변 리스너를 확인할 수 있어요"
                    filteredListeners.isEmpty() -> "조건에 맞는 리스너가 없어요"
                    selected == null -> "레이더의 이름을 눌러 상세를 확인하세요"
                    else -> null
                }
            )
        }
        item {
            if (selected == null) {
                NoListenerResult(
                    threshold = similarityThreshold,
                    message = when {
                        !isSharingActive -> "위치 공유안함 상태예요"
                        filteredListeners.isEmpty() -> "조건에 맞는 버블이 없어요"
                        else -> "확인할 리스너를 선택해 주세요"
                    }
                )
            } else {
                SelectedListenerCard(
                    listener = selected,
                    onOpenDetail = { onOpenListenerDetail(selected) },
                    onReact = { onReact(selected, "이 곡 좋아요") },
                    onFollow = { onFollow(selected) }
                )
            }
        }
    }
}

/**
 * Stateless detail surface. The caller owns reaction-sheet visibility and all domain actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailScreen(
    listener: NearbyListener,
    modifier: Modifier = Modifier,
    reactionSheetVisible: Boolean = false,
    onBack: () -> Unit = {},
    onOpenTrack: (Track) -> Unit = {},
    onShowReactionSheet: () -> Unit = {},
    onDismissReactionSheet: () -> Unit = {},
    onReact: (NearbyListener, String) -> Unit = { _, _ -> },
    onFollow: (NearbyListener) -> Unit = {},
    onOpenChat: (NearbyListener) -> Unit = {},
    onBlock: (NearbyListener) -> Unit = {},
    onReport: (NearbyListener) -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MelodyBubbleColors.Background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(
                start = ScreenHorizontalPadding,
                top = 8.dp,
                end = ScreenHorizontalPadding,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "뒤로 가기",
                            tint = MelodyBubbleColors.Text
                        )
                    }
                    Text(
                        text = "사용자 상세",
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                        color = MelodyBubbleColors.Text,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.size(48.dp))
                }
            }
            item {
                ListenerIdentity(listener = listener)
            }
            item {
                CurrentTrackCard(
                    listener = listener,
                    onOpenTrack = onOpenTrack
                )
            }
            item {
                CommonTasteCard(listener = listener)
            }
            item {
                Button(
                    onClick = onShowReactionSheet,
                    enabled = listener.relationship != RelationshipStatus.BLOCKED,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MelodyBubbleColors.Primary,
                        contentColor = MelodyBubbleColors.OnPrimary,
                        disabledContainerColor = MelodyBubbleColors.SurfaceRaised,
                        disabledContentColor = MelodyBubbleColors.TextMuted
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("음악 리액션 보내기", fontWeight = FontWeight.Bold)
                }
            }
            item {
                OutlinedButton(
                    onClick = { onFollow(listener) },
                    enabled = listener.relationship != RelationshipStatus.BLOCKED &&
                        listener.relationship != RelationshipStatus.MUTUAL,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    border = BorderStroke(1.dp, MelodyBubbleColors.BorderStrong),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MelodyBubbleColors.Text,
                        disabledContentColor = MelodyBubbleColors.TextMuted
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PersonAddAlt,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(listener.relationship.followLabel(), fontWeight = FontWeight.Bold)
                }
            }
            if (listener.relationship == RelationshipStatus.MUTUAL) {
                item {
                    Button(
                        onClick = { onOpenChat(listener) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MelodyBubbleColors.Primary,
                            contentColor = MelodyBubbleColors.OnPrimary
                        )
                    ) {
                        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("채팅하기", fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                HorizontalDivider(color = MelodyBubbleColors.Border)
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { onBlock(listener) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MelodyBubbleColors.TextMuted
                        )
                    ) {
                        Icon(Icons.Outlined.Block, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("차단")
                    }
                    TextButton(
                        onClick = { onReport(listener) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MelodyBubbleColors.Danger
                        )
                    ) {
                        Icon(Icons.Outlined.ReportGmailerrorred, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("신고")
                    }
                }
            }
        }

        if (reactionSheetVisible) {
            ReactionSheet(
                listener = listener,
                onDismiss = onDismissReactionSheet,
                onReactionSelected = { reaction ->
                    onReact(listener, reaction)
                    onDismissReactionSheet()
                }
            )
        }
    }
}

@Composable
private fun HomeHeader(
    listenerCount: Int,
    dataSourceLabel: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(16.dp),
            color = MelodyBubbleColors.Primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = "Melody Bubble",
                    tint = MelodyBubbleColors.OnPrimary
                )
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Melody Bubble",
                color = MelodyBubbleColors.Text,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "지금 주변의 음악을 가볍게 발견해요",
                color = MelodyBubbleColors.TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MelodyBubbleColors.Primary.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, MelodyBubbleColors.Border)
        ) {
            Text(
                text = "$listenerCount 버블 · $dataSourceLabel",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                color = MelodyBubbleColors.PrimarySoft,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MyCurrentTrackCard(
    track: Track,
    sharingEnabled: Boolean,
    onClick: () -> Unit,
    onSharingToggle: () -> Unit
) {
    MelodyCard(
        onClick = onClick,
        onClickLabel = "${track.title} 현재 음악 선택",
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(14.dp),
                color = MelodyBubbleColors.SurfaceSelected
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = MelodyBubbleColors.Primary
                    )
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "현재 음악",
                    color = MelodyBubbleColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = track.title,
                    color = MelodyBubbleColors.Text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(track.artist, track.album).joinToString(" · "),
                    color = MelodyBubbleColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .clickable(
                        role = Role.Switch,
                        onClickLabel = if (sharingEnabled) "공유 비허용" else "공유 허용",
                        onClick = onSharingToggle
                    ),
                shape = CircleShape,
                color = if (sharingEnabled) {
                    MelodyBubbleColors.Primary
                } else {
                    MelodyBubbleColors.SurfaceRaised
                },
                border = BorderStroke(
                    1.dp,
                    if (sharingEnabled) MelodyBubbleColors.Primary else MelodyBubbleColors.BorderStrong
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Radar,
                        contentDescription = if (sharingEnabled) "공유 허용" else "공유 비허용",
                        modifier = Modifier.size(21.dp),
                        tint = if (sharingEnabled) {
                            MelodyBubbleColors.OnPrimary
                        } else {
                            MelodyBubbleColors.TextMuted
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactRadar(
    listeners: List<NearbyListener>,
    nearestMusic: NearbyListener?,
    onOpenNearby: () -> Unit
) {
    MelodyCard(
        onClick = onOpenNearby,
        onClickLabel = "근처 화면 열기",
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        contentDescription = "추상 주변 레이더, 리스너 ${listeners.size}명. 실제 위치나 방향을 나타내지 않음"
                    }
            ) {
                val center = Offset(size.width * 0.5f, size.height * 0.5f)
                listOf(0.22f, 0.39f, 0.56f).forEach { scale ->
                    drawCircle(
                        color = MelodyBubbleColors.Border.copy(alpha = 0.82f),
                        radius = size.minDimension * scale,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                listeners.take(6).forEach { listener ->
                    val position = listener.displayPosition.safePosition()
                    drawCircle(
                        color = Color(listener.colorHex).copy(alpha = 0.22f),
                        radius = if (listener.isNew) 18.dp.toPx() else 13.dp.toPx(),
                        center = Offset(size.width * position.x, size.height * position.y)
                    )
                    drawCircle(
                        color = Color(listener.colorHex),
                        radius = 6.dp.toPx(),
                        center = Offset(size.width * position.x, size.height * position.y)
                    )
                }
                drawCircle(
                    color = MelodyBubbleColors.Primary,
                    radius = 8.dp.toPx(),
                    center = center
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.58f)
                    .padding(18.dp)
            ) {
                Text(
                    text = "주변 버블 스냅샷",
                    color = MelodyBubbleColors.Text,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "실제 거리·방향을 숨긴 추상 분포",
                    color = MelodyBubbleColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            nearestMusic?.currentTrack?.let { track ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .widthIn(max = 168.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MelodyBubbleColors.Background.copy(alpha = 0.84f),
                    border = BorderStroke(1.dp, MelodyBubbleColors.Border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Headphones,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MelodyBubbleColors.Primary
                        )
                        Spacer(Modifier.width(7.dp))
                        Column {
                            Text(
                                text = "가장 가까운 음악",
                                color = MelodyBubbleColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                            Text(
                                text = track.title,
                                color = MelodyBubbleColors.Text,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "탐색하기",
                    color = MelodyBubbleColors.Primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MelodyBubbleColors.Primary
                )
            }
        }
    }
}

@Composable
private fun NearbyMusicCard(
    listener: NearbyListener,
    onClick: () -> Unit
) {
    val track = listener.currentTrack ?: return
    MelodyCard(
        onClick = onClick,
        onClickLabel = "${listener.displayAlias} 상세 보기"
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ListenerAvatar(
                listener = listener,
                size = 58.dp,
                showWave = true
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = listener.proximity.label,
                        color = MelodyBubbleColors.Primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " · ${listener.displayAlias}",
                        color = MelodyBubbleColors.TextMuted,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = track.title,
                    color = MelodyBubbleColors.Text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    color = MelodyBubbleColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MelodyBubbleColors.TextFaint
            )
        }
    }
}

@Composable
private fun EmptyMusicCard() {
    MelodyCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Headphones,
                contentDescription = null,
                tint = MelodyBubbleColors.TextMuted
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "재생 중인 주변 음악이 아직 없어요",
                color = MelodyBubbleColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ListenerStrip(
    listeners: List<NearbyListener>,
    onSelectListener: (NearbyListener) -> Unit
) {
    if (listeners.isEmpty()) {
        Text(
            text = "공유를 시작하면 주변의 익명 버블이 여기에 나타나요.",
            color = MelodyBubbleColors.TextMuted,
            style = MaterialTheme.typography.bodyMedium
        )
        return
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(end = 4.dp)
    ) {
        items(listeners, key = { it.nearbyHandle }) { listener ->
            Surface(
                modifier = Modifier
                    .width(112.dp)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "${listener.displayAlias} 상세 보기",
                        onClick = { onSelectListener(listener) }
                    ),
                shape = RoundedCornerShape(20.dp),
                color = MelodyBubbleColors.Surface,
                border = BorderStroke(1.dp, MelodyBubbleColors.Border)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ListenerAvatar(listener, size = 48.dp, showWave = false)
                    Spacer(Modifier.height(9.dp))
                    Text(
                        text = listener.displayAlias,
                        color = MelodyBubbleColors.Text,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "유사도 ${listener.matchScore}%",
                        color = MelodyBubbleColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun PopularTrackCarousel(
    tracks: List<PopularTrack>,
    onSelectTrack: (Track) -> Unit
) {
    if (tracks.isEmpty()) {
        EmptyMusicCard()
        return
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(end = 4.dp)
    ) {
        items(tracks, key = { it.track.id }) { popularTrack ->
            PopularTrackCompactCard(
                popularTrack = popularTrack,
                onClick = { onSelectTrack(popularTrack.track) }
            )
        }
    }
}

@Composable
private fun PopularTrackCompactCard(
    popularTrack: PopularTrack,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(176.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = "${popularTrack.track.title} 선택",
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        color = MelodyBubbleColors.Surface,
        border = BorderStroke(1.dp, MelodyBubbleColors.Border)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = MelodyBubbleColors.SurfaceSelected
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                        tint = MelodyBubbleColors.Primary
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = popularTrack.track.title,
                color = MelodyBubbleColors.Text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = popularTrack.track.artist,
                color = MelodyBubbleColors.TextMuted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${popularTrack.listenerCount}명 · 공감 ${popularTrack.reactionCount}",
                color = MelodyBubbleColors.PrimarySoft,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PopularTrackRow(
    popularTrack: PopularTrack,
    onClick: () -> Unit
) {
    MelodyCard(
        onClick = onClick,
        onClickLabel = "${popularTrack.track.title} 선택",
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(15.dp),
                color = MelodyBubbleColors.SurfaceSelected
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.MusicNote,
                        contentDescription = null,
                        tint = MelodyBubbleColors.Primary
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = popularTrack.track.title,
                    color = MelodyBubbleColors.Text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = popularTrack.track.artist,
                    color = MelodyBubbleColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${popularTrack.listenerCount}명 재생",
                    color = MelodyBubbleColors.PrimarySoft,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "공감 ${popularTrack.reactionCount}",
                    color = MelodyBubbleColors.TextFaint,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun NearbyMusicFilterRow(
    selectedFilter: NearbyMusicFilter,
    onFilterChange: (NearbyMusicFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NearbyMusicFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterChange(filter) },
                modifier = Modifier.height(44.dp),
                label = {
                    Text(
                        text = filter.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MelodyBubbleColors.Primary,
                    selectedLabelColor = MelodyBubbleColors.OnPrimary,
                    containerColor = MelodyBubbleColors.Surface,
                    labelColor = MelodyBubbleColors.TextMuted
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedFilter == filter,
                    borderColor = MelodyBubbleColors.Border,
                    selectedBorderColor = MelodyBubbleColors.Primary
                )
            )
        }
    }
}

@Composable
private fun SimilarityFilter(
    threshold: Int,
    resultCount: Int,
    onThresholdChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "취향 유사도",
                color = MelodyBubbleColors.Text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$resultCount 버블",
                color = MelodyBubbleColors.TextMuted,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(60, 75, 90).forEach { value ->
                FilterChip(
                    selected = threshold == value,
                    onClick = { onThresholdChange(value) },
                    modifier = Modifier.height(48.dp),
                    label = { Text("$value% 이상") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MelodyBubbleColors.Primary,
                        selectedLabelColor = MelodyBubbleColors.OnPrimary,
                        containerColor = MelodyBubbleColors.Surface,
                        labelColor = MelodyBubbleColors.TextMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = threshold == value,
                        borderColor = MelodyBubbleColors.Border,
                        selectedBorderColor = MelodyBubbleColors.Primary
                    )
                )
            }
        }
    }
}

@Composable
private fun AbstractNearbyMap(
    listeners: List<NearbyListener>,
    selectedHandle: String?,
    currentTrack: Track,
    onSelectListener: (NearbyListener) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.96f),
        shape = RoundedCornerShape(28.dp),
        color = MelodyBubbleColors.Surface,
        border = BorderStroke(1.dp, MelodyBubbleColors.Border)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        contentDescription = "추상 근처 버블맵. 버블 위치는 실제 거리 또는 방향을 나타내지 않음"
                    }
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                listOf(0.17f, 0.30f, 0.43f).forEach { factor ->
                    drawCircle(
                        color = MelodyBubbleColors.Border,
                        radius = size.minDimension * factor,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                drawCircle(
                    color = MelodyBubbleColors.Primary.copy(alpha = 0.10f),
                    radius = 20.dp.toPx(),
                    center = center
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(42.dp)
                    .semantics { contentDescription = "내 버블" },
                shape = CircleShape,
                color = MelodyBubbleColors.Primary,
                border = BorderStroke(2.dp, MelodyBubbleColors.Primary.copy(alpha = 0.20f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "나",
                        color = MelodyBubbleColors.OnPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            listeners.forEach { listener ->
                val selected = listener.nearbyHandle == selectedHandle
                val musicMatchLevel = listener.musicMatchLevel(currentTrack)
                val pointSize = if (musicMatchLevel == MusicMatchLevel.SONG) 22.dp else 16.dp
                val labelWidth = 88.dp
                val position = listener.displayPosition.safePosition()
                ListenerMapBubble(
                    listener = listener,
                    selected = selected,
                    musicMatchLevel = musicMatchLevel,
                    pointSize = pointSize,
                    modifier = Modifier.offset(
                        x = (maxWidth - labelWidth) * position.x,
                        y = (maxHeight - 54.dp) * position.y
                    ),
                    onClick = { onSelectListener(listener) }
                )
            }
        }
    }
}

@Composable
private fun ListenerMapBubble(
    listener: NearbyListener,
    selected: Boolean,
    musicMatchLevel: MusicMatchLevel,
    pointSize: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = "${listener.displayAlias} 선택",
                onClick = onClick
            )
            .semantics {
                contentDescription = buildString {
                    append(listener.displayAlias)
                    append(", 취향 유사도 ${listener.matchScore}%")
                    append(", ${listener.proximity.label}")
                    if (listener.isPlaying) append(", 음악 재생 중")
                    if (musicMatchLevel == MusicMatchLevel.SONG) append(", 같은 노래 재생 중")
                    if (musicMatchLevel == MusicMatchLevel.ALBUM_OR_ARTIST) append(", 같은 앨범 또는 가수")
                    if (selected) append(", 선택됨")
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(
                    when {
                        musicMatchLevel == MusicMatchLevel.SONG -> 38.dp
                        musicMatchLevel == MusicMatchLevel.ALBUM_OR_ARTIST || selected -> 32.dp
                        else -> 26.dp
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (musicMatchLevel != MusicMatchLevel.NONE) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            if (musicMatchLevel == MusicMatchLevel.SONG) {
                                MelodyBubbleColors.Primary.copy(alpha = 0.18f)
                            } else {
                                Color.Transparent
                            }
                        )
                        .border(
                            width = if (musicMatchLevel == MusicMatchLevel.SONG) 3.dp else 2.dp,
                            color = if (musicMatchLevel == MusicMatchLevel.SONG) {
                                MelodyBubbleColors.Primary
                            } else {
                                MelodyBubbleColors.PrimarySoft.copy(alpha = 0.85f)
                            },
                            shape = CircleShape
                        )
                )
            }
            Box(
                modifier = Modifier
                    .size(pointSize)
                    .clip(CircleShape)
                    .background(Color(listener.colorHex))
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) MelodyBubbleColors.PrimarySoft else Color.White.copy(alpha = 0.35f),
                        shape = CircleShape
                    )
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = listener.displayAlias,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            color = if (selected) MelodyBubbleColors.PrimarySoft else MelodyBubbleColors.Text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SelectedListenerCard(
    listener: NearbyListener,
    onOpenDetail: () -> Unit,
    onReact: () -> Unit,
    onFollow: () -> Unit
) {
    MelodyCard(
        onClick = onOpenDetail,
        onClickLabel = "${listener.displayAlias} 상세 열기"
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ListenerAvatar(listener, size = 56.dp, showWave = listener.isPlaying)
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = listener.displayAlias,
                        color = MelodyBubbleColors.Text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (listener.isNew) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MelodyBubbleColors.Primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "NEW",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                color = MelodyBubbleColors.Primary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = "${listener.proximity.label} · 유사도 ${listener.matchScore}%",
                    color = MelodyBubbleColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = listener.currentTrack?.let { "${it.title} · ${it.artist}" }
                        ?: "현재 공개된 음악 없음",
                    color = MelodyBubbleColors.PrimarySoft,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MelodyBubbleColors.TextFaint
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onReact,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                border = BorderStroke(1.dp, MelodyBubbleColors.BorderStrong),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MelodyBubbleColors.Text
                )
            ) {
                Text("리액션", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onFollow,
                enabled = listener.relationship != RelationshipStatus.BLOCKED &&
                    listener.relationship != RelationshipStatus.MUTUAL,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MelodyBubbleColors.Primary,
                    contentColor = MelodyBubbleColors.OnPrimary,
                    disabledContainerColor = MelodyBubbleColors.SurfaceRaised,
                    disabledContentColor = MelodyBubbleColors.TextMuted
                )
            ) {
                Text(listener.relationship.followLabel(), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NoListenerResult(
    threshold: Int,
    message: String = "유사도 $threshold% 이상인 버블이 없어요"
) {
    MelodyCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.Radar,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MelodyBubbleColors.TextMuted
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                color = MelodyBubbleColors.Text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "필터를 낮추면 더 많은 음악 취향을 발견할 수 있어요.",
                color = MelodyBubbleColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ListenerIdentity(listener: NearbyListener) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ListenerAvatar(listener, size = 104.dp, showWave = listener.isPlaying)
        Spacer(Modifier.height(14.dp))
        Text(
            text = listener.displayAlias,
            color = MelodyBubbleColors.Text,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "${listener.proximity.label} · 취향 유사도 ${listener.matchScore}%",
            color = MelodyBubbleColors.TextMuted,
            style = MaterialTheme.typography.bodyMedium
        )
        if (listener.isNew) {
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MelodyBubbleColors.Primary.copy(alpha = 0.14f)
            ) {
                Text(
                    text = "새로 발견한 버블",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = MelodyBubbleColors.Primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CurrentTrackCard(
    listener: NearbyListener,
    onOpenTrack: (Track) -> Unit
) {
    val track = listener.currentTrack
    MelodyCard(
        onClick = track?.let { { onOpenTrack(it) } },
        onClickLabel = track?.let { "${it.title} 음악 열기" }
    ) {
        Text(
            text = "현재 듣는 음악",
            color = MelodyBubbleColors.TextMuted,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(12.dp))
        if (track == null) {
            Text(
                text = "공개된 음악이 없어요",
                color = MelodyBubbleColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(listener.colorHex).copy(alpha = 0.25f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (listener.isPlaying) {
                                Icons.Outlined.GraphicEq
                            } else {
                                Icons.Outlined.MusicNote
                            },
                            contentDescription = if (listener.isPlaying) "재생 중" else "음악",
                            tint = Color(listener.colorHex)
                        )
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = MelodyBubbleColors.Text,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        color = MelodyBubbleColors.TextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val tags = (track.genreTags + track.moodTags).take(3)
                    if (tags.isNotEmpty()) {
                        Text(
                            text = tags.joinToString(" · "),
                            color = MelodyBubbleColors.TextFaint,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (track.externalUrl != null) {
                    Icon(
                        imageVector = Icons.Outlined.IosShare,
                        contentDescription = "외부 음악 링크 있음",
                        tint = MelodyBubbleColors.Primary
                    )
                }
            }
        }
    }
}

@Composable
private fun CommonTasteCard(listener: NearbyListener) {
    MelodyCard {
        Text(
            text = "공통 취향",
            color = MelodyBubbleColors.TextMuted,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))
        if (listener.commonGenres.isEmpty()) {
            Text(
                text = "아직 공통 장르 데이터가 없어요",
                color = MelodyBubbleColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listener.commonGenres.forEach { genre ->
                    AssistChip(
                        onClick = {},
                        modifier = Modifier.height(48.dp),
                        label = { Text(genre) },
                        enabled = false,
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = MelodyBubbleColors.SurfaceSelected,
                            disabledLabelColor = MelodyBubbleColors.PrimarySoft
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = false,
                            borderColor = MelodyBubbleColors.Border,
                            disabledBorderColor = MelodyBubbleColors.Border
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReactionSheet(
    listener: NearbyListener,
    onDismiss: () -> Unit,
    onReactionSelected: (String) -> Unit
) {
    val reactions = listOf(
        "이 곡 좋아요",
        "취향이 닮았어요",
        "선곡 멋져요",
        "같이 듣고 싶어요"
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MelodyBubbleColors.Surface,
        contentColor = MelodyBubbleColors.Text,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 44.dp, height = 4.dp),
                shape = CircleShape,
                color = MelodyBubbleColors.BorderStrong
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                )
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
        ) {
            Text(
                text = "${listener.displayAlias}에게 리액션",
                modifier = Modifier.semantics { heading() },
                color = MelodyBubbleColors.Text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "안전한 대화를 위해 정해진 문구만 보낼 수 있어요.",
                color = MelodyBubbleColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(18.dp))
            reactions.forEach { reaction ->
                OutlinedButton(
                    onClick = { onReactionSelected(reaction) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    border = BorderStroke(1.dp, MelodyBubbleColors.Border),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MelodyBubbleColors.Text
                    )
                ) {
                    Text(
                        text = reaction,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ListenerAvatar(
    listener: NearbyListener,
    size: Dp,
    showWave: Boolean
) {
    Surface(
        modifier = Modifier
            .size(size)
            .semantics {
                contentDescription = buildString {
                    append("${listener.displayAlias} 익명 버블")
                    if (showWave) append(", 음악 재생 중")
                }
            },
        shape = CircleShape,
        color = Color(listener.colorHex),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (showWave) Icons.Outlined.GraphicEq else Icons.Outlined.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(size * 0.38f),
                tint = Color(0xFF03170A)
            )
        }
    }
}

private fun com.example.myapplication.core.model.DisplayPosition.safePosition() = copy(
    x = x.coerceIn(0.08f, 0.92f),
    y = y.coerceIn(0.10f, 0.90f)
)

private fun NearbyListener.matchesMusicFilter(
    currentTrack: Track,
    filter: NearbyMusicFilter
): Boolean = when (filter) {
    NearbyMusicFilter.ALL -> true
    NearbyMusicFilter.SAME_MUSIC -> musicMatchLevel(currentTrack) != MusicMatchLevel.NONE
}

private fun NearbyListener.musicMatchLevel(currentTrack: Track): MusicMatchLevel {
    val listenerTrack = this.currentTrack ?: return MusicMatchLevel.NONE
    return when {
        listenerTrack.title.equals(currentTrack.title, ignoreCase = true) &&
            listenerTrack.artist.equals(currentTrack.artist, ignoreCase = true) -> MusicMatchLevel.SONG
        listenerTrack.artist.equals(currentTrack.artist, ignoreCase = true) ||
            sameNonBlank(listenerTrack.album, currentTrack.album) -> MusicMatchLevel.ALBUM_OR_ARTIST
        else -> MusicMatchLevel.NONE
    }
}

private fun sameNonBlank(first: String?, second: String?): Boolean =
    !first.isNullOrBlank() && !second.isNullOrBlank() && first.equals(second, ignoreCase = true)

private fun RelationshipStatus.followLabel(): String = when (this) {
    RelationshipStatus.NONE -> "팔로우"
    RelationshipStatus.FOLLOWING -> "팔로잉"
    RelationshipStatus.FOLLOWS_ME -> "맞팔하기"
    RelationshipStatus.MUTUAL -> "맞팔 중"
    RelationshipStatus.BLOCKED -> "차단됨"
}
