package app.zhixu.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AiPreferences(
    private val context: Context,
) {
    enum class OcrMode {
        OCR_ONLY,
        OCR_PLUS_AI,
    }

    data class State(
        val aiEnabled: Boolean,
        val baseUrl: String,
        val apiKey: String,
        val model: String,
        val debugEnabled: Boolean,
        val ocrEnabled: Boolean,
        val ocrMode: OcrMode,
        val ocrCreateTodos: Boolean,
        val noteAiEnabled: Boolean,
        val todoAiEnabled: Boolean,
    )

    private val aiEnabledKey = booleanPreferencesKey("ai_enabled")
    private val aiBaseUrlKey = stringPreferencesKey("ai_base_url")
    private val aiApiKeyKey = stringPreferencesKey("ai_api_key")
    private val aiModelKey = stringPreferencesKey("ai_model")
    private val aiDebugKey = booleanPreferencesKey("ai_debug")

    private val ocrEnabledKey = booleanPreferencesKey("ocr_enabled")
    private val ocrModeKey = stringPreferencesKey("ocr_mode")
    private val ocrCreateTodosKey = booleanPreferencesKey("ocr_ai_create_todos")

    private val noteAiEnabledKey = booleanPreferencesKey("note_ai_enabled")
    private val todoAiEnabledKey = booleanPreferencesKey("todo_ai_enabled")

    val state: Flow<State> =
        context.dataStore.data.map { prefs: Preferences ->
            val mode =
                runCatching { OcrMode.valueOf(prefs[ocrModeKey] ?: OcrMode.OCR_ONLY.name) }
                    .getOrDefault(OcrMode.OCR_ONLY)
            State(
                aiEnabled = prefs[aiEnabledKey] ?: false,
                baseUrl = prefs[aiBaseUrlKey] ?: "https://api.openai.com/v1",
                apiKey = prefs[aiApiKeyKey] ?: "",
                model = prefs[aiModelKey] ?: "",
                debugEnabled = prefs[aiDebugKey] ?: false,
                ocrEnabled = prefs[ocrEnabledKey] ?: true,
                ocrMode = mode,
                ocrCreateTodos = prefs[ocrCreateTodosKey] ?: false,
                noteAiEnabled = prefs[noteAiEnabledKey] ?: false,
                todoAiEnabled = prefs[todoAiEnabledKey] ?: false,
            )
        }

    suspend fun setAiEnabled(enabled: Boolean) {
        context.dataStore.edit { it[aiEnabledKey] = enabled }
    }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { it[aiBaseUrlKey] = url.trim() }
    }

    suspend fun setApiKey(key: String) {
        context.dataStore.edit { it[aiApiKeyKey] = key.trim() }
    }

    suspend fun setModel(model: String) {
        context.dataStore.edit { it[aiModelKey] = model.trim() }
    }

    suspend fun setDebugEnabled(enabled: Boolean) {
        context.dataStore.edit { it[aiDebugKey] = enabled }
    }

    suspend fun setOcrEnabled(enabled: Boolean) {
        context.dataStore.edit { it[ocrEnabledKey] = enabled }
    }

    suspend fun setOcrMode(mode: OcrMode) {
        context.dataStore.edit { it[ocrModeKey] = mode.name }
    }

    suspend fun setOcrCreateTodos(enabled: Boolean) {
        context.dataStore.edit { it[ocrCreateTodosKey] = enabled }
    }

    suspend fun setNoteAiEnabled(enabled: Boolean) {
        context.dataStore.edit { it[noteAiEnabledKey] = enabled }
    }

    suspend fun setTodoAiEnabled(enabled: Boolean) {
        context.dataStore.edit { it[todoAiEnabledKey] = enabled }
    }
}
