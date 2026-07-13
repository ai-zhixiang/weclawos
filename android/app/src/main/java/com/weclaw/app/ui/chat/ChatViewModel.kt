package com.weclaw.app.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.weclaw.app.data.ApiService
import com.weclaw.app.data.AuthManager
import com.weclaw.app.data.WebSocketManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ActionButton(
    val label: String,
    val action: String,   // "pick_photo" | "pick_file" | "open_url" | "share"
    val data: String = "",
)

data class ChatMessage(
    val role: String,        // "user" | "assistant"
    val content: String,
    val actions: List<ActionButton> = emptyList(),
    val isPending: Boolean = false,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isConnected: Boolean = false,
    val isListening: Boolean = true,
    val partialText: String = "",
    val error: String? = null,
)

class ChatViewModel(
    private val api: ApiService,
    private val wsManager: WebSocketManager,
    private val auth: AuthManager,
    private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state
    private val voiceManager = VoiceManager(context)

    init {
        // 监听连接状态
        viewModelScope.launch {
            wsManager.connected.collect { connected ->
                _state.update { it.copy(isConnected = connected) }
            }
        }

        // WebSocket 消息监听
        viewModelScope.launch {
            wsManager.events.collect { msg ->
                when {
                    msg.error != null -> {
                        _state.update { it.copy(error = msg.error) }
                        _state.update { s -> s.copy(messages = s.messages.filterNot { it.isPending }) }
                    }
                    msg.content != null -> {
                        val (text, actions) = parseActions(msg.content!!)
                        _state.update { s ->
                            s.copy(
                                messages = s.messages.filterNot { it.isPending } +
                                    ChatMessage(role = "assistant", content = text, actions = actions)
                            )
                        }
                    }
                }
            }
        }

        // 语音管理
        viewModelScope.launch {
            voiceManager.state.collect { vState ->
                _state.update { it.copy(isListening = vState == ListeningState.LISTENING) }
            }
        }
        viewModelScope.launch {
            voiceManager.partialText.collect { text ->
                _state.update { it.copy(partialText = text) }
            }
        }

        // 自动启动语音监听
        if (auth.isLoggedIn()) {
            wsManager.connect()
            startListening()
        }
    }

    /** 解析 AI 回复中的操作标记，提取 ActionButton */
    private fun parseActions(text: String): Pair<String, List<ActionButton>> {
        val actions = mutableListOf<ActionButton>()
        val cleaned = text.replace(Regex("""\[action:(\w+)(?::(.+?))?\]""")) { match ->
            val type = match.groupValues[1]
            val data = match.groupValues.getOrElse(2) { "" }
            val label = when (type) {
                "pick_photo" -> "📷 选择照片"
                "pick_file"  -> "📄 选择文件"
                "open_url"   -> "🔗 打开链接"
                "share"      -> "📤 分享"
                else -> "执行"
            }
            actions.add(ActionButton(label, type, data))
            "" // 标记从原文移除
        }
        return cleaned.trim() to actions
    }

    fun onInputChange(text: String) {
        _state.update { it.copy(inputText = text, error = null) }
    }

    fun sendMessage() {
        val text = _state.value.inputText.trim()
        if (text.isBlank()) return

        val userMsg = ChatMessage(role = "user", content = text)
        val pendingMsg = ChatMessage(role = "assistant", content = "思考中...", isPending = true)
        _state.update { s ->
            s.copy(messages = s.messages + userMsg + pendingMsg, inputText = "")
        }
        wsManager.send(text)
        startListening()
    }

    fun sendVoiceMessage(text: String) {
        if (text.isBlank()) return
        val userMsg = ChatMessage(role = "user", content = text)
        val pendingMsg = ChatMessage(role = "assistant", content = "思考中...", isPending = true)
        _state.update { s ->
            s.copy(messages = s.messages + userMsg + pendingMsg)
        }
        wsManager.send(text)
    }

    private fun startListening() {
        voiceManager.startListening { text ->
            sendVoiceMessage(text)
        }
    }

    fun onMediaSelected(uri: String, type: String) {
        val text = if (type == "image") "[图片已选择: $uri]" else "[文件已选择: $uri]"
        val userMsg = ChatMessage(role = "user", content = text)
        val pendingMsg = ChatMessage(role = "assistant", content = "处理中...", isPending = true)
        _state.update { s ->
            s.copy(messages = s.messages + userMsg + pendingMsg)
        }
        wsManager.send("用户选择了文件: $uri 类型: $type，请处理")
    }

    fun toggleAttachmentSheet() {
        _state.update { it.copy(showAttachmentSheet = !it.showAttachmentSheet) }
    }

    fun dismissAttachmentSheet() {
        _state.update { it.copy(showAttachmentSheet = false) }
    }

    /** 创建临时拍照 URI */
    fun createTempImageUri(): Uri = Uri.parse("file:///tmp/weclaw_camera_${System.currentTimeMillis()}.jpg")

    override fun onCleared() {
        wsManager.disconnect()
    }

    class Factory(
        private val api: ApiService,
        private val wsManager: WebSocketManager,
        private val auth: AuthManager,
        private val context: Context,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatViewModel(api, wsManager, auth, context) as T
    }
}
