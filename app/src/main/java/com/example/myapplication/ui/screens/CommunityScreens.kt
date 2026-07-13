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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
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
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.myapplication.core.model.ChatMessage
import com.example.myapplication.core.model.ChatPreview
import com.example.myapplication.core.model.DeliveryState
import com.example.myapplication.core.model.InboxNotification
import com.example.myapplication.core.model.MelodyAliasCandidate
import com.example.myapplication.core.model.MusicSearchResult
import com.example.myapplication.core.model.OfflineExchangeRecord
import com.example.myapplication.core.model.ProfileSettings
import com.example.myapplication.core.model.ProfileArtist
import com.example.myapplication.core.model.ProfilePrivacySettings
import com.example.myapplication.core.model.ProfileTrack
import com.example.myapplication.core.model.PublicProfile
import com.example.myapplication.core.model.RelationshipStatus
import com.example.myapplication.core.model.SyncState
import com.example.myapplication.core.model.SocialConnection
import com.example.myapplication.core.model.Track
import com.example.myapplication.offlineexchange.ExchangeConnectionState
import com.example.myapplication.offlineexchange.ExchangeMusicCard
import com.example.myapplication.ui.theme.MossOutline
import com.example.myapplication.ui.theme.MossSurface
import com.example.myapplication.ui.theme.MossSurfaceHigh
import com.example.myapplication.ui.theme.MutedMint
import com.example.myapplication.ui.theme.PaleMint
import com.example.myapplication.ui.theme.SignalGreen
import com.example.myapplication.ui.components.MelodyCard
import com.example.myapplication.ui.components.MelodyBubbleColors
import com.example.myapplication.ui.MelodyAliasGenerationState
import com.example.myapplication.ui.LyriaGenerationState
import com.example.myapplication.ui.MusicSearchUiState
import kotlin.math.roundToInt
import java.io.ByteArrayOutputStream

@Composable
fun OnboardingScreen(
    musicSearchState: MusicSearchUiState,
    onSearchMusic: (String) -> Unit,
    onClearMusicSearch: () -> Unit,
    onPreviewMusic: (MusicSearchResult) -> Unit = {},
    onComplete: (List<String>, List<String>, List<ProfileArtist>, List<ProfileTrack>) -> Unit,
    modifier: Modifier = Modifier
) {
    var page by remember { mutableIntStateOf(0) }
    var acceptedTerms by rememberSaveable { mutableStateOf(false) }
    var genres by rememberSaveable { mutableStateOf(setOf("Indie", "R&B")) }
    var moods by rememberSaveable { mutableStateOf(setOf("Calm", "Night")) }
    var favoriteArtists by remember { mutableStateOf<List<ProfileArtist>>(emptyList()) }
    var signatureTracks by remember { mutableStateOf<List<ProfileTrack>>(emptyList()) }
    val titles = listOf(
        "약관을 확인해 주세요",
        "좋아하는 장르를 골라요",
        "최애 아티스트를 찾아요",
        "나를 설명하는 곡을 골라요",
        "공개는 내가 시작해요",
    )
    val descriptions = listOf(
        "서비스 이용약관과 개인정보 처리방침에 동의해야 Melody Bubble을 시작할 수 있어요.",
        "좋아하는 장르와 분위기를 선택해 주세요. 추천과 주변 취향 유사도에 사용됩니다.",
        "이름을 검색해 프로필에 보여줄 아티스트를 최대 3명까지 선택해 주세요.",
        "검색 결과에서 내 음악 취향을 대표하는 곡을 최대 3곡까지 골라 주세요.",
        "주변 공유는 자동으로 켜지지 않아요. 홈에서 직접 시작하고 언제든 알림에서 중지할 수 있어요."
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(5) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (index <= page) SignalGreen else MossOutline)
                )
            }
        }
        if (page > 0) {
            TextButton(
                onClick = {
                    onClearMusicSearch()
                    page -= 1
                },
                modifier = Modifier.padding(top = 6.dp),
            ) { Text("이전") }
        } else {
            Spacer(Modifier.height(42.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                modifier = Modifier.size(68.dp),
                shape = RoundedCornerShape(22.dp),
                color = SignalGreen.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, SignalGreen.copy(alpha = 0.45f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (page) {
                            0 -> Icons.Outlined.Radio
                            1 -> Icons.Outlined.Tune
                            2 -> Icons.Outlined.PeopleOutline
                            3 -> Icons.Outlined.MusicNote
                            else -> Icons.Outlined.Shield
                        },
                        contentDescription = null,
                        tint = SignalGreen,
                        modifier = Modifier.size(32.dp)
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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (page == 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = acceptedTerms,
                        onCheckedChange = { acceptedTerms = it },
                        modifier = Modifier.testTag("onboarding_terms_checkbox"),
                    )
                    Text("이용약관 및 개인정보 처리방침에 동의합니다")
                }
            }
            if (page == 1) {
                Text("선호 장르", style = MaterialTheme.typography.titleMedium)
                listOf(
                    listOf("Indie", "R&B", "Pop", "K-Pop"),
                    listOf("Hip-hop", "Rock", "Jazz", "Electronic"),
                ).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { label ->
                            FilterChip(selected = label in genres, onClick = {
                                genres = if (label in genres) genres - label else genres + label
                            }, label = { Text(label, maxLines = 1) })
                        }
                    }
                }
                Text("선호 분위기", style = MaterialTheme.typography.titleMedium)
                listOf(
                    listOf("Calm", "Night", "Dreamy"),
                    listOf("Bright", "Energetic", "Warm"),
                ).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { label ->
                            FilterChip(selected = label in moods, onClick = {
                                moods = if (label in moods) moods - label else moods + label
                            }, label = { Text(label) })
                        }
                    }
                }
            }
            if (page == 2) {
                SelectedArtistSummary(favoriteArtists) { removed ->
                    favoriteArtists = favoriteArtists
                        .filterNot { it.providerArtistId == removed.providerArtistId && it.name == removed.name }
                        .mapIndexed { index, artist -> artist.copy(rank = index + 1) }
                }
                MusicCatalogSearch(
                    state = musicSearchState,
                    placeholder = "아티스트 이름 · 예: 아이유",
                    onSearch = onSearchMusic,
                )
                val artistResults = (musicSearchState as? MusicSearchUiState.Success)
                    ?.results
                    ?.distinctBy { it.artistId?.toString() ?: it.artist.lowercase() }
                    .orEmpty()
                    .take(6)
                artistResults.forEach { result ->
                    val selected = favoriteArtists.any {
                        it.providerArtistId == result.artistId?.toString() ||
                            it.name.equals(result.artist, ignoreCase = true)
                    }
                    MusicCatalogResultRow(
                        result = result,
                        title = result.artist,
                        subtitle = "${result.genre.ifBlank { "Music" }} · ${result.title}",
                        selected = selected,
                        enabled = selected || favoriteArtists.size < 3,
                    ) {
                        favoriteArtists = if (selected) {
                            favoriteArtists.filterNot {
                                it.providerArtistId == result.artistId?.toString() ||
                                    it.name.equals(result.artist, ignoreCase = true)
                            }
                        } else {
                            favoriteArtists + result.toProfileArtist(favoriteArtists.size + 1)
                        }.mapIndexed { index, artist -> artist.copy(rank = index + 1) }
                    }
                }
            }
            if (page == 3) {
                if (favoriteArtists.isNotEmpty()) {
                    Text("선택한 아티스트로 바로 검색", color = MutedMint, style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        favoriteArtists.take(3).forEach { artist ->
                            FilterChip(
                                selected = false,
                                onClick = { onSearchMusic(artist.name) },
                                label = { Text(artist.name, maxLines = 1) },
                            )
                        }
                    }
                }
                SelectedTrackSummary(signatureTracks) { removed ->
                    signatureTracks = signatureTracks
                        .filterNot { it.providerTrackId == removed.providerTrackId }
                        .mapIndexed { index, track -> track.copy(rank = index + 1) }
                }
                MusicCatalogSearch(
                    state = musicSearchState,
                    placeholder = "곡 또는 아티스트 검색",
                    onSearch = onSearchMusic,
                )
                val trackResults = (musicSearchState as? MusicSearchUiState.Success)?.results.orEmpty().take(6)
                trackResults.forEach { result ->
                    val selected = signatureTracks.any { it.providerTrackId == result.id.toString() }
                    MusicCatalogResultRow(
                        result = result,
                        title = result.title,
                        subtitle = "${result.artist} · ${result.album}",
                        selected = selected,
                        enabled = selected || signatureTracks.size < 3,
                    ) {
                        onPreviewMusic(result)
                        signatureTracks = if (selected) {
                            signatureTracks.filterNot { it.providerTrackId == result.id.toString() }
                        } else {
                            signatureTracks + result.toProfileTrack(signatureTracks.size + 1)
                        }.mapIndexed { index, track -> track.copy(rank = index + 1) }
                    }
                }
            }
            if (page == 4) {
                AppPanel {
                    InfoLine("표시 범위", "캠퍼스 주변")
                    InfoLine("정확한 거리·방향", "공개 안 함")
                    InfoLine("프로필", "내가 선택한 음악 취향")
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (page < 4) {
                    onClearMusicSearch()
                    page += 1
                } else {
                    onComplete(genres.toList(), moods.toList(), favoriteArtists, signatureTracks)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(18.dp),
            enabled = when (page) {
                0 -> acceptedTerms
                1 -> genres.isNotEmpty() && moods.isNotEmpty()
                2 -> favoriteArtists.isNotEmpty()
                3 -> signatureTracks.isNotEmpty()
                else -> true
            }
        ) {
            Text(if (page < 4) "계속" else "시작하기")
        }
    }
}

