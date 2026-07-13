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

        if (auth.isLoggedInSync()) wsManager.connect()

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
