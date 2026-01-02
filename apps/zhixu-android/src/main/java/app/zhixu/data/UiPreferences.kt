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

enum class UiFontOption(
    val raw: String,
) {
    SYSTEM("system"),
    SOURCE_SANS_PRO_LIGHT("source_sans_pro_light"),
    SOURCE_SANS_PRO_REGULAR("source_sans_pro_regular"),
    LXGW_WENKAI_MONO_LIGHT("lxgw_wenkai_mono_light"),
    ;

    companion object {
        fun fromRaw(raw: String?): UiFontOption =
            when (raw?.trim()) {
                SYSTEM.raw -> SYSTEM
                SOURCE_SANS_PRO_REGULAR.raw -> SOURCE_SANS_PRO_REGULAR
                LXGW_WENKAI_MONO_LIGHT.raw -> LXGW_WENKAI_MONO_LIGHT
                SOURCE_SANS_PRO_LIGHT.raw -> SOURCE_SANS_PRO_LIGHT
                else -> SYSTEM
            }
    }
}

data class UiSettings(
    val languageTag: String,
    val themeMode: UiThemeMode,
    val fontOption: UiFontOption,
)

class UiPreferences(
    private val context: Context,
) {
    private val languageTagKey = stringPreferencesKey("ui_language_tag")
    private val themeModeKey = stringPreferencesKey("ui_theme_mode")
    private val fontOptionKey = stringPreferencesKey("ui_font_option")

    val settings: Flow<UiSettings> =
        context.dataStore.data.map { prefs ->
            UiSettings(
                languageTag = prefs[languageTagKey] ?: "",
                themeMode = UiThemeMode.fromRaw(prefs[themeModeKey]),
                fontOption = UiFontOption.fromRaw(prefs[fontOptionKey]),
            )
        }

    val languageTag: Flow<String> = context.dataStore.data.map { it[languageTagKey] ?: "" }

    /**
     * Nullable variant for app bootstrap: avoids applying a default value before DataStore emits.
     * `null` means "not set yet" (follow system default without forcing a locale update).
     */
    val languageTagOrNull: Flow<String?> = context.dataStore.data.map { it[languageTagKey] }

    val themeMode: Flow<UiThemeMode> = context.dataStore.data.map { prefs -> UiThemeMode.fromRaw(prefs[themeModeKey]) }

    val fontOption: Flow<UiFontOption> = context.dataStore.data.map { prefs -> UiFontOption.fromRaw(prefs[fontOptionKey]) }

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

    suspend fun setFontOption(option: UiFontOption) {
        context.dataStore.edit { prefs ->
            prefs[fontOptionKey] = option.raw
        }
    }
}
