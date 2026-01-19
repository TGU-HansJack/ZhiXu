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
    val email: String,
    val avatarUri: String,
    val avatarUpdatedAtMs: Long,
) {
    val isLoggedIn: Boolean get() = token.isNotBlank()
    val hasAvatar: Boolean get() = avatarUri.isNotBlank()
}

class AccountPreferences(
    private val context: Context,
) {
    private val tokenKey = stringPreferencesKey("account_token")
    private val usernameKey = stringPreferencesKey("account_username")
    private val userIdKey = longPreferencesKey("account_user_id")
    private val emailKey = stringPreferencesKey("account_email")
    private val avatarUriKey = stringPreferencesKey("account_avatar_uri")
    private val avatarUpdatedAtMsKey = longPreferencesKey("account_avatar_updated_at_ms")

    val state: Flow<AccountState> =
        context.dataStore.data.map { prefs ->
            AccountState(
                token = prefs[tokenKey] ?: "",
                username = prefs[usernameKey] ?: "",
                userId = prefs[userIdKey] ?: 0L,
                email = prefs[emailKey] ?: "",
                avatarUri = prefs[avatarUriKey] ?: "",
                avatarUpdatedAtMs = prefs[avatarUpdatedAtMsKey] ?: 0L,
            )
        }

    suspend fun setLoggedIn(
        token: String,
        username: String,
        userId: Long,
        email: String = "",
    ) {
        context.dataStore.edit { prefs ->
            prefs[tokenKey] = token
            prefs[usernameKey] = username
            prefs[userIdKey] = userId
            if (email.isNotBlank()) prefs[emailKey] = email
        }
    }

    suspend fun setEmail(email: String) {
        context.dataStore.edit { prefs ->
            if (email.isBlank()) prefs.remove(emailKey) else prefs[emailKey] = email
        }
    }

    suspend fun setAvatarUri(uri: String, updatedAtMs: Long = 0L) {
        context.dataStore.edit { prefs ->
            if (uri.isBlank()) {
                prefs.remove(avatarUriKey)
                prefs.remove(avatarUpdatedAtMsKey)
            } else {
                prefs[avatarUriKey] = uri
                if (updatedAtMs > 0L) prefs[avatarUpdatedAtMsKey] = updatedAtMs else prefs.remove(avatarUpdatedAtMsKey)
            }
        }
    }

    suspend fun logout() {
        context.dataStore.edit { prefs ->
            prefs.remove(tokenKey)
            prefs.remove(usernameKey)
            prefs.remove(userIdKey)
            prefs.remove(emailKey)
            prefs.remove(avatarUriKey)
            prefs.remove(avatarUpdatedAtMsKey)
        }
    }
}
