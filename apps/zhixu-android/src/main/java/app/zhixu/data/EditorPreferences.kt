package app.zhixu.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class EditorDefaultMode {
    LIVE_PREVIEW,
    SOURCE,
    ;
}

data class EditorSettings(
    val defaultMode: EditorDefaultMode,
    val showNoteProperties: Boolean,
    val showLineNumbers: Boolean,
    val showEditorToolbar: Boolean,
)

class EditorPreferences(
    private val context: Context,
) {
    private val sourceModeKey = booleanPreferencesKey("editor_source_mode")
    private val showNotePropertiesKey = booleanPreferencesKey("editor_show_note_properties")
    private val showLineNumbersKey = booleanPreferencesKey("editor_show_line_numbers")
    private val showEditorToolbarKey = booleanPreferencesKey("editor_show_editor_toolbar")

    val settings: Flow<EditorSettings> =
        context.dataStore.data.map { prefs ->
            EditorSettings(
                defaultMode = if (prefs[sourceModeKey] == true) EditorDefaultMode.SOURCE else EditorDefaultMode.LIVE_PREVIEW,
                showNoteProperties = prefs[showNotePropertiesKey] ?: true,
                showLineNumbers = prefs[showLineNumbersKey] ?: false,
                showEditorToolbar = prefs[showEditorToolbarKey] ?: true,
            )
        }

    suspend fun setDefaultMode(mode: EditorDefaultMode) {
        context.dataStore.edit { prefs ->
            prefs[sourceModeKey] = mode == EditorDefaultMode.SOURCE
        }
    }

    suspend fun setShowNoteProperties(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[showNotePropertiesKey] = enabled
        }
    }

    suspend fun setShowLineNumbers(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[showLineNumbersKey] = enabled
        }
    }

    suspend fun setShowEditorToolbar(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[showEditorToolbarKey] = enabled
        }
    }
}

