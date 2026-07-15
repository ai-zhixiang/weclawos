package com.weclaw.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weclaw.app.data.ApiService
import com.weclaw.app.data.AuthManager
import com.weclaw.app.data.WebSocketManager
import com.weclaw.app.ui.chat.ChatScreen
import com.weclaw.app.ui.chat.ChatViewModel
import com.weclaw.app.ui.theme.WeClawTheme
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var auth: AuthManager
    private lateinit var api: ApiService
    private lateinit var wsManager: WebSocketManager
    private var tempCameraUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = AuthManager(applicationContext)
        api = ApiService(auth)
        wsManager = WebSocketManager(auth)

        // 自动登录：用设备 UUID 作为手机号
        if (!auth.isLoggedInSync()) {
            val deviceId = UUID.randomUUID().toString().take(11).replace("-", "9")
            CoroutineScope(Dispatchers.IO).launch {
                api.login(deviceId).onSuccess { resp ->
                    auth.saveAuth(resp)
                    wsManager.connect()
                }
            }
        } else {
            wsManager.connect()
        }

        setContent {
            WeClawTheme {
                val vm: ChatViewModel = viewModel(
                    factory = ChatViewModel.Factory(api, wsManager, auth, applicationContext)
                )

                ChatScreen(
                    viewModel = vm,
                    onCameraClick = {
                        val file = File(cacheDir, "camera_${System.currentTimeMillis()}.jpg")
                        tempCameraUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
                        cameraLauncher.launch(tempCameraUri!!)
                    },
                    onPhotoPickerClick = {
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onFilePickerClick = {
                        filePickerLauncher.launch(arrayOf("*/*"))
                    },
                    onWebViewOpen = { },
                )
            }
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { _ ->
        tempCameraUri?.let { uri ->
            wsManager.onMediaSelected(uri.toString(), "image")
        }
    }

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { wsManager.onMediaSelected(it.toString(), "image") } }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { wsManager.onMediaSelected(it.toString(), "file") } }
}
