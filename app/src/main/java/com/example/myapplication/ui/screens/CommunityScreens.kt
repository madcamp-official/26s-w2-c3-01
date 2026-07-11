package com.example.myapplication.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Block
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.example.myapplication.core.model.MelodyAliasCandidate
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
import com.example.myapplication.ui.components.MelodyCard
import com.example.myapplication.ui.MelodyAliasGenerationState
import com.example.myapplication.ui.LyriaGenerationState
import kotlin.math.roundToInt
import java.io.ByteArrayOutputStream

@Composable
fun OnboardingScreen(
    onComplete: (List<String>, List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    var page by remember { mutableIntStateOf(0) }
    var acceptedTerms by rememberSaveable { mutableStateOf(false) }
    var genres by rememberSaveable { mutableStateOf(setOf("Indie", "R&B")) }
    var moods by rememberSaveable { mutableStateOf(setOf("Calm", "Night")) }
    val titles = listOf("약관을 확인해 주세요", "취향의 신호를 골라요", "공개는 내가 시작해요")
    val descriptions = listOf(
        "서비스 이용약관과 개인정보 처리방침에 동의해야 Melody Bubble을 시작할 수 있어요.",
        "좋아하는 장르와 분위기를 선택해 주세요. 추천과 주변 취향 유사도에 사용됩니다.",
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
                text = "MELODY BUBBLE · ONBOARDING",
                style = MaterialTheme.typography.labelLarge,
                color = SignalGreen
            )
            Text(text = titles[page], style = MaterialTheme.typography.displaySmall)
            Text(
                text = descriptions[page],
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (page == 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = acceptedTerms, onCheckedChange = { acceptedTerms = it })
                    Text("이용약관 및 개인정보 처리방침에 동의합니다")
                }
            }
            if (page == 1) {
                Text("선호 장르", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Indie", "R&B", "Pop", "Rock").forEach { label ->
                        FilterChip(selected = label in genres, onClick = {
                            genres = if (label in genres) genres - label else genres + label
                        }, label = { Text(label) })
                    }
                }
                Text("선호 분위기", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Calm", "Night", "Bright", "Energetic").forEach { label ->
                        FilterChip(selected = label in moods, onClick = {
                            moods = if (label in moods) moods - label else moods + label
                        }, label = { Text(label) })
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
                if (page < 2) page += 1 else onComplete(genres.toList(), moods.toList())
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp),
            enabled = when (page) {
                0 -> acceptedTerms
                1 -> genres.isNotEmpty() && moods.isNotEmpty()
                else -> true
            }
        ) {
            Text(if (page < 2) "계속" else "시작하기")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyScreen(
    profile: ProfileSettings,
    offlineExchangeCount: Int,
    onDiscoverableChange: (Boolean) -> Unit,
    onAllowReactionsChange: (Boolean) -> Unit,
    onOfflineExchangeChange: (Boolean) -> Unit,
    onMusicVisibilityChange: (String) -> Unit,
    onProfileUpdate: (String, Long, String, String?, List<String>, List<String>) -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    onOpenMelodyAlias: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenOfflineExchange: () -> Unit,
    onOpenBlockedUsers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var visibilityEditing by rememberSaveable { mutableStateOf(false) }
    var deleteConfirm by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable(profile.accountAlias) { mutableStateOf(profile.accountAlias) }
    var bio by rememberSaveable(profile.bio) { mutableStateOf(profile.bio) }
    var avatar by rememberSaveable(profile.avatarDataUrl) { mutableStateOf(profile.avatarDataUrl) }
    if (deleteConfirm) AlertDialog(
        onDismissRequest = { deleteConfirm = false },
        title = { Text("계정을 탈퇴할까요?") },
        text = { Text("프로필, 인증 정보와 서버에 저장된 계정 데이터가 삭제됩니다. 이 작업은 되돌릴 수 없습니다.") },
        confirmButton = { TextButton(onClick = { deleteConfirm = false; onDeleteAccount() }) { Text("탈퇴", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("취소") } },
    )
    var colorHex by rememberSaveable(profile.colorHex) { mutableStateOf(profile.colorHex) }
    var genres by rememberSaveable(profile.genres) { mutableStateOf(profile.genres) }
    var moods by rememberSaveable(profile.moods) { mutableStateOf(profile.moods) }
    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) avatar = runCatching {
            val bitmap = context.contentResolver.openInputStream(uri).use(BitmapFactory::decodeStream)
            val ratio = minOf(1f, 512f / maxOf(bitmap.width, bitmap.height))
            val resized = if (ratio < 1f) android.graphics.Bitmap.createScaledBitmap(
                bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true,
            ) else bitmap
            val bytes = ByteArrayOutputStream().also { resized.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
            "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        }.getOrNull()
    }

    if (editing) ModalBottomSheet(
        onDismissRequest = { editing = false },
        containerColor = MossSurface,
        contentColor = PaleMint,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("프로필 편집", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("주변에 보여줄 나의 음악 프로필", color = MutedMint)
                }
                TextButton(onClick = { editing = false }) { Text("닫기") }
            }
            Spacer(Modifier.height(22.dp))
            Box(contentAlignment = Alignment.BottomEnd) {
                ProfileAvatar(avatar, name, colorHex, 112.dp)
                Surface(
                    modifier = Modifier.size(38.dp).clickable {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    shape = CircleShape,
                    color = SignalGreen,
                ) { Box(contentAlignment = Alignment.Center) { Text("＋", color = Color(0xFF00210B), fontWeight = FontWeight.Bold) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("사진 선택") }
                if (avatar != null) TextButton(onClick = { avatar = null }) { Text("사진 삭제", color = MaterialTheme.colorScheme.error) }
            }
            OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("프로필 이름") }, singleLine = true)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                bio, { bio = it.take(160) }, Modifier.fillMaxWidth(), label = { Text("소개") },
                supportingText = { Text("${bio.length}/160") }, minLines = 3,
            )
            Spacer(Modifier.height(18.dp))
            Text("프로필 테마", Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(0xFF25C76FL, 0xFF6C63FFL, 0xFFFF6B8AL, 0xFF3AA8FFL, 0xFFFFB84DL).forEach { option ->
                    Surface(
                        modifier = Modifier.size(48.dp).then(if (colorHex == option) Modifier.border(3.dp, PaleMint, CircleShape) else Modifier).clickable { colorHex = option },
                        shape = CircleShape,
                        color = Color(option),
                    ) { Box(contentAlignment = Alignment.Center) { if (colorHex == option) Text("✓", color = Color.White, fontWeight = FontWeight.Bold) } }
                }
            }
            Spacer(Modifier.height(20.dp))
            ProfileTagEditor("좋아하는 장르", listOf("Indie", "R&B", "Pop", "Rock", "Jazz", "Hip-hop"), genres) { genres = it }
            Spacer(Modifier.height(16.dp))
            ProfileTagEditor("내 음악 무드", listOf("Calm", "Night", "Dreamy", "Bright", "Energetic", "Warm"), moods) { moods = it }
            Spacer(Modifier.height(26.dp))
            Button(
                onClick = { onProfileUpdate(name, colorHex, bio, avatar, genres, moods); editing = false },
                enabled = name.trim().length >= 2,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("프로필 저장", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (visibilityEditing) ModalBottomSheet(
        onDismissRequest = { visibilityEditing = false },
        containerColor = MossSurface,
        contentColor = PaleMint,
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text("음악 공개 범위", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("주변 리스너에게 현재 음악을 어떻게 보여줄지 선택하세요.", color = MutedMint)
            Spacer(Modifier.height(18.dp))
            listOf(
                "제목과 아티스트 공개" to "곡 제목과 아티스트만 보여줘요.",
                "맞팔에게만 공개" to "서로 팔로우한 사용자에게만 현재 음악을 보여줘요.",
                "비공개" to "취향 유사도만 보여주고 현재 음악은 숨겨요.",
            ).forEach { (option, description) ->
                MelodyCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    onClick = { onMusicVisibilityChange(option); visibilityEditing = false },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(option, fontWeight = FontWeight.Bold); Text(description, color = MutedMint) }
                        if (profile.musicVisibilityLabel == option || profile.musicVisibilityLabel.replace("·", "과 ") == option) Text("✓", color = SignalGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("PROFILE", color = SignalGreen, style = MaterialTheme.typography.labelLarge); Text("내 프로필", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = { editing = true }) { Text("편집") }
            }
        }
        item {
            MelodyCard(modifier = Modifier.fillMaxWidth(), onClick = { editing = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(profile.avatarDataUrl, profile.accountAlias, profile.colorHex, 88.dp)
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.accountAlias, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(profile.bio.ifBlank { "소개를 추가해 나를 표현해보세요." }, color = MutedMint, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(9.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (profile.genres + profile.moods).take(3).forEach { MiniTag(it, true) } }
                    }
                }
            }
        }
        item { SectionLabel("음악 정체성") }
        item { SettingsLink(Icons.Outlined.Tune, "멜로디 별칭", "${profile.melodyNotes.joinToString(" · ")} · ${profile.melodyAliasTone}", onOpenMelodyAlias) }
        item { SettingsLink(Icons.Outlined.Shield, "음악 공개 범위", profile.musicVisibilityLabel, { visibilityEditing = true }) }
        item { SectionLabel("공개 및 연결") }
        item { SettingsToggle(Icons.Outlined.Radio, "주변에서 발견 가능", "정확한 위치와 방향은 항상 숨겨요", profile.discoverable, onDiscoverableChange) }
        item { SettingsToggle(Icons.Outlined.FavoriteBorder, "음악 리액션 받기", "정해진 안전한 리액션만 받아요", profile.allowReactions, onAllowReactionsChange) }
        item { SettingsLink(Icons.Outlined.Block, "차단 사용자 관리", "차단 해제와 목록 확인", onOpenBlockedUsers) }
        item { SectionLabel("기기 기능") }
        item { SettingsLink(Icons.Outlined.Notifications, "자동 음악 감지", "음악 앱의 재생 알림으로 현재 곡을 감지해요", onOpenNotificationAccess) }
        item { SettingsToggle(Icons.AutoMirrored.Outlined.BluetoothSearching, "오프라인 음악 카드 교환", "가까운 상대와 승인한 카드만 교환해요", profile.offlineExchangeEnabled, onOfflineExchangeChange) }
        item { SettingsLink(Icons.Outlined.History, "교환 기록", "이 기기에 저장된 기록 ${offlineExchangeCount}건", onOpenOfflineExchange) }
        item { Spacer(Modifier.height(4.dp)); OutlinedButton(onClick = onLogout, Modifier.fillMaxWidth().height(50.dp)) { Text("로그아웃") } }
        item { TextButton(onClick = { deleteConfirm = true }, modifier = Modifier.fillMaxWidth()) { Text("회원 탈퇴", color = MaterialTheme.colorScheme.error) } }
    }
}

@Composable
private fun ProfileAvatar(dataUrl: String?, name: String, colorHex: Long, size: androidx.compose.ui.unit.Dp) {
    val bitmap = remember(dataUrl) { dataUrl?.substringAfter("base64,", "")?.takeIf(String::isNotBlank)?.let { encoded -> runCatching { BitmapFactory.decodeByteArray(Base64.decode(encoded, Base64.DEFAULT), 0, Base64.decode(encoded, Base64.DEFAULT).size) }.getOrNull() } }
    Surface(modifier = Modifier.size(size), shape = CircleShape, color = Color(colorHex)) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(contentAlignment = Alignment.Center) { Text(name.trim().take(1).uppercase().ifBlank { "♪" }, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ProfileTagEditor(title: String, options: List<String>, selected: List<String>, onChange: (List<String>) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        options.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { tag -> FilterChip(selected = tag in selected, onClick = { onChange(if (tag in selected) selected - tag else (selected + tag).take(6)) }, label = { Text(tag) }) } } }
    }
}

@Composable
private fun LegacyMyScreen(
    profile: ProfileSettings,
    offlineExchangeCount: Int,
    onDiscoverableChange: (Boolean) -> Unit,
    onAllowReactionsChange: (Boolean) -> Unit,
    onOfflineExchangeChange: (Boolean) -> Unit,
    onMusicVisibilityChange: (String) -> Unit,
    onProfileUpdate: (String, Long) -> Unit,
    onLogout: () -> Unit,
    onOpenMelodyAlias: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenOfflineExchange: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editProfile by rememberSaveable { mutableStateOf(false) }
    var editVisibility by rememberSaveable { mutableStateOf(false) }
    var profileName by rememberSaveable(profile.accountAlias) { mutableStateOf(profile.accountAlias) }
    var profileColor by rememberSaveable(profile.colorHex) { mutableStateOf(profile.colorHex) }
    if (editProfile) AlertDialog(
        onDismissRequest = { editProfile = false },
        title = { Text("프로필 변경") },
        text = { Column { OutlinedTextField(value = profileName, onValueChange = { profileName = it }, label = { Text("프로필 이름") }, singleLine = true); Spacer(Modifier.height(16.dp)); Text("프로필 색상"); Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { listOf(0x6750A4L, 0x248A55L, 0xD05A73L, 0x3478C7L).forEach { color -> Surface(modifier = Modifier.size(42.dp).clickable { profileColor = color }.then(if (profileColor == color) Modifier.border(3.dp, PaleMint, CircleShape) else Modifier), shape = CircleShape, color = Color(color)) {} } } } },
        confirmButton = { TextButton(onClick = { onProfileUpdate(profileName, profileColor); editProfile = false }, enabled = profileName.length >= 2) { Text("저장") } },
        dismissButton = { TextButton(onClick = { editProfile = false }) { Text("취소") } },
    )
    if (editVisibility) AlertDialog(
        onDismissRequest = { editVisibility = false },
        title = { Text("음악 공개 범위") },
        text = { Column { listOf("제목과 아티스트 공개", "비공개").forEach { option ->
            FilterChip(selected = profile.musicVisibilityLabel == option, onClick = { onMusicVisibilityChange(option); editVisibility = false }, label = { Text(option) }, modifier = Modifier.fillMaxWidth())
        } } },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { editVisibility = false }) { Text("닫기") } },
    )
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenTitle(eyebrow = "MY SIGNAL", title = "마이", subtitle = "공개 범위와 로컬 기록을 관리해요")
        }
        item {
            Row(modifier = Modifier.fillMaxWidth().clickable { editProfile = true }, verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(Modifier.weight(1f))
                Icon(Icons.Outlined.ChevronRight, contentDescription = "프로필 변경", tint = MutedMint)
            }
        }
        item { SectionLabel("음악과 프로필") }
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
                subtitle = "${profile.melodyNotes.joinToString(" · ")} · ${profile.melodyAliasTone}",
                onClick = onOpenMelodyAlias
            )
        }
        item {
            SettingsLink(
                icon = Icons.Outlined.Shield,
                title = "음악 공개 범위",
                subtitle = profile.musicVisibilityLabel,
                onClick = { editVisibility = true }
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
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("로그아웃") }
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
fun MelodyAliasScreen(
    onBack: () -> Unit,
    generationState: LyriaGenerationState,
    onGenerate: (Map<String, Int>, String, List<String>, Int, Int) -> Unit,
    onPlayFull: () -> Unit,
    onPlaySelection: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf<String?>("mood") }
    var calm by rememberSaveable { mutableFloatStateOf(60f) }
    var bright by rememberSaveable { mutableFloatStateOf(45f) }
    var dreamy by rememberSaveable { mutableFloatStateOf(70f) }
    var dark by rememberSaveable { mutableFloatStateOf(20f) }
    var genre by rememberSaveable { mutableStateOf("R&B") }
    var instruments by rememberSaveable { mutableStateOf(setOf("Piano", "Synth")) }
    var pitch by rememberSaveable { mutableFloatStateOf(50f) }
    var speed by rememberSaveable { mutableFloatStateOf(50f) }
    var clipStart by rememberSaveable { mutableFloatStateOf(10f) }
    val isLoading = generationState is LyriaGenerationState.Loading

    Column(modifier = modifier.fillMaxSize()) {
        BackHeader(onBack = onBack, title = "멜로디 별칭", subtitle = "나의 바이브를 담은 30초 음악")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                CollapsibleOption("mood", "무드", "감정의 비율을 조절해 주세요", expanded, { expanded = it }) {
                    MoodAmount("Calm", calm) { calm = it }
                    MoodAmount("Bright", bright) { bright = it }
                    MoodAmount("Dreamy", dreamy) { dreamy = it }
                    MoodAmount("Dark", dark) { dark = it }
                }
            }
            item {
                CollapsibleOption("genre", "장르", genre, expanded, { expanded = it }) {
                    val genres = listOf("팝", "힙합", "R&B", "밴드사운드", "전자음악", "어쿠스틱", "재즈", "락", "클래식")
                    genres.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { item ->
                                FilterChip(selected = genre == item, onClick = { genre = item }, label = { Text(item) }, modifier = Modifier.weight(1f))
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            item {
                CollapsibleOption("tone", "톤", "중심이 될 악기를 여러 개 고를 수 있어요", expanded, { expanded = it }) {
                    Text("선택한 악기가 조금 더 부각되며, 곡을 풍성하게 만드는 다른 악기도 함께 들어갈 수 있어요.", color = MutedMint, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    listOf("Piano", "Guitar", "Synth", "Bass", "Drums", "Bell", "Strings").chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { tone ->
                                FilterChip(
                                    selected = tone in instruments,
                                    onClick = { instruments = if (tone in instruments) instruments - tone else instruments + tone },
                                    label = { Text(tone) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                CollapsibleOption("pitch", "피치", "${pitch.roundToInt()} · 낮고 묵직함 ↔ 높고 공기감", expanded, { expanded = it }) {
                    Slider(value = pitch, onValueChange = { pitch = it }, valueRange = 0f..100f)
                }
            }
            item {
                CollapsibleOption("speed", "스피드", "${speed.roundToInt()} · 느리고 여유로움 ↔ 빠르고 에너지", expanded, { expanded = it }) {
                    Slider(value = speed, onValueChange = { speed = it }, valueRange = 0f..100f)
                }
            }
            item {
                Button(
                    onClick = {
                        onGenerate(
                            mapOf("Calm" to calm.roundToInt(), "Bright" to bright.roundToInt(), "Dreamy" to dreamy.roundToInt(), "Dark" to dark.roundToInt()),
                            genre,
                            instruments.toList(),
                            pitch.roundToInt(),
                            speed.roundToInt()
                        )
                    },
                    enabled = !isLoading && instruments.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) { Text(if (isLoading) "음악 생성 중" else "30초 음악 만들기") }
            }
            if (isLoading) item { LyriaLoadingPanel() }
            if (generationState is LyriaGenerationState.Error) item {
                AppPanel(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .35f)) {
                    Text("생성에 실패했어요", style = MaterialTheme.typography.titleMedium)
                    Text(generationState.message, color = MutedMint)
                }
            }
            if (generationState is LyriaGenerationState.Success) {
                item {
                    AppPanel(color = SignalGreen.copy(alpha = .09f)) {
                        Text("30초 음악이 완성됐어요", color = SignalGreen, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = onPlayFull, modifier = Modifier.fillMaxWidth()) { Text("전체 30초 듣기") }
                    }
                }
                item {
                    AppPanel {
                        Text("사용할 5초 선택", style = MaterialTheme.typography.titleMedium)
                        Text("막대를 드래그해 시작 지점을 정하세요.", color = MutedMint)
                        Spacer(Modifier.height(14.dp))
                        Text("${"%.1f".format(clipStart)}초 - ${"%.1f".format(clipStart + 5f)}초", color = SignalGreen, fontWeight = FontWeight.Bold)
                        Slider(value = clipStart, onValueChange = { clipStart = it }, valueRange = 0f..25f, steps = 49)
                        Row(modifier = Modifier.fillMaxWidth()) { Text("0초", color = MutedMint); Spacer(Modifier.weight(1f)); Text("30초", color = MutedMint) }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { onPlaySelection(clipStart) }, modifier = Modifier.fillMaxWidth()) { Text("선택한 5초 듣기") }
                    }
                }
                item { OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("조건을 바꿔 다시 만들기") } }
            }
        }
    }
}

@Composable
private fun CollapsibleOption(
    id: String,
    title: String,
    summary: String,
    expanded: String?,
    onExpanded: (String?) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    AppPanel {
        Row(modifier = Modifier.fillMaxWidth().clickable { onExpanded(if (expanded == id) null else id) }, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(summary, color = MutedMint, style = MaterialTheme.typography.bodySmall) }
            Text(if (expanded == id) "−" else "+", style = MaterialTheme.typography.headlineSmall)
        }
        if (expanded == id) { Spacer(Modifier.height(14.dp)); content() }
    }
}

@Composable
private fun MoodAmount(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text(label, modifier = Modifier.width(72.dp)); Slider(value = value, onValueChange = onChange, valueRange = 0f..100f, modifier = Modifier.weight(1f)); Text(value.roundToInt().toString(), modifier = Modifier.width(32.dp)) }
}

@Composable
private fun LyriaLoadingPanel() {
    AppPanel(color = SignalGreen.copy(alpha = .10f)) {
        Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(modifier = Modifier.size(30.dp), color = SignalGreen, strokeWidth = 3.dp); Spacer(Modifier.width(14.dp)); Column { Text("나만의 음악을 만드는 중", style = MaterialTheme.typography.titleMedium); Text("Lyria 3가 30초 곡을 작곡하고 있어요.", color = MutedMint) } }
        Spacer(Modifier.height(14.dp)); LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = SignalGreen)
    }
}

