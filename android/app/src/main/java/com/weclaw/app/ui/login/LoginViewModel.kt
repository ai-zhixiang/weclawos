package com.weclaw.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weclaw.app.data.ApiService
import com.weclaw.app.data.AuthManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LoginUiState(
    val phone: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

class LoginViewModel(
    private val api: ApiService,
    private val auth: AuthManager,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state

    fun onPhoneChange(phone: String) {
        _state.update { it.copy(phone = phone.take(11), error = null) }
    }

    fun submit() {
        val phone = _state.value.phone.trim()
        if (phone.length != 11) {
            _state.update { it.copy(error = "请输入 11 位手机号") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            // 先尝试登录，失败则注册
            val result = api.login(phone).onFailure {
                // 登录失败，尝试注册
            }

            val finalResult = if (result.isSuccess) result
            else api.register(phone)

            finalResult.onSuccess { resp ->
                auth.saveAuth(resp)
                _state.update { it.copy(isLoading = false, success = true) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message ?: "网络错误") }
            }
        }
    }
}