@Composable
private fun MusicCatalogSearch(
    state: MusicSearchUiState,
    placeholder: String,
    onSearch: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(80) },
            modifier = Modifier.weight(1f).testTag("music_search_input"),
            placeholder = { Text(placeholder) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSearch(query) }),
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = { onSearch(query) },
            enabled = query.isNotBlank() && state !is MusicSearchUiState.Loading,
            modifier = Modifier.height(56.dp).testTag("music_search_button"),
        ) {
            if (state is MusicSearchUiState.Loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("검색")
            }
        }
    }
    when (state) {
        is MusicSearchUiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
        is MusicSearchUiState.Success -> Text("${state.results.size}개의 검색 결과", color = MutedMint, style = MaterialTheme.typography.labelMedium)
        else -> Unit
    }
}

@Composable
private fun MusicCatalogResultRow(
    result: MusicSearchResult,
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        color = if (selected) SignalGreen.copy(alpha = 0.16f) else MossSurfaceHigh,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) SignalGreen else MossOutline),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            ProfileArtwork(result.artworkUrl, SignalGreen, 48.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = MutedMint, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                when {
                    selected -> "선택됨"
                    enabled -> "선택"
                    else -> "최대 3개"
                },
                color = if (selected || enabled) SignalGreen else MutedMint,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SelectedArtistSummary(
    artists: List<ProfileArtist>,
    onRemove: (ProfileArtist) -> Unit,
) {
    if (artists.isEmpty()) return
    Text("선택한 아티스트 ${artists.size}/3", fontWeight = FontWeight.Bold)
    artists.forEach { artist ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${artist.rank}. ${artist.name}", Modifier.weight(1f), maxLines = 1)
            TextButton(onClick = { onRemove(artist) }) { Text("삭제") }
        }
    }
}

@Composable
private fun SelectedTrackSummary(
    tracks: List<ProfileTrack>,
    onRemove: (ProfileTrack) -> Unit,
) {
    if (tracks.isEmpty()) return
    Text("선택한 대표곡 ${tracks.size}/3", fontWeight = FontWeight.Bold)
    tracks.forEach { track ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${track.rank}. ${track.title}", fontWeight = FontWeight.Bold, maxLines = 1)
                Text(track.artist, color = MutedMint, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { onRemove(track) }) { Text("삭제") }
        }
    }
}

private fun MusicSearchResult.toProfileTrack(rank: Int) = ProfileTrack(
    rank = rank,
    provider = "ITUNES",
    providerTrackId = id.toString(),
    title = title,
    artist = artist,
    album = album.ifBlank { null },
    artworkUrl = artworkUrl,
    genreTags = listOfNotNull(genre.takeIf(String::isNotBlank)),
)

private fun MusicSearchResult.toProfileArtist(rank: Int) = ProfileArtist(
    rank = rank,
    provider = "ITUNES",
    providerArtistId = artistId?.toString(),
    name = artist,
    imageUrl = artworkUrl,
    genreTags = listOfNotNull(genre.takeIf(String::isNotBlank)),
)

@Composable
fun InboxScreen(
    chats: List<ChatPreview>,
    onOpenChat: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            ScreenTitle(
                eyebrow = "PRIVATE CHAT",
                title = "채팅",
                subtitle = "맞팔 사용자와 나눈 대화를 보여요"
            )
        }
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

