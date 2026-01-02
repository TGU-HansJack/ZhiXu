package app.zhixu.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class UiThemeMode(
    val raw: String,
) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromRaw(raw: String?): UiThemeMode =
            when (raw?.trim()?.lowercase()) {
                LIGHT.raw -> LIGHT
                DARK.raw -> DARK
                else -> SYSTEM
            }
    }
}

data class UiSettings(
    val languageTag: String,
    val themeMode: UiThemeMode,
)

class UiPreferences(
    private val context: Context,
) {
    private val languageTagKey = stringPreferencesKey("ui_language_tag")
    private val themeModeKey = stringPreferencesKey("ui_theme_mode")

    val settings: Flow<UiSettings> =
        context.dataStore.data.map { prefs ->
            UiSettings(
                languageTag = prefs[languageTagKey] ?: "",
                themeMode = UiThemeMode.fromRaw(prefs[themeModeKey]),
            )
        }

    val languageTag: Flow<String> = context.dataStore.data.map { it[languageTagKey] ?: "" }

    /**
     * Nullable variant for app bootstrap: avoids applying a default value before DataStore emits.
     * `null` means "not set yet" (follow system default without forcing a locale update).
     */
    val languageTagOrNull: Flow<String?> = context.dataStore.data.map { it[languageTagKey] }

    val themeMode: Flow<UiThemeMode> = context.dataStore.data.map { prefs -> UiThemeMode.fromRaw(prefs[themeModeKey]) }

    suspend fun setLanguageTag(tag: String) {
        context.dataStore.edit { prefs ->
            prefs[languageTagKey] = tag.trim()
        }
    }

    suspend fun setThemeMode(mode: UiThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[themeModeKey] = mode.raw
        }
    }
}
