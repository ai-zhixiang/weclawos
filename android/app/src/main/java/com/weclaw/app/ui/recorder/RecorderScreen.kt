package com.weclaw.app.ui.recorder

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.weclaw.app.ui.theme.WeClawColors

@Composable
fun RecorderScreen(
    viewModel: RecorderViewModel,
    onTranscriptResult: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.state) {
        if (state.state == RecorderState.DONE && state.transcript.isNotBlank()) {
            onTranscriptResult(state.transcript)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WeClawColors.background)
            .clickable(enabled = false, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (state.state == RecorderState.RECORDING) {
                // 录音中
                Text(
                    formatTime(state.elapsedSeconds),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Light,
                    color = WeClawColors.textPrimary,
                    letterSpacing = 4.sp,
                )
                Spacer(Modifier.height(16.dp))

                // 实时转写
                if (state.partialText.isNotBlank()) {
                    Text(
                        state.partialText,
                        fontSize = 18.sp,
                        color = WeClawColors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }

                Spacer(Modifier.height(40.dp))

                // 停止按钮
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = WeClawColors.error.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(2.dp, WeClawColors.error),
                    onClick = { viewModel.stopRecording() },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("⏹", fontSize = 32.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("点击停止录音", color = WeClawColors.textTertiary, fontSize = 13.sp)
            } else if (state.state == RecorderState.TRANSCRIBING) {
                Text("正在转写...", color = WeClawColors.textSecondary, fontSize = 18.sp)
            } else if (state.state == RecorderState.IDLE) {
                // 录制前
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = WeClawColors.primary.copy(alpha = 0.15f),
                    onClick = { viewModel.startRecording(onTranscriptResult) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🎤", fontSize = 32.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("点击开始录音", color = WeClawColors.textTertiary, fontSize = 13.sp)
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}
