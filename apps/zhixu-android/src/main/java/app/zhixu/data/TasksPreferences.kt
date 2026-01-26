package app.zhixu.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TasksPreferences(
    private val context: Context,
) {
    private val tasksEnabledKey = booleanPreferencesKey("tasks_plugin_enabled")

    val enabled: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[tasksEnabledKey] ?: true }

    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[tasksEnabledKey] = enabled
        }
    }
}
