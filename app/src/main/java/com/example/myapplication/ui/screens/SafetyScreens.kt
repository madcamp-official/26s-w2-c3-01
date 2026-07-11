package com.example.myapplication.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ReportGmailerrorred
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.core.model.BlockedUser
import com.example.myapplication.core.model.NearbyListener
import com.example.myapplication.core.model.ReportReason
import com.example.myapplication.ui.components.MelodyBubbleColors
import com.example.myapplication.ui.components.MelodyCard

@Composable
fun ReportUserScreen(
    listener: NearbyListener,
    onBack: () -> Unit,
    onSubmit: (ReportReason, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf<ReportReason?>(null) }
    var description by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SafetyHeader("신고하기", onBack) }
        item {
            Text(
                "${listener.displayAlias} 님을 신고하는 이유를 선택해 주세요.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "신고 내용은 상대에게 공개되지 않으며 서버 운영 검토에만 사용돼요.",
                color = MelodyBubbleColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        items(ReportReason.entries, key = ReportReason::name) { reason ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { selected = reason },
                shape = RoundedCornerShape(16.dp),
                color = MelodyBubbleColors.Surface,
                border = BorderStroke(
                    1.dp,
                    if (selected == reason) MelodyBubbleColors.Primary else MelodyBubbleColors.Border,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == reason, onClick = { selected = reason })
                    Text(reason.label, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(500) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("추가 설명 (선택)") },
                supportingText = { Text("${description.length}/500") },
                minLines = 3,
            )
        }
        item {
            Button(
                onClick = {
                    selected?.let { reason -> onSubmit(reason, description.trim().ifBlank { null }) }
                },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Outlined.ReportGmailerrorred, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("신고 제출", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun BlockedUsersScreen(
    users: List<BlockedUser>,
    onBack: () -> Unit,
    onUnblock: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SafetyHeader("차단 사용자", onBack) }
        if (users.isEmpty()) {
            item {
                MelodyCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Block, contentDescription = null, tint = MelodyBubbleColors.TextMuted)
                        Spacer(Modifier.height(10.dp))
                        Text("차단한 사용자가 없어요", fontWeight = FontWeight.Bold)
                        Text("차단하면 서로의 주변 목록과 대화방에서 숨겨져요.", color = MelodyBubbleColors.TextMuted)
                    }
                }
            }
        } else {
            items(users, key = BlockedUser::blockId) { user ->
                MelodyCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(44.dp), shape = CircleShape, color = Color(user.colorHex)) {}
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(user.displayAlias, fontWeight = FontWeight.Bold)
                            Text("서로의 주변 목록에서 숨김", color = MelodyBubbleColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onUnblock(user.blockId) }) { Text("차단 해제") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SafetyHeader(title: String, onBack: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로")
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        }
        HorizontalDivider(color = MelodyBubbleColors.Border)
    }
}
