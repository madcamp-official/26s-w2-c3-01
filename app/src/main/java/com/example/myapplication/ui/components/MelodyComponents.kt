package com.example.myapplication.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.core.model.ConnectionState
import com.example.myapplication.core.model.MainTab
import com.example.myapplication.core.model.SharingState
import com.example.myapplication.core.model.PreviewPlaybackState
import com.example.myapplication.ui.theme.CurrentSyncPalette

@Composable
fun PreviewNowPlayingBar(
    state: PreviewPlaybackState,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
    onOpenInMusicApp: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(state.title, state.artist, onOpenInMusicApp) {
                detectTapGestures(onDoubleTap = { onOpenInMusicApp() })
            },
        color = MelodyBubbleColors.SurfaceRaised,
        contentColor = MelodyBubbleColors.Text,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, MelodyBubbleColors.BorderStrong),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            PreviewEqualizerBars(active = state.isPlaying)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(state.title.ifBlank { "미리듣기 준비 중" }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state.artist, color = MelodyBubbleColors.TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            androidx.compose.material3.IconButton(
                onClick = onTogglePause,
                enabled = !state.isLoading,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    if (state.isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                    contentDescription = if (state.isPaused) "미리듣기 계속 재생" else "미리듣기 일시정지",
                )
            }
            androidx.compose.material3.IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Stop, contentDescription = "미리듣기 정지")
            }
        }
    }
}

@Composable
fun PreviewEqualizerBars(active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "preview equalizer")
    Row(
        modifier = modifier.size(width = 28.dp, height = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(15, 27, 20, 30, 18).forEachIndexed { index, maximum ->
            val fraction by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(260 + index * 90),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar $index",
            )
            Box(
                Modifier
                    .width(3.dp)
                    .height((if (active) maximum * fraction * 0.7f else 4f).dp)
                    .background(MelodyBubbleColors.Primary, RoundedCornerShape(2.dp))
            )
        }
    }
}

/** Palette shared by the handoff screens so they stay consistent even before app theming is wired. */
object MelodyBubbleColors {
    val Background: Color get() = CurrentSyncPalette.background
    val Surface: Color get() = CurrentSyncPalette.surface
    val SurfaceRaised: Color get() = CurrentSyncPalette.surfaceRaised
    val SurfaceSelected: Color get() = CurrentSyncPalette.surfaceRaised
    val Border: Color get() = CurrentSyncPalette.border
    val BorderStrong: Color get() = CurrentSyncPalette.border
    val Primary: Color get() = CurrentSyncPalette.primary
    val PrimarySoft: Color get() = CurrentSyncPalette.primarySoft
    val OnPrimary: Color get() = CurrentSyncPalette.background
    val Text: Color get() = CurrentSyncPalette.text
    val TextMuted: Color get() = CurrentSyncPalette.textMuted
    val TextFaint: Color get() = CurrentSyncPalette.textMuted.copy(alpha = 0.6f)
    val Warning: Color get() = CurrentSyncPalette.primarySoft
    val Danger: Color get() = CurrentSyncPalette.error
}

private val CardShape = RoundedCornerShape(22.dp)

@Composable
fun MelodySurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MelodyBubbleColors.Background),
        content = content
    )
}

