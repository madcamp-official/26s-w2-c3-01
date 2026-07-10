package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

data class TrackUi(
    val title: String,
    val artist: String,
    val mood: String
)

data class NearbyUserUi(
    val id: String,
    val nickname: String,
    val melodyId: String,
    val distance: Int,
    val compatibility: Int,
    val track: TrackUi,
    val x: Float,
    val y: Float,
    val color: Color,
    val isFollowing: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MeloMapApp()
        }
    }
}

@Composable
fun MeloMapApp() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF07070A),
            surface = Color(0xFF111116),
            primary = Color(0xFFB8FF5C),
            secondary = Color(0xFF9BA4FF)
        )
    ) {
        MeloMapHomeScreen()
    }
}

@Composable
fun MeloMapHomeScreen() {
    val context = LocalContext.current

    val users = remember {
        listOf(
            NearbyUserUi(
                id = "u1",
                nickname = "wave_03",
                melodyId = "C-E-G",
                distance = 8,
                compatibility = 91,
                track = TrackUi("seasons", "wave to earth", "calm indie"),
                x = 0.28f,
                y = 0.25f,
                color = Color(0xFFB8FF5C)
            ),
            NearbyUserUi(
                id = "u2",
                nickname = "midnight",
                melodyId = "A-C-E",
                distance = 13,
                compatibility = 78,
                track = TrackUi("Ditto", "NewJeans", "soft pop"),
                x = 0.72f,
                y = 0.31f,
                color = Color(0xFF9BA4FF)
            ),
            NearbyUserUi(
                id = "u3",
                nickname = "lofi.zip",
                melodyId = "D-F-A",
                distance = 18,
                compatibility = 84,
                track = TrackUi("Like I Need U", "keshi", "r&b"),
                x = 0.20f,
                y = 0.62f,
                color = Color(0xFFFFC978)
            ),
            NearbyUserUi(
                id = "u4",
                nickname = "bluehour",
                melodyId = "G-B-D",
                distance = 21,
                compatibility = 69,
                track = TrackUi("Snooze", "SZA", "late night"),
                x = 0.80f,
                y = 0.70f,
                color = Color(0xFFFF8DB3)
            ),
            NearbyUserUi(
                id = "u5",
                nickname = "echo.7",
                melodyId = "E-G-B",
                distance = 16,
                compatibility = 88,
                track = TrackUi("Bad", "wave to earth", "dreamy"),
                x = 0.55f,
                y = 0.54f,
                color = Color(0xFF7DE7FF)
            )
        )
    }

    var selectedUser by remember { mutableStateOf(users.first()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF1B1B24),
                        Color(0xFF08080C),
                        Color(0xFF030305)
                    ),
                    center = Offset(250f, 150f),
                    radius = 900f
                )
            )
    ) {
        BackgroundGlow()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp)
                .padding(top = 48.dp, bottom = 18.dp)
        ) {
            TopHeader()

            Spacer(modifier = Modifier.height(18.dp))

            FilterRow()

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(36.dp))
                    .background(Color.White.copy(alpha = 0.045f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(36.dp)
                    )
            ) {
                BubbleMap(
                    users = users,
                    selectedUser = selectedUser,
                    onUserClick = { selectedUser = it }
                )

                Text(
                    text = "Approximate mode · live nearby",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 18.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            NowPlayingCard(
                user = selectedUser,
                onOpenMusic = {
                    val query = "${selectedUser.track.title} ${selectedUser.track.artist}"
                    val uri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            BottomNavMock()
        }
    }
}

@Composable
fun TopHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "MeloMap",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "24 listeners around you",
                color = Color.White.copy(alpha = 0.48f),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
fun FilterRow() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilterChipMock("For you", selected = true)
        FilterChipMock("Nearby")
        FilterChipMock("Calm")
        FilterChipMock("R&B")
    }
}