@Composable
private fun LegacyMelodyAliasScreen(
    profile: ProfileSettings,
    candidates: List<MelodyAliasCandidate>,
    onBack: () -> Unit,
    onPreview: (MelodyAliasCandidate) -> Unit,
    onPreviewTone: (String) -> Unit,
    generationState: MelodyAliasGenerationState,
    onGenerate: (String, String, String, String) -> Unit,
    onResetGeneration: () -> Unit,
    onSelect: (MelodyAliasCandidate) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var selectedMood by rememberSaveable { mutableStateOf(profile.melodyAliasMood) }
    var selectedTone by rememberSaveable { mutableStateOf(profile.melodyAliasTone) }
    var selectedPitch by rememberSaveable { mutableStateOf(0.5f) }
    var selectedTempo by rememberSaveable { mutableStateOf(tempoLabel(profile.melodyAliasTempo)) }

    val generatedCandidates = (generationState as? MelodyAliasGenerationState.Success)?.candidates.orEmpty()
    val currentCandidate = (candidates + generatedCandidates).firstOrNull { it.id == profile.melodyAliasId }
        ?: (candidates + generatedCandidates).firstOrNull { it.notes == profile.melodyNotes }
    val moods = (listOf("밝음", "몽환", "차분", "설렘", "귀여움", "에너지") +
        candidates.map { it.mood }).distinct()
    val tones = (listOf("전자음", "피아노", "기타", "벨", "오르골", "신스패드") +
        candidates.map { it.tone }).distinct()

    Column(modifier = modifier.fillMaxSize()) {
        BackHeader(onBack = onBack, title = "멜로디 별칭", subtitle = "짧고 또렷한 알림음 시그널")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AppPanel(color = SignalGreen.copy(alpha = 0.08f)) {
                    Text("현재 멜로디", color = SignalGreen, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        profile.melodyNotes.joinToString(" · "),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniTag(profile.melodyAliasMood, selected = true)
                        MiniTag(profile.melodyAliasTone, selected = true)
                        MiniTag("${profile.melodyAliasTempo} BPM")
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { currentCandidate?.let(onPreview) },
                            enabled = currentCandidate != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("멜로디 듣기")
                        }
                        Button(
                            onClick = {
                                editing = true
                                onResetGeneration()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("수정")
                        }
                    }
                }
            }

            if (editing && generationState !is MelodyAliasGenerationState.Success) {
                item { SectionLabel("1. 원하는 무드") }
                item {
                    ChoiceGrid(
                        options = moods,
                        selected = selectedMood,
                        columns = 2,
                        onSelect = {
                            selectedMood = it
                            onResetGeneration()
                        }
                    )
                }
                item { SectionLabel("2. 원하는 톤") }
                item {
                    ToneChoiceGrid(
                        options = tones,
                        selected = selectedTone,
                        onSelect = {
                            selectedTone = it
                            onResetGeneration()
                            onPreviewTone(it)
                        }
                    )
                }
                item { SectionLabel("3. 피치") }
                item {
                    PitchSlider(
                        value = selectedPitch,
                        onValueChange = {
                            selectedPitch = it
                            onResetGeneration()
                        }
                    )
                }
                item { SectionLabel("4. 속도") }
                item {
                    ChoiceGrid(
                        options = listOf("느림", "보통", "빠름"),
                        selected = selectedTempo,
                        columns = 3,
                        onSelect = {
                            selectedTempo = it
                            onResetGeneration()
                        }
                    )
                }
                item {
                    Button(
                        onClick = {
                            onGenerate(
                                selectedMood,
                                selectedTone,
                                pitchLabel(selectedPitch),
                                tempoRange(selectedTempo)
                            )
                        },
                        enabled = generationState !is MelodyAliasGenerationState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("AI 멜로디 3개 만들기")
                    }
                }
                if (generationState is MelodyAliasGenerationState.Idle) {
                    item {
                        AppPanel(color = SignalGreen.copy(alpha = 0.07f)) {
                            Text("선택한 느낌으로 새로운 알림음을 만들어요", style = MaterialTheme.typography.titleMedium)
                            Text("무드, 톤, 피치, 속도를 OpenAI에 보내 후보 3개를 생성합니다.", color = MutedMint)
                        }
                    }
                }
                if (generationState is MelodyAliasGenerationState.Loading) {
                    item { MelodyAliasLoadingPanel() }
                }
                if (generationState is MelodyAliasGenerationState.Error) {
                    item {
                        AppPanel(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)) {
                            Text("멜로디 생성에 실패했어요", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(generationState.message, color = MutedMint)
                        }
                    }
                }
            }
            if (editing && generationState is MelodyAliasGenerationState.Success) {
                item {
                    AppPanel(color = SignalGreen.copy(alpha = 0.07f)) {
                        Text("선택한 조건", color = SignalGreen, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniTag(selectedMood, selected = true)
                            MiniTag(selectedTone, selected = true)
                            MiniTag(pitchLabel(selectedPitch))
                            MiniTag(selectedTempo)
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onResetGeneration,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("조건 다시 고르기")
                        }
                    }
                }
                item { SectionLabel("AI 추천 후보 3개") }
                items(generatedCandidates, key = { it.id }) { candidate ->
                        MelodyAliasCandidateCard(
                            candidate = candidate,
                            selected = candidate.id == profile.melodyAliasId,
                            onPreview = { onPreview(candidate) },
                            onSelect = {
                                onSelect(candidate)
                                editing = false
                            }
                        )
                }
            }
        }
    }
}

