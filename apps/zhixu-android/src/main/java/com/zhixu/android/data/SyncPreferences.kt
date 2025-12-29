package com.zhixu.android.data

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
)

class SyncPreferences(
    private val context: Context,
) {
    private val webdavEnabledKey = booleanPreferencesKey("webdav_enabled")
    private val webdavBaseUrlKey = stringPreferencesKey("webdav_base_url")
    private val webdavUsernameKey = stringPreferencesKey("webdav_username")
    private val webdavPasswordKey = stringPreferencesKey("webdav_password")
    private val webdavRemoteRootKey = stringPreferencesKey("webdav_remote_root")
    private val includeIndexSqliteKey = booleanPreferencesKey("sync_include_index_sqlite")

    val webDavConfig: Flow<WebDavConfig> =
        context.dataStore.data
            .map { prefs ->
                WebDavConfig(
                    enabled = prefs[webdavEnabledKey] ?: false,
                    baseUrl = prefs[webdavBaseUrlKey] ?: "",
                    username = prefs[webdavUsernameKey] ?: "",
                    password = prefs[webdavPasswordKey] ?: "",
                    remoteRoot = prefs[webdavRemoteRootKey] ?: "/",
                    includeIndexSqlite = prefs[includeIndexSqliteKey] ?: false,
                )
            }

    val webDavEnabled: Flow<Boolean> = context.dataStore.data.map { it[webdavEnabledKey] ?: false }
    val includeIndexSqlite: Flow<Boolean> = context.dataStore.data.map { it[includeIndexSqliteKey] ?: false }

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
        }
    }
}
