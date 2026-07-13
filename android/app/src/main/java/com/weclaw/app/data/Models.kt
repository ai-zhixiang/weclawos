package com.weclaw.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ── 数据模型 ──

@Serializable
data class AuthResponse(
    val token: String,
    val user_id: Int,
    val phone: String,
    val nickname: String,
    val device_uuid: String,
)

@Serializable
data class UserInfo(
    val user_id: Int,
    val phone: String,
    val nickname: String,
    val avatar_url: String,
    val device_uuid: String,
    val subscription: Subscription,
)

@Serializable
data class Subscription(
    val plan: String,
    val messages_limit: Int,
    val messages_used: Int,
    val cloud_space_mb: Int,
    val cloud_used_mb: Int,
    val hicards_limit: Int,
    val hicards_used: Int,
    val expires_at: String = "",
)

@Serializable
data class ChatResponse(
    val reply: String,
    val role: String,
)

@Serializable
data class PlanInfo(
    val name: String,
    val price: Double,
    val messages: Int,
    val cloud_mb: Int,
    val hicards: Int,
)

@Serializable
data class PlansResponse(
    val plans: Map<String, PlanInfo>,
)

@Serializable
data class SubscriptionInfoResponse(
    val plan: String,
    val plan_name: String,
    val messages: UsageInfo,
    val storage: StorageInfo,
    val hicards: UsageInfo,
    val expires_at: String,
)

@Serializable
data class UsageInfo(
    val limit: String,
    val used: Int,
)

@Serializable
data class StorageInfo(
    val limit_mb: Int,
    val used_mb: Int,
)

@Serializable
data class WsMessage(val message: String)

@Serializable
data class WsReply(
    val status: String? = null,
    val role: String? = null,
    val content: String? = null,
    val done: Boolean? = null,
    val error: String? = null,
)

val appJson = Json { ignoreUnknownKeys = true; isLenient = true }
