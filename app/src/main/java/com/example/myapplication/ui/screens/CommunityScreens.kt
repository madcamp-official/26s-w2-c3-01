package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.core.model.ChatMessage
import com.example.myapplication.core.model.ChatPreview
import com.example.myapplication.core.model.DeliveryState
import com.example.myapplication.core.model.InboxNotification
import com.example.myapplication.core.model.Lounge
import com.example.myapplication.core.model.LoungeStatus
import com.example.myapplication.core.model.OfflineExchangeRecord
import com.example.myapplication.core.model.ProfileSettings
import com.example.myapplication.core.model.RelationshipStatus
import com.example.myapplication.core.model.SyncState
import com.example.myapplication.core.model.Track
import com.example.myapplication.ui.theme.MossOutline
import com.example.myapplication.ui.theme.MossSurface
import com.example.myapplication.ui.theme.MossSurfaceHigh
import com.example.myapplication.ui.theme.MutedMint
import com.example.myapplication.ui.theme.PaleMint
import com.example.myapplication.ui.theme.SignalGreen
import kotlin.math.roundToInt

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var page by remember { mutableIntStateOf(0) }
    val titles = listOf("음악으로만 보여요", "취향의 신호를 골라요", "공개는 내가 시작해요")
    val descriptions = listOf(
        "얼굴, 계정 닉네임, 정확한 위치 없이 임시 별칭과 음악 취향으로 주변을 만나요.",
        "Indie · R&B · Calm · Night를 데모 취향으로 선택했어요. 마이 화면에서 바꿀 수 있어요.",
        "주변 공유는 자동으로 켜지지 않아요. 홈에서 직접 시작하고 언제든 알림에서 중지할 수 있어요."
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (index <= page) SignalGreen else MossOutline)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Surface(
                modifier = Modifier.size(92.dp),
                shape = RoundedCornerShape(28.dp),
                color = SignalGreen.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, SignalGreen.copy(alpha = 0.45f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (page) {
                            0 -> Icons.Outlined.Radio
                            1 -> Icons.Outlined.MusicNote
                            else -> Icons.Outlined.Shield
                        },
                        contentDescription = null,
                        tint = SignalGreen,
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
            Text(
                text = "MELODY BUBBLE · DEMO",
                style = MaterialTheme.typography.labelLarge,
                color = SignalGreen
            )
            Text(text = titles[page], style = MaterialTheme.typography.displaySmall)
            Text(
                text = descriptions[page],
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (page == 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Indie", "R&B", "Calm", "Night").forEach {
                        MiniTag(it, selected = true)
                    }
                }
            }
            if (page == 2) {
                AppPanel {
                    InfoLine("표시 범위", "캠퍼스 주변")
                    InfoLine("정확한 거리·방향", "공개 안 함")
                    InfoLine("프로필", "세션별 임시 별칭")
                }
            }
        }

        Button(
            onClick = {
                if (page < 2) page += 1 else onComplete()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(if (page < 2) "계속" else "데모 시작")
        }
    }
}

@Composable
fun LoungeListScreen(
    lounges: List<Lounge>,
    onOpen: (String) -> Unit,
    onJoinAndOpen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenTitle(
                eyebrow = "TOGETHER, LIVE",
                title = "라운지",
                subtitle = "텍스트 대신 음악 카드와 투표로 만나요"
            )
        }
        items(lounges, key = { it.id }) { lounge ->
            AppPanel(
                modifier = Modifier.clickable { onOpen(lounge.id) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LiveDot(live = lounge.status == LoungeStatus.LIVE)
                            Spacer(Modifier.width(8.dp))
                            Text(lounge.name, style = MaterialTheme.typography.titleLarge)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            lounge.description,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "${lounge.name} 열기")
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.PeopleOutline,
                        contentDescription = null,
                        tint = MutedMint,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (lounge.status == LoungeStatus.SCHEDULED) "예정" else "${lounge.memberCount}명 참여 중",
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedMint
                    )
                    Spacer(Modifier.weight(1f))
                    lounge.vibeTags.take(2).forEach {
                        MiniTag(it)
                        Spacer(Modifier.width(6.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        if (lounge.isJoined) {
                            onOpen(lounge.id)
                        } else {
                            onJoinAndOpen(lounge.id)
                        }
                    },
                    enabled = lounge.status != LoungeStatus.CLOSED,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(if (lounge.isJoined) "상세 보기" else if (lounge.status == LoungeStatus.SCHEDULED) "알림 받기" else "입장")
                }
            }
        }
    }
}

