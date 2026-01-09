package app.zhixu.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LogPreferences(
    private val context: Context,
) {
    private val debugEnabledKey = booleanPreferencesKey("log_debug_enabled")

    val debugEnabled: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[debugEnabledKey] ?: false
        }

    suspend fun setDebugEnabled(enabled: Boolean) {
        context.dataStore.edit { it[debugEnabledKey] = enabled }
    }
}

