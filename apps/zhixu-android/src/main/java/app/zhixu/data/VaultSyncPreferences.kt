package com.zhixu.android.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class VaultStorageLocation {
    LOCAL,
    OFFICIAL_SERVER,
    THIRD_PARTY_SERVICE,
}

data class ThirdPartyServiceConfig(
    val url: String,
    val username: String,
    val password: String,
    val e2eeEnabled: Boolean,
    val e2eeMasterPassword: String,
)

data class VaultSyncConfig(
    val location: VaultStorageLocation,
    val thirdParty: ThirdPartyServiceConfig,
)

class VaultSyncPreferences(
    private val context: Context,
) {
    private val locationKey = stringPreferencesKey("vault_storage_location")
    private val thirdPartyUrlKey = stringPreferencesKey("vault_third_party_url")
    private val thirdPartyUsernameKey = stringPreferencesKey("vault_third_party_username")
    private val thirdPartyPasswordKey = stringPreferencesKey("vault_third_party_password")
    private val thirdPartyE2eeEnabledKey = booleanPreferencesKey("vault_third_party_e2ee_enabled")
    private val thirdPartyE2eeMasterPasswordKey = stringPreferencesKey("vault_third_party_e2ee_master_password")

    val config: Flow<VaultSyncConfig> =
        context.dataStore.data.map { prefs: Preferences ->
            val location =
                runCatching { VaultStorageLocation.valueOf(prefs[locationKey] ?: "") }
                    .getOrDefault(VaultStorageLocation.LOCAL)
            VaultSyncConfig(
                location = location,
                thirdParty =
                    ThirdPartyServiceConfig(
                        url = prefs[thirdPartyUrlKey] ?: "",
                        username = prefs[thirdPartyUsernameKey] ?: "",
                        password = prefs[thirdPartyPasswordKey] ?: "",
                        e2eeEnabled = prefs[thirdPartyE2eeEnabledKey] ?: false,
                        e2eeMasterPassword = prefs[thirdPartyE2eeMasterPasswordKey] ?: "",
                    ),
            )
        }

    suspend fun saveConfig(config: VaultSyncConfig) {
        context.dataStore.edit { prefs ->
            prefs[locationKey] = config.location.name
            prefs[thirdPartyUrlKey] = config.thirdParty.url
            prefs[thirdPartyUsernameKey] = config.thirdParty.username
            prefs[thirdPartyPasswordKey] = config.thirdParty.password
            prefs[thirdPartyE2eeEnabledKey] = config.thirdParty.e2eeEnabled
            prefs[thirdPartyE2eeMasterPasswordKey] = config.thirdParty.e2eeMasterPassword
        }
    }

    suspend fun setLocation(location: VaultStorageLocation) {
        context.dataStore.edit { prefs -> prefs[locationKey] = location.name }
    }
}

