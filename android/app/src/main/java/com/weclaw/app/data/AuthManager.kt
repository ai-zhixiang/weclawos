package com.weclaw.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore("weclaw_auth")

class AuthManager(private val context: Context) {

    companion object {
        private val KEY_TOKEN = stringPreferencesKey("jwt_token")
        private val KEY_PHONE = stringPreferencesKey("phone")
        private val KEY_NICKNAME = stringPreferencesKey("nickname")
        private val KEY_DEVICE_UUID = stringPreferencesKey("device_uuid")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
    }

    val token get() = context.dataStore.data.map { it[KEY_TOKEN] ?: "" }
    val phone get() = context.dataStore.data.map { it[KEY_PHONE] ?: "" }
    val nickname get() = context.dataStore.data.map { it[KEY_NICKNAME] ?: "" }
    val deviceUuid get() = context.dataStore.data.map { it[KEY_DEVICE_UUID] ?: "" }

    suspend fun getToken(): String = token.first()
    suspend fun isLoggedIn(): Boolean = token.first().isNotBlank()

    fun isLoggedInSync(): Boolean = runBlocking { isLoggedIn() }

    suspend fun saveAuth(resp: AuthResponse) {
        context.dataStore.edit {
            it[KEY_TOKEN] = resp.token
            it[KEY_PHONE] = resp.phone
            it[KEY_NICKNAME] = resp.nickname
            it[KEY_DEVICE_UUID] = resp.device_uuid
            it[KEY_USER_ID] = resp.user_id.toString()
        }
    }

    suspend fun logout() {
        context.dataStore.edit { it.clear() }
    }
}
