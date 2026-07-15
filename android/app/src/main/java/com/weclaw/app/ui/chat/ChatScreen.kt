package com.weclaw.app.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.weclaw.app.ui.theme.WeClawColors

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onCameraClick: () -> Unit,
    onPhotoPickerClick: () -> Unit,
    onFilePickerClick: () -> Unit,
    onWebViewOpen: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WeClawColors.background)
            .clickable { /* dismiss keyboard */ }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── 顶部状态 ──
            StatusBar(isConnected = state.isConnected)

            // ── 消息区域 ──
            if (state.messages.isEmpty()) {
                // 待机态：打开即聊
                Spacer(Modifier.weight(0.35f))
                ClockDisplay()
                Spacer(Modifier.height(32.dp))
                if (state.isListening) {
                    WaveformAnimation()
                } else {
                    Text("请说话", color = WeClawColors.textTertiary, fontSize = 14.sp)
                }
                Spacer(Modifier.weight(0.3f))
            } else {
                // 对话态：消息列表
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.messages, key = { "${it.role}-${it.content.hashCode()}-${it.isPending}" }) { msg ->
                        MessageBubble(
                            message = msg,
                            onActionClick = { action ->
                                when (action.action) {
                                    "pick_photo" -> onPhotoPickerClick()
                                    "pick_file" -> onFilePickerClick()
                                    "open_url" -> action.data.let { onWebViewOpen(it) }
                                }
                            },
                        )
                    }
                }

                // 底部麦克风（对话态缩小版）
                Spacer(Modifier.height(4.dp))
                MiniMicButton(onClick = { /* start listening */ })
                Spacer(Modifier.height(12.dp))
            }
        }

        // ── 底部文字输入（始终可见） ──
        TextInputBar(
            inputText = state.inputText,
            onInputChange = viewModel::onInputChange,
            onSend = { viewModel.sendMessage() },
            onDismiss = { },
        )
    }
}

// ── 状态栏 ──
@Composable
private fun StatusBar(isConnected: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "WeClaw",
            color = WeClawColors.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (isConnected) WeClawColors.success else WeClawColors.textTertiary)
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (isConnected) "AI" else "离线",
            color = WeClawColors.textSecondary,
            fontSize = 13.sp,
        )
    }
}

// ── 大时钟（待机态） ──
@Composable
private fun ClockDisplay() {
    val time = remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val cal = java.util.Calendar.getInstance()
            time.value = String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
            kotlinx.coroutines.delay(10000)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            time.value,
            fontSize = 80.sp,
            fontWeight = FontWeight.Light,
            color = WeClawColors.textPrimary,
            letterSpacing = 4.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            java.text.SimpleDateFormat("yyyy年M月d日 · EEEE", java.util.Locale.CHINESE).format(java.util.Date()),
            fontSize = 14.sp,
            color = WeClawColors.textTertiary,
        )
    }
}

// ── 大麦克风按钮（待机态） ──
@Composable
private fun MicButton(isListening: Boolean, onClick: () -> Unit) {
    val borderColor = if (isListening) WeClawColors.accent else WeClawColors.textTertiary.copy(alpha = 0.3f)
    Surface(
        modifier = Modifier.size(72.dp),
        shape = CircleShape,
        color = WeClawColors.surface,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("🎤", fontSize = 28.sp)
        }
    }
}

// ── 小麦克风（对话态） ──
@Composable
private fun MiniMicButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = WeClawColors.primary.copy(alpha = 0.15f),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("🎤", fontSize = 18.sp)
        }
    }
}

// ── 聆听波形动画 ──
@Composable
private fun WaveformAnimation() {
    val infiniteTransition = rememberInfiniteTransition()
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(5) { index ->
            val delay = index * 0.15f
            val animatedHeight by infiniteTransition.animateFloat(
                initialValue = 8f,
                targetValue = 32f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = (delay * 1000).toInt()),
                    repeatMode = RepeatMode.Reverse,
                ),
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(animatedHeight.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(WeClawColors.accent.copy(alpha = 0.8f))
            )
        }
    }
}

// ── 消息气泡 ──
@Composable
private fun MessageBubble(
    message: ChatMessage,
    onActionClick: (ActionButton) -> Unit,
) {
    val isUser = message.role == "user"
    val align = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalAlignment = align,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = if (isUser) 16.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp,
            ),
            color = if (isUser) WeClawColors.userBubble else WeClawColors.surface,
            shadowElevation = 0.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                if (message.isPending) {
                    Text("···", color = WeClawColors.textSecondary, fontSize = 18.sp)
                } else {
                    Text(
                        message.content,
                        color = WeClawColors.textPrimary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    )
                    message.actions.forEach { action ->
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            modifier = Modifier.clickable { onActionClick(action) },
                            shape = RoundedCornerShape(8.dp),
                            color = WeClawColors.primary.copy(alpha = 0.15f),
                        ) {
                            Text(
                                action.label,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = WeClawColors.primary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── 隐藏式文字输入 ──
@Composable
private fun TextInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        color = WeClawColors.inputBg,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = WeClawColors.textPrimary, fontSize = 16.sp),
                cursorBrush = SolidColor(WeClawColors.primary),
                decorationBox = { innerTextField ->
                    Box {
                        if (inputText.isEmpty()) Text("输入文字...", color = WeClawColors.textTertiary, fontSize = 16.sp)
                        innerTextField()
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            if (inputText.isNotBlank()) {
                Text(
                    "发送",
                    color = WeClawColors.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onSend() },
                )
            } else {
                Text(
                    "⏺",
                    color = WeClawColors.textSecondary,
                    modifier = Modifier
                        .clickable { onDismiss() } // placeholder - TODO: wire to recorder
                        .padding(horizontal = 8.dp),
                )
            }
            Text(
                "取消",
                color = WeClawColors.textSecondary,
                modifier = Modifier.padding(start = 12.dp).clickable { onDismiss() },
            )
        }
    }
}
