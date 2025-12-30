package com.zhixu.android.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class UiSettings(
    val languageTag: String,
)

class UiPreferences(
    private val context: Context,
) {
    private val languageTagKey = stringPreferencesKey("ui_language_tag")

    val settings: Flow<UiSettings> =
        context.dataStore.data.map { prefs ->
            UiSettings(
                languageTag = prefs[languageTagKey] ?: "",
            )
        }

    val languageTag: Flow<String> = context.dataStore.data.map { it[languageTagKey] ?: "" }

    suspend fun setLanguageTag(tag: String) {
        context.dataStore.edit { prefs ->
            prefs[languageTagKey] = tag.trim()
        }
    }
}