@Composable
fun LoungeDetailScreen(
    lounge: Lounge,
    onBack: () -> Unit,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onVote: (String) -> Unit,
    onReactToCard: (String) -> Unit,
    onSendCurrentTrack: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            BackHeader(onBack = onBack, title = lounge.name)
        }
        item {
            AppPanel(color = SignalGreen.copy(alpha = 0.10f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot(live = lounge.status == LoungeStatus.LIVE)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (lounge.status == LoungeStatus.LIVE) "LIVE · ${lounge.memberCount}명" else "오픈 예정",
                        color = SignalGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(lounge.vibeTags.joinToString(" · "), style = MaterialTheme.typography.headlineSmall)
                Text(
                    "집계 정보만 공유되며 개인 청취 기록은 표시하지 않아요.",
                    color = MutedMint,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        item { SectionLabel("추천곡 카드") }
        if (lounge.isJoined) {
            items(lounge.cards, key = { it.id }) { card ->
                AppPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TrackGlyph(card.senderAlias, card.track.title.hashCode().toLong())
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(card.track.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${card.track.artist} · ${card.senderAlias}",
                                color = MutedMint,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(onClick = { onReactToCard(card.id) }) {
                            Icon(
                                Icons.Outlined.FavoriteBorder,
                                contentDescription = if (card.hasReacted) "${card.track.title} 공감 취소" else "${card.track.title} 공감",
                                tint = if (card.hasReacted) SignalGreen else MutedMint
                            )
                        }
                        Spacer(Modifier.width(5.dp))
                        Text(card.reactionCount.toString(), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        } else {
            item {
                AppPanel {
                    Text("라운지 입장 후 추천곡을 볼 수 있어요", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("입장하면 추천곡 카드와 실시간 투표에 참여할 수 있어요.", color = MutedMint)
                }
            }
        }
        lounge.poll?.let { poll ->
            item { SectionLabel("진행 중 투표") }
            item {
                AppPanel {
                    Text(poll.question, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(14.dp))
                    poll.options.forEach { option ->
                        val total = poll.totalVotes.coerceAtLeast(1)
                        val progress = option.voteCount.toFloat() / total
                        val selected = poll.myChoice == option.id
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(enabled = poll.isOpen) { onVote(option.id) }
                                .padding(vertical = 8.dp)
                        ) {
                            Row {
                                Text(
                                    option.label,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) SignalGreen else PaleMint
                                )
                                Text("${(progress * 100).roundToInt()}%", color = MutedMint)
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(CircleShape),
                                color = if (selected) SignalGreen else MutedMint,
                                trackColor = MossOutline
                            )
                        }
                    }
                    Text(
                        "${poll.totalVotes}표 · 서버 ACK와 동일한 상태 전이를 데모로 재현",
                        color = MutedMint,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        item {
            Button(
                onClick = onSendCurrentTrack,
                enabled = lounge.isJoined,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Outlined.MusicNote, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (lounge.isJoined) "현재 음악 카드 보내기" else "입장 후 카드 보내기")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (lounge.isJoined) onLeave() else onJoin()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(if (lounge.isJoined) "라운지 나가기" else "라운지 입장")
            }
        }
    }
}

@Composable
fun InboxScreen(
    notifications: List<InboxNotification>,
    chats: List<ChatPreview>,
    onOpenChat: (String) -> Unit,
    onMarkRead: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            ScreenTitle(
                eyebrow = "PRIVATE QUEUES",
                title = "인박스",
                subtitle = "개인 알림과 맞팔 대화만 보여요"
            )
        }
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("알림") },
                icon = { Icon(Icons.Outlined.Notifications, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("채팅") },
                icon = { Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null) }
            )
        }
        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("개인 Queue", color = MutedMint, modifier = Modifier.weight(1f))
                        Text(
                            "모두 읽음",
                            color = SignalGreen,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onMarkRead() }
                                .padding(12.dp)
                        )
                    }
                }
                items(notifications, key = { it.id }) { notification ->
                    AppPanel(
                        color = if (notification.isRead) MossSurface else SignalGreen.copy(alpha = 0.09f)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TrackGlyph(
                                notification.actorAlias ?: "시스템",
                                notification.actorColorHex ?: 0xFF2A4937L
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    notification.actorAlias ?: "Melody Bubble",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(notification.preview, color = MutedMint)
                            }
                            Text(notification.relativeTime, style = MaterialTheme.typography.labelMedium, color = MutedMint)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    AppPanel(color = SignalGreen.copy(alpha = 0.08f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, tint = SignalGreen)
                            Spacer(Modifier.width(10.dp))
                            Text("맞팔이 성립된 사용자만 자유 메시지를 보낼 수 있어요")
                        }
                    }
                }
                items(chats, key = { it.roomId }) { chat ->
                    AppPanel(
                        modifier = Modifier.clickable(
                            enabled = chat.relationship == RelationshipStatus.MUTUAL
                        ) { onOpenChat(chat.roomId) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TrackGlyph(chat.peerAlias, chat.peerColorHex)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(chat.peerAlias, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    chat.lastMessage,
                                    color = MutedMint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (chat.unreadCount > 0) {
                                Surface(shape = CircleShape, color = SignalGreen) {
                                    Text(
                                        chat.unreadCount.toString(),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = Color(0xFF00210B),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(
    chat: ChatPreview,
    messages: List<ChatMessage>,
    currentTrack: Track,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        BackHeader(onBack = onBack, title = chat.peerAlias, subtitle = "${currentTrack.title} 재생 중")
        HorizontalDivider(color = MossOutline)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "맞팔이 확인된 1:1 대화 · 링크와 파일은 MVP에서 지원하지 않아요",
                    color = MutedMint,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            items(messages, key = { it.clientMessageId }) { message ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (message.isMine) 18.dp else 5.dp,
                            bottomEnd = if (message.isMine) 5.dp else 18.dp
                        ),
                        color = if (message.isMine) SignalGreen.copy(alpha = 0.22f) else MossSurfaceHigh,
                        modifier = Modifier.fillMaxWidth(0.76f)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(message.content)
                            Spacer(Modifier.height(5.dp))
                            Text(
                                when (message.deliveryState) {
                                    DeliveryState.PENDING -> "전송 중"
                                    DeliveryState.SENT -> "전송됨"
                                    DeliveryState.READ -> "읽음"
                                    DeliveryState.FAILED -> "실패 · 다시 시도"
                                },
                                color = MutedMint,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { if (it.length <= 1_000) input = it },
                placeholder = { Text("메시지 입력") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(22.dp),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (input.isNotBlank()) {
                        onSend(input)
                        input = ""
                    }
                })
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        onSend(input)
                        input = ""
                    }
                },
                modifier = Modifier
                    .size(52.dp)
                    .background(SignalGreen, CircleShape)
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Send,
                    contentDescription = "메시지 보내기",
                    tint = Color(0xFF00210B)
                )
            }
        }
    }
}

@Composable
fun MyScreen(
    profile: ProfileSettings,
    offlineExchangeCount: Int,
    onDiscoverableChange: (Boolean) -> Unit,
    onAllowReactionsChange: (Boolean) -> Unit,
    onOfflineExchangeChange: (Boolean) -> Unit,
    onOpenMusicSelect: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenOfflineExchange: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenTitle(eyebrow = "MY SIGNAL", title = "마이", subtitle = "공개 범위와 로컬 기록을 관리해요")
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    color = Color(profile.colorHex)
                ) {}
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(profile.accountAlias, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "주변에서는 ${profile.nearbyDisplayAlias} · 세션 종료 시 변경",
                        color = MutedMint
                    )
                    Text(
                        (profile.genres + profile.moods).joinToString(" · "),
                        color = SignalGreen,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
        item { SectionLabel("음악과 프로필") }
        item {
            SettingsLink(
                icon = Icons.Outlined.MusicNote,
                title = "현재 음악 선택",
                subtitle = "자동 감지가 안 되면 직접 선택",
                onClick = onOpenMusicSelect
            )
        }
        item {
            SettingsLink(
                icon = Icons.Outlined.Notifications,
                title = "자동 음악 감지",
                subtitle = "알림 접근을 허용한 음악 앱만 보조 감지",
                onClick = onOpenNotificationAccess
            )
        }
        item {
            SettingsLink(
                icon = Icons.Outlined.Tune,
                title = "멜로디 별칭",
                subtitle = profile.melodyNotes.joinToString(" · "),
                onClick = {}
            )
        }
        item {
            SettingsLink(
                icon = Icons.Outlined.Shield,
                title = "음악 공개 범위",
                subtitle = profile.musicVisibilityLabel,
                onClick = {}
            )
        }
        item { SectionLabel("공개와 안전") }
        item {
            SettingsToggle(
                icon = Icons.Outlined.Radio,
                title = "주변에서 발견 가능",
                subtitle = "정확한 위치·방향은 항상 숨김",
                checked = profile.discoverable,
                onCheckedChange = onDiscoverableChange
            )
        }
        item {
            SettingsToggle(
                icon = Icons.Outlined.FavoriteBorder,
                title = "정해진 리액션 받기",
                subtitle = "맞팔 전 자유 메시지는 허용 안 함",
                checked = profile.allowReactions,
                onCheckedChange = onAllowReactionsChange
            )
        }
        item {
            SettingsToggle(
                icon = Icons.AutoMirrored.Outlined.BluetoothSearching,
                title = "오프라인 음악 카드 교환",
                subtitle = "승인한 상대와 카드만 로컬 저장",
                checked = profile.offlineExchangeEnabled,
                onCheckedChange = onOfflineExchangeChange
            )
        }
        item {
            SettingsLink(
                icon = Icons.Outlined.History,
                title = "오프라인 교환 기록",
                subtitle = "Room 로컬 DB · ${offlineExchangeCount}건",
                onClick = onOpenOfflineExchange
            )
        }
        item {
            AppPanel(color = SignalGreen.copy(alpha = 0.07f)) {
                Text("개인정보 기본값", color = SignalGreen, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "좌표, JWT, 주변 사용자 목록은 로컬 DB에 저장하지 않아요. 교환 기록과 미전송 outbox만 저장합니다.",
                    color = MutedMint,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun OfflineExchangeScreen(
    records: List<OfflineExchangeRecord>,
    onBack: () -> Unit,
    onCreate: (String) -> Unit,
    onSync: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPeer by remember { mutableStateOf<String?>(null) }
    Column(modifier = modifier.fillMaxSize()) {
        BackHeader(onBack = onBack, title = "오프라인 교환", subtitle = "Nearby 흐름을 안전한 데모로 재현")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AppPanel(color = SignalGreen.copy(alpha = 0.08f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Outlined.BluetoothSearching, contentDescription = null, tint = SignalGreen)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("근처 기기 찾기", style = MaterialTheme.typography.titleMedium)
                            Text("실제 API 대신 승인·인증·저장 흐름을 검증해요", color = MutedMint)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    listOf("Clover", "Moon").forEach { peer ->
                        OutlinedButton(
                            onClick = { selectedPeer = peer },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selectedPeer == peer) SignalGreen.copy(alpha = 0.12f) else Color.Transparent
                            )
                        ) {
                            Text(peer, modifier = Modifier.weight(1f))
                            if (selectedPeer == peer) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = "선택됨")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (selectedPeer != null) {
                        Text("양쪽 화면의 인증 코드 · 4812", color = SignalGreen, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                selectedPeer?.let(onCreate)
                                selectedPeer = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("코드 확인 및 카드 교환")
                        }
                    }
                }
            }
            item { SectionLabel("이 기기에 저장된 기록") }
            if (records.isEmpty()) {
                item {
                    AppPanel {
                        Text("아직 교환 기록이 없어요", style = MaterialTheme.typography.titleMedium)
                        Text("위 데모 기기를 선택해 Room 저장 흐름을 확인해 보세요.", color = MutedMint)
                    }
                }
            }
            items(records, key = { it.id }) { record ->
                AppPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TrackGlyph(record.peerDisplayAlias, record.peerDisplayAlias.hashCode().toLong())
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(record.peerDisplayAlias, fontWeight = FontWeight.Bold)
                            Text("${record.trackTitle} · ${record.trackArtist}", color = MutedMint)
                            Text(record.melodyAlias, color = MutedMint, style = MaterialTheme.typography.labelMedium)
                        }
                        if (record.syncState == SyncState.SYNCED) {
                            Icon(Icons.Outlined.CloudDone, contentDescription = "동기화됨", tint = SignalGreen)
                        } else {
                            IconButton(onClick = { onSync(record.id) }) {
                                Icon(Icons.Outlined.CloudUpload, contentDescription = "서버 동기화 확인")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MusicSelectScreen(
    currentTrack: Track,
    options: List<Track>,
    onBack: () -> Unit,
    onSelect: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        BackHeader(onBack = onBack, title = "현재 음악 선택", subtitle = "자동 감지 실패 시 수동 대체")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                AppPanel(color = SignalGreen.copy(alpha = 0.08f)) {
                    Text("현재 공유 중", color = SignalGreen, style = MaterialTheme.typography.labelLarge)
                    Text(currentTrack.title, style = MaterialTheme.typography.headlineSmall)
                    Text(currentTrack.artist, color = MutedMint)
                }
            }
            item { SectionLabel("최근 선택") }
            items(options, key = { it.id }) { track ->
                AppPanel(
                    modifier = Modifier.clickable {
                        onSelect(track)
                        onBack()
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TrackGlyph(track.title, track.id.hashCode().toLong())
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.title, fontWeight = FontWeight.Bold)
                            Text(track.artist, color = MutedMint)
                        }
                        if (track.id == currentTrack.id) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = "현재 선택", tint = SignalGreen)
                        } else {
                            Icon(Icons.Outlined.ChevronRight, contentDescription = "${track.title} 선택")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenTitle(eyebrow: String, title: String, subtitle: String) {
    Column {
        Text(eyebrow, color = SignalGreen, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, color = MutedMint, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BackHeader(
    onBack: () -> Unit,
    title: String,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
        }
        Spacer(Modifier.width(4.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge)
            subtitle?.let { Text(it, color = MutedMint, style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun AppPanel(
    modifier: Modifier = Modifier,
    color: Color = MossSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = color,
        border = androidx.compose.foundation.BorderStroke(1.dp, MossOutline)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun MiniTag(label: String, selected: Boolean = false) {
    Surface(
        shape = CircleShape,
        color = if (selected) SignalGreen.copy(alpha = 0.17f) else MossSurfaceHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) SignalGreen.copy(alpha = 0.5f) else MossOutline
        )
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) SignalGreen else PaleMint
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(label, color = MutedMint, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LiveDot(live: Boolean) {
    Box(
        modifier = Modifier
            .size(9.dp)
            .background(if (live) SignalGreen else MutedMint, CircleShape)
    )
}

@Composable
private fun TrackGlyph(label: String, colorSeed: Long) {
    val colors = listOf(0xFF25C76FL, 0xFF53D889L, 0xFF79A85BL, 0xFF2C9F65L)
    val color = Color(colors[(kotlin.math.abs(colorSeed) % colors.size).toInt()])
    Surface(modifier = Modifier.size(46.dp), shape = CircleShape, color = color) {
        Box(contentAlignment = Alignment.Center) {
            Text(label.take(1).uppercase(), color = Color(0xFF00210B), fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun SettingsLink(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    AppPanel(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = SignalGreen)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MutedMint, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = "$title 열기")
        }
    }
}

@Composable
private fun SettingsToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    AppPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = SignalGreen)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MutedMint, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
