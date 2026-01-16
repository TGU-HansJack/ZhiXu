package app.zhixu.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class WebDavConfig(
    val enabled: Boolean,
    val baseUrl: String,
    val username: String,
    val password: String,
    val remoteRoot: String,
    val includeIndexSqlite: Boolean,
    val conflictStrategy: WebDavConflictStrategy,
)

enum class WebDavConflictStrategy {
    KEEP_BOTH,
    LOCAL_WINS,
    REMOTE_WINS,
    ASK_EACH_TIME,
    ;

    companion object {
        fun fromRaw(raw: String?): WebDavConflictStrategy {
            val normalized = raw.orEmpty().trim().uppercase()
            return entries.firstOrNull { it.name == normalized } ?: KEEP_BOTH
        }
    }
}

class SyncPreferences(
    private val context: Context,
) {
    private val webdavEnabledKey = booleanPreferencesKey("webdav_enabled")
    private val webdavBaseUrlKey = stringPreferencesKey("webdav_base_url")
    private val webdavUsernameKey = stringPreferencesKey("webdav_username")
    private val webdavPasswordKey = stringPreferencesKey("webdav_password")
    private val webdavRemoteRootKey = stringPreferencesKey("webdav_remote_root")
    private val includeIndexSqliteKey = booleanPreferencesKey("sync_include_index_sqlite")
    private val webdavConflictStrategyKey = stringPreferencesKey("webdav_conflict_strategy")

    val webDavConfig: Flow<WebDavConfig> =
        context.dataStore.data
            .map { prefs ->
                WebDavConfig(
                    enabled = prefs[webdavEnabledKey] ?: false,
                    baseUrl = prefs[webdavBaseUrlKey] ?: "",
                    username = prefs[webdavUsernameKey] ?: "",
                    password = prefs[webdavPasswordKey] ?: "",
                    remoteRoot = prefs[webdavRemoteRootKey] ?: "/",
                    includeIndexSqlite = prefs[includeIndexSqliteKey] ?: true,
                    conflictStrategy = WebDavConflictStrategy.fromRaw(prefs[webdavConflictStrategyKey]),
                )
            }

    val webDavEnabled: Flow<Boolean> = context.dataStore.data.map { it[webdavEnabledKey] ?: false }
    val includeIndexSqlite: Flow<Boolean> = context.dataStore.data.map { it[includeIndexSqliteKey] ?: true }

    suspend fun setWebDavEnabled(enabled: Boolean) {
        context.dataStore.edit { it[webdavEnabledKey] = enabled }
    }

    suspend fun setIncludeIndexSqlite(include: Boolean) {
        context.dataStore.edit { it[includeIndexSqliteKey] = include }
    }

    suspend fun saveWebDavConfig(config: WebDavConfig) {
        context.dataStore.edit { prefs ->
            prefs[webdavEnabledKey] = config.enabled
            prefs[webdavBaseUrlKey] = config.baseUrl
            prefs[webdavUsernameKey] = config.username
            prefs[webdavPasswordKey] = config.password
            prefs[webdavRemoteRootKey] = config.remoteRoot
            prefs[includeIndexSqliteKey] = config.includeIndexSqlite
            prefs[webdavConflictStrategyKey] = config.conflictStrategy.name
        }
    }
}
