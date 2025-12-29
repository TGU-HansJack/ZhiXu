package com.zhixu.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ThirdPartyAuthState(
    val baseUrl: String,
    val username: String,
    val token: String,
)

class ThirdPartyAuthPreferences(
    private val context: Context,
) {
    private val baseUrlKey = stringPreferencesKey("third_party_base_url")
    private val usernameKey = stringPreferencesKey("third_party_username")
    private val tokenKey = stringPreferencesKey("third_party_token")

    val state: Flow<ThirdPartyAuthState> =
        context.dataStore.data.map { prefs ->
            ThirdPartyAuthState(
                baseUrl = prefs[baseUrlKey] ?: "",
                username = prefs[usernameKey] ?: "",
                token = prefs[tokenKey] ?: "",
            )
        }

    suspend fun set(baseUrl: String, username: String, token: String) {
        context.dataStore.edit { prefs ->
            prefs[baseUrlKey] = baseUrl
            prefs[usernameKey] = username
            prefs[tokenKey] = token
        }
    }

    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.remove(baseUrlKey)
            prefs.remove(usernameKey)
            prefs.remove(tokenKey)
        }
    }
}

