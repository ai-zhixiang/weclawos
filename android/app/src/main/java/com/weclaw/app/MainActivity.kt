package com.weclaw.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weclaw.app.data.ApiService
import com.weclaw.app.data.AuthManager
import com.weclaw.app.data.WebSocketManager
import com.weclaw.app.ui.chat.ChatScreen
import com.weclaw.app.ui.chat.ChatViewModel
import com.weclaw.app.ui.theme.WeClawTheme

class MainActivity : ComponentActivity() {

    private lateinit var auth: AuthManager
    private lateinit var api: ApiService
    private lateinit var wsManager: WebSocketManager

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { _ -> /* VM 通过 savedUri 拿到照片 */ }

    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { wsManager.onMediaSelected(it.toString(), "image") } }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { wsManager.onMediaSelected(it.toString(), "file") } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = AuthManager(applicationContext)
        api = ApiService(auth)
        wsManager = WebSocketManager(auth)

        // Try to restore session
        var isLoggedIn by mutableStateOf(false)
        LaunchedEffect(Unit) {
            isLoggedIn = auth.isLoggedIn()
            if (isLoggedIn) wsManager.connect()
        }

        setContent {
            WeClawTheme {
                val vm: ChatViewModel = viewModel(
                    factory = ChatViewModel.Factory(api, wsManager, auth, applicationContext)
                )

                ChatScreen(
                    viewModel = vm,
                    onCameraClick = { cameraLauncher.launch(vm.createTempImageUri(this)) },
                    onPhotoPickerClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onFilePickerClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    onWebViewOpen = { url -> /* WebView */ },
                )
            }
        }
    }
}
