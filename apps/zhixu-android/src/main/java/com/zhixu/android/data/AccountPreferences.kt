package com.zhixu.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

data class AccountState(
    val token: String,
    val username: String,
    val userId: Long,
    val deviceId: String,
) {
    val isLoggedIn: Boolean get() = token.isNotBlank()
}

class AccountPreferences(
    private val context: Context,
) {
    private val tokenKey = stringPreferencesKey("account_token")
    private val usernameKey = stringPreferencesKey("account_username")
    private val userIdKey = longPreferencesKey("account_user_id")
    private val deviceIdKey = stringPreferencesKey("device_id")

    val state: Flow<AccountState> =
        context.dataStore.data.map { prefs ->
            AccountState(
                token = prefs[tokenKey] ?: "",
                username = prefs[usernameKey] ?: "",
                userId = prefs[userIdKey] ?: 0L,
                deviceId = prefs[deviceIdKey] ?: "",
            )
        }

    suspend fun ensureDeviceId(): String =
        withContext(Dispatchers.IO) {
            val current = state.first().deviceId
            if (current.isNotBlank()) return@withContext current
            val id = "android-" + UUID.randomUUID().toString()
            context.dataStore.edit { it[deviceIdKey] = id }
            id
        }

    suspend fun setDeviceId(deviceId: String) {
        context.dataStore.edit { it[deviceIdKey] = deviceId.trim() }
    }

    suspend fun setLoggedIn(
        token: String,
        username: String,
        userId: Long,
    ) {
        context.dataStore.edit { prefs ->
            prefs[tokenKey] = token
            prefs[usernameKey] = username
            prefs[userIdKey] = userId
        }
    }

    suspend fun logout() {
        context.dataStore.edit { prefs ->
            prefs.remove(tokenKey)
            prefs.remove(usernameKey)
            prefs.remove(userIdKey)
        }
    }
}
