package app.zhixu.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AccountState(
    val token: String,
    val username: String,
    val userId: Long,
) {
    val isLoggedIn: Boolean get() = token.isNotBlank()
}

class AccountPreferences(
    private val context: Context,
) {
    private val tokenKey = stringPreferencesKey("account_token")
    private val usernameKey = stringPreferencesKey("account_username")
    private val userIdKey = longPreferencesKey("account_user_id")

    val state: Flow<AccountState> =
        context.dataStore.data.map { prefs ->
            AccountState(
                token = prefs[tokenKey] ?: "",
                username = prefs[usernameKey] ?: "",
                userId = prefs[userIdKey] ?: 0L,
            )
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
