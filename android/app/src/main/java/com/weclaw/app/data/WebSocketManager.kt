package com.weclaw.app.data

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import okhttp3.*

class WebSocketManager(private val auth: AuthManager) {

    private val client = OkHttpClient.Builder()
        .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()

    private var ws: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _events = Channel<WsReply>(Channel.BUFFERED)
    val events: Flow<WsReply> = _events.receiveAsFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    fun connect() {
        scope.launch {
            val token = auth.getToken()
            if (token.isBlank()) return@launch

            val request = Request.Builder()
                .url("wss://hai.pangoozn.com/api/chat/ws?token=$token")
                .build()

            ws = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    _connected.value = true
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    try {
                        val msg = appJson.decodeFromString<WsReply>(text)
                        _events.trySend(msg)
                    } catch (_: Exception) {}
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    _connected.value = false
                    // 5 秒后重连
                    scope.launch {
                        delay(5000)
                        connect()
                    }
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    _connected.value = false
                }
            })
        }
    }

    fun send(message: String) {
        ws?.send("""{"message":"${message.replace("\"", "\\\"")}"}""")
    }

    fun onMediaSelected(uri: String, type: String) {
        ws?.send("""{"message":"上传文件: $uri","file_uri":"$uri","file_type":"$type"}""")
    }

    fun disconnect() {
        ws?.close(1000, "user_close")
        scope.cancel()
    }
}
