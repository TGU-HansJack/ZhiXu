package app.zhixu.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

data class WebDavAutomationSettings(
    val intervalMinutes: Int,
    val retryCount: Int,
    val retryIntervalSeconds: Int,
) {
    companion object {
        // Conservative defaults: reduce surprise battery/network usage while still being useful.
        val DEFAULT = WebDavAutomationSettings(intervalMinutes = 15, retryCount = 2, retryIntervalSeconds = 30)
    }
}

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
    private val webdavPairNameKey = stringPreferencesKey("webdav_pair_name")
    private val webdavRemoteRootConfirmedKey = booleanPreferencesKey("webdav_remote_root_confirmed")
    private val includeIndexSqliteKey = booleanPreferencesKey("sync_include_index_sqlite")
    private val officialSyncEnabledKey = booleanPreferencesKey("official_sync_enabled")
    private val webdavConflictStrategyKey = stringPreferencesKey("webdav_conflict_strategy")
    private val webdavAutoSyncEnabledKey = booleanPreferencesKey("webdav_autosync_enabled")
    private val webdavAutoSyncIntervalMinutesKey = intPreferencesKey("webdav_autosync_interval_minutes")
    private val webdavAutoSyncRetryCountKey = intPreferencesKey("webdav_autosync_retry_count")
    private val webdavAutoSyncRetryIntervalSecondsKey = intPreferencesKey("webdav_autosync_retry_interval_seconds")

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
    val officialSyncEnabled: Flow<Boolean> = context.dataStore.data.map { it[officialSyncEnabledKey] ?: false }

    val webDavPairName: Flow<String> = context.dataStore.data.map { it[webdavPairNameKey] ?: "" }

    // Default OFF: avoid surprise background-ish behavior, and prevent accidental sync when remoteRoot isn't configured yet.
    val webDavAutoSyncEnabled: Flow<Boolean> = context.dataStore.data.map { it[webdavAutoSyncEnabledKey] ?: false }

    // "Remote root explicitly selected" gate for all sync actions.
    //
    // Backward compatible default:
    // - if the confirmed flag is missing but remoteRoot != "/", treat it as confirmed (user likely configured it already)
    // - if remoteRoot == "/", require explicit confirmation to avoid accidental syncing to the root folder.
    val webDavRemoteRootConfirmed: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[webdavRemoteRootConfirmedKey]
                ?: ((prefs[webdavRemoteRootKey] ?: "/").trim().ifBlank { "/" } != "/")
        }

    val webDavAutomationSettings: Flow<WebDavAutomationSettings> =
        context.dataStore.data.map { prefs ->
            WebDavAutomationSettings(
                // Safety: keep a conservative minimum interval to avoid excessive battery/network usage.
                intervalMinutes = (prefs[webdavAutoSyncIntervalMinutesKey] ?: WebDavAutomationSettings.DEFAULT.intervalMinutes).coerceIn(15, 24 * 60),
                retryCount = (prefs[webdavAutoSyncRetryCountKey] ?: WebDavAutomationSettings.DEFAULT.retryCount).coerceIn(0, 10),
                retryIntervalSeconds =
                    (prefs[webdavAutoSyncRetryIntervalSecondsKey] ?: WebDavAutomationSettings.DEFAULT.retryIntervalSeconds).coerceIn(1, 60 * 60),
            )
        }

    suspend fun setWebDavEnabled(enabled: Boolean) {
        context.dataStore.edit { it[webdavEnabledKey] = enabled }
    }

    suspend fun setIncludeIndexSqlite(include: Boolean) {
        context.dataStore.edit { it[includeIndexSqliteKey] = include }
    }

    suspend fun setOfficialSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[officialSyncEnabledKey] = enabled }
    }

    suspend fun setWebDavPairName(name: String) {
        val trimmed = name.trim()
        context.dataStore.edit { prefs ->
            if (trimmed.isBlank()) prefs.remove(webdavPairNameKey) else prefs[webdavPairNameKey] = trimmed
        }
    }

    suspend fun setWebDavRemoteRoot(remoteRoot: String) {
        val trimmed = remoteRoot.trim()
        context.dataStore.edit { prefs ->
            prefs[webdavRemoteRootKey] = trimmed.ifBlank { "/" }
            prefs[webdavRemoteRootConfirmedKey] = true
        }
    }

    suspend fun setWebDavAutoSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[webdavAutoSyncEnabledKey] = enabled
        }
    }

    suspend fun saveWebDavAutomationSettings(settings: WebDavAutomationSettings) {
        context.dataStore.edit { prefs ->
            prefs[webdavAutoSyncIntervalMinutesKey] = settings.intervalMinutes.coerceIn(15, 24 * 60)
            prefs[webdavAutoSyncRetryCountKey] = settings.retryCount.coerceIn(0, 10)
            prefs[webdavAutoSyncRetryIntervalSecondsKey] = settings.retryIntervalSeconds.coerceIn(1, 60 * 60)
        }
    }

    suspend fun saveWebDavConfig(config: WebDavConfig) {
        context.dataStore.edit { prefs ->
            val nextRemoteRoot = config.remoteRoot.trim().ifBlank { "/" }
            val prevRemoteRoot = (prefs[webdavRemoteRootKey] ?: "/").trim().ifBlank { "/" }
            if (prevRemoteRoot != nextRemoteRoot) {
                // Require explicit selection again if the remote root changes.
                prefs[webdavRemoteRootConfirmedKey] = false
                // Auto-sync can't stay enabled when the target folder changes.
                prefs[webdavAutoSyncEnabledKey] = false
            }
            if (!config.enabled) {
                // Sync disabled -> auto-sync must be disabled as well.
                prefs[webdavAutoSyncEnabledKey] = false
            }
            prefs[webdavEnabledKey] = config.enabled
            prefs[webdavBaseUrlKey] = config.baseUrl
            prefs[webdavUsernameKey] = config.username
            prefs[webdavPasswordKey] = config.password
            prefs[webdavRemoteRootKey] = nextRemoteRoot
            prefs[includeIndexSqliteKey] = config.includeIndexSqlite
            prefs[webdavConflictStrategyKey] = config.conflictStrategy.name
        }
    }
}
