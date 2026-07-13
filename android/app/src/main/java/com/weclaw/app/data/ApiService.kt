package com.weclaw.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class ApiService(private val auth: AuthManager) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = "https://hai.pangoozn.com"

    // ── Auth ──

    suspend fun register(phone: String): Result<AuthResponse> = apiCall(
        Request.Builder()
            .url("$baseUrl/api/auth/register")
            .post("""{"phone":"$phone","code":"000000"}""".toRequestBody(JSON))
    )

    suspend fun login(phone: String): Result<AuthResponse> = apiCall(
        Request.Builder()
            .url("$baseUrl/api/auth/login")
            .post("""{"phone":"$phone","code":"000000"}""".toRequestBody(JSON))
    )

    // ── User ──

    suspend fun getUserInfo(): Result<UserInfo> = authApiCall(
        Request.Builder().url("$baseUrl/api/auth/weclaw-userinfo").get()
    )

    // ── Chat ──

    suspend fun sendMessage(message: String): Result<ChatResponse> = authApiCall(
        Request.Builder()
            .url("$baseUrl/api/chat/send")
            .post("""{"message":"${message.replace("\"", "\\\"")}"}""".toRequestBody(JSON))
    )

    // ── Subscription ──

    suspend fun getSubscriptionInfo(): Result<SubscriptionInfoResponse> = authApiCall(
        Request.Builder().url("$baseUrl/api/subscription/info").get()
    )

    suspend fun getPlans(): Result<PlansResponse> = apiCall(
        Request.Builder().url("$baseUrl/api/subscription/plans").get()
    )

    // ── Helpers ──

    private suspend inline fun <reified T> authApiCall(request: Request.Builder): Result<T> {
        val token = auth.getToken()
        request.header("Authorization", "Bearer $token")
        return apiCall(request)
    }

    private suspend inline fun <reified T> apiCall(request: Request.Builder): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request.build()).execute()
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("${response.code}: $body"))
                }
                val data = appJson.decodeFromString<T>(body)
                Result.success(data)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
