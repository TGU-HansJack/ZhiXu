package com.zhixu.android.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VaultPreferences(
    private val context: Context,
) {
    private val vaultRootUriKey = stringPreferencesKey("vault_root_uri")

    val vaultRootUri: Flow<String?> =
        context.dataStore.data.map { prefs: Preferences -> prefs[vaultRootUriKey] }

    suspend fun setVaultRootUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(vaultRootUriKey)
            else prefs[vaultRootUriKey] = uri
        }
    }
}