@Composable
private fun MelodyAliasLoadingPanel() {
    AppPanel(color = SignalGreen.copy(alpha = 0.10f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
                color = SignalGreen,
                strokeWidth = 3.dp
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text("멜로디 생성 중", style = MaterialTheme.typography.titleMedium)
                Text("AI가 어울리는 음표와 리듬을 조합하고 있어요.", color = MutedMint)
            }
        }
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = SignalGreen)
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
private fun ChoiceGrid(
    options: List<String>,
    selected: String,
    columns: Int = 2,
    onSelect: (String) -> Unit
) {
    AppPanel {
        options.chunked(columns).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { option ->
                    FilterChip(
                        selected = selected == option,
                        onClick = { onSelect(option) },
                        label = { Text(option) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(columns - rowOptions.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ToneChoiceGrid(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    AppPanel {
        options.chunked(2).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { option ->
                    FilterChip(
                        selected = selected == option,
                        onClick = { onSelect(option) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(option)
                                Spacer(Modifier.width(6.dp))
                                Text("🔊")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(2 - rowOptions.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PitchSlider(
    value: Float,
    onValueChange: (Float) -> Unit
) {
    AppPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("로우피치", color = MutedMint, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text("하이피치", color = MutedMint, style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            steps = 3
        )
        Text(
            text = when {
                value < 0.25f -> "낮고 안정적인 알림음"
                value < 0.5f -> "조금 낮은 톤"
                value < 0.75f -> "선명한 중고음"
                else -> "높고 반짝이는 톤"
            },
            color = SignalGreen,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun MelodyAliasCandidateCard(
    candidate: MelodyAliasCandidate,
    selected: Boolean,
    onPreview: () -> Unit,
    onSelect: () -> Unit
) {
    AppPanel(color = if (selected) SignalGreen.copy(alpha = 0.10f) else MossSurface) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(candidate.name, style = MaterialTheme.typography.titleMedium)
                Text(candidate.notes.joinToString(" · "), color = PaleMint)
            }
            if (selected) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = "선택됨", tint = SignalGreen)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniTag(candidate.mood, selected = true)
            MiniTag(candidate.tone)
            MiniTag("${candidate.tempo} BPM")
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPreview, modifier = Modifier.weight(1f)) {
                Text("듣기")
            }
            Button(onClick = onSelect, modifier = Modifier.weight(1f)) {
                Text(if (selected) "선택됨" else "이걸로 변경")
            }
        }
    }
}

private fun melodyCandidateScore(
    candidate: MelodyAliasCandidate,
    mood: String,
    tone: String,
    pitch: Float,
    tempo: String
): Int {
    val tempoDiff = kotlin.math.abs(candidate.tempo - tempoTarget(tempo))
    val pitchDiff = kotlin.math.abs(candidate.averageMidiPitch() - pitchTarget(pitch))
    return (if (candidate.mood == mood) 50 else 0) +
        (if (candidate.tone == tone) 40 else 0) +
        (25 - (pitchDiff * 2).toInt()).coerceAtLeast(0) +
        (30 - (tempoDiff / 4)).coerceAtLeast(0)
}

private fun MelodyAliasCandidate.averageMidiPitch(): Int {
    val pitches = notes.mapNotNull { note ->
        val match = Regex("""([A-G]#?)(\d)""").matchEntire(note) ?: return@mapNotNull null
        val index = mapOf(
            "C" to 0,
            "C#" to 1,
            "D" to 2,
            "D#" to 3,
            "E" to 4,
            "F" to 5,
            "F#" to 6,
            "G" to 7,
            "G#" to 8,
            "A" to 9,
            "A#" to 10,
            "B" to 11
        )[match.groupValues[1]] ?: return@mapNotNull null
        val octave = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
        (octave + 1) * 12 + index
    }
    return if (pitches.isEmpty()) 76 else pitches.sum() / pitches.size
}

private fun pitchTarget(value: Float): Int = when {
    value < 0.25f -> 66
    value < 0.5f -> 72
    value < 0.75f -> 80
    else -> 88
}

private fun pitchLabel(value: Float): String = when {
    value < 0.25f -> "로우피치"
    value < 0.5f -> "중저음"
    value < 0.75f -> "중고음"
    else -> "하이피치"
}

private fun tempoLabel(tempo: Int): String = when {
    tempo < 110 -> "느림"
    tempo > 135 -> "빠름"
    else -> "보통"
}

private fun tempoTarget(label: String): Int = when (label) {
    "느림" -> 96
    "빠름" -> 148
    else -> 124
}

private fun tempoRange(label: String): String = when (label) {
    "느림" -> "80-100 BPM"
    "빠름" -> "130-160 BPM"
    else -> "100-130 BPM"
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
