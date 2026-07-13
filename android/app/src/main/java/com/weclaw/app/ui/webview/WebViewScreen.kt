package com.weclaw.app.ui.webview

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.weclaw.app.data.AuthManager
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    url: String,
    auth: AuthManager,
    onClose: () -> Unit,
) {
    var pageTitle by remember { mutableStateOf("") }

    // 拼接 WeClaw 参数
    val token = runBlocking { auth.getToken() }
    val finalUrl = buildString {
        append(url)
        val sep = if (url.contains("?")) "&" else "?"
        append("${sep}wc_token=$token")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pageTitle.ifBlank { "浏览" }, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "关闭")
                    }
                },
            )
        },
    ) { padding ->
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            pageTitle = view.title ?: ""
                        }

                        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                            // 拦截 weclaw:// 回调
                            if (url.startsWith("weclaw://")) {
                                handleWeClawScheme(ctx, url, auth)
                                return true
                            }
                            // 外部链接
                            if (url.startsWith("http") && !url.contains("pangoozn.com")) {
                                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                ctx.startActivity(intent)
                                return true
                            }
                            return false
                        }
                    }

                    // 注入 JS Bridge
                    addJavascriptInterface(WeClawJsBridge(auth), "WeClaw")
                    loadUrl(finalUrl)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

class WeClawJsBridge(private val auth: AuthManager) {

    @JavascriptInterface
    fun getToken(): String = runBlocking { auth.getToken() }

    @JavascriptInterface
    fun getDeviceId(): String = runBlocking { auth.deviceUuid.first() }

    @JavascriptInterface
    fun close() {
        // 由 shouldOverrideUrlLoading 处理 weclaw://close
    }

    @JavascriptInterface
    fun share(json: String) {
        // weclaw://share?title=...&desc=...&link=...&img=...
    }

    @JavascriptInterface
    fun openExternal(url: String) {
        // weclaw://open?url=...
    }

    @JavascriptInterface
    fun pay(json: String) {
        // weclaw://pay?amount=...&order_id=...
    }
}

@SuppressLint("QueryPermissionsNeeded")
private fun handleWeClawScheme(ctx: android.content.Context, url: String, auth: AuthManager) {
    val uri = android.net.Uri.parse(url)
    when (uri.host) {
        "close" -> {
            // Activity finish 由 Compose 回调处理
        }
        "open" -> {
            val target = uri.getQueryParameter("url") ?: return
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(target))
            ctx.startActivity(intent)
        }
        "share" -> {
            val title = uri.getQueryParameter("title") ?: ""
            val desc = uri.getQueryParameter("desc") ?: ""
            val link = uri.getQueryParameter("link") ?: ""
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "$title\n$desc\n$link")
            }
            ctx.startActivity(Intent.createChooser(intent, "分享"))
        }
    }
}