@Composable
fun FilterChipMock(
    text: String,
    selected: Boolean = false
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) Color(0xFFB8FF5C)
                else Color.White.copy(alpha = 0.07f)
            )
            .border(
                width = 1.dp,
                color = if (selected) Color.Transparent else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 15.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (selected) Color.Black else Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun BubbleMap(
    users: List<NearbyUserUi>,
    selectedUser: NearbyUserUi,
    onUserClick: (NearbyUserUi) -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "bubbleMotion")
    val pulse by infinite.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(width / 2f, height / 2f)

            drawCircle(
                color = Color.White.copy(alpha = 0.04f),
                radius = size.minDimension * 0.34f,
                center = center,
                style = Stroke(width = 1.5f)
            )

            drawCircle(
                color = Color.White.copy(alpha = 0.025f),
                radius = size.minDimension * 0.22f,
                center = center,
                style = Stroke(width = 1.5f)
            )

            drawCircle(
                color = Color(0xFFB8FF5C).copy(alpha = 0.13f),
                radius = 48f * pulse,
                center = center
            )
        }

        Box(
            modifier = Modifier
                .size(74.dp)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.11f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Waves,
                    contentDescription = null,
                    tint = Color(0xFFB8FF5C)
                )
                Text(
                    text = "YOU",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        users.forEachIndexed { index, user ->
            val offsetAnim by infinite.animateFloat(
                initialValue = -5f,
                targetValue = 5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 1600 + index * 180,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "floating$index"
            )

            val isSelected = user.id == selectedUser.id

            Box(
                modifier = Modifier
                    .offset(
                        x = (maxWidth * user.x) - 34.dp,
                        y = (maxHeight * user.y) - 34.dp + offsetAnim.dp
                    )
                    .size(if (isSelected) 78.dp else 64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                user.color.copy(alpha = if (isSelected) 0.95f else 0.72f),
                                user.color.copy(alpha = 0.24f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) Color.White.copy(alpha = 0.75f)
                        else Color.White.copy(alpha = 0.16f),
                        shape = CircleShape
                    )
                    .clickable { onUserClick(user) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "♪",
                        color = Color.Black.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "${user.distance}m",
                        color = Color.Black.copy(alpha = 0.62f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun NowPlayingCard(
    user: NearbyUserUi,
    onOpenMusic: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(28.dp)
            )
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                user.color,
                                Color.White.copy(alpha = 0.12f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = null,
                    tint = Color.Black.copy(alpha = 0.72f)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.nickname,
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Melody ID · ${user.melodyId}",
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFB8FF5C))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${user.compatibility}%",
                    color = Color.Black,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Now playing",
            color = Color.White.copy(alpha = 0.42f),
            style = MaterialTheme.typography.labelMedium
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = user.track.title,
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "${user.track.artist} · ${user.track.mood}",
            color = Color.White.copy(alpha = 0.56f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onOpenMusic,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Search")
            }

            OutlinedButton(
                onClick = {},
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = SolidColor(Color.White.copy(alpha = 0.18f))
                )
            ) {
                Text("Follow")
            }
        }
    }
}

@Composable
fun BottomNavMock() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .border(
                1.dp,
                Color.White.copy(alpha = 0.09f),
                RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        BottomNavItem("Map", selected = true)
        BottomNavItem("Chat")
        BottomNavItem("Me")
    }
}

@Composable
fun BottomNavItem(
    text: String,
    selected: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = when (text) {
                "Chat" -> Icons.Outlined.ChatBubbleOutline
                "Me" -> Icons.Outlined.PersonOutline
                else -> Icons.Outlined.Waves
            },
            contentDescription = null,
            tint = if (selected) Color(0xFFB8FF5C) else Color.White.copy(alpha = 0.48f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = text,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.42f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun BackgroundGlow() {
    Box(
        modifier = Modifier
            .offset(x = (-80).dp, y = 80.dp)
            .size(240.dp)
            .blur(70.dp)
            .background(Color(0xFFB8FF5C).copy(alpha = 0.18f), CircleShape)
    )

    Box(
        modifier = Modifier
            .offset(x = 240.dp, y = 240.dp)
            .size(260.dp)
            .blur(80.dp)
            .background(Color(0xFF9BA4FF).copy(alpha = 0.16f), CircleShape)
    )
}