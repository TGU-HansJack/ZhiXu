package app.zhixu.sync

import android.content.Context
import app.zhixu.data.AccountPreferences
import app.zhixu.data.SyncPreferences
import app.zhixu.data.ThirdPartyAuthPreferences
import app.zhixu.data.ThirdPartyServiceConfig
import app.zhixu.data.VaultStorageLocation
import app.zhixu.data.VaultSyncPreferences
import kotlinx.coroutines.flow.first

data class SyncServerAuth(
    val baseUrl: String,
    val token: String,
)

suspend fun resolveSyncServerAuth(context: Context): SyncServerAuth? {
    val appContext = context.applicationContext
    val vaultSyncPrefs = VaultSyncPreferences(appContext)
    val config = vaultSyncPrefs.config.first()

    return when (config.location) {
        VaultStorageLocation.LOCAL -> {
            val enabled = SyncPreferences(appContext).officialSyncEnabled.first()
            if (!enabled) return null
            val account = AccountPreferences(appContext).state.first()
            val token = account.token
            if (token.isBlank()) return null
            SyncServerAuth(baseUrl = OfficialSync.BASE_URL, token = token)
        }
        VaultStorageLocation.OFFICIAL_SERVER -> {
            val account = AccountPreferences(appContext).state.first()
            val token = account.token
            if (token.isBlank()) return null
            SyncServerAuth(baseUrl = OfficialSync.BASE_URL, token = token)
        }
        VaultStorageLocation.THIRD_PARTY_SERVICE -> resolveThirdPartyAuth(appContext, config.thirdParty)
    }
}

private suspend fun resolveThirdPartyAuth(context: Context, cfg: ThirdPartyServiceConfig): SyncServerAuth? {
    val baseUrl = cfg.url.trim()
    val username = cfg.username.trim()
    val password = cfg.password
    if (baseUrl.isBlank() || username.isBlank() || password.isBlank()) return null

    val prefs = ThirdPartyAuthPreferences(context)
    val cached = prefs.state.first()
    if (cached.baseUrl != baseUrl || cached.username != username) {
        prefs.clear()
    }

    val token =
        prefs.state.first().token.ifBlank {
            val login = SyncServerClient.login(baseUrl, username, password)
            val t = login.value.orEmpty()
            if (login.ok && t.isNotBlank()) {
                prefs.set(baseUrl = baseUrl, username = username, token = t)
                t
            } else {
                ""
            }
        }
    if (token.isBlank()) return null
    return SyncServerAuth(baseUrl = baseUrl, token = token)
}