@Composable
fun MelodyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(
            role = Role.Button,
            onClickLabel = onClickLabel,
            onClick = onClick
        )
    }

    Surface(
        modifier = modifier.then(interactionModifier),
        shape = CardShape,
        color = MelodyBubbleColors.Surface,
        contentColor = MelodyBubbleColors.Text,
        border = BorderStroke(1.dp, MelodyBubbleColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MelodyBubbleColors.Text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = MelodyBubbleColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                modifier = Modifier
                    .height(48.dp)
                    .widthIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MelodyBubbleColors.SurfaceRaised,
                    contentColor = MelodyBubbleColors.Primary
                ),
                border = BorderStroke(1.dp, MelodyBubbleColors.Border)
            ) {
                Text(actionLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun SharingStatusCard(
    sharingState: SharingState,
    connectionState: ConnectionState,
    scopeLabel: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sharing = sharingState == SharingState.ACTIVE ||
        sharingState == SharingState.STARTING
    val stateLabel = when (sharingState) {
        SharingState.STOPPED -> "주변 공유 꺼짐"
        SharingState.STARTING -> "주변 공유를 준비하는 중"
        SharingState.ACTIVE -> "주변 공유 중"
        SharingState.PERMISSION_REQUIRED -> "권한 확인이 필요해요"
        SharingState.FAILED -> "공유 시작에 실패했어요"
    }
    val connectionLabel = when (connectionState) {
        ConnectionState.DISCONNECTED -> "연결 안 됨"
        ConnectionState.CONNECTING -> "연결 중"
        ConnectionState.LIVE -> "실시간 연결"
        ConnectionState.RECONNECTING -> "다시 연결 중"
    }

    MelodyCard(
        modifier = modifier.semantics {
            contentDescription = "$stateLabel, $connectionLabel, 공유 범위 $scopeLabel"
        },
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = if (sharing) {
                    MelodyBubbleColors.Primary.copy(alpha = 0.16f)
                } else {
                    MelodyBubbleColors.SurfaceRaised
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.WifiTethering,
                        contentDescription = null,
                        tint = if (sharing) {
                            MelodyBubbleColors.Primary
                        } else {
                            MelodyBubbleColors.TextMuted
                        }
                    )
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(
                        color = if (connectionState == ConnectionState.LIVE) {
                                    MelodyBubbleColors.Primary
                                } else {
                                    MelodyBubbleColors.Warning
                                },
                                shape = CircleShape
                            )
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = stateLabel,
                        color = MelodyBubbleColors.Text,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "$scopeLabel · $connectionLabel",
                    color = MelodyBubbleColors.TextMuted,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = if (sharing) onStop else onStart,
                modifier = Modifier
                    .height(42.dp)
                    .widthIn(min = 64.dp),
                contentPadding = PaddingValues(horizontal = 13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (sharing) {
                        MelodyBubbleColors.SurfaceRaised
                    } else {
                        MelodyBubbleColors.Primary
                    },
                    contentColor = if (sharing) {
                        MelodyBubbleColors.Text
                    } else {
                        MelodyBubbleColors.OnPrimary
                    }
                ),
                border = if (sharing) {
                    BorderStroke(1.dp, MelodyBubbleColors.BorderStrong)
                } else {
                    null
                }
            ) {
                Text(
                    text = if (sharing) "중지" else "시작",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun MelodyBottomNavigationBar(
    selectedTab: MainTab,
    unreadCount: Int,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MelodyBubbleColors.Background,
        contentColor = MelodyBubbleColors.Text,
        tonalElevation = 0.dp
    ) {
        MainTab.entries.forEach { tab ->
            val icon = tab.icon()
            val badgeLabel = if (tab == MainTab.INBOX && unreadCount > 0) {
                if (unreadCount > 99) "99개 이상" else "${unreadCount}개"
            } else {
                null
            }
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Box {
                        Icon(
                            imageVector = icon,
                            contentDescription = "${tab.label} 탭"
                        )
                        if (badgeLabel != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(8.dp)
                                    .background(MelodyBubbleColors.Primary, CircleShape)
                                    .semantics {
                                        contentDescription = "읽지 않은 항목 $badgeLabel"
                                    }
                            )
                        }
                    }
                },
                label = { Text(tab.label) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MelodyBubbleColors.OnPrimary,
                    selectedTextColor = MelodyBubbleColors.Primary,
                    indicatorColor = MelodyBubbleColors.Primary,
                    unselectedIconColor = MelodyBubbleColors.TextMuted,
                    unselectedTextColor = MelodyBubbleColors.TextMuted
                )
            )
        }
    }
}

private fun MainTab.icon(): ImageVector = when (this) {
    MainTab.HOME -> Icons.Outlined.Home
    MainTab.NEARBY -> Icons.Outlined.Radar
    MainTab.LOUNGE -> Icons.Outlined.Groups
    MainTab.INBOX -> Icons.Outlined.ChatBubbleOutline
    MainTab.MY -> Icons.Outlined.PersonOutline
}