@Composable
fun NotificationScreen(
    notifications: List<InboxNotification>,
    onBack: () -> Unit,
    onMarkRead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        BackHeader(onBack = onBack, title = "알림", subtitle = "새로운 활동을 확인하세요")
        HorizontalDivider(color = MossOutline)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("알림", color = MutedMint, modifier = Modifier.weight(1f))
                    Text(
                        "모두 읽음",
                        color = SignalGreen,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onMarkRead() }
                            .padding(12.dp),
                    )
                }
            }
            if (notifications.isEmpty()) {
                item {
                    AppPanel {
                        Text("아직 새로운 알림이 없어요", color = MutedMint)
                    }
                }
            }
            items(notifications, key = { it.id }) { notification ->
                AppPanel(
                    color = if (notification.isRead) MossSurface else SignalGreen.copy(alpha = 0.09f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TrackGlyph(
                            notification.actorAlias ?: "시스템",
                            notification.actorColorHex ?: 0xFF2A4937L,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                notification.actorAlias ?: "Melody Bubble",
                                fontWeight = FontWeight.Bold,
                            )
                            Text(notification.preview, color = MutedMint)
                        }
                        Text(
                            notification.relativeTime,
                            style = MaterialTheme.typography.labelMedium,
                            color = MutedMint,
                        )
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
    var inputFocused by remember { mutableStateOf(false) }
    val messageListState = rememberLazyListState()
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)

    LaunchedEffect(messages.size, messages.lastOrNull()?.clientMessageId) {
        if (messages.isNotEmpty()) {
            // The first LazyColumn item is the chat notice, so the last message index is size.
            messageListState.animateScrollToItem(messages.size)
        }
    }

    LaunchedEffect(inputFocused, imeBottom, messages.size) {
        if (inputFocused && messages.isNotEmpty()) {
            // Keep the newest bubble visible while the keyboard changes the list viewport.
            messageListState.scrollToItem(messages.size)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        BackHeader(onBack = onBack, title = chat.peerAlias, subtitle = "${currentTrack.title} 재생 중")
        HorizontalDivider(color = MossOutline)
        LazyColumn(
            state = messageListState,
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
                            if (message.isMine) {
                                Text(
                                    when (message.deliveryState) {
                                        DeliveryState.PENDING -> "전송 중"
                                        DeliveryState.SENT -> "안 읽음"
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
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { inputFocused = it.isFocused },
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
    profileSaving: Boolean,
    feedbackMessage: String?,
    followingCount: Int,
    followerCount: Int,
    verifiedOfflineExchangeCount: Int,
    offlineExchangeGenres: List<String>,
    offlineExchangeMoods: List<String>,
    nowPlayingTrack: Track?,
    nowPlayingActive: Boolean,
    onLoadConnections: () -> Unit,
    onOpenFollowing: () -> Unit,
    onOpenFollowers: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBubbleMode: () -> Unit,
    onProfileUpdate: (String, Long, String, String?, List<String>, List<String>) -> Unit,
    onProfileCurationUpdate: (List<ProfileTrack>, List<ProfileArtist>) -> Unit,
    musicSearchState: MusicSearchUiState,
    onSearchMusic: (String) -> Unit,
    onClearMusicSearch: () -> Unit,
    onPreviewMusic: (MusicSearchResult) -> Unit,
    onPlayProfileMusic: () -> Unit,
    onDeleteProfileMusic: () -> Unit,
    onOpenMelodyAlias: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onLoadConnections() }
    var editorSection by rememberSaveable { mutableStateOf<String?>(null) }
    var awaitingProfileSave by rememberSaveable { mutableStateOf(false) }
    var profileSaveStarted by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable(profile.accountAlias) { mutableStateOf(profile.accountAlias) }
    var bio by rememberSaveable(profile.bio) { mutableStateOf(profile.bio) }
    var avatar by rememberSaveable(profile.avatarDataUrl) { mutableStateOf(profile.avatarDataUrl) }
    var colorHex by rememberSaveable(profile.colorHex) { mutableStateOf(profile.colorHex) }
    var genres by rememberSaveable(profile.genres) { mutableStateOf(profile.genres) }
    var moods by rememberSaveable(profile.moods) { mutableStateOf(profile.moods) }
    var signatureTracks by remember(profile.signatureTracks) { mutableStateOf(profile.signatureTracks) }
    var favoriteArtists by remember(profile.favoriteArtists) { mutableStateOf(profile.favoriteArtists) }
    LaunchedEffect(profileSaving, feedbackMessage, awaitingProfileSave) {
        if (awaitingProfileSave && profileSaving) profileSaveStarted = true
        if (awaitingProfileSave && profileSaveStarted && !profileSaving) {
            awaitingProfileSave = false
            profileSaveStarted = false
            if (feedbackMessage == "프로필을 변경했어요") editorSection = null
        }
    }
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

    if (editorSection != null) ModalBottomSheet(
        onDismissRequest = {
            editorSection = null
            onClearMusicSearch()
        },
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
                    Text(
                        when (editorSection) {
                            "TRACKS" -> "대표곡 편집"
                            "ARTISTS" -> "최애 아티스트 편집"
                            else -> "기본 프로필 편집"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when (editorSection) {
                            "TRACKS" -> "프로필을 설명하는 곡을 최대 3곡까지 골라요"
                            "ARTISTS" -> "취향을 보여줄 아티스트를 최대 3명까지 골라요"
                            else -> "이름, 소개, 테마와 취향 태그를 바꿔요"
                        },
                        color = MutedMint,
                    )
                }
                TextButton(onClick = {
                    editorSection = null
                    onClearMusicSearch()
                }) { Text("닫기") }
            }
            Spacer(Modifier.height(22.dp))
            if (editorSection == "BASIC") {
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
                onClick = {
                    awaitingProfileSave = true
                    profileSaveStarted = false
                    onProfileUpdate(name, colorHex, bio, avatar, genres, moods)
                },
                enabled = name.trim().length >= 2 && !awaitingProfileSave,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (awaitingProfileSave) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (awaitingProfileSave) "저장 중" else "프로필 저장", fontWeight = FontWeight.Bold)
            }
            if (!awaitingProfileSave && feedbackMessage?.contains("저장하지 못해") == true) {
                Text(feedbackMessage, color = MaterialTheme.colorScheme.error)
            }
            }
            if (editorSection == "TRACKS") {
                SelectedTrackSummary(signatureTracks) { removed ->
                    signatureTracks = signatureTracks
                        .filterNot { it.providerTrackId == removed.providerTrackId }
                        .mapIndexed { index, track -> track.copy(rank = index + 1) }
                }
                MusicCatalogSearch(
                    state = musicSearchState,
                    placeholder = "곡 또는 아티스트 검색",
                    onSearch = onSearchMusic,
                )
                (musicSearchState as? MusicSearchUiState.Success)?.results.orEmpty().take(8).forEach { result ->
                    val selected = signatureTracks.any { it.providerTrackId == result.id.toString() }
                    MusicCatalogResultRow(
                        result = result,
                        title = result.title,
                        subtitle = "${result.artist} · ${result.album}",
                        selected = selected,
                        enabled = selected || signatureTracks.size < 3,
                    ) {
                        onPreviewMusic(result)
                        signatureTracks = if (selected) {
                            signatureTracks.filterNot { it.providerTrackId == result.id.toString() }
                        } else {
                            signatureTracks + result.toProfileTrack(signatureTracks.size + 1)
                        }.mapIndexed { index, track -> track.copy(rank = index + 1) }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        onProfileCurationUpdate(signatureTracks, favoriteArtists)
                        editorSection = null
                        onClearMusicSearch()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("대표곡 저장", fontWeight = FontWeight.Bold) }
            }
            if (editorSection == "ARTISTS") {
                SelectedArtistSummary(favoriteArtists) { removed ->
                    favoriteArtists = favoriteArtists
                        .filterNot { it.providerArtistId == removed.providerArtistId && it.name == removed.name }
                        .mapIndexed { index, artist -> artist.copy(rank = index + 1) }
                }
                MusicCatalogSearch(
                    state = musicSearchState,
                    placeholder = "아티스트 이름 · 예: 아이유",
                    onSearch = onSearchMusic,
                )
                (musicSearchState as? MusicSearchUiState.Success)
                    ?.results
                    ?.distinctBy { it.artistId?.toString() ?: it.artist.lowercase() }
                    .orEmpty()
                    .take(8)
                    .forEach { result ->
                        val selected = favoriteArtists.any {
                            it.providerArtistId == result.artistId?.toString() ||
                                it.name.equals(result.artist, ignoreCase = true)
                        }
                        MusicCatalogResultRow(
                            result = result,
                            title = result.artist,
                            subtitle = "${result.genre.ifBlank { "Music" }} · ${result.title}",
                            selected = selected,
                            enabled = selected || favoriteArtists.size < 3,
                        ) {
                            favoriteArtists = if (selected) {
                                favoriteArtists.filterNot {
                                    it.providerArtistId == result.artistId?.toString() ||
                                        it.name.equals(result.artist, ignoreCase = true)
                                }
                            } else {
                                favoriteArtists + result.toProfileArtist(favoriteArtists.size + 1)
                            }.mapIndexed { index, artist -> artist.copy(rank = index + 1) }
                        }
                    }
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        onProfileCurationUpdate(signatureTracks, favoriteArtists)
                        editorSection = null
                        onClearMusicSearch()
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("아티스트 저장", fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    val canvas = MelodyBubbleColors.Background
    val ink = MelodyBubbleColors.Text
    val muted = MelodyBubbleColors.TextMuted
    val accent = MelodyBubbleColors.Primary
    val outline = MelodyBubbleColors.Border
    LazyColumn(
        modifier = modifier.fillMaxSize().background(canvas).testTag("my_profile_list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("음악 프로필", color = ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { editorSection = "BASIC" }) { Text("편집", color = accent) }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "설정", tint = ink) }
            }
        }
        item {
            ProfileLightPanel(outline = outline, ink = ink, onClick = { editorSection = "BASIC" }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(profile.avatarDataUrl, profile.accountAlias, profile.colorHex, 82.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(profile.accountAlias, color = ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (profile.profileHandle.isNotBlank()) Text("@${profile.profileHandle}", color = muted)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            profile.bio.ifBlank { "소개를 추가해 나를 표현해보세요." },
                            color = muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            (profile.genres + profile.moods).distinct().take(3).forEach {
                                LightProfileTag(it.uppercase(), accent, outline)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(15.dp))
                Row(Modifier.fillMaxWidth()) {
                    LightProfileCount("팔로잉", followingCount, ink, muted, Modifier.weight(1f).clickable(onClick = onOpenFollowing))
                    LightProfileCount("팔로워", followerCount, ink, muted, Modifier.weight(1f).clickable(onClick = onOpenFollowers))
                    LightProfileCount("검증된 교환", verifiedOfflineExchangeCount, ink, muted, Modifier.weight(1f).clickable(onClick = onOpenBubbleMode))
                }
            }
        }
        item {
            ProfileLightPanel(outline = outline, ink = ink) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("지금 듣는 음악", color = ink, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Surface(
                        color = if (nowPlayingActive) MelodyBubbleColors.Primary.copy(alpha = 0.14f) else MelodyBubbleColors.SurfaceSelected,
                        contentColor = if (nowPlayingActive) accent else muted,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            if (nowPlayingActive) "● LIVE" else "대기 중",
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (nowPlayingTrack != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProfileArtwork(null, accent, 52.dp)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(nowPlayingTrack.title, color = ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(nowPlayingTrack.artist, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    ProfileEmptyRow(
                        title = "재생 중인 음악이 아직 없어요",
                        description = "음악 앱에서 재생하면 이곳에 표시돼요.",
                        accent = accent,
                        muted = muted,
                    )
                }
            }
        }
        item {
            ProfileLightPanel(outline = outline, ink = ink, contentPadding = 13.dp, onClick = onOpenBubbleMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Outlined.BluetoothSearching, null, tint = accent)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("버블 모드", color = ink, fontWeight = FontWeight.Bold)
                        Text("가까운 사람과 음악 카드를 직접 교환해요", color = muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Surface(color = accent, contentColor = MelodyBubbleColors.OnPrimary, shape = RoundedCornerShape(16.dp)) {
                        Text("시작", Modifier.padding(horizontal = 13.dp, vertical = 7.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            ProfileLightPanel(outline = outline, ink = ink, onClick = { editorSection = "TRACKS" }) {
                Text("요즘 나를 설명하는 3곡", color = ink, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(9.dp))
                if (profile.signatureTracks.isEmpty()) {
                    ProfileEmptyRow(
                        title = "아직 고른 곡이 없어요",
                        description = "프로필을 대표할 노래를 최대 3곡까지 추가해보세요.",
                        actionLabel = "3곡 고르기",
                        accent = accent,
                        muted = muted,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        profile.signatureTracks.take(3).forEachIndexed { index, track ->
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${index + 1}", color = accent, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(5.dp))
                                    ProfileArtwork(track.artworkUrl, accent, 40.dp)
                                }
                                Spacer(Modifier.height(5.dp))
                                Text(track.title, color = ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                Text(track.artist, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        item {
            ProfileLightPanel(outline = outline, ink = ink, onClick = { editorSection = "ARTISTS" }) {
                Text("최애 아티스트 3명", color = ink, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(9.dp))
                if (profile.favoriteArtists.isEmpty()) {
                    ProfileEmptyRow(
                        title = "좋아하는 아티스트를 알려주세요",
                        description = "내 취향을 가장 잘 보여주는 아티스트를 추가할 수 있어요.",
                        actionLabel = "아티스트 추가",
                        accent = accent,
                        muted = muted,
                    )
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        profile.favoriteArtists.take(3).forEach { artist ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                ProfileArtwork(artist.imageUrl, accent, 48.dp, circle = true)
                                Spacer(Modifier.height(4.dp))
                                Text(artist.name, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        val discoveredTaste = (profile.tasteFingerprint.genres + profile.tasteFingerprint.moods).take(4)
        val measuredTaste = discoveredTaste.filter { it.ratio > 0.0 }
        val discoveredLabels = (discoveredTaste.map { it.label } + offlineExchangeGenres + offlineExchangeMoods).distinct().take(4)
        item {
            ProfileLightPanel(outline = outline, ink = ink) {
                Text("교환으로 발견한 취향", color = ink, fontWeight = FontWeight.Bold)
                Text("양쪽에서 확인된 음악 카드만 반영해요", color = muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                when {
                    measuredTaste.isNotEmpty() -> measuredTaste.forEach { metric ->
                        LightTasteMetric(metric.label, (metric.ratio * 100).roundToInt(), ink, muted, accent)
                    }
                    discoveredLabels.isNotEmpty() -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        discoveredLabels.take(3).forEach { label -> LightProfileTag(label, accent, outline) }
                    }
                    else -> ProfileEmptyRow(
                        title = "교환 데이터가 쌓이면 취향이 보여요",
                        description = "검증된 음악 교환을 바탕으로 장르와 무드를 정리해드려요.",
                        accent = accent,
                        muted = muted,
                    )
                }
            }
        }
        if (profile.profileMusicUrl != null) item {
            ProfileLightPanel(outline = outline, ink = ink, contentPadding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.MusicNote, null, tint = accent)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("30초 프로필 음악", color = ink, fontWeight = FontWeight.Bold)
                        Text(profile.profileMusicDescription ?: "나의 바이브를 담은 음악", color = muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    TextButton(onClick = onPlayProfileMusic) { Text("재생", color = accent) }
                    TextButton(onClick = onDeleteProfileMusic) { Text("삭제", color = MelodyBubbleColors.Danger) }
                }
            }
        }
        item {
            ProfileLightPanel(outline = outline, ink = ink, contentPadding = 12.dp, onClick = onOpenMelodyAlias) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Tune, null, tint = accent)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("멜로디 별칭과 30초 음악", color = ink, fontWeight = FontWeight.Bold)
                        Text(
                            if (profile.melodyAliasId.isBlank()) "나만의 음악 정체성을 만들어보세요."
                            else "${profile.melodyAliasMood} · ${profile.melodyAliasTone} · ${profile.melodyAliasTempo} BPM",
                            color = muted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = muted)
                }
            }
        }
    }
}

@Composable
fun PublicProfileScreen(
    profile: PublicProfile?,
    loading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onFollow: () -> Unit,
    onPlayProfileMusic: (String, Float) -> Unit,
    onPlayTrackPreview: (ProfileTrack) -> Unit = {},
    modifier: Modifier = Modifier,
    onShare: () -> Unit = {},
    onMore: (() -> Unit)? = null,
) {
    val canvas = MelodyBubbleColors.Background
    val ink = MelodyBubbleColors.Text
    val muted = MelodyBubbleColors.TextMuted
    val lavender = MelodyBubbleColors.Primary
    val outline = MelodyBubbleColors.Border
    Column(modifier.fillMaxSize().background(canvas)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로", tint = ink)
            }
            Text("음악 프로필", color = ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onShare) { Text("공유", color = lavender) }
            onMore?.let { action -> TextButton(onClick = action) { Text("더보기", color = muted) } }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = lavender)
            }
            profile == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(errorMessage ?: "프로필을 불러오지 못했어요.", color = muted)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onRetry) { Text("다시 시도") }
                }
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().testTag("public_profile_list"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, top = 4.dp, end = 18.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    ProfileLightPanel(outline = outline, ink = ink) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProfileAvatar(profile.avatarUrl, profile.displayName, profile.colorHex, 82.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(profile.displayName, color = ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text("@${profile.profileHandle}", color = muted, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    profile.bio.ifBlank { "음악으로 자신을 소개하는 사용자" },
                                    color = muted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    (profile.genres + profile.moods).distinct().take(3).forEach {
                                        LightProfileTag(it.uppercase(), lavender, outline)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        if (!profile.isSelf) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (profile.mutual) {
                                    Surface(
                                        color = MelodyBubbleColors.SurfaceSelected,
                                        contentColor = lavender,
                                        shape = RoundedCornerShape(22.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MelodyBubbleColors.BorderStrong),
                                    ) { Text("맞팔 중", Modifier.padding(horizontal = 18.dp, vertical = 10.dp), fontWeight = FontWeight.Bold) }
                                }
                                Button(
                                    onClick = onFollow,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = lavender, contentColor = MelodyBubbleColors.OnPrimary),
                                ) {
                                    Text(when {
                                        profile.following -> "팔로잉"
                                        profile.relationship == RelationshipStatus.FOLLOWS_ME -> "맞팔하기"
                                        else -> "팔로우"
                                    })
                                }
                            }
                        } else {
                            Surface(color = MelodyBubbleColors.SurfaceSelected, contentColor = lavender, shape = RoundedCornerShape(22.dp)) {
                                Text("내 프로필", Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(15.dp))
                        Row(Modifier.fillMaxWidth()) {
                            LightProfileCount("팔로잉", profile.stats.followingCount, ink, muted, Modifier.weight(1f))
                            LightProfileCount("팔로워", profile.stats.followerCount, ink, muted, Modifier.weight(1f))
                            LightProfileCount("검증된 교환", profile.stats.verifiedExchangeCount, ink, muted, Modifier.weight(1f))
                        }
                    }
                }
                if (profile.sharedVerifiedExchangeCount > 0) item {
                    ProfileLightPanel(outline = outline, ink = ink, contentPadding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.PeopleOutline, null, tint = lavender)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("실제로 음악을 교환한 사이", color = ink, fontWeight = FontWeight.Bold)
                                Text("서로 확인된 교환 ${profile.sharedVerifiedExchangeCount}회", color = muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Outlined.ChevronRight, null, tint = muted)
                        }
                    }
                }
                profile.nowPlaying?.let { nowPlaying ->
                    item {
                        ProfileLightPanel(outline = outline, ink = ink) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("지금 듣는 음악", color = ink, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                Surface(color = MelodyBubbleColors.Danger.copy(alpha = 0.14f), contentColor = MelodyBubbleColors.Danger, shape = RoundedCornerShape(12.dp)) {
                                    Text("● LIVE", Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ProfileArtwork(nowPlaying.artworkUrl, lavender, 58.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(nowPlaying.title, color = ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(nowPlaying.artist, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val progress = if ((nowPlaying.durationMs ?: 0L) > 0L) {
                                        ((nowPlaying.positionMs ?: 0L).toFloat() / nowPlaying.durationMs!!.toFloat()).coerceIn(0f, 1f)
                                    } else 0f
                                    Spacer(Modifier.height(7.dp))
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth().height(3.dp),
                                        color = lavender,
                                        trackColor = MelodyBubbleColors.SurfaceSelected,
                                    )
                                }
                            }
                        }
                    }
                }
                if (profile.signatureTracks.isNotEmpty()) {
                    item {
                        ProfileLightPanel(outline = outline, ink = ink) {
                            Text("요즘 나를 설명하는 3곡", color = ink, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                profile.signatureTracks.take(3).forEachIndexed { index, track ->
                                    Surface(
                                        modifier = Modifier.weight(1f).clickable { onPlayTrackPreview(track) },
                                        color = MelodyBubbleColors.SurfaceRaised,
                                        contentColor = ink,
                                        shape = RoundedCornerShape(14.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, outline),
                                    ) {
                                        Column(Modifier.padding(8.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(color = lavender, contentColor = MelodyBubbleColors.OnPrimary, shape = CircleShape) {
                                                    Text("${index + 1}", Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                                                }
                                                Spacer(Modifier.width(5.dp))
                                                ProfileArtwork(track.artworkUrl, lavender, 36.dp)
                                            }
                                            Spacer(Modifier.height(7.dp))
                                            Text(track.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                            Text(track.artist, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (profile.favoriteArtists.isNotEmpty()) {
                    item {
                        ProfileLightPanel(outline = outline, ink = ink) {
                            Text("최애 아티스트 3명", color = ink, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                profile.favoriteArtists.take(3).forEach { artist ->
                                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                        ProfileArtwork(artist.imageUrl, lavender, 52.dp, circle = true)
                                        Spacer(Modifier.height(5.dp))
                                        Text(artist.name, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
                val fallbackTaste = (profile.tasteFingerprint.genres + profile.tasteFingerprint.moods).take(4)
                if (profile.commonTaste != null || fallbackTaste.isNotEmpty()) item {
                    ProfileLightPanel(outline = outline, ink = ink) {
                        Text(if (profile.commonTaste != null) "공통 취향" else "교환으로 발견된 취향", color = ink, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                val metrics = profile.commonTaste?.metrics.orEmpty()
                                if (metrics.isNotEmpty()) {
                                    metrics.forEach { metric ->
                                        LightTasteMetric(metric.label, metric.score, ink, muted, lavender)
                                    }
                                } else {
                                    fallbackTaste.forEach { metric ->
                                        LightTasteMetric(metric.label, (metric.ratio * 100).roundToInt(), ink, muted, lavender)
                                    }
                                }
                            }
                            profile.commonTaste?.let { taste ->
                                Spacer(Modifier.width(14.dp))
                                Surface(
                                    modifier = Modifier.size(82.dp),
                                    shape = CircleShape,
                                    color = MelodyBubbleColors.SurfaceSelected,
                                    contentColor = lavender,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text("취향 겹침", style = MaterialTheme.typography.labelSmall)
                                        Text("${taste.score}%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                profile.profileMusicUrl?.let { musicUrl ->
                    item {
                        ProfileLightPanel(
                            outline = outline,
                            ink = ink,
                            contentPadding = 12.dp,
                            onClick = { onPlayProfileMusic(musicUrl, profile.profileMusicStartSeconds ?: 0f) },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.MusicNote, null, tint = lavender)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("30초 프로필 음악", color = ink, fontWeight = FontWeight.Bold)
                                    Text(profile.profileMusicDescription ?: "이 사용자의 음악 정체성", color = muted, style = MaterialTheme.typography.bodySmall)
                                }
                                Icon(Icons.Outlined.ChevronRight, null, tint = muted)
                            }
                        }
                    }
                }
                item {
                    ProfileLightPanel(outline = outline, ink = ink, contentPadding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Shield, null, tint = lavender)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("공개 범위는 항목별로 달라요", color = ink, fontWeight = FontWeight.Bold)
                                Text("지금 듣는 음악 · 청취 분석 · 교환 기록", color = muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileLightPanel(
    outline: Color,
    ink: Color,
    contentPadding: androidx.compose.ui.unit.Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val panelModifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    Surface(
        modifier = panelModifier,
        shape = RoundedCornerShape(18.dp),
        color = MelodyBubbleColors.Surface,
        contentColor = ink,
        border = androidx.compose.foundation.BorderStroke(1.dp, outline),
        shadowElevation = 0.dp,
    ) { Column(Modifier.padding(contentPadding), content = content) }
}

@Composable
private fun ProfileEmptyRow(
    title: String,
    description: String,
    accent: Color,
    muted: Color,
    actionLabel: String? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(12.dp),
            color = MelodyBubbleColors.SurfaceSelected,
            contentColor = accent,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.MusicNote, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = MelodyBubbleColors.Text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(description, color = muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        actionLabel?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, color = accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LightProfileTag(label: String, accent: Color, outline: Color) {
    Surface(color = MelodyBubbleColors.SurfaceSelected, contentColor = accent, border = androidx.compose.foundation.BorderStroke(1.dp, outline), shape = RoundedCornerShape(10.dp)) {
        Text(label, Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LightProfileCount(label: String, count: Int, ink: Color, muted: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), color = ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, color = muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ProfileArtwork(url: String?, accent: Color, size: androidx.compose.ui.unit.Dp, circle: Boolean = false) {
    val shape = if (circle) CircleShape else RoundedCornerShape(10.dp)
    Surface(modifier = Modifier.size(size), shape = shape, color = MelodyBubbleColors.SurfaceSelected, contentColor = accent) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.MusicNote, null, tint = accent) }
        }
    }
}

@Composable
private fun LightTasteMetric(label: String, score: Int, ink: Color, muted: Color, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        Text("${score.coerceIn(0, 100)}%", color = muted, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(8.dp))
        LinearProgressIndicator(
            progress = { score.coerceIn(0, 100) / 100f },
            modifier = Modifier.width(64.dp).height(4.dp),
            color = accent,
            trackColor = MelodyBubbleColors.SurfaceSelected,
        )
    }
}

@Composable
private fun ConnectionCount(
    label: String,
    count: Int,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = RoundedCornerShape(18.dp),
        color = MossSurfaceHigh,
    ) {
        Column(Modifier.padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, color = MutedMint, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profile: ProfileSettings,
    offlineExchangeCount: Int,
    onBack: () -> Unit,
    onDiscoverableChange: (Boolean) -> Unit,
    onAllowReactionsChange: (Boolean) -> Unit,
    onOfflineExchangeChange: (Boolean) -> Unit,
    onMusicVisibilityChange: (String) -> Unit,
    onProfilePrivacyChange: (ProfilePrivacySettings) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenOfflineExchange: () -> Unit,
    onOpenBlockedUsers: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visibilityEditing by rememberSaveable { mutableStateOf(false) }
    var deleteConfirm by rememberSaveable { mutableStateOf(false) }
    var draftPrivacy by remember(profile.privacy) { mutableStateOf(profile.privacy) }

    if (deleteConfirm) AlertDialog(
        onDismissRequest = { deleteConfirm = false },
        title = { Text("계정을 탈퇴할까요?") },
        text = { Text("프로필과 계정 데이터가 삭제되며 되돌릴 수 없습니다.") },
        confirmButton = { TextButton(onClick = { deleteConfirm = false; onDeleteAccount() }) { Text("탈퇴", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("취소") } },
    )
    if (visibilityEditing) ModalBottomSheet(
        onDismissRequest = { visibilityEditing = false },
        containerColor = MossSurface,
        contentColor = PaleMint,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text("프로필 공개 범위", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("프로필의 각 정보를 누구에게 보여줄지 따로 정해요.", color = MutedMint)
            Spacer(Modifier.height(18.dp))
            Text("지금 듣는 음악", fontWeight = FontWeight.Bold)
            PrivacyChoiceRow(
                selected = draftPrivacy.currentMusicVisibility,
                options = listOf("EVERYONE" to "전체", "MUTUALS" to "맞팔", "PRIVATE" to "비공개"),
            ) { draftPrivacy = draftPrivacy.copy(currentMusicVisibility = it) }
            Spacer(Modifier.height(18.dp))
            SettingsToggle(
                Icons.Outlined.MusicNote,
                "청취 기반 취향 분석",
                "선택해야만 청취 데이터를 취향 계산에 사용해요",
                draftPrivacy.listeningInsightsEnabled,
            ) { draftPrivacy = draftPrivacy.copy(listeningInsightsEnabled = it) }
            if (draftPrivacy.listeningInsightsEnabled) {
                PrivacyChoiceRow(
                    selected = draftPrivacy.listeningInsightsVisibility,
                    options = listOf("EVERYONE" to "전체", "MUTUALS" to "맞팔", "PRIVATE" to "비공개"),
                ) { draftPrivacy = draftPrivacy.copy(listeningInsightsVisibility = it) }
            }
            Spacer(Modifier.height(18.dp))
            Text("교환으로 발견된 취향", fontWeight = FontWeight.Bold)
            PrivacyChoiceRow(
                selected = draftPrivacy.exchangeInsightsVisibility,
                options = listOf(
                    "EVERYONE" to "전체",
                    "MUTUALS" to "맞팔",
                    "EXCHANGED" to "교환한 사람",
                    "PRIVATE" to "비공개",
                ),
            ) { draftPrivacy = draftPrivacy.copy(exchangeInsightsVisibility = it) }
            Spacer(Modifier.height(18.dp))
            Text("버블 모드 참여 상태", fontWeight = FontWeight.Bold)
            PrivacyChoiceRow(
                selected = draftPrivacy.bubblePresenceVisibility,
                options = listOf(
                    "PARTICIPANTS_ONLY" to "참여자",
                    "MUTUALS" to "맞팔",
                    "PRIVATE" to "비공개",
                ),
            ) { draftPrivacy = draftPrivacy.copy(bubblePresenceVisibility = it) }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    onProfilePrivacyChange(draftPrivacy)
                    onMusicVisibilityChange(
                        when (draftPrivacy.currentMusicVisibility) {
                            "MUTUALS" -> "맞팔에게만 공개"
                            "PRIVATE" -> "비공개"
                            else -> "제목과 아티스트 공개"
                        }
                    )
                    visibilityEditing = false
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("공개 범위 저장", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(28.dp))
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
                }
                Column(Modifier.padding(start = 8.dp)) {
                    Text("SETTINGS", color = SignalGreen, style = MaterialTheme.typography.labelLarge)
                    Text("설정", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { SectionLabel("공개 및 연결") }
        item { SettingsLink(Icons.Outlined.Shield, "프로필 공개 범위", "현재 음악 · 청취 분석 · 교환 기록") { draftPrivacy = profile.privacy; visibilityEditing = true } }
        item { SettingsToggle(Icons.Outlined.Radio, "주변에서 발견 가능", "정확한 위치와 방향은 항상 숨겨요", profile.discoverable, onDiscoverableChange) }
        item { SettingsToggle(Icons.Outlined.FavoriteBorder, "음악 리액션 받기", "정해진 안전한 리액션만 받아요", profile.allowReactions, onAllowReactionsChange) }
        item { SettingsLink(Icons.Outlined.Block, "차단 사용자 관리", "차단 해제와 목록 확인", onOpenBlockedUsers) }
        item { SectionLabel("기기 기능") }
        item { SettingsLink(Icons.Outlined.Notifications, "자동 음악 감지", "재생 알림으로 현재 곡을 감지해요", onOpenNotificationAccess) }
        item { SettingsToggle(Icons.AutoMirrored.Outlined.BluetoothSearching, "오프라인 음악 카드 교환", "가까운 상대와 승인한 카드만 교환해요", profile.offlineExchangeEnabled, onOfflineExchangeChange) }
        item { SettingsLink(Icons.Outlined.History, "교환 기록", "이 기기에 저장된 기록 ${offlineExchangeCount}건", onOpenOfflineExchange) }
        item { SectionLabel("계정") }
        item { OutlinedButton(onClick = onLogout, Modifier.fillMaxWidth().height(50.dp)) { Text("로그아웃") } }
        item { TextButton(onClick = { deleteConfirm = true }, Modifier.fillMaxWidth()) { Text("회원 탈퇴", color = MaterialTheme.colorScheme.error) } }
    }
}

@Composable
private fun PrivacyChoiceRow(
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        options.chunked(2).forEach { rowOptions ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { (value, label) ->
                    FilterChip(
                        selected = selected == value,
                        onClick = { onSelect(value) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowOptions.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SocialConnectionsScreen(
    following: List<SocialConnection>,
    followers: List<SocialConnection>,
    loading: Boolean,
    initialFollowing: Boolean,
    onBack: () -> Unit,
    onUnfollow: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(if (initialFollowing) 0 else 1) }
    var pendingRemoval by remember { mutableStateOf<SocialConnection?>(null) }
    val connections = if (selectedTab == 0) following else followers

    pendingRemoval?.let { connection ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(if (connection.mutual) "맞팔을 취소할까요?" else "팔로우를 취소할까요?") },
            text = { Text("${connection.displayAlias} 님과의 팔로우 관계가 변경됩니다.") },
            confirmButton = {
                TextButton(onClick = {
                    connection.relationshipId?.let(onUnfollow)
                    pendingRemoval = null
                }) { Text("취소하기", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("돌아가기") } },
        )
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
            }
            Text("팔로우 관계", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("팔로잉 ${following.size}") })
            Tab(selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("팔로워 ${followers.size}") })
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!loading && connections.isEmpty()) item {
                Text(
                    if (selectedTab == 0) "아직 팔로우한 사용자가 없어요." else "아직 팔로워가 없어요.",
                    color = MutedMint,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                )
            }
            items(connections, key = { "${selectedTab}-${it.followedAt}-${it.displayAlias}" }) { connection ->
                MelodyCard(
                    Modifier.fillMaxWidth(),
                    onClick = { connection.profileHandle?.let(onOpenProfile) },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProfileAvatar(connection.avatarUrl, connection.displayAlias, connection.colorHex, 52.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(connection.displayAlias, fontWeight = FontWeight.Bold)
                            Text(
                                if (connection.mutual) "서로 팔로우 중" else connection.bio.ifBlank { "음악으로 연결된 사용자" },
                                color = if (connection.mutual) SignalGreen else MutedMint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (connection.relationshipId != null) {
                            TextButton(onClick = { pendingRemoval = connection }) {
                                Text(if (connection.mutual) "맞팔 취소" else "취소")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileAvatar(dataUrl: String?, name: String, colorHex: Long, size: androidx.compose.ui.unit.Dp) {
    val remoteUrl = dataUrl?.takeIf { it.startsWith("https://") }
    val bitmap = remember(dataUrl) { dataUrl?.substringAfter("base64,", "")?.takeIf(String::isNotBlank)?.let { encoded -> runCatching { BitmapFactory.decodeByteArray(Base64.decode(encoded, Base64.DEFAULT), 0, Base64.decode(encoded, Base64.DEFAULT).size) }.getOrNull() } }
    Surface(modifier = Modifier.size(size), shape = CircleShape, color = Color(colorHex)) {
        if (remoteUrl != null) AsyncImage(remoteUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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
    onSaveProfile: (Float) -> Unit,
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
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { onSaveProfile(clipStart) }, enabled = generationState.song.candidateKey != null, modifier = Modifier.fillMaxWidth()) {
                            Text("선택한 5초를 Melody ID로 저장")
                        }
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
    myCard: ExchangeMusicCard,
    exchangeState: ExchangeConnectionState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onConnect: (String) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onStop: () -> Unit,
    onClearResult: () -> Unit,
    onSync: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        BackHeader(onBack = onBack, title = "버블 모드", subtitle = "인터넷 상태와 관계없이 가까운 사람과 음악 카드를 교환해요")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                AppPanel {
                    Text("내보낼 음악 카드", color = SignalGreen, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("${myCard.trackTitle} · ${myCard.trackArtist}", style = MaterialTheme.typography.titleMedium)
                    Text(myCard.melodyAlias.ifBlank { "멜로디 별칭 없음" }, color = MutedMint)
                }
            }
            item {
                AppPanel(color = SignalGreen.copy(alpha = 0.08f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Outlined.BluetoothSearching, contentDescription = null, tint = SignalGreen)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("근처 기기 찾기", style = MaterialTheme.typography.titleMedium)
                            Text("Bluetooth와 Wi-Fi를 켜고 상대방도 이 화면을 열어 주세요", color = MutedMint)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    when (exchangeState) {
                        ExchangeConnectionState.Idle -> Button(
                            onClick = onStart,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) { Text("주변 기기 찾기") }
                        ExchangeConnectionState.Discovering -> {
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = SignalGreen)
                            Spacer(Modifier.height(10.dp))
                            Text("교환 가능한 기기를 찾고 있어요…", color = MutedMint)
                            TextButton(onClick = onStop) { Text("검색 중지") }
                        }
                        is ExchangeConnectionState.EndpointFound -> {
                            Text("${exchangeState.endpointName} 기기를 찾았어요", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { onConnect(exchangeState.endpointId) },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                            ) { Text("이 기기와 연결") }
                        }
                        is ExchangeConnectionState.AwaitingApproval -> {
                            Text("상대 기기와 아래 코드가 같은지 확인하세요", color = MutedMint)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                exchangeState.authenticationDigits,
                                color = SignalGreen,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) { Text("취소") }
                                Button(onClick = onApprove, modifier = Modifier.weight(1f)) { Text("코드 일치") }
                            }
                        }
                        is ExchangeConnectionState.Connecting,
                        is ExchangeConnectionState.Exchanging -> {
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = SignalGreen)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                if (exchangeState is ExchangeConnectionState.Connecting) "상대 기기에 연결하고 있어요…"
                                else "서명된 음악 카드를 교환하고 있어요…",
                                color = MutedMint,
                            )
                        }
                        is ExchangeConnectionState.Completed -> {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = SignalGreen)
                            Spacer(Modifier.height(8.dp))
                            Text("${exchangeState.result.peerCard.displayAlias} 님과 교환했어요", fontWeight = FontWeight.Bold)
                            Text(
                                "${exchangeState.result.peerCard.trackTitle} · ${exchangeState.result.peerCard.trackArtist}",
                                color = MutedMint,
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = onClearResult, modifier = Modifier.fillMaxWidth()) { Text("확인") }
                        }
                        is ExchangeConnectionState.Error -> {
                            Text(exchangeState.message, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text("다시 시도") }
                        }
                    }
                }
            }
            item { SectionLabel("이 기기에 저장된 기록") }
            if (records.isEmpty()) {
                item {
                    AppPanel {
                        Text("아직 교환 기록이 없어요", style = MaterialTheme.typography.titleMedium)
                        Text("가까운 기기와 인증 코드를 확인하면 받은 카드가 여기에 저장돼요.", color = MutedMint)
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (record.syncState == SyncState.SYNCED) {
                                Icon(Icons.Outlined.CloudDone, contentDescription = "동기화됨", tint = SignalGreen)
                            } else if (record.syncState == SyncState.UPLOADING) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = SignalGreen)
                            } else {
                                IconButton(onClick = { onSync(record.id) }) {
                                    Icon(Icons.Outlined.CloudUpload, contentDescription = "서버 동기화")
                                }
                            }
                            IconButton(onClick = { onDelete(record.id) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "교환 기록 삭제")
                            }
                        }
                    }
                    if (record.syncState == SyncState.SYNCED) {
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = { onOpenProfile(record.id) }) {
                            Text("교환한 사람의 음악 프로필 보기", color = SignalGreen)
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
