package app.zhixu.sync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.zhixu.data.dataStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject

internal data class WebDavAutoSyncStateEntry(
    val lastAttemptedAtMs: Long,
    val lastSucceededAtMs: Long,
    val consecutiveFailures: Int,
    val lastError: String?,
)

internal class WebDavAutoSyncStateStore(
    private val context: Context,
) {
    private val stateKey = stringPreferencesKey("webdav_autosync_state_v1")

    suspend fun get(key: String): WebDavAutoSyncStateEntry {
        val raw = context.dataStore.data.first()[stateKey].orEmpty()
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
        val entries = obj.optJSONObject("entries") ?: JSONObject()
        val e = entries.optJSONObject(key) ?: JSONObject()
        return WebDavAutoSyncStateEntry(
            lastAttemptedAtMs = e.optLong("lastAttemptedAtMs", 0L).coerceAtLeast(0L),
            lastSucceededAtMs = e.optLong("lastSucceededAtMs", 0L).coerceAtLeast(0L),
            consecutiveFailures = e.optInt("consecutiveFailures", 0).coerceAtLeast(0),
            lastError = e.optString("lastError").orEmpty().trim().ifBlank { null },
        )
    }

    suspend fun set(key: String, entry: WebDavAutoSyncStateEntry) {
        context.dataStore.edit { prefs ->
            val raw = prefs[stateKey].orEmpty()
            val obj = runCatching { JSONObject(raw) }.getOrNull() ?: JSONObject()
            val entries = obj.optJSONObject("entries") ?: JSONObject()
            val e =
                JSONObject()
                    .put("lastAttemptedAtMs", entry.lastAttemptedAtMs.coerceAtLeast(0L))
                    .put("lastSucceededAtMs", entry.lastSucceededAtMs.coerceAtLeast(0L))
                    .put("consecutiveFailures", entry.consecutiveFailures.coerceAtLeast(0))
            if (!entry.lastError.isNullOrBlank()) {
                e.put("lastError", entry.lastError.take(240))
            }
            entries.put(key, e)
            obj.put("version", 1).put("entries", entries)
            prefs[stateKey] = obj.toString()
        }
    }
}

