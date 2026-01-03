package app.zhixu.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.zhixu.data.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AiPreferences(
    private val context: Context,
) {
    private val key = stringPreferencesKey("ai_settings_json")

    val settings: Flow<AiSettings> =
        context.dataStore.data.map { prefs ->
            AiSettings.fromJson(prefs[key])
        }

    suspend fun setSettings(settings: AiSettings) {
        context.dataStore.edit { prefs ->
            prefs[key] = settings.toJson().toString()
        }
    }
}

