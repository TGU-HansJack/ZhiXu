package app.zhixu.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProPreferences(
    private val context: Context,
) {
    private val proEnabledKey = booleanPreferencesKey("pro_enabled")

    val isProEnabled: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[proEnabledKey] ?: false
        }

    suspend fun setProEnabled(enabled: Boolean) {
        context.dataStore.edit { it[proEnabledKey] = enabled }
    }
}
